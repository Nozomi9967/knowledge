---
tags:
  - java
created: 2026-06-28
updated: 2026-06-28
---
# Java并发-并发安全（参考答案）

> 题目对外展示，点击下方"参考答案"可展开查看。
> 范围：[[Java并发编程面试题]] 第二大节「## 并发安全」。

---

### 1. JUC 包下你常用的类有哪些？请按四类各举 2-3 个并说明场景。

> **问题描述**
> 开场题，考察 JUC 全景图。回答关键不是"罗列 API"，而是"四类划分 + 各自解决什么问题"。
>
> 建议回答顺序：先一句话定义 JUC → 线程池（解决资源管理）→ 并发集合（解决 HashMap 不安全）→ 同步工具（解决线程协作）→ 原子类（解决单变量原子操作）→ 最后点出"四类问题 = 四类解决方案"的对应关系。

> [!abstract]- 参考答案
> JUC（`java.util.concurrent`）是 JDK 提供的并发工具包，按"解决的问题"分四类：
>
> | 分类 | 常用类 | 典型场景 |
> |------|--------|----------|
> | **线程池** | `ThreadPoolExecutor`、`Executors`、`ScheduledThreadPoolExecutor` | 异步任务、定时任务、削峰填谷 |
> | **并发集合** | `ConcurrentHashMap`、`CopyOnWriteArrayList`、`ConcurrentLinkedQueue` | 高并发 KV 读写、读多写少、并发队列 |
> | **同步工具** | `CountDownLatch`、`CyclicBarrier`、`Semaphore` | 主等子（一次性）、线程相互等（可循环）、限流/连接池 |
> | **原子类** | `AtomicInteger`、`AtomicLong`、`AtomicReference`、`LongAdder` | 计数器、状态标志、序列号、高并发计数 |
>
> **一句话**：JUC = 线程池（资源管理）+ 并发集合（数据结构）+ 同步工具（线程协作）+ 原子类（无锁单变量）——四类对应"四种并发问题"。

---

### 2. CountDownLatch 的实现原理？和 CyclicBarrier 的对比？

> **问题描述**
> 经典对比题。面试官喜欢追："CountDownLatch 能否循环使用？""CyclicBarrier 能不能像 CountDownLatch 那样一个线程等？"——把"是否可重用"和"谁等谁"两个维度说透。
>
> 进阶：CountDownLatch 基于 AQS 共享模式实现，`state` 初始为 N，每个 `countDown()` CAS 减 1，await() 在 state=0 时唤醒。

> [!abstract]- 参考答案
> **CountDownLatch** 核心原理：
> 1. 初始化时给一个计数器 N（`new CountDownLatch(N)`）
> 2. 任务线程完成后调用 `countDown()` 减 1
> 3. 等待线程调用 `await()`，**阻塞直到计数器归 0**
> 4. 归 0 后所有 await 线程一次性唤醒
>
> **CountDownLatch vs CyclicBarrier**：
>
> | 维度 | CountDownLatch | CyclicBarrier |
> |------|---------------|---------------|
> | **是否可重用** | ❌ 一次性，计数器归 0 后不能 reset | ✅ 可循环，parties 个线程到齐后自动 reset |
> | **谁等谁** | N 个任务线程等主线程（或反向） | N 个线程相互等，**到齐后一起继续** |
> | **典型场景** | 主线程汇总：等 N 个子线程跑完再继续 | 多阶段任务：所有线程完成阶段 1 一起进入阶段 2 |
> | **底层** | AQS 共享模式 | ReentrantLock + Condition + Generation |
>
> **记忆口诀**：CountDownLatch = "主等子、一次性"；CyclicBarrier = "互相等、可循环"。

---

### 3. Semaphore 内部基于 AQS 的共享模式还是独占模式？

> **问题描述**
> 考察 AQS 两种模式的区分。ReentrantLock 独占、Semaphore/CountDownLatch 共享。共享模式的"链式唤醒"是高频追问点。
>
> 进阶追问：Semaphore 公平/非公平实现区别？许可证累加原理？

> [!abstract]- 参考答案
> **Semaphore 基于 AQS 共享模式**实现。
>
> **共享 vs 独占的本质区别**：
> - **独占模式**（如 ReentrantLock）：state 表示"锁是否被持有"，1 个线程独占，释放后**只唤醒后继 1 个节点**
> - **共享模式**（如 Semaphore）：state 表示"剩余许可数"，可被多个线程同时持有，释放后**链式唤醒**（后继节点拿到许可后还会继续唤醒它的后继）
>
> Semaphore 的 `state` 初始为 N 个许可；`acquire()` 用 CAS 把 state -1（≥0 成功）；`release()` 把 state +1，并触发共享模式下的 `doReleaseShared()` 唤醒后继。
>
> **和 ReentrantLock 的核心区别**：ReentrantLock 关注"互斥"（一时刻一线程），Semaphore 关注"准入"（一时刻最多 N 线程）。

---

### 4. ConcurrentHashMap 1.7 vs 1.8 的本质不同？为什么废弃分段锁？

> **问题描述**
> 高频题，但常被简答成"1.7 用 Segment、1.8 用 CAS+synchronized"——这只是表象，要答出"为什么 1.8 要废弃"。
>
> 进阶：1.8 锁粒度变成"桶头节点"，并发度从"段数（默认 16）"提升到"桶数（成百上千）"。

