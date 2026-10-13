---
tags:
  - java
  - 并发
  - JUC
  - 锁
  - ReentrantLock
  - Condition
  - AQS
created: 2026-06-13
updated: 2026-06-13
source: [[ReentrantLock详解]]
---

# Condition 详解

> 拆分自 [[ReentrantLock详解]] 第 3 节「多个条件变量（Condition）」。本页展开 Condition 的原理、与 `wait/notify` 的全面对比、AQS 底层 `ConditionObject` 源码、面试话术与踩坑点。
>
> 反向引用：[[ReentrantLock详解]] · [[AQS 详解]] · [[Java并发编程面试题]] · [[synchronized 关键字详解|synchronized 关键字]] · [[线程池]]

---

## 什么是 Condition

**Condition**（条件变量）是 `java.util.concurrent.locks` 包下的接口，它**把 `Object.wait()/notify()` 升级为多队列、可分组、显式绑定**的等待/通知机制。**一个 `ReentrantLock` 可以创建多个 `Condition` 实例**，每个 Condition 维护自己独立的等待队列，调用 `signal()` 时**只唤醒本队列里的线程**。

> [!info] Condition — Condition Variable（条件变量）
> POSIX 术语叫「条件变量」。它的本质是**一个等待队列**（链表 + Node），让拿到锁的线程可以**主动释放锁并挂起**，等别的线程**条件满足时再叫醒**。
>
> 通俗理解：把 ReentrantLock 想象成一间**会议室**，Condition 就是会议室门口的**多个主题讨论区**。线程 A 拿着钥匙进会议室讨论「库存不足」（`notFull.await()`），线程 B 想叫醒它必须走到「库存不足」那个讨论区喊一声（`notFull.signal()`），不会去「库存过多」那里喊。

### 一句话对比

| 比喻 | `Object.wait()/notify()` | `Condition.await()/signal()` |
|------|---------------------------|-------------------------------|
| 等候室 | 1 个大通铺 | N 个独立包间 |
| 叫醒 | 大喇叭喊话，全员都得醒了自己判断 | 谁睡在哪个包间，就叫谁 |
| 钥匙 | 和 `synchronized` 隐式绑定 | 显式绑定到具体 `Lock` |

---

## 一、核心 API 与基础范式

| 方法 | 行为 | 是否抛 `InterruptedException` |
|------|------|-------------------------------|
| `await()` | 释放锁并挂起，进入本 Condition 的等待队列 | ✅ |
| `await(time, unit)` | 同上，带超时自动醒来 | ✅ |
| `awaitNanos(nanos)` | 纳秒级超时等待 | ✅ |
| `awaitUntil(deadline)` | 等待到绝对时间 | ✅ |
| `signal()` | 唤醒本条件队列的**一个**线程（按 FIFO） | ❌ |
| `signalAll()` | 唤醒本条件队列的**全部**线程 | ❌ |

**标准范式：**

```java
ReentrantLock lock = new ReentrantLock();
Condition notFull  = lock.newCondition();   // 写线程的等待室
Condition notEmpty = lock.newCondition();   // 读线程的等待室

// 写线程
lock.lock();
try {
    while (条件不满足) notFull.await();   // 释放锁、挂起、醒来后再抢锁
    // 临界区
    notEmpty.signal();                     // 唤醒一个读者
} finally {
    lock.unlock();
}
```

> [!warning] 三条铁律
> 1. `await()` **必须**在 `lock()` 之后调用，否则抛 `IllegalMonitorStateException`
> 2. 条件判断必须用 **`while` 循环**而不是 `if`——防止**虚假唤醒**
> 3. `signal()` 之后必须 **`unlock()`**——signal 不释放锁，unlock 时才真正让出

---

## 二、为什么需要 Condition

### 2.1 `wait()/notify()` 的痛点

```java
synchronized (lock) {
    while (库存 == 0) lock.wait();   // 货架空了，消费者睡
    // 取货...
    lock.notifyAll();                // 叫醒所有等的人（生产者 + 其他消费者）
}
```

**问题：**
- `notifyAll()` 会唤醒**所有**等待者，其中**大部分唤醒是无效的**（条件其实没满足），醒来后又 `wait()` 回去——**惊群效应（thundering herd）**
- 如果用 `notify()` 又存在**信号丢失**风险（叫醒了不相关的人）
- `synchronized` 只能配一个 `waitSet`，**无法按条件分组**

