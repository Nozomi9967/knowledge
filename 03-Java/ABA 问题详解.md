---
tags:
  - java
  - 并发
  - CAS
created: 2026-08-24
updated: 2026-08-24
背诵状态: 已完成
背诵开始: 2026-08-24 12:59
背诵结束: 2026-08-24 13:06
---

# ABA 问题详解

ABA 问题是 CAS 操作的一个经典陷阱，面试高频追问。

---

## 核心定义

```
线程 1 读值 = A
线程 2 把 A → B → A（中间改过又改回来）
线程 1 执行 CAS 发现 V 还是 A → CAS 成功
→ 但中间被改过的事实被忽略了
```

**CAS 只比较"值是什么"，不比较"值变过没有"。**

---

## 典型场景：无锁栈（最常考的追问）

这是面试官最想听到的具体场景，比纸上谈 A→B→A 有说服力。

### 背景

一个无锁栈，用 CAS 实现 push/pop：

```
初始栈顶 → NodeA → NodeB → null

线程 1 要 pop：读栈顶 = NodeA，准备 CAS(栈顶, NodeA, NodeA.next)
```

### 线程 2 插进来捣乱

```
线程 2 pop A → 栈顶变为 NodeB
线程 2 push C → 栈顶变为 NodeC，NodeC.next = NodeB
线程 2 push A → 栈顶变为 NodeA，NodeA.next = NodeC
```

此时栈的结构：`栈顶 → NodeA → NodeC → NodeB → null`

> [!note] 关键前提：这个 NodeA 就是原来的那个 NodeA，不是新建的
> "线程 2 push A" 是把 pop 出来的那个 NodeA **原对象**又压了回去——线程 2 在 pop 时持有它的引用，Java 的 GC 不会回收它。所以栈顶引用和最初**是同一个地址**，这一点都不巧，是同一块对象。
>
> 这正是 ABA 能骗过 CAS 的原因：栈顶引用值没变（还是同一个 NodeA），但 `NodeA.next` 已经被线程 2 从 NodeB 改成了 NodeC。CAS 只比引用，不比 `next` 指向。

### 线程 1 醒了

```
线程 1 执行 CAS(栈顶, NodeA, NodeA.next)
→ 栈顶此刻确实是 NodeA（值没变！）
→ CAS 成功，栈顶 = NodeA.next = ???（线程 1 以为还是 NodeB，实际是 NodeC）
→ NodeC 和 NodeB 全部丢失，链表断裂
```

**栈顶引用没变，但栈的结构被换了**——CAS 只认引用，不认结构。

> [!warning] 上面的推演有一处自相矛盾（第 60-61 行），更正如下
> 线程 1 在 ①「读栈顶 = NodeA」时就已把 `newTop = NodeA.next = NodeB` **记到本地**了（经典 pop 写法：`newTop = oldTop.next` 在 CAS 之前就取好）。所以 CAS 更新用的**不是**"现在的 `NodeA.next = NodeC`"，而是**旧值 NodeB**。
>
> 正确推演：线程 1 CAS(栈顶, NodeA, **NodeB**) → 栈顶此刻是 NodeA（引用没变）→ CAS 成功，栈顶被设成 NodeB → 结果 `栈顶 → NodeB → null`。
>
> **真正丢失的是 NodeC**（A→C→B 整条被跳过），NodeA 也没被正确弹出（游离）。**NodeB 不是"丢失"，而是被错误地顶回了栈顶**——它本应已不在栈里。
>
> 一句话本质：CAS 只保证「栈顶引用没变」，不保证「栈顶下面的链没被换掉」。线程 1 用的是旧 next，栈却被线程 2 换成 A→C→B，于是 C 被跳过丢失。

### 类比

> 你看到家门口停着一辆白色卡罗拉，转身去拿钥匙，回来白色卡罗拉还在，你开门上车——但这不是你的车，是别人刚开过来的同款。你的车刚才被开走了又开回来一台同款。

---

## 解决方案：AtomicStampedReference

### 原理

```
AtomicStampedReference<V>(initialRef, initialStamp)
```

内部维护一个 `Pair<reference, stamp>`，CAS 时**同时比较引用和版本号**，不能只比引用。

```java
// 伪代码示意
AtomicStampedReference<Node> top = new AtomicStampedReference<>(head, 0);

// pop 时
int[] stampHolder = new int[1];
Node oldTop = top.get(stampHolder);
int oldStamp = stampHolder[0];
Node newTop = oldTop.next;

// CAS 同时比较引用 + stamp
top.compareAndSet(oldTop, newTop, oldStamp, oldStamp + 1);
```

### 关键点（面试话术）

- 每次更新 `stamp + 1`，stamp 是单调递增的
- stamp 变了 = 中间被改过 → CAS 失败，重试
- 跟**乐观锁的 version 字段**是一个道理
- 缺陷：`AtomicStampedReference` 维护的是 `Pair<V, Integer>` 对象，每次 CAS 会创建新对象，**内存开销比普通 Atomic 大**，高频场景要考虑 GC 压力

### 替代方案

| 方案 | 原理 | 适用场景 |
|------|------|---------|
| **AtomicStampedReference** | 引用 + int stamp，精确计数 | 需要精确记录修改次数 |
| **AtomicMarkableReference** | 引用 + boolean mark，只标记"有没有被改过" | 只需要知道"被碰过没有"（如 A 被改过就行，不管改了几次） |
| **加锁** | 用 synchronized/ReentrantLock 包住 CAS | 对性能不敏感，但简单可靠 |

---

## 面试话术

> ABA 最经典的场景就是无锁栈。假如线程 1 要 pop 栈顶 NodeA，这时候线程 2 把 A pop 了，又 push 了 C 和 B，最后把 A 又 push 回来。线程 1 醒来 CAS 一看栈顶还是 NodeA，以为没变过，就成功地把栈顶设成了 A 原来的 next——但那个 next 已经不是 NodeB 了，链表结构被破坏，后面的节点全部丢失。
>
> 解决就是用 **AtomicStampedReference**，加一个版本号 stamp，每次更新 stamp+1，CAS 同时比较引用和版本号。stamp 和引用对不上，就说明中间被改过，CAS 失败，重新读。

---

## 关联知识点

- [[Java并发编程面试题]] — Java 并发八股总览
- [[Java并发编程热门面试题]] — 并发卷（Q11 CAS 与 ABA）

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-24 12:59 | 2026-08-24 13:06 | CAS 只比较值不比较“是否变过”，典型陷阱是无锁栈中线程 2 将栈顶 A→B→A 后线程 1 用旧 next 执行 CAS 导致节点丢失；可用 AtomicStampedReference 加版本号同时校验引用与 stamp 解决。 | 第一轮背记完成 |
