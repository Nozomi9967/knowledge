---
tags:
  - 其他
背诵状态: 已完成
背诵开始: 2026-08-10 19:30
背诵结束: 2026-08-10 19:31
created: 2026-06-28
updated: 2026-06-28
---
# MyBatis — 持久层框架详解

## 一句话定义

> **MyBatis**（原 **iBatis**，Internet + Abatis）是一个**半自动 ORM 框架**（SQL Mapping），由开发者手写 SQL，框架负责结果集到 Java 对象的映射。
>
> 发音：**My-Ba-tis**（麦-巴-提斯），三个音节，重音在第一个音节。原名 iBatis 读 "艾-巴-提斯"
>
> **核心定位**：SQL 映射框架，不是全自动 ORM。开发者掌控 SQL，框架做 JDBC 封装和结果映射。

**本项目实现**：MyBatis-Plus 基于 MyBatis 增强，底层仍是 MyBatis 的 SQL Mapping 机制。

---

## 架构与工作流程

### 核心组件

```
┌─────────────────────────────────────────────────────┐
│                  MyBatis 核心架构                      │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ┌──────────────┐    ┌──────────────┐                 │
│  │ SqlSession    │    │  Mapper       │                 │
│  │ （会话层）     │───▶│  （映射层）   │                 │
│  └──────────────┘    └──────┬───────┘                 │
│                              │                          │
│                     ┌───────▼───────┐                 │
│                     │  XML Mapper   │                 │
│                     │  / 注解       │                 │
│                     └───────┬───────┘                 │
│                              │                          │
│                     ┌───────▼───────┐                 │
│                     │ SqlSession     │                 │
│                     │ Factory        │                 │
│                     └───────┬───────┘                 │
│                              │                          │
│                     ┌───────▼───────┐                 │
│                     │  Configuration │                 │
│                     │  （全局配置）  │                 │
│                     └───────┬───────┘                 │
│                              │                          │
│                     ┌───────▼───────┐                 │
│                     │  JDBC → MySQL │                 │
│                     └───────────────┘                 │
└─────────────────────────────────────────────────────┘
```

| 组件 | 职责 |
|------|------|
| **SqlSessionFactory** | 全局唯一，负责创建 SqlSession，从 Configuration 构建 |
| **SqlSession** | 数据库会话，线程不安全（每次用完关闭） |
| **Mapper** | 接口定义 + XML/注解实现 SQL 绑定 |
| **Configuration** | 全局配置中心，存放所有 SQL 定义（MappedStatement）、数据源、缓存、类型别名等 |
| **MappedStatement** | 一条 SQL 的完整定义：SQL 语句、参数映射、结果映射、是否缓存等。以 `namespace.methodName` 为 key 存在 Configuration 里 |
| **Executor** | SQL 执行器（BaseExecutor、CachingExecutor），负责读写缓存、调用 JDBC |

### Configuration + MappedStatement — 启动时干了什么

MyBatis 启动时做两件事：**① 解析配置 ② 注册 SQL**，结果全装在 Configuration 这个对象里。


>**MyBatis 启动时，把每个 Mapper XML 里的 SQL 标签解析成 `MappedStatement`（一条 SQL 的完整定义），然后按"类名.方法名"作为 key，存进 `Configuration` 这个全局大仓库里。**



```
mybatis-config.xml ──▶ Configuration
                            │
扫描 Mapper XML ─────────────┤
  │                          │
  ├─ UserMapper.xml  ──▶ MappedStatement("com.shop.mapper.UserMapper.selectById")
  │                          ├─ sql: "SELECT * FROM user WHERE id = ?"
  │                          ├─ resultMap: userMap
  │                          ├─ parameterMapping: id → Integer
  │                          └─ ...
  │                          │
  ├─ OrderMapper.xml ──▶ MappedStatement("com.shop.mapper.OrderMapper.searchUsers")
  │                          ├─ sql: "SELECT * FROM user <where> ..."
  │                          ├─ resultMap: userMap
  │                          └─ ...
  └─ ...
```

**Configuration** 是个大仓库，里面放了：

