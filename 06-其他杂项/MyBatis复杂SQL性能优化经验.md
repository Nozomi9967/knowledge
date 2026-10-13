# MyBatis 复杂 SQL 性能优化经验

面试场景：手写 MyBatis 复杂 SQL 时的性能优化要点，共 5 条核心经验，逐条展开说明。

---

## 一、分页用数据库 LIMIT，不要查出来后在内存里分页

### 问题描述

很多新手写法是：先用 `SELECT *` 把全表数据查出来，封装成 List，然后在 Java 代码里用 `subList()` 或者循环截取做"内存分页"。

```java
// 反例：内存分页
List<Order> all = orderMapper.selectAll(); // 假设有 10 万条
List<Order> pageData = all.subList((page - 1) * size, page * size);
```

### 为什么有性能问题

- 无论用户要看第几页，数据库都要把**全表数据**通过网络传输到应用服务器，IO 开销巨大。
- JVM 内存中要持有全量数据对象，容易导致 Full GC 甚至 OOM。
- 数据库本身的索引、排序优化能力完全没有被利用。

### 正确做法

利用数据库的 `LIMIT ... OFFSET ...`（MySQL）或 `ROW_NUMBER()`（Oracle/SQL Server）在数据库层面完成分页：

```xml
<select id="selectByPage" resultMap="OrderResultMap">
    SELECT id, order_no, amount, create_time
    FROM orders
    WHERE status = #{status}
    ORDER BY create_time DESC
    LIMIT #{offset}, #{pageSize}
</select>
```

### 进阶优化：深分页问题

当 `OFFSET` 很大时（比如 `LIMIT 1000000, 20`），MySQL 仍然需要扫描并丢弃前 100 万条记录，性能会急剧下降。常见优化方式：

1. **延迟关联（覆盖索引 + 子查询）**：先用主键索引查出目标 ID，再回表查详情。

```sql
SELECT o.* FROM orders o
INNER JOIN (
    SELECT id FROM orders
    WHERE status = #{status}
    ORDER BY create_time DESC
    LIMIT #{offset}, #{pageSize}
) t ON o.id = t.id;
```


>为什么这样就不用扫描丢弃前100万条记录

其实**还是要扫描/跳过前 100 万条**,这一步省不掉——LIMIT OFFSET 的本质决定了数据库必须定位到第 1000020 行(假设 offset=1000000, pageSize=20)。

真正的优化点不在于"不扫描",而在于**让被跳过的这 100 万行扫描代价变得极小**。

**普通写法 vs 优化写法的区别**

**普通写法:**

```sql
SELECT * FROM orders
WHERE status = #{status}
ORDER BY create_time DESC
LIMIT 1000000, 20;
```

假设 `(status, create_time)` 上有索引。MySQL 会:

1. 走索引,按 `create_time` 顺序定位
2. 对于排序范围内的每一行,**回表**(根据索引中的主键去聚簇索引查出整行数据,即 `id, order_no, amount, customer_id, address, ...` 等所有列)
3. 拿到完整行后,前 1000000 行直接丢弃,只保留最后 20 行

问题在于:**前 100 万行也做了"回表"这个动作**。回表是随机 IO(因为聚簇索引上的物理顺序和二级索引的逻辑顺序不一致),代价很高。100 万次随机 IO 是这条 SQL 慢的真正原因。

**优化写法做了什么**

```sql
SELECT id FROM orders
WHERE status = #{status}
ORDER BY create_time DESC
LIMIT 1000000, 20;
```

子查询里**只 SELECT id**。如果索引是 `(status, create_time)`,这个查询所需的 `status`、`create_time`、`id`(主键,二级索引天然携带)全部都在二级索引里能拿到——这就是**覆盖索引(covering index)**。

这意味着:

- 前 100 万行的"扫描+丢弃"全部发生在二级索引这棵 B+树上
- **完全不需要回表**,只是顺序读索引页,代价是顺序 IO,而且索引页比数据页小得多,缓存命中率也更高
- 只有最后筛出来的 20 个 `id`,才会拿去外层 JOIN 做回表,取完整行数据

