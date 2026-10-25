---
tags:
  - 操作系统
  - 内存管理
  - OOM
  - 容器
  - cgroup
  - 面试
created: 2026-10-14
updated: 2026-10-14
source: Q13 追问链答疑整理（页缓存 / slab / cgroup / swapoff / OOM Killer / dmesg / 退出码 137）
---

# OOM Killer 与容器内存详解

> **定位**：[[OS八股#4.5 内存不够会怎样：swap 与 OOM Killer|OS八股 4.5]] 的深水区，也是 [[OS热门面试题]] Q13 追问链的完整展开——从「内存不够三步走」一路挖到页缓存、slab、cgroup、swapoff、dmesg、退出码 137。
>
> 名词级定义（页缓存、Slab、匿名页、swap）不在这里重复，见 [[OS 名词解释速查#一、内存管理]]；本页只写它没覆盖的机制与实操。

---

## 一、总览：内存不够时的三步求生线

内存不够不是"一步到杀"，是**三级台阶，先捡代价小的做**：

1. **回收**：丢页缓存、收缩 slab 里可回收的部分。这些东西磁盘上都还有"原件"或随时能重建，丢了没有副作用——**几乎免费**。
2. **换出**：回收腾不出足够空间，就把**匿名页**写进 swap 再释放物理内存。匿名页没有原件，只能"抄走"；回头访问要读盘（毫秒级），**RT 抖动的总根源**。
3. **杀进程**：swap 也满了（或压根没 swap / 被 `swapoff` 关了），内核放弃"救所有人"，**OOM Killer** 出手，SIGKILL 挑一个杀掉。

| 台阶 | 动作 | 代价 | 何时走到这一步 |
|------|------|------|----------------|
| ① 回收 | 丢页缓存、收缩可回收 slab | 几乎免费 | 内存开始紧张 |
| ② 换出 | 匿名页写进 swap，释放物理内存 | 慢且不可预测（RT 毛刺） | 回收腾不出足够空间 |
| ③ 杀进程 | OOM Killer 按 oom_score 挑一个 SIGKILL | 一条命（丢状态、丢服务） | swap 满 / 无 swap / 被 swapoff |

**中间件的取舍逻辑**：②和③之间，它们宁可选③——被 OOM 快速杀掉、由守护进程或集群拉起，也不要②那种全系统跟着一起"慢性毛刺"。所以生产上很多中间件主动 `swapoff`（下面第三节细说）。

---

## 二、第一步：页缓存与 slab 的回收

> 两段完整定义在 [[OS 名词解释速查#页缓存（Page Cache）]] 和 [[OS 名词解释速查#Slab 分配器（Slab Allocator）]]，这里只补"回收视角"下没说透的细节。

### 2.1 页缓存：为什么"优先拿它开刀"

- 读文件时，内核把磁盘数据**按页（通常 4KB）**缓存在内存里，下次读同一数据直接从内存返回，不碰磁盘；写文件先写页缓存、标记"脏页"，再由内核异步刷回。
- 回收代价低的原因就在"原件"二字：**干净页**（与磁盘一致）直接丢弃，需要时再读；**脏页**先写回磁盘再释放。
- `free -h` 里看到的 **`buff/cache` 大部分就是它**——别把 buff/cache 高当成"内存被占了"，判断内存紧张看 **available** 而不是 free（Linux 哲学：空闲内存不拿去缓存等于浪费）。
- ⚠ **容器坑**（这是 OOM 排障里"页缓存也可能是元凶"的由来）：**页缓存计入 cgroup 内存用量**。容器里疯狂写日志、大文件读写时，页缓存会把这部分"软占用"顶到 cgroup 限额上——明明堆没超，容器却超限被杀。

### 2.2 slab：内核自己的对象池

- 内核运行时要频繁创建销毁大量小对象（`dentry` 目录项、`inode` 文件元数据、进程描述符、socket 结构）。每次按整页 4KB 分配既浪费又碎片严重——slab 的做法是**先拿整页、切成固定小块、按对象类型分池（`kmem_cache`）复用**。
- 查看方式：**`slabtop`**（动态刷新）、**`cat /proc/slabinfo`**（静态快照）。
- 回收时区分两类：
  - **`SReclaimable`（可回收）**：dentry、inode 这类纯缓存，丢了随时能从磁盘重建；
  - **`SUnreclaim`（不可回收）**：内核正在使用的对象——几十万线程、海量连接把这类顶上去，是挤不掉的。
- ⚠ **`SUnreclaim` 持续增长，往往是内核或驱动有内存泄漏**。这种局面的麻烦在于：**即使页缓存全可回收、swap 还能用，内存也可能被不可回收的 slab 顶到 OOM**——排障时如果发现 slab 是元凶，手段就不是调参数了，而是升级内核/换驱动/重启。

---

## 三、第二步：swap 换出与 swapoff / swap 区

### 3.1 换出为什么造成 RT 抖动（一句带过）

匿名页（堆、栈、JVM 堆、堆外内存）没有磁盘原件，**要腾地方只能先写进 swap**；之后访问它触发缺页中断、从磁盘读回（**必是硬缺页**，见 [[缺页异常详解#五、判断总表]]）。磁盘比内存慢几个数量级——JVM 堆的一部分被换出后，GC 扫过这些页就要大量读盘，GC 停顿从毫秒级跳到秒级。完整机制（kswapd、LRU 冷页、vmstat si/so 观测、桌面笔记类比）见 [[OS 名词解释速查#swap / 换入换出（Swap In / Swap Out）]]。

### 3.2 swapoff：命令与副作用

```bash
swapoff -a          # 关闭系统里所有已启用的 swap
swapoff /swapfile   # 只关指定的 swap 区
swapon --show       # 查看当前 swap 启用情况（对应开启命令是 swapon）
```

- ⚠ **执行时的隐藏副作用**：关闭前，内核会**先把 swap 区里已换出的匿名页全部读回物理内存**，然后才真正停用。如果当前可用内存装不下这些数据，`swapoff` 会非常慢，**甚至自己就把系统推到 OOM Killer**。所以 swap 用量大的机器做 `swapoff` 要挑业务低峰期。
- **永久关闭**：`swapoff -a` 只在当前运行期间有效，重启会恢复——要永久关闭得把 `/etc/fstab` 里带 `swap` 的那行注释掉。
- **工程动机**：关掉 swap 后，匿名页只能留在物理内存，不会被换出——代价是内存真不够时没有缓冲、直接走到 OOM Killer。这正是中间件的主动选择：**宁可快死，不要慢喘**。Kubernetes 的 kubelet 也默认要求节点关闭 swap，让内存限制和调度行为可预测。
- **折中方案**：不想完全关闭，就把 `vm.swappiness` 调低（如 1 或 10），让内核尽量不换出匿名页、先丢页缓存。

### 3.3 swap 区：分区还是文件

| 形式 | 说明 | 特点 |
|------|------|------|
| swap 分区 | 单独划一块磁盘分区，格式化为 swap 类型（如 `/dev/sdb2`） | 传统做法，大小改不了 |
| swap 文件 | 普通文件系统里的一个固定大小文件（如 `/swapfile`） | 现代常用，调整大小灵活 |

创建示例（swap 文件四步）：

```bash
fallocate -l 2G /swapfile   # 分配 2GB 空间
chmod 600 /swapfile         # 仅 root 可读写
mkswap /swapfile            # 格式化为 swap 格式
swapon /swapfile            # 启用
```

- 内核不关心是哪种形式，都当作"一块可以存放内存页的磁盘空间"；要开机自动启用，在 `/etc/fstab` 加一行配置。
- swap 区里的内容是**内核内部数据，不是普通文件**，不能直接打开阅读。
- **大小权衡**：太小起不到缓冲作用；太大则会让系统在严重缺内存时长时间"慢性卡死"（一直换入换出、什么都不干），而不是快速失败——这也是"宁 swapoff 勿毛刺"的另一个理由。

---

## 四、第三步：OOM Killer 全解

### 4.1 什么时候触发：两种范围

内核分配内存失败 → 先回收页缓存和可回收 slab → 再尝试 swap 换出 → **仍然凑不出所需内存**，才调用 OOM Killer。触发有两种范围：

- **全局 OOM**：整机物理内存（加 swap）耗尽，谁来申请都拿不到。
- **cgroup OOM**：容器或某个 cgroup 的用量超过**自己的上限**——即使宿主机内存还很充足，也会在组内触发。容器里 Java 进程退出码 137，多半是这种（见第五节）。

### 4.2 杀谁：oom_score 怎么算

- 内核给每个进程算一个 **`oom_score`**（0~1000，看 `/proc/<pid>/oom_score`），分数最高者被杀。
- 主要依据：
  - **占用的内存越多（常驻内存、swap 用量、页表等），分数越高**——但注意"占内存最多的最容易被杀"的精确版是"**占匿名内存最多的**"，页缓存占再多也不扛分（随时可回收，不构成威胁）；
  - 再叠加 **`oom_score_adj`**（-1000~+1000）的调整值。
- **保护级别**（`/proc/<pid>/oom_score_adj`）：
  - **-1000**：该进程基本不会被杀（免死金牌，内核直接跳过）；
  - **+1000**：更容易被杀；默认 0。
  - 实战几乎只用负值：给 **sshd、监控 agent** 这类"排障入口"设 -1000——否则机器快挂时你连登都登不上。
  - ⚠ 反向注意：**别把大内存业务进程都设 -1000**。内核需要找到可杀对象时如果全被豁免，可能扩大选择范围甚至触发整机 panic——-1000 是给小而关键的进程的，不是给"重要的大家伙"的。

### 4.3 被杀之后：尸检特征

被选中后内核发 **SIGKILL（9）**：不可捕获、不可忽略、不可阻塞。特征是"三无"：

- **无异常**——JVM 不会抛 OutOfMemoryError（它来不及知道）；
- **无 hs_err**——不写崩溃日志；**无 shutdown hook**——不执行任何清理；
- **应用日志一片空白**——进程直接消失，这是排查时最迷惑的地方。

"是内核杀的（oom-kill）还是 JVM 自己抛 OutOfMemoryError"，正是 OOM 排障要分清的第一件事——两种 OOM 的对照表见 [[OS八股#4.5 内存不够会怎样：swap 与 OOM Killer]] 和 [[OS八股-重构版#两种 OOM 对照表]]。核心记忆：**前者说明整机/容器内存真没了，元凶常在堆外，加 -Xmx 反而死得更快；后者是 JVM 管不住堆，机器有余量时加 -Xmx 可能真管用。**

---

## 五、容器与 cgroup：Java 进程为什么要特别小心

### 5.1 容器不是虚拟机：namespace + cgroup

题目里的"容器"**不限于 Docker**——Docker、containerd、Podman、Kubernetes 调度的 Pod，底层都靠同样的两项内核能力：

- **namespace（隔离）**：让进程以为自己独占一套进程号、网络、文件系统、主机名——决定**"看得见什么"**；
- **cgroup（限制）**：规定这组进程最多能用多少 CPU、内存、IO——决定**"能用多少"**。

所以容器**不是虚拟机**，没有独立内核。容器里的 Java 进程就是**宿主机上的一个普通进程**，只是被套上了这两层限制。

### 5.2 cgroup 是什么：内存限额怎么写在文件里

cgroup（control group）把一组进程归成"组"，对组统一设资源上限并统计用量。常见控制项：memory（内存）、cpu（份额/配额）、io（磁盘带宽）、pids（进程数上限）。

内存上限的落点（看容器配额就看这两个文件）：

- **cgroup v1**：`/sys/fs/cgroup/memory/memory.limit_in_bytes`
- **cgroup v2**：`/sys/fs/cgroup/memory.max`

`docker run -m 2g`、Kubernetes 的 `resources.limits.memory: 2Gi`，最终都是在为容器对应的 cgroup 写这个值。

### 5.3 cgroup OOM：宿主机内存充足也会杀

- 整机内存不够触发**全局 OOM**；但容器的用量一旦超过**自己的 cgroup 上限**，即使宿主机内存还很充足，内核也会在这个 cgroup 内部触发 OOM Killer，**直接杀掉组内进程**——这就是退出码 137（SIGKILL）的来源。
- 这个过程中，内核计入 cgroup 的**不只是 Java 堆**，还包括：元空间、线程栈、堆外内存（DirectByteBuffer）、Code Cache，以及**页缓存**。
- 关键点：**JVM 并不知道自己被"超限"了**，所以来不及抛 OutOfMemoryError，进程就被杀了——表现就是"日志什么都没写、进程没了、退出码 137"。

### 5.4 追问完整版：为什么容器里的 Java 进程特别容易触发 OOM Killer？

因果链四层：

1. **老版本 JVM 根本看不到 cgroup**：没显式设置 `-Xmx` 时，JVM 按**宿主机物理内存**算默认堆（约 1/4）。宿主机 8G、容器限 2G 的情况下，JVM 可能把堆默认开到 2G——光堆就顶满配额，再算上非堆必超。
2. **新版本也不是万能**：JDK 8u191+/10+ 默认开启容器感知（`UseContainerSupport`），堆按 cgroup 限额算——但配额里住的**不只堆**：元空间、每线程约 1MB 的栈、DirectByteBuffer 堆外、Code Cache 全占同一池子。
3. **超限即被杀**：一旦总和超过 cgroup limit，内核直接 SIGKILL——JVM 没有任何机会抛异常，日志空白。
4. **页缓存也在凑热闹**：容器里的文件读写（日志狂写）产生的页缓存同样计入限额，让"看起来没超"的容器超限。

**解决四件事**：

- 显式配 `-Xmx` 和容器配额匹配：**堆一般取配额的 50%~75%**（留 25% 左右给非堆）；
- 用**容器感知的 JDK**（8u191+/10+），或用 `-XX:MaxRAMPercentage` 按比例设堆；
- **留意堆外内存**：`-XX:MaxDirectMemorySize` 限住 DirectByteBuffer，用 NMT（`-XX:NativeMemoryTracking=summary` + `jcmd VM.native_memory`）看 JVM 原生内存全景；
- **线程数纳入预算**：每线程栈默认约 1MB，线程数失控是常见的隐性开销。

### 5.5 容器 OOM 排查命令组

| 想看什么 | 命令 | 判据 |
|----------|------|------|
| 容器内存上限（v2） | `cat /sys/fs/cgroup/memory.max` | 这就是配额 |
| 容器内存上限（v1） | `cat /sys/fs/cgroup/memory/memory.limit_in_bytes` | 同上 |
| Docker 是否因 OOM 被杀 | `docker inspect` 容器名，看 `OOMKilled` | `true` = 内存超限被杀 |
| K8s Pod 是否 OOMKilled | `kubectl describe pod` | `Reason: OOMKilled` + `Exit Code: 137` |
| 限额使用率 | `docker stats` / `kubectl top pod` | 逼近 100% 就该动手了 |
| 宿主机内核日志 | `dmesg -T \| grep -i oom` | 写明是哪个 cgroup 超限被杀 |

---

## 六、排障两板斧：dmesg 与退出码 137

### 6.1 `dmesg | grep -i oom` 拆解

**这条命令**：查看内核日志，只显示包含 "oom"（不区分大小写）的行，用来确认系统是否发生过 OOM Killer 杀进程。

拆开看：

- `dmesg`：打印**内核环形缓冲区**里的日志——内存不足、硬件错误、驱动加载、OOM Killer 杀进程等内核层面的事件都记录在这里；
- `|`：管道，把左边输出交给右边；
- `grep -i oom`：筛出含 `oom` 的行，`-i` 表示忽略大小写（`OOM`/`oom`/`Oom` 都能命中）。

**典型输出**（发生过 OOM Killer 时）：

```
[12345.678901] java invoked oom-killer: gfp_mask=0x24201ca(GFP_HIGHUSER_MOVABLE), order=0, oom_score_adj=0
[12345.678950] Out of memory: Killed process 2345 (java) total-vm:8123456kB, anon-rss:3912345kB, file-rss:0kB
```

逐行解读：

- 第一行说的是**谁触发的**——java 在申请内存时调用（invoked）了 oom-killer。⚠ **触发者 ≠ 被杀者**，它只是"点了炮"的那个进程，别读混；
- 第二行说的是**谁被杀**——PID 2345（名字 java），后面 `anon-rss:3912345kB`（约 3.7G）是它实际占用的**匿名内存**，这是排障最关键的数字；`total-vm` 只是虚拟地址空间大小，参考价值低；
- 什么都没输出，通常说明没发生过 OOM Killer——进程退出是别的原因（比如 JVM 自己抛了 OutOfMemoryError）。

**实用变体**：

- `dmesg -T | grep -i -E "oom|killed process"`：`-T` 把时间戳转成可读日期时间（方便对照应用何时挂掉），`-E` 支持多关键词；
- `journalctl -k | grep -i oom`：systemd 系统上查内核日志的等价入口；
- `/var/log/messages`、`/var/log/kern.log`：dmesg 缓冲被冲掉后的历史记录所在。

**三个注意**：

- dmesg 的**环形缓冲区大小有限**，日志太多时旧记录被覆盖——机器运行很久后可能查不到，要去翻 messages/kern.log；
- 有些系统普通用户执行 `dmesg` 会被限制（`kernel.dmesg_restrict`），需要 `sudo`；
- **在容器里通常看不到宿主机的内核日志**，必须到宿主机上执行。

**冷知识**：`dmesg` 的 d 没有官方定义，三种常见说法——display message（显示内核消息，最好记）、driver message（早期主要用来看驱动信息）、diagnostic message（诊断消息）。按 "display message" 记最省事。

### 6.2 退出码 137：128 + 信号号

**规则从哪来**：进程正常退出时，退出码是程序自己返回的数字（0 成功，1~125 通常是自定义错误码）；**被信号终止时，shell 会把退出码设为 `128 + 信号编号`**，以此与正常退出码区分。

| 退出码 | 信号 | 含义 | 常见场景 |
|--------|------|------|----------|
| **137** | 9（SIGKILL） | 被强杀 | cgroup OOM、`kill -9`、`docker stop` 超时补杀 |
| 143 | 15（SIGTERM） | 被"请你退出"终止 | `docker stop` 先发它、K8s 停服默认发它 |
| 139 | 11（SIGSEGV） | 段错误 | 本地代码崩溃（JVM crash） |
| 134 | 6（SIGABRT） | 主动中止 | JVM 崩溃生成 hs_err 时可能出现 |

**为什么"十有八九是 OOM"**：在容器里，谁会发 SIGKILL 呢——常见来源有四个：

1. **内核 OOM Killer**（cgroup 内存超限）——最常见；
2. 人为执行 `kill -9`；
3. **`docker stop` 超时**：先发 SIGTERM，等宽限期（默认 10 秒）仍未退出，就改发 SIGKILL；
4. **Kubernetes 的 kubelet**：因节点内存压力驱逐，或 Pod 终止超时。

**怎么判断是不是 OOM**：

- Docker：`docker inspect` 容器名，`OOMKilled` 为 `true` 就是内存超限；
- Kubernetes：`kubectl describe pod`，看到 `Reason: OOMKilled` 且 `Exit Code: 137`；
- 宿主机：`dmesg -T | grep -i oom` 查内核记录。

如果 `OOMKilled` 是 `false` 而退出码仍是 137，就要考虑是被人为或编排系统杀掉的（上面来源 2~4），**别只盯着内存查**。

---

## 七、预防 checklist（防 OOMKilled 六件事）

1. **先定性**：`dmesg -T | grep -i oom` 确认是不是 oom-kill，看被杀进程的 `anon-rss`——分清"内核杀的"还是"JVM 自己 OOM"；
2. **算总账**：cgroup 配额 = 堆 + 元空间 + 线程栈 + 堆外 + 其他；**-Xmx 一般取配额的 50%~75%**，留 25% 余量；
3. **限住堆外**：`-XX:MaxDirectMemorySize` 限制 DirectByteBuffer；用 NMT 看 JVM 原生内存分布；
4. **数线程**：每线程栈默认约 1MB，几百上千线程就是几百 MB~1G 的隐性开销；
5. **容器感知**：JDK 8u191+/10+ 默认开 `UseContainerSupport`；或用 `-XX:MaxRAMPercentage` 按比例设置；
6. **保关键进程 + 观测兜底**：给 sshd、监控 agent 设 `oom_score_adj = -1000`（别滥用）；容器内存使用率接监控告警，配合自动重启。

---

## 八、面试话术：主答案 + 追问链速答

### 8.1 主答案（可直接背）

> "内存不够时内核分三步走：**先回收**页缓存和 slab 这类能丢的；**不够就把匿名页换到 swap**——生产上很多中间件宁可 `swapoff`，因为换出后 RT 抖动很大；**还不行就 OOM Killer** 按 `oom_score` 挑进程杀掉。Java 进程被 OOM Killer 杀了，先 `dmesg | grep -i oom` 看内核日志，关键是分清——**是内核杀的（oom-kill）还是 JVM 自己抛的 OutOfMemoryError**：前者说明整机或容器内存真没了、元凶常在堆外（堆外内存、线程栈、页缓存），这时候加 `-Xmx` 反而死得更快；后者是 JVM 管不住堆。容器里退出码 **137（=128+9）** 基本就是被 SIGKILL 杀的，十有八九是 **cgroup 内存超限**，不是 JVM 自己的堆问题。"

### 8.2 追问链速答（一句到三句，防连珠追问）

| 追问 | 速答 |
|------|------|
| **页缓存是？** | 内核拿空闲内存缓存磁盘文件内容的区域：读文件按 4KB 页读进来复用，写文件先改页缓存、标脏页异步刷盘。`free -h` 的 buff/cache 大部分就是它；回收代价低（干净页直接丢、脏页回写后丢），所以内存紧张时第一个被回收。容器里它计入 cgroup 用量，IO 密集的容器也可能被顶到限额。 |
| **slab 是？** | 内核给小对象（dentry、inode、task_struct、socket 结构）专设的对象池：先拿整页、切成固定小块按类型缓存复用，分配快、碎片少。分可回收（SReclaimable，纯缓存）和不可回收（SUnreclaim，活着的对象）——回收只收前者；SUnreclaim 持续涨往往是内核/驱动泄漏。 |
| **容器是 Docker 容器吗？** | 不限于 Docker。Docker、containerd、Podman、K8s Pod 底层都是同一套内核能力：namespace 负责隔离（以为独占 PID/网络/文件系统），cgroup 负责限额。容器**不是虚拟机**——没有独立内核，里面的 Java 进程就是宿主机上的普通进程。 |
| **cgroup 是啥？** | Linux 内核把一组进程归组、统一设资源上限并统计用量的机制。内存上限 v1 是 `memory.limit_in_bytes`、v2 是 `memory.max`；`docker run -m 2g`、K8s 的 `limits.memory` 最终都是写它。超过上限时即使宿主机内存充足也在组内触发 OOM Killer——137 的来源。 |
| **swap 换出匿名页指的是？** | 匿名页是不对应磁盘文件的内存页（堆、栈、JVM 堆、堆外）——磁盘上没有原件，丢了就没了。内存紧张时内核把它们写进 swap 区保存、释放物理内存，叫**换出**；再访问时缺页中断从磁盘读回，叫**换入**——毫秒级磁盘读，所以 RT 抖动。 |
| **swapoff 是？** | 关闭 swap 的命令（`swapoff -a` 关全部）。执行时内核先**把 swap 里已换出的页全部读回物理内存**——内存不够时可能很慢甚至触发 OOM；永久关闭要改 `/etc/fstab`。生产中间件主动关它：宁可被 OOM 快速杀掉，不要 swap 抖动；K8s 也默认要求节点关 swap。 |
| **swap 区是？** | 磁盘上划出来专门存放换出页的空间，有 swap 分区、swap 文件两种形式（`fallocate` → `mkswap` → `swapon` 三步创建）。它是"救急仓库"不是内存：速度差几个数量级；太小没缓冲，太大可能慢性卡死。 |
| **讲讲 OOM Killer。** | 内核在回收+换出都凑不出内存时的最后手段：按 `oom_score`（主要由**匿名内存**占用决定，页缓存不扛分；可用 `oom_score_adj` 调整保护）挑进程发 SIGKILL。范围两种：整机耗尽触发全局 OOM；容器超 cgroup 限额触发组内 OOM。被杀进程没有清理机会——不抛异常、不写 hs_err、不跑 shutdown hook，日志一片空白，靠 dmesg 定案。 |
| **`dmesg \| grep -i oom` 啥意思？** | 查内核日志里 OOM 相关记录：dmesg 打印内核环形缓冲区日志，管道给 grep，-i 忽略大小写。典型两行：一行"某进程 invoked oom-killer"（**触发者**），一行"Out of memory: Killed process PID（名字）…… anon-rss:……"（**被杀者** + 实际匿名内存）。 |
| **dmesg 的 d 是？** | 没有官方定义，三种说法：display message（显示内核消息，最好记）、driver message（早期主要看驱动）、diagnostic message（诊断）。按 display message 记就行。 |
| **137（=128+9）的含义？** | 被信号终止的进程，shell 报 128 + 信号号：137 = 128+9 = 被 **SIGKILL** 杀。容器里十有八九是 cgroup 内存超限，也可能是 `kill -9`、`docker stop` 超时补杀、kubelet 驱逐——用 `docker inspect` 的 `OOMKilled` / `kubectl describe pod` 定案。 |

---

## 关联知识点

- [[OS八股]] — 主知识源：4.5 节（swap 与 OOM Killer 面试版）、6.x 信号与退出码
- [[OS热门面试题]] — Q13 主答案与「容器里 Java 为什么容易 OOM」追问
- [[OS八股-重构版]] — 第 5 章（内存管理：伙伴系统、slab、两种 OOM）、第 9 章（容器与隔离）
- [[OS 名词解释速查]] — 页缓存 / Slab / 匿名页 / swap 的定义层（本页不重复）
- [[缺页异常详解]] — swap 换入必为硬缺页
- [[DirectByteBuffer与堆外内存回收]] — 堆外内存为什么是 cgroup 超限常客
- [[StackOverflowError与OutOfMemoryError（虚拟机栈）]] — 线程栈与 OOM 的关系
