---
tags:
  - jdbc
  - 持久层
  - 面试
created: 2026-08-11
updated: 2026-08-11
source: 面试前 JDBC 补全
背诵状态: 已完成
背诵开始: 2026-08-12 15:36
背诵结束: 2026-08-12 15:41
---

# JDBC 详解 — Java 官方数据库访问规范（一切 ORM 的地基）

## 一句话定义

> **JDBC**（Java Database Connectivity，Java 数据库连接）是 Java 官方的**数据库访问规范**——它只定义一组接口（`Driver`、`Connection`、`Statement`、`ResultSet`），自己不干活，**具体实现由数据库厂商的驱动提供**（MySQL 驱动、Oracle 驱动、PostgreSQL 驱动…）。

类比：JDBC 是「插座标准」，驱动是「各厂商的插头」，Java 程序是「电器」——按标准造电器，任何牌子的插座都能插。

---

## 为什么需要 JDBC

- **没有 JDBC 前**：每种数据库一套私有 API（MySQL 的 C API、Oracle 的 OCI…），换数据库 = 重写全部数据访问代码
- **有了 JDBC 后**：`java.sql.*` 统一接口，换数据库只需换驱动 jar + 改连接 URL
- 这是「**规范 vs 实现**」思想在持久层的最底层体现——和「JPA 规范 vs Hibernate 实现」是同一套思路（见 [[JPA详解]]）

---

## 全链路：你的代码到数据库之间隔了几层

```
Spring Data JPA / MyBatis-Plus   ← 上层框架（本项目用 MyBatis-Plus）
        ↓ 基于
JPA 规范 / MyBatis               ← ORM 层：把对象/SQL 翻译成 JDBC 调用
        ↓ 调用
JDBC（java.sql.* 接口）           ← Java 标准库，只定义接口，不干活
        ↓ 实现
MySQL Connector/J（驱动）         ← 厂商实现：TCP 连接 + 协议解析
        ↓
MySQL
```

> 面试一句话点破：「**任何 ORM 的尽头都是 JDBC**——MyBatis 解析 XML 后拼的还是 PreparedStatement + ResultSet；JPA/Hibernate 生成 SQL 后同样走 JDBC 发到数据库。」——这句话能串起整个持久层知识体系。

---

## 核心组件

| 组件                  | 职责                                      |
| ------------------- | --------------------------------------- |
| `Driver`            | 厂商实现的驱动接口，负责建立底层网络连接                    |
| `DriverManager`     | 驱动管理器：SPI 扫描加载驱动，按 URL 匹配并返回 Connection |
| `Connection`        | 数据库连接：会话级对象，**事务边界所在**                  |
| `Statement`         | 静态 SQL 语句（字符串拼接，⚠ 有注入风险）                |
| `PreparedStatement` | 预编译 SQL 语句（`?` 占位符，防注入，性能更好）⚠ 面试高频      |
| `ResultSet`         | 查询结果集：游标式遍历，`next()` + `getXxx(列名/下标)`  |

---

## 五步流程（必背）

```java
// 1. 加载驱动 — JDBC 4.0 起 SPI 自动加载，Class.forName 可省略
//    Class.forName("com.mysql.cj.jdbc.Driver");

// 2. 获取连接
String url = "jdbc:mysql://localhost:3306/db?useSSL=false&serverTimezone=Asia/Shanghai";
try (Connection conn = DriverManager.getConnection(url, "root", "123456");
     // 3. 创建预编译语句
     PreparedStatement ps = conn.prepareStatement(
             "SELECT * FROM user WHERE name = ? AND age > ?")) {

    // 4. 绑定参数 + 执行
    ps.setString(1, "张三");
    ps.setInt(2, 18);
    ResultSet rs = ps.executeQuery();

    // 5. 遍历结果集
    while (rs.next()) {
        System.out.println(rs.getString("name") + " / " + rs.getInt("age"));
    }
} // try-with-resources：Connection/Statement/ResultSet 自动 close
```

面试时说这五步：**加载驱动 → 获取连接 → 创建语句 → 执行 SQL → 处理结果集**。

---

## 资源释放：三个都要关（易错点）

**Connection、Statement、ResultSet 全是资源，用完都要关**——PreparedStatement 不只是 Java 对象，它背后占着数据库会话的句柄：

- 开启 `useServerPrepStmts` 时，MySQL 服务端为它缓存预编译执行计划，一个 statement 占一个槽位
- 不关的后果：数据库端 `max_prepared_stmt_count` 被打满，报错 `Can't create more than max_prepared_stmt_count statements`

**关闭顺序（由内到外）：** `ResultSet → Statement → Connection`

