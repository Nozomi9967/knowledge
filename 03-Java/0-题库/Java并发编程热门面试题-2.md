---
tags:
  - java
  - 并发
  - 面试题
created: 2026-10-25
updated: 2026-10-25
---

# Java 并发编程热门面试题（二）

> **考查范围**：CAS / AQS / JUC 锁与工具类 / 线程池 / 阻塞队列 / 并发容器 / 死锁（题 23-43）
> **答案形态**：按口述化标准重写——每段都是能直接开口说的话，不分点、不列表、不画表。
> **上半部分**：线程基础 / JMM / synchronized / volatile（题 1-22）见 [[Java并发编程热门面试题-1]]。

---

## 五、CAS 与 AQS（4 题）

### 23. CAS 是什么？它有什么问题（ABA）？怎么解决？

> **知识点跳转：** [[Java并发编程面试题]] · [[ABA 问题详解]]

**参考答案**

CAS 就是 Compare And Swap，对比并交换，三个参数：内存值 V、预期值 A、新值 B。只有当 V 等于 A 时才把 V 更新成 B，否则不更新，失败的话调用方可以重试。底层是 CPU 的原子指令，比如 x86 的 lock cmpxchg，属于无锁实现。它有三个问题。第一是 ABA：线程 1 读到值 A 准备 CAS，线程 2 把 A 改成 B 又改回 A，线程 1 再 CAS 发现还是 A 就成功了，但中间其实被改过。典型场景是无锁栈，栈顶引用没变但栈的结构已经变了，CAS 误判成功。解法是加版本号，用 AtomicStampedReference，每次更新 stamp 加一，CAS 同时比较引用和 stamp。第二是自旋开销，竞争激烈时大量线程空转烧 CPU。第三是只能保证单个变量的原子性，多个变量要原子就得用 AtomicReference 把它们打包成一个对象。

**追问参考答**

为什么不能所有锁都用 CAS？因为它适合"短临界区、低竞争"的场景，改成自旋反而浪费 CPU。高竞争时线程反复失败重试，CPU 打满还没进展，这种时候重量级锁把线程挂起反而更划算。所以 ConcurrentHashMap 在桶为空时用 CAS、有冲突时才上 synchronized，就是这个取舍。

### 24. AQS 的核心原理是什么？

> **知识点跳转：** [[AQS 详解]]

**参考答案**

AQS 是 JUC 的基石，一个抽象的同步队列框架，ReentrantLock、Semaphore、CountDownLatch 都是基于它做的。核心三件套：state 状态字段、CLH 变体双向等待队列、tryAcquire 这类钩子方法。state 是 volatile 的，语义由子类定义：ReentrantLock 里它是重入次数，0 表示无人持有、重入加一；Semaphore 里它是剩余许可证数；CountDownLatch 里它是还没完成的计数。所以不能说"独占模式下 state 只有 0 和 1"，可重入锁重入两次就是 2。CLH 队列存抢锁失败的线程，原始 CLH 是自旋的，AQS 改成阻塞式：后继在 park 之前先把自己的前驱节点 waitStatus 置为 SIGNAL，意思是"你释放的时候记得 unpark 我"，前驱 release 时据此唤醒后继。钩子方法 tryAcquire、tryRelease 由子类实现，决定具体锁的行为，这就是 AQS 能派生出各种锁的原因。

**追问参考答**

独占和共享的区别在唤醒：独占模式释放锁只 unpark 后继一个节点；共享模式释放会向后链式唤醒连续的共享节点，因为它们都能同时持有锁。注意"被唤醒不等于拿到锁"，醒来还要重新 tryAcquire 抢一次。

### 25. AQS 用了什么设计模式？

> **知识点跳转：** [[AQS 详解]]

**参考答案**

