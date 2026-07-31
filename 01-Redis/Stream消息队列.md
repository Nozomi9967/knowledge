---
tags:
  - redis
  - Stream
  - 消息队列
created: 2026-07-28
updated: 2026-07-28
---

# Redis Stream 消息队列

Redis 5.0 引入的 Stream 是一种专门为消息队列设计的持久化数据结构。它既有 List 的队列语义，又具备消费者组、消息确认、消息回溯等专业 MQ 特性，是目前 Redis 官方推荐的消息队列方案。

## 核心概念

### 消息 ID

Stream 中的每条消息都有一个**全局唯一、单调递增**的 ID，格式为 `时间戳-序号`：

```
1526919030474-0   第一条消息
1526919030474-1   第二条消息
1526919030532-0   第三条消息
```

这个 ID 是消息的"身份证"，永远不会变，是实现消息回溯的基础。详见 [[Stream的消息回溯能力]]。

### 消费者组（Consumer Group）

消费者组是 Stream 消息队列的核心机制。组内有多个消费者（consumer），当消费者通过 `XREADGROUP` 主动拉取消息时，Redis 根据该组的 `last-delivered-id` 将尚未交付的消息分配给发起请求的消费者，实现组内负载均衡。正常路径下，每条消息只被组内一个消费者读取。

但 Redis Stream 是基于 PEL（Pending Entries List）的**至少一次（at-least-once）**交付语义：消费者读取消息后必须显式调用 `XACK` 确认，否则该消息会一直留在 PEL 中标记为"已交付但未确认"。如果消费者宕机，其他消费者可以通过 `XCLAIM` 或 `XAUTOCLAIM` 将这些未确认的消息重新认领并处理。因此故障恢复场景下，同一条消息可能被两个不同的消费者先后处理，业务侧需要做好幂等。

> [!note] 注意：
> - **组内**：正常路径下一条消息只分配给一个消费者（负载均衡）；故障恢复场景下同一消息可能被重新认领（at-least-once）
> - **组间**：不同消费者组各自维护独立的 Last-delivered-ID，同一批消息可以被多个组各自消费一遍（广播/多路消费）

这与 List 的 BRPOP 竞争消费完全不同——List 是多个消费者去"抢"消息，抢到就没了，没有分配机制、没有确认机制，也没有任何"谁消费了什么"的追踪记录。详细对比见 [[List与Stream消息队列对比]]。

### Pending List（待处理列表）

消息被投递给消费者后，会进入该消费者的 **Pending List**，记录着"已交付但还没确认处理完成"的消息。消费者处理完成后需要调用 `XACK` 显式确认，消息才会从 Pending List 中移除。

如果消费者处理途中宕机，消息不会丢失——它会一直留在 Pending List 中，其他消费者可以通过 `XCLAIM` 或 `XPENDING` 将其重新领走处理。详见 [[Stream的ACK与PendingList]]。

### XREADGROUP 三种读取模式

消费者通过 `XREADGROUP` 命令从消费者组中读取消息，最后参数决定读取模式：

| 模式 | 读取内容 | 使用场景 |
|------|----------|----------|
| `>` | 从未被任何消费者处理过的新消息 | 日常消费，拉新任务 |
| `0` | 已投递给自己但未 ACK 的消息 | 宕机重启，恢复未完成任务 |
| `$` | 命令执行之后才到达的消息 | 新消费者加入，不处理历史数据 |

详见 [[Stream的XREADGROUP三种模式]]。

## 消息不丢失的完整流程

```
1. 消费者用 XREADGROUP > 拉取消息
2. Stream 将消息投递给消费者，写入该消费者的 Pending List
3. 消费者处理消息
   ├─ 处理成功 → 调用 XACK，从 Pending List 移除
   └─ 处理失败（消费者挂了/超时）
        └─ 消息留在 Pending List
4. 其他消费者用 XCLAIM 或 XPENDING 查看超时的 pending 消息
    重新分配给其他消费者处理
```

## 消息回溯

