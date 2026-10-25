---
tags:
  - spring
created: 2026-06-28
updated: 2026-10-13
---


## Gateway限流



一般限流都是从网关这里做
Nginx,Openresty,kong这些都是代理层网关

> Openresty=Nginx+lua



zuul,Spring Cloud Gateway本质是tomcat，不能用来挡外层流量



### 计数器算法

计数器算法采用计数器实现限流有点简单粗暴，一般我们会限制一秒钟的能够通过的请求数，比如限流aps为100，算法的实现思路就是从第一个请求进来开始计时，在接下去的1s内，每来一个请求，就把计数加1，如果累加的数字达到了100，那么后续的请求就会被全部拒绝。等到1s结束后，把计数恢复成0，重新开始计数



### 漏桶算法

漏桶算法内部有一个容器，类似生活用到的漏斗，当请求进来时，相当于水倒入漏斗，然后从下端小口慢慢匀速的流出。不管上面流量多大，下面流出的速度始终保持不变。不管服务调用方多么不稳定，通过漏桶算法进行限流，每10毫秒处理一次请求。因为处理的速度是固定的，请求进来的速度是未知的，可能突然进来很多请求，没来得及处理的请求就先放在桶里，既然是个桶，肯定是有容量上限，如果桶满了，那么新进来的请求就丢弃。漏桶算法存在一个缺陷，无法应对短时间的突发流量，比如双十一抢购、秒杀活动开始。



### 令牌桶算法

令牌桶算法是对漏桶算法的一种改进，桶算法能够限制请求调用的速率，而令牌桶算法能够在限制调用的平均速率的同时还允许一定程度的突发调用。在令牌桶算法中，存在一个桶，用来存放固定数量的令牌。算法中存在一种机制，以一定的速率往桶中放令牌。每次请求调用需要先获取令牌，只有拿到令牌，才有机会继续执行，否则选择选择等待可用的令牌、或者直接拒绝。放令牌这个动作是持续不断的进行，如果桶中令牌数达到上限，就丢弃令牌，所以就存在这种情况，桶中一直有大量的可用令牌，这时进来的请求就可以直接拿到令牌执行，比如设置qps为100，那么限流器初始化完成一秒后，桶中就已经有100个令牌了，这时服务还没完全启动好，等启动完成对外提供服务时，该限流器可以抵挡瞬时的100个请求。所以，只有桶中没有令牌时，请求才会进行等待，最后相当于以一定的速率执行。





## Gateway基于redis的令牌桶实现



**以ip作为限流标识**

```java
public class IpKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }
}

```



**关于mono**

> 简单来说，`Mono` 就像是一个**未来才会准时送达的快递盒**。
>
> 它是 Spring WebFlux（响应式编程框架）核心库 Reactor 中的一个类。你可以把它理解为“一个延迟交付的容器”**，里面要么装著**有且仅有一个的数据，要么就是一个错误，要么就是什么都没有（空包裹）。
>
> 在传统的 Java 开发中，你的方法如果要返回一个字符串，直接写 `public String resolve()` 就可以了。但在高并发的响应式编程（Reactive Programming）世界里，我们不能让代码卡在那里死等结果，所以要用 `Mono` 把结果包装起来。
>
> ### 为什么要搞得这么麻烦？
>
> 为了**不阻塞（Non-blocking）**。我们可以用一个生活中的例子来对比传统开发和用 `Mono` 的区别：
>
> - **传统开发（同步阻塞）：** 你去前台点奶茶，服务员收了钱，开始在后面做奶茶。你就站在前台死等，后面排队的人也只能等著，直到你拿到奶茶走人，下一个人才能点。
> - **响应式开发（异步非阻塞，使用 `Mono`）：** 你去前台点奶茶，服务员收了钱，给了你一个**取餐号（这就是 `Mono`）**，然后对你说：“好了，你去旁边坐着等吧，好了叫你。” 接着服务员立刻开始接待下一个人。等奶茶做好了，取餐号震动，你过去拿奶茶。
>
> 在你的代码里：
>
> Java
>
> ```
> return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
> ```
>
> `Mono.just(...)` 就是立刻制造并返回了这么一个“取餐号”（快递盒），里面装著获取到的 IP 地址。Spring 框架拿到这个 `Mono` 后，会在合适的时间（数据准备好时）去拆开它，拿到真正的 IP 地址去进行接下来的限流等操作，而不会让当前的线程傻傻地卡在那里等。
>
> ### 总结 `Mono` 的三句话
>
> 1. **数量限制：** 里面最多只能装 **0 个或 1 个** 元素。（如果需要装一堆元素，也就是类似 List 的概念，响应式里有另一个兄弟叫 `Flux`）。
> 2. **异步开关：** 它代表的是一个“未来的操作”，只要你不去“订阅（subscribe）”它，包裹里的代码甚至都不会真正执行。
> 3. **高并发神器：** 有了它，服务器可以用极少的线程处理海量的并发请求。





**yml配置**

```
        #商品服务
        - id: goods_route
          uri: lb://mall-goods
          predicates:
            - Path=/mall/brand/**,/mall/category/**,/mall/skuAttribute/**,/mall/sku/**,/mall/spu/**
          filters:
            - StripPrefix=1
#            指定过滤器
            - name: RequestRateLimiter
              args:
                #指定当前限流标识 IP/URI
                key-resolver: '#{@ipKeyResolver}'
                #速率限流值
                redis-rate-limiter.replenishRate: 1
                #能容纳的并发流量总数
                redis-rate-limiter.burstCapacity: 2
```

---

## 关联知识点

- [[Sentinel详解]] — 网关限流的另一条路线（Gateway Adapter + 滑动窗口 + 规则实时推送），和本页的 Redis RequestRateLimiter（令牌桶）二选一
- [[04-Spring/Spring Boot 与 Spring Cloud Alibaba 的关系]] — SCA 组件全景

