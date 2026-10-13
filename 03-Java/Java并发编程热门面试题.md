---
tags:
  - java
  - 并发
  - 面试题
created: 2026-08-20
updated: 2026-08-20
背诵状态: 已完成
背诵开始: 2026-08-25 14:10
背诵结束: 2026-08-25 14:10
---

# Java 并发编程热门面试题

> **考查范围**：JMM 与可见性 · synchronized · volatile 与 CAS · AQS 与 JUC 锁 · 并发工具类 · 阻塞队列与线程池 · ThreadLocal · 并发容器
> **题型**：全部为简答题，面试导向
> **建议用时**：90 ~ 120 分钟
> **说明**：先自己作答，答完发给 Claude 批卷评估。每题后的「追问」是面试官可能深挖的点，作答时一并思考。批卷按 CLAUDE.md 面试答题原则 9 条执行，落盘「批改分析区 + 参考答案」两区。

---

## 复习速查表（按主题 → 笔记跳转）

- 并发总纲 → [[Java并发编程面试题]]
- volatile / 可见性 → [[volatile 关键字详解]]
- JMM / 有序性 → [[内存屏障和happens-before]]
- synchronized → [[synchronized 关键字详解]]
- AQS → [[AQS 详解]]
- ReentrantLock → [[ReentrantLock详解]]
- 读写锁 → [[ReentrantReadWriteLock 完整详解]]
- Condition → [[Condition 详解]]
- CountDownLatch → [[CountDownLatch详解]]
- CyclicBarrier → [[CyclicBarrier详解]]
- Semaphore → [[Semaphore详解]]
- 线程池 → [[线程池]]
- 阻塞队列 → [[阻塞队列详解]]
- ThreadLocal → [[ThreadLocal 深度解析与内存泄露笔记]]
- ConcurrentHashMap → [[ConcurrentHashMap_1.7_vs_1.8]]
- CompletableFuture → [[CompletableFuture]]
- DCL 单例 → [[DCL（Double-Checked Locking）单例完整展开]]

---

## 一、JMM 与可见性（4 题）

### 1. JMM（Java 内存模型）是什么？并发编程的三大特性是哪三个？【高频】

JMM是专门解决多线程并发问题的一套规则

原子性、可见性、有序性

