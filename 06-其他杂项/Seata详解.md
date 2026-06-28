# Seata — 分布式事务中间件详解

## 一句话定义

> **Seata**（Simple Extensible Autonomous Transaction Architecture）是阿里巴巴开源的**分布式事务中间件**，为微服务架构提供跨数据库/跨服务的强一致性事务能力。
>
> 发音：`/siːˈɑːtə/`（西-阿-塔），中文常读作 "西阿塔"

**本项目实现**：Seata AT 模式，覆盖下单链路（扣库存 → 创建订单 → 清购物车）共 5 步操作的分布式事务。
 
---

## 架构角色

Seata 采用 **TC → TM → RM** 三层模型：

```
                   ┌──────────────────┐
                   │   TC (Server)    │ ← 事务协调者，维护全局事务状态
                   │                  │
                   │   seata-server   │
                   └──────┬───────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
   ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
   │   TM        │ │   RM        │ │   RM        │
   │ 事务管理器    │ │ 资源管理器    │ │ 资源管理器    │
   │             │ │             │ │             │
   │ 下单服务      │ │ 库存服务      │ │ 订单服务      │
   └─────────────┘ └─────────────┘ └─────────────┘
```

| 角色 | 全称 | 职责 |
|------|------|------|
| **TC** | Transaction Coordinator | 全局事务协调者，维护全局事务 ID 和分支事务状态 |
| **TM** | Transaction Manager | 定义全局事务边界（`@GlobalTransactional`），向 TC 发起 begin/commit/rollback 指令 |
| **RM** | Resource Manager | 管理分支事务的资源，向 TC 注册分支，报告状态 |

---

## 四种事务模式详解

### AT 模式（Automatic Transaction）— 自动补偿 ✅ 本项目使用

**特点**：对业务代码零侵入，自动生成回滚 SQL。

**前提条件**：
- 使用支持 ACID 的**关系型数据库**（MySQL、PostgreSQL 等）
- 使用 **JDBC** 数据源（Seata 代理数据源连接池）

**一阶段流程**：

```
1. TM 向 TC 发起 begin，获取全局事务 XID
2. RM 执行业务 SQL（如 UPDATE sku SET num = num - 1 WHERE id = ?）
3. Seata 自动拦截该 SQL，生成：
   ┌─ 前镜像（Before Image）— SQL 执行前的数据快照
   └─ 后镜像（After Image） — SQL 执行后的数据快照
4. RM 将 undo log（前镜像 + 后镜像 + 事务信息）通过本地事务写入 undo_log 表
5. 向 TC 报告分支事务状态
```

**二阶段流程**：

```
         ┌── 全部分支成功 ──▶ TC 通知各 RM 删除 undo log（异步，不阻塞）
         │
TC 决策 ──┤
         │
         └── 任一分支失败 ──▶ TC 通知所有 RM 用 undo log 反向 SQL 回滚
                             RM 取出前镜像，生成逆向 SQL 恢复数据
```

**图解**：

```
              一阶段                       二阶段
  ┌─────────────────────┐        ┌──────────────────────┐
  │ 执行业务 SQL         │        │ 全部成功 → 删 undo log │
  │ 自动生成 undo log    │ ─────▶ │ 任一失败 → 反向回滚     │
  │ 提交本地事务（暂锁）   │        │ 释放全局锁             │
  └─────────────────────┘        └──────────────────────┘
```

**⚠ 注意**：一阶段提交后 Seata 会持有**全局锁**（行级），其他事务需要等待锁释放。这是 AT 模式的性能瓶颈。

---

### TCC 模式（Try-Confirm-Cancel）

**特点**：业务代码需要手动实现三个阶段接口，侵入性高但灵活性最强。

| 阶段 | 操作 | 说明 |
|------|------|------|
| **Try** | 预留资源 | 如库存冻结、账户预扣 |
| **Confirm** | 确认执行 | 将预留资源真正扣减 |
| **Cancel** | 取消回滚 | 释放预留资源 |