```
Configuration
├── 数据源（DataSource）              ── 连接哪个库
├── 全局设置（Settings）              ── cacheEnabled、mapUnderscoreToCamelCase 等
├── 类型别名（TypeAliasRegistry）      ── "User" → User.class
├── 类型处理器（TypeHandlerRegistry）  ── Java类型 ↔ JDBC类型 转换器
├── MapperRegistry                   ── 所有 Mapper 接口
└── MappedStatement 集合（Map<String, MappedStatement>）
    ├── "com.shop.mapper.UserMapper.selectById"     → 对应 XML 的 <select id="selectById">
    ├── "com.shop.mapper.UserMapper.searchUsers"    → 对应 <select id="searchUsers">
    └── "com.shop.mapper.OrderMapper.searchOrders"  → 对应 <select id="searchOrders">
```

**MappedStatement** 是一条 SQL 的完整定义，里面装了：

```
MappedStatement
├── id              ── "com.shop.mapper.UserMapper.selectById"
├── sql             ── 编译后的 SQL（SqlSource），含动态 SQL 节点树
├── resultMaps      ── 结果映射定义（ResultMap）
├── parameterMap    ── 参数映射
├── sqlCommandType  ── SELECT / INSERT / UPDATE / DELETE
├── flushCacheRequired  ── 执行后是否清空缓存
├── useCache        ── 是否使用二级缓存
└── keyGenerator    ── 主键生成策略（自增/序列/自定义）
```

**调用时怎么找到它**：Mapper 动态代理拦截方法调用 → 根据**方法全限定名**（如 `com.shop.mapper.UserMapper.selectById`）从 Configuration 的 Map 里取到对应的 MappedStatement → 拿出 SQL 执行。

> **面试一句话**："MyBatis 启动时把每个 XML 里的 `<select>` 等标签解析成一个 MappedStatement，以 `namespace + 方法名` 为 key 注册到 Configuration 的 Map 里。调用 Mapper 接口时，动态代理拿着方法名去这个 Map 里取到对应的 SQL 定义来执行。"

### 工作流程（一条 SQL 的完整生命周期）

```
应用程序调用 Mapper 接口方法
        │
        ▼
Mapper 接口动态代理（JDK Proxy）
        │
        ▼
查找对应 XML Mapper 中的 SQL 语句
        │
        ▼
SqlSession.selectList() / insert() / update() / delete()
        │
        ▼
Executor 执行器
        │
   ┌────┴────┐
   │ 一级缓存  │（SqlSession 级别，默认开启）
   └────┬────┘
        │
        ▼
StatementHandler（JDBC Statement 处理器）
        │
        ▼
ParameterHandler（参数处理：#{} → PreparedStatement ? 占位符）
        │
        ▼
JDBC 执行 → ResultSet
        │
        ▼
ResultSetHandler（结果集 → Java 对象映射）
        │
        ▼
返回 List<T> / T / Integer
```

---

## 三大核心能力

### 1. SQL 映射（XML Mapper）

```xml
<!-- 传统 MyBatis Mapper XML -->
<mapper namespace="com.shop.mapper.UserMapper">
    
    <!-- 结果映射：数据库字段 → Java 属性 -->
    <resultMap id="userMap" type="User">
        <id column="user_id" property="userId"/>
        <result column="user_name" property="userName"/>
        <result column="create_time" property="createTime"/>
    </resultMap>
    
    <!-- 基本查询 -->
    <select id="selectById" resultMap="userMap">
        SELECT * FROM user WHERE user_id = #{id}
    </select>
    
    <!-- 动态 SQL -->
    <select id="searchUsers" resultMap="userMap">
        SELECT * FROM user
        <where>
            <if test="name != null and name != ''">
                AND user_name LIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="age != null">
                AND age >= #{age}
            </if>
        </where>
        ORDER BY create_time DESC
    </select>
    
    <!-- 批量插入 -->
    <insert id="batchInsert">
        INSERT INTO user (user_name, age) VALUES
        <foreach collection="list" item="user" separator=",">
            (#{user.userName}, #{user.age})
        </foreach>
    </insert>
</mapper>
```

### 2. 动态 SQL（MyBatis 的灵魂）

