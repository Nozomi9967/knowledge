
## CompletableFuture 全面解析

CompletableFuture 是 Java 8 引入的异步编程核心工具，实现了 `Future` 和 `CompletionStage` 两个接口，解决了传统 `Future` 无法链式组合、无法注册回调、`get()` 阻塞等痛点。

先来一张整体结构图：

![[Pasted image 20260629165229.png]]

---
## 一、为什么需要 CompletableFuture

传统 `Future` 的缺陷：

- `get()` 强制阻塞，无法注册回调
- 多个 Future 无法方便地组合
- 没有异常处理链
- 无法手动完成（`complete()`）

---

## 二、创建方式

```java
// 有返回值，异步执行
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "hello");

// 无返回值
CompletableFuture<Void> f2 = CompletableFuture.runAsync(() -> System.out.println("run"));

// 指定线程池（生产环境推荐）
ExecutorService pool = Executors.newFixedThreadPool(10);
CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "hello", pool);

// 直接手动完成（包装同步结果）
CompletableFuture<String> f4 = CompletableFuture.completedFuture("done");
```

**秋招重点**：`supplyAsync` 不指定线程池时使用 `ForkJoinPool.commonPool()`，在 CPU 密集型任务场景下容易和其他 ForkJoin 任务抢资源，生产上务必自定义线程池。


[[supplyAsync生产环境务必自定义线程池]]

---

## 三、核心 API

接下来展示三大类 API 的全貌和区别：
![[Pasted image 20260629165311.png]]

[[Function 和 Consumer（函数式接口）]]

---

## 四、典型代码示例

**① 链式调用**

```java
CompletableFuture.supplyAsync(() -> queryUserFromDB(userId))   // 异步查 DB
    .thenApplyAsync(user -> enrichWithCache(user))             // 异步加工
    .thenAccept(user -> updateResponse(user))                  // 消费结果
    .exceptionally(ex -> { log.error("failed", ex); return null; });
```


> **那 `thenApplyAsync` 里的 `Async` 到底指的是什么？**

既然都要等第一步算完，那加不加 `Async` 有什么区别？

- **如果不加 Async (`thenApply`)：** 当第一步的线程 A 查完数据库后，它发现后面还有个任务，于是线程 A **顺便、亲自**把第二步（查缓存）的代码也给执行了。
    
- **如果加了 Async (`thenApplyAsync`)：** 当第一步的线程 A 查完数据库后，它不亲自干第二步的活，而是**把执行通知和数据打包成一个新任务，重新扔进线程池**。随后，线程池里的线程 B 慢悠悠地领到这个任务，开始执行第二步。
    

 **总结它们的区别：**

- **`thenApply`**：第一步做完，**同一个线程**接着做第二步（串行且不换线程）。
    
- **`thenApplyAsync`**：第一步做完，把第二步**派发到新线程**去做（串行，但换线程）。



**② thenCompose vs thenApply 对比（高频考点）**

```java
// thenApply：返回 CF<CF<String>>，需要两次 get
CompletableFuture<CompletableFuture<String>> nested =
    CompletableFuture.supplyAsync(() -> "hello")
                     .thenApply(s -> CompletableFuture.supplyAsync(() -> s + " world"));

// thenCompose：扁平化为 CF<String>，推荐
CompletableFuture<String> flat =
    CompletableFuture.supplyAsync(() -> "hello")
                     .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + " world"));
```

**③ allOf 并行聚合（实际业务最常用）**

```java
CompletableFuture<User>    cfUser    = CompletableFuture.supplyAsync(() -> getUser(id));
CompletableFuture<Order>   cfOrder   = CompletableFuture.supplyAsync(() -> getOrder(id));
CompletableFuture<Coupon>  cfCoupon  = CompletableFuture.supplyAsync(() -> getCoupon(id));

// 等三个请求全部完成
CompletableFuture.allOf(cfUser, cfOrder, cfCoupon).join();

User   user   = cfUser.join();   // join() 不抛 checked exception，比 get() 更干净
Order  order  = cfOrder.join();
Coupon coupon = cfCoupon.join();
```

