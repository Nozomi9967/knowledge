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
