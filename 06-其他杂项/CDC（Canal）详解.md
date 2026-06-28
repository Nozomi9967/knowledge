# CDC（Change Data Capture）— Canal 详解

## 一句话定义

> **CDC** = **Change Data Capture**（变更数据捕获）  
> 通过监听数据库的变更日志（如 MySQL binlog），实时捕获数据变更事件，同步到其他系统。

**本项目实现**：阿里巴巴 **Canal** — 监听 MySQL binlog，实时同步数据到 Elasticsearch 和静态页面。

---

## 架构图

```
MySQL ──产生 binlog ──▶ Canal ──▶ Canal Client ──▶ Feign ──▶ Search 服务 ──▶ ES
                                                               └─▶ 静态页服务 ──▶ HTML
```

---

## 核心原理

### MySQL 主从复制（Canal 模拟的机制）

```
Master: 写 binlog ──▶ Slave: 读 binlog ──▶ 回放 SQL ──▶ 数据一致
```

Canal **伪装成 MySQL Slave**，向 Master 请求 binlog，解析后推送给客户端。

### binlog 三种格式

| 格式 | 说明 | 优缺点 |
|------|------|--------|
| **STATEMENT** | 记录原始 SQL | 体积小，但某些函数（NOW()）结果不一致 |
| **ROW**（✅ 推荐） | 记录每行变更前后快照 | 准确，但体积大 |
| **MIXED** | 混合模式 | 自动选择，默认用 STATEMENT，不确定时用 ROW |

### Canal 工作流程

```
1. Canal 模拟 Slave 协议向 MySQL 请求 binlog
2. MySQL 推送 binlog 到 Canal
3. Canal 解析 binlog 为 Event 对象（Insert / Update / Delete）
4. Canal Client 轮询拉取 Event
5. Client 通过 Feign 调用下游服务写入 ES / 更新静态页
```

---

## 为什么用 CDC 而不是业务代码直接写 ES？

### 方案对比

| 方式 | 优点 | 缺点 |
|------|------|------|
| **业务代码双写**（写 MySQL + 写 ES） | 实现简单 | 侵入业务代码，耦合高，失败难处理 |
| **Canal CDC**（✅ 本项目方案） | 解耦，无业务侵入 | 多一层组件维护 |
| **消息队列**（RocketMQ 异步同步） | 削峰填谷，可靠 | 增加延迟，架构更重 |

**本项目选择 Canal 的原因**：
- 业务代码零侵入 — 已有服务不需要改一行代码
- 一个 Canal 实例可监听多个表，统一管理
- 配合 Feign 直调，比 MQ 延迟更低

---

## 面试高频问题

### Q1：Canal 挂掉怎么办？数据一致性如何保证？

```
方案一：Canal 自身高可用
  └─ Canal Server 集群 + ZooKeeper 选举
  └─ 故障时自动切换，Client 重连新 Server

方案二：全量+增量结合
  └─ 定期全量重建 ES（凌晨低峰期）
  └─ Canal 只负责增量同步
  └─ 即使 Canal 挂了，全量重建后数据最终一致

方案三：Canal 记录位点（position）
  └─ 重启后从断点续传
  └─ 不丢数据（binlog 有保留周期）
```

### Q2：ES 索引如何重建？

```bash
# Step 1: 创建新索引
PUT /shopsearch_v2

# Step 2: 切换别名（零停机）
POST /_aliases
{
  "actions": [
    { "remove": { "index": "shopsearch_v1", "alias": "shopsearch" } },
    { "add":    { "index": "shopsearch_v2", "alias": "shopsearch" } }
  ]
}

# Step 3: 全量同步数据（从 MySQL 全部查出写入新索引）
# Step 4: 删除旧索引
```

### Q3：Canal 同步延迟多大？

- **局域网内**：毫秒级（通常 < 50ms）
- **高峰写入**：受 binlog 堆积影响，可能秒级延迟
- **优化**：Canal 消费端异步 + 批量写入 ES（攒一批再 flush）

### Q4：和 Debezium 有什么区别？

| 对比项 | Canal | Debezium |
|--------|-------|----------|
| 生态 | 阿里开源，Java 生态 | Red Hat，Kafka Connect 生态 |
| 输出 | 自定义 Client / MQ | 主要推 Kafka |
| 部署 | 轻量，单进程 | 需要 Kafka + Connect 集群 |
| 适合场景 | 中小团队，Spring Cloud 架构 | 大型数据平台，已有 Kafka 的团队 |

---

## 面试话术

> **面试官**：说说你们项目中的 CDC 是怎么实现的？

**回答要点**：
1. **选型**：使用阿里 Canal 监听 MySQL binlog，实现数据变更捕获
2. **架构**：Canal 伪装成 MySQL Slave → 解析 binlog → Canal Client 拉取 → Feign 调用下游
3. **作用**：搜索同步（MySQL → ES）、静态页面更新
4. **高可用**：Canal 集群 + ZK 选举 + 断点续传
5. **容灾**：定期全量重建，增量同步保证最终一致性

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[06-其他杂项/N+1查询问题详解\|N+1 查询问题]] — ORM 经典陷阱
- [[MySQL binlog 原理]] — binlog 格式与主从复制细节
- [[ES 索引管理]] — 索引重建与别名切换