>`join`的作用？


`join()` 的作用就是**把“异步”变成“同步”，确保你在用数据之前，数据已经真正拿到了。**

1. `CompletableFuture.allOf(...).join();`

**作用：栅栏机制（等待所有人到齐）**

- `CompletableFuture.allOf(cfUser, cfOrder, cfCoupon)` 本身会立即返回一个新的 `CompletableFuture<Void>`。这个新的 CF 就像一个“计数器”或“栅栏”，只有当里面的三个异步任务（查用户、查订单、查优惠券）**全部执行完毕**时，它才会完成。
    
- 后面紧跟的 `.join()`，就是让主线程**停在这一行死等**，直到这三个后台任务全部结束。
    
- **为什么要这一行？** 如果没有这一行，主线程会直接往下走。此时后台线程可能还在查数据库，你后面的代码就会去拿一个还没准备好的数据。


2. `cfUser.join();`（以及后面的 order 和 coupon）

**作用：提取最终的计算结果**

- 虽然前面的 `allOf(...).join()` 已经保证了任务都做完了，但 `allOf` 本身是不返回任何业务数据的（它的返回值是 `Void`）。
    
- 为了真正拿到后台线程查出来的 `User`、`Order` 和 `Coupon` 对象，你需要调用具体任务的 `.join()`。
    
- **因为前面已经确保任务完成了，所以这里的 `.join()` 不会发生任何阻塞**，它会瞬间把后台线程计算好的值直接“吐”给左边的变量。

>为什么join能取到值

`join()` 之所以能够取到值，是因为 `CompletableFuture` 内部实现了一套**状态机机制（State Machine）**和**线程等待/唤醒队列**。

当我们调用 `CompletableFuture.supplyAsync(() -> getUser(id))` 时，Java 已经在底层为你做了一系列精妙的安排。它能取到值的核心原因可以拆解为以下三个核心步骤：

1. 结果的“容器”属性（内部变量 result）

`CompletableFuture` 本质上是一个**包装盒**。它的内部有一个用来存放最终结果的成员变量（在 JDK 源码中通常叫做 `result`）。

- **初始状态**：当你刚创建 `cfUser` 时，这个 `result` 变量是 `null`，代表任务还在执行，结果还没出来。
    
- **完成状态**：当后台线程把 `getUser(id)` 执行完毕后，它会调用一个内部方法（如 `completeValue()`），把计算出来的 `User` 对象赋值给这个 `result` 变量。
    


---

2. `join()` 的阻塞与唤醒机制（如果还没做完）

如果你在后台线程**还没执行完**的时候就调用了 `cfUser.join()`，会发生什么？

1. **检查状态**：`join()` 首先去检查内部的 `result` 变量是否已经有值。
    
2. **入队等待**：如果发现还是 `null`，当前主线程就不会再往下走，而是会被封装成一个等待节点（Completion Node），放入 `CompletableFuture` 内部的**等待队列**中。
    
3. **线程挂起**：主线程会利用类似 `LockSupport.park()` 的底层机制进入**阻塞（等待）状态**，让出 CPU 资源。
    
4. **后台唤醒**：当后台线程终于把 `getUser(id)` 算完了，它在把结果存入 `result` 的同时，会去检查那个等待队列，发现主线程在等它，就会调用 `LockSupport.unpark()` 把主线程**唤醒**。


---

 3. 直接读取内存值（如果已经做完）

在你的代码场景中，情况更加简单高效：

Java

```
CompletableFuture.allOf(cfUser, cfOrder, cfCoupon).join(); // 这一步已经卡死，直到所有任务完成
User user = cfUser.join(); // 此时再调用 join()
```

因为前面的 `allOf(...).join()` 已经扮演了“拦路虎”的角色，**确保了三个后台任务绝对已经全部执行完毕**。

所以，当你执行到 `cfUser.join()` 时：

1. `join()` 内部去检查 `result` 变量。
    
