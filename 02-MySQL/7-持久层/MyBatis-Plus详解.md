---
tags:
  - 其他
背诵状态: 已完成
背诵开始: 2026-08-11 16:28
背诵结束: 2026-08-11 16:50
created: 2026-06-28
updated: 2026-06-28
---
# MyBatis-Plus — MyBatis 增强工具详解

## 一句话定义

> **MyBatis-Plus**（简称 **MP**）是 MyBatis 的**增强工具**，只做增强不做改变——在 MyBatis 基础上自动提供 **CRUD、分页、条件构造器**等能力，无需手写 Mapper XML 和 SQL。


**本项目实现**：MyBatis-Plus + MySQL 做持久层，配合 MyBatis-Plus Generator 自动生成 Mapper/Service/Controller 样板代码；防 SQL 注入靠 `#{}` 占位符 + PreparedStatement 参数绑定，Lambda 查询语法提供列名类型安全。

---

## 核心能力

### 1. 基础 CRUD — 零 SQL

```java
// 继承 BaseMapper<T> 即可获得所有基础 CRUD
public interface UserMapper extends BaseMapper<User> {
    // 无需任何方法声明！
}

// 直接用：
userMapper.insert(user);
userMapper.selectById(1L);
userMapper.selectList(Wrappers.lambdaQuery().eq(User::getAge, 18));
userMapper.updateById(user);
userMapper.deleteById(1L);
```

| 方法 | 作用 |
|------|------|
| `insert(T entity)` | 插入一条记录 |
| `deleteById(Serializable id)` | 根据 ID 删除 |
| `updateById(T entity)` | 根据 ID 更新 |
| `selectById(Serializable id)` | 根据 ID 查询 |
| `selectList(Wrapper<T> wrapper)` | 条件查询 |
| `selectPage(Page<T> page, Wrapper<T> wrapper)` | 分页查询 |

### 2. 条件构造器（Wrapper）— 核心卖点

**Lambda 风格**：编译期类型安全，字段名写错直接报错。

```java
// 传统 MyBatis：手写 XML
// SELECT * FROM user WHERE age >= 18 AND name LIKE '%张%' ORDER BY age DESC

// MyBatis-Plus LambdaQueryWrapper：
List<User> users = userMapper.selectList(
    Wrappers.<User>lambdaQuery()
        .ge(User::getAge, 18)         // age >= 18
        .like(User::getName, "张")     // name LIKE '%张%'
        .orderByDesc(User::getAge)     // ORDER BY age DESC
);
```

**常用方法**：

| 方法 | 含义 |
|------|------|
| `eq` / `ne` | 等于 / 不等于 |
| `gt` / `ge` / `lt` / `le` | 大于 / ≥ / 小于 / ≤ |
| `like` / `notLike` | LIKE / NOT LIKE |
| `in` / `notIn` | IN / NOT IN |
| `isNull` / `isNotNull` | IS NULL / IS NOT NULL |
| `between` / `notBetween` | BETWEEN / NOT BETWEEN |
| `and` / `or` | 逻辑与 / 或 |
| `orderByAsc` / `orderByDesc` | 排序 |
| `last(String)` | 追加自定义 SQL（⚠ 有注入风险） |

### 3. 分页插件

```java
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

// 使用：
Page<User> page = userMapper.selectPage(
    new Page<>(1, 10), // 第 1 页，每页 10 条
    Wrappers.lambdaQuery()
);
page.getRecords();     // 当前页数据
page.getTotal();       // 总记录数
page.getPages();       // 总页数
```

**原理**：`PaginationInnerInterceptor` 拦截器在执行 SQL 前自动拼接 `LIMIT ? OFFSET ?`，同时自动执行 `SELECT COUNT(*)` 查询总记录数。

### 4. 代码生成器（AutoGenerator）

```java
// 一键生成：Entity + Mapper + Service + Controller
FastAutoGenerator.create("jdbc:mysql://localhost:3306/db", "root", "123456")
    .globalConfig(builder -> builder.author("Nozomi").outputDir("src/main/java"))
    .packageConfig(builder -> builder.parent("com.shop").entity("entity"))
    .strategyConfig(builder -> builder.addInclude("user", "order"))
    .execute();
```

**生成产物**：

```
src/main/java/com/shop/
├── entity/User.java        // @TableName、@TableId 注解
├── mapper/UserMapper.java  // extends BaseMapper<User>
├── service/IUserService.java
├── service/impl/UserServiceImpl.java
└── controller/UserController.java
```