> [!abstract]- 参考答案
> **JDK 1.7**：**分段锁（Segment）**——每个 Segment 继承 ReentrantLock，相当于把整张表分成 16 段，**每段一把锁**，并发度 = 段数 = 16。
>
> **JDK 1.8**：**CAS + synchronized 锁桶头节点**——摒弃 Segment，锁粒度细化到**单个桶（链表头/红黑树根节点）**，并发度 = 桶数（远大于 16）。
>
> **为什么废弃分段锁**：
> 1. **并发度瓶颈**：1.7 最多 16 个段同时写，段数写死后无法应对更高并发
> 2. **空间浪费**：每个 Segment 都继承 ReentrantLock，有额外内存开销
> 3. **查询路径长**：查一个 key 要先定位段、再定位桶，两次哈希
> 4. **1.8 的细粒度锁已足够**：CAS 处理无冲突插入（锁桶头），synchronized 处理有冲突（锁桶头），配合红黑树，热点分散到每个桶
>
> **记忆**：1.7 是"粗粒度大锁"（锁段），1.8 是"细粒度小锁"（锁桶头 + 树化）。

[[ConcurrentHashMap_1.7_vs_1.8]]

---

### 5. CopyOnWriteArrayList 的适用场景与副作用？

> **问题描述**
> 经典坑题：很多人推荐 COW 给"读多写少"用，但没意识到它的副作用——**写时复制会导致 STW 级别的内存抖动**。
>
> 进阶追问：COW 适合"白名单/黑名单"这种几乎不写的场景，为什么不适合事件订阅列表？

> [!abstract]- 参考答案
> **适用场景**：**读多写少 + 数据量不大 + 一致性弱要求**（读能容忍读到旧版本）。
>
> **典型例子**：
> - 配置中心黑/白名单
> - 事件监听器列表（启动后基本不变）
> - 路由表
>
> **副作用（不适合写多）**：
> 1. **写时复制整数组**：每次 add/set 都要 `Arrays.copyOf()` 一份新数组，旧数组还要等所有读线程 GC 完才能释放，**大列表写一次成本 = 复制一次**
> 2. **内存占用翻倍**：写瞬间会出现"新旧两个数组"同时存在
> 3. **读可能读到旧数据**：因为没有加锁，写和读是分离的快照
> 4. **不适合高频写**：比如"实时事件订阅列表"——每次 add 一个监听器都要复制整个数组，绝对不能这么用
>
> **对比选择**：写多 → `ConcurrentLinkedQueue` 或 `synchronized List`；读多写少 → `CopyOnWriteArrayList`。

---

### 6. synchronized 底层实现原理？monitorenter / Monitor 对象 / Mark Word？

> **问题描述**
> 必考题，但 90% 的人答不全。三个层面：JVM 字节码（monitorenter/monitorexit）→ 对象头（Mark Word）→ 操作系统互斥量（ObjectMonitor）。
>
> 进阶：ObjectMonitor 的 `_owner`、`_entryList`、`_waitSet` 三个字段各管什么？

> [!abstract]- 参考答案
> synchronized 底层实现分三个层面：
>
> **1. 字节码层面**：
> - 进入同步块：插入 `monitorenter` 指令
> - 退出同步块：插入 `monitorexit` 指令（正常退出和异常退出各一次）
> - 同步方法：在方法访问标志位加 `ACC_SYNCHRONIZED`，由 JVM 在方法调用时隐式执行 monitorenter/exit
>
> **2. 对象头层面（Mark Word）**：
> - Mark Word 是对象头的核心字段，根据锁状态存储不同内容
> - **无锁**：hashCode、GC age 等
> - **偏向锁**：持有线程的 ThreadID
> - **轻量级锁**：指向线程栈上 LockRecord 的指针
> - **重量级锁**：指向 ObjectMonitor（Monitor）对象的指针
>
> **3. 操作系统层面（重量级锁阶段）**：
> - Mark Word 指向 JVM 内部的 `ObjectMonitor` 对象（C++ 实现）
> - ObjectMonitor 核心字段：
>   - `_owner`：指向持有锁的线程
>   - `_entryList`：等待锁的线程队列（被 `monitorenter` 阻塞的线程）
>   - `_waitSet`：调用了 `wait()` 的线程队列
>   - `_count`：重入计数器
> - `_owner` 通过 CAS 从 NULL 抢到当前线程引用 → 抢锁成功；释放时从引用改回 NULL → 释放
> - 真正阻塞/唤醒线程依赖底层 `pthread_mutex`（内核态），所以叫"重量级"
>
> **一句话**：字节码 monitorenter → Mark Word 状态切换 → 重量级阶段转 ObjectMonitor + pthread_mutex。

---

### 7. synchronized 锁升级过程？四种状态的 Mark Word 标志位与适用场景？

> **问题描述**
> 锁升级是面试必问，且常被追问"轻量级锁怎么变成重量级锁""CAS 自旋失败几次升级"。每个状态的 Mark Word 内容、标志位、适用场景都要说清。
>
> 进阶：JDK 15 之后偏向锁默认禁用，路径变成"无锁→轻量级→重量级"。

> [!abstract]- 参考答案
> synchronized 锁升级路径：**无锁 → 偏向锁 → 轻量级锁 → 重量级锁**，**单向不可降级**。
>
> | 状态 | Mark Word 内容 | 标志位 | 核心机制 | 适用场景 |
> |------|---------------|--------|----------|----------|
> | **无锁** | hashCode、GC age | 01 + 0 | 无同步 | 对象刚创建 |
> | **偏向锁** | 持有线程 ThreadID | 01 + 1 | CAS 记录 ThreadID | 同一线程反复进入（无竞争） |
> | **轻量级锁** | 指向线程栈 LockRecord 的指针 | 00 | CAS + 自旋 | 多线程交替执行（短时竞争） |
> | **重量级锁** | 指向 ObjectMonitor 的指针 | 10 | 阻塞 + 唤醒 | 多线程真正同时竞争 |
>
> **升级时机**：
> 1. 第一个线程进入 → 升级为**偏向锁**（CAS 记录 ThreadID）
> 2. 第二个线程尝试进入 → **撤销偏向锁**（需到 SafePoint）→ 升级为**轻量级锁**
> 3. 轻量级锁 CAS 失败 → **自旋重试**（JDK 1.6+ 自适应自旋）
> 4. 自旋达到阈值仍失败 → **膨胀为重量级锁**
>
> **核心思想**：用"空间换复杂度"——JVM 在对象头里维护锁状态，根据实际竞争激烈程度选择最轻的同步方式。

