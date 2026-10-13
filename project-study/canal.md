---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---
# canal



## 启动命令

```shell
docker run -d \
  --restart always \
  -p 11111:11111 \
  -p 9099:9099 \
  --name canal \
  --privileged \
  -v canal-conf:/home/admin/canal-server/conf \
  docker.io/canal/canal-server:v1.1.7
  
# 具名卷启动：-v canal-conf:/home/admin/canal-server/conf 
```



## **找宿主机的具名卷实际路径**

```shell
docker volume inspect canal-conf
```



## mysql配置工作

为你的 MySQL 数据库**创建一个允许远程连接的专属账号**。

```mysql
CREATE USER 'canal'@'%' IDENTIFIED BY 'canal';
```

创建一个用户：`canal`

密码：`canal`

`'%'` 表示：**任何 IP 都可以连**





```mysql
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
```

ON 数据库.表

| 权限               | 干嘛用                    |
| ------------------ | ------------------------- |
| SELECT             | 读取数据（有些场景需要）  |
| REPLICATION SLAVE  | ✅ **读取 binlog（核心）** |
| REPLICATION CLIENT | 查看主库状态              |





```mysql
FLUSH PRIVILEGES;
```

刷新权限







destination是啥

```
canal:
  server: 192.168.172.100:11111
  destination: example
```

在 Alibaba Canal（一个用来同步数据库增量数据的开源组件）的配置中，**`destination` 指的是一个具体的“数据同步实例”（Instance）的名称**。

你可以把它理解为 Canal Server 内部的一个**独立工作队列**或者**数据通道**。

### 它有什么用？

1. **一对一绑定数据库：** Canal Server（服务端）可以同时监听很多个不同的数据库。为了把它们区分开，每一个被监听的数据库集群都会在 Server 端对应一个 Instance。这个 `destination` 就是那个 Instance 的名字。
2. **客户端路由：** 你的客户端程序（比如 Java 代码）连接到 Canal Server (`192.168.172.100:11111`) 时，必须告诉 Server：“我想订阅哪个数据库的数据变化？”。通过指定 `destination: example`，Server 就知道该把名为 `example` 的那个实例里的 Binlog 数据发给你。

### 一个直观的比喻

- **`server (192.168.172.100:11111)`** = 电视总台。
- **`destination (example)`** = 某个具体的**电视频道**（比如“电影频道”）。
- **你的客户端** = 电视机。你不仅要连上信号（Server），还要调到指定的频道（Destination），才能看到你想看的内容。

> 💡 **提示：** `example` 是 Canal 官方默认自带的一个示例实例名称。如果你去 Canal Server 的安装目录下的 `conf/` 文件夹看，你会发现里面有一个叫 `example` 的文件夹，里面就放着这个实例的具体数据库连接配置（如 MySQL 的账号、密码、主库地址等）。如果你以后要监听新的数据库，通常会在 Server 端新建一个文件夹（比如 `conf/order_db/`），到时候你客户端的 `destination` 就要改成 `order_db`。