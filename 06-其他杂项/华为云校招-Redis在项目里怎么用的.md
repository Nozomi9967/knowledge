---
tags:
  - 面试
  - Redis
  - Redisson
  - 华为云
  - 项目话术
created: 2026-09-04
updated: 2026-09-04
source: nozomi-mall 代码扫描 + 06-其他杂项/Redisson详解.md + 07-简历项目/云商城（nozomi-mall）一页纸.md
---

# 华为云校招 —「Redis 在项目里怎么用的」追问链第一环

> 华为面经出镜率最高的 Redis 追问链：**Redis 在项目里怎么用的 → 分布式锁解决什么问题 → 怎么实现 → 锁过期了业务没执行完怎么办**。本页是第一环的标准口述答案，后三环见 [[华为云校招-Redisson高频三题]]。
> 回答策略：**按请求链路讲，不背清单**——每个用法 = 场景 + 数据结构 + key 设计 + 一个数字，并主动埋钩子把追问引向自己准备最好的区域。

---

## 参考答案（口述版 · 约 90 秒）

> "Redis 在我项目里不是单一角色，我按一条请求进来经过的顺序讲，大概四类用法。
>
> **第一类是网关接入层，两个用途。** 一是 **RBloomFilter 布隆过滤器**做 URI 合法性校验——权限服务启动时把所有合法接口路径灌进过滤器，容量 100 万、误判率万分之一；网关收到请求先查一遍，不命中直接 404。它挡的是扫描器随机打路径这种根本不存在的请求，避免无效流量穿透到后端。二是 **RBAC 权限缓存**——权限数据用 Redis Hash 存成两个结构，鉴权时按 url + method + serviceName 精确匹配，请求级鉴权完全不碰 MySQL。
>
> **第二类是商品读链路的缓存。** goods 服务详情接口上 @Cacheable，RedisCacheManager 统一 TTL 12 小时，更新时 @CacheEvict 主动失效；这层之上还有 OpenResty 的 Lua 直查 Redis，所以 Redis 在三级缓存里其实是中间那一层。
>
> **第三类是秒杀链路，这是用得最重的。** 热点商品被隔离后，库存预加载进 Redis Hash `HotSeckillGoods`，field 是商品 ID、value 是库存数。请求先在网关做**去重准入**——用 `OrderQueue:{用户名}:{商品ID}` 做 INCR 加 2 分钟过期，返回值大于 1 说明这人已经在队列里，直接拒绝，这是防重复下单。排上队的进 RocketMQ，消费端在秒杀服务里用 **Redisson 分布式锁**保证扣库存互斥，查库存、扣库存都是对这个 Hash 操作，扣到 0 就删 key 并把 MySQL 库存同步成 0。
>
> **第四类是 Redisson 这个客户端本身。** 我选它不只是为了锁——分布式锁、布隆过滤器是一套客户端同时解决的，锁部分还带了看门狗自动续期。
>
> 分布式锁和扣库存这两块其实是我在项目里踩坑最多的地方，您感兴趣的话我可以展开讲。"

## 30 秒精简版

> "四类：网关层布隆过滤器挡无效流量 + RBAC 权限缓存；商品读链路 12 小时 TTL 注解缓存；秒杀链路最重——热点库存放 Redis Hash、网关 INCR 去重准入、消费端 Redisson 锁互斥扣减；客户端统一用 Redisson，锁和布隆过滤器一套解决。"

---

## 场景总表（全部来自代码扫描，可被翻代码验证）

| 场景 | 数据结构 / API | key 设计 | 关键参数 | 代码位置 |
|------|---------------|----------|---------|---------|
| 网关 URI 校验（防扫描/穿透） | `RBloomFilter` | `UriBloomFilterArray` | **容量 100 万，误判率 0.0001（万分之一）** | `InitPermission.java:54`、`AuthorizationInterceptor.java:40-45` |
| 网关 RBAC 鉴权缓存 | Redis **Hash** | `RolePermissionMap` / `RolePermissionAll` | url+method+service 精确匹配 | `AuthorizationInterceptor.java:72,122` |
| 商品详情缓存 | Spring Cache `@Cacheable` / `@CacheEvict` | 注解 key=`#id` | **TTL 12 小时**（RedisCacheManager entryTtl） | `SkuServiceImpl.java:55,98`、`RedisConfig.java:47` |
| 秒杀热点库存 | Redis **Hash**（`HGET` 查 / `HINCRBY -num` 扣） | `HotSeckillGoods`，field=商品ID | 扣到 0 删 key + 回写 MySQL `store_count=0` | `SeckillOrderServiceImpl.java:55,73-77`、预热 `SeckillGoodsServiceImpl.java:41` |
| 网关去重准入（幂等） | **INCR + TTL** | `OrderQueue:{username}:{goodsId}` | **TTL 2 分钟**，返回值 >1 判重复请求 | `HotQueue.java:44-47` |
| 秒杀扣减互斥 | Redisson `RLock`（无参 `lock()` → 看门狗启动） | ⚠️ 固定字符串 `seckill-order-lock`（全局单锁，见硬伤） | Redisson **3.27.2** | `SeckillOrderServiceImpl.java:43-44`、`pom.xml:165` |