```java
try (Connection conn = DriverManager.getConnection(url, user, pwd);
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {   // 声明逆序关闭：rs → ps → conn，正好满足顺序
    ...
}
```

规范上关闭 Statement 会级联关闭它的 ResultSet，所以最小必要是 `Statement + Connection`。

⚠ **连接池场景的隐藏坑**：不用连接池时，`conn.close()` 真正销毁连接、级联清理 Statement；但连接池里 `conn.close()` 只是**归还**连接——泄漏的 Statement 一直挂在池里复用的连接上，日积月累把数据库句柄耗尽。所以规范是：**连接借还，Statement 用完即关**。

---

## Statement vs PreparedStatement（必考）

| 维度 | Statement | PreparedStatement |
|------|-----------|-------------------|
| SQL 构造 | 字符串拼接 | `?` 占位符 + `setXxx` 绑定 |
| 预编译 | ❌ 每次执行全量解析 | ✅ SQL 骨架先编译，参数后绑定 |
| SQL 注入 | ⚠ 拼接用户输入可被打穿 | ✅ 参数只作为「值」，不参与拼接 |
| 性能 | 相同 SQL 重复解析 | 复用编译结果，批量执行更快 |
| 用途 | 几乎不用 | 一律用它 |

**注入演示**：

```sql
SELECT * FROM user WHERE name = '张三'  -- 正常写法：字符串拼接
-- 用户输入：' or '1'='1
SELECT * FROM user WHERE name = '' or '1'='1'   -- 恒真 → 全表泄露
```

PreparedStatement 里同样的输入只会被当成一个普通字符串值 `' or '1'='1`，查不到任何行。

### 为什么表名、列名不能参数化（追问深挖）

占位符的本质是「**SQL 和参数分开传**」：服务器编译语句时 `?` 处是空位，执行时才绑定参数包（类型 + 长度 + 内容）。所以：

- **值的位置有参数槽**：`WHERE name = ?` 编译通过，执行时塞参数
- **结构的位置没有参数槽**：`FROM ?`、`ORDER BY ?` 在编译阶段就必须是真实标识符，`?` 直接语法错误——服务器从头到尾把参数当数据，不会把参数「拼」进 SQL 文本

所以表名/列名只能**白名单校验后拼接**——拼进去的字符串会被当作 SQL 代码执行，绝不能直接拼用户输入：

```java
if (!TABLE_WHITELIST.contains(tableName)) throw new IllegalArgumentException();
String sql = "SELECT * FROM " + tableName;   // 白名单校验后才允许拼
```

> 联系 MP：`LambdaQueryWrapper.eq(User::getName, v)` 的方法引用 = 「编译器版本的白名单」——列名最终仍是拼进 SQL 文本（SerializedLambda 读方法名，驼峰转下划线），字段名由编译器保证写不错；**防注入靠的不是 Lambda，而是 MP 底层把值一律用 `#{}` 占位 → MyBatis 编译成 PreparedStatement 的 `?` 参数**（JDBC 页讲的就是这个）。`QueryWrapper.eq("name", v)` 用字符串列名同样防注入，因为 v 也走参数绑定 → 详见 [[MyBatis-Plus详解]]

---

## 连接池：为什么 JDBC 离不开它

- 创建 Connection 很贵：TCP 三次握手 + 数据库认证 + 会话初始化，一次几十到几百毫秒
- 连接池：启动时预建一批连接，用完归还复用——Web 应用每秒成百上千次访问，全靠池子兜底
- 主流实现：**HikariCP**（Spring Boot 默认，快）、**Druid**（阿里，带监控面板，见 [[OLAP（Druid）详解]]）
- MyBatis / JPA 都跑在连接池之上——面试问「MyBatis 的连接哪来的」，答「数据源 DataSource → 连接池 → JDBC Connection」

---

## 事务：边界在 Connection 上

### 什么叫「边界在 Connection 上」

**这里的 Connection 就是数据库连接**，分两层看：

| 层 | 是什么 | 在哪端 |
|-----|--------|--------|
| Java 侧 | `java.sql.Connection` 对象（客户端操作入口） | 你的进程里 |
| MySQL 侧 | 一个**会话（session）**，持有认证信息、事务状态 | MySQL 服务器上 |

`DriverManager.getConnection()` 底层 = 建 TCP 连接 → 认证握手 → 服务端开一个会话，返回的 Connection 对象就是这个会话的客户端入口。**事务状态（autoCommit、未提交的修改、锁、隔离级别）实际存在 MySQL 服务端的会话上**，`conn.commit()` 是通过入口命令服务端确认该会话上的修改。

