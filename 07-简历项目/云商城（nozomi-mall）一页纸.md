---
tags:
  - 简历项目
  - 微服务
  - nozomi-mall
created: 2026-09-03
updated: 2026-10-10
source: C:\Users\q1948\Desktop\project\nozomi-mall（代码全量扫描，非笔记推测）
---

# 云商城（nozomi-mall）— 简历一页纸

> **定位**：个人独立项目 · 投递方向 Java 后端（通用）
> **本页纪律**：架构与难点全部来自代码扫描，每条都能翻到文件。**成果栏的数字一律留空**，压测跑完再填 —— 方案见 [[云商城压测方案（成果数字怎么跑出来）]]。
> **上桌前必读**：[[云商城已知硬伤与追问预案]]（12 处代码级硬伤，面试官一挖就到，先自己知道）

---

## 一、电梯陈述（30 秒）

基于 **JDK 17 + Spring Boot 3.2.5 + Spring Cloud Alibaba 2023.0.1.0** 从 0 搭的微服务电商系统，14 个可独立部署的服务，覆盖商品、搜索、购物车、订单、支付、秒杀全链路。技术上主要打通了三条链路：

1. **Canal 订阅 binlog** → 一次变更驱动两个下游：**同步 ES 索引**（保证搜索结果一致）+ **触发静态页重建**（保证详情页不陈旧）
2. **Seata AT 模式**兜住跨 3 个服务的下单事务（购物车 → 扣库存 → 落单 → 清车）
3. **访问日志 OLAP 聚合发现热点 → 商品隔离 → 网关去重准入 → MQ 异步下单**的秒杀削峰链路

---

## 二、背景

个人独立项目，目标是把分布式组件在一个真实业务闭环里跑通，而不是停在 demo。技术选型、模块拆分、数据流设计全部自己拍板。选电商是因为它天然带三种难点：**读多写少的商品链路**（缓存/索引一致性）、**强一致的交易链路**（分布式事务）、**瞬时高并发的秒杀链路**（超卖/削峰/幂等）。

**规模事实**（以下数字均由代码统计得出，可以直接写简历）：

| 维度 | 事实 |
|------|------|
| 可独立启动服务 | **14 个**（11 业务服务 + 1 网关 + 2 Web 端） |
| Maven 模块 | mall-api 10 + mall-service 11 + mall-web 2 + mall-util 2 + mall-gateway 1 |
| 数据存储 | 5 个 MySQL 库 / **17 张业务表**（+ Seata 4 张事务表）、MongoDB、Redis、ES、Apache Druid |
| HTTP 接口 | **22 个 Controller / 48 个接口** |
| 服务间调用 | **10 个 @FeignClient / 19 个方法** |
| 代码量 | **181 个 Java 文件 / 7,358 行** |
| 种子数据 | 约 1.75 万条 INSERT（其中秒杀商品 12,605 条） |

---

## 三、架构

### 请求主链路

```
                        客户端 / 浏览器
                              │
                  ┌───────────▼────────────┐
                  │  mall-api-gateway:9001 │  GlobalFilter(order=10001)
                  │  ① 布隆过滤器判 URI 合法（不中直接 404）
                  │  ② JWT 解析 + IP 绑定校验（token 内 ip 比对 MD5(客户端IP)）
                  │  ③ RBAC：Redis Hash 按 url+method+service 精确匹配
                  │  ④ 命中 /seckill/order → 进秒杀去重准入
                  └───────────┬────────────┘
                              │  Nacos 服务发现 + Feign 声明式调用
   ┌──────────┬───────────┬───┴───────┬──────────┬───────────┐
   ▼          ▼           ▼           ▼          ▼           ▼
 goods     search       cart       order       pay       seckill
 :8081     :8084       :8087      :8089      :8090       :8092
   │          │           │           │          │           │
 MySQL       ES        MongoDB     MySQL      MySQL       MySQL
shop_goods shop-search  shop      shop_order  shop_pay  shop_seckill
   │                                 │                     ▲
   │ binlog                          │ @GlobalTransactional │ RocketMQ
   ▼                                 ▼   (Seata AT)         │ order-queue
 Canal 1.1.7 ──► canal-service:8083 ─┴─► Feign 写 ES / 触发静态页重建
                                                            │
 访问日志 msitemlogs ──► Apache Druid(OLAP) ──► dw-service:8093
                              ▲                      │
                              └── ElasticJob 每 30s ──┘ 发现热点 → 隔离
```

### 服务清单

