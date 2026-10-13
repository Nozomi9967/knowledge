---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---
# Redisson 详解 — Redis 客户端 & 分布式解决方案

> **关于名称**：Redisson 是 **Redis** + **son**（意为"Redis 之子"）的合成词，由 Redis 官方推荐的 Java 客户端之一。它不仅仅是简单的 Redis 客户端（如 Jedis/Lettuce），更是一个构建在 Redis 之上的 **内存数据网格（In-Memory Data Grid）**，提供了分布式锁、布隆过滤器、分布式集合、信号量、计数锁等 30+ 开箱即用的分布式数据结构。

## 一句话定义

> **Redisson** 是一个构建在 Redis 之上的 Java 客户端框架（Redis 官网收录的 Java 客户端之一），它将 Redis 的数据结构封装为 `java.util.concurrent` 风格的分布式 Java 对象，开箱即用地提供分布式锁、布隆过滤器、分布式集合、信号量等 30+ 分布式工具，让开发者以操作本地并发工具的方式操作分布式资源。

---

## 分布式锁解决了什么问题？

在分布式系统中，单机版的 `synchronized` / `ReentrantLock` 无法跨进程工作，需要用分布式锁来协调多节点对共享资源的互斥访问。

### ❌ 原生方案（手写 SETNX + Lua）

```java
// 1. 加锁（SETNX + EXPIRE 原子操作）
String uuid = UUID.randomUUID().toString();
Boolean success = redisTemplate.opsForValue()
    .setIfAbsent("lock:sku:123", uuid, 30, TimeUnit.SECONDS);

// 2. 业务处理
if (success) {
    try {
        // ... 执行业务
    } finally {
        // 3. 释放锁（Lua 脚本保证原子性）
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
            Arrays.asList("lock:sku:123"), uuid);
    }
}
```

问题：
- **代码繁琐**：每个加锁/解锁都得写 Lua 脚本
- **无自动续期**：业务超时锁就过期，需手动实现看门狗
- **无阻塞等待**：`SETNX` 失败立即返回，需自行轮询
- **无锁粒度控制**：不支持可重入、读写锁等高级语义

### ✅ Redisson 方式

```java
@Autowired
private RedissonClient redissonClient;

public void secKill(Long skuId) {
    // 直接使用分布式锁（自动续期、可重入、支持阻塞等待）
    RLock lock = redissonClient.getLock("lock:sku:" + skuId);
    lock.lock(10, TimeUnit.SECONDS);  // 最长等待 10s
    try {
        // ... 秒杀业务
    } finally {
        lock.unlock();
    }
}
```

> [!warning] 上面这段注释有个语义错误（2026-09-04 标注，原文未改）
> `lock.lock(10, TimeUnit.SECONDS)` 的 10 秒是 **leaseTime（锁持有时间上限，到期自动释放）**，不是「最长等待 10s」。带单个 time 参数的 `lock()` 会一直阻塞直到拿到锁，拿到后 10 秒强制释放，且**看门狗不启动**——与下文「常见 Bug #2」的结论一致。
> 想同时控制等待时间和持有时间要用三参重载：`tryLock(waitTime, leaseTime, unit)`。面试时把 leaseTime 说成"等待时间"会被当场抓。

---

## Redisson 核心能力

### 分布式数据结构一览

| 数据结构 | Redisson 对应类 | 类比 Java 本地 | 本项目使用 |
|---------|----------------|---------------|-----------|
| 分布式锁 | `RLock` | `ReentrantLock` | ✅ 秒杀防超卖 |
| 布隆过滤器 | `RBloomFilter` | `BloomFilter` (Guava) | ✅ 网关 URI 校验 |
| 分布式集合 | `RMap` / `RSet` / `RList` | `Map` / `Set` / `List` | — |
| 信号量 | `RSemaphore` | `Semaphore` | — |
| 计数锁 | `RCountDownLatch` | `CountDownLatch` | — |
| 分布式队列 | `RQueue` / `RBlockingQueue` | `Queue` / `BlockingQueue` | — |
| 分布式原子变量 | `RAtomicLong` / `RAtomicDouble` | `AtomicLong` / `AtomicDouble` | — |

