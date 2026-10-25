---
tags:
  - java
  - kotlin
  - JVM
  - 面试
created: 2026-10-09
updated: 2026-10-09
source: 笔试题复盘（Kotlin 变量声明 var/val + 类型推断）
---

# Kotlin

> **Kotlin** 是由 **JetBrains**（IntelliJ IDEA 的开发者）设计的现代化编程语言，运行在 **JVM** 上，与 Java **完全互操作**。它编译后生成 JVM 字节码，因此在很多考试 / 教材里被归到 **Java 相关章节**——它和 Java 同属 JVM 语言生态（和 Scala 一样，是"能在 JVM 上跑、又能调 Java 库"的语言）。

---

## 一、它是什么 / 从哪来

- **诞生**：2011 年前后由 JetBrains 设计，2016 年发布正式版 1.0。
- **定位**：在**保持与 Java 兼容**的前提下，解决 Java 中冗长、易错的写法，用更少的代码做同样的事。
- **Android**：Google 2017 年宣布 Kotlin 成为 Android 一等语言，2019 年提出 **Kotlin-first**，如今大量新 Android 应用和库首选 Kotlin。
- **不限于 Android**：服务端（Spring Boot、Ktor）、多平台（Kotlin Multiplatform）、前端（Kotlin/JS）都能用。

**为什么笔试把 Kotlin 划进「Java」知识点？** 因为 Kotlin 编译成 JVM 字节码、能**双向调用** Java 代码，考试常把它放在 Java / JVM 章节里讲。

---

## 二、变量声明：`var` 与 `val`

Kotlin 声明变量用两个关键字：

| 关键字 | 含义 | 类比 Java | 可否重复赋值 |
|--------|------|----------|:---:|
| `var` | 可变变量 | 普通变量 | ✅ 可以 |
| `val` | **只读**变量（read-only） | `final` 变量 | ❌ 赋值一次后不能再改 |

```kotlin
var name = "Tabbit"      // 可变
name = "Kotlin"          // ✅

val pi = 3.14            // 只读
// pi = 3.15             // ❌ 编译错误
```

> **`val` ≈ Java 的 `final`**：赋值一次后不能重新指向别的对象。（注意它约束的是"引用不可变"，不是"对象内容不可变"——这点和 Java `final` 一致。）

---

## 三、类型推断：声明变量**不必**指定类型（高频考点）

这是那道题的错误选项 C 所在 —— **Kotlin 声明变量时不必指定类型，编译器会根据初始值自动推断**：

```kotlin
val name = "Tabbit"      // 推断为 String
val age = 20             // 推断为 Int
val price = 9.9          // 推断为 Double
```

也可以**显式**写出类型，但那是可选项、不是强制：

```kotlin
val age: Int = 20        // 显式指定，合法但非必需
```

所以"Kotlin 中声明变量必须指定类型"是**错的**。

> 类比：Kotlin 的类型推断，思路类似 Java 10 引入的 `var` 局部变量类型推断（`var x = 1;`），但 Kotlin 从一开始就支持、且更彻底。

---

## 四、和 Java 的互操作性：`println()` 的背后

**题目选项 A 正确**：Kotlin 的 `println()` 是**顶层函数**（`kotlin.io` 包），其底层实现**调用了 Java 的 `System.out.println()`**。

```kotlin
println("hello")
// 底层 → System.out.println("hello")
```

这正是 Kotlin / Java **互操作性**的体现：Kotlin 可以直接使用整个 Java 标准库和第三方 Java 库，Java 也能调用 Kotlin 编译出的类。

---

## 五、Kotlin 的主要特性（面试可展开）

| 特性 | 说明 |
|------|------|
| **空安全（Null Safety）** | 通过 `?` 类型语法在**编译期**避免大部分 NPE；`String?` 表示可空 |
| **类型推断** | 声明变量可省略类型（见上） |
| **数据类 `data class`** | 自动生成 `equals` / `hashCode` / `toString` / `copy` |
| **扩展函数** | 不修改原类就能给它加新方法 |
| **协程（Coroutines）** | 简化异步 / 并发编程 |
| **Elvis 运算符 `?:`** | 值为 null 时给默认值 |

```kotlin
data class User(val name: String, val age: Int?)

fun main() {
    val user = User("Tabbit", null)
    println(user.age ?: "年龄未知")   // age 为 null → 输出 "年龄未知"
}
```

上面这段一个 `data class` 顶掉 Java 里一大堆样板：构造函数、getter、`equals`、`hashCode`、`toString`。

---

## 六、面试话术（口述版）

**问：Kotlin 是什么？和 Java 什么关系？**

> Kotlin 是 JetBrains 设计的现代化 JVM 语言，2016 年发布 1.0，Google 在 2017 年把它定为 Android 一等语言、2019 年提出 Kotlin-first。它编译成 JVM 字节码，和 Java **完全互操作**——Kotlin 能直接调 Java 库，Java 也能调 Kotlin 编译出的类，所以考试常把它归到 Java 知识点里。语法上它用 `var` 声明可变变量、`val` 声明只读变量（相当于 Java 的 `final`）；声明变量时**不必**指定类型，编译器会做**类型推断**，比如 `val name = "Tabbit"` 直接推断成 `String`，当然也可以显式写类型。此外它有**空安全**能在编译期避免大部分空指针、`data class` 自动生成 `equals`/`hashCode`/`toString`、支持**扩展函数**和**协程**。说到底，它就是想用更少的代码、更强的类型安全，来解决 Java 里冗长易错的老问题。

---

## 关联知识点

- [[Java 类加载器测验卷（双亲委派 · TCCL）]] —— JVM 字节码是 Kotlin / Scala 能"寄居"JVM 的基础
- [[Java基础面试题#JVM、JDK、JRE三者关系？]] —— JVM 只认字节码，不绑死 Java 语言，所以 Kotlin/Scala 也能跑
- [[sealed class]] —— Kotlin 的 `sealed class` 与 Java 17 的 `sealed` 是彼此借鉴的设计
- [[文本块（Text Blocks）]] —— 同为"现代字符串 / 语言特性"话题，可对照
