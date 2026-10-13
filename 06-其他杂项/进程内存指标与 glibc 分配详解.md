---
tags:
  - 操作系统
  - 内存管理
  - glibc
  - Linux排障
  - Java
created: 2026-09-15
updated: 2026-09-15
source: 用户整理的内存指标 / glibc / free 流程笔记，Claude 落盘并做少量补充（⚠ 标出处）
---

# 进程内存指标与 glibc 分配详解

> 一条链讲完三个问题：**RES 是什么**（VIRT/RES/SHR 怎么看）→ **free 了为什么 RES 不降**（glibc 中间商 + 空闲链表 + 内存空洞）→ **什么时候它才会降**。用户原稿为主，⚠ 标注处是本次补充/勘误。

---

## 一、RES（Resident Set Size）

**进程当前实际占用的物理内存大小。**

### 对比几个容易混淆的指标

| 指标 | 含义 |
|------|------|
| **VIRT** | 虚拟地址空间总量（包括没映射到物理内存的） |
| **RES** | 实际在物理内存中的页面大小 |
| **SHR** | RES 中与其他进程共享的部分（如共享库） |

⚠ **三条补充读法**（排障时最容易踩的坑）：

1. **被 swap 出去的页**：VIRT 不变、**RES 会掉**——RES 只数"此刻真在物理内存里"的页
2. **共享页会被重复计数**：同一个 libc 页码，100 个进程的 RES 里各算一份——**所有进程 RES 加起来大于物理内存不是 bug**。要精确分摊看 `smaps` 里的 **PSS**（按共享者数量摊薄）
3. **VIRT 高 ≠ 内存压力**：64 位程序 VIRT 天然巨大（JVM 的 Xmx 预留、每线程栈、glibc 的 arena 都记在 VIRT 里）——**看压力看 RES，看预留才看 VIRT**

> 简单记忆：**RES = OS 认为你占的物理内存。**

---

## 二、查看 VIRT / RES / SHR 的命令

### `top` / `htop`

```bash
top
```

```
PID    USER    VIRT    RES    SHR  %MEM  COMMAND
1234   root   512m    128m   20m   1.6   python
```

`htop` 更直观，可以鼠标操作，颜色区分各列。

### `ps`

```bash
ps aux
# VSZ = VIRT，RSS = RES

ps -o pid,vsz,rss,comm -p 1234
# 指定进程，指定列
```

### `/proc/[pid]/status`

```bash
cat /proc/1234/status | grep -E 'VmRSS|VmSize|VmSHR'
```

```
VmSize:   524288 kB   ← VIRT
VmRSS:    131072 kB   ← RES
VmShr:     20480 kB   ← SHR
```

⚠ **原命令有个大小写 bug**：文件里的字段名是 **`VmShr`**（不是 VmSHR），`grep` 默认区分大小写会漏匹配。改成 `grep -E 'VmSize|VmRSS|VmShr'` 或直接 `grep ^Vm /proc/1234/status`（全量 Vm 行里还能看到 **`VmHWM` = 峰值 RSS**，判断"现在高还是曾经高"很有用）。

### `/proc/[pid]/smaps`

```bash
cat /proc/1234/smaps
# 每段内存的详细信息，最细粒度
```

⚠ 进程映射多时（JVM 动辄几千段）输出爆炸，用 **`cat /proc/[pid]/smaps_rollup`** 看每段汇总后的单行总量，PSS 也在这两个文件里。

### 快速对比

| 命令 | 特点 |
|------|------|
| `top` | 实时刷新，交互方便 |
| `htop` | 更好看，需要安装 |
| `ps aux` | 脚本友好，一次性输出 |
| `/proc/status` | 精确到 kB，适合调试 |
| `/proc/smaps` | 最详细，按内存段展示 |

### 最常用

```bash
# 看某个进程
top -p 1234

# 按内存排序
top 然后按 M
```

---

## 三、glibc — 夹在代码和内核之间的中间商

**Linux 系统上最核心的 C 标准库。**

```
你的程序
    ↓
glibc          ← 在这一层
    ↓
Linux 内核
    ↓
硬件
```

> 你写的 `malloc` / `printf` / `fopen`
> **不是直接调用内核**
> 而是调用 glibc 提供的实现
> glibc 再决定要不要去找内核

### 它具体包含什么

| 功能 | 函数举例 |
|------|------|
| 内存管理 | `malloc` `free` `realloc` |
| 文件操作 | `fopen` `fread` `fwrite` |
| 字符串 | `strcpy` `strlen` `sprintf` |
| 进程 | `fork` `exec` `exit` |
| 网络 | `getaddrinfo` `socket` |
| 数学 | `sin` `cos` `sqrt` |

### 为什么 malloc 不直接找内核要内存

```
每次 malloc(8字节)
都去找内核 →  太慢了（系统调用开销大）

所以 glibc 的策略：
一次找内核要一大块
自己切小块分配
free 了先放自己链表
下次再用
→ 这就是为什么 RES 不降
```

> 简单记忆：**glibc = Linux 上 C 语言的标准工具箱**，几乎所有程序都在用它，它夹在你的代码和内核之间。

---

## 四、free 之后发生了什么：为什么 RES 纹丝不动

### 先理解"谁管内存"

```
┌─────────────┐
│   你的代码   │  malloc(100) / free(ptr)
├─────────────┤
│    glibc     │  真正的内存管理者
├─────────────┤
│   Linux内核  │  掌管物理内存
└─────────────┘
```

