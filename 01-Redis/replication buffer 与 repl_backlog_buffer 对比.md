---
tags:
  - redis
  - 主从复制
  - replication-buffer
  - repl-backlog
created: 2026-07-26
updated: 2026-07-26
---

# 主从复制中 replication buffer 与 repl_backlog_buffer 对比

> 以下内容基于 [[主从复制是怎么实现的？]] 中已有知识点的补充说明。

---

## 数据流向：两者如何配合

### 命令传播阶段（主从正常连接时）

主节点收到写命令后"双写"：一份写入该从节点的 replication buffer 推送给 socket，另一份写入 repl_backlog_buffer 留底。

### 断连重连阶段（增量复制）

从节点重连后通过 psync 把自己的 slave_repl_offset 发给主节点，主节点按以下步骤操作：

1. 比对 offset：用从节点的 slave_repl_offset 和自己的 master_repl_offset 对比
2. 从 backlog 找回 delta：主节点去 repl_backlog_buffer 中捞出缺失的那段增量数据
3. 灌入 replication buffer：把这段 delta 写入从节点专属的 replication buffer
4. 推送给从节点：通过 replication buffer 发到从节点的 socket

如果 backlog 已被环形覆盖，则退回全量复制。

---

## replication buffer 的完整职责范围

replication buffer 不只是全量同步时才用。它是从节点作为 client 的输出缓冲区，贯穿全流程：

- 全量复制阶段：bgsave 期间主节点不阻塞，新写命令暂存到 buffer，等 RDB 传完后补发给从节点
- 命令传播阶段：实时把新写命令缓存后推送给从节点
- 断连重连阶段：把从 backlog 找回的 delta 灌入 buffer 后发送

---

## 断连期间 replication buffer 的状态

主从断开后，该从节点对应的连接关闭，replication buffer 随之释放，主节点无法往里写数据。

断连期间主节点只往 repl_backlog_buffer 追加写命令留底。重连后新的 TCP 连接建立，新的 replication buffer 被分配，主节点再从 backlog 取数据灌入发送。

---

## 配置注意

Valkey/Redis 中，replication backlog 和 replica 的 output buffer 实际共享同一份底层内存分配。如果 client-output-buffer-limit replica 设得比 repl-backlog-size 还小，可能出现 backlog 数据还在但 output buffer 先超限断连的矛盾。Redis 引擎会自动用 backlog 大小作为 output buffer 的有效上限兜底。

---

## 关联知识点

- [[主从复制是怎么实现的？]] — 主从复制三阶段全流程、全量复制、增量复制
- [[Redis fork 全量同步优化案例]] — bgsave fork 在复制中的应用
- [[Redis 大 Key 对持久化有什么影响？]] — 大 Key 导致 COW 内存放大的问题