---

### 8. JDK 15 之后偏向锁被默认废弃的原因？废弃后的实际升级路径？

> **问题描述**
> 进阶题。考的是"是否了解 JDK 新动向"。如果答完锁升级不补充这一点，面试官会认为你知识停留在 1.6 时代。
>
> 延伸：JEP 374 / JEP 18 走向，禁用原因——现代应用很少从偏向锁获益（JVM 实现成本高）。

> [!abstract]- 参考答案
> **废弃原因（JEP 374）**：
> 1. **JVM 实现复杂度过高**：偏向锁的"撤销"需要在 SafePoint 暂停线程，开销不小；现代应用很少有"同一线程反复进入同步块"的纯单线程场景
> 2. **现代应用场景变了**：Lambda、Stream、虚拟线程（Java 21+）等场景下，几乎没有线程能反复独占同一把锁，偏向锁的优化收益微乎其微
> 3. **维护成本不划算**：撤销逻辑复杂、容易引入新 bug，HotSpot 团队希望简化锁机制
>
> **废弃后的实际路径**：
> - **JDK 15**：偏向锁默认禁用（`-XX:+UseBiasedLocking` 仍然可用但 deprecated）
> - **JDK 18**：偏向锁的 HotSpot 实现被 obsolete
> - **JDK 21**：`UseBiasedLocking` 命令行选项 deprecated
>
> **新路径**：**无锁 → 轻量级锁 → 重量级锁**（去掉了"偏向锁"中间态）。
>
> **面试建议**：在回答锁升级时主动补充这个变化，**会加分**——面试官能看出你跟得上 JDK 新动向。

---

### 9. JVM 对 synchronized 的四种优化？

> **问题描述**
> 必答题。锁升级/锁消除/锁粗化/自适应自旋——四件套。
>
> 进阶：每种优化的**触发条件**和**是否需要参数开启**。

> [!abstract]- 参考答案
>
> | 优化 | 触发条件 | 效果 | 是否需要参数 |
> |------|----------|------|--------------|
> | **锁升级** | 竞争激烈程度变化 | 从无锁→偏向→轻量级→重量级，逐级加重 | 默认开启 |
> | **锁消除** | JIT 通过**逃逸分析**发现对象只在单线程内使用 | 消除整个 synchronized 块 | `-XX:+DoEscapeAnalysis` + `-XX:+EliminateLocks`（JDK 1.6+ 默认开） |
> | **锁粗化** | 同一对象在循环或连续操作中反复加锁解锁 | 把多个锁合并为一个 | 默认开启（JIT 优化） |
> | **自适应自旋** | 轻量级锁 CAS 失败时 | 根据上次自旋结果动态调整本次自旋次数（JDK 1.6+） | 默认开启 |
>
> **经典例子**：
> - 锁消除：方法内的 `StringBuffer.append()`（局部对象，逃逸不出去）
> - 锁粗化：`for(int i=0;i<1000;i++) synchronized(list) { list.add(...); }` 粗化为 `synchronized(list) { for(...) list.add(...); }`
> - 自适应自旋：上次自旋成功 → 这次自旋更久；上次没成功 → 这次可能直接放弃自旋升级重量级
>
> **锁只能升级不能降级**——保证一致性，避免降级过程中的复杂同步问题。

---

### 10. synchronized 是可重入锁吗？两个阶段如何实现？

> **问题描述**
> 看似简单，但常被问"可重入的意义是什么？"——答出"防止递归调用死锁"还不够，要答出"计数器的具体实现位置"。
>
> 进阶：可重入语义和"持有线程"字段密切相关。

> [!abstract]- 参考答案
> **synchronized 是可重入的**——同一线程对同一把锁可以多次 `monitorenter` 不会死锁。
>
> **为什么需要可重入**：避免"递归调用 / 子方法调用"时自己锁自己——**同一线程再次申请已被自己持有的锁时不被挂起**，否则递归/子方法调用会自己等自己，永远释放不了锁，直接死锁。
>
> 详细解释见 [[可重入性详解]]。
>
> **不同阶段的实现**：
>
> | 锁阶段 | 重入计数存哪 | 机制 |
> |--------|-------------|------|
> | **偏向锁** | Mark Word 里存的是 ThreadID，无需计数器（同一线程直接放行） | 线程 ID 一致就放行 |
> | **轻量级锁** | 线程栈上**LockRecord** 链（每次重入压入一个新 LockRecord） | 每次重入压一个 LockRecord，displaced mark word 为空表示是重入 |
> | **重量级锁** | ObjectMonitor 的 **`_count` 字段** | 每次 `monitorenter` +1，`monitorexit` -1；减到 0 才真正释放 |
>
> **面试话术**：synchronized 任何阶段都是可重入的，区别只在于"重入计数存在哪"——轻量级靠 LockRecord 链，重量级靠 ObjectMonitor._count。

---

### 11. synchronized 修饰普通方法 vs 静态方法？

> **问题描述**
> 看似基础，但很多人会忽略"多实例下静态方法锁的范围"——不同实例的 `synchronized 普通方法` 互不影响，但**所有实例的 `synchronized 静态方法` 互相阻塞**。
>
> 进阶：和"Class 对象是全局唯一"的关系。

> [!abstract]- 参考答案
>
> | 维度 | 普通方法 | 静态方法 |
> |------|----------|----------|
> | **锁的对象** | 当前实例（`this`） | 当前类的 `Class` 对象（全局唯一） |
> | **作用范围** | 同一实例的同步方法互斥 | 所有实例的该静态同步方法互斥 |
> | **多实例影响** | 不同实例的同步方法**可并行** | 不管多少实例，**同时只有 1 线程**能执行 |
>
> **底层原理**：
> - 普通方法的 monitor 指向 `this`（每个实例一个）
> - 静态方法的 monitor 指向 `Class<?>` 对象（类加载器加载时生成，JVM 全局唯一）
>
> **反例**：
> ```java
> // 错误：以为锁住了 Counter 类的所有 increment
> public class Counter {
>     public synchronized void increment() { count++; }  // 锁 this
> }
> // 多实例下，每个实例的 increment 互不影响——会超卖！
> ```
>
> **正确做法**：用 `synchronized static` 或改用 `AtomicInteger`。

