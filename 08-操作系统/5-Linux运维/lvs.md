---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---


# 概览

LVS（Linux Virtual Server）的 DR（Direct Routing，直接路由）模式是三种转发模式中性能最高的一种。

## 核心思路

**请求由 LVS 转发，响应由真实服务器直接返回给客户端**，不经过负载均衡器。这样 LVS 只处理进入流量，出口流量完全绕过它，极大减轻了 LVS 的压力。

## 数据包流转过程

![image-20260526145326694](C:\Users\q1948\Desktop\project\project-study\image-20260526145326694.png)

## 核心机制

DR 模式的关键在于**数据包的 MAC 地址改写**，而非 NAT。

**请求阶段**：客户端将请求发往 VIP，LVS Director 收到后，不修改 IP 头，只将数据帧的目标 MAC 地址改写为选中的 RS 的 MAC 地址，然后在同一局域网内转发出去。RS 收到后，看到目标 IP 是 VIP，自身 `lo:0` 上也绑了 VIP，所以接受并处理请求。

**响应阶段**：RS 直接以 `VIP → CIP` 的 IP 包回复给客户端，完全不经过 LVS。这是 DR 模式吞吐量极高的根本原因——LVS 只承担进方向的流量。



> ### VIP
>
> VIP = **Virtual IP**，虚拟 IP 地址。
>
> 它是对外暴露给客户端的那个 IP，比如你的网站域名解析到 `192.168.1.100`，这个就是 VIP。客户端永远只知道这一个地址，不知道背后有几台真实服务器。
>
> "虚拟"的意思是：它不属于任何一台真实的物理机，而是由 LVS Director 统一持有和对外宣告。真实服务器（RS）也绑了这个 IP，但不对外暴露（靠 ARP 抑制隐藏起来）。
>
> ```
> 客户端眼中：                   实际上：
>                                ┌── RS1 (RIP1)
> client → 192.168.1.100(VIP) ──┤── RS2 (RIP2)
>                                └── RS3 (RIP3)
> ```
>
> 对应的概念还有：
>
> - **CIP**：Client IP，客户端真实 IP
> - **RIP**：Real IP，真实服务器的 IP
> - **DIP**：Director IP，LVS 自己的管理 IP



> ### lo:0
>
> `lo` 是 Linux 的 **loopback 接口**，也就是回环网卡，固定 IP 是 `127.0.0.1`，专门用于本机内部通信，从不向外发包。
>
> `lo:0` 是在 `lo` 上创建的一个**虚拟子接口**（alias），相当于给 loopback 再挂一个额外的 IP 地址。命名上 `:0` 只是编号，你也可以叫 `lo:1`、`lo:vip`（用 `ip addr` 命令的话直接加 `label`）。
>
> 把 VIP 绑在 `lo:0` 而不是 `eth0` 的关键原因：
>
> - `lo` 接口天然不参与 ARP，绑在上面的 IP 不会被广播出去
> - 配合内核参数 `arp_ignore=1` + `arp_announce=2`，RS 对外完全"假装不知道" VIP 是谁
> - 但内核处理数据包时，看到 `dst IP = VIP`，发现本机 `lo:0` 上有这个地址，就正常接收处理
>
> 简单说：`lo:0` 绑 VIP 是为了让内核"认识"这个 IP，但又不让外面的人"发现"它。