⚠ 区分：**TCP 连接 ≠ 数据库会话**。TCP（传输层）只传字节流不懂事务；会话（逻辑层）才持有事务状态。连接池 `conn.close()` 归还后两者都还活着，只是不归你了——这正是「串味」能发生的物理基础。

事务不是悬浮的概念，它是**这个会话上**从 `setAutoCommit(false)` 到 `commit()/rollback()` 之间的一组操作：

- 同一个连接上的 N 条语句 → 属于同一个事务
- **跨连接不共享**：A 连接 commit 只提交 A 连接上的修改；两个操作走了不同连接 = 两个事务
- 连接关闭 → 会话结束 → 未提交的修改自动回滚

所以 Spring 的 `@Transactional` 底层必须**保证同一线程的所有 JDBC 操作拿同一个连接**（ThreadLocal 绑定），否则一个方法里的 SQL 拆到两个连接上，事务就分家了。

```java
conn.setAutoCommit(false);   // 关掉自动提交
try {
    ps1.executeUpdate();     // 业务操作 1
    ps2.executeUpdate();     // 业务操作 2
    conn.commit();           // 一起提交
} catch (Exception e) {
    conn.rollback();         // 全部回滚
}
```

⚠ 坑：事务状态挂在连接上——从连接池拿的连接用完必须干净归还（归还前 commit/rollback 收尾），否则事务状态「串味」到下一个使用者：B 借到 A 还的脏连接，B 的 commit 会把 A 的遗留修改一起提交，且 B 的 SQL 跑在残留的 autoCommit=false 里。

---

## 驱动加载：SPI 机制（进阶考点）

- JDBC 4.0 之前：手动 `Class.forName("com.mysql.jdbc.Driver")` 触发静态注册
- JDBC 4.0 起：**SPI 自动发现**——驱动 jar 里放 `META-INF/services/java.sql.Driver` 文件，写上驱动类全限定名，`DriverManager` 启动时用 `ServiceLoader` 自动加载
- 背后的类加载坑：`DriverManager` 在 `rt.jar`（Bootstrap 加载器），而驱动实现类在应用 classpath——Bootstrap 看不到应用类，双亲委派也上不去，只能靠**线程上下文类加载器（TCCL）**向下加载 → 机制本身见 [[SPI详解]]，类加载详见 [[线程上下文类加载器（TCCL）]]

---

## 面试高频追问与话术

### Q1: JDBC 是什么？驱动是什么？

> "JDBC 是 Java 官方的数据库访问规范，`java.sql.*` 里全是接口；驱动是厂商对接口的实现——核心职责是把 JDBC 调用翻译成数据库自己的网络协议（wire protocol，如 MySQL Protocol），通过 Socket（TCP）与服务器收发数据。类比：JDBC 是 USB 标准，驱动是各厂商的适配器。换数据库 = 换驱动 + 改 URL，业务代码不动。"

### Q2: Statement 和 PreparedStatement 区别？（必考）

> "可以从三个方面说。第一，预编译——它先把 SQL 骨架发给数据库编译，参数用 `?` 占位，之后才绑定值，也就是把『语句』和『数据』分开了。第二，防注入——因为参数是作为『值』传进去的，不参与 SQL 拼接，你传 `' or '1'='1` 这种，它只会被当成一个普通字符串字面量，打不穿。第三，性能——相同的 SQL 骨架编译结果可以复用，循环里批量执行的时候收益最明显。所以正常情况下我都会用 PreparedStatement。"

**追问：PreparedStatement 有什么局限？**

> "它只能参数化『值』，像表名、列名、ORDER BY 后面的字段这种 SQL 结构是没法用占位符的——占位符会被解析成字面量，直接语法错误。这种场景只能先做白名单校验再拼接，绝对不能直接拼用户输入。"

> ⚠ 细节（被追问再说）：MySQL 驱动默认 `useServerPrepStmts=false`，此时是**客户端模拟预编译**——参数在客户端转义后拼进 SQL，靠转义防注入，并非真发给服务器编译；开启该参数才是服务端预编译（走二进制协议）。面试主动说容易引坑，被问到再展开。

### 预编译的两种真相（被追问再展开）

| 参数 | 模式 | 预编译发生在哪 | 防注入靠什么 |
|------|------|----------------|--------------|
| `useServerPrepStmts=false`（**默认**） | 客户端模拟 | **不预编译**，参数转义后拼 SQL 文本 | 转义（`'` → `\'`） |
| `useServerPrepStmts=true` | 服务端预编译 | 骨架先发服务器编译，参数走二进制协议单独传 | 参数不进 SQL 文本 |

**默认模式（客户端模拟）实际干了什么：**

