---
tags:
  - spring
  - springboot
  - 异步
  - async
  - aop
created: 2026-10-25
updated: 2026-10-25
source: tabbit 剪藏（@Async 答疑）+ Spring 官方文档
---

# @Async 异步执行详解

> 本质一句话：**@Async = AOP 代理 + 线程池**。注解本身只是元数据，真正干活的是 `AsyncExecutionInterceptor` 把方法调用丢进 `TaskExecutor`。
> 相关：[[@Transactional 类上 vs 方法上]] · [[题21-Async异常与ThreadLocal传递]] · [[@Configuration 的 CGLIB 原地增强]]

---

## 一、它是什么，怎么工作

`@Async` 是 Spring 提供的注解，作用是让被标注的方法**在独立线程里执行**，调用方不必等它返回就能继续往下走。

使用时必须在配置类上加 `@EnableAsync` 开启异步支持——不加的话注解完全无效，方法照样同步执行，这是最常见的"配了没生效"原因。

工作机制分三层：

**第一层，注解只是元数据。** 它不会改变方法的业务逻辑，行为由解析方触发。`@Async` 的 Retention 是 `RUNTIME`，Spring 启动时通过反射读到它。

**第二层，生成代理。** Spring 为带 `@Async` 的 Bean 生成代理对象（JDK 动态代理或 CGLIB），调用方拿到的其实是代理，不是原始对象。代理在方法调用前后插入异步逻辑。

**第三层，交给线程池。** 代理把任务包装成 `Runnable` 提交给 `TaskExecutor`，立即返回。底层由 `AsyncAnnotationAdvisor`（切面定义）和 `AsyncExecutionInterceptor`（拦截器）配合完成。

```java
@Configuration
@EnableAsync                      // 必须加，否则 @Async 形同虚设
public class AsyncConfig { }

@Service
public class OrderService {
    @Async                        // 不指定线程池，用容器里的默认执行器
    public void sendSms(String phone) {
        // 在独立线程执行
    }
}
```

---

## 二、返回值决定调用方能拿到什么

| 返回类型 | 调用方拿到的 | 异常怎么处理 |
|---|---|---|
| `void` | 什么都拿不到 | 只能靠 `AsyncUncaughtExceptionHandler` 兜底 |
| `Future<T>` | `Future`，可 `get()` 阻塞等待 | 异常包装在 Future 里，`get()` 时抛 `ExecutionException` |
| `CompletableFuture<T>` | `CompletableFuture`，可回调组合 | 同上，也可用 `exceptionally` 处理 |
| 其他类型 | **不支持**，会被当成同步方法 | —— |

**返回 void 时的异常处理**（高频追问）：

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("异步方法 {} 执行异常，参数 {}", method.getName(), params, ex);
            // 告警、落库、重试都可以在这里做
        };
    }
}
```

注意这个处理器**只对 void 返回值的 `@Async` 方法生效**。返回 `Future`/`CompletableFuture` 时异常会被包装进返回对象，不需要它兜底。

**默认实现是 `SimpleAsyncUncaughtExceptionHandler`，只打一行 error 日志**，不作任何补偿——生产上必须自己实现。

---

## 三、线程池怎么选（三层退让）

不指定线程池名称时，`@Async` 的查找顺序是：

**第一优先，容器中唯一的 `TaskExecutor` Bean。** 如果有且只有一个，就用它。

**第二优先，名为 `taskExecutor` 的 Bean。** 这是 Spring Boot 自动配置的默认 Bean 名。

**第三，退化为 `SimpleAsyncTaskExecutor`。** 每次调用都新建线程、用完即弃，不复用。**这是生产环境必须避免的**——高并发下会瞬间创建大量线程，把内存和 CPU 打满。

### 自动配置的 ThreadPoolTaskExecutor 是什么

`ThreadPoolTaskExecutor` 是 Spring 对 JDK `ThreadPoolExecutor` 的封装，实现了 `TaskExecutor` 接口。所谓"自动配置"，指引入 spring-boot-starter-web 后，Spring Boot 会根据 `spring.task.execution` 前缀的配置自动创建它，Bean 名通常是 `taskExecutor`：

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 200
        keep-alive: 60s
      thread-name-prefix: async-
```

这个自动配置由 `TaskExecutionAutoConfiguration` 完成，它的类上有 `@ConditionalOnMissingBean(TaskExecutor.class)`——**只要你声明了任何 `TaskExecutor`，它就退让**。

默认参数比较保守（核心数等具体值随版本变化，要用时查当前版本文档），所以生产上通常自己定义：

```java
@Bean("orderExecutor")
public ThreadPoolTaskExecutor orderExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(8);
    e.setMaxPoolSize(16);
    e.setQueueCapacity(200);            // 有界队列，别用默认的无界
    e.setThreadNamePrefix("order-async-");
    e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    e.initialize();
    return e;
}

@Async("orderExecutor")                 // 显式指定，避免和其他异步任务抢池子
public void sendSms(String phone) { ... }
```

**为什么要显式指定 Bean 名**：不指定的话所有 `@Async` 方法共用一个默认池。短信发送慢会把订单导出的线程占满，互不影响变成了互相拖累。

---

## 四、五个必踩的坑

### 坑一：自调用失效（最高频）

同一个类里，普通方法直接调用 `@Async` 方法，**不经过代理**，所以仍然同步执行：

```java
@Service
public class OrderService {
    public void create() {
        this.sendSms();          // ❌ this 是原始对象，不是代理，异步失效
    }
    @Async
    public void sendSms() { ... }
}
```

