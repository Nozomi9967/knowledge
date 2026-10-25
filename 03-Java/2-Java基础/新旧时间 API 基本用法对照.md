---
tags:
  - java
  - 日期时间
  - java.time
  - SimpleDateFormat
created: 2026-10-22
updated: 2026-10-22
source: 由 [[时区与时间标准详解笔记]] 第 7 节「Java 正确写法」的展开（2026-10-22）
---

# 新旧时间 API 基本用法对照（Date / Calendar / SimpleDateFormat vs java.time）

## 一句话本质

**老 API（`Date` + `Calendar` + `SimpleDateFormat`）的问题是「可变 + 职责混乱 + 格式化器线程不安全」；新 API（`java.time`）用「不可变 + 类型拆分 + 线程安全格式化器」把它们全部替换掉。日常写新代码一律用 `java.time`，只在读老代码和对接老框架（JDBC、老 SDK）时才需要认识老的。**

这篇只讲**怎么用**，不深挖原理。类型怎么选、为什么 `LocalDateTime` 没有时区，见 [[Java 8 日期时间 API（java.time）]]。

## 一、老 API：三个类，各管一段

### 1. `java.util.Date` —— 其实是个时间戳

```java
Date now = new Date();                 // 当前时刻
Date fromMillis = new Date(1_700_000_000_000L);   // 从毫秒数构造

long millis = now.getTime();           // 取毫秒时间戳（唯一还算好用的方法）
boolean later = a.after(b);            // 比较
```

要点：

- 名字叫 `Date`，本质是**从 1970-01-01 00:00:00 UTC 起的毫秒数**，不含任何时区信息
- 但它的 `toString()` 会按 **JVM 默认时区**打印，所以同一毫秒在不同机器上打印结果不同
- **月份从 0 开始**（`new Date(2026 - 1900, 9, 22)` 才是 2026 年 10 月），`getYear()` 返回的是「年份 − 1900」，几乎没人记得住
- **可变**：`setTime()` 会改自己，作为参数传出去有被改的风险

> 结论：`Date` 除了当「毫秒数的容器」用，其余方法都别碰。

### 2. `java.util.Calendar` —— 想补 Date 的坑，结果挖了更多坑

```java
Calendar cal = Calendar.getInstance();          // 默认时区 + 默认 Locale
int year  = cal.get(Calendar.YEAR);             // 2026
int month = cal.get(Calendar.MONTH);            // ⚠️ 0~11，10 表示 11 月
int day   = cal.get(Calendar.DAY_OF_MONTH);     // 1~31

cal.add(Calendar.DAY_OF_MONTH, 7);              // 加 7 天（会正确处理跨月跨年）
cal.set(Calendar.HOUR_OF_DAY, 0);               // 设成 0 点
cal.set(Calendar.MINUTE, 0);
cal.set(Calendar.SECOND, 0);

Date d = cal.getTime();                         // Calendar → Date
cal.setTime(d);                                 // Date → Calendar
```

要点：

- **月份依然从 0 开始**（`Calendar.JANUARY == 0`），这是最容易写错的地方
- `DAY_OF_MONTH` 和 `DATE` 是同一个字段的别名；`DAY_OF_WEEK` 从**周日 = 1** 开始
- 想设时区：`Calendar.getInstance(TimeZone.getTimeZone("GMT"))`
- 同样**可变**，同样线程不安全

### 3. `SimpleDateFormat` —— 格式化 / 解析，但线程不安全

```java
SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
sdf.setTimeZone(TimeZone.getTimeZone("GMT"));   // ⚠️ 不设就用 JVM 默认时区

String text = sdf.format(new Date());           // Date → String
Date parsed = sdf.parse("2026-10-22 09:00:00"); // String → Date（抛 ParseException）
```

#### 补一句：`Locale` 是什么，为什么这里必须写 `Locale.US`

`Locale`（区域设置）表示「用哪种语言、哪个地区的习惯」，写法是 `语言_国家`，如 `zh_CN`、`en_US`、`ja_JP`。它决定格式化时**文字部分的语言**——pattern 里的 `EEE`（星期）和 `MMM`（月份）在文本模式下会去查 Locale 对应的名称表：

