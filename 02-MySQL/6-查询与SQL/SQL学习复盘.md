---
tags:
  - mysql
  - SQL
  - 窗口函数
  - GROUP BY
created: 2026-09-27
updated: 2026-09-27
---

# SQL 学习复盘（本次会话知识点整理）

> 本复盘基于本次刷题会话整理。⭐⭐⭐ 标记的是你曾出错或提问过的薄弱点，优先复习。

## 目录
1. WHERE 子句与基础筛选
2. IN / NOT IN 与多条件逻辑
3. 聚合函数（MAX / COUNT / AVG）
4. GROUP BY 与 HAVING
5. 多表 JOIN 与聚合统计
6. UNION / UNION ALL
7. ⭐⭐⭐ CASE 表达式（简单 CASE vs 搜索 CASE）
8. ⭐⭐⭐ DISTINCT 去重规则
9. ⭐⭐⭐ 次日留存率（自连接 + DATE_ADD）
10. ⭐⭐⭐ 字符串截取（SUBSTRING_INDEX 等）
11. ⭐⭐⭐ 窗口函数：累计求和（SUM OVER）
12. 题目歧义排查（结合表结构判断）
13. 实例题：每月播放 TOP3（窗口函数综合）
14. WITH (CTE) 与 ROW_NUMBER()
15. ⭐⭐⭐ 为什么 ranking 不能在同层 WHERE 筛
16. 实例题：按还款能力统计逾期比例（条件聚合）
17. GROUP BY 的完整规则

---

## 1. WHERE 子句与基础筛选

**真题还原**：`SELECT device_id, gender, age, university FROM user_profile WHERE university != '复旦大学';`

当时踩的坑：
- 写成了 `from user_profilewhere`——`FROM` 与 `WHERE` 之间丢了空格/换行，MySQL 把它当成错误的表名。
- `NOT IN` 忘记加括号：`NOT IN ('复旦大学')` 必须用括号包住值列表。

要点：
- `!=` 和 `<>` 都表示不等于。
- 关键字之间必须有空格：`FROM 表名 WHERE 条件`。

---

## 2. IN / NOT IN 与多条件逻辑

**真题还原**：

```sql
SELECT device_id, gender, age, university, gpa
FROM user_profile
WHERE university = '山东大学' AND gpa > 3.5
   OR university = '复旦大学' AND gpa > 3.8;
```

要点（⭐⭐⭐ 你问过“如果 and 在中间呢”）：
- **AND 优先级高于 OR**。上面的写法等价于：
  ```sql
  WHERE (gpa > 3.5 AND university = '山东大学')
     OR (gpa > 3.8 AND university = '复旦大学')
  ```
- 如果把 AND 换成 OR/调换结构，写成 `A OR B AND C OR D` 要一步步按优先级重新拆分。
- 跨学校的组合条件写成 AND 会要求同一条记录同时属于两所大学，不可能命中。
- 养成习惯：写复杂条件时**主动加括号**，避免优先级误读。

---

## 3. 聚合函数（MAX / COUNT / AVG）

**真题还原**：
- 复旦最高 GPA：`SELECT MAX(gpa) AS gpa FROM user_profile WHERE university = '复旦大学';`
- 男性人数与平均 GPA：`SELECT COUNT(1) AS male_num, ROUND(AVG(gpa), 1) AS avg_gpa ... WHERE gender='male';`

当时踩的坑：
- 把平均写成 `average(gpa)`——MySQL 的函数是 `AVG(gpa)`。
- 别名问题（⭐ 你问过“不 as 是啥”）：没有别名时列名显示为 `MAX(gpa)` / `AVG(gpa)`。`AS` 可以省略但别名必须写：`SELECT MAX(gpa) gpa`。

要点：
- 常用聚合：`COUNT / MAX / MIN / AVG / SUM`。
- 题目要求输出列名时，一定要起别名（`AS 别名` 或直接写别名）。
- `ROUND(x, d)` 保留 d 位小数。

---

## 4. GROUP BY 与 HAVING

**真题还原**：按性别和学校统计人数、平均活跃天数、平均提问数。

当时踩的坑：
- 写成 `GROUP BY university and gender`——GROUP BY 里要用**逗号**分隔多个分组列，`AND` 是逻辑运算符。
- ⭐ 子句顺序写反了：**GROUP BY 必须在 HAVING 之前**，且 WHERE 在 GROUP BY 之前。

正确顺序口诀：

```
SELECT → FROM → JOIN → WHERE → GROUP BY → HAVING → ORDER BY → LIMIT
```

