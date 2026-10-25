---
tags:
  - java
  - spi
  - 类加载
  - 面试
created: 2026-08-11
updated: 2026-08-11
source: 用户在 JDBC 详解页提问「SPI 是啥」；2026-08-11 问答补充（jar 归属 / 多实现 / 自写 SPI 示例 / getResources 底层机制 / classpath 是类加载器私有清单 / DriverManager 与驱动实现类关系 / ServiceLoader 与 DriverManager 分工）
背诵状态: 已完成
背诵开始: 2026-08-11 20:58
背诵结束: 2026-08-11 20:59
---

# SPI 详解 — Service Provider Interface（服务提供者接口）

## 一句话定义

> **SPI**（Service Provider Interface，服务提供者接口）是 Java 的「**接口由 JDK/框架定义，实现由第三方 jar 提供，运行时自动发现、自动装配**」机制。

**和 API 的区别（面试必问）：**

| 概念 | 定义 | 面向谁 |
|------|------|--------|
| API | 框架实现好功能，暴露接口给你调用 | 面向**调用者** |
| SPI | 框架只定义接口，实现留给第三方，运行时自动发现 | 面向**实现者** |

同一个接口可以同时是 API 和 SPI——`DriverManager.getConnection()` 对你是 API；`java.sql.Driver` 接口对 MySQL 厂商是 SPI（他们负责实现）。

---

## 三个角色

| 角色 | 谁 | JDBC 例子 |
|------|-----|-----------|
| 接口定义者 | JDK / 框架 | `java.sql.Driver`（JDK 定义） |
| 实现提供者 | 第三方厂商 | MySQL Connector/J |
| 加载机制 | `ServiceLoader` | `DriverManager` 初始化时调用 |

---

## 工作原理：三要素

1. **约定目录**：实现 jar 里的 `META-INF/services/`
2. **描述文件**：文件名 = 接口全限定名，内容 = 实现类全限定名（每行一个）
3. **加载器**：`ServiceLoader.load(接口.class)` 通过类加载器的 `getResources("META-INF/services/" + 接口名)` 遍历 **classpath 上所有条目**（jar 内的条目、目录下的文件），读取描述文件，反射实例化

```java
ServiceLoader<Driver> drivers = ServiceLoader.load(Driver.class);
for (Driver d : drivers) {   // 自动发现并实例化的所有驱动实现
    ...
}
```

---

## 底层机制：ServiceLoader 怎么找描述文件

### classpath 是类加载器私有的路径清单

classpath 不是抽象概念，就是启动 JVM 时传入的一串路径清单（`java -cp "lib/mysql.jar;target/classes"`）。这份清单**存在类加载器对象内部、由它独占**——业务代码碰不到它。找类、找资源都必须通过类加载器，因为它手里握着唯一的清单。

### getResource 与 getResources

类加载器找类的 `loadClass()` 之外，还有一套并行的方法找资源（非类文件：配置文件、图片、描述文件）：

| 方法 | 行为 | 用途 |
|------|------|------|
| `getResource("路径")` | 找到**第一个**匹配就停 | 配置文件只有一份的场景 |
| `getResources("路径")` | 遍历 classpath **所有条目**，全部收集 | SPI 描述文件可能存在于多个 jar（MySQL / PG 驱动），一个都不能漏 |

### 完整的查找流程

```text
ServiceLoader.load(Driver.class)
  ↓ 构造资源路径
getResources("META-INF/services/java.sql.Driver")
  ↓ 类加载器逐条检查自己的路径清单
  ├── lib/mysql.jar    → 有！→ jar:file:/.../mysql.jar!/META-INF/services/java.sql.Driver
  ├── lib/postgresql.jar → 有！→ jar:file:/.../postgresql.jar!/META-INF/services/java.sql.Driver
  ├── target/classes   → 有！→ file:/.../target/classes/META-INF/services/java.sql.Driver
  └── lib/logback.jar  → 没有，跳过
  ↓ 拿到 URL 列表，逐个打开、逐行读取实现类名
  ↓ Class.forName + 反射实例化
一个可迭代的 Driver 对象集合 → 交给 for 循环
```