---

## 分布式锁（RLock）原理

### 加锁流程

Redisson 通过 **Lua 脚本** 保证加锁原子性：

```lua
-- 加锁 Lua 脚本（简化）
if redis.call("exists", KEYS[1]) == 0 then
    redis.call("hset", KEYS[1], ARGV[2], 1)    -- 创建锁，hash 结构存储
    redis.call("pexpire", KEYS[1], ARGV[1])    -- 设置过期时间
    return nil
end
if redis.call("hexists", KEYS[1], ARGV[2]) == 1 then
    redis.call("hincrby", KEYS[1], ARGV[2], 1) -- 可重入：计数+1
    redis.call("pexpire", KEYS[1], ARGV[1])
    return nil
end
return redis.call("pttl", KEYS[1])             -- 返回剩余过期时间
```

> **为什么用 Hash 结构？** 支持**可重入**——同一线程多次调用 `lock()` 不会死锁：Hash 的 field 存线程 ID（标识锁的持有者），field 对应的 value 用 `HINCRBY` 记录重入次数。

### 看门狗（WatchDog）机制

**看门狗是 Redisson 最核心的面试考点。**

```
lock() 成功
   │
   ▼
启动看门狗后台线程（TimeoutTask）
   │
   ├─ 每 10 秒检查锁是否存在
   ├─ 如果锁还存在（业务未完成）→ 续期 30 秒
   │
   ▼
业务执行完毕 → unlock()
   │
   ▼
看门狗线程退出
```

**关键参数**：
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `lockWatchdogTimeout` | 30 秒 | 锁的过期时间 |
| 续期间隔 | 10 秒 | `lockWatchdogTimeout / 3`，每 10 秒检查一次 |
| 续期值 | 30 秒 | 每次续期重置为 `lockWatchdogTimeout` |

**两种使用方式对比**：

| 方式 | 代码 | 看门狗行为 |
|------|------|-----------|
| **无参 lock()** | `lock.lock()` | ✅ 启动看门狗，业务超时自动续期 |
| **指定过期时间** | `lock.lock(10, TimeUnit.SECONDS)` | ❌ 不看门狗，到期自动释放 |

> ⚠ **注意**：`lock(leaseTime, unit)` 传入过期时间后，看门狗**不会启动**，锁到期自动释放。看门狗只在无参 `lock()` 或 `tryLock()` 时生效。

### 释放锁流程

```lua
-- 释放锁 Lua 脚本（简化）
if redis.call("hexists", KEYS[1], ARGV[2]) == 0 then
    return nil
end
local counter = redis.call("hincrby", KEYS[1], ARGV[2], -1)
if counter > 0 then
    redis.call("pexpire", KEYS[1], ARGV[1])
    return 0
else
    redis.call("del", KEYS[1])
    return 1
end
```

> 同样通过 Lua 脚本保证原子性。可重入时 count-1 不减到 0 就不删除锁，只刷新过期时间。

---

## 三种锁 API 对比

| API                                  | 阻塞等待           | 中断响应    | 推荐场景                 |
| ------------------------------------ | -------------- | ------- | -------------------- |
| `lock()`                             | ✅ 阻塞直到获取锁      | ❌ 不响应中断 | 必须拿到锁的场景             |
| `lock(leaseTime, unit)`              | ✅ 阻塞直到获取锁      | ❌ 不响应中断 | 明确知道最大时长的业务          |
| `tryLock(waitTime, leaseTime, unit)` | ✅ 最多等 waitTime | ✅ 响应中断  | **推荐**：秒杀等需控制等待时间的场景 |

