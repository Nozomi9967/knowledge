---
tags:
  - java
  - 缓存
  - caffeine
  - 本地缓存
created: 2026-06-28
updated: 2026-06-28
source: https://github.com/ben-manes/caffeine
---
ka·feen
# Caffeine 详解 — Java 高性能本地缓存库

> Caffeine 是 Java 8 后最流行的本地缓存库，基于 **W-TinyLFU** 淘汰算法和 **StripedBuffer** 无锁读路径设计，在"读多写少、有界、线程安全"场景下被公认为 Guava Cache 的替代者。Spring Boot 2.x+ 已将 Caffeine 设为默认的本地缓存实现（替换 Guava）。

---

## 核心特性

| 特性 | 说明 |
|------|------|
| **自动淘汰** | 容量上限 + 多种过期策略（写入后过期、访问后过期、自定义权重） |
| **线程安全** | 读操作几乎无锁，写操作高效异步 |
| **高性能淘汰** | W-TinyLFU 算法，命中率优于纯 LRU |
| **统计监控** | 内置 `recordStats()`，记录命中率、加载时间、淘汰数 |
| **异步加载** | `AsyncCache` 支持 `CompletableFuture` 异步加载 |
| **淘汰监听** | 可注册 `evictionListener` 感知条目被淘汰 |
| **Spring 集成** | 作为 `CacheManager` 的一等公民，一行注解即可使用 |

---

## W-TinyLFU 淘汰算法

Caffeine 区别于 Guava Cache 最核心的设计。

### TinyLFU（Tiny Least Frequently Used）

- 用 **Count-Min Sketch** 概率数据结构记录每个 key 的访问频率
- 维护一个固定的 d×w 二维计数器数组（几 KB），所有 key 通过哈希映射到同一些计数器槽位，多 key 共享计数空间。相比传统 LFU 为每个 key 申请独立计数器，计数内存开销从 O(N) 降为 O(1)
- 相比 LFU 的每个 key 独立计数器，TinyLFU 大幅降低内存开销

### LRU 窗口 + 分段设计

W-TinyLFU = **Window**（窗口区）+ **TinyLFU**（主区）+ 淘汰门限

```
访问事件
   │
   ▼
┌──────────┐  窗口区满   ┌──────────────┐
│ Window   │ ─────────→ │ Main Probe   │ （TinyLFU 维护）
│ (LRU)   │             │              │
│ 占 1%    │ ◄───────── │ 占 99%       │
└──────────┘  淘汰回退   └──────────────┘
                              │
                              ▼
                        淘汰候选者 → Count-Min Sketch 判定
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
                频率更高保留      频率更低淘汰
```

**设计意图**：
- **Window LRU（1%）**：给新加入的 key 一个"试用期"，避免纯粹的 LFU 让突发热点数据还没积累频率就被淘汰
- **Main Probe（99%）**：用 Count-Min Sketch 维护长期频率统计，淘汰访问次数最少的 key
- 新 key 先进入 Window，满后挤入 Main Probe，再由 TinyLFU 决策是否真的留下

> **面试话术**：W-TinyLFU 解决了 LFU 的两个痛点——==新数据刚加入时频率低容易被淘汰（冷启动问题）==，以及传统 LFU 每个 key 独立计数器内存开销大。Window 窗口给了新数据"缓冲期"，Count-Min Sketch 让频率统计的内存成本从 O(N) 降到 O(1)。

---
## Caffeine StripedBuffer 读路径无锁设计

### 核心问题：为什么普通缓存读要加锁

传统 LRU 缓存每次读都要更新"最近访问"顺序，比如把链表节点移到头部。这个操作需要加锁，否则多线程并发读会把双向链表指针搞坏。读越频繁，锁竞争越激烈，最终多线程反而比单线程慢。

Caffeine 的解法是把"读命中"和"记录读事件"分离：

- 读命中 → 直接查 `ConcurrentHashMap`，无锁
- 记录读事件 → 异步写到 RingBuffer，不影响返回速度

---

### Caffeine 的底层存储就是 ConcurrentHashMap

