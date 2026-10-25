---
tags:
  - spring
  - springboot
  - springcloud
  - 微服务
  - 版本兼容
created: 2026-09-03
updated: 2026-09-03
source: https://github.com/alibaba/spring-cloud-alibaba/wiki/版本说明
---

# Spring Boot 与 Spring Cloud Alibaba 的关系

> [!tip] 一句话
> Spring Boot 管**单个服务怎么跑起来**，Spring Cloud Alibaba 管**一堆服务怎么协作**。SCA 的每个组件都是以 Boot starter 形态存在的，离开 Boot 一行都跑不起来；并且两者的**版本必须严格对齐**，错配直接启动失败。

---

## 1. 不是两层，是四层

技术栈表里写成 `Spring Boot 2.3.0 / Spring Cloud Alibaba` 容易让人以为是两个并列的东西，实际是一条自下而上的依赖链：

```
Spring Framework   ← 内核：IoC 容器 / AOP / 事务抽象
       ↑
Spring Boot        ← 脚手架：自动装配 + starter + 内嵌容器
       ↑
Spring Cloud       ← 规范层：服务治理的抽象接口（只定标准，不给实现）
       ↑
Spring Cloud Alibaba ← 实现层：阿里中间件对上面这套抽象的具体实现
```

| 层级 | 角色 | 核心产出 | 能否独立使用 |
|------|------|---------|------------|
| Spring Framework | 内核 | `BeanFactory`、`ApplicationContext`、AOP 代理 | ✅ 能 |
| Spring Boot | 单体脚手架 | 自动装配、`spring-boot-starter-*`、内嵌 Tomcat | ✅ 能（本项目的每个微服务本质都是一个 Boot 应用） |
| Spring Cloud | 分布式规范 | `DiscoveryClient`、`PropertySourceLocator`、`CircuitBreaker` 等 SPI 抽象 | ❌ 依赖 Boot |
| Spring Cloud Alibaba | 规范的阿里实现 | Nacos / Sentinel / Seata / RocketMQ 的接入 starter | ❌ 依赖 Boot + Spring Cloud |

⚠ **关键理解**：Spring Cloud 本身几乎不干活，它只定义"注册中心应该长什么样"。真正干活的是实现套件——阿里系是 Spring Cloud Alibaba，Netflix 系是 Spring Cloud Netflix（Eureka/Hystrix/Ribbon），两者在架构上**平级且可互换**。

---

## 2. 职责边界

| 维度 | Spring Boot | Spring Cloud Alibaba |
|------|-------------|---------------------|
| 关注对象 | **一个**服务进程 | **一群**服务之间的关系 |
| 解决的问题 | 配置繁琐、部署麻烦（打 war 装 Tomcat） | 服务怎么找到彼此、怎么防雪崩、跨库事务怎么办 |
| 典型能力 | 自动装配、内嵌容器、`application.yml`、Actuator | 服务注册发现、配置中心、流控熔断、分布式事务 |
| 本项目对应 | 14 个微服务各自的启动类与 starter 依赖 | [[06-其他杂项/Nacos详解\|Nacos]]、[[Sentinel详解\|Sentinel]]、[[06-其他杂项/Seata详解\|Seata]]、[[06-其他杂项/RocketMQ详解\|RocketMQ]] |

去掉 Spring Cloud Alibaba，这 14 个服务还能各自单独启动，只是彼此不认识、要硬编码 IP 互调；去掉 Spring Boot，SCA 连自动装配的入口都没有。

---

## 3. 技术上的耦合点：SCA 为什么离不开 Boot

不是"生态上建议一起用"，而是**实现机制上的硬绑定**：

1. **starter 形态** — SCA 的每个能力都打包成 Boot starter，比如 `spring-cloud-starter-alibaba-nacos-discovery`、`spring-cloud-starter-alibaba-sentinel`，依赖坐标就是 Boot 的约定。
2. **靠自动装配生效** — 这些 starter 内部通过 `spring.factories`（Boot 3.0 后改为 `AutoConfiguration.imports`）声明 `@Configuration` 类，是 Boot 的 `@EnableAutoConfiguration` 把它们加载进容器的。参见 [[04-Spring/@SpringBootApplication解析|@SpringBootApplication解析]]。
3. **实现 Spring Cloud 的 SPI 接口** — SCA 干的事就是"把阿里中间件塞进 Spring Cloud 留好的插槽"：

| Spring Cloud 抽象 | Spring Cloud Alibaba 实现 | 作用 |
|------------------|--------------------------|------|
| `DiscoveryClient` | `NacosDiscoveryClient` | 服务注册与发现 |
| `PropertySourceLocator` | `NacosPropertySourceLocator` | bootstrap 阶段拉远程配置 |
| `CircuitBreaker` | Sentinel 适配器 | 熔断降级 |
| `LoadBalancerClient` | 结合 Nacos 元数据的权重负载 | 客户端负载均衡 |

