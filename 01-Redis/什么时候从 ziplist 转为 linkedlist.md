
### Redis 3.0 之前（List 场景）

List 底层有两种编码：**ziplist** 和 **linkedlist**。转换条件：

|条件|结果|
|---|---|
|元素个数 ≤ `list-max-ziplist-entries`（默认 512）**且** 所有元素 ≤ `list-max-ziplist-value`（默认 64B）|用 **ziplist**|
|元素个数 > 512 **或** 任一元素 > 64B|转 **linkedlist**（不可逆）|

触发转换的原因其实就两个：

1. **元素太多** → ziplist 的连锁更新风险会累积，每次插入都要挪动大量内存
2. **元素太大** → ziplist 的内存压缩优势就没了，每个 entry 本身的数据就很大，压缩收益不明显，反而带来连锁更新的风险

### 注意：双向链表本身也曾是"当前最佳"

在 Redis 3.0 之前，linkedlist 就是 List 的唯一"重型"编码。每个节点有：

- `prev` / `next` 指针（各 8 字节）
- 数据指针（8 字节）
- 前后指针导致**内存碎片化**，每个节点单独 malloc

这也是为什么 Redis 3.2 引入 quicklist 来**替代 linkedlist + ziplist**——quicklist 本质就是 LinkedList<Node，取了一个折中：每个节点用 ziplist 打包一批元素，节点之间用指针连接，既避免了单一 ziplist 太大导致连锁更新范围过大，又比纯 linkedlist 省内存。




---
# 🎯 面试题 · 2026-07-21 15:52（基于《什么时候从 ziplist 转为 linkedlist》）

> [!question]- 面试题 1：Redis 3.0 之前 List 类型的 ziplist 编码满足什么条件会转为 linkedlist 编码？触发这种转换的核心原因是什么？
> Redis 3.0 之前 List 的 ziplist 编码触发转换为 linkedlist 的条件是两个配置项的判定：当 List 的元素个数超过 `list-max-ziplist-entries` 默认值 512，或者任一元素的大小超过 `list-max-ziplist-value` 默认值 64B 时，就会发生转换，且这个转换是不可逆的。
> 触发转换的核心原因有两个方面：一方面是如果元素个数过多，ziplist 的连锁更新风险会不断累积，每次插入新元素都可能需要挪动大量后续内存块，性能损耗会明显变大；另一方面是如果单个元素过大，ziplist 的内存压缩优势就完全无法体现，因为每个 entry 本身的数据量已经很大，压缩带来的收益极低，反而还会伴随连锁更新的风险。

> [!question]- 面试题 2：Redis 3.0 之前 List 使用 linkedlist 编码存在什么明显问题？为什么 Redis 3.2 要引入 quicklist 来替代原有的 linkedlist + ziplist 组合方案？
> Redis 3.0 之前 List 使用 linkedlist 编码的明显缺陷是内存开销大、碎片化严重：每个链表节点需要额外存储 prev、next 两个指针（各占 8 字节），再加上数据指针（8 字节），且每个节点都是单独通过 malloc 分配内存，会导致严重的内存碎片化，存储效率很低。
> 而 Redis 3.2 引入 quicklist 替代原有组合方案，本质是取两者的折中：quicklist 可以理解为带节点的链表，每个节点内部用 ziplist 打包一批元素，节点之间通过指针连接。这种方式既避免了单一 ziplist 存储元素过多导致连锁更新的影响范围过大，又比纯 linkedlist 的存储方式更省内存，解决了原有两种编码的痛点。
