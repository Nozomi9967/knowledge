## ziplist（压缩列表）是什么

ziplist 是 Redis 自己实现的一种**紧凑型顺序数据结构**，把一串元素**连续、紧凑地**放在一块连续内存里。目的是节省内存——相比双向链表每个节点要存 prev/next 指针（各 8 字节），ziplist 完全不存指针。

### 内存布局

```
每个 ziplist 整体长这样（连续内存块）：

zlbytes | zltail | zllen | entry1 | entry2 | ... | entryN | zlend
```

| 字段          | 大小  | 作用                          |
| ----------- | --- | --------------------------- |
| **zlbytes** | 4B  | 整个 ziplist 占用的总字节数          |
| **zltail**  | 4B  | 最后一个元素的偏移量（方便从尾部倒序遍历）       |
| **zllen**   | 2B  | 元素个数（> 65535 时需要遍历才能知道实际个数） |
| **entryX**  | 不定  | 每个元素                        |
| **zlend**   | 1B  | 结束标记 `0xFF`                 |


### 每个 entry 的结构

```
每个 entry 长这样：

prevlen | encoding | data
```

|字段|作用|
|---|---|
|**prevlen**|前一个 entry 的总字节数（往前回溯用）|
|**encoding**|当前 entry 的数据类型和长度|
|**data**|实际存储的数据（字符串或整数的二进制）|

`prevlen` 有两种编码：

- 如果前一个 entry 长度 **< 254B**，prevlen 只用 **1 字节**
- 如果前一个 entry 长度 **≥ 254B**，prevlen 用 **5 字节**（第 1 字节 = `0xFE` 标记，后 4 字节存长度）

---

## 连锁更新（cascade update）—— ziplist 的致命伤

这就是 ziplist 最终被放弃的根本原因。

### 触发场景

假设你的 ziplist 里有一串 entry，每个 entry 长度都在 **250~253 字节**之间：

```
entry1(250B) → entry2(253B) → entry3(251B) → entry4(252B) → ...
         ↑              ↑              ↑              ↑
      prevlen=1B    prevlen=1B      prevlen=1B      prevlen=1B
```

现在你在 entry1 后面**插入了一个新元素**，这个新元素很大，占了 **300 字节**：

```
entry1(250B) → NEW(300B) → entry2(253B) → entry3(251B) → entry4(252B) → ...
                              ↑
                         问题来了：NEW 的长度 ≥ 254B
                         所以 entry2 的 prevlen 需要从 1B 扩容到 5B
```

### 连锁反应

1. **entry2 的 prevlen 从 1B → 5B**，导致 entry2 的整体长度增加了 4 字节（253 → 257）
2. entry2 现在 ≥ 254B 了，那 **entry3 的 prevlen 也得从 1B 扩容到 5B**
3. entry3 长度增加 4 字节（251 → 255），又 ≥ 254B
4. **entry4 的 prevlen 也得扩容**
5. ...一直传导下去，像多米诺骨牌

这就是**连锁更新（cascade update）**——最坏情况下，插入一个元素要往后更新 N 个 entry，**时间复杂度 O(n²)**。

### 概率问题

> 实际生产中，连锁更新并不常见。因为需要：
> 
> 1. 每个 entry 长度恰好落在 [250, 253] 这个狭窄区间
> 2. 连续多个 entry 都这样
> 
> 概率低，但一旦触发，延迟会瞬间飙升。

这就是 Redis 团队仍然要换掉它的原因——哪怕概率低，**作为基础设施不能有这种 O(n²) 的隐患**。