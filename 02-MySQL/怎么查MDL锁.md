

## 查看 MDL 锁

```sql
SELECT 
    object_type,
    object_schema,
    object_name,
    lock_type,
    lock_duration,
    lock_status,
    owner_thread_id
FROM performance_schema.metadata_locks
WHERE object_type = 'TABLE';
```

关键字段说明：

|字段|含义|
|---|---|
|`lock_type`|`SHARED_READ`（读锁）/ `EXCLUSIVE`（写锁）|
|`lock_status`|`GRANTED`（已持有）/ `PENDING`（阻塞等待中）|
|`owner_thread_id`|持有锁的线程 ID|

---

## 找到对应的会话并 kill

`owner_thread_id` 是 performance_schema 的内部线程 ID，需要转换成 processlist ID 才能 kill：

```sql
-- 通过 thread_id 找到 processlist_id
SELECT 
    t.processlist_id,
    t.processlist_user,
    t.processlist_time,
    t.processlist_info,
    m.lock_type,
    m.lock_status
FROM performance_schema.metadata_locks m
JOIN performance_schema.threads t ON m.owner_thread_id = t.thread_id
WHERE m.object_type = 'TABLE'
  AND m.lock_status = 'GRANTED';
```

找到长时间持有读锁的事务后：

```sql
KILL <processlist_id>;
```

---

## 你描述的阻塞场景

```
事务A: SELECT (MDL读锁，长时间未提交)
                    ↓
事务B: ALTER TABLE (申请MDL写锁，阻塞 PENDING)
                    ↓
事务C: SELECT (申请MDL读锁，也阻塞！)  ← 容易被忽视
```

事务C 被阻塞的原因是：写锁在队列里等待，后续读锁排在写锁后面，**防止写锁饥饿**。这也是为什么一个未提交的长事务可能导致整张表查询全部卡住，影响非常大。