> ## eth0
>
> ## Claude responded: Linux 网络接口的命名约定。
>
> Linux 网络接口的命名约定。
>
> `eth0` 是 Linux 下第一块**以太网物理网卡**的传统名字，`eth` 是 Ethernet 的缩写，`0` 是编号（第二块叫 `eth1`，以此类推）。它就是那块真实插着网线（或连着虚拟交换机）、会参与 ARP、有真实 MAC 地址的网卡。
>
> 不过现在的 Linux 发行版（CentOS 7+、Ubuntu 16+）基本已经换成了**可预测命名**，比如：
>
> - `ens33`、`ens192` — VMware 虚拟机里常见
> - `enp2s0` — 物理机，`p2` 是 PCI 总线号，`s0` 是插槽号
> - `eno1` — 板载网卡（onboard）
>
> 规则是 `en`（ethernet）+ 位置信息，目的是让网卡名跟硬件位置绑定，重启后不会乱变。`eth0` 这种纯序号命名在多网卡时可能因为探测顺序不同而错位，所以被替代了。
>
> 在 LVS DR 的语境里，说 `eth0` 就是指那块**对外通信的真实网卡**，VIP 如果绑在这上面就会向外广播 ARP，所以才要绑到 `lo:0` 上。



## 关键配置要点

**1. RS 绑定 VIP 到 lo:0 接口**

```bash
ip addr add <VIP>/32 dev lo label lo:0
```

必须用 `/32`，否则 RS 会认为自己是这个网段的网关并参与路由广播。

> ### 1. 为什么必须 /32
>
> 先理解 Linux 绑 IP 时掩码的含义。你写：
>
> bash
>
> ```bash
> ip addr add 192.168.1.100/24 dev lo
> ```
>
> Linux 不只是记了一个 IP，它同时会在路由表里自动生成一条：
>
> ```
> 192.168.1.0/24 dev lo scope link
> ```
>
> 意思是"去往 192.168.1.0/24 这个网段的包，从 lo 出去"。这就出问题了——RS 的 `eth0` 也在 `192.168.1.0/24`，现在 `lo` 也宣称自己能处理这个网段，内核路由就乱了，可能把本该从 `eth0` 出去的包错误地路由到 `lo`。
>
> 换成 `/32`：
>
> bash
>
> ```bash
> ip addr add 192.168.1.100/32 dev lo label lo:0
> ```
>
> 路由表只生成：
>
> ```
> 192.168.1.100/32 dev lo scope host
> ```
>
> `scope host` 表示这个 IP 仅本机内部使用，不产生任何网段路由，对其他接口零影响。

**2. 抑制 RS 对 VIP 的 ARP 应答**

这是 DR 模式最容易踩的坑。如果 RS 也对 VIP 的 ARP 请求作答，交换机会把请求直接发给 RS，绕过 LVS，负载均衡失效。

```bash
# 只响应目标 IP 为自身接口 IP 的 ARP 请求
echo 1 > /proc/sys/net/ipv4/conf/all/arp_ignore
# ARP 应答的源 MAC 只使用请求进入的网卡 IP 对应的 MAC
echo 2 > /proc/sys/net/ipv4/conf/all/arp_announce
```



> ### 2. 两个 ARP 参数
>
> 先理解 ARP 是什么。局域网里发包之前，发送方需要知道目标 IP 对应的 MAC 地址，于是广播问一句"谁是 192.168.1.100？"，持有这个 IP 的机器回答"是我，我的 MAC 是 xx:xx"。这就是 ARP。
>
> DR 模式的问题在于：VIP 同时存在于 Director 的 `eth0` 和所有 RS 的 `lo:0` 上。如果 RS 也回答 ARP 请求，交换机的 ARP 表就会记"VIP 的 MAC = RS 的 MAC"，客户端的包直接送到 RS，Director 完全被绕过。
>
> 所以需要让 RS 闭嘴。
>
> **`arp_ignore = 1`**
>
> 控制"收到 ARP 请求时，要不要回答"。
>
> - 默认值 `0`：只要本机有这个 IP，不管请求是问哪个接口的，都回答
> - 设为 `1`：只有当 ARP 请求的目标 IP，正好是收到这个请求的那个接口上配置的 IP，才回答
>
> RS 的 `eth0` 上没有 VIP，VIP 只在 `lo:0` 上。ARP 请求从 `eth0` 进来问 VIP 是谁，`arp_ignore=1` 让 RS 检查：`eth0` 上有 VIP 吗？没有，不回答。沉默。
>
> **`arp_announce = 2`**
>
> 控制"主动发出 ARP 包时（比如本机发包、或者响应时），用哪个 IP 作为源 IP 宣告自己"。
>
> - 默认值 `0`：可以用本机任意接口的 IP 来宣告，包括 `lo:0` 上的 VIP
> - 设为 `2`：只用发包那个接口上配置的 IP 来宣告，绝不跨接口借用
>
> 这防止了另一种泄露：RS 主动发 ARP 时（比如发出第一个包触发 ARP），不小心用 VIP 作为源 IP 宣告出去，导致交换机把 VIP 的 MAC 学成这台 RS 的 MAC。
>
> 两个参数配合，RS 在 ARP 层面对 VIP 完全隐身，整个局域网里只有 Director 宣告 VIP。



