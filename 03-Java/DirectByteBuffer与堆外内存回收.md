---
tags:
  - java
  - jvm
  - NIO
  - 堆外内存
  - DirectByteBuffer
  - GC
created: 2026-06-10
updated: 2026-10-08
---

# DirectByteBuffer 回收：为什么堆外内存不足会触发 Full GC？

## 问题

> 堆外内存（Direct Memory）是 JVM 堆之外的内存，Full GC 只回收堆内的对象——那堆外内存不够了关 Full GC 什么事？

**关键不在堆外内存本身，而在管理堆外内存的那个 Java 对象在堆里。**

---

## DirectByteBuffer 的结构

`DirectByteBuffer` 分配堆外内存时，内部是这样的：

```
Java 堆（Heap）
┌──────────────────────────────┐
│  DirectByteBuffer 对象        │  ← 很小的 Java 对象（≈ 100 字节）
│  ├─ address                  │  ← 指向堆外内存的地址（long）
│  ├─ capacity                 │
│  └─ cleaner                  │  ← Cleaner 对象（附属于 DirectByteBuffer）
└──────────┬───────────────────┘
           │ 引用
           ▼
堆外内存（Direct Memory / 直接内存）
┌──────────────────────────────┐
│  实际存放数据的内存块          │  ← 真正占大头的地方
│  （如文件读写缓冲区）           │     比如分配 1GB 的 DirectByteBuffer，
│                               │     堆里就一个对象，堆外占 1GB
└──────────────────────────────┘
```

**核心事实**：堆外内存本身 JVM 的 GC 管不了，但**什么时候释放它，是由堆里的 `DirectByteBuffer` 对象是否被回收决定的**。

---

## 回收流程

```
DirectByteBuffer 不再被引用
    ↓
下一次 GC 时，DirectByteBuffer 对象被标记为可回收
    ↓
DirectByteBuffer.finalize() / Cleaner 被调用
    ↓
Cleaner 里调用的 Deallocator 执行 Unsafe.freeMemory(address)
    ↓
堆外内存块被释放
```

**关键链条**：回收堆外内存的前提是——堆里的 `DirectByteBuffer` 对象先被 GC 干掉。

---

## 为什么堆外内存不足会触发 Full GC？

当代码尝试分配新的 `DirectByteBuffer` 但堆外内存不够时，`Bits.reserveMemory()` 方法会执行以下逻辑（简化版）：

```
Bits.reserveMemory(capacity) 被调用
    ↓
1. 检查当前已分配的堆外内存总量
   ↓ 已经超过 -XX:MaxDirectMemorySize 的限制
2. 调用 System.gc() ！（关键！）
    ↓
3. System.gc() 触发 Full GC
    ↓
4. Full GC 尝试回收不可达的 DirectByteBuffer 对象
    ↓
5. 回收成功 → Cleaner 运行 → 释放对应的堆外内存
    ↓
6. 再次尝试分配 → 这次可能就够了
```

**所以逻辑链条是：**

```
堆外内存不足
    → JVM 主动调 System.gc() 暗示 GC
    → Full GC 回收不可达的 DirectByteBuffer（堆内对象）
    → DirectByteBuffer 的 Cleaner 释放对应的堆外内存（堆外）
    → 堆外内存有了空闲空间
    → 新的分配请求成功
```

**Full GC 干的不是"回收堆外内存"，而是"回收堆里持有堆外内存引用的 Java 对象，顺带触发堆外内存的释放"。**

---

## 实际代码中的体现

```java
// Bits.reserveMemory() 的简化逻辑（java.nio.Bits 类）
static boolean reserveMemory(long size, int cap) {
    // ...
    while (true) {
        if (tryReserveMemory(size, cap)) {
            return true;    // 堆外内存够用 → 直接分配
        }
        
        // 堆外内存不够了！
        if (sleeps >= MAX_SLEEPS) {
            break;          // 重试次数用完 → 抛 OOM
        }
        
        // 关键：触发 GC，期望回收掉一些 DirectByteBuffer
        System.gc();        
        sleep(100);         // 等 GC 执行
        sleeps++;
    }
    
    throw new OutOfMemoryError("Direct buffer memory");
}
```

---

## 面试高频关联：`-XX:+DisableExplicitGC` 的副作用

