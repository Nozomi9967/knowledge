---
tags:
  - java
  - 并发
  - JUC
  - AQS
  - 锁
  - 源码
created: 2026-06-13
updated: 2026-06-13
source: [[ReentrantLock详解]]
---

# AQS 详解

> 拆分自 [[ReentrantLock详解]] 第 4 节「底层原理：AQS 框架」。本页展开 AQS 的三要素、CLH 队列原理、AQS 对 CLH 的改造、源码级加锁/解锁/取消、与 `synchronized` 的对照、面试话术与踩坑点。
>
> 反向引用：[[ReentrantLock详解]] · [[Condition 详解]] · [[Java并发编程面试题]] · [[synchronized 关键字]] · [[线程池]]

---

## 什么是 AQS

**AQS（AbstractQueuedSynchronizer）** 是 `java.util.concurrent.locks` 包下的**抽象同步器框架**，是 JUC 一切锁和同步器的**底层基石**。ReentrantLock、Semaphore、CountDownLatch、CyclicBarrier、ReentrantReadWriteLock、ThreadPoolExecutor（Worker）**全部基于它实现**。

> [!info] AQS — Abstract Queued Synchronizer（抽象队列同步器）
> 名字直译：**抽象**（子类填具体语义）+ **队列**（CLH 同步队列管等待线程）+ **同步器**（state 字段表示同步状态）。子类只需要重写几个钩子方法，**AQS 帮你管好「抢锁失败后的排队、唤醒、取消」**所有苦力活。

### 一句话理解

> AQS = **state（同步状态）+ CLH 队列（等待线程）+ 5 个钩子方法（子类填业务）**。
>
> 子类决定「什么算拿到锁」（CAS state？可重入？共享？），AQS 决定「没拿到怎么办」（入队 / 挂起 / 唤醒 / 取消）。

### 通俗类比

把 AQS 想象成**银行大堂经理**：

- 客户（线程）来了，先看「有没有空闲窗口」（`state` 是不是 0）
- 有就直接办（CAS 抢到）
- 没有就**拿号排队**（入 CLH 队列），**坐着等叫号**（`park` 挂起）
- 前面客户办完，大堂经理**叫下一位**（`unpark` 唤醒）
- **具体办什么业务**（存取款？贷款？理财？）由窗口员工决定——这就是**子类重写的钩子方法**

---

## 一、AQS 核心三要素

| 要素                          | 类型             | 作用                                  | 谁定义                          |
| --------------------------- | -------------- | ----------------------------------- | ---------------------------- |
| **`state`**                 | `volatile int` | 同步状态，**子类定义语义**                     | AQS 提供原始字段，子类定义具体语义          |
| **`head` / `tail`**         | `Node` 引用      | CLH 同步队列的**头尾指针**                   | AQS 内部维护（CLH 队列的 Node 头尾引用） |
| **`Node.waitStatus`**       | `int`          | 节点等待状态（4 种取值）                      | AQS 内部定义（Node 内部状态字段）       |

### 1.1 `state` 字段

```java
private volatile int state;
```

**`volatile`** 保证多线程可见性 + 禁止指令重排。语义由子类定义：

| 子类                         | `state` 含义                              |
| -------------------------- | --------------------------------------- |
| **ReentrantLock**          | **重入次数**（0=空闲，1=被持有 1 次，2=可重入 1 次……）    |
| **Semaphore**              | **剩余许可证数**（state=3 表示还能放 3 个线程）         |
| **CountDownLatch**         | **倒计数**（state=N 表示还要 N 次 countDown 才放行） |
| **ReentrantReadWriteLock** | 高 16 位=读锁计数，低 16 位=写锁计数                 |

### 1.2 CLH 同步队列（重点 ⭐）

**CLH** 是一种**基于链表的自旋锁队列算法**，由 **Craig、Landin、Hagersten** 三人 1994 年发明。AQS 改造了它。

> [!info] CLH — Craig-Landin-Hagersten Lock
> 原始 CLH 节点**自旋前驱的状态**判断能否拿锁。AQS 把自旋改成了 **`LockSupport.park()` 挂起**——避免空耗 CPU。

**AQS 的 CLH 队列结构：**

```
head (dummy)    ← 哑节点，不存线程
  ↓
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ NodeA (T1)   │ ←→ │ NodeB (T2)   │ ←→ │ NodeC (T3)   │ ← tail
│ ws=SIGNAL    │    │ ws=SIGNAL    │    │ ws=0         │
└──────────────┘    └──────────────┘    └──────────────┘
     ↑ 在跑          park 等 A 唤醒     park 等 B 唤醒
```

| 关键设计 | 作用 |
|----------|------|
| **head 是哑节点** | 简化入队出队，避免边界判断 |
| **双向链表**（prev + next） | 取消节点 / 唤醒前驱时需要 prev 倒着找 |
| **每个 Node 持线程引用** | unpark 时能找到对应线程 |
| **`waitStatus`** | 表达 4 种状态（SIGNAL 最关键） |

### 1.3 `waitStatus` 四种状态

| 状态值                  | 含义                            | 触发场景                           |
| -------------------- | ----------------------------- | ------------------------------ |
| **`CANCELLED (1)`**  | 线程**取消**了等待                   | 等待中被 interrupt、tryLock 超时      |
| **`SIGNAL (-1)`**    | **后继要靠我唤醒**——我释放时要 unpark 它   | 最常见，acquireQueued 里设置          |
| **`CONDITION (-2)`** | 节点在 **Condition 队列**里（不在 CLH） | await() 时                      |
| **`PROPAGATE (-3)`** | **共享模式**下传播唤醒                 | Semaphore / CountDownLatch 释放时 |
| `0`                  | 初始值 / 刚迁移到 CLH                | 默认                             |

**`SIGNAL` 是绝对核心**——它的语义是「**这个节点释放时，必须 unpark 它的后继**」。

---

## 二、AQS 对 CLH 的两个关键改造

| 维度 | 原始 CLH | AQS 的 CLH 变体 |
|------|----------|----------------|
| **等待方式** | 自旋（**占 CPU**） | **`LockSupport.park()` 挂起**（不占 CPU，OS 调度） |
| **唤醒机制** | 自旋发现前驱状态变化 | **前驱 `unpark` 当前节点** |
| **节点状态** | 简单的 locked/unlocked | **`waitStatus` 四种**（CANCELLED/SIGNAL/CONDITION/PROPAGATE） |
| **队列方向** | 单向 | **双向**（prev + next） |

### 为什么 AQS 要把自旋改成 park？

高并发下成百上千线程如果都在自旋，**CPU 全空转**——典型的「假并行、真浪费」。改成 park 后：

| 维度 | 自旋 | park 挂起 |
|------|------|-----------|
| 线程状态 | RUNNABLE | **WAITING** |
| 占 CPU？ | ✅ 占 | ❌ 不占 |
| 调度由谁管 | JVM 忙等 | **OS 调度器** |
| 上下文切换 | 几乎没有 | **有**（unpark 时切一次） |

> **trade-off**：park 省 CPU，但唤醒有上下文切换开销（1~10 微秒）。
> AQS 的设计哲学：**平时挂起省 CPU，关键路径用 CAS 抢锁**。

---

## 三、AQS 类结构

```java
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {
    
    // 同步状态
    private volatile int state;
    
    // CLH 队列头尾
    private transient volatile Node head;
    private transient volatile Node tail;
    
    // CAS 工具
    protected final boolean compareAndSetState(int expect, int update) { ... }
    private final boolean compareAndSetHead(Node update) { ... }
    private final boolean compareAndSetTail(Node expect, Node update) { ... }
    
    // 模板方法（final 不能重写）
    public final void acquire(int arg) { ... }      // 独占获取
    public final boolean release(int arg) { ... }   // 独占释放
    public final void acquireShared(int arg) { ... } // 共享获取
    public final boolean releaseShared(int arg) { ... } // 共享释放
    
    // 钩子方法（protected，子类必须实现）
    protected boolean tryAcquire(int arg) { throw new UnsupportedOperationException(); }
    protected boolean tryRelease(int arg) { throw new UnsupportedOperationException(); }
    protected int tryAcquireShared(int arg) { throw new UnsupportedOperationException(); }
    protected boolean tryReleaseShared(int arg) { throw new UnsupportedOperationException(); }
    protected boolean isHeldExclusively() { throw new UnsupportedOperationException(); }
    
    // 内部 Node 类
    static final class Node { ... }
}
```