**适用场景**：
- 高并发短事务（如秒杀扣库存）
- 对隔离性有严格要求

**面试坑点**：
- ⚠ Confirm 和 Cancel 必须**幂等**（AT 模式自动保证，TCC 需要手动保证）
- ⚠ 空回滚问题（Try 未执行就收到 Cancel）
- ⚠ 悬挂问题（Cancel 先于 Try 执行完毕）

---

### Saga 模式

**特点**：基于状态机编排的**长事务**方案，每个节点提交本地事务，失败时通过**补偿事务**回滚。

```
下单 Saga 示例：
  下单 ──▶ 扣库存 ──▶ 支付 ──▶ 发货
                                  │
                          ┌───────┘
                          ▼
                    回滚：退款 → 加库存 → 取消订单
```

**适用场景**：
- 业务流程很长（几天到几周）
- 对隔离性要求低
- 不要求强一致性，最终一致即可

**⚠ 缺点**：无隔离性，中间状态对外可见，需要业务层处理脏读。

---

### XA 模式

**特点**：基于数据库本身的 XA 协议，Seata 只是协调者，由数据库完成两阶段提交。

```
阶段一：所有分支预提交（prepare）
阶段二：全部成功 → 真正提交（commit）
        任一失败 → 全部回滚（rollback）
```

**适用场景**：
- 使用支持 XA 的数据库（Oracle、MySQL InnoDB 等）
- 追求强一致性的短事务

**⚠ 缺点**：阻塞式协议，性能差；依赖数据库 XA 实现；MySQL XA 有 bug（某些版本）。

---

## 四模式选型对比

| 维度 | AT | TCC | Saga | XA |
|------|-----|-----|------|-----|
| 业务侵入 | 低（加注解） | 高（三接口） | 中（状态机） | 低 |
| 隔离性 | ✅ 全局锁保证 | ✅ 业务保证 | ❌ 无 | ✅ 隔离级别 |
| 一致性 | 强一致 | 强一致 | 最终一致 | 强一致 |
| 性能 | 中（全局锁开销） | 高（无锁） | 高 | 低（阻塞） |
| 典型场景 | 下单、支付 | 秒杀、扣减 | 长流程审批 | 银行转账 |

---

## 本项目：下单分布式事务

### 事务链路

```
@GlobalTransactional 开始 ─── 全局事务 XID 生成
    │
    ├── ① 扣库存（商品服务） ─── UPDATE sku SET num = num - #{num}
    ├── ② 创建订单（订单服务） ─── INSERT INTO order_info
    ├── ③ 清购物车（购物车服务） ─── DELETE FROM cart_items
    ├── ④ 更新日志                   
    └── ⑤ 其他后置操作
```

### 代码示例

```java
@Service
public class OrderService {

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public Order createOrder(OrderDTO dto) {
        // 1. 扣库存（跨服务 Feign 调用）
        skuService.deductStock(dto.getSkuId(), dto.getNum());
        
        // 2. 创建订单（本地数据库操作）
        Order order = new Order();
        order.setUserId(dto.getUserId());
        order.setTotalAmount(dto.getTotal());
        orderMapper.insert(order);
        
        // 3. 清购物车（跨服务 Feign 调用）
        cartService.clearCart(dto.getUserId());
        
        return order;
        // 方法正常返回 → Seata 自动提交全局事务
        // 方法抛异常 → Seata 自动回滚所有分支
    }
}
```

### YAML 配置（Spring Boot 微服务）

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: tx_shop           # 事务服务组名
  registry:
    type: nacos
    nacos:
      server-addr: 192.168.172.100:8848
      namespace: ""
      group: SEATA_GROUP
      cluster: default
  service:
    vgroup-mapping:
      tx_shop: default                 # 虚拟组 → 真实集群映射
  data-source-proxy-mode: AT           # 数据源代理模式（AT / XA）
