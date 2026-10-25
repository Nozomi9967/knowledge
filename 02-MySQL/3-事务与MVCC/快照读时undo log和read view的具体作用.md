---
tags:
  - mysql
created: 2026-06-28
updated: 2026-06-28
---
## undo log 版本链

每行记录都有两个隐藏字段：

```
trx_id     — 最后一次修改这行的事务ID
roll_pointer — 指向 undo log 中上一个版本
```

假设一行数据被多个事务修改过，版本链长这样：

```
当前记录 [name=丁, trx_id=40] 
    ↓ roll_pointer
[name=丙, trx_id=30]
    ↓ roll_pointer
[name=乙, trx_id=20]
    ↓ roll_pointer
[name=甲, trx_id=10]
```

---

## Read View 里有什么

```
m_ids          — 创建 Read View 时，当前活跃的事务ID列表（已启动但未提交）
min_trx_id     — m_ids 中最小的事务ID
max_trx_id     — 下一个待分配的事务ID（即当前最大事务ID + 1）
creator_trx_id — 创建这个 Read View 的事务ID
```

---

## 可见性判断规则

拿版本链中某个版本的 `trx_id` 和 Read View 比较：

```
trx_id < min_trx_id
→ 该版本在 Read View 创建前就已提交 ✅ 可见

trx_id >= max_trx_id
→ 该版本在 Read View 创建后才开启 ❌ 不可见

min_trx_id <= trx_id < max_trx_id
→ 再判断 trx_id 是否在 m_ids 里
    在 m_ids 里 → 还未提交 ❌ 不可见
    不在 m_ids 里 → 已提交 ✅ 可见
```

---

## 完整执行流程

```
1. 执行快照读，找到这行的当前版本

2. 取出该版本的 trx_id，按上面规则判断可见性

3. 可见 → 返回这个版本的数据，结束

4. 不可见 → 顺着 roll_pointer 找上一个版本

5. 重复 2-4，直到找到可见版本

6. 如果链到头都不可见 → 返回空
```

---

## 举个例子

Read View：`m_ids=[20,30]`，`min=20`，`max=40`，`creator=25`不在链里

```
[name=丁, trx_id=40] → 40 >= max_trx_id=40 ❌ 不可见，往下找
[name=丙, trx_id=30] → 30 在 m_ids 里 ❌ 未提交，往下找  
[name=乙, trx_id=20] → 20 在 m_ids 里 ❌ 未提交，往下找
[name=甲, trx_id=10] → 10 < min_trx_id=20 ✅ 可见，返回「甲」
```

所以快照读返回的是 `name=甲` 这个历史版本，完全不影响其他事务的并发修改。