### 3.1 模板方法 vs 钩子方法

| 类型 | AQS 提供 | 子类实现 |
|------|----------|----------|
| **模板方法**（`final`） | `acquire` / `release` / `acquireShared` / `releaseShared` | **不能重写**，逻辑已固定 |
| **钩子方法**（`protected`） | `tryAcquire` / `tryRelease` / `tryAcquireShared` / `tryReleaseShared` / `isHeldExclusively` | **子类必须重写**（至少用到的） |

**设计模式：模板方法模式（Template Method）**——父类定骨架，子类填细节。

### 3.2 共享模式 vs 独占模式

| 模式 | 钩子方法 | 典型子类 | 同时持有者 |
|------|----------|----------|------------|
| **独占（Exclusive）** | `tryAcquire` / `tryRelease` | ReentrantLock、ReentrantReadWriteLock.WriteLock | **1 个** |
| **共享（Shared）** | `tryAcquireShared` / `tryReleaseShared` | Semaphore、CountDownLatch、ReentrantReadWriteLock.ReadLock | **N 个** |

#### 3.2.1 什么是独占模式（Exclusive Mode）

> [!info] 独占模式 — Exclusive Mode（排他持有）
> **同一时刻只允许一个线程持有同步状态**。其他线程来了要么 CAS 抢到，要么进 CLH 队列 park 等待。**Mutex（互斥）语义**。

**核心特征：**
- **互斥**：state 表示"有没有人占着"（ReentrantLock 中是重入次数，但仍只属于 owner 一个线程）
- **owner 明确**：`AbstractOwnableSynchronizer.exclusiveOwnerThread` 记录当前持有线程
- **`tryAcquire` 返回 boolean**：true=拿到，false=没拿到
- **可重入性是独占模式专属特性**（state 计重入次数）——共享模式没有"重入"概念

**独占模式加锁流程（`acquire`）：**

```
线程进来
  ↓
tryAcquire(arg) ──成功──→ 拿到锁，继续执行
  │
  └─失败──→ addWaiter(Node.EXCLUSIVE) 入队
              ↓
            acquireQueued 死循环
              ↓
            park 挂起，等前驱 unpark
              ↓
            醒来后前驱是 head 才重新 tryAcquire
```


# AQS 独占锁获取失败后的核心处理流程

当线程调用 `tryAcquire(arg)` 获取同步状态失败后，AQS（AbstractQueuedSynchronizer）会通过以下四个核心步骤实现线程的队列化管理、高效率自旋与精细化阻塞。

---

## 1. 节点封装与 FIFO 双向队列入队 (`addWaiter`)
* **线程封装：** 将当前获取锁失败的线程及独占模式标记（`Node.EXCLUSIVE`）封装为一个 `Node` 节点。
* **双向链表挂载：** AQS 内部维护一个 FIFO 的双向 CLH 队列变体。`addWaiter` 的核心任务是将该节点安全地插入到同步队列的尾部（Tail）。
* **高并发 CAS 自旋：** 为了应对多线程同时入队的竞争，AQS 采用无锁的 **CAS（Compare And Swap）** 操作。如果单次插入失败，会进入 `enq(Node)` 方法通过 `for(;;)` 死循环自旋，直到通过 CAS 成功将节点挂载到 `tail` 后方。

---

## 2. 同步状态轮询与局部自旋 (`acquireQueued`)
线程进入队列后，并不会立刻被挂起，而是进入 `acquireQueued` 方法的 `for(;;)` 死循环中进行有条件的局部自旋：
* **前驱节点判定（Head Predicate）：** 线程在循环中首先检查自己的前驱节点（Prev）。**只有当其前驱节点为 `head` 时**，当前节点才具备调用 `tryAcquire` 尝试获取锁的法定资格（因为 `head` 的后继节点是队列中唯一的合法继承者）。
* **拓扑结构晋升（setHead）：** 如果抢锁成功，当前节点将通过 `setHead(node)` 晋升为新的 `head` 节点，并断开原 `head` 的引用链以利于垃圾回收（GC）。

---

## 3. 状态信号量标记与线程阻塞 (`LockSupport.park`)
如果当前节点的前驱不是 `head`，或者尝试获取锁再次失败，线程为了避免 CPU 空转，必须进入阻塞状态。但在阻塞前需要确保通知机制完备：
* **前驱状态修改（`shouldParkAfterFailedAcquire`）：** * 线程会检查前驱节点的等待状态（`waitStatus`）。如果前驱状态为 `Node.SIGNAL`（-1），说明前驱释放锁时一定会通知自己，当前线程可以安全阻塞。
  * 如果前驱状态大于 0（`CANCELLED`），则向前追溯跳过已取消的节点。
  * 如果是其他状态（如 0），则利用 **CAS** 将前驱的 `waitStatus` 强行修改为 `Node.SIGNAL`。
* **线程显式挂起：** 确认前驱状态为 `SIGNAL` 后，调用底层内核原语 `LockSupport.park(this)`。线程状态转为 **WAITING**，出让 CPU 时间片。

---

## 4. 线程唤醒与锁二次竞争 (`Unpark` / `Re-Acquire`)
当持有锁的线程释放同步状态时，会触发 AQS 的唤醒与重新竞争机制：
* **释放与唤醒（`unparkSuccessor`）：** 持锁线程（其节点即 head）调用 `release()` 时，检查 `head.waitStatus`，若为 `SIGNAL`，则传 head 给 `unparkSuccessor(head)`，找到 head 的后继节点并调用 `LockSupport.unpark(s.thread)` 显式唤醒。
* **上下文恢复与惊群竞争：** * 被唤醒的线程从 `LockSupport.park` 处**原位复活**，继续执行 `for(;;)` 死循环。
  * **在非公平锁模式下：** 刚醒来的线程在执行 `tryAcquire` 的瞬间，可能会遭遇外面新来线程（未入队）的“插队”竞争。如果锁被新来的线程劫走，唤醒的线程会遭遇抢锁失败。
* **二次阻塞：** 若抢锁失败，该线程保持队列位置不变，重新执行第 3 步的逻辑——确认前驱 `SIGNAL` 状态后，再次调用 `park` 进入休眠，等待下一次被唤醒。


**独占模式释放流程（`release`）：**

```
线程释放
  ↓
tryRelease(arg) ──state 减到 0──→ unparkSuccessor(head)
  │                                    唤醒后继一个线程
  └─state 还没归零（重入中）──→ 只减计数，不唤醒
```

> **关键点：独占模式一次只唤醒一个后继**（head.next），不传播。被唤醒的线程再去 CAS 抢锁——这保证了"一山不容二虎"。



#### 3.2.2 独占锁非公平模式下的唤醒与抢锁机制（深度解析）

> **核心观点：** 独占模式下"AQS 一次只唤醒一个后继"这个结论**完全正确**。非公平锁所谓的"都能抢"，是指**队列外的新来线程**和**队列头刚醒的线程**之间的博弈，而不是"队列内部所有线程集体暴动"。

##### 3.2.2.1 队列内部秩序：一山不容二虎

独占模式（`Node.EXCLUSIVE`，如 `ReentrantLock`）下，持有锁的线程调用 `unlock()` 释放时：

1. **单一唤醒**：AQS 的 `release` 方法最终只会触发**一次** `unparkSuccessor` 调用
2. **精准定位**：从 `head` 节点开始，通过 `head.next` 找到**第一个合法**后继节点，仅对一个线程调用 `LockSupport.unpark(thread)`
3. **绝对静默**：排得更靠后的线程（`head.next.next`……）由于前驱不是 `head`，在 `acquireQueued` 的 `for(;;)` 循环中**根本没有调用 `tryAcquire` 的资格**——它们仍处于 OS 级 `WAITING` 状态，不会被唤醒，更不会参与哄抢

