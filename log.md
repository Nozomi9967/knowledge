# LLM Wiki 操作日志

> 按时间倒序追加。每次 Ingest、Query、Lint 后记录。
> 格式：`## [YYYY-MM-DD] 操作类型 | 标题`
> 快速检索：`grep "^## \[" log.md | head -10`

---

## [2026-06-28] extract | Caffeine 从面试题汇总中独立成 [[Caffeine详解]]

## [2026-06-28] query | 解释 LinkedHashMap 构造函数参数、putIfAbsent、跨结构原子性、竞态窗口

**操作：** Nozomi 在 [[Java后端开发面试题汇总]] 中对 LRU 方案的细节提问，逐条解释后抽取并发概念到独立文件。

**变更：**
- `03-Java/Java后端开发面试题汇总.md`：补充 LinkedHashMap 三参数表、removeEldestEntry 机制、putIfAbsent 解析；将跨结构原子性和竞态窗口的详细解释替换为 wikilink
- `03-Java/跨结构原子性与竞态窗口.md`：新建概念详解页，覆盖两个并发概念的完整定义、典型场景、解决方案
- `index.md`：添加 [[跨结构原子性与竞态窗口]] 条目

---

## [2026-06-28] query | for-each 遍历 ArrayList 时 remove 抛 ConcurrentModificationException 的原因与修复

**操作：** Nozomi 选取 [[Java后端开发面试题汇总]] 第 1 题，要求给出完整答案解析。

**变更：**
- `03-Java/ConcurrentModificationException 详解（for-each remove 问题）.md`：新建详解页，覆盖：
  1. **产生原因** — fail-fast + modCount 机制、Iterator 内部 expectedModCount 未同步的源码级分析
  2. **删除倒数第二个不抛异常**的陷阱说明
  3. **三种修复方案详解**：
     - `Iterator.remove()` — 原理（同步 expectedModCount）、优缺点
     - `CopyOnWriteArrayList` — 快照迭代器机制、写复制成本、适合"读多写极少"场景
     - `removeIf()` — JDK 8 批量标记 + BitSet + 一次移位 O(N)、与逐个 remove 的性能对比 O(N²)
  4. **三方案对比表格** + **面试话术**
- `Java后端开发面试题汇总.md`：第 1 题后追加 → [[ConcurrentModificationException 详解（for-each remove 问题）]]
- `index.md`：Java 基础分类下新增该页面条目

## [2026-06-27] query | DirectBuffer 是否多一次拷贝 → 补充 [[03-Java/Java基础面试题.md]]
NIO Channel/Buffer 小节第 3 点下增加折叠 callout：GC 移动对象导致堆内 I/O 需临时 DirectBuffer 中转，DirectBuffer 零拷贝 ≠ sendfile 零拷贝的易错点。

## [2026-06-24] query | ReentrantLock 是否会钉住虚拟线程 → 更新 [[06-其他杂项/虚拟线程详解.md]]
- 在「注意事项」表格后补 `> [!warning]`：JDK 24 / JEP 491 已修复 synchronized pinning，「建议用 ReentrantLock 替代」仅适用于 JDK 21~23
- 新增「为什么 ReentrantLock 不钉住」小节：synchronized 锁挂在 native 栈帧（C++ 层）→ pin；ReentrantLock 走 AQS + LockSupport.park → 可卸载

## [2026-06-22] query | 栈溢出为什么会触发 OOM，新建详解笔记

**操作：** Nozomi 在 [[jvm八股]] 第 539 行「递归调用：深度递归导致栈溢出」处追问为何栈溢出被列为 OOM 原因，要求新建笔记深入讨论。

**变更：**
- `03-Java/栈溢出导致OOM的深层机制.md`：新建详解页，覆盖 4 条 SOF→OOM 路径：
  1. **多线程并发递归** — 多线程栈总和撑爆进程内存 → `unable to create new native thread`
  2. **捕获 SOF 后未终止** — Error 对象带完整调用栈（深度万层时几百 KB / 个），频繁分配填满堆 → `Java heap space`
  3. **栈帧持堆引用** — 每帧的局部变量引用的对象不可回收，堆先满于 SOF
  4. **逃逸分析失效** — 栈上分配被迫逃逸 → 堆压力
- `03-Java/jvm八股.md`：第 539 行末尾添加 wikilink → [[栈溢出导致OOM的深层机制]]
- `index.md`：Java → JVM 分类下加入 [[栈溢出导致OOM的深层机制]] 条目

**核心结论：** SOF 本身是线程级错误，OOM 是进程级错误，但 SOF 可通过多条路径（最常见的是多线程同时递归和捕获 SOF 后不终止）间接触发 OOM。这也是面试中把"递归导致栈溢出"归入 OOM 原因的原因——它确实是 OOM 的潜在触发器。

---

## [2026-06-22] query | 静态集合内存泄漏与类卸载详解，jvm八股.md 引用

**操作：** Nozomi 在 [[jvm八股]] 第 527 行「静态集合：使用静态数据结构存储对象」处追问"为啥强调静态"，要求深入讨论并新建笔记，同时补充类卸载条件（初次回答遗漏了）。

**变更：**
- `03-Java/静态集合内存泄漏与类卸载详解.md`：新建详解页，包含 GC Roots 视角分析（static 变量是 GC Root）、生命周期对比（实例变量 vs 静态变量）、类卸载三个必要条件及分析（Bootstrap/App/自定义 ClassLoader 三种场景）、Metaspace OOM 关联、面试话术
- `03-Java/jvm八股.md`：第 527 行末尾添加 wikilink → [[静态集合内存泄漏与类卸载详解]]
- `index.md`：在 Java → JVM 分类下加入 [[静态集合内存泄漏与类卸载详解]] 条目

**核心结论：**
1. static 变量是 GC Root，生命周期绑定类生命周期，类不卸载则集合永不释放，这是它成为内存泄漏头号危险的根本原因。
2. 类卸载需同时满足三个条件（无实例 → 无 ClassLoader → 无 Class 引用），但在普通应用中几乎不触发——只有自定义 ClassLoader 场景（Tomcat 热部署、OSGI、DevTools）才能真正卸载。
3. 类卸载不在对象不可达时立即发生，要等待 Full GC / 并发类卸载周期。

---

## [2026-06-21] query | SCAN 查大 key 详解并在 redis八股.md 引用

**操作：** Nozomi 在 [[redis八股]] 第 934 行「使用SCAN命令查找大key」小节要求讲清楚具体操作步骤，要求新建独立详解页并在此处引用。