| 服务 | 端口 | 存储 | 职责 |
|------|:----:|------|------|
| mall-api-gateway | 9001 | Redis 集群 | 统一入口、鉴权、Sentinel 限流、秒杀去重准入 |
| mall-goods-service | 8081 | MySQL shop_goods | 品牌/分类/SPU/SKU、库存扣减 |
| mall-search-service | 8084 | ES | 关键词检索、条件筛选、聚合、高亮 |
| mall-cart-service | 8087 | MongoDB | 购物车（文档模型天然适配 SKU 快照） |
| mall-order-service | 8089 | MySQL shop_order | 下单主流程（Seata TM） |
| mall-pay-service | 8090 | MySQL shop_pay | 支付宝支付 + RocketMQ 事务消息 |
| mall-seckill-service | 8092 | MySQL + Redis | 秒杀下单、热点商品隔离、ElasticJob |
| mall-canal-service | 8083 | — | binlog 订阅、ES/静态页增量同步 |
| mall-dw-service | 8093 | Apache Druid | 访问日志 OLAP 聚合、热点 URI 发现 |
| mall-permission-service | 8094 | MySQL + Redis 集群 | RBAC 权限、布隆过滤器初始化 |
| mall-file-service | 8080 | FastDFS | 图片上传/下载/删除 |
| mall-page-web | 8086 | — | Thymeleaf 详情页/秒杀页静态化落盘 |
| mall-search-web | 8085 | — | 搜索页渲染 |

### 技术栈（版本号均取自 pom.xml，可被追问）

| 类别 | 组件 | 版本 | 项目里干什么 |
|------|------|------|------|
| 基础 | JDK / Spring Boot | 17 / 3.2.5 | — |
| 微服务 | Spring Cloud / Alibaba | 2023.0.1 / 2023.0.1.0 | BOM 统一版本 |
| 注册配置 | [[Nacos详解\|Nacos]] | client 2.4.0 | 服务发现 + bootstrap 配置中心 |
| 网关 | Spring Cloud Gateway | SC BOM | 统一入口 + GlobalFilter 鉴权 |
| 限流 | [[Gateway限流\|Sentinel]] | sentinel-gateway | 网关层路由/API 分组限流，429 兜底 |
| RPC | [[Feign详解\|OpenFeign]] | SC BOM | 10 个声明式客户端 |
| 分布式事务 | [[Seata详解\|Seata]] | server 2.0.0 / AT | tx_shop 事务组 |
| 消息 | [[RocketMQ详解\|RocketMQ]] | starter 2.3.0 | 秒杀削峰 + [[RocketMQ事务消息详解\|事务消息]] |
| 缓存/锁 | Redis + [[Redisson详解\|Redisson]] | 3.27.2 | 注解缓存 TTL 12h、分布式锁、布隆过滤器 |
| 搜索 | [[ES详解\|Elasticsearch]] | 3.2.5 starter | shop-search / shop-seckill-search 双索引，IK 分词 |
| CDC | [[CDC（Canal）详解\|Canal]] | 1.1.7 | 订阅 4 张表 binlog |
| OLAP | [[OLAP（Druid）详解\|Apache Druid]] | avatica 1.27.0 | 访问日志聚合（注意：**不是**阿里连接池） |
| 定时任务 | ElasticJob-Lite | 3.0.4 + ZK 3.8.0 | 每 30s 扫热点商品 |
| 持久层 | [[MyBatis-Plus详解\|MyBatis-Plus]] | 3.5.7 | 注解式 CRUD，仅 1 个 XML |
| 文件 | [[fastDFS\|FastDFS]] | client 1.27.0.0 | 商品图片存储 |
| 鉴权 | jjwt | 0.12.6 HS256 | 网关统一校验 |
| 文档 | SpringDoc OpenAPI | 2.5.0 | 运行时 /swagger-ui.html |

---

## 四、技术难点（按含金量排序，每条都有代码位置）

### 难点 1｜热点商品发现 → 隔离 → 去重准入 → 异步下单（这条最独一份，优先讲）

**问题**：秒杀流量不是均匀的，往往集中在极少数商品上。如果不区分冷热，热点商品会把整条链路（DB 连接、库存行锁）打穿，而冷商品被无辜连坐。

**方案**（6 步闭环，全链路自建）：

