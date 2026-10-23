---
tags:
  - java
  - 并发
  - CAS
  - 原子类
created: 2026-10-15
updated: 2026-10-15
source: 2026-10-15 面试真题（被问到未答出）
---

# AtomicLong 底层原理与高性能原因

> 面试真题：AtomicLong 的底层原理是什么？为什么性能好？
> 本文补足这条链路：**volatile 值 + CAS 自旋 + Unsafe 硬件指令**，以及它和 LongAdder 的分工。

---

## 一、一句话结论

**AtomicLong 的本质是「volatile 变量 + CAS 自旋」**：用 volatile 保证可见性、用 CPU 的 CAS 指令保证「读-改-写」的原子性，全程**不阻塞线程**，所以比加锁快——快在**没有线程切换、没有内核态陷入**，而不是快在"无锁就零成本"。

代价要说清楚：**高并发下 CAS 大量失败会自旋烧 CPU**，这时候该换 LongAdder。

---

## 二、三个组成部分

### 1. volatile 的值——保证可见性

```java
public class AtomicLong extends Number implements java.io.Serializable {
    private volatile long value;
    // ...
}
```

值本身是 `volatile long`。这保证两件事：

- **可见性**：一个线程改完，其他线程立刻能看到最新值，不会读到工作内存里的旧副本
- **有序性**：禁止相关指令重排序

但注意：**volatile 只解决了"看得见"，没解决"不重叠"**——`i++` 这种读-改-写三步，光有 volatile 照样丢更新（两个线程同时读到 0、都写回 1）。原子性的那一步靠 CAS。

### 2. CAS——保证「读-改-写」的原子性

CAS（Compare-And-Swap）是**一条 CPU 原子指令**（x86 上是带 `lock` 前缀的 `cmpxchg`），语义是：

```
CAS(V, A, B)：如果 V 当前值 == 期望值 A，则把它改成 B，返回成功；否则什么都不做，返回失败。
```

整个过程由硬件保证原子，不会被其他线程打断。

`incrementAndGet()` 的自旋逻辑（概念版）：

```java
public final long incrementAndGet() {
    long prev, next;
    do {
        prev = get();              // ① 读当前值（volatile 读）
        next = prev + 1;           // ② 算新值
    } while (!compareAndSet(prev, next));  // ③ CAS 写入；失败就重来
    return next;
}
```

**关键就在 ③**：如果这一步 CAS 失败，说明「在我读值和写值之间，有别的线程先改了」——那我读到的 prev 已经过期，丢弃这次计算，重新读、重新算、再试。这个循环叫**自旋**。

用一句因果链串起来：**CAS 失败 ⟺ 期间被别的线程改过 ⟹ 旧值作废 ⟹ 重来**。这也是乐观锁"先干活、冲突再重试"的典型形态。

### 3. Unsafe——Java 层到硬件指令的桥

`compareAndSet` 往下走是：

```java
private static final Unsafe U = Unsafe.getUnsafe();
private static final long VALUE = U.objectFieldOffset(AtomicLong.class, "value");

public final boolean compareAndSet(long expectedValue, long newValue) {
    return U.compareAndSetLong(this, VALUE, expectedValue, newValue);
}
```

`Unsafe.compareAndSetLong` 是 **native 方法**，JVM 会把它直接编译成对应平台的 CAS 硬件指令（x86 的 `lock cmpxchg`）。所以：

- **对象**：`this`
- **字段偏移**：`VALUE`（先算出 value 字段在对象里的内存偏移）
- **期望值 / 新值**：`expectedValue` / `newValue`

==这就是"直接操作内存"的那一层——Unsafe 绕过 Java 语法限制，把 CAS 指令的能力暴露上来。==（JDK 9 之后 VarHandle 是它的官方替代，语义相同、有访问权限检查。）

---

## 三、为什么性能好——对比加锁

对比 `synchronized` / `ReentrantLock`，AtomicLong 省掉的是**阻塞相关的开销**：

| 维度 | synchronized / ReentrantLock | AtomicLong（CAS） |
|------|------------------------------|-------------------|
| 竞争失败时 | 线程**挂起阻塞**，进等待队列 | 线程**继续跑**，循环重试 |
| 状态切换 | 用户态 ↔ 内核态**上下文切换** | 全程**用户态**，不陷入内核 |
| 调度 | 依赖 OS 唤醒，有延迟 | 不依赖调度 |
| 死锁 | 可能 | **不可能**（没有"持有"这回事） |
| 竞争极高时 | 阻塞让出 CPU，反而省资源 | **自旋烧 CPU** |

**因果链（口述版）**：加锁的开销不在"加锁"这个动作本身，而在**竞争失败后线程的挂起与唤醒**——那是一次用户态到内核态的切换，还要等 OS 重新调度，代价是微秒级、高出实际计算几个数量级。CAS 失败**不挂起**，就在原地重试，只要冲突能在几轮内解决，就比"挂起再唤醒"便宜得多。