**变更：**
- `01-Redis/SCAN命令查找大key详解.md`：新建详解页，包含 SCAN 与 KEYS 对比、完整语法（cursor / MATCH / COUNT / TYPE）、4 步排查流程（SCAN 扫描 → TYPE 判断 → STRLEN/MEMORY USAGE 测大小 → sort -rn 排序）、bash 循环脚本与 `--scan` 封装、生产注意事项（从节点执行、`-i 0.01` 控制节奏、SCAN 的两个坑）、5 个面试高频追问（COUNT 是否硬限制、能否保证完整遍历、为何不漏、LLEN 能否判断大 key、MEMORY USAGE 返回什么）
- `01-Redis/redis八股.md`：第 934 行标题改为「使用SCAN命令查找大key — 详见 [[SCAN命令查找大key详解]]」
- `index.md`：在 Redis → 综合分类下加入 `[[SCAN命令查找大key详解]]` 条目

**核心结论：** SCAN 是基于游标的渐进式迭代器，单次 O(1)，生产可用；`LLEN/HLEN/SCARD/ZCARD` 只返回元素个数，乘以平均大小能粗估但误差大；`MEMORY USAGE` 返回真实字节数（含编码 overhead、ziplist 压缩等），4.0+ 可用，但单次调用开销不小，建议在从节点或低峰期跑。

---

## [2026-06-20] query | 讲一下 listpack 并在 quicklist 节点页引用

**操作：** Nozomi 在 [[quicklist的内部节点]] 页面看到"7.0+ 内部节点用 listpack"但缺少对 listpack 的独立解释，要求新建独立笔记并在此处引用。

**变更：**
- `01-Redis/listpack.md`：新建详解页，包含内存布局、entry 结构（encoding + content + len）、与 ziplist 的核心差异（自描述 vs 反向依赖）、编码类型、优势、局限、7.0 各场景应用、与 ziplist 对比表、面试话术
- `index.md`：在 Redis → 数据结构分类下加入 `[[listpack]]` 条目

**核心结论：** listpack 的 entry 结构从 `prevlen + encoding + data` 改成 `encoding + content + len`——**prevlen 存前一个 entry 的长度（反向依赖，会触发连锁更新），len 存当前 entry 自己的长度（自描述，互不依赖）**。这是 listpack 彻底消除 ziplist 连锁更新的根本原因，代价是无法 O(1) 反向遍历。

---

## [2026-06-18] query | 为什么 >=、<=、BETWEEN、like 前缀匹配不会停止最左匹配

**操作：** Nozomi 在 [[mysql_八股整理]] 第 109 行看到"对于 >=、<=、BETWEEN、like 前缀匹配的范围查询，并不会停止匹配"这条结论，要求新建独立笔记深入解释其原理。

**变更：**
- `02-MySQL/最左前缀匹配范围查询不停止的原因.md`：新建详解页，包含原理（B+Tree 排序结构、起点是否明确）、场景对比表、叶子节点示意、面试话术
- `index.md`：在 MySQL → 查询优化分类下加入 `[[最左前缀匹配范围查询不停止的原因]]` 条目

**核心结论：** `>` / `<` 是开区间，起点模糊，导致后续字段全局无序；`>=`、`<=`、`BETWEEN`、`LIKE 'xxx%'` 是闭区间，能精确定位起点，等价于"先 = 定位 + 向右扫"，起点之后一段字段值相等，后续字段仍可利用索引有序性。

---

## [2026-06-18] query | Java 基础合并：java基础.md → Java基础面试题.md

**操作：** Nozomi 要求将 `03-Java/java基础.md` 的内容作为补充章节追加到 `03-Java/Java基础面试题.md`（小林 coding 高频面试题长文）。

**内容核对结果：**
- `Java基础面试题.md`（2629 行）— 小林 coding 系列，覆盖概念/数据类型/面向对象/关键字/泛型/反射/注解/异常/Object/新特性/序列化/设计模式/I-O/其他
- `java基础.md`（394 行）— 早前整理的容器与 IO 章节（流分类、BIO/NIO/AIO、Files、容器、Collection vs Collections、数组与 List 转换）
- 两文件内容**几乎不重叠**：`java基础.md` 集中在 IO 流和集合容器框架，而 `Java基础面试题.md` 的 IO 章节只覆盖 NIO 网络编程高并发，无重叠

**变更：**
- `Java基础面试题.md`：补全 YAML frontmatter（updated 字段、tags 修正为 `java`/`基础`/`面试`/`io`/`容器`/`集合`）
- `Java基础面试题.md`：在文件末尾追加「容器与集合框架」大章节，包含 6 个子节：
  - Java 中流分为几种
  - BIO、NIO、AIO 有什么区别
  - Files 的常用方法都有哪些
  - java 容器都有哪些
  - Collection 和 Collections 有什么区别
  - 如何实现数组和 List 之间的转换
- `Java基础面试题.md`：删除文件末尾「登录后查看评论」「公众号海报」等无关尾部内容
- `Java基础面试题.md`：末尾新增「关联知识点」小节，链接到 [[枚举]]、[[sealed class]]、[[jvm八股]]、[[Java并发编程面试题]] 等
- `index.md`：删除 `[[java基础]]` 条目（已合并），更新「最后更新」日期
- 删除 `03-Java/java基础.md`（已被合并）

**原则遵循：**
- 按 CLAUDE.md「不动原有内容」原则，只追加补充，不修改小林 coding 原文
- 按 CLAUDE.md「页面至少被另一个页面链接」原则，新增「关联知识点」小节
- 章节层级统一为「## 容器与集合框架（java基础补充）」包裹子节，避免与原 I-O 章节混淆

---

## [2026-06-18] query | MySQL 综合页面合并：mysql八股.md → mysql_八股整理.md

**操作：** Nozomi 要求合并 `02-MySQL/mysql_八股整理.md` 和 `02-MySQL/mysql八股.md`。

**内容核对结果：**
- 两文件内容高度重复，`mysql_八股整理.md` 是系统性整理版（带分章节、加粗重点、规范引用）
- `mysql八股.md` 是早期草稿（无 frontmatter、无章节标题、格式零散）
- `mysql_八股整理.md` 已包含 `mysql八股.md` 的全部核心内容（B+Tree、回表、覆盖索引、联合索引、MVCC、锁、日志、主从复制等）
- 差异点：`mysql_八股整理.md` 多了「MVCC 幻读补充」「字节面试死锁」「两阶段提交细节」等深度整理

