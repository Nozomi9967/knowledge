# Elasticsearch详解 — 分布式搜索引擎

> **关于名称**：Elasticsearch 简称 **ES**，是 Elastic Stack（ELK）核心组件，基于 **Apache Lucene** 的分布式搜索引擎。提供 RESTful API，支持全文搜索、结构化搜索、聚合分析等能力。母公司 Elastic 于 2018 年在纽交所上市（NYSE: ESTC）。

## 一句话定义

> **Elasticsearch** 是一个**分布式、RESTful 风格的搜索和数据分析引擎**，基于倒排索引实现**毫秒级的全文搜索**，常用于商品搜索、日志分析、指标监控等场景。

---

## 解决了什么问题？

在电商平台中，用户需要根据关键词搜索商品。

### ❌ 传统方式（MySQL LIKE）

```sql
SELECT * FROM sku WHERE name LIKE '%手机%';
```

问题：
- **全表扫描**：百万级数据 LIKE 不走索引（即使加索引，前导通配符也失效）
  > **什么是前导通配符？** `LIKE '%手机%'` 中 `%` 在开头，MySQL 的 B+ Tree 索引是按**从左到右的字符串前缀**组织的，无法通过 B+ Tree 前缀定位，只能逐行扫描。相当于"从字典里找出所有包含'手机'的词"——你不知道第一个字，只能翻遍整本字典。而 `LIKE '手机%'`（无前导通配）是可以走索引的。
- **不支持分词**：搜"智能手机"匹配不到"智慧手机"
- **性能差**：百万数据 LIKE 耗时秒级，QPS 极低
- **不支持排序**：难以按相关性对结果排序

### ✅ ES 方式

```
用户搜索 "手机" → ES 倒排索引 → 分词匹配 → 按相关性打分 → 毫秒级返回结果
```

ES 对"手机"分词后，通过 **Term Dictionary**（通常用 FST 或跳表实现）定位到"手机"这个词，再取出对应的 Posting List。

> **复杂度分析（面试加分点）**：
> - **MySQL `LIKE '%手机%'`**：**O(N)**，N = 总文档数。每行都得扫一遍，数据越多越慢。
> - **ES 倒排索引查询**：找词用 **FST（Finite State Transducer）** → **O(k)**，k = 词的长度（跟有多少数据无关）。取 Posting List → **O(m)**，m = 命中文档数。
> - **关键差异**：ES 的耗时**不随总数据量增长**（O(k) 只跟搜的词有多长有关），MySQL LIKE 跟总数据量成正比（O(N)）。百万数据下，前者 < 50ms，后者秒级。

---

## ES 核心概念

### 先搞清两个世界：关系型数据库 vs ES

| MySQL | ES | 通俗理解 |
|-------|-----|---------|
| Database（数据库） | **Index**（索引） | 一个"库" |
| Table（表） | **Type**（类型，7.x 起废弃） | 一张"表" |
| Row（行） | **Document**（文档，JSON 格式） | 一条"记录" |
| Column（列） | **Field**（字段） | 一个"属性" |
| Index（索引） | **Inverted Index**（倒排索引） | 检索目录 |
| SQL | **Query DSL**（JSON 格式查询） | 查询语言 |
| `SELECT * FROM table` | `GET /index/_search` | 查询方式 |

**本项目映射**：
```
MySQL:  nozomi-mall.sku（商品表）  ──▶  ES:  shopsearch 索引
                                          ├─ Document = 一件商品
                                          ├─ Field = 商品名、价格、品牌、分类...
                                          └─ 倒排索引加速搜索
```

---

### 核心原理：倒排索引

这是 ES 秒级搜索的根本原因。

#### 正排索引（MySQL 的方式）

```
文档1: "华为手机"  →  [华为, 手机]
文档2: "苹果手机"  →  [苹果, 手机]
文档3: "华为平板"  →  [华为, 平板]

搜索"手机" → 遍历所有文档 → 找到文档1、2  ✅
但数据量大了就慢（全表扫描）
```