| 标签                            | 作用                        | 示例                                                                                                  |
| ----------------------------- | ------------------------- | --------------------------------------------------------------------------------------------------- |
| `<if>`                        | 条件判断                      | `<if test="name != null"> AND name = #{name}</if>`                                                  |
| `<where>`                     | 自动处理 WHERE + 去掉多余的 AND/OR | 自动包裹条件                                                                                              |
| `<set>`                       | UPDATE 时动态 SET            | 自动去掉末尾逗号                                                                                            |
| `<foreach>`                   | 遍历集合（IN 查询/批量插入）          | `WHERE id IN <foreach item="id" collection="list" open="(" separator="," close=")">#{id}</foreach>` |
| `<choose>/<when>/<otherwise>` | switch-case 逻辑            | 多条件互斥选择                                                                                             |
| `<trim>`                      | 自定义前缀/后缀处理                | 灵活控制 SQL 拼接                                                                                         |
| `<bind>`                      | 变量绑定                      | `<bind name="pattern" value="'%' + name + '%'"/>`                                                   |

**面试必说**：MyBatis 启动时解析 XML，把每个 `<if>`、`<where>` 等标签编译成对应的 SqlNode（`IfSqlNode`、`WhereSqlNode`），组合成 `MixedSqlNode` 树。执行 SQL 时根据参数递归解析这棵树，动态拼出最终 SQL。

### 3. 结果映射（ResultMap）

**核心功能**：解决数据库字段名和 Java 属性名不一致的问题。

```xml
<resultMap id="orderMap" type="Order" autoMapping="true">
    <id column="order_id" property="orderId"/>
    <result column="user_name" property="userName"/>
</resultMap>
```

**关联映射（一对一 / 一对多）**：详细说明和 SQL 示例见 [[MyBatis ResultMap 关联映射详解]]。

| 标签 | 关系 | 属性 |
|------|------|------|
| `<association>` | 一对一（对象里有对象） | `property` + `javaType` |
| `<collection>` | 一对多（对象里有列表） | `property` + `ofType` |

**要点**：
- `<id>` 必须配，MyBatis 用它判重
- 一对多查询必须 ORDER BY 主表 ID
- 尽量用 JOIN（嵌套结果），不用嵌套查询（N+1）

---

## 缓存机制

### 一级缓存（SqlSession 级别）

- **默认开启**，无法关闭
- **作用范围**：同一个 SqlSession 内
- **失效条件**：
	- 执行 INSERT/UPDATE/DELETE 后清空；
	- 调用 `clearCache()`；
	- SqlSession 关闭
- **为什么还存在**：一级缓存是 MyBatis 框架层的设计，不依赖 Spring。在不同使用场景下效果不同：

| 使用方式                          | SqlSession 生命周期              | 一级缓存效果          |
| ----------------------------- | ---------------------------- | --------------- |
| **MyBatis 独立使用**（非 Spring）    | 开发者手动管理，同一个 SqlSession 内多次查询 | ✅ 生效，能减少重复查询    |
| **Spring + 非事务**              | 每个 Mapper 方法新建/关闭            | ❌ 跨方法无效         |
| **Spring + `@Transactional`** | 整个事务共享同一个 SqlSession         | ✅ 生效，事务内重复查询走缓存 |

所以准确的说法是：**非事务下基本用不上，事务下仍然有效**。

### 二级缓存（Mapper Namespace 级别）

- **默认关闭**，需手动开启
- **作用范围**：跨 SqlSession，同一个 Namespace 共享
- **配置**：`<setting name="cacheEnabled" value="true"/>` + Mapper XML 加 `<cache/>`
- **序列化要求**：缓存对象必须实现 Serializable
- **坑**：多表关联查询时，A 表 Mapper 更新不会清空 B 表 Mapper 的二级缓存 → 脏读

---

## 面试高频追问与话术

### Q1: MyBatis 中 `#{}` 和 `${}` 有什么区别？

> **面试话术**：
> > "`#{}` 是预编译参数替换：MyBatis 把 `#{}` 编译成 `?` 占位符，执行时通过 PreparedStatement 的 `setXXX()` 把参数作为**数据值**绑定进去。**SQL 语句在编译时结构就已固定，用户输入只作为值参与绑定、不参与 SQL 语法解析，所以无论输入什么都不会改变 SQL 语义，从根本上防止 SQL 注入**。`${}` 是字符串直接拼接，输入会进入 SQL 语法层，所以有注入风险。"



>"MyBatis 里有两种参数传递方式，**井号大括号**是预编译的，底层用 `PreparedStatement` 占位符，能防 SQL 注入；**美元符大括号**是直接字符串拼接，会有注入风险，一般只用在动态表名、列名这种场景。"



