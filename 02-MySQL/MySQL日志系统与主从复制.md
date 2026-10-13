---
tags:
  - mysql
  - 日志
  - 主从复制
created: 2026-08-01
updated: 2026-08-01
---

# MySQL 日志系统与主从复制

---

### Mysql有哪几种日志？

- **undo log**（回滚日志）：是 **InnoDB** 存储引擎层生成的日志，实现了事务的**原子性**，主要用于事务回滚和 MVCC
- **redo log**（重做日志）：是 **InnoDB** 存储引擎层生成的日志，实现了事务的**持久性**，主要用于掉电等故障恢复
- **binlog**（归档日志）：是 **Server** 层生成的日志，主要用于**数据备份**和**主从复制**

### undo log

#### 为什么需要undo log？

当我们增删改时，Mysql会隐性的开启事务，执行完一条语句是否提交事务，是由参数 `autocommit` 决定的，默认是开启的

如果一个事务运行过程中，Mysql发生了崩溃，事务还未提交，怎么回滚到事务开启前的数据呢？

如果我们在事务执行过程中，记录回滚时需要用到的信息到日志中，实现这一机制的就是**undo log**

- 当插入记录时，将记录的主键值记录下来，回滚时删除对应主键值的记录即可
- 当删除记录时，将记录的完整信息记录下来，回滚时新增记录即可
- 当修改记录时，将被更新列的旧值记录下来，回滚时更新回去即可

#### undo log 两大作用

1. **实现事务回滚**，保障事务的原子性。事务处理过程中，如果出现了错误或者用户执行了 ROLLBACK 语句，MySQL 可以利用 undo log 中的历史数据将数据恢复到事务开始之前的状态。
2. **实现 MVCC（多版本并发控制）关键因素之一**。MVCC 是通过 ReadView + undo log 实现的。undo log 为每条记录保存多份历史数据，MySQL 在执行快照读（普通 SELECT 语句）的时候，会根据事务的 Read View 里的信息，顺着 undo log 的版本链找到满足其可见性的记录

> 详细参考：[快照读时undo log和read view的具体作用](快照读时undo log和read view的具体作用.md)

### Buffer Pool

#### 为什么需要Buffer Pool？

> MySQL 的数据最终是保存在**磁盘**上的。如果每次读写数据都直接去跟磁盘打交道，那么磁盘的 I/O 瓶颈会直接把整个数据库的性能拉垮。
> 
> 为了解决这个问题，MySQL InnoDB 引擎在启动时，会向操作系统申请一块**连续的内存空间**，这块内存就是 **Buffer Pool**。

Mysql的数据都是存在磁盘中的，当我们需要更新数据时，先要把磁盘中的数据拿出来，放到内存中，再将内存中的数据修改，修改后的数据是直接写回磁盘好呢？还是放在内存好呢？

放在内存好，下次要查询此数据直接从内存拿即可

**为此，InnoDB专门设计了一个缓冲池（Buffer Pool），来提高数据库的性能**

有了Buffer Pool之后：

- 当需要查数据时，数据如果存在缓冲池，直接读缓冲池就可
- 当需要改数据时，如果数据在缓冲池，将缓冲池中的**数据所对应的数据页**修改，并标记为**脏页**即可，以后再挑选一个合适的时机由**后台线程**写回

#### 数据页是什么

MySQL InnoDB 以**页（Page）**为单位管理数据，默认每页 **16KB**，数据页就是存放表中行记录的基本单位。

```
磁盘
└── 表空间文件（.ibd）
    └── 数据页 Page（16KB）
        └── 行记录 row
```

#### Buffer Pool 和数据页的关系

```
磁盘上的数据页  ──读入──▶  Buffer Pool（内存）
                                 ↓ 修改
                            脏页（dirty page）
                                 ↓ 刷盘
                          磁盘上的数据页（更新）
```

