---
tags:
  - java
  - 并发
  - JUC
  - Semaphore
  - AQS
  - 共享模式
created: 2026-06-13
updated: 2026-06-13
source: [[AQS 详解]]
---

# Semaphore 详解

> 拆分自 [[AQS 详解]] 第七章「共享模式」与 [[Java并发编程面试题]] Semaphore 段落。本页展开 Semaphore 的原理、`acquireShared` / `releaseShared` / `doReleaseShared` 链式唤醒源码、Permit 许可证机制、与 CountDownLatch 对比、面试话术与踩坑点。
>
> 反向引用：[[AQS 详解]] · [[CountDownLatch详解]] · [[Java并发编程面试题]] · [[线程池]]

---

## 什么是 Semaphore

**Semaphore**（计数信号量）是 `java.util.concurrent` 包下的同步器，用来**控制同时访问特定资源的线程数量**。它维护一个**许可（permit）计数器**：线程访问资源前 `acquire()` 获取许可，访问后 `release()` 释放许可；许可数为 0 时新线程必须等待。

> [!info] Semaphore — Semaphore（信号量）
> 1965 年 Dijkstra 提出的同步原语，本质是**带计数的互斥**——mutex 是 0/1 的特例，Semaphore 是 N 的泛化。
>
> 通俗理解：把 Semaphore 想象成**餐厅的座位**——餐厅有 N 个座位（permits=N），顾客进门先拿号（acquire），座位满了就在门口等；顾客吃完离开（release）腾出座位，门口的下一位才能进。**Semaphore 控制的是「同一时间多少线程能进」，而不是「谁进谁出」。**

### 核心三要素

| 要素 | 含义 |
|------|------|
| **permits（许可数）** | 构造函数传入，表示「同一时刻最多 N 个线程能进入」 |
| **state（AQS）** | 当前剩余许可数（子类覆写为「许可计数」语义） |
| **公平性** | `new Semaphore(N, true)` 是公平锁，按 FIFO；默认非公平 |

### 一句话 vs CountDownLatch

| 维度 | Semaphore | CountDownLatch |
|------|-----------|----------------|
| 状态语义 | **许可证数**（可增可减） | **倒计数**（只能减，不可增） |
| 能否复用 | ✅ `release()` 加回去 | ❌ state=0 后一蹶不振 |
| 典型场景 | 限流、连接池、并发访问控制 | 等待多任务完成 |

---

## 一、核心 API 与基础范式

| 方法 | 行为 | 是否可中断 |
|------|------|-----------|
| `acquire()` | 获取 1 个许可，**阻塞**直到拿到 | ❌ |
| `acquire(int n)` | 获取 n 个许可 | ❌ |
| `acquireUninterruptibly()` | 同 acquire，**不可中断** | ❌ |
| `tryAcquire()` | 立即尝试，返回 boolean | — |
| `tryAcquire(time, unit)` | 在指定时间内尝试 | ✅ |
| `release()` | 释放 1 个许可，**唤醒一个等待者** | — |
| `release(int n)` | 释放 n 个许可 | — |
| `availablePermits()` | 当前剩余许可数 | — |

**标准范式：**

```java
Semaphore semaphore = new Semaphore(3);     // 限流 3 个并发
try {
    semaphore.acquire();                    // 拿到许可才能进
    // 临界区（受保护的资源）
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    semaphore.release();                    // 务必在 finally 释放
}
```

> [!warning] 易错点
> - `release()` **必须**放在 `finally` 里——否则异常路径不会释放，后续线程永远等不到
> - 每次 `acquire` 必须对应一次 `release`（或 `acquire(n)` 对应 `release(n)`）
> - **许可证可以累加**：连续 release 不加 acquire，许可证数会**超过初始值**——这是合法的（不是 bug，是特性）

---

## 二、典型使用场景

### 2.1 限流：最多 3 个并发

```java
public class RateLimiter {
    private final Semaphore semaphore = new Semaphore(3);

    public void handleRequest() throws InterruptedException {
        semaphore.acquire();
        try {
            processBusiness();              // 限流保护的业务
        } finally {
            semaphore.release();
        }
    }
}
```