所以准确的说法应该是:**不是不扫描丢弃 100 万行,而是把"扫描丢弃"这个动作的代价从"100 万次随机 IO 回表"降低成了"100 万次索引顺序扫描",只让最终需要的 20 行去做回表**。

数量级差异大概是这样:普通写法 = 1000020 次回表;优化写法 = 20 次回表 + 100 万次轻量索引扫描。当数据量很大、索引页能较多地命中 Buffer Pool 时,后者会快非常多。

**一个容易被问到的追问点**

如果子查询里写 `SELECT *` 或者查询字段超出了索引覆盖的范围,这个优化就失效了——会退化成和普通写法一样的"逐行回表",这也是为什么子查询里**必须严格只 SELECT 主键**。



>为啥普通做法要回表啊,都写了limit 1000000了,他难道不知道该跳过吗


关键点在于:**MySQL 不是先排好序拿到 id 列表、再决定要不要回表,而是在排序/扫描的过程中,每"看一行"就要立刻把这一行的完整数据(SELECT 里要的所有列)取出来,然后才谈得上排序和计数。**

**拆开看执行过程**

普通写法:

```sql
SELECT * FROM orders
WHERE status = #{status}
ORDER BY create_time DESC
LIMIT 1000000, 20;
```

假设走的是 `(status, create_time)` 这个二级索引。执行流程大致是:

1. 在二级索引上找到 `status = xxx` 的所有记录,这些记录在索引里已经是按 `create_time` 排好序的(所以不需要额外排序,这点是好的)
2. 但是 `SELECT *` 要查 `id, order_no, amount, customer_id, address...` 这些列,这些列**不在二级索引里**,只存在于聚簇索引(主键索引)里
3. 所以**对索引扫到的每一行**,MySQL 必须立刻去聚簇索引里"回表"一次,把这一整行数据捞出来,拼成一个完整的 row
4. 拼好的这个 row,才能被 LIMIT 的计数器判断:"这是第几行了?要不要丢弃?"

也就是说,**"判断是第几行"这个动作,和"取出这一行的完整数据"这个动作,是绑在一起、按顺序发生的**——不存在"先数到第 1000000 行,再开始取数据"这种分阶段执行。MySQL 的执行器是逐行(row-by-row)处理的,每读一行(无论是从索引还是回表),都要把它组装成 SELECT 子句要求的格式,然后才送到上层(排序器/LIMIT计数器)去判断。

**一个类比**

可以想象成你在图书馆找书:

- 索引(二级索引)就像是**书架上的标签卡片**,上面只写了"书名 + 索书号"
- 聚簇索引就像是**书本身**,放在另一排书架上,内容(作者、出版社、简介……)都在书里

普通写法相当于:你拿着标签卡片一张一张翻,**每翻到一张,都要跑去另一排书架把那本书整本抽出来**,翻开看看是不是要丢弃的——翻了 100 万次卡片,就跑了 100 万趟书架。

优化写法(子查询只 SELECT id)相当于:你先**只翻卡片**(标签卡片上就有书名和索书号,不用去取书),翻到第 1000000~1000020 张,记下这 20 个索书号,然后**只去书架取这 20 本书**。

**为什么不能**"先数后取"

理论上是有可能做到"只数索引、不取数据"的——前提是 `SELECT` 的列**全部都在索引里**(覆盖索引)。这种情况下 MySQL 确实可以只扫二级索引、不回表,直到该返回的那几行才...其实覆盖索引场景下根本不需要"返回前再取",因为数据本身就在索引里,扫描的同时数据就已经拿到了,只是不需要再去聚簇索引拿其余列而已。

但 `SELECT *` 打破了这个前提——只要有一列不在索引里,这一行就**必须**完整组装,而组装就意味着回表,这是 InnoDB 存储引擎的执行机制决定的,执行器没法"预判后面要丢弃就先不组装"——因为是否丢弃这个判断本身,就发生在组装完成之后。


---