**变更：**
- `mysql_八股整理.md`：补全 YAML frontmatter（tags / created / updated）
- `mysql_八股整理.md`：扩充「关联知识点」小节，加入 [[MySQL八股挑战卷]]、[[MySQL 八股测验卷（事务 · 锁 · 日志 · 主从复制）]]、以及全部事务/锁相关子页面
- `index.md`：删除 [[mysql八股]] 条目（其内容已合并到 [[mysql_八股整理]]）
- `index.md`：更新「最后更新」日期
- 删除 `02-MySQL/mysql八股.md`（已被合并）

**原则遵循：**
- 按 CLAUDE.md「不动原有内容」原则，只补充不删改
- 按 CLAUDE.md「页面至少被另一个页面链接」原则，关联知识点小节包含全部子页面链接

---

## [2026-06-16] query | 哑节点与双向链表深度归档 → [[AQS 详解]] 9.4 / 9.5

**操作：** Nozomi 提供"📌 AQS 核心设计归档：哑节点与双向链表并发谜团"长文（哑节点双重作用：统一入队+统一出队+消灭队首边界特例 / 双向链表 next 短暂为 null 的三大时序步骤 / 致命时间差 / 对 cancelAcquire 致命影响 / prev 指针绝对可靠 / 核心归纳金句）。要求：内容并入当前笔记。

**编排位置：**
- 9.4 `head` 为什么是哑节点 → 扩展为 9.4.1-9.4.3 三个子节
  - **9.4.1** 核心原因拆解（统一入队 + 统一出队）
  - **9.4.2** 深度理解：消灭队首边界特例——`> [!abstract]` 对照表（没有哑节点 vs 引入哑节点），点出"`prev` 全部不为 null"的核心代码红利
  - **9.4.3** 面试话术模板（**结构设计换取代码简洁性**）
- 9.5 为什么 CLH 队列是双向的 → 扩展为 9.5.1-9.5.6 六个子节
  - **9.5.1** 缺陷诞生的三大时序步骤（含 enq 三行源码）
  - **9.5.2** 致命时间差——`> [!danger]` 折叠标注，三步时序展开，"通路 vs 断路"对照
  - **9.5.3** 对 cancelAcquire / unparkSuccessor 致命影响——引出 `> [!warning]` 折叠标注点透"必须从 tail 往前"
  - **9.5.4** 安全救赎：为什么 prev 指针绝对可靠——Doug Lea 的设计铁律
  - **9.5.5** 核心归纳金句——`> [!abstract]` 折叠，双指针对仗（哑节点消灭队首特例 / 双向链表解决 next 断路）
  - **9.5.6** 面试话术模板（**正确性而非性能**）

**格式遵循：**
- 按 CLAUDE.md「技术点解释规范」——标注内不放代码块/表格，复杂内容写标注外
- 5 种标注类型各司其职：`info` 概念前置 / `abstract` 核心归纳 / `warning` 必须遵守的规则 / `danger` 致命时序 / `success`（6.4 节）终极解法
- 6.4 节归档手册的标注体系（info/danger/warning/success/abstract）在 9.5 节复用，形成统一风格

**未修改：** 9.4 原三行表格、9.5 原"取消节点需要 prev"一行核心句全部保留原样，仅在其后追加新内容。

**与第 1 节「1.2 CLH 同步队列」的呼应：** 1.2 节表里说"双向链表：取消节点/唤醒前驱时需要 prev 倒着找"——9.5.1-9.5.6 把这句话展开为完整的"为什么 next 会断、为什么 prev 不会断、为什么必须从 tail 往前"三层论据。

---

## [2026-06-16] query | 详解 `acquireQueued` 死循环设计 → 写入 [[AQS 详解]] 9.6

**操作：** Nozomi 选中 9.6 节"`acquireQueued` 为什么用死循环？"，问"啥意思，详细解释"。先在聊天里详细展开，Nozomi 进一步要求"你给我写进去啊倒是"。

**变更：**
- 9.6 节从 4 行 bullet 扩展为完整 6 个子节：
  - **9.6.1** 前驱可能不是 head——`unparkSuccessor` 不一定来自 head，CANCELLED 节点也会叫醒后继
  - **9.6.2** `tryAcquire` 可能失败——非公平锁下，外部新线程可能在唤醒线程被调度前抢到锁
  - **9.6.3** 伪唤醒——JLS 允许 OS 偶发
  - **9.6.4** 隐藏的第 4 条——cancelAcquire 分支 2 亲自唤醒场景，链回 6.4 节"队列自愈"
  - **9.6.5** 错误设想代码 + 3 个致命问题表格
  - **9.6.6** 面试话术模板（**用"重检"对抗"状态过期"，用"循环"对抗"不确定性"**）
- 9.6 节顶部加 `acquireQueued` 源码片段（含 `for(;;)`、`predecessor`、`tryAcquire`、`shouldParkAfterFailedAcquire`、`parkAndCheckInterrupt`、`cancelAcquire` 关键调用点）
- 9.6 开头加一句"线程从 park 醒来时，它观察到的所有状态都是'过去时'"——把核心金句提到最前

**互链：**
- 9.6.1 → 6.4 节（cancelAcquire 终极解法）
- 9.6.2 → 3.2.2 节（非公平锁插队）
- 9.6.3 → 4.5 节（伪唤醒 warning）
- 9.6.4 → 6.4 节（队列自愈）

**与原 4 行 bullet 的关系：** 原文 4 行 bullet 是 9.6.1-9.6.3 三个子节的核心，原文"唯一安全做法"一句拆为 9.6.5（错误设想）+ 9.6.6（话术）。

---

## [2026-06-16] query | 详解 `acquireQueued` 死循环设计 → 聊天回复

**操作：** Nozomi 选中 9.6 节"`acquireQueued` 为什么用死循环？"，问"啥意思，详细解释"。

**回复要点：**
- **4 个 bullet 逐条展开**：
  1. 前驱不是 head——跳过被取消的节点，`unparkSuccessor` 路径不一定来自 head
  2. tryAcquire 可能失败——非公平锁下，外部新线程可能抢在唤醒线程前面
  3. 伪唤醒——JLS 允许 OS 偶发
  4. 隐藏路径——`cancelAcquire` 分支 2 亲自唤醒场景（6.4 节）
