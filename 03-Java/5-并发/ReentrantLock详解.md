---
tags:
  - java
  - 并发
  - JUC
  - 锁
  - ReentrantLock
  - AQS
created: 2026-06-13
updated: 2026-06-13
source: [[Java并发编程面试题]]
---

# ReentrantLock 详解

> [[Java并发编程面试题]] 中仅给出特性提纲，本页展开讲解原理、源码、面试话术与踩坑点。
>
> 反向引用：[[Java并发编程面试题]] · [[synchronized 关键字详解|synchronized 关键字]] · [[线程池]] · [[AQS 详解]]

---

## 什么是 ReentrantLock

**ReentrantLock**（可重入互斥锁）是 `java.util.concurrent.locks` 包下的核心实现类，行为上等价于 `synchronized`，但提供了更丰富的操作能力：**可中断、可超时、支持公平锁、支持多个条件变量**。

> [!info] Reentrant — Reentrant + Lock
> 英文前缀 `Re-` 表示「再次」，`entrant` 来自「enter 进入」。直译：**可以再次进入的锁**，即同一线程对同一把锁可重复加锁而不会死锁，名字直接点明了它的核心特性。

### 通俗理解

把 ReentrantLock 想象成**酒店房间的房卡**：

- 一间房对应一把锁
- 房卡可以被**同一住客**反复刷卡进出（可重入）
- 房卡也可以**设置使用时长**（超时获取）
- 房卡可以被**前台叫醒后作废**（可中断）
- 酒店可以决定房卡按**排队顺序**还是**先抢先得**发放（公平/非公平）

---

## 一、核心 API 与基础范式

| 方法                    | 行为                    | 是否可中断                      |
| --------------------- | --------------------- | -------------------------- |
| `lock()`              | 阻塞获取，未获取到则一直等待        | ❌                          |
| `lockInterruptibly()` | 阻塞获取，等待期间可被 interrupt | ✅ 抛 `InterruptedException` |
| `tryLock()`           | 立即尝试获取，返回 boolean     | —                          |
| `tryLock(time, unit)` | 在指定时间内尝试获取            | ✅ 超时或被中断                   |
| `unlock()`            | 释放锁（必须在 finally 中）    | —                          |
| `newCondition()`      | 创建一个绑定到本锁的条件变量        | —                          |


**标准范式（必须背下来）：**

```java
ReentrantLock lock = new ReentrantLock();
lock.lock();                  // ← lock() 必须放在 try 外（见下方易错点）
try {
    // 临界区
} finally {
    lock.unlock();   // 一定要放在 finally，避免死锁
}
```

> [!warning] 易错点
> - `unlock()` 次数必须等于 `lock()` 次数，否则可能提前释放别人的锁
> - `unlock()` **必须**在 `finally` 块里，异常路径也要释放
> - `lock.lock()` 应放在 `try` **外面**：如果放在 try 内、且 lock 本身抛异常，进入 finally 时调 `unlock()` 会抛 `IllegalMonitorStateException`

---

## 二、四大核心特性详解

### 2.1 可重入性

**机制：** 内部维护一个**锁持有线程的引用** + **重入计数器**。

- 第一次获取：CAS 把 `_owner` 改为当前线程，`state = 1`
- 同一线程重入：判断 `_owner == 当前线程`？是则 `state++` 直接放行
- 释放：`state--`，减到 0 才真正释放并唤醒等待队列

**和 synchronized 的对应关系：**

| synchronized (ObjectMonitor) | ReentrantLock (AQS) |
|---|---|
| `_owner`（持有线程） | `exclusiveOwnerThread` |
| `_count`（重入次数） | `state` |
| `_EntryList` | CLH 同步队列 |
| `_WaitSet` | Condition 等待队列 |

**重入次数查询 API：**
```java
lock.getHoldCount();      // 当前线程持有次数
lock.isHeldByCurrentThread(); // 是否被当前线程持有
```

### 2.2 可中断性 ⭐

> 详见主文件 `[[Java并发编程面试题]]` 中可中断性段落

**核心 API：`lockInterruptibly()`**

线程在等待锁的过程中，可以被其他线程通过 `interrupt()` 唤醒，**抛 `InterruptedException` 让调用方主动放弃**。

