## List vs Stream 消息队列关键区别

### 核心差异：消息分发机制

假设有 3 个消费者（worker）都在等任务：

### List 的方式（BRPOP）

```
生产者：LPUSH queue task1 task2 task3 task4 task5 task6

消费者1：BRPOP queue 0  → 抢到 task1
消费者2：BRPOP queue 0  → 抢到 task2
消费者3：BRPOP queue 0  → 抢到 task3
消费者1：BRPOP queue 0  → 抢到 task4
消费者2：BRPOP queue 0  → 抢到 task5
消费者3：BRPOP queue 0  → 抢到 task6
```

**特点：** 多个消费者用 BRPOP 去"抢"消息，谁抢到算谁的。这是**竞争消费**，每个消费者没有身份，消息被谁消费了也不知道。

### Stream 的消费者组方式

```
生产者：XADD mystream * task "work1"
         XADD mystream * task "work2"
         XADD mystream * task "work3"

消费者组：mygroup（有 consumer-A、consumer-B、consumer-C）
          
consumer-A → task1   （组内分配，不广播）
consumer-B → task2
consumer-C → task3
consumer-A → task4   （轮转分配）
consumer-B → task5
consumer-C → task6
```

**特点：** 消费者组里每条消息**只被组内一个消费者处理**，不会重复消费。Stream 内部维护了分发状态，知道谁处理了哪条消息。

---

### 对比总结

| | List（BRPOP） | Stream 消费者组 |
|---|---|---|
| **分发机制** | 多个消费者"抢"消息 | 组内**自动分配**，一条消息只给一个人 |
| **消费者身份** | 无身份，抢完就没了 | 有消费者名称，可追踪 |
| **谁消费了哪条消息** | 不知道 | 通过 Pending List 可查 |

### 为什么这个区别重要？

**举例——订单处理系统**

用 **List**：3 个 worker 抢订单处理。worker1 抢到订单 A，处理到一半挂了。**订单 A 就丢了**——没人知道它被谁领走了，List 里也找不回来了。

用 **Stream 消费者组**：worker1 拿到订单 A，pending list 里记录着 `订单A → consumer-A`。worker1 挂了没回复 ACK，系统知道"订单 A 还在 pending，consumer-A 没处理完"，可以把订单 A 重新分配给 worker2。

---

### 一句话总结

> List 的 BRPOP 只是让多个消费者去"抢"消息，抢到就没了，没有分配机制也没有追踪能力。而 Stream 的消费者组可以精确控制"这条消息给谁处理"，还能追踪处理状态并实现消息重投。

## 关联知识点

- [[Stream消息队列]] — Redis Stream 消息队列总览
- [[Stream的消息回溯能力]] — Stream 消息回溯能力详解
- [[Stream的ACK与PendingList]] — ACK 确认机制与 Pending List 完整流程

---
# 🎯 面试题 · 2026-07-28 16:20（基于《List与Stream消息队列对比》）

> [!question]- 面试题 1：请结合消息分发机制，谈谈 Redis 的 List（BRPOP）与 Stream 消费者组在消费模式上的核心区别是什么？
> 这两者在消息分发逻辑上有本质的不同。使用 List 配合 BRPOP 时，多个消费者是处于“竞争”状态的，大家一起去队列头部抢消息，谁先执行命令谁就拿到数据，系统并不关心这条消息具体被哪个消费者拿走了，消费者本身也没有身份标识，抢完即走。而 Stream 的消费者组则是一种“分配”机制，生产者写入消息后，消费者组内部会自动维护状态，确保每条消息只会被组内的一个消费者处理，不会发生重复消费。最关键的区别在于，Stream 能够精确知道“这条消息给谁处理了”，并且有消费者名称可以追踪，而 List 模式下消息一旦被 pop 出队列，就不知道是谁消费的，也无法回溯。

> [!question]- 面试题 2：在订单处理系统中，如果消费者节点在处理任务中途宕机，为什么使用 List 会导致消息丢失，而 Stream 能解决这个问题？
> 这主要取决于两者对消息处理状态的追踪能力。如果使用 List，假设 worker1 通过 BRPOP 抢到了订单 A，但在处理到一半时宕机了，由于 List 没有记录“订单 A 正在被谁处理”，消息已经从队列里消失了，系统无法感知这个异常，订单 A 也就彻底丢失。相比之下，Stream 消费者组内部维护了一个 Pending List，当 worker1 拿到订单 A 但还没发送 ACK 确认时，系统会记录“订单 A 分配给了 consumer-A 且处于待处理状态”。一旦 worker1 宕机没回复 ACK，系统就能从 Pending List 中发现这笔任务未完成，从而可以将订单 A 重新分配给其他健康的消费者，比如 worker2 来继续处理，保证了数据的可靠性。