```java
new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(d);     // Thu, 22 Oct 2026
new SimpleDateFormat("EEE, dd MMM yyyy", Locale.CHINA).format(d);  // 星期四, 22 十月 2026
```

**不传 Locale 就用 JVM 默认值**（`Locale.getDefault()`），于是同一份代码在中文开发机上输出「星期四」，在英文服务器上输出 `Thu`——典型的「本地好好的，上线就不对」。

更隐蔽的是 Locale 还会影响数字格式和日历体系：阿拉伯语环境可能把年份格式化成阿拉伯-印度数字；泰国的默认日历是**佛历**，`yyyy` 输出的年份比公历大 543 年。

所以 HTTP 日期（要求英文缩写）必须锁死 `Locale.US`，不能靠默认值：

```java
// ✅ 显式锁语言，不依赖部署环境
new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
```

> [!warning] 同源坑：`toLowerCase` / `String.format` 也吃默认 Locale
> 土耳其语环境下 `"FILE".toLowerCase()` 会得到 `fıle`（`I` 变成无点 `ı`），业内叫 **Turkish-I problem**；部分欧洲 Locale 用逗号做小数点，`String.format("%.2f", 3.14)` 会输出 `3,14`。凡是**机器可读**的字符串转换，一律显式传 `Locale.ROOT`（语言中立）：
> ```java
> "FILE".toLowerCase(Locale.ROOT);
> String.format(Locale.US, "%.2f", x);
> ```

`DateTimeFormatter` 同样接受 Locale，而 `DateTimeFormatter.RFC_1123_DATE_TIME` 这个预置常量内部已经锁好了英文缩写和 GMT 时区——用它就不必再操心 Locale 和 TimeZone 两件事。

**线程不安全的实证**：内部持有一个可变的 `Calendar`，`format()` / `parse()` 都会读写它。多线程共享一个实例会出现解析出错误日期、抛 `NumberFormatException`、甚至死循环。

三种将就的修法（都不如换新 API）：

```java
// ① 每次 new（性能差）
// ② 方法内加 synchronized（串行化）
// ③ ThreadLocal 各存一份（啰嗦，且要注意内存泄漏）
private static final ThreadLocal<SimpleDateFormat> TL =
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
```

### 老 API 的格式符陷阱（笔试高频）