**完整可运行示例：**

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {

    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        // 持锁线程：先抢到锁，sleep 5 秒不释放
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[holder] 拿到锁，sleep 5s ...");
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                System.out.println("[holder] 被中断");
            } finally {
                lock.unlock();
                System.out.println("[holder] 释放锁");
            }
        }, "holder");
        holder.start();

        TimeUnit.MILLISECONDS.sleep(200); // 确保 holder 先抢到锁

        // 等待线程：用 lockInterruptibly() 抢锁 —— 等待过程可被中断
        Thread waiter = new Thread(() -> {
            try {
                System.out.println("[waiter] 调用 lockInterruptibly() 抢锁");
                lock.lockInterruptibly();   // ← 关键 API
                try {
                    System.out.println("[waiter] 拿到锁（不会执行到这里）");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("[waiter] 被中断，放弃抢锁，优雅退出");
            }
        }, "waiter");
        waiter.start();

        TimeUnit.SECONDS.sleep(1);
        System.out.println("[main]   中断 waiter 线程");
        waiter.interrupt();

        holder.join();
        waiter.join();
        System.out.println("[main]   结束");
    }
}
```

**预期输出：**
```
[holder] 拿到锁，sleep 5s ...
[waiter] 调用 lockInterruptibly() 抢锁
[main]   中断 waiter 线程
[waiter] 被中断，放弃抢锁，优雅退出
[holder] 释放锁
[main]   结束
```

**对比 synchronized 的不可中断：**

把 `lock.lockInterruptibly()` 换成 `synchronized (lock)`，`waiter.interrupt()` **不会让它立即醒**——必须等 `holder` 5 秒后释放锁，`waiter` 才能继续。这就是为什么**需要支持任务取消、线程池 shutdown、检测死锁**的场景必须用 `ReentrantLock.lockInterruptibly()`。

**底层：** `LockSupport.park()` 挂起线程 → interrupt 时 `unpark` 唤醒 → 重抛 `InterruptedException`。

### 2.3 超时获取：`tryLock(time, unit)`

```java
if (lock.tryLock(3, TimeUnit.SECONDS)) {
    try {
        // 抢到锁，执行业务
    } finally {
        lock.unlock();
    }
} else {
    // 超时未抢到，放弃或重试
}
```

**用途：**
- 避免无限等待
- 实现**限时业务**：超过阈值就降级
- **防止死锁**：多个锁按固定顺序 + tryLock + 超时回滚

**无参版 `tryLock()`** 表示「立即尝试」，**不会阻塞**，适合「能抢就抢、抢不到就走」的非阻塞场景。

### 2.4 公平锁 vs 非公平锁

| 维度 | 公平锁 | 非公平锁（默认） |
|------|--------|----------------|
| 排队 | 严格按等待队列 FIFO | 允许插队，新线程可能抢到队列前面的线程 |
| 吞吐量 | 较低（要维护队列 + 频繁切换） | **较高**（HotSpot 注释说非公平吞吐通常更高） |
| 饥饿 | 不会 | 理论上可能（实际概率极低） |
| 适用 | 业务强依赖顺序、避免饥饿 | 高并发、追求吞吐 |

**为什么默认非公平？**

- 线程唤醒有上下文切换开销（park→唤醒→调度上CPU，约1~10微秒）。非公平锁释放时新线程可直接CAS抢锁，可能完全不需要唤醒队列中的线程,从而省掉这次切换；公平锁则必须按FIFO唤醒队首线程，每次释放都有这开销。
    
- 新到的线程可能刚好持有CPU缓存（cache affinity），让它直接抢反而省了一次切换。被唤醒的线程因为刚才一直在park，其工作数据很可能已从CPU缓存中被换出，重新执行时要承受一次"冷启动"的缓存miss；而新来的线程很可能数据还热在缓存里，执行更快，整体上让它插队反而提升了吞吐。
    
- 实际场景中严格公平的收益通常远小于吞吐损失。公平性主要解决"线程饥饿"问题，但在大多数业务场景下，锁的持有时间很短、竞争不激烈，饥饿概率很低；而为了这点公平性，每次都付出唤醒+切换的代价，对高并发吞吐的损耗是实打实的。所以ReentrantLock默认选择非公平。


```java
ReentrantLock fairLock = new ReentrantLock(true);   // 公平
ReentrantLock unfairLock = new ReentrantLock();     // 非公平（默认）
```

---

## 三、多个条件变量（Condition）

> [!info] 完整讲解已拆分
> 本节内容已拆出独立详解文件：[[Condition 详解]]。本节保留**精简对照 + 经典示例**，便于阅读连续性；更深入的 AQS 源码（await/signal）、节点迁移、面试话术请看 [[Condition 详解]]。

> [!abstract] Condition — 条件队列（Condition Variable）
> Condition 是绑定在 ReentrantLock 上的「条件等待队列」，作用相当于 `wait()/notify()` 的多队列升级版。一个 Lock 可以有多个 Condition，**精确唤醒**特定条件上的线程。

**为什么需要多个？** `wait()/notify()` 只能有一个等待队列，调用 `notifyAll()` 会唤醒所有等待者，但很多唤醒是无效的（条件其实没满足）。Condition 把等待者分组，**signal() 只唤醒本组**，效率高且语义清晰。

**使用示例：经典「有界阻塞队列」**

```java
class BoundedQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Object[] items;
    private int putIdx, takeIdx, count;

    public BoundedQueue(int capacity) {
        items = new Object[capacity];
    }

    public void put(T x) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == items.length) {     // 满则等待
                notFull.await();
            }
            items[putIdx] = x;
            if (++putIdx == items.length) putIdx = 0;
            count++;
            notEmpty.signal();                  // 唤醒一个消费者
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {                // 空则等待
                notEmpty.await();
            }
            T x = (T) items[takeIdx];
            items[takeIdx] = null;
            if (++takeIdx == items.length) takeIdx = 0;
            count--;
            notFull.signal();                   // 唤醒一个生产者
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

