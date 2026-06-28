---
tags:
  - redis
  - 大key
  - 排查
created: 2026-06-21
updated: 2026-06-21
source: [[redis八股]]
---

# SCAN 命令查找大 Key — 详解

> SCAN — Cursor-based incremental iterator（基于游标的渐进式迭代器）

## 一、为什么不用 KEYS 而用 SCAN

| 命令 | 时间复杂度 | 是否阻塞 | 是否阻塞主线程 | 生产可用 |
|------|-----------|---------|---------------|---------|
| `KEYS pattern` | O(N)，N 是整个 keyspace | 是，会一次性遍历完所有 key | 是，单线程模型下整个 Redis 停摆 | **禁止** |
| `SCAN cursor [MATCH pattern] [COUNT count] [TYPE type]` | 单次 O(1)，整体 O(N) 分摊 | 否，分批推进 | 否，每批之间可处理其他命令 | **推荐** |

**面试话术：** `KEYS *` 是 O(N) 阻塞命令，生产实例 key 数量大时一次扫描能卡死 Redis 几百毫秒到几秒；`SCAN` 用游标分批推进，每次只处理一小批，O(1) 单次复杂度，对主线程影响可控。

## 二、SCAN 命令完整语法

```
SCAN cursor [MATCH pattern] [COUNT count] [TYPE type]
```

- `cursor`：游标，初始传 `0`，Redis 返回下一次要传的游标，当游标再次变为 `0` 时表示遍历结束
- `MATCH pattern`：glob 风格过滤（`user:*`、`order:2026:*`），但**仅对扫描到的批次生效**，可能漏掉匹配项
- `COUNT count`：建议每次返回的元素数量，**只是 hint 不是硬限制**，默认 10
- `TYPE type`：Redis 6.0+ 支持，只返回指定类型的 key，进一步减少扫描量

**返回值格式：**

```
1) "12345"               ← 下一次要传进去的游标
2) 1) "user:1001"
   2) "user:1002"
   ...
```

## 三、完整排查流程

### 步骤 1：用 SCAN 扫描出候选 key

```bash
# 单次执行
redis-cli -h 127.0.0.1 -p 6379 SCAN 0 MATCH "user:*" COUNT 1000

# 批量循环（bash）
cursor=0
while [ "$cursor" != "0" ]; do
  result=$(redis-cli -h 127.0.0.1 -p 6379 SCAN $cursor MATCH "*" COUNT 1000)
  cursor=$(echo "$result" | head -1)
  echo "$result" | tail -n +2 | tr -d '"'
  sleep 0.05   # 降低对线上实例的压力
done
```

或者用 `redis-cli` 自带的 `--scan` 封装（内部就是 SCAN 循环）：

```bash
redis-cli -h 127.0.0.1 -p 6379 --scan --pattern "user:*"
```

### 步骤 2：用 TYPE 判断 key 的类型

```bash
TYPE user:1001
# 返回：string / list / hash / set / zset / stream / none
```

### 步骤 3：根据类型测大小

#### String 类型：直接用 STRLEN

```bash
STRLEN article:99001
# 返回字节数
```

STRLEN 直接返回字符串的字节数，等于实际内存占用（String 类型没有内嵌元素）。

#### 集合类型：两种方法

| 方法 | 命令 | 优点 | 缺点 |
|------|------|------|------|
| **估算** | `LLEN` / `HLEN` / `SCARD` / `ZCARD` × 平均元素大小 | 快，开销低 | 元素大小未知时误差大；100 万个 1 字节元素只占 1MB，但 SCARD 返回 100 万 |
| **精确** | `MEMORY USAGE key` | 返回真实占用（含编码 overhead、ziplist/listpack 压缩、RedisObject 头） | 需要 Redis 4.0+；单次调用开销较大 |

**LLEN / HLEN / SCARD / ZCARD 只能返回元素个数**，无法直接得到字节数。要得到字节数必须乘以「平均元素大小」（要靠业务估算），或者直接用 `MEMORY USAGE`：

```bash
# Hash 类型（精确值，推荐）
MEMORY USAGE order:7788
# 返回字节数，包含 key 本身 + 字段 + 值的全部开销

# Hash 类型（估算）
HLEN order:7788
# 假设每个 field-value 平均 64 字节 → 大小 ≈ 7788 × 64
```

