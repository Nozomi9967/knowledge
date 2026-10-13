# Feign /feɪn/（费恩）详解 — 声明式 HTTP 客户端

> **关于名称**：Feign 原本是 Netflix 开源的声明式 HTTP 客户端，Netflix 停更后社区接手改名为 **OpenFeign**（GitHub: `OpenFeign/feign`）。Spring Cloud 在此基础上封装为 **Spring Cloud OpenFeign**，集成了 `@FeignClient`、Spring MVC 注解、负载均衡等能力。日常说的 "Feign" 通常指的就是 Spring Cloud OpenFeign。

## 一句话定义

> **Feign** 是一个**声明式 HTTP 客户端**，让你用 **Java 接口 + 注解** 的方式调用远程 HTTP 服务，无需手写 HTTP 请求代码。

---

## 解决了什么问题？

在微服务架构中，服务之间需要频繁互相调用（A 调用 B，B 调用 C）。

### ❌ 传统方式（RestTemplate）

```java
// 手动拼接 URL、序列化、处理异常
String url = "http://user-service/user/" + userId;
ResponseEntity<User> response = restTemplate.getForEntity(url, User.class);
User user = response.getBody();
```

问题：代码繁琐、URL 硬编码、不易维护。

### ✅ Feign 方式

```java
// 只需定义接口
@FeignClient("user-service")
public interface UserClient {
    @GetMapping("/user/{id}")
    User getUserById(@PathVariable("id") Long id);
}

// 直接调用，像调用本地方法一样
User user = userClient.getUserById(1001L);
```

---

## Feign 工作原理

### 先搞清三个角色

```
服务A                  Feign                    服务B
 ┌─────────┐    @FeignClient    ┌──────────┐   TCP连接   ┌──────────┐
 │ 业务代码 │  ──▶  声明接口   ──▶  Feign   ──▶  HTTP  ──▶ │ 目标服务 │
 │ (调用方) │  ◀──  返回对象   ◀──  客户端  ◀──  响应  ◀── │ (被调用方)│
 └─────────┘                   └──────────┘            └──────────┘
     ↑                              ↑                       ↑
  你的代码                      Feign 框架 (帮你干活)        对方服务器
  (只声明要调谁)                 (负责发 HTTP、等响应)       (处理请求、返回数据)
```

**三个角色之间的关系**：

| 角色 | 是谁 | 通俗理解 |
|------|------|---------|
| **调用方** | 你写的业务代码（如 OrderService） | 想找人帮忙的"你" |
| **Feign 客户端** | Feign 框架自动生成的代理对象 | 帮你跑腿的"中间人" |
| **目标服务** | 被调用的远程服务（如 UserService） | 真正干活的"对方" |

所以 connectTimeout 和 readTimeout 描述的都是 **Feign 客户端（中间人）** 与 **目标服务（对方）** 之间的时间：

```
调用方（你的代码）
    │
    │  (本地方法调用，几乎没延迟)
    ▼
Feign 客户端（中间人）
    │
    ├─ connectTimeout：中间人找到对方，打通电话，等对方接听 ⏱
    │
    ├─ 连接建立 ✅
    │
    └─ readTimeout：电话接通了，中间人等对方把话说完 ⏱
    │
    ▼
目标服务（对方）
```

---

### 💡 FeignClient 注入在哪一方？

**调用方注入。** 谁需要调用别人，谁就在自己项目里定义和注入 FeignClient。

```java
// ===== mall-order-service（调用方）===== 这里注入 FeignClient
@FeignClient("mall-pay-service")          // 声明调谁
public interface PayServiceFeign {
    @PostMapping("/pay")
    String pay(@RequestBody Order order);
}

@Service
public class OrderServiceImpl {
    @Autowired
    private PayServiceFeign payServiceFeign;  // ← 调用方注入

    public void createOrder(Order order) {
        payServiceFeign.pay(order);           // ← 调用
    }
}

// ===== mall-pay-service（被调用方）===== 只提供接口，不关心谁调
@RestController
public class PayController {
    @PostMapping("/pay")
    public String pay(@RequestBody Order order) {
        // 处理支付逻辑...
    }
}
```