三种解法：

1. **拆到另一个 Bean**（最推荐，架构上更清晰）
2. **注入自身代理**：`@Lazy @Autowired OrderService self;` 然后 `self.sendSms()`
3. **`AopContext.currentProxy()`**：需要开 `@EnableAspectJAutoProxy(exposeProxy = true)`

> 和 `@Transactional` 自调用失效是同一个原因——都是"没有经过代理"。见 [[@Transactional 类上 vs 方法上]]。

### 坑二：方法必须是 public、不能是 final/static

- `private` 方法：CGLIB 无法覆写，代理拦截不到
- `final` 方法：同样无法覆写
- `static` 方法：静态方法不属于实例，代理管不到

### 坑三：上下文不会自动传递

`ThreadLocal` 里的数据、`SecurityContext`、`MDC` 里的 traceId，都**不会带到新线程**。要手动传：

```java
// 方式一：TaskDecorator（推荐，全局统一）
@Bean("orderExecutor")
public ThreadPoolTaskExecutor orderExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setTaskDecorator(runnable -> {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        String user = UserContext.get();
        return () -> {
            try {
                if (mdc != null) MDC.setContextMap(mdc);
                UserContext.set(user);
                runnable.run();
            } finally {
                MDC.clear();
                UserContext.clear();     // 必须清，线程池线程会复用
            }
        };
    });
    e.initialize();
    return e;
}

// 方式二：TransmittableThreadLocal（阿里 TTL），适合 ThreadLocal 链路
```

**注意 finally 里必须 clear**：线程池的线程会复用，不清理会把上一个任务的上下文带给下一个任务——这就是 `ThreadLocal` 在线程池里的串扰问题。

### 坑四：事务是独立的

`@Async` 方法在新线程里执行，**会开启自己的新事务，无法加入调用方的事务**。

后果是：调用方事务回滚，异步方法里已经做的操作**不会跟着回滚**。

```java
@Transactional
public void createOrder() {
    orderDao.insert(order);
    asyncService.sendNotify(order.getId());   // 新线程、新事务，即使这里回滚也不影响
    throw new RuntimeException("模拟回滚");
    // order 会回滚，但 sendNotify 里的操作已经提交了
}
```

设计时要考虑清楚：异步方法要么做成幂等可重试的，要么用消息队列代替直接调用。

### 坑五：`@Async` 和 `@Transactional` 同时标注的顺序问题

如果一个方法同时标了 `@Async` 和 `@Transactional`，**事务在异步线程里开启**，和调用方完全无关。而且两个注解都依赖代理，注意 `@EnableAsync` 和 `@EnableTransactionManagement` 都要开。

---

## 五、@Async vs @Transactional 对比

| 维度 | @Async | @Transactional |
|---|---|---|
| 本质 | AOP + 线程池 | AOP + 事务管理器 |
| 失效原因 | 自调用、非 public、final | 自调用、非 public、异常被吞、非受检异常默认不回滚 |
| 线程 | 切换新线程 | 同线程 |
| 上下文 | 不传递（要 TaskDecorator） | 不涉及 |
| 副作用 | 调用方拿不到结果（void） | 回滚范围 |

**共同点**：都靠代理生效，自调用都会失效。这两个"失效"是 Spring 面试的经典双胞胎问题。

---

## 六、面试话术（可直接念）

`@Async` 是 Spring 提供的注解，让方法在独立线程执行、调用方不必等待。它要配合 `@EnableAsync` 使用，否则完全不生效。本质是 AOP 加线程池：注解只是元数据，Spring 启动时读到它，为 Bean 生成代理，代理把调用包装成任务提交给 `TaskExecutor`，底层由 `AsyncAnnotationAdvisor` 和 `AsyncExecutionInterceptor` 完成。

返回值决定调用方能拿到什么。返回 void 时拿不到结果，异常只能由 `AsyncUncaughtExceptionHandler` 处理，默认实现只打日志，生产要自己实现 `AsyncConfigurer` 注册。返回 Future 或 CompletableFuture 时，异常包装在返回对象里，`get` 的时候能感知。

线程池的查找顺序是：容器里唯一的 TaskExecutor、然后名为 taskExecutor 的 Bean、最后退化成 SimpleAsyncTaskExecutor 每次新建线程。Spring Boot 的 `TaskExecutionAutoConfiguration` 会在你没有自定义时自动配一个 `ThreadPoolTaskExecutor`，但默认参数保守，生产建议自己定义有界队列加显式拒绝策略，并用 `@Async("beanName")` 指定。

最容易踩的坑是自调用，同一个类里 this 调用不走代理所以还是同步的，要拆到另一个 Bean 或者注入自身代理。另外方法必须是 public 且不能 final，ThreadLocal、MDC、安全上下文都不会自动传递要用 TaskDecorator，异步方法的事务是独立的，调用方回滚不会带着它一起滚。

---

## 关联知识点

- [[Spring ioc]] — Bean 生命周期与代理生成时机
- [[@Transactional 类上 vs 方法上]] — 同为代理失效问题
- [[@Configuration 的 CGLIB 原地增强]] — 代理机制的另一面
- [[线程池]] — 线程池参数与拒绝策略
- [[题21-Async异常与ThreadLocal传递]] — 项目场景下的 @Async 用法
- [[项目里线程池用在哪（抢座调度池·定时任务池）]] — 线程池落点