**3. LVS 与 RS 必须在同一个二层网络**

因为 DR 靠改写 MAC 地址转发，不能跨路由器，这是 DR 模式最大的限制。



> ### 3. 为什么必须同一个二层网络
>
> DR 的转发手段是**改写以太网帧的目标 MAC**。这是一个纯二层操作。
>
> 二层帧的有效范围就是一个广播域（同一个交换机或 VLAN）。帧出了这个范围碰到路由器，路由器会剥掉二层帧头、重新封装，Director 改写的那个 MAC 就消失了，转发完全失效。
>
> 对比一下 NAT 模式和 TUN 模式为什么能跨网段：
>
> - NAT：Director 改的是三层 IP 地址，IP 包穿越路由器后 IP 头还在
> - TUN：在原始 IP 包外面再套一层 IP 隧道，靠外层 IP 头寻址，能跨路由
>
> DR 没有这些，它的"转发凭证"就是那个 MAC 地址，离开二层就失效。所以 LVS Director 和所有 RS 必须插在同一个交换机上（或同一个 VLAN 内）。这是 DR 模式唯一的硬限制，也是为了极致性能付出的代价。



## 与其他模式对比

|          | DR                       | NAT               | TUN          |
| -------- | ------------------------ | ----------------- | ------------ |
| 响应路径 | 直接返回                 | 经过 LVS          | 直接返回     |
| 跨网段   | ❌ 不支持                 | ✅                 | ✅            |
| 性能     | 最高                     | 受限于 LVS 带宽   | 高           |
| RS 要求  | 绑 VIP + 抑制 ARP        | 默认网关指向 LVS  | 支持 IP 隧道 |
| 适用场景 | 高并发 HTTP/视频等大流量 | 后端 RS 无公网 IP | 跨机房部署   |

DR 模式是实际生产中用得最多的 LVS 模式，特别适合读多写少、响应体大的场景（如静态资源、流媒体）。可以点击图中节点继续深入某个细节。





# LVS Director 在 DR 模式下做了什么操作？

## LVS Director 在 DR 模式下做的事极其克制

一句话概括：**只改二层帧头，不碰三层 IP 包**。用数据包"改造前后对比"来展示最直观：

![image-20260526145620905](C:\Users\q1948\Desktop\project\project-study\image-20260526145620905.png)

## Director 具体做的几件事

**1. 维护连接追踪表（`ip_vs` 内核模块）**

第一个 SYN 包到达时，`ip_vs` 根据调度算法（rr、wrr、lc 等）选定一台 RS，在连接表里记下 `{CIP:port → RS}`。后续属于同一连接的包直接查表，不再重新调度，保证同一 TCP 连接始终去同一台 RS。

**2. ARP 代答 VIP**

局域网里只有 Director 对外宣告"VIP 的 MAC 是我"，RS 上的 `arp_ignore/arp_announce` 让它们对 VIP 的 ARP 请求保持沉默。Director 的这个 ARP 角色是整个 DR 模式能工作的前提。

**3. 改写 dst MAC 并转发**