**要点：**
- `await()` 必须放在 `lock.lock()` 之后（因为 Condition 依赖 Lock）
- 循环判断 `while (条件不满足)` 而不是 `if`，防止**虚假唤醒**
- `signal()` 唤醒本条件队列一个线程；`signalAll()` 唤醒全部

**与 wait/notify 的对比：**

| 维度 | `wait()/notify()` | `Condition.await()/signal()` |
|------|-------------------|------------------------------|
| 锁绑定 | 与 `synchronized` 隐式绑定 | 与显式 `Lock` 绑定 |
| 等待队列数 | 1 个 | 多个，可分组精确唤醒 |
| 中断响应 | 抛 `InterruptedException` | 抛 `InterruptedException` |
| 当前置位 | 当前线程释放锁 | 当前线程释放锁并加入等待队列 |

---

## 四、底层原理：AQS 框架

> [!info] 完整讲解已拆分
> 本节内容已拆出独立详解文件：[[AQS 详解]]。本节保留**ReentrantLock 与 AQS 的接入点 + 加锁/解锁源码**，便于阅读连续性；AQS 自身的三要素、CLH 队列原理、`shouldParkAfterFailedAcquire` 状态机、`unparkSuccessor`、`cancelAcquire` 源码、共享模式、与 synchronized 对比、面试话术请看 [[AQS 详解]]。

### 4.1 AQS 是什么

**AQS（AbstractQueuedSynchronizer）** 是 JUC 的同步器基础框架，ReentrantLock、Semaphore、CountDownLatch、CyclicBarrier、ReentrantReadWriteLock、ThreadPoolExecutor（Worker）都基于它。

**核心三要素：**
1. **`state` 字段**（int）—— 同步状态，由子类定义语义（ReentrantLock 用来计数重入）
2. **`exclusiveOwnerThread`** —— 记录独占模式下持有锁的线程
3. **CLH 同步队列** —— 等待锁的线程被包装成 Node 节点串成的 FIFO 队列

> 详细 CLH 队列原理、AQS 对 CLH 的两个关键改造（自旋→park、单状态→waitStatus 四态）见 [[AQS 详解]]。

### 4.2 ReentrantLock 的类结构

```
ReentrantLock
   ├── 内部类 Sync extends AQS（核心实现）
   │      ├── NonfairSync（默认）
   │      └── FairSync
   └── 实现 Lock 接口
```

调用 `ReentrantLock()` 默认创建 NonfairSync；`ReentrantLock(true)` 创建 FairSync。

### 4.3 非公平锁加锁源码（重点）