2. **游标分页（Seek 分页）**：用上一页最后一条记录的排序字段作为条件，避免 OFFSET。

```sql
SELECT * FROM orders
WHERE create_time < #{lastCreateTime}
ORDER BY create_time DESC
LIMIT #{pageSize};
```

适合"无限滚动"类场景，性能最优但不支持跳页。

3. **使用 PageHelper 等分页插件时**，要注意它本质上也是拼接 LIMIT，深分页问题依然存在，需要结合上述思路优化。

---

## 二、JOIN 查询批量，避免 N+1 问题

### 问题描述

典型场景：先查询 N 条主记录（比如订单列表），再针对每条记录循环查询关联表（比如每个订单的商品明细），导致执行了 1 + N 次 SQL。

```java
// 反例：N+1 查询
List<Order> orders = orderMapper.selectOrders(); // 1 次查询
for (Order order : orders) {
    List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId()); // N 次查询
    order.setItems(items);
}
```

如果有 100 个订单，就会产生 101 次数据库往返（round trip），每次往返都有网络延迟，整体响应时间会被严重拉长。

### 解决方案 1：JOIN 一次性查出（适合一对一或数据量可控的一对多）

```xml
<select id="selectOrdersWithItems" resultMap="OrderWithItemsMap">
    SELECT o.id AS order_id, o.order_no, o.amount,
           i.id AS item_id, i.product_name, i.quantity
    FROM orders o
    LEFT JOIN order_item i ON o.id = i.order_id
    WHERE o.id IN
    <foreach collection="orderIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>

<resultMap id="OrderWithItemsMap" type="Order">
    <id property="id" column="order_id"/>
    <result property="orderNo" column="order_no"/>
    <result property="amount" column="amount"/>
    <collection property="items" ofType="OrderItem">
        <id property="id" column="item_id"/>
        <result property="productName" column="product_name"/>
        <result property="quantity" column="quantity"/>
    </collection>
</resultMap>
```

注意：JOIN 一对多会产生**笛卡尔积**（一个订单有 5 个商品就会重复 5 行主表数据），数据量大时反而增加网络传输量，需要权衡。

### 解决方案 2：批量 IN 查询（适合一对多，关联数据量较大时更优）

先查主表，再用 `IN` 一次性批量查关联表，最后在内存中用 Map 组装：

```xml
<select id="selectItemsByOrderIds" resultType="OrderItem">
    SELECT * FROM order_item
    WHERE order_id IN
    <foreach collection="orderIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

```java
List<Order> orders = orderMapper.selectOrders(); // 1 次
List<Long> orderIds = orders.stream().map(Order::getId).collect(toList());
List<OrderItem> items = orderItemMapper.selectItemsByOrderIds(orderIds); // 1 次
Map<Long, List<OrderItem>> itemMap = items.stream().collect(groupingBy(OrderItem::getOrderId));
orders.forEach(o -> o.setItems(itemMap.getOrDefault(o.getId(), Collections.emptyList())));
```

总查询次数从 1+N 降为 2 次，数据量也不会因为 JOIN 笛卡尔积而膨胀。**这是实际项目中最常用、最推荐的方式。**

> 注意：`IN` 子句也要控制数量（比如分批，每批不超过 1000），避免 SQL 过长或触发某些数据库的 IN 数量限制。

---

## 三、大批量插入用 BatchExecutor 或 foreach 拼接批量 SQL

### 问题描述

如果要插入 1 万条数据，循环单条 `insert` 会产生 1 万次网络往返 + 1 万次 SQL 解析/执行计划生成，性能极差。

```java
// 反例：循环单条插入
for (User user : userList) {
    userMapper.insert(user); // 每次都是一次完整的 SQL 执行
}
```

### 方案 1：foreach 拼接批量 INSERT 语句

```xml
<insert id="batchInsert" parameterType="java.util.List">
    INSERT INTO user (name, age, email)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.name}, #{item.age}, #{item.email})
    </foreach>
