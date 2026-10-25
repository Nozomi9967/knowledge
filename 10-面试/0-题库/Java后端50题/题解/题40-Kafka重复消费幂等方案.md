### **重复消费是 at-least-once 语义的必然结果，不是 bug；正确解法是「生产端幂等 + 消费端幂等」双保险——生产端靠 `enable.idempotence=true` 消除重试造成的重复，消费端靠业务唯一键 + Redis/唯一索引兜住 offset 提交失败和 rebalance 造成的重复，两层缺一不可。**

下面按面试口述节奏展开，先说清楚重复从哪来，再分别讲两端怎么做。

---

**第一部分：先讲清楚消息为什么会重复**

只要投递语义是 at-least-once，"不丢"和"不重"就天然冲突，工程上永远优先保不丢，重复交给幂等消化。重复来自三个地方：

第一种是**生产端重试**。Producer 发送后没收到 broker 的 ack（网络抖动、broker 主从切换、请求超时），它不知道消息到底写没写进去，只能重发。这一重发就可能写了两条。

第二种是**消费端 offset 提交失败**。消费者把消息处理完了（业务已经落库），但提交 offset 时进程挂了或者网络断了，broker 认为这条还没消费，重平衡后重新投给它，于是又处理一遍。这是最常见的重复来源，注意它是"业务成功、位点没记住"。

第三种是**rebalance**。消费组内成员上下线、分区数变化都会触发重平衡，重平衡期间已经拉取但还没提交位点的消息会被重新分配给别人。

所以结论很直接：只要你在用 Kafka/RocketMQ，就必须假设消息会重复，幂等是消费方的必答题，而不是可选项。

---

**第二部分：生产端幂等——`enable.idempotence` 解决什么**

Kafka 从 0.11 开始支持幂等生产者，配置一行：

```properties
enable.idempotence=true
# 下面三个会由 Kafka 自动校验/设置
acks=all
retries=Integer.MAX_VALUE
max.in.flight.requests.per.connection <= 5
```

它的原理是给每个 Producer 分配一个 **PID（Producer ID）**，再对每个分区维护一个单调递增的 **Sequence Number**。Broker 端按 `<PID, Partition, Seq>` 缓存最近一批序号，收到重复序号时直接丢弃并返回成功。

这解决了什么问题：**只解决单生产者会话内、单分区、因重试导致的重复**。

它解决不了什么，这点必须说清：

- Producer 重启后 PID 会变，新会话的序号从 0 开始，broker 认为是新消息，去重失效
- 跨分区、跨 topic 的重复它管不了
- 消费者侧的重复它完全管不了

所以 `enable.idempotence` 只是把"生产端重试导致的重复"这一半堵上了，消费端仍然必须自己做幂等。RocketMQ 侧对应的是 `producer.setRetryTimesWhenSendFailed()` 加上业务侧用 `keys` 做去重，思路一样。

---

**第三部分：消费端幂等——三种实现，按成本排序**

**方案一：Redis 去重（最常用）**

用消息的唯一标识（业务单号 / 消息 ID）做 key，处理前先 `SETNX`，成功才处理，处理完写个短过期标记。

```java
String dedupKey = "msg:dedup:" + msg.getOrderNo();
Boolean first = redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24));
if (Boolean.FALSE.equals(first)) {
    log.info("重复消息，直接 ack: {}", msg.getOrderNo());
    return;  // 注意：要正常 ack，不能抛异常，否则会无限重投
}
try {
    businessService.handle(msg);
} catch (Exception e) {
    redis.delete(dedupKey);   // 业务失败要把标记删掉，否则重试也进不来
    throw e;
}
```

两个坑要讲：一是**失败必须删标记**，不然第一次处理失败、第二次重试被去重挡住，消息永远处理不了；二是**过期时间要覆盖重试窗口**，设 24 小时是为了覆盖 Kafka 的重试周期，设太短（比如 5 分钟）挡不住延迟重投。

**方案二：数据库唯一索引（最可靠）**

建一张消息消费表或者直接用业务表的唯一约束：

```sql
INSERT INTO t_order (order_no, user_id, amount, status)
VALUES (?, ?, ?, 'CREATED')
-- order_no 上有唯一索引，重复插入直接抛 DuplicateKeyException
```

捕获 `DuplicateKeyException` 就当作"已处理过"，直接 ack。这个方案的好处是**幂等和业务落库在同一个事务里**，不存在"去重成功但业务没写"的中间态；缺点是只适用于"插入型"业务，更新型业务（比如扣库存）没法靠唯一索引。

**方案三：状态机 / 版本号（更新型业务专用）**

扣库存、改订单状态这类操作，用状态判断挡重复：

```sql
UPDATE t_order SET status = 'PAID'
WHERE order_no = ? AND status = 'UNPAID';
-- 影响行数为 0 说明已经处理过或状态不对，直接 ack
```

用 `WHERE` 带上前置状态，天然幂等。比加锁轻，比 Redis 可靠。

---

**第四部分：生产环境的组合拳与话术**

实际项目里我是这么配的：

1. 生产端开 `enable.idempotence=true`，消掉重试重复
2. 消息体里带业务唯一键（订单号），不用 Kafka 自己生成的 offset 当幂等键——因为重投时 offset 可能变
3. 消费端按业务类型选方案：插入型用唯一索引，更新型用状态机，通知型用 Redis 去重
4. 去重 key 的 TTL 覆盖最长重试周期，一般 24 小时
5. 监控重复率，如果某类消息重复率突然升高，说明消费端处理慢、频繁 rebalance，要回头查消费能力

面试被追问"能不能做到 exactly-once"，标准答法是：Kafka 的 exactly-once（事务 + 幂等）只在 Kafka Streams 的"读-处理-写回 Kafka"闭环里成立，一旦要写外部数据库，仍然要靠业务幂等。所以工程上落地的永远是"至少一次 + 幂等"，不要说我做到了精确一次。

> 关联：[[RocketMQ详解]] · [[RocketMQ事务消息详解]] · [[Redisson详解]]
