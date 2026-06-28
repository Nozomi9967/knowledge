---
tags:
  - java
  - 并发
  - JUC
  - CountDownLatch
  - AQS
  - 共享模式
created: 2026-06-13
updated: 2026-06-13
source: [[AQS 详解]]
---

# CountDownLatch 详解

> 拆分自 [[AQS 详解]] 第七章「共享模式」与 [[Java并发编程面试题]] CountDownLatch 段落。本页展开倒计数门闩原理、`countDown` CAS 自减到 0 唤醒所有线程、`await` 共享模式源码、与 CyclicBarrier / Semaphore 对比、面试话术与踩坑点。
>
> 反向引用：[[AQS 详解]] · [[Semaphore详解]] · [[Java并发编程面试题]] · [[线程池]]

---

## 什么是 CountDownLatch

**CountDownLatch**（倒计时门闩）是 `java.util.concurrent` 包下的同步辅助类，允许**一个或多个线程等待其他线程完成操作**。它用一个**倒计数器**实现：构造时传入 N，调用 `countDown()` 减一，调用 `await()` 的线程**阻塞直到计数器归零**。

> [!info] CountDownLatch — Count Down Latch（倒计时门闩）
> 名字直译：**Count Down**（倒计时）+ **Latch**（门闩）。一扇门关着，倒计时归零时门闩松开，所有等的人一起放行。
>
> 通俗理解：把 CountDownLatch 想象成**运动会的起跑门**——裁判喊「各就各位」（构造 latch=N），每个运动员准备好了就 `countDown()` 报数，**当最后一个运动员也报完（N→0），起跑门闩弹开**，所有等的人一齐起跑。

### 核心三要素

| 要素 | 含义 |
|------|------|
| **count（初始计数）** | 构造时传入，表示「要等多少件事完成」 |
| **state（AQS）** | 当前剩余计数（子类覆写为「倒计数」语义） |
| **复用性** | ❌ **一次性**——state 归零后再 await 直接放行，不能 reset |

### 一句话 vs Semaphore

| 维度 | CountDownLatch | Semaphore |
|------|----------------|-----------|
| 状态语义 | **倒计数**（只能减） | 许可证（**可增可减**） |
| 能否复用 | ❌ 一次性 | ✅ release 加回去 |
| 典型场景 | 等待多任务完成 | 限流、连接池 |

---

## 一、核心 API 与基础范式

| 方法 | 行为 | 是否抛 `InterruptedException` |
|------|------|-------------------------------|
| `await()` | 阻塞直到 count=0 | ✅ |
| `await(time, unit)` | 最多等指定时间 | ✅ |
| `countDown()` | 计数减 1，**减到 0 时唤醒所有等者** | ❌ |
| `getCount()` | 查询当前计数 | — |

**标准范式（最常用：主线程等 N 个工作线程）：**

```java
int N = 3;
CountDownLatch latch = new CountDownLatch(N);

for (int i = 0; i < N; i++) {                          // 启动 N 个 worker
    new Thread(() -> {
        try {
            doWork();                                  // 干活
        } catch (Exception e) {
            // 业务异常处理
        } finally {
            latch.countDown();                         // 无论成败都计数
        }
    }, "worker-" + i).start();
}

latch.await();                                         // 主线程阻塞
System.out.println("所有 worker 完成，主线程继续");
```

> [!warning] 易错点
> - `countDown()` 必须放在 `finally` 里——异常路径也要计数，否则主线程**永远等不到**
> - CountDownLatch **不能 reset**——用完就废，需要循环用请改 `CyclicBarrier`
> - **多次 `await()` 各自独立**——多个等者互不影响

---

## 二、典型使用场景

### 2.1 主线程等所有子线程完成（最经典）

```java
public class MainWaitWorkers {
    public static void main(String[] args) throws InterruptedException {
        int N = 5;
        CountDownLatch latch = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 1000));
                    System.out.println("worker " + id + " done");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }, "worker-" + i).start();
        }

        latch.await();
        System.out.println("=== 所有 worker 完成，开始汇总 ===");
    }
}
```

