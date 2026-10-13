---
tags:
  - java
created: 2026-07-02
updated: 2026-10-13
---
### **JDK 7+ 字符串常量池(StringTable)从永久代搬到了堆里,`intern()` 会往这个哈希表里塞字符串,业务大量调用会让堆里短命 String 暴涨、StringTable 桶膨胀,直接推高 YGC 频率——替代方案是自己维护一个可控的缓存(Guava Interner / ConcurrentHashMap)**

  

---

  

### **一、字符串常量池是什么**

  

JVM 里有个专门的哈希表叫 **StringTable**,存放"被 intern 的字符串引用"。它的作用是**字符串去重**——相同内容的字符串在池里只保留一份,`s1.intern() == s2.intern()` 为 true(如果内容相同)。

  

**演进历史**:

  

- **JDK 6 及以前**:StringTable 和字符串对象都在**永久代(PermGen)**。永久代大小固定且小(默认 64M),intern 多了直接 `OutOfMemoryError: PermGen space`。而且永久代 GC 不积极,intern 的字符串很难被回收。

- **JDK 7**:**StringTable 本身还在永久代,但它引用的 String 对象搬到了堆**。这是关键转折——`intern()` 不再复制字符串到永久代,而是直接把堆里的引用登记到 StringTable。

- **JDK 8**:永久代废除,StringTable 搬到**堆**(元数据去了 Metaspace,但 StringTable 属于运行时数据,在堆里)。

- **JDK 9+**:StringTable 优化持续,默认桶数从 60013 提升,String 底层从 `char[]` 改成 `byte[]`(Compact String)省一半内存。

  

**StringTable 的实现**:本质是个**固定大小的哈希表**(数组 + 链表),默认桶数由 `-XX:StringTableSize` 控制,JDK 8 默认 60013,JDK 11+ 默认 65536。桶数是**固定的**,不会像 HashMap 那样扩容——所以塞太多字符串时链表会变长,哈希冲突查找变慢。

  

---

  

### **二、`intern()` 的机制和代价**

  

**`String.intern()` 干了什么**:

  

```java

String s = new String("hello"); // 堆里创建 String 对象

String pooled = s.intern(); // 查 StringTable

// 池里有 "hello" -> 返回池里的引用

// 池里没有 -> 把 s 的引用登记进去,返回 s

```

  

JDK 7+ 的 `intern()` 逻辑(HotSpot 源码 `StringTable::intern`):

  

1. 计算字符串的 hash。

2. 查 StringTable 对应桶,遍历链表比较内容。

3. **命中**:返回池里已有的引用。

4. **未命中**:把当前 String 引用登记到 StringTable,返回自己。

  

**为什么业务大量调用会 YGC 飙升**:

  

**1. Eden 区被短命 String 打爆**

  

`intern()` 之前你得**先有一个 String 对象**。典型代码:

  

```java

String key = ("user_" + userId).intern(); // 拼接 -> 新 String -> intern

// 或者

String key = new String(bytes, UTF_8).intern(); // 反序列化 -> 新 String -> intern

```

  

每次调用都在堆里创建**临时 String + 底层的 byte[]/char[]**。如果这个字符串在池里已经存在,临时对象立即变成垃圾——Eden 快速填满,**YGC 频率飙升**。

  

假设 QPS 1 万,每个请求 intern 10 个字符串,每个 50 字节:每秒产生 **5MB 垃圾**,Young 区 512M 的话每 100 秒一次 YGC——这还只是 intern 那部分。

  

**2. StringTable 链表变长,intern 本身变慢**

  

StringTable 桶数固定,intern 越多冲突越严重。**intern 是 STW 敏感操作**——它内部会加锁(每个桶一个锁)保护并发写入。链表长了之后:

  

- 查找 O(链表长度),CPU 飙。

- 锁持有时间变长,并发场景下线程阻塞。

- **Full GC 时 StringTable 是 GC Root 的一部分,遍历它也要时间**——StringTable 巨大时,单次 Full GC 的 Root Scan 阶段能长几秒。

  

**3. 池里的字符串"僵尸化"**

  

StringTable 里的字符串**只要还有引用就不会被回收**。业务如果 intern 了大量一次性字符串(比如把用户 ID 拼进去 intern),这些字符串会一直挂在 StringTable 里,占堆、拖 GC——**变相内存泄漏**。

  