#### 倒排索引（ES 的方式）

```
先对所有文档做 分词（Tokenization），建立"词 → 文档"的映射表：

华为  →  文档1, 文档3
手机  →  文档1, 文档2
苹果  →  文档2
平板  →  文档3

搜索"手机" → 查倒排表 → 直接定位到文档1、2  ✅
时间复杂度 O(1)，不随数据量增长而变慢
```

**通俗理解**：正排索引像书的**目录**（第1页→标题A，第2页→标题B），倒排索引像书的**索引页**（关键词A→第1,3,5页，关键词B→第2,4页）。

### ES 中的倒排索引包含什么？

```
┌──────────────────────────────────────────┐
│               Term Dictionary             │
│  （词项字典，按字母排序）                    │
│  ├─ 华为 ──▶ Posting List: [doc1, doc3]  │
│  ├─ 手机 ──▶ Posting List: [doc1, doc2]  │
│  ├─ 苹果 ──▶ Posting List: [doc2]        │
│  └─ 平板 ──▶ Posting List: [doc3]        │
│                                           │
│  每个 Posting List 包含：                   │
│  ├─ 文档 ID（docId）                       │
│  ├─ 词频（TF，这个词在该文档中出现几次）       │
│  └─ 位置（Position，第几个词）              │
└──────────────────────────────────────────┘
```

> **名词解释**：
> - **Term Dictionary**：所有不重复词的列表，按字典序排列，用于快速查找
> - **Posting List**：包含某个词的文档列表，附带词频和位置信息
> - **TF（Term Frequency）**：词频，一个词在文档中出现的次数，影响相关性评分
> - **DF（Document Frequency）**：文档频率，包含某个词的文档数量，DF 越低词越"稀有"
> - **TF-IDF**：经典相关性评分算法，TF×IDF，ES 5.x 之前默认，之后改为 BM25

---

## ES 工作流程

### 写入流程

```
外部数据 ──▶ 分词器（Analyzer）
                ├─ Character Filter（字符过滤）：去除 HTML 标签、特殊字符
                ├─ Tokenizer（分词器）：按规则拆词
                └─ Token Filter（词元过滤）：转小写、去停用词、词干提取
                    │
                    ▼
              倒排索引 ←── 写入内存 Buffer
                                    │
                          refresh（默认 1s）→ 写入 FileSystem Cache → 可搜索
                                    │
                          flush → 写入磁盘（translog + segment）
```

**关键机制**：
- **近实时搜索**（NRT）：ES 不是写入即搜到，而是 **refresh** 后才可搜索（默认 1s）
- **分段存储**（Segment）：每个分片的倒排索引由多个 **segment**（段）组成，新写入生成新 segment，后台合并
- **translog**：写操作先记 translog（类似 MySQL 的 redo log），保证断电不丢数据

### 查询流程

```
用户搜索 "华为手机"
    │
    ▼
协调节点（Coordinating Node）接收请求
    │
    ▼
分发到所有分片（或指定分片）
    │
    ▼
各分片在本地倒排索引中查找
    ├─ 分词"华为" → 定位 Posting List
    └─ 分词"手机" → 定位 Posting List
    │
    ▼
计算相关性得分（BM25 算法）
    │
    ▼
协调节点汇总排序 → 返回 Top N 结果
```

### 本项目搜索链路

```
用户搜索
  │
  ▼
mall-search-web（Thymeleaf 前端）
  │
  ▼
Search 服务 ──▶ ES（shopsearch 索引）
                  ├─ bool 查询组合多条件
                  │   ├─ match：关键词分词匹配
                  │   ├─ term：精确匹配（如品牌 ID）
                  │   ├─ range：范围过滤（如价格区间）
                  │   └─ filter：不参与评分的过滤条件
                  ├─ 高亮（highlight）：命中关键词加 `<em>` 标签
                  ├─ 排序（sort）：按价格/销量/相关性
                  └─ 分页（from + size）
  │
  ▼
渲染搜索结果页
```