### 2.2 多个线程等一个信号（开工门闩）

**反向用法：** 让所有 worker 等主线程的「开工」信号。

```java
CountDownLatch startGate = new CountDownLatch(1);     // 初始为 1

for (int i = 0; i < N; i++) {
    new Thread(() -> {
        startGate.await();                             // 都在起点等
        doWork();                                      // 同时起跑
    }, "worker-" + i).start();
}

Thread.sleep(2000);                                   // 准备时间
startGate.countDown();                                // 开门！
```

### 2.3 多个线程等多个线程（汇总门闩）

**两边都等：** 主线程等多个 worker 完成的子集。

```java
CountDownLatch done = new CountDownLatch(N);
CountDownLatch start = new CountDownLatch(1);         // 开工门闩

// worker: 等开工 → 干活 → 报完成
// main: 开门 → 等全部完成
```

### 2.4 最大并行耗时

```java
long start = System.nanoTime();
CountDownLatch latch = new CountDownLatch(N);
for (int i = 0; i < N; i++) executor.submit(() -> {
    doWork();
    latch.countDown();
});
latch.await();
long elapsedMs = (System.nanoTime() - start) / 1_000_000;  // 测并发总耗时
```

### 2.5 并发测试框架

JUnit 风格的「先起 N 个线程等信号 → 一起执行 → 测响应时间」。

---

## 三、底层原理：共享模式 AQS

`CountDownLatch` 内部类 `Sync extends AQS`：

```
CountDownLatch
   └── 内部类 Sync extends AQS
         └── 实现 tryAcquireShared / tryReleaseShared
```

| 与 Semaphore 差异 | CountDownLatch | Semaphore |
|------------------|----------------|-----------|
| state 语义 | **倒计数**（只能减） | 许可证数（可增可减） |
| 初始 state | N | N |
| 释放后还能加吗 | **❌ 不能再加**（`tryReleaseShared` 返回值精妙设计） | ✅ 永远返回 true |
| 公平选项 | ❌ | ✅ |
| 钩子返回值 | `count == 0 ? 1 : -1` | `remaining` |

### 3.1 `tryAcquireShared` 源码（精妙 ⭐）

```java
// CountDownLatch.Sync.tryAcquireShared
protected int tryAcquireShared(int acquires) {
    return (getState() == 0) ? 1 : -1;                 // 一行代码的精华
}
```

**绝妙之处：**
- 计数归零就返回 **1**（成功）
- 计数没归零就返回 **-1**（失败）
- **不需要 CAS**——countDown 已经把 state 改了，await 只读不写
- `await()` 调一次后，**所有等者一齐放行**——返回 1 后 AQS 会做 `doAcquireShared` 链式唤醒

### 3.2 `tryReleaseShared` 源码（更精妙 ⭐⭐）

```java
// CountDownLatch.Sync.tryReleaseShared
protected boolean tryReleaseShared(int releases) {
    for (;;) {                                          // CAS 自旋
        int c = getState();
        if (c == 0)                                     // 已经是 0？直接返回
            return false;                               // 关键：返回 false 防止 state 变负
        int nextc = c - 1;
        if (compareAndSetState(c, nextc))
            return nextc == 0;                          // 减到 0 才返回 true，触发唤醒
    }
}
```

**绝妙之处：**
- `c == 0` 立刻返回 **false**——**防止 state 减成负数**（如果 countDown 调用次数 > N，state 不会变）
- `compareAndSetState` 失败自旋——并发安全
- **只有减到 0 才返回 true**——返回 true 时 AQS 才会调 `doReleaseShared` 唤醒所有

### 3.3 `countDown` → `await` 完整流程

```
Worker1: latch.countDown()  →  state: N → N-1
Worker2: latch.countDown()  →  state: N-1 → N-2
...
WorkerN: latch.countDown()  →  state: 1 → 0  ★ tryReleaseShared 返回 true
                                    ↓
                              doReleaseShared 唤醒所有 await 线程
                                    ↓
                              所有 await 线程一齐放行
```