**4. `StringTable::rehash` 不发生**

  

StringTable 不会自动扩容(直到 JDK 更新的版本才有部分优化)。默认 65536 个桶,业务 intern 百万级字符串,平均每个桶 15 个元素,查找性能垮掉。

  

---

  

### **三、诊断手段**

  

**看 StringTable 状态**:

  

```bash

jcmd <pid> VM.stringtable

# 或

jmap -heap <pid> # 老命令,输出里有 StringTable 统计

```

  

输出会告诉你:

  

- Number of entries:池里有多少字符串

- Number of buckets:桶数

- Average bucket size / Longest bucket:平均和最长链表长度

  

**健康值**:平均链表长度 < 5,最长 < 20。超过就有问题。

  

**看 intern 相关 GC Root**:堆 dump 后在 MAT 里找 `sun.misc.Cleaner` 附近或者直接搜 StringTable 引用。

  

**JVM 参数**:

  

```

-XX:+PrintStringTableStatistics # JVM 退出时打印 StringTable 统计

-XX:StringTableSize=1000003 # 调大桶数,必须是素数

```

  

紧急止血可以把 `StringTableSize` 调到 100 万级(用素数),缓解链表变长问题。但**这是治标**,根本还是要减少 intern 调用。

  

---

  

### **四、替代方案**

  

核心思路:**不要滥用 JVM 自带的 StringTable,自己维护一个可控的字符串缓存**。

  

**方案 1:Guava `Interners.newWeakInterner()`(推荐)**

  

```java

private static final Interner<String> INTERNER = Interners.newWeakInterner();

  

String key = INTERNER.intern("user_" + userId);

```

  

**优势**:

  

- **弱引用**:key 没有外部强引用时会被 GC 回收,不会泄漏。

- **分段锁**:内部用 `ConcurrentHashMap` 类似结构,并发性能远好于 StringTable 的桶级锁。

- **不动 JVM 内部数据结构**:StringTable 不涨,Full GC 的 Root Scan 不受影响。

- **可控**:能 monitor 大小,能主动清理。

  

**Guava 还提供 `newStrongInterner()`**——强引用版本,行为更接近 `intern()` 但性能好、无 StringTable 副作用。适合**确定字符串集合有限**的场景(比如枚举、配置常量)。

  

**方案 2:`ConcurrentHashMap` 自实现**

  

```java

private static final ConcurrentHashMap<String, String> POOL = new ConcurrentHashMap<>();

  

public static String intern(String s) {

String existing = POOL.get(s);

if (existing != null) return existing;

String prev = POOL.putIfAbsent(s, s);

return prev != null ? prev : s;

}

```



---
>代码解释

这段代码的意思是：**实现了一个自定义的、线程安全的“字符串常量池”（String Intern Pool）。**

它的核心功能是：**确保相同的字符串在内存中只保留一个对象实例，以节省内存。** 只要两个字符串的内容（`equals`）完全一样，通过这个方法返回的就一定是同一个内存地址的字符串对象。

**逐行大白话解析**

我们可以把这个 `POOL` 想象成一个“单词登记表”：

Java

```
private static final ConcurrentHashMap<String, String> POOL = new ConcurrentHashMap<>();
```

- **干嘛的**：创建了一个全局的登记表。
    
- **为什么用 `ConcurrentHashMap`**：因为它是线程安全的。多个人（多个线程）同时来登记字符串，也不会把表格搞乱。
    

Java

```
public static String intern(String s) {
```

- **干嘛的**：定义了一个方法，你传进去一个字符串 `s`，它还你一个”标准版”的字符串。
    

Java

```
    String existing = POOL.get(s);
    if (existing != null) return existing;
```

- **干嘛的**：**先查表。** 看看这个字符串以前有没有人来登记过。
    
- 如果查到了（`existing != null`），说明已经有标准版了，直接把以前登记的那个返回给你，收工。
    

Java

```
    String prev = POOL.putIfAbsent(s, s);
    String prev = POOL.putIfAbsent(s, s);
```

- **干嘛的**：如果没查到，就**尝试登记**。
    