**为什么 MEMORY USAGE 更准：** Redis 的集合底层编码不是固定的——元素少时用 ziplist/listpack（紧凑编码），元素多时转为 hashtable/skiplist（指针结构），不同编码的内存开销差异巨大。只有 MEMORY USAGE 会真实计算当前编码下的占用字节数。

### 步骤 4：汇总筛选 Top N

把扫描 + 类型判断 + 大小测量串成一个完整脚本，按大小排序输出：

```bash
redis-cli -h 127.0.0.1 -p 6379 --scan --pattern "*" | \
while read key; do
  size=$(redis-cli -h 127.0.0.1 -p 6379 MEMORY USAGE "$key")
  echo "$size $key"
done | sort -rn | head -50
```

输出形如：

```
1048576 biglist:order:2026:06
524288  hash:user:profile:10086
102400  string:article:99001
...
```

## 四、生产环境注意事项

### ① 在从节点执行

主节点负责处理写命令，扫描大 key 会占用主线程，影响业务请求。建议在从节点执行 SCAN / `MEMORY USAGE`：

```bash
redis-cli -h 从节点IP -p 6379 SCAN 0 MATCH "*" COUNT 1000
```

### ② 控制扫描节奏

加 `-i 0.01` 让 `redis-cli` 在每条命令间 sleep 10ms：

```bash
redis-cli -h 127.0.0.1 -p 6379 -i 0.01 --scan --pattern "*"
```

COUNT 不要设太大（默认 10 已经够用），避免单次阻塞太久。

### ③ SCAN 的两个坑

1. **可能在同一个 key 上返回多次**（在数据变动时），需要应用层去重。
2. **可能漏掉新写入的 key**（遍历期间新加的 key 不保证被扫到）。

### ④ MEMORY USAGE 的开销

它**真的去算**，需要遍历整个数据结构，单次调用比 `STRLEN` 等重得多。不要对线上热 key 频繁调用。

## 五、面试高频追问

### Q1：SCAN 的 COUNT 参数是硬限制吗？

**不是 hint。** Redis 不保证每次返回恰好 COUNT 个元素，只是一个**建议值**。默认 10 表示「这次大致处理 10 个槽位」，实际可能返回 0 个或几十个。

### Q2：SCAN 能保证完整遍历吗？

**遍历期间数据不变时能保证。** 但如果在扫描过程中有 key 过期、新增、修改，可能：
- 同一个 key 被返回多次（应用层需要去重）
- 新写入的 key 不保证被扫到

### Q3：为什么 SCAN 不用锁还能保证不漏？

Redis 的 hash table 在 rehash 时采用**渐进式 rehash**：保留旧表，新 key 写到新表，扫描时同时遍历两张表，保证不漏。但同一元素可能在旧表出现一次、新表出现一次，导致重复，应用层要去重。

### Q4：LLEN / HLEN 这些命令能不能判断大 key？

**能初步判断，但容易误判。** 它们只看元素个数，不看实际字节数。100 万个短字符串的 Set 实际只占几 MB，但 `SCARD` 返回 100 万。**要精确判断大 key，必须用 `MEMORY USAGE`。**

### Q5：MEMORY USAGE 返回的字节数包含什么？

包含：
- key 本身的字符串占用
- 值对象的 RedisObject 头
- 底层编码（ziplist/listpack/hashtable/skiplist）实际占用的内存
- 各种元数据开销

**不包含**网络传输开销、客户端缓冲区开销。

## 六、一句话总结（面试话术）

> SCAN 游标分批扫出候选 key，TYPE 判断类型，String 用 STRLEN，其他类型直接用 `MEMORY USAGE`（4.0+）拿到真实字节数，最后按大小排序筛出 Top N。整个过程非阻塞，适合线上执行，但 `MEMORY USAGE` 单次开销不小，仍要在从节点或低峰期跑。

---

## 关联知识点

- [[redis八股]] — Redis 核心八股总览
- [[Redis 大 Key 对持久化有什么影响？]] — 大 Key 对 AOF/RDB 的阻塞影响
- [[Redis 常见数据类型和应用场景]] — 5 种基础类型的用途与命令
