---
tags:
  - jvm
  - java
  - 内存溢出
  - 虚拟机栈
created: 2026-06-22
updated: 2026-06-22
---
# 栈溢出导致 OOM 的深层机制

## 一个"误导性"的合并条目

在 [[jvm八股]] 的内存溢出（OOM）常见原因中有一条：

> - 递归调用：深度递归导致栈溢出。

这看起来很奇怪——`StackOverflowError` 和 `OutOfMemoryError` 明明是两个不同的 Error，为什么把"栈溢出"归类到"OOM 原因"下？

**答案：递归虽直接抛 SOF，但可以通过多条路径间接触发 OOM。** 下面拆解这些路径。

---

## 先明确：StackOverflowError 本身不是 OOM

| Error | 资源范围 | 含义 |
|---|---|---|
| `StackOverflowError` | **单线程**栈空间耗尽 | "我这条线程的栈不够用了" |
| `OutOfMemoryError` | **JVM 进程**内存耗尽 | "整个进程都没内存了" |

SOF 是"纵向"超限——一个线程的栈帧太多，超出了 `-Xss` 设定的深度上限。  
OOM 是"横向"枯竭——整个进程再也拿不到更多内存。

但正是这种"纵向"问题在特定条件下会引发"横向"枯竭。

---

## 路径一：多线程并发递归 → 栈总和撑爆进程内存

### 原理

**每个线程的栈都是独立的，从操作系统堆外内存（Native Memory）中分配，不受 `-Xmx` 控制。**

```
系统物理内存：8 GB
堆（-Xmx）：               4 GB
元空间 / Code Cache：      ~500 MB
操作系统及其他：           ~1.5 GB
───────────────────────────────────
留给线程栈的可用空间：     ~2 GB

单线程栈（-Xss = 1 MB）：  约可创建 2048 个线程
```

当**大量线程同时执行深度递归**时（比如 Web 服务器的每个请求线程都调用了一个递归方法），所有线程的栈空间总和可能逼近进程内存上限：

```java
// 伪代码：Tomcat 线程池里的每个请求线程都走这个递归
@GetMapping("/recursive")
public String handleRequest() {
    return deepRecursion(100000); // 每个线程都递归 10 万层
}
```

- 假设 200 个 Tomcat 线程同时走到这段递归
- 每个线程栈 1MB × 各递归到 90% 深度 → 栈已分配 180 MB
- 如果同时 GC 压力大、堆扩容抢占内存，整体内存不足 → **`OutOfMemoryError: unable to create new native thread`**（或 `OperatingSystem` OOM）

> 虽然每个线程的单点错误是 SOF，但**多个 SOF 线程汇聚的效果**和"线程太多导致 OOM"是一样的——都是进程级的内存耗尽。

### 真实案例

```
Tomcat 默认 max-threads = 200
接口递归无终止条件
→ 200 个线程同时递归到几万层
→ 每个线程栈填满 1MB
→ 进程内存被栈占满
→ 新的请求无法创建线程 → OOM
```

---

## 路径二：捕获 SOF 后继续递归 → Error 对象撑爆堆

### 原理

`StackOverflowError` 被抛出时，JVM 会构造一个**带有完整调用栈信息**的 Error 对象：

```java
public void dangerous() {
    try {
        dangerous();      // 深度 10000 层时抛 SOF
    } catch (StackOverflowError e) {
        // ❌ 错误做法：捕获后不终止，继续递归
    }
    dangerous();          // 又递归，又抛 SOF，又捕获……
}
```

- 每次 `new StackOverflowError()` 会评估整个调用栈，生成 StackTraceElement 数组
- 深度 10000 层的 SOF，StackTraceElement 数组长度 ≈ 10000
- 每个 StackTraceElement 包含 className、methodName、fileName、lineNumber 等字符串
- **一个 SOF 对象 ≈ 几十 KB ~ 几百 KB 的堆内存**

```java
// 循环中反复抛 SOF（深度 10000 层）
// 每次抛一个新 Error → 每次在堆里分配~300KB
// 1000 次 → 300MB 堆被 Error 对象填满 → OOM: Java heap space
```