- `putIfAbsent(key, value)` 的意思是：“如果表里没有这个 key，我就把它存进去；如果已经有了，我就不存，但把表格里原有的值返回给我”。
    
- 这一步是为了防止**并发冲突**。比如线程 A 和线程 B 同时带着 "hello" 冲进来，都发现没登记，然后同时往里存。`putIfAbsent` 能保证最终只有一个人能存成功。
    

Java

```
    return prev != null ? prev : s;
}
```

- **干嘛的**：**做最后的决定。**
    
- 如果 `prev != null`，说明在你准备登记的万分之一秒内，别人抢先一步把 "hello" 登记进去了。那没办法，你只能用别人抢先登记的那个 `prev`。
    
- 如果 `prev == null`，说明你登记成功了！这个 `s` 成了官方认证的标准版，直接返回你自己的 `s`。
    

**为什么不直接用 Java 自带的 `String.intern()`？**

Java 的 `String` 类自己就带了一个 `s.intern()` 方法，功能几乎一模一样，那为什么还要自己写一个呢？

通常有以下几个原因：

1. **垃圾回收（GC）更可控**：原生 `String.intern()` 使用的是 JVM 内部的字符串常量池（StringTable）。在某些老旧的 Java 版本中，这个池子垃圾回收效率很低，容易导致内存溢出（OOM）。自己用 `ConcurrentHashMap` 写的池子，生命周期更容易随着类加载和卸载来控制。
    
2. **性能与调优**：原生的 JVM 字符串池大小是固定的（虽然可以通过参数调节），如果存入几十万个不同的字符串，可能会发生严重的哈希冲突导致性能急剧下降。而 `ConcurrentHashMap` 的扩容和并发性能非常优秀。
    

**适用场景**

当你的程序需要处理**大量重复的字符串**（例如：从数据库或文件中读取了几百万条数据，里面有大量的性别 "男"/"女"、省份 "北京市"/"上海市" 等），用这个方法处理一下，就可以让几百万个小对象合并成几十个对象，**大幅度降低内存占用**。

---
**优势**:

  

- 完全可控,能加大小限制、TTL、监控。

- 性能好,`ConcurrentHashMap` 的分段锁比 StringTable 强。

  

**劣势**:

  

- 强引用,要自己控制大小防泄漏。

- 建议配合 **Caffeine / Guava Cache** 加 maxSize 和 expireAfterAccess,变成 LRU 缓存。

  

**方案 3:Caffeine LRU 缓存(最工业化)**

  

```java

private static final Cache<String, String> POOL = Caffeine.newBuilder()

.maximumSize(100_000)

.expireAfterAccess(Duration.ofHours(1))

.build();

  

public static String intern(String s) {

return POOL.get(s, k -> k);

}

```

  

**优势**:

  

- 高性能(Caffeine 是当前 JVM 上最快的本地缓存)。

- 天然 LRU + TTL,不会无限增长。

- 有 stats(命中率、驱逐次数),便于观测。

  

**适合大规模去重场景**,比如日志字段去重、埋点 tag 去重。

  

**方案 4:改用枚举 / 常量类**

  

如果 intern 的字符串**集合是有限且已知**(比如状态码、类型标识),根本不该用 intern:

  

```java

// 反例：状态码是有限集合，用 String + intern 纯属多余

String status = ("ORDER_STATUS_" + code).intern();

  

// 正解：枚举，类加载时实例化，天然全局唯一

public enum OrderStatus { CREATED, PAID, SHIPPED, ... }

OrderStatus status = OrderStatus.valueOf(code);

```

  

**为什么枚举是正解**:

  

- **`==` 比较**:枚举实例是 JVM 类加载时创建的**单例**,同一枚举值全局只有一个对象,`==` 直接比较地址,零开销。和 intern 后的字符串一样能享受 `==` 的好处,但**不需要运行时去池里查**。
- **类型安全**:`OrderStatus` 是编译期已知类型,IDE 能补全、编译器能检查。用 String 存状态码,拼错了编译期不报错,线上才炸。
- **零运行时开销**:枚举实例在类加载时一次性创建,不需要像 intern 那样每次调用都走哈希查找 + 桶锁。
- **元空间存放**(JDK 8+):枚举实例存在元空间,不占堆内存,不参与 Young/Old GC。元空间默认上限是物理内存的 1/4,少量枚举完全无压力。

  