---

### 12. synchronized vs ReentrantLock 的核心区别？

> **问题描述**
> 经典对比题。三个维度：用法、特性、性能。
>
> 进阶：JDK 1.6 之后 synchronized 性能已大幅优化（锁升级），和 ReentrantLock 性能差距不大，**选择标准更多是"特性需求"而非性能**。

> [!abstract]- 参考答案
>
> | 维度 | synchronized | ReentrantLock |
> |------|--------------|---------------|
> | **用法** | 关键字，JVM 自动加锁/释放锁（monitorenter/exit） | 类，需手动 `lock()` / `unlock()`，通常配合 try-finally |
> | **公平性** | ❌ 非公平 | ✅ 可选公平/非公平（`new ReentrantLock(true)`） |
> | **可中断** | ❌ 不可中断 | ✅ `lockInterruptibly()` |
> | **超时** | ❌ | ✅ `tryLock(timeout, unit)` |
> | **多条件变量** | ❌ 只有一个（`wait`/`notify`） | ✅ 一个锁可绑多个 `Condition` |
> | **实现** | JVM 层面，ObjectMonitor + mutex | JDK 层面，基于 AQS（CAS + CLH 队列） |
> | **性能** | JDK 1.6+ 锁升级优化后接近 ReentrantLock | 高竞争时仍略优 |
>
> **选型建议**：
> - 简单同步 + 无高级需求 → **synchronized**（简洁、不易出错）
> - 需要公平锁 / 可中断 / 超时 / 多条件变量 → **ReentrantLock**
> - 注意：**tryLock + unlock 必须 try-finally 配对**，否则会死锁或泄漏


>“可中断”指的是**正在等待获取锁的线程可以被中断（Interrupt）**。

---

### 13. AQS 三要素？state 在三种同步器中分别代表什么？

> **问题描述**
> AQS 是整个 JUC 工具类的基石，三要素必须背熟。state 的"语义因实现类而异"是高频考点。
>
> 进阶：AQS 同步队列（CLH 变体，双向）和条件队列（每 Condition 一个单向）的关系。

> [!abstract]- 参考答案
> **AQS 三要素**：
>
> 1. **`state`（volatile int 同步状态）**——用 CAS 修改，代表"资源的占用情况"，**具体含义由实现类定义**
> 2. **CLH 变体双向队列**——存放"等待抢锁的线程节点"
> 3. **获取/释放方法（模板方法模式）**——AQS 实现了 acquire/release 的整体流程框架，子类重写 `tryAcquire`/`tryRelease` 等钩子
>
> **state 在不同实现类中的含义**：
>
> | 实现类 | state 含义 |
> |--------|-----------|
> | **ReentrantLock** | 锁的占有情况：0=未占用，>0=持有线程的重入次数 |
> | **Semaphore** | 剩余许可证数量 |
> | **CountDownLatch** | 还需要倒数的数量（await 归 0 唤醒） |
> | **ReentrantReadWriteLock** | 高 16 位=读锁计数，低 16 位=写锁计数 |
> | **ThreadPoolExecutor** | Worker 线程数 + 锁状态（用内部类 Worker 继承 AQS） |
>
> **一句话**：state 是 AQS 给"资源"打的标签，**标签含义由实现类定义**，AQS 只负责"围绕 state 做排队 + 阻塞 + 唤醒"。

---

### 14. AQS 用的是哪种设计模式？为什么把"state 语义"留空给子类？

> **问题描述**
> 常被答错成"策略模式"或"观察者模式"。正确答案是**模板方法模式**——AQS 写好流程，子类填具体逻辑。
>
> 进阶：子类必重写的 4 个方法（tryAcquire/tryRelease/tryAcquireShared/tryReleaseShared）+ isHeldExclusively。

> [!abstract]- 参考答案
> **AQS 用的是模板方法模式（Template Method Pattern）**。
>
> **为什么这么设计**：
> - AQS 实现了"获取/释放资源的通用流程框架"（入队 → 阻塞 → 等待唤醒 → 出队）
> - 但**"state 到底代表什么"、"获取成功/失败的判断标准"在每个同步器里都不同**——比如 ReentrantLock 看 owner 线程、Semaphore 看 state>0、CountDownLatch 看 state=0
> - 把这部分"易变逻辑"留空给子类，**AQS 才能"一统江山"地支撑整个 JUC**
>
> **子类需要重写的核心方法**（独占模式）：
> - `tryAcquire(int)`：尝试获取锁（CAS 改 state + 业务判断）
> - `tryRelease(int)`：尝试释放锁
> - `isHeldExclusively()`：是否独占持有（Condition 用）
>
> **共享模式**：
> - `tryAcquireShared(int)` / `tryReleaseShared(int)`
>
> **典型例子**：
> - ReentrantLock 的 `tryAcquire`：CAS 改 state（0→1），已持有则 +1 实现重入
> - Semaphore 的 `tryAcquireShared`：state > 0 时 CAS 减 1
> - CountDownLatch 的 `tryReleaseShared`：CAS 把 state 减 1，state=0 时返回 true
>
> **面试话术**：AQS 是"JUC 的发动机"——它定义了"线程怎么排队、怎么阻塞唤醒"的通用流程，**子类只要回答"state 是什么、获取/释放怎么算成功"两个问题**。

---

### 15. ReentrantLock 如何实现公平锁？公平 vs 非公平的关键差异（一行级别）？