AQS 用的是模板方法模式。它把加锁解锁的骨架写在 acquire、release 这些 final 方法里，比如 acquire 里依次做 tryAcquire 尝试获取、失败就入队、入队后挂起等待，这一整套流程是固定的、子类改不了；真正变化的部分——"什么算获取成功"——留给 tryAcquire、tryRelease、tryAcquireShared 这些钩子方法让子类实现。ReentrantLock 的 NonfairSync 实现 tryAcquire 时判断 state 是不是 0、是不是自己持有；Semaphore 实现时把 state 当许可证减一；CountDownLatch 实现时判断 state 是否已经减到 0。同一套队列和挂起唤醒机制，配上不同的 tryAcquire，就变成了不同的同步器，这就是模板方法的威力。

**追问参考答**

为什么不把这些全写死？因为各种同步器的语义差别很大：有的独占有的共享、有的计次有的计许可证。如果写死就没法复用；如果全靠子类写，队列、CAS、park 这些容易出错的逻辑每个子类都要重写一遍。模板方法正好把不变的骨架和变化的行为分开，这也解释了为什么 AQS 的源码里 acquire 是 final 而 tryAcquire 是 protected 的。

### 26. ReentrantLock 怎么实现可重入？公平锁和非公平锁的实现区别？

> **知识点跳转：** [[ReentrantLock详解]] · [[AQS 详解]]

**参考答案**

可重入靠 state 计数：lock 一次 state 加一，unlock 一次减一，减到 0 才算真正释放，所以同一个线程反复 lock 不会自己锁自己。公平和非公平的区别在 tryAcquire 那一小段：公平锁进来先看队列，CLH 队列不为空就老实排队，先来后到；非公平锁进来先 CAS 抢一次，抢到就直接持有，抢不到才去排队。非公平效率高的原因是：公平锁唤醒 head 的后继时要经过线程从阻塞恢复的上下文切换，而新来的线程是活跃的、没这个开销，它抢到就省掉了一次唤醒加切换。所以非公平吞吐更高，代价是可能饿死排队已久的线程。synchronized 和 ReentrantLock 默认都是非公平，就是这个原因。

**追问参考答**

非公平锁"抢一次"失败进队之后，如果发现前驱是 head，还会再抢一次，这叫"插队"，是 AQS 里非公平的第二次机会。所以非公平不等于完全无序，只是允许活跃线程插队。

---

## 六、JUC 锁与工具类（5 题）

### 27. ReentrantReadWriteLock 的读写规则是什么？什么是锁降级？

> **知识点跳转：** [[ReentrantReadWriteLock 完整详解]]

**参考答案**

规则是三句话：读读共享、写写互斥、读写互斥，也就是多个线程可以同时读，写必须独占。它的实现是把 state 拆成两半，高 16 位记写锁的重入次数、低 16 位记读锁的持有数，一次 CAS 同时维护两把锁。锁降级是持有写锁的线程先获取读锁、再释放写锁，从写锁平滑降为读锁。为什么要降级：写完数据后还需要继续读，如果直接释放写锁，别的写线程可能立刻插进来改数据，你接着读到的就不是自己刚写的了；先拿读锁再放写锁，能保证这段读的连续性。反过来锁升级是不允许的，会死锁。

**追问参考答**

锁升级为什么死锁：两个线程各持一把读锁，都想升级成写锁，而升级必须等所有读锁释放，A 等 B 放、B 等 A 放，互相等死。所以这套锁只支持降级、禁止升级。补充一点，读锁不支持条件变量，只有写锁能 new Condition。

### 28. Condition 和 wait/notify 的区别？

> **知识点跳转：** [[Condition 详解]]

**参考答案**

wait/notify 是 Object 自带的，必须配合 synchronized 用，只有一个隐式的等待队列，notify 随机唤醒一个、notifyAll 全唤醒，没法精确唤醒某一类线程。Condition 配合 ReentrantLock 用，通过 lock.newCondition 创建，核心优势是一个锁上可以开多个 Condition，各自维护独立的等待队列。比如生产者消费者，可以建"不满"和"不空"两个队列，生产者等不满、消费者等不空，signal 的时候精确唤醒对应那一队，不会像 notifyAll 那样把无关线程也全部叫起来。底层上 Condition 是 AQS 内部的 ConditionObject 实现，await 把节点移进条件队列并释放锁，signal 把节点从条件队列挪回同步队列等着重新抢锁。