Caffeine 不是独立于 Map 之外的东西，它是在 `ConcurrentHashMap` 之上加了一层淘汰策略管理（W-TinyLFU + LRU 顺序 + 容量控制）。

```
Caffeine.getIfPresent(key)
    └─ 内部：data.get(key)   ← data 就是一个 ConcurrentHashMap
```

|层|是什么|职责|
|---|---|---|
|`ConcurrentHashMap`|实际存数据|key → value 的快速查找|
|StripedBuffer + Drain|Caffeine 加的|记录访问频率，决定淘汰谁|

---

### RingBuffer：首尾相接的定长数组

本质是一个定长数组，用两个指针 `head`（消费位置）和 `tail`（写入位置）模拟队列，到头了就绕回 0。

写入：`buffer[tail] = event; tail = (tail + 1) % size`

不需要 `new` 对象、不需要移动元素，纯数组下标操作，极快。Drain 线程从 head 消费，消费完 `head++`。

---

### Striped：多备一份，分散竞争

如果只有一个 RingBuffer，所有线程都在抢同一个 `tail` 的 CAS，失败率高。

Striped 的做法是准备多个 RingBuffer（比如 4 个），按线程 ID 分流：

```
线程 A → threadId % 4 = 0 → 写 Buffer[0]
线程 B → threadId % 4 = 1 → 写 Buffer[1]
线程 C → threadId % 4 = 2 → 写 Buffer[2]
线程 D → threadId % 4 = 3 → 写 Buffer[3]
```

原来 100 个线程抢 1 个 CAS，现在变成 4 组各自内部竞争，每组平均 25 个线程，CAS 成功率大幅提升。**用空间换竞争**。

所以 `StripedBuffer` = Striped（多个）× RingBuffer（环形数组）。

---

### RingBuffer 写入有什么用

记录"我刚才读了这个 key"。Caffeine 有容量上限，装满了要淘汰数据，淘汰谁取决于每个 key 的访问频率和最近访问时间，这些数据就来自 RingBuffer 里积累的读事件。

```
读了 key="user:123"
    ↓
往 RingBuffer 写一条：[READ, "user:123"]
    ↓
后台 Drain 线程消费
    ↓
更新 "user:123" 的访问频率计数 + LRU 顺序
    ↓
缓存快满时：拿这些数据决定淘汰谁
```

RingBuffer 是**访问统计的暂存区**，存的是"谁被读过"这件元信息，专门喂给淘汰算法（W-TinyLFU）用。

---

### 读路径完整流程

```
调用方：cache.getIfPresent(key)
    │
    ├─ map.get(key)  ← ConcurrentHashMap，无锁，直接返回 value
    │
    ├─ threadId % stripes → 选一个 RingBuffer
    │       ├─ CAS 写入成功 → 事件入队
    │       └─ CAS 失败（竞争）→ 直接丢弃，不重试，不等待
    │
    └─ 返回给调用方（整个过程调用方线程不持锁）

后台 Drain 线程（异步）：
    ├─ Buffer 达阈值 或 定时触发
    ├─ 批量消费所有 Buffer 中的事件
    └─ 更新 LRU 顺序 + W-TinyLFU 频率统计 + 淘汰决策
```

---

### 三个关键设计要点

**① CAS 失败直接丢弃，不重试**

最反直觉的地方。丢失的只是"我刚才读了这个 key"这条统计，不是数据本身。最坏情况是该 key 热度被低估，淘汰决策稍微不准，但缓存数据永远正确，调用方完全无感知。

**② 后台批量消费比每次读都更新高效得多**

每次读立刻更新 LRU 链表需要加锁，性能差。积攒一批之后由单一后台线程处理，既避免了锁竞争，又减少了更新次数。

**③ "读无锁"的准确含义**

不是整个系统没有锁，而是**调用方线程的关键路径上没有锁**。锁和慢操作都被推到了异步后台，对调用方完全透明。

---

## 五种缓存加载模式

### 1. Cache（手动加载）

```java
Cache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .build();

cache.put("key", user);
User u = cache.getIfPresent("key");
```

### 2. LoadingCache（同步自动加载）