**本项目中的实际映射**：

| 调用方（注入 FeignClient） | FeignClient 接口 | 目标服务 |
|--------------------------|-----------------|---------|
| mall-order-service | `PayServiceFeign` | mall-pay-service |
| Canal Client | `SearchServiceFeign` | mall-search-service |
| Canal Client | `PageServiceFeign` | mall-page-service |

---

**核心流程**：
1. **动态代理**：启动时，`@FeignClient` 接口被 JDK 动态代理生成实现类
2. **解析注解**：方法上的 `@GetMapping` / `@PostMapping` 被解析为 HTTP 请求模板
3. **负载均衡**：集成 Ribbon（Spring Cloud 2020 前）或 Spring Cloud LoadBalancer，将服务名解析为实际 IP:Port

4. **发起请求**：通过 `Client` 组件（默认 Apache HttpClient / OKHttp）发送 HTTP 请求
5. **反序列化**：将 HTTP 响应体（JSON/XML）反序列化为 Java 对象

---

## Spring Cloud Alibaba 中的 Feign

本项目使用 Spring Cloud Alibaba + [[06-其他杂项/Nacos详解\|Nacos]] 注册中心，Feign 调用链路：

```
服务A（消费者）
  ├─ @FeignClient("order-service")  ← 声明
  ├─ Nacos 服务发现                  ← 获取 order-service 实例列表
  ├─ 负载均衡（LoadBalancer）         ← 选一台
  └─ HTTP 调用                       ← 发送请求
```

### 常见配置

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000      # 连接超时 5s
        readTimeout: 10000        # 读取超时 10s
        loggerLevel: BASIC        # 日志级别：NONE / BASIC / HEADERS / FULL
  compression:
    request:
      enabled: true               # 请求压缩
    response:
      enabled: true               # 响应压缩