Stream 保留了所有历史消息，配合消息 ID 可以按 ID 范围查询任意时间段的记录：

```
XRANGE mystream - +           # 查所有消息
XRANGE mystream <from> <to>    # 查 ID 范围内的消息
XRANGE mystream - + COUNT 10   # 限制数量
```

这是 List 做不到的——List 的消息被 pop 后就删除了，且没有消息 ID 概念。

## 基本命令详解

### XADD — 添加消息

```
XADD stream_name [MAXLEN | MINID] [=|~] threshold [LIMIT offset] *|id field value [field value ...]
```

生产消息的唯一入口。最常用的写法：

```
XADD mystream * task "work1" user "Alice"
```

| 参数 | 说明 |
|------|------|
| `mystream` | Stream 的 key 名，不存在则自动创建 |
| `*` | 让 Redis 自动生成消息 ID（时间戳-序号），最常用 |
| `1526919030474-0` | 也可以手动指定 ID，但必须大于当前最大 ID |
| `task "work1" user "Alice"` | 消息内容，类似 Hash 的 field-value 对，可以写多个 |
| `MAXLEN 1000` | 可选项，限制 Stream 最多保留 1000 条消息，防止内存溢出 |

返回新消息的 ID，如 `1526919030474-0`。

---

### XREAD — 读取消息（普通模式）

```
XREAD [COUNT n] [BLOCK ms] STREAMS stream_name id
```

最简单的读取方式，不需要消费者组：

```
XREAD BLOCK 5000 STREAMS mystream 0
```

| 参数 | 说明 |
|------|------|
| `COUNT n` | 一次最多读 n 条 |
| `BLOCK ms` | 阻塞等待毫秒数，0 表示无限阻塞，类似 BLPOP |
| `0` 或 `$` | 起始 ID。`0` 从第一条读起，`$` 从当前最新之后读起 |
| `STREAMS` | 后面跟流名和 ID，可以同时读多个 Stream |

注意：这个命令**没有消费者组语义**，所有消费者读到的是同一批数据，无法做任务分配。生产环境一般用 `XREADGROUP` 替代。

---

### XREADGROUP — 消费者组读取

```
XREADGROUP GROUP group_name consumer_name [COUNT n] [BLOCK ms] STREAMS stream_name mode
```

消费者组模式下的拉取命令：

```
XREADGROUP GROUP mygroup consumer1 COUNT 10 BLOCK 5000 STREAMS mystream >
```

| 参数 | 说明 |
|------|------|
| `GROUP mygroup` | 消费者组名称，必须先 `XGROUP CREATE` 创建 |
| `consumer1` | 当前消费者的名称，可以自定义 |
| `COUNT 10` | 一次最多拉 10 条 |
| `BLOCK 5000` | 没有新消息时阻塞 5 秒 |
| `>` | 读取模式，见下表 |

**三种模式：**

| 模式 | 含义 | 场景 |
|------|------|------|
| `>` | 只读从未被任何消费者处理过的消息 | 日常消费 |
| `0` | 只读已投递给自己但未 ACK 的消息（pending 列表） | 宕机重启恢复 |
| `$` | 只读命令执行**之后**到达的消息 | 新消费者加入，跳过历史 |

---

### XACK — 确认消息已处理

```
XACK stream_name group_name message_id
```

消费者处理完一条消息后必须调用，否则该消息会一直留在 Pending List 中：

```
XACK mystream mygroup 1526919030474-0
```

只有被 ACK 的消息，才会从消费者的 Pending List 中移除，并且 Stream 才认为该消息"已消费完毕"。

---

### XPENDING — 查看 Pending 状态

```
XPENDING stream_name group_name
XPENDING stream_name group_name start end count
```

查看组内所有 pending 消息的概况：

```
XPENDING mystream mygroup
# 输出：消费者名称 |  pending 消息数量 | 最小 pending ID | 最大 pending ID
```

加范围参数可以查看具体消息：

```
XPENDING mystream mygroup - + 10
# 列出组内前 10 条 pending 消息及其消费者归属
```

