# ES Segment（段）详解

> 本文件是 [[06-其他杂项/ES详解]] 中 Segment 机制的独立详解延伸。主文件在写入流程和面试题中提及了 refresh、NRT 等概念，本文展开 Segment 的完整原理。

---

## 一句话概括

> **Segment 是 Lucene 索引的最小不可变存储单元，每个 Segment 都是一个完整的倒排索引，一旦写入磁盘便不可修改。** ES 通过不断创建新 Segment 实现近实时搜索，通过后台合并（Merge）清理已删除文档、减少 Segment 数量。

---

## Segment 与 MySQL 的对比

| 特性 | MySQL（B+ Tree） | ES Segment（Lucene） |
|------|------|------|
| **修改方式** | 原地更新 | **不可变**，更新 = 标记旧文档删除 + 重新索引新文档 |
| **删除方式** | 物理删除 | `.del` 文件中标记，段合并时才物理清理 |
| **新增数据** | 追加到数据页 | 创建**新 Segment** |
| **并发控制** | 需要锁 | 无需锁，Segment 只读，可并发访问 |
| **缓存利用** | 更新导致缓存失效 | 缓存永不过期，利于 OS 页缓存 |

**设计权衡**：Segment 不可变 → 读性能高、并发友好，但更新删除昂贵。ES 适合**读多写少**的搜索场景，不适合频繁更新的事务场景。

---

## Segment 生命周期

```
写入请求 → 内存 Buffer → refresh（1s）→ 生成新 Segment → 写入 FileSystem Cache → 可搜索
                                                                               │
后台合并线程 ← 选择小 Segment ← 查询时遍历所有 Segment（Segment 越多越慢）
    │
    ▼
合并成更大 Segment → 清理已删除文档 → 旧 Segment 删除
```

**关键点**：
- 每次 refresh 生成一个新 Segment（默认 1s 一个，积少成多）
- 查询时需要遍历该分片下的**所有 Segment**，Segment 越多越慢
- 每个 Segment 的元数据（Term Index 等）常驻 JVM 堆内存，Segment 越多内存压力越大

---

## 段合并（Segment Merge）

段合并解决两个问题：Segment 数量爆炸、已删除文档未清理。

| 阶段 | 说明 |
|------|------|
| **选择** | 合并策略（默认 `TieredMergePolicy`）挑选大小相近的小 Segment |
| **归并** | 重新构建倒排索引，计算词频/位置，剔除被标记删除的文档 |
| **提交** | 新 Segment 写入磁盘，旧 Segment 删除 |

**合并策略核心参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `floor_segment` | 2MB | 小于此值视为"小段"，优先合并 |
| `max_merge_at_once` | 10 | 一次最多合并几个 Segment |
| `max_merged_segment` | 5GB | 合并后 Segment 最大不超过此值 |

---

## 面试高频题

**Q：为什么 ES 的更新和删除比 MySQL 慢？**

> 因为 Segment 不可变。更新 = 标记旧文档删除 + 重新索引新文档（生成新 Segment）。删除 = 在 `.del` 文件中标记，物理清理要等段合并。真正的删除代价在合并时才发生。

**Q：Segment 过多有什么影响？如何优化？**

> 影响：查询需遍历所有 Segment → 延迟升高；元数据驻留堆内存 → GC 压力大甚至 OOM。
> 优化：① 只读索引执行 `_forcemerge` 将 Segment 压到最少；② 调大 `floor_segment` 减少小 Segment 生成；③ 历史索引冷热分离。

**Q：ES 的近实时搜索原理是什么？**

> 数据写入内存 Buffer → refresh（1s）→ 生成新 Segment 写入 FileSystem Cache → 此时可搜索。所以写入后最多等 1 秒就能搜到——这就是 NRT。translog 保证断电不丢数据。

**Q：`.del` 文件是什么？**

> 删除文档时 ES 不在 Segment 里物理删除，而是在 `.del` 文件中标记该文档为"已删除"。查询时跳过这些文档。只有段合并时才能真正清理掉。

---

## 关联知识点

- [[06-其他杂项/ES详解]] — ES 主文件：倒排索引原理、集群架构、面试要点

> 📎 **反链**：本文件是 [[06-其他杂项/ES详解]] 中写入流程与面试题部分的独立详解。