> **知识点跳转：** [[Java并发编程面试题#Java 的内存模型（JMM）介绍一下]] · [[内存屏障和happens-before]]

**追问：** 原子性、可见性、有序性分别指什么？各自靠什么机制保证？

原子性指的是同一时间只能有一个线程操作数据
可见性是指一个线程对数据的修改应该及时的写入主内存被其他线程看到
有序性是指多线程环境下，需要确保一个线程观察其他线程的指令执行顺序保持有序

---

**批改分析区：**

**评分：6.5/10**

- ✅ JMM 定位对（一套并发规则）、三大特性列全
- ⚠️ 有序性定义不精准：有序性 = **禁止指令重排序**（编译器/CPU 优化打乱执行顺序），不是"观察其他线程的执行顺序"
- ⚠️ 追问只定义了三个特性，**没答"各自靠什么机制保证"**

**参考答案 ✅**

JMM 就是 Java 内存模型，它是 Java 官方定的一套规范，用来约束多线程共享变量的读写，保证并发安全。它规定了主内存和工作内存两层：所有共享变量都住在主内存里，每个线程操作的是自己工作内存里的副本，不能直接改主内存。

并发编程的三大特性是原子性、可见性、有序性。原子性就是同一时刻只能有一个线程去改这个数据，靠锁或者 CAS 来保证；可见性就是线程改完的数据要及时刷回主内存，让其他线程能看到，靠 volatile、synchronized、Lock 来保证；有序性就是禁止指令重排序，代码写在前面的不能被编译器或 CPU 乱序执行，靠 happens-before 规则和内存屏障来保证。

> **追问参考答：** 就是上面三大特性各自的含义和保证机制。特别纠正一下：有序性的核心是"禁止指令重排序"，不是"观察其他线程的执行顺序"——编译器、CPU 都可能打乱指令顺序。

### 2. 什么是缓存一致性？为什么会出现可见性问题？【中频】

指的是缓存数据与真实数据的一致性问题

因为线程有自己线程内存空间，修改数据时会先修改自己的那份，在多线程环境下，两份数据就存在不一致的时间窗口

> **知识点跳转：** [[Java并发编程面试题#Java 的内存模型（JMM）介绍一下]]

**追问：** 线程对共享变量的修改为什么其他线程可能看不到？（CPU 缓存 + 主内存模型）

因为有cpu缓存

---

**批改分析区：**

**评分：6/10**

- ✅ 缓存一致性 = 缓存与真实数据不一致，方向对
- ✅ 工作内存模型（线程先改自己的副本）对
- ⚠️ 追问太简略："因为有 cpu 缓存"——要说清机制：线程操作的是**工作内存（CPU 缓存）副本，修改后留在缓存不立即写回主内存**，其他线程读主内存看不到

**参考答案 ✅**

缓存一致性，指的是主内存的数据和各个线程工作内存里的副本数据不一致的问题。为什么会不一致？因为 JMM 规定线程不能直接操作主内存，必须先把共享变量读到自己的工作内存里，改了之后先写回工作内存，什么时候刷回主内存不确定。这就导致一个窗口期：线程 A 改了，但还没刷回主内存，线程 B 读主内存拿到的还是旧值，就出现了可见性问题。

> **追问参考答：** 线程读共享变量的时候，先从主内存拷到自己的 CPU 缓存里，改完之后先留在缓存里，延迟写回主内存。其他线程读主内存的时候，拿到的还是旧值，所以看不到修改。volatile 的作用就是强制读写直通主内存，配合缓存一致性协议和内存屏障来解决这个问题。

> [!note] 概念澄清：工作内存 vs CPU 缓存
> **工作内存是 JMM 的抽象概念**（规范层），**CPU 缓存是物理硬件**（实现层）。工作内存在实现上大致对应 **寄存器 + 各级 CPU 缓存 + 写缓冲（store buffer）**。面试说"工作内存 ≈ CPU 缓存"是简化类比、可用；但要心里清楚：前者是规范层抽象，后者是物理实现，两者是抽象与实现的关系。

### 3. happens-before 规则有哪些？它解决什么问题？【低频·加分】

> **知识点跳转：** [[内存屏障和happens-before]]

**追问：** 程序顺序规则、volatile 规则、锁规则分别是什么？

---

**批改分析区：**

**评分：0/10**

- ❌ 空着——低频加分题，但至少把"**是什么 + 解决什么问题**"答出来

**参考答案 ✅**

happens-before 是 JMM 里两个操作之间的一种偏序关系，它解决的是"没有锁的情况下，多线程怎么保证有序性和可见性"的问题。它的含义就是：如果 A happens-before B，那 A 的执行结果对 B 是可见的，而且 A 不能被重排到 B 的后面。要注意，这是逻辑保证，不是实时时间的先后，本质是禁止编译器、CPU 重排序。

三条核心规则：第一条是程序顺序规则，同一个线程内，写在前面的代码 happens-before 后面的；第二条是 volatile 规则，对一个 volatile 变量的写，happens-before 后面所有对它的读；第三条是锁规则，解锁 happens-before 后面再进来的加锁。另外还有一条传递性：A happens-before B、B happens-before C，那 A 就 happens-before C。

### 4. 内存屏障是什么？有哪几类？【低频·加分】

> **知识点跳转：** [[内存屏障和happens-before]] · [[volatile 关键字详解#番外：为什么屏障叫 StoreStore / StoreLoad —— 命名规律]]

**追问：** volatile 的写/读分别插入什么屏障？

---

**批改分析区：**

**评分：0/10**

- ❌ 空着——低频加分题，**知道"有内存屏障这回事、volatile 用它防止指令重排"**即可

**参考答案 ✅**

内存屏障是 CPU 或者编译器提供的一种约束指令，作用就是禁止屏障前后的指令绕过它乱序执行，本质是拦指令重排序。一共有四类：LoadLoad、StoreStore、LoadStore、StoreLoad。命名规则就是"什么不能被重排到什么后面"——比如 LoadLoad，就是屏障前的读指令不能重排到屏障后的读。

volatile 的语义就靠它：volatile 写的时候，前面插 StoreStore、后面插 StoreLoad；volatile 读的时候，后面插 LoadLoad 和 LoadStore。说白了就是——写之前先把自己的写都写完，读之后保证读到的都是最新的。面试答到"volatile 靠内存屏障防止指令重排"就够了。

> **追问参考答：** 见上。volatile 写 = 前插 StoreStore + 后插 StoreLoad；volatile 读 = 后插 LoadLoad + LoadStore。效果是：写之前写完就写、读之后不能读到旧值。

---

## 二、synchronized 与锁机制（4 题）

### 5. synchronized 有哪几种用法？锁的对象分别是谁？【高频】

同步方法、同步代码块

锁的是方法对应实例

> **知识点跳转：** [[synchronized 关键字详解#一、synchronized 是什么]]

**追问：** 修饰实例方法、静态方法、代码块分别锁的是什么？

对象实例、Class对象

---

**批改分析区：**

**评分：6/10**

- ✅ 同步方法、同步代码块两种用法答对
- ❌ **漏了第三种用法：静态方法**（synchronized 也可以修饰静态方法）
- ⚠️ 追问"对象实例、Class对象"只答了实例方法和静态方法，**没答代码块锁的是括号里指定的任意对象**
- ⚠️ 实例方法锁的是当前实例（this），"锁的是方法对应实例"表述不够精确

**参考答案 ✅**

synchronized 有三种用法。第一种是修饰实例方法，锁的是当前实例对象，也就是 this；第二种是修饰静态方法，锁的是当前类的 Class 对象，因为静态资源属于类级别，所有实例共享；第三种是修饰代码块，锁的是括号里指定的那个对象，可以是任意对象。

> **追问参考答：** 就是上面三种：实例方法锁 this、静态方法锁 Class 对象、代码块锁指定对象。关键记住代码块可以锁任意对象，不限于实例或 Class，这也是缩小锁粒度、控制并发范围的手段。

### 6. synchronized 的底层原理是什么？【高频】



> **知识点跳转：** [[synchronized 关键字详解#三、底层实现三层面]]

**追问：** monitorenter / monitorexit 指令？Monitor（监视器锁）是什么？

---

**批改分析区：**

**评分：0/10**

- ❌ **空着**——这是 synchronized 最核心的高频题，**必须会背**

**参考答案 ✅**

synchronized 的底层是 JVM 层面的 Monitor，也就是监视器锁。编译成字节码之后，同步代码块前后会生成 monitorenter 和 monitorexit 两条指令。每个对象都关联一个 Monitor，由 JVM 的 ObjectMonitor 对象实现，它记录着哪个线程持有锁、重入了几次、还有哪些线程在等待。

加锁的流程是这样：monitorenter 的时候，如果 Monitor 的计数器是 0，当前线程就拿到锁，计数变成 1；如果锁已经被自己持有了，计数再 +1，这就是可重入；如果被别的线程占着，就进阻塞队列等待。monitorexit 释放的时候计数 -1，减到 0 才完全释放，然后唤醒等待的线程。

> **追问参考答：** monitorenter 就是尝试获取 Monitor，monitorexit 就是释放。Monitor 是 JVM 层面的同步原语，底层基于操作系统的 mutex 实现，每个 Java 对象头里都隐含着一个 Monitor 指针。

### 7. synchronized 的锁升级过程是怎样的？【高频】

jdk1.5前一直是重量级锁，jdk1.6+有了锁升级
无锁->偏向锁->轻量级锁->重量级锁，锁越来越重，开销越来越大

> **知识点跳转：** [[synchronized 关键字详解#四、锁升级过程]]

**追问：** 偏向锁撤销的代价是什么？为什么 JDK 15 默认禁用了偏向锁？

对象刚创建出来，是处于无锁
当第一个线程进入同步代码块中时，无锁会升级为偏向锁，此时对象的对象头Mark Work为该线程ID，重入时直接CAS判断ID是否是自己的即可，不需加锁，很高效
当存在其他线程尝试获取锁时，偏向锁会升级为轻量级锁，此时会在线程的栈帧上创建锁记录空间，并尝试CAS更新对象头的Mark Word执行该锁记录，如果成功则获取锁成功，否则会自旋CAS重试，当重试次数达到一定阈值时，轻量级锁会升级为重量级锁

---

**批改分析区：**

**评分：7/10**

- ✅ 主问答对：1.6 引入锁升级、四态顺序正确
- ✅ 触发条件完整（第一个线程→偏向锁存 ID、其他线程竞争→轻量级锁 CAS、自旋失败→重量级锁）——这是原问「升级过程」的一部分，已并入主问计分（追问已改版，原追问不再重复主问）
- ❌ "Mark Work" → 正确拼写是 **Mark Word**（拼错专有名词扣分）
- ⚠️ "重入时直接CAS判断ID" 不准确：**偏向锁重入是直接判断线程 ID 是否是自己，不需要 CAS**（CAS 是轻量级锁阶段才用的）
- ⚠️ "执行该锁记录" → 应为"**指向**该锁记录"
- ⚠️ 新追问（偏向锁撤销代价 + JDK 15 禁用）未作答——这两个点是加分项，参考答案里已补全

**参考答案 ✅**

JDK 1.6 之前 synchronized 只有重量级锁，一上来就直接挂起线程、走系统调用，性能很差。1.6 之后引入了锁升级，链路是：无锁 → 偏向锁 → 轻量级锁 → 重量级锁，越往后越重、开销越来越大。

各阶段的触发条件：无锁就是对象刚 new 出来，没人访问；第一个线程进入同步块的时候，升级成偏向锁，对象头 Mark Word 里记录这个线程的 ID，这个线程重入的时候直接判断 ID 是不是自己就行，不需要 CAS、不需要加锁，所以很高效；当有第二个线程来竞争的时候，偏向锁要撤销，升级成轻量级锁，这个线程会在自己栈帧里创建一个锁记录 Lock Record，然后 CAS 把对象头的 Mark Word 指向这个锁记录，成功了就拿到锁，失败就自旋重试；如果自旋到一定次数还是拿不到，说明竞争很激烈，就升级成重量级锁，把线程挂起，走操作系统 mutex。

> **追问参考答：** 偏向锁撤销的代价是**必须 STW 到安全点**——JVM 暂停所有线程，检查对象头的偏向记录，才能把偏向撤销。这个撤销成本其实不低。JDK 15 默认禁用偏向锁（JEP 374）的原因：偏向锁省的是"无竞争时重入免 CAS"那点开销，但现代应用锁竞争往往没那么少，偏向锁带来的收益越来越小，反而要承担撤销时 STW 的开销，得不偿失。

### 8. synchronized 和 ReentrantLock 的区别？【高频】

synchronized是jvm层面基于监视器实现的内置锁
ReentrantLock是基于AQS框架实现的可重复锁
相比synchronized，ReentrantLock多了很多锁功能，比如可中断的锁尝试、可超时的锁获取、公平锁和非公平锁、可以创建多个条件变量、锁住的范围更加灵活

> **知识点跳转：** [[synchronized 关键字详解#七、与 ReentrantLock 的核心区别]] · [[ReentrantLock详解#五、与 synchronized 全面对比]]

**追问：** 公平性、可中断、条件变量、底层实现上有什么区别？

---

**批改分析区：**

**评分：7/10**

- ✅ synchronized 是 JVM 层 Monitor 内置锁，ReentrantLock 基于 AQS，底层定位答对
- ✅ ReentrantLock 的额外功能列举很全（可中断、可超时、公平/非公平、多条件变量、锁范围灵活）
- ❌ "可重复锁" → 专业术语是**可重入锁**
- ⚠️ 没提关键前提：**synchronized 也是可重入的**——可重入不是 ReentrantLock 独有
- ⚠️ 追问空着（公平性、可中断、条件变量、底层实现的具体区别没展开）

**参考答案 ✅**

我从底层和功能两个维度来说。底层上，synchronized 是 JVM 层面的内置锁，靠 Monitor 监视器实现，编译成字节码就是 monitorenter/monitorexit；ReentrantLock 是 Java 层面基于 AQS 框架实现的，用 CAS 加上一个双向队列来管理等待线程。

功能上，两者都是可重入的，但 ReentrantLock 多了几个能力：第一可中断，lockInterruptibly() 能响应中断；第二可超时，tryLock 可以限时等锁；第三公平锁，构造时传 fair=true 就能排队；第四多个条件变量，可以 new 多个 Condition 队列，比 synchronized 单一的 wait/notify 队列更灵活；第五锁的范围更灵活，可以跨方法 lock 再 unlock。

代价是：synchronized 由 JVM 自动释放锁，就算抛异常也会释放；ReentrantLock 必须手动 unlock()，而且一定要放在 finally 里，不然容易死锁。所以我的原则是：能用 synchronized 就用 synchronized，只有确实需要可中断、可超时、多条件这些高级特性的时候才换 ReentrantLock。

> **追问参考答：** 见上。公平性——synchronized 只有非公平，ReentrantLock 默认非公平、构造可传 fair=true；可中断——synchronized 不响应中断，ReentrantLock 的 lockInterruptibly() 可中断；条件变量——synchronized 只有 wait/notify 单队列，ReentrantLock 可建多个 Condition；底层——Monitor vs AQS 队列。

---

## 三、volatile 与 CAS（3 题）

### 9. volatile 能保证什么？不能保证什么？为什么不能保证原子性？【高频】

保证可见性和有序性，无法保证原子性
因为它只能加在一个变量上啊（乐，这也要问

> **知识点跳转：** [[volatile 关键字详解#一句话总结]]

**追问：** `volatile int count` 的 `count++` 为什么不是原子的？

乐，因为获取count值，然后++，再赋值回去这是三个动作

---

**批改分析区：**

**评分：6.5/10**

- ✅ 主问核心答对：保证可见性 + 有序性，不保证原子性
- ❌ **"为什么不能保证原子性"推理错**：不是因为"它只能加在一个变量上"——volatile 不提供互斥锁，复合操作（读-改-写）不受保护
- ⚠️ **态度问题**："乐，这也要问"——面试大忌，跟 MySQL 卷"傻逼吧"同类。面试官问基础题可能是在测你的深度，答对机制即可，别流露不屑
- ✅ 追问机制答对：count++ 是「读值→+1→写回」三个动作，不是原子的

**参考答案 ✅**

volatile 能保证可见性和有序性，不能保证原子性。可见性就是：线程改了 volatile 变量，会立即写回主内存，其他线程立刻能看到。有序性就是：volatile 用内存屏障禁止指令重排序。

为什么不能保证原子性？因为 volatile 只提供了读写屏障，它不提供互斥。像 count++ 这种操作，本质是读、加、写三步，volatile 只保证每一步的可见性，不保证这三步是连续的。两个线程可能同时读到旧值，各自加一再写回，就丢了更新。

> **追问参考答：** count++ 编译后是三条指令：读 count → 加一 → 写回 count。volatile 只让这三步各自可见，但步骤之间可以被其他线程插进来。两个线程同时读到 10，都加一写回 11，本该是 12。要让 count++ 原子，得用 AtomicInteger（CAS）或者加锁。

### 10. volatile 的底层实现是什么？【中频】



> **知识点跳转：** [[volatile 关键字详解#四、救星：volatile 怎么卡死 CPU]] · [[内存屏障和happens-before]]

**追问：** volatile 写屏障和读屏障分别防止什么重排序？（读写屏障语义）

---

**批改分析区：**

**评分：0/10**

- ❌ **空着**——中频题，至少知道"volatile 底层用**内存屏障**防止指令重排"

**参考答案 ✅**

volatile 的底层实现是内存屏障。volatile 写的时候，会在前面插一个 StoreStore 屏障、后面插一个 StoreLoad 屏障；volatile 读的时候，会在后面插 LoadLoad 和 LoadStore 屏障。作用是禁止屏障前后的指令越过它重排。

简单概括：volatile 写，保证写之前该写的普通写都完成了，写之后的所有操作不能重排到这个写之前；volatile 读，保证读之后的所有操作不能重排到这个读之前，读到的都是最新值。

> **追问参考答：** volatile 写 = 前插 StoreStore + 后插 StoreLoad；volatile 读 = 后插 LoadLoad + LoadStore。一句话：写前写完该写的、读后读到的都是最新的。

### 11. CAS 是什么？它有什么问题（ABA）？怎么解决？【高频】

compare and swap，对比并交换
存在ABA问题，假如开始时，数据是A，中间被其他线程改成了B然后又改为了A，这个过程对于当前线程是不可见的，它会仍认为此A就是初始的A
加版本号或时间戳，每次更新数据都更新版本号，CAS同时对比版本号和数据是否跟原来的一致

> **知识点跳转：** [[Java并发编程面试题#CAS 有什么问题，Java是怎么解决的？]] · [[ABA 问题详解#核心定义]]

**追问：** ABA 问题的典型场景？AtomicStampedReference 怎么解决？

---

**批改分析区：**

**评分：7/10**

- ✅ CAS 全称答对（Compare And Swap，对比并交换）
- ✅ **ABA 问题描述清晰**——A→B→A 过程对当前线程不可见，误以为还是最初的 A
- ✅ 解决思路答对（版本号/时间戳，CAS 同时对比版本和数据）
- ⚠️ 追问空着：**典型场景**（栈/链表的 CAS 操作）+ **AtomicStampedReference 具体机制**没答
- ⚠️ 可以说出 "compare and swap 的语义"：**先比较预期值，相等才更新，否则重试/失败**——你只说了名字没展开语义

**参考答案 ✅**

CAS 就是 Compare And Swap，对比并交换，三个参数：内存值 V、预期值 A、新值 B。只有当 V 等于 A 的时候，才把 V 更新成 B，否则就不更新。底层是 CPU 的原子指令，比如 x86 的 lock cmpxchg，无锁实现并发安全。

ABA 问题就是：线程 1 读到一个值 A，准备做 CAS 的时候，线程 2 把 A 改成 B 又改回 A，线程 1 再 CAS 发现 V 还是 A，就成功了，但实际上中间被改过了。典型的场景是 CAS 操作无锁栈：线程 1 要 pop 栈顶节点 A，此时线程 2 push 两个节点又 pop 回 A，栈顶值没变但栈的结构已经变了，线程 1 的 CAS 会错误地成功。

解决方式就是加版本号，用 AtomicStampedReference。它里面维护了一个引用加上一个 int stamp，每次更新的时候 stamp 加一，CAS 同时比较引用和 stamp 两个值，stamp 变了说明被改过，就失败。跟乐观锁的 version 字段一个道理。

> **追问参考答：** 典型场景是无锁栈/链表的 CAS 操作——结构变了但栈顶引用没变，CAS 误判成功。AtomicStampedReference 用 int stamp 版本号，CAS 时同时比较引用和版本号，stamp 变了就失败，跟乐观锁的 version 字段一样。

[[ABA 问题详解]]

---

## 四、AQS 与 JUC 锁（4 题）

### 12. AQS 的核心原理是什么？【中频】

AQS是一个抽象的同步器框架，是java并发工具的基石

主要核心有三个，state状态字段、变体CLH队列、tryAcquire等钩子方法

state对于可重入锁来说，表示重入次数，0表示无人持有，1表示已被线程持有，重入+1
对于独占模式来说，state只有0、1的语义，表示锁持有和空闲
对于共享模式来说，state字段表示可进入线程数或可获取资源的数量

CLH队列用来存放等待锁的线程，AQS用的是变体的，它不再让head自旋，而是让等待的线程park阻塞，减少CPU的损耗

而tryAcquire、tryRelease等钩子方法可以使锁的类型以及功能更加丰富

> **知识点跳转：** [[AQS 详解#一、AQS 核心三要素]]

**追问：** state 字段 + CLH 双向队列，独占和共享模式的区别？

独占模式下，释放锁的线程只会唤醒它的后继节点，而共享模式会链式唤醒它的后面所有连续的共享节点

---

**批改分析区：**

**评分：8/10**

- ✅ AQS 定位答对（抽象同步器框架、并发工具基石）
- ✅ 三要素答全：state 字段 + 变体 CLH 队列 + tryAcquire 钩子方法
- ❌ **"独占模式下 state 只有 0/1" 自相矛盾**——你前面刚说"可重入锁 state=重入次数"，可重入独占锁重入两次 state=2。正确说法：**state 具体语义由子类定义**（可重入锁=重入次数、信号量=许可证数）
- ⚠️ "不让 head 自旋"表述不精确：AQS 变体是**排队节点 park 阻塞、由前驱唤醒**，不是"head 不自旋"
- ✅ 追问答得漂亮：独占只唤醒后继、共享链式唤醒连续共享节点

**参考答案 ✅**

**① AQS 是什么：** AQS 是 JUC 的基石，一个抽象的同步队列框架。ReentrantLock、Semaphore、CountDownLatch 全是基于它做的。核心三件套：**state 状态字段、CLH 双向等待队列、tryAcquire 这类钩子方法**。

**② state 的语义（关键）：** state 是 volatile 的，具体含义**由子类定义**。可重入独占锁（ReentrantLock）里 state=重入次数，0 无人持、1 持一层、重入 +1；共享信号量（Semaphore）里 state=剩余许可证数。所以不能说"独占只能 0/1"——**可重入就 >1**。

**③ CLH 变体队列：** 存放抢锁失败的线程。原始 CLH 是**自旋锁**——每个线程自旋读**前驱节点**的状态、等它释放（占 CPU）；AQS 变体改成**阻塞式**：后继在 `park` 挂起**之前**，先把自己的**前驱节点 `waitStatus` 置为 `SIGNAL`**（语义是"你释放时记得 `unpark` 我"），前驱 `release` 时再据此 `unparkSuccessor` 唤醒后继。关键是**唤醒是"后继预约、前驱执行"，不是前驱凭空知道要叫谁**。省 CPU，代价是唤醒时一次上下文切换（而且**被唤醒 ≠ 拿到锁**，醒来还要重新 `tryAcquire` 抢）。

**④ 钩子方法：** tryAcquire/tryRelease（独占）、tryAcquireShared/tryReleaseShared（共享）由子类实现，决定锁的行为，这就是 AQS 能派生各种锁的原因。

> **追问参考答：** 独占模式释放锁只 unpark 唤醒**后继一个节点**；共享模式释放时会**向后链式唤醒连续排队的共享节点**，因为它们都能同时持有锁。

### 13. ReentrantLock 怎么实现可重入？公平锁和非公平锁的实现区别？【中频】

通过state字段，0表示无线程持锁，1表示有，重入+1，state减到0就释放锁

公平锁中，线程获取到锁的顺序就是申请锁的顺序，如果线程发现CLH队列不为空，就一定会去排队
而非公平锁中，线程刚开始会CAS直接抢一次锁，抢到了就持有锁了，抢不到再去排队

> **知识点跳转：** [[ReentrantLock详解#二、四大核心特性详解]] · [[AQS 详解#9.1 AQS 公平锁和非公平锁的 tryAcquire 差异]]

**追问：** 非公平锁为什么"插队"？公平锁为什么开销大？

因为当head节点释放锁唤醒后继节点时，后继节点需要从阻塞状态恢复回来，这过程有上下文开销，而刚进来抢锁的线程没有这个开销，它如果能抢到就避免了这部分开销，所以非公平锁效率更高

---

**批改分析区：**

**评分：8.5/10**

- ✅ 可重入机制答对（state 计数，减到 0 释放）
- ✅ 公平/非公平实现区别答对（公平必排队、非公平先 CAS 抢一次）
- ✅ 追问答得**非常好**——完整因果链：唤醒后继有上下文切换开销 vs 新线程无此开销，所以非公平高效
- ⚠️ 可加一句提升精确度：非公平锁"抢一次"失败后进队列，进队后如果前驱是 head 还会再抢

**参考答案 ✅**

**① 可重入怎么实现：** 靠 state 计数。lock 一次 state+1，unlock 一次 state-1，**减到 0 才算释放**。同一个线程反复 lock，state 一直累加，不自己锁自己。

**② 公平 vs 非公平：** 公平锁——**先看队列**，CLH 队列不为空就老实排队，先来后到；非公平锁——**先进来先 CAS 抢一次**，抢到就持有，抢不到才排队。

**③ 为什么非公平效率高（追问）：** 公平锁唤醒 head 的后继要经过**线程从阻塞恢复的上下文切换**；而新来的线程是活跃的、没有这个开销，它 CAS 抢到就直接省掉了这次唤醒+切换。所以非公平吞吐更高，代价是可能饿死排队线程。**这也正是 synchronized/ReentrantLock 默认都用非公平的原因。**

> **追问参考答：** 见上。一句话——**非公平 = 用"可能插队"换"省一次上下文切换"**。

### 14. ReentrantReadWriteLock 的读写规则是什么？什么是锁降级？【中频】

可支持多个线程同时读，但只有一个线程能写
读读共享、读写互斥，写写互斥

锁降级指的是线程可以平滑的从持有写锁降级为持有读锁

> **知识点跳转：** [[ReentrantReadWriteLock 完整详解#六、读写锁的局限性：锁降级与升级]]

**追问：** 为什么只允许"锁降级"、禁止"锁升级"？锁升级的死锁是怎么发生的？

如果允许锁升级会导致死锁，因为如果多个线程同时申请升级，他们会互相等待其他读锁线程释放

---

**批改分析区：**

**评分：7.5/10**

- ✅ 读写规则答对（读读共享、写写互斥、读写互斥、只有一个写）
- ✅ 锁降级定义答对（写锁平滑降为读锁）
- ✅ 追问方向对（锁升级会死锁）
- ⚠️ 追问可更精确：死锁图景是**两个线程各持一把读锁，都想升级为写锁，互相等对方释放读锁** → 谁也拿不到写锁
- ⚠️ 加分点没提：**为什么需要锁降级**（写完后降为读，避免释放写锁瞬间被其他写线程插入）+ 实现（state 高 16 位写、低 16 位读）

**参考答案 ✅**

**① 读写规则：** 读读共享、写写互斥、读写互斥——**多个线程可同时读，写必须独占**。

**② 锁降级：** 持有写锁的线程，先获取读锁，再释放写锁 → 从写锁平滑降为读锁。**意义：** 写完数据降为读锁继续读，避免释放写锁的瞬间被其他写线程抢走，保证自己读的是自己写的。

**③ 为什么不能锁升级：** 会死锁。**两个线程各持一把读锁，都想升级为写锁**——升级要等所有读锁释放，A 等 B 放、B 等 A 放，互相等死。所以 ReentrantReadWriteLock 只支持降级、禁止升级。

> **追问参考答：** 见上面 ③。补充实现细节——**state 拆成高 16 位（写锁重入次数）+ 低 16 位（读锁持有数）**，一次 CAS 同时维护两把锁。锁降级成立是因为写锁→读锁是"持有者主动让出写独占"，安全；锁升级会死锁是因为读锁→写锁需要等所有读锁释放，而持读锁的线程又在等写锁，互相等死。

### 15. Condition 和 wait/notify 的区别？【低频·加分】



> **知识点跳转：** [[Condition 详解#五、与 wait()/notify() 全面对比]]

**追问：** Condition 怎么实现"多条件队列"？底层基于 AQS 的什么？

---

**批改分析区：**

**评分：0/10**

- ❌ 空着——低频加分题，但至少答出"**Condition 配合 Lock 用、可建多个条件队列；wait/notify 是 synchronized 自带、只有一个等待队列**"

**参考答案 ✅**

**① wait/notify：** Object 自带，**必须配合 synchronized** 用。只能**一个隐式等待队列**，notify 随机唤醒一个 / notifyAll 全唤醒，不能精确唤醒指定条件的线程。

**② Condition：** 配合 **ReentrantLock** 用（`lock.newCondition()`）。核心优势是**一个 Lock 上可以创建多个 Condition，各自独立条件队列**——比如生产者消费者，用"不满"和"不空"两个队列，各等各的，signal 精确唤醒对应队列。

**③ 底层：** Condition 是 AQS 内部的 **ConditionObject** 实现，每个 ConditionObject 维护自己的等待队列（node 复用 AQS 节点），await 移入条件队列、signal 从条件队列挪回同步队列。

> **追问参考答：** 见上。一句话——**多个 Condition = 多个独立等待队列**，这是 wait/notify 做不到的。

---

## 五、并发工具类（3 题）

### 16. CountDownLatch 和 CyclicBarrier 的区别？【中频】

CountDownLatch是一个或一组线程等待其他线程执行结束
CyclicBarrier是一组线程互相等待，直到共同达到某个公共点，相比CDL，它可以reset，重复使用

> **知识点跳转：** [[CountDownLatch详解#五、与 CyclicBarrier 全面对比]] · [[CyclicBarrier详解#五、与 CountDownLatch 全面对比]]

**追问：** 底层实现模式有什么区别？为什么 CountDownLatch 基于 AQS 共享模式、而 CyclicBarrier 用 ReentrantLock + Condition？



---

**批改分析区：**

**评分：6/10**

- ✅ 主问答对：CountDownLatch 是一组线程等别人执行完，CyclicBarrier 是一组线程互相等到共同点，且 CDL 不可复用、CB 可 reset
- ⚠️ 追问空着：**底层模式不同**没答——CDL 基于 AQS **共享模式**（await 阻塞、countDown 减到 0 链式唤醒），CyclicBarrier 用的是 **ReentrantLock + Condition**（每次 await 对 barrier 内部计数减，减到 0 触发 signalAll）

**参考答案 ✅**

CountDownLatch 和 CyclicBarrier 的区别，我从三个角度说。第一个是"谁等谁"：CountDownLatch 是**一个或一组线程等着其他线程把活干完**——比如主线程等几个子线程都完成初始化；CyclicBarrier 是**一组线程互相等**，谁先到谁等，直到大家都到齐了才一起往下走，像是旅行团等人齐了才出发。

第二个是**能不能复用**：CountDownLatch 是**一次性**的，计数器减到 0 就废了，不能重置；CyclicBarrier 是可循环的，一轮跑完自动进入下一轮，所以叫"回环屏障"。

第三个是**底层实现**：CountDownLatch 基于 AQS 的**共享模式**，核心就是一个计数器，await 时如果计数器不为 0 就阻塞，countDown 减到 0 时一次性唤醒所有等待线程；CyclicBarrier 不是直接靠 AQS 计数器，而是内部用一个 **ReentrantLock + Condition**，每次 await 先**加锁**，然后对计数器减一，减到 0 就用 Condition 的 signalAll 唤醒大家、进入下一轮；没减到 0 的线程就调 **Condition.await()——这一步会释放锁并挂起**，等被 signal 唤醒后**重新抢到锁**，检查代际变了就 return 进入下一轮。

> **追问参考答：** CountDownLatch 用 AQS **共享模式**——await 对 state 判断、countDown 减到 0 后链式唤醒所有等待者，适合"一个线程等一群线程完成"。CyclicBarrier 用 **ReentrantLock + Condition**——因为它的语义是"一组线程互相等齐 + 一轮结束自动重置进入下一轮"，用可重置的计数器 + Condition.signalAll 实现比 AQS 的一次性计数更直接；而且 CyclicBarrier 支持带参数的构造（`CyclicBarrier(int, Runnable)`）在放行时触发回调，Condition 模型下更好做。注意 await 的流程是**加锁 → 减计数 → 没到 0 的话 Condition.await() 释放锁并挂起 → 被 signalAll 唤醒后重新抢到锁 → 检查代际变了就 return 进入下一轮 → 解锁**，不是"拿着锁等"；被唤醒的线程**不对 count 做任何操作**（count 已被最后一个线程减到 0 并重置），抢回锁只是为了满足 Condition 语义 + 保证读到新 generation 的可见性。锁只保护改 count 的临界区，线程干活时全程无锁并行。一句话——CDL 是"一个人等多人"（共享模式），CB 是"多人互等 + 可循环"（ReentrantLock + Condition）。

### 17. Semaphore 的原理？应用场景？【低频·加分】

state表示剩余许可证的数量，适用于多线程共同操作一些共享资源的情况

> **知识点跳转：** [[Semaphore详解#三、底层原理：共享模式 AQS]]

**追问：** 信号量的"许可证"怎么实现？限流怎么用它？

基于state

---

**批改分析区：**

**评分：5.5/10**

- ✅ 主问答对核心：state 表示剩余许可证数量
- ⚠️ 应用场景太笼统："多线程共同操作共享资源"——更标准的说法是**控制同时访问某个资源的并发数**，比如限流、连接池
- ⚠️ 追问太简略："基于 state"——要说清机制：**acquire 对 state CAS -1（没了就阻塞）、release 对 state +1 并唤醒**；限流就是**限制同一时刻最多 N 个线程进入临界区**

**参考答案 ✅**

Semaphore 就是信号量，用来**控制同时能有多少个线程访问某个资源**，典型的应用是限流和连接池。它的核心就是 AQS 里的 state 字段，**state 表示还剩几个许可证**。

获取许可叫 acquire：线程进来先对 state 做 CAS 减一，如果减完大于等于 0，说明还有许可，直接通过；如果已经到 0 了，说明许可证发完了，线程就阻塞排队等着。用完释放叫 release：对 state 加一，然后唤醒一个正在等的线程。

做限流的话，我可以把许可数设为 N，比如 100，那就相当于**同一时刻最多允许 100 个线程同时往下走**，第 101 个就得等前面有人 release。连接池的思路一样，池子里有几个连接就设几个许可。

> **追问参考答：** 见上面。一句话——acquire 对 state 减一、不够就阻塞；release 对 state 加一、唤醒等待者。限流就是"同时最多 N 个线程通过"。

### 18. 这三个工具类底层都基于 AQS 的什么模式？【低频·加分】

共享模式

> **知识点跳转：** [[AQS 详解#3.2 共享模式 vs 独占模式]] · [[Semaphore详解#七、共享模式 vs 独占模式 一览]]

**追问：** 为什么 CountDownLatch 和 Semaphore 用共享模式，而 CyclicBarrier 用 ReentrantLock + Condition？（提示：跟"多个线程能同时放行"vs"需要循环复用"的语义有关）

---

**批改分析区：**

**评分：4/10**

- ⚠️ "共享模式"只对一半——**CountDownLatch 和 Semaphore 是 AQS 共享模式，但 CyclicBarrier 不是**（它用的是 ReentrantLock + Condition，不走 AQS 的 tryAcquireShared）
- ⚠️ 追问空着：没答出 CyclicBarrier 的特殊性（题干已经提示了，是送分点）

**参考答案 ✅**

这三个工具类底层**并不完全一样**，这里有个容易踩的坑。CountDownLatch 和 Semaphore 确实是基于 AQS 的**共享模式**——CountDownLatch 用 state 做计数器、countDown 减到 0 后链式唤醒所有等待线程；Semaphore 用 state 做许可证数、acquire 对 state 减一。它们的共同点是"**多个线程能同时被放行**"，所以是共享模式。

但 **CyclicBarrier 不是 AQS 共享模式**，它内部用的是 **ReentrantLock + Condition**：每次 await 拿锁，对内部计数器减一，减到 0 就用 Condition 的 signalAll 唤醒所有线程，然后进入下一轮（reset）。它之所以不用 AQS 的共享模式，是因为它的语义是"**互相等齐**"，需要一个可重置的屏障，ReentrantLock + Condition 实现起来更直接。

> **追问参考答：** CountDownLatch 和 Semaphore 走 AQS **共享模式**，因为它们要"放行多个线程"——CDL 减到 0 链式唤醒所有等待者、Semaphore 多线程都能 acquire，共享模式的"多个线程同时进临界区"天然契合。CyclicBarrier 要的是"互相等齐 + 一轮结束自动 reset"，AQS 的计数是一次性的，用它做重置反而别扭，所以用 **ReentrantLock + Condition** 自己维护可重置计数器 + signalAll 唤醒，更直接。一句话——**"多个放行"用共享模式，"循环等齐"用 ReentrantLock + Condition**。

---

## 六、阻塞队列与线程池（5 题）

### 19. 线程池的核心参数有哪些？各是什么含义？【高频】

核心线程数：核心线程的数量
最大线程数：所能创建线程的最大数量，超过则进入等待队列
空闲等待时间：非核心线程空闲的最大等待时间，超过就销毁
时间单位：空闲等待时间的单位
任务队列：存放待执行的任务
线程创建工厂：创建线程的工厂
拒绝策略：当最大线程数满且任务队列满时，拒绝任务的策略

> **知识点跳转：** [[线程池#二、ThreadPoolExecutor 七大核心参数]]

**追问：** 核心线程数和最大线程数怎么设置？CPU 密集型和 IO 密集型的建议公式？



---

**批改分析区：**

**评分：6/10**

- ✅ 七个参数全列全（核心/最大/空闲等待/时间单位/任务队列/工厂/拒绝策略）
- ⚠️ "最大线程数超过则进入等待队列"表述有误：**先入队列，队列满了才创建非核心线程**，不是直接超过最大线程数就进队列
- ⚠️ 追问空着：CPU 密集型 = 核心数+1、IO 密集型 = 核心数×2+IO 等待占比的公式没答

**参考答案 ✅**

线程池有七个核心参数：corePoolSize 核心线程数、maximumPoolSize 最大线程数、keepAliveTime 空闲等待时间、unit 时间单位、workQueue 任务队列、threadFactory 线程工厂、handler 拒绝策略。

核心线程数一般不会超过最大线程数，核心线程是一直活着的，非核心线程是 core 到 max 之间的那部分，空闲时间超过 keepAliveTime 就销毁。任务来了先交给核心线程，核心满了就**扔进任务队列**，队列也满了才创建非核心线程，非核心也满了就执行拒绝策略。

关于线程数设置，面试的回答套路是：CPU 密集型，建议设为 **CPU 核心数 + 1**，因为加一个能防止极端情况下的缺页中断等导致线程停顿；IO 密集型，建议设为 **CPU 核心数 × 2**，或者更精确的公式是 **核心数 × (1 + 等待时间 / 计算时间)**，因为 IO 密集型线程大部分时间在等 IO、CPU 空闲，可以多开线程让 CPU 忙起来。

> **这里的"线程数"= 核心线程数（corePoolSize）：** 公式给的是线程池的**稳态工作线程数**，落到参数就是 `corePoolSize`。常规做法把 `maximumPoolSize` 与它**设成相等**（固定大小线程池），此时 core = max，说"线程数"没有歧义；若 `core < max`，公式算的是 **core**，`maximumPoolSize` 是**突发峰值才顶上去的天花板**，需按洪峰另估，不由这个公式决定。

> **追问参考答：** CPU 密集型 = N+1，IO 密集型 = N×(1+等待时间/计算时间)。面试说"CPU 密集少开线程、IO 密集多开线程"这个方向对就行，精确公式视情况补。

### 20. 线程池提交一个任务后的执行流程是怎样的？【高频】

线程池提交任务后，先检查当前是否有空闲线程，有的话直接执行，否则查看线程数是否超过最大线程数，没超过就创建核心线程执行，否则查看是否超过最大线程数，没超过就创建非核心线程执行，超过了的话就放入任务队列，如果任务队列满了，就执行拒绝策略

> **知识点跳转：** [[线程池#二、ThreadPoolExecutor 七大核心参数]]

**追问：** 核心线程满 → 队列满 → 最大线程满 → 拒绝，每一步走哪个参数？

---

**批改分析区：**

**评分：4/10**

- ❌ **执行流程顺序答反了**：不是"先看最大线程数、再入队"。标准顺序是 **核心线程 → 队列 → 非核心线程 → 拒绝**：核心线程没满就创建核心线程执行；核心满了**先入任务队列**；队列也满了才创建非核心线程；非核心也满了才执行拒绝策略
- ⚠️ 追问空着：每一步走的参数没答（核心走 corePoolSize、入队走 workQueue、非核心走 maximumPoolSize、拒绝走 handler）

**参考答案 ✅**

线程池提交任务的执行流程是四步，顺序很重要：

第一步，**看核心线程池**：当前线程数小于 corePoolSize，就直接创建核心线程来执行这个任务。

第二步，**看任务队列**：核心线程都忙了，就把任务放进 workQueue 排队。

第三步，**看非核心线程**：队列也满了，就创建非核心线程来执行，这时线程数可以一直加到 maximumPoolSize。

第四步，**拒绝策略**：连最大线程数都满了，队列也满了，就执行 handler 拒绝策略。

所以记法就一句：**核心不满建核心 → 核心满入队 → 队列满建非核心 → 全满拒绝**。

> **追问参考答：** 第一步走 corePoolSize、第二步走 workQueue、第三步走 maximumPoolSize、第四步走 handler。注意队列在非核心线程之前——很多人答反成"先建非核心再入队"，是错的。

### 21. 线程池的拒绝策略有哪几种？【高频】

AbortPolicy直接拒绝，抛异常
CallerRunsPolicy由提交任务的主线程执行
DiscardPolicy直接抛弃该任务
DiscardOldestPolicy抛弃队列中最老的任务，提交执行当前任务

> **知识点跳转：** [[线程池#三、四种拒绝策略]]

**追问：** CallerRunsPolicy 为什么能起到"降速"作用？生产环境一般怎么选拒绝策略？

---

**批改分析区：**

**评分：6/10**

- ✅ 四种策略的行为全答对（AbortPolicy 抛异常、CallerRunsPolicy 主线程执行、DiscardPolicy 丢弃、DiscardOldestPolicy 丢弃最老提交当前）
- ⚠️ 追问空着：CallerRunsPolicy 降速原理——**主线程自己执行任务，执行期间主线程不能提交新任务，相当于天然节流**；另外生产选型没答

**参考答案 ✅**

四种拒绝策略，我按常用程度说。**AbortPolicy** 是默认的，直接抛 RejectedExecutionException，让调用方感知到丢任务了；**CallerRunsPolicy** 不抛异常也不丢弃，而是让提交任务的线程自己来执行这个任务，比如主线程提交的任务在 reject 时由主线程自己跑，这时候主线程忙着跑任务就没办法继续提交新任务了，相当于**天然节流**；**DiscardPolicy** 直接静默丢弃，任务丢了调用方不知道；**DiscardOldestPolicy** 把队列里排最久的那个任务丢掉，把当前任务塞进去。

生产环境一般默认用 **AbortPolicy**，因为丢任务不可接受，调用方需要感知。如果业务能接受一定延迟但不能丢，可以用 **CallerRunsPolicy**，让主线程执行来降速。**DiscardPolicy 和 DiscardOldestPolicy 基本不用**，静默丢弃风险太大。

> **追问参考答：** CallerRunsPolicy 降速原理：主线程执行任务时无法提交新任务 → 相当于自然限流。生产选型：AbortPolicy 最常用（默认），能接受延迟用 CallerRunsPolicy，Discard 系列少用。

### 22. 为什么不建议用 Executors 的便捷方法创建线程池？【中频】

因为Executors里面大多数工厂方法创建的线程池的任务队列都无界，生产环境高并发场景下极易OOM

> **知识点跳转：** [[线程池#四、Executors 工厂方法（及其问题）]] · [[supplyAsync生产环境务必自定义线程池#正确理解]]

**追问：** newFixedThreadPool / newCachedThreadPool 分别有什么隐患？（无界队列 / 无界线程数）

---

**批改分析区：**

**评分：5/10**

- ✅ 方向对：Executors 便捷方法大多用**无界队列**，高并发下容易 OOM
- ⚠️ 不够全面：只说了一个点（无界队列）。还有几个隐患没提——**newFixedThreadPool 的 LinkedBlockingQueue 无界**、**newSingleThreadExecutor 无界队列**、**newScheduledThreadPool 的 DelayedWorkQueue 无界**（同属"无界队列"这一类），而 **newCachedThreadPool 才是"无界线程数"**（max=Integer.MAX_VALUE + SynchronousQueue），每类要分开说
- ⚠️ 追问空着：fixed/cached 各自的隐患没分开答

**参考答案 ✅**

不建议用 Executors 便捷方法，核心原因是它们**参数被写死，隐藏了风险**。分几种情况：

**newFixedThreadPool / newSingleThreadExecutor / newScheduledThreadPool**：用的都是**无界队列**——Fixed/Single 是 `LinkedBlockingQueue`，Scheduled 是 `DelayedWorkQueue`（同样无界）。线程数是被限住的（Single = 1、Fixed = n、Scheduled 最多到 corePoolSize），但任务会无限往队列里堆，高并发时队列膨胀到内存溢出 OOM。

**newCachedThreadPool**：队列用的是 `SynchronousQueue`（不缓存任务），最大线程数是 **Integer.MAX_VALUE**——来多少任务就创建多少线程，高并发时线程数爆炸，CPU/内存耗尽。**它是这批里唯一"无界线程数"的那个。**

> **别被 ScheduledThreadPool 的 `max = Integer.MAX_VALUE` 骗了**：它虽然把最大线程数写成 `Integer.MAX_VALUE`，但因为**队列无界、永远不满**，"队列满才创建非核心线程"这条路径根本走不到，线程数**实际涨不过 `corePoolSize`**（内部 `ensurePrestart` 只在 `wc < corePoolSize` 时加线程）。所以它和 Fixed/Single 同属"**无界队列堆积任务**"那一类，隐患不是线程爆炸。**判定口诀：看工作队列有没有界——无界队列堆任务 OOM，`SynchronousQueue`（无容量）才轮到无界线程爆炸。**

所以生产环境一定要**手动 new ThreadPoolExecutor**，把队列设成有界的（如 ArrayBlockingQueue）、最大线程数和拒绝策略都显式指定，这样队列满、线程满时能通过拒绝策略兜底，而不是默默 OOM。

> **追问参考答：** newFixedThreadPool / newSingleThreadExecutor / newScheduledThreadPool = **无界队列**堆任务 OOM；newCachedThreadPool = **无界线程数**（max=Integer.MAX_VALUE + SynchronousQueue），线程爆炸。通用原则：用有界队列 + 显式拒绝策略，别用 Executors 的快捷方法。

### 23. 阻塞队列有哪些？ArrayBlockingQueue 和 LinkedBlockingQueue 的区别？【中频】

ArrayBlockingQueue底层数据结构是数组，天然有界
LinkedBlockingQueue是链表，无界

> **知识点跳转：** [[阻塞队列详解#七、四大实现对比表 ⭐]]

**追问：** 有界/无界？线程池默认用哪个？

---

**批改分析区：**

**评分：5/10**

- ✅ 数据结构答对：ArrayBlockingQueue 数组天然有界、LinkedBlockingQueue 链表
- ⚠️ **"LinkedBlockingQueue 无界"表述不准确**：它**默认无界，但构造时可以指定容量变成有界**——这是它和 ArrayBlockingQueue 的关键差异点之一
- ⚠️ 追问空着：没答线程池默认用哪个（Executors.newFixedThreadPool 默认用无界 LinkedBlockingQueue；手动 new ThreadPoolExecutor 一般指定有界 ArrayBlockingQueue）

**参考答案 ✅**

阻塞队列有两大类：**ArrayBlockingQueue** 底层是**数组**，**创建时必须指定容量，天然有界**，FIFO；**LinkedBlockingQueue** 底层是**链表**，**默认无界，但构造时也能指定容量变成有界**。

它们的区别主要在几个维度：ArrayBlockingQueue 有界、必须初始化容量、用**一把锁**（put/take 共用，并发度低）、数组**内存连续、缓存友好、且不创建节点**，单次操作开销略小；LinkedBlockingQueue 默认无界、可指定容量、生产者和消费者**各用一把锁**（putLock/takeLock 分离，吞吐更高）、每次 put 要 new 节点、链表节点有额外内存开销且缓存不友好。

线程池默认用的是 **LinkedBlockingQueue（无界）**——Executors.newFixedThreadPool 就是它。但生产环境一般**不推荐无界队列**，因为任务堆积会 OOM，应该手动指定有界队列。

> **追问参考答：** ArrayBlockingQueue 有界、LinkedBlockingQueue 默认无界但可指定容量变有界。线程池默认 newFixedThreadPool 用无界 LinkedBlockingQueue；生产推荐手动指定有界 ArrayBlockingQueue。

### 24. 用 wait/notify 实现生产者-消费者模式，为什么判断条件用 while 不能用 if？为什么用 notifyAll 不用 notify？【高频】

```java
import java.util.LinkedList;
import java.util.Queue;

public class PC_WaitNotify {
    private static final Queue<Integer> queue = new LinkedList<>();
    private static final int CAPACITY = 10;
    private static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 20; i++) {
                synchronized (lock) {
                    // ① 必须用 while，不能用 if
                    while (queue.size() == CAPACITY) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    queue.offer(i);
                    System.out.println("生产:" + i);
                    // ② 必须用 notifyAll，不能用 notify
                    lock.notifyAll();
                }
            }
        });

        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 20; i++) {
                synchronized (lock) {
                    while (queue.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    System.out.println("消费:" + queue.poll());
                    lock.notifyAll();
                }
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("end");
    }
}
```

> **知识点跳转：** [[Java并发编程面试题#生产者消费者模式的实现]]

**追问：** wait 和 sleep 的区别？wait 为什么必须写在 synchronized 里？

---

**批改分析区：**

**评分：/10**

- 

**参考答案 ✅**

这题的追问就是考点本身，需要逐个准备好。我从四个角度说。

**① 为什么判断条件用 while 不能用 if：** 两个原因。第一个是 **notifyAll 会唤醒所有等待线程**，它们重新竞争锁，第一个抢到的线程修改了条件（比如取走了队列里的数据），后抢到的线程拿到锁时条件已经不成立了，必须重新检查。如果用 if，被唤醒的线程会直接往下执行，造成超卖或越界。第二个是 **JDK 文档明确存在"虚假唤醒"（spurious wakeup）**，操作系统层面可能无缘无故唤醒线程，用 if 就会在条件不满足时继续往下走，出问题。

**② 为什么用 notifyAll 不用 notify：** notify 只随机唤醒一个线程，可能唤醒的是"同类角色"——比如队列满了，生产者 wait，这时另一个生产者进来也 wait，消费者取走一个元素后 notify，结果唤醒的是另一个生产者，它检查发现队列还是满的又 wait，所有线程都在 wait，**程序永久卡死**。notifyAll 让所有等待线程重新竞争，安全。

**③ wait 和 sleep 的区别：** wait 释放锁、属于 Object、必须在同步块中、被 notify 唤醒；sleep 不释放锁、属于 Thread、不需要同步块、超时自动醒。

**④ wait 为什么必须写在 synchronized 里：** wait 的语义是"释放锁并等待"，前提是先持有这把锁，否则抛 IllegalMonitorStateException。这也是锁资源属于对象（监视器）而不属于线程的体现。

**⑤ 加分项——能说出 ArrayBlockingQueue 源码：** ArrayBlockingQueue 就是一把 **ReentrantLock + 两个 Condition（notEmpty、notFull）** 实现的"精准唤醒"——消费者只唤醒生产者、生产者只唤醒消费者，比 notifyAll 更高效。能画到这一层，面试官就知道你看过源码。

> **追问参考答：** 见上面 ③④。wait 和 sleep 核心区别是**释不释放锁**；wait 必须在 synchronized 里是因为它的语义就是"释放当前持有的监视器锁"，不持有锁就没东西可释放，抛异常。

### 25. 两线程交替打印 1-100，外层循环在锁外读取共享变量有什么可见性问题？怎么修复？【中频·加分】

**❌ 错误写法（锁外读取共享变量）：**

```java
public class AlternatePrint100_Wrong {
    private static final Object lock = new Object();
    private static int count = 1;  // 普通变量，非 volatile

    private static void run(boolean isOdd) {
        // ⚠️ 陷阱：外层循环在锁外读取 count
        while (count <= 100) {  // 锁外读取，不受 synchronized 保护
            synchronized (lock) {
                while ((count % 2 == 1) != isOdd) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (count <= 100) System.out.println(Thread.currentThread().getName() + ":" + count++);
                lock.notifyAll();
            }
        }
    }

    public static void main(String[] args) {
        new Thread(() -> run(true), "奇数线程").start();
        new Thread(() -> run(false), "偶数线程").start();
    }
}
```

**✅ 正确写法（所有读写都在锁内）：**

```java
public class AlternatePrint100_Correct {
    private static final Object lock = new Object();
    private static int count = 1;

    private static void run(boolean isOdd) {
        while (true) {  // 外层用 while(true)，终止条件放锁内检查
            synchronized (lock) {
                // ① 先检查是否结束（锁内读取 count）
                if (count > 100) {
                    lock.notifyAll();  // 唤醒可能仍在等待的另一个线程
                    return;
                }
                
                // ② 判断是否轮到自己
                while ((count % 2 == 1) != isOdd) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                
                // ③ 再次检查（因为被唤醒后可能 count 已越界）
                if (count > 100) {
                    lock.notifyAll();
                    return;
                }
                
                System.out.println(Thread.currentThread().getName() + ": " + count++);
                lock.notifyAll();
            }
        }
    }

    public static void main(String[] args) {
        new Thread(() -> run(true), "奇数线程").start();
        new Thread(() -> run(false), "偶数线程").start();
    }
}
```

> **知识点跳转：** [[内存屏障和happens-before]] · [[Java并发编程面试题#JMM 内存模型]]

**追问：** 为什么同步块里的读取可靠？为什么不简单把 count 改成 volatile？happens-before 规则在这里怎么起作用？

---

**批改分析区：**

**评分：/10**

- 

**参考答案 ✅**

这题是 wait/notify 协作的高级陷阱，涉及 JMM 可见性和 happens-before 规则。我从三个角度说。

**① 锁外读取共享变量的风险：** 错误写法的外层循环 `while (count <= 100)` 在锁外读取普通共享变量 count，**不受 synchronized 保护**。JMM 不保证线程每次都从主内存重新读 count，编译器或处理器可能把 count 缓存到寄存器或 CPU 缓存里。线程 A 在同步块内把 count 改成 101，线程 B 的外层循环可能读到的还是旧值 100，继续进入同步块——**一个线程改了共享变量，另一个线程看不到**。

**② 为什么同步块里的读取可靠：** 因为 **happens-before 规则：解锁 happens-before 后续的加锁**。线程 A 退出 synchronized (lock) 时释放锁，线程 B 随后进入同一把锁时，JMM 保证线程 B 能看到线程 A 在释放锁之前对 count 的所有写入——这是锁的内存语义。所以把所有对 count 的读写都放进同步块，既保证可见性，也避免"检查和使用"之间的竞态（TOCTOU 问题）。

**③ 为什么不简单把 count 改成 volatile：** volatile 只保证读写的可见性和禁止指令重排序，**不会让"判断轮次 → 打印 → 递增 → 通知"这一整套操作变成原子的**。这里本来就需要锁来协调两个线程的交替（奇数线程和偶数线程互相等待），所以把所有状态检查和更新都放在同一把锁里，逻辑更统一、更安全。

**④ 正确写法的关键点：** 
- 外层用 `while (true)`，终止条件 `if (count > 100) return;` 放在锁内检查
- 所有对 count 的读写（包括终止条件判断）都在 synchronized 块内
- wait 之后醒来要再次检查终止条件（因为可能被 notifyAll 唤醒时 count 已经越界）
- 退出前 `lock.notifyAll()` 唤醒可能仍在 wait 的另一个线程，避免死等

**⑤ 判断原则（记住这句话）：** **共享变量如果有线程写，就应当通过同一把锁保护所有读写，或使用明确适用的并发工具（如 volatile、Atomic*）；永远不要在锁外读取普通共享变量来决定线程是否继续工作。**

> **追问参考答：** 同步块里的读取可靠是因为 **happens-before 规则**——线程 A 解锁 happens-before 线程 B 加锁，B 能看到 A 释放锁前的所有写入。不用 volatile 是因为这里需要锁协调交替（互相等待），volatile 只保证可见性不保证原子性，把所有状态检查和更新都放锁里更统一。happens-before 在这里的作用：线程 A 在 `synchronized (lock) { count++; }` 后释放锁，线程 B 进入同一把锁时，JMM 保证 B 读到的 count 是 A 写入后的新值。

---

## 七、ThreadLocal（2 题）

### 26. ThreadLocal 的原理？为什么会有内存泄漏？怎么避免？【高频】

每个线程都有自己的threadLocalMap，key为ThreadLocal，是entry对它的弱引用，value是设定的值，是强引用，当在方法中使用完th，对ThreadLocal的强引用链消失时，下次GC会将ThreadLocal回收，而value仍被entry强引用着，从而导致内存泄露

每次都要在fially块中手动remove

>优化描述

`ThreadLocalMap` 中的每个 `Entry`，其 key 是对 `ThreadLocal` 对象的弱引用；value 则是强引用。





> **知识点跳转：** [[ThreadLocal 深度解析与内存泄露笔记#三、内存泄露是如何发生的？]]

**追问：** ThreadLocalMap 的 key 为什么用弱引用？泄漏链路是什么？

因为如果用强引用的话，就算对ThreadLocal的强引用结束后，ThreadLocal也永远都不会被回收，这会造成更严重的内存泄露
泄露链路是Thead引用threadLocalMap，threadLocalMap引用entry，entry引用threadlocal

---

**批改分析区：**

**评分：8.5/10**

- ✅ 原理答得完整：每个线程有自己的 ThreadLocalMap、key 是弱引用指向 ThreadLocal、value 是强引用
- ✅ 泄漏机制准确：ThreadLocal 的强引用消失被 GC 回收后，value 仍被 Entry 强引用 → 泄漏
- ✅ 避免答对：finally 中手动 remove
- ✅ 追问 key 弱引用原因答对（强引用会导致 ThreadLocal 永不回收）
- ⚠️ 追问"泄漏链路"表述有点绕：泄漏的实质是 **key 已为 null 的 Entry 里 value 仍被强引用**（ThreadLocal 已经回收了，剩 value 被吊着），你说"entry 引用 threadlocal"方向反了——**泄漏的是 value，不是 threadlocal**

**参考答案 ✅**

ThreadLocal 的原理，先说数据结构：每个线程内部都维护着一个 ThreadLocalMap，这个 Map 的 key 是 ThreadLocal 对象本身，value 是你 set 进去的值。线程存取值的时候，就是拿当前线程自己的 ThreadLocalMap 来查，所以不同线程之间天然隔离，互不干扰。

那为什么会有内存泄漏呢？关键在于 ThreadLocalMap 里那个 Entry 的引用设计：key 对 ThreadLocal 是**弱引用**，value 对值是**强引用**。假设你在一个方法里用了 ThreadLocal，方法结束、ThreadLocal 的强引用断了，那 ThreadLocal 这个 key 就会被 GC 回收掉，变成 null。但 value 还是强引用，被 Entry 吊着——这就成了一个 key 为 null、value 还在的"死 Entry"，一直占着内存，回收不掉，这就是内存泄漏。

怎么避免？最标准的做法就是**用完在 finally 里手动 remove()**，把当前 ThreadLocal 对应的 Entry 整个清掉，key 和 value 一起断掉，GC 才能回收。

> **追问参考答：** key 为什么用弱引用？因为如果 key 对 ThreadLocal 是强引用，那即使业务代码里对 ThreadLocal 的强引用已经断了，这个 ThreadLocal 还是被 ThreadLocalMap 的 key 强引用着，永远回收不掉，泄漏更严重。用弱引用至少保证 ThreadLocal 本身能回收。泄漏链路要讲清楚：泄漏的是 **value**——ThreadLocal 被回收成 null key 后，`Entry → value` 这条强引用链还活着，value 就泄漏了；同时 Thread → ThreadLocalMap → Entry 的引用链还在，所以只要线程不销毁（比如线程池线程），这些 Entry 就会一直存在。

### 25. 在线程池中使用 ThreadLocal 有什么坑？【中频】

由于线程池中的线程是循环共用的，如果用ThreadLocal会导致数据错乱，所以用完之后需要remove，不然会污染下一个使用的业务逻辑

> **知识点跳转：** [[ThreadLocal 深度解析与内存泄露笔记#六、最佳实践：如何优雅地防范内存泄露？]] · [[线程池#Java 线程池核心知识]]

**追问：** 线程池复用线程时，"值串扰"和"内存泄漏"是同一个根源吗？remove() 具体清理掉了什么？

不是
清理掉了所有key为null的entry

---

**批改分析区：**

**评分：5/10**

- ✅ 主问答对核心：线程池线程循环复用 → ThreadLocal 数据残留 → 污染下个业务，用完要 remove
- ⚠️ "不是同一个根源"过于绝对：串扰和泄漏**根源相同**——都是"线程池线程长期存活 + 不 remove 导致 Entry 残留"。区别只在表现：串扰是"下一个任务读到了上一个任务的旧值"（脏读），泄漏是"value 回收不掉"（占内存）。根源是同一个
- ❌ 追问硬伤：**remove() 只清理当前 ThreadLocal 对应的那一个 Entry**，不是"所有 key 为 null 的 entry"。清理"所有 key 为 null 的 entry"是 ThreadLocalMap 的 **expungeStaleEntries**（在 set/get 时触发）做的，不是 remove()

**参考答案 ✅**

在线程池里用 ThreadLocal 的坑，核心是**线程复用带来的脏数据**。线程池的线程执行完一个任务不会销毁，而是回到池子里复用。如果这个线程在执行任务 A 时 set 了 ThreadLocal，任务 A 结束你没 remove，那这个值还留在线程的 ThreadLocalMap 里。下一个任务 B 恰好又被分到这个线程上，它一读 ThreadLocal 就拿到了任务 A 留下的值——**业务逻辑被污染了**，这就是串扰。

怎么避免？**每个任务用完在 finally 里 remove()**，把当前 ThreadLocal 对应的 Entry 清掉，下个任务来的时候线程的 ThreadLocalMap 是干净的。

> **追问参考答：** 串扰和泄漏是**同一个根源**——线程池线程长期存活、不 remove 导致 Entry 残留。表现不同：串扰是"下个任务读到旧值"（脏数据，业务错误）；泄漏是"value 占着内存回收不掉"（如果线程一直存活，泄漏的 value 会一直累积）。remove() 具体清理的是**当前 ThreadLocal 对应的那一个 Entry**（key 和 value 一起断引用）；至于"清理所有 key 为 null 的 Entry"，那是 ThreadLocalMap 内部的 expungeStaleEntries 在 set/get 时顺带做的清理，不是 remove() 的职责。

---

## 八、并发容器（2 题）

### 26. ConcurrentHashMap 1.7 和 1.8 的实现区别？【高频】

CHM1.7底层结构用的是Segment分段锁，初始化后并发度就固定了
而1.8去掉了分段锁，直接就是一整个桶数组，桶可以扩容缩容，并发度可以动态调整
并且1.8还增加了乐观锁策略，空桶时直接CAS无锁，性能更好
还有，1.8增加了树化，将查询时间复杂度从O(N)降到了O(logN)
1.8还优化了size()方法，它采用LongAdder思想，将size分为了一个base和多个cell，写时只需写各自线程的cell即可，把一个热点转化成了多个冷点，读size时，将base和多个cell相加即可
（1.7中是读每个segment的count快照，最后再比较初始的modCount和现在的modCount，如果不一致说明有人动过，需要给所有segment加锁再算一遍，实际高并发场景下极易锁全表，性能地狱

> **知识点跳转：** [[ConcurrentHashMap_1.7_vs_1.8#三、为什么废弃分段锁？本质原因]]

**追问：** 1.8 的 CAS + synchronized 为什么比 1.7 的分段锁性能更好？锁的粒度降到了什么级别？

1.8 空桶时直接CAS，无锁，并且1.8synchronized本身优化了，加了锁升级，性能更好
锁的粒度降到了桶级别

---

**批改分析区：**

**评分：8.5/10**

- ✅ 主问答得很全：分段锁→桶数组、并发度固定→动态扩容、CAS 无锁、树化 O(N)→O(logN)、size() 用 LongAdder 思想
- ✅ 追问答对：锁粒度降到**桶（Node 数组的单个槽位）级别**；CAS + synchronized 锁升级
- ⚠️ 小瑕疵：synchronized 优化（锁升级）对 1.8 有贡献，但更本质的是**锁粒度从 Segment（一段多个桶）细化到单个桶（一个 Node）**——竞争范围从"整个分段"缩小到"一个哈希槽"
- ⚠️ 括号里的 1.7 size() 对比是加分项，答得不错，但面试口述时最好把"锁全表"说成"给所有 Segment 加锁"，更准确

**参考答案 ✅**

ConcurrentHashMap 从 1.7 到 1.8，核心变化是**锁粒度从"段"细化到"桶"**，我分四点说。

第一是数据结构。1.7 是 **Segment + HashEntry**，一个 Segment 是一把小锁，默认 16 个 Segment，所以并发度上限就是 16，初始化后固定不变。1.8 去掉了 Segment，直接用**一个大的 Node 数组**，每个桶（哈希槽）都能作为锁的粒度，所以并发度不再有固定上限，桶扩容后并发度还能跟着涨。

第二是加锁方式。1.7 的所有操作都要先抢 Segment 的锁（分段锁），哪怕只是读。1.8 改成**空桶时直接 CAS 无锁**，只有桶里有节点发生冲突时才用 **synchronized 锁住这个桶**——而且 JDK 1.6+ 的 synchronized 本身有锁升级优化，性能比 1.7 的 Segment 锁好很多。

第三是查询优化。1.8 引入了**树化**：当单个桶的链表长度超过阈值（8）且数组容量 ≥ 64 时，链表转成红黑树，把查询从 O(N) 降到 O(logN)。

第四是 size() 的优化。1.7 靠读每个 Segment 的 count 快照 + modCount 校验，冲突时要给所有 Segment 加锁重算，高并发下容易"锁全表"。1.8 用了 **LongAdder 思想**：维护一个 base + 多个 cell，写操作只更新自己线程对应的 cell（把一个热点拆成多个冷点），读 size 时把 base 和所有 cell 加起来即可，无锁高效。

> **追问参考答：** 1.8 性能更好的本质是**锁粒度变小**——从"锁一段"（Segment 覆盖多个桶）变成"锁一个桶"（单个 Node 槽位）。空桶无锁（CAS）、有冲突才锁单桶（synchronized + 锁升级），并发冲突范围大幅缩小。面试说"锁粒度从段降到桶，空桶 CAS 无锁"就抓住了核心。

### 27. CopyOnWriteArrayList 的原理？适用场景？【中频】

写时复制
适用于读多写少


> **知识点跳转：** [[Java并发编程面试题#juc包下你常用的类？]]

**追问：** 写时复制、读不加锁？适合读多写少还是写多读少？

不加锁

---

**批改分析区：**

**评分：5/10**

- ✅ 主问答对核心：写时复制 + 读多写少场景
- ❌ 太简略：写时复制的**机制**没展开——读不加锁但写要加锁；写时复制整个数组、替换引用，读旧数组、写新数组，两者互不干扰
- ❌ 追问只答了"不加锁"，但**没答场景**（读多写少），也没答"为什么适合读多写少"（写代价高，复制整个数组，写多就频繁复制，性能崩）

**参考答案 ✅**

CopyOnWriteArrayList 的原理是**写时复制**：读操作不加锁，直接读当前数组，所以读性能极高；写操作会加锁（ReentrantLock），把整个数组复制一份，在副本上做修改，改完再把底层数组的 volatile 引用指向新副本。这样读者永远在读一个**不可变的旧数组**，写者改的是新副本，读写互不干扰——**读者确实不用管写**，因为读拿的是不可变快照。

但这里有个关键点要讲透：**写锁不是防读者的，是防其他写者的**。因为写是"复制→改→换引用"三步，如果不加锁，两个写线程可能同时基于同一个旧数组各自复制一份、各自 setArray，后一个 setArray 会把前一个的修改整个覆盖掉——**丢失更新**。锁的作用就是让这套"复制-改-换"串行化：一个写线程把新数组发布出去后，下一个写线程才能基于**最新的**数组再复制，保证每次写的修改都保留下来。

它适合的场景是**读多写少**，比如黑名单、缓存、监听器列表这类"读非常频繁、写很少"的数据结构。

但反过来就不行——**写多读少**会非常糟糕，因为每次写都要复制整个数组，O(N) 的拷贝成本，频繁写就频繁复制，性能和内存都受不了。而且写时复制是**弱一致性**的，读者可能读到旧数据（读的是快照），不适合对实时性要求高的场景。

> **追问参考答：** 读不加锁、写加锁（ReentrantLock + 复制数组）。**锁不是防读者的**——读者读的是 volatile 引用指向的不可变旧数组，天然安全；**锁防的是写写竞争**：两个写线程若并发，会基于同一旧数组各自复制、各自换引用，后写的把先写的覆盖掉（丢失更新），加锁让写串行化。适合读多写少：读无锁高性能；写要复制整个数组 O(N)，写多就频繁复制 → 性能崩。另外它是弱一致性（读快照），不适合强一致需求。

---

## 关联知识点

- [[Java并发编程面试题]] — Java 并发八股总览
- [[synchronized 关键字详解]] — synchronized 原理与锁升级
- [[volatile 关键字详解]] — volatile 语义与底层
- [[内存屏障和happens-before]] — 有序性与可见性保障
- [[AQS 详解]] — AQS 三要素、CLH 队列、源码
- [[ReentrantLock详解]] — 可重入锁
- [[ReentrantReadWriteLock 完整详解]] — 读写锁
- [[Condition 详解]] — 条件变量
- [[CountDownLatch详解]] / [[CyclicBarrier详解]] / [[Semaphore详解]] — 并发工具
- [[阻塞队列详解]] — BlockingQueue 五大实现
- [[线程池]] — 线程池参数、流程、拒绝策略
- [[ThreadLocal 深度解析与内存泄露笔记]] — ThreadLocal 与泄漏
- [[ConcurrentHashMap_1.7_vs_1.8]] — CHM 演进
- [[CompletableFuture]] — 异步编程

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-25 14:10 | 2026-08-25 14:10 | Java 并发面试题笔记：覆盖 JMM 三大特性、synchronized 锁升级、volatile/CAS、AQS 与 JUC 锁、线程池参数与流程、ThreadLocal 泄漏及并发容器等高频考点，含批改分析与参考答案。 | 第一轮背记完成 |