---

## ES 集群架构

### 核心角色

| 角色 | 职责 | 通俗理解 |
|------|------|---------|
| **Master Node** | 管理集群元数据、创建/删除索引、分配分片 | 公司的"大管家"，管分配不管干活 |
| **Data Node** | 存储数据、执行 CRUD、执行搜索聚合 | 一线"员工"，真干活 |
| **Coordinating Node** | 接收请求、分发到 Data Node、汇总结果 | "前台接待"，接单派活汇总 |
| **Ingest Node** | 数据写入前预处理（管道） | "质检员"，入库前先检查 |

> **注意**：一个节点可以承担多个角色。小集群用一个节点承担所有角色。

### 分片机制

```
索引 shopsearch
  ├─ Primary Shard 0  ──▶ 数据子集 A
  ├─ Primary Shard 1  ──▶ 数据子集 B
  ├─ Primary Shard 2  ──▶ 数据子集 C
  └─ Primary Shard 3  ──▶ 数据子集 D
                            │
                     （每个 Primary 有 N 个 Replica）
                            │
                     ┌──────┴──────┐
                     ▼              ▼
              Primary Shard 0   Replica Shard 0（在另一台机器）
```

| 概念 | 说明 | 通俗理解 |
|------|------|---------|
| **Primary Shard** | 主分片，索引创建时指定（不可修改） | 一本书被拆成 N 册，每册在一台机器上 |
| **Replica Shard** | 副本分片，可调整数量 | 每册都有复印本，存另一台机器上防丢 |
| **分片数设置** | 7.x 默认 `1主1副` | 小项目一般够用 |

**分片的作用**：
1. **水平扩展**：数据分散到多台机器，突破单机存储上限
2. **并行查询**：一个查询并行到所有分片执行，速度快 N 倍
3. **高可用**：Primary 挂了，Replica 自动升为主

---

## 分词器（Analyzer）

### 分词器三件套

```
Analyzer = Character Filter（字符过滤） + Tokenizer（分词器） + Token Filter（词元过滤）
```

### 常见分词器

| 分词器 | 示例输入 | 分词结果 | 适用场景 |
|--------|---------|----------|---------|
| **Standard Analyzer**（默认） | "I love iPhone" | [i, love, iphone] | 英文文本 |
| **IK Analyzer**（中文，本项目使用） | "华为手机" | [华为, 手机] | ✅ **中文搜索必备** |
| **Whitespace Analyzer** | "Huawei Mate 40" | [Huawei, Mate, 40] | 按空格分 |
| **Keyword Analyzer** | "华为手机" | [华为手机] | 不分词，当成整体 |

> ⚠ **IK 分词器需要额外安装插件**，ES 默认 Standard 对中文是逐字分词（"华""为""手""机"），必须装 IK 才能正确中文分词。

### IK 分词器的两种模式

| 模式 | 输入 | 输出 | 适用 |
|------|------|------|------|
| **ik_smart**（粗粒度） | "华为智能手机" | [华为, 智能手机] | 搜索条件少用，精准 |
| **ik_max_word**（细粒度） | "华为智能手机" | [华为, 智能, 智能手机, 手机] | 索引时多用，召回率高 |

**本项目建议**：索引用 `ik_max_word`（多分一些，提升召回率），搜索用 `ik_smart`（精准匹配，提升准确率）。

---

## 本项目中的 ES 使用

### 1. 商品搜索

```
Canal 监听 MySQL sku 表 binlog
  → Canal Client 通过 Feign 调用 Search 服务
    → Search 服务写入 ES shopsearch 索引
      → 用户在前端搜索 → Search 服务查询 ES → 返回结果
```

### 2. 查询方式

