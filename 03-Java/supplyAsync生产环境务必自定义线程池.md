
> 秋招重点：supplyAsync 不指定线程池时使用 ForkJoinPool.commonPool()，在 CPU 密集型任务场景下容易和其他 ForkJoin 任务抢资源，生产上务必自定义线程池。


---

## 原文的问题

原文说"**CPU 密集型**场景下容易抢资源"，这个说法**不够准确，甚至有点反了**。

---

## 正确理解

### ForkJoinPool.commonPool() 的设计初衷

`commonPool` 的线程数默认是：

```
线程数 = Runtime.getRuntime().availableProcessors() - 1
// 比如 8核机器 → 7个线程
```

它是为 **CPU 密集型**任务设计的——线程数贴近 CPU 核数，避免过多线程切换，充分利用 CPU。

[[ForkJoinPool.commonPool() 共享问题深挖]]


---

### 真正危险的场景是 IO 密集型

```java
// 危险写法：IO 任务用 commonPool
CompletableFuture.supplyAsync(() -> {
    return httpClient.get("https://api.example.com/...");  // 可能阻塞 500ms
});
```

**问题链路：**

1. `commonPool` 只有 7 个线程（8 核机器）
2. 每个 IO 任务阻塞等待网络响应期间，线程被占着不释放
3. 7 个线程全部卡在 IO 等待 → 其他任务全部排队
4. **ForkJoin 框架本身的并行 Stream、`invoke` 等操作也共用这个池** → 整个应用卡死

---

### 两种场景对比

|场景|commonPool 是否适合|原因|
|---|---|---|
|CPU 密集型（加密、计算、压缩）|✅ 适合|线程数 ≈ 核数，刚好匹配，无需等待|
|IO 密集型（HTTP、DB、RPC）|❌ 危险|线程阻塞在 IO，池子很快耗尽|

---

### 生产上正确做法

```java
// IO 密集型：线程数要多，能覆盖等待时间
ExecutorService ioPool = new ThreadPoolExecutor(
    50, 200,
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),
    new ThreadFactoryBuilder().setNameFormat("async-io-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);

CompletableFuture.supplyAsync(() -> httpClient.get(url), ioPool);
```

CPU 密集型反而可以直接用 `commonPool`，或者用核数相近的小池：

```java
// CPU 密集型：线程数 = 核数 + 1 即可
ExecutorService cpuPool = new ThreadPoolExecutor(
    cores + 1, cores + 1, ...
);
```

---

## 总结一句话

> **`commonPool` 怕的是 IO 阻塞，不怕 CPU 计算。** 用它跑 HTTP/DB 调用才是真正的危险，CPU 计算反而是它的主场。

秋招如果面试官问"为什么不用 commonPool"，答 IO 阻塞会耗尽线程、影响全局 ForkJoin 任务，比答"CPU 抢资源"更准确。