---

## 钩子策略：回答就是给面试官出的菜单

| 钩子 | 口述里埋的话 | 面试官会问 | 接招出处 |
|------|------------|-----------|---------|
| ① 布隆过滤器 | "误判率万分之一" | 为什么能防穿透？误判了怎么办？ | 不存在即一定不存在；误判放行后还有 JWT+RBAC 兜底；[[什么是缓存雪崩、击穿、穿透？]] |
| ② 扣库存 | "查库存、扣库存"——**只描述不辩护** | 并发下不会扣超吗？ | 主动承认先查后扣有竞态窗口 → Lua 合并判断+扣减 / DECR 返回值判断，"发现锁可以消掉"是迭代思考的加分展示 |
| ③ 分布式锁 | 最后一句"踩坑最多，可以展开讲" | 看门狗？锁过期了？ | [[华为云校招-Redisson高频三题]] T1/T2/T3（强区，Q39 拿过 8.5） |

钩子②的用法与 T3 坦白话术同源：**真实代码先查后扣非原子**（`:55` GET 与 `:73` HINCRBY 之间有竞态窗口，见 [[云商城已知硬伤与追问预案]] 第 2 条），与其被动被拆穿，不如留在明面上等他问。

---

> [!warning] 三处笔记与代码矛盾（2026-09-04 代码扫描确认，面试一律以代码为准）
>
> **① 布隆过滤器参数：** [[Redisson详解]] 布隆过滤器一节写的 `tryInit(10000L, 0.001)`（1 万容量 / 0.1%误判）是旧估算（出自 doc/面试分析报告.md），**真实代码是 `tryInit(1000000L, 0.0001)`——容量 100 万、误判率万分之一**。口述统一说后者。
>
> **② Redisson 版本：** [[Redisson详解]]「常见 Bug #5」写"本项目使用 3.8.2"已过时，`pom.xml:165` 实为 **redisson-spring-boot-starter 3.27.2**。报旧版本号反而引火——3.8.2 时代看门狗有续期任务泄漏的已知 bug，3.16+ 才修。
>
> **③ 集群范围只对一半：** 6 节点 Cluster（`192.168.172.100:7001-7006`）只是 **permission-service** 的 Redisson 配置；**网关**的 `RedissLock.java` 集群段整个被注释，实际 `useSingleServer()` 连单机 `6379`。被问"哪些服务连集群"别说全项目。**连带自查项：布隆过滤器由 permission（集群 client）初始化、网关用单机 client 读同名 key——若两套 Redis 不同实例，网关读到的过滤器恒为空、全 404。面试前必须本地验证 contains 生效。**

---

## 关联知识点

- [[华为云校招-Redisson高频三题]] — **追问链后三环**：锁原理+看门狗（T1）→ 锁过期事故链（T2）→ 锁粒度坦白话术（T3）
- [[华为云校招-最容易考三题（计网·并发·索引）]] — 同系列跨领域版，补 Redis 之外的基础三件套
- [[华为云校招-Sentinel高频三题]] — 同系列流量治理版（网关限流原理与落地）
- [[华为云校招-Nacos高频三题]] — 同系列后一页（注册中心 CAP 选型、心跳数字、配置长轮询）
- [[Redisson详解]] — 布隆过滤器、看门狗、Hash 可重入锁的原理细节
- [[云商城（nozomi-mall）一页纸]] — 项目整体叙述框架（四层鉴权、三级缓存、秒杀 6 步闭环）
- [[云商城已知硬伤与追问预案]] — 先查后扣非原子、全局单锁等代码级硬伤清单
- [[什么是缓存雪崩、击穿、穿透？]] — 钩子①的八股后盾
- [[06-其他杂项/CDC（Canal）详解]] — 布隆过滤器动态更新的上游链路（binlog → Feign → 异步加载）