```java
// 推荐用法：秒杀场景
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
// 最多等 3 秒，拿到后 30 秒自动释放（不看门狗）
if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
    try {
        // 业务处理
    } finally {
        lock.unlock();
    }
} else {
    // 没抢到锁，返回"人多稍后再试"
}
```

---

## 布隆过滤器（RBloomFilter）

### 本项目中的使用

```java
// 权限服务：初始化布隆过滤器
@Autowired
private RedissonClient redissonClient;

public void initUriBloomFilter() {
    RBloomFilter<String> filters = redissonClient.getBloomFilter("UriBloomFilterArray");
    // 容量 10000，误判率 0.1%
    filters.tryInit(10000L, 0.001);
    // 加载所有合法 URI
    List<String> uris = permissionService.getAllValidUris();
    uris.forEach(filters::add);
}

// 网关：判断 URI 是否合法
public Boolean isInvalid(String uri) {
    RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter("UriBloomFilterArray");
    return !bloomFilter.contains(uri);  // false → 一定不存在，直接 403
}
```

> 💡 **布隆过滤器特性**："不存在就是不存在"，但"存在可能不存在"（误判）。  
> 本项目误判率设为 `0.001`（0.1%），容量 `10000`，在小概率误判和内存占用之间取得平衡。

### 动态更新策略

```
运营发布新商品 / 新秒杀活动
   → MySQL INSERT
     → Canal 监听 binlog
       → Feign 调用权限服务
         → 异步将新 URI 加载到 Redis BloomFilter
           → 无需重启网关，动态生效
```

---

## RedLock（红锁）算法

RedLock 是 Redis 作者 Antirez 提出的**多节点高可用分布式锁**方案，**本项目未使用**（单节点 Redisson 锁已满足需求），但面试高频。

> [!quote] Redis 八股原文
> ——Redis 主从复制模式中的数据是异步复制的，这样导致分布式锁的不可靠性。如果在 Redis 主节点获取到锁后，在没有同步到其他节点时，Redis 主节点宕机了，此时新的 Redis 主节点依然可以获取锁，所以多个应用服务就可以同时获取到锁。
>
> 为了保证集群环境下分布式锁的可靠性，Redis 官方已经设计了一个分布式锁算法 Redlock（红锁）。它是基于多个 Redis 节点的分布式锁，即使有节点发生了故障，锁变量仍然是存在的，客户端还是可以完成锁操作。官方推荐是至少部署 5 个 Redis 节点，而且都是主节点，它们之间没有任何关系，都是一个个孤立的节点。
>
> Redlock 算法的基本思路，是让客户端和多个独立的 Redis 节点依次请求申请加锁，如果客户端能够和半数以上的节点成功地完成加锁操作，那么我们就认为，客户端成功地获得分布式锁，否则加锁失败。
>
> ——引自 [[01-Redis/redis八股]]

### 解决的问题

Redis 主从架构下，异步复制导致锁丢失：

```
客户端A 在 Master 加锁成功 ✅
  → Master 宕机（还没同步到 Slave）
    → Slave 升为 Master（没有这把锁的记录）
      → 客户端B 在新 Master 上加锁成功 ✅
        → 两个客户端同时持有锁 → 互斥性被打破 ❌
```

### 核心思想：多数派

部署 5 个独立 Redis 节点（非主从），加锁需要 **超过半数（≥3）** 节点成功：

```
向 5 个节点依次发送 SET NX EX
  → 统计成功数量
    → ≥3 个成功 → 加锁成功
    → <3 个成功 → 全部释放
```

| 节点数 | 容错数 | 说明              |
| --- | --- | --------------- |
| 3   | 1   | 可坏 1 个          |
| 5   | 2   | 推荐，可在坏 2 个时继续工作 |
| 7   | 3   | 最多坏 3 个         |


### RedLock 争议

