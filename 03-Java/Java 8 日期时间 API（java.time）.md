---
tags:
  - java
  - Java8
  - java.time
  - 日期时间
  - 面试
created: 2026-10-09
updated: 2026-10-09
source: 答疑会话（Java 8 新日期时间 API + LocalDateTime 无时区/Instant/ZonedDateTime）
---

# Java 8 日期时间 API（java.time）

> [!abstract] java.time — JSR-310 Date and Time API
> Java 8 引入的**不可变、线程安全**的日期时间 API，用来取代又难用、又线程不安全的 `java.util.Date` / `Calendar` / `SimpleDateFormat`。核心是把「日期」「时刻」「时区」拆成不同职责的类型。

---

## 一、为什么要换掉老 API

| 老 API | 问题 |
|--------|------|
| `java.util.Date` | 名字叫 Date，实际是**时间戳**（毫秒）；月份从 **0** 开始（0 = 一月）；**可变**、设计混乱 |
| `Calendar` | 可变、API 难用、月份同样从 0 开始 |
| `SimpleDateFormat` | ⚠️ **线程不安全** |

**`SimpleDateFormat` 为什么线程不安全？** 它内部持有一个可变的 `Calendar`，`parse()` / `format()` 都会读写这个共享状态。多个线程共用一个实例时会互相踩踏，出现解析出错误日期、抛 `NumberFormatException` 等诡异问题。常见解法是给它加锁、用 `ThreadLocal` 各存一份、或每次 `new`——都很别扭。

`java.time` 从根上解决了：**类型不可变**（天然线程安全，可作共享常量），职责拆分清晰。

---

## 二、核心类型

| 类型 | 表示什么 | 含时区? |
|------|---------|:---:|
| `LocalDate` | 年月日（如 2026-10-09） | ❌ |
| `LocalTime` | 时分秒纳秒 | ❌ |
| `LocalDateTime` | 日期 + 时间 | ❌ |
| `Instant` | **时间线上的一个点**（UTC 时刻，类似时间戳） | ❌（本身就是 UTC） |
| `ZonedDateTime` | 带**时区**的完整日期时间 | ✅ |
| `OffsetDateTime` | 带**偏移量**（如 +08:00）的日期时间 | ✅（偏移） |
| `ZoneId` / `ZoneOffset` | 时区 / 偏移量 | — |
| `Duration` | 两个**时刻**之差（秒 + 纳秒） | — |
| `Period` | 两个**日期**之差（年 + 月 + 日） | — |
| `DateTimeFormatter` | 格式化 / 解析（**线程安全**） | — |

> 记忆分两类：**Local\* 系列 = 墙上时钟（无时区）**；**Instant = 时间线上的确定时刻**；**Zoned/OffsetDateTime = 带时区的墙上时钟**。

---

## 三、不可变 + 线程安全（最该记的一条）

所有 `java.time` 类型都**不可变**：`plus*` / `with*` / `minus*` 等方法**返回新对象，不改原对象**。

```java
LocalDate date = LocalDate.now();
LocalDate nextWeek = date.plusDays(7);   // 新对象；date 本身没变
```

**`DateTimeFormatter` 也是线程安全的**，所以可以放心声明成 `static final` 常量、全局共享：

```java
private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
```

这正是它相对 `SimpleDateFormat` 的关键优势——**不用加锁、不用 ThreadLocal**。

---

## 四、常用写法

```java
LocalDate date = LocalDate.now();
LocalDateTime dateTime = LocalDateTime.now();

// 加减：返回新对象
LocalDate d2 = date.plusDays(7).minusMonths(1);

// 格式化 / 解析
String text = dateTime.format(FMT);                 // 2026-10-09 14:30:00
LocalDateTime parsed = LocalDateTime.parse(text, FMT);

// 比较
boolean after = date.isAfter(d2);
```

---

## 五、时区：`LocalDateTime` 没有时区（高频追问）

**`LocalDateTime` 只是"墙上时钟显示的日期和时间"，例如 2026-10-09 09:00——它没说明这是哪个时区的 9 点。** 北京、伦敦、纽约都可以是"9 点整"，因此它**无法单独表示一个全球唯一的时间点**。

选型口诀：

| 你要表示的东西 | 用哪个 |
|---------------|--------|
| 约会、营业时间（"周五下午 3 点开会"） | `LocalDateTime`（通常还要配合地点/时区解释） |
| **记录事件发生的时刻**（日志时间、订单创建时间） | `Instant`（时间线上确定的一点，全球统一） |
| 特定地区的当地时间（"纽约当地时间下午 3 点"） | `ZonedDateTime` |

> ⚠️ **`LocalDate` / `LocalDateTime` 虽然叫 "Local"，并不代表用了某个特定时区**——"Local" 是相对"带时区"而言的，意思是"不带时区信息的本地钟面时间"。这是个常见误解。

---

## 六、和老 API 的互转

```java
// Date → Instant → ZonedDateTime / LocalDateTime
Date old = new Date();
Instant instant = old.toInstant();                       // 老 → 新的桥梁
ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

// 新 → 老
Date back = Date.from(instant);
```

- `Date` ⇄ `Instant`：`date.toInstant()` / `Date.from(instant)`
- `Instant` → 带时区：`instant.atZone(zoneId)`
- 反过来：`zdt.toInstant()`

---

## 面试话术（口述版）

**问：Java 8 的新日期时间 API 是什么？为什么要用它？**

> Java 8 引入了 `java.time`（JSR-310），核心是用**不可变、线程安全**的类型取代老掉牙的 `Date` / `Calendar` / `SimpleDateFormat`。老 API 有两个大毛病：一是 `Date` 设计混乱，名字叫 Date 其实是时间戳、月份还从 0 开始；二是 `SimpleDateFormat` **线程不安全**，它内部有个可变的 `Calendar`，多线程共享会互相踩踏，所以平时要么加锁、要么 ThreadLocal、要么每次 new。新的 `java.time` 所有类型都不可变——`plusDays` 之类是**返回新对象**、不改原来的，所以天然线程安全；`DateTimeFormatter` 也是线程安全的，可以直接当 `static` 常量共享。类型上它把职责拆开了：`LocalDate`/`LocalDateTime` 是**不带时区的墙上时钟**，`Instant` 是**时间线上的一个确定时刻**，`ZonedDateTime` 是**带时区的完整时间**。

**追问：`LocalDateTime` 和 `Instant`、`ZonedDateTime` 怎么选？**

> 关键看你要表达的是一次"钟面时间"还是一个"确定时刻"。`LocalDateTime` 没有时区，比如"9 点"可能是任何时区的 9 点，所以它**不能单独表示全球唯一的时间点**——约会、营业时间这种"墙上时钟"语义用它。要记录事件真正发生的时刻（日志、订单创建时间），用 `Instant`，它就是时间线上的一个点、类似时间戳。要处理"某个具体地区的当地时间"，用 `ZonedDateTime`。另外注意，`LocalDate`/`LocalDateTime` 里的 "Local" 并**不代表**用了某个特定时区，只是说它不带时区信息。

---

## 关联知识点

- [[Java基础热门面试题#35. Java 8 有哪些新特性？【高频】]] —— 新日期时间 API 是其中一项
- [[Java基础面试题#Java 新特性]] —— Java 8 新特性板块
- [[StreamAPI]] —— 同属 Java 8 核心新特性
- [[Function 和 Consumer（函数式接口）]] —— 同在 Java 8 语言特性
- [[循环内字符串拼接性能分析（JDK 8 vs JDK 17）]] —— 另一处 JDK 8 / 17 差异
