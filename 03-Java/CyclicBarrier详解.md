---
tags:
  - java
  - 并发
  - JUC
  - CyclicBarrier
  - Condition
  - ReentrantLock
created: 2026-06-13
updated: 2026-06-13
source: [[CountDownLatch详解]]
---

# CyclicBarrier 详解

> 拆分自 [[CountDownLatch详解]] 第七章对比表与 [[Java并发编程面试题]] CyclicBarrier 段落。本页展开回环屏障原理、Generation 代际机制、ReentrantLock + Condition 实现、`await` 源码、可选 barrierAction、与 CountDownLatch / Phaser 对比、面试话术与踩坑点。
>
> 反向引用：[[CountDownLatch详解]] · [[ReentrantLock详解]] · [[Condition 详解]] · [[AQS 详解]] · [[Java并发编程面试题]] · [[线程池]]

---

## 什么是 CyclicBarrier

**CyclicBarrier**（回环屏障）是 `java.util.concurrent` 包下的同步辅助类，允许**一组线程相互等待**，直到所有线程都到达某个公共屏障点后**才一起放行**。**屏障可以循环使用**——一拨线程放行后，下一拨又能用同一个 barrier 继续等。

> [!info] CyclicBarrier — Cyclic Barrier（回环屏障）
> 名字直译：**Cyclic**（循环的）+ **Barrier**（屏障）。**可循环使用的屏障**——一拨人全到了就开门，下一拨人能接着用。
>
> 通俗理解：把 CyclicBarrier 想象成**登山队的集合点**——队长说「**所有人都到齐才能出发**」。每个队员到达后 await，直到所有 N 个队员都到齐，屏障打开（同时触发可选的集合动作，如「拍合照」）。**到达山顶后队员继续往下一段走**——下一段又一个集合点，能**循环用同一个 barrier**。

### 核心三要素

| 要素 | 含义 |
|------|------|
| **parties** | 参与的线程数（构造时指定） |
| **count** | 当前代际剩余未到达的线程数（**到达一个减一个**） |
| **Generation** | 代际（**每次全员放行就生成新一代**）——循环用的关键 |

### 一句话 vs CountDownLatch

| 维度 | CyclicBarrier | CountDownLatch |
|------|---------------|----------------|
| 计数方向 | **N → 0**（**每次 await 减**） | **N → 0**（每次 countDown 减） |
| 能否复用 | ✅ **循环用**（reset） | ❌ 一次性 |
| 参与方式 | **所有线程都调 await**（对称） | N 个 countDown + 1 个 await（不对称） |
| 触发动作 | ✅ 可选 barrierAction | ❌ 无 |
| 异常处理 | 某线程挂了，**其他等者抛 BrokenBarrierException** | 某 worker 挂了不影响其他 |
| 底层 | **ReentrantLock + Condition** | AQS 共享模式 |

---

## 一、核心 API 与基础范式

| 方法 | 行为 | 是否抛异常 |
|------|------|-----------|
| `await()` | 阻塞直到所有线程都到达 | ✅ `InterruptedException` / `BrokenBarrierException` |
| `await(time, unit)` | 超时等待 | ✅ `TimeoutException` / `BrokenBarrierException` |
| `getParties()` | 参与方数量 | — |
| `getNumberWaiting()` | 当前在屏障上等待的线程数 | — |
| `isBroken()` | 屏障是否被打破 | — |
| `reset()` | 手动重置屏障到初始状态 | — |

**标准范式（最经典：多阶段并行计算）：**

```java
int N = 3;
CyclicBarrier barrier = new CyclicBarrier(N, () -> {
    System.out.println("=== 所有人到齐，做点收尾工作 ===");
});

for (int i = 0; i < N; i++) {
    new Thread(() -> {
        try {
            System.out.println(Thread.currentThread().getName() + " 准备...");
            Thread.sleep((long) (Math.random() * 1000));
            System.out.println(Thread.currentThread().getName() + " 到达屏障");
            barrier.await();                        // ★ 关键：所有线程都 await 才能继续
            System.out.println(Thread.currentThread().getName() + " 一起进入下一阶段");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }, "worker-" + i).start();
}
```