| 阵营     | 观点                                        | 代表人物                      |
| ------ | ----------------------------------------- | ------------------------- |
| **反对** | 依赖系统时钟（时钟跳跃导致锁判断错误）、GC 暂停时锁已过期但仍执行        | Martin Kleppmann（DDIA 作者） |
| **支持** | 时钟漂移在合理范围内可用；fencing token 本身也需要资源端配合，非银弹 | Antirez（Redis 作者）         |

**生产建议**：
- 对锁正确性要求极高（资金、库存强一致）→ 使用 ZooKeeper / etcd 分布式锁
- 用于防重复、去重、限流等"尽力而为"场景 → Redisson 单节点锁或 RedLock 已足够

---

## 本项目中的 Redisson 使用

### 1. 集群配置

```java
@Configuration
public class RedissLock {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Redis 集群模式（3主3从，16,384个哈希槽）
        config.useClusterServers()
            .addNodeAddress(
                "redis://192.168.100.130:7001",
                "redis://192.168.100.130:7002",
                "redis://192.168.100.130:7003",
                "redis://192.168.100.130:7004",
                "redis://192.168.100.130:7005",
                "redis://192.168.100.130:7006")
            .setMasterConnectionPoolSize(64)   // 主节点连接池
            .setMasterConnectionMinimumIdleSize(24);  // 最小空闲连接
        return Redisson.create(config);
    }
}
```

### 2. 秒杀防超卖（分布式锁兜底）

```java
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
lock.lock();
try {
    // 查 Redis 库存
    Long stock = redisTemplate.opsForValue().decrement("stock:sku:" + skuId);
    if (stock < 0) {
        // 已售罄
        return "已售罄";
    }
    // 创建订单 → RocketMQ 排队
    orderService.createOrder(skuId, userId);
} finally {
    lock.unlock();
}
```

### 3. 网关布隆过滤器（防缓存穿透）

```java
// 启动时初始化
RBloomFilter<String> filters = redissonClient.getBloomFilter("UriBloomFilterArray");
filters.tryInit(10000L, 0.001);

// 请求过滤
if (!filters.contains(uri)) {
    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "无效路径"));
}
```

---

## 面试高频考点

### Q1：Redisson 分布式锁的实现原理？看门狗如何工作？

**回答要点**：

```
实现层次：
  ├─ Lua 脚本保证加锁原子性（SET NX EX + 可重入计数）
  ├─ 看门狗后台线程（每 10s 续期 30s）
  └─ Lua 脚本保证释放锁原子性（判断 value 再 DEL）

看门狗机制（核心是"一个会自我续命的定时任务"，不是"到点检查"这么简单）：
  lock() 无参 → 发给 Lua 的 pexpire 参数 = internalLockLeaseTime = 30000ms
                （Redis 里 TTL 就是实打实 30s；-1 只是 Java 侧"用户没指定 lease"的哨兵，不会进 Redis）
    → 加锁成功 → 注册一个后台续期任务（scheduleExpirationRenewal）
      → 每次任务触发，执行一段 Lua「续期」脚本：
          hexists(lockKey, threadId) == 1          -- 锁还在不在自己手里？
            → 是 → pexpire lockKey 30000           -- 把 30s 过期时间重新推回去
                   → 续期成功 → 把自己重新排到 lockWatchdogTimeout/3（10s）之后
            → 否 → return 0 → 停止续期，任务不再排下一次
      → 滚动续期：10s 续一次 30s，TTL 一直在 [20s,30s] 之间滚动，永远掉不到 0
  → unlock() 时 cancel() 这个续期任务 → 看门狗退出
```

