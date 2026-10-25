---
tags:
  - nacos
  - 微服务
  - 面试
created: 2026-08-10
updated: 2026-09-05
背诵状态: 已完成
背诵开始: 2026-08-10 14:46
背诵结束: 2026-08-10 15:48
---
# Nacos 详解 — 服务注册与配置中心

## 一句话定义

> **Nacos** = **Na**ming **Co**nfiguration **S**ervice  
> 一个同时提供**服务发现**和**配置管理**的基础设施组件。

**本项目**：Nacos 替代了 Eureka（服务注册）+ Spring Cloud Config（配置中心）两套组件，一套搞定。

---

## 两⼤核心功能

```
         Nacos
  ┌─────────────────┐
  │  注册中心        │   ← 服务A 在这里注册自己，服务B 从这里找到服务A
  │  (Service Discovery) │
  ├─────────────────┤
  │  配置中心        │   ← 统一管理所有服务的配置，改一处全局生效
  │  (Configuration)  │
  └─────────────────┘
```

### 1. 注册中心（替代 Eureka）

**解决的问题**：微服务实例数量多且地址动态变化，服务之间需要获取彼此的实例地址（IP:Port）才能完成调用，由注册中心统一维护实例列表。

**工作原理**：

```
服务启动时：
  mall-order-service ── 注册自己 ──▶ Nacos
  mall-pay-service   ── 注册自己 ──▶ Nacos
  mall-user-service  ── 注册自己 ──▶ Nacos

服务调用时：
  mall-order-service ── 查询 "pay-service 实例列表" ──▶ Nacos
                    ◀── 返回 [192.168.1.10:8080, 192.168.1.11:8080]
                      ── 选一台调 ──▶ mall-pay-service
```

**核心概念**：

| 概念 | 说明 |
|------|------|
| **服务名** | 服务的唯一标识，如 `mall-pay-service` |
| **实例** | 服务的每台机器，一个服务可以有多个实例 |
| **IP:Port** | 每个实例的网络地址 |
| **健康检查** | Nacos 定期检查实例是否存活（2.x 为 gRPC 长连接） |

**健康检查机制**（Nacos 2.x）：

```
服务实例 ── 与服务端建立 gRPC 长连接，双向通信 ──▶ Nacos
                                            │
                          连接断开时服务端主动感知 → 标记不健康 → 剔除实例
```

Nacos 1.x 为客户端每 5s 发送一次心跳（UDP/HTTP），服务端 15s 未收到标记不健康、30s 剔除；**2.x 改为 gRPC 长连接**，连接状态由服务端维护，断开即摘除，心跳包的概念被长连接取代。

### 2. 配置中心（替代 Spring Cloud Config）

**解决的问题**：每个服务都有自己的配置（数据库地址、超时时间等），配置变更时无需重启服务即可动态生效。

**工作原理**：

```
Nacos 配置中心                             服务实例
  ┌──────────┐    发布配置    ┌──────────────┐
  │  Data ID  │  ──────────▶  │  监听配置变化  │
  │  {服务名}.yaml │         │  自动注入新值  │
  └──────────┘              └──────────────┘
       ↑
  运维修改 timeout=60

服务启动时：按 serverAddr 连接 Nacos，用 Data ID + Group + Namespace
           三个维度定位并拉取自己的配置
```

**定位配置的三要素**：`Data ID`（配置文件名）+ `Group`（分组）+ `Namespace`（命名空间）三者组合唯一确定一份配置。

**核心概念**：

| 概念 | 说明 |
|------|------|
| **Data ID** | 配置文件的标识，如 `mall-order-service-dev.yaml` |
| **Group** | 配置分组，默认 `DEFAULT_GROUP` |
| **Namespace** | 命名空间，隔离环境（dev/test/prod） |
| **配置格式** | 支持 YAML、Properties、JSON、TEXT |

**配置优先级**（从高到低）：

```
1. 扩展配置（extension-configs）— 应用私有配置，列表内越靠后优先级越高
2. 共享配置（shared-configs）— 多应用共用的公共配置
3. 应用自身配置（Data ID = {服务名}-{profile}.{后缀}）
4. 应用本地配置（application.yml 中的内容）
```

> [!note] 关键记忆点
> 所有 Nacos 远端配置**整体优先于**本地 `application.yml`，否则"改配置动态生效"无法成立——本地文件会把远端配置覆盖掉。extension-configs 与 shared-configs 用 `spring.config.import` 引入时，**后引入的优先级更高**。

