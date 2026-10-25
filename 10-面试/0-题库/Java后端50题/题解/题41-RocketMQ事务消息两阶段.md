### **事务消息本质是「两阶段提交 + 回查兜底」：先发一条对消费者不可见的 half message 探路，本地事务成功再 commit 让它可见、失败就 rollback 丢弃；如果本地事务结果没来得及告知 broker，broker 会反向回查生产者的本地事务状态。它解决的是「本地事务和消息发送要么都成功、要么都失败」，而不是跨服务的数据一致性。**

下面按面试口述节奏展开，重点是两阶段流程和回查机制。

---

**第一部分：先讲清楚要解决什么问题**

下单场景：本地要写订单库，同时要发一条消息让库存服务扣减。如果没有事务消息，代码会这么写：

```java
orderDao.insert(order);        // ① 本地事务
mqProducer.send(deductMsg);    // ② 发消息
```

两个操作之间没有原子性，两种失败都很要命：

- ① 成功、② 失败：订单建了，库存没扣，超卖
- ① 失败、② 成功：订单没建，库存扣了，少卖

那能不能把发消息放进本地事务？不行，消息发出去就收不回来，数据库回滚不会撤销一条已经投递的 MQ 消息——这是**两个不同系统，本地事务管不到 MQ**。

事务消息就是为这个缝设计的。

---

**第二部分：两阶段流程（面试要能画出来）**

```
生产者                                      Broker
  │                                           │
  │ ① 发送 half message（半消息）              │
  │──────────────────────────────────────────>│  存入 RMQ_SYS_TRANS_HALF_TOPIC
  │<──────────────────────────────────────────│  返回成功（此时消费者看不到）
  │                                           │
  │ ② 执行本地事务（写订单库）                  │
  │                                           │
  │ ③ 根据本地事务结果 commit / rollback       │
  │──────────────────────────────────────────>│
  │                                           │  commit：消息转入真实 topic，对消费者可见
  │                                           │  rollback：删除半消息，消费者永远看不到
  │                                           │
  │ ④ 若 broker 迟迟没收到 ③（生产者挂了/网络断）│
  │<──────────────────────────────────────────│  定时回查（默认 60s 后，最多 15 次）
  │ ⑤ 返回本地事务状态（COMMIT / ROLLBACK / UNKNOWN）
  │──────────────────────────────────────────>│
```

四个关键点：

**第一，半消息存的是内部 topic**（`RMQ_SYS_TRANS_HALF_TOPIC`），消费者看不到，所以"消息先发了但事务没成"不会造成脏消费。

**第二，第 ③ 步是"告知结果"而不是"真正发送"**。commit 的动作是把半消息从内部 topic 转移到真实业务 topic，这一步由 broker 完成，生产者只发一个很轻的确认请求。所以生产者挂在这一步也不会丢消息，broker 会回查。

**第三，回查是兜底而不是主路径**。默认 `transactionCheckInterval=60s` 开始回查，`transactionCheckMax=15` 次，超过就丢弃并告警。回查时生产者要实现 `checkLocalTransaction`，去数据库查这笔订单到底在不在。

**第四，回查接口必须实现幂等且能给出明确结论**。如果一直返回 UNKNOWN，15 次之后 broker 会丢弃消息，这笔订单就变成"订单有、库存没扣"的脏数据，所以回查逻辑要能处理"订单表里没有"和"订单表里有"两种确定状态。

---

**第三部分：代码骨架**

```java
// 生产者：发事务消息
TransactionMQProducer producer = new TransactionMQProducer("order-producer");
producer.setTransactionListener(new TransactionListener() {

    // ② 执行本地事务
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            orderService.createOrder(parse(msg));   // 本地 DB 事务
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    // ④ 回查：broker 问"这笔到底成没成"
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String orderNo = msg.getKeys();
        return orderService.exists(orderNo)
                ? LocalTransactionState.COMMIT_MESSAGE
                : LocalTransactionState.ROLLBACK_MESSAGE;
    }
});

producer.sendMessageInTransaction(msg, null);
```

注意 `executeLocalTransaction` 里如果抛异常但业务其实已经提交（比如提交后回写状态失败），返回 ROLLBACK 会造成"订单有、消息没发"；所以稳妥做法是本地事务里把"消息状态"和业务数据写在同一个库事务里，回查时以业务表为准。

---

**第四部分：和本地消息表怎么选**

面试常追问"为什么不直接用本地消息表"。两者都能保证最终一致，差别在：

| 维度 | 事务消息 | 本地消息表 |
|---|---|---|
| 侵入性 | 低，业务代码只管本地事务 | 高，要建消息表 + 定时扫表 + 重试 |
| 依赖 | 强依赖 RocketMQ | 只依赖数据库，MQ 可以换 |
| 实时性 | 高，commit 后立刻投递 | 低，靠定时任务扫，有秒级到分钟级延迟 |
| 可靠性 | 依赖 broker 回查机制 | 靠扫表重试，逻辑自己掌控 |
| 适用 | 用 RocketMQ 且追求实时 | 多 MQ 混用、或不想绑死某个 MQ |

我的选择标准是：项目已经在用 RocketMQ 就直接上事务消息，接入成本最低；如果 MQ 可能换、或者要兼容多种 MQ，用本地消息表更稳。

还有一句要补：事务消息保证的是"本地事务和消息发送的原子性"，**它不保证下游消费一定成功**。库存服务消费失败仍然要靠重试 + 死信队列兜底。所以完整链路是"事务消息保生产端 + 消费幂等保消费端 + 死信队列保异常"，三段缺一不可。

> 关联：[[RocketMQ详解]] · [[题40-Kafka重复消费幂等方案]] · [[Seata详解]]