> **结论：同步队列内部，永远只有队头那一个刚醒来的线程在尝试拿锁。**

##### 3.2.2.2 非公平的"插队"本质：队列内 vs 队列外

既然队列内部秩序井然，那非公平锁的"非公平"和"都能抢"到底体现在哪里？**体现在队列外部新产生的活跃线程身上**。

当一个新线程（Thread-C）在非公平锁模式下调用 `lock()` 时：

```java
final void lock() {
    // 进来不管三七二十一，直接尝试 CAS 砸门抢锁！
    if (compareAndSetState(0, 1)) 
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1); // 砸门失败，才会乖乖去排队（走入队流程）
}
```

**新线程的执行路径有两条岔路：**

| 路径 | 触发条件 | 结果 |
|------|----------|------|
| **路径 A：砸门成功** | `compareAndSetState(0, 1)` CAS 成功 | 跳过 `acquire`，直接拿到锁。**这就是"插队"的来源** |
| **路径 B：砸门失败** | CAS 失败（state≠0，说明锁被人占着） | 走 `acquire(1)`，老老实实入队 |

**关键洞察：非公平锁的"插队"窗口只在锁刚释放的极短瞬间打开。** 此时：

```
时序展开（锁持有者 T1 正在 unlock，Thread-C 刚到）：
                                                ┌─→ Thread-C CAS 成功，插队拿到锁
T1 释放锁 → state: 1→0 ─┤
                                                └─→ Thread-C CAS 失败，入队
         ↓
T1 调 unparkSuccessor(head) 唤醒后继 T2
         ↓
T2 从 park 处醒来，在 tryAcquire 时发现：
         ├─ 锁被 Thread-C 抢走（插队发生）→ T2 抢锁失败，重新 park
         └─ 锁还没被抢走（Thread-C 还在路上）→ T2 抢锁成功
```

**两类"抢锁者"对比：**

| 维度 | 队列头刚醒的 T2 | 队列外新来的 Thread-C |
|------|----------------|----------------------|
| **位置** | 在 CLH 队列里，前驱是 head | 不在队列里（刚到） |
| **抢锁方式** | 走 `acquireQueued` → `tryAcquire` | 走 `lock()` → 裸 `compareAndSetState` |
| **抢锁时机** | 被 unpark 后 | 任何时刻（包括 T1 释放瞬间） |
| **抢锁失败后** | 保持队列位置，重新 park | 入队排到队尾 |
| **是否公平** | 相对公平（FIFO 顺序） | 插队特权 |

> **设计权衡：非公平锁的"插队"换来的是更高的吞吐量**——避免唤醒后继的上下文切换开销。但代价是**队列中等待的线程可能"饿不死但总被插队"**。Java 的 `synchronized` 也采用非公平策略，原因相同。

##### 3.2.2.3 公平锁 vs 非公平锁：博弈对象不同

| 锁类型 | 新线程 lock() 时的行为 | 刚醒线程 vs 新线程 抢锁结果 |
|--------|----------------------|---------------------------|
| **非公平锁** | **先裸 CAS 抢一把**，抢不到再 `acquire(1)` 入队 | 新线程可能插队成功，刚醒线程可能抢锁失败 |
| **公平锁** | 直接 `acquire(1)`，全程走 `hasQueuedPredecessors()` 检查 | **新线程发现队列有人就直接入队**，刚醒线程几乎一定抢到 |

> **核心差异：公平锁的 `tryAcquire` 里有个 `hasQueuedPredecessors()` 检查**——队列里有人就让，不会发生"插队"。这是 9.1 节要展开的内容。

##### 3.2.2.4 面试话术

> "AQS 独占模式的唤醒机制是'一山不容二虎'：一次只唤醒一个后继，队列里排在更后面的线程根本不会被唤醒，更不会参与哄抢。
>
> 但非公平锁的'非公平'体现在另一个维度——**队列外的新来线程**。新线程调用 `lock()` 时，**不检查队列**直接裸 CAS 抢锁（`compareAndSetState(0, 1)`），如果刚好赶上锁释放的瞬间就能'插队'成功。这是 `synchronized` 和 `ReentrantLock` 默认非公平策略的根本原因——**避免唤醒后继的上下文切换开销，换取更高吞吐量**。
>
> 所以非公平锁里有两类抢锁者：①队列头刚被 unpark 醒来的线程（走 `acquireQueued` 路径）；②队列外新到的线程（走裸 CAS 路径）。两者的博弈结果是**新线程可能插队成功，刚醒线程可能抢锁失败**——但这只是**两类抢锁者之间**的竞争，**队列内部**仍然是严格 FIFO 的秩序。"

#### 3.2.3 什么是共享模式（Shared Mode）

> [!info] 共享模式 — Shared Mode（并发共享）
> **同一时刻允许多个线程同时持有同步状态**。Semaphore(state=N) 表示最多 N 个线程可以并发通过；CountDownLatch(state=0) 表示放行所有等待者。**Semaphore（信号量）语义**。

**核心特征：**
- **并发**：state 表示"还有多少个名额可用"
- **没有 owner 概念**：所有持有者地位平等
- **`tryAcquireShared` 返回 int**：≥0=拿到（返回值是剩余许可证数），<0=失败
- **可重入？没意义**——共享模式没人能独占，多个线程可以同时拿到，不需要"重入计数"

**共享模式加锁流程（`acquireShared`）：**

```
线程进来
  ↓
tryAcquireShared(arg) ──返回值 ≥0──→ 拿到许可证，直接执行
  │
  └─返回值 <0──→ doAcquireShared
                   ↓
                 addWaiter(Node.SHARED) 入队
                   ↓
                 死循环：前驱是 head 且 tryAcquireShared 成功
                   ↓
                 setHeadAndPropagate  ← ★ 与独占的核心差异
                   ↓
                 park 挂起
```

**共享模式释放流程（`releaseShared`）：**

```
线程释放
  ↓
tryReleaseShared(arg) ──成功──→ doReleaseShared
                                 ↓
                               unparkSuccessor(head)
                                 ↓
                               ★ 唤醒后还要继续传播（PROPAGATE）
                                 ↓
                               链式唤醒所有能拿锁的线程
```

> **关键点：共享模式释放时要 `doReleaseShared` 链式唤醒**。比如 CountDownLatch 的 state 从 N 减到 0 时，**要一次唤醒所有等待者**——不能像独占模式只唤醒一个。这正是 `PROPAGATE (-3)` 状态存在的原因。

#### 3.2.4 Node 上的 mode 标记：`EXCLUSIVE` vs `SHARED`

AQS 的 `Node` 类里有个 `nextWaiter` 字段，**被复用为"模式标记"**：

```java
static final class Node {
    static final Node SHARED = new Node();    // 共享模式标记
    static final Node EXCLUSIVE = null;       // 独占模式标记（即 null）

    volatile Thread thread;                   // 入队线程
    Node nextWaiter;                          // ★ nextWaiter 字段：null=独占，SHARED=共享
}
```

**两种 Node 可以在同一个 CLH 队列里共存**（读写锁就是这样——读锁是 SHARED 节点、写锁是 EXCLUSIVE 节点混排）。但**唤醒规则要分模式**：

| 模式                 | 唤醒行为                                                    |
| ------------------ | ------------------------------------------------------- |
| **EXCLUSIVE 节点释放** | `unparkSuccessor` 只唤醒**下一个**节点                          |
| **SHARED 节点释放**    | `doReleaseShared` 唤醒后还要**继续向后传播**，唤醒所有连续能拿到锁的 SHARED 节点 |

> 连续指的是中间不能有EXCLUSIVE节点

#### 3.2.5 源码级差异对照表