正因为存在这层"抽象 + 实现"的插槽关系，Nacos 才能一行代码不改地替换掉 Eureka——业务代码只依赖 `@LoadBalanced` 和 Feign 接口，不依赖 Nacos。

---

## 4. 版本对应关系（面试高频坑，必须记住）

这是"这俩什么关系"最实在的一层答案：**SCA 的版本号不是自由选的，它锁死了 Spring Cloud 版本，Spring Cloud 又锁死了 Spring Boot 版本**。三者错配的典型症状是 `NoSuchMethodError`、`NoClassDefFoundError`、或者启动时 Bean 定义解析失败。

官方版本对应表（摘录，`*` 为该分支最新）：

| Spring Cloud Alibaba | Spring Cloud | Spring Boot |
|---------------------|--------------|-------------|
| 2022.0.0.0 * | 2022.0.0 | 3.0.2 |
| 2021.0.5.0 * | 2021.0.5 | 2.6.13 |
| 2021.1 | 2020.0.1 | 2.4.2 |
| 2.2.10-RC1 * | Hoxton.SR12 | 2.3.12.RELEASE |
| 2.2.9.RELEASE | Hoxton.SR12 | 2.3.12.RELEASE |
| 2.2.7.RELEASE | Hoxton.SR12 | 2.3.12.RELEASE |
| 2.2.6.RELEASE | Hoxton.SR9 | 2.3.2.RELEASE |
| 2.2.1.RELEASE | Hoxton.SR3 | 2.2.5.RELEASE |
| 2.1.4.RELEASE | Greenwich.SR6 | 2.1.13.RELEASE |

> [!info] 本项目（Spring Boot 2.3.0）落在哪一格
> Boot 2.3.x 对应的是 **2.2.x 分支 + Hoxton**（Hoxton.SR9 / SR12 一线）。严格说官方表里 2.3.x 的行是从 `2.3.2.RELEASE` 起算的，2.3.0 没有专属行——所以说自己用 Boot 2.3.0 时，最好能同时报出 SCA 的具体版本号（2.2.5/2.2.6.RELEASE 这一档）和 Spring Cloud 的 Hoxton 代号，否则面试官追问"你 SCA 用的哪版"会答不上来。**技术栈表里目前只写了 `Spring Cloud Alibaba` 没写版本，建议去项目 `pom.xml` 的 `dependencyManagement` 里确认后补上。**

**Spring Cloud 用伦敦地铁站命名**（Finchley → Greenwich → Hoxton），从 2020.0 起改用年份版本号；SCA 在 2021.x 之后也跟着改成 `2021.0.5.0` 这种"前三段跟 Spring Cloud、最后一段是自增扩展号"的格式。

---

## 5. 两个分水岭版本（升级时踩的就是这两个）

SCA 之所以要同时维护 `2.2.x` / `2021.x` / `2022.x` 三条分支并行，就是被 Boot 的两次破坏性变更切开的：

| 分水岭 | 变了什么 | 后果 |
|--------|---------|------|
| **Boot 2.4 / Spring Cloud 2020.0** | bootstrap 上下文**默认关闭** | `bootstrap.yml` 直接失效，Nacos 配置拉不到；必须额外引 `spring-cloud-starter-bootstrap`，或设 `spring.cloud.bootstrap.enabled=true` |
| **Boot 3.0** | 包名 `javax.*` → `jakarta.*`；自动配置从 `spring.factories` 改为 `AutoConfiguration.imports`；最低 JDK 17 | 老 starter 全部失效，必须换 SCA 2022.x 分支 |

> [!warning] 与 [[06-其他杂项/面试分析报告|面试分析报告]] Q7 踩坑题的关联
> 报告里 Q7 提到"Nacos 配置刷新不生效——确认 `bootstrap.yml` 的优先级"。本项目在 Boot 2.3.0 + Hoxton 上，bootstrap 上下文**还是默认开启的**，所以 `bootstrap.yml` 天然生效。这个坑真正爆发是在升到 Boot 2.4+ 之后——回答时把"我们这版还没这问题，但 2.4 之后 bootstrap 默认关了要额外引 starter"讲出来，比只说"确认优先级"专业得多。

---

## 6. 别搞混：哪些组件其实不是 Alibaba 的

面试官爱问的一个小陷阱题——"你的 Gateway 是 Spring Cloud Alibaba 提供的吗？"

| 组件 | 归属 | 说明 |
|------|------|------|
| Nacos / Sentinel / Seata / RocketMQ / Dubbo | **Spring Cloud Alibaba** | 阿里自家中间件的接入 |
| **Spring Cloud Gateway** | Spring 官方 | Spring 自研的响应式网关，SCA 只提供 Sentinel 与它的适配器（`sentinel-spring-cloud-gateway-adapter`） |
| **OpenFeign** | Spring 官方（源自 Netflix Feign） | 声明式调用，见 [[06-其他杂项/Feign详解\|Feign详解]] |
| **Spring Cloud LoadBalancer** | Spring 官方 | 2020.x 起取代 Ribbon |
| Elastic-Job / Canal / ES / FastDFS | 都不属于 | 独立中间件，跟 SCA 无版本绑定 |

