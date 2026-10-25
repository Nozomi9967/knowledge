---
tags:
  - java
  - jvm
  - G1
  - 垃圾回收
  - GC
created: 2026-06-10
updated: 2026-06-10
---

# G1 Humongous Region（大对象区）

## 一句话

G1 把堆划分为若干大小相等的 Region（1MB~32MB）。当一个对象的大小**超过 Region 的一半**时，G1 不会把它放进普通的 Eden/Old Region，而是分配在一种特殊的 **Humongous Region** 中。

---

## 判定规则

```
Region 大小 = 1MB（由 -XX:G1HeapRegionSize 设定）
     ↓
对象大小 > 512KB（Region 的一半）
     ↓
该对象被视为 Humongous Object（巨型对象）
     ↓
分配到连续的若干 Humongous Region 中
```

> Humongous 对象的大小下限 = **`RegionSize / 2`**，上限 = **整个堆大小**（超过直接 OOM）。

---

## 分配方式

```
普通对象分配：
┌──────────────────────────────────────────┐
│   Region (Eden)                          │
│   [obj1][obj2][obj3]...[objN]            │  ← 多个对象挤在一个 Region 里
└──────────────────────────────────────────┘

巨型对象分配：
┌──────────┬──────────┬──────────┬──────────┐
│ Humongous│ Humongous│          │          │  ← 一个对象占连续 N 个 Region
│  对象数据  │  对象数据  │ (浪费)   │ (浪费)   │
│  Region 1│ Region 2 │ Region 3 │ Region 4 │
└──────────┴──────────┴──────────┴──────────┘
```

注意第二个 Region 和后续 Region 的剩余空间**无法被其他对象使用**，这是 Humongous 分配的空间浪费问题。

---

## 回收特点

### 1. 分配和回收都发生在并发标记（Concurrent Marking）周期中

- 普通 Region 在 Young GC / Mixed GC 中回收
- Humongous Region **不在 Young GC 中回收**，只在**并发标记结束后**、或者**Full GC** 中回收

### 2. 回收效率低

- 一个 Humongous Region 只放一个对象，只要这个对象还活着，整个 Region 都不能回收
- 如果大对象存活时间长（比如缓存），对应的 Region 就一直无法复用

### 3. 回收时整块 Region 全量释放

- 普通 Region 回收后空间可以放其他大小的对象
- Humongous Region 回收后整块 Region 变为空闲，不会产生内存碎片

---

## 可能触发的问题

### 1. Humongous 分配触发 Full GC

分配巨型对象时，如果找不到足够连续的 Humongous Region，G1 会先尝试**并发标记**回收，标记后仍然不够 → **Full GC（Serial Old，单线程 STW）**

### 2. 提前触发并发标记

- G1 有一个阈值：`-XX:InitiatingHeapOccupancyPercent`（默认 45%）
- 当堆使用率超过该阈值 + Humongous 分配时，会**提前启动并发标记周期**

### 3. 空间浪费

一个刚好比 Region 一半大一点点的对象，就导致后续整个 Region 被浪费。比如 Region=2MB，一个 1.1MB 的对象就占用 2 个 Region（4MB），空间利用率只有 27.5%。

---

## 相关参数

| 参数 | 作用 |
|------|------|
| `-XX:G1HeapRegionSize` | 设置 Region 大小（1M~32M），影响 Humongous 的判定阈值 |
| `-XX:G1HeapWastePercent` | 允许浪费的堆空间百分比，达到时触发 Mixed GC |
| `-XX:G1MixedGCLiveThresholdPercent` | Region 存活对象占比低于此值才被选入 Mixed GC |

---

## 面试话术

> **问：G1 中 Humongous Region 是什么？**
>
> 答：G1 把堆分成大小均等的 Region。当一个对象的大小超过 Region 的一半时，G1 会把它认定为 Humongous Object（巨型对象），不再放入普通的 Eden/Old Region，而是在连续的一组 Humongous Region 中分配。
>
> 巨型对象的回收方式和普通对象不同：它在 Young GC 中不会被回收，只在并发标记周期结束后或 Full GC 中才回收。而且一个巨型对象占用了连续多个 Region，中间剩余的空间会被浪费。所以如果业务中有大量较大的数组或缓存，要特别注意 `-XX:G1HeapRegionSize` 的配置，避免产生大量 Humongous 分配导致的 Full GC。

---

## 关联知识点

- [[jvm八股]] — JVM 八股总览（G1 部分）
- [[jvm_review]] — JVM 模拟面试批改（含原始出处）