2. 发现 `result` 已经不是 `null` 了（里面已经躺着后台线程存进去的 `User` 对象）。
    
3. **完全不发生任何阻塞**，它直接通过内存屏障（保证可见性）读取这个 `result` 变量的值，并将其返回给你的 `User user` 变量。
    

 **总结**

`join()` 能取到值，是因为：

- 异步线程执行完后，把值存进了 `CompletableFuture` 对象的**内部属性**里。
    
- `join()` 的职责就是去读这个属性。如果值还没到，它就**等**值来；如果值已经到了（比如被 `allOf` 截获之后），它就**直接拿**出来给你。





**④ 异常处理三种方式对比**

```java
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
    if (new Random().nextBoolean()) throw new RuntimeException("oops");
    return "ok";
});

// 方式1：exceptionally - 兜底默认值
cf.exceptionally(ex -> "default");

// 方式2：handle - 最灵活，同时拿到结果和异常
cf.handle((result, ex) -> ex != null ? "fallback" : result.toUpperCase());

// 方式3：whenComplete - 类似 finally，不改变返回值
cf.whenComplete((result, ex) -> {
    if (ex != null) log.error("error", ex);
});
```

---

## 六、高频面试考点

**1. `thenApply` vs `thenApplyAsync` 的线程模型**

`thenApply` 的执行线程取决于注册时上游是否已完成：

| 场景 | 执行线程 | 原因 |
|------|----------|------|
| 上游**还没完** | **上游线程**（线程池 worker / 调 `complete()` 的线程） | 注册时上游未完成，回调被暂存，上游完成时顺手带执行 |
| 上游**已完了** | **注册线程**（调用 `.thenApply()` 的那个线程） | 注册时上游已完成，当前线程直接原地执行 |

```java
// 场景 1：上游还没完 → thenApply 跑在线程 B（上游线程）
CompletableFuture.supplyAsync(() -> {
    // 线程 B（线程池 worker）
    return "hello";
}).thenApply(s -> {
    // 线程 B，上游完成后顺手执行
    return s.toUpperCase();
});

// 场景 2：上游已完了 → thenApply 跑在线程 A（注册线程）
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "hello");
Thread.sleep(1000); // 确保上游已完成
cf.thenApply(s -> {
    // 线程 A（main），当前线程原地执行
    return s.toUpperCase();
});
```

`thenApplyAsync` 强制提交到线程池，不受上游完成状态影响，行为 100% 可预期。

**2. `thenCompose` vs `thenCombine`**

- `thenCompose`：串行依赖，后一个 CF 依赖前一个的结果
- `thenCombine`：并行独立，两个 CF 同时跑，都完成后合并

**3. `allOf` 拿不到各子任务结果**

`allOf` 返回 `CF<Void>`，要拿各子任务结果必须在 `join()` 之后单独调用每个 CF 的 `join()`。

**4. 异常传播机制**

链中一旦某个节点抛出异常，后续的 `thenApply` / `thenAccept` 都会跳过，直到遇到 `exceptionally` 或 `handle` 才被处理。这与 RxJava 的 `onError` 语义类似。

**5. 手动完成**

```java
CompletableFuture<String> cf = new CompletableFuture<>();
// 另一个线程手动触发完成
cf.complete("result");
cf.completeExceptionally(new RuntimeException("failed"));
cf.cancel(true);  // 也可取消
```

---

## 七、实际项目场景（nozomi-mall / nozomi-house 对照）

在你的项目里，CompletableFuture 典型应用场景：

- **商品详情页聚合**：并行调用商品服务、库存服务、评论服务，`allOf` 等全部完成后组装响应，接口耗时从串行的 300ms 降到并行的 ~100ms
- **Canal 消息处理**：消费 binlog 后异步写 ES，不阻塞主线程
- **Finance 服务账单生成**：异步查多个子账户再合并，`thenCombine` 链接两个来源

核心原则：**IO 密集型场景一定要自定义线程池**，不要用 `commonPool()`，否则和 ForkJoin 的其他 CPU 任务互相干扰。