> **glibc 是中间商**，它从内核批发内存，再零售给你。

### glibc 怎么从内核拿内存

```bash
# 两种方式
brk()     → 把堆往上涨，拿到一大块
mmap()    → 单独映射一块
```

```
内核分配物理页面
    ↓
glibc 持有这块内存
    ↓
你 malloc() 时，glibc 从自己手里切一块给你
```

### 你 free 时发生什么

```
你以为：
free(ptr) → 内存还给OS → OS回收 → RES下降

实际上：
free(ptr) → glibc："我先收着，放进空闲链表"
                        ↓
              内核完全不知道这件事
                        ↓
              物理页面还映射着
                        ↓
              RES 没变
```

### 为什么 glibc 要这样做

```
假设你循环 malloc/free 1000次

方案A 每次都找内核：
malloc → 问内核要  ↗
free  → 还给内核  ↘  × 1000次  太慢！

方案B glibc缓存（实际做法）：
第一次  → 找内核要一大块
后续    → 自己内部切/回收
          内核只打扰了一次  快！
```

> 关键点：`free()` 只是告诉 **glibc** "我不用了"，没告诉 **内核**；内核不回收物理页，所以 RES 不降。**glibc 的空闲链表 ≠ 归还给 OS。**

---

## 五、什么时候 RES 才会降

```
情况1: mmap 分配的内存
大块内存（默认>128KB）→ glibc用mmap要的
free() 时 → glibc直接 munmap 还给内核
→ RES 下降  ✓

情况2: 手动 malloc_trim()
malloc_trim() → 告诉glibc "把空闲的还给内核"
→ glibc 调用 brk 收缩堆
→ RES 下降  ✓

情况3: 小块内存（堆上的）
free() → 进空闲链表 → RES 不变  ✗
```

⚠ **两条"其实没那么绝对"的补充**（防被追问打穿）：

1. **堆顶空闲够大会"自动 trim"**：glibc 还有个对称的阈值 **`M_TRIM_THRESHOLD`（默认也是 128KB）**——堆顶**连续**空闲超过它，free 时 glibc 会自己调 brk 收缩，不用等 `malloc_trim()`。所以情况 3 的准确说法是"free 后**不立刻**降"，堆顶大段连续空闲时它会降。
2. **128KB 的大块阈值是动态的**（glibc 2.7+）：一个 mmap 大块被 free 过一次后，glibc 会把 mmap 阈值**上调**到它的尺寸（封顶 32MB）——同一个"大块"反复申请释放时改走堆、不再反复 mmap/munmap 抖动。所以"超过 128KB 必走 mmap"是**初始值**，不是铁律；`M_MMAP_THRESHOLD` 也可显式设死。

---

## 六、内存空洞（堆顶为什么不能下移）

```
free()前：
堆: [已用][已用][已用]
                        ← 堆顶在这

free()后：
堆: [已用][空闲][已用]
                        ← 堆顶没动！
                           因为后面还有[已用]挡着
                           没法收缩
```

> 这就是**内存空洞**：就算中间那块空了，堆顶不能下移，内核不知道那块空了，RES 不变。

---

## 七、Java 侧落点

- **JVM 的 VIRT 天然巨大**：`-Xmx` 是**预留**不是占用——堆 + 每线程 1MB 栈 + 元空间 + 直接内存的虚拟映射全记在 VIRT；RES 只随页面**真被碰到**才涨。"VIRT 几十 G、RES 几个 G"是 JVM 常态，别拿 VIRT 告警。
- **GC 收的只是堆内复用，默认不急着还给 OS**（G1 会在并发周期之间逐步 uncommit，但节奏保守）——所以"老年代 full GC 之后 RES 还是高"和本文的 glibc 逻辑同源：**中间商留着自用**。
- **容器内存告警看的是 RSS/cgroup 用量**（≈ RES），不是 heap used——堆外、线程栈、页缓存都算在里面，对应卷子 Q13（OOM Killer）与 [[DirectByteBuffer与堆外内存回收]]。

---

## 面试话术

> "RES 是进程当前真占的物理内存——VIRT 只是虚拟地址空间的总盘子，共享库那类还挂在 SHR 里。free 了 RES 不降，是因为内存分两层管：小块走 brk 找 glibc 批发，free 只是还给 glibc 的空闲链表，内核压根不知道，物理页还映射着，RES 自然不动；而且堆里有个'空洞'挡着——已用的块不连续，堆顶就收不下来。真降只有三条路：这块本来就是 mmap 的大块（free 直接 munmap）、堆顶连续空闲超过 128KB 触发自动 trim、或者手动 malloc_trim。所以线上排查内存，先看 RES 再翻 /proc 的 smaps 分段看堆、so、匿名页各占多少；Java 进程还要分清楚 RES 高是堆的、还是堆外和线程栈的。"

---

## 关联知识点

- [[OS八股]] — 主文件；§4.3 用户态的堆：brk 与 mmap（本笔记是它的指标层 + 全流程展开）
- [[OS热门面试题]] — Q11（malloc/free 与 RES）、Q13（OOM Killer 先看什么指标）、Q22（系统视角 free/available 与本笔记进程视角 VIRT/RES 是两套）
- [[DirectByteBuffer与堆外内存回收]] — Java 堆外内存不吃 Xmx 但吃 RES
- [[磁盘 IO 瓶颈排查详解]] — RES 之外另一半排障视角：si/so 非零 = 换页，和"内存不够"互为印证