```java
LoadingCache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .build(key -> userDao.findById(key));  // 缓存未命中时自动调用

User u = cache.get("key");  // 可能阻塞等待加载
```

### 3. AsyncCache（异步手动加载）

```java
AsyncCache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .buildAsync();

CompletableFuture<User> future = cache.get("key", (k, e) ->
    userDao.findByIdAsync(k));
```

### 4. AsyncLoadingCache（异步自动加载）

```java
AsyncLoadingCache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .buildAsync(key -> userDao.findByIdAsync(key));
```

### 5. 针对"最近访问有序"场景

建立有界缓存，配合 `asMap().keySet()` 按访问顺序迭代：

```java
Cache<Long, Long> cache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(Duration.ofHours(1))
    .recordStats()
    .build();

// 写
cache.put(userId, userId);

// 读有序列表（Caffeine 内部按 access order 维护）
List<Long> recentUsers = cache.asMap()
    .keySet()
    .stream()
    .limit(1000)
    .collect(Collectors.toList());
```

---

## 淘汰策略对比

| 策略          | 方法                                  | 说明                        |
| ----------- | ----------------------------------- | ------------------------- |
| **基于容量**    | `maximumSize(long)`                 | 超过容量时淘汰最久未访问的条目           |
| **基于权重**    | `maximumWeight(long)` + `weigher()` | 按自定义权重计算，适合 value 大小不一的场景 |
| **写入后过期**   | `expireAfterWrite(Duration)`        | 写入后固定时间过期，适用于不变数据         |
| **访问后过期**   | `expireAfterAccess(Duration)`       | 一段时间未被访问则过期，适用于冷数据        |
| **自定义过期**   | `expireAfter(Expiry)`               | 每条缓存独立过期时间，最灵活            |
| **软引用/弱引用** | `weakKeys()` / `softValues()`       | 配合 GC 回收，适用于内存敏感场景        |

---

## Caffeine vs Guava Cache

| 维度 | Caffeine | Guava Cache |
|------|----------|-------------|
| **淘汰算法** | W-TinyLFU（更高命中率） | LRU（简单但命中率低） |
| **读性能** | StripedBuffer 无锁读 | 读时同步更新访问顺序，有竞争 |
| **统计** | recordStats() 内置 | 类似实现 |
| **异步加载** | AsyncCache / AsyncLoadingCache | 无 |
| **淘汰监听** | evictionListener | RemovalListener（功能弱） |
| **内存效率** | Count-Min Sketch 固定小内存，O(1) | LRU 链表逐条维护访问顺序，O(N) |
| **Spring Boot 默认** | ✅ 是（Spring 5 / Boot 2） | ❌ 已弃用 |

> **一句话总结**：Guava Cache 是"够用的本地缓存"；Caffeine 是"把本地缓存做到极致的工程实现"。如果项目不在老 Guava 版本上，直接上 Caffeine。

---

## Caffeine vs Redis（本地缓存 vs 分布式缓存）

| 维度 | Caffeine（本地） | Redis（分布式） |
|------|-----------------|-----------------|
| **存储位置** | JVM 堆内 | 独立进程（内存） |
| **速度** | 纳秒级，无网络开销 | 微秒级，有网络 RTT |
| **容量** | 受 JVM 堆大小限制 | 独立内存池，可分布式扩容 |
| **一致性** | 各节点不一致（本地缓存） | 全局一致（中心化） |
| **持久化** | 无 | RDB/AOF |
| **适用** | 单机热点缓存、不可变元数据 | 共享状态、跨服务数据、分布式锁 |

**典型分层架构**：Nginx（磁盘缓存，挡 80% 流量）→ Redis（分布式缓存，挡 15%）→ Caffeine（本地热点缓存，处理 <5%）

---

## Spring Boot 集成

