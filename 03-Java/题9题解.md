---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---
这是一个典型的微服务聚合网关场景。在处理这种高并发、需要强隔离的场景时，合理组合 `CompletableFuture` 的 API 能够非常优雅地解决问题。

下面直接为你上干货：先给核心代码骨架，再拆解面试核心考点。

## 核心代码骨架

在实际生产中，调用下游服务**必须使用自定义线程池**，绝对不能用默认的 `ForkJoinPool.commonPool()`（会互相挤占线程）。同时，Java 9+ 引入的 `orTimeout` 是处理超时的利器。


```java
import java.util.concurrent.*;

public class GoodsDetailService {

    // 1. 必须根据业务场景定制线程池（核心、最大、等待队列、拒绝策略）
    private final ExecutorService executor = new ThreadPoolExecutor(
            10, 30, 1L, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public GoodsDetailVO getGoodsDetail(Long goodsId) {
        // 2. 定义整体的超时时间
        long timeout = 1500; 

        // 3. 并行异步调用三个下游服务
        CompletableFuture<String> goodsFuture = CompletableFuture
                .supplyAsync(() -> queryGoods(goodsId), executor);

        CompletableFuture<String> stockFuture = CompletableFuture
                .supplyAsync(() -> queryStock(goodsId), executor);

        CompletableFuture<String> marketingFuture = CompletableFuture
                .supplyAsync(() -> queryMarketing(goodsId), executor);

        try {
            // 4. 使用 allOf 编排，并通过 orTimeout 限制整体最大忍受时间
            CompletableFuture.allOf(goodsFuture, stockFuture, marketingFuture)
                    .orTimeout(timeout, TimeUnit.MILLISECONDS)
                    .join(); // 阻塞等待全部完成或超时

            // 5. 组装正常结果
            return new GoodsDetailVO(goodsFuture.join(), stockFuture.join(), marketingFuture.join());

        } catch (CompletionException e) {
            // 6. 异常处理：无论是任意服务报错，还是 orTimeout 触发的 TimeoutException，都会包装在 CompletionException 中
            System.err.println("【降级触发】下游调用失败或超时: " + e.getCause().getMessage());
            return getFallbackResult();
        }
    }

    // --- 模拟下游接口 ---
    private String queryGoods(Long id) { return "商品基本信息"; }
    private String queryStock(Long id) { return "库存信息"; }
    private String queryMarketing(Long id) { return "营销活动信息"; }

    // --- 降级兜底方案 ---
    private GoodsDetailVO getFallbackResult() {
        return new GoodsDetailVO("标准商品", "有货", "暂无优惠（降级数据）");
    }
}
```

## 面试必考：关键 API 用法与辨析

### 1. `allOf`

- **用法**：`public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs)`
    
- **逻辑**：**“全对才对”**。传入的多个 Future **全部完成**时，返回的 `CompletableFuture<Void>` 才会完成。
    
- **注意点**：
    
    - 它的返回值是 `Void`，不包含任何结果。想拿结果，必须等它完成后，去原来的各个 future 上调用 `join()` 或 `get()`。
        
    - 如果其中任意一个 future 抛出异常，`allOf` 组合后的 future 也会立刻感知到异常，但**它不会主动去取消（cancel）其他还在运行的异步任务**。
        

### 2. `anyOf`

- **用法**：`public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs)`
    
- **逻辑**：**“一成即成”**。传入的多个 Future 中，**只要有任意一个**执行完成（无论是正常结束还是异常结束），返回的 Future 就会立刻结束。
    
- **适用场景**：
    
    - 主备双发：同时向两个镜像服务器请求相同的数据，谁先返回就用谁的。
        
    - **不适用于本题**：本题需要聚合商品、库存、营销**三个全量数据**，用 `anyOf` 会导致只拿到一个结果就返回了。
        

### 3. `orTimeout`（Java 9 引入）

- **用法**：`public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit)`
    
- **逻辑**：如果在指定的超时时间内，当前的 Future 还没有完成，它就会强制让该 Future 以 `TimeoutException` 异常结束。
    
- **结合本题的妙处**：
    
    - 在 Java 8 中，我们必须用 `CompletableFuture.get(timeout, unit)` 来做超时，但这是一种**同步阻塞**的外围控制。
        
    - Java 9 的 `orTimeout` 则是**链式异步编排**。在 `allOf(...)` 后面直接挂上 `.orTimeout(...)`，意味着给这组联合任务整体加了一个“定时炸弹”。一旦时间到，整体直接抛出 `TimeoutException` 进入 `catch` 块，完美实现**任意一个超时，整体接口降级**的需求。