### 3.4 `await` 源码

```java
public void await() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    if (tryAcquireShared(arg) < 0)                      // 1 ? -1（绝大多数情况是 -1）
        doAcquireSharedInterruptibly(arg);
}
```

**和 `acquireShared` 区别：支持中断响应**——`await()` 抛 `InterruptedException`，区别于 Semaphore 的 `acquire()`。

### 3.5 节点类型：`Node.SHARED`

`doAcquireSharedInterruptibly` 入队时节点标记为 `Node.SHARED`——和 Semaphore 一样。

---

## 四、完整可运行示例：主线程等 3 个 worker

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CountDownLatchDemo {

    public static void main(String[] args) throws InterruptedException {
        int N = 3;
        CountDownLatch latch = new CountDownLatch(N);
        long start = System.nanoTime();

        for (int i = 1; i <= N; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.println("[worker-" + id + "] 开始干活");
                    TimeUnit.MILLISECONDS.sleep((long) (Math.random() * 1000));
                    System.out.println("[worker-" + id + "] 干完");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                    System.out.println("[worker-" + id + "] 报数, 剩余: " + latch.getCount());
                }
            }, "worker-" + i).start();
        }

        System.out.println("[main] 等所有 worker...");
        latch.await();                                  // 阻塞
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[main] 全部完成！耗时: " + elapsedMs + "ms");
    }
}
```

**预期输出（节选）：**
```
[main] 等所有 worker...
[worker-1] 开始干活
[worker-2] 开始干活
[worker-3] 开始干活
[worker-2] 干完
[worker-2] 报数, 剩余: 2
[worker-1] 干完
[worker-1] 报数, 剩余: 1
[worker-3] 干完
[worker-3] 报数, 剩余: 0   ★ 减到 0，唤醒 main
[main] 全部完成！耗时: 800ms
```

---

## 五、与 CyclicBarrier 全面对比

| 维度 | CountDownLatch | CyclicBarrier |
|------|----------------|----------------|
| 计数方向 | **N → 0**（减） | **0 → N → 0**（加再减到 0） |
| 能否复用 | ❌ 一次性 | ✅ 循环用（reset） |
| 参与的线程 | **N 个 worker + 1 个等者**（不对称） | **N 个 worker 互相等**（对称） |
| 触发动作 | 等者被唤醒 | 所有线程到达屏障，**触发可选 barrierAction** |
| 谁来调用 | worker 调 `countDown`，main 调 `await` | **所有线程都调 `await`** |
| 异常处理 | 某 worker 挂了不影响其他 worker | **某线程挂了，其他等者抛 BrokenBarrierException** |
| 底层 | AQS 共享模式 | **ReentrantLock + Condition** |
| 适用场景 | 主线程等子线程 | 多线程分阶段协同 |

**口诀：「CountDownLatch 是发令枪，CyclicBarrier 是接力赛交接棒」。**

### 典型对比场景

```java
// CountDownLatch: 主线程等 N 个 worker 完成后汇总
CountDownLatch latch = new CountDownLatch(3);
for (int i = 0; i < 3; i++) new Thread(() -> {
    doWork();
    latch.countDown();                                 // worker 报数
}).start();
latch.await();                                        // main 等