1. 接入层访问日志采集进 Apache Druid，事实表 `msitemlogs`（采集链路推测为 OpenResty 打点 → Kafka topic `mslogs` → Druid 摄入，依据是你 [[开发学习日志]] 里建过 `mslogs` topic、且 Druid 表名与之同源 —— **这段不在 Java 仓库，讲之前先自己确认一遍实际接法**）
2. `mall-dw-service` 用 Avatica JDBC 查 Druid，SQL 是 `group by uri having viewCount &gt; #{threshold}` 的时间窗口聚合，并用 `not in` 排除已隔离的 URI
3. `mall-seckill-service` 的 ElasticJob（cron `0/30 * * * * ?`）每 30 秒拉一次热点 URI 清单
4. 拿到热点后做**隔离**：`UpdateWrapper.eq("id",x).eq("is_lock",0).gt("store_count",0).set("is_lock",1)` —— 单条 CAS 式 UPDATE 靠影响行数判成功，避免并发重复隔离
5. 隔离后的商品库存搬进 Redis Hash `HotSeckillGoods`，请求在网关侧走去重准入：`OrderQueue:{username}:{id}` INCR + 2 分钟 TTL，返回值 &gt; 1 即判定重复请求
6. 通过准入的请求扔进 RocketMQ topic `order-queue`，秒杀服务 `@RocketMQMessageListener` 异步消费落单，前端轮询结果

**代码位置**：`mall-dw-service/.../mapper/HotGoodsMapper.xml`、`mall-seckill-service/.../task/DiscoverHotGoods.java`、`mall-gateway/.../api/hot/HotQueue.java`、`mall-seckill-service/.../mq/OrderQueueListener.java`

**为什么这么设计**：热点识别放在**离线 OLAP** 而不是在线统计，是因为在线埋点做 topN 本身就要抢锁、写 Redis，等于把热点问题又搬了一层；Druid 天生擅长时间窗口聚合，30 秒的发现延迟对秒杀活动可接受。

### 难点 2｜Canal 订阅 binlog，一次变更同时驱动「ES 索引」和「静态详情页」

**问题**：商品改一次价，要让搜索结果、详情页两处同时变。业务代码里手动双写会侵入下单/改价主流程，还会漏掉后台直接改库的场景。

**方案**：用 Canal 伪装 MySQL 从库读 binlog，把"谁变了"这件事从业务代码里彻底剥出来。

- 原生 `CanalConnector`，`@PostConstruct` 起独立线程常驻，`getWithoutAck(100)` 批量拉 + 逐批 ack，单条异常 catch 掉不中断主循环（保证一条脏数据不会让整个同步停摆）
- 订阅 4 张表：`shop_goods.sku`、`shop_goods.ad_items`、`shop_seckill.seckill_goods`、`shop_seckill.seckill_activity`，各自一个 handler
- `SkuHandler` 把 binlog 行转成 `SkuEs`，Feign 调 search 服务 `add/del` 写 ES；同时 Feign 调 page 服务 `html(spuId)` 触发 Thymeleaf 静态页重建
- **关键细节**：下游 Feign 调用放在 `new Thread` 里异步执行 —— 因为 ES 写入和模板渲染都是重 IO，同步执行会拖慢 Canal 主循环、造成 binlog 位点堆积

**代码位置**：`mall-canal-service/.../CanalNativeClient.java`（312 行，全仓最大文件）、`canal/listener/SkuHandler.java:36`

**追问预案**：为什么不用延迟双删？—— 延迟双删解决的是"缓存旧值"，而这里要解决的是"下游派生数据（索引 + 静态页）重建"，binlog 订阅拿到的是**行级 after 值**，可以直接重建而不是删了等回源。取舍是 Canal 引入了组件复杂度，且当前是 Canal 直连 Feign，缺一层 MQ 缓冲（见硬伤页）。

### 难点 3｜Seata AT 兜住跨 3 个服务的下单事务

**问题**：一次下单跨了 cart（查购物车 / 清车）、goods（扣库存）、order（落 order + order_sku）三个服务、两个库。任何一步失败，已扣的库存和已清的购物车都必须回滚。

**方案**：`mall-order-service` 的 `OrderServiceImpl` 上标 `@GlobalTransactional` 作 TM，goods 服务作分支 RM；`tx-service-group: tx_shop`，Seata Server 2.0.0 注册在 Nacos SEATA_GROUP，`data-source-proxy-mode: AT`。事务内 5 步：`cartFeign.list` → `skuFeign.decount` → 插 `order_sku` → 插 `order` → `cartFeign.delete`。

**防超卖是双层的**：Seata 保证的是原子性，不是防超卖。真正防超卖靠语句级 CAS —— `UPDATE sku SET num = num - #{num} WHERE id = #{id} AND num &gt;= #{num}`，Service 层判影响行数 `count &lt;= 0` 抛"库存不足"。这样即使并发进来，靠 InnoDB 行锁 + 条件更新也不会扣成负数。