这就是为什么笔记第 221 行提到这个参数有副作用：

```
-XX:+DisableExplicitGC 禁用了 System.gc()
    ↓
DirectByteBuffer 分配失败时调用的 System.gc() 被忽略
    ↓
堆外内存回收的"最后手段"失效了
    ↓
堆外内存可能泄漏，最终 Direct OOM
```

解决方案：
- 不用 `DisableExplicitGC`，或者用 `-XX:+ExplicitGCInvokesConcurrent` 把 Full GC 改成并发 GC 减少停顿
- 手动管理 `DirectByteBuffer`，用完后显式调用 `cleaner.clean()`

---

## 一句话总结

> 堆外内存不是 GC 直接管的，**但释放堆外内存的钥匙——`DirectByteBuffer` 对象——在堆里**。堆外内存不足时，JVM 通过 `System.gc()` 触发 Full GC，先把堆里不可达的 `DirectByteBuffer` 回收掉，间接释放它们对应的堆外内存块。

---

## 面试话术

> **问：堆外内存不足为什么会触发 Full GC？Full GC 又不会回收堆外内存。**
>
> 答：这是 NIO 设计上的巧妙之处。`DirectByteBuffer` 分配堆外内存时，内部有一个 `Cleaner` 对象，当 `DirectByteBuffer` 本身被 GC 回收时，`Cleaner` 会释放对应的堆外内存。所以**回收堆外内存的前提是堆内的 `DirectByteBuffer` 对象先被 GC 回收**。当分配新的 `DirectByteBuffer` 发现堆外内存不足时，`Bits.reserveMemory()` 方法会主动调用 `System.gc()` 触发 Full GC，期望通过回收不可达的 `DirectByteBuffer` 对象来间接释放堆外内存。这也是为什么 `-XX:+DisableExplicitGC` 有副作用——它禁掉了这个"最后回收手段"，可能导致堆外 OOM。

---

## 补充：堆外内存 vs native memory（本机内存）

面试里"堆外内存"和"native memory"经常被混着说，其实前者是后者的**真子集**：

| 口径 | 范围 | 谁统计 |
|------|------|--------|
| **堆外内存 / 直接内存（Direct Memory）** | 仅 NIO `DirectByteBuffer` 经 `Unsafe.allocateMemory` 申请的那部分，受 `-XX:MaxDirectMemorySize` 约束 | `java.nio.Bits` 里的计数器 |
| **native memory（本机内存）** | JVM 进程向**操作系统**申请的全部非堆内存，是 Direct Memory 的**超集** | NMT（Native Memory Tracking） |

native memory 除了 Direct Memory，还包括：**线程栈**（每个 `-Xss`）、**JIT 生成的机器码**（Code Cache）、**类元数据**（Metaspace）、**GC 自身数据结构**（如 G1 的 RSet / 记忆集）、**JVM 内部结构**等。所以 `MaxDirectMemorySize` 只管住 Direct Memory 这一小块，管不住线程栈、Metaspace 那些。

**怎么看这两块？**

- 看**堆**（不是堆外）：`jcmd <pid> GC.heap_info`（旧版 `jmap -heap`），看新生代 / 老年代使用量。
- 看 **native memory 明细**：`jcmd <pid> VM.native_memory summary`。
  - 它的输出里确实有 `Java Heap` 一项——但**看堆不该用这条命令**。`VM.native_memory` 的立意是"JVM **进程**视角的各块内存"，把堆也一并列出来只是为了完整，不是看堆的主命令。
  - 且 **NMT 默认关闭**，必须在**启动时**加 `-XX:NativeMemoryTracking=summary`（或 `detail`）才有输出；进程已经跑起来再 `jcmd` 也拿不到。

> 一句话：**堆外内存 ⊂ native memory。`MaxDirectMemorySize` 只框住 DirectByteBuffer 那一块，native memory 还包含线程栈、JIT、元数据、GC 结构等；要看 native 全貌用 NMT（需启动时开启），要看堆用 `GC.heap_info`。**

---

## 关联知识点

- [[jvm_review]] — JVM 模拟面试批改（第 6 题 / 第 7 题 Full GC 触发条件）
- [[jvm八股]] — JVM 八股总览（GC 部分）
