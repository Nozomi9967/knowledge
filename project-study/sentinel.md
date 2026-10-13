---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---
以下是 **Sentinel 在 SpringBoot 3.2.5 + Spring Cloud Gateway** 场景下的完整使用指南。

> [!info] 已升级至规范页
> 本页保留为原始实操记录，**不再单独维护**。面试向的完整版本（滑动窗口 LeapArray、QPS/并发线程数阈值、四种流控效果、熔断三策略与半开、vs Hystrix 隔离方式、本项目硬伤预案）已吸收进 [[Sentinel详解]]，背诵请去那一页。

------

## 一、核心概念

Sentinel 是阿里巴巴开源的流量治理框架，核心能力有三：

- **流量控制**（Flow Control）：按 QPS 或并发数限流
- **熔断降级**（Circuit Breaker）：按异常比例/慢调用比例自动熔断
- **系统自适应保护**：根据系统负载整体限流

在 Gateway 场景下，Sentinel 以**路由粒度** 或**自定义 API 分组**为资源进行限流，比应用层更靠前。

------

## 二、依赖配置

**pom.xml** — Spring Cloud Alibaba 版本需与 Spring Boot 3.x 兼容（2023.0.x 系列）：

```xml
<properties>
  <spring-cloud.version>2023.0.1</spring-cloud.version>
  <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
</properties>

<dependencies>
  <!-- Spring Cloud Gateway -->
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
  </dependency>

  <!-- Sentinel Gateway 适配器 -->
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
  </dependency>

  <!-- Sentinel 核心 -->
  <dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-core</artifactId>
  </dependency>

  <!-- Sentinel 控制台通信（可选，推荐） -->
  <dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-transport-simple-http</artifactId>
  </dependency>
</dependencies>
```

------

## 三、application.yml 配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**

    sentinel:
      transport:
        dashboard: localhost:8080   # Sentinel 控制台地址
        port: 8719                  # 本地通信端口（各服务不同）
      eager: true                   # 启动时主动注册到控制台
      filter:
        enabled: false              # Gateway 模式下关闭 Servlet filter
```

------

## 四、代码配置

### 4.1 启动类注解

```java
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### 4.2 Sentinel Gateway 初始化配置

```java
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void init() {
        initGatewayRules();
        initCustomApi();
    }

    /** 针对路由 ID 配置限流规则 */
    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // order-service 路由：QPS 限制为 10
        rules.add(new GatewayFlowRule("order-service")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)
                .setBurst(5)           // 突发流量容忍
        );

        // user-service 路由：QPS 限制为 20，排队等待策略
        rules.add(new GatewayFlowRule("user-service")
                .setCount(20)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)
                .setMaxQueueingTimeoutMs(500)
        );

        GatewayRuleManager.loadRules(rules);
    }

    /** 自定义 API 分组（更细粒度，跨路由） */
    private void initCustomApi() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 将 /api/order/pay/** 单独作为一个 API 资源
        ApiDefinition payApi = new ApiDefinition("pay-api")
                .setPredicateItems(Set.of(
                        new ApiPathPredicateItem()
                                .setPattern("/api/order/pay/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                ));
        definitions.add(payApi);

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);

        // 对 pay-api 单独限流：QPS 为 5
        Set<GatewayFlowRule> apiRules = new HashSet<>();
        apiRules.add(new GatewayFlowRule("pay-api").setCount(5).setIntervalSec(1));
        GatewayRuleManager.loadRules(apiRules);
    }
}
```

### 4.3 自定义限流响应（替代默认的 429 页面）

```java
@Configuration
public class SentinelFallbackConfig {

    @PostConstruct
    public void init() {
        // 设置自定义 block handler
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(
                    ServerWebExchange exchange, Throwable ex) {

                Map<String, Object> body = Map.of(
                        "code", 429,
                        "msg", "请求过于频繁，请稍后重试",
                        "timestamp", System.currentTimeMillis()
                );

                return ServerResponse
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(body));
            }
        });
    }
}
```

------

## 五、熔断规则配置（可选）

```java
private void initDegradeRules() {
    List<DegradeRule> rules = new ArrayList<>();

    DegradeRule rule = new DegradeRule("order-service")
            .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
            .setCount(0.5)          // 错误比例超过 50% 触发熔断
            .setMinRequestAmount(10)
            .setStatIntervalMs(10000)
            .setTimeWindow(30);     // 熔断持续 30 秒

    rules.add(rule);
    DegradeRuleManager.loadRules(rules);
}
```

------

## 六、架构流程图---

## 七、JVM 启动参数（连接控制台必须）

```bash
-Dcsp.sentinel.dashboard.server=localhost:8080
-Dcsp.sentinel.app.name=gateway-service
-Dcsp.sentinel.api.port=8719
```

------

## 八、规则持久化（Nacos）

