---
tags:
  - Java
  - 集合框架
  - Deque
  - 栈
  - 队列
created: 2026-08-28
updated: 2026-09-02
source: 用户知识点总结
---

# Java Deque 方法速记 — 栈风格 vs 队列风格

`Deque` 的方法看似多，其实就 **两组**：**栈风格**和**队列风格**。核心是把 `Deque` 想象成"双端队列"，记住操作发生在哪一端即可。

```java
Deque<Integer> deque = new ArrayDeque<>();
```

---

## 1. `push / pop / peek` — 把 Deque 当栈用

这三个操作的都是 **队头（head）**。

```
        队头                         队尾
          ↓                           ↓
       [ 3 ][ 2 ][ 1 ]
        ↑
      push/pop/peek
```

| 操作 | 效果 | 等价于 |
|------|------|--------|
| `push(x)` | 从队头入栈 | `addFirst(x)` |
| `pop()` | 从队头出栈（删除并返回） | `removeFirst()` |
| `peek()` | 看栈顶（不删除） | `peekFirst()` |

```java
deque.push(1); deque.push(2); deque.push(3);  // [3, 2, 1]
int x = deque.pop();    // x = 3, 剩下 [2, 1]
int y = deque.peek();   // y = 2, 还是 [2, 1]
```

---

## 2. `offer / poll / peek` — 把 Deque 当队列用

```
入队 → 队尾
出队 → 队头

       队头              队尾
         ↓                 ↓
       [1][2][3][4]
        ↑                 ↑
       poll              offer
```

| 操作 | 效果 | 等价于 |
|------|------|--------|
| `offer(x)` | 从队尾入队 | `addLast(x)` |
| `poll()` | 从队头出队（删除并返回） | `removeFirst()` |
| `peek()` | 看队头（不删除） | `peekFirst()` |

```java
deque.offer(1); deque.offer(2); deque.offer(3);  // [1, 2, 3]
int x = deque.poll();    // x = 1, 剩下 [2, 3]
int y = deque.peek();    // y = 2, 还是 [2, 3]
```

---

## 3. 关键区别 — 空的时候怎么办？

> 这才是面试最容易考的地方。

| 操作         | 正常情况     | Deque 为空时                          |
| ---------- | -------- | ---------------------------------- |
| `push(x)`  | 加入       | **抛异常** (`IllegalStateException`)  |
| `pop()`    | 删除并返回队头  | **抛异常** (`NoSuchElementException`) |
| `offer(x)` | 加入       | 返回 `false`（容量受限时）                  |
| `poll()`   | 删除并返回队头  | 返回 `null`                          |
| `peek()`   | 返回队头，不删除 | 返回 `null`                          |

**记忆口诀：** `push/pop` 是栈风格，空时直接抛异常；`offer/poll` 是队列风格，空时返回 `null`/`false`。

> [!warning] 上表第三列的标题有误：`push` / `offer` 的失败跟「空」没有关系
> `push(x)` 和 `offer(x)` 都是**插入**操作。往一个空的 Deque 里插入元素是最正常的用法，不可能因为「空」而失败——恰恰相反，它们失败的触发条件是**容量已满**（或插入 `null` 而该实现不允许）。上表把这两行塞进「Deque 为空时」这一列，是把两种完全不同的失败原因压到了同一个轴上。下面把两个轴拆开。

### 3.1 修正版 — 两个轴要分开看

**轴一：什么情况算失败，由操作类型决定。**

| 操作类型 | 失败触发条件 | 备注 |
|---|---|---|
| 插入（`push` / `offer` / `add`） | **容量已满**，或元素为 `null` 而实现禁止 | `ArrayDeque` / `LinkedList` 无界，正常插不失败 |
| 删除（`pop` / `poll`） | **Deque 为空** | 刷题真正的坑在这里 |
| 查看（`peek` / `getFirst`） | **Deque 为空** | 同上 |

**轴二：失败之后什么反应，Deque 的每个语义都提供两派方法。**

| 语义 | 抛异常派 | 返回特殊值派 |
|---|---|---|
| 头部插入 | `addFirst(x)` / **`push(x)`** → 满时 `IllegalStateException` | `offerFirst(x)` → 满时 `false` |
| 尾部插入 | `addLast(x)` → 满时 `IllegalStateException` | `offerLast(x)` / **`offer(x)`** → 满时 `false` |
| 头部删除 | `removeFirst()` / **`pop()`** → 空时 `NoSuchElementException` | `pollFirst()` / **`poll()`** → 空时 `null` |
| 头部查看 | `getFirst()` / `element()` → 空时 `NoSuchElementException` | `peekFirst()` / **`peek()`** → 空时 `null` |

`push`/`pop` 落在抛异常派，`offer`/`poll`/`peek` 落在返回特殊值派——所以上面口诀里「栈风格抛异常、队列风格返回 null/false」这半句是对的，错的只是把触发条件一律写成「空」。另外 `peek()` 也有它的抛异常搭档 `getFirst()`/`element()`，原表没列。

**刷题时真正会踩的坑：**

- `ArrayDeque` / `LinkedList` 都是**无界**的，所以 `push` 的 `IllegalStateException` 实际上根本跑不出来。真正有界、会抛 `IllegalStateException("Deque full")` 的是指定了容量的 `LinkedBlockingDeque`，见 [[阻塞队列详解]]。
- 无界时 `offer` 恒返回 `true`。所以在刷题场景下，`push` 和 `offer` 的区别只在**插入哪一端**，不在失败行为。
- 无界 `ArrayDeque` 上 `push` 唯一会抛的是 `push(null)` → `NullPointerException`（`ArrayDeque` 禁止 null 元素）。
- **唯一高频踩坑点是 `pop()` 空栈抛 `NoSuchElementException`，而 `poll()` 只返回 `null`。** 写单调栈、BFS 时要么先 `while (!deque.isEmpty())` 判空再 `pop()`，要么统一用 `poll()` 配 null 判断，别混着写。
- 一个连带细节：`ArrayDeque` 禁止 null 元素，正是为了让 `poll()`/`peek()` 返回 `null` 能唯一地表示「空」；而 `LinkedList` 允许存 null，它的 `poll()` 返回 null 就有「队列空」和「取到一个 null 元素」两种解释，判空只能靠 `isEmpty()`。


---

## 4. 刷题速记

| 用途 | 写法 | 口诀 |
|------|------|------|
| **当栈** | `push` → `pop` → `peek` | 从头进，从头出 |
| **当队列** | `offer` → `poll` → `peek` | 从尾进，从头出 |

```java
// 栈
Deque<Integer> stack = new ArrayDeque<>();
stack.push(x);
stack.pop();
stack.peek();

// 队列
Deque<Integer> queue = new ArrayDeque<>();
queue.offer(x);
queue.poll();
queue.peek();
```

---

## 5. 为什么 `peek` 两边都能用？

`peek()` 本质就是 `peekFirst()`——无论是栈还是队列，需要的都是"看队头/栈顶但不删除"，所以 `peek` 是通用的。

**一句话总结：**

> **栈：push / pop / peek（都操作队头）**
> **队列：offer / poll / peek（offer 从队尾进，poll / peek 从队头出）**
> **`push` 从头进，`offer` 从尾进；`pop` 和 `poll` 都从头出。**

---

## 关联知识点

- [[阻塞队列详解]] — BlockingQueue 的 put/take 与 offer/poll 超时重载
- [[Java基础面试题]] — 集合框架常见面试点
- [[线程池]] — 线程池中的任务队列（也是 BlockingQueue）