---

### XCLAIM — 认领超时消息

```
XCLAIM stream_name group_name consumer_name min-idle-time message_id [message_id ...]
```

当某个消费者宕机，其 pending 的消息长时间未被 ACK，其他消费者可以主动"认领"过来重新处理：

```
XCLAIM mystream mygroup consumer2 60000 1526919030474-0 1526919030474-1
```

| 参数 | 说明 |
|------|------|
| `consumer2` | 认领消息的消费者名称 |
| `60000` | 消息闲置超过 60 秒才算超时（毫秒） |
| `1526919030474-0` | 要认领的消息 ID，可以一次认领多条 |

> [!tip] XCLAIM 的典型用法：消费者 A 宕机 → 消费者 B 用 `XPENDING` 查到超时消息 → 用 `XCLAIM` 认领 → 重新处理 → `XACK` 确认。

---

### XRANGE / XREVRANGE — 按范围查询历史消息

```
XRANGE stream_name start_id end_id [COUNT n]
XREVRANGE stream_name end_id start_id [COUNT n]
```

按消息 ID 范围查询，用于消息回溯和审计：

```
XRANGE mystream - +              # 查全部消息
XRANGE mystream 1526919030474-0 1526919030532-0  # 查 ID 范围
XRANGE mystream - + COUNT 20     # 最多返回 20 条
XREVRANGE mystream + - COUNT 10  # 最新的 10 条（倒序）
```

| 参数 | 说明 |
|------|------|
| `-` | 最小 ID（第一条） |
| `+` | 最大 ID（最新一条） |
| `COUNT n` | 限制返回条数 |

---

### XLEN — 查看消息数量

```
XLEN mystream
# 返回当前 Stream 中的消息总数（包括已消费和未消费的）
```

---

### XTRIM — 限制 Stream 长度

```
XTRIM mystream MAXLEN 10000
```

Stream 默认不会自动删除消息，需要手动限制长度防止内存无限增长。设置后，超出部分的最旧消息会被删除。

| 策略 | 说明 |
|------|------|
| `MAXLEN n` | 精确模式：Stream 最多保留 n 条 |
| `MAXLEN ~ n` | 近似模式：性能更好，实际长度可能略超 n |

> [!warning] 如果在生产环境使用 `MAXLEN`，建议配合消费者组 + ACK 使用，避免在消费者来不及处理时删除了未消费的消息。也可以只限制 Stream 的长度，在确认消息已 ACK 后再 trim。

---

### XDEL — 删除指定消息

```
XDEL mystream 1526919030474-0
```

按消息 ID 精确删除某条消息。删除后该消息不可再被 `XRANGE` 等命令读到，但消息 ID 不会复用。

---

## 核心命令速查

| 命令 | 作用 |
|------|------|
| `XADD stream * field value` | 添加消息 |
| `XREAD [COUNT n] [BLOCK ms] STREAMS stream id` | 读取消息（非消费者组模式） |
| `XREADGROUP GROUP grp consumer STREAMS stream 模式` | 消费者组读取 |
| `XACK stream group message-id` | 确认消息已处理 |
| `XPENDING stream group` | 查看组内 pending 消息概况 |
| `XCLAIM stream group consumer min-idle-time message-id` | 把超时的 pending 消息认领过来 |
| `XRANGE stream start end` | 按 ID 范围查询历史消息 |
| `XLEN stream` | 查看消息数量 |
| `XTRIM stream MAXLEN n` | 限制 Stream 最大长度 |
| `XDEL stream message-id` | 删除指定消息 |

## 关联知识点

- [[List与Stream消息队列对比]] — List vs Stream 在消息队列场景下的核心差异
- [[Stream的消息回溯能力]] — XRANGE 与消息 ID 机制详解
- [[Stream的XREADGROUP三种模式]] — `>` / `0` / `$` 三种读取模式详解
- [[Stream的ACK与PendingList]] — ACK 确认机制与 Pending List 完整流程