### Q2: MyBatis 的 Mapper 接口为什么不需要写实现类？

> **面试话术**：
> "Mapper 接口在启动时通过 **JDK 动态代理**创建代理对象。MyBatis 扫描 `mapperLocations` 路径下的 XML 文件，解析 `<select>/<insert>/<update>/<delete>` 标签，以 namespace + 方法名作为 key 注册到 Configuration 中。调用 Mapper 方法时，代理对象拦截方法调用，根据方法名找到对应的 `MappedStatement`，执行 SQL 并返回结果。整个过程不需要开发者写实现类。"

### Q3: MyBatis 和 JDBC 相比有什么优势？

> **面试话术**：
> "JDBC 你需要手动处理：
> ① 获取 Connection；
> ② 创建 Statement/PreparedStatement；
> ③ 设置参数；
> ④ 执行 SQL；
> ⑤ 遍历 ResultSet 封装成对象；
> ⑥ 关闭每个资源。
> 
> MyBatis 封装了这些模板代码，开发者只需要写 SQL 和定义结果映射。核心好处：
> ① **SQL 统一管理**在 XML 中，可维护性好；
> ② **自动映射** ResultSet 到 Java 对象；
> ③ **动态 SQL** 减少字符串拼接；
> ④ **连接池集成**（默认 HikariCP）自动管理连接生命周期。"

### Q4: MyBatis 的二级缓存有什么问题？什么场景下会禁用？

> **面试话术**：
> "二级缓存是 Mapper Namespace 级别的，也就是说两个不同的 Mapper 操作同一张表时，一个更新了数据但另一个的缓存不会失效，导致脏读。比如 UserMapper 和 UserExtMapper 都操作 user 表，UserMapper 更新了 user，但 UserExtMapper 的二级缓存还在。所以**多表关联查询或有多 Mapper 操作同一张表时，建议禁用二级缓存**。实际生产项目中，我们更倾向于用 Redis 做外部缓存而不是 MyBatis 内置的二级缓存。"

### Q5: 手写 MyBatis 复杂 SQL 时有什么性能优化的经验？

> **面试话术**：
> "几个经验：
> ① **分页用数据库 LIMIT**，不要查出来后在内存里分页；
> ② **JOIN 查询批量**，避免 N+1 问题（查 N 条主记录再循环查 N 次关联表）；
> ③ **大批量插入用 BatchExecutor** 或 `foreach` 拼接批量 SQL（注意不超过 1000 条/批）；
> ④ **显式列名 + 显式 resultMap，别用 `SELECT *`**——只查需要的列，减少传输和内存；显式 resultMap 主要防多表 JOIN 同名列冲突（这是正确性问题，autoMapping 本身不是性能瓶颈）；
> ⑤ **大文本字段（TEXT/BLOB）延迟加载**，用 `<result column="content" property="content" jdbcType="LONGVARCHAR" select="lazyLoadContent"/>`；
> ⑥ **大批量查询用 fetchSize 流式取数**（MySQL 需流式模式或服务端游标才生效），避免全量结果一次性进内存。"

[[MyBatis复杂SQL性能优化经验]]

### Q6: MyBatis 执行 SQL 的完整流程是什么？

> **面试话术**：
> "Mapper 接口方法被调用 → JDK 动态代理拦截 → 查找 MappedStatement → Executor 执行器（先查一级缓存）→ StatementHandler 创建 Statement → ParameterHandler 设置参数 → JDBC 执行 SQL → ResultSetHandler 映射结果 → 返回。如果需要返回自增主键，配置 `useGeneratedKeys="true"` 和 `keyProperty="id"`，MyBatis 在执行 INSERT 后会从 JDBC 获取 generatedKeys 并设置到对象属性中。"

---

## MyBatis vs MyBatis-Plus vs JDBC

| 维度 | MyBatis | MyBatis-Plus ✅ | 原生 JDBC |
|------|---------|----------------|-----------|
| 简单 CRUD | 手写 SQL | BaseMapper 零 SQL | 手写 |
| 复杂 SQL | XML Mapper ✅ | XML Mapper / LambdaWrapper | 字符串拼接 |
| 动态 SQL | 标签（if/where/foreach）✅ | 标签 + Lambda 条件构造器 | ❌ 手写 |
| 分页 | 手写 LIMIT / PageHelper | 插件自动 ✅ | 手写 LIMIT |
| 结果映射 | ResultMap ✅ | 同 MyBatis | 手写 getXXX() |
| 代码量 | 中 | 少 ✅ | 极多 |
| SQL 控制力 | ✅ 完全控制 | ✅ 完全控制（复杂场景回退 XML） | ✅ 完全控制 |