要点：
- `WHERE` 过滤原始行；`HAVING` 过滤分组后的聚合结果。
- `HAVING` 里要写聚合表达式，如 `HAVING AVG(question_cnt) < 5 OR AVG(answer_cnt) < 20`。
- 用聚合后的别名去 ORDER BY 是允许的。

### ⭐ HAVING 的进阶语义（易错，反复追问）

**① 没有 `GROUP BY` 时，整张表是一个组**（**不是**"每条记录各自成一组"）：

```sql
SELECT AVG(Price) FROM Products HAVING AVG(Price) > 100;
```

- 全表当**一个组**算一次 `AVG(Price)`，`HAVING` 筛的是这个整体聚合结果 → **最多一行**，筛不出每件商品。
- 要找"价格高于全表平均价的商品"，用标量子查询先算全表均值、再逐行比：

```sql
SELECT * FROM Products
WHERE Price > (SELECT AVG(Price) FROM Products);
```

**② `HAVING` 里不能直接写"裸列 + 聚合"比较**：

```sql
SELECT product_id FROM Products
HAVING AVG(Price) > 100;   -- ❌
```

- 无 `GROUP BY` → 全表一个组，此时 `product_id` 既不在 `GROUP BY`、也没被聚合，**标准 SQL 报错**（无法确定这一个组该返回哪个 product_id）。
- 若加了 `GROUP BY product_id`：每个商品一组，`HAVING AVG(Price)` 筛的是**每组均值**——但这只是"单价 > 100"，**不是**"高于全表平均价"。

**③ 记忆**：`GROUP BY` 决定**怎么分组**，`HAVING` 筛**组**；逐行筛用 `WHERE`。想让每条记录各成一组，必须 `GROUP BY 唯一列`（如 `GROUP BY product_id, Price`），但逐行筛选用 `WHERE` 更清楚。

---

## 5. 多表 JOIN 与聚合统计

**真题还原**（你纠正出的最优解）：

```sql
SELECT university,
       COUNT(question_id) / COUNT(DISTINCT qpd.device_id) AS avg_answer_cnt
FROM question_practice_detail AS qpd
INNER JOIN user_profile AS up
    ON qpd.device_id = up.device_id
GROUP BY university
```

要点：
- 关联答题明细和用户表用 `ON qpd.device_id = up.device_id`。
- 平均答题量的巧妙算法：**总答题次数 ÷ 不同答题用户数**，一层 GROUP BY 搞定，不需要子查询再 AVG。
- 三表 JOIN（题目难度题）：同理分组用 `university + difficult_level`。
- ⭐ 不能在同一层嵌套聚合函数：`AVG(COUNT(...))` 会报错。

---

## 6. UNION / UNION ALL

**真题还原**：山东大学用户 △ 男性用户，不去重。

```sql
SELECT device_id, gender, age, gpa FROM user_profile WHERE university = '山东大学'
UNION ALL
SELECT device_id, gender, age, gpa FROM user_profile WHERE gender = 'male';
```

要点：
- 多个 SELECT 结果**上下拼接**，列数、顺序、类型要对应。
- `UNION` 自动去重；`UNION ALL` 保留全部（含重复行）。题目要求不去重 → 用 `UNION ALL`。

---

## 7. ⭐⭐⭐ CASE 表达式（重点：两个 CASE 你都翻车过）

**两种格式**：

**(1) 简单 CASE——比较同一个表达式“等于”某值**：

```sql
CASE gender
    WHEN 'male' THEN '男性'
    WHEN 'female' THEN '女性'
    ELSE '未知'
END
```

**不能**在 WHEN 里写 `age IS NULL OR age < 25` 这类条件。

**(2) 搜索 CASE——每个 WHEN 是完整条件**（本题应该用的）：

```sql
CASE
    WHEN age IS NULL OR age < 25 THEN '25岁以下'
    ELSE '25岁及以上'
END
```

**规则**：从上往下检查，命中第一个成立的条件即返回；无 ELSE 则返回 NULL。

**真题还原**：你最初写了：

```sql
case age
when age is null or age<25 then '25岁以下'   -- ❌ 简单CASE格式里不能放条件
when age >=25 then '25岁及以上'
end
```

两个错误：
1. `CASE age WHEN 条件` 是简单格式，检查的是 `age = 条件`，不能放宽泛条件 → 改成搜索格式（`CASE` 后不带值）。
2. 分类后忘了 `GROUP BY`，聚合必须先分组统计。

正确答案：

```sql
SELECT CASE WHEN age IS NULL OR age < 25 THEN '25岁以下'
            ELSE '25岁及以上'
       END AS age_cut,
       COUNT(*) AS number
FROM user_profile
GROUP BY age_cut;
```

