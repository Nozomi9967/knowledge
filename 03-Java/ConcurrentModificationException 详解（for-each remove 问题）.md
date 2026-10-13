---
tags:
  - java
  - 集合
  - 并发
  - 面试
created: 2026-06-28
updated: 2026-10-08
---

# ConcurrentModificationException 详解：for-each 遍历时 remove 的问题

## 产生原因

> 对应面试题：[[Java后端开发面试题汇总#一、Java 基础与集合（1-6）]] 第 1 题

### 场景复现

```java
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
for (String s : list) {
    if ("B".equals(s)) {
        list.remove(s);  // ❌ 抛出 ConcurrentModificationException
    }
}
```

### 背后的机制：fail-fast + modCount

**for-each 的底层本质是 Iterator。** 编译器会把上面的代码编译成：

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if ("B".equals(s)) {
        list.remove(s);  // 用 List 自己的 remove 方法
    }
}
```

**ArrayList 内部维护一个 `modCount` 字段（继承自 AbstractList）：**

| 字段                 | 类型    | 说明                                           |
| ------------------ | ----- | -------------------------------------------- |
| `modCount`         | `int` | 记录结构性修改的次数（add、remove、clear 等都会 +1）          |
| `expectedModCount` | `int` | Iterator 在创建时快照的 `modCount`，存在迭代器内部类 `Itr` 中 |

**抛出异常的链条：**

1. 创建 Iterator 时，`expectedModCount = modCount`（假设此时都是 4）
2. 每次调用 `it.next()`，内部先执行 `checkForComodification()`：

```java
final void checkForComodification() {
    if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
}
```

3. `list.remove(s)` 是 **List 自身** 的 remove 方法，执行 `modCount++`（现在 `modCount = 5`）
4. 下一次循环调用 `it.next()` → `modCount(5) != expectedModCount(4)` → **抛异常**

### 关键理解

> [!info] 本质原因
> **Iterator 和 List 各自维护了一个修改计数器，但 List 自己的修改不会同步更新 Iterator 的 `expectedModCount`。** for-each 底层用的是 Iterator，而你调用的是 List 自己的 remove，计数器对不上就抛异常。

### 易错点辨析

| 操作                                  | 是否抛异常    | 原因                                                                   |
| ----------------------------------- | -------- | -------------------------------------------------------------------- |
| for-each 内 `list.remove(obj)`       | ❌ 抛异常    | `modCount` 增加但 `expectedModCount` 没变                                 |
| for-each 内 `list.add(obj)`          | ❌ 抛异常    | 同上，也是结构性修改                                                           |
| Iterator 显式循环内 `list.remove(obj)`   | ❌ 抛异常    | 虽没用 for-each 但仍是 Iterator 模式                                         |
| 单线程 for-each 内修改但不触发 `hasNext()` 检查 | ✅ 部分情况不抛 | 比如删除倒数第二个元素时，`size--` 导致 `hasNext()` 恰巧返回 false，异常未触发——这是**巧合**，不可依赖 |
| 普通 for 循环按索引遍历 `list.remove(i)`     | ✅ 不抛     | 没有 Iterator，不经 `checkForComodification()`                            |


每次迭代时，`for-each` 实际在做这两步：

1. **先调用 `hasNext()`**：判断是否还有下一个元素（判断逻辑是：`cursor != size`）。
    
2. **再调用 `next()`**：获取元素。在 `next()` 的第一行，会触发 `checkForComodification()`，即检查 `modCount == expectedModCount`。如果不相等，立刻抛出 CME 异常。


> [!warning] "删除倒数第二个不抛异常"是陷阱
> 当 `size=4`，遍历到倒数第二个（index=2），`list.remove(2)` 后 `size=3`。此时 `hasNext()` 判断 `cursor(3) != size(3)` 为 false，**直接结束循环，不会调用 `next()`**，所以异常没有被抛出。这是巧合而非修复，数据换一批就崩。

---

## 三种修复方案及取舍

### 方案一：Iterator.remove（单线程首选）

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if ("B".equals(s)) {
        it.remove();  // ✅ Iterator 自己的 remove
    }
}
```

**原理：** `ArrayList.Itr.remove()` 内部做了两件事：

```java
public void remove() {
    if (lastRet < 0) throw new IllegalStateException();
    checkForComodification();    // 校验一致性
    ArrayList.this.remove(lastRet);  // 调用外部 List 的 remove
    expectedModCount = modCount;     // ← 关键：同步修改计数器
    cursor = lastRet;
    lastRet = -1;
}
```

它调完 `ArrayList.this.remove()` 之后，**立刻把 `modCount` 的最新值重新赋值给 `expectedModCount`**，下一次 `checkForComodification()` 就通过了。

**优缺点：**

| 维度 | 评价 |
|------|------|
| 性能 | ✅ 最优，无额外内存/数组复制 |
| 线程安全 | ❌ 非线程安全，多线程仍需外部同步 |
| 约束 | 只能删除当前遍历到的元素（`lastRet`），不能随便 remove |
| 适用 | 单线程遍历中删除元素，最推荐 |

---

### 方案二：CopyOnWriteArrayList（读多写少 or 多线程场景）

```java
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>(Arrays.asList("A", "B", "C", "D"));
for (String s : list) {
    if ("B".equals(s)) {
        list.remove(s);  // ✅ 不会抛异常 —— 但可能不是你想的那样
    }
}
```

**原理：** `CopyOnWriteArrayList` 的迭代器是**快照迭代器**。迭代器在创建时拿到数组的不可变快照，之后所有修改操作都在新数组副本上进行，迭代器读的始终是旧数组，因此 `modCount` 检查彻底不存在（COW 根本没有 modCount 机制）。

```java
// CopyOnWriteArrayList 的迭代器
static class COWIterator<E> implements ListIterator<E> {
    final Object[] snapshot;  // 创建时快照的数组，永远不会变
    // ... 没有 checkForComodification()
}
```

**优缺点：**

| 维度 | 评价 |
|------|------|
| 线程安全 | ✅ 本身是并发容器，多线程安全 |
| 写性能 | ❌ 每次修改（add/remove/set）复制整个底层数组。N 个元素复制 O(N)，高频写下 CPU 和 GC 压力巨大 |
| 内存开销 | ❌ 写时旧数组暂不可回收，存在短暂的内存双倍占用 |
| 读一致性 | ⚠️ 读到的可能是旧数据（写操作对已持有的迭代器**不可见**） |
| 适用 | **读多写极少**的场景（如配置白名单、监听器注册列表，改一次后很长时间只读） |

> [!warning] 面试高频陷阱
> 很多人说 CopyOnWriteArrayList 适合"读多写少"，但少到多少才算"少"？**如果写入频率超过每秒几次，或数据量超过几千条，COW 的复制成本就不可接受了。** 例如 10 万元素的列表，每次 remove 要复制 10 万次引用（约 800KB），触发 Young GC，平摊到每次写入就是 O(N) 的写延迟。

---

### 方案三：removeIf（JDK 8+，最简洁）

```java
list.removeIf(s -> "B".equals(s));  // ✅ 一行搞定
```

**原理：** `ArrayList.removeIf()` 是 JDK 8 在 `Collection` 接口中新增的默认方法，它的内部实现使用了**批量标记删除**：

```java
// 简化版逻辑
public boolean removeIf(Predicate<? super E> filter) {
    final int size = this.size;
    final Object[] elementData = this.elementData;
    int removeCount = 0;
    final BitSet removeSet = new BitSet(size);  // 标记哪些位置要删除
    
    // 第一遍：遍历，标记所有要删除的位置
    for (int i = 0; i < size; i++) {
        if (filter.test(elementData[i])) {
            removeSet.set(i);
            removeCount++;
        }
    }
    
    // 第二遍：批量移位（一次到位，避免逐个删除的反复移位）
    // ... 将所有幸存元素前移
    
    // 修改 modCount 只一次
    modCount += removeCount;
    // 更新 size
    // 尾部置 null 让 GC 回收
}
```

**关键：** `removeIf` 在**一次遍历中完成所有标记**，然后**一次批量移位**，最后只 **一次修改 `modCount`**。它根本不经过 Iterator，走的是内部循环 + BitSet 标记，因此不存在 `ConcurrentModificationException`。

**优缺点：**

| 维度 | 评价 |
|------|------|
| 代码简洁度 | ✅ 最优，一行搞定 |
| 性能 | ✅ 比 Iterator 逐个 remove 更快。逐个 remove 会导致每删一个元素就 O(N) 移位（平均 O(N²)），removeIf 批量移位是 O(N) |
| 线程安全 | ❌ 非线程安全，多线程需外部加锁 |
| 适用 | 单线程批量删除，**现代 Java 的首选方案** |

---

## 三种方案对比总结

| 对比维度 | `Iterator.remove` | `CopyOnWriteArrayList` | `removeIf` |
|----------|-------------------|----------------------|------------|
| 代码简洁度 | ⭐⭐ 循环 + hasNext/next/remove | ⭐⭐⭐ 直接替换 List 实现 | ⭐⭐⭐ 一行 Lambda |
| 单线程性能 | ⭐⭐⭐ O(N) 但每次 remove 都移位 | ⭐ 写 O(N²) 复制 + GC 压力 | ⭐⭐⭐ O(N) 批量移位最优 |
| 多线程安全 | ❌ 否 | ✅ 是 | ❌ 否 |
| 读旧数据风险 | ❌ 无 | ✅ 可能读到旧快照 | ❌ 无 |
| 适用场景 | 单线程遍历时删除当前元素 | 读多写极少的并发场景 | 单线程批量删除（推荐首选） |

### 面试话术

> **面试官问："for-each 遍历 ArrayList 时 remove 为什么抛异常？"**
>
> 答：本质上是因为 ArrayList 内部有一个 `modCount` 字段记录结构性修改次数。for-each 编译后本质是 Iterator 模式，Iterator 在创建时会快照一份 `expectedModCount`。每次 `next()` 前都检查这两个值是否相等。直接调用 `list.remove()` 会改变 `modCount`，但不会更新 Iterator 内部的 `expectedModCount`，导致下一次 `next()` 时检查失败，抛出异常。
>
> **面试官追问："怎么修复？"**
>
> 答：有三种方案。一、用 `Iterator.remove()`，它会在调用 List.remove 后同步 `expectedModCount`，适合单线程遍历中删当前元素。二、用 `CopyOnWriteArrayList`，它的迭代器是快照模式的，没有 modCount 检查，但每次增删都复制底层数组，只适合读多写极少的并发场景。三、用 `removeIf`，这是 JDK 8 的批量删除方法，内部用 BitSet 标记 + 一次批量移位，性能最优且一行搞定。**单线程场景下 `removeIf` 是最佳选择。**

---

## fail-fast 的精确边界

很多人把 `ConcurrentModificationException` 当成"Java 帮你检测并发问题"的可靠机制，这是误解。精确表述如下：

**1. fail-fast 是"尽力尽早抛"，不是保证必抛。**
它只是"结构性修改计数（`modCount`）≠ 迭代器快照（`expectedModCount`）时，若在下一次 `next()/hasNext()` 的检查中被发现，就抛异常"。它**不保证**一定检测到，只是"一旦发现就尽早失败"。所以**没抛 ≠ 没有并发修改**，不能拿"没报错"当"安全"。

**2. 它不是并发安全检测工具，单线程照样触发。**
前文 `for-each` 里直接 `list.remove` 就是**单线程**触发的经典例子。fail-fast 跟"几个线程"没有必然关系，它检测的是"**迭代器使用期间集合被结构性改动**"。

**3. "结构性修改"依实现而定。**
`modCount` 只在结构性修改时才 ++：
- `add` / `remove` / `clear` → 算结构性修改，会触发；
- `ArrayList.set(i, e)`（替换已有位置、**不改变 size**）→ **不算**结构性修改，`modCount` 不变，**不会**触发 fail-fast。

**4. 多线程场景不能一概"换 CopyOnWriteArrayList"。**
`CopyOnWriteArrayList` 靠"写时复制 + 快照迭代器"**回避**了 CME，但它不是万能解药：
- 它是**读多写极少**的容器——每次写都复制整个底层数组，写频繁时 CPU / GC 代价巨大；
- 迭代器读到的是**创建时的快照**，写操作对已持有的迭代器**不可见**（弱一致），有读到旧数据的风险。

  真正的多线程高并发读写，应优先考虑 `ConcurrentHashMap`、加锁，或 `Collections.synchronizedList` + 显式同步迭代，而不是无脑 COW。

> 一句话：**fail-fast 是"迭代器使用期间集合被改动就赶紧报错"的尽力而为机制，不是并发检测；单线程也会触发，结构性修改的定义依实现，多线程也不等于必须换 COW。**

---

## 关联知识点

- [[Java基础面试题]] — Java 集合框架基础（ArrayList、Iterator、fail-fast 机制）
- [[Java后端开发面试题汇总]] — 更多场景化面试题
- [[答案-Java并发-并发安全#5. CopyOnWriteArrayList 的适用场景与副作用？]] — COW 并发容器详解
- [[阻塞队列详解]] — 其他并发容器的设计思路
