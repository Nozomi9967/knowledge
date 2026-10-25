---
tags:
  - java
created: 2026-07-02
updated: 2026-07-31
---

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

==thenApplyAsync避免阻塞主线程==

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

---
# 🎯 面试题 · 2026-07-13 20:46（基于《CompletableFuture》）
> [!question]- 面试题 1：CompletableFuture 的核心作用是什么？相比传统 Future 解决了哪些痛点？
> CompletableFuture 是 Java 8 引入的异步编程核心工具，同时实现了 `Future` 和 `CompletionStage` 两个接口，核心是为了解决传统 Future 在异步场景下的几大痛点：第一，传统 Future 的 `get()` 方法会强制阻塞调用线程，无法注册回调；第二，多个 Future 之间无法方便地进行链式组合，需要手动处理依赖关系；第三，传统 Future 没有内置的异常处理链，异步任务抛出的异常很难被捕获和处理；第四，传统 Future 不支持手动完成（`complete`）操作，无法包装同步结果或者主动标记任务完成状态。CompletableFuture 通过链式 API、回调注册、内置异常处理等能力，大幅提升了异步编程的效率和可维护性。

> [!question]- 面试题 2：使用 `CompletableFuture.supplyAsync()` 创建异步任务时，不指定线程池和传入自定义线程池有什么区别？为什么生产环境推荐自定义线程池？
> 如果不传入自定义线程池，`supplyAsync` 会默认使用 `ForkJoinPool.commonPool()` 作为执行线程池；如果传入自定义的 `ExecutorService`，异步任务就会提交到自定义线程池中执行。生产环境不推荐使用默认 `commonPool` 的核心原因是：在 CPU 密集型任务场景下，`commonPool` 会和其他使用 ForkJoin 框架的任务（比如并行流 Stream 的操作）抢占 CPU 资源，导致任务执行效率下降，甚至出现饥饿问题。如果是 IO 密集型场景混用，也会互相干扰线程资源，所以生产环境无论什么场景，都建议自定义线程池，隔离资源避免竞争。

> [!question]- 面试题 3：`thenApply` 和 `thenApplyAsync` 的核心区别是什么？它们的线程执行行为有什么差异？
> 两者的核心区别在于第二步任务的线程分配逻辑不同。`thenApply` 是同步非异步的执行方式：当上游任务完成后，如果上游执行线程还在，会直接由上游线程继续执行第二步任务，整个过程串行且不会切换线程；而 `thenApplyAsync` 是异步执行方式：上游任务完成后，不会由上游线程直接执行第二步，而是把第二步任务封装成新的任务提交到线程池，由线程池中的其他线程执行，会切换线程。举个例子，如果第一步是异步查数据库，用 `thenApply` 的话，查完数据库的线程会直接继续执行查缓存的逻辑；用 `thenApplyAsync` 的话，查完数据库的线程会把查缓存的任务扔给线程池，自己直接释放，由其他线程来处理查缓存的逻辑。另外 `thenApplyAsync` 的线程行为是 100% 可预期的，因为它强制走线程池，不受上游是否完成的影响，适合对线程隔离有要求的场景。

> [!question]- 面试题 4：`thenCompose` 和 `thenCombine` 的核心区别是什么？分别适用于什么业务场景？
> 两者的核心区别在于任务之间的依赖关系和并行逻辑不同。`thenCompose` 用于串行依赖的场景：后一个异步任务依赖前一个异步任务的计算结果，相当于把两个有依赖的 CompletableFuture 扁平化合并成一个，避免出现嵌套的 CompletableFuture。比如先异步查询用户信息，再根据用户ID异步查询用户的订单列表，订单查询依赖用户查询的结果，这种场景就用 `thenCompose`。而 `thenCombine` 用于并行独立的场景：两个异步任务之间没有依赖，可以同时启动执行，等两个任务都完成后再把结果合并，比如同时异步查询用户的基础信息和订单的统计信息，两者互不依赖，跑完后合并成完整的页面响应数据，这种场景就用 `thenCombine`。

> [!question]- 面试题 5：在需要并行调用多个独立接口获取数据后聚合的业务场景中，如何使用 CompletableFuture 实现？为什么 `CompletableFuture.allOf()` 之后还需要单独调用每个子任务的 `join()` 方法？另外 CompletableFuture 链式调用中的异常是如何传播的？
> 首先我们可以分别用 `supplyAsync` 异步启动多个独立的查询任务，每个任务对应一个接口的调用，然后把所有子任务的 CompletableFuture 传给 `allOf` 方法，`allOf` 会返回一个代表所有任务都完成的 `CompletableFuture<Void>`，调用它的 `join()` 方法可以阻塞主线程直到所有任务全部执行完毕。之后还需要单独调用每个子任务的 `join()` 方法，核心原因是 `allOf` 本身的返回值是 `Void`，它只负责等待所有任务完成，不携带任何业务数据，所以我们需要通过每个子任务自己的 `join()` 方法来获取它们各自的计算结果。另外因为前面 `allOf` 的 `join()` 已经保证了所有任务都执行完成，所以这里的 `join()` 不会发生阻塞，会直接从 CompletableFuture 内部存储结果的成员变量中直接读取已经计算好的值返回。
> 关于异常传播：链式调用中一旦某个节点抛出异常，后续的 `thenApply`、`thenAccept` 等普通回调节点会被直接跳过，异常会一直向后传递，直到遇到专门处理异常的回调才会被消费。常用的处理方式有三种：`exceptionally` 相当于 catch 块，用来提供兜底默认值，只能拿到异常不能拿正常结果；`handle` 最灵活，同时能拿到正常结果和异常，可以根据异常是否存在做不同处理；`whenComplete` 类似 finally 块，不管是否异常都会执行，不能改变链的返回值，一般用来做日志打印之类的收尾工作。