> [!warning] 易错点
> - 构造时 parties **必须 ≥ 实际 await 线程数**，否则永远等不到全员
> - 某线程 await 时**被 interrupt 或超时**，整个屏障**会被打破**——所有其他等者抛 `BrokenBarrierException`
> - `barrierAction` 由**最后一个到达的线程**执行——在 await 返回前执行

---

## 二、典型使用场景

### 2.1 多阶段并行计算（最经典）

```java
// 场景：3 个 worker 协作，3 个阶段，每阶段开始前同步
CyclicBarrier barrier = new CyclicBarrier(3);

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        // 阶段 1
        doPhase1Work();
        awaitAt(barrier);                          // 等其他人
        // 阶段 2
        doPhase2Work();
        awaitAt(barrier);
        // 阶段 3
        doPhase3Work();
    }).start();
}
```

**vs CountDownLatch：** CountDownLatch 只能等一次；CyclicBarrier 能**循环用同一对象**做 N 阶段。

### 2.2 并行迭代(Map-Reduce 风格)

```java
// 场景：4 个 worker 并行处理 4 块数据，到齐后汇总
CyclicBarrier barrier = new CyclicBarrier(4, () -> {
    System.out.println("所有 worker 算完了，开始汇总");
    mergeResults();
});

for (int i = 0; i < 4; i++) {
    final int id = i;
    new Thread(() -> {
        Result r = computeChunk(id);
        results[id] = r;
        try { barrier.await(); } catch (Exception e) {}
    }).start();
}
```

### 2.3 模拟高并发压测

```java
// 让 N 个线程在同一瞬间起跑，模拟瞬时高并发
CyclicBarrier startGate = new CyclicBarrier(100);
ExecutorService pool = Executors.newFixedThreadPool(100);
for (int i = 0; i < 100; i++) {
    pool.submit(() -> {
        startGate.await();                         // 100 个线程都到这才起跑
        doRequest();                              // 瞬时高并发
    });
}
```

### 2.4 barrierAction 收集阶段性结果

```java
// barrierAction 特别适合做「阶段末的统一处理」
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    // 在第 3 个线程 await() 里执行（不在新线程）
    sendEmailToAdmin("阶段完成通知");
});
```

---

## 三、底层原理：ReentrantLock + Condition

`CyclicBarrier` 内部**没有用 AQS**——它直接用 **`ReentrantLock` + 一个 `Condition` + `Generation` 代际对象**。

```
CyclicBarrier
├── ReentrantLock lock           (互斥访问内部状态)
├── Condition trip               (所有等者挂在这一把 Condition 上)
├── int parties                  (参与方数)
├── int count                    (当前代际剩余未到达数)
└── Generation generation        (代际：每次全员放行 +1)
```

### 3.1 核心字段

```java
private static class Generation {
    boolean broken = false;       // 当前代际是否被打破
}

private final ReentrantLock lock = new ReentrantLock();
private final Condition trip = lock.newCondition();
private final int parties;
private final Runnable barrierCommand;        // 可选 barrierAction
private Generation generation = new Generation();
private int count;                            // 当前代际剩余未到达数
```

### 3.2 `await()` 源码

```java
public int await() throws InterruptedException, BrokenBarrierException {
    try {
        return dowait(false, 0L);
    } catch (TimeoutException toe) {
        throw new Error(toe);                  // 不会发生（dowait 用 false）
    }
}

private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        final Generation g = generation;        // ★ 进入临界区，先记下当前代际

        if (g.broken)
            throw new BrokenBarrierException();

        if (Thread.interrupted()) {
            breakBarrier(false);               // 中断 → 打破屏障
            throw new InterruptedException();
        }

        int index = --count;                   // ★ 关键：自己来了 count--
        if (index == 0) {                      // 自己是最后一个到的
            boolean ranAction = false;
            try {
                final Runnable command = barrierCommand;
                if (command != null)
                    command.run();             // ★ 由最后一个线程执行 barrierAction
                nextGeneration();              // ★ 进入下一代
                ranAction = true;
                return 0;
            } finally {
                if (!ranAction)
                    breakBarrier(false);
            }
        }

        // 还没到齐，循环等待
        for (;;) {
            try {
                if (!timed)
                    trip.await();              // 挂在 trip Condition 上
                else if (nanos > 0L)
                    nanos = trip.awaitNanos(nanos);
            } catch (InterruptedException ie) {
                if (g == generation && !g.broken)
                    breakBarrier(false);
                throw ie;
            }

            if (g.broken)
                throw new BrokenBarrierException();

            if (g != generation)                // 被唤醒且代际变了 = 全员到齐放行
                return index;

            if (timed && nanos <= 0L) {        // 超时
                breakBarrier(false);
                throw new TimeoutException();
            }
        }
    } finally {
        lock.unlock();
    }
}
```

