---
tags:
  - java
  - 字符串
  - 性能优化
  - JVM
created: 2026-06-28
updated: 2026-10-07
---

# 同事写了 `String s = "a" + i + "b" + j;` 在循环里拼接 10 万次出现 CPU 飙高，请解释 JDK 8 与 JDK 17 下编译器分别如何处理，以及你会怎么改写。


## 问题分析

### JDK 8 的处理方式

`"a" + i + "b" + j` 在 JDK 8 中由 `javac` 编译为：

```java
new StringBuilder()
    .append("a")
    .append(i)
    .append("b")
    .append(j)
    .toString();  // 每次循环 new StringBuilder + new String
```

**循环 10 万次 = 10 万个 StringBuilder 对象 + 10 万个 String 对象**，频繁触发 GC，Eden 区被打满，CPU 飙高本质是 GC 压力。

---

### JDK 17 的处理方式（invokedynamic + StringConcatFactory）

JDK 9+ 引入了 `invokedynamic` 指令配合 `StringConcatFactory`：

```
// 字节码层面变为：
invokedynamic #7, 0  // BootstrapMethods: StringConcatFactory.makeConcatWithConstants
```

运行时 JVM **首次触发时 bootstrap 生成优化的拼接方法**，避免了显式 `new StringBuilder`。但关键点是：**在循环里每次仍然产生新 String 对象**，本质上对象分配压力依然存在，只是常数系数更小（去掉了 StringBuilder 本身的分配），并不是零成本的。

#### 「首次触发时 bootstrap 生成」是什么意思

`invokedynamic` 的特点是：**调谁不在编译期定死，而是第一次执行到这里时由 JVM 现场决定**。拆成三段看：

- **bootstrap（引导方法）= 查号台**：普通方法调用（invokevirtual/invokestatic）编译期就写死了调谁、直接调用；`invokedynamic` 编译期只留一句"到时候问查号台"。字符串拼接场景下，这个查号台就是 `StringConcatFactory.makeConcatWithConstants`。
- **首次触发**：这条 `invokedynamic` 指令**第一次被执行**的那一刻，JVM 才去调那个 bootstrap 方法，让它返回"真正该调的方法"。
- **生成 + 缓存**：bootstrap 拿到模板（recipe）和参数类型后，用 MethodHandle **现场生成一个专用拼接方法**，**生成一次后缓存到调用点（CallSite）**，后续直接复用、不再走 bootstrap。

一句话打比方：普通调用 = 通讯录里早存好号码、拨就通；`invokedynamic` = 第一次打电话前先问查号台"号码是多少"，查号台把号码给你、顺手存进通讯录，以后直接拨。

#### 相比 JDK 8 自动生成的 StringBuilder，三个优势

1. **分配更少**：4 个 → 2 个（省掉 StringBuilder 对象本身 + 一次 char[] 复制）
2. **不扩容**：StringBuilder 默认容量 16、不够就 `Arrays.copyOf` 留下废弃数组；invokedynamic 先算精确长度、一次分配到位
3. **解耦编译期**：「怎么拼最高效」交给运行时决定，以后优化不用改字节码、不用改语法

> [!warning] 这三个优势只对「单次拼接」成立
> 对手写、提到**循环外**的 StringBuilder，它**没有优势**——每次仍 new 一个 String、不跨循环累加。所以循环里拼字符串，该手写 StringBuilder 还得手写，invokedynamic 救不了。

---

### 如何改写

#### ✅ 方案一：手动复用 StringBuilder（最经典，JDK 8 友好）

```java
StringBuilder sb = new StringBuilder(10 * 100000); // 预估容量，避免扩容
for (int k = 0; k < 100000; k++) {
    sb.append("a").append(i).append("b").append(j);
}
String result = sb.toString();
```

- `StringBuilder` 在循环**外**创建，只有一次对象分配
- 指定初始容量避免内部 `char[]` 反复扩容（默认 16，每次 ×2）

#### ✅ 方案二：StringJoiner / String.join（有分隔符场景）

```java
StringJoiner sj = new StringJoiner("", "", "");
for (...) {
    sj.add("a").add(String.valueOf(i)).add("b").add(String.valueOf(j));
}
```

#### ✅ 方案三：如果是构造固定格式，用 char[] 或 ByteBuffer（极致性能）

```java
// 适合格式固定、高频写入场景（如序列化、日志）
char[] buf = new char[estimatedSize];
int pos = 0;
for (...) {
    buf[pos++] = 'a';
    pos += writeInt(buf, pos, i); // 手写 int→char 转换
    buf[pos++] = 'b';
    pos += writeInt(buf, pos, j);
}
String result = new String(buf, 0, pos);
```

---

### 三种方案对比

|方案|对象分配|可读性|适用场景|
|---|---|---|---|
|`+` 在循环内|O(n) 次|✅ 高|❌ 禁止用于循环|
|循环外 `StringBuilder`|O(1) 次|✅ 高|✅ 首选|
|`char[]` 手写|O(1) 次|❌ 低|极致性能/序列化|

