## Stream 的 ACK 与 Pending List

### Pending List（待处理列表）

Stream 消费者组里，每个消费者都有一个 **Pending List**，记录了所有**已投递给它、但还没确认处理完成**的消息。

```
Stream  →  消费者组 mygroup
              ├─ consumer-A  → Pending List: [msg1, msg4, msg7]
              ├─ consumer-B  → Pending List: [msg2, msg5]
              └─ consumer-C  → Pending List: [msg3, msg6, msg8, msg9]
```

一条消息被投递给消费者后，会立刻进入该消费者的 Pending List。消费者处理完后调用 `XACK` 才移除。

### ACK（确认）机制

```
# 消费者处理完消息后，回复 ACK
XACK mystream mygroup msg-id
```

**ACK 的作用：** 告诉 Stream "这条消息我处理完了，可以从我的 Pending List 里移除了"。

### 保证消息不丢的流程

```
1. 消费者用 XREADGROUP > 拉取消息
2. Stream 把消息投递给消费者，同时写入该消费者的 Pending List
3. 消费者处理消息
   ├─ 处理成功 → 调用 XACK，从 Pending List 移除
   └─ 处理失败（消费者挂了/超时）
        └─ 这条消息一直留在 Pending List 里
4. 其他消费者可以用 XCLAIM 或 XPENDING 查看超时的 pending 消息
   然后重新分配给其他消费者处理
```

### 和 List 对比

| | List | Stream |
|---|---|---|
| **消息被谁消费了** | 不知道 | Pending List 记录了 `消息 → 消费者` 的对应关系 |
| **处理失败怎么办** | 消息已 pop，丢失 | 消息仍在 Pending List，可重新分配 |
| **重复消费** | 无法避免 | `XACK` 确认过的不会重复投递 |
| **有无 ACK** | 无 | 有，显式确认 |

### 场景举例

**List 场景：**
```
worker1 抢到订单A → 处理到一半服务器宕机 → 重启
→ 订单A 已经 RPOP 出去了，再也找不回来了
→ 用户没收到货，投诉
```

**Stream 场景：**
```
consumer-A 收到订单A → 处理到一半服务器宕机 → 重启
→ 用 XREADGROUP ... 0 拉取 pending 消息
→ 订单A 还在 Pending List 里，继续处理
→ 或者超时后被其他 consumer 用 XCLAIM 领走处理
→ 订单不会丢

## 关联知识点

- [[Stream消息队列]] — Redis Stream 消息队列总览
- [[Stream的XREADGROUP三种模式]] — XREADGROUP 三种读取模式（配合 Pending List 理解模式 `0`）
```