> **问题描述**
> 经典源码对比题。关键差异只有**一行**——`hasQueuedPredecessors()`。
>
> 进阶：非公平锁吞吐量大的本质原因（避免线程挂起/恢复的上下文切换）。

> [!abstract]- 参考答案
> **公平锁实现**：在 `tryAcquire` 中除了 CAS 改 state，还**多判断一行**：
> ```java
> if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
>     setExclusiveOwnerThread(current);
>     return true;
> }
> ```
>
> **`hasQueuedPredecessors()`**：判断"等待队列中是否已经有线程在排队"。如果有，当前线程**不抢锁，直接入队**——保证 FIFO。
>
> **非公平锁**：去掉 `!hasQueuedPredecessors()` 判断，**直接 CAS 抢锁**，抢不到再入队。
>
> **为什么非公平吞吐量更大**：
> - 公平锁：释放锁 → 唤醒队首线程 → 该线程从阻塞态恢复（**用户态→内核态→用户态**，上下文切换开销大）
> - 非公平锁：释放锁时，正好有**新线程来抢锁** → 直接 CAS 拿到锁 → 避免了上下文切换
> - **本质**："插队"用 CAS 替代了"唤醒队首"，省了一次线程切换
>
> **特例**：`tryLock()` 无论公平/非公平都走 `nonfairTryAcquire()`，**一定插队**——即使设置了公平锁，tryLock 也能抢到。

>**用户态→内核态→用户态?**

原话是从整个并发系统引发的“上下文切换开销”**这个宏观角度来算的。一个完整的线程切换，包含了**“前任下马”**和**“现任上马”两个半场：

- **前半场（当前释放锁的线程 / 或者是系统调度器）：**
    
    当前线程执行完 `unlock()`，为了去唤醒排队的线程，它必须发起一个系统调用（System Call）。这时 CPU 必须从 **用户态 $\rightarrow$ 内核态**，进入内核去翻看 AQS 等待队列。
    
- **后半场（被唤醒的队首线程）：**
    
    内核在内核态中找到了队首线程，进行上下文保存与恢复，然后 CPU 带着新的线程数据切回 **内核态 $\rightarrow$ 用户态**。
    

将这两步连起来，就是一次完整的、代价昂贵的 **用户态 $\rightarrow$ 内核态 $\rightarrow$ 用户态** 的 CPU 模式切换。

---

### 16. 什么是非公平锁的"插队"？插队一定能提升吞吐量吗？什么场景导致线程饥饿？`tryLock()` 守不守公平性？

> **问题描述**
> 延伸上一题。常被追问"插队会不会导致饥饿"——答出"会"还不够，要说清"什么场景会"。

> [!abstract]- 参考答案
> **插队定义**：非公平锁释放时，**不直接唤醒队首**，而是让新到的线程先 CAS 抢一次锁；抢到就执行，抢不到才入队。
>
> **插队一定能提升吞吐量吗**：
> - **平均来看，能**——避免队首线程的"唤醒-恢复"开销
> - **极端情况反而降**：
>   1. 会导致排队的线程长时间抢不到锁（引发严重的线程饥饿）
>   2. 如果大量新线程同时插队引发严重的 CAS 锁竞争（CPU 暴涨、缓存冲突），吞吐量反而会下降
>
> **什么场景导致饥饿**：
> - 任务执行时间**极短**（如几微秒）+ 任务**源源不断涌入**
> - 队首线程刚被挂上队列，新线程就抢到锁跑完出队
>
>
> **`tryLock()` 不守公平性**——这是特例：
> ```java
> public boolean tryLock() {
>     return sync.nonfairTryAcquire(1);  // 直接走非公平
> }
> ```
> 即使你 `new ReentrantLock(true)` 设了公平锁，`tryLock()` 仍然插队——这是 API 设计的有意为之，避免"等待中"的语义破坏 tryLock 的"快速失败"特性。
>
> **面试话术**：默认 ReentrantLock 是非公平锁——插队用 CAS 换上下文切换，提升吞吐；但极端场景下可能导致饥饿。`tryLock()` 永远走非公平路径，不受公平性设置影响。

---

### 17. 悲观锁 vs 乐观锁？synchronized 和 AtomicInteger 分别属于哪类？

> **问题描述**
> 概念题，但面试官常问"乐观锁一定是无锁吗？""悲观锁一定是阻塞吗？"——答出"哲学思想"和"实现机制"的区别。

> [!abstract]- 参考答案
>
> | 维度 | 悲观锁 | 乐观锁 |
> |------|--------|--------|
> | **哲学** | 假设最坏情况：每次必有竞争 | 假设最好情况：先无竞争，冲突再重试 |
> | **行为** | 读/写前先加锁 | 修改前先读旧值，CAS 更新，失败重试 |
> | **实现** | `synchronized`、`ReentrantLock`、`ReadWriteLock` | `CAS`（`Atomic*`）、版本号、时间戳 |
> | **开销** | 线程阻塞/唤醒（上下文切换） | CAS 自旋（CPU 占用） |
> | **适用** | 写多、临界区时间长 | 读多、写少、临界区短 |
>
> **synchronized** = 悲观锁（上来就加锁，阻塞其他线程）。
> **AtomicInteger.incrementAndGet()** = 乐观锁（CAS 自旋重试）。
>
> **进阶理解**：
> - 乐观锁**不等于"无锁"**——CAS 自旋本质是有锁的（CPU 总线锁/缓存锁），只是不阻塞
> - 悲观锁的"悲观"在**用户态**——加锁失败就阻塞；乐观锁的"乐观"在**应用层**——失败就重试

---

### 18. CAS 三大缺点？Java 怎么解决"只能保证单个变量"？

> **问题描述**
> 经典题，但常被简答成"ABA + 自旋开销"——漏掉"只能单变量"这一条。**AtomicReference 能解决多变量吗？**——能，包装成对象。