### 5. 常用注解

| 注解 | 作用 |
|------|------|
| `@TableName("user_table")` | 实体类 → 数据库表映射（驼峰→下划线自动转换可省） |
| `@TableId(type = IdType.AUTO)` | 主键策略（AUTO / ASSIGN_ID / INPUT） |
| `@TableField("nick_name")` | 字段映射，`exist = false` 标注非数据库字段 |
| `@Version` | 乐观锁版本号 |
| `@TableLogic` | 逻辑删除标记 |

---

## 面试高频追问与话术

### Q1: MyBatis-Plus 和 MyBatis 的区别？

> **面试话术**：
> "MyBatis-Plus 是 MyBatis 的增强工具，不是替代品。它保留了 MyBatis 的所有能力（手写 SQL、动态 SQL、结果映射），额外提供了三样东西：① **BaseMapper 基类**——基础的 CRUD 不用写 SQL 了；② **条件构造器**——用 Lambda 语法拼条件，类型安全；③ **分页插件**——自动拼接 LIMIT 和 COUNT。核心原则是'只做增强不做改变'，所以你原来在 MyBatis 里手写的复杂 SQL、自定义映射完全不受影响。"

### Q2: 为什么用 MyBatis-Plus 而不是 JPA？

> **面试话术**：
> "JPA 是 ORM 全自动方案，开发者不需要写 SQL。但**电商项目**的查询往往比较复杂——多表关联、动态条件、分页排序——JPA 的 HQL / `@Query` 在这种情况下可读性和可控性都不如 MyBatis 手写 SQL。MyBatis-Plus 取了中间值：简单 CRUD 用 BaseMapper 零 SQL，复杂查询可以用条件构造器或者直接手写 XML。我们项目的选择是 MyBatis-Plus + 手写复杂 SQL，这样既保证了开发效率又保留了 SQL 的调优空间。"

### Q3: LambdaQueryWrapper 凭什么能防止 SQL 注入？

> **面试话术（背诵版）**：
> MP 底层生成 SQL 时参数一律用 `#{}` 占位符，通过 PreparedStatement 绑定值，所以防注入；Lambda 只是替代手写字符串列名，属于便利性和类型安全，不是防注入的关键。

**追问：QueryWrapper 用字符串列名会注入吗？**

> "不会。列名怎么写不影响防注入——防注入靠的是值走 `#{}` 参数绑定，字符串列名也一样安全。"

### 详细解释（理解用，不用背）

**防注入的完整链路：**

```text
MP 生成 SQL：WHERE name = #{name}
    ↓ MyBatis 解析
骨架：WHERE name = ?          （#{} 编译成 ? 占位符）
    ↓ 交给 JDBC
PreparedStatement：ps.setString(1, 值)   （值作为参数存进驱动对象内部）
    ↓ execute 时驱动以参数方式处理
MySQL：语句 + 参数（编码或转义后发出）
```

**「驱动对象」是什么：** `ps` 的真实类型是驱动 jar 里的实现类（MySQL 是 `ClientPreparedStatementImpl`）。`setXxx` 调的是它的方法，值只存进对象内部的参数数组，**不发网络**；`execute` 时才把值编码（服务端预编译）或转义（客户端模拟）后发出。

**`#{}` vs `${}` 的分水岭：**

| 语法 | 值到达驱动时 | 结果 |
|------|--------------|------|
| `#{}` | 是「参数」 | 驱动以参数方式处理 → 防注入 |
| `${}` | 已是 SQL 文本的一部分 | 原样发送 → 不防注入 |

区别在 MyBatis 层就定了：`#{}` 值走参数通道，`${}` 值走文本通道（直接拼进 SQL 字符串）。

**驱动处理参数的两种模式：**

| 模式 | 驱动怎么发参数 | 防注入靠什么 |
|------|----------------|--------------|
| `useServerPrepStmts=false`（默认） | 转义（`'` → `\'`）后拼进 SQL 文本 | 转义让引号失去闭合作用 |
| `useServerPrepStmts=true` | 参数走二进制协议单独传 | 值物理上不进 SQL 文本 |

两种模式手段不同，`#{}` 都防、`${}` 都不防 → 详见 [[JDBC详解]]「预编译的两种真相」

