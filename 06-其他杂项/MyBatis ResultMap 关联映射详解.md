# MyBatis ResultMap — association & collection 详解

## 一句话

**association** 和 **collection** 是 MyBatis ResultMap 里处理关联查询的两个标签，解决表 JOIN 查询后结果怎么装进 Java 对象的问题。

| 标签 | 关系 | Java 属性类型 | 例子 |
|------|------|-------------|------|
| `<association>` | 一对一 | 单个对象 | Order → User |
| `<collection>` | 一对多 | 集合（List） | Order → List\<OrderItem\> |

---

## association — 一对一

### 场景

一个订单属于一个用户：`Order → User`

### Java 类

```java
public class Order {
    private Long orderId;
    private User user;     // ← 一对一，单个对象
}

public class User {
    private Long userId;
    private String userName;
}
```

### ResultMap 定义

```xml
<resultMap id="orderMap" type="Order">
    <id column="order_id" property="orderId"/>

    <!-- Order 里有个 user 属性，装的是 User 对象 -->
    <association property="user" javaType="User">
        <id column="user_id" property="userId"/>
        <result column="user_name" property="userName"/>
    </association>
</resultMap>
```

| 属性 | 含义 |
|------|------|
| `property="user"` | Order 类里的属性名 |
| `javaType="User"` | 这个属性的 Java 类型 |

### 对应的 SQL

```sql
SELECT o.order_id,
       u.user_id, u.user_name
FROM order_info o
LEFT JOIN user u ON o.user_id = u.user_id
WHERE o.order_id = #{id}
```

### 映射过程

SQL 返回一行结果，包含订单和用户字段。MyBatis 按 `resultMap` 的配置装填：

```
SQL 结果集一行：
┌───────────┬─────────┬───────────┐
│ order_id  │ user_id │ user_name │
│ 1001      │ 42      │ 张三      │
└───────────┴─────────┴───────────┘

  order_id ──────────────▶ orderId
                           ↓
  user_id + user_name ──▶ user (User 对象)
                           ├── userId: 42
                           └── userName: "张三"
```

**最终对象结构**：

```
Order {
  orderId: 1001,
  user: User {           ← association 装出来的嵌套对象
    userId: 42,
    userName: "张三"
  }
}
```

---

## collection — 一对多

### 场景

一个订单包含多个商品条目：`Order → List<OrderItem>`

### Java 类

```java
public class Order {
    private Long orderId;
    private List<OrderItem> items;   // ← 一对多，集合
}

public class OrderItem {
    private Long itemId;
    private String skuName;
    private BigDecimal price;
}
```

### ResultMap 定义

```xml
<resultMap id="orderWithItemsMap" type="Order">
    <id column="order_id" property="orderId"/>

    <!-- Order 里有个 items 属性，是 List<OrderItem> -->
    <collection property="items" ofType="OrderItem">
        <id column="item_id" property="itemId"/>
        <result column="sku_name" property="skuName"/>
        <result column="price" property="price"/>
    </collection>
</resultMap>
```

| 属性 | 含义 |
|------|------|
| `property="items"` | Order 类里的属性名 |
| `ofType="OrderItem"` | 集合里元素的类型（不是集合本身的类型） |

### 对应的 SQL

```sql
SELECT o.order_id,
       i.item_id, i.sku_name, i.price
FROM order_info o
LEFT JOIN order_item i ON o.order_id = i.order_id
WHERE o.order_id = #{id}
ORDER BY o.order_id   -- 必须按主表 ID 排序
```

### 映射过程

一个订单有 3 件商品，SQL 返回 3 行。MyBatis 按 `order_id` **合并行**，把商品字段装进 List：

```
SQL 结果集（3 行）：
┌───────────┬─────────┬───────────┬───────┐
│ order_id  │ item_id │ sku_name  │ price │
├───────────┼─────────┼───────────┼───────┤
│ 1001      │ 1       │ 手机      │ 2999  │
│ 1001      │ 2       │ 钢化膜    │ 19    │  ← order_id 相同，合并
│ 1001      │ 3       │ 手机壳    │ 29    │
└───────────┴─────────┴───────────┴───────┘
                  │
                  ▼
          Order 只创建一次
                   │
    ┌──────────────┴──────────────┐
    │                              │
 order_id: 1001           items: List<OrderItem>
                              ├── { itemId: 1, skuName: "手机",   price: 2999 }
                              ├── { itemId: 2, skuName: "钢化膜", price: 19   }
                              └── { itemId: 3, skuName: "手机壳", price: 29   }
```

**最终对象结构**：

```
Order {
  orderId: 1001,
  items: [                    ← collection 装出来的对象列表
    { itemId: 1, skuName: "手机",   price: 2999 },
    { itemId: 2, skuName: "钢化膜", price: 19   },
    { itemId: 3, skuName: "手机壳", price: 29   }
  ]
}
```

---

## 对照总结

| | association | collection |
|--|------------|------------|
| 关系 | 一对一 | 一对多 |
| 属性类型 | 单个对象 | List / Set |
| 声明属性 | `javaType`（对象类型） | `ofType`（集合元素类型） |
| 判重依据 | 主表 `<id>` | 主表 `<id>` 合并 + 子表 `<id>` 去重 |
| SQL 要求 | JOIN 即可 | JOIN + ORDER BY 主表 ID |
| 返回行数 | 1 行 | N 行（主子表乘积） |
| 装填结果 | 1 个嵌套对象 | N 个对象装进 List |

---

## 三个要命的细节

### 1. `<id>` 必须配

MyBatis 靠 `<id>` 判断行的唯一性。`<id column="order_id">` 告诉 MyBatis "order_id 相同的行属于同一个 Order 对象"。如果不配 `<id>`，MyBatis 会把所有列都拿来判重，同一个订单可能生成多个 Order 对象。

```xml
<!-- ✅ 正确：配了 id -->
<id column="order_id" property="orderId"/>

<!-- ❌ 错误：不配 id，可能生成重复对象 -->
<result column="order_id" property="orderId"/>
```

### 2. collection 必须 ORDER BY

一对多查询必须按主表 ID 排序，否则 MyBatis 处理到第二行时发现 `order_id` 变了，不会再往当前 List 里加。

```sql
-- ✅ 必须 ORDER BY
SELECT ... FROM order_info o LEFT JOIN order_item i ...
WHERE o.order_id = #{id}
ORDER BY o.order_id   -- 保证同一订单的行连续
```

### 3. 分清 N+1 和 JOIN

MyBatis 支持两种实现方式：

| 方式 | 写法 | 查询次数 | 性能 |
|------|------|---------|------|
| **嵌套结果**（JOIN） | `<association>` / `<collection>` 里直接映射 | 1 次 | ✅ 好 |
| **嵌套查询**（N+1） | `<association select="otherMapper.method"/>` | N + 1 次 | ❌ 差 |

```xml
<!-- ✅ JOIN 方式（推荐）：一次查完 -->
<association property="user" javaType="User"
    column="user_id"     ← 来自 JOIN 结果集的列
    select="selectUserById"/>   ← 额外再查一次 — 这就是 N+1！
```

嵌套查询写法方便但**性能极差**——查 100 个订单要额外查 100 次用户表。生产环境尽量用 JOIN。

---

## 关联知识点

- [[MyBatis详解]] — MyBatis 总览
- [[N+1查询问题详解]] — 嵌套查询的性能坑
- [[MyBatis-Plus详解]] — MyBatis 增强工具（不支持复杂的 association/collection 映射）
