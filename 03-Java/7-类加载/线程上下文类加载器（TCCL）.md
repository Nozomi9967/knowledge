---
tags:
  - java
  - jvm
  - 类加载
  - 双亲委派
  - SPI
created: 2026-06-10
updated: 2026-10-08
背诵状态: 已完成
背诵开始: 2026-08-11 20:34
背诵结束: 2026-08-11 20:35
---

# 线程上下文类加载器（Thread Context ClassLoader）

## 一句话

线程上下文类加载器（TCCL）是 JVM 提供的一个"后门"，让**上层类加载器加载的代码**可以反过来调用**下层类加载器加载的代码**，从而打破双亲委派的单向性。

> 每个线程都有一个 `contextClassLoader`，**子线程创建时在构造器里直接复制父线程的值**（不是概念，是 `Thread` 构造器里的一行赋值）。JVM 启动时把 main 线程的 TCCL 默认设为 AppClassLoader，所以没人手动改过的话，全应用所有线程的 TCCL 都是 AppClassLoader——这正是 SPI 默认能加载到应用 classpath 上实现类的前提。框架也可以随时 `setContextClassLoader()` 换掉（如 Java EE 容器改成当前应用的类加载器）。

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

> [!tip] 深挖：为什么"从上往下传"根本不存在？
> 很多人以为双亲委派是"从下到上，再从上到下"——其实**"下去"的那一步不是父往下发，而是调用栈原路退回发起者**。真相在 `ClassLoader.loadClass()` 源码里：**每个加载器只有一根 `parent` 指针（指向父），没有 `children` 列表**。
>
> ```java
> // loadClass() 核心（简化）
> if (parent != null) {          // 只问父，代码里没有任何"问子"的分支
>     c = parent.loadClass(name);
> } else {
>     c = findBootstrapClassOrNull(name);  // 我是根，没有父
> }
> if (c == null) c = findClass(name);      // 父不行，自己兜底
> ```
>
> **谁发起，谁兜底。** 从 AppClassLoader 发起时，"下→顶→下"的"下"是**请求沿原路退回 App，App 自己加载**——Extension/Bootstrap 只是被 App"顺路问了一嘴"的过客，控制权始终在 App 手里。
>
> **从 Bootstrap 发起就是死路**：它 `parent == null` 没人可问，又因为没有 children 引用**够不到 AppClassLoader**，只能自己试（classpath 里没有驱动）→ 失败。这就是"Bootstrap 够不着驱动类"的字面含义。
>
> TCCL 的魔法不是"让 Bootstrap 往下发"，而是**把发起者从顶层换成 AppClassLoader**，让标准委派链（App 问父 → Extension → Bootstrap）重新完整跑起来。TCCL 之后走的仍是标准双亲委派。

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

→ SPI 机制本身（`META-INF/services/` + ServiceLoader 三要素）见 [[SPI详解]]

---

## 两种打破双亲委派的方式对比

| 方式 | 操作 | 典型场景 |
|------|------|----------|
| **重写 `loadClass()`** | 子加载器优先自己加载，不委派给父 | Tomcat WebappClassLoader |
| **线程上下文类加载器** | 父加载器反叫子加载器去加载 | JDBC SPI、JNDI、JAXB |

前者是从**下层打破**（子加载器不听话了），后者是从**上层打破**（父加载器主动调子加载器）。

> 自测入口：[[Java 类加载器测验卷（双亲委派 · TCCL）]]

---

## 面试话术

> **问：什么是线程上下文类加载器？为什么需要它？**
>
> 答：线程上下文类加载器是每个线程持有的一个 ClassLoader 引用，默认继承父线程的设置。它存在的根本原因是**双亲委派是单向的**——上层类加载器无法获取下层加载器加载的类。以 JDBC 为例，`java.sql.Driver` 在 rt.jar 中由 Bootstrap 加载，编译期不依赖 MySQL 驱动，运行时才通过 SPI 发现实现类。但 Bootstrap 看不到 classpath 上的 MySQL 驱动，所以 `DriverManager` 用线程上下文类加载器（此时是 AppClassLoader）去加载驱动实现，实现了"上层调用下层"的反向打破。

> **问：线程上下文类加载器一定等于 AppClassLoader 吗？**
>
> 答：不一定，默认情况下是。但框架可以手动 `setContextClassLoader()` 换成自己的加载器。比如某些 Java EE 容器会把 TCCL 设为当前应用的类加载器，这样 SPI 加载的就是当前应用依赖的实现，而不是全局的。

---

## 补充：TCCL 的本质——Thread 上的一个可设置引用

面试里容易把 TCCL 神化成"一种特殊的加载器"。要点破：

**TCCL 本身不是一种类加载器类型，它只是 `Thread` 对象上的一个普通引用字段（`Thread.contextClassLoader`），静态类型就是 `ClassLoader`。**

| 说法 | 对不对 | 说明 |
|------|:---:|------|
| TCCL 是一种特殊的类加载器 | ❌ | 它就是一个 `ClassLoader` 引用，指向的仍是 App / Platform / 自定义等**普通**加载器实例 |
| AppClassLoader 就是 TCCL | ⚠️ 默认值 | 只是**默认取值**——JVM 启动时把 main 线程的 TCCL 设为 AppClassLoader，且线程创建时复制父线程的值，没被改过就全应用沿用 |
| TCCL 可以手动换 | ✅ | 框架随时 `Thread.currentThread().setContextClassLoader(cl)`，指向别的加载器 |

**命名由来**：它叫"**线程上下文**类加载器"，是因为它挂在**线程**上、代表"**当前执行环境**该用哪个加载器"。同一段 SPI 代码，换个 TCCL 就能加载到不同来源的实现——这正是 Java EE 容器"同一份 `DriverManager` 代码、不同应用加载各自驱动"的根基。

> 正确的理解链是：**TCCL 是"一个可以被设置的引用" → 它的默认值恰好是 AppClassLoader → SPI 靠读这个引用打破"父不见子"的僵局**。它提供的是"**换发起者**"的能力（见上文核心矛盾），而不是一个全新种类的加载器。

---

## 关联知识点

- [[三层类加载器与classpath]] — Bootstrap/Extension/Application 三层加载器详解
- [[jvm八股]] — JVM 八股总览（类加载部分）
- [[jvm_review]] — JVM 模拟面试批改（含本知识点原始出处）
- [[类初始化的主动引用与被动引用]] — 类初始化触发时机
- [[打破双亲委派的经典场景]] — 打破双亲委派两大场景汇总（Tomcat 下层打破 + JDBC/SPI 上层打破）

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-11 20:34 | 2026-08-11 20:35 | 线程上下文类加载器（TCCL）是 JVM 提供的后门，允许上层类加载器通过当前线程的 contextClassLoader 反向调用下层加载器，从而打破双亲委派单向性，主要用于解决 SPI 等场景中核心 API 加载第三方实现类的问题。 | 第一轮背记完成 |