### 3.3 `nextGeneration`：循环的关键

```java
private void nextGeneration() {
    trip.signalAll();                          // 唤醒所有等者
    count = parties;                           // 重置 count
    generation = new Generation();              // ★ 新代际
}
```

**关键三步：**
1. `trip.signalAll()`——唤醒所有等者
2. `count = parties`——重置剩余计数
3. `generation = new Generation()`——**新代际**（旧代际还在等者引用了旧对象，会发现 `g != generation` 而放行）

### 3.4 `breakBarrier`：打破屏障

```java
private void breakBarrier(boolean timed) {
    generation.broken = true;                  // 标记当前代际为 broken
    count = parties;
    trip.signalAll();                          // 唤醒所有等者
}
```

被唤醒的等者看到 `g.broken` 抛 `BrokenBarrierException`——**不会放行**。

---

## 四、完整可运行示例：3 worker 多阶段

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class CyclicBarrierDemo {

    public static void main(String[] args) {
        int N = 3;
        CyclicBarrier barrier = new CyclicBarrier(N, () -> {
            System.out.println("=== 全员到齐（" + System.currentTimeMillis() + "），拍合照 ===");
        });

        for (int i = 1; i <= N; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int phase = 1; phase <= 2; phase++) {   // 2 个阶段，循环用 barrier
                        System.out.println("[worker-" + id + "] 阶段 " + phase + " 准备...");
                        TimeUnit.MILLISECONDS.sleep((long) (Math.random() * 1000));
                        System.out.println("[worker-" + id + "] 阶段 " + phase + " 到达屏障");
                        barrier.await();
                        System.out.println("[worker-" + id + "] 阶段 " + phase + " 一起出发");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "worker-" + id).start();
        }
    }
}
```

**预期输出（节选）：**
```
[worker-1] 阶段 1 准备...
[worker-2] 阶段 1 准备...
[worker-3] 阶段 1 准备...
[worker-1] 阶段 1 到达屏障
[worker-2] 阶段 1 到达屏障
[worker-3] 阶段 1 到达屏障
=== 全员到齐（1700000000000），拍合照 ===           ← barrierAction
[worker-1] 阶段 1 一起出发
[worker-2] 阶段 1 一起出发
[worker-3] 阶段 1 一起出发
...（阶段 2 同上）...
```

**注意：「拍合照」由最后一个到的 worker（worker-3）执行**——这点是源码层面确定的，不是新线程。

---

## 五、与 CountDownLatch 全面对比

| 维度 | CyclicBarrier | CountDownLatch |
|------|---------------|----------------|
| 计数方向 | **N → 0**（await 时减） | N → 0（countDown 时减） |
| 能否复用 | ✅ **循环用**（Generation） | ❌ 一次性 |
| 参与方式 | **N 个线程都调 await**（对称） | N 个 countDown + 1 个 await（不对称） |
| 谁来调用 | 所有线程都等 | worker 报数 + main 等 |
| barrierAction | ✅ 可选触发 | ❌ |
| 异常处理 | 某线程挂了 → **全员抛 BrokenBarrierException** | 某 worker 挂了不影响 |
| 底层 | **ReentrantLock + Condition + Generation** | AQS 共享模式 |
| 唤醒机制 | `signalAll` 一次性唤醒所有 | `doReleaseShared` 链式唤醒 |
| 适用场景 | 多线程**分阶段协同**、**互相等待** | 主线程等子线程**单次完成** |

### 决策树

```
需要复用吗？
├── 是 → CyclicBarrier
└── 否 →
    是「主线程等子线程」？
    ├── 是 → CountDownLatch
    └── 否（多线程互相等）→ CyclicBarrier