要点：URL 里 `!` 是 jar 内部的分隔符（`jar!/路径` = 压缩包里的条目）；jar 条目和目录文件两种形态类加载器都处理——这就是为什么说遍历的是「classpath 上所有条目」而不是「所有 jar」。

### 为什么要"通过类加载器"

资源查找和类加载走同一套**双亲委派**路径——子加载器先问父加载器，父找不到才自己找。另外 `ServiceLoader.load()` 不传参时用的正是**当前线程的上下文类加载器（TCCL）**：Bootstrap 自己扫不到应用 classpath 的资源，必须靠 TCCL 往下找，见 [[线程上下文类加载器（TCCL）]]。

---

## JDBC 实例（最经典考点）

- **JDBC 4.0 之前**：手动 `Class.forName("com.mysql.cj.jdbc.Driver")` 触发静态注册
- **JDBC 4.0 起**：驱动 jar 里放描述文件，DriverManager 启动时用 ServiceLoader 自动加载 → `Class.forName` 可省略

```text
mysql-connector-java-8.x.jar
└── META-INF/
    └── services/
        └── java.sql.Driver     ← 文件内容：com.mysql.cj.jdbc.Driver
```

### DriverManager 和驱动实现类的关系

**DriverManager 是 JDK 自带的"调度台"，驱动实现类是厂商的"司机"**——两者都只认 `java.sql.Driver` 接口，互相不认识对方的具体类。

| 角色 | 谁 | 干什么 | 类比 |
|------|----|--------|------|
| 接口定义者 | JDK | 定义 `Driver` 接口 + 提供 `DriverManager` 调度台 | 交通部：司机执照规范 + 调度中心 |
| 实现提供者 | MySQL 厂商 | 实现 `Driver` 接口，真正建立连接、收发 SQL | 持照司机，真会开车 |
| 使用者 | 你 | 只调 `DriverManager.getConnection(url)` | 乘客，只报目的地 |

`Driver` 接口本身几乎不干活，核心就两个方法：

| Driver 接口方法 | 干什么 |
|----------------|--------|
| `acceptsURL(url)` | 自报家门：回答"这个 URL 是不是我家的" |
| `connect(url, props)` | 真正去连数据库，返回 Connection |

调度的完整流程：你给 `jdbc:mysql://...` 的 URL → DriverManager 遍历登记册里所有驱动，挨个问 `acceptsURL` → MySQL 驱动认领 → 调它的 `connect` 创建连接 → 返回给你。**你从头到尾没提"MySQL"三个字**，决定谁来干活的是 DriverManager 按 URL 前缀匹配。

驱动想被调度，必须先**注册**进 DriverManager 的登记册：JDBC 4.0 之前靠手动 `Class.forName`（驱动静态块里调 `registerDriver`）；4.0 起 SPI 自动加载并注册，`Class.forName` 可省略。

### ServiceLoader 和 DriverManager 的分工（易混淆点）

两者不是二选一，是**协作关系**——ServiceLoader 负责「**找**」，DriverManager 负责「**管**」：

| | ServiceLoader | DriverManager |
|--|---------------|---------------|
| 是什么 | **通用 SPI 机制**（`java.util`，谁都能用） | **JDBC 专属调度台**（`java.sql`） |
| 干的活 | 「找」：扫 `META-INF/services/`，实例化实现类 | 「管」：注册驱动、按 URL 匹配、返回 Connection |
| 在 JDBC 里的角色 | SPI 机制的**执行者** | SPI 机制的**使用方**（调用 ServiceLoader 的那个人） |

**完整链条（谁调用谁）：**

