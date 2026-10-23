---
tags:
  - spring
  - 事务
  - 面试题
created: 2026-10-14
updated: 2026-10-14
source: 用户提问答疑（2026-10-14）
---

# @Transactional 加在类上和方法上有什么区别？

> **一句话结论：** 类上的注解是**默认配置**，作用于该类所有符合条件的 public 方法；方法上的注解只对**它自己那个方法**生效；两者同时存在时**以方法上的为准，而且是「覆盖」不是「合并」**。

---

## 一、作用范围

`@Transactional` 既可以标在类上，也可以标在方法上，两者的作用范围不同：

| 位置 | 作用范围 | 适用场景 |
|------|----------|----------|
| **类上** | 该类中所有符合条件的 public 方法 | 统一设定事务默认行为，例如把整个 Service 设为只读 |
| **方法上** | 仅它所在的那一个方法 | 为个别方法设定特殊规则，例如某写操作单独设回滚规则、超时时间 |

---

## 二、优先级：覆盖，而非合并（关键坑）【高频】

当类和方法**同时**标注时，Spring 以**方法上的配置为准**。但这是**覆盖**，不是**合并**——**方法上没有显式写出的属性，不会继承类上的值，而是回落到 `@Transactional` 的默认值。**

举例：类上写了 `readOnly = true`，方法上只写了一个光秃秃的 `@Transactional`，那么该方法的 `readOnly` 实际是 **false**（默认值），而不是继承类上的 true。

> [!warning] 所以覆盖时要把属性完整写出来
> 想保留类上的某个配置，方法上必须**重新显式声明**，否则会被默认值冲掉。

---

## 三、代码示例

```java
@Service
@Transactional(readOnly = true)
public class OrderService {

    public Order findById(Long id) {
        // 继承类上的配置：只读事务
        return orderMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createOrder(OrderDTO dto) {
        // 方法上的配置覆盖类上的配置：readOnly 变为 false，
        // 且受检异常也会触发回滚
        orderMapper.insert(dto);
    }
}
```

`createOrder` 上只写了 `rollbackFor`，但因为它**整体覆盖**了类上的配置，`readOnly` 变成了默认的 `false`——这正好符合写操作的需要。反过来如果某方法既要写又要保持类上其它配置，就得把这些属性一起写上。

---

## 四、三个容易忽略的要点

### 1. 默认只对 public 方法生效

默认情况下，事务**只对 public 方法生效**。标注在 `protected` 或 `private` 方法上**通常不会有效果**——因为 Spring AOP 代理无法拦截（CGLIB 子类无法覆写 private，protected/default 也不推荐）。

### 2. 自调用（this 调用）会导致注解失效

同一个类中，方法 A 直接调用同类里标了 `@Transactional` 的方法 B，这个调用**不经过 Spring 生成的代理对象**，因此 B 上的事务规则不生效——**类上的规则同样如此**（也走不到代理）。

解决办法：把方法拆到另一个 Bean 中，或者通过**注入自身代理**（`AopContext.currentProxy()` / 自注入）来调用。

> 详见 [[spring八股#Spring的事务在什么情况下会失效？]] 与 [[Spring-interview-guide#4.2 @Transactional 在哪些情况下会失效？]]。

### 3. 默认回滚规则只认 RuntimeException 和 Error

默认回滚规则**只针对 `RuntimeException` 和 `Error`**，遇到受检异常（如 `IOException`、`Exception`）**不会回滚**，需要用 `rollbackFor` 显式指定，例如 `@Transactional(rollbackFor = Exception.class)`。

---

## 五、实践共识

实际开发中常见的做法是：**在类上标注 `@Transactional` 作为统一基线**，查询类方法可以保持只读，需要特殊处理的写操作再**在方法上单独覆盖**，并注意**覆盖时把要用的属性完整写出**。

---

## 关联知识点

- [[spring八股]] — Spring 全家桶八股总览（事务失效场景、传播机制）
- [[Spring-interview-guide]] — Spring 事务口述版（编程式 vs 声明式、失效场景）
- [[Seata详解]] — 分布式事务（`@GlobalTransactional` 与本地 `@Transactional` 的区别）
- [[Seata @GlobalTransactional 使用规范]] — 全局事务注解使用规范
- [[Spring ioc]] — AOP 代理与 Bean 生命周期