所以结论是：**低到中等竞争、临界区极短（就一个自增）的场景，CAS 完胜加锁**。

---

## 四、代价与边界（必须说的另一半）

CAS 不是银弹，三个代价：

1. **自旋烧 CPU**：竞争一高（比如几百个线程抢同一个计数器），大部分 CAS 都会失败，线程全在空转重试——CPU 打满但业务没进展。这时候**阻塞反而更优**（让出 CPU 给有用的线程）。
2. **只能原子一个变量**：多个变量要一起原子改，CAS 做不到。要么用 `AtomicReference` 把它们打包成一个对象，要么加锁。
3. **ABA 问题**：值从 A 被改成 B 又改回 A，CAS 看到还是 A 误以为没变。计数器场景几乎无影响（只关心最终加了多少），但**无锁栈/链表**这类依赖"中间过程"的结构会真的坏掉，用 `AtomicStampedReference` 加版本号解决（详见 [[ABA 问题详解]]）。

**这就是 LongAdder 存在的原因**：高并发计数时，把"一个热点"拆成"多个冷点"。

---

## 五、LongAdder：把热点拆成冷点

`LongAdder`（JDK 8 引入）的思路是**分散热点**：

```
AtomicLong：所有线程抢同一个 value        → 一个热点的竞争
                    ↓
LongAdder：base + Cell[] 数组
           线程按哈希落到不同的 Cell 上累加 → N 个冷点，冲突概率降到 1/N
```

- **写**：`add(x)` 时，线程对**自己那个 Cell** 做 CAS（不同线程大概率落在不同 Cell，几乎不冲突）
- **读**：`sum()` 把 `base` + 所有 `Cell` 的值**加起来**
- 竞争激烈时，Cell 数组会**扩容**（翻倍，上限约等于 CPU 核数），进一步分散

**取舍要讲清楚**：

| | AtomicLong | LongAdder |
|---|-----------|-----------|
| 竞争 | 所有线程争一个变量 | 分散到多个 Cell |
| 高并发吞吐 | 低（CAS 大量失败） | **高** |
| `sum()` 精度 | **精确**（就是变量本身） | **不保证实时精确**——求和时可能有线程正在改别的 Cell，得到的是"某个瞬间的近似和" |
| 适用场景 | 需要**精确值**、需要 CAS 语义（如 compareAndSet 做状态机） | **高并发计数/统计**（QPS 统计、监控打点），只关心量级 |
| 空间 | O(1) | O(N)（Cell 数组） |

**一句话选型**：要精确、要 CAS 语义 → `AtomicLong`；只要**高并发累加、能容忍 sum 是近似值** → `LongAdder`。

补充：`ConcurrentHashMap` 1.8 的 `size()` 用的就是 LongAdder 思想（base + 多个 cell），见 [[ConcurrentHashMap_1.7_vs_1.8]]。

---

## 六、面试话术（口述版）

> AtomicLong 的底层是 **volatile 值 + CAS 自旋**。它内部持有一个 `volatile long value`，volatile 保证可见性；真正的原子性靠 CAS——调 Unsafe 的 native 方法 `compareAndSetLong`，底层是 CPU 的 `cmpxchg` 指令，硬件保证"比较并交换"这一步不会被打断。`incrementAndGet()` 就是个自旋循环：读出旧值、加一、CAS 写回；如果 CAS 失败，说明期间被别的线程改过，旧值作废，重新读、重新算、再试。
>
> 它性能好，是因为**CAS 失败不阻塞线程**。加锁慢的根因不在加锁动作本身，而在竞争失败后线程要挂起、等 OS 唤醒，那是一次用户态到内核态的上下文切换，比实际自增的开销高几个数量级；CAS 全程在用户态重试，不陷入内核、不依赖调度，也不会死锁。所以**低到中等竞争、临界区极短**的场景它明显优于锁。
>
> 但代价也要说：**高并发下大量 CAS 失败会自旋烧 CPU**，而且它只能原子一个变量、还有 ABA 问题。所以超高并发做计数统计，我会换 **LongAdder**——它维护 base 加一个 Cell 数组，让不同线程落在不同的 Cell 上累加，把一个热点拆成多个冷点，冲突概率降到 1/N，吞吐高很多；代价是 `sum()` 只是近似值，没有 compareAndSet 语义。所以**要精确用 AtomicLong，只要高并发累加的统计值用 LongAdder**。

---

## 关联知识点

- [[ABA 问题详解]] — CAS 的 ABA 陷阱与 AtomicStampedReference 解法
- [[Java并发编程面试题]] — Java 并发八股总览（CAS / 乐观锁）
- [[Java并发编程热门面试题]] — 并发卷（Q11 CAS 与 ABA）
- [[ConcurrentHashMap_1.7_vs_1.8]] — 1.8 的 size() 用的就是 LongAdder 思想
- [[volatile 关键字详解]] — volatile 的两个作用与内存屏障
- [[Java基础热门面试题-2]] — 本页对应的面试题（Q33）
