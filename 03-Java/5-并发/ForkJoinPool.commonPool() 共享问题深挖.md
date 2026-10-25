---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---

### 哪些东西共用 commonPool

```java
// 以下三者在 JVM 内共用同一个 commonPool

// 1. 并行 Stream
list.parallelStream().map(this::heavyCompute).collect(toList());

// 2. CompletableFuture（不指定线程池）
CompletableFuture.supplyAsync(() -> callRemoteAPI());

// 3. ForkJoinTask 直接提交
ForkJoinPool.commonPool().submit(new MyRecursiveTask());
```

同一个 JVM 进程里，这三者**争的是同一批线程**。

---

### 具体会发生什么

假设 8 核机器，`commonPool` 有 7 个线程：

```
时间线：

t=0   parallelStream 开始处理 10 万条数据，占用全部 7 个线程

t=50ms  某个请求进来，触发 CompletableFuture.supplyAsync(...)
        → 提交到 commonPool
        → 7 个线程全忙
        → 任务进队列等待

t=200ms parallelStream 还没跑完
        → supplyAsync 的任务还在队列里没执行
        → 请求超时
```

更危险的场景是**IO 阻塞叠加**：

```
t=0   7个 supplyAsync 任务全部在等 HTTP 响应（各自阻塞 500ms）
      → commonPool 7个线程全部挂起

t=1ms 业务代码调用 list.parallelStream()...
      → 提交到 commonPool
      → 没有可用线程
      → parallelStream 退化为串行执行（主线程自己跑）
      → 原本 100ms 的批处理变成 700ms
```

---

### 为什么 parallelStream 会退化为串行

这是 ForkJoin 的一个细节，值得单独说清楚。

`parallelStream` 底层调用的是 `ForkJoinPool.commonPool().invoke(task)`。

ForkJoin 的任务拆分依赖**工作窃取（work-stealing）**：

```
正常情况：
  主线程 submit task
  → task 被拆成 subtask1, subtask2, subtask3...
  → 各工作线程从队列里窃取 subtask 并行执行

commonPool 全忙时：
  主线程 submit task
  → 没有工作线程可以窃取
  → 主线程自己调用 task.join()，同步等待
  → 实际上主线程在串行执行所有 subtask
```

源码层面，`ForkJoinPool.invoke()` 里有这样的逻辑：

```java
// 简化示意
public <T> T invoke(ForkJoinTask<T> task) {
    // 如果当前线程本身是 ForkJoin 工作线程 → 直接 fork
    // 否则（普通线程）→ externalSubmit，然后 task.join() 阻塞等待
    // join() 内部会尝试 helpComplete，即主线程自己去执行子任务
}
```

结果就是：**没有工作线程时，并行任务由提交线程（通常是业务主线程）串行兜底执行**。

---

### 验证代码

可以在本地跑一下感受差异：

```java
public static void main(String[] args) throws Exception {
    // 先用 supplyAsync 把 commonPool 线程全部占满（模拟 IO 阻塞）
    List<CompletableFuture<Void>> blockers = new ArrayList<>();
    for (int i = 0; i < Runtime.getRuntime().availableProcessors() - 1; i++) {
        blockers.add(CompletableFuture.runAsync(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) {}
        }));
    }

    Thread.sleep(100); // 确保上面的任务已经占住线程

    // 此时跑 parallelStream，观察是否退化为串行
    long start = System.currentTimeMillis();
    IntStream.range(0, 8).parallel().forEach(i -> {
        System.out.println(Thread.currentThread().getName() + " handling " + i);
        try { Thread.sleep(200); } catch (InterruptedException e) {}
    });
    System.out.println("elapsed: " + (System.currentTimeMillis() - start) + "ms");
    // commonPool 空闲时约 200ms，被占满时约 1600ms（串行）
}
```

---

### 实际项目怎么隔离

Spring Boot 项目里常见的做法是给不同任务类型分配独立线程池，互不干扰：

```java
@Configuration
public class AsyncConfig {

    // IO 密集型：给 CompletableFuture / RPC 调用用
    @Bean("ioExecutor")
    public Executor ioExecutor() {
        return new ThreadPoolExecutor(
            50, 200, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("io-async-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // CPU 密集型：给 parallelStream 或计算任务用（独立 ForkJoinPool）
    @Bean("cpuPool")
    public ForkJoinPool cpuPool() {
        return new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }
}

// 使用独立 ForkJoinPool 跑 parallelStream，不污染 commonPool
cpuPool.submit(() ->
    list.parallelStream().map(this::compute).collect(toList())
).get();
```

**关键点**：给 `parallelStream` 也要用独立 `ForkJoinPool` 包裹，直接调用 `parallelStream()` 永远走 `commonPool`，没有别的办法绕过。

---

### 一句话总结

> `commonPool` 是 JVM 级别的全局共享资源，`parallelStream`、`CompletableFuture`、`ForkJoinTask` 三者共用。任何一方长时间占用（尤其是 IO 阻塞），其他两方都会受影响——轻则延迟上升，重则 `parallelStream` 退化为串行。生产上应当用独立线程池彻底隔离。