**代码位置**：`mall-order-service/.../impl/OrderServiceImpl.java:43`、`mall-goods-service/.../mapper/SkuMapper.java:13`

**为什么选 AT 不选 TCC**：AT 无侵入，业务代码不用写 try/confirm/cancel 三份；代价是要靠 undo_log 做逆向补偿，且全局锁会降低并发。秒杀链路刻意**没有**用 Seata —— 那条路走的是 Redis 扣减 + MQ 异步，用最终一致换吞吐。

### 难点 4｜网关统一鉴权：布隆过滤器 + JWT + IP 绑定 + RBAC 四层

**问题**：14 个服务如果各写一遍鉴权，一是重复，二是漏一个就是后门。

**方案**：全部收敛到网关 `ApiFilter`（`GlobalFilter`，order = 10001），四层依次过：

1. **布隆过滤器判 URI 合法性** —— `tryInit(1000000L, 0.0001)`（100 万容量 / 万分之一误判率），不命中直接 404。挡的是扫描器随机打路径，避免无效请求穿到后端服务
2. **JWT 校验** —— jjwt 0.12.6 + HS256，`Authorization: Bearer` 解析
3. **IP 绑定** —— 发 token 时把 `MD5(客户端IP)` 塞进 payload，校验时重新算一次比对，token 被抓走换机器也用不了
4. **RBAC** —— Redis Hash `RolePermissionMap` / `RolePermissionAll`，按 `url + method + serviceName` 精确匹配

**代码位置**：`mall-gateway/.../filter/ApiFilter.java`、`mall-util/mall-common/.../util/JwtUtils.java`、`mall-permission-service/.../init/InitPermission.java:54`

### 难点 5｜Nginx + OpenResty 三级缓存（代码在服务器上，不在 Java 仓库）

**问题**：推广商品位是典型读多写少。即使 goods-service 有 Redis 注解缓存，请求仍要走完「网关 → Feign → Tomcat → Spring MVC → Service」整条 Java 链路才拿到一个几乎不变的值。

**方案**：把缓存前移到接入层，形成三级，逐级回源：

1. **OpenResty + Lua 直查 Redis**：`access_by_lua_file` 阶段用 `resty.redis` 连 Redis 取 `ad_items_skus::{id}`，命中直接 `ngx.say` + `return ngx.exit(200)` —— 请求**根本不进 Java 服务**
2. **Nginx 磁盘缓存**：未命中则落到 `proxy_cache`，`proxy_cache_valid 200 304 60s`、`proxy_cache_key $host$uri$is_args$args`；另配 `/purge` location 用 `proxy_cache_purge` 主动失效
3. **回源 Java**：两级都未命中才 `proxy_pass` 到 goods-service:8081，Spring 的 `@Cacheable` 顺手把 Redis 缓存重建回去

**两个能证明真踩过的细节**：

- 为什么用 `access_by_lua` 而不是 `rewrite_by_lua`：**access 阶段执行完会自动继续走 proxy_pass，rewrite 阶段不会**，用错了缓存未命中时请求就断在那儿
- Lua 里命中缓存后必须 `return ngx.exit(200)`：不 return 的话 Nginx 会继续走后面的阶段，等于白查一遍

> [!warning] 这部分代码不在 Java 仓库里
> Lua 脚本和 nginx.conf 在虚拟机的 `/usr/local/openresty/nginx/lua/aditem.lua`，Java 代码里搜 OpenResty/Lua 是 0 命中（仓库里只有 `doc/第3章` 的教程正文）。**面试对方要看代码时，记得把 nginx.conf 和 lua 脚本一起备好**，否则会显得这段是抄来的话术。你的 [[开发学习日志]] 里存着完整脚本。

### 备用难点（问到再展开，不主动铺开）


- **ES 搜索实现**：新版 `co.elastic.clients` + `NativeQuery`，`must(match name)` 负责相关性打分、`filter(term categoryName/brandName)` 走过滤上下文不打分可缓存；遍历 `attr_` 前缀参数动态拼 `attrMap.xxx.keyword` 实现任意规格筛选；三个 terms 聚合（categoryList / brandList / attrmaps）回填筛选面板；`HighlightQuery` 对 name 高亮。见 [[ES must与filter详解]]
- **RocketMQ 事务消息**：支付回调用 `sendMessageInTransaction("log", ...)` + `RocketMQLocalTransactionListener`，`executeLocalTransaction` 写支付日志、异常返回 ROLLBACK。见 [[RocketMQ事务消息详解]]
- **秒杀库存扣减**：Redis Hash `HotSeckillGoods` 存库存，Redisson 锁串行化，扣到 0 时删 key 并回写 MySQL `store_count = 0`

