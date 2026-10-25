---
tags:
  - java
created: 2026-06-28
updated: 2026-06-28
---
# ConcurrentHashMap：1.7 vs 1.8 的本质差异

---

## 一、1.7：分段锁（Segment + HashEntry）

### 数据结构

```
ConcurrentHashMap (1.7)
│
├── Segment[0]  (继承 ReentrantLock)
│   └── HashEntry[] table
│       ├── HashEntry -> HashEntry -> ...   (链表)
│       └── HashEntry -> HashEntry -> ...
│
├── Segment[1]
│   └── HashEntry[] table
│       └── ...
│
└── Segment[15]  (默认 16 个 Segment)
    └── ...
```

### 核心设计

- **Segment** 继承自 `ReentrantLock`，每个 Segment 本质上是一把独立的锁
- 默认 **16 个 Segment**，并发度（concurrencyLevel）= Segment 数量
- 每个 Segment 内部维护一个 `HashEntry[]`，哈希冲突用**链表**解决
- 操作时：先定位 Segment（高位 hash），再定位桶（低位 hash）

### put 流程（1.7）

```java
// 伪代码
int segmentIndex = (hash >>> segmentShift) & segmentMask;
Segment<K,V> s = segments[segmentIndex];

s.lock();  // 对整个 Segment 加锁
try {
    // 在 Segment 内部的 HashEntry[] 中操作
    int index = hash & (s.table.length - 1);
    // ... 链表插入
} finally {
    s.unlock();
}
```

### 1.7 的问题

| 问题 | 说明 | 展开 |
|------|------|------|
| **并发度固定** | 初始化后 Segment 数量不变，高并发时仍然存在锁竞争 | — |
| **内存浪费** | 每个 Segment 有独立的 count、modCount、threshold 等字段，空 Segment 也占内存 | — |
| **size() 开销大** | 遍历所有 Segment 求和 + 失败全锁重试，弱一致性实现复杂 | 见下文 |
| **链表查询 O(n)** | 大量哈希冲突时退化严重，无树化优化 | — |
| **扩容粒度粗** | 每个 Segment 独立扩容，无法整体协调 | 见下文 |

---

#### Segment 内部字段一览（理解 size() 源码的前置知识）

在展开 size() 实现之前,先看 1.7 中 Segment 内部都维护了哪些字段 —— 后续 size() 源码里反复出现的 `count`、`modCount`、`threshold` 全都来自这里。

```java
static final class Segment<K,V> extends ReentrantLock implements Serializable {

    // 1. 当前 Segment 内部的真实桶数组(HashEntry 链表)
    transient volatile HashEntry<K,V>[] table;

    // 2. 该 Segment 中的元素个数(本节重点)
    transient volatile int count;

    // 3. 修改次数,任何结构性变更(put / remove / clear)都会 +1
    //    注意:modCount 不是 volatile!
    transient int modCount;

    // 4. 扩容阈值,当 count 超过 threshold 时触发本 Segment 内部扩容
    transient int threshold;

    // 5. 负载因子,默认 0.75,扩容时 threshold = (int)(capacity * loadFactor)
    final float loadFactor;
}
```

**逐字段拆解：**

| 字段 | 类型 | 作用 | 关键细节 |
|------|------|------|----------|
| `table` | `volatile HashEntry[]` | 当前 Segment 内部真正的桶数组 | `volatile` 保证扩容后其他线程能立即看到新数组 |
| `count` | `volatile int` | **本 Segment 中 KV 对的总数** | size() 累加的就是它;`int` 而非 `long`,32 位原子读,不会撕裂 |
| `modCount` | `int`(非 volatile) | **结构性变更计数器** | put / remove / clear 都会 `++modCount`;size() 靠它判断"两次读之间有没有人写过" |
| `threshold` | `int` | 本 Segment 扩容阈值 | 超过则把本 Segment 的 `table` 数组长度 ×2 |
| `loadFactor` | `float` | 负载因子,默认 0.75 | 初始化时算 `threshold = capacity * loadFactor` |

**为什么说"内存浪费"？**

- 默认 **16 个 Segment**,每个 Segment 不管用没用到,**这 5 个字段全部存在**(16 × (引用 + int + int + int + float) ≈ 几百字节)
- 即使业务上 ConcurrentHashMap 实际只放了几百个元素、远没撑满,**16 个 Segment 的元数据开销照付不误**
- 对比 1.8:`size = baseCount + CounterCell[]` 是**全局共享**的,不随桶数线性膨胀;只有非空桶才占内存

**与 size() 强相关的两个字段（先记住,源码里反复出现）：**

- **`count`**:要累加的目标值 → "1.7 的 size = sum(所有 Segment.count)"
- **`modCount`**:要校验的版本号 → "如果这次读到的 modCount 跟上次不同,说明中间被写过,sum 不可信"

理解这两点之后,再去看 size() 的乐观读 + 重试逻辑就顺了。

---

#### 1.7 size() 的实现细节 —— 为什么"开销大、实现复杂"

##### 1. size() 源码（1.7）