Buffer Pool 是**内存缓冲区**，缓存的内容就是从磁盘读进来的数据页。对数据的修改先在 Buffer Pool 里的数据页上进行，这时候内存里的页和磁盘上的页不一致，称为**脏页**。

### redo log

#### 为什么需要redo log？

因为Buffer Pool是基于内存的，一旦断电，数据就会丢失

为了防止这种情况，当一条记录需要更新时，InnoDB会先更新内存，再将此数据页的修改信息写入redo log中

#### 为什么不直接将脏页写回？

事务提交时，脏页刷回磁盘（随机IO，很慢），但 写redo log（顺序IO，很快）。

```
事务提交
  ↓
redo log 写入磁盘 ✅（顺序IO，快）
  ↓
Buffer Pool 脏页之后慢慢刷盘（随机IO，异步）

如果中间崩溃 → 用 redo log 重放，恢复数据页
```

这就是 **WAL（Write-Ahead Logging）预写日志**机制。

#### 什么是redo log？

redo log是**物理日志**，记录了某个数据页做了什么修改

事务提交时，只需要将redo log持久化到磁盘即可

#### redo log 记录的是什么

```
redo log："对第X号数据页，偏移量Y处，写入了Z字节的数据"
```

==是磁盘上真实数据页的物理修改记录，不是 Buffer Pool 本身。==

#### 被修改 Undo 页面，需要记录对应 redo log 吗？

**需要的。** 开启事务后，InnoDB 层更新记录前，首先要记录相应的 undo log，如果是更新操作，需要把被更新的列的旧值记下来，也就是要生成一条 undo log，undo log 会写入 Buffer Pool 中的 Undo 页面。不过，在内存修改该 Undo 页面后，也是需要记录对应的 redo log，因为 undo log 也要实现持久性的保护

#### redo log 和 undo log 区别在哪？

这两种日志是属于 InnoDB 存储引擎的日志，它们的区别在于：

- **redo log** 记录了此次事务「修改后」的数据状态，记录的是更新之后的值，主要用于事务**崩溃恢复**，保证事务的**持久性**。
- **undo log** 记录了此次事务「修改前」的数据状态，记录的是更新之前的值，主要用于事务**回滚**，保证事务的**原子性**。

所以有了 redo log，再通过 WAL 技术，InnoDB 就可以保证即使数据库发生异常重启，之前已提交的记录都不会丢失，这个能力称为 **crash-safe（崩溃恢复）**。可以看出来，redo log 保证了事务四大特性中的持久性

#### redo log 要写到磁盘，数据也要写磁盘，为什么要多此一举？

写入 redo log 的方式使用了**追加操作**，所以磁盘操作是**顺序写**，而写入数据需要先找到写入位置，然后才写到磁盘，所以磁盘操作是**随机写**。

磁盘的「顺序写」比「随机写」高效的多，因此 redo log 写入磁盘的开销更小

#### 产生的 redo log 是直接写入磁盘的吗？

**不是的。** 实际上，执行一个事务的过程中，产生的 redo log 也不是直接写入磁盘的，因为这样会产生大量的 I/O 操作，而且磁盘的运行速度远慢于内存。

所以，redo log 也有自己的缓存——**redo log buffer**，每当产生一条 redo log 时，会先写入到 redo log buffer

#### redo log什么时候刷盘？

![[images/image-20260206181927386.png]]

redo log buffer 中的日志写入 redo log 文件（即刷盘），有以下几种触发场景：

**1. MySQL 后台线程每秒轮询**
- InnoDB 有一个后台线程，每隔 **1 秒** 执行一次 write + fsync
- 即使事务未提交，redo log buffer 中的日志也会被写入磁盘
- 这意味着**未提交事务的 redo log 也可能被持久化**

**2. redo log buffer 达到一半容量**
- 当 redo log buffer 占用空间达到 `innodb_log_buffer_size` 的 **50%** 时
- 后台线程会主动触发 write（写入 Page Cache），但不会 fsync（因为事务未提交）