**追问参考答**

一句话总结：多个 Condition 等于多个独立等待队列，这是 wait/notify 做不到的。注意 await 和 wait 一样要放在锁保护下，否则抛 IllegalMonitorStateException。

### 29. CountDownLatch 和 CyclicBarrier 的区别？

> **知识点跳转：** [[CountDownLatch详解]] · [[CyclicBarrier详解]]

**参考答案**

三个角度。第一是谁等谁：CountDownLatch 是一个或一组线程等别人把活干完，比如主线程等几个子线程初始化完成；CyclicBarrier 是一组线程互相等，谁先到谁等着，人到齐了一起走，像旅行团等齐了才出发。第二是能不能复用：CountDownLatch 一次性，计数减到 0 就废了；CyclicBarrier 可循环，一轮跑完自动进入下一轮，所以叫回环屏障。第三是底层实现：CountDownLatch 基于 AQS 共享模式，await 时计数不为 0 就阻塞，countDown 减到 0 时一次性唤醒所有等待者；CyclicBarrier 不用 AQS 计数，内部是 ReentrantLock 加 Condition，每次 await 先加锁、对计数减一，减到 0 就 signalAll 唤醒大家进入下一轮，没减到 0 的线程调 Condition.await 释放锁并挂起。

**追问参考答**

为什么两者实现不同？因为"放行多个线程"这个语义天然契合 AQS 共享模式，而"互相等齐还能重置"用一次性计数很别扭，用可重置计数器加 Condition 更直接。另外 CyclicBarrier 支持构造时传一个 Runnable，在放行那一刻执行，做汇总统计很方便。

### 30. Semaphore 的原理？应用场景？

> **知识点跳转：** [[Semaphore详解]]

**参考答案**

Semaphore 是信号量，用来控制同时能有多少个线程访问某个资源，典型场景是限流和连接池。核心就是 AQS 的 state 字段，state 表示还剩几个许可证。获取叫 acquire：进来对 state 做 CAS 减一，减完大于等于 0 说明还有许可、直接通过，已经到 0 就阻塞排队。释放叫 release：state 加一，然后唤醒一个等待的线程。做限流时把许可设成 N，比如 100，就相当于同一时刻最多 100 个线程往下走，第 101 个必须等前面有人 release。连接池同理，池里有几个连接就设几个许可。它也是共享模式，因为多个线程可以同时拿到许可。

**追问参考答**

追问"和线程池限流有什么区别"：Semaphore 限的是正在执行的动作数，不管线程是谁；线程池限的是线程总数。所以 Semaphore 更适合保护下游资源，比如限制同时访问某个第三方接口的并发数。

### 31. CountDownLatch、CyclicBarrier、Semaphore 底层都基于 AQS 的什么模式？

> **知识点跳转：** [[AQS 详解]] · [[Semaphore详解]]

**参考答案**

这题有个坑，不能一句"共享模式"打包。CountDownLatch 和 Semaphore 确实基于 AQS 共享模式：CountDownLatch 用 state 当计数器，countDown 减到 0 后链式唤醒所有等待者；Semaphore 用 state 当许可证数，acquire 减一。它们的共同点是多个线程能同时被放行，正好对应共享模式。但 CyclicBarrier 不是 AQS 共享模式，它内部用的是 ReentrantLock 加 Condition，自己维护一个可重置的计数器，每次 await 减一、减到 0 就 signalAll。原因就是前者要"放行多个"，后者要"循环等齐还能重置"，语义不同，实现选型也不同。

**追问参考答**

一句话记：多个放行用共享模式，循环等齐用 ReentrantLock 加 Condition。答"三个都是共享模式"是典型错误，因为 CyclicBarrier 压根没走 AQS 的 tryAcquireShared。

---

## 七、线程池与阻塞队列（5 题）

### 32. 线程池的核心参数有哪些？各是什么含义？

