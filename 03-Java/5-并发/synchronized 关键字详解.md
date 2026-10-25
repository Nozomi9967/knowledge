---
tags:
  - java
  - 并发
  - synchronized
  - 锁
created: 2026-06-17
updated: 2026-06-17
source: [[Java并发编程面试题]]
---

# synchronized 关键字详解

> [[Java并发编程面试题]] 中仅给出特性提纲，本页展开 synchronized 的**可重入性、锁升级、Monitor 对象、Mark Word** 等核心机制的详细解释。
>
> 反向引用：[[Java并发编程面试题]] · [[ReentrantLock详解]] · [[AQS 详解]] · [[线程池]]

---

## 一、synchronized 是什么

`synchronized` 是 Java 的**关键字级互斥锁**，JVM 层面通过 `monitorenter` / `monitorexit` 字节码实现，**自动加锁、自动释放**（方法结束或异常退出）。

**三种使用形式：**

| 形式 | 锁对象 | 作用范围 |
|------|--------|----------|
| `synchronized` 修饰实例方法 | 当前实例 `this` | 同一实例的同步方法互斥 |
| `synchronized` 修饰静态方法 | 当前类的 `Class<?>` 对象 | 所有实例的该静态同步方法互斥 |
| `synchronized(对象) { ... }` 代码块 | 括号内的对象 | 灵活指定锁对象 |

> 反例：以为 `synchronized 普通方法` 能锁住"该类的所有实例"——错。多实例下每个实例一把锁，仍会超卖。应改用 `synchronized static` 或 `AtomicInteger`。

---

## 二、可重入性（Reentrancy）

> 完整讲解已拆分
> 本节内容已拆出独立详解文件：[[可重入性详解]]。本节保留**精简总结 + 三阶段实现位置**对照表；更深入的因果链分析、典型触发场景（子方法/递归/多态/回调）、计数器方案、synchronized vs ReentrantLock 可重入对比、面试话术请看 [[可重入性详解]]。

**定义**：同一线程对同一把锁可以多次 `monitorenter` 而不会死锁。

**为什么需要**：避免"递归调用 / 子方法调用"时自己锁自己——**同一线程再次申请已被自己持有的锁时不被挂起**，否则递归/子方法调用会自己等自己，永远释放不了锁，直接死锁。

### 2.1 不同锁阶段的可重入实现

| 锁阶段 | 重入计数存哪 | 机制 |
|--------|-------------|------|
| **偏向锁** | Mark Word 里存的是 ThreadID，**无需计数器**（同一线程直接放行） | 线程 ID 一致就放行 |
| **轻量级锁** | 线程栈上 **LockRecord 链**（每次重入压入一个新 LockRecord） | 每次重入压一个 LockRecord，displaced mark word 为空表示是重入 |
| **重量级锁** | ObjectMonitor 的 **`_count` 字段** | 每次 `monitorenter` +1，`monitorexit` -1；减到 0 才真正释放 |

**面试话术**：synchronized 任何阶段都是可重入的，区别只在于"重入计数存在哪"——轻量级靠 LockRecord 链，重量级靠 ObjectMonitor._count。

---

## 三、底层实现三层面

### 3.1 字节码层面

- **进入同步块**：插入 `monitorenter` 指令
- **退出同步块**：插入 `monitorexit` 指令（正常退出和异常退出各一次）
- **同步方法**：在方法访问标志位加 `ACC_SYNCHRONIZED`，由 JVM 在方法调用时隐式执行 monitorenter/exit

### 3.2 对象头层面（Mark Word）

Mark Word 是对象头的核心字段，根据锁状态存储不同内容：

| 状态 | Mark Word 内容 | 标志位 |
|------|---------------|--------|
| **无锁** | hashCode、GC age | 01 + 0 |
| **偏向锁** | 持有线程的 ThreadID | 01 + 1 |
| **轻量级锁** | 指向线程栈上 LockRecord 的指针 | 00 |
| **重量级锁** | 指向 ObjectMonitor（Monitor）对象的指针 | 10 |

### 3.3 操作系统层面（重量级锁阶段）

重量级锁阶段，Mark Word 指向 JVM 内部的 `ObjectMonitor` 对象（C++ 实现），核心字段：

- **`_owner`**：指向持有锁的线程
- **`_entryList`**：等待锁的线程队列（被 `monitorenter` 阻塞的线程）
- **`_waitSet`**：调用了 `wait()` 的线程队列
- **`_count`**：重入计数器

`_owner` 通过 CAS 从 NULL 抢到当前线程引用 → 抢锁成功；释放时从引用改回 NULL → 释放。真正阻塞/唤醒线程依赖底层 `pthread_mutex`（内核态），所以叫"重量级"。

---

## 四、锁升级过程

synchronized 锁升级路径：**无锁 → 偏向锁 → 轻量级锁 → 重量级锁**，**单向不可降级**。

| 状态 | 核心机制 | 适用场景 |
|------|----------|----------|
| **无锁** | 无同步 | 对象刚创建 |
| **偏向锁** | CAS 记录 ThreadID | 同一线程反复进入（无竞争） |
| **轻量级锁** | CAS + 自旋 | 多线程交替执行（短时竞争） |
| **重量级锁** | 阻塞 + 唤醒 | 多线程真正同时竞争 |

**升级时机**：
1. 第一个线程进入 → 升级为**偏向锁**（CAS 记录 ThreadID）
2. 第二个线程尝试进入 → **撤销偏向锁**（需到 SafePoint）→ 升级为**轻量级锁**
3. 轻量级锁 CAS 失败 → **自旋重试**（JDK 1.6+ 自适应自旋）
4. 自旋达到阈值仍失败 → **膨胀为重量级锁**