**3. 事务提交时（`innodb_flush_log_at_trx_commit=1`）**
- 每次 COMMIT 都会触发 fsync
- 这是最频繁的刷盘场景，也是高并发下 IO 压力的主要来源

**4. 并发事务的"捎带"刷盘（也会触发组提交优化）**

redo log buffer 是连续写入的。当事务 B 提交并触发强制刷盘（`innodb_flush_log_at_trx_commit=1`）时，会将当前 buffer 中直到 B 为止的所有日志全部刷入磁盘。此时，若有并发事务 A 尚未提交，其产生的 redo log 只要已经写入 buffer，就会被 B **顺带/捎带** 刷入磁盘。

*（注：多事务同时提交时，MySQL 会通过**组提交（Group Commit）**机制将这些并发提交合并为一次 `fsync`，从而提升整体捎带效率。）*

**5. 正常关闭 MySQL 时**
- MySQL 关闭时会触发一次完整的 redo log 刷盘（包括 checkpoint）

#### innodb_flush_log_at_trx_commit 参数

![[images/image-20260206182024310.png]]

| 值 | 行为 | 数据安全性 | 性能 |
|---|---|---|---|
| **0** | 事务提交时只留在 redo log buffer，不写入磁盘，**每秒**由后台线程刷盘 | 崩溃可能丢 1 秒数据 | 最高 |
| **1**（默认） | 每次事务提交时**直接 fsync 刷盘**到磁盘文件 | 事务持久性有保障，不丢数据 | 最低（IO 开销最大） |
| **2** | 每次事务提交时只写入 OS 的 **Page Cache**，由 OS 异步刷盘 | MySQL 进程崩溃不丢，但 OS 崩溃可能丢数据 | 较高 |

**关键区别：**

- **值为 0**：redo log 只留在 redo log buffer，不进行 write，依赖后台线程每秒一次的刷盘。此时如果 MySQL 崩溃，会丢失 1 秒的数据。
- **值为 2**：redo log 写入 Page Cache（write），但不进行 fsync。如果 MySQL 崩溃，Page Cache 还在，数据不会丢；如果操作系统崩溃，Page Cache 丢失，数据会丢。
- **值为 1**：redo log 写入 Page Cache 后立即 fsync，保证数据不丢。

#### redo log 文件写满了怎么办？

![[images/image-20260206184605075.png]]
![[images/image-20260206184615669.png]]
![[images/image-20260206184622704.png]]

**redo log 的文件结构：**

redo log 是**固定大小、循环写入**的。InnoDB 默认配置一组 redo log 文件（称为 redo log Group），通常包含 2 个文件：`ib_logfile0` 和 `ib_logfile1`。

```
ib_logfile0    ib_logfile1
```

每个 redo log File 的大小是固定的（默认为 48MB，建议生产环境设置为 1GB ~ 2GB 每个）。

**写入机制：**

redo log 有两个关键指针：
- **write pos**：当前写入的位置，一边写一边往后移
- **checkpoint**：当前擦除的位置，擦除后往后移

```
redo log buffer
    ↓
┌────────────────────────────────────────┐
│  ib_logfile0  │  ib_logfile1          │
│  [checkpoint → write pos → 循环...]   │
└────────────────────────────────────────┘
```

当 `write pos` 追上了 `checkpoint`，表示 redo log 已经写满，此时 **InnoDB 必须强制刷脏页**，将 checkpoint 往前推进，释放空间。

**写满的后果：**
- 系统**不能再接受新的更新请求**，所有更新操作会被阻塞
- 这会导致 **QPS 暴跌**
- 建议：redo log 不要设置太小。如果是几 TB 的磁盘，建议直接设置 **4 个文件、每个文件 1GB**。太小会导致频繁强制刷盘，WAL 机制的优势就发挥不出来了。

### binlog

#### binlog是什么？

binlog是 **Server层** 生成的二进制日志

