---
tags:
  - mysql
  - sql
  - join
  - 面试题
created: 2026-10-14
updated: 2026-10-14
source: 用户提问答疑（2026-10-14）
---

# JOIN、LEFT JOIN、RIGHT JOIN 的区别

> **一句话结论：** `JOIN` 返回两表都匹配的行（交集），`LEFT JOIN` 保留左表全部行，`RIGHT JOIN` 保留右表全部行，无匹配的一侧填充 NULL。

---

## 一、示例数据

假设有两张表：部门表 `dept` 和员工表 `emp`。

```sql
CREATE TABLE dept (
    id   INT PRIMARY KEY,
    name VARCHAR(20)
);

CREATE TABLE emp (
    id      INT PRIMARY KEY,
    name    VARCHAR(20),
    dept_id INT
);

INSERT INTO dept VALUES (1, '研发部'), (2, '市场部'), (3, '财务部');
INSERT INTO emp  VALUES (1, '张三', 1), (2, '李四', 1), (3, '王五', 2), (4, '赵六', NULL);
```

其中，**财务部（id=3）暂时没有员工**，**赵六（dept_id 为 NULL）没有分配部门**。

---

## 二、三种 JOIN 对比

### 1. INNER JOIN（或直接写 JOIN）—— 交集

只返回两边**能够匹配上**的记录。财务部和赵六都不会出现。

```sql
SELECT e.name AS 员工, d.name AS 部门
FROM emp e
JOIN dept d ON e.dept_id = d.id;
```

| 员工 | 部门 |
| ---- | ------ |
| 张三 | 研发部 |
| 李四 | 研发部 |
| 王五 | 市场部 |

### 2. LEFT JOIN（LEFT OUTER JOIN）—— 以左表为准

左表的**每一行都会保留**，右表找不到匹配时右表字段显示为 NULL。因此赵六会出现，部门为空。

```sql
SELECT e.name AS 员工, d.name AS 部门
FROM emp e
LEFT JOIN dept d ON e.dept_id = d.id;
```

| 员工 | 部门 |
| ---- | ------ |
| 张三 | 研发部 |
| 李四 | 研发部 |
| 王五 | 市场部 |
| 赵六 | NULL |

### 3. RIGHT JOIN —— 以右表为准

逻辑与 LEFT JOIN 相反，**右表每一行都保留**。财务部没有员工，因此员工字段为 NULL。

```sql
SELECT e.name AS 员工, d.name AS 部门
FROM emp e
RIGHT JOIN dept d ON e.dept_id = d.id;
```

| 员工 | 部门 |
| ---- | ------ |
| 张三 | 研发部 |
| 李四 | 研发部 |
| 王五 | 市场部 |
| NULL | 财务部 |

---

## 三、两个关键点

### 1. RIGHT JOIN 总能改写为 LEFT JOIN

`RIGHT JOIN` **总可以改写为 `LEFT JOIN`**，只需**交换两表的位置**即可，结果完全一致。因此实际开发中多数团队**只用 `LEFT JOIN`**，保持语句阅读习惯统一。

### 2. LEFT JOIN 后 WHERE 过滤右表会退化成 INNER JOIN【易错】

如果在 `LEFT JOIN` 之后用 `WHERE` 对**右表字段**做条件过滤，例如 `WHERE d.name = '研发部'`，那些右表为 NULL 的行会被过滤掉，`LEFT JOIN` **实际上退化为了 `INNER JOIN`**。

如果需要保留左表所有行，应把条件写在 **`ON` 子句**中：

```sql
-- ❌ 退化成 INNER JOIN：赵六（d.name 为 NULL）被过滤掉
SELECT e.name, d.name FROM emp e
LEFT JOIN dept d ON e.dept_id = d.id
WHERE d.name = '研发部';

-- ✅ 条件放 ON，左表所有行仍保留
SELECT e.name, d.name FROM emp e
LEFT JOIN dept d ON e.dept_id = d.id AND d.name = '研发部';
```

> [!warning] ON 与 WHERE 的区别
> `ON` 决定**如何连接**（不匹配的右表补 NULL）；`WHERE` 决定**连接后如何过滤**（会剔除补 NULL 的行）。要保留左表全集，过滤条件必须放 `ON`。

---

## 四、FULL OUTER JOIN

**MySQL 原生并不支持 `FULL OUTER JOIN`**。如果需要同时保留两边的未匹配行，可以用 `LEFT JOIN` 与 `RIGHT JOIN` 通过 **`UNION`** 合并实现。

---

## 五、口述版参考答案

> 这三个的核心区别就在「以谁为准」。`JOIN` 也就是内连接，只返回两表都能匹配上的行，是交集。`LEFT JOIN` 以左表为准，左表每一行都保留，右表匹配不上的字段补 NULL。`RIGHT JOIN` 反过来，以右表为准，右表每一行都保留。举个子，部门和员工两张表，财务部没员工、赵六没部门：内连接会把这两个都丢掉；左连接会保留赵六、部门显示 NULL；右连接会保留财务部、员工显示 NULL。实际开发里 `RIGHT JOIN` 都能改写成 `LEFT JOIN`，交换两表位置就行，所以团队一般统一用 `LEFT JOIN`。有个容易踩的坑：`LEFT JOIN` 之后用 `WHERE` 过滤右表的字段，会把补 NULL 的行筛掉，左连接就退化成内连接了；要保留左表全集，过滤条件得写在 `ON` 里。另外 MySQL 不支持 `FULL OUTER JOIN`，要用 `LEFT JOIN` 和 `RIGHT JOIN` 加 `UNION` 拼出来。

---

## 关联知识点

- [[SQL学习复盘]] — 多表 JOIN 与聚合统计、ON/WHERE 放置陷阱
- [[IN和EXISTS]] — 子查询与关联查询的选择
- [[MyBatis复杂SQL性能优化经验]] — JOIN 批量查询避免 N+1
- [[N+1查询问题详解]] — JOIN 一次性查出 vs 循环查询
- [[mysql_八股整理]] — MySQL 八股总览
- [[MySQL索引]] — JOIN 驱动表与被驱动表、连接字段走索引