```json
// ES Query DSL 示例（商品搜索）
GET /shopsearch/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "name": "手机" } }           // 关键词模糊匹配
      ],
      "filter": [
        { "term": { "categoryId": 123 } },        // 分类精确过滤
        { "range": { "price": { "gte": 1000, "lte": 5000 } } }, // 价格范围
        { "term": { "brandId": 456 } }            // 品牌过滤
      ]
    }
  },
  "highlight": {
    "fields": { "name": {} }                      // 命中关键词高亮
  },
  "sort": [
    { "price": "asc" }                            // 按价格升序
  ],
  "from": 0,
  "size": 20
}
```

### 3. 架构设计决策

| 决策 | 方案 | 原因 |
|------|------|------|
| 数据同步方式 | **Canal** 监听 binlog | 零业务侵入，解耦业务写与搜索同步 |
| 中文分词 | **IK Analyzer** | ES 默认 Standard 不支持中文分词 |
| 索引命名 | `shopsearch` | 单一商品搜索索引，结构简单 |

---

## 面试高频考点

### Q1：ES 倒排索引的原理是什么？为什么比 MySQL LIKE 快？

**回答要点**：

```
MySQL LIKE "%手机%"
  → 全表扫描，逐行比对字符串
  → 时间复杂度 O(n)，数据越多越慢

ES 倒排索引
  → 写入时建立"词 → 文档"的映射表
  → 搜索时查表定位，时间复杂度 O(1)，不随数据量增长
```

**进阶话术**：
> MySQL 的 B+ Tree 索引对前缀查询（如 `手机%`）有效，但对 `%手机%` 这种前后模糊匹配**完全失效**，必须全表扫描。ES 把文本拆成词后建立倒排表，搜索时直接命中——这是两种完全不同的数据结构设计，ES 为此付出的代价是**索引构建和存储成本更高**（索引体积通常比源数据大）。

### Q2：ES 的写入延迟是多少？为什么不是写入即搜索？

**回答要点**：
- ES 是**近实时搜索**（Near Real-Time），默认 **refresh_interval = 1s**
- 写入过程：`写入 Buffer → refresh(1s) → FileSystem Cache → 可搜索`
- 所以数据写入后最多 1s 才可见，不是实时

**为什么设计成近实时？**
> 为了**批量提交**：攒够一批文档一次性写入磁盘，减少磁盘 I/O 次数。如果写入一条就刷盘一次（像 MySQL），写入性能会极大下降。这是"写入性能"和"实时性"之间的 trade-off。

### Q3：ES 分片数怎么设置？设置后能改吗？

**回答要点**：

| 问题 | 答案 |
|------|------|
| 分片数什么时候定？ | **创建索引时** |
| Primary Shard 能改吗？ | **不能**（创建后不可修改） |
| Replica Shard 能改吗？ | **能**（随时动态调整） |
| 分片数设多少合适？ | 7.x 默认 1 主 1 副够用，大数据量按 **每个分片 20~50GB** 估算 |

**为什么要预先规划分片？**
> 分片数决定了集群的**最大并行度**和**水平扩展上限**。分片太多→查询时需要汇总大量结果，协调节点压力大；分片太少→单分片数据量大，查询慢且无法水平扩展。

### Q4：ES 数据量大时查询变慢，怎么优化？

**优化思路（从低成本到高成本）**：

| 优化手段 | 原理 | 效果 |
|---------|------|------|
| 1. **Filter 代替 Must** | Filter 不计算评分，结果可缓存 | 显著提升 |
| 2. **使用 Keyword 类型** | 精确匹配字段设为 keyword，不分词 | 显著提升 |
| 3. **控制返回字段** | `_source` 只返回需要的字段 | 中等提升 |
| 4. **分页优化** | 深度分页用 `search_after` 替代 `from+size` | 大幅提升 |
| 5. **冷热分离** | 热数据 SSD + 冷数据 HDD，按时间建索引 | 中等提升 |
| 6. **增加节点** | 水平扩展，分担查询压力 | 线性提升 |
| 7. **调整 Refresh 间隔** | `refresh_interval` 从 1s 改为 30s | 写入性能显著提升 |