> [!abstract]- 参考答案
> **CAS 三大缺点**：
>
> 1. **ABA 问题**：值从 A→B→A，CAS 看到值还是 A 误以为没变，但中间过程已变
> 2. **循环时间长开销大**：CAS 失败会自旋重试，长时间不成功会大量占用 CPU
> 3. **只能保证一个共享变量的原子操作**：多个变量同时改，CAS 无能为力
>
> **Java 怎么解决"只能单变量"**：
>
> - **`AtomicReference`**：把多个变量封装成一个**对象**，对对象引用做 CAS = 对多个字段整体原子操作
> - **加锁（synchronized / ReentrantLock）**：用悲观锁包住多变量操作
> - **多个 Atomic* 拼接**：**不行**——多个独立 CAS 之间没有原子性，多个变量之间会出现「A 改成功、B 还没改完就被打断」的不一致窗口
>
> **ABA 怎么解决**：`AtomicStampedReference` / `AtomicMarkableReference`——加版本号或标记位，CAS 时同时比较值和版本。
>
> **CAS 自旋开销怎么解决**：
> - 设置**最大自旋次数**（超过则升级为锁）
> - 配合 **`LongAdder`**（高并发计数时分散热点，CAS 失败的概率更低）
> - 业务层加**重试上限**（避免无限制重试）

---

### 19. 什么是 CAS 的 ABA 问题？举个具体场景，Java 怎么解决（API 名 + 字段）？

> **问题描述**
> 经典题。"举个具体场景"是常踩坑点——很多人只说"A→B→A" 不说"为什么会引发 bug"。
>
> 进阶：栈顶/链表节点的 ABA 场景。

> [!abstract]- 参考答案
> **ABA 定义**：CAS 比较的是"值"而不是"过程"——值从 A 改成 B 又改回 A，CAS 看到还是 A 就以为"没变过"，但中间过程已变。
>
> **具体 bug 场景（链表/栈）**：
> - 线程 1 准备 CAS 把栈顶 A 弹出，预期栈顶是 A
> - 线程 2 弹掉 A → 弹出 B → 又把 A 压回去（栈顶还是 A）
> - 线程 1 CAS 成功，弹出 A —— **但栈结构已被破坏**（B 也丢了）
>
> **Java 解决方案**：`java.util.concurrent.atomic.AtomicStampedReference<V>`
>
> **核心字段**：`value`（实际值） + `stamp`（int 版本号）
>
> **使用方式**：
> ```java
> AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);
>
> // CAS 同时比较值和版本号
> boolean success = ref.compareAndSet(100, 200, 0, 1);
> // 前提：当前值=100 且 版本号=0 → 才更新为（200, 1）
> ```
>
> **为什么能解决**：版本号单调递增，**A→B→A 之后版本号已经从 0 变成 2**，CAS 看到版本号对不上就失败。
>
> **轻量级替代**：`AtomicMarkableReference`——版本号改成 boolean mark（只关心"是否被改过"），适合"是否被改过"二值场景。

---

### 20. 为什么不能"所有锁都用 CAS"？

> **问题描述**
> 看似反问，其实考"自旋 vs 阻塞"的取舍。自旋的 CPU 开销不是无成本的。

> [!abstract]- 参考答案
> **CAS 本质是"自旋重试"**——失败了就循环重试。这带来两个问题：
>
> 1. **CPU 资源浪费**：CAS 长时间不成功，线程一直在循环占用 CPU 时间片
> 2. **不适用于长临界区**：如果持锁线程要跑 100ms，其他线程会**自旋 100ms 全占 CPU**
>
> **对比场景**：
>
> | 场景 | CAS 表现 | synchronized 表现 |
> |------|----------|-------------------|
> | **竞争极少、临界区极短**（如计数器） | ✅ 几乎不重试，开销极低 | ❌ 加锁/释放/上下文切换反而是浪费 |
> | **竞争激烈、临界区长**（如 DB 调用） | ❌ 自旋烧 CPU | ✅ 阻塞挂起，CPU 释放给其他线程 |
> | **高并发长任务** | ❌ 完全不适合 | ✅ 用阻塞避免 CPU 空转 |
>
> **结论**：CAS 适合**低竞争、短临界区**；高竞争/长临界区必须用阻塞锁（synchronized / ReentrantLock）。**所有场景都用 CAS 会导致 CPU 100% 但业务没进展**。

---

### 21. volatile 的两个作用？4 种内存屏障（LoadLoad / StoreStore / LoadStore / StoreLoad）如何实现？

> **问题描述**
> 进阶题。volatile 的可见性是基础，禁止重排序常被考"具体怎么实现"——4 种屏障和插入策略必须背熟。
>
> 进阶：x86 平台下 volatile 写为什么用 `lock` 前缀指令。

> [!abstract]- 参考答案
> **volatile 两个作用**：
> 1. **可见性**：写 volatile 变量 → 立即刷主内存；读 volatile 变量 → 强制从主内存读
> 2. **禁止指令重排序**：通过内存屏障实现
>
> **JMM 4 种内存屏障**：
>
> | 屏障 | 作用 |
> |------|------|
> | **LoadLoad** | 防止 Load1 和 Load2 重排序（都读） |
> | **StoreStore** | 防止 Store1 和 Store2 重排序（都写） |
> | **LoadStore** | 防止 Load1 和 Store2 重排序（读在前写在后） |
> | **StoreLoad** | 防止 Store 和后面的 Load/Store 重排序（开销最大） |
>
> **JMM 对 volatile 的标准插入策略**：
> - volatile **写之前**：插 `StoreStore` 屏障（防普通写被重排到 volatile 写之后）
> - volatile **写之后**：插 `StoreLoad` 屏障（防 volatile 写和后面的 volatile 读写重排）——**开销最大**
> - volatile **读之后**：插 `LoadLoad` 屏障（防 volatile 读和后面的普通读重排）
> - volatile **读之后**：插 `LoadStore` 屏障（防 volatile 读和后面的普通写重排）
>
> **x86 平台实现**：volatile 写通过 `lock` 前缀指令（如 `lock addl $0,0(%rsp)`）实现，等价于一个**全屏障**，因为 x86 自身内存模型已隐含了 LoadLoad/StoreStore/LoadStore 的保证。
>
> **屏障怎么"卡死 CPU"——DCL 单例案例**：详细分析见 [[volatile 关键字详解]]。
> 一句话：DCL 单例中 `instance = new Singleton()` 会被拆成"分配内存 → 初始化 → 赋值引用"三步，CPU 可能重排为 1→3→2，导致其他线程拿到"半成品对象"；volatile 在赋值引用（volatile 写）前后插入 StoreStore + StoreLoad 屏障，强制"初始化必须在赋值引用之前完成"。