</insert>
```

生成的 SQL 类似：

```sql
INSERT INTO user (name, age, email) VALUES
('a', 18, 'a@x.com'), ('b', 19, 'b@x.com'), ..., ('z', 20, 'z@x.com');
```

一次网络往返插入多条数据，效率提升明显。

**注意控制每批数量（建议不超过 1000 条）**，原因：
- MySQL 的 `max_allowed_packet` 限制单条 SQL 报文大小，拼接过长会报错。
- 单条 SQL 过大会增加事务锁定时间，影响并发，也不利于出错时定位和回滚。
- 实践中常做法：在 Service 层把大列表按 1000 条切分（`Lists.partition`），分批调用 `batchInsert`。

```java
List<List<User>> partitions = Lists.partition(userList, 1000);
for (List<User> batch : partitions) {
    userMapper.batchInsert(batch);
}
```

### 方案 2：MyBatis BatchExecutor（ExecutorType.BATCH）

适合"逐条 SQL 语句相同、仅参数不同"的场景，底层使用 JDBC 的 `addBatch()` / `executeBatch()`，将多条 SQL 一次性发送到数据库执行：

```java
try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
    UserMapper mapper = session.getMapper(UserMapper.class);
    for (User user : userList) {
        mapper.insert(user); // 不会立即发送，先在 JDBC 层缓存
    }
    session.flushStatements(); // 一次性批量发送
    session.commit();
}
```

### 两种方式如何选择

- **foreach 拼接**：SQL 可读性好、调试方便，适合记录数中等（几百到几千）、字段不多的场景；缺点是 SQL 文本会随数据量增大。
- **BatchExecutor**：基于 JDBC 原生批处理协议，更适合数据量很大、字段较多、需要复用同一条 PreparedStatement 的场景；但要注意不同数据库驱动对 `rewriteBatchedStatements=true`（MySQL JDBC URL 参数）的支持，否则 JDBC 层可能仍是逐条发送，达不到预期效果。

---

## 四、显式列名 + 显式 resultMap：别依赖 SELECT * 与 autoMapping

### 问题描述

MyBatis 默认会开启自动映射（`autoMappingBehavior=PARTIAL`），即查询结果集的列名会通过反射自动匹配到 POJO 的属性（按列名/属性名，或下划线转驼峰规则）。

```xml
<!-- 隐式写法：依赖自动映射 -->
<select id="selectById" resultType="User">
    SELECT * FROM user WHERE id = #{id}
</select>
```

### 真正的理由：正确性优先，性能其次

1. **正确性（最重要）——多表 JOIN 同名列冲突**：`user` 和 `order` 都有 `id` / `create_time` 列时，autoMapping 按列名匹配，两列映射到同一个属性，**后出现的列值覆盖先出现的**——数据错了但代码不报错。复杂关联查询必须用显式 resultMap + 列别名精确控制：

```xml
<resultMap id="OrderWithUserMap" type="Order">
    <id property="id" column="order_id"/>
    <result property="createTime" column="order_create_time"/>
    <association property="user">
        <id property="id" column="user_id"/>
        <result property="createTime" column="user_create_time"/>
    </association>
</resultMap>
```

2. **性能——收益来自「只查需要的列」，不是 autoMapping 本身**：`SELECT *` 会把用不到的大字段（TEXT、BLOB、JSON）也查出来传输 + 占内存。显式列名直接减少网络传输量和内存占用，这才是实打实的性能点。
3. **维护性——字段变更风险**：表结构新增字段后，`SELECT *` 会自动多查出新列，可能意外映射到 POJO 中同名但语义不同的属性，或者因为找不到匹配属性而被 MyBatis 忽略（看似无害，实则隐藏了"漏配置"的问题）。

### 关于「autoMapping 反射开销」的说法（易错点）

网上流传"autoMapping 依赖反射慢，显式 `<result>` 按下标直接赋值快"——**不严谨**。列名 → 属性的匹配是结果集元数据层面的一次性工作（首次执行后即缓存），逐行赋值两者都要反射调 setter，高频调用下差异可忽略。面试被追问「具体差多少」时很难自圆其说。**autoMapping 该避免的理由是正确性（同名列冲突），不是性能。**

### 推荐做法：显式列出字段 + 显式 resultMap

```xml
<resultMap id="UserResultMap" type="User">
    <id property="id" column="id"/>
    <result property="name" column="name"/>
    <result property="age" column="age"/>
    <result property="email" column="email"/>