- **错误设想代码**：用 `if + park + return` 演示为什么死循环是唯一安全做法（3 个致命问题：不知道为何醒来、不知道抢锁成不成立、不知道前驱还是不是 head）
- **面试话术模板**：状态空间不收敛、三个不确定性、用"重检"对抗不用"记忆"对抗
- 引用：[[AQS 详解]] 4.3 / 4.5 / 6.4 / 3.2.2 / [[Condition 详解]]

**未新建页面：** 4 个 bullet 已在 9.6 节罗列，本次为展开解释 + 错误设想 + 面试话术，深度适合直接在聊天里消化，无需独立页面。已在 `log.md` 追加记录。

---

## [2026-06-16] query | cancelAcquire 深度归档 → [[AQS 详解]] 6.1-6.4

**操作：** Nozomi 提供"📌 AQS cancelAcquire 核心归档手册"长文（含傀儡 Head、release 执行者、核心分支拆解、ws<=0 含义、为什么前驱不是 head 静默断开、为什么前驱是 head 必须亲自唤醒、致命死锁时序、AQS 终极解法、核心总结金句）。要求：内容不动、只调格式、找到合理编排位置并入 [[AQS 详解]]。

**编排位置：** 第六节 `cancelAcquire` 源码之下，扩展为 6.1-6.4 共 4 个子节。

**结构设计：**
- **6.1 核心概念前置**——两个 `> [!info]` 折叠标注：傀儡 Head 本质、release 执行者。不放正文里，做阅读 cancelAcquire 源码的"认知前提"
- **6.2 核心分支拆解**——粘贴原文核心跳线代码 + 两个子小节（6.2.1 ws<=0 含义、6.2.2 分支 1 静默断开原理）
- **6.3 深度死磕：为什么前驱是 head 必须亲自唤醒**——三个子小节：6.3.1 错误设想（`> [!warning]` 折叠）、6.3.2 致命死锁时序（`> [!danger]` 折叠，5 步时序展开）、6.3.3 AQS 终极解法（`> [!success]` 折叠）
- **6.4 一句话总览**——`> [!abstract]` 折叠标注，把"入队从后往前找 / 出队取消紧跟 head 必须亲自唤醒"两条金句对仗起来收尾

**格式遵循：**
- 按 CLAUDE.md「技术点解释规范」——标注内不放代码块/表格，复杂内容写标注外
- `> [!info]` 用于"概念前置"（技术名 + 解释），`> [!warning]` 用于"错误设想"，`> [!danger]` 用于"致命死锁时序"，`> [!success]` 用于"终极解法"，`> [!abstract]` 用于"一句话总览"
- 所有折叠标注默认折叠，避免打断源码阅读节奏

**未修改：** 第六节原源码块、`**取消的语义**` 三行 bullet、`**waitStatus > 0 = CANCELLED**` 强调句全部保留原样，仅在其后追加新内容。

---

## [2026-06-16] query | `waitStatus <= 0` 改用 [!abstract] 标注 → [[AQS 详解]] 5.1

**操作：** Nozomi 吐槽 5.1 节"从后往前找"后面贴的"### 1. `waitStatus <= 0` 到底过滤掉了什么？"一坨列表 + 段落太重，要求改用 Obsidian Callout 标注（按 CLAUDE.md「技术点解释规范」——标注内不放代码块/表格，复杂内容写标注外；但这次是单点金句，适合 `[!abstract]` 折叠标注）。

**变更：**
- 把列表 + 段落合并成一个 `> [!abstract]` 折叠标注，标题直接用问题本身"waitStatus <= 0 到底过滤掉了什么"
- 点出"唯一大于 0 的就是 CANCELLED"这个核心事实，把"过滤掉的就是 CANCELLED"一句说透
- 末尾加一句"这个判断模式（waitStatus > 0 ⇔ CANCELLED）遍布整个 AQS 源码，是阅读 AQS 的钥匙"——把单点知识升华为通用规律
- 标注内不放代码块/表格，遵循 CLAUDE.md 规范

---

## [2026-06-16] query | 优化"park 醒来三方式"表格 → [[AQS 详解]]

**操作：** Nozomi 吐槽 4.5 节「`LockSupport.park` 醒来方式」表设计混乱（"谁 unpark / interrupt"列把来源和去向混在一起），要求改清晰。

**变更：**
- 2 列表 → 3 列表：**原因 / 谁触发 / 醒来后去哪里**——三步链条完整
- 关键修正：把"伪唤醒"的触发方明确写为"**无外力**"——这是伪唤醒的本质（没有 unpark、没有 interrupt，就是 JLS 允许的 OS/CPU 偶发唤醒）
- 补全「醒来后去哪里」：unpark 走 `acquireQueued` 重抢锁，interrupt 走 `cancelAcquire` 取消节点，伪唤醒返回 false 正常重抢
- 加 `> [!warning]` 折叠标注：JLS 明文允许伪唤醒，**所有 park/await 都必须包在循环里**（`acquireQueued` 的 `for(;;)` 天然重检，Condition 同理见 [[Condition 详解]]）

---

## [2026-06-16] query | 解释"裸 CAS 抢锁"术语

**操作：** Nozomi 选中 [[AQS 详解]] 4.1 节「裸 CAS 抢锁——非公平锁允许插队」一栏，问这个词是啥意思。

**回复要点：**
- **裸** = 光秃秃的 `compareAndSetState(0, 1)`，不带 `tryAcquire` 模板、不带 `hasQueuedPredecessors()` 公平性检查、不判可重入、不看队列
- **非公平来源** = 这步允许新线程直接和队尾等待者抢锁，是 [[ReentrantLock详解]] 非公平锁"插队"现象的根因
- 用代码对比表列出"裸 CAS vs 走 acquire"在 4 个维度的差异
- 给出一句话面试话术模板
- 引用：[[AQS 详解]]（4.1 节流程表 + 4.1.1 子流程概览）、[[ReentrantLock详解]]（非公平锁机制）

**未新建页面：** 术语解释已在 [[AQS 详解]] 4.1 节的流程表 + 4.1.1 概览表中涵盖，无新分析/对比需要沉淀。

---

## [2026-06-15] query | 补充 AQS 共享/独占模式详解 → [[AQS 详解]]

**操作：** Nozomi 指出 [[AQS 详解]] 3.2 节「共享模式 vs 独占模式」只给了对比表，没有展开介绍两种模式本身是什么。补充 6 个子节——概念定义、Node mode 标记、源码级差异表、通俗类比、面试话术。