---

### 22. volatile 能保证线程安全吗？为什么 `i++` 加了 volatile 也不安全？应该用 AtomicInteger 还是 synchronized？

> **问题描述**
> 经典坑题。很多人以为 volatile 等于"安全"——错。volatile 只保证可见性，不保证原子性。
>
> 进阶：`AtomicInteger.incrementAndGet()` 用 CAS 自旋实现原子性，比 synchronized 轻量。

> [!abstract]- 参考答案
> **volatile 不能完全保证线程安全**——它只保证**可见性**和**有序性**，不保证**原子性**。
>
> **为什么 `i++` 加了 volatile 也不安全**：
> - `i++` 是复合操作，**三步**：
>   1. 读 i（volatile 保证看到最新值）
>   2. i + 1（线程本地计算）
>   3. 写 i（volatile 写回主内存）
> - 假设 i=0，线程 A 读到 0，线程 B 也读到 0 → 各自 +1 → 都写回 1
> - **结果是 1 而不是 2**——可见性救不了你，"读-改-写"之间没有原子性
>
> **应该用什么**：
>
> | 方案 | 适用场景 | 开销 |
> |------|----------|------|
> | **`AtomicInteger`** | 单计数器、序列号、状态标志 | 低（CAS 自旋） |
> | **`LongAdder`** | **超高并发**计数（百万级），牺牲一点实时精度 | 更低（分散热点到 Cell） |
> | **`synchronized`** | 复合操作（多变量原子改） | 高（阻塞） |
>
> **面试话术**：volatile 不是"万能保险"，它只管"看得见"，不管"不重叠"。`i++` 这种"读-改-写"用 AtomicInteger.incrementAndGet() 才对。

---

### 23. volatile vs synchronized 的本质区别？（原子性/可见性/有序性三维度）

> **问题描述**
> 必考对比题。三个维度展开。
>
> 进阶：volatile 是"轻量级同步"，但能力比 synchronized 弱。

> [!abstract]- 参考答案
>
> | 维度 | volatile | synchronized |
> |------|----------|--------------|
> | **原子性** | ❌ 不保证（`i++` 仍不安全） | ✅ 保证（monitorenter/exit 包住整个代码块） |
> | **可见性** | ✅ 写立即刷主内存，读强制从主内存读 | ✅ 进入同步块清空工作内存，退出刷回主内存 |
> | **有序性** | ✅ 通过 4 种内存屏障禁止重排序 | ✅ 同一时刻只有一个线程执行同步块，本质有序 |
> | **作用范围** | 变量 | 变量/方法/代码块 |
> | **实现** | 内存屏障（JVM 指令级） | 字节码 monitorenter/exit + 对象头 Mark Word + Monitor |
> | **阻塞** | ❌ 不会阻塞线程 | ✅ 会阻塞（重量级阶段） |
>
> **本质区别**：
> - volatile 是**轻量级**同步机制——只解决"看见"和"顺序"，不解决"重叠"
> - synchronized 是**重量级**同步机制——同时解决"看见 + 顺序 + 不重叠"
>
> **记忆口诀**：
> - volatile 管**单变量**的"看得见"+"不乱序"
> - synchronized 管**多操作**的"原子性"

---

### 24. ThreadLocal 实现原理？key/value 引用类型？内存泄漏原因？为什么 key 设计成弱引用？

> **问题描述**
> 经典题，但常被简答成"每个线程一份"——面试官会追问"为什么 key 是弱引用？""value 为什么不会被回收？"
>
> 进阶：线程池场景下 ThreadLocal 内存泄漏被放大的原因。

> [!abstract]- 参考答案
> **ThreadLocal 原理**：
> - 每个 `Thread` 对象内部有一个 `ThreadLocalMap` 字段
> - `ThreadLocalMap` 内部是 `Entry[]` 数组，`Entry` 继承 `WeakReference<ThreadLocal<?>>`
> - **key = ThreadLocal 对象（弱引用）**，**value = 业务对象（强引用）**
>
> **内存泄漏过程**：
> 1. `ThreadLocal tl = new ThreadLocal<>(); tl.set("hello");` —— `ThreadLocalMap` 里的 `Entry`（节点）的 Key，【弱引用】了 `tl` 所指向的那个 `ThreadLocal` 实例对象，`ThreadLocalMap` 里的 `Entry`（节点）的 Value，【强引用】了 `hello` 。
> 2. 局部变量 `tl` 生命周期结束 → 强引用消失，只剩 ThreadLocalMap 里 key 的弱引用
> 3. GC 触发 → 弱引用的 ThreadLocal 被回收 → Entry.key 变成 null
> 4. **但 value 仍是强引用**（被 `ThreadLocalMap → Thread → Thread 实例链`持有）
> 5. **只要线程还活着**（线程池中的核心线程），value 永远无法被 GC 回收 → 内存泄漏
>
> **为什么 key 设计成弱引用**：
> - 如果 key 是**强引用**：ThreadLocalMap 一直强引用 ThreadLocal 对象 → ThreadLocal 和 value 都泄漏
> - 弱引用设计**至少能让 ThreadLocal 对象本身被回收**，虽然解决不了 value，但至少减少了一半的泄漏
> - 弱引用是"两害相权取其轻"——不解决 value，但**逼着开发者用 `remove()` 主动清理**
>
> **怎么避免内存泄漏**：
> - **必须**用 `try-finally` 包 `tl.remove()`（线程池场景必须，方法局部场景也应该）
> - 不能依赖 `ThreadLocalMap` 的"启发式清理"（仅在 get/set/remove 时才检查）