| 维度             | 独占模式                                              | 共享模式                                        |
| -------------- | ------------------------------------------------- | ------------------------------------------- |
| **模板方法**       | `acquire(int)` / `release(int)`                   | `acquireShared(int)` / `releaseShared(int)` |
| **钩子方法**       | `tryAcquire` / `tryRelease` / `isHeldExclusively` | `tryAcquireShared` / `tryReleaseShared`     |
| **钩子返回值**      | `boolean`                                         | `int`（共享模式 ≥0/失败 还要表示剩余资源）                  |
| **Node mode**  | `Node.EXCLUSIVE`                                  | `Node.SHARED`                               |
| **入队**         | `addWaiter(Node.EXCLUSIVE)`                       | `addWaiter(Node.SHARED)`                    |
| **获取循环**       | `acquireQueued`                                   | `doAcquireShared`                           |
| **出队设 head**   | `setHead(node)`                                   | `setHeadAndPropagate(node, prop)`（★ 多了传播）   |
| **释放**         | `release` → `unparkSuccessor`                     | `releaseShared` → `doReleaseShared`（★ 链式唤醒） |
| **owner**      | ✅ `exclusiveOwnerThread`                          | ❌ 无 owner                                   |
| **可重入**        | ✅ state 计重入次数                                     | ❌ 无重入概念                                     |
| **可中断**        | ✅ `lockInterruptibly`                             | ✅ `acquireSharedInterruptibly`              |
| **tryLock 超时** | ✅ `tryAcquireNanos`                               | ✅ `tryAcquireSharedNanos`                   |
| **Condition**  | ✅ 独占模式支持（写锁可）                                     | ⚠️ 仅 ReentrantReadWriteLock.ReadLock 有限支持   |

#### 3.2.6 通俗类比

把 AQS 想象成**银行大堂**：

| 场景 | 对应模式 | 比喻 |
|------|----------|------|
| **VIP 窗口** | 独占（ReentrantLock） | **只服务一个客户**，办完才叫下一位。VIP 客户可以中途追加业务（**可重入**） |
| **普通柜台** | 共享（Semaphore） | **开 N 个窗口**同时服务，state=N 表示当前还有 N 个空位 |
| **排队叫号机** | 共享（CountDownLatch） | N 个任务完成（state→0）后**一次性放行**所有等的人 |
| **借阅室** | 读写锁 | **多个读者可同时进**（共享读锁），但**写者进来时所有读者都得让**（独占写锁） |

#### 3.2.7 面试话术

> "AQS 同时支持独占和共享两种模式。
> **独占模式**同一时刻只允许一个线程持有锁——典型代表是 ReentrantLock，state 记重入次数，Node 模式是 EXCLUSIVE，加锁走 `acquire → tryAcquire → acquireQueued`，释放走 `release → unparkSuccessor`，**一次只唤醒一个后继**。
> **共享模式**同一时刻允许多个线程并发持有，典型代表是 Semaphore（state=许可证数）和 CountDownLatch（state=倒计数），Node 模式是 SHARED，加锁走 `acquireShared → tryAcquireShared → doAcquireShared`，**核心差异在释放时要 `doReleaseShared` 链式唤醒**——比如 latch 减到 0 要一次唤醒所有等待者。
>> 两种模式可以共存于同一个 CLH 队列里（读写锁就是这样：读节点是 SHARED、写节点是 EXCLUSIVE 混排），Node 用 nextWaiter 字段标记模式——null=独占，SHARED =共享。"



---

## 四、加锁源码：`acquire` 全流程

`ReentrantLock.lock()` 最终走到 AQS 的 `acquire`：

```java
// ReentrantLock.NonfairSync.lock
final void lock() {
    if (compareAndSetState(0, 1))              // ① 裸 CAS 抢锁（非公平来源）
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);                            // ② 抢不到走完整 acquire
}

// AQS.acquire
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&                    // ③ 完整 tryAcquire（可重入判断 + 公平检查）
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  // ④ 入队 + park
        selfInterrupt();
}
```

### 4.1 完整加锁流程

| 步骤  | 方法                                   | 行为                        |
| --- | ------------------------------------ | ------------------------- |
| ①   | `compareAndSetState(0, 1)`           | 裸 CAS 抢锁——非公平锁允许插队        |
| ②   | `acquire(1)`                         | 抢不到走完整流程                  |
| ③   | `tryAcquire(arg)`                    | 完整尝试：CAS 抢 / 可重入判断 / 公平检查 |
| ④   | `acquireQueued(addWaiter(...), arg)` | 真抢不到才入队 + park            |

#### 4.1.1 获取失败后的核心子流程概览

`tryAcquire` 失败后，线程进入 `acquireQueued(addWaiter(...), arg)`，**这一段是 AQS 最核心的代码**。下面 4.2–4.5 四个子节按执行顺序拆解它：