### 2.2 数据库连接池：最多 N 个连接

```java
public class ConnectionPool {
    private final Semaphore semaphore;
    private final List<Connection> pool;

    public ConnectionPool(int size) {
        pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) pool.add(createConnection());
        semaphore = new Semaphore(size);     // 许可证 = 连接数
    }

    public Connection get() throws InterruptedException {
        semaphore.acquire();                 // 没连接就阻塞
        return checkoutFromPool();
    }

    public void returnConn(Connection c) {
        checkinToPool(c);
        semaphore.release();                 // 归还许可证
    }
}
```

### 2.3 互斥（mutex）的泛化

```java
// mutex = 许可证为 1 的 Semaphore
Semaphore mutex = new Semaphore(1);
mutex.acquire();                             // 临界区开始
// ... 业务 ...
mutex.release();                             // 临界区结束
```

### 2.4 资源准入控制

```java
// 只允许 5 个线程同时打印日志（避免日志框架被打爆）
Semaphore logPermit = new Semaphore(5);
```

---

## 三、底层原理：共享模式 AQS

`Semaphore` 内部类 `Sync extends AQS`：

```
Semaphore
   ├── 内部类 Sync extends AQS
   │      ├── NonfairSync（默认）
   │      └── FairSync
   └── 实现 java.util.concurrent.Semaphore 接口
```

| 与 ReentrantLock 差异 | Semaphore | ReentrantLock |
|----------------------|-----------|---------------|
| AQS 模式 | **共享（Shared）** | 独占（Exclusive） |
| state 语义 | **剩余许可数** | 重入次数 |
| 加锁方法 | `acquireShared` | `acquire` |
| 释放方法 | `releaseShared` | `release` |
| 钩子 | `tryAcquireShared` / `tryReleaseShared` | `tryAcquire` / `tryRelease` |

### 3.1 `tryAcquireShared` 源码

```java
// Semaphore.NonfairSync.tryAcquireShared
protected int tryAcquireShared(int acquires) {
    return nonfairTryAcquireShared(acquires);
}

final int nonfairTryAcquireShared(int acquires) {
    for (;;) {                                              // CAS 自旋
        int available = getState();                         // 剩余许可
        int remaining = available - acquires;               // 拿完后剩多少
        if (remaining < 0 || compareAndSetState(available, remaining)) {
            return remaining;                               // 返回剩余（<0 表示失败）
        }
        // CAS 失败就重试
    }
}
```

**关键点：**
- `acquireShared` 要求返回**剩余许可数**（>=0 成功，<0 失败）
- 非公平版**不检查队列**——允许插队
- CAS 失败就自旋重试

### 3.2 公平版 `tryAcquireShared` 差异

```java
// Semaphore.FairSync.tryAcquireShared
protected int tryAcquireShared(int acquires) {
    for (;;) {
        if (hasQueuedPredecessors())        // ★ 队列里有人就放弃
            return -1;
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 || compareAndSetState(available, remaining))
            return remaining;
    }
}
```

**公平就多了一个 `hasQueuedPredecessors()`**——和 ReentrantLock 公平版一脉相承。

### 3.3 `tryReleaseShared` 源码

```java
// Semaphore.Sync.tryReleaseShared
protected final boolean tryReleaseShared(int releases) {
    for (;;) {                              // CAS 自旋
        int current = getState();
        int next = current + releases;      // 释放 = state + n
        compareAndSetState(current, next);
        return true;                        // Semaphore 永远返回 true（许可证可以无限累加）
    }
}
```

**注意：** 释放永远成功（许可证可以累加超过初始值），不像 CountDownLatch 释放到 0 后就废了。

### 3.4 `acquireShared` 完整流程（AQS）

```java
// AQS.acquireShared
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)          // 失败才走完整流程
        doAcquireShared(arg);               // 入队 + park
}
```

`doAcquireShared` 和 `acquireQueued` 很像，**但有链式唤醒**：

```java
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);   // 节点标记为 SHARED
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);  // 前驱是 head 才抢
                if (r >= 0) {                  // 抢到了
                    setHeadAndPropagate(node, r);  // ★ 关键：升 head + 唤醒下一个 SHARED
                    p.next = null;
                    if (interrupted) selfInterrupt();
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed) cancelAcquire(node);
    }
}
```