它记录了所有数据库表结构变更和表数据修改的日志

#### redo log和binlog的区别

1. **适用对象不同**
   - redo log 只适用于 **InnoDB**
   - binlog 适用于所有存储引擎，因为它是在 Server 层

2. **文件格式不同**
   - redo log 是**物理日志**，记录"在某个数据页上做了什么修改"
   - binlog 是**逻辑日志**，记录的是语句或行级的逻辑变更。binlog 有三种记录格式：**STATEMENT / ROW / MIXED**，5.7 默认 statement、8.0 默认 row。三者的区别、取舍和面试话术见 [[binlog三种格式（statement-row-mixed）]]

3. **写入方式不同**
   - redo log 是**循环写**，写满就重头写
   - binlog 是**追加写**，写满就创建新的文件

   > 你的质疑是对的，但需要区分一下"追加写"的含义。
   >
   > redo log 的写入方式确实不是纯粹的追加写，而是**循环覆盖写**。它有固定大小的日志文件组（比如两个文件 ib_logfile0、ib_logfile1），写满之后会回到头部覆盖旧数据，所以不能无限保留历史记录。
   >
   > 你可能觉得它是"追加写"，是因为在**单次写入行为**上，redo log 确实是顺序往后追加的，不是随机写。但这只是物理 I/O 层面的特性，跟 binlog 那种"写满就新建文件、历史永久保留"的追加语义是不同的。
   >
   > 所以原文的表述没有错，关键在于：
   >
   > - redo log：固定空间，循环覆盖，旧数据会被淘汰
   > - binlog：无限扩展，追加新文件，历史完整保留
   >
   > 这也正好解释了用途的差异——redo log 只需要保留"还没刷盘的脏页"对应的日志，够崩溃恢复用就行；binlog 需要完整历史，才能支持主从复制和按时间点还原。

4. **用途不同**
   - redo log 用于**断电故障恢复**
   - binlog 用于**备份恢复**和**主从复制**

![[images/image-20260206201022973.png]]

#### binlog什么时候刷盘？

**事务执行时，先把日志写到 `binlog cache` 中，事务提交时，再把 binlog cache 写到 binlog 文件中**

*binlog cache 每个线程都有一份*

**binlog文件是操作系统文件缓存，还需要持久化到磁盘**

Mysql提供 `sync_binlog` 来控制binlog持久化到磁盘上的频率

- `sync_binlog=0`：每次事务提交都只 **write**（binlog cache写到binlog文件），什么时候 **fsync**（binlog文件持久化到磁盘）由操作系统决定
- `sync_binlog=1`：每次事务提交先write，然后马上fsync
- `sync_binlog=N`（N&gt;1）：每次事务提交都write，累计N个事务后fsync

一般设置为 100 ~ 1000 中的某个数值

### update语句执行流程

![[images/image-20260206213538016.png]]

### 两阶段提交

#### 为什么需要两阶段提交？

![[images/image-20260206215051245.png]]

两阶段提交是为了解决 **redo log**（InnoDB 引擎日志）和 **binlog**（Server 层日志）之间的**逻辑一致性问题**。

**反证法说明：**

| 场景      | 先写 redo log 后写 binlog                                                           | 先写 binlog 后写 redo log                                         |
| ------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 崩溃发生在中间 | redo log 已写但 binlog 没写完 → 主库恢复后数据正确（因为 redo log 能恢复），但从库用 binlog 恢复时会**缺失这次更新** | binlog 已写但 redo log 没写完 → 主库认为事务无效，但从库用 binlog 恢复时会**多出一次更新** |
| **结果**  | **主备数据不一致**                                                                     | **主备数据不一致**                                                   |


两阶段提交让"所有人都说 ok 了，再一起提交"，保证了 redo log 和 binlog 的一致性。

#### 两阶段提交的过程

两阶段提交将 redo log 的写入拆成 **Prepare** 和 **Commit** 两个步骤，中间插入 binlog 的写入：