> **知识点跳转：** [[线程池]]

**参考答案**

七个参数：corePoolSize 核心线程数、maximumPoolSize 最大线程数、keepAliveTime 空闲存活时间、unit 时间单位、workQueue 任务队列、threadFactory 线程工厂、handler 拒绝策略。核心线程是常驻的，非核心线程是 core 到 max 之间那部分，空闲超过 keepAliveTime 就销毁。任务进来的顺序要注意：先交给核心线程，核心满了不是马上建新线程，而是先扔进任务队列，队列也满了才创建非核心线程，非核心加满到 max 之后才走拒绝策略。这个顺序很多人答反。

**追问参考答**

线程数怎么设：CPU 密集型建议核心数加一，多一个能防止缺页中断之类的短暂停顿时 CPU 空闲；IO 密集型建议核心数乘二，更精确的公式是核心数乘以一加等待时间比计算时间，因为 IO 线程大部分时间在等、CPU 是闲的，多开线程能让 CPU 忙起来。注意这里算的是 corePoolSize，max 要按突发洪峰另外估。

### 33. 线程池提交一个任务后的执行流程是怎样的？

> **知识点跳转：** [[线程池]]

**参考答案**

四步，顺序是关键。第一步看核心线程，当前线程数小于 corePoolSize 就直接创建核心线程执行。第二步看任务队列，核心线程都忙了，就把任务放进 workQueue 排队。第三步看非核心线程，队列也满了才创建非核心线程，线程数可以一直加到 maximumPoolSize。第四步拒绝，连最大线程数都满了、队列也满，就执行 handler 拒绝策略。记法就一句：核心不满建核心、核心满入队、队列满建非核心、全满拒绝。最常见的错误是把入队和非核心线程的顺序搞反，以为核心满了就建线程。

**追问参考答**

每一步对应哪个参数：第一步 corePoolSize、第二步 workQueue、第三步 maximumPoolSize、第四步 handler。再补一个细节：非核心线程空闲超过 keepAliveTime 会被回收，但核心线程默认不回收，除非开 allowCoreThreadTimeOut。

### 34. 线程池的拒绝策略有哪几种？生产环境怎么选？

> **知识点跳转：** [[线程池]]

**参考答案**

四种。AbortPolicy 是默认的，直接抛 RejectedExecutionException，让调用方知道任务被拒了；CallerRunsPolicy 不抛也不丢，让提交任务的线程自己执行这个任务，比如主线程提交的被拒了，主线程就自己跑，跑的时候没法提交新任务，等于天然节流；DiscardPolicy 静默丢弃，调用方不知道；DiscardOldestPolicy 把队列里排最久的那个丢掉，把当前任务塞进去。生产上默认用 AbortPolicy，因为丢任务不可接受，必须让调用方感知；业务能接受延迟但不能丢的，用 CallerRunsPolicy 自动降速；两个 Discard 策略基本不用，静默丢任务风险太大。

**追问参考答**

CallerRunsPolicy 的降速原理值得多说一句：它不是限流器，而是靠"占用提交线程"来被动限流，效果是请求堆积在调用方，压力反压回上游。如果提交线程本身是不能阻塞的关键线程，这个策略反而会拖慢它，要谨慎。

### 35. 为什么不建议用 Executors 的便捷方法创建线程池？

> **知识点跳转：** [[线程池]]

**参考答案**

因为它们把参数写死了，把风险藏起来了。分两类看。newFixedThreadPool、newSingleThreadExecutor、newScheduledThreadPool 用的是无界队列，Fixed 和 Single 是 LinkedBlockingQueue，Scheduled 是 DelayedWorkQueue，同样无界；线程数被限住了，但任务会无限往队列里堆，高并发时队列膨胀到 OOM。newCachedThreadPool 反过来，队列是 SynchronousQueue 不缓存任务，最大线程数是 Integer.MAX_VALUE，来多少任务就建多少线程，线程数爆炸，把 CPU 和内存耗光。它是这批里唯一"无界线程数"的。所以生产必须手动 new ThreadPoolExecutor，队列设成有界、最大线程数和拒绝策略都显式指定，满了能走拒绝策略兜底，而不是默默 OOM。

