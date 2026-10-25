---
tags:
  - java
  - 并发
  - 线程优先级
  - 面试
created: 2026-10-09
updated: 2026-10-09
source: 答疑会话（多线程下用什么设置优先级）
---

# 线程优先级（setPriority）

> 用 `Thread.setPriority(int)` 设置线程优先级，范围 **1~10**、默认 **5**。但**优先级只是给调度器的"建议"，不保证高优先级线程一定先执行**——所以不能用它来控制执行顺序。

---

## 一、怎么设置

```java
Thread t = new Thread(() -> System.out.println("运行"));
t.setPriority(Thread.MAX_PRIORITY);   // 10
t.start();
```

**范围 1~10**，对应三个常量：

| 常量 | 值 | 含义 |
|------|:--:|------|
| `Thread.MIN_PRIORITY` | 1 | 最低 |
| `Thread.NORM_PRIORITY` | 5 | 默认 |
| `Thread.MAX_PRIORITY` | 10 | 最高 |

- 读取当前优先级：`getPriority()`
- **新线程默认继承创建它的线程的优先级**
- `setPriority` 超出 1~10 会抛 `IllegalArgumentException`

---

## 二、为什么不保证生效（高频考点）

**Java 的线程优先级只映射到操作系统的线程优先级，而且映射关系由平台/JVM 决定：**

- **Linux 上**，Java 优先级被映射到 `nice` 值，效果很弱——很多场景下调度差异几乎可忽略（甚至在某些 JVM/内核组合里被"架空"）。
- **Windows** 上映射相对明显一些，但仍不保证。
- 结论：**优先级是"建议"，不是"命令"。** 它可能影响线程被调度的**概率**，但**不能**用来保证线程 A 一定先于线程 B 执行，也不能保证高优先级线程不会被饿死或插队。

**要控制执行顺序，应该用同步手段**，而不是优先级：

- `Thread.join()` —— 等某线程结束
- 锁（`synchronized` / `Lock`）
- 协调工具 `CountDownLatch`、`CyclicBarrier`、`Semaphore`
- 线程池按任务提交顺序 / 优先级队列（`PriorityBlockingQueue`）

---

## 三、在线程池里设置优先级

线程池不直接暴露"给某个线程设优先级"的接口，要在**线程工厂**里做：

```java
ThreadFactory factory = r -> {
    Thread t = new Thread(r);
    t.setPriority(Thread.MAX_PRIORITY);
    t.setName("my-pool-" + t.getId());
    return t;
};
ExecutorService pool = new Executors()... // 或 new ThreadPoolExecutor(..., factory);
```

> ⚠️ 想让**任务**按优先级执行，靠线程优先级没用——要用 `PriorityBlockingQueue` 之类的**优先级任务队列**。线程优先级管的是"哪个线程更容易被 CPU 选中"，不是"哪个任务先被执行"。

---

## 四、面试话术（口述版）

**问：Java 里怎么设置线程优先级？它能保证执行顺序吗？**

> 用 `Thread` 的 `setPriority` 方法，范围 1 到 10，对应 `MIN_PRIORITY`、`NORM_PRIORITY`、`MAX_PRIORITY` 三个常量，默认是 5，新线程默认继承创建它的线程的优先级。但重点在于——**优先级只是给操作系统调度器的一个建议，不保证高优先级的线程一定先执行**。因为 Java 的优先级最终要映射到操作系统的线程优先级，映射规则由平台和 JVM 决定；比如在 Linux 上它映射到 `nice`，效果很弱，很多情况下几乎可以忽略。所以**不能用优先级来控制执行顺序**，要保证顺序得用 `join`、锁或者 `CountDownLatch` 这类同步手段。在线程池里也一样，优先级要在自定义的线程工厂里设，而且它管的是"哪个线程更容易被调度"，不是"哪个任务先执行"——后者得用优先级队列。

---

## 关联知识点

- [[Java并发编程面试题]] —— 并发八股总览（线程状态、创建方式、同步）
- [[线程池]] —— 线程工厂、任务队列、`PriorityBlockingQueue`
- [[CountDownLatch详解]] · [[CyclicBarrier详解]] · [[Semaphore详解]] —— 真正能控制执行顺序的协调工具