---

## CAP 定位：临时实例 AP（Distro）、持久实例 CP（Raft）

> [!note] 先分清两个"集群"
> 本节说"集群每个节点"——**指 Nacos 服务端集群**（注册中心自己那组节点，节点间跑 Distro/Raft，一般奇数台，Raft 过半写要求），**不是你的微服务**。微服务是集群外的**客户端**，`server-addr` 连的是其中任一节点。另有一个同名坑：Nacos 数据模型里有另一层 `service → cluster → instance`，那个 cluster（`cluster-name`，如杭州/上海）是实例分组、就近负载均衡用的——**两个"集群"不同层级**，面试说混了会露怯。

### 先掰开名词：「临时 / 持久」说的是注册表里那条**记录**的死活

不是"服务临时上线还是永久上线"，而是**这条实例记录是否跟实例进程同生共死**：

- **临时实例**：服务进程启动时由 SDK（nacos-client）自动登记；心跳/长连接一断，服务端**直接删除记录**。看着"丢记录"很狠，其实无所谓——进程重启时 SDK 会自动再注册，**记录丢了能自己长回来**。我们写的微服务默认全是这种。
- **持久实例**：记录是**线下登记**的（控制台手动配，或给不能装 SDK 的老服务、数据库登记），实例再不健康也**只标记不健康、不删除记录**，下线必须人工注销——**记录丢了就真没了**，没有任何 SDK 会帮你重新登记。

**这里的 SDK 指什么**：就是 `nacos-client` 这个客户端 jar（由 `spring-cloud-starter-alibaba-nacos-discovery` 引入），它**跑在你自己服务进程的 JVM 里**，像个常驻代办员干四件事：①启动时注册 IP:Port；②后台维持心跳/长连接报活；③拉取并缓存目标服务的实例列表给 Feign/LoadBalancer；④关闭时优雅注销。理解了两件事就通了：**临时实例"丢了能自动补回"，是因为进程一重启 SDK 第①步又跑一遍**；**持久实例走不了这条路，是因为数据库/遗留服务没法塞进这个 jar**，只能人在外面手动登记——记录来源是"人"，所以丢不得、错不得。

> [!question] 为什么我用 Nacos 从没感知过这两个概念？
> 因为 **`spring.cloud.nacos.discovery.ephemeral` 的缺省值就是 `true`**——你项目里所有服务走的都是默认的临时实例通道，从没拨过第二个档位。持久实例只在"把装不进 SDK 的东西登记进来"时才用到：控制台手动建服务（建服务时的**集群类型**决定这个服务收哪类实例，默认 AP 服务会**拒收** `ephemeral=false` 的注册，需建 CP 类型服务），或配置显式 `ephemeral: false`。生产里 99% 的人只用过临时实例——它是**低频功能、高频考点**。
> **30 秒验证实验**：kill 一个临时实例的进程，控制台看它 1.x 十五秒置灰、三十秒消失（2.x 秒级消失）；再登记一个持久实例停掉目标 IP，看它**永远留在列表里只标不健康**。"记录会不会自己消失、丢了能不能自己回来"，一个实验就把两个概念焊死了。

→ 通俗理解：临时实例像**酒店入住记录**，人走自动退房，下次入住再生成新记录；持久实例像**手机里手动存的联系人**，对方停机记录也还在，删错了就找不回来。

**名词和 CAP 的挂钩点就在这里**——"记录能不能丢"决定了"数据出错要不要紧"：

| | 记录丢了会怎样 | 出错代价 | 一致性取舍 | 协议 |
|---|---|---|---|---|
| **临时实例** | 进程重启自动补回 | 读到旧列表 → 调用失败重试一次即可**自愈** | 可用性优先 → **AP** | Distro（异步复制） |
| **持久实例** | 没人补回，只能重新登记 | 地址丢失/配错 → **不可自愈的事故** | 一致性优先 → **CP** | Raft（过半确认写入） |

### 准确表述

「Nacos 是 AP+CP 可切换」这种说法容易误导。准确表述是：**Nacos 同时为两类数据配了两套协议**，不是全局开关，而是按实例类型决定走哪条路——