> [!warning] 源码勘误（2026-09-04 · 核对 Redisson master 源码 + 官方 Javadoc，原文其余部分未改）
>
> **① Redis 里锁的 TTL 就是 30 秒，不是 -1。** 上面这段原先写「底层把过期时间设成 -1，TTL 不固定」，已就地更正。真实调用链：
> ```java
> // RedissonLock.java:214-224 —— ARGV[1] 直接进 pexpire
> "redis.call('hincrby', KEYS[1], ARGV[2], 1); redis.call('pexpire', KEYS[1], ARGV[1]);"
> // 无参 lock() 时：tryAcquireAsync0(-1, -1, ...) → leaseTime<=0
>   → tryLockInnerAsync(waitTime, internalLockLeaseTime, MILLISECONDS, ...)
>   → ARGV[1] = internalLockLeaseTime = lockWatchdogTimeout = 30000
> ```
> `-1` 是 **Java 方法内部表示"用户没指定 leaseTime"的哨兵值**，在进 Lua 之前就被换成 30000 了，Redis 侧永远看不到它。说法应是"锁先进 30 秒，再被每 10 秒推回 30 秒"，而不是"TTL 不固定"。
>
> **② `lock(10, TimeUnit.SECONDS)` 的 10 是持有时间，等待时间仍无限。** Javadoc：*"leaseTime the maximum time to hold the lock after it's acquisition"*；源码 `lockAsync` 里传的是 `tryAcquireAsync0(-1, leaseTime, unit, threadId)`，第一个 `-1` 才是 waitTime。要同时控制二者只能用 `tryLock(waitTime, leaseTime, unit)`。
>
> **③ 看门狗确实不启动，但原因是一行分支**，可以背下来当"读过源码"的证据：
> ```java
> // RedissonLock.java:173-178
> if (acquired) {
>     if (leaseTime > 0) { internalLockLeaseTime = unit.toMillis(leaseTime); }  // 记死，不续
>     else               { scheduleExpirationRenewal(threadId); }               // ← 只这条起看门狗
> }
> ```
>
> **④ 看门狗不跑在 Redis 服务端。** 它是客户端 JVM 里的哈希轮：`RedissonBaseLock.scheduleExpirationRenewal` → `LockRenewalScheduler.renewLock` → `RenewalTask`，间隔取 `getLockWatchdogTimeout() / 3` = **10 秒**（`RenewalTask.java:68`）；`Config.java:82` 里 `lockWatchdogTimeout = 30 * 1000`。Redis 侧只有那把 Hash 锁和它的 TTL。
>
> ⚠️ 另两处只是**表述不够准**（不算硬伤，被追问会露）：本段「实现层次」里的 `SET NX EX`、释放锁「判断 value 再 DEL」都是**手写 SETNX 方案**的写法；Redisson 实际是 `HEXISTS + HINCRBY`，解锁也是 `HINCRBY -1` 判断是否减到 0，见本页上方两段完整 Lua。

**进阶话术**：
> "Redisson 的分布式锁底层用 Lua 脚本包装了三个关键能力——**原子加锁**（SET NX + 可重入计数）、**自动续期**（看门狗）、**安全解锁**（先校验身份再删除）。看门狗具体怎么续期：加锁成功时它会注册一个后台续期任务，每隔十秒就用 Lua 脚本去检查这把锁还在不在自己手里——在的话就把过期时间重新推回 30 秒，然后把自己再排到下一个十秒；锁一旦被释放就停止。所以只要业务没做完，这把锁的过期时间就一直被续着，不会因为业务超时而过期。注意看门狗只在无参 lock() 时生效，如果指定了过期时间就不启动——因为用户给出的时间就是明确的业务预期。另外锁的数据结构用 Hash 而不是普通 String，因为 Hash 的 field 可以存线程 ID 来标识锁的持有者，field 对应的 value 用 `HINCRBY` 来记录重入次数——一个结构同时解决两件事。"


>原子加锁

### **这段是 Redisson `lock()` 的核心加锁 Lua 脚本（简化版），一句话概括：用 Hash 结构 + 原子脚本同时实现“加锁”和“可重入”**

#### **先看数据结构：锁在 Redis 里长什么样**

Redisson 的锁不是 String，是一个 Hash：

```
key:   lock:order          （锁名）
type:  hash
field: 客户端ID:线程ID       （持有者标识，即脚本里的 ARGV[2]）
value: 重入次数              （1 表示加了一次，2 表示同线程又重入了一层）
```

