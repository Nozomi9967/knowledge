---
tags:
  - Git
  - 版本控制
  - 工程实践
created: 2026-10-03
updated: 2026-10-03
source: Git 教程会话 + Nozomi_House_Project 实操问答
---

# Git 详解

## 一、Git 是什么？三大区域

Git 是**版本控制系统**：记录项目文件的变化，支持查看历史、回到旧版本、多人并行开发。

把 Git 想成三个区域：

| 区域 | 说明 |
|:---|:---|
| **工作区** | 你正在编辑的文件 |
| **暂存区（index / staged）** | 你挑选好、准备提交的更改（提交前的"候选清单"） |
| **本地仓库** | 已经提交的历史记录 |

工作流：`编辑文件 → git add → git commit →（可选）git push 到远程`

> [!note] add vs commit
> `git add` 只是把改动放进**暂存区**，不会立刻生成版本；`git commit` 才会在本地记录一次快照。暂存区可以只挑一部分改动提交（`git add login.java` 就只提交它，`README.md` 的改动仍留在工作区）。

## 二、从零起步

```bash
git init                                  # 当前目录初始化仓库
git config --global user.name "你的名字"   # 首次设置提交人姓名
git config --global user.email "你的邮箱"  # 提交人邮箱
git status                                # 查看状态（最常用）
git add README.md                         # 暂存某文件
git add .                                 # 暂存当前目录所有改动
git commit -m "修复登录页表单校验"         # 提交（写明具体改动）
```

每次提交尽量只包含**一件逻辑清楚的改动**，说明写具体。

## 三、查看改动与历史

```bash
git diff                # 工作区未暂存的改动
git diff --staged       # 已暂存、准备提交的改动（--staged 与 --cached 同义）
git diff -- 文件名       # 只看某个文件（-- 表示后面是路径，防歧义）
git log --oneline       # 简洁历史：每条一行 "提交编号缩写 提交说明"
git show 提交编号        # 看某次提交的说明和改动
```

`git diff`（无 `--cached`）看**工作区**没 add 的；`git diff --cached` 看**暂存区**里已 add 的。`--cached` 是 Git 对暂存区的旧称。

### diff 输出怎么读（`@@` 与 `+`/`-`）

每个文件从 `diff --git a/路径 b/路径` 开始，到下一个 `diff --git` 前结束。

```diff
diff --git a/Nozomi_House/ConfigService/.../AuthService.java b/Nozomi_House/ConfigService/.../AuthService.java
@@ -204,9 +208,9 @@ public class AuthService {
-    private AuthResponse buildAuthResponse(UserAccount account) {
+    private AuthResponse buildAuthResponse(UserAccount account, long ttlSeconds) {
```

- `@@ -204,9 +208,9 @@`：一个**改动区块**标题——旧文件从第 204 行起涉及 9 行，新文件从第 208 行起涉及 9 行；后面 `public class AuthService {` 是附近上下文。
- 行首 `-`：旧版有、现被删/替换的行；`+`：新增/替换的行；空格开头：未变，仅作上下文。
- 一个文件可有多个 `@@` 区块；看到下一个 `diff --git` 才表示换了个文件。

### LF 与 CRLF（行尾警告）

`LF`（换行）与 `CRLF`（回车+换行）都是"换到下一行"的表示，区别在字符：

- **LF**：一个字符，Linux/macOS/多数现代工具用。
- **CRLF**：两个字符，Windows 传统用。

> 实际终端常见警告 `warning: LF will be replaced by CRLF ...` 是**提醒不是报错**：Git 发现文件是 LF，而本机配置预计下次处理时转成 CRLF。通常不影响运行。
>
> 判断文件当前换行：IDEA 右下角有 `CRLF/LF` 标记可点看/改。**是否 CRLF 取决于 IDEA 设置、文件原有格式与 `.editorconfig`，不能单凭"AI 写的还是 IDEA 写的"判断。**
>
> 临时让某次命令不刷提醒：`git -c core.safecrlf=false diff`。
>
> 项目统一换行：见下文 **.gitattributes**。**别为了消除提醒就 reset/丢弃改动**——`diff` 里的代码改动仍要按需保留。

## 四、远程协作