```java
// ReentrantLock.NonfairSync
final void lock() {
    if (compareAndSetState(0, 1))                 // ① CAS 抢锁
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);                                // ② 抢不到入队等待
}

// AQS.acquire
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&                        // 再次尝试
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

**核心流程：**

| 步骤                 | 行为                                                   |
| ------------------ | ---------------------------------------------------- |
| ① CAS `state: 0→1` | 没人抢就直接拿锁（这就是非公平的来源：允许插队）                             |
| ② `tryAcquire` 失败  | 再次尝试（可重入判断）                                          |
| ③ `addWaiter`      | 把当前线程包装成 Node 加入 CLH 队尾                              |
| ④ `acquireQueued`  | 循环：前驱是 head 就再 tryAcquire，否则 `LockSupport.park()` 挂起 |
| ⑤ 唤醒               | 前驱释放时 unpark 当前节点，重新 tryAcquire                      |
|                    |                                                      |

### 4.4 公平锁 vs 非公平锁的 `tryAcquire` 差异

```java
// NonfairSync.tryAcquire
protected boolean tryAcquire(int acquires) {
    return nonfairTryAcquire(acquires);  // 直接 CAS，不检查队列
}

// FairSync.tryAcquire
protected boolean tryAcquire(int acquires) {
    for (;;) {
        if (hasQueuedPredecessors())      // ★ 关键：队列里有等的人就让
            return false;
        int c = getState();
        if (c == 0) {
            if (compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
        } else if (isHeldByCurrentThread()) {
            int nextc = c + acquires;
            setState(nextc);              // 重入
            return true;
        }
    }
}
```

**公平就多了一个 `hasQueuedPredecessors()` 判断**——队列里有人就放弃，不插队。

### 4.5 解锁源码

```java
// AQS.release
public final boolean release(int arg) {
    if (tryRelease(arg)) {                 // state 减 1，减到 0 才算释放
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);            // 唤醒队首后继
        return true;
    }
    return false;
}

// ReentrantLock.tryRelease
protected boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = (c == 0);
    if (free)
        setExclusiveOwnerThread(null);
    setState(c);
    return free;
}
```

**注意：`unlock()` 不会抛检查异常**——如果调用方未持有锁，会静默失败还是抛运行时异常？答案是**抛 `IllegalMonitorStateException` 运行时异常**（见上）。

---

## 五、与 synchronized 全面对比

| 维度    | `synchronized`            | `ReentrantLock`                            |
| ----- | ------------------------- | ------------------------------------------ |
| 语法    | 关键字                       | 类（`java.util.concurrent.locks`）            |
| 加锁/释放 | 自动                        | 手动（必须 `finally` 里 `unlock`）                |
| 可重入   | ✅（monitor 计数器）            | ✅（state 字段）                                |
| 公平性   | 仅非公平                      | 公平 / 非公平可选                                 |
| 可中断   | ❌                         | ✅ `lockInterruptibly()`                    |
| 超时获取  | ❌                         | ✅ `tryLock(time, unit)`                    |
| 条件变量  | 1 个（waitSet）              | 多个 Condition                               |
| 性能    | JDK 6 之后已优化，差距极小          | 高度竞争时略优                                    |
| 锁信息查询 | ❌                         | ✅ `getHoldCount` / `isLocked` / `isFair` 等 |
| 实现层级  | JVM 内部（C++ ObjectMonitor） | Java 层（AQS）                                |
| 锁升级   | 无锁 → 偏向 → 轻量 → 重量         | 始终走 AQS + 阻塞队列                             |

**JDK 6 之后**两者性能差距很小，**99% 的场景 `synchronized` 够用**。只有需要「可中断 / 可超时 / 公平 / 多条件变量」时才用 `ReentrantLock`。

---

## 六、面试高频踩坑

### 6.1 `lock()` 应放在 `try` 外还是 `try` 内？

```java
// ❌ 错误：lock 抛异常会进 finally，再 unlock 抛 IllegalMonitorStateException
try {
    lock.lock();
} finally {
    lock.unlock();
}