![[images/image-20260206220117584.png]]
![[images/image-20260206220126370.png]]

```
1. 执行器调用 InnoDB 引擎，修改内存中的数据
2. 写入 redo log（Prepare 阶段）✅
3. 写入 binlog ✅
4. 写入 redo log（Commit 阶段）✅
```

具体流程：

1. **Prepare 阶段**：InnoDB 将修改记录写入 redo log，并将 redo log 状态设置为 Prepare
2. **写 binlog**：Server 层将修改记录写入 binlog，并完成刷盘
3. **Commit 阶段**：InnoDB 将 redo log 状态设置为 Commit，表示事务已提交

#### 异常重启会出现什么情况？

![[images/image-20260206220416207.png]]
![[images/image-20260206220428303.png]]

崩溃恢复时，MySQL 会扫描 redo log，按以下规则判断：

| redo log 状态 | binlog 情况 | 恢复行为 |
|---|---|---|
| 有 **Prepare** + 有 **Commit** 标识 | — | ✅ **直接提交事务** |
| 只有 **Prepare**（无 Commit） | binlog **存在且完整** | ✅ **提交事务** |
| 只有 **Prepare**（无 Commit） | binlog **不存在或不完整** | ❌ **回滚事务** |

**时刻 A**（redo log Prepare 后、binlog 写入前崩溃）：
- binlog 还没写 → 事务**回滚**
- binlog 不会传到备库，主备一致

**时刻 B**（binlog 写完、redo log Commit 前崩溃）：
- redo log 只有 Prepare，但 binlog 已写完且完整
- 按规则**提交事务**
- 原因：binlog 已经写入了，之后会被从库使用，主库也必须提交才能保持主备一致

**为什么 `Prepare` 状态的 redo log 加上完整的 binlog 也要提交？**

- **逻辑一致性**：因为 binlog 一旦写完并落盘，它就会被从库（Slave）读取并执行。
- **必须提交**：如果此时主库重启后因为 redo log 是 `Prepare` 就选择回滚，那么主库就没有这笔数据，而从库却有了。
- **结论**：为了保证主从数据绝对一致，只要 binlog 写入成功了，主库重启后就必须把对应的 `Prepare` 事务提交掉。

---

## 主从复制

### 主从复制是如何实现的？

主从复制是依赖 **binlog** 实现的，也就是将数据库上发生的所有变化以二进制的形式保存到磁盘，复制也就是将binlog的数据从主库传输到从库上

这个复制过程是**异步**的，也就是说主库上的事务不会阻塞等待复制binlog的线程同步完成

**主从复制的流程如下：**

主库写入binlog → 从库创建一个专门的 **IO线程** 连接主库的 **log dump线程** → 接收binlog并写入 **relay log（中继日志）** 中 → 从库再创建一个 **SQL线程** 从 relay log 中读取binlog来回放

### 从库是不是越多越好？

**不是**，从库越多，从库连接上来的IO线程越多，主库也需要提供相同数量的log dump线程，对主库资源消耗较高

### Mysql主从复制还有哪些模型？

- **同步复制**：提交事务的线程需要等待**所有从库**的复制成功响应，才能返回客户端结果
- **异步复制**：不需要等待从库复制成功，直接返回客户端结果
- **半同步复制**：事务线程不用等待所有从库复制成功，只需**一半**从库复制成功，就可以返回客户端结果

---

## 关联知识点

- [[MySQL事务与隔离级别]] — undo log 保证原子性，redo log 保证持久性
- [[MySQL索引]] — 数据页是索引的持久化载体
- [[MySQL查询执行流程]] — update 语句执行流程的上下文
- [[MySQL锁机制]] — 行锁的 undo log 写入依赖 redo log 保证持久性

---

## 附：参考来源

- [字节面试：加了什么锁，导致死锁的？ | 小林coding](https://xiaolincoding.com/mysql/lock/show_lock.html)