```bash
git clone <仓库地址>              # 拷贝远程项目到本地
git remote -v                     # 查看远程仓库地址（-v = verbose，列 fetch/push 地址）
git fetch                         # 获取远程更新，但不自动合并
git pull                          # fetch + 合并到当前分支
git push                          # 把本地提交推送到远程
```

- **`origin`**：远程仓库的默认**简称**（不是真实路径）。`origin/feature/login` 表示"远程 `origin` 上的 `feature/login` 分支"，即**远程仓库名 / 分支名**，是本地保存的远程分支状态记录。
- **`-u`（`--set-upstream`）**：建立本地分支与远程分支的**跟踪关系**。`git push -u origin feature/login` 后，Git 记住对应关系，之后在该分支直接 `git push`/`git pull` 即可，不必每次都写 `origin feature/login`。

## 五、分支：并行开发

```bash
git branch                       # 列出本地分支（带 * 是当前分支）
git switch main                  # 切回 main
git switch -c feature/login      # 创建并切换到新分支（-c = --create）
git merge feature/login          # 把功能分支合并进当前分支
git branch -d feature/login      # 删除已合并的本地分支
```

- 分支名没有强制格式，`feature/login` 的 `/` 只是分类习惯；常用 `feature/功能名`、`bugfix/问题名`。
- **合并冲突**：两边改了同一文件同一段时 Git 暂停，标出：

```text
<<<<<<< HEAD
main 分支内容
=======
feature/login 分支内容
>>>>>>> feature/login
```

流程：**冲突文件待处理 → 手动编辑删标记 → `git add 冲突文件`（标记已解决并暂存）→ `git commit`（完成合并提交）**。注意冲突文件处于"未合并"状态、**不在暂存区**，`git add` 才把它视为已解决。不想继续则 `git merge --abort`。

> [!note] merge 能取消吗？
> 能，但只针对**尚未完成**的合并（如卡在冲突中）；`git merge --abort` 会放弃并尽量恢复到合并前。若已无冲突、合并已提交，则不能用 `--abort` 撤销，通常用 `git revert` 反向撤销。

## 六、撤销与恢复（重点易错）

| 命令 | 历史怎么变 | 修改是否保留 | 常见用途 |
|:---|:---|:---|:---|
| `git restore 文件` | 文件恢复成最近一次提交 | **丢弃**未提交改动 | 丢弃单个文件改动 |
| `git restore --staged 文件` | 只从暂存区移出 | **保留**工作区改动 | 取消暂存 |
| `git revert 提交编号` | **新增一个反向提交** | 旧提交仍在历史 | 撤销**已推送/共享**的提交 |
| `git reset --soft HEAD~1` | 分支指针回退 | 保留并**暂存** | 改写最近提交但先留内容 |
| `git reset HEAD~1`（默认 `--mixed`） | 分支指针回退 | 保留在**工作区** | 撤回提交、重新整理 |
| `git reset --hard HEAD~1` | 分支指针回退 | **丢弃**相关本地改动 | 明确要彻底回退时才用 |

- **`restore` vs `restore --staged`**：不带 `--staged` 丢弃工作区改动并恢复为最近提交版；带 `--staged` 只取消暂存、保留工作区改动。`restore` 是**恢复文件**（恢复工作区），**不是下载文件**；"文件名"是占位，要换成实际路径（如 `git restore Nozomi_House/README.md`）。
- **`revert` vs `reset`**：`git revert C` 生成一个新提交 `D` 抵消 `C`，历史 `A—B—C—D`，**不改写已共享历史**，适合已推送的提交；`git reset` 是**移回分支指针**，常用于**还没推送**的本地提交（改提交说明、重排提交）。已推送的提交优先用 `revert`，别随意 `reset --hard`。
- 若在提交 `C` 后**无任何未提交改动**，`git reset --hard HEAD~1` 只是把分支从 `C` 退回 `B`（撤销 `C` 这次提交），不会丢工作区新改动；但前提是 `C` 未推送、且确定要撤销它。

## 七、临时收起改动：stash

```bash
git stash        # 把未提交改动打包收起，工作区恢复干净（先"装进抽屉"）
git stash list   # 列出收起的记录
git stash pop    # 应用最近一次收起，并从列表移除
git stash apply  # 应用但不移除
```