</resultMap>

<select id="selectById" resultMap="UserResultMap">
    SELECT id, name, age, email
    FROM user
    WHERE id = #{id}
</select>
```

好处：

- 只查需要的列，减少网络传输和内存占用，尤其是排除大字段后效果明显。
- 多表 JOIN 时用列别名 + 显式 `<result>` 精确控制每个字段的来源，杜绝同名列互相覆盖。
- SQL 即文档，列出哪些字段一目了然，便于代码审查和后续维护，新增表字段不会"悄悄"影响现有查询。

> 实践建议：项目中可以全局配置 `mapUnderscoreToCamelCase=true`（下划线转驼峰自动映射），单表简单查询用自动映射没问题；但多表 JOIN、宽表查询，建议显式声明 resultMap 并精简查询字段。



> POJO 类的 boolean 属性不能加 is 前缀,数据库字段则建议加 is_ 前缀

1. **数据库命名习惯**:很多团队约定布尔类型字段用 `is_xxx` 前缀,一眼就能看出这是个布尔标志位,比如 `is_deleted`、`is_valid`、`is_default`。
2. **Java/MyBatis 的坑**:如果 POJO 里写成 `private Boolean isDeleted`,有些序列化框架(比如某些版本的 fastjson、jackson)在生成 getter 时会变成 `isDeleted()`(而不是 `getIsDeleted()`),导致：
    - MyBatis 的 `mapUnderscoreToCamelCase` 自动映射时,`isDeleted` 反推回数据库字段可能变成 `is_deleted` 没问题,但也可能因为某些 ORM 框架对 `is` 前缀的特殊处理(把 `isDeleted` 识别成 `deleted` 属性 + `is` 前缀,反而映射到 `deleted` 字段)出现字段对不上的问题
    - JSON 序列化时 `isDeleted` 字段可能被序列化成 `deleted`(因为框架认为 `is` 是 getter 的修饰符而非字段名的一部分),导致前端拿到的字段名和后端字段名不一致

所以阿里规范干脆建议:**POJO 属性直接叫 `deleted`**(不加 is),数据库字段可以保留 `is_deleted` 习惯,通过显式的 `<result>` 映射来桥接两者:

```xml
<result property="deleted" column="is_deleted"/>
```

---

## 五、大文本字段（TEXT/BLOB）延迟加载

### 问题描述

某些表中包含大字段，例如文章正文 `content`（TEXT/LONGTEXT）、文件二进制 `file_data`（BLOB）。如果在列表查询场景（比如文章列表页只需要标题、摘要、作者）中也把这些大字段一起查出来：

- 大量无用数据通过网络从数据库传输到应用层，浪费带宽和数据库 IO。
- 应用内存中持有大量大字段数据，增加 GC 压力。
- 而实际上用户在列表页根本不会看到这些内容，只有点进详情页才需要。

### 解决方案：MyBatis 延迟加载（lazy loading）+ 嵌套查询

利用 `<resultMap>` 中 `<result>` 标签的 `select` 属性，将大字段配置为单独的延迟查询：

```xml
<resultMap id="ArticleResultMap" type="Article">
    <id property="id" column="id"/>
    <result property="title" column="title"/>
    <result property="author" column="author"/>
    <result property="createTime" column="create_time"/>
    <!-- 大字段延迟加载：只有真正访问 getContent() 时才会触发查询 -->
    <result property="content" column="id" jdbcType="LONGVARCHAR"
            select="lazyLoadContent"/>
</resultMap>

<select id="selectArticleList" resultMap="ArticleResultMap">
    SELECT id, title, author, create_time
    FROM article
    WHERE status = 1
</select>

<!-- 单独的延迟加载查询，column="id" 会作为这条 SQL 的入参 -->
<select id="lazyLoadContent" resultType="String">
    SELECT content FROM article WHERE id = #{id}