**变更：**
- 在 [[AQS 详解]] 3.2 节下追加 3.2.1–3.2.6 共 6 个子节：
  - 3.2.1 独占模式：Mutex 语义、owner 明确、可重入、tryAcquire 返回 boolean
  - 3.2.2 共享模式：Semaphore 语义、state=许可证、tryAcquireShared 返回 int、链式唤醒
  - 3.2.3 Node 模式标记（EXCLUSIVE/SHARED 共存于 CLH 队列）
  - 3.2.4 源码级差异对照表（10 个维度）
  - 3.2.5 银行大堂通俗类比（VIP 窗口=独占、柜台=共享、叫号机=CountDownLatch）
  - 3.2.6 面试话术模板

**保持不动：** 原 3.2 节对比表保留，新增内容接在表后。

---

## [2026-06-15] query | 整理独占锁非公平模式机制解析 → [[AQS 详解]] 3.2.2

**操作：** Nozomi 要求把"独占锁非公平模式下的唤醒与抢锁机制深度解析"整理进 [[AQS 详解]]，并清理掉残留在文件中间、被错误插入的二、三级标题块（"AQS 独占锁获取失败后的核心处理流程"和它下属的 1-4 节标题）。

**变更：**
- 在 [[AQS 详解]] 新增 3.2.2 节「独占锁非公平模式下的唤醒与抢锁机制（深度解析）」，4 个子节：
  - 3.2.2.1 队列内部秩序：单一唤醒 + 精准定位 + 绝对静默三步走，澄清"一山不容二虎"
  - 3.2.2.2 非公平的"插队"本质：拆解 NonfairSync.lock 源码两条路径，附时序展开图和两类抢锁者对比表
  - 3.2.2.3 公平 vs 非公平锁博弈对照表
  - 3.2.2.4 面试话术（整合三段："一山不容二虎" + "非公平=外部新线程" + "两类抢锁者博弈"）
- 修复章节编号错位：原 3.2.2（共享模式定义）→ 3.2.3，3.2.3（Node mode）→ 3.2.4，3.2.4（差异表）→ 3.2.5，3.2.5（类比）→ 3.2.6，3.2.6（面试话术）→ 3.2.7
- 修复 3.2.3 callout 块的断裂行（原 linter 把"**Semaphore（信号量）语义**"挤到 callout 外面，已合并回 callout）
- 删除冗余的「AQS 独占锁获取失败后的核心处理流程」章节——其中"入队 / 局部自旋 / park / 唤醒"的四步拆解与 4.1.1 概览表 + 4.2-4.5 各小节内容完全重复

---

## [2026-06-15] query | 调整 [[AQS 详解]] 4.1 节结构 → 加锁子流程概览表

**操作：** Nozomi 指出 4.1 节「完整四步流程」只有一张步骤表，缺少对**获取失败后**子流程的整体导览。把 4.1 节调整为两层结构。