**附带用法**：CASE 做条件计数——`SUM(CASE WHEN gender='male' THEN 1 ELSE 0 END)`。

**注意条件顺序**：`WHEN gpa >= 3.8` 要放在 `WHEN gpa >= 3.0` 前面，否则先命中宽条件。

---

## 8. ⭐⭐⭐ DISTINCT 去重规则（你反复追问的点）

**核心规则**：
- `SELECT DISTINCT a, b`：对 **(a, b) 两列组合**去重，不是只对第一列去重。MySQL **不能**在多列 SELECT 中让 DISTINCT 只作用于某一列。
- `COUNT(DISTINCT 列名)`：只对该列的去重值计数。

**记忆方法**：
| 写法 | 作用范围 |
|---|---|
| `SELECT DISTINCT a` | 只按 a 去重 |
| `SELECT DISTINCT a, b` | 按 (a,b) 组合去重 |
| `COUNT(DISTINCT a)` | 只统计 a 的不同值个数 |

**衍生问题**：`SELECT DISTINCT device_id, date` 里每个设备只想保留一条？必须先定义保留规则，比如最早日期：

```sql
SELECT device_id, MIN(date) AS date
FROM question_practice_detail
GROUP BY device_id;
```

数据库不会替你猜“保留哪个 date”。

---

## 9. ⭐⭐⭐ 次日留存率（你说过“看不懂”的那题）

**思路**：用户日 = 某用户在某天出现过。次日留存率 = 有次日记录的用户日 ÷ 所有用户日。

**结构拆解（三层）**：

```sql
SELECT COUNT(date2) / COUNT(date1) AS avg_ret
FROM (  -- 第二层：配对“今天”与“同一用户的下一天”
    SELECT DISTINCT qpd.device_id, qpd.date AS date1, u.date AS date2
    FROM question_practice_detail qpd
    LEFT JOIN (
        SELECT DISTINCT device_id, date
        FROM question_practice_detail
    ) u  -- 第一层：去重后的“用户-日期”表
    ON qpd.device_id = u.device_id
   AND DATE_ADD(qpd.date, INTERVAL 1 DAY) = u.date
) id_last_next_date
```

关键点：
- 第一层子查询 `DISTINCT device_id, date`：把“一天答多题”压缩成一个用户日。
- `DATE_ADD(date, INTERVAL 1 DAY)`：当天加一天，与子表匹配 → 找到该用户**下一天**是否活跃。
- `LEFT JOIN` 保留所有 date1，使分母完整；未再活跃的用户 date2 = NULL。
- `COUNT(date1)` 计算所有行；`COUNT(date2)` **忽略 NULL** → 正好是累计留存的分子。
- 第二层的 `DISTINCT` 修饰整个三列组合（见第 8 节）。

---

## 10. ⭐⭐⭐ 字符串截取（SUBSTRING_INDEX 参数含义你也追问过）

**四个常用函数**：

```sql
LEFT(str, n)                          -- 左边 n 个字符
RIGHT(str, n)                         -- 右边 n 个字符
SUBSTRING(str, pos [, len])           -- 从第 pos 个字符起（1 开始；负数从尾部数）
SUBSTRING_INDEX(str, delim, count)
```

示例：

```sql
SELECT SUBSTRING('abcdef', 2, 3);   -- bcd
SELECT SUBSTRING('abcdef', -2);     -- ef
SELECT LEFT('abcdef', 3);           -- abc
```

**SUBSTRING_INDEX 的 count 含义**（你追问过）：表示**遇到分隔符第 count 次**就停止：
- 正数：从左数第 count 个分隔符**之前**的内容。
- 负数：从右数第 |count| 个分隔符**之后**的内容。
- 分隔符不存在：返回整个字符串；count=0：返回空。

```sql
SELECT SUBSTRING_INDEX('a-b-c', '-', 2);   -- a-b
SELECT SUBSTRING_INDEX('a-b-c', '-', -1);  -- c
```

**真题还原 1**：从 `profile` 字段（如 `0,3258,male,28`）提取性别并统计：

```sql
SELECT SUBSTRING_INDEX(profile, ',', -1) AS gender,
       COUNT(1) AS number
FROM user_submit        -- 注意：题目表名是 user_submit，别写成 user_profile
GROUP BY gender;
```

**真题还原 2（牛客经典）**：`profile` 形如 `01325,female,27,male`，取出年龄并统计人数：

```sql
SELECT
    SUBSTRING_INDEX(SUBSTRING_INDEX(profile, ',', -2), ',', 1) AS age,
    COUNT(*) AS number
FROM user_submit
GROUP BY age;
```