**关于"枚举不回收"的迷思**:

  

枚举实例**确实几乎不会被回收**——只有加载该枚举的类加载器卸载时才会回收(Web 应用重启/重新部署)。但这是**特性不是 bug**,因为:

  

- 枚举的集合是**固定的**,不会无限增长。10 个状态码就是 10 个对象,不随业务运行膨胀。
- intern 的字符串如果集合也是固定的,那它和枚举一样不回收,但 intern 的字符串在堆里,Full GC 时照样要被 Root Scan 遍历;枚举在元空间,不参与堆 GC。
- 所以"不回收"对枚举不是问题,因为总量固定且不在堆里。

  

**什么时候不该用枚举**:

  

- 集合**动态变化**(运行时才知道有哪些值) → 枚举写死在代码里,改一个值要发版。
- 集合**极大**(上千个值) → 枚举类膨胀,初始化慢,元空间占用大。
- 值来自**外部配置/数据库** → 不确定的集合不该硬编码为枚举。
- 这些场景用 `String` + `ConcurrentHashMap` 自维护缓存(方案 2/3),或者干脆用 `String` + `HashMap` 就行,不需要 intern。

  

**一句话**:集合固定 → 枚举;集合动态 → 应用层可控缓存;永远不需要 → JVM 自带的 `intern()`。

  

**方案 5:避免不必要的 intern**

  

很多代码 intern 是无脑加的,反问自己:

  

- **只用作 Map 的 key?** 不需要 intern,`equals` 就够了。`HashMap` 的性能不依赖 `==`。

- **只是想省内存?** 用 `-XX:+UseStringDeduplication`(G1 支持,自动去重堆里的 String 底层字符数组),零代码改动。

- **只是频繁比较?** 用枚举或者常量。

  

**真正需要 intern 的场景很少**,主要是:大规模字符串处理(编译器、SQL 解析器)、要求 `==` 快速比较、内存极度敏感——这些场景也应该用可控的 Interner 而不是 JVM 自带的。

  

---

  

### **五、优化决策树**

  

遇到 intern 导致 YGC 飙升,我的处理顺序:

  

**第一步**:确认是不是 intern 问题

- `jcmd VM.stringtable` 看 StringTable 是不是巨大。

- 分配火焰图(`async-profiler --event alloc`)看是不是 intern 相关调用在疯狂分配 String。

  

**第二步**:能不 intern 就不 intern

- 有限集合改枚举。

- 只做 Map key 直接用 String。

- 只为省内存开 `UseStringDeduplication`。

  

**第三步**:必须去重的换 Guava Weak Interner

- 弱引用防泄漏,性能好,零 JVM 副作用。

  

**第四步**:高频热点用 Caffeine LRU

- 大量去重 + 可控大小 + 监控 stats。

  

**第五步**:实在改不了代码,JVM 层面缓解

- `-XX:StringTableSize=1000003`(素数)扩桶数。

- `-XX:+UseStringDeduplication`(G1)自动去重底层数组,减少 String 内存但不减 StringTable 压力。

  

---

  

### **六、`UseStringDeduplication` 顺便说说**

  

G1 的这个参数**和 intern 是两码事**,但常被混淆。它的机制是:

  

- **YGC 时**,G1 遍历 Survivor 区的 String 对象,把内容相同的 String 的**底层 char[]/byte[]** 指向同一份。

- 只共享底层数组,**String 对象本身还是独立的**。

- 完全在 GC 线程做,业务代码零改动。

  

**优势**:开一个参数,大量重复字符串场景能省 10~30% 堆。

  

**劣势**:只在 G1 上有,ZGC 也支持但 Shenandoah / Parallel 没有;而且**只减内存不减 String 对象数**,YGC 频率不会因此下降。

  

所以如果**痛点是"堆里 String 太占内存"** → 开 `UseStringDeduplication`;**痛点是"YGC 飙升"或"StringTable 巨大"** → 换掉 `intern()` 调用。**两者互补,不冲突**。




### 七、为啥intern() 调用会导致YGC飙升


这是一个非常经典且容易踩坑的 Java 性能问题。

你提到的 `intern()` 导致 YGC（Young GC，新生代垃圾回收）飙升，通常发生在**大量使用 Java 原生 `String.intern()`** 的场景下。

