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

**解决的问题**：微服务那么多，每个服务怎么知道其他服务在哪台机器上？

**工作原理**：

```
服务启动时：
  mall-order-service ── 注册自己 ──▶ Nacos
  mall-pay-service   ── 注册自己 ──▶ Nacos
  mall-user-service  ── 注册自己 ──▶ Nacos

服务调用时：
  mall-order-service ── 问 "pay-service 在哪？" ──▶ Nacos
                    ◀── 返回 [192.168.1.10:8080, 192.168.1.11:8080]
                      ── 选一台调 ──▶ mall-pay-service
```

**核心概念**：

| 概念 | 说明 |
|------|------|
| **服务名** | 服务的唯一标识，如 `mall-pay-service` |
| **实例** | 服务的每台机器，一个服务可以有多个实例 |
| **IP:Port** | 每个实例的网络地址 |
| **健康检查** | Nacos 定期检查实例是否存活（心跳机制） |

**健康检查机制**：

```
服务实例 ── 每 5s 发一次心跳 ──▶ Nacos
                                     │
                          Nacos 等待 15s 没收到心跳 → 标记为不健康
                          Nacos 等待 30s 没收到心跳 → 剔除实例
```

### 2. 配置中心（替代 Spring Cloud Config）

**解决的问题**：每个服务都有自己的配置（数据库地址、超时时间等），改一个配置要重启所有服务？不需要。

**工作原理**：

```
Nacos 配置中心                             服务实例
  ┌──────────┐    发布配置    ┌──────────────┐
  │  Data ID  │  ──────────▶  │  监听配置变化  │
  │  application.yml │       │  自动注入新值  │
  └──────────┘              └──────────────┘
       ↑
  运维修改 timeout=60
```

**核心概念**：

| 概念 | 说明 |
|------|------|
| **Data ID** | 配置文件的标识，如 `mall-order-service-dev.yaml` |
| **Group** | 配置分组，默认 `DEFAULT_GROUP` |
| **Namespace** | 命名空间，隔离环境（dev/test/prod） |
| **配置格式** | 支持 YAML、Properties、JSON、TEXT |

**配置优先级**（从高到低）：

```
1. 应用级配置（spring.cloud.nacos.config.extension-configs）
2. Data ID 精确匹配
3. 共享配置（shared-configs）
4. 应用默认配置（application.yml 中本地配置）
```

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

### 配置（bootstrap.yml）

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

### 为什么用 bootstrap.yml 而非 application.yml？

`bootstrap.yml` 比 `application.yml` **先加载**。Nacos 的地址和配置必须在应用启动时就加载，否则后面的配置无法注入。

---

## Nacos vs Eureka vs Config

### 注册中心对比

| 对比项 | Nacos | Eureka |
|--------|-------|--------|
| **CAP** | AP + CP（可切换） | AP  |
| **健康检查** | 心跳 + TCP 探测 | 心跳 |
| **控制台** | 有（Web UI） | 有 |
| **配置中心** | 内置 | ❌ 需搭配 Config |
| **当前状态** | ✅ 活跃维护 | ⚠ 已停更 |

### 配置中心对比

| 对比项 | Nacos Config | Spring Cloud Config |
|--------|-------------|-------------------|
| **存储后端** | Nacos 自身存储 | Git（需要 Git 仓库） |
| **实时推送** | ✅ 支持（长轮询） | ⚠ 需配合 Bus + MQ |
| **版本管理** | 有 | 有（依赖 Git） |
| **回滚** | 一键回滚 | 通过 Git 操作 |

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
2. **社区活跃**：Eureka 2.0 已停更，Nacos 是阿里主导、社区活跃
3. **控制台好用**：Web UI 直接查看服务列表、配置内容，运维方便
4. **健康检查更全面**：支持心跳 + TCP 探测，比 Eureka 仅心跳更可靠

---

## 常见 Bug

### 1. 配置刷新不生效
- 确认用了 `bootstrap.yml` 而非 `application.yml`
- 确认 `file-extension` 和 Nacos 上一致
- 确认类加了 `@RefreshScope`

### 2. 服务注册不上
- 检查 Nacos 地址是否正确 `127.0.0.1:8848`
- 检查网络是否能连通（telnet 8848）
- 检查服务名是否重复

### 3. Nacos 连接超时
- 检查 Nacos 服务是否启动
- 检查防火墙是否放行 8848 端口

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[06-其他杂项/Feign详解]] — Feign 通过 Nacos 做服务发现
- [[06-其他杂项/CDC（Canal）详解]] — 同样依赖 Nacos 注册
- [[Spring Cloud 配置优先级]] — 配置覆盖规则详解