**追问参考答**

别被 ScheduledThreadPool 的 max 等于 Integer.MAX_VALUE 骗了：它队列无界、永远不满，"队列满才建非核心"这条路根本走不到，线程数其实涨不过 corePoolSize。判定口诀：看工作队列有没有界，无界队列堆任务 OOM，只有 SynchronousQueue 这种零容量队列才会轮到无界线程爆炸。

### 36. 阻塞队列有哪些？ArrayBlockingQueue 和 LinkedBlockingQueue 的区别？

> **知识点跳转：** [[阻塞队列详解]]

**参考答案**

常见的五种：ArrayBlockingQueue、LinkedBlockingQueue、SynchronousQueue、PriorityBlockingQueue、DelayQueue。Array 和 Linked 的区别有几个维度。ArrayBlockingQueue 底层是数组，创建时必须指定容量、天然有界，用一把锁，put 和 take 共用，所以并发度低；但它内存连续、缓存友好、不创建节点，单次操作开销小。LinkedBlockingQueue 底层是链表，默认无界但构造时能指定容量变成有界，生产者和消费者各用一把锁，putLock 和 takeLock 分离，吞吐更高；代价是每次 put 要 new 节点，有额外内存开销、缓存不友好。线程池默认用的是 LinkedBlockingQueue，Executors.newFixedThreadPool 就是它，但生产不推荐无界，应该手动指定有界队列。

**追问参考答**

"LinkedBlockingQueue 无界"这个说法不准确，准确说法是默认无界、可指定容量变有界，这是它和 ArrayBlockingQueue 的关键差异之一。另外 SynchronousQueue 容量是零，不存任务，put 必须有线程正在 take 才成功，CachedThreadPool 用它配合无界线程数。

---

## 八、线程协作场景（2 题）

### 37. 用 wait/notify 实现生产者-消费者，为什么用 while 不能用 if？为什么用 notifyAll？

> **知识点跳转：** [[Java并发编程面试题]] · [[阻塞队列详解]]

**参考答案**

用 while 有两个原因。第一是 notifyAll 会唤醒所有等待线程，它们重新抢锁，第一个抢到的可能改了条件，比如取走了队列里唯一的数据，后面抢到锁的线程再一看条件已经不成立了，必须重新检查；用 if 的话被唤醒就直接往下执行，会造成越界或者超卖。第二是 JDK 文档明确写了存在虚假唤醒，操作系统层面可能无缘无故把线程唤醒，这时条件根本没变，用 if 就漏检了。必须用 notifyAll 的原因类似：notify 随机唤醒一个，可能唤醒的是同类角色，比如队列满了有多个生产者在等，消费者取走一个后 notify 又叫醒一个生产者，它看队列还满又 wait 回去，消费者也在等新数据，结果全员睡死。notifyAll 让所有等待者去竞争，谁条件满足谁执行。

**追问参考答**

加分项是能说出 ArrayBlockingQueue 的做法：它用一把 ReentrantLock 加两个 Condition，notEmpty 和 notFull，消费者只唤醒生产者、生产者只唤醒消费者，比 notifyAll 更精确，也不会误唤醒。能讲到这一层面试官就知道你看过源码。

### 38. 两线程交替打印 1-100，外层循环在锁外读取共享变量有什么问题？怎么修复？

> **知识点跳转：** [[内存屏障和happens-before]]

**参考答案**

问题在可见性。错误写法把 while count 小于等于 100 这个判断放在 synchronized 块外面，而 count 是普通变量不是 volatile，JMM 不保证线程每次都从主内存重新读它，编译器或 CPU 可能把它缓存到寄存器里。结果一个线程在锁内把 count 改到 101 了，另一个线程的外层循环读到的还是 100，继续进同步块，逻辑就乱了；更糟的是这个判断本身也不受锁保护，检查和使用之间有竞态。修复办法是把所有对 count 的读写都放进同一把锁里：外层用 while true，终止条件 if count 大于 100 放在锁内检查，wait 醒来之后还要再查一次，退出前 notifyAll 唤醒另一个可能还在等的线程。