### Q5：ES 深度分页问题（from + size 为什么慢？）

**回答要点**：

```
请求：from=10000, size=20
  → 每个分片都要取前 10020 条
  → 协调节点汇总 N×10020 条
  → 排序后取第 10000~10020 条
  → 前面的 10000 条全部丢弃
  —— 越翻到后面，浪费的计算越多
```

**解决方案**：
- **Scroll API**：生成快照游标，适合**全量导出**（不保证实时）
- **search_after**：基于上一页最后一条的排序值继续翻，适合**实时分页**

---

## 项目相关追问

### Q1：为什么用 Canal 同步数据到 ES，而不是业务代码直接写 ES？

> **分析**：「架构选型类」问题，考察架构决策能力。

**回答要点**：
1. **解耦**：业务代码只管写 MySQL，不用关心搜索同步——单一职责
2. **零侵入**：不需要在 CRUD 方法里多写一行调 ES 的代码
3. **一致性保障**：Canal 基于 binlog，写入 MySQL 就一定会被同步（除非 Canal 挂了）
4. **可扩展**：如果以后要同时同步到 ES + Redis + 静态页，Canal 只需加几个 Client，业务代码不变

**对比双写方案**：
```java
// 双写：业务代码里既写 MySQL 又写 ES
@Transactional
public void updateSku(Sku sku) {
    skuMapper.updateById(sku);           // 写 MySQL
    esClient.update("shopsearch", sku);  // 写 ES  ← 耦合
    // 如果 ES 写入失败怎么办？事务一致性怎么保证？
    // 如果以后还要写缓存呢？再加一行？
}
```
```java
// Canal 方案：业务代码只写 MySQL
@Transactional
public void updateSku(Sku sku) {
    skuMapper.updateById(sku);  // 只写 MySQL
    // MySQL binlog → Canal → ES，异步自动同步
}
```

### Q2：Canal 如果挂了，ES 数据不一致怎么办？

> **分析**：「容灾设计类」问题，考察数据一致性保障方案。

**回答要点**（分层递进）：

| 层级 | 方案 | 说明 |
|------|------|------|
| 1️⃣ **增量恢复** | Canal 断点续传 | Canal 重启后从上次的位置继续消费 binlog |
| 2️⃣ **全量重建** | 定期重建 ES 索引 | 凌晨低峰期全量同步 MySQL 数据到 ES |
| 3️⃣ **兜底** | 缓存 TTL 过期 | 搜索结果缓存设置 TTL（如 60s），数据最终一致 |

**Canal 的高可用（HA）方案**：
```
Canal HA 模式：
  ZK 协调 → 多台 Canal 互备
    ├─ Leader：消费数据
    ├─ Follower：待命
    └─ Leader 挂了 → ZK 自动切到 Follower
```
```
Canal + RocketMQ 方案（更可靠）：
  MySQL → Canal → RocketMQ → Canal Client → ES
              ↑
          加一层 MQ 缓冲
          → Canal 挂了消息不丢
          → MQ 可重复消费
          → 更可靠但增加延迟
```

**面试话术**：
> "本项目目前是 Canal 直连 Feign 调 ES，架构简洁但确实存在 Canal 挂掉导致数据不一致的风险。更完善的方案是中间加一层 **RocketMQ**——Canal 把变更事件发到 MQ，ES 消费端从 MQ 拉取。这样 Canal 短暂不可用时，消息在 MQ 里堆积，恢复后继续消费，数据不丢。代价是链路多了一层，延迟稍高。"

### Q3：ES 索引怎么重建？平滑切换怎么做？

> **分析**：「运维实操类」问题，考察 ES 索引管理经验。

**回答要点**：