// CyclicBarrier: N 个线程互相等，3 阶段循环
CyclicBarrier barrier = new CyclicBarrier(3);
for (int i = 0; i < 3; i++) new Thread(() -> {
    doPhase1();
    barrier.await();                                   // 3 个线程都到这才往下走
    doPhase2();
    barrier.await();
    doPhase3();
}).start();
```

---

## 六、与 Semaphore 全面对比

| 维度 | CountDownLatch | Semaphore |
|------|----------------|-----------|
| 状态语义 | **倒计数**（只能减） | 许可证数（可增可减） |
| 复用性 | ❌ 一次性 | ✅ |
| 公平选项 | ❌ | ✅ |
| 同时持有者 | N 个 worker + 1 个等者 | **最多 N 个**并发 |
| 节点类型 | `Node.SHARED` | `Node.SHARED` |
| 唤醒时机 | count=0 时**唤醒所有** | release 时唤醒一个 |

---

## 七、面试高频踩坑

### 7.1 为什么 `tryAcquireShared` 不需要 CAS？

`countDown` 已经把 state 改了，await 是个**只读操作**。读 state 看是不是 0，0 就放行，不 0 就阻塞。**没有并发问题**——因为多个 await 线程都在等同一个 state=0 的事件。

### 7.2 为什么 `tryReleaseShared` 要返回 `false` 当 `c == 0`？

**防止 state 变负数**——如果 countDown 调用次数超过 N（业务 bug），state 不能减到 -1。返回 false 让 AQS 啥也不做（不唤醒、不抛错），相当于「无害化吞掉」。

### 7.3 能不能 reset？

**不能**。CountDownLatch 源码没有 `reset()` 方法。**需要循环用请用 CyclicBarrier**，或者业务上自己包一层「latch 数组 + 索引」。

### 7.4 `await(time, unit)` 超时返回什么？

返回 `boolean`——`true` 表示计数归零、`false` 表示超时。但**不抛异常**（不像 `await()` 抛 `InterruptedException`）。

### 7.5 多个等者会一齐醒吗？

**会**。`doReleaseShared` 链式唤醒所有 SHARED 节点。但**这些线程抢锁的顺序**不保证 FIFO（和 Semaphore 一样）。

### 7.6 `countDown` 调多次会怎样？

超过 N 的 `countDown` 会被**静默忽略**（见 7.2）。`getCount()` 始终 ≥ 0。

### 7.7 CountDownLatch 能不能完全替代 join？

| 维度 | `Thread.join()` | CountDownLatch |
|------|-----------------|----------------|
| 等待多个线程 | ❌ 一次只能等一个 | ✅ 一次等 N 个 |
| 灵活度 | 低 | 高（可以多个等者） |
| 中断响应 | 抛 `InterruptedException` | 抛 `InterruptedException` |
| 资源占用 | 调一次 join 等一个线程 | 一个 latch 等 N 个线程 |

**推荐：需要等 N 个线程用 CountDownLatch；只需要等一个线程用 join。**

---

## 八、面试话术模板

> "CountDownLatch 是 JUC 的倒计时门闩，用来让一个或多个线程等待其他线程完成操作。构造时传入 N，线程调 `countDown()` 把 state 减 1，调 `await()` 的线程阻塞直到 state 归零。
>
> 底层基于 AQS 共享模式。它的精妙之处在于两个钩子方法：**`tryAcquireShared` 返回 (state==0)?1:-1**——一行代码的精华，因为 await 是只读操作，根本不需要 CAS；**`tryReleaseShared` 在 c==0 时返回 false**——防止 countDown 调用次数过多导致 state 变负数。只有减到 0 时才返回 true，触发 `doReleaseShared` 链式唤醒所有等待者。
>
> 相对 CyclicBarrier，CountDownLatch 是**一次性**的（不能 reset），参与方式**不对称**（N 个 worker + 1 个等者），典型场景是主线程等子线程完成后再汇总，或者多个线程等一个开工信号。注意点是 `countDown` 必须放 finally 里，否则异常路径不计数，主线程永远等不到。"

---

## 关联知识点

- [[AQS 详解]] — CountDownLatch 是共享模式 + 一次性门闩，`tryReleaseShared` 返回值设计精妙
- [[Semaphore详解]] — 共享模式另一个案例，对比理解「可累加 vs 一次性」
- [[ReentrantLock详解]] — 独占模式代表
- [[CyclicBarrier详解]] — CountDownLatch 的循环版 + 对称等（已建：Generation 代际 + barrierAction + ReentrantLock 实现）
- [[线程池]] — CountDownLatch 经常配合线程池测并发耗时
- [[Java并发编程面试题]] — 并发编程主文件，CountDownLatch 段已简述