---

### 面试补充要点

**为什么 `+` 在循环外（常量折叠）是安全的？**

```java
String s = "hello" + "world"; // 编译期直接变成 "helloworld"，零运行时开销
```

**JVM 层的根因**：`String` 是不可变对象，每次 `toString()` 都 `new` 一个新的 `byte[]`（JDK 9+ 内部是 `byte[]`，Latin-1 优化），这是对象分配压力的来源，不是 CPU 计算本身慢。



# `StringConcatFactory` 到底做了什么


---

## StringConcatFactory 到底优化了什么

### 它生成的不是 StringBuilder

JDK 9+ 的默认策略是 `MH_INLINE_SIZED_EXACT`，bootstrap 阶段会用 `MethodHandle` **动态生成**一个专用拼接方法，大致等价于：

```java
// 对于 "a" + i + "b" + j，JVM 生成的逻辑类似：
byte[] buf = new byte[exactSize]; // 一次性算好精确长度
buf[0] = 'a';
putInt(buf, 1, i);          // 直接写入，无装箱
buf[1 + lenOf(i)] = 'b';
putInt(buf, ..., j);
return new String(buf, LATIN1); // 共享这个 byte[]，不再复制
```

关键差异对比：

||JDK 8 `StringBuilder`|JDK 17 `StringConcatFactory`|
|---|---|---|
|对象分配|`new StringBuilder` + 内部 `char[]` + `new String` + 内部 `char[]`（复制一次）|直接 `new byte[]` + `new String`（共享，**不复制**）|
|内存分配次数|**4次**|**2次**|
|扩容风险|有（默认容量16，不够就 `Arrays.copyOf`）|无（精确预算）|

所以"常数系数更小"的实质是：**少了 StringBuilder 对象本身、少了一次 char[] 复制**，但 `new String` 这个分配在循环里仍然每次都发生，10 万次循环该有的 GC 压力还是有。

---

### 为什么循环里还是不够用

`StringConcatFactory` 的优化是**单次拼接**的效率，它没有也不可能跨循环迭代做累积，每次 `invokedynamic` 调用仍然返回一个全新的 `String`。

循环 10 万次的结果是 10 万个短命 `String` 对象打满 Eden，触发频繁 Minor GC——这才是 CPU 飙高的直接原因，跟单次拼接快不快无关。

所以结论不变：**循环里拼字符串，无论 JDK 版本，都要把 `StringBuilder` 提到循环外。**



# JDK 8：为什么是 4 次分配  JDK 17：为什么是 2 次分配


把两种方式的内存分配逐步拆开来讲。

---

## JDK 8：为什么是 4 次分配

`new StringBuilder().append("a").append(i).append("b").append(j).toString()` 执行时：

```
① new StringBuilder 对象本身          → 1 次分配
② StringBuilder 内部 char[] buf       → 1 次分配（默认容量 16）
                                         （容量不够还会 Arrays.copyOf 再分配一次）
③ .toString() 调用 new String(...)    → 1 次分配
④ String 内部 char[] 从 buf 复制过来  → 1 次分配
                                         （这次复制是必须的，否则外部修改
                                          StringBuilder 会影响已生成的 String）
```

**共 4 个堆对象**，而且 ④ 是一次完整的数组内存复制（`System.arraycopy`）。

**④ 的「从 buf 复制过来」是什么意思：** `StringBuilder` 内部有个工作数组 `char[] buf`（默认容量 16、有富余），拼接时字符先写进它：

```
StringBuilder → char[] buf: [a][1][2][3][b][_][_]   ← 有效字符 + 空位
```

调 `toString()` 时，String **不能直接共享这个 buf**——因为 String 是不可变的，若共享，之后再 `sb.append()` 会把已生成的 String 也改了、破坏不可变性。所以 String 把 buf 里**有效字符复制一份**存进自己的 char[]：

```
String → char[]: [a][1][2][3][b]   ← 复制品，String 独占
```

这就是第 4 个对象。**JDK 9+ 没这一步**：StringConcatFactory 直接 new 精确大小的 byte[]，`new String` 直接**采用**（trust，不复制）——数组是刚造的、不怕被别人改。

---

## JDK 17：为什么是 2 次分配

`StringConcatFactory` 在 bootstrap 阶段已经知道模板是 `"a{}\b{}"` 以及每个参数的类型，所以它生成的方法可以：

```
① 算出精确长度：1 + len(i) + 1 + len(j)
   new byte[exactSize]                 → 1 次分配
   直接往里写字符，不需要中间缓冲区

② new String(buf, 0, len, LATIN1)     → 1 次分配
   String 内部直接持有这个 byte[]，
   不再复制一份
```

**共 2 个堆对象**，没有中间 StringBuilder，没有数组复制。

---

## 直观对比

```
JDK 8 堆里的样子：

[StringBuilder] → [char[] buf: a5b3__________]
                                  ↑ 多余空间
[String]        → [char[] copy: a5b3]   ← 从 buf 复制来的


JDK 17 堆里的样子：

[byte[] buf: a5b3]
[String]  ──────┘  直接引用，无复制
```

