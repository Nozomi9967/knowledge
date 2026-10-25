---
tags:
  - redis
created: 2026-06-28
updated: 2026-06-28
---
## Redis 底层编码标准答案

### 1. String → SDS
- O(1) 获取长度、二进制安全、自动扩容防缓冲区溢出

### 2. List

| 版本 | 底层实现 | 要点 |
|------|---------|------|
| **3.0 之前** | ziplist ↔ linkedlist | 元素 ≤ 512 **且** ≤ 64B → ziplist，任一超标 → linkedlist |
| **3.2 ~ 6.2** | **quicklist** | 双向链表 + 每个节点装一个 ziplist，`list-max-ziplist-size` 控制，默认 8KB |
| **7.0+** | **quicklist** | 内部节点从 ziplist 换成 **listpack**，参数改名 `list-max-listpack-size` |

> quicklist 是「双向链表 + 压缩列表」的混合体，既避免纯 ziplist 的连锁更新风险，又避免纯 linkedlist 的内存碎片。7.0 换 listpack 彻底根除连锁更新。

### 3. Hash

| 版本 | 条件 | 编码 |
|------|------|------|
| **7.0 之前** | 字段数 ≤ 512 **且** 字段值 ≤ 64B | **ziplist** |
| | 任一超标 | **hashtable** |
| **7.0+** | 字段数 ≤ 512 **且** 字段值 ≤ 64B | **listpack** |
| | 任一超标 | **hashtable** |

### 4. Set

| 条件 | 编码 |
|------|------|
| 所有元素都是整数 **且** 元素个数 ≤ 512 | **intset**（整数集合） |
| 存在非整数元素 **或** 元素个数 > 512 | **hashtable** |

> Set 没有"元素大小"这个维度，只关心是否整数值。

### 5. ZSet

| 版本 | 条件 | 编码 |
|------|------|------|
| **7.0 之前** | 元素个数 ≤ **128** **且** 每个元素 ≤ 64B | **ziplist** |
| | 任一超标 | **skiplist** |
| **7.0+** | 元素个数 ≤ **128** **且** 每个元素 ≤ 64B | **listpack** |
| | 任一超标 | **skiplist** |

> ZSet 阈值是 **128**，不是 512（Hash 才是 512）。

### 速记口诀
> **String 是 SDS，List 是个 quicklist**
> **Hash 小用 listpack，大则 hashtable**
> **Set 要全整数且 ≤ 512 才 intset**
> **ZSet 小用 listpack（128），大则跳**
> **7.0 统一换 listpack，除了 Set 不用改**