### 配置

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterAccess=3600s
```

### 使用

```java
@Service
public class UserService {
    @Cacheable(value = "users", key = "#userId")
    public User getUser(Long userId) {
        return userDao.findById(userId);
    }
}
```

---

## 面试高频题

### Q1：Caffeine 为什么比 Guava Cache 快？

主要有两个原因。

第一个是淘汰算法不一样。Guava 用的是纯 LRU，而 Caffeine 用的是 W-TinyLFU。这个算法本质上是用频率来淘汰——访问少的先走。但它比传统 LFU 高明在哪呢？它搞了一个 Window 窗口，占缓存 1% 的空间，新 key 先进来在这个窗口里待一会儿，给它一个"试用期"。不然的话，一个新 key 刚进来频率是零，LFU 一看直接把它淘汰了，冷启动问题就来了。Window 用 LRU 管，试用期过了再交给主区的 TinyLFU 去按频率决策。再加上频率统计用的是 Count-Min Sketch，不是每个 key 单独搞一个计数器，而是用几 KB 的固定二维数组，通过哈希映射共享计数器，内存开销从 O(N) 降到 O(1)。所以整体命中率比纯 LRU 高不少。

第二个原因是读路径的设计。Guava 每次读命中都要去更新访问顺序，这个操作要加锁，线程一多锁竞争就很严重。Caffeine 把"读命中"和"记录读事件"拆开了——读命中直接走 ConcurrentHashMap，完全无锁返回；至于"我刚才读了这个 key"这件事，异步写到 RingBuffer 里，后台 Drain 线程慢慢消费、慢慢更新频率统计。所以读线程在整个关键路径上根本不碰锁。

---

### Q2：Caffeine 的读操作真的完全无锁吗？

严格来说不是"完全无锁"，但实际使用中你基本感觉不到锁的存在。

具体来说：读 ConcurrentHashMap 本身是无锁的；往 RingBuffer 写事件的时候用的是一个 CAS 操作，失败了直接丢弃这条统计，不会 spin 等待；真正需要持锁的只有写操作（put、evict）和后台 Drain 线程更新内部链表的时候。所以读线程几乎没有阻塞的风险，最多就是 RingBuffer 满了丢几条统计，影响的是淘汰精度，不影响数据正确性，调用方完全无感知。

---

### Q3：Caffeine 的过期时间是精确的吗？

不精确。Caffeine 用的是**惰性删除加定时清理**，没有后台线程做毫秒级扫描。

具体怎么工作的：惰性删除就是你下次 get 的时候，发现这个 entry 过期了，当场删掉返回 null；定时清理是在 Drain 线程批量消费的时候顺便把过期的清掉。所以如果你设了 `expireAfterWrite(1s)`，理论上 1 秒后过期，但你有可能在 1.1 秒之后 get 才观察到它已经没了。这个精度对于绝大多数业务场景完全够用，但如果你需要精确到毫秒级的过期控制，Caffeine 不适合你。

---

### Q4：Caffeine 适合什么场景？

首先，**读多写少**的场景是最适合的，比如配置信息、用户元数据、字典表这种读远多于写的。因为它的读路径几乎无锁，写路径才有锁，读越多越能体现优势。

其次，**有界内存**的场景，也就是你明确知道缓存不能无限膨胀、需要设 maximumSize 防止 OOM 的。

第三，**需要命中率监控**的场景，它内置了 `recordStats()`，可以直接拿到命中率、加载时间、淘汰数这些指标。

还有一类就是**正在用 Guava Cache 的项目**，Spring Boot 2 之后已经把 Caffeine 设为默认实现了，直接替换 Guava 是个零成本升级。

反过来，**不适合的场景也很明确**：写密集的——因为写操作还是要加锁的；还有就是需要强一致性的——本地缓存天然是节点级别的，各实例之间不一致，别指望用它来做分布式一致的东西。

---

## 关联知识点

- [[Java并发编程面试题]] — Caffeine 的 StripedBuffer 涉及 CAS、RingBuffer 等并发技术
- [[Java后端开发面试题汇总]] — "最近访问用户ID"技术选型题实战
- [[跨结构原子性与竞态窗口]] — Caffeine 如何通过 Buffer 异步化消除跨结构一致性难题
- [[Redisson详解]] — 分布式缓存方案对比
- [[ES详解]] — 另一类缓存/搜索场景的选型