```

> ⚠ 这个配置写在**调用方**（注入 FeignClient 的那一方）。因为 Feign 客户端运行在调用方的进程里，HTTP 连接是从调用方发起的，超时、压缩等配置自然也由调用方控制。被调用方不需要配置这些。

### 各配置详解

| 配置 | 作用 | 通俗理解 |
|------|------|---------|
| **connectTimeout** | **Feign 客户端 → 目标服务** 建立 TCP 连接的超时时间 | 打电话时拨号到对方接听的最长等待，不接就挂断 |
| **readTimeout** | 连接建立后，等待**目标服务**返回数据的超时时间 | 电话接通后问了个问题，等对方回答的最长时间 |
| **loggerLevel** | 控制 Feign 请求的日志输出详细程度 | 要不要把每次调用过程记下来 |
| **compression** | 请求/响应内容 gzip 压缩，减少传输数据量 | 寄快递前抽真空，体积小了寄得更快 |

> 💡 **Feign 客户端** = 调用方项目里自动生成的代理对象（如 `PayServiceFeign` 的代理）  
> **目标服务** = 被调用的远程服务（如 `mall-pay-service`）

#### connectTimeout（连接超时）
- **默认值**：通常 10s
- **设太短**：网络轻微波动就报 `Connect timeout`
- **设太长**：对方挂了，调用方一直傻等，占着线程不释放
- **建议**：内网服务 **3~5s**，外网服务 **10~30s**

#### readTimeout（读取超时）
- **默认值**：通常 60s
- ⚠ **最容易踩坑**：支付/下单接口处理耗时较长，Feign 默认等不住就抛异常
- **本项目**：下单长事务设 **600000ms（10 分钟）**
- **建议**：按接口耗时灵活配置——查询 5~10s，事务 120s+

#### loggerLevel（日志级别）

| 级别 | 打印内容 | 适用场景 |
|------|---------|---------|
| `NONE` | 不打印 | 生产环境，避免刷屏 |
| `BASIC` | 请求方法 + URL + 响应码 + 耗时 | **日常推荐**，够用不吵 |
| `HEADERS` | BASIC + 请求/响应头 | 调试 Header 相关问题 |
| `FULL` | 全部（含 Body） | **仅本地开发**，别上生产 |

#### compression（压缩）
- 内容用 **gzip** 压缩，传输量减少 **60%~80%**
- 大数据量接口效果明显（列表查询、批量导入）
- 副作用：两端需解压，多耗一点 CPU，通常值得

---

## 面试高频考点

### Q1：Feign 和 RestTemplate 有什么区别？

| 对比项 | RestTemplate | Feign |
|--------|-------------|-------|
| 编码方式 | 手写 HTTP 请求代码 | 声明式接口+注解 |
| 可读性 | 差，URL 散落在业务代码中 | 好，接口集中管理 |
| 负载均衡 | 需手动整合 | 内置支持 |
| 适合场景 | 简单测试、非微服务项目 | **微服务项目（推荐）** |

**口述版**：

> **面试官**：Feign 和 RestTemplate 有什么区别？

> RestTemplate 是 Spring 提供的 HTTP 客户端，用的时候需要手动拼 URL、写请求方法、处理响应，代码比较繁琐，而且 URL 散落在业务代码各处，维护起来很麻烦。
>
> Feign 是声明式的，只需要定义一个接口，用注解声明要调哪个服务、用什么 HTTP 方法、传什么参数，框架自动帮你发请求、处理响应。代码集中在一个接口里，可读性和可维护性都更好。而且 Feign 内置了负载均衡，从 Nacos 拿到实例列表后自动轮询，RestTemplate 要实现同样的功能得自己整合 Ribbon 或 LoadBalancer，比较麻烦。
>
> 所以微服务项目里推荐用 Feign，RestTemplate 更适合简单测试或者非微服务的单体项目。

### 💡 什么是负载均衡？

一个服务（如 `mall-pay-service`）通常部署了**多台机器**（多个实例），每台机器有不同的 IP:Port。  
负载均衡就是 **"把请求分散到不同的机器上，避免某一台被打爆"**。

```
                     ┌─▶ 192.168.1.10:8080（实例1）
mall-order-service ──┼─▶ 192.168.1.11:8080（实例2）
  （调用方）          └─▶ 192.168.1.12:8080（实例3）
```

**Feign 中的负载均衡**：Feign 从 Nacos 拿到目标服务的所有实例列表后，用负载均衡策略选一台来调用。

常见策略：

| 策略 | 含义 | 类比 |
|------|------|------|
| **轮询（Round Robin）** | 按顺序轮流分发 | 三个窗口轮流叫号 |
| **最少连接（Least Connections）** | 谁闲派给谁 | 哪个窗口排队人少去哪个 |
| **随机（Random）** | 随机挑一台 | 闭着眼睛选一个窗口 |
| **一致性哈希（Consistent Hash）** | 同一个用户固定走同一台 | 老顾客固定找同一个理发师 |

### Q2：Feign 调用超时怎么处理？

项目中出现过：支付回调涉及分布式事务，处理时间长，导致 Feign 默认超时（1s）触发异常。

**解决**：配置长事务接口的超时时间

```yaml
feign:
  client:
    config:
      order-service:              # 指定服务
        connectTimeout: 30000     # 下单长事务设 30s
        readTimeout: 600000       # 600s