所以本项目"基于 Spring Cloud Alibaba"这句话严格讲是：**注册配置 + 流控 + 分布式事务 + MQ 这四块用的是 SCA，网关和远程调用用的是 Spring Cloud 官方组件**。

---

## 7. 为什么国内选 Alibaba 套件而不是 Netflix 套件

先说 Netflix 为什么受限，再说 SCA 的优势，对比才立得住：

| 能力 | Spring Cloud Netflix | Spring Cloud Alibaba |
|------|---------------------|---------------------|
| 注册中心 | Eureka（2.0 开源烂尾，只有 AP） | Nacos（AP/CP 可切换，且**注册 + 配置二合一**） |
| 配置中心 | 需另配 Spring Cloud Config + Bus + Git | Nacos 内置，改完推送即生效 |
| 熔断限流 | Hystrix **已停止新功能开发** | [[Sentinel详解\|Sentinel]]（实时监控面板 + 动态规则推送 + 热点参数限流） |
| 负载均衡 | Ribbon 进入维护模式 | Spring Cloud LoadBalancer + Nacos 权重元数据 |
| 分布式事务 | ❌ 无 | Seata 四种模式 |

一句话：**Netflix 那套是"每个能力配一个组件"，SCA 是"一个 Nacos 顶两个组件"，加上 Hystrix/Ribbon 停更、Eureka 只有 AP，国内基本全面迁到了 SCA。**

---

## 8. 面试话术（口述版）

> "这两个不是并列关系，是上下依赖关系。Spring Boot 解决的是单个服务怎么快速跑起来的问题——自动装配、starter、内嵌 Tomcat，约定优于配置；Spring Cloud Alibaba 解决的是一堆服务之间怎么协作的问题——服务注册发现、配置中心、流控熔断、分布式事务。
>
> 中间其实还夹着一层 Spring Cloud。Spring Cloud 只定规范，它定义了 `DiscoveryClient`、`PropertySourceLocator` 这些抽象接口，本身几乎不干活；真正干活的是实现套件，阿里系就是 Spring Cloud Alibaba，比如 Nacos 实现了 `DiscoveryClient` 做服务发现、实现了 `PropertySourceLocator` 在 bootstrap 阶段拉远程配置。所以整条链是 Spring Framework → Spring Boot → Spring Cloud → Spring Cloud Alibaba。也正因为有这层插槽关系，我们从 Eureka 换成 Nacos 时业务代码一行都没改。
>
> 说白了 SCA 离不开 Boot——它每个组件都是打成 Boot starter 的，靠 Boot 的自动装配机制加载，反过来 Boot 完全可以脱离 SCA 单独用。
>
> 还有一点最实际的：三者版本是锁死的，不能自由组合。我们项目是 Spring Boot 2.3.0，对应的就是 Spring Cloud 的 Hoxton 版本和 SCA 的 2.2.x 分支。版本对不上的话，启动时会直接报 `NoSuchMethodError` 或者 `NoClassDefFoundError`。这里面有两个分水岭要注意：Boot 2.4 开始 bootstrap 上下文默认关掉了，`bootstrap.yml` 会失效，得额外引 `spring-cloud-starter-bootstrap`；Boot 3.0 又把 `javax` 换成了 `jakarta`，必须升到 SCA 的 2022.x 分支。这也是 SCA 至今要三条分支并行维护的原因。"

---

## 关联知识点

- [[04-Spring/Spring-interview-guide|Spring-interview-guide]] — §7.1 Spring Cloud 与 Spring Boot 关系的简版口述答案
- [[04-Spring/spring八股|spring八股]] — Spring 全家桶总览
- [[04-Spring/@SpringBootApplication解析|@SpringBootApplication解析]] — 自动装配入口，SCA starter 靠它加载
- [[04-Spring/自定义SpringBootStarter详解|自定义SpringBootStarter详解]] — SCA 每个组件的打包形态就是这个
- [[04-Spring/SpringBoot 热门面试题|SpringBoot 热门面试题]] — Q1-Q3 涉及 Boot 定位与 starter 概念
- [[06-其他杂项/Nacos详解|Nacos详解]] — SCA 的核心实现，注册 + 配置二合一
- [[06-其他杂项/CAP定理详解|CAP定理详解]] — Nacos AP/CP 切换 vs Eureka 只有 AP
- [[06-其他杂项/Feign详解|Feign详解]] — 属于 Spring Cloud 官方而非 Alibaba
- [[06-其他杂项/Seata详解|Seata详解]] — SCA 提供的分布式事务能力
- [[04-Spring/Gateway限流|Gateway限流]] — Spring 官方网关 + Sentinel 适配
- [[Sentinel详解]] — 替代 Hystrix 的那套流控熔断组件：滑动窗口、熔断三策略、与线程池隔离的区别
- [[06-其他杂项/面试分析报告|面试分析报告]] — 本页起源：技术栈表首行「Spring Boot 2.3.0 / Spring Cloud Alibaba」