| 实例类型 | 注册方式 | 一致性模型 | 协议 | 健康检查由谁发起 |
|----------|---------|-----------|------|------------------|
| **临时实例**（默认） | `ephemeral=true`（客户端缺省） | **AP** | Distro | 客户端上报（1.x 心跳 / 2.x gRPC 长连接） |
| **持久实例** | `ephemeral=false` | **CP** | Raft（2.x 为 JRaft） | 服务端主动探测（TCP/HTTP） |

### 为什么临时实例选 AP（业务决策——与协议实现无关）

临时实例本质是微服务节点，随时上线/下线，注册与注销非常频繁。如果走 CP（写多、且每写都要过半确认）：

- 每次注册/续约都要等**过半节点确认**才返回成功——节点一抖动、分区一分裂，少数派一侧直接写不进去，注册失败；
- 服务发现场景里"注册失败"意味着**新实例上不了线、旧实例被误摘**——不是慢一点，是连锁雪崩。

而 AP 的取舍是：写入落本地就返回成功，各节点短暂不一致没关系——**服务发现本来就能容忍拿到稍旧的实例列表**（拿错地址可自愈：调用失败重试换一台；拿不到地址不可自愈：所有调用直接雪崩，详见 [[CAP定理详解]]）。**所以临时实例选 AP 是业务特性决定的，跟用什么协议实现无关**——就算 Nacos 不用 Distro，这个结论也不变。这就是"为什么选 AP"的完整答案。

### Distro 三个机制在说什么（怎么实现 AP——实现细节）

选完 AP 之后的问题是"具体怎么落地"：集群里哪台节点说了算、写完后其他节点什么时候能拿到、对不上了怎么办——**这才是 Distro 的职责**，三个机制一条因果链：

1. **数据分片**：集群每个节点只负责一部分服务实例的数据（按服务名哈希决定权威节点），收到不属于自己分片的写请求会转发给对应节点——解决"写入交给谁"；
2. **异步复制**：权威节点写完本地就向客户端返回成功，再**异步**同步给其他节点——任一时刻各节点的实例列表不保证一致，读到的可能是旧数据——决定 AP 本质；
3. **完整性校验**：节点周期性地用 checksum 互相核对数据，发现不一致就全量重同步，数据最终收敛——兜底确保最终一致。

**为什么注册表也要"分片"？三个候选方案里它最优：**

- 方案一（全量复制）：每个节点都存完整注册表——每次注册要同步给所有节点，写开销随集群规模线性涨，还要处理"多份数据以谁为准"的冲突；
- 方案二（单点写）：全集群一台节点接收所有写入——写路径简单，但那个节点既是性能热点又是单点故障，集群等于白扩容；
- 方案三（分片，Distro 的做法）：按服务名哈希决定每个服务的权威节点，写入落在权威节点即成功，再异步抄送全集群——写开销恒定、无热点、权威性天然消冲突，代价是别的节点副本可能短暂滞后，而这点滞后正好是 AP 能兜住的。

→ 和 Redis Cluster 的 16384 个哈希槽是**同一个拆分思路**：按 key 哈希把数据分到各节点，请求落在非负责节点就转发/重定向。Nacos"分"的数据是"服务名 → 实例列表"，分片键就是服务名。

### 为什么持久实例走 CP（Raft）

==持久实例多是**线下登记**的数据库、遗留系统，IP:Port 配错代价很大==，所以实例元数据走 Raft：写请求经 Leader 发起，**过半数节点确认**才算提交；分区时少数派一侧拒绝写入——宁可不可用，不能给错信息。注意这类实例下线后**不会被自动删除**，只是被标记不健康，这也是"持久"的含义。

### 配置中心怎么选边

配置数据集中存在一个数据库（集群部署时挂同一个 MySQL），以 DB 为唯一事实源，靠「变更通知 + MD5 校验」让各客户端最终一致，一致性模型上更接近 CP——配置宁可多等一会同步到，不能让不同实例拿到互相冲突的值还不知道。

> **面试话术：** "Nacos 的 CAP 不能二选一地答。临时实例走 AP——**这是业务特性决定的**：注册中心场景下旧信息可重试、没信息会雪崩，所以写入落本地即成功、容忍短暂不一致；**具体实现上**才是 Distro 用数据分片、异步复制、定期 checksum 核对把 AP 落了地。持久实例走 CP——线下登记的数据库地址丢不得，所以走 Raft 过半写入；配置中心以单库为事实源、通知加 MD5 校验兜底，偏 CP。一句话总结：**Nacos 只是对不同数据用了不同协议**。"

---