</select>
```

需要在全局配置中开启延迟加载：

```xml
<settings>
    <setting name="lazyLoadingEnabled" value="true"/>
    <setting name="aggressiveLazyLoading" value="false"/>
</settings>
```

### 工作原理

- `selectArticleList` 执行时，只查询 `id, title, author, create_time` 四个轻量字段，列表查询非常快。
- MyBatis 通过动态代理为返回的 `Article` 对象生成代理类，`content` 属性此时为空。
- 只有当业务代码真正调用 `article.getContent()` 时，代理对象才会触发 `lazyLoadContent` 这条 SQL，按需查询该条记录的大字段内容。
- `aggressiveLazyLoading=false` 表示"按需加载每个属性"，而不是访问任意属性就触发所有延迟字段的加载（旧版本默认是 true，会导致访问任意一个延迟属性就把所有延迟字段都加载出来，失去意义）。

### 适用场景与权衡

- **适合**：列表页/概览页大量展示，但详情字段访问频率低、且字段本身较大（几 KB 到几 MB）的场景。
- **代价**：详情页访问时会多一次 SQL 查询（如果一次性查出可能只需要 1 次）；同时延迟加载基于动态代理，序列化（如转 JSON 返回前端）时如果不小心触发了代理对象的字段访问，可能意外触发额外查询，需要注意（比如先确保所有需要的字段都已加载，或在序列化前手动赋值）。
- **替代方案**：很多团队更倾向于"业务层显式区分"——列表查询用一个不含大字段的 resultMap/SQL，详情查询用另一个包含大字段的 resultMap/SQL，由调用方根据场景选择不同的 Mapper 方法。这种方式逻辑更直观，避免了延迟加载代理带来的"隐式查询"问题，是实际项目中更常见的做法；MyBatis 原生的延迟加载机制则更适合"字段访问与否不确定、且希望对调用方透明"的通用框架场景。

---

## 六、大批量查询用 fetchSize 流式取数

### 问题描述

导出、定时任务扫全表这类「结果集很大、每条处理很快」的场景，默认一次性拉取会把全量数据堆进 JVM 内存，量大时 OOM 或频繁 Full GC。

### 解决方案：流式读取

```java
try (Connection conn = ...;
     PreparedStatement ps = conn.prepareStatement(sql,
             ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
    ps.setFetchSize(Integer.MIN_VALUE);   // MySQL 流式模式
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {               // 边读边处理，内存只占一条
            ...
        }
    }
}
```

⚠ 注意：

- MySQL 驱动下 `setFetchSize(n)` **默认不生效**——要么用 `Integer.MIN_VALUE` 触发流式模式，要么 `useCursorFetch=true` 走服务端游标。
- 流式模式下游标存活期间连接被独占，处理完必须关闭 Statement 才能归还连接池；同一连接不能嵌套开第二个 ResultSet。

---

## 总结对照表

| 序号 | 优化点 | 核心思路 | 关键技术 |
|------|--------|----------|----------|
| 1 | 数据库分页 | 把分页计算下推到数据库层 | LIMIT/OFFSET、覆盖索引子查询、游标分页 |
| 2 | 避免 N+1 | 减少查询次数（往返次数） | JOIN、批量 IN + 内存组装 |
| 3 | 批量插入 | 一次网络往返写多条数据 | foreach 拼接 SQL、ExecutorType.BATCH |
| 4 | 显式 resultMap | 只查需要的列 + 防同名列冲突 | 显式列名、显式 `<result>` 映射、列别名 |
| 5 | 大字段延迟加载 | 按需加载，避免无用传输 | `<result select="...">`、lazyLoadingEnabled |
| 6 | fetchSize 流式取数 | 大结果集边读边处理，避免全量进内存 | setFetchSize（Integer.MIN_VALUE / useCursorFetch） |

整体思路可以归纳为：**减少数据传输量、减少网络往返次数、减少不必要的运行时开销**，三条主线贯穿所有优化点。
