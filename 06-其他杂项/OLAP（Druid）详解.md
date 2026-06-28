# OLAP 解析 — Online Analytical Processing（联机分析处理）— Druid 详解

## 一句话定义

> **OLAP** = **Online Analytical Processing**（联机分析处理）  
> 面向**分析型**场景，支持对海量数据进行多维聚合、钻取、切片等复杂查询。

**本项目实现**：Apache Druid（通过 Avatica JDBC 连接），用于热门商品分析，亿级数据秒级聚合查询。

---

## OLTP vs OLAP

| 对比项 | OLTP（联机事务处理） | OLAP（联机分析处理） |
|--------|-------------------|-------------------|
| 面向 | 业务操作（C 端用户） | 数据分析（运营/管理层） |
| 典型操作 | 增删改查（INSERT/UPDATE） | 聚合查询（SUM/COUNT/GROUP BY） |
| 数据量 | MB ~ GB | TB ~ PB |
| 响应要求 | 毫秒级 | 秒级~分钟级 |
| 典型系统 | MySQL、PostgreSQL | Druid、ClickHouse、StarRocks |

**一句话**：OLTP 管"写入和事务"，OLAP 管"查询和分析"。

---

## Apache Druid 核心特性

### 1. 列式存储
- 只读取查询涉及的列，而非整行
- 对聚合查询（SUM、AVG、COUNT）极快
- 按列压缩，存储空间大幅减少

### 2. 预聚合（Roll-up）
```
原始数据：
  2024-01-01 10:00, 商品A, 北京, 浏览量=1
  2024-01-01 10:00, 商品A, 北京, 浏览量=1

Roll-up 后：
  2024-01-01 10:00, 商品A, 北京, 浏览量=2
```
- 相同维度自动合并，聚合指标累加
- 数据量可压缩 10~100 倍

### 3. 时间分区 + 多级存储
- 数据按时间分区（按小时/天/月分段）
- 热数据放内存（SSD），冷数据放磁盘
- 查询自动路由到对应分区

### 4. 实时 + 批量摄入
- 实时流式摄入（从 Kafka 实时消费）
- 批量离线导入（定时全量/增量加载）
- 本项目通过 **Avatica JDBC** 连接查询

---

## 本项目中的 OLAP 场景

**热门商品分析**：
```sql
-- 通过 Avatica JDBC 查询 Druid
SELECT
  product_id,
  product_name,
  SUM(view_count) AS total_views,
  SUM(order_count) AS total_orders,
  SUM(order_count) / SUM(view_count) AS conversion_rate
FROM hot_product_analysis
WHERE __time >= '2024-01-01' AND __time < '2024-02-01'
GROUP BY product_id, product_name
ORDER BY total_views DESC
LIMIT 100
```

**特点**：
- 亿级浏览/点击/订单数据
- 秒级返回 Top N 商品排行
- 支持按时间、地域、品类多维筛选

---

## Druid vs ClickHouse vs StarRocks

| 对比项 | Druid | ClickHouse | StarRocks |
|--------|-------|------------|-----------|
| 摄入方式 | 实时流（Kafka）+ 批量 | 批量为主 | 实时 + 批量 |
| 查询性能 | 预聚合场景极快 | 大表扫描极快 | 多表 JOIN 强 |
| 更新能力 | 不支持单行更新 | 支持 Mutation | 支持主键更新 |
| 运维复杂度 | 组件多（Coordinator/Overlord 等） | 单进程，简单 | 较简单 |
| 适合场景 | 时序/事件流分析（用户行为） | 宽表扫描/日志分析 | 多维分析/报表 |

---

## 面试话术

> **面试官**：你们项目中的 OLAP 是怎么做的？为什么用 Druid？

**回答要点**：
1. **场景**：热门商品分析，需要从亿级用户行为数据中秒级返回聚合结果
2. **选型**：Apache Druid，预聚合 + 列式存储 + 时间分区，天然适合时序分析
3. **接入方式**：通过 Avatica JDBC 连接 Druid，应用层写 SQL 查询
4. **为什么不是 MySQL**：亿级数据 GROUP BY，MySQL 跑不动，需要走索引全表扫描
5. **为什么不是 ClickHouse**：本项目以流式摄入为主，Druid 的 Kafka 实时摄入更成熟

---

## 关联知识点

- [[06-其他杂项/面试分析报告.md|面试分析报告]] — 项目整体分析
- [[MySQL 索引优化]] — OLTP 场景的索引设计
- [[Elasticsearch 搜索原理]] — 另一类分析型引擎（全文搜索）