### Q4: MyBatis-Plus 分页插件原理？

> **面试话术**：
> "MyBatis-Plus 通过 `MybatisPlusInterceptor` 拦截 `Executor` 的 query 方法。当检测到参数包含 `Page` 对象时，自动执行两步：① 把原 SQL 包装成 `SELECT COUNT(*) FROM (原SQL) total` 查总数；② 在原 SQL 末尾拼接 `LIMIT ? OFFSET ?`。注意旧版的 `PaginationInterceptor` 在新版已经废弃了，要用 `MybatisPlusInterceptor` 加 `PaginationInnerInterceptor`。"

### Q5: 有哪些踩过的坑？

> **面试话术**：
> "几个常见的坑：
> ① **分页插件不生效**——旧版要配 `PaginationInterceptor`，新版要配 `MybatisPlusInterceptor` + `PaginationInnerInterceptor`，版本对不上就不生效；
> ② **updateById 会全字段更新**——默认更新所有非 null 字段，如果用 `@TableField(updateStrategy = FieldStrategy.IGNORED)` 可以控制策略，或直接用 `LambdaUpdateWrapper` 指定更新字段；
> ③ **逻辑删除和唯一索引冲突**——如果字段加了 `@TableLogic` 标记逻辑删除，但删了再插入相同业务主键时会报唯一索引冲突，需要把逻辑删除字段加入唯一索引或用时间戳后缀；
> ④ **Seata + MyBatis-Plus 分页冲突**——Seata 代理数据源后分页拦截器可能失效，需要在代理后重新配置。"

---

## MyBatis-Plus vs JPA vs 原生 MyBatis

| 维度 | MyBatis-Plus ✅ | 原生 MyBatis | JPA / Hibernate |
|------|----------------|-------------|------------------|
| CRUD 效率 | 零 SQL | 每个操作写 XML | 零 SQL（更自动） |
| 复杂 SQL | 手写 XML / 条件构造器 | 手写 XML | JPQL / @Query |
| 类型安全 | ✅ LambdaWrapper | ❌ 字符串拼接 | ⚠ JPQL 编译期不检查 |
| 分页 | 插件自动 | 手写 LIMIT | Pageable 接口 |
| 缓存 | ⚠ 仅一级 | 二级缓存 | 一级+二级+查询缓存 |
| 学习曲线 | 低（MyBatis基础+Plus注解） | 中 | 高（缓存/懒加载/级联） |

---

## 本项目用法

### 依赖

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.3</version>
</dependency>
```

### 配置

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: auto                # 主键自增
      logic-delete-field: deleted  # 逻辑删除字段
      logic-delete-value: 1        # 已删除
      logic-not-delete-value: 0    # 未删除
  configuration:
    map-underscore-to-camel-case: true  # 驼峰映射
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 开发环境打印 SQL
```

### 典型 CRUD 示例

```java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    
    public Page<User> searchUser(String name, Integer age, int pageNum, int pageSize) {
        return this.page(
            new Page<>(pageNum, pageSize),
            Wrappers.<User>lambdaQuery()
                .like(StringUtils.isNotBlank(name), User::getName, name)   // 条件带空判断
                .ge(age != null, User::getAge, age)
                .orderByDesc(User::getCreateTime)
        );
    }
}
```

---

## 关联知识点

- [[面试分析报告#技术栈全景]] — 本项目技术栈中 MyBatis-Plus 定位
- [[面试分析报告#Q7 踩坑经验]] — MyBatis-Plus 分页失效等实战问题
- [[N+1查询问题详解]] — ORM 性能坑，MyBatis-Plus 同样会遇到
- [[Feign详解]] — 跨服务调用与 Seata 结合时的分页配置冲突
- [[Seata详解]] — 全局事务下 MyBatis-Plus 分页插件配置注意事项

---

> **通俗理解**：MyBatis-Plus 就像给 MyBatis 装了"自动挡"。
>
> MyBatis 本身是手动挡——你需要自己踩离合（写 SQL）、换挡（调参数）、加油门（控制 SQL 执行）。MyBatis-Plus 保留了手动模式（你依然可以手写复杂 SQL），但加上了自动模式——你只要说"我要开"（`selectList`），它自己帮你完成挂挡起步。简单 CRUD 用自动挡，复杂查询切回手动挡，两全其美。

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-11 16:28 | 2026-08-11 16:50 |  | 第一轮背记完成 |