**和独占模式最大的差异：`setHeadAndPropagate`——一个线程抢到锁后，会尝试唤醒下一个 SHARED 节点**。

### 3.5 `setHeadAndPropagate`：共享模式的链式唤醒 ⭐

```java
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head;
    setHead(node);                                // 自己升为 head
    if (propagate > 0 || h == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())            // 下一个是 SHARED 节点
            doReleaseShared();                    // ★ 链式唤醒
    }
}
```

**为什么需要链式唤醒？** 因为**共享模式下可能有多个线程能同时拿到资源**——一个释放时，要让下一个等者也能进来。

### 3.6 `doReleaseShared`：唤醒 SHARED 后继

```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) continue;
                unparkSuccessor(h);               // 唤醒后继
            }
            else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                          // 改成 PROPAGATE 保证唤醒传播
        }
        if (h == head) break;                      // 期间 head 没变才退出
    }
}
```

**核心循环：** 不断检查 head 状态，唤醒后继，直到 head 不再变化。`PROPAGATE` 状态保证**唤醒信号能向后传播**（即使本轮释放后下一个线程还来不及 park，下一轮也会被唤醒）。

---

## 四、完整可运行示例：3 个车位、10 辆车

```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ParkingDemo {

    private static final Semaphore spot = new Semaphore(3);   // 3 个车位

    public static void main(String[] args) {
        for (int i = 1; i <= 10; i++) {
            final int carId = i;
            new Thread(() -> {
                try {
                    System.out.println("🚗 车辆 " + carId + " 找车位...");
                    spot.acquire();
                    System.out.println("✅ 车辆 " + carId + " 停进车位 (剩余: " + spot.availablePermits() + ")");
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    spot.release();
                    System.out.println("🚙 车辆 " + carId + " 离开 (剩余: " + spot.availablePermits() + ")");
                }
            }, "car-" + i).start();
        }
    }
}
```

**预期输出（节选）：**
```
🚗 车辆 1 找车位...
🚗 车辆 2 找车位...
🚗 车辆 3 找车位...
✅ 车辆 1 停进车位 (剩余: 2)
✅ 车辆 2 停进车位 (剩余: 1)
✅ 车辆 3 停进车位 (剩余: 0)
🚗 车辆 4 找车位...   ← 阻塞
...
🚙 车辆 1 离开 (剩余: 0)
✅ 车辆 4 停进车位 (剩余: 0)   ← 被唤醒
```

---

## 五、与 CountDownLatch 全面对比

| 维度 | Semaphore | CountDownLatch |
|------|-----------|----------------|
| 状态语义 | **许可证数**（可增可减） | 倒计数（**只能减**） |
| 能否复用 | ✅ release 加回去 | ❌ 一次性 |
| 初始 state | 构造时指定（N） | 构造时指定（N），**用完就废** |
| 锁定的线程 | **多个**（最多 N 个并发） | **多个**（都在等 state=0） |
| 加锁方向 | 拿许可才能进 | 倒数结束后等者才能进 |
| 释放方向 | release 唤醒等者 | countDown 减计数 |
| 共享模式 | ✅ | ✅ |
| 公平选项 | ✅ | ❌（无公平概念） |
| 典型场景 | 限流、连接池 | 等待多任务完成 |

**口诀：「Semaphore 管进，CountDownLatch 管等」。**

---

## 六、面试高频踩坑

### 6.1 为什么 `release()` 永远返回 true？

Semaphore 的许可证**可以累加**：`acquire(1)` + `release(1)` + `release(1)` 会让 state 从 1 升到 2。**这是设计**——表示「我把资源加回去了」。如果业务希望「许可证不能超过初始值」，需要自己用 `availablePermits()` 做检查：

```java
public boolean tryRelease(int n) {
    int current = getState();
    if (current + n > MAX_PERMITS) return false;     // 业务层校验
    compareAndSetState(current, current + n);
    return true;
}
```

### 6.2 Semaphore 公平和非公平怎么选？