**追问参考答**

为什么不直接把 count 改成 volatile？因为 volatile 只保证可见性和有序性，不能把"判断轮次、打印、递增、通知"这一整套动作变成原子的。这里本来就需要锁来协调两个线程交替，所以统一放在锁内更安全。核心原则一句话：共享变量如果有线程写，就要么用同一把锁保护所有读写，要么用明确适用的并发工具。

---

## 九、ThreadLocal（2 题）

### 39. ThreadLocal 的原理？为什么会有内存泄漏？怎么避免？

> **知识点跳转：** [[ThreadLocal 深度解析与内存泄露笔记]]

**参考答案**

先说结构：每个线程内部都维护一个 ThreadLocalMap，key 是 ThreadLocal 对象本身，value 是 set 进去的值。线程存取时拿的是当前线程自己的那个 Map，所以线程之间天然隔离、互不干扰。泄漏的根源在 Entry 的引用设计：key 对 ThreadLocal 是弱引用，value 是强引用。方法结束、ThreadLocal 的强引用断了之后，key 会被 GC 回收变成 null，但 value 还被 Entry 强引用着，成了 key 为 null、value 还在的死 Entry，回收不掉。更麻烦的是 Entry 又被 Thread 里的 ThreadLocalMap 强引用，只要线程活着，比如线程池里的线程几乎不死，这些 value 就一直占着内存。避免办法就是在 finally 里手动 remove，把当前 ThreadLocal 对应的整个 Entry 清掉，key 和 value 一起断。

**追问参考答**

key 为什么用弱引用？因为如果用强引用，业务代码里对 ThreadLocal 的引用断了之后，它还被 Map 的 key 强引用着，ThreadLocal 和 value 会双重泄漏。用弱引用至少保证 ThreadLocal 本身能回收，只漏 value。要说清楚泄漏的是 value 不是 ThreadLocal，这点容易被追问。另外 remove 只清当前这一个 Entry，至于清理所有 key 为 null 的 Entry，那是 ThreadLocalMap 的 expungeStaleEntries 在 get 和 set 时顺带做的。

### 40. 在线程池中使用 ThreadLocal 有什么坑？

> **知识点跳转：** [[ThreadLocal 深度解析与内存泄露笔记]] · [[线程池]]

**参考答案**

核心是线程复用带来的脏数据。线程池的线程执行完一个任务不会销毁，回到池子里等下个任务。如果任务 A 里 set 了 ThreadLocal 却没 remove，这个值就留在线程的 ThreadLocalMap 里；任务 B 恰好又分到这个线程，一读 ThreadLocal 就拿到了 A 留下的值，业务逻辑被污染，这就是串扰。避免办法还是每个任务用完在 finally 里 remove，保证下个任务来时 Map 是干净的。

**追问参考答**

串扰和泄漏是不是同一个根源？是同一个：都是"线程池线程长期存活，加上没 remove 导致 Entry 残留"。区别只在表现，串扰是下个任务读到旧值、属于业务错误；泄漏是 value 占着内存回收不掉。所以 remove 一次解决两个问题，线程池场景必须做。

---

## 十、并发容器与死锁（3 题）

### 41. ConcurrentHashMap 1.7 和 1.8 的实现区别？

> **知识点跳转：** [[ConcurrentHashMap_1.7_vs_1.8]]

**参考答案**

