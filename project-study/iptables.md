## 什么是 iptables？

iptables 是 Linux 内核中的**防火墙/网络地址转换工具**，它利用内核的 netfilter 框架对进出本机的网络数据包进行**过滤、修改、重定向**等操作。

可以把它理解为 Linux 系统的“软件路由器/防火墙”，所有经过本机的网络包都必须通过 iptables 设置的规则检查。

---

## 一、核心概念

### 1. 表（Tables）
iptables 有多个表，每个表负责不同功能：

| 表名       | 功能                      | 常用场景           |
| ---------- | ------------------------- | ------------------ |
| **filter** | 包过滤（允许/拒绝）       | 防火墙规则         |
| **nat**    | 网络地址转换（DNAT/SNAT） | 端口映射、共享上网 |
| **mangle** | 修改包头部                | QoS、特殊路由      |
| **raw**    | 跳过连接跟踪              | 高性能场景         |

### 2. 链（Chains）
每个表内置若干条链，链是数据包经过的**检查点**：

| 链名        | 触发时机               | 方向       |
| ----------- | ---------------------- | ---------- |
| PREROUTING  | 数据包刚进入网卡       | 入口预处理 |
| INPUT       | 数据包要发给本机进程   | 本机入口   |
| FORWARD     | 数据包要转发给其他机器 | 转发路径   |
| OUTPUT      | 本机进程发出的数据包   | 本机出口   |
| POSTROUTING | 数据包即将离开网卡     | 出口后处理 |

### 3. 规则与动作
每条链上按顺序排列多条规则，匹配成功后执行指定动作：

| 动作       | 含义                           |
| ---------- | ------------------------------ |
| ACCEPT     | 允许通过                       |
| DROP       | 丢弃（无响应）                 |
| REJECT     | 拒绝（返回错误）               |
| LOG        | 记录日志                       |
| DNAT       | 改目标地址（只在 nat 表）      |
| SNAT       | 改源地址（只在 nat 表）        |
| MASQUERADE | 动态 SNAT（自动用出口网卡 IP） |

---

## 二、数据包流向图

```
                     ┌──────────────────────────────────┐
                     │           网络接口 eth0           │
                     └───────────────┬──────────────────┘
                                     ↓
                            ┌─────────────────┐
                            │   PREROUTING    │ ← raw/mangle/nat
                            └────────┬────────┘
                                     ↓
                            ┌─────────────────┐
                            │    路由决策      │
                            └────────┬────────┘
                                     ↓
               ┌─────────────────────┴─────────────────────┐
               ↓                                           ↓
    ┌─────────────────┐                         ┌─────────────────┐
    │     INPUT       │                         │    FORWARD      │
    │ (发往本机进程)   │                         │ (转发给其他机器) │
    └────────┬────────┘                         └────────┬────────┘
             ↓                                           ↓
    ┌─────────────────┐                         ┌─────────────────┐
    │   本机进程       │                         │   POSTROUTING   │
    └────────┬────────┘                         └────────┬────────┘
             ↓                                           ↓
    ┌─────────────────┐                                  ↓
    │     OUTPUT      │                         ┌─────────────────┐
    │ (本机发出的包)   │                         │   网络接口 eth0  │
    └────────┬────────┘                         └─────────────────┘
             ↓
    ┌─────────────────┐
    │   POSTROUTING   │
    └────────┬────────┘
             ↓
    ┌─────────────────┐
    │   网络接口 eth0  │
    └─────────────────┘
```

---

## 三、基本命令格式

```bash
iptables [-t 表名] 命令 链名 匹配条件 -j 动作
```

### 常用参数

| 参数      | 说明                  |
| --------- | --------------------- |
| `-t`      | 指定表（默认 filter） |
| `-L`      | 列出规则              |
| `-A`      | 追加规则到链尾        |
| `-I`      | 插入规则到链头        |
| `-D`      | 删除规则              |
| `-F`      | 清空链所有规则        |
| `-P`      | 设置链默认策略        |
| `-p`      | 协议（tcp/udp/icmp）  |
| `--dport` | 目标端口              |
| `--sport` | 源端口                |
| `-s`      | 源 IP                 |
| `-d`      | 目标 IP               |
| `-i`      | 入口网卡              |
| `-o`      | 出口网卡              |