---

## 五、成果 ⚠️ 数字未压测，暂不可写简历

代码扫描结论：**仓库内无任何压测产物**（无 .jmx / .jtl / wrk / ab 脚本，无耗时埋点，无 README 数据）。`doc/面试分析报告.md` 里那张"可量化的成果"表，表头自己写的就是**"估算/设计值"**，例如"Nginx 层 10,000+ QPS""搜索响应 &lt;50ms""秒杀 QPS 数千级" —— 这些是推演不是实测，写进简历一旦被问"什么环境、多少并发、P99 还是均值"就会当场崩。

要填的四个数字，怎么测见 [[云商城压测方案（成果数字怎么跑出来）]]：

| 待填成果 | 测什么 | 优化前 | 优化后 |
|------|------|:---:|:---:|
| 商品详情接口 | 加 Redis 注解缓存前后的 P99 与 QPS | ⚠️ 待填 | ⚠️ 待填 |
| 搜索接口 | ES 检索 P99（当前索引数据量下） | ⚠️ 待填 | ⚠️ 待填 |
| 秒杀下单 | 同步下单 vs 网关去重准入+MQ 异步的吞吐与超卖数 | ⚠️ 待填 | ⚠️ 待填 |
| 数据同步 | 改一条 SKU → ES 可见 / 静态页更新的延迟 | ⚠️ 待填 | ⚠️ 待填 |

**在跑出数字之前，成果栏这样写**（描述工程事实而非编造性能）：

- 独立完成 14 个服务的拆分与落地，17 张业务表、48 个接口、10 个 Feign 客户端
- 打通 binlog → ES → 静态页的增量同步链路，商品变更无需业务代码双写
- 秒杀链路从「同步扣库存」重构为「热点隔离 + 网关去重准入 + MQ 异步落单」，DB 只承接最终落单流量

---

## 六、简历 bullet（压测数字填好后直接粘）

**三行版**：

> **云商城 nozomi-mall（个人项目）**｜Spring Boot 3.2.5 · Spring Cloud Alibaba · MySQL/Redis/ES/MongoDB/RocketMQ
> · 独立设计并实现 14 个微服务（商品/搜索/购物车/订单/支付/秒杀），17 张业务表、48 个 HTTP 接口，服务间以 Nacos + OpenFeign 通信，网关统一鉴权与限流。
> · 用 Canal 订阅 MySQL binlog 驱动 ES 索引与 Thymeleaf 静态详情页增量重建，把双写从业务代码剥离；下游调用异步化避免阻塞 binlog 消费，同步延迟 ⚠️ 待填 ms。
> · 秒杀链路自建「Druid OLAP 发现热点 → CAS 隔离商品 → 网关 INCR 去重准入 → RocketMQ 异步落单」削峰闭环，压测下单吞吐由 ⚠️ 待填 提升至 ⚠️ 待填，零超卖。
> · 跨 cart/goods/order 三服务下单用 Seata AT 保证原子性，库存扣减以 `num &gt;= 扣减量` 条件更新兜底防超卖。

---

## 关联知识点

- 组件详解：[[Seata详解]] · [[CDC（Canal）详解]] · [[RocketMQ详解]] · [[RocketMQ事务消息详解]] · [[Redisson详解]] · [[ES详解]] · [[Nacos详解]] · [[Feign详解]] · [[Gateway限流]] · [[OLAP（Druid）详解]] · [[MyBatis-Plus详解]] · [[FastDFS]]
- 八股对照：[[数据库和缓存如何保证一致性？]] · [[什么是缓存雪崩、击穿、穿透？]] · [[多节点争抢资源， Redis 分布式锁是怎么实现的？]] · [[ES must与filter详解]] · [[CAP定理详解]]
- 本项目其他页：[[云商城已知硬伤与追问预案]] · [[云商城压测方案（成果数字怎么跑出来）]]
- 项目原始资料：[[面试分析报告]]（更早的自写分析，⚠ 其技术栈表首行 Spring Boot 版本号有误，已在该页加 warning 标注）· [[开发学习日志]]（OpenResty/Lua/Kafka/Druid 等服务器侧的真实操作记录）· [[Spring Boot 与 Spring Cloud Alibaba 的关系]]