**嵌套 SUBSTRING_INDEX 拆解**：
- 内层 `-2` → 从右取最后 2 个片段 → `27,male`
- 外层 `1` → 从左取第 1 个片段 → `27`

**易错点**：
- ❌ 把 `SUBSTRING()` 当 `SUBSTRING_INDEX()` 用，误传 `','` 当参数。`SUBSTRING(str, pos, len)` 需要的是起始位置和长度，不是分隔符。
- **表名写错**：先确认题目给的真实表名。

---

## 11. ⭐⭐⭐ 窗口函数：累计求和（SUM ... OVER）

**问题背景**：每日利润表 `daily_profits(profit_id, profit_date, profit)`，要求按日期逐日**累计**利润。

**常见误区**：想用 `COUNT(profit)` 或直接 `GROUP BY` 得到累计——但 `GROUP BY` 会把多行**合并成一行**，无法逐行输出"到当前行为止的累计值"。累计要**保留每一行**、同时看到"前面所有行的和"，这正是窗口函数的用途。

**正确写法**：

```sql
SELECT
    profit_id,
    profit_date,
    profit,
    SUM(profit) OVER (
        ORDER BY profit_date, profit_id
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS cumulative_profit
FROM daily_profits
ORDER BY profit_date, profit_id;
```

**`SUM(...) OVER (...)` 逐部分拆解**：

| 部分 | 含义 |
|---|---|
| `SUM(profit)` | 把利润加起来 |
| `OVER (...)` | 声明为**窗口计算**：保留每条原始记录，不合并行 |
| `ORDER BY profit_date, profit_id` | 先按日期排序；同一天再按 profit_id 定先后 |
| `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` | 计算范围 = 从第一行到当前行 |

**累计效果**（利润 100、150、200）：

| profit | 累计计算 | cumulative_profit |
|---|---|---|
| 100 | 100 | 100 |
| 150 | 100 + 150 | 250 |
| 200 | 100 + 150 + 200 | 450 |

`ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` 直观图：

```
日期      利润   累计范围      累计利润
1月1日    100    第 1 行        100
1月2日    150    第 1~2 行      250
1月3日    200    第 1~3 行      450
```

- `UNBOUNDED PRECEDING` → 从最开头那一行
- `CURRENT ROW` → 到正在计算的这一行

**⚠️ 排序优先级提醒**：`ORDER BY profit_date, profit_id` 是**先按日期，日期相同才按 profit_id**。若写成 `ORDER BY profit_id, profit_date`，日期顺序会被 ID 打乱，累计就不再是"按日期累计"了。

---

## 12. 题目歧义排查（结合表结构判断真实意图）

"每种产品每一天的累计利润"这类表述，容易让人以为要先按"商品 + 日期"汇总再累计。判断依据：

- **表里有没有商品标识字段？** 没有商品字段 → 不可能按商品分。
- **示例中每天是否多条记录？** 每天只有一条 → 直接按日期累计即可。

**经验**：结合**表结构**和**示例数据**判断题目真实意图，不要只看文字描述被带偏。

---

## 13. 实例题：音乐平台每月周杰伦歌曲播放 TOP3（窗口函数综合）

**题目**：统计 2022 年，18~25 岁用户播放的周杰伦歌曲中，每月播放次数排名前三的歌曲与次数。次数相同时按 `song_id` 升序排。

**相关表**：
- `play_log`：播放记录（user_id, song_id, fdate）
- `song_info`：歌曲信息（song_id, song_name, singer_name）
- `user_info`：用户信息（user_id, age）

**第一步：GROUP BY 统计每月每首歌播放次数**

`GROUP BY` 的作用：把指定字段值相同的行归成一组，再对每组做统计。

```sql
GROUP BY MONTH(pl.fdate), si.song_id, si.song_name
```

为什么要**同时**分组月份和歌曲？
- 只按月份分组 → 这个月所有歌混在一起；
- 只按歌曲分组 → 不同月份的播放混在一起；
- 两者一起 → 才能得到"每月的每首歌"的次数。

```
原始（分组前）              分组后
月份  song  song_name       月份  song  song_name    COUNT(*)
1     0     明明就           1     0     明明就        4
1     0     明明就
1     0     明明就
1     0     明明就
1     1     说好的幸福呢
...
```

**细节**：把 `song_id` 也放进 `GROUP BY`（即使不参与输出），用于并列次数时按 `song_id` 排序；`song_name` 放进来是让查询选出的歌名有明确归属。

**第二步：ROW_NUMBER() 按月排名**

