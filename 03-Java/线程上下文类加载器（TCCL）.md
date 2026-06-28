---
tags:
  - java
  - jvm
  - 类加载
  - 双亲委派
  - SPI
created: 2026-06-10
updated: 2026-06-10
---

# 线程上下文类加载器（Thread Context ClassLoader）

## 一句话

线程上下文类加载器（TCCL）是 JVM 提供的一个"后门"，让**上层类加载器加载的代码**可以反过来调用**下层类加载器加载的代码**，从而打破双亲委派的单向性。

> 每个线程都有一个 `contextClassLoader`，默认继承自父线程，一般等于 AppClassLoader。

---

## 为什么要这个东西？

### 核心矛盾

```
Bootstrap ClassLoader
    ↑ 只能向下委托，不能反向
    │
    ┌─ java.sql.Driver（接口，在 rt.jar 里，由 Bootstrap 加载）
    │
    └─ 但 MySQL 驱动实现（com.mysql.cj.jdbc.Driver）在 classpath 上
       Bootstrap 看不到 classpath，加载不到！
```

**问题**：`java.sql.DriverManager` 初始化时，要加载所有可用的 `Driver` 实现。但它自己是由 Bootstrap 加载的，按双亲委派的规则，它只能往下委托（Bootstrap → Extension → Application），而 MySQL 驱动在 Application 的势力范围，Bootstrap 根本拿不到。

**双亲委派是单向的**：上层加载器无法调用下层加载器去加载类。

### 解决方案：线程上下文类加载器

```
DriverManager.init() 运行时
    ↓
获取当前线程的 contextClassLoader（一般是 AppClassLoader）
    ↓
用 contextClassLoader.loadClass("com.mysql.cj.jdbc.Driver") 加载
    ↓ 成功！
```

这就是**从上层调用下层"反向"打破**双亲委派。

---

## 怎么用？

### 获取和设置

```java
// 获取
ClassLoader cl = Thread.currentThread().getContextClassLoader();

// 设置（一般框架代码才需要主动设）
Thread.currentThread().setContextClassLoader(myClassLoader);
```

### JDBC 典型流程

```java
// 1. Class.forName("com.mysql.cj.jdbc.Driver") 手动注册
//    → JDBC 4.0 之后不需要这步了，SPI 自动发现

// 2. DriverManager 初始化时执行 sun.misc.Service 或 ServiceLoader
//    扫描 META-INF/services/java.sql.Driver 文件
//    → 用线程上下文类加载器加载找到的实现类
//    → 调用 DriverManager.registerDriver() 注册

// 3. 用户使用
Connection conn = DriverManager.getConnection(url, user, password);
//    → DriverManager 遍历已注册的 Driver，挑一个合适的
```

### SPI（Service Provider Interface）机制

线程上下文类加载器最常见的用途就是 **Java SPI**：

```
META-INF/services/
└── java.sql.Driver           ← 文件内容：com.mysql.cj.jdbc.Driver
                                ↓
                    ServiceLoader 读取文件
                                ↓
                    用 TCCL 加载所列的实现类
```

SPI 的设计就是为了解决"由核心 API 定义接口，由第三方 jar 提供实现"这个场景——`java.sql.Driver` 是 JDK 的，`com.mysql.cj.jdbc.Driver` 是 MySQL 的，两者在完全不同的类加载器管辖下，必须靠 TCCL 来桥接。

---

## 两种打破双亲委派的方式对比

| 方式 | 操作 | 典型场景 |
|------|------|----------|
| **重写 `loadClass()`** | 子加载器优先自己加载，不委派给父 | Tomcat WebappClassLoader |
| **线程上下文类加载器** | 父加载器反叫子加载器去加载 | JDBC SPI、JNDI、JAXB |

前者是从**下层打破**（子加载器不听话了），后者是从**上层打破**（父加载器主动调子加载器）。

---

## 面试话术

> **问：什么是线程上下文类加载器？为什么需要它？**
>
> 答：线程上下文类加载器是每个线程持有的一个 ClassLoader 引用，默认继承父线程的设置。它存在的根本原因是**双亲委派是单向的**——上层类加载器无法获取下层加载器加载的类。以 JDBC 为例，`java.sql.Driver` 在 rt.jar 中由 Bootstrap 加载，编译期不依赖 MySQL 驱动，运行时才通过 SPI 发现实现类。但 Bootstrap 看不到 classpath 上的 MySQL 驱动，所以 `DriverManager` 用线程上下文类加载器（此时是 AppClassLoader）去加载驱动实现，实现了"上层调用下层"的反向打破。

> **问：线程上下文类加载器一定等于 AppClassLoader 吗？**
>
> 答：不一定，默认情况下是。但框架可以手动 `setContextClassLoader()` 换成自己的加载器。比如某些 Java EE 容器会把 TCCL 设为当前应用的类加载器，这样 SPI 加载的就是当前应用依赖的实现，而不是全局的。

---

## 关联知识点

- [[三层类加载器与classpath]] — Bootstrap/Extension/Application 三层加载器详解
- [[jvm八股]] — JVM 八股总览（类加载部分）
- [[jvm_review]] — JVM 模拟面试批改（含本知识点原始出处）
- [[类初始化的主动引用与被动引用]] — 类初始化触发时机