```json
// 1️⃣ 创建新索引（带新 mapping / 新分词器）
PUT /shopsearch_v2
{
  "settings": { "number_of_shards": 3 },
  "mappings": { ... }
}

// 2️⃣ 全量同步数据（从 MySQL 或旧索引）
POST /_reindex
{
  "source": { "index": "shopsearch_v1" },
  "dest":   { "index": "shopsearch_v2" }
}

// 3️⃣ 别名切换（原子操作，应用无感）
POST /_aliases
{
  "actions": [
    { "remove": { "index": "shopsearch_v1", "alias": "shopsearch" }},
    { "add":    { "index": "shopsearch_v2", "alias": "shopsearch" }}
  ]
}

// 之后应用代码用 shopsearch 别名查询 → 自动指向新索引 ✅
```

**要点**：
- 应用代码里用的是**别名**（alias），不是索引真名
- 别名切换是**原子操作**，切换期间查询不中断
- `_reindex` 可以在线执行，不影响旧索引查询

### Q4：你们用 ES 做商品搜索，为什么不用 Redis 搜索？

> **分析**：「技术选型类」问题，考察对不同中间件的理解。

**对比**：

| 能力 | ES | Redis |
|------|-----|-------|
| 全文搜索 | ✅ 倒排索引，分词搜索 | ❌ 不支持（只有 key-value / 简单结构） |
| 多条件组合过滤 | ✅ bool 查询（term + range + match） | ❌ 复杂 |
| 相关性排序 | ✅ BM25 打分 | ❌ |
| 高亮 | ✅ 内置高亮 | ❌ |
| 聚合分析 | ✅ 强大 | ❌ 弱 |
| 性能（简单查询） | 毫秒级 | 微秒级（更快） |
| 内存占用 | 高（索引体积大） | 低（纯内存） |

**结论**：ES 是搜索引擎，Redis 是缓存——**术业有专攻**。商品搜索需要分词、排序、过滤、高亮，这是 ES 的专长。Redis 更适合做**搜索结果缓存**而非搜索引擎本身。

### Q5：你了解 ES 的写一致性吗？与 MySQL 有什么区别？

> **分析**：「原理深度类」问题，考察对 ES 底层机制的理解。

| 对比项 | MySQL | ES |
|--------|-------|-----|
| **ACID 事务** | ✅ 支持（InnoDB） | ❌ 不支持（单文档操作原子） |
| **写入确认** | `COMMIT` 后持久化 | `refresh` 后可搜（默认 1s） |
| **断电恢复** | redo log + undo log | translog + segment |
| **一致性级别** | 强一致性 | **最终一致性** |

ES 的写入流程：
```
写入请求 → Primary Shard → 写入 Buffer + translog → 转发 Replica
                                                         ↓
                              等待所有 Replica 确认（wait_for_active_shards）
                                                         ↓
                              返回成功 → refresh 后可见（默认 1s）
```

**面试话术**：
> "ES 不保证强一致性，它是最终一致性模型。写入到可见之间有 1s 的窗口（refresh interval），这意味着 ES 更适合'写后立刻搜可能搜不到也 OK'的场景，比如商品搜索。如果业务要求写入立刻可读（如库存扣减），那应该用 MySQL 而不是 ES。"

---

## 面试话术

### 场景一：为什么用 ES 做搜索？

> **面试官**：你们的商品搜索为什么用 Elasticsearch？

**回答要点**：
1. **性能**：倒排索引实现分词毫秒级搜索，百万数据 < 50ms
2. **功能**：支持 bool 组合查询（关键词 + 分类 + 品牌 + 价格区间），高亮，排序
3. **对比 MySQL**：`LIKE '%手机%'` 全表扫描不可用
4. **对比 Redis**：Redis 不支持全文搜索和复杂过滤
5. **本项目链路**：Canal 监听 MySQL binlog → Feign 调 Search 服务 → 写入 ES

