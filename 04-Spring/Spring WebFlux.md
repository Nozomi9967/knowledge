---
tags:
  - spring
created: 2026-06-28
updated: 2026-06-28
---
# Spring WebFlux 讲解

## 什么是 WebFlux？

Spring WebFlux 是 Spring 5 引入的**响应式 Web 框架**，与传统的 Spring MVC 并存。它基于 Reactive Streams 规范，采用**非阻塞、异步**的编程模型。



![image-20260515153238392](C:\Users\q1948\Desktop\八股\images\image-20260515153238392.png)

## 核心概念

**编程模型**

WebFlux 基于 Project Reactor，提供两个核心类型：

- `Mono<T>`：0 或 1 个元素的异步序列
- `Flux<T>`：0 到 N 个元素的异步流

```java
// MVC 风格（阻塞）
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id); // 阻塞等待
}

// WebFlux 风格（响应式）
@GetMapping("/user/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return userRepository.findById(id); // 立即返回 Mono，不阻塞
}
```

**两种编程风格**

WebFlux 支持两种写法并存：

| 风格                       | 特点                                        |
| -------------------------- | ------------------------------------------- |
| 注解式（`@Controller`）    | 和 MVC 几乎一样，迁移成本低                 |
| 函数式（`RouterFunction`） | 更函数化，用 `route()` + `handler` 组织路由 |

```java
// 函数式风格示例
@Bean
public RouterFunction<ServerResponse> routes(UserHandler handler) {
    return route()
        .GET("/users/{id}", handler::getUser)
        .POST("/users", handler::createUser)
        .build();
}
```

## 运行时：Netty vs Servlet 容器

WebFlux 默认运行在 **Netty** 上（非阻塞 NIO），也可跑在 Servlet 3.1+ 容器（Tomcat、Jetty）上。与 MVC 的关键区别在于：Netty 的 I/O 线程在等待数据库/网络时不会阻塞，而是注册回调后立刻去处理别的请求。

## 什么时候该用 WebFlux？

适合用 WebFlux 的场景：

- **大量并发长连接**：如 API 网关、SSE 推送、WebSocket
- **I/O 密集型服务**：大量外部 HTTP 调用、数据库读写
- **已经在用 Reactor/RxJava** 的项目

不适合的场景：

- 团队对响应式不熟悉（学习曲线较陡）
- 大量使用阻塞库（如传统 JDBC、某些 Spring Data 模块），强行套 WebFlux 反而性能更差
- 简单 CRUD 业务，MVC 够用

## 常见操作符速查

```java
// 转换
Mono.just("hello").map(String::toUpperCase);           // 同步转换
Mono.just(1L).flatMap(id -> repo.findById(id));        // 异步转换（最常用）

// 组合
Mono.zip(getMono1(), getMono2(), (a, b) -> merge(a,b)); // 并行等两个完成

// 错误处理
flux.onErrorReturn(defaultValue);
flux.onErrorResume(e -> fallbackMono());

// 背压（Flux）
flux.take(10)           // 只取前10个
    .filter(x -> x > 0)
    .collectList();     // 收集成 Mono<List>
```

## 与 nozomi-mall 的关联

你的项目目前用的是 Spring Cloud（MVC 栈）。WebFlux 在 Spring Cloud Gateway 里默认就是响应式的——如果后续给 mall 加网关层，Gateway 的路由配置、全局过滤器（`GlobalFilter`）就是 WebFlux 编程模型，值得提前了解一下。

有想深入的部分吗？比如 Reactor 调试技巧、与 R2DBC 的集成，或者 Gateway 过滤器写法？