```

**下游配合**：接口要做幂等，防止重试导致重复下单。

**口述版**：

> **面试官**：Feign 调用超时了怎么排查？

> 首先看报什么错——如果是 `Connect timed out`，那是网络不通或者目标服务挂了；如果是 `Read timed out`，是目标服务处理太慢。
>
> 排查的时候先看链路追踪，Zipkin 上一眼就能看到哪个环节耗时最长。然后查目标服务的日志和监控，有没有慢 SQL、死锁或者 Full GC。
>
> 如果确认是超时配置的问题，就把对应的超时时间调大。我们项目里下单这种长事务接口，readTimeout 设到了 10 分钟。但要提醒的是，超时不能设得太大，目标服务挂了的话调用方会一直傻等，线程池被占满，最终调用方也会崩溃。所以最好是按接口类型区分配置——查询类设短一点，事务类设长一点。另外下游接口要做好幂等，防止超时重试导致重复请求。
>
> 如果超时问题频繁出现，就得考虑加 [[Sentinel详解|Sentinel]] 熔断了，下游失败率超过阈值就直接切断，快速返回降级结果。

### Q3：Feign 如何实现服务间调用的链路追踪？

集成 **Sleuth** /sluːθ/（斯鲁斯） + **Zipkin** /ˈzɪp.kɪn/（吉普金），实现微服务调用链路的可视化追踪。

#### 解决了什么问题？

微服务架构中，一个请求会跨多个服务：

```
用户请求 → Gateway → OrderService → PayService → UserService
```

出问题了（超时、报错），你没法知道**是哪一步慢了、在哪一步断了**。链路追踪就是给整个请求打一个"身份证号"（TraceId），串联所有服务。

#### 两个组件各管什么

| 组件         | 角色                                                                                                                          | 通俗理解                       |
| ---------- | --------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| **Sleuth** | 为每个请求分配全局唯一的 TraceId，在服务间调用时通过 HTTP 请求头自动向下游透传；记录每个 Span（调用单元）的耗时、服务名、状态等元数据，形成完整的调用链拓扑                                   | 给每个请求发一个"身份证号"，经过每个服务时自动记录 |
| **Zipkin** | 通过 HTTP 或消息队列接收各服务上报的 Span 数据，将其持久化存储（支持内存/MySQL/Elasticsearch），提供 Web UI 进行调用链查询、耗时分析、依赖拓扑图展示，支持按 TraceId、服务名、耗时、状态码等多维度检索 | 把所有服务的"身份证记录"汇总到一个看板，可视化展示 |
|            |                                                                                                                             |                            |

#### 工作流程

```
用户请求
  │  Sleuth 生成 TraceId = abc123
  ▼
Gateway ──▶ OrderService ──▶ PayService ──▶ UserService
  │            │               │               │
  │ Sleuth 自动 │ Sleuth 自动    │ Sleuth 自动    │ Sleuth 自动
  │ 记录耗时     │ 记录耗时       │ 记录耗时       │ 记录耗时
  └────────────┴───────────────┴──────────────┴────────────▶ Zipkin（汇总展示）
```

**Feign 的角色**：Sleuth 利用 Feign 的请求拦截器，自动把 `TraceId` 塞进 HTTP 请求头中，传给下游服务。下游服务的 Sleuth 从请求头中取出 TraceId，继续透传。

```
Feign 发请求时，自动在 HTTP Header 中注入：
  X-B3-TraceId: abc123
  X-B3-SpanId: def456
  X-B3-ParentSpanId: 789xyz

下游服务收到后，取出继续往下传 → 整条链路串起来了
```

#### 配置方式

```yaml
spring:
  sleuth:
    feign:
      enabled: true       # 开启 Feign 的链路透传（默认就是 true）
    sampler:
      probability: 1.0    # 采样率：1.0=全部采样，0.1=采样10%
```

**采样率说明**：
- 生产环境设 `0.1`（10%），避免 Zipkin 存储压力
- 调试环境设 `1.0`（100%），不漏掉任何一次调用

#### 在 Zipkin 界面能看到什么

```
TraceId: abc123
  Service     | Span        | 耗时  | 状态
  Gateway     | 转发请求      | 5ms   | ✅
  OrderService| 创建订单      | 200ms | ✅
  PayService  | 调用支付      | 1.5s  | ❌ 超时  ← 一眼找到慢在哪
  UserService | 查询用户      | -     | ⏸ 未执行