```sql
ROW_NUMBER() OVER (
    PARTITION BY month
    ORDER BY play_pv DESC, song_id
) AS ranking
```

- `PARTITION BY month`：按月分组，每月编号从 1 重新开始；
- `ORDER BY play_pv DESC, song_id`：次数多的排前，并列时 song_id 小的排前。

```
排名  歌曲          播放次数
1     明明就         4
2     说好的幸福呢    4
3     大笨钟         2
```

**第三步：筛选前三 + 输出**

```sql
SELECT
    sub.month,
    sub.ranking,
    sub.song_name,
    sub.play_pv
FROM (
    SELECT
        MONTH(p.fdate) AS month,
        ROW_NUMBER() OVER (
            PARTITION BY MONTH(p.fdate)
            ORDER BY COUNT(*) DESC, s.song_id ASC
        ) AS ranking,
        s.song_name,
        COUNT(*) AS play_pv
    FROM play_log p
    JOIN song_info s ON s.song_id = p.song_id
    JOIN user_info u ON u.user_id = p.user_id
    WHERE s.singer_name = '周杰伦'
      AND u.age BETWEEN 18 AND 25
      AND YEAR(p.fdate) = 2022
    GROUP BY MONTH(p.fdate), s.song_name, s.song_id
) AS sub
WHERE sub.ranking <= 3
ORDER BY sub.month ASC, sub.play_pv DESC;
```

---

## 14. WITH (CTE) 与 ROW_NUMBER()

**`WITH ... AS`：临时结果表（CTE）**

把一段查询的结果临时命名，后续像查普通表一样使用：

```sql
WITH song_counts AS (
    SELECT ...   -- 统计每月每首歌的播放次数
)
SELECT * FROM song_counts;
```

- 括号里的查询**先执行**，结果临时叫 `song_counts`；
- **不是永久表**，本次 SQL 执行完名字就失效；
- 作用：把"先统计"和"再处理"分成两段，读起来清楚。

**`ROW_NUMBER()`：给每一行编号**

窗口函数，需要 `OVER (...)` 指定"怎么分组、按什么顺序编号"：

```sql
ROW_NUMBER() OVER (
    PARTITION BY month              -- 按月分组，每组重新编号
    ORDER BY play_pv DESC, song_id  -- 组内排序依据
)
```

---

## 15. ⭐⭐⭐ 为什么 ranking 不能在同一层 WHERE 里筛？

**报错根因**：SQL 执行顺序中，`WHERE` 阶段**早于** `SELECT` 阶段。`ranking = ROW_NUMBER()` 是在 `SELECT` 阶段才算出来的，`WHERE` 阶段它还不存在 → 报错"未知列 ranking"。