| 场景 | 推荐 |
|------|------|
| 严格 FIFO，避免饥饿 | `new Semaphore(N, true)` |
| 高吞吐，许可不紧张 | `new Semaphore(N)`（默认非公平） |

### 6.3 许可证没释放会怎样？

**永久泄露**——其他线程永远拿不到许可。`finally` 里 release 是铁律。

### 6.4 `acquire(n)` 和 `acquire()` 区别？

`acquire(n)` 一次性拿 n 个许可，**原子操作**——拿不到 n 个就阻塞，**不会只拿部分**。

### 6.5 为什么 `tryAcquireShared` 返回 int 而不是 boolean？

返回**剩余许可数**给 AQS，负数表示失败。`acquireShared` 看返回值判断是否入队。

### 6.6 Semaphore 的 `release()` 唤醒谁？

**按 FIFO** 唤醒等待队列里的下一个 SHARED 节点。但**抢到许可的顺序**不保证 FIFO——`tryAcquireShared` 是 CAS，公平锁也要看 `hasQueuedPredecessors`。

---

## 七、共享模式 vs 独占模式 一览

| 维度 | 共享（Shared） | 独占（Exclusive） |
|------|----------------|-------------------|
| 钩子 | `tryAcquireShared` / `tryReleaseShared` | `tryAcquire` / `tryRelease` |
| 加锁 | `acquireShared` | `acquire` |
| 释放 | `releaseShared` | `release` |
| 同时持有者 | N 个 | 1 个 |
| 节点类型 | `Node.SHARED` | `Node.EXCLUSIVE` |
| 链式唤醒 | ✅ `setHeadAndPropagate` | ❌ |
| waitStatus 状态 | 用到 `PROPAGATE` | 不需要 |
| 典型子类 | Semaphore、CountDownLatch、ReentrantReadWriteLock.ReadLock | ReentrantLock、ReentrantReadWriteLock.WriteLock |

---

## 八、典型使用场景汇总

| 场景 | Semaphore 用法 |
|------|----------------|
| **接口限流** | 限制 N 个并发请求 |
| **数据库连接池** | 许可证 = 连接数 |
| **线程池并发控制** | 限制同时运行的 task 数 |
| **资源准入** | 限制访问某个稀缺资源的线程数 |
| **分布式限流的本地实现** | 单机版漏桶/令牌桶 |
| **生产者-消费者** | 限制生产和消费的并发度 |

---

## 九、面试话术模板

> "Semaphore 是 JUC 的计数信号量，用来控制同时访问特定资源的线程数量。它底层基于 AQS 的**共享模式**——state 表示剩余许可证数，初始为构造时传入的 permits。线程通过 `acquire()` 获取许可（CAS 减 state），许可证为 0 时阻塞；通过 `release()` 归还许可（CAS 加 state），并唤醒等待队列。
>
> Semaphore 内部 Sync 类有两个版本：NonfairSync（默认）不检查队列直接 CAS 抢，性能高；FairSync 调用 `hasQueuedPredecessors()`，按 FIFO 排队。和 ReentrantLock 的独占模式不同，Semaphore 用的是共享模式，多个线程能同时持有许可证。`releaseShared` 时调 `doReleaseShared` 链式唤醒后继——这叫 setHeadAndPropagate，保证一个释放后下一个 SHARED 节点也能进。
>
> 相对 CountDownLatch，Semaphore 的核心优势是**许可证可累加、可复用**，既能限流（'控制谁能进'）也能互斥（许可证=1 退化）。典型场景有限流、连接池、并发准入控制。注意点是 `release()` 必须放 finally 里，否则异常路径不释放，许可证就永久泄露了。"

---

## 关联知识点

- [[AQS 详解]] — Semaphore 是共享模式经典案例，`setHeadAndPropagate` 链式唤醒详述
- [[CountDownLatch详解]] — 共享模式的另一个经典案例（一次性 vs 可累加）
- [[ReentrantLock详解]] — 独占模式代表，对比理解两种模式差异
- [[Condition 详解]] — Semaphore 不使用 Condition，只用共享模式
- [[Java并发编程面试题]] — 并发编程主文件，Semaphore 段已简述
- [[线程池]] — 可用 Semaphore 实现限流版线程池