```

#### 面试话术

> **面试官**：你们怎么做链路追踪的？

**口述版**：

> 我们用的是 Sleuth + Zipkin，Spring Cloud 原生生态，集成起来很简单。
>
> 原理是这样的：Sleuth 给每个请求生成一个全局唯一的 TraceId，当一个服务调用另一个服务的时候，Feign 的请求拦截器会自动把这个 TraceId 塞进 HTTP 请求头里，传给下游。下游收到后从请求头里取出 TraceId，继续往下传，整条链路上的每个服务都有了同一个 TraceId。
>
> 然后每个服务把自己的调用信息（耗时、服务名、状态）上报给 Zipkin，Zipkin 把所有上报的数据汇总起来，按 TraceId 关联，就能在界面上看到一条完整的调用链，哪个环节慢、哪个环节报错了，一目了然。
>
> 采样率的话，生产环境设 10%，调试环境设 100%。效果就是从之前出问题要逐台翻日志，变成打开 Zipkin 看板就能定位，排查效率高很多。

### Q4：Feign 和 Dubbo /ˈdʌbəʊ/（达波）有什么区别？

| 对比项 | Feign | Dubbo |
|--------|-------|-------|
| 协议 | HTTP（应用层） | TCP（传输层） |
| 性能 | 一般（HTTP 序列化/反序列化） | 高（二进制协议） |
| 兼容性 | 好（HTTP 可被网关/Nginx 识别） | 差（需 Dubbo 协议支持） |
| 生态 | Spring Cloud 全家桶 | Apache Dubbo 生态 |

#### 核心区别一：通信协议不同

```
Feign（HTTP）
  请求：HTTP 报文（JSON/XML）
  特点：文本协议，可读性强，但传输效率低

Dubbo（TCP）
  请求：二进制流（Hessian2 序列化）
  特点：二进制协议，传输量小，性能高
```

**通俗理解**：
- Feign 像寄**快递**（包裹里有纸条写明地址和内容）— 笨重但谁都能看懂
- Dubbo 像打**摩斯密码**（嘀嗒嘀嗒直接传）— 轻快但需要双方约定好解码规则

#### 核心区别二：兼容性不同

**兼容性**指：请求能不能被中间设备（Nginx、网关、防火墙）识别和转发。

```
Feign（HTTP）
  Nginx 看到的是：POST /pay  HTTP/1.1
                  Content-Type: application/json
                  {"orderId": 123}
  Nginx 可以：✅ 按路径路由  ✅ 限流  ✅ 记日志  ✅ 缓存响应
  网关可以：  ✅ 鉴权  ✅ 灰度发布  ✅ 熔断
  —— 因为 HTTP 是标准协议，所有网络设备都认识

Dubbo（TCP 二进制）
  Nginx 看到的是一堆二进制乱码：\x00\x01\x02\xcafebabe...
  Nginx 看不懂：❌ 没法路由  ❌ 没法限流  ❌ 没法记日志
  —— 必须专门搭 Dubbo 网关做协议转换，才能对外暴露
```

**对本项目的影响**：
- 使用 Feign（HTTP），Nginx 可以直接识别和转发请求
- Gateway 可以直接基于 URL 做限流、鉴权，无需额外协议转换
- 如果换成 Dubbo，需要在 Gateway 层加 Dubbo 协议转换，增加复杂度

#### 核心区别三：架构模式不同

```
Feign：RESTful 风格
  └─ 调用方直接 HTTP 调用目标服务
  └─ 通过 Nacos/Eureka 做服务发现
  └─ 单体架构也能用，对外接口也是 HTTP

Dubbo：RPC 风格（Remote Procedure Call）
  └─ 调用方像调本地方法一样调远程
  └─ 通过 ZooKeeper/Nacos 做服务注册
  └─ 对外暴露 Dubbo 协议，不适合直接给前端调