> 执行顺序回顾（见 [[SQL学习复盘#4. GROUP BY 与 HAVING]]）：
> `FROM → WHERE → GROUP BY → HAVING → SELECT（窗口函数在此算） → ORDER BY → LIMIT`

**两种等价写法**：

**写法 A：WITH + 两层 CTE**

```sql
WITH song_counts AS (   -- 统计
    ...
),
ranked AS (             -- 排名
    SELECT ..., ROW_NUMBER() OVER (...) AS ranking
    FROM song_counts
)
SELECT ... FROM ranked WHERE ranking <= 3 ...;
```

**写法 B：子查询（更紧凑）**

统计 + 排名放同一个子查询，用**外层** WHERE 筛前三。

**结论**：统计和排名可以合并为一层；但"**筛排名**"必须放到外层（CTE 或子查询之外）。

---

## 16. 实例题：按还款能力统计逾期比例（条件聚合）

**需求**：按 `ct.pay_ability` 分组，统计每组有逾期记录（`overdue_days IS NOT NULL`）的人数占比。

```sql
SELECT
    ct.pay_ability,
    CONCAT(
        ROUND(
            SUM(CASE WHEN lt.overdue_days IS NOT NULL THEN 1 ELSE 0 END)
            / COUNT(*) * 100,
            1
        ), '%'
    ) AS overdue_ratio
FROM customer_tb ct
JOIN loan_tb lt ON lt.customer_id = ct.customer_id
GROUP BY ct.pay_ability;
```

**关键解释**：`overdue_days` 不在 `GROUP BY` 里也能出现在 `SELECT`——因为它被**聚合函数包裹**了：

```sql
SUM(CASE WHEN lt.overdue_days IS NOT NULL THEN 1 ELSE 0 END)
```

每条记录先经 `CASE` 判断：逾期 → 1，未逾期 → 0；`SUM` 把 1/0 加总得到该组逾期记录数。

**示例**：某组 `overdue_days` 为 `NULL, 38, NULL, 5` → CASE 后 `0, 1, 0, 1` → `SUM = 2` → 该组逾期 2 人。

> 条件计数 `SUM(CASE WHEN 条件 THEN 1 ELSE 0 END)` 与 [[SQL学习复盘#7. ⭐⭐⭐ CASE 表达式（重点：两个 CASE 你都翻车过）]] 的附带用法一脉相承。

---

## 17. GROUP BY 的完整规则

**核心原则**：分组后，每组变成一行；`SELECT` 里的每个字段，都必须能**明确得到这一行的值**。

**合法出现的三种字段**：
1. `GROUP BY` 中出现的分组字段（或只依赖它们的表达式）；
2. 聚合函数结果：`COUNT / SUM / AVG / MIN / MAX`；
3. 由分组字段或聚合结果计算出的表达式。

**合法示例**：

```sql
SELECT
    ct.pay_ability,                        -- 分组字段 ✓
    COUNT(*) AS total,                     -- 聚合 ✓
    SUM(CASE WHEN lt.overdue_days IS NOT NULL
             THEN 1 ELSE 0 END) AS cnt     -- 字段被聚合包裹 ✓
FROM ...
GROUP BY ct.pay_ability;
```

**非法示例**：

```sql
SELECT ct.pay_ability, lt.overdue_days
FROM ...
GROUP BY ct.pay_ability;   -- ✗ 一组内 overdue_days 有多个不同值，无法确定显示哪一个
```

**⚠️ 别随意把字段加进 GROUP BY 来"修复"报错**：

```sql
GROUP BY ct.pay_ability, lt.overdue_days
```

这会**改变统计粒度**：原本每种还款能力一行，现在同一还款能力可能拆成多行。只有确实需要按这个细分维度统计时才这样分组。

**判断口诀**——一个字段能不能出现在 `SELECT`？
- 在 `GROUP BY` 里？→ 可以
- 在聚合函数里？→ 可以
- 都不在 → 该值在每个分组内是否唯一确定？不确定就不合法

**补充说明**：
- 某些数据库（如 MySQL 关闭 `ONLY_FULL_GROUP_BY`）会容忍未分组未聚合字段，但结果**不确定**、可读性差，不建议依赖；
- 开启 `ONLY_FULL_GROUP_BY` 时会**直接报错**；
- 严格遵守上述规则，能保证结果明确、跨库可移植。

---

## 18. ⭐ 每组最值对应的整行（子查询 + JOIN 回原表）

**经典题**：找出每所学校 GPA 最低的学生。

```sql
SELECT u.device_id, u.university, u.gpa
FROM user_profile AS u
JOIN (
    SELECT university, MIN(gpa) AS min_gpa
    FROM user_profile
    GROUP BY university
) AS m
    ON u.university = m.university
   AND u.gpa = m.min_gpa
ORDER BY u.university;
```

**要点**：
- ❌ 错误写法 `GROUP BY university HAVING gpa = MIN(gpa)`：`gpa`、`device_id` 都不是分组字段，`ONLY_FULL_GROUP_BY` 下直接报错，也无法可靠选中原始行。
- ✅ 通用套路：**子查询先算每组最值 → 再 JOIN 回原表匹配整行**。这是"每组最值行 / TopN"的经典解法（另一路是窗口函数 `ROW_NUMBER`，见 [[SQL学习复盘#13. 实例题：音乐平台每月周杰伦歌曲播放 TOP3（窗口函数综合）]]）。
- 若多人并列最低，会**全部返回**。

---

## 19. ⭐⭐⭐ LEFT JOIN 聚合统计：主表选择 + ON/WHERE 放置

**经典题**：统计 2021 年 8 月复旦大学每个用户的答题数和正确数（**没答题的人也要出现、计 0**）。

```sql
SELECT
    up.device_id,
    up.university,
    COUNT(qpd.id) AS question_cnt,
    SUM(CASE WHEN qpd.result = 'right' THEN 1 ELSE 0 END) AS right_question_cnt
FROM user_profile AS up
LEFT JOIN question_practice_detail AS qpd
    ON up.device_id = qpd.device_id
    AND qpd.date >= '2021-08-01'
    AND qpd.date < '2021-09-01'
WHERE up.university = '复旦大学'
GROUP BY up.device_id, up.university
ORDER BY up.device_id;
```

**四条易错**：
1. **主表选择**：从 `user_profile` 出发做 LEFT JOIN，才能保留没有 8 月答题记录的复旦用户（计数为 0）。
2. **右表过滤条件放 ON，不放 WHERE**：把 `qpd.date >= ...` 放 WHERE 会把未匹配行（date 为 NULL）整体过滤掉，LEFT JOIN 退化成 INNER JOIN。
3. **主表过滤条件放 WHERE**：`up.university='复旦大学'` 是筛主表，放 WHERE；若放 ON 只影响匹配、不排除其他学校的行。
4. **必须 GROUP BY**：SELECT 里有非聚合列（`device_id`）却不分组会报 `only_full_group_by`；`HAVING` 只过滤分组后的聚合值，别当 WHERE 用。

> 一句话：**ON 决定两表如何匹配；WHERE 决定匹配后哪些行保留。** LEFT JOIN 下这条区别直接决定"未匹配行"的生死。

---

## 20. ⭐⭐⭐ COUNT(1)/COUNT(*) vs COUNT(右表列)（LEFT JOIN 陷阱）

LEFT JOIN 时，右表无匹配会生成一行、右表字段全为 NULL：

| 写法 | 无匹配用户计数 | 原因 |
|---|---|---|
| `COUNT(1)` / `COUNT(*)` | **1**（错） | 常量 / 整行非 NULL，照样计入 |
| `COUNT(qpd.id)` | **0**（对） | 只统计非 NULL 的 `qpd.id` |

**结论**：LEFT JOIN + 统计右表记录数时，一定用 `COUNT(右表列)`，避免 `COUNT(*)` / `COUNT(1)` 把"没答题"的人误算成答了 1 题。

> 三个 COUNT 语义补充：`COUNT(*)` = 行数（含 NULL 行）；`COUNT(col)` = 该列非 NULL 的行数；`COUNT(DISTINCT col)` = 去重计数。

---

## 21. 三表 JOIN + 正确率（CASE WHEN 聚合）与 ON/WHERE 举例

**经典题**：统计浙江大学用户各难度平均正确率。

```sql
SELECT
    qd.difficult_level,
    ROUND(
        SUM(CASE WHEN qpd.result = 'right' THEN 1 ELSE 0 END) / COUNT(*),
        4
    ) AS correct_rate
FROM user_profile AS up
JOIN question_practice_detail AS qpd
    ON up.device_id = qpd.device_id
JOIN question_detail AS qd
    ON qpd.question_id = qd.question_id
WHERE up.university = '浙江大学'
GROUP BY qd.difficult_level
ORDER BY correct_rate ASC;
```

**要点**：
1. **语法**：`CASE WHEN ... THEN ... ELSE ... END`，不能漏 `END`。
2. **从哪张表 FROM**：只统计某校用户 → 从 `user_profile` 起、`WHERE` 筛校；只统计**实际答过题**的记录 → 用普通 `JOIN` 而非 `LEFT JOIN`。
3. **LEFT JOIN 的坑**：若用户表里有没答过题的人，`GROUP BY difficult_level` 会多出一行 `difficult_level = NULL`（输出 `None | None`）。用 `JOIN` 即可避免。

**ON vs WHERE 举例**（练习表 `2315(right)`、`2138(wrong)`；用户表 `2315=浙大`、`2138=北大`）：

条件放 **ON**——只影响匹配，北大记录不消失、只是 university 变 NULL，仍被计入正确率 → **结果错**：

| device | result | university |
|---|---|---|
| 2315 | right | 浙江大学 |
| 2138 | wrong | NULL |

条件放 **WHERE**——匹配后真正过滤，只剩浙大记录 → **结果对**。原理同 [[SQL学习复盘#19. ⭐⭐⭐ LEFT JOIN 聚合统计：主表选择 + ON/WHERE 放置]]。

---

## 22. ⭐ 整体统计：COUNT 不一定需要 GROUP BY

**经典题（SQL39）**：统计 2021 年 8 月练习题目的**不同用户数**和**答题总次数**。

```sql
SELECT
    COUNT(DISTINCT device_id) AS did_cnt,
    COUNT(*) AS question_cnt
FROM question_practice_detail
WHERE date >= '2021-08-01'
  AND date < '2021-09-01';
```

**要点**：
- `COUNT()` 是聚合函数，但**不分组时会把 WHERE 筛选后的所有行当作一个整体**，返回一行汇总。
- 加了 `GROUP BY device_id` 反而变成"每个用户一行"，不符题意——**只要整体汇总就别多加 GROUP BY**。
- 月份范围写法：`date >= '2021-08-01' AND date < '2021-09-01'`，起始日包含、下月 1 日不包含，避免数错月内天数（也不受大小月/闰月影响）。

---

## 23. 正则校验（REGEXP）

**经典题**：筛出合法手机号（① 连续 10 位数字；② 3-3-4 格式如 `123-456-7890`）。

```sql
SELECT *
FROM contacts
WHERE phone_number REGEXP '^[1-9][0-9]{9}$|^[1-9][0-9]{2}-[0-9]{3}-[0-9]{4}$'
ORDER BY id;
```

**正则语法速记**：
- `^` / `$`：锚定开头 / 结尾，确保**整个字符串**匹配。缺 `$` 时 `1234567890abc` 会被误匹配。
- `|`：或。
- `[1-9]`：一个 1~9 的字符；`[0-9]`：一个 0~9 的数字。
- `{n}`：重复 n 次，如 `[0-9]{3}` = 连续 3 位。
- 边界坑：`9-0-1-234-5678` 这类连字符位置错误的号码，只判"去掉 - 后有 10 位"的写法会误放行——必须用 `^...$` 精确锚定每一段格式才能挡住。

---

## 易错清单（考前速记）

1. 关键字之间要空格：`FROM user_profile WHERE ...`。
2. `NOT IN (...)` 值列表必须加括号。
3. `AVG()` 不是 `average()`；聚合结果按题目要求 `ROUND` + 别名。
4. `GROUP BY` 多列用逗号分隔；执行顺序 `WHERE → GROUP BY → HAVING → ORDER BY`。
5. 不要嵌套聚合函数，用“计数÷去重人数”或子查询代替。
6. 不去重拼接用 `UNION ALL`。
7. 带条件（IS NULL、比较、组合）的 CASE 一律用搜索格式：`CASE WHEN 条件 THEN ...`。
8. `SELECT DISTINCT a, b` 去重组合；单列去重计数用 `COUNT(DISTINCT col)`。
9. 留存率套路：LEFT JOIN + `DATE_ADD(x, INTERVAL 1 DAY)` + `COUNT(非NULL)/COUNT(全)`。
10. `SUBSTRING_INDEX` 的数字是**分隔符出现次数**，不是字符数。
11. 动手前先核对**真实表名**和**要求的输出列名**。
12. 累计求和用窗口函数 `SUM(x) OVER (ORDER BY ... ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)`，不是 `GROUP BY`（GROUP BY 会合并行）。
13. 窗口 `OVER` 里的 `ORDER BY` 决定累计/排名口径，字段顺序不能反（先日期后 ID）。
14. `ROW_NUMBER() OVER(PARTITION BY ... ORDER BY ...)` 算出的别名**不能在同层 WHERE 筛**，要套子查询/CTE 到外层再筛。
15. `WITH x AS (...)` 是临时结果表（CTE），执行完即失效，用来把"统计"和"排名/筛选"分层。
16. 条件计数用 `SUM(CASE WHEN 条件 THEN 1 ELSE 0 END)`；被聚合包裹的字段**无需**进 GROUP BY。
17. GROUP BY 判断口诀：SELECT 字段要么在 GROUP BY、要么被聚合包裹，否则非法；别乱加字段进 GROUP BY 改变粒度。
18. 审题先看**表结构 + 示例数据**，判断是否真需要按某维度分组，别被文字描述带偏。
19. `CASE WHEN ... THEN ... ELSE ... END` 不能漏 `END`；聚合"正确数"用 `SUM(CASE WHEN cond THEN 1 ELSE 0 END)`。
20. LEFT JOIN 三放置口诀：**主表过滤放 WHERE、右表匹配/时间范围放 ON、统计右表记录数用 `COUNT(右表列)`**（`COUNT(*)`/`COUNT(1)` 会把未匹配行误算成 1）。
21. 「每组最值对应的整行」用**子查询算最值 + JOIN 回原表**，别用 `GROUP BY ... HAVING col = MIN(col)`。
22. 只需整体汇总时不要多加 `GROUP BY`；不分组的 `COUNT` 会把筛选后所有行当作一个整体返回一行。
23. 日期区间用 `>= 开始日 AND < 下月 1 日`，别用"到月末"数天数（大小月/闰月易错）。
24. `REGEXP` 校验格式必须带 `^...$` 锚定整串，否则子串命中会误放行。

---

## 关联知识点

- [[SQL学习复盘#4. GROUP BY 与 HAVING]] — 子句执行顺序，本篇窗口函数筛选、GROUP BY 规则的前置基础
- [[SQL学习复盘#7. ⭐⭐⭐ CASE 表达式（重点：两个 CASE 你都翻车过）]] — 条件聚合 `SUM(CASE WHEN...)` 的源头用法
- [[mysql_八股整理]] — MySQL 八股系统整理
- [[IN和EXISTS]] — 子查询相关的执行差异，与"排名筛选必须放外层"的分层思路相通