```java
public int size() {
    // 1. 先尝试无锁统计
    long sum = 0L;
    long[] mc = new long[segments.length]; // 暂存各 Segment 的 modCount

    // 2. 先尝试乐观统计:不锁,只读各 Segment 的 count
    for (int i = 0; i < segments.length; ++i) {
        sum += segments[i].count;  // count 是 volatile int,可见性 OK,但无锁读会拿到各 Segment 的不一致快照
        mc[i] = segments[i].modCount;  // modCount 非 volatile,读不读得到"最新"都无所谓,只用作版本号
    }

    // 3. 校验:如果期间没有任何 Segment 被修改(modCount 未变),则 sum 准确
    // 4. 否则判定失败,加锁重试
    if (segments.length > 1) {
        for (int i = 0; i < segments.length; ++i) {
            if (mc[i] != segments[i].modCount) {
                retry:  // 锁全部 Segment,精确统计
                    for (;;) {
                        long[] s = new long[segments.length];
                        for (int j = 0; j < segments.length; ++j)
                            s[j] = segments[i].count;
                        long checkSum = 0;
                        for (int j = 0; j < segments.length; ++j)
                            checkSum += s[j];
                        if (checkSum == sum) break retry; // 校验通过
                        sum = checkSum;
                    }
                return (int) sum;
            }
        }
    }
    return (int) sum;
}
```

##### 2. 三个核心问题

**(1) 无锁读拿到的是各 Segment 之间不一致的快照(不是"读到旧值")**

```java
// Segment 内部
transient volatile int count;   // ← volatile 保证可见性,32 位 int 读也原子,单次读一定是"最新的"
transient int modCount;         // ← modCount 非 volatile
```

- `volatile int count`:可见性 OK,单次读**不会读到撕裂值、也不会读到陈旧值**
- 真正的问题:**各 Segment 的 count 没有同步关系**。线程 A 累加 sum 的过程中,各 Segment 的 count 是各自时刻的快照,合起来不代表"同一瞬间的全局大小"

**典型不一致累加场景:**

- t0:Segment[0].count = 10,Segment[1].count = 20,Segment[2].count = 30 → 真实总和 = 60
- t1:线程 A 开始读,先读到 Segment[0].count = 10
- t2:线程 B 在 Segment[1] 里 put 一个元素,Segment[1].count 变成 21
- t3:线程 A 继续读,读到 Segment[1].count = 21(新鲜,但已经"晚了一拍"),Segment[2].count = 30
- t4:线程 A 累加得到 sum = 10 + 21 + 30 = 61,比真实值多 1

不是读到"旧值",而是**各 Segment 的时间点不对齐** —— 这才是必须再用 modCount 校验的根本原因。

**(2) 弱一致性靠 modCount + 重试保证**

```java
mc[i] = segments[i].modCount;   // 第一轮:记录 modCount
// ... 之后比较
if (mc[i] != segments[i].modCount) {  // modCount 变了 → 重试
    // 加锁全部 Segment,再来一次
}
```

逻辑链条:
1. **乐观读** → 拿到一个 sum 和各 Segment 的 modCount 快照
2. **校验** → 再读一次 modCount,如果**全部未变**说明这两次读之间没有写,sum 准
3. **重试** → 只要有一个 Segment 的 modCount 变了,说明刚才那次乐观读"穿越"了一次写,必须**加锁全部 Segment** 重新统计
4. **加锁重试** → 把 16 个 Segment 全 lock,逐个累加 count

**高并发场景下这个乐观读几乎必定失败**,退化为"全锁遍历",而这 16 把锁一旦被 size() 短暂持有,**所有写线程都会卡住**。

**(3) size() 持锁期间 = 全 Map 写阻塞**

```java
// 加锁所有 Segment,按 i 顺序依次 lock
for (int j = 0; j < segments.length; ++j) {
    lock(segments[j]);
}
```

虽然每把锁的粒度只是单个 Segment,但**任何一个写操作都需要先 lock 自己那把 Segment 锁**。而 size() 加锁重试时,会按顺序把 16 把 Segment 锁**一把不漏地全 lock 住**,整个持锁期间**所有 Segment 上的写操作都会阻塞**——这正是著名的"size() 是性能的毒药"的由来。

##### 3. 面试话术

> "1.7 的 size() 走的是乐观读 + 失败重试的策略:先无锁累加各 Segment 的 count,再用 modCount 校验这两次读之间有没有写发生;一旦校验失败,就**把 16 个 Segment 全 lock 重新统计**。高并发下乐观读几乎一定失败,等于每次调用 size() 都会引发一轮全锁遍历,写线程会被卡住,这就是 1.7 size() 开销大的根本原因。1.8 改用 `baseCount + CounterCell[]` 的 LongAdder 思路,size() 退化为一次 `baseCount + sum(cells)` 的 O(n_cells) 求和,根本不需要锁。"

##### 4. 一句话记忆

> 1.7 的 size() = "乐观读 + modCount 校验 + 失败全锁重试" → 高并发退化为全锁遍历;1.8 的 size() = "LongAdder 分段计数 + 无锁求和" → 真正的 O(cells) 轻量级。

---

#### 1.7 扩容 —— 为什么说"粒度粗,无法整体协调"

##### 1. 扩容的触发逻辑

1.7 的扩容是**按 Segment 粒度**独立进行的:

```java
// 触发条件:put 时,发现本 Segment 的 count 超过 threshold
if (++count > threshold) {
    rehash();  // 只扩容"当前这一个"Segment
}
```