```

#### 对比表格详解

| 对比项      | Feign                     | Dubbo                   |
| -------- | ------------------------- | ----------------------- |
| **协议**   | HTTP + JSON               | TCP + Hessian2/Protobuf |
| **性能**   | 一般（序列化/反序列化开销大）           | **高**（二进制，体积小速度快）       |
| **兼容性**  | **好**（HTTP 可被 Nginx、网关识别） | 差（需要 Dubbo 网关做协议转换）     |
| **跨语言**  | 好（HTTP 标准协议，任何语言都能调）      | 差（强依赖 Java 生态）          |
| **适用场景** | 微服务对外接口、异构系统集成            | 内部服务间高频调用、对性能要求极高       |
| **连接管理** | 短连接（每次请求新建或复用连接池）         | **长连接**（复用 TCP 连接，减少握手） |

#### 面试话术

> **面试官**：你们为什么用 Feign 不用 Dubbo？

**口述版**：

我们项目用的是 Spring Cloud Alibaba 生态，当时选型的时候主要考虑了三个因素。

第一个是**业务场景**。我们服务间的调用全是 HTTP 接口，走的是 Nacos 做服务发现、Gateway 做流量管控这套架构。HTTP 的好处是网关、Nginx、WAF 这些中间设备都能直接识别和转发，不用做任何协议转换。如果用 Dubbo 的话，这些中间设备看到的是二进制乱码，得专门搭一套 Dubbo 网关做协议转换，就多了一层复杂度。

第二个是**性能需求**。我们电商场景的 QPS 大概在几千级别，HTTP 虽然序列化开销比 Dubbo 大，但几千 QPS 完全扛得住。Dubbo 的二进制协议性能确实更高，但在我们的业务规模下，这点性能差异换不来足够的收益。

第三个是**开发效率**。Feign 是声明式的，定义一个接口加几个注解就能调用远程服务，代码非常简洁。Dubbo 虽然功能更强，但要配置的东西也多，对我们这个项目来说有点重了。

**所以结论就是**：Feign 对我们来说够用、好用、接入成本低。当然如果未来内部服务间的调用量涨到十万甚至百万级 QPS，那我们会考虑用 Dubbo 做性能优化，到那时候性能收益才值得去承担协议转换和运维的额外成本。

---

## 本项目中的 Feign 使用

| 调用方 | Feign 接口 | 目标服务 |
|--------|-----------|---------|
| Canal Client | `SearchServiceFeign` | search-service |
| Canal Client | `PageServiceFeign` | page-service |
| 订单服务 | `PayServiceFeign` | pay-service |
| 支付服务 | `OrderServiceFeign` | order-service |

---

## 常见 Bug（面试问过）

### 1. Feign 调用超时 — `Read timed out`

**现象**：调用远程接口时抛出 `Read timed out` 或 `Connect timed out` 异常。

**原因**：Feign 默认超时很短（connectTimeout 通常 10s，readTimeout 通常 60s）。下单/支付等长事务接口处理耗时可能超过这个值。

**解决**：
```yaml
feign:
  client:
    config:
      order-service:              # 针对慢接口单独配置
        connectTimeout: 30000     # 连接超时 30s
        readTimeout: 600000       # 读取超时 10min（下单长事务）
```

**追问**：超时设太长会不会有问题？
- 会。如果目标服务彻底挂了，调用方会一直傻等，占着线程池不释放 → 可能导致调用方自己也挂掉
- **折衷**：区分接口类型——查询类设短（5~10s），事务类设长（120s+）

### 3. FeignClient 找不到 — 启动报错

**现象**：启动报 `Autowired` 失败，提示 `PayServiceFeign` 类型的 Bean 找不到。

**原因**：`@EnableFeignClients` 没有扫描到 Feign 接口所在的包。

**解决**：
```java
// 正确写法：指定 Feign 接口所在的包路径
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.mall.order.feign", "com.mall.common.feign"})
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

**检查顺序**：
1. `@EnableFeignClients` 加了没有？
2. `basePackages` 是否覆盖了 Feign 接口的包路径？
3. Feign 接口上的 `@FeignClient(name = "xxx")` 服务名正确吗？