### 2.2 Condition 的解法

```java
lock.lock();
try {
    while (库存 == 0) notEmpty.await();   // 睡在「非空」队列
    // 取货...
    notFull.signal();                      // 只叫醒「不满」队列里的生产者
} finally {
    lock.unlock();
}
```

**收益：**
- **精确唤醒**，避免惊群
- 一把锁 N 个 Condition，**按业务语义分组**
- 显式绑定，代码可读性高

---

## 三、完整可运行示例：有界阻塞队列

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();   // 写线程的等待室
    private final Condition notEmpty = lock.newCondition();   // 读线程的等待室
    private final Object[] items;
    private int putIdx, takeIdx, count;

    public BoundedQueue(int capacity) {
        items = new Object[capacity];
    }

    public void put(T x) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == items.length) {     // 满则等
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

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {                // 空则等
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

    public static void main(String[] args) {
        BoundedQueue<Integer> q = new BoundedQueue<>(2);
        // 3 个生产者 + 3 个消费者，队列容量 2
        for (int i = 0; i < 3; i++) {
            final int id = i;
            new Thread(() -> {
                try { q.put(id); System.out.println("put " + id); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "P" + i).start();
            new Thread(() -> {
                try { Integer v = q.take(); System.out.println("take " + v); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "C" + i).start();
        }
    }
}
```

**运行效果：**
- 写满 2 个后第 3 个写线程睡在 `notFull` 队列
- 读线程拿走 1 个，唤醒 `notFull` 里的写者
- 读空后第 3 个读线程睡在 `notEmpty` 队列
- 写线程放入 1 个，唤醒 `notEmpty` 里的读者
- **绝不互相惊扰**

---

## 四、底层原理：AQS 的 `ConditionObject`

### 4.1 类结构

`Condition` 是接口，**唯一实现**是 AQS 内部类 `ConditionObject`：

```
ReentrantLock
   └── Sync (extends AQS)
          └── ConditionObject (implements Condition, java.io.Serializable)   ← 唯一实现
```

`lock.newCondition()` 实际就是 `sync.newCondition()`，返回一个新的 `ConditionObject` 实例。

### 4.2 等待队列：单向链表

每个 `ConditionObject` 内部维护**一条单向链表**，节点类型是 AQS 的 `Node`（复用 CLH 节点结构）：

```
firstWaiter → NodeA → NodeB → NodeC ← lastWaiter
             (next)  (next)  (next)
```

- 节点状态用 `waitStatus = Node.CONDITION (-2)` 标记「在条件队列里」
- 不在 CLH 同步队列（CLH 存的是抢锁的线程）

### 4.3 `await()` 源码（AQS `ConditionObject`）

```java
public final void await() throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    Node node = addConditionWaiter();              // ① 加入条件队列尾部
    int savedState = fullyRelease(node);           // ② 完全释放锁（state → 0）
    int interruptMode = 0;
    while (!isOnSyncQueue(node)) {                 // ③ 不在 CLH 队列就一直挂起
        LockSupport.park(this);                    //    真正挂起线程
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    if (acquireQueued(node, savedState))           // ④ 被唤醒后重新抢锁
        interruptMode = INTERRUPT_1;
    if (node.nextWaiter != null) unlinkCancelledWaiters();  // 清理取消节点
    if (interruptMode != 0) reportInterruptAfterWait(interruptMode);
}
```

**核心流程：**

| 步骤 | 行为 | 关键点 |
|------|------|--------|
| ① `addConditionWaiter` | 当前线程包装成 Node 加入条件队列尾部 | Node 状态 = CONDITION |
| ② `fullyRelease` | **完全**释放锁（state 减到 0） | 唤醒 CLH 队首的下一个线程 |
| ③ `LockSupport.park` | 挂起当前线程 | 等别的线程 `signal()` |
| ④ `acquireQueued` | 被唤醒后**重新抢锁** | 自旋 + park 抢锁逻辑 |
| 中断响应 | 醒来时若检测到中断，抛 `InterruptedException` | — |

### 4.4 `signal()` 源码

```java
public final void signal() {
    if (!isHeldExclusively())                       // 必须持锁，否则 IllegalMonitorStateException
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        doSignal(first);                            // 唤醒队首
}

private void doSignal(Node first) {
    do {
        if ((firstWaiter = first.nextWaiter) == null)
            lastWaiter = null;
        first.nextWaiter = null;
    } while (!transferForSignal(first) && (first = firstWaiter) != null);
}

final boolean transferForSignal(Node node) {
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))  // 状态 CONDITION → 0
        return false;
    Node p = enq(node);                              // 节点塞回 CLH 同步队列尾部
    int ws = p.waitStatus;
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        LockSupport.unpark(node.thread);             // 唤醒对应线程
    return true;
}
```

**核心流程：**
1. 校验：调用 `signal()` 的线程**必须持锁**
2. 把条件队列的**队首节点**摘下，状态从 `CONDITION` 改为 `0`
3. 把这个节点**塞回 CLH 同步队列**尾部
4. `LockSupport.unpark` 唤醒节点的线程
5. 被唤醒的线程在 `await()` 的 `while` 循环里发现「自己已经在 CLH 队列了」，退出循环去 `acquireQueued` 抢锁

### 4.5 `signalAll()` 源码

```java
public final void signalAll() {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        doSignalAll(first);
}

private void doSignalAll(Node first) {
    lastWaiter = firstWaiter = null;
    do {
        Node next = first.nextWaiter;
        first.nextWaiter = null;
        transferForSignal(first);
        first = next;
    } while (first != null);
}
```

把**整个条件队列**一次性全部塞回 CLH 同步队列。

### 4.6 节点迁移：条件队列 ↔ CLH 队列

```
        await() 之前                      signal() 之后
                                                          
CLH 同步队列: head → ... → (空)         CLH 同步队列: head → ... → NodeA → ...
                                                          
Condition 队列:                         Condition 队列: (空)
   firstWaiter → NodeA → NodeB → ...
```

- `await()`：**从 CLH 摘下**（释放锁时 CLH 上的节点会被移除），**加入 Condition 队列**
- `signal()`：**从 Condition 摘下**，**塞回 CLH 队尾**，**unpark 唤醒**
- `signalAll()`：一次性全部迁移

---

## 五、与 `wait()/notify()` 全面对比

| 维度 | `Object.wait()/notify()` | `Condition.await()/signal()` |
|------|--------------------------|------------------------------|
| 所在包 | `java.lang.Object`（基类） | `java.util.concurrent.locks` |
| 锁绑定 | 与 `synchronized` **隐式**绑定 | 与 `Lock` **显式**绑定，可一把锁多个 |
| 等待队列数 | **1 个**（对象监视器的 waitSet） | **多个**（一把锁 N 个 Condition） |
| 唤醒精度 | `notify()` 随机 / `notifyAll()` 全醒 | `signal()` 精准叫醒本组 |
| 响应中断 | `wait()` 抛 `InterruptedException` | `await()` 抛 `InterruptedException` |
| 超时支持 | `wait(time)` | `await(time, unit)` / `awaitNanos` / `awaitUntil` |
| 当前置位 | 释放锁 + 加入 waitSet | 释放锁 + 加入 Condition 队列 |
| 公平性 | 由 JVM / monitor 决定 | 可与公平/非公平 Lock 配合 |
| 灵活度 | 低（关键字限制多） | 高（可与 tryLock、isFair 等组合） |

**结论：能用 `wait/notify` 解决的场景，`Condition` 都能解决且更优雅；反之不行。**

---

## 六、面试高频踩坑

### 6.1 为什么用 `while` 而不是 `if`？

```java
// ❌ 错误：虚假唤醒后可能越过条件检查
if (count == 0) notEmpty.await();

// ✅ 正确：醒来后重新检查条件
while (count == 0) notEmpty.await();
```

**原因：**
- `await()` 可能被**虚假唤醒（spurious wakeup）**——线程并没有被 `signal()`，也没被中断也没超时，但**莫名其妙醒了**（OS / JVM 层面的事，JLS 允许）
- 即使是正常 `signal()` 唤醒，条件也**可能仍然不满足**（多个等者只醒一个，状态可能被别人改了）
- 唯一安全做法：**醒来后重新 while 一次条件**

### 6.2 `signal()` 之后需要 `unlock()` 吗？

**必须 unlock。** `signal()` 只做「把节点从条件队列搬到 CLH 队列 + unpark」，**不释放锁**。当前线程仍然持有锁，要等 `unlock()` 时才真正让出，让被唤醒的线程去 `acquireQueued` 抢锁。

### 6.3 `await()` 之前没抢锁会怎样？

抛 `IllegalMonitorStateException`——AQS 在 `addConditionWaiter` 之前的 `isHeldExclusively()` 检查会拦截。同理 `signal()` / `signalAll()` 也必须在持锁状态下调用。

### 6.4 `signal()` 唤醒的顺序？

按 **FIFO** 唤醒条件队列里的队首（参考 AQS 源码 `transferForSignal`）。但**抢到锁的顺序不保证 FIFO**——被唤醒的线程和其他 CLH 上的线程一起竞争，最终谁能抢到锁取决于 `tryAcquire` 实现（非公平锁就是抢，公平锁则看队列位置）。

### 6.5 `signal()` 和 `signalAll()` 选哪个？

| 场景 | 推荐 |
|------|------|
| 条件**互斥**（生产者/消费者，醒一个就够） | `signal()` |
| 同一条件**多个等者**都需要重新检查（如状态机切换） | `signalAll()` |
| 不确定时 | `signalAll()` 更安全（不会丢信号），代价是惊群 |

**判断口诀：「一对一精准唤醒用 `signal`；一对多广播用 `signalAll`」**

### 6.6 为什么不用 `synchronized + wait/notify` 写有界队列？

可以写，但 Condition 更优：
- 避免 `notifyAll` 惊群（生产者的 signal 只叫读者，读者的 signal 只叫写者）
- 代码可读性更好（语义清晰）
- 性能上 Condition 通常略优（精确唤醒减少无效唤醒次数）

---

## 七、典型使用场景

| 场景 | 为什么用 Condition |
|------|-------------------|
| **有界阻塞队列**（`ArrayBlockingQueue` 内部） | 读写分组，精确唤醒 |
| **生产者-消费者** 多对多 | 同上 |
| **等待某个状态成立**（如资源就绪） | 比 `wait/notify` 灵活 |
| **读写锁**（`ReentrantReadWriteLock`） | 写锁可单独 `newCondition`，不被读互斥 |
| **阻塞线程池任务**（`LinkedBlockingQueue` 内部） | put/take 分别用一个 Condition |

---

## 八、面试话术模板

> "Condition 是 JUC 提供的等待/通知机制，是 `Object.wait()/notify()` 的多队列升级版。一把 ReentrantLock 可以 `newCondition()` 多次，每个 Condition 维护一条独立的等待队列（条件队列），存放 `await()` 释放锁并挂起的线程。`signal()` 把队首节点从条件队列迁移到 AQS 的 CLH 同步队列并 unpark 线程，被唤醒的线程再去 CLH 里抢锁。底层 await 做了四件事：加入条件队列 → 完全释放锁 → `LockSupport.park` 挂起 → 醒来后重新 `acquireQueued` 抢锁。signal 则做：校验持锁 → 摘队首 → 改状态 CONDITION→0 → enq 到 CLH 队尾 → unpark。
>
> 相对 `wait/notify`，Condition 的核心优势是**多队列 + 精确唤醒 + 显式绑定 Lock**，可以避免 `notifyAll` 的惊群效应。注意点是条件判断必须用 `while` 循环防止虚假唤醒，`signal` 之后必须 `unlock` 才能让被唤醒的线程真正抢到锁。"

---

## 关联知识点

- [[ReentrantLock详解]] — Condition 绑定的 Lock 实现，包含四大特性 + AQS 源码
- [[AQS 详解]] — ConditionObject 是 AQS 内部类，理解 CLH 队列很关键
- [[Java并发编程面试题]] — 线程间通信方式总览，Condition 是其中一种
- [[synchronized 关键字详解|synchronized 关键字]] — Condition 是 `wait/notify` 的升级替代
- [[线程池]] — `LinkedBlockingQueue` 内部就用 Condition 做 put/take 同步
- [[阻塞队列详解]] — ArrayBlockingQueue / LinkedBlockingQueue / SynchronousQueue / DelayQueue / PriorityBlockingQueue 五大实现 + Condition 应用（已建）
