---
tags:
  - sentinel
  - 限流
  - 熔断降级
  - 微服务
  - spring-cloud-alibaba
created: 2026-09-05
updated: 2026-09-05
source: project-study/sentinel.md
---
# Sentinel — 流量治理与熔断降级详解

## 一句话定义

> **Sentinel**（Alibaba 开源，现属 Apache 孵化生态）是面向**分布式服务**的**流量治理组件**，以「流量」为切入点，从**限流、熔断降级、系统自适应保护、热点参数防护**几个维度保障服务稳定性。
>
> 定位对比：Hystrix 只做熔断，Sentinel 是「限流 + 熔断 + 降级 + 系统保护 + 可视化控制台」的一整套，且规则可**实时推送生效**。

**本项目实现**：Spring Cloud Gateway + Sentinel 做网关层限流（路由粒度 + 自定义 API 分组），规则通过 Nacos 持久化。⚠ 业务层熔断降级是硬伤，见 [[#硬伤预案：业务层零 @SentinelResource]]。

---

## 📌 考点速查（背之前先看这张表）

| 面试官问什么 | 跳到哪一节 | 一句话答案 |
|------|------|------|
| Sentinel 限流用的什么算法 | [[#一、统计基础：滑动窗口（LeapArray）]] | 滑动窗口（LeapArray 分桶计数），不是令牌桶；令牌桶是它的「匀速排队」效果在用 |
| 阈值类型有哪些 | [[#2.1 阈值类型：QPS vs 并发线程数（grade）]] | QPS 和**并发线程数**两种，默认 QPS；并发线程数对 RT 波动更鲁棒 |
| 流控模式 / 流控效果分不清 | [[#2.2 流控关系（strategy）：直接 / 关联 / 链路]] + [[#2.3 流控效果（controlBehavior）：四种]] | strategy 管「谁触发限谁」，controlBehavior 管「超额的请求怎么处理」 |
| 快速失败 / Warm Up / 排队等待 | [[#2.3 流控效果（controlBehavior）：四种]] | 0 直接拒绝、1 预热（阈值从 count/3 爬坡）、2 漏桶匀速排队、3 预热+排队 |
| 熔断有哪几种策略 | [[#三、熔断降级（DegradeRule）：三策略与状态机]] | 慢调用比例 / 异常比例 / 异常数，都带 CLOSED→OPEN→HALF_OPEN 状态机 |
| 熔断和降级的区别 | [[#3.4 熔断 vs 降级]] | 熔断是被动故障保护（有状态机、会自动恢复），降级是主动策略（要自己决定何时结束） |
| Sentinel 和 Hystrix 怎么选 | [[#四、Sentinel vs Hystrix]] | 关键在**隔离方式**：Hystrix 线程池隔离，Sentinel 信号量（并发计数）隔离；Hystrix 已停更 |
| 规则重启就丢了怎么办 | [[#5.4 规则持久化（Nacos，生产必问）]] | 规则以 Nacos 为准 + push 模式监听，控制台改成只读 |
| 和 Gateway 怎么集成 | [[#五、本项目实战：Gateway + Sentinel]] | `sentinel-spring-cloud-gateway-adapter`，按 route id 建资源，`GatewayCallbackManager` 换 429 响应 |

---

## 核心架构：SlotChain 责任链

一次资源调用的所有判断，都跑在一条**插槽责任链**上（`ProcessorSlotChain`），按顺序大致是：

```
NodeSelectorSlot   → 按资源名构建调用树节点（DefaultNode）
ClusterBuilderSlot → 构建集群维度节点（ResourceWrapper 全局统计）
StatisticSlot      → 调滑动窗口做计数（QPS、RT、线程数、成功/异常数）
SystemSlot         → 系统级自适应保护（Load、总 QPS、总线程数）
AuthoritySlot      → 黑白名单授权
FlowSlot           → 流控规则判断，超限抛 BlockException
DegradeSlot        → 熔断规则判断，熔断中抛 DegradeException
```

**因果链（面试要说出这个顺序）**：必须**先有统计才有判断**，所以 `StatisticSlot` 排在 `FlowSlot`、`DegradeSlot` 之前——滑动窗口先记账，流控和熔断槽再拿账本比对阈值。规则本身不存储在链上，链上只做「读统计 + 比规则 + 抛异常」。

> [!tip] 可扩展性话术
> "Sentinel 的可扩展点就是插槽：SPI 机制（`ProcessorSlotChain` 通过 `SlotChainBuilder` 装配），要加一个自定义保护逻辑，实现 `ProcessorSlot` 并用 `@SpiOrder` 指定插入位置就行。"

---

## 一、统计基础：滑动窗口（LeapArray）

### 1.1 为什么不用固定窗口、漏桶、令牌桶

| 算法 | 缺陷 | Sentinel 的态度 |
|------|------|------|
| 固定窗口计数 | **临界突刺**：窗口切换瞬间可以放行 2 倍流量（两个窗口边界各打满） | 不用，改用滑动窗口 |
| 漏桶 | 流出速率恒定，**无法应对合法的短时突发**（秒杀、活动开抢） | 只在「匀速排队」这一种效果里用 |
| 令牌桶 | 允许突发，但桶满后瞬时全放，冷启动时服务还没预热就被打满 | 不作为主统计模型 |

**通俗理解**：固定窗口像"每小时放行 100 人，整点清零"——59 分放 100 个、01 分再放 100 个，两分钟实际过了 200 人。滑动窗口像"任意连续 60 秒内不超过 100 人"，窗口是**贴着时间往前滑**的。

### 1.2 Sentinel 的实现：LeapArray 分桶

Sentinel 不可能记录每一个请求的时间戳（内存会爆），所以用**分桶近似滑动窗口**——把时间窗口切成若干小格（bucket），每格存一个 `WindowWrap`（起止时间 + 计数器 `LongAdder`/`RetentionData`），窗口整体向前"跳格"。

关键结构（`com.alibaba.csp.sentinel.slots.block.flow.controller` 之下的 `LeapArray` / `ArrayMetric`）：

- **秒级统计**：窗口总长 1000ms，默认切成 2 个格子，每格 500ms
- **分钟级统计**：窗口总长 60000ms，切成 60 个格子，每格 1s
- 一格存哪些指标：请求数、通过数、异常数、**RT 累加值**、被阻塞数、**当前并发线程数**、按区间分桶的 RT 分布（`RetentionData`）
- 老格子过期后**原地复用**（`resetTo`），不再分配新对象——这是 Sentinel 能把统计开销压到极低的根本原因

**RT 是按区间分桶统计的，不是精确记录每个请求耗时**，所以「慢调用比例」本质是近似值。被追问精度时可以主动交代这一点，比背错具体分桶边界要安全。

> [!warning] 数字保守说法
> "默认 1s 窗口、切 2 个 500ms 的格子"这两个数字来自 `Constants.SAMPLE_COUNT`（可配）。**记不准就别报格子数**，说"滑动窗口按固定长度的小格分桶、窗口整体前滑、过期格子原地复用"——机制说清楚，分数一分不丢。

### 1.3 并发线程数不走窗口

并发线程数（`FLOW_GRADE_THREAD`）统计的是**当前瞬时值**，用 `ThreadLocalCounter`/`runningCount` 直接加减，和时间窗口无关。所以「线程数模式」没有窗口误差，也不会被临界突刺影响——这是它比 QPS 模式更鲁棒的原因之一。

### 1.4 面试话术

"Sentinel 的限流底层是**滑动窗口**，具体实现叫 LeapArray：把统计窗口切成固定长度的小格子，每格存通过数、异常数、RT 累加值、并发线程数这些指标，窗口随时间向前滑，过期格子原地重置复用，所以内存开销是常数级、不会随流量增长。之所以不用固定窗口，是因为固定窗口有临界突刺问题——两个窗口边界各打满就等于放行两倍流量。有意思的是**它把令牌桶、漏桶这些算法放在了'限流效果'层**：匀速排队那种模式内部就是漏桶。而并发线程数这个阈值类型根本不查窗口，它统计的是瞬时值，所以也没有窗口误差。"

---

## 二、流控（FlowRule）：阈值类型 × 流控关系 × 流控效果

**三个维度必须分清，混了就答不全**：

| 维度 | 字段 | 回答的问题 | 可选值 |
|------|------|------|------|
| 阈值类型 | `grade` | 拿什么和阈值比 | QPS=1（默认）、并发线程数=0 |
| 流控关系 | `strategy` | 谁超限时限谁 | 直接=0、关联=1、链路=2 |
| 流控效果 | `controlBehavior` | 超额的请求怎么处理 | 直接拒绝=0、预热=1、匀速排队=2、预热+排队=3 |
| 阈值 | `count` | 阈值本身 | 单位随 grade 变 |

### 2.1 阈值类型：QPS vs 并发线程数（grade）

- **QPS 模式**（`RuleConstant.FLOW_GRADE_QPS`）：单位时间通过的请求数。需要配合 `intervalSec` 一起理解——`GatewayFlowRule` 里就是 `count / intervalSec` 才是真正的速率（count=100、intervalSec=10 等价于每秒 10 次）。
- **并发线程数模式**（`FLOW_GRADE_THREAD`）：count 是**同一时刻允许处于该资源内部的线程数**，超了直接拒绝。

**为什么并发线程数更好用（面试重点，讲因果）**：QPS 阈值是**依赖 RT 的隐式约定**——你按"RT 50ms、单机能扛 200 QPS"配了 count=200；下游一旦劣化 RT 涨到 500ms，200 QPS 就意味着 100 个线程同时挂在这个接口上，Tomcat 200 个工作线程直接被吃掉一半，别的接口跟着一起死。**QPS 挡住了流量，没挡住线程占用**。并发线程数直接保护的是真正稀缺的资源（线程），RT 变差时它自动收紧通过量，不需要人跟着改配置。

**话术**："我配阈值时的原则是：接口 RT 稳定、下游可控的用 QPS，语义直观好和运营解释；RT 波动大或者下游是第三方接口、可能被打慢的，用并发线程数，因为它保护的是线程资源本身——RT 一恶化，QPS 限流挡不住线程被占满，并发数限流会自动把通过量压下来。"

### 2.2 流控关系（strategy）：直接 / 关联 / 链路

- **直接（DIRECT）**：资源自己超限限自己。最常用。
- **关联（RELATE）**：限的是**另一个**资源——`limitRef` 指向的资源 QPS 超阈值时，把当前资源掐掉。典型场景：读接口和写接口抢同一份资源，写压力大时优先保读、限写？反过来更常见——**推荐场景**：商品详情页（读）流量暴涨时，把「下单」（写）这条链路限掉，把 DB 容量留给读。
- **链路（CHAIN）**：只统计**从指定入口进来**的调用量。同一个 `/a` 被 `/entry1 → /a` 和 `/entry2 → /a` 两条链路共用时，可以只针对 entry1 这条来源限流。

### 2.3 流控效果（controlBehavior）：四种

#### 0 = 直接拒绝（快速失败，DEFAULT）

超阈值立刻抛 `FlowException`，不等待不排队。这是默认值。**适用**：对延迟敏感、宁可报错也不能等的接口。

Gateway 场景可配 `setBurst(5)`：允许瞬时突发超出 5 个再拒绝。

#### 1 = Warm Up（冷启动 / 预热）

```java
.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
.setWarmUpPeriodSec(10)   // 爬坡时长
```

服务刚启动时**允许的 QPS 从低位逐渐爬升到 count**，内部换算基于 **Guava `SmoothWarmingUp` 同源思路**：默认 `coldFactor = 3`，也就是**初始允许阈值 = count / 3**，在 `warmUpPeriodSec` 内线性/指数式抬到 count。

**为什么需要（因果链）**：冷启动时 JVM 还没 JIT 充分预热、本地缓存（Caffeine）是空的、连接池还没建立、Spring 懒加载 Bean 没初始化——这时候按满阈值放流量进来，等于让一个"半睡"的进程处理满负荷请求，RT 直接飙升、连锁触发超时和熔断。**预热不是保护下游，是保护自己。**

**适用**：缓存类服务、批处理任务长期空闲后被流量突然唤醒、JVM 冷启动。

#### 2 = 匀速排队（RATE_LIMITER，漏桶）

```java
.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)
.setMaxQueueingTimeMs(500)   // Gateway 侧是 setMaxQueueingTimeoutMs
```

请求不立即拒绝，而是按 `1000ms / count` 的固定间隔排队放行——**这就是漏桶**。等待超过最长排队时间才拒绝（抛 `FlowException`，带 `isUsingObserver()` 之类的判断）。

⚠ **两个必讲的坑**：
1. **匀速排队会忽略窗口长度**，只看 `count`，内部换算成最小间隔（count=20 → 每 50ms 放行一个）。
2. **Sentinel 官方明确这个模式不建议用于 QPS 波动大的 Web 接口**（排队等于把耗时累加到 RT 上，容易雪崩），更适合**消息消费削峰、写库削峰**这类能容忍延迟的场景。

#### 3 = 预热 + 排队（WARM_UP_RATE_LIMITER）

两者叠加：冷启动爬坡 + 匀速放行。**面试加分点**——大多数候选人只说三种，第四种是 `RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER`。

### 2.4 另外两类常被忘的流控

- **热点参数限流**（`ParamFlowRule`）：按**参数值**分别统计，比如「同一个商品 ID 的 QPS 限 100」「同一个用户 ID 每分钟限 20 次」。底层是 LRU + 每个参数值一个滑动窗口（`HotParamLeapArray`），支持例外项（`paramFlowItemList` 给某个特殊值单独配更高阈值）。本项目热点商品防护就是这条路（配合 Druid 统计）。
- **系统自适应保护**（`SystemRule`）：入口总流量的兜底，按 **Load1 / 平均 RT / 并发线程数 / 总 QPS** 四个维度触发，用的是 BBR 式自适应思路（不是拍一个固定值）。**话术**："系统规则是最后一道防线，它不看单个接口，看整机负载，防止前面规则都没覆盖到、机器已经先扛不住了。"
- **集群限流**：默认所有阈值都是**单机维度**（N 台机器 × count）。要做全局阈值得开集群流控，需要一个 Token Server 汇总计数——多一次 RPC、有单点问题，本项目没用，面试按"知道成本所以没上"来答。

### 2.5 面试话术（整段）

"FlowRule 我会拆成三个维度讲，因为很容易混。第一是**阈值类型**，QPS 或者并发线程数，默认 QPS，但线程数在 RT 会抖动的场景更稳，因为它保护的是线程本身。第二是**流控关系**，直接、关联、链路——关联是'A 超了限 B'，比如读的量大到影响写的时候用它做取舍；链路是同一个资源被多个入口共用时只针对某个入口限。第三是**流控效果**，四种：直接拒绝是默认；Warm Up 是冷启动保护，阈值从 count 除以 3 开始爬到设定值，本质是保护自己别在半热的 JVM 上被打满；匀速排队就是漏桶，间隔是 1000 除以 count 毫秒，它会忽略窗口长度，而且官方不建议用在 Web 接口，适合削峰；最后还有预热加排队的组合。除了这三层，还有热点参数限流按参数值分别统计，系统自适应保护看整机负载兜底，集群限流要 Token Server、有 RPC 开销，我们没用。"

---

## 三、熔断降级（DegradeRule）：三策略与状态机

### 3.1 三种熔断策略

| 策略 | 常量 | count 的含义 | 触发条件 |
|------|------|------|------|
| **慢调用比例** | `CircuitBreakerStrategy.SLOW_REQUEST_RATIO`（1.8 前叫 `DEGRADE_GRADE_RT`） | **RT 阈值（ms）** | 统计窗口内 RT 超过该阈值的调用占比 ≥ `slowRatioThreshold` |
| **异常比例** | `ERROR_RATIO` | **比例 0~1** | 窗口内异常数 / 请求数 ≥ count |
| **异常数** | `ERROR_COUNT` | **次数** | 窗口内异常数 ≥ count（窗口必须正好 1 分钟时才等价于"每分钟异常数"） |

⚠ **1.8 版本前后 `DEGRADE_GRADE_RT` 的 count 语义变了**：1.8 之前 count 是「慢调用比例（0~1）」，1.8 之后 count 改成「RT 阈值 ms」、比例另用 `slowRatioThreshold`。笔记里 1.8+ 的写法是：

```java
DegradeRule rule = new DegradeRule("order-service")
        .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
        .setCount(0.5)             // 异常比例超 50%
        .setMinRequestAmount(10)   // 窗口内至少 10 个请求才判定
        .setStatIntervalMs(10000)  // 统计窗口 10s
        .setTimeWindow(30);        // 熔断 30s，之后半开试探
DegradeRuleManager.loadRules(rules);
```

**为什么「慢调用比例」这个策略最有用（因果）**：下游没挂但变慢，是最难发现也最容易雪崩的状态——异常比例是 0，一条规则都不触发，但线程正在被慢慢耗光。所以 Sentinel 把 RT 也纳入熔断触发条件，这恰好是它比 Hystrix（只看错误率）强的地方。

### 3.2 三个公共参数

- `statIntervalMs`：统计窗口，默认 **1000ms**。
- `minRequestAmount`：**最小请求数**，默认 **5**。窗口内请求太少不做判定——防止"只有 1 个请求且失败"就把服务熔断掉（低频接口最容易踩）。
- `timeWindow`：熔断**时长（秒）**，到期进入半开。

### 3.3 状态机：CLOSED → OPEN → HALF_OPEN

```
CLOSED（正常放行，同时记账）
   │  窗口内命中阈值
   ▼
OPEN（熔断中，直接抛 DegradeException → 走 fallback 降级）
   │  timeWindow 到期
   ▼
HALF_OPEN（半开：只放一个探测请求）
   ├── 探测成功（未超阈值）→ 回 CLOSED，清空统计
   └── 探测失败 → 重新 OPEN，再等一个 timeWindow
```

**实现细节（加分）**：半开不是定时任务，Sentinel 是**请求驱动**的——熔断期间来的请求顺手判断一下"是不是已经到期了"，到期的那个请求抢 `tryAcquire` 变成探测请求。所以**没有流量时永远不会自动恢复**，这一点和 Hystrix 一致，也是面试可以反问回去的深度点。

### 3.4 熔断 vs 降级

| | 熔断 | 降级 |
|---|---|---|
| 触发方 | 框架自动（达到阈值） | 可自动（fallback）也可人工（开关、限流后主动简化） |
| 有没有状态机 | 有，会自动半开恢复 | 没有，取决于业务什么时候撤 |
| 关注点 | 防止**故障扩散**（不继续打下游） | 保证**核心链路可用**（非核心让路） |
| 关系 | 熔断是降级的一种触发手段 | 降级是熔断之后的**处理结果** |

**话术**："熔断是被动的故障保护，有状态机会自动恢复；降级是主动的资源取舍，可以人工开关。熔断之后一定要有降级逻辑接住，不然熔断只是把异常换了个类型抛出来，用户看到的还是错误页。"

**fallback 三种写法**：`@SentinelResource(fallback=)`（同类型、可拿到原参数）、`blockHandler=`（**只处理 BlockException 系列**，方法签名必须多一个 `BlockException` 参数）、`defaultFallback=`（兜底、不区分异常）。⚠ 常见坑：`fallback` 默认**不捕获 `BlockException`**（它管业务异常），限流/熔断要走 `blockHandler`——或者用 `exceptionsToIgnore` 配。

---

## 四、Sentinel vs Hystrix

### 4.1 能力对比

| 维度 | Hystrix | Sentinel |
|------|------|------|
| 现状 | **2018 年官方进入维护模式**，无新功能 | 活跃迭代，Spring Cloud Alibaba 默认方案 |
| 核心能力 | 只有熔断降级 | 限流 + 熔断降级 + 系统保护 + 热点参数 + 授权规则 |
| 隔离方式 | **线程池隔离**（也可选信号量） | **并发计数（信号量式）隔离**，不额外建线程池 |
| 熔断触发依据 | 错误率（默认 10s 窗口、≥20 个请求、错误率 ≥50% 打开，sleepWindow 5s 后半开） | 慢调用比例 / 异常比例 / 异常数，窗口默认 1s |
| 规则下发 | 配置文件为主，改动需发布 | **控制台实时修改 + DataSource 推送到客户端** |
| 观测 | Turbine 聚合 dashboard（无实时曲线） | 控制台秒级实时曲线、调用拓扑 |
| 编程模型 | RxJava 异步（`HystrixCommand`） | 注解 / `SphU.entry()` / 各框架 adapter（Feign、Gateway、Dubbo） |
| 依赖包袱 | 引入 RxJava、Netflix 组件全家桶 | 轻量，核心 jar 一个 |

### 4.2 隔离方式才是最深的一条

**Hystrix 线程池隔离**：每个依赖（`HystrixCommandKey`）一套独立线程池 + 队列，业务调用被提交到那个池的线程里异步执行，主线程用 `Future.get(timeout)` 等。

- ✅ 真隔离：下游再慢也只吃掉自己那个池，不污染宿主线程；超时由 Hystrix 控制，到点直接返回降级。
- ❌ 代价：一次调用多两次**线程切换**（提交 + 回调），高 QPS 下开销显著；线程池数量 = 依赖数，参数（核心线程、队列长度、超时）要一个个调，调错就是"池子没满但请求全在排队"或者池子小了误杀。

**Sentinel 的信号量式隔离**：**不新建线程**，就在业务线程里跑，只在进入资源时对「当前并发线程数」这个计数器 +1 / -1，超阈值直接抛 `BlockException`。

- ✅ 零线程切换开销，同线程内判断纳秒级；不需要维护 N 个池的参数。
- ⚠️ **它挡不住"线程被下游拖死"**——线程还是那个 Tomcat 工作线程，下游 RT 5 秒它就实打实等 5 秒。Sentinel 能在超过并发阈值后**拒绝新请求**，但已经放进去的、卡在 IO 上的线程它救不回来。要真正"快速返回"，必须叠加**超时控制**（Feign/Ribbon 的 `readTimeout`、Future 超时）——这也正是 [[云商城已知硬伤与追问预案#6]] 里 Feign 超时没配够会踩的雷。

**所以准确的对比说法不是"谁比谁强"，而是分工**：

> "Hystrix 用线程池把故障**关**在池子里，代价是线程切换和一堆参数要调；Sentinel 只做**计数**，开销小、语义直接，但真正让线程不卡住要靠超时时间。我们的做法是 Sentinel 限并发数 + Feign 显式配超时 + fallback 兜降级，三层拼起来替代 Hystrix 的线程池隔离。"

### 4.3 面试话术（整段）

"Hystrix 2018 年就进维护模式了，现在新项目基本都用 Sentinel。能力上 Sentinel 不止熔断，还有限流、热点参数、系统自适应保护。熔熔断依据上 Hystrix 只看错误率，Sentinel 多了一个**慢调用比例**——这一条很关键，因为下游'没挂但变慢'是最危险的，错误率是 0 触发不了任何规则，线程却在被慢慢耗尽。最实质的差别在隔离方式：Hystrix 是线程池隔离，一个依赖一个池，故障被关在池子里，代价是线程切换开销和一堆池参数要调；Sentinel 是并发计数、相当于信号量隔离，不新建线程所以开销很小，但要说清楚——它只能拒绝新请求，已经卡在 IO 上的线程救不回来，所以真正落地必须再叠超时控制和 fallback。最后是运维体验：Sentinel 有控制台，规则实时推送生效，不用发版，这在生产上做应急差别非常大。"

---

## 五、本项目实战：Gateway + Sentinel

> [!info] 为什么限流放在网关
> 网关是所有外部流量的唯一入口，在这层做限流：**保护面最大、代码侵入最小**（下游 14 个服务一行不改）、规则集中在一处好维护。缺点是**粒度粗**——只能按路由/路径分，看不到方法级的业务语义，所以服务间的熔断还是得下沉到 Feign/业务层，这也是我们项目当前的缺口。

### 5.1 依赖（Spring Boot 3.2.5 / SCA 2023.0.1.0）

```xml
<!-- Spring Cloud Alibaba Sentinel Gateway 适配器 -->
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
<!-- 规则持久化到 Nacos -->
<dependency>
  <groupId>com.alibaba.csp</groupId>
  <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

⚠ Boot 3.x 必须用 SCA **2023.0.x** 系列；用 2021.x 会因 Jakarta 命名空间不兼容直接起不来。

### 5.2 配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service        # ← 资源名默认就是这个 route id
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
    sentinel:
      transport:
        dashboard: localhost:8080   # 控制台
        port: 8719                  # 客户端与控制台通信端口，多实例必须各不相同
      eager: true                   # 启动即注册到控制台（否则要等第一次请求才出现）
      filter:
        enabled: false              # Gateway 是 WebFlux，必须关掉 Servlet filter
```

### 5.3 两种资源粒度

**① 路由粒度**（默认）：一个 route id 一个资源，规则用 `GatewayFlowRule`。

```java
Set<GatewayFlowRule> rules = new HashSet<>();
rules.add(new GatewayFlowRule("order-service")
        .setCount(10).setIntervalSec(1)
        .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)
        .setBurst(5));                       // 允许瞬时突发 5
rules.add(new GatewayFlowRule("user-service")
        .setCount(20).setIntervalSec(1)
        .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)
        .setMaxQueueingTimeoutMs(500));
GatewayRuleManager.loadRules(rules);
```

**② 自定义 API 分组**（跨路由、更细）：先定义 API（按路径前缀匹配），再对这个 API 名配规则。

```java
ApiDefinition payApi = new ApiDefinition("pay-api")
        .setPredicateItems(Set.of(new ApiPathPredicateItem()
                .setPattern("/api/order/pay/**")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)));
GatewayApiDefinitionManager.loadApiDefinitions(defs);
GatewayRuleManager.loadRules(apiRules);   // ⚠ 见下方硬伤 #6
```

### 5.4 规则持久化（Nacos，生产必问）

内存规则重启即丢，生产把 **Nacos 当唯一数据源**：

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        gw-flow:
          nacos:
            server-addr: localhost:8848
            dataId: gateway-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: gw-flow      # 可选：flow / degrade / param-flow / system / authority / gw-flow
        degrade:
          nacos:
            dataId: gateway-degrade-rules
            rule-type: degrade
```

**必须讲清的因果**：原生 Sentinel 控制台改规则**只写进客户端内存**，服务重启就没了，也不会回流到 Nacos。生产做法有两种——① **规则以 Nacos 为准**，改规则直接改 Nacos 配置（客户端通过 `ReadableDataSource` 监听、**push 模式**热更新），控制台只当只读监控用；② 魔改控制台，让它在用户点保存时双写 Nacos。①简单可控，我们走的是①。

**话术**："Nacos 当规则源，Sentinel 客户端订阅配置变更，`ReadableDataSource` 收到推送后调 `RuleManager.loadRules` 热更新，改规则不用发版也不重启。代价是控制台上改的规则会被下次 Nacos 推送覆盖，所以我们约定控制台只看不改，改规则一律走 Nacos，配好灰度和审批。"

### 5.5 自定义限流响应

默认被限流是 429 + 一段裸文本，前端不好处理：

```java
GatewayCallbackManager.setBlockHandler((exchange, ex) -> ServerResponse
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(Map.of(
                "code", 429,
                "msg", "请求过于频繁，请稍后重试",
                "timestamp", System.currentTimeMillis()))));
```

### 5.6 踩坑清单

| 问题 | 根因 | 解法 |
|------|------|------|
| 规则不生效 | `sentinel.filter.enabled` 没关 | Gateway（WebFlux）模式下必须 `false` |
| 控制台看不到服务 | 缺 JVM 参数 / 8719 端口冲突 | `-Dcsp.sentinel.api.port` 各实例唯一，且 `eager: true` |
| 路由规则完全没命中 | 资源名默认是 **route id**，写成了路径 | `GatewayFlowRule` 第一个参数必须与 `routes[].id` 完全一致 |
| 自定义响应不触发 | `BlockRequestHandler` 没注册 | 放到 `@PostConstruct`，确保容器启动后执行 |
| Boot 3.x 启动报错 | SCA 版本过旧 | 升到 2023.0.1.0 及以上 |

### 硬伤预案：业务层零 @SentinelResource

[[云商城已知硬伤与追问预案]] 里两条和 Sentinel 直接相关，被追问时必须能接：

- **#6（P1）规则被自己覆盖**：`initCustomApi()` 里第二次调用 `GatewayRuleManager.loadRules(...)`，把整个规则集**全量替换**而不是追加，导致 `user-service` 的 QPS 10 规则丢了。→ **标准说法**："`RuleManager.loadRules` 是**全量覆盖语义**，多处初始化要先把所有规则收集齐再一次性 load；生产上根本不该在代码里 load 规则，应该由 Nacos 数据源统一推。这个坑正好是我改成 Nacos 持久化的直接动因。"
- **#8（P1）业务层零熔断降级**：`@SentinelResource` 全仓 0 处，只有网关限流。被问"服务雪崩怎么防"时不能只答限流。→ **补救话术**："网关限流只解决'入口流量过大'，解决不了'服务 A 调服务 B、B 变慢把 A 的线程拖死'这种链路级雪崩，那个必须靠服务间的熔断降级。我们这块当时只在网关层做了防护，服务间是靠 Feign 超时 + 兜底默认值顶着的，属于我明确知道的技术债——正规做法是给 Feign 调用加 `@SentinelResource` 配 blockHandler，或者用 `feign.sentinel.enabled=true` 让 Feign 走 Sentinel 的熔断，再配异常比例规则。"

⚠ **不要把这条藏起来**：面试官问"你们怎么防雪崩"时，答"网关限流"就被打穿了；主动交代"网关限流只挡入口，服务间熔断当时缺位、现在能说出怎么补"反而是加分。

---

## 面试高频追问与话术

### Q1: Sentinel 和 Resilience4j / Guava RateLimiter 有什么区别？
> "Guava RateLimiter 只是**单机**限流算法（令牌桶），没有熔断、没有规则下发、没有控制台；Resilience4j 是函数式轻量库，熔断/限流/重试都有但同样偏单机、没有流量治理面板。Sentinel 的差异化在三点：① 规则维度更丰富（关联、链路、热点参数、系统自适应）；② **有控制台 + 实时推送**，生产上能应急；③ 各种框架 adapter 齐全（Feign、Gateway、Dubbo、RestTemplate），接进来不用改业务写法。"

### Q2: 滑动窗口的格子数怎么定？格子太细/太粗会怎样？
> "格子粒度决定**精度和内存**的折中。格子太少（比如 1s 就一格）退化成固定窗口，临界突刺回来了；格子太多（1s 切 1000 格）内存和遍历成本上升，而且对'最近有多少请求'这个统计其实没更准，因为每个格子内部还是累加值。Sentinel 秒级窗口默认 2 格，够平滑又几乎零开销。"

### Q3: Warm Up 的阈值是怎么爬的？
> "内部思路和 Guava 的 SmoothWarmingUp 同源：维护一个'剩余令牌/冷度量'，流量过来时按当前冷度量决定该不该放行，冷度量随时间被慢慢消耗掉，等价于阈值从 `count / coldFactor`（默认 3）线性抬到 count，`warmUpPeriodSec` 控制爬升时长。所以配预热要一起想清楚预热期多长——太短等于没预热，太长会影响正常放量。"

### Q4: 为什么匀速排队不建议用在 Web 接口？
> "因为排队是把压力转成延迟。Web 接口的用户是在线的，排队 500ms 意味着 P99 直接加 500ms，而请求还占着连接和线程；下游持续超阈值时会形成'排队→超时→重试→更多排队'的正反馈。它适合的是**生产者能等**的场景：MQ 消费削峰、批量写库。"

### Q5: 熔断规则该选哪种策略？
> "外部依赖（第三方接口、跨机房服务）优先**慢调用比例**，因为这类依赖最常见的故障形态是变慢不是报错；内部服务、逻辑复杂、异常种类多的用**异常比例**，比异常数更稳（不受流量涨跌影响）；**异常数**适合低频接口——它流量小、比例容易被一两个请求带飞，用绝对次数更合理。三种都要配 `minRequestAmount` 兜住小流量误判。"

### Q6: 半开状态怎么实现的？没流量的时候能自动恢复吗？
> "半开是请求驱动的，不是定时器。熔断窗口内每来一个请求都会判断'到 timeWindow 了没'，到了的那个请求抢到资格变成探测请求，放行；成功就回 CLOSED 并清统计，失败就重新 OPEN。所以**完全没有流量时它不会自动恢复**，得等下一个请求进来——这也是为什么核心链路通常要配一个定时健康探测。"

### Q7: 网关已经限流了，服务里还要限流吗？
> "要，两层职责不同。网关限流防的是**外部流量洪峰**，看的是路径/用户/IP；服务内限流防的是**内部调用关系**，比如某个接口被三个上游调用，网关维度看不见这三个上游各自的量，也看不见 MQ 消费、定时任务这类不走网关的入口。热点参数限流（比如单个 SKU 的访问量）更只能在业务层做——网关拿不到参数。"

### Q8: 集群限流了解吗？
> "了解但没上。Sentinel 默认是**单机阈值**，扩缩容会让全局阈值跟着变，要固定全局值就得上集群流控：一个 Token Server 统一计数，客户端每条请求先问 Token Server。代价很实在——多一次 RPC（同进程/同机可省），而且 Token Server 本身要做高可用，挂了要退化到本地阈值。我们是电商场景，单机阈值 + 弹性扩容能接受，就没引入。"

---

## 关联知识点

- [[华为云校招-Sentinel高频三题]] — 华为云校招出题版：本页内容按出现概率筛成三题（限流原理 → 网关与服务间熔断落地 → vs Hystrix），只出题不给答案
- [[04-Spring/Gateway限流]] — Gateway 自带的 Redis RequestRateLimiter（令牌桶）实现，与 Sentinel 是两条不同路线
- [[06-其他杂项/Feign详解]] — `feign.sentinel.enabled=true` 让 Feign 走 Sentinel 熔断；Feign 超时与熔断的联动
- [[04-Spring/Spring Boot 与 Spring Cloud Alibaba 的关系]] — SCA 组件全景、Hystrix→Sentinel 的替代关系、`CircuitBreaker` SPI 抽象
- [[04-Spring/Spring-interview-guide]] — 服务熔断 vs 服务降级概念区分（L435）、Hystrix 默认参数
- [[06-其他杂项/Nacos详解]] — Sentinel 规则持久化的配置中心
- [[07-简历项目/云商城已知硬伤与追问预案]] — 硬伤 #6（loadRules 覆盖）、#8（业务层零熔断）
- [[06-其他杂项/面试分析报告]] — 项目技术栈中 Sentinel 定位（L22）、追问清单（L438）
- [[Seata详解]] — 同属 SCA，但一致性方案，和流量治理是两回事
- [[线程池]] / [[Semaphore详解]] — 信号量式并发控制的 JUC 基础，讲隔离方式时可直接迁移
