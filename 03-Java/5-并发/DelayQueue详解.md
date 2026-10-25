---
tags:
  - java
  - 并发
  - JUC
  - DelayQueue
  - 延迟队列
  - 定时任务
created: 2026-06-13
updated: 2026-06-13
source: [[阻塞队列详解]]
---

# DelayQueue 详解

> 拆分自 [[阻塞队列详解]] 第六章与 [[Java并发编程面试题]] 阻塞队列段落。本页展开延迟队列原理、`Delayed` 接口、`PriorityQueue` 堆结构、leader-follower 优化、ScheduledThreadPoolExecutor 应用、与 Timer / ScheduledExecutorService 对比、面试话术与踩坑点。
>
> 反向引用：[[阻塞队列详解]] · [[线程池]] · [[ReentrantLock详解]] · [[Condition 详解]] · [[AQS 详解]] · [[Java并发编程面试题]]

---

## 什么是 DelayQueue

**DelayQueue**（延迟队列）是 `java.util.concurrent` 包下的**无界阻塞队列**，元素必须实现 **`Delayed` 接口**。只有**到期**的元素才能从队列取出——`take()` 阻塞到队首元素到期才能返回。

> [!info] DelayQueue — Delay Queue（延迟队列）
> 本质 = **`PriorityQueue`（按到期时间排序的堆） + `Delayed` 接口 + `ReentrantLock` + `Condition`**。最常用场景：**定时任务调度、订单超时关闭、缓存过期清理**。
>
> 通俗理解：把 DelayQueue 想象成**快递驿站的取件货架**——每个包裹上贴了**取件时间标签**（比如「18:00 后才能取」）。顾客（消费者）来取件，如果队首包裹的标签还没到时间，**就坐在驿站等**；时间一到立刻取走，下一个顾客才能取下一个到期的包裹。

### 核心三要素

| 要素 | 含义 |
|------|------|
| **Delayed 元素** | 必须实现 `getDelay()` 和 `compareTo()` |
| **到期时间** | 每个元素自带 `expireTime`（绝对时间戳） |
| **队首元素** | **永远是最快到期的**——`PriorityQueue` 按到期时间小顶堆排 |

### 一句话 vs 其它队列

| 队列 | 出队条件 |
|------|----------|
| ArrayBlockingQueue / LinkedBlockingQueue | **FIFO**，与时间无关 |
| PriorityBlockingQueue | 按 `compareTo` 排序，**与时间无关** |
| **DelayQueue** | 按到期时间排，**队首必须到期才能出** |

---

## 一、核心 API 与基础范式

| 方法 | 行为 |
|------|------|
| `put(E e)` | **不阻塞**（无界队列） |
| `add(E e)` | 同 put |
| `offer(E e)` | 同 put，返回 true |
| **`take()`** | **阻塞**到队首到期才返回 |
| `poll()` | 立即返回 null（队首没到期） |
| `poll(time, unit)` | 超时等队首到期 |
| `peek()` | 看队首（不删除） |
| `size()` | 元素数量 |

**元素必须实现 Delayed：**

```java
public interface Delayed extends Comparable<Delayed> {
    long getDelay(TimeUnit unit);             // 剩余延迟时间
}
```

**标准范式：**

```java
DelayQueue<OrderTask> queue = new DelayQueue<>();

// 放入 5 秒后到期的任务
queue.put(new OrderTask("order-1", 5000));

// 阻塞到任务到期才取出
OrderTask task = queue.take();              // 5 秒后返回
```

---

## 二、Delayed 接口完整实现

### 2.1 典型实现：订单任务

```java
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class OrderTask implements Delayed {
    private final String orderId;
    private final long expireTime;            // 绝对到期时间戳

    public OrderTask(String orderId, long delayMs) {
        this.orderId = orderId;
        this.expireTime = System.currentTimeMillis() + delayMs;
    }

    /** 剩余延迟时间：>0 表示未到期 */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    /** 堆排序：到期早的排前面 */
    @Override
    public int compareTo(Delayed other) {
        return Long.compare(this.expireTime, ((OrderTask) other).expireTime);
    }

    public String getOrderId() { return orderId; }

    @Override
    public String toString() { return "OrderTask[" + orderId + "]"; }
}
```

**两个关键方法：**
- `getDelay()`——返回剩余延迟；**OS 时间可能回拨**，建议用 `System.nanoTime()` 避免
- `compareTo()`——堆排序用；**到期早的排前面**（小顶堆）

### 2.2 完整可运行示例