注意:扩容判断的是**本 Segment 自己的 count 和 threshold**,不关心其他 Segment 装了多少。

##### 2. 扩容时发生了什么

```java
private void rehash() {
    // 1. 把本 Segment 的 table 长度 ×2(例如 4 → 8)
    HashEntry<K,V>[] newTable = new HashEntry[oldCapacity * 2];

    // 2. 遍历本 Segment 内的链表,按新掩码重新算桶下标,搬到 newTable
    for (HashEntry<K,V> e : oldTable) {
        // ... 链表拆分:一个桶的链表按 hash & newMask 拆成"低位链"和"高位链"
    }

    // 3. 替换 table 引用
    this.table = newTable;
}
```

**关键点:整个 rehash 是在 Segment 自己的锁里完成的,只动本 Segment 的 table 数组,完全不影响其他 15 个 Segment。**

##### 3. 三个层面的"粒度粗"

| 层面 | 1.7 表现 | 问题 |
|------|---------|------|
| **判断粒度** | 只看**本 Segment** 的 `count > threshold` | 全局数据分布不均时,有的 Segment 早就该扩,有的还空着,扩容是"被动响应"而非"全局规划" |
| **执行粒度** | 一次只扩**一个** Segment | 热点 Segment 扩容期间,该 Segment 上所有写阻塞;其他 Segment 正常服务 |
| **协调粒度** | 各 Segment **互不知情** | 同一个 key 散布在 16 个 Segment 里,无法做"整体 rehash";扩容后元素的 Segment 归属也不会变 |

##### 4. 具体场景:数据倾斜时的问题

```
初始:16 个 Segment,每个内部 table 长度 = 4(全局 64 桶)

t1:所有 put 都 hash 到 Segment[3]    → Segment[3].count = 20,远远超 threshold = 3
t2:Segment[3] 触发 rehash,table 从 4 → 8(只动了 1/16)
t3:继续往 Segment[3] 灌数据        → 又超 threshold,再 8 → 16
t4:与此同时,Segment[0..2, 4..15] 一直空着,table 长度还是 4
```

**结果**:
- 热点 Segment 被连续扩容多次(4 → 8 → 16 → 32...),每次扩容期间该 Segment **写阻塞**
- 冷 Segment 永远不扩容,空间浪费
- **无法把热点数据从热 Segment 转移到冷 Segment**——因为没有"跨 Segment 协调"机制

##### 5. 跟 1.8 的对比(为什么 1.8 不存在这个问题)

```
1.7:扩容粒度 = 一个 Segment(只覆盖 1/16 的数据)
1.8:扩容粒度 = 整个 table(覆盖 100% 的数据)
     触发后所有桶一起搬到 nextTable,空间统一翻倍
     + 多线程协助迁移(其他写线程遇到 ForwardingNode 就来帮忙搬桶)
```

1.8 的扩容是**整表级别**的:
- 一次扩容 = 一次 `table.length × 2`,所有桶统一搬,没有"局部 vs 全局"的不一致
- 多个写线程可以**瓜分桶区间**并行迁移,解决"单线程搬一整张表"太慢的问题
- 不存在"数据倾斜但局部反复扩容"的场景——table 是统一的,空间利用率整体最优

##### 6. 一句话记忆

> 1.7 的扩容 = **"谁满扩谁、各自为政"**(粒度 = Segment,16 个独立王国);1.8 的扩容 = **"整表翻倍、多线程协助"**(粒度 = table,统一指挥)。

---

## 二、1.8：CAS + synchronized（Node + 红黑树）

### 数据结构

```
ConcurrentHashMap (1.8)
│
└── Node[] table  (单层数组，volatile)
    ├── Node        -> Node -> Node ...    (链表，长度 < 8)
    ├── TreeBin     (红黑树根节点包装)
    │   └── TreeNode -> TreeNode ...      (链表转树，长度 >= 8)
    └── ForwardingNode  (扩容时占位标记)
```

**特殊节点类型：**

| 类型 | hash 值 | 用途 |
|------|---------|------|
| `Node` | ≥ 0 | 普通链表节点 |
| `TreeBin` | -2 | 红黑树根节点的包装 |
| `ForwardingNode` | -1 | 扩容时标记已迁移的桶 |
| `ReservationNode` | -3 | `computeIfAbsent` 占位 |