// ✅ 正确
lock.lock();
try {
    // 业务
} finally {
    lock.unlock();
}
```

> 正确写法是把`lock()`放在`try`外面，原因可以拆成两步：
> 
> 1. **`lock()`本身极少抛异常**（正常情况下会一直阻塞直到拿到锁，不会"拿一半"失败）。但万一发生极端情况（比如OOM导致`lock()`内部抛出`Error`），说明**锁根本没拿到手**。
> 2. 如果把`lock()`放在`try`里，一旦它抛了异常，依然会执行`finally`里的`unlock()`。但问题是——**你都没拿到锁，凭什么去释放它？** 这时`unlock()`会检测到"当前线程不是持有者"，直接抛出`IllegalMonitorStateException`。
> 
> **后果**：原本的`Error`（真正的根因）被这个新抛出的`IllegalMonitorStateException`覆盖掉了，你看到的异常信息是"释放锁失败"，而真正的"OOM"被隐藏了，排错时会很懵。
> 
> **所以原则是**：只有在**确定拿到锁之后**才进入`try`，这样`finally`里的`unlock()`才是安全、对应的操作。
> 
> 一句话总结：**`lock()`不在`try`里，是因为不能让"加锁失败"和"解锁失败"这两件不该同时发生的事，绞在一起把异常信息搞乱。**

### 6.2 为什么 `unlock()` 要在 `finally` 里？

防止**业务代码抛异常**或**线程被中断**导致锁未释放，造成死锁。finally 块保证无论正常返回还是异常路径，锁都能被释放。

### 6.3 死锁排查套路

- `jstack <pid>` 看线程栈，找 `Waiting on <0x...>` 的 BLOCKED 线程
- 用 `tryLock(timeout)` 替换 `lock()`，超时回滚业务
- 保证多个锁的获取顺序一致（**顺序加锁**）

### 6.4 `Condition` 必须在 `lock()` 之后创建？

不一定。`lock.newCondition()` 本身没有这个限制，但 **`await()` 必须**在 `lock.lock()` 之后调用，否则 `IllegalMonitorStateException`。

### 6.5 为什么 `ReentrantLock` 默认非公平？

HotSpot AQS 作者 Doug Lea 注释里写明：在高竞争下，**非公平锁的吞吐通常比公平锁高**。原因是公平锁唤醒有上下文切换开销，而非公平允许「持锁线程释放后立即让新到的线程拿到」，省掉一次切换。

---

## 七、适用场景选择

| 场景 | 推荐 |
|------|------|
| 简单同步、单方法同步 | `synchronized` |
| 需要可中断（任务取消、shutdown） | `ReentrantLock.lockInterruptibly()` |
| 需要限时（防止死锁、降级） | `ReentrantLock.tryLock(time, unit)` |
| 需要严格排队（FIFO） | `ReentrantLock(true)` 公平锁 |
| 需要多个条件队列精确唤醒 | `ReentrantLock + 多 Condition` |
| 分布式场景（多 JVM） | `[[Redisson详解]]` / 分布式锁 |
| 读多写少 | `ReentrantReadWriteLock` |

---

## 八、面试话术模板

> "ReentrantLock 是 JUC 提供的可重入互斥锁，底层基于 AQS 框架。state 字段记录重入次数，exclusiveOwnerThread 记录持有线程，CLH 队列管理等待线程。相对 synchronized，它有四个核心优势：**可中断、可超时、支持公平锁、多个条件变量**。底层通过 CAS + LockSupport.park/unpark 实现线程挂起与唤醒。高竞争场景下非公平锁吞吐更高，所以默认是非公平的。只有需要这四个高级特性时才用 ReentrantLock，否则优先用 synchronized，JDK 6 之后两者性能差距很小。"

---

## 关联知识点

- [[Java并发编程面试题]] — 主文件，本详解由其「可重入性 / 可中断性 / 超时 / 公平 / 多条件变量」段展开
- [[AQS 详解]] — ReentrantLock 的底层框架（独立详解：CLH 队列、acquire/release/cancelAcquire 源码、共享模式、面试话术）
- [[线程池]] — 线程池的 Worker 也基于 AQS 独占模式
- [[synchronized 关键字详解|synchronized 关键字]] — 与 ReentrantLock 对比的核心
- [[内存屏障和happens-before]] — 锁的内存语义来源
- [[Redisson详解]] — 分布式场景下的 ReentrantLock 升级
- [[Condition 详解]] — ReentrantLock 多条件变量机制独立详解（AQS 内部类 ConditionObject）