**变更：**
- 在 [[AQS 详解]] 4.1 节下追加 4.1.1 子节「获取失败后的核心子流程概览」：4 列对照表（阶段 / 涉及方法 / 核心问题 / 对应小节），覆盖入队 → 抢锁+自旋 → 状态信号量 → 真挂起 4 个阶段
- 原 4.1 节四步流程表保留，改为「完整加锁流程」标题
- 用 [[#4.2 ...|4.2]] 这种锚点 wikilink 让概览表直接跳转到 4.2–4.5 各小节
- 末尾加一句"整体设计哲学"总结，把 AQS 兼顾吞吐和公平性的核心思想点透

---

## [2026-06-15] query | 并发安全专题出卷 → [[试题-Java并发-并发安全]] + [[答案-Java并发-并发安全]]

**操作：** Nozomi 要求按"出卷准则"出卷，范围 [[Java并发编程面试题]] 第二大节「## 并发安全」（第 1082 行起）。出卷准则要求"不要生成答案"——但 Nozomi 明确要求"同时生成答案"，本次按显式要求处理。

**变更：**
- 创建 [[试题-Java并发-并发安全]]：25 道简答题，按 6 大板块组织——JUC 工具类与并发容器（1-5）、synchronized 体系（6-11）、ReentrantLock 与 AQS（12-16）、CAS 与原子类（17-20）、volatile 与内存可见性（21-23）、ThreadLocal 与死锁（24-25）
- 创建 [[答案-Java并发-并发安全]]：25 道题对应参考答案，每道题遵循"批卷准则"结构——问题描述（考察点 + 建议回答顺序 + 进阶追问） + `> [!abstract]-` 折叠参考答案（定义/对比表/原理/代码/一句话记忆口诀）
- 答案侧重点：每道题点出"面试官会追问什么"而非"标准答案"——比如 Q3 标注"Semaphore 和 ReentrantLock 在 AQS 共享/独占模式层面的区别"

**出卷维度覆盖：**
- 概念对比：JUC 分类 / CountDownLatch vs CyclicBarrier / 公平 vs 非公平 / 悲观 vs 乐观 / volatile vs synchronized
- 原理机制：synchronized 锁升级 / AQS 三要素 / 模板方法模式 / CAS ABA / ThreadLocal 内存泄漏
- 源码细节：Mark Word 标志位 / monitorenter / `_owner` / `hasQueuedPredecessors()` / 4 种内存屏障
- 实战陷阱：`i++` 加 volatile 也不安全 / 线程池 + ThreadLocal 内存泄漏放大 / CAS 自旋烧 CPU

---

## [2026-06-14] query | 优化"控制并发强度"措辞

**操作：** Nozomi 指出 [[Java并发编程面试题]] 第 2563 行"控制并发强度"小节表述不准，要求优化。

**变更：**
- 标题改：**控制并发强度，避免资源耗尽** → **并发可控，资源有上限**
- 措辞改：用"`maximumPoolSize` 硬上限 + 有界队列积压上限 + 拒绝策略安全网"三件套替换笼统说法；用 20 线程 / 200 队列的具体数字把"有界"具象化
- 对比改：把"三个线程"对比从"要么 A 要么 B"改为"无安全网 / 不可观测"，突出**有界 vs 无界**这个核心矛盾
- 关键金句下沉：尾部用加粗点出"线程池把'无界并发'变成了'有界并发 + 拒绝兜底'"——这句可以背下来当面试开场

---

## [2026-06-14] query | 线程池懒加载误区 → 双页面同步标注

**操作：** Nozomi 提供"线程池 ≠ 提前创建好所有线程"完整辨析，要求融入知识库。

**变更：**
- 更新 [[线程池]]：新增「十、线程池是懒加载的」板块，含 5 个核心线程逐个创建的演进图、`prestartAllCoreThreads` / `prestartCoreThread` 预热 API、冷启动 RT 敏感场景的适用性、完整提交流程图
- 更新 [[Java并发编程面试题]]：「线程池工作原理」段落（第 2403 行附近）末尾加 `> [!warning]` 折叠标注——按 CLAUDE.md 规范，warning 类型用于"误区/反直觉点"。原"任务执行流程"代码块原样保留，未做精简
- 互链：[[线程池]] 第十节末尾用 > 引用块反链回 [[Java并发编程面试题]] 同步标注点

---

## [2026-06-14] query | IO 密集型线程数公式 → 沉淀到 [[线程池]]

**操作：** Nozomi 提供完整的"IO 密集型线程数公式 + 生命周期图 + 代码示例"片段，要求融入知识库。

**变更：**
- 更新 [[线程池]]：新增「九、为什么 IO 密集型任务线程数可以多些」板块，含核心原因（线程等待 IO 不占 CPU）、CPU/IO 密集型线程数公式、`N × (1 + W/C)` 推导（8 核 + 90%IO 等于 80 线程）、`ThreadPoolExecutor` 8 核 ×2 示例代码、面试话术
- 更新 [[Java并发编程面试题]]：第 2344 行 `ThreadPoolExecutor` 处加 `> [!info]` 折叠标注，按 CLAUDE.md 规范"标注标题 = 技术名 — 英文全称（中文翻译）"，指向 [[线程池]] 详解
- 未新建独立文件：内容已归属 [[线程池]] 第九节，与第六节"线程池参数如何设置"互补

---

## [2026-06-13] query | DelayQueue 独立详解 → 生成 [[DelayQueue详解]]

**操作：** Nozomi 确认创建 DelayQueue 详解。覆盖 Delayed 接口 + leader-follower 优化。

**变更：**
- 创建 [[DelayQueue详解]]：覆盖八大板块（什么是 DelayQueue / Delayed 接口完整实现 / PriorityQueue + ReentrantLock + Condition 原理 / leader-follower 模式 / 4 种使用场景 / 与 Timer / ScheduledExecutorService 对比 / ScheduledThreadPoolExecutor 关系 / 7 个面试踩坑 / 面试话术）。含 `offer` 自己成队首时清 leader、`take` 双重 await（leader 等到期 / follower 无限等）的精妙设计
- 更新 [[阻塞队列详解]]：「关联知识点」段 [[DelayQueue详解]] 条目（之前是 stub）更新为「已建」状态
- 更新 [[index.md]]：Java 并发分类新增 [[DelayQueue详解]] 条目

---

## [2026-06-13] query | CyclicBarrier 独立详解 → 生成 [[CyclicBarrier详解]]

**操作：** Nozomi 确认创建 CyclicBarrier 详解。对比 CountDownLatch。

**变更：**
- 创建 [[CyclicBarrier详解]]：覆盖九大板块（什么是 CyclicBarrier / API + 范式 / 4 种使用场景 / ReentrantLock + Condition + Generation 实现 / 完整可运行示例 / 与 CountDownLatch 全面对比 / 与 Phaser 简述 / 7 个面试踩坑 / 面试话术）。含 `dowait` 源码中 `g != generation` 代际切换的精妙设计、`nextGeneration` 三步（signalAll + 重置 count + new Generation）、为什么 CyclicBarrier 不用 AQS 的设计哲学
- 更新 [[CountDownLatch详解]]：「关联知识点」段 [[CyclicBarrier详解]] 条目（之前是 stub）更新为「已建」状态
- 更新 [[Java并发编程面试题]]：「CyclicBarrier 段」末尾加 info callout 指向 [[CyclicBarrier详解]]
- 更新 [[index.md]]：Java 并发分类新增 [[CyclicBarrier详解]] 条目

---

## [2026-06-13] query | 阻塞队列独立详解 → 生成 [[阻塞队列详解]]

**操作：** Nozomi 确认创建阻塞队列详解。覆盖 BlockingQueue 五大实现对比 + Condition 应用 + 线程池选型。

**变更：**
- 创建 [[阻塞队列详解]]：覆盖十二大板块（什么是阻塞队列 / 五大实现全景 / ArrayBlockingQueue / LinkedBlockingQueue / SynchronousQueue / PriorityBlockingQueue / DelayQueue / 四大实现对比表 / 线程池工作队列选择 / Condition 应用 / 典型场景 / 面试踩坑 / 面试话术）。含环形数组 ASCII 图、双锁吞吐提升原理、阿里规约 OOM 避坑、DelayQueue Delayed 接口示例
- 更新 [[Condition 详解]]：「关联知识点」段 [[阻塞队列详解]] 条目（之前是 stub）更新为「已建」状态
- 更新 [[Java并发编程面试题]]：「BlockingQueue 段」末尾加 info callout 指向 [[阻塞队列详解]]
- 更新 [[index.md]]：Java 并发分类新增 [[阻塞队列详解]] 条目

---

## [2026-06-13] query | CountDownLatch 独立详解 → 生成 [[CountDownLatch详解]]

**操作：** Nozomi 确认创建 CountDownLatch 详解。对比 Semaphore / CyclicBarrier。

**变更：**
- 创建 [[CountDownLatch详解]]：覆盖九大板块（什么是 CountDownLatch / 4 种使用场景 / 共享模式 AQS 原理 / 完整可运行示例 / 与 CyclicBarrier 全面对比 / 与 Semaphore 全面对比 / 7 个面试踩坑 / 面试话术 / 关联知识点）。含 `tryAcquireShared` 一行精华（`return (state==0) ? 1 : -1`）、`tryReleaseShared` 防负数精妙设计、与 Semaphore 「可累加 vs 一次性」对比
- 更新 [[AQS 详解]]：「关联知识点」段 [[CountDownLatch详解]] 条目（之前是 stub）更新为「已建」状态
- 更新 [[Java并发编程面试题]]：「CountDownLatch 段」末尾加 info callout 指向 [[CountDownLatch详解]]，修复了主文件中多余右大括号
- 更新 [[index.md]]：Java 并发分类新增 [[CountDownLatch详解]] 条目

---

## [2026-06-13] query | Semaphore 独立详解 → 生成 [[Semaphore详解]]

**操作：** Nozomi 确认创建 Semaphore 详解。覆盖共享模式 + 链式唤醒 + 许可证机制。

**变更：**
- 创建 [[Semaphore详解]]：覆盖九大板块（什么是 Semaphore / 4 种使用场景 / 共享模式 AQS 原理 / 完整可运行示例 / 与 CountDownLatch 全面对比 / 共享 vs 独占模式一览 / 7 个面试踩坑 / 典型使用场景汇总 / 面试话术）。含 `setHeadAndPropagate` 链式唤醒原理、`doReleaseShared` PROPAGATE 状态传播、`acquire(n)` 原子拿多个许可
- 更新 [[AQS 详解]]：「关联知识点」段 [[Semaphore详解]] 条目（之前是 stub）更新为「已建」状态
- 更新 [[Java并发编程面试题]]：「Semaphore 段」末尾加 info callout 指向 [[Semaphore详解]]
- 更新 [[index.md]]：Java 并发分类新增 [[Semaphore详解]] 条目

---

## [2026-06-13] query | AQS 独立详解 → 生成 [[AQS 详解]]

**操作：** Nozomi 问「CLH 是啥」「`tryAcquire` 在干嘛」，并确认要把 AQS 拆为独立详解文件（粒度：03-Java/ 下新建；深度：与 ReentrantLock 详解同等）。

**变更：**
- 创建 [[AQS 详解]]：覆盖十一大板块（什么是 AQS / 三要素 / 对 CLH 的两个关键改造 / 类结构与模板方法 / `acquire` 全流程源码 / `release` 源码 / `cancelAcquire` 源码 / 共享模式 + ConditionObject 简述 / 与 synchronized 对比 / 面试高频踩坑 / 典型子类一览 / 面试话术模板）。含 CLH 双向链表 ASCII 图、waitStatus 四态表、addWaiter CAS 队尾源码、acquireQueued 死循环、shouldParkAfterFailedAcquire 状态机、unparkSuccessor 从后往前找的精妙设计
- 更新 [[ReentrantLock详解]]：第 4 节开头加 callout「完整讲解已拆分」并指向 [[AQS 详解]]，4.1 节末尾加 CLH 详情指引；末尾「关联知识点」[[AQS 详解]] 条目加详细描述
- 更新 [[Condition 详解]]：「关联知识点」段已有 [[AQS 详解]] 链接（创建 Condition 详解时已预埋），无需修改
- 更新 [[Java并发编程面试题]]：「介绍一下 AQS」节末尾加 info callout 指向 [[AQS 详解]]
- 更新 [[index.md]]：Java 并发分类新增 [[AQS 详解]] 条目；顶部「最后更新」日期保持 2026-06-13

---

## [2026-06-13] query | Condition 独立详解 → 生成 [[Condition 详解]]

**操作：** Nozomi 询问 `ReentrantLock.newCondition()` 含义，并确认要把 Condition 拆为独立详解文件（粒度：独立 md；深度：与 ReentrantLock 详解同等）。

**变更：**
- 创建 [[Condition 详解]]：覆盖八大板块（API 范式 / 为什么需要 Condition / 有界阻塞队列可运行示例 / AQS ConditionObject 源码 / 与 wait/notify 全面对比 / 高频踩坑 / 典型场景 / 面试话术模板）。含 `await()` 源码四步流程、`signal()` 节点迁移图、节点状态 CONDITION→0 转换、虚假唤醒 `while` 防坑
- 更新 [[ReentrantLock详解]]：第 3 节开头加 callout「完整讲解已拆分」并指向 [[Condition 详解]]；末尾「关联知识点」新增 [[Condition 详解]]
- 更新 [[Java并发编程面试题]]：「多个条件变量」段的 info callout 末尾追加 `Condition 独立详解见 [[Condition 详解]]`
- 更新 [[index.md]]：Java 并发分类新增 [[Condition 详解]] 条目；顶部「最后更新」日期改为 2026-06-13

---

## [2026-06-13] query | ReentrantLock 详细讲解 → 生成 [[ReentrantLock详解]]

**操作：** 根据 Nozomi 的反馈，将 [[Java并发编程面试题]] 中 ReentrantLock 的提纲式介绍拆分为独立详解文件，并在主文件做引用。

**变更：**
- 创建 [[ReentrantLock详解]]：覆盖八大板块（API 范式 / 四大特性详解 / 多 Condition / AQS 源码 / synchronized 对比 / 高频踩坑 / 场景选择 / 面试话术），含可运行的可中断性示例代码
- 更新 [[Java并发编程面试题]]：「可中断性」段落末尾加 `详见 [[ReentrantLock 详解]]` 引用；「可重入性」段落后新增 info callout 引导到详解文件
- 更新 [[index.md]]：Java 并发分类新增 [[ReentrantLock详解]] 条目

## [2026-06-10] query | MyBatis ResultMap association/collection 详解 → 生成 [[MyBatis ResultMap 关联映射详解]]

## [2026-06-10] query | MyBatis 介绍 → 生成 [[MyBatis详解]]

## [2026-06-10] ingest | 面试分析报告 5 大亮点补充 + 新增 Q11-Q16

## [2026-06-10] query | MyBatis-Plus 介绍 → 生成 [[MyBatis-Plus详解]]

## [2026-06-10] query | Seata 介绍 → 生成 [[Seata详解]]

## [2026-06-10] ingest | 合并 java并发 → Java并发编程面试题

**操作：** 将 [[java并发]] 合并到 [[Java并发编程面试题]]（二者内容高度重叠，合并为主文件后删除冗余文件）。

**合并内容：**
- 移除 `clippings` 标签，改为 `java`、`并发`、`JUC`、`面试` 标签
- 新增 **LockRecord（锁记录）与 Displaced Mark Word** 详解：轻量级锁加解锁全流程、栈上分配机制、Mark Word 与 LockRecord 协作方式
- 增强 **ThreadLocal 内存泄露** 分析：分步讲解泄露过程、弱引用设计原因、remove() 最佳实践、线程池场景处理、框架兜底策略
- 删除冗余文件 `03-Java/java并发.md`
- 更新 `index.md`：`[[java并发]]` → `[[Java并发编程面试题]]`

---

## [2026-06-10] query | 补充"主动引用 vs 被动引用"详解 + SOF/OOM 详解

**操作：** 将 [[jvm_review]] 中两个追问点提取为独立详解文件并替换为 wikilink 引用。

**变更：**
- 创建 [[类初始化的主动引用与被动引用]]：5 种主动引用、6 种被动引用陷阱、快速判断表、面试话术
- 创建 [[StackOverflowError与OutOfMemoryError（虚拟机栈）]]：SOF/OOM 触发场景、对比、话术
- 创建 [[三层类加载器与classpath]]：三层加载器详解 + classpath 在不同场景的对应路径
- 创建 [[线程上下文类加载器（TCCL）]]：TCCL 原理、JDBC/SPI 反向打破双亲委派的典型场景
- 创建 [[重写loadClass与findClass的区别]]：源码级对比 findClass（扩展） vs loadClass（真正破坏）
- 创建 [[DirectByteBuffer与堆外内存回收]]：DirectByteBuffer 结构、Cleaner 回收机制、堆外 OOM 触发 Full GC 的原理
- 创建 [[G1 Humongous Region（大对象区）]]：G1 巨型对象的分配回收机制、空间浪费和 Full GC 触发条件
- 创建 [[TLAB详解]]：TLAB 原理、对象分配完整链路、为何避免锁竞争
- 更新 [[jvm_review]]：三个追问点改为 wikilink 引用
- 更新 [[index.md]]：JVM 分类下新增四条

## [2026-06-10] query | 补充 StackOverflowError 与 OOM（虚拟机栈）详解

**操作：** 将 [[jvm_review]] 中"虚拟机栈 SOF vs OOM"的追问点提取为独立详解文件。

**变更：**
- 创建 [[StackOverflowError与OutOfMemoryError（虚拟机栈）]]：覆盖触发场景、原理对比、面试话术、排查方向
- 更新 [[jvm_review]]：追问点改为 wikilink 引用新文件
- 更新 [[index.md]]：JVM 分类下新增条目

## [2026-06-09] query | 优化 jvm 八股内容 & 创建 JVM 模拟卷

**操作：** 根据 Nozomi 的逐轮反馈修正 jvm 八股多处细节，创建 JVM 面试模拟卷。

**变更：**
- 修正运行时常量池/字符串常量池的位置变迁（Metaspace 是本地内存，不在堆）
- 补充 static final 在准备阶段的赋值时机（编译期常量 vs 运行时计算）
- 将 Eden/Survivor 的 Q&A 和 G1 详解从纯块引用改为 Obsidian Callout
- System.gc() 修正为"建议"而非"触发条件"，补充 DisableExplicitGC
- 展开说明空间分配担保和元空间不足触发 Full GC 的机制
- 补充 G1 适合大内存堆的四个核心原因（作为 info 标注，不动原文）
- 创建 [[JVM 八股测验卷（内存模型 · 类加载 · GC）]]（10 道简答题）

---


**操作：** 按 llm-wiki 模式初始化知识库结构。

**变更：**
- 创建 `raw/` 目录（`raw/clippings/` + `raw/assets/`），将原 `Clippings/` 移入
- 重写 `CLAUDE.md`：三层架构（原始资料 / Wiki 页面 / Schema）、目录规范、页面格式、三大操作（Ingest / Query / Lint）、交叉引用规则
- 创建 `index.md`：按知识域分类的完整页面目录，含一句话摘要
- 创建 `log.md`（本文件）：操作日志

**初始状态：**
- Wiki 页面总数：约 180 个
- 知识域：Redis、MySQL、Java、Spring、计算机网络、其他杂项（技术详解）、算法题、Spring MVC 课程笔记、剪藏资料
- 待整理：4 个未分类文件

---

## [2026-06-08] 整理 | Clippings 归位 + 算法题归类子文件夹

**操作：** 将 `raw/clippings/` 中的所有笔记按主题移入对应知识域目录；将 `算法题/` 下所有以数字开头的题解文件按类型归入子文件夹。

### Clippings 迁移
- 12 篇 Redis 系列（小林coding）→ `01-Redis/`
- 1 篇算法题单（力扣分类）→ `算法题/`
- 删除 `raw/clippings/` 空目录
- `index.md` 中移除 Clippings 章节，Redis 资料并入 `参考资料（小林coding 系列）`，算法题单并入 `题单索引`

### 算法题归类
在 `算法题/` 下创建 20 个分类子文件夹，共移动 **127 个文件**：

| 子文件夹 | 题数 |
|---------|------|
| 哈希-双指针 | 5 |
| 滑动窗口 | 3 |
| 链表 | 12 |
| 栈-队列-单调栈 | 6 |
| 二叉树 | 21 |
| 图-BFS-DFS | 10 |
| 回溯 | 7 |
| 动态规划 | 12 |
| 贪心 | 6 |
| 二分查找 | 14 |
| 堆-优先队列 | 3 |
| 前缀和-差分 | 5 |
| Trie | 2 |
| 位运算 | 5 |
| 数学-模拟 | 7 |
| 字符串 | 11 |
| 矩阵 | 3 |
| 数据结构设计 | 2 |
| 表达式 | 1 |
| 其他 | 2 |

**额外发现：** 已有但未在 index 中注册的题目（二分 7 道、前缀和 3 道、排序 Java 实现 3 份），已归入对应目录并补充到 index.md。

**变更文件：**
- `index.md` — 更新算法题章节结构，补充新题目
- `log.md` — 追加本次记录

---

## [2026-06-08] merge | jvm八股 + Java虚拟机面试题 合并

**操作：** 将 `03-Java/Java虚拟机面试题.md` 的内容合并到 `03-Java/jvm八股.md`，删除原文件。

**合并新增内容：**
- 堆的详细划分（新生代、老年代、元空间、大对象）+ 大对象分配区域
- 类加载机制章节：类加载器、双亲委派模型、类加载过程（七阶段）、创建对象过程
- 堆溢出/栈溢出详细排查案例及解决思路
- ThreadLocal 内存泄漏案例
- 未关闭资源导致内存泄漏案例
- Minor GC / Major GC / Full GC 区别
- 标记清除算法的缺点

**保留 jvm八股 原有独特内容：**
- Eden:Survivor 8:2 + 分配担保机制详解
- G1 弱化分代 + 分区详解（社区类比）
- 堆与栈的区别（含 ASCII 图和表格）
- String vs new String 详细对比

**变更文件：**
- `03-Java/jvm八股.md` — 合并后 888 行，覆盖 JVM 三大板块（内存模型、类加载、GC）
- `03-Java/Java虚拟机面试题.md` — 已删除
- `index.md` — 更新 jvm八股 条目描述