### put 流程（1.8）

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    for (Node<K,V>[] tab = table;;) {

        // 1. 表未初始化 → initTable()（CAS 保证只有一个线程初始化）
        if (tab == null || tab.length == 0)
            tab = initTable();

        // 2. 目标桶为空 → CAS 直接插入，无锁！
        else if ((f = tabAt(tab, i)) == null) {
            if (casTabAt(tab, i, null, new Node<>(hash, key, value)))
                break;
        }

        // 3. 遇到 ForwardingNode → 当前线程协助扩容
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);

        // 4. 桶非空 → 对桶头节点 synchronized
        else {
            synchronized (f) {
                if (fh >= 0) {
                    // 链表插入/更新
                } else if (f instanceof TreeBin) {
                    // 红黑树插入/更新
                }
            }
            // 链表长度 >= 8 → treeifyBin()
        }
    }
    addCount(1L, binCount); // 更新 size（LongAdder 思想）
    return null;
}
```

### 关键机制详解

#### 2.1 initTable —— CAS 保证单线程初始化

**`sizeCtl` 是一个身兼数职的字段,语义随阶段变化:**

| 阶段 | `sizeCtl` 值 | 含义 |
|------|-------------|------|
| 构造时(刚 new 完) | **0**(默认) | 还没人初始化 |
| 构造时(传入 `initialCapacity`) | 该容量值 | 用作初始化时的目标容量 |
| 某个线程正在初始化中 | **-1** | 已被 CAS 抢占为"唯一初始化线程"的标记 |
| 初始化完成(正常运行) | `capacity × 0.75` | 扩容阈值 |
| 正在扩容中 | **-(1 + n)**,n 是已并入扩容的线程数 | 高位 16 位是扩容戳,低 16 位是参与线程数 |

```java
private final Node<K,V>[] initTable() {
    while ((tab = table) == null || tab.length == 0) {
        if ((sc = sizeCtl) < 0)
            Thread.yield(); // 其他线程正在初始化，自旋等待
        else if (U.compareAndSetInt(this, SIZECTL, sc, -1)) {
            // CAS 成功，本线程负责初始化
            try {
                table = new Node[capacity];
                sizeCtl = capacity * 0.75; // 设置扩容阈值
            } finally { ... }
        }
    }
}
```

**"怎么判断其他线程正在初始化?"的完整解读:**

1. **判断条件**:`sizeCtl < 0` 即视为"有人在搞事"
   - 初始化阶段:`sizeCtl == -1` ← 唯一对应"正在初始化"
   - 扩容阶段:`sizeCtl` 是 `-1 ~ -65535` 之间的某个负数(由 `(resizeStamp << 16) + 2` 计算得出)
   - 这里的 `< 0` 实际上**同时涵盖了"初始化中"和"扩容中"两种情况**

2. **为什么不用 `== -1` 精确判断?**
   - 简化逻辑:不管是初始化还是扩容,都先让出 CPU 再说
   - `Thread.yield()` 只是"谦让一下",下一次循环还会再读 `table` 看是否初始化完,完事就退出
   - 比起用 `synchronized` 排队,这种**自旋 + yield** 的开销小得多

3. **抢占机制**:看 if 链的**第二个分支**:
   - 第一个 else if:本线程尝试 `CAS(sizeCtl, 0, -1)`
   - **CAS 成功的线程** = 唯一被选中的"初始化线程"
   - 之后进入 try 块真正 `new Node[capacity]`,最后把 `sizeCtl` 改回 `capacity × 0.75`(转为扩容阈值语义)

4. **整段 while 循环的安全保证**(对照三种情况):
   - 本线程抢到 CAS → 干活,干完 `sizeCtl` 变正,跳出循环
   - 别的线程抢到 CAS → 本线程看到 `sizeCtl < 0` → `Thread.yield()` → **下一轮 while 重新检查 `table == null`**,发现 table 已被赋值,直接跳出
   - 极端竞争:CAS 失败但 sizeCtl 仍 ≥ 0(本线程 CAS 跟另一线程 CAS 几乎同时打,都改了 sizeCtl?)→ 实际不会发生,CAS 失败的线程看到 sizeCtl 一定是 -1

5. **`Thread.yield()` 为什么够用?**
   - **不需要等初始化线程干完才继续**:yield 后下一轮 while 循环会发现 `table != null`,直接 `return` 走人
   - **不需要保证公平性**:先抢到 CAS 的先初始化,后到的只是"看一眼就走",不存在饿死
   - 真正的"阻塞 + 唤醒"代价太大(OS 线程切换),自旋 + yield 在初始化这种**极短**的临界区里更划算

6. **`Thread.yield()` 到底发生了什么?**

   `Thread.yield()` 是 `Thread` 类的**静态 native 方法**,语义是"提示线程调度器**当前线程愿意让出 CPU 使用权**,但**不释放任何锁**,也**不进入阻塞状态**"。

   **OS 层面大致做的事:**

   ```
   1. 把当前线程从 "Running" 状态移回 "Ready(就绪)" 状态
   2. 重新参与 CPU 时间片的竞争(和其他 Ready 线程一起)
   3. 下一次被调度到时,从 yield() 调用点继续往下执行
   ```

   **关键性质:**

| 性质 | 说明 |
|------|------|
| **不释放锁** | 如果当前线程持有 `synchronized` / `ReentrantLock`,yield 期间锁依然在手上 |
| **不进入阻塞** | 不会触发 `BLOCKED` / `WAITING` 状态,只是暂时让出 CPU |
| **不保证立即生效** | JVM 可以**忽略**这个提示(规范允许)。某些 JVM 在单核/无竞争时可能啥也不做 |
| **不保证公平** | yield 后**自己也可能被立刻调度回来**,规范明确说了"被调度器忽略也是允许的" |

   **回到 initTable 的语境:**

   ```java
   while ((tab = table) == null || tab.length == 0) {
       if ((sc = sizeCtl) < 0)
           Thread.yield();        // ← 让出 CPU,让"真正干活的初始化线程"多跑一会儿
       else if (U.compareAndSetInt(this, SIZECTL, sc, -1)) {
           // 抢到 CAS 的线程:真正执行 new Node[capacity]
       }
   }
   ```

   - **本线程不持锁**:`initTable` 走的是 CAS,不是 `synchronized`,所以 yield 不会影响别的线程继续推进
   - **让出 CPU 的目的**:给"正在 `new Node[capacity]` 的线程"更多执行机会,**避免自己空转**反复读 `sizeCtl` 浪费 CPU
   - **本线程还在"自旋"**:yield 后回到 Ready 状态,被调度回来后**继续 while 循环**,重新读 `table` 判空
   - **退出条件不是"等初始化完成"**:而是"下一次 while 看到 `table != null`"——可能是 yield 一次后,也可能是 yield 多次后,取决于调度

   **和 `Object.wait()` / `LockSupport.park()` 的区别:**

| | `Thread.yield()` | `wait()` / `park()` |
|---|------------------|---------------------|
| 释放锁? | ❌ | ✅(wait 必须先持锁) |
| 进入阻塞? | ❌(仍 Ready) | ✅(BLOCKED / WAITING) |
| 需要唤醒? | ❌ | ✅ |
| 开销 | 极小(就一次状态切换) | 大(可能要 OS 线程切换) |
| 适用场景 | 短临界区"礼让" | 长时间等待 |

   **一句话**:`yield` 是"礼貌让路"——告诉调度器"我先不急,你让别人先跑",但**我随时能被叫回来**,不需要任何唤醒成本。在 initTable 这种"等几纳秒"的场景下,**比 wait/park 划算得多**。

7. **面试话术**:

> "`sizeCtl` 在 1.8 里是个语义多段的字段:0/正数=未初始化或扩容阈值,**-1=正在初始化**,负数(<-1)=正在扩容。initTable() 进入 while 循环后,先看 `sizeCtl < 0` 就让出 CPU;否则尝试 CAS 把 sizeCtl 从 0 改成 -1,抢到的线程负责 `new Node[capacity]`,最后把 sizeCtl 改成 `capacity × 0.75` 作为扩容阈值。这种用 `sizeCtl` 兼做"状态机"的写法,比单独加锁轻得多,代价只是其他线程短暂自旋。"

#### 2.2 tabAt / casTabAt —— Unsafe 直接操作内存

```java
// 读：保证可见性（等价于 volatile 读）
static <K,V> Node<K,V> tabAt(Node<K,V>[] tab, int i) {
    return (Node<K,V>) U.getObjectAcquire(tab, ((long)i << ASHIFT) + ABASE);
}