选 Hash 而不是 String 的原因就在这段脚本里：String 只能存“有没有人持锁”，Hash 能同时存**谁持锁**（field）和**重入了几层**（value）。

#### **逐段走一遍脚本逻辑**

执行时，`lock()` 会把参数准备好：`KEYS[1]` 是锁名，`ARGV[1]` 是过期时间（毫秒），`ARGV[2]` 是“客户端 UUID + 线程 ID”拼出的持有者标识。然后 Redis 单线程原子执行三段判断：

**第一段：锁不存在 → 首次加锁**

```lua
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('hset', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end
```

`exists` 返回 0 说明没人持锁，直接写入自己的标识、重入次数设为 1，并设置过期时间。`return nil` 在 Redisson 的 Java 层对应“加锁成功”。注意 `hset` 和 `pexpire` 是在同一个脚本里，**中间不可能被其他客户端的命令插入**——这就是当初手写 `SETNX` + `EXPIRE` 两步做不到的原子性。

**第二段：锁存在、且就是我自己持有的 → 可重入**

```lua
if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end
```

这是可重入的关键。`hexists` 检查的不是“锁有没有”，而是“**锁的持有者是不是我**”：

- 是我 → 重入次数 +1，同时**刷新过期时间**。典型场景：方法 A 加锁后调用方法 B，B 里又对同一把锁 `lock()`——如果是 String + NX，第二次 `SET NX` 会直接失败造成死锁或逻辑错误；这里计数变 2，B 执行完 `unlock()` 减回 1，A 执行完再 `unlock()` 减到 0 才真正释放。加减必须严格配对；
- 顺带刷新 TTL 也有讲究：内层重入后过期时间重置，保证整条调用链执行期间锁不会中途过期。

**第三段：锁被别人持有 → 加锁失败**

```lua
return redis.call('pttl', KEYS[1])
```

前两个条件都不满足，说明锁被**别的**客户端/线程持有。返回该 key 的剩余存活毫秒数。这个返回值不是随便给的——Java 层拿到它之后不会傻傻自旋，而是**订阅解锁频道 + 带超时等待**：如果剩余时间很短，就等这么久；期间锁被释放会收到发布消息立即重试。`pttl` 的值就是用来设置这次等待上限的，避免抢一把马上要过期的锁却白等 30 秒。

#### **这段脚本体现的三个设计点（面试总结）**

1. **原子性由 Lua 保证**：exists/hexists 判断、写入、设置过期在 Redis 单线程里一次执行完，天然不可分割，不需要额外加锁去保护加锁动作；
2. **Hash 结构一石二鸟**：field 存持有者标识 → 支持解锁前校验防误删；value 存计数 → 支持可重入；
3. **失败返回 pttl 而不是 false**：为上层“发布订阅 + 精确等待重试”提供参数，这是 Redisson 抢锁失败的优雅处理。

对应的解锁脚本是镜像操作：`hexists` 校验是自己 → `hincrby -1` → 次数减到 0 才 `del` 并**发布解锁消息**唤醒等待者；次数没减到 0 就只减计数不释放。加锁和解锁这两段凑在一起，就是追问链第二、四环的完整底层实现。

### Q2：Redisson 的锁粒度怎么设计？商品维度还是用户维度？

**回答要点**：

```java
// ✅ 商品维度锁（推荐）
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
// 同一个商品的抢购请求串行化，不同商品互不干扰

// ❌ 用户维度锁
RLock lock = redissonClient.getLock("lock:user:" + userId);
// 同一个用户的多个请求串行——但秒杀是按商品粒度，不是按用户粒度

// ❌ 全局维度锁
RLock lock = redissonClient.getLock("lock:seckill");
// 所有秒杀请求串行 → 性能极差
```