这是 Director 对数据包的唯一操作：把以太网帧的目标 MAC 换成目标 RS 的 MAC，然后从网卡发出。由于 LVS 和 RS 在同一二层，交换机会直接把帧投递给 RS，不需要经过路由。

**4. 对响应包完全不感知**

RS 回给客户端的包，Director 根本看不到。这是 DR 性能高的根本——Director 的带宽压力只有入方向，出方向的大流量（视频、文件下载）全部由 RS 直接承担。

## 与 NAT 模式的本质区别

NAT 模式的 Director 是一个真正的**代理**，需要同时处理请求和响应，出口带宽是瓶颈；DR 模式的 Director 更像一个**帧级路由器**，只做入方向的 MAC 改写，处理成本极低，单台 Director 理论上可以调度数十台 RS 而不成为瓶颈。





## ipvsadm 命令结构

`ipvsadm` 是管理 LVS 的用户态工具，操作分两层：

- **上层**：管理"集群服务"（Virtual Service），即 VIP:port
- **下层**：管理某个集群服务下的"真实服务器"（Real Server）

大写字母操作集群服务，小写字母操作 RS，记住这个规律就全懂了。

------

## 集群服务操作（大写）

```bash
ipvsadm -A -t VIP:port -s 调度算法    # 新增
ipvsadm -E -t VIP:port -s 新算法      # 修改
ipvsadm -D -t VIP:port                # 删除某条
ipvsadm -C                            # 清空所有
ipvsadm -S                            # 保存到标准输出（可重定向到文件）
ipvsadm -R                            # 从标准输入恢复（读文件）
```

`-t` 表示 TCP，对应的 `-u` 是 UDP。`-s` 后面跟调度算法：

| 算法  | 全称             | 含义                   |
| ----- | ---------------- | ---------------------- |
| `rr`  | Round Robin      | 轮询，依次分配         |
| `wrr` | Weighted RR      | 加权轮询，按权重比例   |
| `lc`  | Least Connection | 最少连接数             |
| `wlc` | Weighted LC      | 加权最少连接，默认算法 |

------

## RS 操作（小写）

```bash
ipvsadm -a -t VIP:port -r RIP:port -g    # 添加 RS
ipvsadm -e -t VIP:port -r RIP:port -w 2  # 修改 RS（比如改权重）
ipvsadm -d -t VIP:port -r RIP:port       # 删除 RS
```

`-r` 后面跟真实服务器的 IP:port，`-g` 指定转发模式为 DR（gateway），`-w` 设置权重。

转发模式参数对应关系：

| 参数 | 模式                 |
| ---- | -------------------- |
| `-g` | DR（Direct Routing） |
| `-m` | NAT（Masquerading）  |
| `-i` | TUN（IP Tunneling）  |

------

## 你给的那条命令

```bash
ipvsadm -A -t 192.168.100.150:80 -s rr
```

逐字段解读：

- `-A`：新增一个集群服务
- `-t 192.168.100.150:80`：TCP 协议，VIP 是 `192.168.100.150`，监听 80 端口
- `-s rr`：调度算法用轮询

这条只是声明了"有这么一个集群入口"，还没有 RS，接下来还需要：

```bash
# 添加两台 RS，DR 模式，权重默认 1
ipvsadm -a -t 192.168.100.150:80 -r 192.168.100.11:80 -g
ipvsadm -a -t 192.168.100.150:80 -r 192.168.100.12:80 -g
```

用 `ipvsadm -Ln` 查看当前配置：

```
IP Virtual Server version 1.2.1 (size=4096)
Prot LocalAddress:Port Scheduler Flags
  -> RemoteAddress:Port           Forward Weight ActiveConn InActConn
TCP  192.168.100.150:80 rr
  -> 192.168.100.11:80            Route   1      0          0
  -> 192.168.100.12:80            Route   1      0          0
```

`Route` 就是 DR 模式，`Weight` 是权重，`ActiveConn` 是当前活跃连接数。