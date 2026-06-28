---
tags:
  - mysql
  - mvcc
  - 幻读
  - 间隙锁
created: 2026-06-18
updated: 2026-06-18
---

# 为什么 MVCC 无法完全避免幻读现象

## 先明确概念

- **不可重复读**：同一事务里，两次读**同一行**，结果不同（被其他事务修改了）
- **幻读**：同一事务里，两次查询**同一范围**，第二次多出了（或少了）几行

可重复读（RR）通过 **MVCC** 解决了不可重复读，但幻读没完全解决。

---

## 为什么 MVCC 解决不了幻读

MVCC 的原理是：事务开始时创建一个**快照（Read View）**，后续普通 SELECT 都读这个快照，看不到其他事务新插入的数据。

看起来好像解决了？但问题出在**当前读**：

```sql
-- 事务A
SELECT * FROM student WHERE age > 18;         -- 快照读，读到2条

-- 事务B 此时插入一条 age=20 的记录并提交

-- 事务A
UPDATE student SET name='x' WHERE age > 18;   -- 当前读！影响了3行
SELECT * FROM student WHERE age > 18;         -- 快照读，变成3条了！
```

==`UPDATE`/`DELETE`/`SELECT ... FOR UPDATE` 都是**当前读**，会读最新数据，执行后快照也随之更新，幻行就出现了。==

---

## 根本原因

|读类型|方式|是否受 MVCC 保护|
|---|---|---|
|快照读|普通 SELECT|✅ 读旧版本，看不到新插入行|
|当前读|UPDATE/DELETE/SELECT FOR UPDATE|❌ 读最新数据，能看到新插入行|

MVCC 只保护快照读，但当前读绕过了快照，所以幻读依然可能发生。

---

## InnoDB 的解决方案

InnoDB 在可重复读下用 **间隙锁（Gap Lock）** 来防止幻读——锁住范围，阻止其他事务在这个范围内插入新行：

```sql
SELECT * FROM student WHERE age > 18 FOR UPDATE;
-- 不只锁住已有的行，还锁住 age>18 这个间隙
-- 其他事务无法插入 age>18 的新记录
```

所以严格来说，InnoDB 的可重复读通过 MVCC + 间隙锁，**在大多数场景下避免了幻读**，但并非完全杜绝（上面那种先快照读再当前读的场景仍会出现）。

---

## 间隙锁的细节

### 间隙锁加锁时机

间隙锁是在**当前读的语句执行时**才加上的，不是事务一开始就有。

```sql
-- 事务A
SELECT * FROM student WHERE age > 18;         
-- 普通快照读，不加任何锁，没有间隙锁

-- 事务B 此时插入 age=20 的记录并提交  ← 此时可以成功，因为没有间隙锁

-- 事务A
UPDATE student SET name='x' WHERE age > 18;   
-- 当前读，此时才加间隙锁 + 行锁
-- 但已经晚了，事务B的数据已经插进来了

SELECT * FROM student WHERE age > 18;         -- 看到幻行
```

如果想一开始就防住，事务A 开头就用当前读：

```sql
-- 事务A
SELECT * FROM student WHERE age > 18 FOR UPDATE;
-- 立刻加上间隙锁，锁住 age>18 的范围

-- 事务B 想插入 age=20 ← 阻塞！插不进来

-- 事务A 后续无论怎么读，都不会出现幻行
```

| 时机 | 是否有间隙锁 |
|---|---|
| 事务开始时 | ❌ |
| 执行普通 SELECT | ❌ |
| 执行 SELECT FOR UPDATE / UPDATE / DELETE | ✅ 此时才加 |

所以间隙锁是**按需加锁**，不是事务级别的自动保护。

---

### 什么时候释放间隙锁？

间隙锁（包括 InnoDB 中的所有行锁、下一个键锁 Next-Key Lock）都是在当前事务结束（提交 `COMMIT` 或回滚 `ROLLBACK`）时才统一释放的。

---

## 关联知识点

- [[MVCC 多版本并发控制详解]]
- [[InnoDB 锁机制详解]]
- [[MySQL 事务隔离级别详解]]
- [[当前读与快照读]]

## 为什么 MVCC 解决不了幻读