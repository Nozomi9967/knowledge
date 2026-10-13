---
tags:
  - java
  - jvm
  - 类加载
  - 双亲委派
created: 2026-06-10
updated: 2026-10-08
---

# 三层类加载器与 classpath

## 层次结构

```
┌─────────────────────────────────────┐
│   Bootstrap ClassLoader（启动类加载器） │  ← C++ 实现，getParent() = null
│   加载：<JAVA_HOME>/lib/             │
└──────────────┬──────────────────────┘
               ↑ 委派
┌──────────────┴──────────────────────┐
│   Extension ClassLoader（扩展类加载器） │  ← Java 实现，sun.misc.Launcher$ExtClassLoader
│   加载：<JAVA_HOME>/lib/ext/         │
└──────────────┬──────────────────────┘
               ↑ 委派
┌──────────────┴──────────────────────┐
│ Application ClassLoader（应用类加载器）│  ← Java 实现，sun.misc.Launcher$AppClassLoader
│   加载：classpath                    │
└──────────────┬──────────────────────┘
               ↑ 委派
┌──────────────┴──────────────────────┐
│   自定义 ClassLoader                 │  ← 用户继承 ClassLoader 实现
└─────────────────────────────────────┘
```

---

## 三个加载器详解

### 1. Bootstrap ClassLoader（启动类加载器）

| 属性 | 说明 |
|------|------|
| **语言** | C++ 实现，是 JVM 的一部分，不是 Java 对象 |
| **parent** | 无（`getParent()` 返回 `null`） |
| **加载路径** | `<JAVA_HOME>/lib/` 目录（`rt.jar`、`charsets.jar` 等）及 `-Xbootclasspath` 指定路径 |
| **核心内容** | `java.lang.*`、`java.util.*`、`java.sql.*`、`java.io.*` 等所有 Java 标准库 |

> **为什么 `getParent()` 是 null？** 因为 Bootstrap 不是 Java 写的，它没有对应的 Java 对象实例。这是面试常问的坑——并不是"没有父加载器"，而是"父加载器不是 Java 对象，获取不到引用"。

### 2. Extension ClassLoader（扩展类加载器）

| 属性 | 说明 |
|------|------|
| **语言** | Java 实现，类名 `sun.misc.Launcher$ExtClassLoader` |
| **parent** | Bootstrap ClassLoader |
| **加载路径** | `<JAVA_HOME>/lib/ext/` 目录或 `java.ext.dirs` 系统变量指定的路径 |
| **核心内容** | 标准扩展库（JDK 的扩展功能，如加密扩展包） |

> 实际面试中 Extension ClassLoader 问得较少，重点在前两个加载器。