所以 JDK 17 快在：**省掉了 StringBuilder + 一次数组复制**，但 `new byte[]` 和 `new String` 这两次分配在循环里每次照样发生，GC 压力的根本问题没有消失。



# JDK 8 `StringBuilder`的扩容风险指的是？

## StringBuilder 扩容机制

### 初始状态

```java
new StringBuilder()  // 内部 char[] 容量默认 16
```

### 拼接 `"a" + i + "b" + j` 时

假设 `i = 12345, j = 67890`，实际内容是 `"a12345b67890"`，长度 12。

这次刚好放得下（12 ≤ 16），**没有触发扩容**。

但如果 `i` 和 `j` 位数更多，比如内容变成 `"a123456789b987654321"`，长度 21：

```
① append("a")        → 已用 1,  容量 16  ✅
② append(123456789)  → 已用 10, 容量 16  ✅
③ append("b")        → 已用 11, 容量 16  ✅
④ append(987654321)  → 需要 20, 容量 16  ❌ 触发扩容！
```

### 扩容时发生什么

```java
// StringBuilder 源码（JDK 8）
void ensureCapacityInternal(int minimumCapacity) {
    if (minimumCapacity - value.length > 0) {
        value = Arrays.copyOf(value,
            newCapacity(minimumCapacity)); // 新容量 = 旧容量 * 2 + 2
    }
}
```

```
旧 char[16] ──────────────────────────────┐
                                           ↓ Arrays.copyOf
新 char[34]  ← 重新分配 + 把旧数据复制过来
```

**旧的 `char[16]` 立刻变成垃圾**，等待 GC 回收。

---

### 循环 10 万次的叠加效果

每次循环都是 `new StringBuilder()`，每次都从容量 16 开始，**每次都可能触发扩容**，结果是：

```
10 万次循环，每次产生：
  - char[16]（初始，可能立即废弃）
  - char[34]（扩容后）
  - char[34] 的复制版（toString 时）
  - StringBuilder 对象本身
```

大量短命小对象涌入 Eden 区 → Minor GC 频率极高 → CPU 飙高。

---

### 而 JDK 17 StringConcatFactory 为什么没有这个问题

它在拼接前**先算好精确长度**，一次性分配足够的 `byte[]`，不存在"分配少了再扩容"的情况，所以也不会产生那个废弃的中间数组。




# 常量折叠（Constant Folding）


`"hello" + "world"` 两个操作数在编译期都是已知的字面量，`javac` 直接把结果算出来写进字节码：

```java
// 你写的
String s = "hello" + "world";

// 编译后字节码等价于
String s = "helloworld";  // 常量池里的一个条目，运行时直接 ldc 指令加载
```

用 `javap -c` 看字节码只会看到：

```
ldc  #2  // String helloworld
```

没有任何 `StringBuilder`，没有任何对象分配，零运行时开销。

**但这个优化有严格前提**：两边必须都是编译期常量。只要有一个是变量就失效：

```java
String s = "hello" + "world";   // ✅ 常量折叠
String s = "hello" + getName(); // ❌ getName() 运行时才知道，回退到 StringBuilder/invokedynamic
final String w = "world";
String s = "hello" + w;         // ✅ final 变量也算常量，同样折叠
```

---

# Latin-1 优化（JDK 9+ Compact Strings）

JDK 8 的 `String` 内部是 `char[]`，每个字符固定占 **2 字节**（UTF-16）。

JDK 9 改成了 `byte[]` + 一个 `coder` 标志位：

```java
// JDK 9+ String 源码
private final byte[] value;
private final byte coder;  // LATIN1 = 0, UTF16 = 1
```

逻辑是：

```
字符串内容全是 ASCII/Latin-1？
  → coder = LATIN1，每个字符存 1 byte，内存减半
  → "helloworld" 占 10 bytes，而不是 20 bytes

含有中文、emoji 等？
  → coder = UTF16，退回每字符 2 bytes
```

对于你们这个场景 `"a" + i + "b" + j`，全是 ASCII，JDK 17 下每个拼接结果的 `byte[]` 大小是 JDK 8 `char[]` 的一半，**单个对象更小，GC 压力也更小**，这是 JDK 17 在这个问题上又额外占便宜的地方。

---

### 两者的关系

这两点是独立的优化，放在一起说是因为它们都指向同一个根因：

> String 不可变 → 每次拼接必须产生新对象 → 对象分配是性能瓶颈，不是字符串操作的计算本身慢。

常量折叠是"编译期把分配次数降到零"，Latin-1 是"运行时把每次分配的代价降低一半"，但循环里反复拼接这个问题，两者都治不了，还是得靠 `StringBuilder` 提到循环外。

---

## 关联知识点

- [[jvm八股]] — JVM 内存模型与 GC 机制
- [[Java基础面试题]] — Java 基础面试题总览
- [[编译时类型和运行时类型可能不同]] — 编译期与运行时的行为差异