### 这不是理论——Netty 曾经踩过这个坑

Netty 3.x 的某些版本中，`MemoryAwareThreadPoolExecutor` 在任务执行超时后会向执行线程发送中断信号，如果线程恰好在递归中，捕获 `StackOverflowError` 后未正确退出，会导致反复重建 Error 对象，最终 OOM。

> [!tip] 最佳实践
> 不要捕获 `StackOverflowError` 后不终止——要么让它传播出去，要么 catch 后立刻 return/throw。否则 SOF 会变成堆 OOM 的放大器。

---

## 路径三：递归调用中已分配大量堆对象 → 连锁 OOM

深度递归在每个栈帧中往往持有局部变量引用：

```java
public void process(List<Data> results, int depth) {
    List<Data> batch = loadBatch(depth); // 每层都分配堆对象
    results.addAll(batch);               // 全局引用，不可回收
    process(results, depth + 1);
}
```

- 递归到 5000 层时，`loadBatch()` 已经分配了 5000 个 List 对象
- 这些对象被 `results` 链（或各栈帧的局部变量）强引用
- **GC 无法回收**，因为每一帧都还在栈上活着
- 还没等到 SOF，堆先满了 → **`OutOfMemoryError: Java heap space`**

> 这种情况，SOF 和 OOM 同时在竞争"谁先触发"。`-Xss` 越大（栈越深）、`-Xmx` 越小（堆越小），OOM 越容易先到。

---

## 路径四：栈上分配（Stack Allocation）逃生失败

JVM 的逃逸分析（Escape Analysis）会将未逃逸的对象分配在**栈上**而非堆上。深度递归时：

- 如果递归方法开启了逃逸分析（JDK 8 默认开启）
- 大量本该栈上分配的对象因栈帧太深、栈空间不足而**被迫逃逸到堆**
- 堆压力激增 → GC 频繁 → 甚至 OOM

> 这条路径比较隐晦，属于 JIT 编译优化导致的间接影响。

---

## 总结：SOF → OOM 的路径地图

```
深度递归
   │
   ├─→ 单线程栈满 → StackOverflowError（直接结果）
   │
   ├─→ 多线程同时递归 → 栈总和占满进程内存 → OOM: unable to create new native thread
   │
   ├─→ 捕获 SOF 后未终止 → Error 对象大量分配 → OOM: Java heap space
   │
   ├─→ 每帧持有堆对象引用 → 对象无法回收 → OOM: Java heap space
   │
   └─→ 逃逸分析失效 → 对象逃逸到堆 → 堆压力 → OOM
```

---

## 面试话术

> **问：StackOverflowError 和 OutOfMemoryError 是什么关系？栈溢出会导致 OOM 吗？**
>
> 答：两者是不同层面的 Error——SOF 是单线程栈深度超限，OOM 是进程级内存耗尽。但 SOF 确实可以通过几条路径间接引发 OOM。
>
> 最常见的是**多条路径并发递归**：比如 Tomcat 所有请求线程同时调一个无限递归的接口，每个线程的栈都在吞噬进程的堆外内存（Native Memory），栈总和可能撑爆进程，表现为 `unable to create new native thread` 的 OOM。
>
> 还有一个隐蔽的路径：**捕获 SOF 后不终止**。`StackOverflowError` 携带完整的调用栈信息，一个深度 10000 层的 Error 对象可能占几百 KB 堆内存。在循环中反复 catch 再递归，堆很快被 Error 对象填满，引发 `Java heap space` 的 OOM。所以处理递归的兜底策略应该是**让 SOF 传播到顶层**，而不是捕获后继续。

---

## 关联知识点

- [[jvm八股]] — JVM 八股总览
- [[StackOverflowError与OutOfMemoryError（虚拟机栈）]] — SOF 与线程 OOM 的基础对比
- [[静态集合内存泄漏与类卸载详解]] — 另一类典型的内存泄漏场景
- [[DirectByteBuffer与堆外内存回收]] — 堆外内存的 OOM 场景
