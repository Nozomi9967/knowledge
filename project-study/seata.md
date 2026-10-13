# docker安装



## **1. 创建数据库**

执行mysql.sql脚本



## **2. 创建application.yml**

*/root/seata/conf/application.yml*

*内有nacos用户设置和mysql账号密码设置*

```yaml

server:
  port: 7091

spring:
  application:
    name: seata-server

logging:
  config: classpath:logback-spring.xml
  file:
    path: ${log.home:${user.home}/logs/seata}

console:
  user:
    username: seata
    password: seata

seata:
  config:
    type: nacos
    nacos:
      server-addr: 192.168.172.100:8848   # 你的 Nacos 地址
      namespace: ""                         # public 命名空间留空
      group: SEATA_GROUP
      username: nacos
      password: nacos
      data-id: seataServer.properties      # Nacos 中存放 seata 配置的 dataId

  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 192.168.172.100:8848
      namespace: ""
      group: SEATA_GROUP
      username: nacos
      password: nacos
      cluster: default

  store:
    mode: db                               # 切 Nacos 一般同步换成 db 模式
    db:
      datasource: druid
      db-type: mysql
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://192.168.172.100:3306/seata?useUnicode=true&characterEncoding=utf8&autoReconnect=true&serverTimezone=Asia/Shanghai
      user: root
      password: darling1224
      min-conn: 10
      max-conn: 100
      global-table: global_table
      branch-table: branch_table
      lock-table: lock_table
      distributed-lock-table: distributed_lock
      query-limit: 1000
      max-wait: 5000

  security:
    secretKey: SeataSecretKey0c382ef121d778043159209298fd40739be1054b
    tokenValidityInMilliseconds: 1800000
    ignore:
      urls: /,/**/*.css,/**/*.js,/**/*.html,/**/*.map,/**/*.svg,/**/*.png,/**/*.ico,/console-fe/public/**,/api/v1/auth/login[
```



## 3. 执行

```
docker run --name seata-server \
  --restart always \
  -p 8091:8091 \
  -p 7091:7091 \
  -e SEATA_IP=192.168.172.100 \
  -e JAVA_OPTS="-Xmx512m -Xms512m -XX:MaxDirectMemorySize=256m" \
  -v /root/seata/conf/application.yml:/seata-server/resources/application.yml \
  -v /root/seata/logs:/root/logs \
  -d seataio/seata-server:2.0.0
```

- /root/seata/conf/application.yml

- /root/seata/logs

把宿主机上的单个文件挂载到容器内指定路径，覆盖容器内原来的文件。





# SpringBoot配置



```yaml
seata:
  tx-service-group: tx_shop
  registry:
    type: nacos
    nacos:
      server-addr: 192.168.172.100:8848
      namespace: ""
      group: SEATA_GROUP
      application: seata-server
      cluster: default
      username: nacos
      password: nacos
  service:
    vgroup-mapping:
      tx_shop: default
  data-source-proxy-mode: AT
```



**`tx-service-group: tx_shop`**：定义当前微服务所属的**事务服务组**名称，这里叫 `tx_shop`（通常对应你的业务系统，比如商城、订单等）。微服务在启动时，会拿着这个组名去后面的映射表里找真正的 Seata 服务集群。

**`type: nacos`**：指定使用 **Nacos** 作为服务注册与发现中心。Seata 服务端会把自己注册到 Nacos 中，微服务通过 Nacos 找到它的 IP 和端口。

**`group`**：Seata 服务端在 Nacos 中注册时所属的**分组名称**。默认是 `SEATA_GROUP`

**`cluster`**：指定去 Nacos 的哪一个**集群**中寻找 Seata 服务。这里是默认的 `default` 集群。

**`vgroup-mapping`（虚拟组映射）**： 它的格式是 `虚拟组名: 真实的Seata集群名`。 还记得第一行定义的 `tx-service-group: tx_shop` 吗？这一步就是把虚拟的 `tx_shop` 映射到了名为 `default` 的真实 Seata 集群上。

> **为什么不直接写死集群名？** 为了环境隔离和动态切换。比如你在开发环境，把 `tx_shop` 映射到 `dev_cluster`；到了生产环境，代码不用变，只需要把配置改成映射到 `prod_cluster`，就能无缝切换。

**`data-source-proxy-mode: AT`**：指定 Seata 的分布式事务模式为 **AT 模式（Automatic Transaction）**。

- **AT 模式**是 Seata 最常用、最经典的无侵入模式。
- **工作原理**：Seata 会自动代理你的数据库连接池。在第一阶段提交本地事务时，它会自动解析 SQL，把更新前和更新后的数据做成“快照”（Undo Log）存到数据库的 `undo_log` 表里。如果后续其他微服务报错需要回滚，Seata 就会利用这些快照自动把数据还原，不需要你手动写回滚逻辑。