---

## 面试话术

### 场景一：为什么选 Feign？

> **面试官**：你们服务间调用用的什么？为什么选 Feign？

**回答要点**：
1. **选型**：项目采用 Spring Cloud Alibaba 生态，Feign 是其中声明式 HTTP 客户端标准方案
2. **开发效率**：定义接口 + 注解即可调用远程服务，无需手写 HTTP 请求代码
3. **配合 Nacos**：服务名自动解析为 IP:Port，天然集成负载均衡
4. **配置灵活**：超时时间、压缩、日志级别可按服务独立配置
5. **对比 RestTemplate**：代码简洁、URL 集中管理、更符合微服务规范

### 场景二：Feign 调用超时了怎么排查？

> **面试官**：线上 Feign 调用突然超时了，你怎么排查？

**回答思路**（层层递进）：
1. **确认现象**：报 `Read timed out` 还是 `Connect timed out`？
   - `Connect` 超时 → 网络不通或目标服务挂了
   - `Read` 超时 → 目标服务处理太慢
2. **查看监控**：Zipkin 链路看哪个环节耗时最长
3. **检查日志**：目标服务有没有慢 SQL、死锁、Full GC
4. **调整配置**：确认当前超时配置是否合理，长事务是否单独设了较大的值
5. **下游容错**：加 Sentinel 熔断降级，防止调用方被拖垮

   > **为什么下游变慢会拖垮调用方？**
   >
   > 调用方的线程池是有限的（Tomcat 默认 200 线程）。当下游响应变慢时，发出去的请求迟迟不返回，线程就被一直占着不放。
   >
   > ```
   > 正常：线程1 调支付 → 100ms 返回 ✅ → 释放线程
   > 变慢：线程1 调支付 → 30s... 还在等 ❌ → 线程被占住
   >       线程2 调支付 → 30s... 还在等 ❌ → 线程被占住
   >       ... 200 个线程全部被堵死 → 新请求进不来 → 调用方也挂了
   > ```
   >
      > **连锁反应（雪崩效应）**：
   > ```
   > 支付服务变慢
   >   → 订单服务调支付，200 线程全被堵住
   >     → 前端用户请求网关，网关调订单服务
   >       → 订单服务已无空闲线程处理，请求在订单服务队列里排队
   >         → 网关的线程也一直等订单服务响应，越积越多
   >           → 网关的连接池/线程池也被占满
   >             → 前端请求进不来 → 整个系统崩溃 💥
   > ```
   > 就像一个高速公路连环追尾：第一辆车（支付）突然急刹 → 后面的车（订单）跟着刹 → 再后面的车（网关）也来不及躲 → 全线瘫痪。
   >
   > **Sentinel 熔断机制**：检测到下游接口 10s 内失败率 > 50%，直接切断请求，快速返回降级结果，不让线程继续等下去。

### 场景三：Feign 和 Dubbo 怎么选？

> **面试官**：如果让你设计一个新项目，Feign 和 Dubbo 你怎么选？

**决策思路**：

| 条件 | 推荐 | 原因 |
|------|------|------|
| Spring Cloud 生态 | **Feign** | 原生集成，声明式调用 |
| 内部服务 QPS 极高（十万+） | **Dubbo** | TCP 二进制协议性能更好 |
| 需要对外暴露 HTTP 接口 | **Feign** | HTTP 兼容性好，网关直接转发 |
| 异构系统（多语言） | **Feign** | HTTP 标准协议，跨语言友好 |
| 已有 Dubbo 基础设施 | **Dubbo** | 保持技术栈一致性 |

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[06-其他杂项/CDC（Canal）详解]] — Feign 常用于 Canal Client 调用下游
- [[06-其他杂项/Nacos详解]] — Feign 的服务名解析依赖 Nacos 注册中心
- [[分布式事务 Seata]] — 长事务场景的 Feign 超时配置