**核心思想**：用"空间换复杂度"——JVM 在对象头里维护锁状态，根据实际竞争激烈程度选择最轻的同步方式。

---

## 五、JVM 对 synchronized 的四种优化

| 优化 | 触发条件 | 效果 | 是否需要参数 |
|------|----------|------|--------------|
| **锁升级** | 竞争激烈程度变化 | 从无锁→偏向→轻量级→重量级，逐级加重 | 默认开启 |
| **锁消除** | JIT 通过**逃逸分析**发现对象只在单线程内使用 | 消除整个 synchronized 块 | `-XX:+DoEscapeAnalysis` + `-XX:+EliminateLocks`（JDK 1.6+ 默认开） |
| **锁粗化** | 同一对象在循环或连续操作中反复加锁解锁 | 把多个锁合并为一个 | 默认开启（JIT 优化） |
| **自适应自旋** | 轻量级锁 CAS 失败时 | 根据上次自旋结果动态调整本次自旋次数（JDK 1.6+） | 默认开启 |

**经典例子**：
- **锁消除**：方法内的 `StringBuffer.append()`（局部对象，逃逸不出去）
- **锁粗化**：`for(int i=0;i<1000;i++) synchronized(list) { list.add(...); }` 粗化为 `synchronized(list) { for(...) list.add(...); }`
- **自适应自旋**：上次自旋成功 → 这次自旋更久；上次没成功 → 这次可能直接放弃自旋升级重量级

> **锁只能升级不能降级**——保证一致性，避免降级过程中的复杂同步问题。

---

## 六、JDK 15 之后偏向锁的废弃

**废弃原因（JEP 374）**：
1. **JVM 实现复杂度过高**：偏向锁的"撤销"需要在 SafePoint 暂停线程，开销不小；现代应用很少有"同一线程反复进入同步块"的纯单线程场景
2. **现代应用场景变了**：Lambda、Stream、虚拟线程（Java 21+）等场景下，几乎没有线程能反复独占同一把锁，偏向锁的优化收益微乎其微
3. **维护成本不划算**：撤销逻辑复杂、容易引入新 bug，HotSpot 团队希望简化锁机制

**废弃后的实际路径**：
- **JDK 15**：偏向锁默认禁用（`-XX:+UseBiasedLocking` 仍然可用但 deprecated）
- **JDK 18**：偏向锁的 HotSpot 实现被 obsolete
- **JDK 21**：`UseBiasedLocking` 命令行选项 deprecated

**新路径**：**无锁 → 轻量级锁 → 重量级锁**（去掉了"偏向锁"中间态）。

> **面试建议**：在回答锁升级时主动补充这个变化，**会加分**——面试官能看出你跟得上 JDK 新动向。

---

## 七、与 ReentrantLock 的核心区别

| 维度 | synchronized | ReentrantLock |
|------|--------------|---------------|
| **用法** | 关键字，JVM 自动加锁/释放锁（monitorenter/exit） | 类，需手动 `lock()` / `unlock()`，通常配合 try-finally |
| **公平性** | ❌ 非公平 | ✅ 可选公平/非公平（`new ReentrantLock(true)`） |
| **可中断** | ❌ 不可中断 | ✅ `lockInterruptibly()` |
| **超时** | ❌ | ✅ `tryLock(timeout, unit)` |
| **多条件变量** | ❌ 只有一个（`wait`/`notify`） | ✅ 一个锁可绑多个 `Condition` |
| **实现** | JVM 层面，ObjectMonitor + mutex | JDK 层面，基于 AQS（CAS + CLH 队列） |
| **性能** | JDK 1.6+ 锁升级优化后接近 ReentrantLock | 高竞争时仍略优 |

**选型建议**：
- 简单同步 + 无高级需求 → **synchronized**（简洁、不易出错）
- 需要公平锁 / 可中断 / 超时 / 多条件变量 → **ReentrantLock**
- 注意：**tryLock + unlock 必须 try-finally 配对**，否则会死锁或泄漏

---

## 八、面试话术模板

> "synchronized 是 JVM 提供的关键字级互斥锁，底层分三层：字节码层用 monitorenter/monitorexit，对象头层用 Mark Word 记录锁状态，操作系统层用 ObjectMonitor + pthread_mutex 实现阻塞唤醒。具备可重入性——同一线程重复加锁通过计数器 +1 实现，减到 0 才真正释放。JDK 1.6 之后做了四大优化：锁升级（无锁→偏向→轻量级→重量级）、锁消除、锁粗化、自适应自旋，性能已接近 ReentrantLock。JDK 15 之后偏向锁默认废弃，升级路径变成无锁→轻量级→重量级三段式。如果需要可中断、可超时、公平锁或多条件变量这些高级特性，再考虑 ReentrantLock。"

---

## 关联知识点

- [[Java并发编程面试题]] — 主文件，本详解由其"可重入 / 锁升级 / synchronized vs ReentrantLock"段展开
- [[可重入性详解]] — synchronized 可重入性的独立详解（因果链、典型场景、计数器方案、三阶段实现、与 ReentrantLock 对比）
- [[ReentrantLock详解]] — 显式锁，与 synchronized 对比的核心
- [[AQS 详解]] — ReentrantLock 底层框架
- [[线程池]] — 线程池的 Worker 也基于 AQS 独占模式
- [[Condition 详解]] — ReentrantLock 多条件变量机制
