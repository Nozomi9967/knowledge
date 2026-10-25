---
tags:
  - redis
  - 缓存策略
  - Read Through
  - Write Through
  - Write Back
  - 面试
created: 2026-07-31
updated: 2026-07-31
source: [[Redis八股模拟卷——过期删除·内存淘汰·缓存设计·实战]]
---

# Read/Write Through 和 Write Back 深度拆解

> 核心判断：Redis + MySQL 场景很少用这两种策略，因为 **Redis 本身只是纯内存存储，不提供自动读写 DB 的能力**。

---

## 一、四种策略的职责边界

理解这个问题，关键在于区分"**谁来负责协调缓存与数据库**"。

| 策略 | 谁负责读写 DB | 应用层感知 DB？ |
|:----:|:------------:|:------------:|
| **Cache Aside（旁路缓存）** | 应用层自己 | 是，应用直接读写 DB |
| **Read/Write Through（读写穿透）** | 缓存层自身 | 否，应用只跟缓存交互 |
| **Write Back / Write Behind（异步写回）** | 缓存层自身（后台异步） | 否，应用只写缓存 |

后三种策略的共同特征——**缓存组件本身要承担与数据库交互的职责**。而这恰恰是 Redis 的短板。

---

## 二、为什么 Redis + MySQL 很少用

### 1. Redis 不提供"读写穿透"所需的 DB 同步能力

Read/Write Through 的本质是把 Cache Aside 中"读 Miss → 读 DB → 回填缓存"的逻辑下沉到缓存服务内部，由缓存服务自己来加载和回写。

Redis 没有这个能力——它是纯粹的"内存数据结构存储"，只负责存取，不内置"把数据写回关系型数据库"或"缓存未命中时自动从 MySQL 加载并回填"的功能。要实现就得在业务侧或中间件层自己包一层，开发成本高，收益却不明显——应用层自己写这几行代码并不复杂，反而更灵活、更可控。

### 2. 真正原生支持这些模式的是"专用缓存中间件"

Read/Write Through 和 Write Behind 的典型落地场景，是那些设计之初就内置了 `CacheStore` 接口的专用分布式缓存：

- **Oracle Coherence**：提供 Read-Through、Write-Through、Write-Behind 全套能力，支持配置 write-behind 队列和刷盘延迟
- **Apache Ignite、Hazelcast**：内置数据网格能力，支持缓存节点直接与底层存储交互
- **NCache、AppFabric Caching**：同样支持 read-through/write-behind provider

在这些中间件里，缓存节点是"有数据库感知能力"的智能节点，天然能扮演代理的角色。Redis 想达到同等效果，必须由使用者自行在外层封装——相当于"用 Redis 模拟一个 Coherence"，工程上并不划算。

### 3. 性能与一致性上的权衡不划算

即便强行在 Redis 上实现，两种策略相比 Cache Aside 也没有压倒性优势：

| 策略 | 写延迟 | 丢数据风险 | 实现复杂度 | 一致性 | 典型落地 |
|:----:|:----:|:----:|:----:|:----:|:----:|
| Cache Aside | 低 | 低 | 低 | 最终一致 | Redis + MySQL 通用 Web 应用 |
| Read/Write Through | 高（同步写 DB） | 极低 | 高（需专用中间件） | 强一致 | Coherence/Ignite、Guava LoadingCache |
| Write Back | 极低 | 中（宕机丢数据） | 中 | 最终一致 | InnoDB Buffer Pool、Page Cache、点赞计数 |

- **Write Through 是同步写 DB**，每次写操作都要等 MySQL 写成功才返回，写延迟被 DB 拖住，相比 Cache Aside 没有性能优势
- **Write Back 虽然写性能极高，但数据丢失风险大**：缓存宕机而异步刷盘未完成，这部分更新就永久丢失了

**Redis 是应用层纯内存，没有硬件保护。** CPU 缓存和文件系统有硬件保障（NVDIMM、电池保护、内核强制刷盘），但 Redis 没有这些保护——宕机时内存中的数据全部丢失。

---

## 三、Read/Write Through 适合什么场景

Read/Write Through 的核心价值是"**对应用透明**"——应用代码只与缓存层交互，无需感知 DB 的存在。

**专用缓存中间件 + 强一致内部系统**。当项目使用 Oracle Coherence、Apache Ignite、Hazelcast 这类内置 `CacheStore` 的数据网格时，Read/Write Through 是开箱即用的。

**本地缓存场景**。Guava Cache 的 `LoadingCache`、Caffeine 的 `AsyncLoadingCache` 都允许你提供一个 `CacheLoader`，缓存未命中时由缓存组件自动调用 loader 从数据源加载并回填——这正是 Read Through 的标准实现。本地缓存与数据源在同进程，封装 loader 成本低、延迟低，非常适合读多写少的场景。

**银行类应用**。写操作不算高频、但对实时一致性要求极高时，Write Through 能保证缓存与 DB 同步更新，从缓存读和从 DB 读永远一致。代价是写延迟较高，只适合写量适中的场景。

---

## 四、Write Back 适合什么场景

Write Back 的精髓是"只更新缓存即返回，后台异步批量刷盘"，写性能极高，代价是丢数据风险和实现复杂度。

**系统级缓存机制**。MySQL 的 InnoDB Buffer Pool——数据修改先在内存 Buffer Pool 完成，再由后台线程异步刷写到磁盘；操作系统的 Page Cache——文件写入先到内存，再由操作系统异步刷盘。这些场景下缓存与持久层是紧耦合、同进程的，实现 Write Back 的成本可控。

**高频计数场景**。文章浏览量、帖子点赞数、视频播放量这类指标，写入极其频繁（每秒可能成千上万次），但对一致性要求不高。典型做法是先在 Redis 中快速累加，再通过定时任务或消息队列异步批量同步回 MySQL。

**实时日志、PV/UV 统计**。同样属于"写量大、可容忍最终一致"的范畴，先在缓存堆积再批量落库。

---

## 五、面试话术

> Read/Write Through 和 Write Back 本质上是"缓存具备数据库感知能力"时才划算的模式。Redis 只是一个纯内存 KV 存储，不内置 DB 读写能力，要实现在业务层自己封装，成本高收益低，所以 Redis + MySQL 场景普遍选择 Cache Aside。Read/Write Through 适合专用缓存中间件（Coherence/Ignite）和本地缓存（Guava/Caffeine）；Write Back 适合系统级缓存（InnoDB Buffer Pool）和高频计数场景。

---

## 关联知识点

- [[Redis八股模拟卷——过期删除·内存淘汰·缓存设计·实战]] — 本题来源（第 8 题）
- [[Cache Aside 写策略满分拆解]] — Cache Aside 写策略的严谨时序分析
- [[什么是缓存雪崩、击穿、穿透？]] — 三大缓存问题及对策
- [[数据库和缓存如何保证一致性？]] — Cache Aside 策略基础介绍