## Spring Cloud Alibaba 集成

### 依赖

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

### 配置（bootstrap.yml）— 旧写法，需引入 bootstrap 依赖

> [!warning] 旧写法
> 使用 `bootstrap.yml` 需要在 `spring-cloud-starter-alibaba-nacos-config` 之外额外引入 `spring-cloud-starter-bootstrap` 依赖，且 Spring Cloud 2021+ 默认不加载。新项目推荐用上文的 `spring.config.import` 方式。

```yaml
spring:
  application:
    name: mall-order-service       # 服务名，也是 Data ID 的一部分

  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848   # Nacos 服务地址
        namespace: dev                 # 命名空间
        group: DEFAULT_GROUP           # 分组

      config:
        server-addr: 127.0.0.1:8848
        namespace: dev
        group: DEFAULT_GROUP
        file-extension: yaml          # 配置文件的扩展名
```

### 配置加载：bootstrap.yml 还是 spring.config.import？

**早期写法（Spring Cloud Alibaba 2021 以前）**：用 `bootstrap.yml`，它比 `application.yml` **先加载**，Nacos 的地址和配置必须在应用启动时就加载，否则后续配置无法注入。

**当前推荐写法（Spring Cloud 2020.0+）**：默认不再加载 `bootstrap.yml`，改用 `spring.config.import`：

```yaml
spring:
  application:
    name: mall-order-service
  config:
    import: nacos:mall-order-service.yaml   # 启动时从 Nacos 拉取配置
```

`spring.config.import` 是配置项的**新入口**，两者机制不同：bootstrap 是"引导上下文先加载"，import 是"标准配置项按顺序合并"。老项目常见 `bootstrap.yml` 不生效的坑，多半是版本已升级到 2021+ 但写法没跟上。

>"Nacos 地址是配置的配置，必须在启动早期拿到。旧方案用 bootstrap 引导上下文先行加载，但它是黑盒、还要维护两个容器；Spring Boot 2.4 之后用 `spring.config.import`，把 Nacos 变成标准配置源，跟 application.yml 按声明顺序合并，一个容器就搞定了。"

---

## 配置动态刷新原理（长轮询 → gRPC 推送）

### 1.x：HTTP 长轮询——用拉模拟推

Nacos 1.x 没有长连接，用**长轮询**在 HTTP 上模拟服务端推送：

1. 客户端 `ClientWorker` 发起带 **30s 超时**的查询请求，body 里带上自己订阅的所有配置的 **MD5 列表**；
2. 服务端**不立即返回**，把请求挂进队列（实际挂 **29.5s**，预留 0.5s 给网络回程，避免客户端先超时）；
3. 挂起期间有配置发布 → 服务端比对 MD5 发现变化，**立刻**返回变化的 Data ID；没有变化 → 超时返回空，客户端发起下一轮；
4. 客户端收到变化后重新拉**全量配置内容**，更新**本地快照文件**，回调监听器；Spring Cloud 层收到通知后发布 `RefreshEvent`，触发应用内 Bean 刷新链路。

**对比固定间隔轮询的价值**：30s 定轮询的平均感知延迟是 15s；长轮询在挂起窗口内是**变更即返回**，秒级感知——请求开销和轮询一样，效果接近推送。

**客户端容错三件套**：
- **本地快照缓存**：启动时连不上 Nacos 就读本地快照，保证服务能起来；
- **故障重试**：拉取失败自动退避重试；
- **定时全量 MD5 比对**：长轮询通知可能丢，客户端每隔 5 分钟主动全量核对一次——**通知可以失败，核对不会漏**。

### 2.x：gRPC 主动推送

2.x 复用服务发现同一条 gRPC 长连接，配置变更由**服务端直接推**通知，感知延迟从秒级降到**毫秒级**；服务端还能通过连接断开事件实时感知客户端上下线。客户端侧的拉取与刷新链路不变。

### 应用侧刷新链路（@RefreshScope 的触发时机）

客户端拿到新配置后，Spring 内部是一条六步链式反应：`RefreshEvent` → `ContextRefresher` 重建 Environment 并 diff 出变化的 key → `RefreshScope.refreshAll()` 清空 scope 缓存 → 下次访问走代理时发现缓存为空 → **懒重建** Bean → `@ConfigurationProperties` 重新绑定新值。**关键点：Bean 不是变更瞬间重建，而是下次被访问时才重建**——所以发布配置后第一次调用会略慢。完整链路、单例持旧引用等坑见 [[题23-RefreshScope动态刷新限流阈值]]。