// 写：CAS 原子替换
static <K,V> boolean casTabAt(Node<K,V>[] tab, int i, Node<K,V> c, Node<K,V> v) {
    return U.compareAndSetReference(tab, ((long)i << ASHIFT) + ABASE, c, v);
}
```

**这一段为什么不像普通 Java 代码?**

`tabAt` / `casTabAt` 完全**绕开了对象头 → 数组 → 元素的常规访问链**,直接通过 `Unsafe` 算偏移量操作内存。一次性解决了两个问题:**可见性** + **原子性**。

##### 1. 三个关键常量 —— 为什么不是 `tab[i]`

```java
ABASE   // Node[] 数组对象的**起始内存地址**(对象头之后)
ASHIFT  // 一个 Node 引用占用的字节数(通常 = 3,因为对象引用在 64 位 JVM 里被压缩到 4 字节,但寻址用 8 字节对齐 → log2(8) = 3)
ABASE   // 同上,数组首地址
```

| 字段 | 含义 | 典型值(64 位 JVM + 压缩指针) |
|------|------|------------------------------|
| `ABASE` | 数组对象**数据区起始地址**(跳过对象头 16 字节) | 对象头之后那个地址 |
| `ASHIFT` | `<< ASHIFT` = 1 个 Node 引用占几个字节的寻址粒度 | `3`(= 2^3 = 8 字节对齐) |
| `(long)i << ASHIFT` | 第 i 个元素相对 ABASE 的**字节偏移** | `i * 8` |

**为什么不直接写 `tab[i]`?**

- `tab[i]` 编译出来其实是**两次内存访问**:先读 `tab` 数组的第 i 个槽得到引用,再 deref 读 Node
- 问题是:**这两次访问都不是 volatile 语义**,普通读拿不到最新值
- 直接走 Unsafe:**一次 native 调用**,在底层用 `volatile read` + `LoadLoad 屏障` 把那个槽里的引用读出来,**绝对拿的是最新值**

##### 2. `getObjectAcquire` vs `getObjectVolatile` 的区别

| | `getObjectVolatile` | `getObjectAcquire`(1.8 后用这个) |
|---|---|---|
| **屏障** | `LoadLoad + LoadStore` | **只有 `LoadLoad`** |
| **开销** | 略重 | 更轻 |
| **语义** | 读后所有读写都不能重排到读前面 | **读后只要保证"读不重排到读前面"即可** |
| **适用** | 既要可见性又要约束后续操作 | **只要可见性,后续操作自己能保证顺序** |

`getObjectAcquire` 是 JDK 9 之后对 `getObjectVolatile` 的"瘦身版"——CAS 场景下用 `getObjectAcquire` + `compareAndSetReference` 就够了,不需要 `LoadStore` 屏障,CPU 流水线影响更小。1.8 后期版本(8u191+)已经替换成 `getObjectAcquire`。

##### 3. `casTabAt` 的四个参数

```java
casTabAt(tab, i, c, v)
//         │  │  │  └─ 期望写入的新值
//         │  │  └──── 期望读到的"当前值"(快照)
//         │  └─────── 桶下标
//         └────────── 数组
```

CAS 三要素:**内存地址** + **预期值 c** + **新值 v**

- 如果 `tab` 数组第 i 个槽"当前等于 c" → 写成 v,**返回 true**
- 否则"别人抢先改了" → 不动,**返回 false**(调用方重新循环)
- 这一步等价于:
  ```java
  synchronized (tab) {
      if (tab[i] == c) { tab[i] = v; return true; }
      return false;
  }
  ```
  但**全程无锁**,靠硬件 `cmpxchg` 指令保证原子性。

##### 4. 面试话术:为什么 CHM 不用 `volatile` 数组 + `tab[i] = ...`

> "1.8 的 `table` 数组**本身不是 volatile**——`volatile` 修饰数组只对'数组引用'生效,对元素的读写不提供 volatile 语义。所以 CHM 干脆**不靠 volatile**,而是用 `Unsafe.getObjectAcquire` 读元素、`Unsafe.compareAndSetReference` 写元素:前者保证读到的引用一定是最新的(LoadLoad 屏障),后者保证写入是原子的(CAS 硬件指令)。一句话:**用 Unsafe 精确控制每一次内存访问的可见性和原子性,绕开 volatile 数组的局限。**"

##### 5. 一句话记忆

> `tabAt` = 一次 `Unsafe` 读,**绝对最新**;`casTabAt` = 一次 `Unsafe` CAS,**绝对原子**;CHM 用这两件套替换了 `volatile Node[]` + `tab[i]`,精确控制每一次桶访问的内存语义。

#### 2.3 多线程协助扩容（重点！）

1.8 扩容时，**其他写线程遇到 `ForwardingNode` 会主动加入扩容**，而不是等待：

```
扩容线程 T1：负责迁移桶 [0..15]
写线程 T2  ：发现桶 i 是 ForwardingNode → 调用 helpTransfer() → 领取桶 [16..31] 参与迁移
写线程 T3  ：同理领取桶 [32..47]
...
```

这是 1.8 高并发性能的核心优化之一。

#### 2.4 size 统计 —— LongAdder 思想

```
baseCount（long）
    ↓ CAS 竞争失败时