```

---

## 六、与 Phaser 简述

`Phaser` 是 JDK 7 引入的更强大的同步器，**可以理解成 CyclicBarrier + CountDownLatch 的超集**：

| 维度 | CyclicBarrier | Phaser |
|------|---------------|--------|
| 参与方数 | 构造时固定 | **可动态增减**（`register` / `arriveAndDeregister`） |
| 阶段 | 是 | **是**（更灵活） |
| 树形 | ❌ | ✅ **可分层**（父 phaser 等待子 phaser） |
| 复杂度 | 简单 | 复杂 |

**何时选 Phaser：** 参与方数动态变化、或需要分阶段树形协同。一般业务场景 CyclicBarrier 够用。

---

## 七、面试高频踩坑

### 7.1 为什么所有线程都抛 `BrokenBarrierException`？

**打破屏障的语义：** 一旦发生异常（中断 / 超时 / barrierAction 抛异常），**整个代际作废**——所有还在等的线程**都要知道**。如果不打破屏障，那些线程会永远等下去。

### 7.2 屏障打破后能继续用吗？

**能**——打破的是**当前代际**，下一个线程 await 时会发现 `generation.broken`，抛异常后**等下一拨人重新调 await 即可**。但更稳妥是调 `reset()` 显式重置：

```java
if (barrier.isBroken()) barrier.reset();
```

### 7.3 barrierAction 抛异常会怎样？

整个 `nextGeneration` 流程被中断 → 走 `breakBarrier` 路径 → **所有等者抛 `BrokenBarrierException`**。

### 7.4 屏障能不能用一次就扔？

**可以**——用一次就扔的话，CyclicBarrier 和 CountDownLatch 等价。但 CyclicBarrier 写法更对称（所有线程都 await），可读性更好。

### 7.5 为什么不用 AQS 实现？

| 维度 | AQS | ReentrantLock + Condition |
|------|-----|---------------------------|
| 需要「全员等齐一起放行」语义 | 不天然适合 | **天然适合**（signalAll） |
| 需要「打破屏障」语义 | 不天然适合 | **天然适合**（标记 + signalAll） |
| 需要循环用 | 要重写 Generation | **直接 new Generation** |

**设计选择：** CyclicBarrier 的「代际」+「全等全放」语义和 AQS 的「抢锁」语义**不匹配**——用 ReentrantLock + Condition 更直接。

### 7.6 `await()` 内部怎么自旋检查代际？

```java
for (;;) {
    try { trip.await(); } catch (...) { ... }
    if (g.broken) throw new BrokenBarrierException();
    if (g != generation) return index;         // ★ 代际变了 = 全员到齐
    if (timed && nanos <= 0L) { breakBarrier(false); throw new TimeoutException(); }
}
```

**关键：`g != generation` 是判断「自己这一代已经结束」的标志**。

---

## 八、面试话术模板

> "CyclicBarrier 是 JUC 的回环屏障，用来让一组线程互相等待，直到所有线程都到达屏障点后一起放行。它和 CountDownLatch 最大的区别是**可循环用**——一拨线程放行后，下一拨能接着用同一个 barrier 继续等。
>
> 底层用 ReentrantLock + Condition + Generation 实现。每个 await 线程先 `count--`，自己减到 0 时代表全员到齐，由它执行可选的 barrierAction，然后调 nextGeneration：signalAll 唤醒所有等者、重置 count、**new 一个新 Generation**。被唤醒的等者检查 `g != generation` 发现自己这一代已结束，就放行返回。
>
> 相对 CountDownLatch，CyclicBarrier 适合**多线程分阶段协同**——所有线程都 await 对称、可以循环用、可选触发 barrierAction。CountDownLatch 适合**主线程等子线程单次完成**——N 个 countDown + 1 个 await 不对称、一次性。注意点是某线程异常会打破整个屏障，其他等者抛 BrokenBarrierException，屏障可调 reset 重置继续用。"

---

## 关联知识点

- [[CountDownLatch详解]] — 与 CyclicBarrier 对比：一次性 vs 循环、不对称 vs 对称
- [[ReentrantLock详解]] — CyclicBarrier 底层用 ReentrantLock
- [[Condition 详解]] — CyclicBarrier 底层用单个 Condition（trip）实现「全等全放」
- [[AQS 详解]] — CyclicBarrier **不用 AQS**，对比理解两种同步器设计哲学
- [[Java并发编程面试题]] — 并发编程主文件，CyclicBarrier 段已简述
- [[线程池]] — 可用 CyclicBarrier 做并发压测的起跑门
- [[Phaser详解]]（待创建）— CyclicBarrier 的超集，支持动态参与方
