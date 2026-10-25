---
tags:
  - redis
  - 数据结构
  - listpack
created: 2026-06-20
updated: 2026-06-20
source: quicklist的内部节点.md
---

## listpack（紧凑列表）是什么

listpack 是 Redis 5.0 引入、**7.0 全面替代 ziplist** 的紧凑型顺序数据结构。设计目标与 ziplist 一致——连续内存、紧凑布局、不存指针以节省内存——但**彻底解决了 ziplist 的连锁更新（cascade update）问题**。

> 通俗理解：ziplist 像一列**互相依靠**的书架（前面书架变长了，后面书架的"距离标记"也得跟着改）；listpack 像一列**各自独立**的储物格（每个格子里都写了自己的位置，互不干扰）。

---

## 内存布局

```
每个 listpack 整体长这样（连续内存块）：

total-bytes | num-elements | entry1 | entry2 | ... | entryN | listpack-end
```

| 字段 | 大小 | 作用 |
|---|---|---|
| **total-bytes** | 4B | 整个 listpack 占用的总字节数 |
| **num-elements** | 2B | 元素个数（> 65535 时需要遍历才能知道实际个数） |
| **entryX** | 不定 | 每个元素 |
| **listpack-end** | 1B | 结束标记 `0xFF` |

---

## 每个 entry 的结构（与 ziplist 的核心区别）

```
每个 entry 长这样：

encoding | content | len
```

| 字段 | 作用 |
|---|---|
| **encoding** | 当前 entry 的数据类型和长度（和 ziplist 类似） |
| **content** | 实际存储的数据（字符串或整数的二进制） |
| **len** | **当前 entry 自己的长度**（1~5 字节，自描述） |

### 与 ziplist 的关键差异

| 对比项 | ziplist entry | listpack entry |
|---|---|---|
| 字段组成 | prevlen + encoding + data | encoding + content + len |
| **prevlen** | 存**前一个** entry 的长度 | **没有了** |
| **len** | 没有 | 存**当前** entry 自己的长度 |

**这是 listpack 解决连锁更新的核心：每个 entry 只记录自己的长度，不记录前一个 entry 的长度。** 后面 entry 不依赖前面 entry 的元数据，所以插入/修改一个 entry 不会引发后续 entry 连锁更新。

---

## 编码类型（与 ziplist 类似）

`encoding` 字段采用变长编码，常见类型：

| 类型 | 标识 | 用途 |
|---|---|---|
| 7bit unsigned int | `xxxxxxx`（高位为 0） | 0~127 的小整数 |
| 6bit string | `10xxxxxx` | 长度 ≤ 63 的字符串 |
| 13bit int | `110xxxxx xxxxxxxx` | 0~4095 的小整数 |
| 12bit string | `1110xxxx` | 长度 ≤ 4095 的字符串 |
| 16bit / 32bit int | `11110001` / `11110010` | 16/32 位整数 |
| 32bit string | `11110011` | 长度 ≤ 4294967295 的字符串 |

---

## listpack 的优势

### 1. 彻底消除连锁更新

ziplist 的 prevlen 是"反向依赖"——后面的 entry 依赖前面的长度，前面的长度一变，后面的 prevlen 全得改。listpack 改成"自描述"——每个 entry 只存自己的长度，**互不依赖**，插入/修改最坏 O(n)。

### 2. 内存布局更简单

少了一个 prevlen 字段，每个 entry 至少省 1 字节。对于大量小元素的场景（如 Hash 字段），内存节省明显。

### 3. 遍历效率相当

虽然没有了 prevlen 无法 O(1) 回溯，但通过 `len` 字段仍可 O(1) **向后** 跳到下一个 entry，遍历效率与 ziplist 持平。

---

## listpack 的局限

| 局限 | 说明 |
|---|---|
| **无法 O(1) 反向遍历** | 没有 prevlen，要倒着遍历得从头再来 |
| **大列表性能退化** | 元素过多时，连续内存分配和迁移成本高（List 走 quicklist 也是为了规避这点） |
| **随机访问仍是 O(n)** | 必须顺序扫描定位 |

---

## Redis 7.0 中的 listpack 应用

| 数据结构 | 7.0+ 编码 | 配置参数 |
|---|---|---|
| **List**（quicklist 节点） | listpack | `list-max-listpack-size` |
| **Hash**（小字段时） | listpack | `hash-max-listpack-entries`、`hash-max-listpack-value` |
| **ZSet**（小元素时） | listpack（替代 ziplist + skiplist 的压缩层） | `zset-max-listpack-entries`、`zset-max-listpack-value` |
| **Stream**（listpack 存消息） | listpack（5.0 起就在用） | `stream-node-max-entries` |

> 7.0 之后，除了 Set（intset）和 ZSet 大元素（skiplist），Redis 的"压缩容器"几乎都被 listpack 接管。

---

## listpack vs ziplist 对比表

| 维度 | ziplist | listpack |
|---|---|---|
| 引入版本 | Redis 早期 | Redis 5.0（7.0 全面替代） |
| entry 字段 | prevlen + encoding + data | encoding + content + len |
| 反向依赖 | 有（prevlen 指向前面） | 无（只自描述） |
| 连锁更新 | **存在**（最坏 O(n²)） | **不存在**（最坏 O(n)） |
| 反向遍历 | O(1)（用 prevlen） | O(n)（需重头遍历） |
| 内存占用 | 略大（多 prevlen） | 略小 |
| 二进制安全 | 是 | 是 |
| 连续内存 | 是 | 是 |

---

## 面试话术

> **问：Redis 7.0 为什么要把 ziplist 换成 listpack？**
>
> 答：ziplist 有个**连锁更新**问题——每个 entry 里有 prevlen 字段存前一个 entry 的长度。如果前一个 entry 因为扩容导致长度跨过了 254 字节这个临界点，prevlen 就要从 1 字节扩到 5 字节，这又会反过来让下一个 entry 的 prevlen 也跟着扩，最坏情况下插入一个元素要更新后面 N 个 entry，时间复杂度 O(n²)。listpack 把 prevlen 字段拿掉了，每个 entry 只存自己的长度（`len` 字段），互不依赖，从根上消除了连锁更新。代价是没法 O(1) 反向遍历了，但 listpack 用在 hash 字段、quicklist 节点这种场景，本来就很少反向遍历，影响不大。

> **问：listpack 和 ziplist 的核心区别？**
>
> 答：ziplist 的 entry 是 `prevlen + encoding + data`，listpack 改成了 `encoding + content + len`。**prevlen 存前一个 entry 的长度，len 存当前 entry 自己的长度**——一个是反向依赖，一个是自描述。这是 listpack 解决连锁更新的关键。

---

## 关联知识点

- [[quicklist的内部节点]] — 7.0 后 quicklist 的内部容器从 ziplist 换成 listpack
- [[ziplist]] — listpack 替代的对象，理解 ziplist 才能理解为何换
- [[Redis底层编码标准答案]] — 7.0+ 各数据结构的底层编码（Hash/ZSet/List 都已用 listpack）
- [[什么时候从 ziplist 转为 linkedlist]] — 阈值转换规则