| 阶段 | 涉及方法 | 核心问题 | 对应小节 |
|------|----------|----------|----------|
| **入队** | `addWaiter` / `enq` | 怎么把当前线程**安全挂到队尾**？ | [[#4.2 addWaiter：入队（队尾 CAS）\|4.2]] |
| **抢锁 + 局部自旋** | `acquireQueued` | 入队后**要不要立即 park**？前驱是 head 就有资格抢锁 | [[#4.3 acquireQueued：抢锁 + park（核心 ⭐）\|4.3]] |
| **状态信号量** | `shouldParkAfterFailedAcquire` | park 之前**前驱 waitStatus 是不是 SIGNAL**？否则要 CAS 改 | [[#4.4 shouldParkAfterFailedAcquire：状态机\|4.4]] |
| **真挂起** | `parkAndCheckInterrupt` | `LockSupport.park` 让线程进入 **WAITING** 状态 | [[#4.5 parkAndCheckInterrupt：真挂起\|4.5]] |

> **整体设计哲学：先 CAS 抢一把（快速路径），失败走完整 acquire。`tryAcquire` 再抢一次（含可重入判断），还失败才入队。入队后进入 `acquireQueued` 的 `for(;;)` 循环——前驱是 head 就有资格抢，每次被唤醒都会再抢；如果前驱的 waitStatus 还不是 SIGNAL（刚入队时是 0），`shouldParkAfterFailedAcquire` 会把状态改成 SIGNAL 并让循环再转一次，这个**局部自旋**省掉了不必要的 park/unpark 开销。实在没机会才 park 挂起。被 unpark 唤醒后又回到循环顶部——这就是 AQS 兼顾吞吐和公平性的秘密。**

### 4.2 `addWaiter`：入队（队尾 CAS）

```java
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);  // EXCLUSIVE / SHARED
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {             // CAS 队尾
            pred.next = node;
            return node;
        }
    }
    enq(node);                                            // CAS 失败走完整入队
}

private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) {                                  // 队列空，先建 dummy head
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

**入队要点：**
- **CAS 队尾**——并发安全，比 synchronized 快
- **head 是哑节点**——队列空时 `head == tail == null`，先建个空节点
- **失败自旋**——CAS 没抢到就重试

### 4.3 `acquireQueued`：抢锁 + park（核心 ⭐）

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {                                        // 死循环
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {           // ① 前驱是 head + 抢到锁
                setHead(node);
                p.next = null;                            // 旧的 head 帮新 head 断开
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node)    // ② 前驱是 SIGNAL？
                && parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed) cancelAcquire(node);                  // 异常时取消
    }
}
```

**关键逻辑：**

1. **前驱是 head 才抢锁**——**保证 FIFO 公平性**（非公平锁也会在 tryAcquire 里抢，但 CLH 队列位置靠前有优势）
2. **`shouldParkAfterFailedAcquire`**——把前驱状态改成 SIGNAL，然后自己 park
3. **`parkAndCheckInterrupt`**——真挂起，等前驱 unpark

### 4.4 `shouldParkAfterFailedAcquire`：状态机

```java
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)                                // 前驱已经是 SIGNAL
        return true;                                      // 可以 park
    if (ws > 0) {                                         // 前驱 CANCELLED（1）
        do {
            node.prev = pred = pred.prev;                 // 跨过取消节点
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {                                              // 0 或 PROPAGATE(-3)
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);   // CAS 改成 SIGNAL
    }
    return false;                                         // 还要再循环一次
}
```

**核心目的：把前驱状态设为 SIGNAL——告诉前驱「你释放时记得 unpark 我」**

### 4.5 `parkAndCheckInterrupt`：真挂起

```java
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);                  // 线程挂起（WAITING 状态）
    return Thread.interrupted();             // 醒来后看有没有被中断
}
```

`LockSupport.park` 挂起的线程，**有三种醒来方式**：

| 醒来原因 | 谁触发 | 醒来后去哪里 |
|---------|--------|-------------|
| **unpark 信号** | 前驱线程（`unparkSuccessor` 中 `LockSupport.unpark(s.thread)`） | 重新进入 `acquireQueued` 死循环重抢锁 |
| **interrupt 信号** | 其他线程 `thread.interrupt()`（JVM 内部用 unpark + 中断标志模拟） | `parkAndCheckInterrupt` 读到中断标记，**吞掉**并走 `cancelAcquire` 取消节点 |
| **伪唤醒（spurious wakeup）** | **无外力**——JLS 允许 OS/CPU 偶发唤醒 | `Thread.interrupted()` 返回 false，正常重抢锁 |

> [!warning] 伪唤醒必须用 `while`（或 `for` 死循环）重检条件
> JLS 明文允许线程在没有任何原因的情况下从 `park` 醒来。`acquireQueued` 的 `for (;;)` 死循环天然重检，**所有用 `park` / `await` 的地方都必须包在循环里**，否则遇到伪唤醒会带着"我还在排队"的错误认知往下走。Condition 的 `await` 源码同理，见 [[Condition 详解]]。

---

## 五、解锁源码：`release` 全流程

```java
// AQS.release
public final boolean release(int arg) {
    if (tryRelease(arg)) {                   // ① 真的释放了（state 减到 0）
        Node h = head;
        if (h != null && h.waitStatus != 0)  // ② head 不空且状态需要唤醒
            unparkSuccessor(h);              // ③ 唤醒后继
        return true;
    }
    return false;
}

// ReentrantLock.tryRelease
protected boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = (c == 0);                 // 重入计数器减到 0 才是真释放
    if (free)
        setExclusiveOwnerThread(null);
    setState(c);
    return free;
}
```

### 5.1 `unparkSuccessor`：唤醒后继

```java
private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);  // 状态清零

    Node s = node.next;
    if (s == null || s.waitStatus > 0) {       // 后继为 null 或已 CANCELLED
        s = null;
        for (Node t = tail; t != node && t != null; t = t.prev)  // 从后往前找
            if (t.waitStatus <= 0) s = t;
    }
    if (s != null)
        LockSupport.unpark(s.thread);
}
```

**关键设计：**
- **从后往前找**——因为 `prev` 可靠（`next` 可能因并发是 null）
- **跳过 CANCELLED 节点**——只唤醒还在等的
- **只唤醒一个**——被唤醒的线程再去 `acquireQueued` 抢锁

> [!abstract] `waitStatus <= 0` 到底过滤掉了什么？
> AQS 里 `waitStatus` 一共 5 种取值：0 / `SIGNAL(-1)` / `CONDITION(-2)` / `PROPAGATE(-3)` / `CANCELLED(1)`。**唯一大于 0 的就是 `CANCELLED`**。所以 `if (t.waitStatus <= 0)` 的真实意图不是"判断是不是 SIGNAL"，而是**排除已取消的节点**——`unparkSuccessor` 从后往前扫，**挑一个还活着的后继**叫醒它。这个判断模式（`waitStatus > 0` ⇔ CANCELLED）遍布整个 AQS 源码，是阅读 AQS 的"钥匙"。

---

## 六、取消节点：`cancelAcquire`

线程在 park 中被 interrupt / tryLock 超时 → 走取消：

```java
private void cancelAcquire(Node node) {
    if (node == null) return;
    node.thread = null;                       // 1. 线程引用置空（GC 友好）

    // 2. 跳过前驱的所有 CANCELLED 节点
    Node pred = node.prev;
    while (pred.waitStatus > 0) node.prev = pred = pred.prev;
    Node predNext = pred.next;

    node.waitStatus = Node.CANCELLED;         // 3. 标记自己为 CANCELLED

    if (node == tail && compareAndSetTail(node, pred)) {  // 4. 是队尾，CAS 剪掉
        compareAndSetNext(pred, predNext, null);
    } else {
        // 5. 不是队尾，处理前驱 SIGNAL + 唤醒后继
        if (pred != head
            && (pred.waitStatus == Node.SIGNAL
                || compareAndSetWaitStatus(pred, pred.waitStatus, Node.SIGNAL))
            && pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {
            unparkSuccessor(node);           // 自己是 head 的后继 → 自己唤醒后继
        }
        node.next = node;                    // help GC
    }
}
```

**取消的语义：**
- 把自己标记为 `CANCELLED (1)`
- 跳过自己接到前驱有效节点上（**链表维护**）
- 必要时**主动唤醒后继**（避免前驱在 SIGNAL 状态下挂掉）

**`waitStatus > 0` = CANCELLED 是判断关键**——AQS 整个代码里到处都是这个判断。

### 6.1 核心概念前置（理解 cancelAcquire 必备）

> [!info] 傀儡 Head 的本质
> `head` 节点内部的 `thread` 总是为 `null`。当一个线程成功获取锁后，它的 Node 就会成为**新的 head**，并将其中的 `thread` 置空。因此，head 节点本质上就是当前（或上一任）持锁线程留在队列里的"**大本营替身**"。

> [!info] release 的执行者
> 调用 `release()` 的**永远是当前持有锁、正在运行的线程**，它通过操作 head（它的"老巢"）来向后传递唤醒信号。处于 `head.next` 的排队线程此时处于 **park 昏迷状态**，无法主动执行任何出队或释放代码。

### 6.2 核心分支拆解

排除队尾情况后，`cancelAcquire` 的核心跳线逻辑如下：

```java
if (pred != head
    && (pred.waitStatus == Node.SIGNAL
        || compareAndSetWaitStatus(pred, pred.waitStatus, Node.SIGNAL))
    && pred.thread != null) {
    // 【分支 1】前驱节点靠谱：悄悄抠掉自己，不唤醒后继
    Node next = node.next;
    if (next != null && next.waitStatus <= 0)
        compareAndSetNext(pred, predNext, next);
} else {
    // 【分支 2】前驱是 head（自己是"大师兄"）：被迫营业，亲自唤醒后继！
    unparkSuccessor(node);
}
node.next = node; // help GC
```

#### 6.2.1 为什么 `ws <= 0` 代表节点有效？

在同步队列中，**大于 0 的状态只有 `CANCELLED (1)`**。因此 `ws <= 0`（包含 0 和 `SIGNAL -1`）的真实意图是**过滤掉已经取消的无效节点**。只要没取消，就是还在老实排队的合法节点。

#### 6.2.2 分支 1：为什么前驱不是 head 时可以"静默断开"？

如果前驱 `pred` 不是 head，说明 `pred` 还在队列深处排队，**它连抢锁的资格都没有**，绝对不可能在此时触发 `release`。

因此，当前节点可以安全地将 `pred` 的状态改为 `SIGNAL`，然后通过 CAS 将 `pred.next` 指向自己的 `next`（**绕过自己**）。等到未来 `pred` 真正变成 head 并释放锁时，自然会顺着这条新链去唤醒后面的节点。

### 6.3 深度死磕：为什么前面是 head 就必须亲自唤醒？

这是整个 AQS 源码中**最惊心动魄的并发时序冲突点**。

#### 6.3.1 错误设想

> [!warning] ❌ 错误设想
> 如果当前节点是 `head.next`（"大师兄"），我们**不唤醒后继**，而是直接执行 `head.next = this.next`（或者尝试把 head 设为 `SIGNAL` 寄希望于持锁线程）然后溜走，会发生什么？

#### 6.3.2 致命死锁时序（Race Condition）

> [!danger] 完美错开导致的永久死锁
> 假设锁已被占用，队列为：`head → 你 (this) → 兄弟 (this.next)`。
>
> 1. **你准备走了**：你因为超时进入 `cancelAcquire`，发现 `pred == head`。
> 2. **持锁线程正准备释放锁**：持锁线程调用 `release()`，去检查 head 的状态（`if (h != null && h.waitStatus != 0)`）。
> 3. **完美错开**：
>    - 此时你还没来得及把 head 改成 `SIGNAL`（`head.waitStatus` 依旧是默认值 0）。
>    - 持锁线程一看："哦，`waitStatus == 0`，后面没人定闹钟，我不需要唤醒任何人。"于是 release 彻底执行完毕，**持锁线程离场，锁已空闲（state = 0）**。
> 4. **你执行了断开**：你此时才慢吞吞地执行了指针修改，然后默默离去。
> 5. **最终惨剧**：锁现在是空闲的，但你的兄弟还在**重度昏迷（park）中**。由于持锁线程已经执行完 release 走了，**再也不会有人来开门**，整个队列后方所有排队的线程将陷入永久死锁！

### 6.4 AQS 的终极解法：激活动作传递

> [!success] 💡 核心总结金句
> 为了绝对堵死上述死锁，AQS 规定：**只要发现自己前面是 head，谁也不信，直接由当前取消的节点亲自调用 `unparkSuccessor(this)` 拍醒身后的兄弟**。
>
> 被拍醒的兄弟会重新进入自旋生命周期：
>
> - **锁已空闲**（对应上述时序）：兄弟醒来发现自己的前驱真的变成了 head，立刻调用 `tryAcquire()`，**成功直接拿到锁，开心地把自己提升为新 head**。队列成功自愈激活！
> - **锁还没释放**：兄弟抢锁失败，它会亲自、重新把 head 的状态安全地修改为 `SIGNAL (-1)`，然后放心地再次睡去。

> [!abstract] 📌 一句话总览
> - **入队时从后往前找**（利用 `prev` 连续性），是为了防止高并发下未连上的 `next` 指针导致队列断流。
> - **出队取消时若紧跟 head 必须亲自唤醒后继**，是为了防止与随时可能离场的持锁线程"擦肩而过"，导致唤醒信号丢失。

---

## 七、共享模式（与 ConditionObject 简述）

### 7.1 共享模式加锁：`acquireShared`

```java
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)              // 返回值 < 0 表示失败
        doAcquireShared(arg);
}
```

| 钩子方法 | 含义 |
|----------|------|
| `tryAcquireShared(int)` | 返回**剩余许可证**（≥0 成功，<0 失败） |
| `tryReleaseShared(int)` | 释放许可证，返回是否成功 |

**典型场景：**

| 子类 | state 语义 |
|------|-----------|
| `Semaphore` | 剩余许可证数 |
| `CountDownLatch` | 倒计数（state=0 时唤醒所有等待者） |
| `ReentrantReadWriteLock.ReadLock` | 读锁计数 |

**核心差异：共享模式释放时要 `doReleaseShared` 链式唤醒**（一个唤起一个，状态变 PROPAGATE 继续传播），保证多个线程能并发进入。

### 7.2 `ConditionObject`（详见 [[Condition 详解]]）

`ConditionObject` 是 AQS 的**内部类**，是 `Condition` 接口的**唯一实现**。

| 关键点 | 简述 |
|--------|------|
| 等待队列 | **单向链表**（区别于 CLH 的双向） |
| await() | 释放锁 → 加入 Condition 队列 → park 挂起 |
| signal() | 把队首节点**迁移到 CLH 队列** → unpark |
| 与 CLH 关系 | **两条独立队列**，节点可双向迁移 |

---

## 八、与 `synchronized` 全面对比

| 维度 | `synchronized` | AQS（ReentrantLock 等） |
|------|----------------|------------------------|
| 语法 | 关键字 | 类（`java.util.concurrent.locks`） |
| 加锁/释放 | 自动 | 手动（finally 里 unlock） |
| 公平性 | 仅非公平 | 公平 / 非公平可选 |
| 可中断 | ❌ | ✅ `lockInterruptibly()` |
| 超时获取 | ❌ | ✅ `tryLock(time, unit)` |
| 条件变量 | 1 个（waitSet） | 多个 Condition |
| 等待队列 | ObjectMonitor 的 EntryList | **CLH 同步队列** |
| 实现层级 | JVM 内部（C++ ObjectMonitor） | **Java 层（AQS）** |
| 锁升级 | 无锁 → 偏向 → 轻量 → 重量 | **始终走 AQS + 阻塞队列** |
| 锁信息查询 | ❌ | ✅ `getHoldCount` / `isLocked` / `isFair` |
| 性能（JDK 6+） | 已优化，差距极小 | 高度竞争时略优 |

---

## 九、面试高频踩坑

### 9.1 AQS 公平锁和非公平锁的 tryAcquire 差异

```java
// NonfairSync.tryAcquire
protected boolean tryAcquire(int acquires) {
    return nonfairTryAcquire(acquires);   // 直接 CAS，不检查队列
}

// FairSync.tryAcquire
protected boolean tryAcquire(int acquires) {
    for (;;) {
        if (hasQueuedPredecessors())      // ★ 关键：队列里有等的人就让
            return false;
        // ... CAS 抢 / 可重入判断 ...
    }
}
```

**公平就多了一个 `hasQueuedPredecessors()` 判断**——队列里有人就放弃，不插队。

### 9.2 为什么 `state` 要 `volatile`？

**双重作用：**
- **可见性**：一个线程改了 state，其他线程立刻能看见
- **禁止指令重排**：避免「先 setOwner 再 setState」被重排成「先 setState 再 setOwner」（导致其他线程以为锁被释放了但 owner 还是旧线程）

### 9.3 `unlock()` 之后 `state` 没归零会怎样？

`state != 0` 时 `tryRelease` 返回 `false`，**AQS 不会 unpark 等待者**——后果是别的线程永远拿不到锁。**这正是 ReentrantLock 强调「lock 几次就 unlock 几次」的根本原因**。

### 9.4 `head` 为什么是哑节点？

| 原因   | 解释                                                |
| ---- | ------------------------------------------------- |
| 简化入队 | 不用单独处理「队列空」和「队列非空」两个分支                            |
| 简化出队 | 拿到锁的节点升为新 head 时，**旧 head 帮它断开**（`p.next = null`） |
| 减少边界 | 队首节点的 `prev` 不必指向任何东西                             |

>引入 `head` 哑节点，就是为了让**第一个排队的节点也能像普通节点一样，拥有一个合法存在的 `prev` 前驱**，从而在代码层面上彻底干掉了 `if (p.prev == null)` 这样的边界判断。

### 9.4 `head` 为什么是哑节点？

| 原因 | 解释 |
|------|------|
| 简化入队 | 不用单独处理「队列空」和「队列非空」两个分支 |
| 简化出队 | 拿到锁的节点升为新 head 时，**旧 head 帮它断开**（`p.next = null`） |
| 减少边界 | 队首节点的 `prev` 不必指向任何东西 |

#### 9.4.1 核心原因拆解

在 AQS 初始化时，队列中会率先诞生一个内部 `thread == null` 的特殊傀儡节点——`head`。其核心设计目的在于：**消除边界条件，实现代码结构的极端统一**。

**1. 统一入队逻辑（简化分支）**

无需在每次新节点入队时，单独去写 `if (tail == null)`（队列空）和 `else`（队列非空）这两套截然不同的指针修改分支。有了哑节点垫底，任何时候入队都视作「**往现有队列的尾部追加节点**」。

**2. 统一出队逻辑（旧 head 协助断开）**

当 `head.next`（排队老大）成功抢到锁时，它只需要调用 `setHead(node)` 顺理成章地坐上 `head` 的位置（并把内部 `thread` 清空，使自己成为新的哑节点）。接着，直接执行 `oldHead.next = null` 即可完成出队，**没有任何特殊的指针兜底逻辑，对 GC 极其友好**。

#### 9.4.2 深度理解：消灭队首边界特例

> [!abstract] 没有哑节点 vs 引入哑节点
> - **没有哑节点时**：队首节点在醒来向前检查或被取消时，必须先加一句判断 `if (node.prev == null)`。如果是 `null`，说明自己是老大，要走「直接与锁状态（state）交互」的特例分支 A；如果不是 `null`，才走普通分支 B。这使得队首和后续节点的处理逻辑产生了**肉眼可见的断层**。
> - **引入哑节点后**：排在第一位的排队线程，它的 `prev` 稳稳地指向 `head`。

**核心代码红利**：整个队列里所有在排队的节点，其 `prev` 指针**全部拥有合法存在的 Node 实体，绝对不为 `null`**。判断自己是不是队首，只需要无脑调用 `if (node.prev == head)` 即可。**它让第一个排队的节点也能享受和普通节点完全一样的"待遇"**，在代码层面上彻底干掉了 `if (p.prev == null)` 这样的边界特例判断。

#### 9.4.3 面试话术模板

> AQS 用哑节点垫底的本质是**消灭队首边界特例**。有了哑节点，队首节点 `prev` 也指向一个合法 Node（head），所有排队节点判断"自己是不是队首"统一用 `if (p == head)`，**和普通节点的处理逻辑完全一致**——既简化了入队（不用区分队列空/非空），也简化了出队（旧 head 帮新 head 断开 `next` 引用），还顺便给 GC 一个干净的指针图。哑节点是 AQS 用**结构设计换取代码简洁性**的典型案例。

---

### 9.5 为什么 CLH 队列是双向的？

**取消节点需要 `prev`**：从后往前找未取消的节点时，`prev` 是唯一可靠的指针（`next` 在并发下可能短暂为 null）。

#### 9.5.1 缺陷诞生的三大时序步骤

AQS 为了追求极致的吞吐量，新节点入队操作（`enq` 方法）**没有加任何互斥锁**，而是完全依赖 **CAS 无锁并发**。由于多步操作之间没有原子性屏障，在极高并发的纳秒级时间差内，就会产生 `next` 指针短暂为 `null` 的现象。

当一个新节点 `B` 想要插到老队尾 `A` 的后面时，底层的无锁入队源码执行顺序如下：

```java
node.prev = t;                              // ① 先让新节点 B 的 prev 指向老 tail (A)
if (compareAndSetTail(t, node)) {           // ② CAS 让全局 tail 指针指向新节点 B
    t.next = node;                          // ③ 让老 tail (A) 的 next 指向新节点 B
}
```

#### 9.5.2 致命时间差（Race Condition）

> [!danger] next 指针断路的诡异瞬间
> 假设线程 1 在执行入队时：
> 1. 成功执行完步骤 ①：`B.prev = A`
> 2. 成功执行完步骤 ②：全局的 `tail` 指针在微观上瞬间变成了 `B`——**此时 AQS 宏观视角认为节点 B 已经入队成功**
> 3. **【关键卡点】** 就在线程 1 准备去执行步骤 ③（`A.next = B`）的前一瞬间，CPU 突然发生了上下文切换或缓存延迟，导致**步骤 ③ 还没来得及执行**！
>
> 此时整个队列的诡异状态为：
> - 从后往前看（逆向）：全局 `tail` 顺着 `prev` 指针能够一路完美地连回 `head`（**通路**）
> - 从前往后看（正向）：**`A.next` 依然是 `null`！**（**断路**）

#### 9.5.3 对 `cancelAcquire` / `unparkSuccessor` 的致命影响

就在这个指针断裂的微妙瞬间，如果另一个线程 2 进来执行 `cancelAcquire` 或释放锁。如果它偷懒**从前往后**（顺着 `next`）去遍历队列寻找可以唤醒或接任的节点，当它走到 `节点 A` 时，会看到 `A.next == null`。

线程 2 就会误以为：「**后面已经没有人在排队了**。」从而直接结束。这会导致**刚通过 CAS 入队的 `节点 B` 以及后续所有的排队线程，全部被无情漏掉，永远陷入死睡，队列直接瘫痪**。

> [!warning] 这就是为什么 AQS 一律必须从 tail 往前遍历
> 任何**剔除节点 / 从后往前找有效节点 / 唤醒后继**等核心安全操作，**绝对不能**用 `next`。一旦用了 `next`，遇到上述"步骤 ② 完、步骤 ③ 未完"的时间差窗口，就会把刚入队的节点当成"不存在"漏掉，整个队列静默死锁。

#### 9.5.4 安全救赎：为什么 `prev` 指针绝对可靠？

Doug Lea 在 AQS 中规定：**凡是涉及剔除节点、从后往前找有效节点、唤醒后继等核心安全操作，一律必须从 `tail` 往前（逆向）遍历。**

因为入队逻辑中铁律：**必须先建立好 `prev` 指针（步骤 ①），才能去执行步骤 ② 的 CAS。** 也就是说，任何一个只要能被全局 `tail` 指针扫描到的节点，它的 `prev` 指针**百分之百是完整且连通的**！从后往前找，是一条在高并发下**绝对不会产生逻辑断层的安全纽带**。

#### 9.5.5 核心归纳金句

> [!abstract] 双指针对称记忆
> 1. **Head 哑节点的存在**，是为了让"第一个排队的节点"也拥有合法不为 `null` 的 `prev` 前驱，从而**消灭队首边界特例**。
> 2. **CLH 队列之所以是双向的**，是因为正向的 `next` 指针在无锁 CAS 入队时存在天然的时序时间差，可能短暂为 `null`；而**逆向的 `prev` 指针则是高并发下绝对连续、安全兜底的唯一生命线**。

#### 9.5.6 面试话术模板

> AQS 的 CLH 队列用**双向链表**而不是单向，**根本原因不是性能，而是正确性**。`enq` 入队采用无锁 CAS，三步操作（`prev = t` → `CAS tail` → `t.next = node`）之间没有原子屏障，并发下 `t.next` 可能短暂为 `null`。如果用单向链表，**任何顺着 `next` 找的操作**（`unparkSuccessor` / `cancelAcquire`）都会在这个时间差窗口把刚入队的节点当成"不存在"漏掉，**整个队列静默死锁**。AQS 的解法是：先建 `prev` 再 CAS `tail`（保证 `prev` 绝对可靠），所有安全操作**一律从 `tail` 逆向遍历**。这就是为什么 `prev` 是 AQS 链表里的**安全生命线**。

---

### 9.6 `acquireQueued` 为什么用死循环？

先看源码（4.3 节）：

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {                                        // ← 死循环核心
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {           // ① 抢锁条件：前驱是 head 且能抢到
                setHead(node);
                p.next = null;                            // help GC
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node)    // ② 前驱是 head 抢不到，或前驱不是 head → 设 SIGNAL
                && parkAndCheckInterrupt())               // ③ 真挂起
                interrupted = true;
        }
    } finally {
        if (failed) cancelAcquire(node);
    }
}
```

`for (;;)` 这个死循环**不是 AQS 偷懒**，是**应对 4 个不确定性的唯一安全做法**——线程从 `park` 醒来时，**它观察到的所有状态都是"过去时"**。

#### 9.6.1 前驱可能不是 head（你被叫醒了，但不是叫你的）

`unparkSuccessor` 是**从前驱节点触发**的唤醒——但**前驱 ≠ head** 是完全可能发生的。

典型场景：**前面是取消节点**

```
head → [CANCELLED] → 你 (park 中)
                   ↑
                你就是被唤醒的
```

- 节点 C 取消了（waitStatus=1），你变成 head 的直接后继，但 head 没变
- 现在 C 走 `cancelAcquire` 分支 2（见 [[#6.4 AQS 的终极解法：激活动作传递|6.4]]）：**亲自 `unparkSuccessor(C)` 拍醒你**
- 你从 park 中醒来，但**你的前驱 `p` 是 C（已经 CANCELLED）**，不是 head

此时**不能直接 `tryAcquire`**——得让 `for(;;)` 重头再来一次：
- 下一轮循环里，`p = node.predecessor()` 重新算前驱
- 看到 `p != head`，**乖乖走 `shouldParkAfterFailedAcquire`**，不抢
- **死循环帮你"自动纠错"**——用"重检"代替"记忆"

#### 9.6.2 `tryAcquire` 可能失败（前驱释放的瞬间被别人抢走）

最典型：**非公平锁**下（见 [[#3.2 共享模式 vs 独占模式|3.2.2]]）。

```
时序：
1. 你 park 中，前驱 P 持锁
2. P 释放锁，state=0
3. unparkSuccessor(P) 叫醒你
4. 但在你被 OS 调度起来之前，外部新线程 T_new 抢到锁，state=1
5. 你终于被调度起来，调 tryAcquire → 失败
```

非公平锁的设计就是允许这种**插队**。如果你醒来后**只调一次 tryAcquire 就放弃**，你就被这个"瞬间时序"误伤了。**死循环保证：失败了就再 park 一次，等下次被叫醒**。

#### 9.6.3 伪唤醒（JLS 允许 OS 偶尔瞎叫）

JLS 明文规定：`LockSupport.park` 可能**没有任何原因**就返回（见 [[#4.5 parkAndCheckInterrupt：真挂起|4.5]] 节的 `> [!warning]` 标注）。

- 没收到 `unpark` 信号
- 没收到 `interrupt` 信号
- 就是 OS / CPU 层面偶发**瞎叫**

如果用 `if + park` 写法，看到 park 醒来就以为"有人叫我"——**实际上可能是假的**。`for(;;)` 死循环让 park 醒来**永远走同一条重检路径**，用"状态"对抗伪唤醒。

#### 9.6.4 隐藏的第 4 条：cancelAcquire 分支 2 亲自唤醒路径

结合 [[#6.4 AQS 的终极解法：激活动作传递|6.4]] 节的内容：

- 你的前驱是 head，**但你取消了**
- 你走 `cancelAcquire` 的**分支 2**：`unparkSuccessor(this)` 亲自拍醒后继兄弟
- **兄弟被你叫醒**，但兄弟**不知道**叫醒它的是个**已经 CANCELLED 的节点**

兄弟醒来后：
- 它**必须**重新走 `for(;;)` 循环
- 重新算 `p = node.predecessor()`
- 发现前驱变了（CANCELLED 节点已经从链表里**被剪掉**了），重检一切

**这正是 6.4 节"队列自愈"的来源**——死循环 + 重新取前驱，让链表结构变化对等待者**完全透明**。

#### 9.6.5 错误设想：如果不用死循环会怎样？

```java
// ❌ 错误写法：用 if 代替 for(;;)
if (p == head && tryAcquire(arg)) {
    setHead(node);
    return true;
}
if (shouldParkAfterFailedAcquire(p, node)) {
    LockSupport.park(this);     // park 一次就完事
    return false;               // 返回，不重检
}
```

**3 个致命问题**：

| 错误点 | 后果 |
|--------|------|
| **不知道为何醒来** | park 醒来可能是真唤醒、也可能是 interrupt、也可能是伪唤醒——没循环就**无法分辨**，直接往下走 |
| **不知道抢锁成不成立** | `shouldParkAfterFailedAcquire` 返回 false 那次**没真的 park**，下次 park 是"补 park"，if 写法就漏处理了 |
| **不知道前驱还是不是 head** | 醒来时前驱可能**已经被剪掉**（前驱 cancelAcquire），`p == head` 的判断**瞬间过期**——必须重新取前驱 |

**唯一安全做法：用循环重检一切，从不信任"我醒来时观察到的状态"**。

#### 9.6.6 面试话术模板

> `acquireQueued` 用 `for(;;)` 死循环是 AQS 的**核心防御机制**。线程从 `park` 醒来时，**它观察到的所有状态都是"过去时"**：
> - 前驱可能不是 head 了（被取消了、被剪掉了）
> - 抢锁可能失败（被新线程插队了）
> - 醒来原因不确定（unpark / interrupt / 伪唤醒）
>
> 死循环让线程**重检一切**——重新取前驱、重新 tryAcquire、重新判断 waitStatus。**AQS 用"重检"对抗"状态过期"，用"循环"对抗"不确定性"**。这是 AQS 能正确处理高并发取消/唤醒/插队的根本原因。

---

## 十、典型子类一览

| 子类 | 模式 | state 语义 | 典型用途 |
|------|------|------------|----------|
| **ReentrantLock** | 独占 | 重入次数 | 互斥锁 |
| **ReentrantReadWriteLock** | 独占+共享 | 高 16 位读 / 低 16 位写 | 读写分离 |
| **Semaphore** | 共享 | 剩余许可证 | 限流、连接池 |
| **CountDownLatch** | 共享 | 倒计数 | 等待多任务完成 |
| **CyclicBarrier** | 共享 | 当前代 parties | 多线程汇合 |
| **ThreadPoolExecutor.Worker** | 独占 | 0/1（独占 worker） | 线程池 worker 串行 |

---

## 十一、面试话术模板

> "AQS 是 JUC 同步器的底层基石，名字直译就是「抽象队列同步器」。它用三要素实现：**volatile int state 字段**表示同步状态（子类定义语义，比如 ReentrantLock 用来数重入次数，Semaphore 用来数许可证）；**CLH 变体队列**管理等待线程；**Node.waitStatus** 表达 4 种状态，最关键的是 SIGNAL——后继告诉前驱「你释放时记得 unpark 我」。
>
> AQS 用模板方法模式设计：父类提供 `acquire`/`release` 等 final 模板方法，子类只需重写 `tryAcquire`/`tryRelease` 等钩子方法。AQS 对原始 CLH 做了两个关键改造——**把自旋改成 LockSupport.park 挂起**（避免空耗 CPU），**加 waitStatus 状态机**。ReentrantLock、Semaphore、CountDownLatch、ReentrantReadWriteLock、ThreadPoolExecutor 的 Worker 全部基于 AQS。
>
> 加锁走四步流程：裸 CAS 抢 → 抢不到走 acquire → tryAcquire 完整尝试（含可重入判断）→ addWaiter 入队 → acquireQueued 死循环（前驱是 head 才抢，否则 shouldParkAfterFailedAcquire 设前驱为 SIGNAL，然后 parkAndCheckInterrupt 真挂起）。解锁走 release：tryRelease 减 state → state 减到 0 调 unparkSuccessor 唤醒后继（从后往前找，跳过 CANCELLED 节点）。
>
> AQS 的核心思想是**把抢锁失败后的排队、挂起、唤醒、取消这些苦力活都封装好**，子类只关心'什么算拿到锁'。这也是为什么 synchronized 不能做的'可中断、可超时、公平、多条件变量'，AQS 子类全都能做。"

---

## 关联知识点

- [[ReentrantLock详解]] — AQS 最重要的独占模式使用者，state=重入次数
- [[Condition 详解]] — AQS 内部类 ConditionObject，独立详解
- [[Java并发编程面试题]] — 并发编程主文件，多次引用 AQS
- [[synchronized 关键字]] — 与 AQS 子类（ReentrantLock）的对比
- [[线程池]] — ThreadPoolExecutor 的 Worker 用 AQS 独占模式
- [[Semaphore详解]] — AQS 共享模式经典案例（已建：acquireShared / setHeadAndPropagate 链式唤醒 / 与 CountDownLatch 对比）
- [[CountDownLatch详解]] — AQS 共享模式 + 一次性门闩（已建：`tryAcquireShared` 一行精华 + `tryReleaseShared` 防负数 + 与 CyclicBarrier 对