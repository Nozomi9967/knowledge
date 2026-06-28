好问题。这里的**内部节点**指的是 **quicklist 这个双向链表的每一个链表节点（listNode）**。

### quicklist 的结构（宏观到微观）

```
quicklist（双向链表）
  │
  ├─ 节点1 (quicklistNode)
  │    └─ 数据容器: 原先用 ziplist，7.0 后改用 listpack
  │       ├─ 元素1
  │       ├─ 元素2
  │       └─ 元素3
  │
  ├─ 节点2 (quicklistNode)
  │    └─ 数据容器: listpack
  │       ├─ 元素4
  │       └─ 元素5
  │
  └─ 节点3 (quicklistNode)
       └─ 数据容器: listpack
          ├─ 元素6
          ├─ 元素7
          └─ 元素8
```

### 具体解释

- **quicklist** 本身是一个双向链表
- **内部节点** 就是链表的每个节点 —— 即 `quicklistNode`
- 每个 `quicklistNode` 本身不存储单个元素，而是包含一个**压缩容器**
  - **Redis 3.2 ~ 6.2**：这个容器是 **ziplist**（压缩列表）
  - **Redis 7.0+**：这个容器换成了 **listpack**（紧凑列表）

所以说 "内部节点从 ziplist 换成了 listpack"，意思就是每个 `quicklistNode` 里装数据的那个容器，从 ziplist 换成了 listpack。

### 为什么这么改？

因为 ziplist 有**连锁更新（cascade update）**的问题——往中间插入数据时，可能引发后面所有节点的长度编码连锁扩容，性能 O(n²)。**listpack 每个元素都自带长度信息、互不依赖**，从根本上消除了连锁更新。

可以用一个类比理解：

> quicklist 是一列火车，每节车厢（内部节点）装一批乘客（元素）。
> - 3.2~6.2：每节车厢用的是**老式货架（ziplist）**，放一个箱子可能要挪动后面所有箱子
> - 7.0+：换成了**独立储物格（listpack）**，每个格子独立，存取互不影响

> 想深入了解 listpack 为何能消除连锁更新，看 [[listpack]]；listpack 替代的对象是 [[ziplist]]。