```text
你的输入：张'三
驱动转义：张\'三          ← 单引号前加反斜杠
拼成文本：SELECT * FROM user WHERE name = '张\'三'   ← 完整一条 SQL 发给服务器
服务器：  当普通语句执行（和 Statement 一样，每次全量解析）
```

所以 `' or '1'='1` 打不穿，靠的是**转义让引号失去闭合作用**，不是服务端编译。

**开启后（服务端预编译）实际干了什么：**

```text
第 1 步：发 PREPARE stmt1 FROM 'SELECT * FROM user WHERE name = ?'
         服务器编译、缓存执行计划
第 2 步：发 EXECUTE stmt1 USING 参数    ← 参数单独编码走二进制协议
         参数从头到尾不进 SQL 文本，天然不可能注入
```

**为什么默认关着？**

- 服务器资源：每个预编译语句占一个槽位（`max_prepared_stmt_count` 默认 16382），连接多、语句多会打满
- 兼容性：读写分离代理 / PXC 等中间件对二进制协议的准备语句支持差，可能断连
- 一次性语句更亏：多一次 PREPARE 往返，只执行一次的 SQL 反而更慢

所以驱动为「开箱即用」默认选客户端模拟。**规范答案是主干，这个细节是备胎**——面试深挖「预编译真发生在服务端吗」时才掏出来，主动说容易被当成抬杠。

### Q3: 为什么需要连接池？

> "建连接的代价大：TCP 握手 + 认证 + 会话初始化，几十毫秒起步，高并发下扛不住。连接池预建 + 复用，把『创建/销毁』变成『借/还』，和线程池一个思路。Spring Boot 默认 HikariCP，阿里系常用 Druid（带监控）。"

### Q4: Class.forName 还需要写吗？

> "JDBC 4.0 之后不用了——驱动 jar 里有 `META-INF/services/java.sql.Driver` 的 SPI 描述文件，DriverManager 类初始化时通过 ServiceLoader 自动发现驱动并实例化。Class.forName 是老写法，写了也不报错，但没必要。"

### Q5: JDBC 和 MyBatis / JPA 什么关系？

> "一条链路：ORM 框架在 JDBC 之上。MyBatis 把 XML 里的 SQL 拿出来，还是用 PreparedStatement 执行、ResultSet 反射映射成对象；JPA/Hibernate 把实体注解翻译成 SQL 后同样走 JDBC。所以任何 ORM 的尽头都是 JDBC。"

### Q6: JDBC 怎么保证事务？

> "`Connection.setAutoCommit(false)` 关自动提交，业务全成功就 commit，异常就 rollback，事务边界在连接上。所以框架层的事务（`@Transactional`）底层也是包着这套机制——Spring 事务保证同一个线程拿的是同一个连接。"

---

## 常见踩坑清单

| 坑 | 现象 | 解决 |
|----|------|------|
| SQL 注入 | 登录绕过、数据泄露 | PreparedStatement，禁止拼接 |
| 连接泄漏 | 连接数打满、应用假死 | try-with-resources / 连接池超时回收 |
| 资源没关 | 文件句柄、游标耗尽 | 统一 close 或 try-with-resources |
| 驱动类加载异常 | ClassNotFoundException / NoClassDefFoundError | 检查依赖 + SPI 文件是否被包进 jar |
| 事务串味 | 连接池里上个事务没提交干净 | 归还前 commit/rollback 收尾 |

---

## 通俗理解

> 数据库是「插座」，JDBC 是「统一插孔标准」，厂商驱动是「适配器」——你按标准做电器（写代码），任何牌子的插座都能插。MyBatis / JPA 是「全自动洗衣机」：你按个按钮（调接口），它自己接水管、通电（管理连接、生成 SQL），但水管最末端接的还是那个标准插座（JDBC）。面试被问到底层时，就顺着洗衣机拆到插座——这就是「任何 ORM 的尽头都是 JDBC」。

---

## 关联知识点

- [[JPA详解]] — 上层规范，最终通过 JDBC 执行 SQL；「规范 vs 实现」思想同源
- [[MyBatis详解]] — 半自动框架，本质是 JDBC 的封装（PreparedStatement + ResultSet 映射）
- [[MyBatis-Plus详解]] — 项目实际使用的持久层框架
- [[OLAP（Druid）详解]] — 连接池：JDBC 连接的复用、监控与防注入
- [[线程上下文类加载器（TCCL）]] — 驱动 SPI 加载背后的类加载机制（双亲委派为什么失效）
- [[面试分析报告#技术栈全景]] — 本项目持久层选型与 JDBC 的定位

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-12 15:36 | 2026-08-12 15:41 |  | 第一轮背记完成 |
