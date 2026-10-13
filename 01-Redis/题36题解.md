---
tags:
  - redis
created: 2026-07-31
updated: 2026-07-31
---
### **用 `redis-cli --bigkeys` / `--memkeys` 做全库采样、`MEMORY USAGE` 精准定位单 key、`SCAN + DEBUG OBJECT` 做深度分析；拆分方案核心是"按业务维度 Hash 分片 + 冷热分离 + 惰性迁移"。**

下面按面试口述节奏展开。

---

**第一部分：先讲清楚大 Key 为什么危险**

一个 Hash 存 1000 万字段，会引发一连串问题，理解危害才能理解排查和拆分的方向：

**1. 阻塞主线程**：Redis 是**单线程模型**，对这个 Hash 执行 `HGETALL`、`DEL`、`HKEYS` 都是 O(N) 操作，1000 万字段可能阻塞主线程数秒，期间**所有其他请求全部排队**，QPS 直接归零。

**2. 网络拥塞**：`HGETALL` 一次性返回几百 MB 数据，网卡打满，其他客户端超时。

**3. 内存分布不均**：Redis Cluster 按 key 做 slot 分片，**一个大 key 只能待在一个节点**上，导致该节点内存远高于其他节点，无法通过扩容缓解。

**4. 持久化风险**：AOF rewrite、RDB fork 时，子进程写这个大 key 的耗时长，COW（copy-on-write）内存膨胀严重，可能触发 OOM。

**5. 主从同步风险**：全量同步时这个大 key 会成为传输瓶颈，一旦超时触发重连，形成"同步 → 超时 → 重连"的死循环。

**6. 删除风险**：直接 `DEL` 一个 1000 万字段的 Hash 会阻塞主线程数秒，必须用 `UNLINK`（4.0+）异步删除。

---

**第二部分：排查工具**

**1. `redis-cli --bigkeys`（全库采样扫描）**

```bash
redis-cli -h host -p port --bigkeys
```

原理是**基于 SCAN 迭代整个 keyspace**，对每种数据类型记录"最大的那一个"，最后输出报告。

- **优点**：非阻塞（用 SCAN 而不是 KEYS）、无需下线；
- **缺点**：
  - 只报告**每种类型 Top 1**，不是"所有大 Key"；
  - 大小判断按**元素数量**（Hash 字段数、List 长度），**不是内存占用**——1000 万个空字段和 1 万个 1MB value，前者数量大后者内存大，可能漏报后者；
  - 全库扫描在超大实例上耗时长，生产上加 `-i 0.1` 控制 SCAN 间隔避免打爆 CPU。

**2. `redis-cli --memkeys`（Redis 6.0+，按内存排名）**

```bash
redis-cli --memkeys
```

弥补 `--bigkeys` 的痛点，按 **`MEMORY USAGE`** 排序而不是元素数量，更贴近"真实占内存的大 key"。生产环境推荐用它替代 `--bigkeys`。

**3. `MEMORY USAGE key [SAMPLES 0]`（单 key 精准分析）**

```
MEMORY USAGE user_profile_hash
(integer) 838860800    # 单位字节，约 800MB
```

- 返回该 key 占用的**准确内存字节数**（含 overhead）；
- `SAMPLES 0` 表示扫描所有元素（默认是 5 个采样，采样在大集合上会有偏差）；
- **注意**：`SAMPLES 0` 在千万字段的 Hash 上本身也是 O(N) 操作，会阻塞主线程，**生产上必须去从库执行或在低峰期**。

**4. `DEBUG OBJECT key`（底层编码 + 引用信息）**

```
DEBUG OBJECT user_profile_hash
Value at:0x7f... refcount:1 encoding:hashtable serializedlength:... lru:...
```

看 `encoding` 字段：Hash 小于 `hash-max-listpack-entries`（默认 128）时是 `listpack`（省内存），超过后升级为 `hashtable`（吃内存）。**编码升级是大 key 形成的重要信号**。

**5. RDB 离线分析（生产推荐）**

用 **`redis-rdb-tools`** 或阿里的 **`rdb_bigkeys`** 工具解析 RDB 文件：

```bash
rdb -c memory dump.rdb --bytes 10240 -f bigkey.csv
```

- **完全不影响线上**，从从库或备份文件分析；
- 输出**所有超过阈值的 key 列表**，含类型、字段数、内存、TTL；
- 生产上定期跑一次，是最实用的大 key 发现手段。

**6. Redis 6.0+ 客户端缓存 + 慢日志辅助**

```
SLOWLOG GET 128        # 看最近慢查询命中哪些 key
CLIENT LIST            # 看哪些客户端在读大 key
```

