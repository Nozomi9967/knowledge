# Java 后端开发场景化面试题 50 道

> 覆盖：Java 基础与并发、JVM、Spring/Spring Boot、MySQL、Redis、消息队列、分布式与微服务、系统设计与线上排障。

---

## 一、Java 基础与集合（1-6）

1. 线上日志中频繁出现 `ConcurrentModificationException`，定位到一段在 `for-each` 遍历 `ArrayList` 时执行了 `list.remove(obj)` 的代码，请说明产生原因，并给出三种可行的修复方案（含 `Iterator.remove`、`CopyOnWriteArrayList`、`removeIf`）的取舍。→ [[ConcurrentModificationException 详解（for-each remove 问题）|答案解析]]

2. 一个接口需要返回"最近访问的 1000 个用户ID"，要求按访问顺序去重、线程安全、读多写少。请在 `LinkedHashMap`、`ConcurrentHashMap+Deque`、`Caffeine` 之间做技术选型并说明理由。

### 题 2 解析：技术选型分析

#### 需求拆解

|需求点|关键约束|
|---|---|
|最近访问的 1000 个用户ID|有界，LRU 或插入顺序|
|按访问顺序去重|同一 ID 重复访问要更新位置|
|线程安全|多线程并发读写|
|读多写少|读性能优先|---

#### 三种方案逐一分析

##### 方案一：`LinkedHashMap`

```java
// accessOrder=true 即 LRU 模式
Map<Long, Long> cache = Collections.synchronizedMap(
    new LinkedHashMap<Long, Long>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > 1000;
        }
    }
);
```

`LinkedHashMap` 构造函数的三个参数：

| 参数 | 值 | 含义 |
|------|-----|------|
| **initialCapacity** | `1000` | 初始容量，预先分配以减少扩容次数 |
| **loadFactor** | `0.75f` | 负载因子，元素数超过 `capacity × 0.75` 时扩容为 2 倍 |
| **accessOrder** | `true` | `true` = 按访问顺序迭代（LRU 模式），`false` = 按插入顺序 |> `accessOrder=true` 是 LRU 的核心：每次 `get()` / `put()` 会把该条目移到链表尾部，头部始终是"最久未访问"的条目，配合 `removeEldestEntry` 在超过容量时淘汰头部即实现 LRU 淘汰策略。

----
**`removeEldestEntry` 机制拆解：**

```java
protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return false; // 默认实现——永远不淘汰
}
```

- **调用时机**：每次 `put()` / `putAll()` 插入**新条目之后**，`LinkedHashMap` 内部会回调这个方法，传入当前"最老"的条目（即链表头部那个）。
- **返回值语义**：返回 `true` → 自动移除 `eldest` 条目；返回 `false` → 什么都不做。
- **默认不淘汰**：父类 `HashMap` 没有这个方法，`LinkedHashMap` 留出来给子类重写，默认返回 `false`，所以普通 `LinkedHashMap` 永远不会自动删除元素。
- **重写逻辑**：判断 `size() > capacity`，超出容量时返回 `true`，淘汰头部——刚好是 LRU 中最久未使用的那条。
- **`eldest` 参数**：就是当前链表头部的那个条目，由 `LinkedHashMap` 内部传入，开发者不需要手动找它。

---

**优点**

- 原生支持 accessOrder + removeEldestEntry，实现极简
- LRU 语义天然满足"按访问顺序去重"

**致命缺陷**

- `Collections.synchronizedMap` 用**单把全局锁**包裹，读写互斥
- 读多写少场景下，大量读线程全部串行 → **吞吐量瓶颈**
- `get()` 在 accessOrder 模式下会修改链表（写操作），锁竞争更严重

❌ **排除**：线程安全实现代价太高，读多写少场景下性能最差

---

##### 方案二：`ConcurrentHashMap + Deque`

```java
// 需要自己维护两个数据结构的一致性
ConcurrentHashMap<Long, Boolean> map = new ConcurrentHashMap<>();
Deque<Long> deque = new ConcurrentLinkedDeque<>();

public void access(long userId) {
    if (map.putIfAbsent(userId, true) == null) {
        deque.addLast(userId);
        if (deque.size() > 1000) {
            Long evicted = deque.pollFirst();
            map.remove(evicted); // ← 非原子！
        }
    }
    // 已存在的 ID 如何移到队尾？需要 remove + add，非原子！
}
```
---
**`putIfAbsent` 解析：**

```java
// 语义：key 不存在时才 put，返回旧值（不存在返回 null）
default V putIfAbsent(K key, V value) {
    V v = get(key);
    if (v == null) {
        v = put(key, value);
    }
    return v;
}
```

- **原子性保证**（`ConcurrentHashMap` 实现）：`putIfAbsent` 是**原子操作**，在 CAS 自旋中一次性完成"检查不存在 + 插入"，不会被其他线程打断。上面 `Map` 接口的 `default` 实现是非原子的，但 `ConcurrentHashMap` 重写了它，底层用 `ConcurrentHashMap.putVal(onlyIfAbsent=true)` + `synchronized` 锁节点来实现。
- **返回值语义**：key **不存在** → 插入成功，返回 `null`；key **已存在** → 不覆盖，返回已有的 value。
- **这里的用法**：利用返回值做去重判断——返回 `null` 说明是**新用户**，写入 deque；返回非 `null` 说明已存在，跳过 deque 的尾部追加。但这个方案没法把已存在的用户移到队尾，所以不是真正的 LRU。
---
**问题暴露**