```text
首次用到 DriverManager
  → DriverManager 类初始化（静态块）
    → loadInitialDrivers()
      → ServiceLoader.load(Driver.class)      ← ServiceLoader 干活：扫 META-INF/services/java.sql.Driver
        → 反射实例化 MySQL 驱动
          → 触发驱动静态块 → registerDriver()  ← 驱动把自己注册进 DriverManager 的登记册
  → 之后 getConnection(url)
    → DriverManager 遍历登记册（registeredDrivers）
      → 挨个问 acceptsURL(url) → 匹配 → connect() 返回 Connection
```

关键一步：**ServiceLoader 实例化驱动时（带类初始化）会触发驱动的静态块，静态块里调用 `DriverManager.registerDriver()`**——所以驱动是「被发现 → 实例化 → 自己报名进登记册」三步连着的。

> 类比：ServiceLoader 是快递员，拿着地址清单（`META-INF/services/`）挨家挨户把厂商请来；DriverManager 是调度台，请来的厂商先登记（registerDriver），客户报目的地（URL）时从登记册里挑一个派活。

**一句话区分**：`getConnection` 是 DriverManager 的活（遍历登记册 + URL 匹配）；「自动加载驱动」是 ServiceLoader 的活（SPI 发现）；驱动实例化时静态块注册回 DriverManager——三个动作串成闭环。

---

## 和类加载器的关系（进阶考点）

`DriverManager` 在 rt.jar 中，由 Bootstrap ClassLoader 加载；MySQL 驱动实现类在应用 classpath，归 AppClassLoader 管辖。双亲委派是**单向**的——Bootstrap 看不到应用类，所以 ServiceLoader 用**线程上下文类加载器（TCCL）**反向向下加载。

→ 详见 [[线程上下文类加载器（TCCL）]]

---

## 其他 SPI 应用（举一反三）

| 场景               | 接口                                                   | 典型实现                           |
| ---------------- | ---------------------------------------------------- | ------------------------------ |
| JDBC 驱动          | `java.sql.Driver`                                    | MySQL / Oracle / PostgreSQL 驱动 |
| 日志门面             | `org.slf4j.spi.SLF4JServiceProvider`                 | logback / log4j2               |
| Spring Boot 自动配置 | `META-INF/spring/...AutoConfiguration.imports`（类似思路） | 各 starter 的自动配置类               |
| Dubbo 扩展点        | `@SPI` + ExtensionLoader                             | 协议 / 序列化实现                     |

> 面试提一嘴「**JDBC、日志门面、Dubbo 扩展都是同一套 SPI 思想**」——说明你见过不止一个例子，属于加分项。

---

## 面试话术

> **问：什么是 SPI？和 API 有什么区别？**
>
> 答：SPI 是服务提供者接口——接口由 JDK 或框架定义，实现由第三方 jar 提供，运行时通过 `META-INF/services/` 下的描述文件自动发现并加载。区别在于面向的对象：API 是框架实现好了给我调用，SPI 是框架定义接口、实现方提供实现、框架反过来调实现方。JDBC 就是典型：`java.sql.Driver` 是 JDK 的接口，MySQL 驱动 jar 里写一行描述文件，DriverManager 启动时自动加载，所以 JDBC 4.0 之后不用再写 `Class.forName`。

> **问：SPI 加载的实现类在 classpath 上，Bootstrap 怎么加载到？**
>
> 答：靠线程上下文类加载器。接口在 rt.jar 里由 Bootstrap 加载，但实现类在应用 classpath，按双亲委派 Bootstrap 看不到，所以 ServiceLoader 用当前线程的 contextClassLoader（一般是 AppClassLoader）向下加载——这是「上层调用下层」的反向打破双亲委派。

---

## 常见疑问

**Q：实现 jar 一定是第三方的吗？一个 jar 只能实现一个接口吗？**