---

## 常见面试代码题

### 1. 动态 SQL 分页查询

```xml
<select id="pageQuery" resultType="User">
    SELECT * FROM user
    <where>
        <if test="name != null and name != ''">
            AND name LIKE CONCAT('%', #{name}, '%')
        </if>
        <if test="age != null">
            AND age = #{age}
        </if>
    </where>
    ORDER BY id DESC
    LIMIT #{offset}, #{pageSize}
</select>
```

### 2. 批量更新（foreach）

```xml
<update id="batchUpdateStock">
    UPDATE sku SET num =
    <foreach collection="list" item="item" separator=" " open="CASE id" close="END">
        WHEN #{item.id} THEN num - #{item.num}
    </foreach>
    WHERE id IN
    <foreach collection="list" item="item" separator="," open="(" close=")">
        #{item.id}
    </foreach>
</update>
```

### 3. 自定义 TypeHandler（枚举转换）

```java
public class GenderTypeHandler extends BaseTypeHandler<Gender> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Gender param, JdbcType jt) {
        ps.setInt(i, param.getCode());  // 存 int 到数据库
    }
    @Override
    public Gender getNullableResult(ResultSet rs, String col) {
        return Gender.fromCode(rs.getInt(col));  // 从数据库 int 转枚举
    }
}
```

---

## 常见踩坑清单

| 坑 | 表现 | 原因 | 解决 |
|----|------|------|------|
| ❌ Mapper 方法找不到 | 启动报 `BindingException` | XML namespace 和接口全限定名不匹配 | 检查 namespace 和接口包路径 |
| ❌ 参数绑定失败 | SQL 执行报错 | `#{}` 中的参数名和接口参数名不一致 | `@Param` 注解显式指定 |
| ❌ 查询结果都是 null | 返回记录数对但字段全空 | ResultMap 的 column 拼错或类型不匹配 | 检查数据库字段名或开启 autoMapping |
| ❌ N+1 查询 | 关联查询慢 | `<collection>` / `<association>` 缺少联合查询 | 改用 JOIN 或 `select` 延迟加载 |
| ❌ 自增主键不返回 | 插入后 ID 为 null | 未配 `useGeneratedKeys` | 加 `useGeneratedKeys="true" keyProperty="id"` |

---

## 关联知识点

- [[MyBatis-Plus详解]] — MyBatis 增强工具，继承本文所有能力
- [[N+1查询问题详解]] — MyBatis 关联查询典型性能问题
- [[spring八股]] — Spring 整合 MyBatis 事务管理
- [[面试分析报告#技术栈全景]] — 本项目 MyBatis-Plus 定位
- [[Feign详解]] — 跨服务调用的数据转换（DTO/Entity 分离）

---

> **通俗理解**：MyBatis 就像厨师和一个传菜员的配合。
>
> 你（开发者）是厨师，想做什么菜（SQL）完全自己决定。传菜员（MyBatis）只负责把你做好的菜端给客人（数据库），再把客人桌上的空盘子收回来（结果集 → Java 对象）。你的菜想放多少盐、用什么火候——全由你控制。
>
> **对比 JDBC**：JDBC 是你要自己端菜、自己收盘子、自己擦桌子、自己洗碗。MyBatis 帮你干掉了所有杂活（参数设置、结果映射、资源关闭），你只负责**炒菜**（写 SQL）这一件事。
>
> **对比 MyBatis-Plus**：MyBatis-Plus 是给这个厨师配了一个帮厨——番茄炒蛋（单表 CRUD）这种菜不用亲自炒了，帮厨就能搞定。但佛跳墙（复杂 SQL）还是得你亲自下厨。

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-10 19:30 | 2026-08-10 19:31 | MyBatis 是半自动 ORM 框架，核心为开发者手写 SQL 与框架映射，启动时解析 XML 将 SQL 注册为 MappedStatement，运行时通过动态代理、Executor 及缓存机制完成数据库操作。 | 第一轮背记完成 |
