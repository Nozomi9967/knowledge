
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

这也是为什么 Redis 3.2 引入 quicklist 来**替代 linkedlist + ziplist**——quicklist 本质就是 **LinkedList<Node>**，取了一个折中：每个节点用 ziplist 打包一批元素，节点之间用指针连接，既避免了单一 ziplist 太大导致连锁更新范围过大，又比纯 linkedlist 省内存。