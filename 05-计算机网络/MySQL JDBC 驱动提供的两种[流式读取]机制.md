

**`useCursorFetch=true` 和 `setFetchSize(Integer.MIN_VALUE)` 是 MySQL JDBC 驱动提供的两种"流式读取"机制，目的是让 50w 条数据不一次性塞进客户端内存，而是按需分批从服务端拉取。**

  

---

  

### **背景：默认行为为什么有问题**

  

MySQL Connector/J 默认情况下，执行 `SELECT * FROM user` 时：

  

1. 驱动会**一次性把全部结果集**从服务端读到客户端 JVM 内存里（存在一个内部 `ArrayList` 中）；

2. 然后你调用 `rs.next()` 只是在本地数组上游标移动。

  

后果：

- 50w 条 `User`，每行假设 200 字节，光 JDBC 缓冲就吃掉 100MB+；

- 启动瞬间堆内存陡增，可能触发 Full GC，甚至 OOM；

- 网络传输和 JVM 反序列化都堆在一个时间点，CPU/IO 瞬时打满。

  

要让驱动**边读边给你**，必须显式开启流式模式，MySQL 提供了两条路。

  

---

  

### **方式一：`useCursorFetch=true` + `setFetchSize(N)`（服务端游标）**

  

#### **怎么开**

  

在 JDBC URL 上加参数：

  

```

jdbc:mysql://host:3306/db?useCursorFetch=true

```

  

然后代码里：

  

```java

PreparedStatement ps = conn.prepareStatement("SELECT * FROM user");

ps.setFetchSize(1000); // 每次从服务端拉 1000 行

ResultSet rs = ps.executeQuery();

while (rs.next()) { ... }

```

  

#### **底层原理**

  

- MySQL 服务端为这次查询创建一个**服务端游标（server-side cursor）**，把结果集物化（materialize）到服务端的临时表或内存中；

- 客户端每次通过协议命令 `COM_STMT_FETCH` 申请 `fetchSize` 行；

- 客户端 JVM 里**任意时刻只有 `fetchSize` 行数据**。

  

#### **优点**

- 内存可控，`fetchSize` 你说了算；

- **可以中途取消**、可以与同一个连接上的其他语句穿插（连接没被独占）；

- 支持随机访问类操作（虽然一般不用）。

  

#### **缺点**

- 服务端要把结果集物化，对**大结果集会写临时表**，反而增加 MySQL 服务器 IO；

- 每个 fetch 都是一次网络往返，`fetchSize` 太小（如默认 100）反而慢；

- 对 MySQL 服务端有额外资源开销。

  

---

  

### **方式二：`setFetchSize(Integer.MIN_VALUE)`（客户端流式）**

  

#### **怎么开**

  

URL 不需要特殊参数，但代码必须满足三个条件：

  

```java

Statement stmt = conn.createStatement(

ResultSet.TYPE_FORWARD_ONLY, // 只能向前

ResultSet.CONCUR_READ_ONLY); // 只读

stmt.setFetchSize(Integer.MIN_VALUE); // 关键魔法值

ResultSet rs = stmt.executeQuery("SELECT * FROM user");

while (rs.next()) { ... } // 必须逐行处理完

```

  

`Integer.MIN_VALUE`（即 -2147483648）是 MySQL 驱动约定的**特殊信号值**，告诉驱动："不要缓存，给我一行就处理一行"。

  

#### **底层原理**

  

- 服务端**不创建游标**，正常执行查询，把结果一行一行通过 TCP socket 推给客户端；

- 客户端驱动**不做内存缓冲**，`rs.next()` 直接从 socket 读下一行；

- 整个过程就是 socket 的流式管道，靠 TCP 反压（backpressure）控制节奏——你 `next()` 慢，服务端就发得慢。

  

#### **优点**

- 服务端**零额外开销**，不写临时表；

- 客户端内存几乎为零（只有 1 行的缓冲）；

- 总吞吐最高，最适合"全表 dump 类"批量加载。

  

#### **缺点（坑很多，必须了解）**

- **连接被独占**：在 `ResultSet` 关闭之前，**这个连接上不能执行任何其他 SQL**，否则抛 `Streaming result set ... is still active`；

- 必须**按顺序读完所有行**，不能跳读、不能回退；

- 中途如果业务异常忘了 `close()`，连接会一直挂着；

- 不能和事务中的其他查询混用。

  

---

  

### **两者对比速查**

  

| 维度 | `useCursorFetch=true` | `setFetchSize(Integer.MIN_VALUE)` |

|---|---|---|

| 实现位置 | 服务端游标 | 客户端 socket 流 |

| 客户端内存 | `fetchSize` 行 | ≈ 1 行 |

| 服务端开销 | 可能写临时表 | 无 |

| 连接是否独占 | 否 | **是** |

| 可中途取消 | 容易 | 需 `close()` 后才能复用连接 |

| 适用场景 | 分页式遍历、可能中途停止 | 一次性全量导出/加载 |

| 速度 | 中 | 最快 |

  

---

  

### **回到原场景的建议**

  

启动时加载 50w 条 `User` 到 `HashMap` 这种**一次性、顺序、读完即走**的需求，**优先选 `setFetchSize(Integer.MIN_VALUE)`**：

  

```java

try (Connection conn = dataSource.getConnection();

Statement stmt = conn.createStatement(

ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

stmt.setFetchSize(Integer.MIN_VALUE);

try (ResultSet rs = stmt.executeQuery("SELECT id, name, ... FROM user")) {

while (rs.next()) {

cache.put(rs.getLong("id"), mapRow(rs));

}

}

}

```

  

如果用 MyBatis，对应的就是 `@Options(fetchSize = Integer.MIN_VALUE, resultSetType = ResultSetType.FORWARD_ONLY)` 加 `ResultHandler` 流式处理。

  

如果用 JPA/Hibernate，则用 `Query.setHint(HINT_FETCH_SIZE, Integer.MIN_VALUE)` 配合 `Stream<User>`。

  

这样做之后，**JDBC 层不再是瓶颈**，配合前面提到的 `HashMap` 预设容量和并行分片，启动加载时间就能稳定压到 1-2 秒。