实现者不限于第三方——只要 classpath 上的 jar 在 `META-INF/services/` 里放了描述文件就会被自动发现，可以是厂商 jar、自己的 jar，甚至 JDK 内部的默认实现。且 `META-INF/services/` 是**目录**不是文件：

| 结构 | 表现 |
|------|------|
| 一个 jar 实现多个接口 | `services/` 目录下放多个文件，一接口一文件 |
| 多个 jar 实现同一接口 | 各 jar 里有**同名**描述文件（如 MySQL / PG 驱动都有 `java.sql.Driver`），内容各不相同 |
| 一个 jar 内同一接口多个实现 | 描述文件里**每行一个**全限定名 |

所以 `ServiceLoader.load()` 返回的是可迭代集合，代码里要 `for` 遍历，框架拿到全部候选后再自行挑选（JDBC 的 `DriverManager` 按 URL 前缀选驱动）。

**Q：`META-INF/services/` 平时开发怎么没见过？**

因为它是**打包产物**里的路径，不是源码路径。jar 本质是 zip，`META-INF/services/` 是压缩包内部结构。对应关系：

| 阶段 | 路径 |
|------|------|
| 写代码时 | `src/main/resources/META-INF/services/接口全限定名` |
| 打包后 | jar 内部的 `META-INF/services/接口全限定名` |

`src/main/resources/` 下的文件打包时原样复制进 jar 根目录。IDE 里展开任一依赖 jar（如 mysql-connector）的 `META-INF` 就能看到 `services/` 文件夹。

---

## 动手实践：自己写一个 SPI

```text
my-plugin/（普通 Maven 工程）
├── pom.xml
└── src/main/
    ├── java/com/example/
    │   ├── Greeting.java            ← 接口（自己定义）
    │   └── ChineseGreeting.java     ← 实现
    └── resources/
        └── META-INF/services/
            └── com.example.Greeting ← 文件内容：com.example.ChineseGreeting
```

```java
// Greeting.java —— 接口定义者（你）
public interface Greeting {
    String say();
}

// ChineseGreeting.java —— 实现提供者（也是你）
public class ChineseGreeting implements Greeting {
    public String say() { return "你好"; }
}
```

```text
// resources/META-INF/services/com.example.Greeting —— 描述文件
com.example.ChineseGreeting
```

`mvn package` 后把 jar 放进任意应用的 classpath，应用无需 import 实现类：

```java
ServiceLoader<Greeting> loaders = ServiceLoader.load(Greeting.class);
for (Greeting g : loaders) {
    System.out.println(g.say());   // 输出：你好
}
```

要点：一个接口、一个实现、一个文本文件，打包即完成自动发现——这就是 SPI 的完整闭环。

---

## 通俗理解

> JDK 定好插座规格（接口）和插孔布局（`META-INF/services/` 约定），厂商按规格做插头（实现）并在包装上贴标签（描述文件）。你按下总开关（`ServiceLoader.load`）时，系统自动把所有贴了正确标签的插头装上——不用你一个一个手动插。

---

## 关联知识点

- [[JDBC详解]] — SPI 在驱动加载中的实际应用（JDBC 4.0 自动发现，`Class.forName` 为什么可省略）
- [[线程上下文类加载器（TCCL）]] — SPI 自动加载背后的类加载器机制（双亲委派为什么失效）
- [[打破双亲委派的经典场景]] — JDBC/SPI 作为"上层打破"经典场景的汇总入口
- [[三层类加载器与classpath]] — Bootstrap / AppClassLoader 各自的管辖范围
- [[jvm八股]] — JVM 类加载部分总览

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-11 20:58 | 2026-08-11 20:59 | SPI 是服务提供者接口机制，核心为框架定义接口、第三方提供实现，通过 META-INF/services 描述文件配合 ServiceLoader 在运行时自动发现加载实现类。底层依赖类加载器遍历 classpath 获取资源，并利用线程上下文类加载器解决双亲委派限制。 | 第一轮背记完成 |