- `ConcurrentHashMap` 与 `Deque` 之间**没有跨结构原子性**（详见 [[跨结构原子性与竞态窗口#跨结构原子性]]）
- "去重+更新顺序"需要先从 Deque 中 remove 再 addLast，`ConcurrentLinkedDeque.remove()` 是 O(n)
- 淘汰逻辑（pollFirst + map.remove）存在**竞态窗口**：可能误删刚插入的新 ID（详见 [[跨结构原子性与竞态窗口#竞态窗口]]）
- 要做对需要加 `ReentrantReadWriteLock`，代码复杂度飙升且容易出 bug

❌ **排除**：正确性难以保证，自造轮子成本高，不如用成熟方案

---

##### 方案三：`Caffeine` ✅ 推荐

```java
// 一行配置搞定有界+线程安全+自动淘汰
Cache<Long, Long> cache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(Duration.ofHours(1))
    .build();

cache.put(userId, userId);
List<Long> recentUsers = cache.asMap().keySet()
    .stream().limit(1000).collect(Collectors.toList());
```

详见完整原理分析 → [[Caffeine详解]]

**最终结论**

```
LinkedHashMap  →  ❌ 全局锁，读多写少性能最差
ConcurrentHashMap + Deque  →  ❌ 跨结构无原子性，正确实现复杂
Caffeine      →  ✅ 读无锁、W-TinyLFU 高命中率、生产级线程安全
```

> **一句话理由**：Caffeine 的 StripedBuffer 让读操作走无锁快路径，W-TinyLFU 保证有界+高命中率淘汰，这两点精确命中"读多写少 + 有序去重"的核心矛盾。


---

### 题 2 面试口述稿

> 面试时可以按这个节奏讲，大约 90-120 秒。

---

这个需求拆开看其实是四个约束：第一，有界——最多放 1000 个用户 ID，超过要淘汰最老的；第二，按访问顺序去重——同一个用户 ID 多次访问时更新他的位置，不是重复存储；第三，线程安全——多线程并发读写不能出问题；第四，读多写少——这是最重要的性能约束。基于这四个约束，我对比了三种方案。

第一种是 `LinkedHashMap`，它本身支持 `accessOrder=true` 配合 `removeEldestEntry` 可以很优雅地实现 LRU 去重，代码也简洁。但问题是线程安全——如果用 `Collections.synchronizedMap` 包裹，读写共用一把全局锁，在 `accessOrder=true` 模式下每次 `get()` 都会修改链表结构，锁竞争会非常严重。读多写少的场景下大量读线程全部串行，吞吐量上不去，所以这个方案虽然实现简单，但性能不符合我们的核心诉求，排除。

第二种是自己组合 `ConcurrentHashMap` 加 `ConcurrentLinkedDeque`，想用两个结构分别负责快速查找和顺序维护。但这里有个致命的正确性问题——这两个结构之间没有跨结构的原子性保证。比如"用户已存在时更新位置"需要先从 deque 中 remove 再 addLast，但这两个操作不是原子的，中间可能被其他线程打断。淘汰逻辑——从队头 poll 之后从 map 中 remove——同样存在竞态窗口，可能误删刚插入的新 ID。要解决这些问题就得额外加读写锁，代码复杂度飙升，等于自己造了一个轮子，稳定性还没保障，所以也排除。

第三种是 **Caffeine**，这是 Guava Cache 的继任者，也是 Java 生态里本地缓存的工业级标准。它一行配置就能搞定有界 + 线程安全 + 淘汰策略，底层用分段锁（Striped64）实现，读操作在缓存命中时可以走无锁快路径，这精确命中了"读多写少"的核心矛盾。淘汰算法用的是 W-TinyLFU，比传统 LRU 有更高的命中率，对于"最近访问"这种有访问局部性的场景更友好。而且它的线程安全是生产级验证过的，不需要自己维护任何锁。所以**首选 Caffeine**。

总结一下就是：`LinkedHashMap` 全局锁导致读性能差，`ConcurrentHashMap + Deque` 跨结构无原子性实现复杂且容易出 bug，**Caffeine 在正确性、性能和代码简洁度上都最优，是读多写少场景下的标准答案**。

---

3. 业务中有一个 `HashMap<Long, User>` 缓存对象，重启后从数据库加载 50 万条记录花费 12 秒。请分析可能的性能瓶颈（初始容量、扩容、hash 冲突），并给出优化方案。


### 题 3 解析：HashMap 加载性能优化

> [!abstract] 一句话总结
> **HashMap 加载 50 万条数据耗时 12 秒**，核心瓶颈在于默认初始容量过小导致的反复扩容（rehash），优化方式是预设容量 `new HashMap<>(700000)` 并配合批量查询与并行加载，可将耗时压缩到 1-2 秒级别。

  

下面从三个维度拆解瓶颈，并给出可落地的优化方案。

  

---

  

#### 一、性能瓶颈分析

  

**1. 初始容量与扩容（最主要瓶颈）**

  

`HashMap` 的无参构造默认初始容量为 16，负载因子（load factor）为 0.75，即当元素数量超过 `capacity * 0.75` 时触发扩容（resize），容量翻倍。

  

加载 50 万条数据时，容量增长路径为：

  

$$

16 \to 32 \to 64 \to \cdots \to 524288\ (2^{19}) \to 1048576\ (2^{20})

$$

  

从 16 扩到能容纳 50w 数据（阈值需 ≥ 500000，即容量需 ≥ 666667，向上取 2 的幂为 1048576），**总共需要扩容约 16 次**。

  

每次扩容的代价：

- 新建一个 2 倍大小的 `Node[]` 数组；

- 遍历旧数组所有桶，对每个节点重新计算落点（JDK 8 后通过 `(e.hash & oldCap) == 0` 判断保留在原位还是迁移到 `i + oldCap`）；

- 链表/红黑树需要拆分重组。

  

最后一次扩容时要搬运近 50w 个节点，单次开销就极其可观。**16 次累计搬运的节点总数约等于 100w 次节点重定位操作**，这是 12 秒中占比最大的部分。

  

**2. Hash 冲突**

  

`Key` 是 `Long` 类型，`Long.hashCode()` 实现为：

  

```java

public int hashCode() {

return (int)(value ^ (value >>> 32));

}

```

  

随后 `HashMap` 内部还会做一次扰动：`hash = key.hashCode() ^ (key.hashCode() >>> 16)`，再用 `(n - 1) & hash` 定位桶。

  

风险点：

- 如果数据库主键 `id` 是**连续自增的 Long**（如 1, 2, 3 …），低位分布均匀，冲突率较低，问题不大；

- 如果 `id` 是**雪花算法**或类似有规律的高位时间戳 + 机器位结构，低 16 位可能高度集中，导致**大量 key 落入同一个桶**，形成长链表（JDK 8 链表长度 ≥ 8 且容量 ≥ 64 时会树化为红黑树，但树化本身也耗时）；

- 冲突严重时，每次 `put` 的复杂度从 O(1) 退化为 O(log n) 甚至 O(n)。

  

**3. 其他隐性瓶颈**

  

- **数据库 IO**：50w 条记录如果是单条 `SELECT` 或小批次拉取，网络往返次数会主导耗时；

- **对象构造**：每条记录构造一个 `User` 对象 + `Long` 装箱（自动 `Long.valueOf`），小整数有缓存（-128~127），大 id 全部新建 `Long` 实例，GC 压力大；

- **单线程顺序写入**：完全串行，没利用多核。

  

---

  

#### 二、优化方案

  

**1. 预设初始容量（收益最大、改动最小）**

  

```java

// 公式：initialCapacity = expectedSize / loadFactor + 1

// 50w / 0.75 ≈ 666667，向上取 2 的幂 = 1048576

Map<Long, User> cache = new HashMap<>(1 << 20); // 直接给 2^20

```

  

或者使用 Guava 的工具方法，语义更清晰：

  

```java

Map<Long, User> cache = Maps.newHashMapWithExpectedSize(500_000);

```

  

**收益**：消除全部 16 次 rehash，put 操作回归纯 O(1)，仅此一项即可砍掉 60%-80% 的耗时。

  

**2. 选用更合适的容器**

  

| 场景 | 推荐容器 | 原因 |
|---|---|---|
| 只读缓存，加载后不再修改 | `ImmutableMap` / `Map.copyOf` | 内部紧凑数组，内存与查询均优 |
| 高并发读写 | `ConcurrentHashMap`（同样要预设容量） | 分段锁，避免后续并发竞争 |
| Key 为 `long` 原始类型 | `LongObjectHashMap`（Eclipse Collections / HPPC） | 免装箱，内存省 1/3，速度快 30%+ |

如果 `userId` 真的是 `long`，强烈推荐 Eclipse Collections 的 `LongObjectHashMap<User>`，**省去 50w 个 `Long` 包装对象**，对 GC 和 CPU 缓存命中率都是巨大改善。

  

**3. 数据库加载侧优化**

  

```java

// 反例：逐条或小批查询

for (Long id : ids) userMapper.selectById(id);

  

// 正例：流式 / 分页批量

List<User> batch = userMapper.selectAll(); // 一次性 SELECT *，或按 10w 一页

```

  [[MySQL JDBC 驱动提供的两种[流式读取]机制]]

- 使用 **`ResultSet` 流式读取**（MySQL 驱动设置 `useCursorFetch=true` 或 `Statement.setFetchSize(Integer.MIN_VALUE)`），避免一次性把 50w 条加载进 JDBC 内存；

- **只查需要的列**，减少网络传输和反序列化；

- 分页时按主键范围 `WHERE id > ? LIMIT 100000` 而非 `OFFSET`，避免深分页扫描。

  

**4. 并行加载**

  

利用多核分片并发拉取与填充：

  

```java

Map<Long, User> cache = new ConcurrentHashMap<>(1 << 20);

  

int shards = 8;

long maxId = userMapper.selectMaxId();

long step = maxId / shards + 1;

  

IntStream.range(0, shards).parallel().forEach(i -> {

long from = i * step, to = (i + 1) * step;

userMapper.selectByIdRange(from, to)

.forEach(u -> cache.put(u.getId(), u));

});

```

  

DB 端配合主键索引范围扫描，整体耗时基本等于 `单分片耗时 + 少量协调开销`。

  

**5. Hash 冲突的针对性处理**

  

如果监控发现冲突严重（可通过反射查看 `table` 中树化桶数量，或自行统计 `key.hashCode() & (cap-1)` 的分布）：

  

- 自定义包装 key，重写 `hashCode()` 打散高位：

```java

int h = Long.hashCode(id);

return h ^ (h >>> 16) ^ (h >>> 8); // 额外扰动

```

  

- 或干脆换 key 类型为 `String.valueOf(id)`（不推荐，徒增内存），更好的做法仍是上面的 `LongObjectHashMap`，其内部使用开放寻址 + 线性探测，对 long 的分布更友好。

  

**6. 是否真的需要全量加载到内存？**

  

值得反问的架构问题：

- 50w `User` 对象按每个 200 字节估算，约 100MB 堆内存，**热点访问比例可能不到 10%**；

- 可改用 **Caffeine 本地缓存 + 懒加载 + LRU 淘汰**，启动瞬时完成，按需加载：

  

```java

LoadingCache<Long, User> cache = Caffeine.newBuilder()

.maximumSize(100_000)

.expireAfterAccess(Duration.ofHours(1))

.build(userMapper::selectById);

```

  

- 如果必须全量预热，也可以 **异步预热**：服务先起来对外提供（cache miss 时回源 DB），后台线程并行填充，避免阻塞启动 12 秒。

  

---

  

#### 三、优化收益预估

  

| 优化项 | 预期耗时下降 |
|---|---|
| 预设初始容量（仅此一项） | 12s → 4\~5s |
| + 批量 SQL / 流式读取 | → 2\~3s |
| + 8 线程并行加载 | → 0.8\~1.5s |
| + 改用 `LongObjectHashMap` | 再省 20%-30% 内存与 GC |
| + 异步预热（架构层） | 用户感知启动耗时 ≈ 0 |

实践中**最先做的两件事是「预设容量」和「批量查询 + 并行」**，这两步通常就能把启动时间压到 2 秒以内，再考虑是否引入专用容器或架构调整。



4. 同事写了 `String s = "a" + i + "b" + j;` 在循环里拼接 10 万次出现 CPU 飙高，请解释 JDK 8 与 JDK 17 下编译器分别如何处理，以及你会怎么改写。

[[03-Java/循环内字符串拼接性能分析（JDK 8 vs JDK 17）|循环内字符串拼接性能分析（JDK 8 vs JDK 17）]]



5. 接口入参里有 `BigDecimal amount`，财务对账时出现 `0.1+0.2 != 0.3`，请说明 `BigDecimal` 的正确构造方式（字符串 vs double）以及 `setScale` 的舍入模式选择。


### 题5解析

#### **务必用 `new BigDecimal(String)` 构造，财务场景统一使用 `RoundingMode.HALF_EVEN`（银行家舍入）做 `setScale`。**

  

#### **为什么 `0.1 + 0.2 != 0.3`**

  

`double` 是 IEEE 754 二进制浮点数，`0.1`、`0.2`、`0.3` 都无法被精确表示。`0.1` 实际存储约为 `0.1000000000000000055511151231257827021181583404541015625`。把这种"已经有误差"的 `double` 喂给 `BigDecimal`，误差会被原样保留下来——这正是对账出现尾差的根因。

  

#### **`BigDecimal` 的正确构造方式**

  

```java

// ❌ 错误：double 入参会把二进制误差带进来

new BigDecimal(0.1);

// => 0.1000000000000000055511151231257827021181583404541015625

  

// ✅ 正确：字符串构造，所见即所得

new BigDecimal("0.1");

// => 0.1

  

// ✅ 也可以：BigDecimal.valueOf(double) 内部走 Double.toString,等价于字符串

BigDecimal.valueOf(0.1); // => 0.1

```

  

**对接口入参 `BigDecimal amount` 的几点纪律**：

  

- **入口层用 `String` 接收金额，再转 `BigDecimal`**。如果上游用 JSON，Jackson 默认会用 `double` 中转,需开启 `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS`，或者干脆把 DTO 字段定义成 `String`，进服务层再 `new BigDecimal(str)`。

- **永远不要 `new BigDecimal(double)`**,Sonar/Checkstyle 应作为强制规则禁用。

- **比较金额用 `compareTo` 而不是 `equals`**,`equals` 会把 `1.0` 和 `1.00` 视为不等（scale 不同）。

- **除法必须显式给 scale 和 RoundingMode**,否则除不尽时抛 `ArithmeticException`。

  

#### **`setScale` 舍入模式选择**

  

| 模式 | 行为 | 适用场景 |

|---|---|---|

| `HALF_UP` | 四舍五入（0.5 进位） | 直觉常见，但大批量数据会系统性偏高 |

| `HALF_DOWN` | 五舍六入（0.5 舍去） | 系统性偏低，几乎不用 |

| `HALF_EVEN` | 银行家舍入：0.5 时向偶数靠 | **财务对账首选**，长期统计期望无偏 |

| `DOWN` / `FLOOR` | 向零/向下截断 | 计费向用户少收（让利）、利息计提 |

| `UP` / `CEILING` | 向远离零/向上进位 | 平台手续费向自己多收 |

| `UNNECESSARY` | 断言无需舍入，否则抛异常 | 校验中间结果是否已对齐 scale |

  

**为什么财务推荐 `HALF_EVEN`**：在 `HALF_UP` 下，`0.5、1.5、2.5、3.5` 全部进位，累加后系统性高估；`HALF_EVEN` 让 `0.5→0`、`1.5→2`、`2.5→2`、`3.5→4`,统计期望为零偏差。这是 IEEE 754 默认舍入模式，也是中国《GB/T 8170》推荐的"四舍六入五成双"。

  

```java

BigDecimal a = new BigDecimal("0.1");

BigDecimal b = new BigDecimal("0.2");

BigDecimal sum = a.add(b).setScale(2, RoundingMode.HALF_EVEN);

// => 0.30,且 sum.compareTo(new BigDecimal("0.3")) == 0

```

  

#### **一份可直接落地的最小规约**

  

```java

public final class Money {

public static final int SCALE = 2; // 元,分两位

public static final RoundingMode RM = RoundingMode.HALF_EVEN;

  

public static BigDecimal of(String v) {

return new BigDecimal(v).setScale(SCALE, RM);

}

public static BigDecimal add(BigDecimal a, BigDecimal b) {

return a.add(b).setScale(SCALE, RM);

}

public static BigDecimal divide(BigDecimal a, BigDecimal b) {

return a.divide(b, SCALE, RM); // 必须给 scale

}

public static boolean eq(BigDecimal a, BigDecimal b) {

return a.compareTo(b) == 0; // 不要用 equals

}

}

```

  

补一句额外的工程建议：如果业务涉及汇率、利率这种需要更高精度的中间计算，可以把中间态 scale 放宽到 6\~8 位，只在**落库**和**对外输出**时再 `setScale(2, HALF_EVEN)`,这样能避免中间多次舍入造成的累积误差。



6. 给定一个 `List<Order>`，需要按 `userId` 分组、组内按 `createTime` 倒序、再取每组前 3 条。请用 Stream API 实现，并讨论该写法在百万级数据下的性能问题。

[[题6题解]]

---

## 二、并发与多线程（7-14）

7. 一个秒杀接口使用 `synchronized` 锁住整个方法导致 QPS 只有 200。请提出至少三种优化方案（分段锁、`ReentrantLock` + `tryLock`、Redis 分布式锁、本地库存预扣）并比较。

[[题7题解]]

8. 线上发现 `ThreadPoolExecutor` 的任务被悄悄丢弃，排查发现使用了默认 `AbortPolicy` 但被业务 catch 吞掉。请讲清楚四种拒绝策略的适用场景，以及你会如何设计一个"既不丢任务又不压垮系统"的方案。

**四种拒绝策略**

**AbortPolicy（默认）**，直接抛出 `RejectedExecutionException`。适合调用方能感知异常、需要快速失败的场景。但我们这次线上问题就是它的坑——业务代码有个大 catch 把异常吞掉了，任务静默消失，没有任何日志，很难排查。

**CallerRunsPolicy**，让提交任务的线程自己去执行这个任务。好处是绝对不丢任务，同时天然产生反压，调用方变慢了，自然就减缓了任务提交速度。缺点是如果调用方是 Tomcat 的 IO 线程，阻塞住了会导致大量请求超时，比丢任务还严重。

**DiscardPolicy**，静默丢弃新提交的任务，什么都不做。适合允许丢失的场景，比如埋点上报、日志采样。问题是完全没有任何反压信号，系统过载了你都不知道。

**DiscardOldestPolicy**，丢掉队列里等待最久的那个任务，然后把新任务放进去。适合只关心最新数据的场景，比如实时行情、传感器采样。但要想清楚业务语义——你丢的是已经等了最久的任务，用户可能早就在等结果了。

---

**既不丢任务又不压垮系统的方案**

核心思路是**分级降级加全程可观测**，分三级。

**第一级**，主池队列用有界队列，队列满触发自定义拒绝处理器，先尝试 offer 到一个二级缓冲队列，非阻塞，成功就返回。同时有一个后台 drainer 线程定期把二级缓冲里的任务重新喂回主池，每次限量，避免瞬间打爆。

**第二级**，二级缓冲也满了，就带 200 毫秒超时重新 offer 主队列，给主池一个喘息机会。

**第三级**，还是进不去，才走 CallerRuns 兜底，保证任务绝对不丢。同时在这一步触发告警，因为走到这里说明系统已经在过载边缘了。

**为什么不一开始就用 CallerRuns？** 因为二级缓冲把反压推迟到真正无路可退才触发，避免过早阻塞调用方线程。告警阈值应该设在二级缓冲积压超过某个水位就报警，不要等到第三级才发现。

二级缓冲用的是 `LinkedBlockingQueue.offer(task)`，不带超时参数，队列没满就直接入队返回 true，满了立刻返回 false，不会阻塞调用线程。所以拒绝处理器能快速判断——成功了就直接返回，任务交给 drainer 后续处理；失败了才继续走第二级降级。

这里用 offer 而不是 put，就是为了保证拒绝处理器本身不会卡住，否则拒绝处理器阻塞，主池线程也跟着阻塞，反而加剧过载。


告警不应该等到第三级 CallerRuns 才触发，那时候已经在压垮系统的边缘了。正确的做法是在二级缓冲积压超过水位线的时候就提前告警，比如二级缓冲用了 80% 就报警，让运维有时间介入。第三级 CallerRuns 是最后的兜底保障，保证任务不丢，但走到这一步说明告警早就应该响了。


9. 使用 `CompletableFuture` 并行调用三个下游服务（商品、库存、营销），任意一个超时整体接口要降级返回。请写出核心代码骨架，并说明 `allOf`、`anyOf`、`orTimeout` 的用法。


[[CompletableFuture]]


10. `ThreadLocal` 在使用了线程池的 Web 容器中导致用户信息串号，请分析根因，并对比 `InheritableThreadLocal`、`TransmittableThreadLocal`（阿里 TTL）的差异。

**线程池复用线程导致 ThreadLocal 残留，必须在 finally 中 remove；InheritableThreadLocal 只在子线程创建时拷贝一次，对线程池无效；TTL 通过装饰 Runnable，在任务提交时快照、执行前 replay、执行后 restore，才真正解决了池化场景下的上下文透传。**

  

下面我按面试口述的节奏展开，分四块讲。

  

---

  

**第一块，先讲根因。**

  

ThreadLocal 的本质是每个 Thread 对象内部有一个 `ThreadLocalMap`，key 是 ThreadLocal 实例的弱引用，value 就是我们 set 进去的对象。也就是说，数据是挂在「线程」这个载体上的，不是挂在「请求」上的。

  

在传统的 one-thread-per-request 模型里没问题，请求结束线程就销毁了。但 Web 容器比如 Tomcat 用的是线程池，线程会被复用。典型的串号场景是这样的：

  

- 请求 A 进来，拦截器在 Thread-1 上 `userHolder.set(userA)`，业务跑完返回，但是**没有调用 remove**。

- 请求 B 进来，调度器又把它分配到了同一个 Thread-1，业务代码里如果某个分支没有重新 set（比如游客接口、异步回调、或者 set 在某个 if 里），那 `userHolder.get()` 拿到的就是上一个请求 A 残留的 userA。

  

所以根因一句话：**ThreadLocal 的生命周期绑定的是线程，而线程池里线程的生命周期远长于一次请求**，残留数据没清理就被下一个请求读到了。

  

修复办法也很直接：在过滤器或拦截器的 `finally` 块里强制 `remove()`，Spring 的 `RequestContextHolder`、MDC 都是这么做的。

  

---

  

**第二块，InheritableThreadLocal。**

  

它解决的是另一个问题：**父线程能不能把上下文传给子线程**。

  

实现机制是在 `new Thread()` 的构造函数里，会去检查父线程的 `inheritableThreadLocals`，如果不为空就浅拷贝一份给子线程。注意关键词——**线程创建的那一刻拷贝**。

  

所以它的局限性也很明显：在线程池场景下完全失效。因为线程池里的线程是**复用的**，不是每次任务都 new 一个新线程。线程是在池子初始化时创建的，那一刻拷贝的是初始化线程（通常是 main 或者容器启动线程）的上下文，跟你后面提交任务时的业务线程上下文毫无关系。

  

所以 InheritableThreadLocal 只适合「显式 new Thread」或者 ForkJoin 这种场景，不适合业务线程池。

  

---

  

**第三块，TransmittableThreadLocal（TTL）。**

  

阿里搞 TTL 就是为了补 InheritableThreadLocal 在池化场景下的缺口。核心思路是：**既然线程复用导致拷贝时机不对，那就把拷贝时机从"线程创建时"挪到"任务提交时"**。

  

它的工作流程（口述顺序很重要）：

  

1. **提交任务时 capture**：在主线程调用 `TtlRunnable.get(runnable)` 包装时，把当前线程所有 TTL 变量的值快照下来，挂在这个 Runnable 上。

2. **执行任务前 replay**：池里的工作线程开始跑这个 Runnable，先把快照里的值 set 到当前线程的 TTL 里，并备份工作线程原有的值。

3. **执行任务后 restore**：任务跑完，把备份的原值还原回去，避免污染线程池里的下一个任务。

  

这三步对应源码里的 `capture / replay / restore`。再配合 `TtlExecutors.getTtlExecutorService()` 或者 Java Agent 字节码增强，业务代码基本无侵入。

  

---

  

**第四块，三者对比（口述时我会简单带过）。**

  

| 维度 | ThreadLocal | InheritableThreadLocal | TransmittableThreadLocal |

|---|---|---|---|

| 跨线程传递 | 不支持 | 支持，仅 new Thread 时 | 支持，任务提交时 |

| 线程池场景 | 需手动 remove | **失效**（线程复用） | **正确工作** |

| 拷贝时机 | 无 | 线程创建 | 任务提交（capture） |

| 任务结束清理 | 需手动 | 不清理 | 自动 restore |

  

---

  

**最后收个尾**，面试官如果追问，可以补一句：即使用了 TTL，**ThreadLocal 本身在请求结束时 remove 的纪律依然不能丢**，否则不仅串号，还可能因为 ThreadLocalMap 的 Entry key 是弱引用、value 是强引用导致内存泄漏，这是另一个高频考点。



>InheritableThreadLocal


它是 JDK 在 `java.lang` 包里自带的类，直接继承自 `ThreadLocal`，重写了三个方法：`childValue`、`getMap`、`createMap`。

它要解决的核心问题是：**普通 ThreadLocal 在父子线程之间是隔离的**——主线程 set 的值，子线程 get 不到。而很多场景下我们希望子线程能继承父线程的上下文，比如 traceId、用户身份。

实现机制讲三个点：

第一，Thread 类里除了 `threadLocals`，还有一个独立的字段 `inheritableThreadLocals`，专门给它用。

第二，关键时机在 `new Thread()` 的构造函数里。它会检查**当前线程（也就是创建子线程的那个父线程）​**的 `inheritableThreadLocals` 是否为空，不为空就调用 `ThreadLocal.createInheritedMap()`，把父线程的 Map 浅拷贝一份给子线程。

第三，拷贝时会走 `childValue(parentValue)` 这个钩子方法，默认实现就是直接返回 parentValue，你可以重写它做深拷贝。

它的致命局限：**拷贝动作只发生在 `new Thread` 的那一瞬间**​。在线程池场景下，线程是池子初始化时就创建好的，那个时刻拷贝的是初始化线程（通常是 main 或容器启动线程）的上下文，后面你再往主线程 set 任何东西，池里的工作线程都感知不到。所以它**只适合显式 new Thread 的场景**，比如手写一个 `new Thread(runnable).start()`，对 `ExecutorService` 完全无效。


> TransmittableThreadLocal（TTL）​

阿里中间件团队开源的，GitHub 上叫 `alibaba/transmittable-thread-local`，简称 TTL。它继承自 `InheritableThreadLocal`，所以 new Thread 场景天然兼容；真正的价值是补上了**线程池场景**的窟窿。

核心思路一句话：**既然线程是复用的、拷贝时机不能再放在「线程创建时」，那就挪到「任务提交时」​**。

它的工作流程是经典的 `capture → replay → restore` 三步：

1. **capture（提交时）​**：主线程调用 `TtlRunnable.get(runnable)` 把任务包一层，包装的瞬间把当前线程所有 TTL 变量的值快照下来，挂在这个 Runnable 实例上。
2. **replay（执行前）​**：池里的工作线程拿到这个 TtlRunnable 开始跑，先把工作线程原有的 TTL 值备份起来，然后把快照里的值 set 到当前线程，这样业务代码 `get()` 拿到的就是提交时刻主线程的上下文。
3. **restore（执行后）​**：任务跑完，把刚才备份的原值还原回去，防止污染线程池里下一个任务。

落地方式有两种：一种是手动用 `TtlRunnable`、`TtlCallable` 包装任务，或者用 `TtlExecutors.getTtlExecutorService()` 包装线程池，业务代码自动透传；另一种是挂 Java Agent（`-javaagent:transmittable-thread-local-x.x.x.jar`），通过字节码增强让 JDK 的 `ThreadPoolExecutor`、`ForkJoinPool` 自动具备 TTL 能力，对业务零侵入。

典型使用场景：分布式链路追踪的 traceId、压测标记、租户 ID、灰度标签——这些数据需要从 Web 入口线程透传到业务线程池、异步任务、甚至 `CompletableFuture` 里的回调线程。


11. 一段双重检查锁定（DCL）单例代码没有加 `volatile`，在压测中偶现 NPE，请从 JMM 指令重排序角度解释，并给出三种线程安全单例实现。

不加volatile的DCL问题根源在于对象创建不是原子操作。`instance = new Singleton()`这一行在JMM层面会被拆解成三个步骤：

1. 分配内存空间
2. 在分配的内存上初始化对象（执行构造函数）
3. 将instance引用指向这块内存地址

在没有volatile约束的情况下，JIT编译器和CPU为了优化性能，可能对2和3进行指令重排序，变成1-3-2的执行顺序。也就是说引用已经指向了内存地址，但对象的构造函数还没真正执行完。

这时候如果线程A正好执行到重排序后的步骤3和2之间，instance已经不为null了；线程B这时进来做第一次null检查，发现instance不为null，直接返回这个引用——但实际上对象还没初始化完成，线程B拿到的是一个"半初始化"的对象。如果线程B紧接着调用这个对象的方法或访问字段，就可能触发NPE或者拿到字段的默认值（比如0、null），这就是压测中偶现NPE的根本原因。这本质上是一个可见性和有序性问题，加上volatile之后，它会通过插入内存屏障禁止这种重排序，保证步骤2一定先于步骤3完成。

三种线程安全的实现方式：

第一种是修复后的DCL，给instance加上volatile：

```java
public class Singleton {
    private static volatile Singleton instance;
    
    private Singleton() {}
    
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

第二种是静态内部类（懒加载占位符模式），利用类加载机制保证线程安全，且不需要synchronized带来的性能开销：

```java
public class Singleton {
    private Singleton() {}
    
    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }
    
    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

这种方式的原理是Java类加载是懒加载的，Holder类只有在第一次调用getInstance()时才会被加载，而类加载过程本身由JVM保证线程安全（类加载阶段会加锁），所以不需要任何额外的同步开销。

> 补充：说"Java类加载是懒加载的"没问题，但要注意粒度——**所有类都是按需加载的**，这不是静态内部类的专利。静态内部类的特殊之处在于它比外层类**多了一层延迟**：外层类可能因为其他原因被加载（比如有个`static`字段、反射等），但内部类`Holder`可以纹丝不动，直到`getInstance()`真正被调用时才加载。所以真正让Holder模式成立的，不是"类加载本身是懒的"，而是**内部类可以比外层类更晚加载**。

第三种是枚举单例，是《Effective Java》推荐的写法，天然防止反射攻击和反序列化破坏单例：

```java
public enum Singleton {
    INSTANCE;
    
    public void doSomething() {
        // 业务逻辑
    }
}
```

枚举的实例化由JVM保证只发生一次，而且枚举类的反序列化不会调用构造方法而是直接返回已有的枚举实例，所以它能防住DCL和静态内部类方式都防不住的反射、反序列化攻击。

面试时如果还有时间，可以补充一点：volatile在这里其实承担了两个作用，一是保证可见性（一个线程修改instance后，对其他线程立即可见），二是通过禁止指令重排序保证有序性，这也是面试官经常会追问"volatile到底解决了什么问题"的地方，单纯说"防止重排序"不够全面。

12. 业务方反馈接口偶尔卡 30 秒后恢复，jstack 显示大量线程 `BLOCKED` 在 `log4j` 上。请说明可能原因以及如何用 `Async Appender` 优化。

**现象分析**

接口偶尔卡30秒后自动恢复，jstack显示大量线程BLOCKED在log4j——这是一个非常典型的日志同步写入引发的"假死"问题，定位思路可以这样展开。

**一、为什么会BLOCKED在log4j**

默认情况下log4j（无论是log4j1还是log4j2的同步Appender模式）的日志输出是同步阻塞的，也就是业务线程调用`logger.info(...)`这行代码时，是当前线程亲自去执行"格式化日志内容 + 写磁盘/写网络"这个完整流程，写完才能返回，继续往下执行业务逻辑。

而日志的底层Appender往往不是单纯的本地磁盘写入，常见的几种慢场景包括：写入网络文件系统（NFS）、日志输出到远程ELK/Logstash（Socket Appender）、磁盘本身因为别的进程占用导致IO竞争、又或者是日志文件所在磁盘满了导致写入被卡住重试。一旦底层IO变慢或卡顿，所有调用了log.info/log.error的业务线程都会阻塞在这次写操作上。

更关键的是，log4j的Appender内部对日志写入操作通常加了锁（保证日志顺序和文件写入的原子性），这意味着不仅仅是"我这个线程的日志写得慢"，而是所有线程在抢这同一把锁时全部排队阻塞，哪怕日志内容很短，只要持锁线程卡住了，后面成百上千个线程全部BLOCKED，表现出来就是整个接口集体卡顿。30秒这个数字通常和某个超时重试机制吻合，比如网络存储抖动、连接池超时重连等，超时之后写入恢复，锁释放，所有线程瞬间被唤醒继续执行，于是表现为"卡30秒后恢复"。

简单说，根因是：业务线程的执行路径里混入了一次不可控的同步IO操作，而这个IO操作还被一把全局锁保护着，导致故障从"单点慢"放大成"全局假死"。

**二、用AsyncAppender优化的思路**

核心思路是把日志的"产生"和"落盘"这两件事解耦，业务线程只负责把日志事件丢进一个内存队列，立刻返回继续执行业务逻辑；真正的格式化、写磁盘/写网络这些慢操作交给一个独立的后台线程异步消费队列来做。这样业务线程的阻塞时间从"一次完整IO"降低到"一次入队操作"，即便后端日志存储抖动，最多是队列堆积，不会直接拖死业务线程。

以Log4j2为例（生产中现在更常用），有两种典型配置方式。

第一种是用基于LMAX Disruptor的全局异步Logger，性能最好，需要引入disruptor依赖，在log4j2.xml里配置：

```xml
<Configuration>
    <Appenders>
        <RollingFile name="FileAppender" fileName="logs/app.log" ...>
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger - %msg%n"/>
        </RollingFile>
    </Appenders>
    <Loggers>
        <AsyncRoot level="info">
            <AppenderRef ref="FileAppender"/>
        </AsyncRoot>
    </Loggers>
</Configuration>
```

并在启动参数里指定全局异步模式：

```
-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
```

这种方式下所有日志都走异步队列，吞吐量最高，但需要注意系统崩溃时队列里未落盘的日志可能丢失，需要权衡可靠性和性能。

第二种是局部使用AsyncAppender包装原有的同步Appender，改造成本更低，适合只想异步化部分日志的场景：

```xml
<Configuration>
    <Appenders>
        <RollingFile name="FileAppender" fileName="logs/app.log" .../>
        <Async name="AsyncFileAppender">
            <AppenderRef ref="FileAppender"/>
            <BufferSize>8192</BufferSize>
        </Async>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="AsyncFileAppender"/>
        </Root>
    </Loggers>
</Configuration>
```

**三、用AsyncAppender之后仍然要注意的点**

异步化解决了业务线程被同步IO拖死的问题，但引入了新的风险点，面试或者实际排查时最好一并提到。

队列是有界的，默认BufferSize通常是固定大小（比如AsyncAppender默认128或几千不等，视实现而定），如果日志产生速度长期大于消费速度（后端写入持续慢、磁盘持续IO瓶颈），队列会被打满。打满之后的默认策略有几种：阻塞（这种情况下又会退化回类似同步阻塞的问题，只是阈值变大了）、丢弃（DiscardingAsyncQueueFullPolicy，丢弃低优先级日志保留ERROR级别，可能丢失部分日志细节）。需要根据业务对日志完整性的容忍度去配置合适的策略和队列大小。

另外异步意味着应用崩溃（比如OOM、kill -9）时，队列里还没来得及写盘的日志会丢失，这点在排查"为什么日志缺失了一段"时很容易被遗漏，需要提前和业务方说明这个trade-off，或者结合监控队列堆积长度（很多APM工具可以监控AsyncAppender队列水位）来提前预警IO瓶颈，而不是被动等到队列打满才发现问题。

所以一个更完整的回答应该是：异步化解决了"阻塞传导到业务线程"这个直接症状，但根因（比如远程日志存储不稳定、磁盘IO瓶颈）仍然需要单独定位和解决，AsyncAppender更像是给业务线程加了一层缓冲垫，而不是从根本上修复了底层IO的性能问题。

13. `AQS` 是如何实现 `ReentrantLock` 的公平锁与非公平锁的？如果让你设计一个"读写都可重入、写优先"的锁，你会怎么基于 AQS 实现？

[[题13题解]]

[[ReentrantReadWriteLock 完整详解]]


14. 线上 CPU 100%，`top -Hp` 发现是 GC 线程，jstat 显示 Full GC 每分钟 5 次。请描述完整排障步骤（从 `top` 到 `jmap` 到 MAT 分析 dump）。

[[Full GC频繁导致CPU 100%的完整排障流程]]

---

## 三、JVM 与性能调优（15-19）

15. 一个 4C8G 的容器跑 Spring Boot 应用，频繁 OOMKilled 但堆内存只用了 2G。请列出堆外内存可能的泄漏点（DirectByteBuffer、Netty、JNI、Metaspace、线程栈）以及排查工具。

[[题15题解]]

16. 服务启动 5 分钟内响应慢，之后恢复正常。请从 JIT 编译（C1/C2）、类加载、连接池预热三方面解释，并给出预热方案。

[[题16题解]]

17. 选择 G1 还是 ZGC？请基于"堆 32G、要求 STW < 50ms、对吞吐敏感"的场景做出选择并说明参数调优要点（`MaxGCPauseMillis`、`G1HeapRegionSize` 等）。

[[G1]]
[[ZGC]]
[[题17题解]]



18. 线上 dump 文件 8G 拉不到本地，请说明如何在服务器上用 `jmap -histo:live`、`jcmd GC.class_histogram`、`arthas` 做轻量分析。

[[题18题解]]



19. `String.intern()` 在 JDK 7+ 放在堆里，业务中大量调用导致 YGC 飙升。请解释字符串常量池机制，并给出替代方案。

[[题19题解]]

---

## 四、Spring / Spring Boot（20-26）

20. `@Transactional` 标注的方法内部调用另一个 `@Transactional(propagation=REQUIRES_NEW)` 方法，发现事务没生效。请解释 Spring AOP 自调用失效的原理，并给出三种解决办法。

[[题20题解]]

21. 一个 `@Async` 方法抛出的异常上游捕获不到，且和主线程 ThreadLocal 不共享。请说明 `@Async` 的实现原理与正确的异常处理方式。

[[题21题解]]

22. Spring Boot 启动时报 `BeanCurrentlyInCreationException`，请分析循环依赖的三级缓存机制，以及为什么构造器注入解决不了循环依赖。

[[题22题解]]

23. 你需要在不停机的前提下，动态调整某个限流阈值。请基于 `@ConfigurationProperties` + `@RefreshScope` + Nacos 设计方案，并说明刷新时机与坑点。

[[题23题解]]

24. Spring Cloud Gateway 中如何实现"按用户ID限流 + 按接口限流 + 黑名单"的组合过滤器？请给出 `GlobalFilter` 的执行顺序设计。

[[题24题解]]

25. 使用 MyBatis-Plus 时，分页查询 `selectPage` 返回的 total 和 records 不一致，请分析 `Count SQL` 自动生成的坑（join、group by、distinct）。

[[题25题解]]


26. 接口需要根据请求 header 动态切换数据源（多租户），请基于 `AbstractRoutingDataSource` + `ThreadLocal` 给出实现，并讨论事务跨数据源问题。

[[题26题解]]

---

## 五、MySQL 与数据持久化（27-33）

27. 一张订单表 8000 万行，`SELECT * FROM orders WHERE user_id=? ORDER BY create_time DESC LIMIT 10` 慢到 3 秒。请说明索引设计（联合索引顺序、覆盖索引、ICP）。

[[题27题解]]


28. 业务中出现"幻读"，使用的是 MySQL InnoDB RR 隔离级别。请解释 next-key lock 的工作机制，以及什么场景下 RR 仍可能出现幻读。
[[题28题解]]

29. 一次大促前需要给一张 5 亿行的表加索引，DBA 不允许停机。请说明 Online DDL 的原理、`pt-osc`/`gh-ost` 的选择以及风险点。

[[题29题解]]

30. 接口偶发 `Deadlock found when trying to get lock`，日志显示两个事务以相反顺序更新同一批订单。请讲清楚 MySQL 死锁检测机制和应用层重试策略。
[[题30题解]]

31. 分库分表后，需要做"按用户ID分库 + 按订单ID查询"的需求。请设计基因法（uid 末 4 位融入 orderId）的方案。
[[题31题解]]

32. 主从延迟 30 秒导致用户支付后看不到订单。请给出三种解决思路（强制走主库、半同步、本地缓存）以及适用边界。

[[题32题解]]

33. 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 高并发下出现自增 ID 跳号严重。请解释 `innodb_autoinc_lock_mode` 的三种取值。
[[题33题解]]

---

## 六、Redis 与缓存（34-39）

34. 缓存击穿导致数据库瞬间被打挂。请分别说明缓存穿透、击穿、雪崩的区别，并给出布隆过滤器、互斥锁、逻辑过期三种方案的代码思路。

35. Redis 分布式锁使用 `SETNX + EXPIRE` 两条命令，业务方反馈偶发锁丢失。请说明 Redisson 的看门狗机制以及 RedLock 的争议。
[[题35题解]]

36. 大 Key（一个 Hash 1000 万字段）导致 Redis 慢查询，请说明排查工具（`--bigkeys`、`MEMORY USAGE`）与拆分方案。
[[题36题解]]

37. 缓存与数据库双写不一致问题。请对比"先删缓存再写库"、"先写库再删缓存"、"延迟双删"、"订阅 binlog"四种方案。
[[题37题解]]

38. Redis 集群下 `mget` 一批 key 报 `CROSSSLOT` 错误，请说明 hash tag 的用法以及批量操作优化。

[[题38题解]]

39. 业务需要实现"30 分钟未支付订单自动关闭"，对比 Redis 过期监听、ZSet 轮询、RocketMQ 延迟消息、时间轮四种方案。

---

## 七、消息队列与异步（40-43）

40. 使用 Kafka 时出现消息重复消费。请说明生产端幂等（`enable.idempotence`）、消费端幂等（业务唯一键 + Redis 去重）的完整方案。

41. RocketMQ 事务消息如何保证"本地事务 + 消息发送"原子性？请画出 half-message 的两阶段流程。

42. 消费者堆积 100 万条消息，请说明扩容消费者、提升消费并发度、临时落库后慢慢消费三种应急方案的取舍。

43. 顺序消息（如同一订单的状态流转）在 Kafka/RocketMQ 中如何保证？多个消费者并发下如何不破坏顺序？

---

## 八、分布式与微服务（44-47）

44. 一个跨 3 个服务的下单流程（订单、库存、积分），如何保证最终一致性？请对比 2PC、TCC、Saga、本地消息表四种方案。

45. 服务 A 调用 B，B 调用 C，C 偶发超时导致 A 的线程被全部阻塞。请说明 Hystrix/Sentinel 的舱壁隔离、熔断、降级机制。

46. 分布式 ID 生成：雪花算法在容器化部署中遇到时钟回拨与 workerId 冲突，请给出基于 Zookeeper/Redis 的 workerId 分配方案。

47. 全链路灰度发布：如何基于 Spring Cloud + Nacos 实现"指定用户走灰度版本，其余走稳定版本"？请说明请求标签透传与 Ribbon 自定义负载均衡。

---

## 九、系统设计与线上排障（48-50）

48. 设计一个"短链服务"，要求日均 1 亿次访问、P99 < 50ms、永不重复。请覆盖发号器、存储选型（MySQL vs HBase）、缓存策略与防刷。

49. 设计一个"朋友圈 Feed 流"，请对比推模式、拉模式、推拉结合的优缺点，并讨论大 V（千万粉丝）场景下的特殊处理。

50. 凌晨 3 点报警：某接口 P99 从 100ms 飙升到 5s，错误率 30%。请描述你的排障 SOP（监控看板 → 链路追踪 → 日志 → JVM/DB/Redis → 回滚），并说明每一步用什么工具。

---

## 答题建议

- **先讲业务背景**：复述场景，明确边界与约束（QPS、数据量、一致性要求）。
- **再给方案选型**：至少给出 2-3 种方案并对比，体现技术广度。
- **后讲落地细节**：核心代码、参数配置、监控指标。
- **最后谈风险**：极端场景、容量评估、回滚预案。