### 场景二：ES 性能优化经验？

> **面试官**：你们遇到过 ES 性能问题吗？怎么优化的？

**回答要点**（STAR 法则）：

| 维度 | 内容 |
|------|------|
| **Situation** | ES 使用初期，大规模数据写入时查询延迟升高 |
| **Task** | 优化查询性能，保证搜索毫秒级响应 |
| **Action** | ① 分页用 search_after 替代深度 from+size；② Filter 条件用 bool/filter 代替 bool/must，利用缓存；③ 控制 _source 返回字段；④ 合理设置分片数 |
| **Result** | 查询延迟稳定在 50ms 以内 |

### 场景三：ES 和 MySQL 怎么配合？

> **面试官**：你们 ES 和 MySQL 是怎么配合的？为什么两者都要？

**回答架构**：
```
MySQL：数据主库（写）
  ├─ 事务性写入（增删改）
  ├─ 强一致性保证
  └─ 基础查询（按 ID、按精确条件）

ES：搜索引擎（读）
  ├─ 分词全文搜索
  ├─ 多条件组合过滤
  └─ 索引规模可水平扩展

Canal：桥梁
  └─ 监听 MySQL binlog → 实时同步到 ES
```

**一句话总结**：**MySQL 负责存和算准，ES 负责搜和查得快**。

---

## 常见 Bug / 踩坑

### 1. 中文搜索不出结果

**现象**：输入中文关键词搜索，结果为空或结果不准确。

**原因**：ES 默认 Standard 分词器对中文按字分词（"手机"变成"手""机"）。

**解决**：
```json
// 安装 IK 分词器插件
bin/elasticsearch-plugin install https://.../analysis-ik.zip

// 创建索引时指定 analyzer
PUT /shopsearch
{
  "settings": {
    "analysis": {
      "analyzer": {
        "default": {
          "type": "ik_max_word"    // 索引用细粒度
        },
        "default_search": {
          "type": "ik_smart"       // 搜索用粗粒度
        }
      }
    }
  }
}
```

### 2. 搜索结果与 MySQL 不一致

**现象**：MySQL 数据已更新，但 ES 搜索结果还是旧数据。

**原因链条排查**：
```
MySQL 更新 ✅
  → binlog 生成 ✅（check MySQL binlog-format=ROW）
    → Canal 消费 ✅（check Canal 日志，看消费位点）
      → Feign 调用 Search 服务 ✅（check Feign 日志，有无超时/报错）
        → ES 写入 ✅（check ES 日志，看 refresh 是否正常）
          → 搜索结果 ✅（check ES 数据，GET /shopsearch/_search）
```

**常见原因**：
- Canal 挂了 → 重启后断点续传
- Feign 超时 → 延长 readTimeout
- ES 写入 pending → 检查 ES 集群健康状态

### 3. ES 集群变红（red）

**现象**：`GET /_cluster/health` 返回 `status: red`（主分片未分配）。

**原因**：节点宕机导致 Primary Shard 丢失，或磁盘空间满。

**解决**：
```
# 查看未分配分片原因
GET /_cluster/allocation/explain

# 常见处理：
# 1. 重启宕机节点
# 2. 释放磁盘空间（ES 保留 20% 水位线）
# 3. 减少副本数（Reroute 手动分配）
```

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[06-其他杂项/CDC（Canal）详解]] — ES 的数据来源（Canal 监听 binlog 同步）
- [[06-其他杂项/Feign详解]] — Canal Client 通过 Feign 调用 Search 服务
- [[分布式事务 Seata]] — Canal 消费端的数据一致性保证

> 📎 **反链**：本文件是 [[06-其他杂项/面试分析报告.md|面试分析报告]] 中"搜索引擎"技术点的详解延伸。报告中搜索模块（3.1 商品搜索模块、难点四）和知识盲区（ES 倒排索引、分词器、写入延迟）可参照本文理解。