用途：切换分支或处理别的事时，把当前未提交的草稿暂存起来。注意默认主要保存**已跟踪文件**的改动；新建但未被跟踪的文件通常不会一起收起。

## 八、其他常用

### checkout（旧命令）
旧版本用 `git checkout` 兼顾切分支和恢复文件，现推荐拆成 `git switch` + `git restore`：`git checkout main` → `git switch main`；`git checkout -b feature/login` → `git switch -c feature/login`。（另有一个 `git check-ignore 路径` 检查文件是否被 `.gitignore` 忽略。）

### tag（标签）
给重要提交贴名，如发布版本，指向固定提交而非随分支移动：

```bash
git tag            # 查看
git tag v1.0.0     # 给当前提交打标签
git tag v1.0.0 提交编号   # 给旧提交打
git show v1.0.0    # 看标签指向
git push origin v1.0.0   # 推标签到远程
```

"当前提交"= 执行 `git tag` 那一刻当前分支指向的最新已有提交；`git tag` **不会新建提交**、不改代码。

### .gitignore（忽略文件）
项目根目录写不想被 Git 跟踪的文件（构建产物、日志、临时文件）：

```gitignore
*.log
build/
.env
```

注意：已被 Git 跟踪的文件，后来才写进 `.gitignore` **不会自动停止跟踪**。

### .gitattributes（统一换行符）
仓库级规则，比每个人电脑上的 Git 配置更适合团队统一：

```gitattributes
* text=auto eol=lf
```

`eol=lf` 表示匹配文本文件在工作目录用 LF；Git 存进仓库时也规范化为 LF。可只对某类文件设规则：

```gitattributes
*.bat text eol=crlf
*.sh  text eol=lf
```

> [!warning] 统一换行会扰动已有文件
> 添加规则**不一定立刻改写已有文件**；初次规范化换行时，很多文件会在 `git diff` 里显得"整份都变了"。建议先确认团队统一 LF 还是 CRLF，再单独提交规范化，别和代码改动混在一起。

### rebase（把提交接到新进度之后）
把功能分支的提交重新应用到最新 `main` 之后，历史更直：

```bash
git switch feature/login
git rebase main            # 改写成 D'、E'，提交编号会变
git rebase --continue      # 解决冲突后继续
git rebase --abort         # 放弃
```

**别随意 rebase 已推送、别人也在用的提交**（会改写历史）；共享分支一般先学会 `merge`。

### cherry-pick（只取某个提交）
把另一个分支的**某一次提交**单独复制到当前分支，不合并整个分支：

```bash
git switch main
git cherry-pick 提交编号    # 在 main 上新建等效提交（编号不同），原提交仍在原分支
```

`cherry-pick` 不改写原分支；若该提交依赖分支里其他提交，单独挑它可能跑不通。

### reflog（找回误操作）
查看本地分支指针**曾经**移动到的位置，用于误用 `reset` 后找回"消失"的提交：

```bash
git reflog
git show 提交编号
git branch recovery 提交编号   # 建新分支指向它，避免直接动当前分支
```

`reflog` 只是**本地**记录，不是远程备份，别当永久保险。

## 九、一句话流程 + 易错点

**日常流程：**
```bash
git pull && git status    # 先同步远程、看状态
git diff                  # 检查改动
git add 文件路径            # 挑选要提交的内容
git diff --cached          # 复查暂存区
git commit -m "说明"       # 记录到本地
git push                  # 分享到远程
```

**易错点：**
1. `add` 不进版本、`commit` 才记录快照。
2. `git diff`=工作区未 add；`git diff --cached`=暂存区已 add。
3. `restore` 丢工作区改动；`restore --staged` 只取消暂存。
4. 已推送/共享的提交用 `revert`（新增反向提交），别 `reset --hard`（改写历史、丢改动）。
5. 冲突文件不进暂存区，解决后 `git add` 才算已解决。
6. LF/CRLF 警告是格式提醒非报错，别为消除它 reset/丢弃改动。

## 关联知识点

- [[docker命令]] — 另一个工程工具的命令速查
- [[笔试]] — 常见零散笔试/实操坑点（Git 相关命令常在此考）
- [[等待与僵尸进程回收详解]] — 进程/资源管理类基础（对照工程工具类）