```java
import java.util.concurrent.*;

public class DelayQueueDemo {
    public static void main(String[] args) {
        DelayQueue<OrderTask> queue = new DelayQueue<>();
        long start = System.nanoTime();

        // 放入 3 个不同延迟的任务
        queue.put(new OrderTask("order-A", 3000));   // 3 秒
        queue.put(new OrderTask("order-B", 1000));   // 1 秒
        queue.put(new OrderTask("order-C", 2000));   // 2 秒

        // 消费 3 次（按到期顺序取出）
        for (int i = 0; i < 3; i++) {
            try {
                OrderTask task = queue.take();
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("[" + elapsedMs + "ms] 取出: " + task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

**预期输出（按到期顺序 1s / 2s / 3s）：**
```
[1004ms] 取出: OrderTask[order-B]
[2004ms] 取出: OrderTask[order-C]
[3003ms] 取出: OrderTask[order-A]
```

---

## 三、底层原理：PriorityQueue + ReentrantLock + Condition

```
DelayQueue
├── PriorityQueue q              (小顶堆，按 expireTime 排序)
├── ReentrantLock lock
├── Condition available          (消费者等待队列)
└── Thread leader                (leader-follower 优化)
```

### 3.1 核心字段

```java
private final transient ReentrantLock lock = new ReentrantLock();
private final PriorityQueue<E> q = new PriorityQueue<>();
private final Condition available = lock.newCondition();
private Thread leader = null;                          // 当前等待队首的「leader」线程
```

### 3.2 `offer` 入队（不阻塞）

```java
public boolean offer(E e) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        q.offer(e);                                    // PriorityQueue 入堆
        if (q.peek() == e) {                           // ★ 自己成了队首
            leader = null;                             // 旧的 leader 失效
            available.signal();                        // 唤醒一个等者
        }
        return true;
    } finally {
        lock.unlock();
    }
}
```

**关键：自己成为新队首时，要把 `leader` 置 null 并 signal**——因为旧的 leader 在等「旧的队首」到期，新队首换了，旧的等待就作废了。

### 3.3 `take` 出队（阻塞到到期）

```java
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            E first = q.peek();
            if (first == null) {                       // 队列空
                available.await();                     // 无限等
            } else {
                long delay = first.getDelay(TimeUnit.NANOSECONDS);
                if (delay <= 0) {                      // ★ 到期了
                    return q.poll();                    // 出队返回
                }
                // 还没到期
                if (leader != null) {                  // 已经有 leader 在等
                    available.await();                 // 无限等（让 leader 醒来后 signal）
                } else {
                    Thread thisThread = Thread.currentThread();
                    leader = thisThread;               // ★ 自己当 leader
                    try {
                        available.awaitNanos(delay);   // 等到队首到期
                    } finally {
                        if (leader == thisThread)      // 自己还是 leader 才清
                            leader = null;
                    }
                }
            }
        }
    } finally {
        if (leader == null && q.peek() != null) {      // 自己是最后等者要 signal
            available.signal();
        }
        lock.unlock();
    }
}
```

### 3.4 leader-follower 模式 ⭐⭐（精妙设计）

**问题：** 假设 100 个消费者都在 `take()`，队首要 1 小时后才到期——**如果 100 个线程都 `awaitNanos(1小时)`**，会怎样？

- 100 个线程都要等 1 小时
- 但**只要有 1 个线程等就行**——其他 99 个无限等就行
- 1 小时后这个线程醒来 poll，再 signal 下一个

**leader-follower 优化：**
- **`leader` 角色**：只允许 1 个线程做「定时等」
- **`follower` 角色**：其他线程无限等，被 leader 唤醒后再去竞争 leader

**为什么高效？**
- 避免 N 个线程都注册定时器
- OS 定时器是有限资源，N 个定时器开销大
- 单 leader 定时唤醒最优雅

---

## 四、典型使用场景

### 4.1 订单超时关闭（最经典）

```java
public class OrderTimeoutService {
    private final DelayQueue<OrderTask> queue = new DelayQueue<>();