### 1. 核心结论：是谁对谁的引用？

直接回答你的问题：

1. **`Key` 的弱引用**：是 `ThreadLocalMap.Entry`（也就是那个 Map 里的节点）对 `ThreadLocal 实例对象` 的弱引用。
    
2. **`Value` 的强引用**：是 `ThreadLocalMap.Entry` 对 `"hello" 字符串对象` 的强引用。
    

### 2. 硬核内存图解（彻底看清谁指着谁）

当你写下这几行代码时：

Java

```
ThreadLocal tl = new ThreadLocal<>(); // 1
tl.set("hello");                      // 2
```

在 JVM 的堆内存和栈内存里，真正的引用网络长这样：

我们顺着图里的四条线来看：

- **实线①（强引用）**：你的局部变量 `tl` 强引用着堆内存里的 `ThreadLocal` 实例对象。
    
- **实线②（强引用）**：当前线程对象（`Thread`）内部有一个 `threadLocals` 字段（它的类型是 `ThreadLocalMap`），强引用着这个 Map 数组。
    
- **虚线③（弱引用 ❌ 关键点）**：Map 里的每一个节点叫 `Entry`。这个 `Entry` 继承了 `WeakReference<ThreadLocal<?>>`。它的 **`Key`** 指向 `ThreadLocal` 实例对象，但这条线是**弱引用**。
    
- **实线④（强引用）**：`Entry` 里的 **`Value`** 直接指向了内存里的 `"hello"` 字符串对象，这条线是**强引用**。
    

### 3. 为什么面试总问“弱引用”会导致内存泄漏？

看懂了上面的引用链，你就能秒懂内存泄漏的本质了。

#### 场景模拟：

如果你的业务执行完了，你把栈里的局部变量解绑了：`tl = null;`。 此时，**实线①** 断了。

1. **对于 `ThreadLocal` 对象**： 现在没有任何强引用指向它了，只剩下一根垃圾回收器根本不放在眼里的**虚线③（弱引用）**。所以，下一次垃圾回收（GC）发生时，这个 `ThreadLocal` 对象会**立刻被回收掉**！ 此时，Map 里这个 `Entry` 的 **`Key` 就变成了 `null`**。
    
2. **对于 `"hello"` 对象（Value）**： 由于 `Key` 变成了 `null`，你再也无法通过任何办法访问到这个 `Entry` 的 `Value` 了。 但是！只要你的线程还活着（比如线程池里的核心线程永远不死），**实线②** 就一直存在，**实线④** 也就一直存在。 这导致 `"hello"` 这个死掉的、无法被访问的数据，**永远霸占着内存无法被回收**。
    

这就是大名鼎鼎的 **ThreadLocal 内存泄漏**。

---

### 25. 死锁的四个必要条件？破坏任一即可避免？资源有序分配法为什么能解决大部分死锁？

> **问题描述**
> 经典题。**四个条件必须同时满足才会死锁**——破坏任一即可。重点是"资源有序分配法"为什么能破坏"环路等待"。
>
> 进阶：工程实践中 90% 的死锁都通过"破坏环路等待"解决（按固定顺序加锁）。

> [!abstract]- 参考答案
> **死锁四必要条件**（必须同时满足）：
>
> | 条件 | 含义 | 反例（破坏它） |
> |------|------|----------------|
> | **互斥** | 资源一次只能被一个线程持有 | 用 `ConcurrentHashMap` 等无锁数据结构 |
> | **持有并等待** | 线程持有资源 A 的同时等待资源 B | 一次性申请所有资源（破坏持有+等待） |
> | **不可剥夺** | 线程不主动释放，资源无法被强占 | `tryLock(timeout)` 超时主动放弃 |
> | **环路等待** | A 等 B，B 等 A | **资源有序分配**（所有线程按相同顺序加锁） |
>
> **资源有序分配法**：
> - 核心思想：**所有线程获取资源的顺序保持一致**
> - 例子：线程 A、B、C 都按"先 R1 再 R2 再 R3"的顺序申请
> - 不会形成环路：每个线程都在等"自己还没拿到的"前一个资源，不存在"我等你、你等他"的情况
>
> **为什么能解决大部分死锁**：
> - 工程实践中 90% 死锁都是"环路等待"导致的（多个锁/资源按不同顺序加锁）
> - 其他三条件（互斥、不可剥夺）通常**不能破坏**——业务确实需要互斥、有些资源确实不能强占
> - 资源有序分配**实现简单**：只要约定"全局锁顺序"即可，零成本
>
> **代码示例（破解环路等待）**：
> ```java
> // 反例：两个线程按相反顺序加锁 → 死锁
> // 线程 A: lock1 → lock2
> // 线程 B: lock2 → lock1
>
> // 正例：统一顺序
> // 线程 A: lock1 → lock2
> // 线程 B: lock1 → lock2  // 强制先 lock1
> ```
>
> **面试话术**：死锁四条件破一即可，**工程上最常用的是资源有序分配**——按固定顺序加锁，简单有效。

---

> 关联：[[Java并发编程面试题]] · [[线程池]] · [[synchronized 关键字详解]] · [[可重入性详解]] · [[volatile 关键字详解]] · [[ReentrantLock详解]] · [[AQS 详解]] · [[CountDownLatch详解]] · [[CyclicBarrier详解]] · [[阻塞队列详解]] · [[Condition 详解]] · [[Semaphore详解]]