主要原因可以归结为以下三点：

#### 1. 产生大量短命的 String 对象（最直接原因）

当你调用 `s.intern()` 时，你通常是先通过 `BufferedReader`、JSON 解析器或数据库驱动读取了一行数据，这时候内存中已经创建了一个堆上的 `String` 对象（假设叫 `s1`）。

- 你调用 `s1.intern()`，JVM 去常量池里找，发现已经有了，于是返回了常量池里的引用 `s_pool`。
    
- 接下来，你的代码会使用 `s_pool`，而刚才从数据源读出来的那个 **`s1` 对象就变成了没有任何人引用的“垃圾”**。
    
- 如果你狂热地在循环里读入数百万条数据并不断 `intern()`，就会在**新生代（Eden区）瞬间产生海量的短命 `String` 对象**。Eden 区迅速被填满，从而直接触发频繁的 YGC。
    

#### 2. StringTable 扫描和存活对象标记的开销

在 Java 中，原生的字符串常量池是由一个叫 `StringTable` 的底层哈希表（C++ 实现）管理的。在 YGC 阶段，GC 线程不仅要回收 Eden 区，还需要做两件事：

- **标记存活对象**：如果某个字符串被常量池引用，且它在堆中（比如 Java 7 之后常量池移到了堆中），GC 需要去扫描 `StringTable` 来确保这些字符串不被错误地回收。
    
- **常量池自身的清理**：如果 `StringTable` 里的某个字符串已经没有被任何强引用指向了，GC 需要把它从 `StringTable` 中剔除。
    

随着你不断调用 `intern()`，`StringTable` 引用的对象越来越多，**GC 在新生代扫描和标记这些“逻辑上存活”的字符串对象所耗费的时间就会变长**，表现出来就是 YGC 的停顿时间（Pause Time）明显拉长。

#### 3. StringTable 哈希冲突严重（导致单次 GC 变慢）

原生的 `StringTable` 是有固定大小的（默认值取决于 Java 版本，比如 Java 8 默认是 60013）。

- 如果你 `intern()` 了几十万甚至几百万个**不同的**字符串，这个哈希表就会塞满，发生极其严重的**哈希冲突**（Hash Collision）。
    
- 哈希冲突后，哈希表里的链表会变得非常长。
    
- GC 线程在单次 YGC 过程中，为了遍历、查找和清理这个链表，需要花费极高的 CPU 代价。这会导致**原本只需几毫秒的 YGC，飙升到几百毫秒甚至几秒**。
    

#### 回到你最初的代码：为什么自定义的 POOL 能缓解这个问题？

再看你最开始手写的这个自定义池：

Java

```
private static final ConcurrentHashMap<String, String> POOL = new ConcurrentHashMap<>();
```

如果你用这个自定义的 `POOL.intern(s)`，对比原生的 `String.intern()`，它在某些场景下对 YGC 更友好：

1. **避免了 JVM 底层 StringTable 的压力**：`ConcurrentHashMap` 运行在 Java 堆的高层，它的扩容机制非常优秀（红黑树优化），不会像老版本 JVM 的 `StringTable` 那样因为哈希冲突导致全线变慢。
    
2. **两者的共同痛点**：需要注意的是，即使换成自定义的 `POOL`，**原因 1（产生大量短命临时对象）依然存在**。如果你在外面频繁创建新 `String` 再传给 `POOL`，Eden 区依然会频繁满。
    

#### 怎么解决 YGC 飙升？

1. **调整 JVM 参数（针对原生 intern）**：如果你必须用原生的 `String.intern()`，且确定有大量不同的字符串，可以通过 `-XX:StringTableSize=1000003`（传一个更大的质数）来扩大常量池哈希表的大小，减少冲突，这能暴风式缓解 YGC 变慢的问题。
    
2. **从源头减少 String 的创建**：比如在解析数据时，直接使用 `byte[]` 或 `char[]` 进行比较和存储，或者使用第三方库（如 Netty 的 `AsciiString`），连临时 `String` 对象都不要创建。
    
3. **分批或复用**：避免在一个极其密集的死循环里无节制地读数据和 `intern()`。
    

你目前是在处理现有的系统性能瓶颈，还是在设计一个需要处理海量字符串的新模块呢？