---

## 四、常用命令示例

### 1. 查看规则

```bash
# 查看 filter 表（默认）
iptables -L -n -v

# 查看 nat 表
iptables -t nat -L -n -v

# 带行号查看
iptables -L -n --line-numbers
```

### 2. 基本过滤规则

```bash
# 允许 SSH 连接
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# 拒绝来自 192.168.1.100 的访问
iptables -A INPUT -s 192.168.1.100 -j DROP

# 允许本机 ping 其他机器
iptables -A OUTPUT -p icmp --icmp-type echo-request -j ACCEPT

# 允许已建立的连接（状态跟踪）
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
```

### 3. NAT 规则（你文档中的）

```bash
# DNAT：本机访问公网 IP 时重定向到内网 IP
iptables -t nat -A OUTPUT -d 101.37.182.129 -j DNAT --to-destination 172.17.153.70

# SNAT：让内网机器上网（源地址伪装）
iptables -t nat -A POSTROUTING -s 192.168.1.0/24 -j MASQUERADE

# 端口转发：公网 8080 → 内网 80
iptables -t nat -A PREROUTING -p tcp --dport 8080 -j DNAT --to-destination 192.168.1.10:80
```

### 4. 删除规则

```bash
# 按编号删除（先用 -L --line-numbers 查看编号）
iptables -D INPUT 3

# 按内容删除
iptables -D INPUT -s 192.168.1.100 -j DROP

# 清空所有规则
iptables -F
```

---

## 五、规则持久化

iptables 规则默认是**临时**的，重启后丢失。

### CentOS/RHEL 7/8/9

```bash
# 安装持久化工具
yum install -y iptables-services

# 保存当前规则到文件
service iptables save
# 或
iptables-save > /etc/sysconfig/iptables

# 设置开机自动恢复
systemctl enable iptables
systemctl start iptables
```

### Ubuntu/Debian

```bash
# 安装
apt install iptables-persistent

# 保存规则
iptables-save > /etc/iptables/rules.v4
ip6tables-save > /etc/iptables/rules.v6
```

---

## 六、调试技巧

### 1. 记录被拒绝的包
```bash
iptables -A INPUT -j LOG --log-prefix "DROP: " --log-level 4
iptables -A INPUT -j DROP
```

### 2. 查看连接跟踪
```bash
# 查看当前 NAT 会话
cat /proc/net/nf_conntrack | grep 101.37.182.129
```

### 3. 流量统计
```bash
# 查看每条规则匹配的包数和字节数
iptables -L -n -v

# 清零计数器
iptables -Z
```

---

## 七、你文档中的规则详解

```bash
iptables -t nat -A OUTPUT -d 101.37.182.129 -j DNAT --to-destination 172.17.153.70
```

| 部分                                     | 含义                                 |
| ---------------------------------------- | ------------------------------------ |
| `-t nat`                                 | 使用 NAT 表（改地址）                |
| `-A OUTPUT`                              | 追加规则到 OUTPUT 链（本机发出的包） |
| `-d 101.37.182.129`                      | 匹配目标 IP 为公网 IP 的包           |
| `-j DNAT --to-destination 172.17.153.70` | 把目标地址改成内网 IP                |

**为什么有效**：ECS 本机进程访问公网 IP 的包，在离开本机前被改成内网 IP，流量走内网直达 Kafka 容器，绕过阿里云网关的 NAT 回环问题。外部客户端不受影响，因为外部来的包走 PREROUTING 链，不经过 OUTPUT 链。

---

## 八、一句话总结

**iptables 是 Linux 内核级的数据包处理工具，通过在不同检查点（链）上设置规则（表），可以对网络流量进行过滤、转发、地址转换等操作。**