---
tags:
  - java
created: 2026-06-28
updated: 2026-06-28
---
# Java并发-并发安全（试题）

> 答题说明：以下均为简答题，请尽量用自己的话阐述原理，回答完成后发给我评估。
> 范围：[[Java并发编程面试题]] 第二大节「## 并发安全」（第 1082 行起，至「## 线程池」之前）。

---

## 一、JUC 工具类与并发容器

1. JUC（`java.util.concurrent`）包下你常用的类有哪些？请按"线程池 / 并发集合 / 同步工具 / 原子类"四个分类各举 2-3 个，并分别说明其典型使用场景。

线程池有Executors、threadPoolExecutor
并发集合有CopyOnWriteArrayList、
同步工具有Lock、Condition、CountDownLatch、CyclicBarrier、Semapore
原子类有AtomicInteger、AtomicReference

2. CountDownLatch 的核心实现原理是什么？它和 CyclicBarrier 都能"让一组线程协调"，请从"是否可重用 / 是否要求主线程等待"两个维度对比。

核心实现原理是倒计数，线程调用countDown会使得state-1，await会阻塞等待倒计数为0
它和CyclicBarrier的目的都是使得一个或多个线程等待一组线程完成任务后继续执行
不同的是CylicBarrier可以重用

3. Semaphore 的内部是基于 AQS 的共享模式还是独占模式实现的？请说明它和 ReentrantLock 在实现层面最本质的区别。



4. ConcurrentHashMap 在 JDK 1.7 和 JDK 1.8 的实现有什么本质不同？为什么 1.8 之后要废弃分段锁？

在1.7底层实现是分段锁，1.8改为了CAS+锁桶头节点

5. CopyOnWriteArrayList 适用于什么场景？它的"写时复制"会带来什么副作用？为什么不适合写多的场景？

---

## 二、synchronized 体系

6. synchronized 关键字的底层实现原理是什么？请从"对象头 Mark Word / monitorenter 与 monitorexit 字节码指令 / Monitor 对象（含 _owner、_entryList、_waitSet）"三方面说明。

7. 什么是"synchronized 锁升级"？请按"无锁→偏向锁→轻量级锁→重量级锁"的顺序，说明每种锁的核心特征、Mark Word 标志位、适用场景。

8. JDK 15 之后偏向锁被默认废弃（JEP 374），这背后的原因是什么？废弃后 synchronized 的实际升级路径变成什么？

9. JVM 对 synchronized 做了哪些优化？请至少说出四种（锁升级 / 锁消除 / 锁粗化 / 自适应自旋），并分别说明触发条件与效果。

10. synchronized 是可重入锁吗？可重入语义在"轻量级锁"和"重量级锁"两个阶段分别是如何实现的？

11. synchronized 修饰普通方法和静态方法的区别是什么？请从"锁的对象"和"作用范围"两个维度对比，并说明多实例场景下的差异。

---

## 三、ReentrantLock 与 AQS

12. synchronized 和 ReentrantLock 的核心区别是什么？请从"用法 / 特性（公平锁/可中断/超时/多条件变量）/ 性能"三个维度对比。

13. AQS 的三要素是什么？请说明 `state` 这个 volatile int 变量，在 ReentrantLock / Semaphore / CountDownLatch 中分别代表什么含义。

14. AQS 用的是哪种设计模式？为什么把"state 语义"和"获取/释放是否成功"留空给子类？子类需要重写哪些核心方法？

15. ReentrantLock 是怎么实现"公平锁"的？请对比公平锁和非公平锁在 `tryAcquire` 实现上的关键差异（一行代码级别），并解释为什么非公平锁吞吐量更大。

16. 什么是"非公平锁的插队"？插队一定会提升吞吐量吗？什么场景下插队会导致线程饥饿？`tryLock()` 是否遵守公平性？

---

## 四、CAS 与原子类

17. 悲观锁和乐观锁的核心思想分别是什么？synchronized 属于哪一类？`AtomicInteger.incrementAndGet()` 属于哪一类？

18. CAS 的三大缺点分别是什么？Java 是怎么解决"只能保证单个共享变量原子操作"这个缺点的？

19. 什么是 CAS 的 ABA 问题？请用一个具体场景说明 ABA 为什么会引发 bug，以及 Java 用什么类解决（写 API 名 + 核心字段）。

20. 为什么不能"所有锁都用 CAS"？请从"自旋开销"和"应用场景"两个角度说明。

---

## 五、volatile 与内存可见性

21. volatile 关键字的两个作用是什么？请从"可见性"和"禁止指令重排序"两个角度说明，并用 4 种内存屏障（LoadLoad / StoreStore / LoadStore / StoreLoad）说明 volatile 的实现原理。

22. volatile 能保证线程安全吗？为什么 `i++` 这种复合操作即使加了 volatile 也不安全？应该用 `AtomicInteger` 还是 `synchronized`？

23. volatile 和 synchronized 的本质区别是什么？请从"原子性 / 可见性 / 有序性"三个维度对比。

---

## 六、ThreadLocal 与死锁

24. ThreadLocal 的实现原理是什么？它的"key"和"value"在 ThreadLocalMap 中分别是强引用还是弱引用？这种设计会导致什么内存泄漏问题？为什么 key 一定要设计成弱引用？

25. 死锁发生的四个必要条件是什么？请各举一个反例说明"破坏任意一个条件即可避免死锁"，并重点说明"资源有序分配法"为什么能解决大部分死锁问题。

---

> 关联：[[Java并发编程面试题]] · [[线程池]] · [[ReentrantLock详解]] · [[AQS 详解]] · [[CountDownLatch详解]] · [[CyclicBarrier详解]] · [[阻塞队列详解]] · [[Condition 详解]] · [[Semaphore详解]]