生产环境规则存内存会在重启后丢失，推荐接入 Nacos：

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
spring:
  cloud:
    sentinel:
      datasource:
        gw-flow:
          nacos:
            server-addr: localhost:8848
            dataId: gateway-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: gw-flow   # Gateway 专用类型
        degrade:
          nacos:
            server-addr: localhost:8848
            dataId: gateway-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade
```

------

## 九、常见坑与注意事项

| 问题                   | 原因                             | 解决                                                   |
| ---------------------- | -------------------------------- | ------------------------------------------------------ |
| 规则不生效             | `sentinel.filter.enabled` 未关闭 | Gateway 模式下必须设为 `false`                         |
| 控制台看不到服务       | 缺少 JVM 参数或端口冲突          | 检查 `-Dcsp.sentinel.api.port` 各服务唯一              |
| 自定义响应不触发       | `BlockRequestHandler` 未注册     | 确保 `@PostConstruct` 在容器启动后执行                 |
| Spring Boot 3.x 不兼容 | 使用了旧版 SCA（2021.x）         | 升级到 `2023.0.1.0` 版本                               |
| 路由资源名不匹配       | 资源名默认是路由 `id`            | `GatewayFlowRule` 的第一个参数必须与 route id 完全一致 |





## setControlBehavior 解释

`setControlBehavior` 设置的是**触发限流阈值后的流量控制策略**，即"超出限制的请求怎么处理"。

`RuleConstant` 提供三个可选值：

------

### CONTROL_BEHAVIOR_DEFAULT（直接拒绝）= 0

```java
.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)
```

**超出 QPS 阈值的请求立即抛出 `FlowException`，直接拒绝**，不等待、不排队。这是默认值，不调用 `setControlBehavior` 时也是这个行为。

配合 `setBurst(5)` 使用时，允许短时突发超出 5 个，之后再拒绝。

------

### CONTROL_BEHAVIOR_WARM_UP（冷启动/预热）= 1

```java
.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
.setWarmUpPeriodSec(10)  // 预热时长
```

服务刚启动时，**允许的 QPS 从低位逐渐爬升到设定阈值**，避免冷启动时大流量直接打垮还未完全就绪的服务（如缓存未加载、连接池未满等场景）。

------

### CONTROL_BEHAVIOR_RATE_LIMITER（匀速排队）= 2

```java
.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)
.setMaxQueueingTimeoutMs(500)  // 最长等待时间
```

请求不会被立即拒绝，而是**放入队列匀速通过**（漏桶算法）。等待超过 `maxQueueingTimeoutMs` 才拒绝。适合削峰填谷，让下游压力平滑。

------

### 三种策略对比

| 策略           | 超限处理             | 适用场景                 |
| -------------- | -------------------- | ------------------------ |
| `DEFAULT`      | 立即拒绝             | 对延迟敏感、不接受等待   |
| `WARM_UP`      | 预热爬坡后才满载     | 服务冷启动保护           |
| `RATE_LIMITER` | 排队等待，超时才拒绝 | 消息类、可容忍延迟的接口 |

------

回到你的代码，`order-service` 用的是 `DEFAULT` + `setBurst(5)`：QPS 正常限制 10，但允许瞬时突发到 15，超出后立即拒绝。`user-service` 用的是 `RATE_LIMITER`，超出 20 QPS 的请求最多等 500ms，再超时才拒绝——这适合用户类接口，体验比直接 429 更平滑。





## setIntervalSec 解释

`setIntervalSec` 设置的是**统计时间窗口的长度**，单位是秒。

```java
.setCount(10)
.setIntervalSec(1)   // 含义：每 1 秒内最多 10 次请求
```

本质上，`count / intervalSec` 才是真正的速率。所以这两个参数要**组合理解**：

| count | intervalSec | 实际限制                      |
| ----- | ----------- | ----------------------------- |
| 10    | 1           | 每秒 10 次                    |
| 100   | 10          | 每秒 10 次（等价）            |
| 60    | 60          | 每分钟 60 次（每秒均摊 1 次） |

------

### 和 DEFAULT 策略组合时的注意点

`CONTROL_BEHAVIOR_DEFAULT` 用的是**滑动时间窗口**计数，`intervalSec` 就是这个窗口的大小。窗口越大，统计越平滑，但对突发的敏感度越低。

`CONTROL_BEHAVIOR_RATE_LIMITER`（匀速排队）**忽略 `intervalSec`**，它只看 `count`，内部换算成每次请求的最小间隔：

```
最小间隔 = 1000ms / count = 1000 / 20 = 50ms 放行一个
```

所以用排队策略时，`intervalSec` 写不写都一样，实际不生效。

------

### 实际建议

Gateway 场景下通常直接用 `setIntervalSec(1)` 即可，语义最直观。需要限制"每分钟调用次数"（如第三方 API 配额）时才会设成 60。