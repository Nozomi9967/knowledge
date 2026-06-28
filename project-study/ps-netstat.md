## 一、ps 命令详解

### 什么是 ps？

`ps` (Process Status) 是 Linux/Unix 系统中用于**查看进程状态**的命令，显示当前系统中的进程信息。

### 基本语法

```bash
ps [选项]
```

### 常用参数组合

| 参数  | 含义                         | 说明                          |
| ----- | ---------------------------- | ----------------------------- |
| `aux` | 显示所有进程（BSD风格）      | 最常用，显示用户、CPU、内存等 |
| `-ef` | 显示所有进程（System V风格） | 显示完整格式，包含PPID        |
| `-e`  | 显示所有进程                 | 等同于 `-A`                   |
| `-f`  | 完整格式                     | 显示PPID、启动时间等          |
| `-l`  | 长格式                       | 显示更详细的进程信息          |
| `-u`  | 显示指定用户的进程           | `ps -u root`                  |
| `-p`  | 指定PID                      | `ps -p 1234`                  |

### 输出字段说明（`ps aux`）

```bash
[root@localhost ~]# ps aux
USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
root         1  0.0  0.1 128164  6848 ?        Ss   10:20   0:01 /usr/lib/systemd/systemd
```

| 字段    | 含义                   |
| ------- | ---------------------- |
| USER    | 进程所有者             |
| PID     | 进程ID                 |
| %CPU    | CPU使用率              |
| %MEM    | 内存使用率             |
| VSZ     | 虚拟内存大小（KB）     |
| RSS     | 常驻物理内存大小（KB） |
| TTY     | 终端（?表示无终端）    |
| STAT    | 进程状态码             |
| START   | 启动时间               |
| TIME    | 累计CPU时间            |
| COMMAND | 命令和参数             |

### 进程状态码（STAT）

| 状态 | 含义                    |
| ---- | ----------------------- |
| R    | 运行中（Running）       |
| S    | 可中断睡眠（Sleeping）  |
| D    | 不可中断睡眠（通常I/O） |
| Z    | 僵尸进程（Zombie）      |
| T    | 被停止（Stopped）       |
| s    | 会话首领                |
| +    | 前台进程组              |
| l    | 多线程进程              |

### 常用示例

```bash
# 1. 查看所有进程（最常用）
ps aux

# 2. 查看所有进程，树形结构显示父子关系
ps auxf
# 或
pstree

# 3. 查看特定用户的进程
ps -u root

# 4. 查看进程详细信息（包含PPID）
ps -ef

# 5. 查看某个PID的进程
ps -p 1234

# 6. 查看进程的线程
ps -T -p 1234

# 7. 动态实时查看（top/htop 更合适）
watch -n 1 'ps aux --sort=-%cpu | head -10'

# 8. 自定义输出字段
ps -eo pid,ppid,user,%cpu,%mem,cmd --sort=-%cpu
```

---

## 二、netstat 命令详解


**windows**
netstat -ano | findstr "端口号"

### 什么是 netstat？

`netstat` (Network Statistics) 是 Linux/Unix 系统中用于**查看网络连接、路由表、网络接口统计**的命令。

### 基本语法

```bash
netstat [选项]
```

### 常用参数组合

| 参数 | 含义     | 说明                      |
| ---- | -------- | ------------------------- |
| `-t` | TCP连接  | 显示TCP套接字             |
| `-u` | UDP连接  | 显示UDP套接字             |
| `-l` | 监听状态 | 显示正在监听的端口        |
| `-a` | 所有连接 | 包括监听和非监听          |
| `-n` | 数字格式 | 不解析域名，显示IP/端口号 |
| `-p` | 显示进程 | 显示PID/程序名（需root）  |
| `-r` | 路由表   | 显示内核路由信息          |
| `-i` | 网卡接口 | 显示网络接口统计          |
| `-c` | 持续输出 | 每隔1秒输出一次           |

### 输出字段说明（`netstat -tunlp`）

```bash
[root@localhost ~]# netstat -tunlp
Active Internet connections (only servers)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name
tcp        0      0 0.0.0.0:22              0.0.0.0:*               LISTEN      1234/sshd
tcp6       0      0 :::9090                 :::*                    LISTEN      5678/kafka
```

| 字段             | 含义                         |
| ---------------- | ---------------------------- |
| Proto            | 协议（tcp/tcp6/udp）         |
| Recv-Q           | 接收队列（待应用读取的数据） |
| Send-Q           | 发送队列（待发送的数据）     |
| Local Address    | 本地地址和端口               |
| Foreign Address  | 远端地址和端口               |
| State            | 连接状态（TCP）              |
| PID/Program name | 进程ID和程序名               |

### TCP 连接状态

