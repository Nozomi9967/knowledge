这个问题问得很好，核心在于理解"幻读"的定义范围。

## 先明确幻读的定义

幻读是指：**同一事务内，两次相同查询，返回的行集合不一致**（有新增或消失的行）。

幻读不要求查询加锁，**只要结果集变了就算幻读**。

---

## MVCC 如何解决幻读

MVCC 的快照读（`SELECT`，不加锁）通过 **ReadView** 机制实现：

事务开始后第一次快照读时，生成一个 ReadView，记录：

- 当前活跃事务 ID 列表
- 最大/最小事务 ID 边界

**后续所有快照读，都复用同一个 ReadView。**

```
T1 开始，生成 ReadView（活跃事务：{T1}）
    T2 INSERT 一行，COMMIT  ← T2 的修改对 T1 不可见（T2在ReadView生成之后提交）
T1 SELECT * → 看不到 T2 插入的行  ✓ 没有幻读
```

因为 T2 提交时 `trx_id > ReadView 记录的最大值`（或 T2 在 ReadView 的活跃列表里），这行数据对 T1 永远不可见。**不是加锁，而是根本就"看不见"。**

---

## 那 MVCC 什么情况下解决不了幻读？

**当你在同一个事务里混用了快照读和当前读**：

```sql
-- 事务 T1
SELECT * FROM t WHERE id > 10;        -- 快照读，返回 0 行

-- 事务 T2 此时 INSERT id=15, COMMIT

UPDATE t SET val = 1 WHERE id > 10;   -- 当前读！会看到 T2 插入的行，并更新它
SELECT * FROM t WHERE id > 10;        -- 快照读，现在能看到 id=15 了！（因为它的最新版本有 T1 自己的修改痕迹）
```

这时 MVCC 就失效了，需要 Next-Key Lock 来封住间隙，阻止 T2 的 INSERT。

---

## 总结

|场景|解决幻读的机制|
|---|---|
|纯快照读（只有 SELECT）|MVCC ReadView，"看不见"新数据|
|涉及当前读（UPDATE/FOR UPDATE/LOCK IN SHARE MODE）|Next-Key Lock，阻止新数据插入|
|混用快照读 + 当前读|需要 Next-Key Lock，MVCC 兜不住|

所以说 MVCC 解决幻读，不是靠"事务隔离锁"，而是靠**版本可见性**——你的快照在那个时间点就冻住了，后来的数据你根本看不到，自然不会幻读。