    public void start() {
        // 后台 worker 持续 take
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                try {
                    OrderTask order = queue.take();    // 到期才返回
                    closeOrder(order.getOrderId());    // 关闭订单
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void submitOrder(String orderId, long ttlMs) {
        queue.put(new OrderTask(orderId, ttlMs));      // 订单进来就入队
    }
}
```

### 4.2 缓存过期清理

```java
// 缓存项用 DelayQueue 排队，到期自动清理
public class ExpiringCache<K, V> {
    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
    private final DelayQueue<CacheEntry<K>> delayQueue = new DelayQueue<>();

    public void put(K key, V value, long ttlMs) {
        cache.put(key, value);
        delayQueue.put(new CacheEntry<>(key, ttlMs));
    }

    public V get(K key) {
        // 后台线程持续清理过期项
        return cache.get(key);
    }
}
```

### 4.3 ScheduledThreadPoolExecutor 的底层

**`ScheduledThreadPoolExecutor` 内部就是 DelayQueue**——`schedule(task, delay, unit)` 把任务包成 `ScheduledFutureTask`（实现 Delayed）入队。

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
scheduler.schedule(() -> doSomething(), 5, TimeUnit.SECONDS);    // 5 秒后执行
scheduler.scheduleAtFixedRate(() -> doPeriodic(), 0, 1, TimeUnit.SECONDS);  // 1 秒周期
```

### 4.4 分布式任务调度的简化版

用 DelayQueue 做单机版任务调度——简单业务可代替 Quartz。

---

## 五、与 Timer / ScheduledExecutorService 对比

| 维度 | `Timer` | `ScheduledExecutorService` | `DelayQueue` 自实现 |
|------|---------|----------------------------|----------------------|
| 线程模型 | **单线程** | 线程池 | 自己起 worker |
| 多任务并发 | ❌ 一个卡全卡 | ✅ | ✅ |
| 异常处理 | 一个抛异常**整个 Timer 死掉** | 任务独立 | 任务独立 |
| 精确性 | 单线程 → 任务堆积会延迟 | 多线程 → 较准 | 较准 |
| 推荐 | ❌ 已过时 | ✅ **首选** | 业务简单时 |

> [!warning] Timer 已过时
> 阿里规约明确**禁用 `Timer`**——单线程 + 异常敏感，一个任务抛异常整个 Timer 线程死掉，**后续所有任务都跑不了**。必须用 `ScheduledThreadPoolExecutor`。

---

## 六、ScheduledThreadPoolExecutor 与 DelayQueue 关系

`ScheduledThreadPoolExecutor` **内部**用 `DelayedWorkQueue`（DelayQueue 的变体）实现任务排队：

```java
public class ScheduledThreadPoolExecutor extends ThreadPoolExecutor
        implements ScheduledExecutorService {

    private class DelayedWorkQueue extends AbstractQueue<Runnable>
            implements BlockingQueue<Runnable> {
        // 内部就是 DelayQueue 的简化版
        private final PriorityQueue<Runnable> queue = new PriorityQueue<>();
    }
}
```

**任务入队流程：**

```
schedule(task, 5s)  →  ScheduledFutureTask（实现 Delayed）
                   →  DelayedWorkQueue.offer
                   →  按到期时间入堆
worker.take()        →  阻塞到队首到期
                   →  取出执行
```

---

## 七、面试高频踩坑

### 7.1 Delayed 元素必须实现 compareTo 和 getDelay

否则编译/运行报错。**两个方法都和「时间」密切相关**——`compareTo` 决定堆顺序，`getDelay` 决定能不能出队。

### 7.2 `getDelay` 用 `System.currentTimeMillis()` 还是 `nanoTime()`？

| 时钟 | 适用 |
|------|------|
| `currentTimeMillis()` | **墙钟**（真实时间），**会受 OS 时间回拨影响** |
| **`nanoTime()`** | **单调时钟**，**不受回拨影响**——**推荐** |

```java
private final long expireNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);

@Override
public long getDelay(TimeUnit unit) {
    return unit.convert(expireNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
}
```

### 7.3 `take()` 一直阻塞怎么办？

**检查队首元素的 getDelay 是否真的到了 0**——可能 compareTo 排错了，导致一直是非到期元素在队首。

### 7.4 leader 模式为什么高效？

**避免 N 个线程都注册定时器**——N 个定时器开销远大于 1 个 leader 定时器。

### 7.5 DelayQueue 是有界还是无界？

**无界**（底层 PriorityQueue 自动扩容）。**put 永远不阻塞**——但要小心任务堆积导致**内存爆炸**。

### 7.6 怎么让一个任务「到点必跑」？

**用 ScheduledThreadPoolExecutor**——它内部用 DelayQueue，且**有 worker 线程兜底**。DelayQueue 本身只是个队列，**需要自己起 worker**。

### 7.7 DelayQueue 能不能存 null？

**不能**。源码里 `q.offer(e)` 之前会 `checkNotNull`——`null` 抛 NPE。

---

## 八、面试话术模板

> "DelayQueue 是 JUC 的延迟阻塞队列，元素必须实现 Delayed 接口，只有到期元素才能出队。底层用 PriorityQueue 按到期时间排序（小顶堆），加 ReentrantLock + Condition 实现阻塞，特别的 leader-follower 模式优化定时器开销——只允许一个线程做定时等，其他线程无限等，被唤醒后再竞争 leader。
>
> 典型场景是订单超时关闭、缓存过期清理、ScheduledThreadPoolExecutor 内部任务调度。相对 Timer，DelayQueue 不会因为一个任务抛异常就整个挂掉；相对 ScheduledExecutorService，DelayQueue 更底层、更灵活。
>
> 实现时两个关键方法：getDelay 返回剩余延迟，建议用 System.nanoTime() 避免 OS 时间回拨；compareTo 按到期时间小顶堆排。注意点是 DelayQueue 无界，put 不阻塞，要小心任务堆积导致 OOM。"

---

## 关联知识点

- [[阻塞队列详解]] — DelayQueue 是 BlockingQueue 五大实现之一
- [[线程池]] — ScheduledThreadPoolExecutor 底层用 DelayedWorkQueue（DelayQueue 变体）
- [[ReentrantLock详解]] — DelayQueue 用 ReentrantLock + Condition
- [[Condition 详解]] — available Condition + leader-follower 精妙设计
- [[AQS 详解]] — DelayQueue 不用 AQS，对比理解两种同步器设计哲学
- [[Java并发编程面试题]] — 并发编程主文件
- [[ScheduledThreadPoolExecutor详解]]（待创建）— DelayQueue 的工程化封装