核心变化是锁粒度从段细化到桶，分四点说。第一是数据结构，1.7 是 Segment 加 HashEntry，一个 Segment 是一把小锁，默认 16 个，并发度上限就是 16、初始化后固定；1.8 去掉了 Segment，直接用一个大的 Node 数组，每个桶都能当锁的粒度，并发度没有固定上限，扩容后还能涨。第二是加锁方式，1.7 所有操作都要抢 Segment 锁，1.8 空桶时直接 CAS 无锁，只有桶里有冲突才用 synchronized 锁住这个桶，而 synchronized 本身有锁升级优化。第三是查询优化，1.8 引入树化，单桶链表长度超过 8 且数组容量至少 64 时转红黑树，查询从 O(N) 降到 O(logN)。第四是 size 的优化，1.7 靠读各 Segment 的 count 快照加 modCount 校验，冲突时得给所有 Segment 加锁重算，高并发下容易锁全表；1.8 用 LongAdder 的思想，base 加多个 cell，写只更新自己那个 cell，读的时候全部加起来，无锁高效。

**追问参考答**

为什么 1.8 性能更好，本质是锁粒度变小，从锁一段变成锁一个桶，竞争范围从整个 Segment 缩到一个哈希槽。回答时突出"空桶 CAS 无锁、冲突才锁单桶"这两句就抓住核心了。

### 42. CopyOnWriteArrayList 的原理？适用场景？

> **知识点跳转：** [[Java并发编程面试题]]

**参考答案**

原理是写时复制：读不加锁，直接读当前数组，所以读性能极高；写的时候加 ReentrantLock，把整个数组复制一份，在副本上修改，改完把底层那个 volatile 数组引用指向新副本。这样读者永远读的是不可变的旧数组，写者改的是新副本，读写互不干扰。有个关键点要讲透：写锁不是防读者的，读者读的是不可变快照天然安全；写锁防的是写写竞争，两个写线程如果不加锁会各自基于同一个旧数组复制、各自换引用，后换的把先换的覆盖掉，丢更新。适合读多写少的场景，比如黑名单、配置缓存、监听器列表；写多读少就不行，每次写都要复制整个数组，O(N) 成本，频繁写性能和内存都受不了；而且它是弱一致性的，读者可能读到旧数据。

**追问参考答**

和 Collections.synchronizedList 的区别：后者读也加锁，全串行；COW 读完全无锁，用空间和写成本换读性能。所以选型看读写比例，读远多于写才用 COW。

### 43. 死锁发生的四个必要条件是什么？怎么破坏？

> **知识点跳转：** [[Java并发编程面试题]] · [[ReentrantLock详解]]

**参考答案**

四个条件：互斥，一个资源同一时刻只能被一个线程占用；占有并等待，线程拿着一个资源的同时去请求另一个；不可剥夺，资源只能由持有者主动释放；循环等待，多个线程形成首尾相接的等待环。四个同时成立才可能死锁，破坏任意一个就能避免。破坏互斥很难，锁本来就是为互斥设计的；实践中主要破坏后三个：破坏占有并等待，要么一次性申请所有资源，要么拿不到就释放已持有的；破坏不可剥夺，用 tryLock 带超时，超时拿不到就放弃并释放已有的；破坏循环等待，给锁编号，所有线程按同一顺序加锁，这是工程上最常用的做法。

**追问参考答**

线上怎么排查：jstack 打印线程栈，JVM 会直接输出 "Found one Java-level deadlock" 和具体的线程、锁对象、互相等待关系；也可以用 jconsole 的检测死锁功能。定位到之后一般是调整加锁顺序，或者用 tryLock 加超时兜底。

---

## 关联知识点

- [[Java并发编程热门面试题-1]] — 上半部分：线程基础 / JMM / synchronized / volatile
- [[AQS 详解]] · [[ReentrantLock详解]] · [[ReentrantReadWriteLock 完整详解]] · [[Condition 详解]] — AQS 家族
- [[CountDownLatch详解]] · [[CyclicBarrier详解]] · [[Semaphore详解]] — 同步工具
- [[线程池]] · [[阻塞队列详解]] · [[DelayQueue详解]] — 线程池与队列
- [[ThreadLocal 深度解析与内存泄露笔记]] — 泄漏链路与线程池串扰
- [[ConcurrentHashMap_1.7_vs_1.8]] — 并发容器演进
- [[ABA 问题详解]] · [[AtomicLong 底层原理与高性能原因]] — CAS 家族