| 粒度 | 并发度 | 控制力 | 推荐 |
|------|--------|--------|------|
| 商品维度 `sku:{id}` | ✅ 高（不同商品并行） | ✅ 精准控制 | ✅ **推荐** |
| 用户维度 `user:{id}` | ⚠ 中 | ❌ 无法控制同商品多用户 | ❌ |
| 全局 `seckill` | ❌ 极低 | ✅ 绝对控制 | ❌ |

### Q3：Redisson 集群模式下，锁存放在哪个节点？

**回答要点**：
- Redis 集群有 16384 个哈希槽，key 通过 CRC16 算法计算槽位
- `lock:sku:123` 和 `lock:sku:456` 可能在不同节点
- **天然分布**：不同商品的锁分散在不同节点，不会出现单节点热点
- 但如果锁的 key 设计成全局的（如 `lock:seckill`），就会全部命中同一节点

> **话术**："Redisson 配合 Redis Cluster 时，不同商品的锁天然分布在不同节点上。商品 A 的锁可能在节点 1，商品 B 的锁在节点 2——既实现了互斥，又分摊了压力。但要注意不要把锁 key 设计成全局的，否则单节点就变成瓶颈。"

### Q4：Redisson 和 Jedis / Lettuce 有什么区别？

| 对比项 | Redisson | Jedis | Lettuce |
|--------|----------|-------|---------|
| **定位** | 内存数据网格 | 原生 Redis 客户端 | 异步/响应式客户端 |
| **分布式锁** | ✅ 内置（看门狗） | ❌ 手写 Lua | ❌ 手写 Lua |
| **布隆过滤器** | ✅ 内置 | ❌ 无 | ❌ 无 |
| **可重入锁** | ✅ 支持 | ❌ 需自实现 | ❌ 需自实现 |
| **API 风格** | 高阶抽象（像 Java 集合） | 低阶（Redis 命令） | 低阶（响应式） |
| **线程安全** | ✅ 是 | ⚠ 需连接池 | ✅ 是 |

### Q5：RedLock 和单节点 Redisson 锁怎么选？

| 场景         | 方案                        | 原因             |
| ---------- | ------------------------- | -------------- |
| 秒杀、抢购（本项目） | **单节点 Redisson 锁**        | 性能好、实现简单，宕机概率低 |
| 资金交易、核心账户  | **RedLock 或 ZooKeeper 锁** | 需要严格互斥，容忍性能下降  |
| 防重复、去重、限流  | **单节点 Redisson 锁**        | 丢了锁问题不大，追求高吞吐  |

---

## 常见 Bug / 踩坑

### 1. 锁不释放 — `unlock()` 未在 finally 中调用

```java
// ❌ 错误：业务异常时锁不释放
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
lock.lock();
doBusiness();          // 如果抛出异常 → 锁永远不释放
lock.unlock();
```

```java
// ✅ 正确
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
lock.lock();
try {
    doBusiness();
} finally {
    lock.unlock();     // 保证一定释放
}
```

### 2. 看门狗失效 — 指定了过期时间

```java
// ❌ 看门狗不启动！
lock.lock(10, TimeUnit.SECONDS);  // 10 秒后自动释放，不续期

// ✅ 看门狗启动
lock.lock();  // 默认 30s，看门狗每 10s 续期
```

**建议**：秒杀等已知时长的业务用 `tryLock(waitTime, leaseTime, unit)` 更可控；不确定时长的业务用无参 `lock()` 靠看门狗续期。

### 3. 锁粒度太粗 — 所有商品共用一把锁

```java
// ❌ 所有秒杀请求串行化
RLock lock = redissonClient.getLock("lock:seckill");  // 全局锁

// ✅ 不同商品独立锁
RLock lock = redissonClient.getLock("lock:sku:" + skuId);
```

### 4. 锁超时时间设置不合理

- **太短**：业务没做完锁就过期，其他线程拿到锁，数据混乱
- **太长**：持有锁的线程宕机，其他线程等太久

