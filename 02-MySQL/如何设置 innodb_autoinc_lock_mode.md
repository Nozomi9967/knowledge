---
tags:
  - mysql
created: 2026-06-28
updated: 2026-06-28
---
## 设置方式

**方式一：配置文件（永久生效）**

在 `my.cnf` 或 `my.ini` 中添加：

```ini
[mysqld]
innodb_autoinc_lock_mode = 2
```

然后重启 MySQL 生效。

---

**方式二：启动参数（临时）**

```bash
mysqld --innodb_autoinc_lock_mode=2
```

---

## 注意

这个参数**不支持运行时动态修改**，必须重启才能生效：

```sql
-- 这样会报错
SET GLOBAL innodb_autoinc_lock_mode = 2;  -- ❌ 不支持
```

---

## 三个值的含义顺带说一下

|值|模式|说明|
|---|---|---|
|`0`|传统模式|每次 INSERT 都加表级 AUTO-INC 锁，并发差|
|`1`|连续模式|普通 INSERT 用轻量锁，批量 INSERT 用表锁，MySQL 8.0 前默认|
|`2`|交错模式|完全不用表锁，并发最好，MySQL 8.0 后默认，但 binlog 需用 row 格式|

查看当前值：

```sql
SHOW VARIABLES LIKE 'innodb_autoinc_lock_mode';
```