> [!warning] 路径修正 + JDK 9 起扩展机制已被移除
> **① 上表路径是笼统写法，JDK 8 下严格是 `<JAVA_HOME>/jre/lib/ext`**（注意有 `/jre` 一层；或由 `java.ext.dirs` 系统变量指定），加载的是该目录下 **JAR 包里打包的 `.class`**，不是一堆散落的 class 文件。
>
> **② JDK 9 起扩展机制（Extension Mechanism）被彻底移除**，`ExtClassLoader` 随之**改名为「平台类加载器（PlatformClassLoader）」**：
> - JDK 9 模块化后不再有 `jre/lib/ext` 这个目录，转而加载 **JDK 定义的平台模块**（`java.base` 之外的 Java SE 平台模块，如 `java.sql`、`java.xml` 等）；
> - 三层结构变成：**启动类加载器 → 平台类加载器（PlatformClassLoader）→ 系统/应用类加载器（AppClassLoader）**；
> - 所以 JDK 8 和 JDK 9+ 回答"三层类加载器"时，中层加载器的**名字和加载对象都不同**，面试被追问版本时要能区分。
>
> 详见 [[jvm八股#类加载器有哪些？]]（同一处已按「扩展类加载器（Java 8）/ 平台类加载器（Java 9+）」表述）。

### 3. Application ClassLoader（应用类加载器）

| 属性 | 说明 |
|------|------|
| **语言** | Java 实现，类名 `sun.misc.Launcher$AppClassLoader` |
| **parent** | Extension ClassLoader |
| **加载路径** | **classpath**（见下一节详解） |
| **核心内容** | 项目自己写的代码 + 所有依赖的第三方 jar |

> 这也是 `ClassLoader.getSystemClassLoader()` 返回的默认类加载器。

---

## classpath 到底是什么？

Application ClassLoader 负责加载的 classpath，在不同场景下对应不同路径：

### 命令行场景

```bash
java -classpath bin;lib/* com.example.Main
# classpath = bin/ + lib/*.jar
```

不传 `-cp` 时，默认 classpath = **当前目录（`.`）**

### Maven/Gradle 项目（最常规）

```
项目根/
├── src/main/java/          ← 源码
├── src/main/resources/     ← 配置文件
└── target/
    ├── classes/            ← 编译后的 .class 和资源文件 —— classpath 之一
    └── dependency/         ← Maven 下载的依赖 jar 包 —— classpath 之二
```

AppClassLoader 扫描的就是 **`target/classes/`（你的代码）** + **所有依赖 jar**。

### IDE 直接运行

```
项目根/
├── src/
└── out/                    ← IDEA 编译输出目录（classpath）
    └── production/
        └── com/example/Foo.class
```

classpath = IDE 的编译输出目录。

### Spring Boot 打包后

```
my-app.jar
├── BOOT-INF/classes/       ← 你的代码（由 LaunchedURLClassLoader 加载）
└── BOOT-INF/lib/           ← 依赖 jar 包
```

> Spring Boot 用了自己的 `LaunchedURLClassLoader` 替代标准 AppClassLoader，但逻辑等价。

### 一句话定义

> **classpath = JVM 在运行时会去扫描"有没有这个类"的所有路径的集合**，包括你代码的编译输出目录和所有依赖 jar。

---

## 双亲委派的工作流程

```
ClassLoader.loadClass("com.example.Foo") 被调用
    ↓
1. 检查该类是否已被加载（findLoadedClass）
    ↓ 已加载 → 直接返回
    ↓ 未加载
2. 委派给 parent 加载（parent.loadClass）
    ↓ 递归往上，直到 Bootstrap
    ↓
3. Bootstrap 尝试加载
   - 成功 → 返回
   - 失败 → 退回给 Extension
     - 成功 → 返回
     - 失败 → 退回给 Application
       - 成功 → 返回
       - 失败 → 退给自己的 findClass()
         - 找到 → 加载
         - 找不到 → ClassNotFoundException
```

### 为什么这样设计？

1. **防止重复加载** — 同一个类不会被不同加载器加载多次
2. **安全性** — 防止自定义 `java.lang.String` 替换核心类库
3. **优先级明确** — 核心库优先加载，保证稳定

---

## 典型面试追问

**Q: Bootstrap ClassLoader 为什么不是 Java 对象？**
> 它是 JVM 内部 C++ 实现，没有对应的 Java 类的实例。所以在 Java 代码中调用 `getParent()` 返回 `null`，表示"没有 Java 对象可引用"。

**Q: 为什么 `Class.forName("Foo")` 和 `cl.loadClass("Foo")` 用的加载器不同？**
> `Class.forName()` 默认使用调用方的类加载器（一般是 AppClassLoader）；`cl.loadClass()` 用的就是 `cl` 这个实例。但更关键的区别是：`forName` 会执行初始化，`loadClass` 不会。

**Q: 自定义 ClassLoader 需要继承哪个方法？**
> 只重写 `findClass()` 是标准扩展（不破坏双亲委派）；重写 `loadClass()` 才是真正打破双亲委派。

---

## 关联知识点

- [[jvm八股]] — JVM 八股总览（类加载部分）
- [[jvm_review]] — JVM 模拟面试批改（含本知识点原始出处）
- [[类初始化的主动引用与被动引用]] — 类初始化触发时机
- [[StackOverflowError与OutOfMemoryError（虚拟机栈）]] — 虚拟机栈异常详解