---

## Nacos vs Eureka vs Config

### 注册中心对比

| 对比项 | Nacos | Eureka | ZooKeeper |
|--------|-------|--------|-----------|
| **CAP** | AP + CP（按实例类型，见上文） | AP | CP（ZAB 协议） |
| **健康检查** | 2.x gRPC 长连接 + TCP 探测 | 心跳（30s 续约，客户端主动） | Session + 临时节点（心跳续 Session，超时节点自动删除） |
| **控制台** | 有（Web UI） | 有 | 无官方 UI（命令行/第三方） |
| **配置中心** | 内置 | ❌ 需搭配 Config | 能做但不专业（Watcher 一次性触发） |
| **当前状态** | ✅ 活跃维护 | ⚠ 已停更 | ✅ 活跃（大数据生态根基） |

Eureka 由**客户端每 30s 续约心跳**，服务端 90s 未收到剔除；Nacos 2.x 由**服务端维护长连接**，感知更快、无心跳包风暴。

### Nacos 与 ZooKeeper 的差异（最容易被追问）

ZooKeeper 做服务注册的模式：客户端建 Session（超时时间为 tickTime 的 2~20 倍，心跳续约），注册即创建**临时节点**；Session 一超时临时节点自动删除，其他客户端通过 Watcher 感知。它的致命点在 **CP 取向**：分区或 Leader 选举期间（约 30~120s）集群**拒绝写**，选举窗口内读也受影响——对分布式锁是优点（宁可不可用不能给错），对注册中心是灾难：**实例注册不上=无法扩容，列表读不到=所有依赖该服务的调用全挂**。"注册中心不该拿可用性换强一致性"这个结论，正是 Nacos 为临时实例自研 Distro、而不是照搬 Raft/ZAB 的原因（完整对比见 [[CAP定理详解]]）。

拿 ZK 当配置中心还有两个工程硬伤：
1. **Watcher 一次性**：触发后必须重新注册，"触发→重注册"的窗口内发生变更就会漏；
2. **羊群效应**：一个节点变更会通知所有挂 Watcher 的客户端，大家又同时回来拉数据、同时重注册，集群压力瞬间放大。

> **面试话术（被问"Nacos 和 ZooKeeper 都能做注册中心，有什么区别"）：** "核心区别是 CAP 取向。ZK 是 CP，Leader 选举那几十秒既不能写也影响读，做注册中心会导致新实例注册不上、老列表查不到，服务调用全线冻结；Nacos 对默认的临时实例走 Distro 是 AP，分区时照常读写，旧实例靠客户端重试兜底。ZK 更适合分布式锁、选主这种'宁可不可用不能错'的场景——不是谁更好，是场景匹配。"

### 配置中心对比

| 对比项 | Nacos Config | Spring Cloud Config |
|--------|-------------|-------------------|
| **存储后端** | Nacos 自身存储 | Git（需要 Git 仓库） |
| **实时推送** | ✅ 支持（服务端推送 + 客户端长轮询） | ⚠ 需配合 Bus + MQ |
| **版本管理** | 有 | 有（依赖 Git） |
| **回滚** | 一键回滚 | 通过 Git 操作 |
| **订阅机制** | 客户端长轮询监听，变更即推送 | 依赖 MQ 广播通知 |

Nacos 的推送本质是**客户端长轮询**：客户端发起请求挂起等待，配置变更时服务端立即返回；1.x 是 HTTP 长轮询（30s 超时），2.x 走 gRPC 长连接，感知更快。

---

## 本项目中的 Nacos

| 服务 | 注册中心 | 配置管理 |
|------|---------|---------|
| mall-gateway | 注册到 Nacos | Nacos 管理路由配置 |
| mall-order-service | 注册到 Nacos | Nacos 管理订单超时配置 |
| mall-pay-service | 注册到 Nacos | Nacos 管理支付回调配置 |
| mall-search-service | 注册到 Nacos | Nacos 管理 ES 连接配置 |

---

## 面试话术

> **面试官**：你们项目为什么用 Nacos 而不用 Eureka？