### 八、字符串常量池与String的引用关系

这个指向关系确实容易让人绕晕，用一句话说明白就是：

**是字符串常量池（`StringTable`）引用了堆中的字符串对象。** 我们可以把 `StringTable` 想象成一个底层的特殊的 `WeakHashMap`（底层由 C++ 实现），它里面存放的是一个个的 **键值对（Key-Value）**，其中 Key 和 Value 存的都是**指向堆中具体 `String` 对象的内存地址（引用）**。

为了让你彻底看懂，我们来看看在不同情况下，内存里到底发生了什么：

#### 1. 正常情况：常量池强引用堆对象

当你调用 `String s1 = new String("abc").intern();` 时：

1. 堆中有一个你刚刚 `new` 出来的 `String` 对象（假设地址是 `0x111`）。
    
2. 调用 `intern()` 时，JVM 发现 `StringTable` 里没有 `"abc"`。
    
3. 于是，JVM 会在 `StringTable` 里加一个记录，这个记录的指针**指向堆中的 `0x111`**。
    

这时候的引用链是：

> **`StringTable`** $\rightarrow$ **指向** $\rightarrow$ **堆中的 `String` 对象**

只要 `StringTable` 里的这条记录还在，这个 `String` 对象就有被引用，它在 GC 时就被判定为**存活对象**，绝对不会被回收。

#### 2. 回答你的疑问：为什么 GC 时要互相扫描？

结合你刚才提到的两句话，具体的运行逻辑是这样的：

#### 疑问 A：“GC 扫描 StringTable 确保字符串不被错误回收”

因为 `StringTable` 持有了堆中字符串对象的引用。在 GC（比如 YGC）发生时，GC 根对象（GC Roots）会去扫描 `StringTable`。

- GC 发现：_“哦，`StringTable` 里面还拉着堆里 `0x111` 这个对象的实体呢！”_
    
- 结论：这个 `0x111` 对象**不能回收**，得把它标记为存活，并复制到 Survivor 区。
    

#### 疑问 B：“常量池自身的清理：没有强引用指向了，就剔除”

这里说的“没有强引用指向了”，指的是**你的业务代码（也就是堆栈里的局部变量、静态变量等）不再需要这个字符串了**。

1. 假设你的代码里写了 `s1 = null;`，现在你的业务代码里没有任何人知道 `"abc"` 的存在了。
    
2. 但是，**`StringTable` 此时还眼巴巴地引用着堆里的 `0x111` 对象**。
    
3. 如果 `StringTable` 的引用是死板的强引用，那这个对象就永远死不掉（发生内存泄漏）。所以 JVM 内部做了一层特殊处理（类似于弱引用）。
    
4. 在 GC 阶段，GC 线程会专门去检查 `StringTable`：_“虽然你 `StringTable` 引用着 `0x111`，但我发现全天下（整个 JVM 栈和静态变量）已经没有任何人引用 `0x111` 了，你这属于无效引用。”_
    
5. 于是，GC 就会**把这条记录从 `StringTable` 中无情地剔除（注销）**，然后再把堆里的 `0x111` 对象彻底回收掉。
    

#### 总结

- **谁引用谁**：`StringTable`（常量池）**引用**了堆中的 `String` 对象。
    
- **业务强引用**：你的代码（变量）**引用**了堆中的 `String` 对象。
    
- **清理机制**：当**业务强引用**断开后，GC 就会在垃圾回收时，顺手把 `StringTable` 里的**常量池引用**也抹去，并释放堆内存。
  

---

  

一句话总结:**JDK 7+ 的 StringTable 在堆里,`intern()` 每次调用都要先创建临时 String → Eden 快速填满 → YGC 飙升;同时 StringTable 桶数固定,intern 太多会链表变长、锁竞争、Full GC Root Scan 变慢**。替代方案的核心思路是**"把去重从 JVM 的 StringTable 移到应用层可控的容器"**——首选 **Guava `newWeakInterner()`**(弱引用无泄漏)、大规模场景用 **Caffeine LRU**、有限集合直接改枚举、只为省内存开 **`UseStringDeduplication`**。**能不 intern 就不 intern,真要去重也别用 JVM 自带的**——这是这道题的核心答案。