| 写法 | 含义 | 常见错误 |
|------|------|---------|
| `yyyy` | 年（calendar year） | 写成 `YYYY` 会变成**周所在的年**，跨年那一周会差一年 |
| `MM` | 月 | 写成 `mm` 就变成分钟 |
| `dd` | 日 | 写成 `DD` 就变成**一年中的第几天** |
| `HH` | 24 小时制 | 写成 `hh` 变成 12 小时制，下午 3 点变 03 |
| `mm` | 分 | — |
| `ss` | 秒 | — |
| `SSS` | 毫秒 | — |
| `E` | 星期（`Wed`） | — |
| `z` / `Z` | 时区名 / 偏移（`CST` / `+0800`） | `z` 可能输出有歧义的 `CST`，见 [[时区与时间标准详解笔记#事故二：用 CST 缩写]] |

一句话记：**月大日小、年四时二、分小秒小**——`MM` 月、`dd` 日、`yyyy` 年、`HH` 时、`mm` 分、`ss` 秒。

## 二、新 API：类型按「职责」拆开

### 1. 四个最常用的类型

```java
LocalDate      d  = LocalDate.now();              // 2026-10-22（只有日期）
LocalTime      t  = LocalTime.now();              // 10:30:00.123（只有时间）
LocalDateTime  dt = LocalDateTime.now();          // 2026-10-22T10:30:00.123（日期+时间，无时区）
Instant        i  = Instant.now();                // 2026-10-22T02:30:00.123Z（UTC 时刻，等价于时间戳）
```

创建与解析：

```java
LocalDate d1 = LocalDate.of(2026, 10, 22);        // ⚠️ 月份从 1 开始，和 Calendar 相反
LocalDate d2 = LocalDate.parse("2026-10-22");     // 默认 ISO 格式，不用写 pattern
LocalDateTime dt = LocalDateTime.parse("2026-10-22T10:30:00");
Instant i = Instant.ofEpochMilli(1_700_000_000_000L);
```

> `LocalDate.of(2026, 10, 22)` 的 10 就是十月——**这是从老 API 迁过来最容易搞反的一点**。

### 2. 加减与比较：全部返回新对象

```java
LocalDate d = LocalDate.of(2026, 10, 22);

LocalDate next  = d.plusDays(7).minusMonths(1);   // 链式调用，d 本身不变
LocalDate withY = d.withYear(2027);               // 改某个字段

boolean before = d.isBefore(next);
boolean after  = d.isAfter(next);
boolean same   = d.isEqual(next);
```

**不可变是它的核心优势**：`d` 永远是 2026-10-22，怎么加都不变。所以可以放心当常量、放心跨线程传。

### 3. 时区相关：`ZonedDateTime` / `OffsetDateTime`

```java
ZoneId shanghai = ZoneId.of("Asia/Shanghai");
ZoneId newYork  = ZoneId.of("America/New_York");

ZonedDateTime zdt = ZonedDateTime.now(shanghai);          // 2026-10-22T10:30+08:00[Asia/Shanghai]
OffsetDateTime odt = OffsetDateTime.now(ZoneOffset.UTC);  // 2026-10-22T02:30Z
```

两个方法名字很像但**语义完全相反**，必须分清：

| 方法 | 保持什么不变 | 结果 |
|------|------------|------|
| `withZoneSameInstant(zone)` | **时刻**不变 | 钟面时间跟着变（北京 10:30 → 纽约 22:30 前一天） |
| `withZoneSameLocal(zone)` | **钟面时间**不变 | 时刻变了（北京 10:30 → 纽约 10:30，但那是另一个瞬间） |

日常 99% 的场景要的是 `withZoneSameInstant`（换时区展示同一时刻）。`withZoneSameLocal` 只用于「我要表达纽约当地的 10:30」这类场景。

### 4. `DateTimeFormatter`：线程安全，可以当常量

```java
// 预置格式
DateTimeFormatter.ISO_LOCAL_DATE          // 2026-10-22
DateTimeFormatter.ISO_LOCAL_DATE_TIME     // 2026-10-22T10:30:00
DateTimeFormatter.RFC_1123_DATE_TIME      // Thu, 22 Oct 2026 02:30:00 GMT ← HTTP 日期就用它

// 自定义格式
private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

String text = LocalDateTime.now().format(FMT);
LocalDateTime parsed = LocalDateTime.parse("2026-10-22 10:30:00", FMT);
```

- **线程安全**，可以直接 `static final` 共享，不用加锁也不用 `ThreadLocal`
- 格式符和 `SimpleDateFormat` 基本一致，但 `yyyy` 不会再被 `YYYY` 坑——新 API 里 `YYYY` 是「week-based year」，含义明确
- 注意：`DateTimeFormatter` 里**大小写敏感**的坑依旧存在（`MM` 月 / `mm` 分）

### 5. `Duration` 与 `Period`：两个「差」

```java
Duration dur = Duration.between(t1, t2);     // 基于时刻：秒 + 纳秒
long secs = dur.getSeconds();

Period per = Period.between(d1, d2);         // 基于日期：年 + 月 + 日
int days = per.getDays();

// 想要「一共差多少天」用 ChronoUnit，不要用 Period
long totalDays = ChronoUnit.DAYS.between(d1, d2);
```

**区别一句话**：`Duration` 数的是「流逝的时间」（时分秒），`Period` 数的是「日历上的跨度」（年月日）。`Period.between(1月31日, 3月1日)` 得到的是「1 个月 1 天」而不是「30 天」，要总天数就用 `ChronoUnit.DAYS`。

## 三、新旧互转：`Instant` 是唯一桥梁

```java
// 老 → 新
Date old = new Date();
Instant instant = old.toInstant();
ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
LocalDateTime ldt = instant.atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime();

// 新 → 老
Date back = Date.from(instant);
```

- `Date` ⇄ `Instant`：`date.toInstant()` / `Date.from(instant)`，**这是唯一的中转点**
- `Calendar` ⇄ `Instant`：`cal.toInstant()` / `Date.from(instant)` 后再 `cal.setTime(...)`
- `LocalDateTime` **不能直接转 `Instant`**，因为它没有时区——必须先 `atZone(zoneId)` 补上时区，这也是「`LocalDateTime` 表示不了确定时刻」的直接体现

## 四、一张对照表收口

| 需求 | 老 API | 新 API |
|------|--------|--------|
| 当前时刻 | `new Date()` | `Instant.now()` / `LocalDateTime.now()` |
| 年月日 | `Calendar.get(YEAR/MONTH/DAY_OF_MONTH)` | `LocalDate` |
| 加减天数 | `cal.add(DAY_OF_MONTH, n)` | `date.plusDays(n)` |
| 格式化 | `SimpleDateFormat.format()`（线程不安全） | `DateTimeFormatter`（线程安全） |
| 解析字符串 | `SimpleDateFormat.parse()`（抛 `ParseException`） | `LocalDate.parse()` / `DateTimeFormatter.parse()`（抛 `DateTimeParseException`） |
| 时间戳 | `date.getTime()` | `instant.toEpochMilli()` |
| 时区 | `TimeZone` + `Calendar` | `ZoneId` + `ZonedDateTime` |
| 求差 | 手算毫秒差 | `Duration` / `Period` / `ChronoUnit` |
| 月份起点 | **0** | **1** |
| 可变性 | 可变 | 不可变 |

## 五、三个必须记住的坑

**坑一：`SimpleDateFormat` 不能共享。** 只要看到 `static SimpleDateFormat`，就是 bug。换成 `DateTimeFormatter` 直接解决。

**坑二：月份起点反了。** `Calendar.MONTH` 从 0 开始，`LocalDate.of()` 从 1 开始。迁移代码时这是最高频的 off-by-one。

**坑三：`LocalDateTime` 不等于时间戳。** 它没有时区，存进数据库、跨时区传输时都要明确约定「这是哪个时区的钟面时间」。真正要记录「事件发生的瞬间」用 `Instant`，存库也建议统一存 UTC。

## 面试话术

> 老的时间 API 是 `Date`、`Calendar`、`SimpleDateFormat` 三件套，问题在于：`Date` 名字叫日期其实是毫秒时间戳，月份还从 0 开始；`Calendar` 补了字段访问但月份照样从 0 开始，而且这两个类都是可变的；`SimpleDateFormat` 更麻烦，它内部持有一个可变的 Calendar，format 和 parse 都会读写这个共享状态，多线程共用一个实例会解析出错误日期甚至抛异常，所以平时只能加锁、ThreadLocal 或者每次 new。
>
> Java 8 的 `java.time` 从根上换掉了这套：所有类型不可变，plusDays 之类是返回新对象，天然线程安全；`DateTimeFormatter` 也是线程安全的，可以直接当 static 常量共享。类型上按职责拆开——`LocalDate`、`LocalTime`、`LocalDateTime` 是不带时区的钟面时间，`Instant` 是时间线上的确定时刻、相当于时间戳，`ZonedDateTime` 带完整时区规则、能处理夏令时。新旧互转的唯一桥梁是 `Instant`，Date 用 `toInstant()` 和 `Date.from()`。
>
> 有几个坑要记：`SimpleDateFormat` 绝对不能声明成 static 共享；`Calendar` 的月份从 0 开始而 `LocalDate.of` 从 1 开始，迁移时最容易差一个月；`LocalDateTime` 没有时区，不能直接转 `Instant`，必须先用 `atZone` 补时区，所以它表示不了「全球唯一的时刻」。

## 关联知识点

- [[Java 8 日期时间 API（java.time）]] — 新 API 的类型选型与原理展开（本文只讲用法）
- [[时区与时间标准详解笔记]] — GMT/UTC、时区表示法、HTTP 日期格式、`CST` 歧义
- [[时区与时间标准详解笔记]] — GMT / UTC / 时区偏移的辨析，HTTP 日期头的 IMF-fixdate 格式