**回答要点**：
1. **功能合一**：Nacos 同时提供服务发现和配置中心，省掉了一套 Eureka + Config 的维护
2. **CAP 可切换**：AP/CP 模式可配置，Eureka 只有 AP——关键数据（如金融场景）可切 CP 保强一致
3. **健康检查机制更优**：2.x 基于 gRPC 长连接，由服务端感知连接状态，比 Eureka 客户端心跳更实时、更省资源
4. **配置热更新**：配置变更推送到客户端，`@RefreshScope` 动态刷新，无需重启；Eureka 生态需另接 Bus + MQ
5. **社区活跃**：Eureka 2.0 已停更，Nacos 是阿里主导、社区活跃

---

## 常见 Bug

### 1. 配置刷新不生效

**现象**：修改了 Nacos 配置中心的配置，但应用没有动态刷新。

**原因排查**：

| 可能原因 | 检查点 | 解决 |
| --- | --- | --- |
| 未引入配置依赖 | 确认有 `spring-cloud-starter-alibaba-nacos-config` | 添加依赖 |
| 版本与写法不匹配 | Spring Cloud 2020.0+ 默认不加载 bootstrap.yml：新项目未配 `spring.config.import`，或老项目仍用 bootstrap 写法 | 新项目补 `spring.config.import: nacos:...`；老项目加 `spring-cloud-starter-bootstrap` 依赖恢复，或改用 import |
| `file-extension` 不对 | 确认扩展名与 Nacos 上的一致（yaml/yml/properties） | 统一用 yaml |
| 配置格式错误 | Nacos 上的 YAML 缩进不正确 | 用 YAML 校验工具检查 |
| 类没加 `@RefreshScope` | 需要动态刷新的类没加注解 | 加上 `@RefreshScope` |

### 2. @RefreshScope 与 @Scheduled 冲突

**现象**：配置了 `@RefreshScope` 的类中，`@Scheduled` 定时任务不执行或执行异常。

**原因**：`@RefreshScope` 的 Bean 在配置刷新时会**重新创建**，而 `@Scheduled` 任务绑定的是旧 Bean，新旧 Bean 冲突导致定时任务丢失。

**解决**：`@RefreshScope` 和 `@Scheduled` **不要放在同一个类中**。将配置属性类和定时任务类拆分：

>"@Scheduled 的注册发生在 Bean 初始化时，登记的是当时创建的实例；@RefreshScope 在配置刷新时会把旧实例销毁、换新实例。两者一撞，定时任务挂在已销毁的旧实例上——要么被销毁回调一起取消掉，要么还在调旧实例、配置刷新不生效。所以 @RefreshScope 和 @Scheduled 必须分开放：@RefreshScope 的类只放配置属性，定时任务放普通 @Component 里，通过 `@Autowired` 拿最新配置。"

```java
@RefreshScope
public class ConfigProperties {
    // 只放配置属性，不放 @Scheduled 方法
}

@Component
public class ScheduledTask {
    @Autowired
    private ConfigProperties config;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void run() {
        // 定时任务单独放在不带 @RefreshScope 的类里
    }
}
```

### 3. 服务注册不上
- 检查 Nacos 地址是否正确 `127.0.0.1:8848`
- 检查网络是否能连通（telnet 8848）
- 检查服务名是否重复

### 4. Nacos 连接超时
- 检查 Nacos 服务是否启动
- 检查防火墙是否放行 8848 端口

---

## 关联知识点

- [[华为云校招-Nacos高频三题]] — 基于本页出的华为云校招模拟卷（CAP/心跳数字/长轮询三题，只出题不给答案）
- [[CAP定理详解]] — AP/CP 取向对比：Eureka/ZK/Nacos/Redis 选型依据
- [[题23-RefreshScope动态刷新限流阈值]] — 配置动态刷新完整链路（RefreshEvent → RefreshScope 懒重建）与坑点
- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[06-其他杂项/Feign详解]] — Feign 通过 Nacos 做服务发现
- [[06-其他杂项/CDC（Canal）详解]] — 同样依赖 Nacos 注册
- [[Sentinel详解]] — 流控规则通过 Nacos 持久化与动态下发
- [[01-Redis/为什么要有 Redis Cluster 集群？]] — 对比学习：同样是"注册/寻址"问题，Cluster 用哈希槽，Nacos 用注册表

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-10 14:46 | 2026-08-10 15:48 | Nacos 是集成服务发现与配置管理的一体化组件，替代 Eureka 和 Spring Cloud Config，2.x 采用 gRPC 长连接优化健康检查，配置加载推荐 spring.config.import 方式。 | 第一轮背记完成 |