慢日志里如果频繁出现 `HGETALL`、`SMEMBERS`、`LRANGE 0 -1` 且耗时 > 10ms，基本能锁定大 key 嫌疑。

---

**第三部分：拆分方案**

回到题目：一个 Hash 1000 万字段，怎么拆？核心思路是**"按业务维度打散 + 冷热分离 + 平滑迁移"**。

**方案一：按字段做 Hash 分片（最通用）**

把原本的 `user_profile` 一个大 Hash，拆成 N 个小 Hash：

```
原始： HSET user_profile {uid} {json}      # 1000 万字段
拆后： HSET user_profile:{uid % 1024} {uid} {json}   # 每个 Hash 约 1 万字段
```

- **分片数选择**：让单个 Hash 字段数控制在 `hash-max-listpack-entries` 附近（默认 128），可以享受 listpack 编码红利，内存最省；如果字段本身大，退一步保证每个 Hash **不超过 5000 字段**；
- **路由规则**：应用层根据 `uid % N` 计算分片下标，读写都走同一规则；
- **额外收益**：分片后不同 shard 可能落在 Cluster 的不同 slot，**天然打散热点**。

**方案二：按业务维度垂直拆分**

如果 Hash 里的 field 有明显的业务属性差异，比如 `user_profile` 里既有基础信息又有实时数据：

```
user_profile:{uid}    → user_basic:{uid}     # 基础信息，读多写少
                     → user_realtime:{uid}   # 实时数据，写多读少
                     → user_stats:{uid}      # 统计数据，可延迟
```

不同 key 走不同 TTL、不同持久化策略，甚至放在不同 Redis 实例上。

**方案三：冷热分离**

1000 万字段里往往只有小部分是"热数据"（最近访问的用户），大部分是冷数据：

- **热数据**留 Redis，用小 Hash 或 String；
- **冷数据**下沉到 **MySQL / HBase / TiKV**，Redis 只做缓存，miss 时回源；
- 通过 LRU、`OBJECT IDLETIME` 判断哪些字段可以下沉。

**方案四：改数据结构**

有时候大 Hash 不该是 Hash：

- 如果 field 是有序的时间戳，改用 **ZSet**，支持范围查询；
- 如果 value 都是数字统计，改用 **HyperLogLog / Bitmap**，1000 万字段可能只占几 KB；
- 如果只需要判断存在性，改用 **Bloom Filter**（RedisBloom 模块）。

---

**第四部分：拆分的迁移策略（易被忽略但很重要）**

拆分方案设计好后，**怎么从旧 key 平滑迁到新 key**是关键：

**1. 双写过渡**

新旧结构同时写，读优先读新结构，miss 时回读旧结构并回填新结构，直到旧 key 数据全部迁完。

**2. 后台任务迁移**

用 `HSCAN` 遍历旧 Hash（**必须用 SCAN，绝对不能 HGETALL**），分批 `HSET` 到新 Hash，控制迁移速率避免打爆 Redis。

**3. 删除旧 key 用 UNLINK**

迁移完成后，**绝对不能 `DEL big_key`**，会阻塞主线程数秒。用 `UNLINK`（4.0+）异步删除：

```
UNLINK user_profile
```

主线程只解引用，实际释放内存放到后台线程，不阻塞。

**4. 灰度切流 + 回滚方案**

新老路由通过配置开关切换，观察 QPS、延迟、内存无异常后再全量。

---

**第五部分：预防机制（别让下一个大 key 出现）**

排查和拆分都是事后动作，长期还得靠预防：

1. **业务侧规范**：Hash 字段数上限约定（比如不超过 5000），List、ZSet、Set 长度上限（比如不超过 1 万），代码 review 强卡；
2. **监控告警**：定期跑 `--memkeys` 或 RDB 分析，key 内存超过阈值（比如 10MB）自动告警；
3. **写入侧拦截**：中间件层（比如自研 Redis Proxy）对单 key 写入大小做限制，超过阈值直接拒绝；
4. **TTL 兜底**：所有 key 都要有 TTL，避免"写入永不过期"的历史遗留 key 悄悄膨胀；
5. **禁用危险命令**：`rename-command` 禁掉 `KEYS`、`FLUSHALL`、`HGETALL`（对未知大小的 key），或用 ACL 限制到只有运维账号能执行。

---

**面试收尾一句话：**

大 Key 排查的核心不是"找到最大的那个"，而是**建立"发现—定位—拆分—预防"闭环**：用 `--memkeys` / RDB 离线分析发现，`MEMORY USAGE` 精准定位，按业务维度做 Hash 分片 + 冷热分离拆分，最后用 `UNLINK` 安全清理旧数据。真正稳定的 Redis 集群，不是没有大 key，而是**大 key 还没长大就被监控发现、被规范拦截**。