**建议**：`tryLock(3, 30, TimeUnit.SECONDS)` — 最多等 3 秒，拿到后 30 秒自动释放。根据业务 P999 耗时 + 50% 缓冲估算。

### 5. Redisson 版本兼容性

Redisson 3.8.2（本项目使用）注意事项：
- 需要 JDK 1.8+
- Redis 2.8+ 或 Redis 3.0+（集群模式）
- Spring Boot 2.x 兼容性良好
- 升级到 Redisson 3.12+ 后部分 API 有调整（如 `Redisson.create()` 不再自动读取 `redisson.yml`）

---

## 面试话术

### 场景一：为什么用 Redisson 而不是手写 SETNX？

> **面试官**：你的秒杀系统为什么用 Redisson 而不是自己用 SETNX 实现分布式锁？

**回答要点**：
1. **开箱即用**：Redisson 内置了分布式锁的所有能力——原子加锁、看门狗续期、安全解锁，不需要我手写 Lua 脚本
2. **看门狗自动续期**：手写方案最大的痛点就是"业务超时锁过期"——设短了业务没做完、设长了宕机死锁。Redisson 的看门狗每 10 秒检查一次，业务还在就续 30 秒，完美解决
3. **可重入支持**：同一线程多次调用 lock() 不会死锁
4. **功能丰富**：除了分布式锁，本项目还用到了它的布隆过滤器（RBloomFilter）做网关防护，一个框架解决两个问题

### 场景二：秒杀场景下锁的设计思路？

> **面试官**：秒杀那么多请求，加锁不会变成性能瓶颈吗？

**回答思路**（层层递进）：
1. **锁粒度**：按商品 ID 粒度加锁，不同商品并行处理，不是全局锁
2. **前置过滤**：锁是 6 层漏斗的最后一层——走到锁的流量已经非常少（约等于库存量），锁没有性能瓶颈
3. **Redis 预扣前置**：在加锁前先经过 Redis 预扣库存，90%+ 的无效流量在这里就被挡掉了
4. **锁不是唯一防线**：MySQL 层还有 `UPDATE ... WHERE num>=#{num}` 原子条件做最终兜底

> **话术**："锁是最后一层兜底，不是第一道防线。走到锁的请求已经是经过 BloomFilter、JWT、Redis 预扣、RocketMQ 削峰层层过滤之后的，数量基本等于库存量。在这个量级上加锁，不会有性能问题。"

### 场景三：锁超时了怎么办？

> **面试官**：如果业务执行时间超过了锁的过期时间，会有什么问题？怎么解决？

**回答要点**：

**问题**：
```
线程A 拿到锁（30s 过期）
   → 业务执行 35s（Full GC / 慢查询）
     → 第 30s 锁自动释放
       → 线程B 拿到锁
         → 第 35s 线程A 醒来，继续写数据
           → 两个线程同时写 → 数据错乱
```

**解决方案**：
1. **看门狗自动续期**：无参 lock() 时 Redisson 自动续期，防止业务没做完锁就过期
2. **tryLock 设足够的过期时间**：按业务 P999 耗时 × 2 估算
3. **业务代码主动检测**：在循环或关键步骤中检查锁是否还在自己手里

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析，秒杀模块设计
- [[01-Redis/多节点争抢资源， Redis 分布式锁是怎么实现的？]] — Redis 分布式锁基础理论
- [[01-Redis/如何保证 Redis 分布式锁的高可用和高性能？]] — SETNX 手写方案与 RedLock 详解
- [[分布式事务 Seata]] — 分布式锁 vs 分布式事务的选型取舍
- [[06-其他杂项/CDC（Canal）详解]] — Canal 异步加载 BloomFilter 实现动态更新

> 📎 **反链**：本文件是 [[06-其他杂项/面试分析报告.md|面试分析报告]] 中"Redisson"技术点的详解延伸。报告中秒杀模块（3.3 秒杀模块、亮点二）、技术栈表格（分布式锁行）、知识盲区（看门狗）可参照本文理解。