```

---

## 面试高频追问与话术

### Q1: Seata AT 和 TCC 怎么选？
> **面试话术**：
> "AT 模式对业务零侵入，适合业务逻辑复杂但并发不太极端的场景，比如我们的下单链路。TCC 需要手写 Try/Confirm/Cancel 三接口，开发成本高，但性能更好、不用全局锁，适合秒杀这种短事务高并发场景。我们项目下单用 AT，秒杀用 Redisson 分布式锁而不是 Seata，因为全局锁在高并发下吞吐不够。"

### Q2: AT 模式的全局锁会不会成为性能瓶颈？
> **面试话术**：
> "会的。AT 模型在一阶段提交后持有全局锁，全局锁释放前其他分支事务要等待。并发量大的时候，这个等待时间会明显拖慢。所以 AT 适合并发不太极致的场景。如果面试官接着问怎么优化：可以缩短短事务执行时间、用 TCC 替代 AT、或者用数据库乐观锁 + 消息队列做最终一致性方案。"

### Q3: undo log 满了怎么办？undo_log 表会不会无限膨胀？
> **面试话术**：
> "undo_log 中的记录在二阶段完成后会被异步删除。只有在全局事务正在执行或异常恢复期间，undo_log 才会有数据。所以正常情况下不会无限膨胀——一阶段结束后回滚信息就可以清理了（无需等待二阶段全部事务完成）。"

### Q4: AT 模式如何保证隔离性？
> **面试话术**：
> "AT 模式通过**全局写锁**（Global Lock）保证写隔离——一阶段提交后，其他事务要等待全局锁释放才能修改同一行数据。但 AT 不保证读隔离，也就是说一个分支事务一阶段提交后，它的修改对其它事务可见——这可能导致脏读。如果需要读隔离，可以用 TCC 或 XA。"

### Q5: Seata 和消息队列（RocketMQ）事务消息的区别？
> **面试话术**：
> "Seata AT 是**强一致方案**，保证跨服务 ACID。RocketMQ 事务消息是**最终一致方案**。区别在于：Seata 在二阶段前数据对外不可见（全局锁），RocketMQ 的半消息对外也不可见，但消息 commit 后消费者可能消费失败，rollback 成本不同。我们项目的原则是：下单这种需要强一致的用 Seata，支付状态同步这种可以接受最终一致的用 RocketMQ 事务消息。"

### Q6: 项目中有没有因为引入 Seata 踩过坑？
> **面试话术**：
> "有的。几个注意点：① **undo_log 表要提前建好**，否则默认自动建表可能出现编码问题；② **Feign 调用超时时间要设长**，因为全局事务可能执行比较久，默认的 1 秒超时会导致事务回滚；③ **@GlobalTransactional 要加在事务发起方的最外层**，不能嵌套在其他事务注解里；④ **Seata 和 MyBatis-Plus 的分页插件有冲突**（PaginationInterceptor 在多数据源下会失效），需要在 Seata 代理后重新配置。"

---

## 关联知识点

- [[面试分析报告#3.2 订单创建模块（分布式事务）]] — 本项目下单分布式事务详解
- [[面试分析报告#难点二：跨服务分布式事务（下单链路）]] — STAR 法则描述分布式事务
- [[spring八股]] — Spring 全家桶总览
- [[CDC（Canal）详解]] — 另一种数据一致性方案（最终一致）
- [[ES详解]] — 搜索服务数据同步也涉及一致性
- [[Feign详解]] — 跨服务调用的"最后一公里"

---

> **通俗理解**：Seata 就像一个快递中转站。你发三个包裹（三步业务操作），中转站记录每个包裹发出前的样子和发出后的样子（undo log）。如果三个都成功签收，中转站就把记录销毁；如果有一个中途丢了，中转站按照"发之前的样子"重新发一遍（自动回滚）。你（业务代码）只需要把所有包裹交到前台（`@GlobalTransactional`），剩下的事中转站全包了。