CounterCell[]（分散到不同 cell 上累加）

size = baseCount + sum(counterCells)
```

##### 1. 为什么需要 LongAdder 思想 —— 先看 AtomicLong 的痛点

JDK 的 `AtomicLong` 用一个 `volatile long` + CAS 实现线程安全:

```java
// AtomicLong.addAndGet
do {
    old = value;
    new = old + 1;
} while (!compareAndSwapLong(&value, old, new));  // 失败 → 自旋重试
```

**高并发下的问题:**
- 所有线程都**竞争同一个 `value` 字段**
- CAS 失败的线程进入自旋,白白浪费 CPU
- 多核机器上,Cache Line 持续在核间"颠簸"(MESI 协议失效),性能**随线程数增加而恶化**

##### 2. LongAdder 的核心思想 —— 分散热点

```
把"一个共享变量" 拆成 "一个 base + N 个分散的 cell"
                    │
                    ▼
        每个线程优先操作自己的 cell,失败才用 CAS 抢 base
                    │
                    ▼
           读 size = base + sum(cells),只是简单求和
```

**关键性质:**
- **写**:分散到 N 个 cell,竞争从"一个热点" → "N 个冷点"
- **读**:把 N 个值加起来就行,无需加锁,**最终一致**
- **适用场景**:**写多读少**(统计数据、计数器)——`ConcurrentHashMap.size()` 正是这种

##### 3. CHM 的 addCount() 怎么落地的

```java
// 简化版 addCount(1L, binCount)
private final void addCount(long x, int check) {
    CounterCell[] as; long b, s;

    // 情况 A:CounterCell[] 已存在 → 直接定位到自己的 cell 累加
    if ((as = counterCells) != null ||
        // 情况 B:先尝试 CAS baseCount
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {

        // 情况 C:CAS baseCount 失败 或 cells 还不存在 → 走 cell 路径
        CounterCell a; long v; int m;
        boolean uncontended = true;

        if (as == null || (m = as.length - 1) < 0 ||
            (a = as[getProbe() & m]) == null ||
            !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            // 情况 D:CAS cell 也失败 → 触发 cells 扩容/创建(最难走的分支)
            fullAddCount(x, uncontended);
        }
    }

    if (check >= 0) {  // binCount 用来判断是否需要扩容
        ... // 省略
    }
}
```

**逻辑分层(从轻到重):**

| 优先级 | 分支 | 说明 | 频率 |
|--------|------|------|------|
| 1 | CAS `baseCount` 成功 | 没人竞争时直接累 base,**最快** | 低并发常见 |
| 2 | CAS `cell[mySlot]` 成功 | 定位到自己的 cell 累加,几乎无竞争 | **高并发常见** |
| 3 | `fullAddCount` | cells 未初始化 / 自己的 cell 为 null / CAS cell 失败 → 创建或扩容 cells | 冷启动 / 极端竞争 |

##### 4. `getProbe()` —— 怎么"定位到自己的 cell"

```java
static final int getProbe() {
    return UNSAFE.getInt(Thread.currentThread(), PROBE);  // 从 Thread 对象里读
}
```

- `Thread.threadLocalRandomProbe`:**每个线程独有的随机数**(ThreadLocal 思路,避免全局竞争)
- `getProbe() & (cells.length - 1)` → 得到**自己在 cells 数组里的下标**
- 这样不同线程大概率落到**不同 cell**,CAS 竞争大幅降低

##### 5. size() 怎么读 —— 简单求和,无锁

```java
public int size() {
    long n = sumCount();   // baseCount + sum(counterCells)
    return (n < 0) ? 0 : (n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) n;
}

final long sumCount() {
    CounterCell[] as = counterCells; long s = baseCount;
    if (as != null) {
        for (CounterCell a : as)
            if (a != null) s += a.value;  // 累加所有 cell
    }
    return s;
}
```

- **整个过程不持锁**,只是顺序遍历读
- 因为 cell 是 volatile 写,读到的不是"撕裂值",只是**最终一致**快照
- 极端情况下可能略小于真实 size(读过程中还有新 cell 在累加),但**永远不会大于真实 size**(`addCount` 不会"减")

##### 6. 跟 1.7 size() 的对比

| | 1.7 | 1.8 |
|---|-----|-----|
| 数据结构 | 16 个 Segment,各持 `count` | `baseCount` + `CounterCell[]` |
| 写时竞争点 | Segment 锁(16 把) | `baseCount` 或单个 cell(几十把,动态) |
| size() 复杂度 | O(segments) + 可能加锁重试 | O(cells.length) **无锁求和** |
| size() 期间写阻塞? | **会**(持 16 把锁) | **不会**(纯读) |

##### 7. 面试话术

> "1.8 的 size 统计用的是 LongAdder 思路:`baseCount` 一个共享值 + `CounterCell[]` 分散热点。每个线程用 `getProbe()` 拿自己的随机数,定位到独立 cell 做 CAS 累加,失败才回头抢 base;size() 读的时候就是 `baseCount + sum(cells)` 一次无锁遍历。**写分散到 N 个 cell 降低竞争,读时简单累加不怕'多读少'**,正是 size() 这种'写多读少'场景的最优解。1.7 的 16 个 Segment 求和是 'O(N) + 可能持锁重试',1.8 直接降为 'O(cells) + 纯无锁',差距非常大。"

##### 8. 一句话记忆

> 写: `CAS baseCount` → 失败就 `CAS 自己的 cell` → 再失败就 `fullAddCount` 创建/扩容 cells;读:`baseCount + sum(cells)`,**全程无锁、最终一致**。



---

## 三、为什么废弃分段锁？本质原因

### 3.1 锁粒度：Segment 级 → 桶（Node）级

| | 1.7 分段锁 | 1.8 桶头锁 |
|--|-----------|-----------|
| 锁对象 | Segment（含多个桶） | 单个桶的头节点 |
| 最大并发度 | 16（固定） | `table.length`（动态，默认 16 → 扩容后 32/64...） |
| 空桶写入 | 需要加锁 | **CAS 无锁写入** |

**本质**：分段锁是"粗粒度的悲观锁"；1.8 是"细粒度悲观锁 + 乐观锁（CAS）"的混合策略。

### 3.2 并发度不再受初始化参数限制

```
1.7：concurrencyLevel = 16（初始化后固定）
     即使 table 扩容到 1024 个桶，锁仍然只有 16 把

1.8：锁数量 = 非空桶数量，随 table 扩容线性增长
     table 扩容到 1024 → 最多 1024 个独立锁
```

### 3.3 JDK 对 synchronized 的持续优化

JDK 1.6+ 对 `synchronized` 引入了**锁升级机制**：

```
无锁 → 偏向锁 → 轻量级锁（CAS 自旋）→ 重量级锁（OS Mutex）
```

在低竞争场景下，`synchronized` 几乎没有额外开销，性能已接近甚至超过 `ReentrantLock`。

1.7 用 `ReentrantLock` 的主要原因之一是 `synchronized` 当时太重；1.8 时这个理由已经不成立。

### 3.4 链表 → 红黑树，彻底解决哈希冲突退化

```
1.7：哈希冲突 → 链表查询 O(n)，无上限
1.8：链表长度 ≥ 8 且 table.length ≥ 64 → 转红黑树 → 查询 O(log n)
```

### 3.5 内存结构更简洁

```
1.7：ConcurrentHashMap → Segment[] → HashEntry[][]
     两层间接寻址，GC 压力更大，缓存局部性差

1.8：ConcurrentHashMap → Node[]
     单层数组，CPU Cache 友好
```

---

## 四、一图总结对比

```
┌─────────────────────────────────────────────────────────────────┐
│                    核心设计对比                                    │
├──────────────┬──────────────────────┬───────────────────────────┤
│ 维度         │ JDK 1.7              │ JDK 1.8                   │
├──────────────┼──────────────────────┼───────────────────────────┤
│ 数据结构     │ Segment + HashEntry  │ Node[] + 链表/红黑树       │
│ 锁机制       │ ReentrantLock（段锁）│ CAS + synchronized（桶锁） │
│ 最大并发度   │ 16（固定）           │ table.length（动态）       │
│ 空桶写入     │ 加锁                 │ CAS 无锁                   │
│ 冲突处理     │ 链表 O(n)            │ 链表→红黑树 O(log n)       │
│ 扩容         │ 各 Segment 独立扩容  │ 多线程协助整体扩容         │
│ size 统计    │ 遍历所有 Segment     │ baseCount + CounterCell[]  │
│ 内存层级     │ 两层（段→桶）        │ 一层（直接桶数组）         │
└──────────────┴──────────────────────┴───────────────────────────┘
```

---

## 五、面试高频追问

**Q：1.8 的 synchronized 锁住的是什么？**
> 桶的**头节点**（`Node f = tabAt(tab, i)`），不是整个 Map，也不是数组本身。

**Q：为什么桶为空时用 CAS，不为空时用 synchronized？**
> 空桶时只需要 CAS 一次写入，天然无竞争;非空桶需要遍历链表/树,操作较长,用 synchronized 更合适(避免 CAS 自旋浪费 CPU)。
> 详见 [[答案-Java并发-并发安全#4. ConcurrentHashMap 1.7 vs 1.8 的本质不同？为什么废弃分段锁？|答案-Java并发-并发安全 § 4]] —— 第 4 条 "1.8 的细粒度锁已足够:CAS 处理无冲突插入,synchronized 处理有冲突"。

**展开:为什么"空桶时 CAS 一次写入"就"天然无竞争"?**

##### 1. CAS 一次写入的本质

```java
// putVal 核心片段
else if ((f = tabAt(tab, i)) == null) {  // 桶为空
    if (casTabAt(tab, i, null, new Node<>(hash, key, value)))
        break;  // CAS 成功 → 写入完成
}
```

**"天然无竞争"的含义是:**
- **写入动作只有一步**:把 `null` 换成 `new Node`,**没有遍历、没有查找、没有修改多个字段**
- 没有"读 → 判断 → 写"这种多步操作,也就**没有"中途被打断"的中间态**

##### 2. 三个角度证明"无竞争"

| 角度            | 说明                                                                      |
| ------------- | ----------------------------------------------------------------------- |
| **操作原子性**     | 一次 CAS 就是一条 `cmpxchg` 指令,硬件层面不可打断                                       |
| **没有中间态**     | 桶里要么是 `null`、要么是 `new Node`,**不存在"半写"状态**                               |
| **CAS 失败的语义** | 即使两个线程同时 CAS,**只有一个赢**,输的那个读出来桶已经不是 null,自然走 `else` 分支(synchronized 路径) |

**对比"非空桶"的场景:**

```java
// 桶非空 → 遍历链表 → 找到 key 或到末尾
synchronized (f) {  // 必须加锁,因为下面有多步操作
    if (fh >= 0) {
        for (Node<K,V> e = f;;) {
            K k;
            if (e.hash == h && ((k = e.key) == key || (k != null && key.equals(k)))) {
                // 找到 key → 更新 value
                oldVal = e.val;
                if (!onlyIfAbsent) e.val = value;
                break;
            }
            if ((e = e.next) == null) {
                // 遍历到末尾 → 插入新节点
                Node<K,V> pred = p;
                p = new Node<>(h, key, value, pred);
                break;
            }
        }
    }
    // ... 树化等操作
}
```

**非空桶为什么不能 CAS?**
- 操作**至少 3 步**:遍历链表 / 找位置 / 修改或插入节点
- 中间任何一步被打断,都需要从头再来
- CAS 失败 → 重试 → 别人可能又改了 → 再次失败 → **CAS 自旋浪费 CPU**
- 加 `synchronized` → 失败的线程直接进 EntryList 阻塞,CPU 让出去给别人,等锁释放再唤醒,**公平 + 不浪费**

##### 3. 形象类比

```
空桶写入:把一封信投进一个空邮箱 → 一塞就进,不存在"半塞"状态
         两个邮递员同时到 → 第一个把信塞进去,第二个发现邮箱不空,转身走人

非空桶写入:整理一个堆满信件的邮箱 → 需要打开、翻找、对比、抽出/插入
           两个人同时整理 → 必须排队(一个人锁门整理,另一个门外等)
```

##### 4. 一句话记忆

> 空桶 = **一次原子操作** → CAS 一次搞定,失败就转入 synchronized 路径;非空桶 = **多步复合操作** → 必须用锁保证多步原子,自旋 CAS 反而是浪费。**CAS 适合"短小"操作,锁适合"复杂"操作**。

---

**Q：1.8 还有并发度（concurrencyLevel）参数吗？**
> 保留了该参数，但语义变了——仅用于初始化时估算初始容量，不再直接控制锁数量。

**Q：红黑树转回链表的条件？**
> 扩容后某桶节点数 ≤ 6（`UNTREEIFY_THRESHOLD`）时退化回链表。

**Q：1.8 是否完全无锁？**
> 不是。空桶写入是 CAS 无锁；非空桶写入、树化操作仍然使用 `synchronized`。它是**混合并发策略**，而非纯无锁。