| 状态        | 含义                       |
| ----------- | -------------------------- |
| LISTEN      | 监听状态（服务端等待连接） |
| ESTABLISHED | 已建立连接                 |
| TIME_WAIT   | 主动关闭后等待             |
| CLOSE_WAIT  | 被动关闭后等待应用关闭     |
| SYN_SENT    | 正在尝试连接               |

### 常用示例

```bash
# 1. 查看所有监听端口（最常用）
netstat -tunlp

# 2. 查看所有连接（包括监听和非监听）
netstat -tunlap

# 3. 查看某个端口的占用情况
netstat -tunlp | grep :9092

# 4. 查看路由表
netstat -rn

# 5. 查看网络接口统计
netstat -i

# 6. 持续监控网络连接（每秒更新）
netstat -tunlp -c

# 7. 查看统计信息（收发包数量、错误等）
netstat -s

# 8. 查看某个进程的网络连接
netstat -tunlp | grep kafka
```

---

## 三、ps 和 netstat 的核心区别

| 维度         | **ps**                  | **netstat**                    |
| ------------ | ----------------------- | ------------------------------ |
| **监控对象** | 系统的**进程**          | 系统的**网络**                 |
| **核心功能** | 查看进程状态、CPU、内存 | 查看网络连接、端口、路由       |
| **数据来源** | `/proc` 文件系统        | `/proc/net`、内核网络栈        |
| **典型问题** | "哪个进程CPU跑满了？"   | "哪个端口被占用了？"           |
| **输出信息** | PID、CPU%、MEM%、CMD    | 协议、本地地址、远端地址、状态 |
| **关联性**   | 进程本身的信息          | 进程占用的网络资源             |

---

## 四、两个命令的关联使用

### 1. 找到占用某个端口的进程

```bash
# netstat 找到PID
netstat -tunlp | grep :9092
# 输出: tcp6 0 0 :::9090 :::* LISTEN 1234/java

# ps 查看进程详情
ps -p 1234 -f
# 输出: UID PID PPID C STIME TTY TIME CMD
#      root 1234 1 0 10:20 ? 00:00:15 /usr/bin/java -jar kafka.jar
```

### 2. 查看某个进程的网络连接

```bash
# 先用 ps 找到PID
ps aux | grep kafka
# 假设PID是1234

# 再用 netstat 查看该进程的连接
netstat -tunlap | grep 1234
```

### 3. 排查端口冲突

```bash
# 1. 检查端口是否被监听
netstat -tunlp | grep :8080

# 2. 如果被占用，找到是什么进程
# 3. 用 ps 看这个进程是否正常
ps -fp $(netstat -tunlp 2>/dev/null | grep :8080 | awk '{print $7}' | cut -d/ -f1)
```

### 4. 监控某个进程的 CPU 和网络情况

```bash
# 终端1：实时看进程的CPU/内存
watch -n 1 'ps aux | grep kafka'

# 终端2：实时看该进程的网络连接
watch -n 1 'netstat -tunlp | grep kafka'
```

---

## 五、针对你之前的错误场景

### 错误信息分析

```
Cannot lock svdir, maybe another 'supervise' is running
```

### 用 ps 排查

```bash
# 查看是否有 supervise 进程残留
ps aux | grep supervise

# 查看是否有 Druid 进程
ps aux | grep druid

# 查看所有 java 进程
ps aux | grep java
```

### 用 netstat 排查

```bash
# 查看 Druid 相关端口是否被占用（Druid 默认端口）
netstat -tunlp | grep -E "8081|8082|8083|8090|8091|8100|8888"

# 查看是否有进程在监听 Druid 的端口
```

---

## 六、两个命令的现代替代工具

| 命令      | 替代工具             | 说明                         |
| --------- | -------------------- | ---------------------------- |
| `ps`      | `htop`, `glances`    | 更友好的进程查看界面         |
| `netstat` | `ss`, `lsof`, `nmap` | `ss` 更快，`lsof` 功能更丰富 |

### 使用 `ss` 替代 netstat

```bash
# ss 比 netstat 更快（直接读内核，不解析/proc）
ss -tunlp        # 查看监听端口
ss -tunap        # 查看所有连接
ss -t state listening  # 只查看监听状态
```

### 使用 `lsof` 查看端口占用

```bash
# lsof 也可以看端口
lsof -i :9092    # 查看9092端口谁在用
lsof -i tcp      # 查看所有TCP连接
```

---

## 总结

| 命令         | 一句话概括                 |
| ------------ | -------------------------- |
| **ps**       | "系统里在跑什么进程？"     |
| **netstat**  | "系统的网络在干什么？"     |
| **结合使用** | "哪个进程占用了这个端口？" |