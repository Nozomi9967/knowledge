---
tags:
  - 算法题
  - 动态规划
created: 2026-08-14
updated: 2026-08-14
---
# 0/1 背包问题与变体：从模板到路径还原、择优规则及输入流优化

本篇笔记系统性地梳理了 0/1 背包及其代表性变形问题。涵盖了背包动态规划的本质、Luogu P1048 采药（模板题及 Java 运行时 RE 避坑）、以及同等难度变体 Luogu P2066 机器分配（前/后缀 DP、路径还原与字典序处理）。

---

## 1. 0/1 背包的核心：结构本质与容量倒序

### 1.1 为什么是两层循环？
0/1 背包的状态由两个维度决定，缺一不可：
1. **面对的物品范围**（外层循环 $i$）：因为每个物品是独立决策的，每多引入一个物品，可选的世界就变大一点。
2. **已经占用的容量**（内层循环 $j$）：相同的可选物品下，背包可能被装到 $0, 30, 70 \dots$ 不同的容量，每个容量对应的最优状态也不同。

**类比：**
* **外层循环：** 相当于时间线。每一轮新来一批货（引入一个物品），相当于“超市又上了一个新货架”，整张已有的 DP 表都可能落伍，需要更新。
* **内层循环：** 相当于重新盘点仓库。新货可能塞进任何一个空隙，你必须把仓库的所有容量（$0 \sim K$）全扫一遍，看看新货能否提升该容量下的最大价值。

---

### 1.2 为什么内层容量 $j$ 必须从大到小（倒序）遍历？
0/1 背包的状态转移方程：
$$dp[j] = \max(dp[j], dp[j - c[i]] + v[i])$$

由于 $dp[j]$ 依赖的是比它小的容量状态 $dp[j - c[i]]$（因为 $c[i] > 0$），遍历的方向决定了依赖的那个小容量状态是“干净的旧值”还是“已被本轮更新污染的新值”。

#### 错误示范：从小到大（正序）遍历
假设只有一个物品（$c=30, v=400$），容量上限 $K=90$：
* $j=30$ 时：$dp[30]$ 读 $dp[0]=0$，写入 $dp[30]=400$（物品被选了 $1$ 次）。
* $j=60$ 时：$dp[60]$ 读 $dp[30]=400$，写入 $dp[60]=800$（物品被选了 **2** 次！重复选入）。
* $j=90$ 时：$dp[90]$ 读 $dp[60]=800$，写入 $dp[90]=1200$（物品被选了 **3** 次！）。
这退化成了**完全背包**。

#### 正确示范：从大到小（倒序）遍历
* $j=90$ 时：$dp[90]$ 读 $dp[60]$（旧值，不可达/未变），跳过。
* $j=60$ 时：$dp[60]$ 读 $dp[30]$（旧值，不可达/未变），跳过。
* $j=30$ 时：$dp[30]$ 读 $dp[0]=0$，写入 $dp[30]=400$（物品只选了 $1$ 次）。
**结论：** 倒序遍历时，先更新大容量，它读取的小容量是上一轮的旧状态，绝对不会把本轮物品重复叠加进去。

---

## 2. 经典模板：Luogu P1048 [NOIP 2005 普及组] 采药

### 2.1 题目简述
辰辰要在总时间 $T$ 内采药，有 $M$ 株不同的草药，每株采摘需要时间 $t_i$，价值为 $v_i$。求规定时间内可以采到的草药最大总价值。

### 2.2 运行时错误（RE）排查避坑

在做 Java 竞赛题时，常常会遇到本地运行正确，但 OJ 提交却返回 `RE (Runtime Error)` 或 `exit code 1`。常见元凶如下：

#### ① 数组越界（ArrayIndexOutOfBoundsException）
* **错误写法：**
  ```java
  for(int j = t; j > 0; j--) {
      if(d[j - T] != -1) d[j] = Math.max(d[j - T] + V, d[j]);
  }
  ```
* **原因：** 当物品所需时间 $T > j$ 时，`j - T` 变成负数，访问负下标立刻抛出数组越界异常崩溃。
* **修复方法（加守卫或收窄边界）：**
  ```java
  // 方法一：修改内层循环边界（推荐，更干净）
  for(int j = t; j >= T; j--) { ... }

  // 方法二：使用越界判断守卫
  for(int j = t; j > 0; j--) {
      if(j - T >= 0 && d[j - T] != -1) { ... }
  }
  ```

#### ② 基态丢失
* **原因：** 如果数组初始化为非零无效状态（如 `Arrays.fill(d, -1)`），必须手动把基态点亮：`d[0] = 0`。如果不设，整个转移树没有出发点，算不出任何结果。

#### ③ Scanner 输入流解析器抛出 `InputMismatchException` / `NoSuchElementException`
* **原因：** `Scanner` 依赖底层操作系统和 JVM 的默认本地化设置（`Locale`）。在某些 Linux OJ 环境下，如果 `Locale` 非英美国家，`nextInt()` 解析空格/多余空行/小数点时可能抛出异常。此外，`Scanner` 使用正则匹配 token，运行速度极慢。
* **黄金标准替代方案：** 用 `BufferedReader` + `StreamTokenizer`。它直接切字符流，绕过了 Locale，极速且极稳，不会因环境而抛出非预期的 I/O 错误。

---

### 2.3 采药最终 AC 代码（标准 IO 优化版）

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String args[]) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StreamTokenizer st = new StreamTokenizer(br);
        
        st.nextToken(); int t = (int)st.nval; // 背包容量 (总时间)
        st.nextToken(); int m = (int)st.nval; // 物品数 (草药数)
        
        int[] d = new int[t + 1];
        Arrays.fill(d, -1);
        d[0] = 0; // 点亮基态：0时间对应0价值
        int ans = 0;
        
        for (int i = 0; i < m; i++) {
            st.nextToken(); int T = (int)st.nval;
            st.nextToken(); int V = (int)st.nval;
            if (T > t) continue;
            
            // 内层收窄边界到 T，天然杜绝数组负越界
            for (int j = t; j >= T; j--) {
                if (d[j - T] != -1) {
                    d[j] = Math.max(d[j - T] + V, d[j]);
                    ans = Math.max(ans, d[j]);
                }
            }
        }
        System.out.println(ans);
    }
}
```

---

## 3. 同等难度进阶：Luogu P2066 机器分配

### 3.1 题目简述
总公司拥有高效设备 $M$ 台，准备分给下属的 $N$ 个分公司。第 $i$ 个公司分配 $j$ 台机器可盈利 $a[i][j]$。求如何分配能使总盈利最大，并输出每个分公司的分配台数。
* **并列择优规则（字典序最小）：** 最大盈利值相同时，要求**编号小的公司分得设备尽可能少**。

---

### 3.2 方案一：后缀 DP + 贪心还原（高阶优雅，O(1) 决策）

普通的背包一般用前缀 DP。但是本题有强约束“编号小的公司分得尽可能少”。如果使用后缀 DP，我们在路径还原时就能**正着走**，从公司 1 贪心地试出最小台数，非常契合择优规则。

#### ① 后缀状态定义
`f[i][j]` = **公司 $i$ 到 $N$**（后缀子问题）共同分配 $j$ 台设备时的最大盈利。

#### ② 状态转移方程
$$f[i][j] = \max_{0 \le c \le j} ( a[i][c] + f[i+1][j-c] )$$
* $c$：分给公司 $i$ 的设备台数。
* $j-c$：分配给剩余分公司 $i+1 \sim N$ 的设备台数。

#### ③ 贪心路径还原
计算完后缀表后，我们从公司 $1$ 开始正向推导方案。设当前剩余可用设备为 `remaining`：
* 每一个公司 $i$，让 $c$ 从 $0$ 开始递增，一旦满足下式则立即采纳：
  $$a[i][c] + f[i + 1][remaining - c] == f[i][remaining]$$
* **为什么保证字典序最小？** 因为 $c$ 是从小往大试的，第一个碰到的必定是满足最大盈利的**最小**分配台数，公司 $1$ 的分配额天然被最先压到最小，其次是公司 $2$、公司 $3 \dots$，完美契合题目择优要求。

#### 后缀 DP 完整 AC 代码

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StreamTokenizer st = new StreamTokenizer(br);

        st.nextToken(); int n = (int)st.nval; // 公司数
        st.nextToken(); int m = (int)st.nval; // 设备数

        int[][] a = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            a[i][0] = 0;
            for (int j = 1; j <= m; j++) {
                st.nextToken(); a[i][j] = (int)st.nval;
            }
        }

        final int NEG = Integer.MIN_VALUE / 2;
        int[][] f = new int[n + 2][m + 1]; // 后缀状态
        Arrays.fill(f[n + 1], NEG);
        f[n + 1][0] = 0;

        // 从后往前：倒着递推后缀
        for (int i = n; i >= 1; i--) {
            for (int j = 0; j <= m; j++) {
                f[i][j] = NEG;
                for (int c = 0; c <= j; c++) {
                    if (f[i + 1][j - c] != NEG) {
                        f[i][j] = Math.max(f[i][j], a[i][c] + f[i + 1][j - c]);
                    }
                }
            }
        }

        System.out.println(f[1][m]); // 全局最大盈利

        // 贪心路径恢复：正向扫描，c从小到大试
        int[] assign = new int[n + 1];
        int remaining = m;
        for (int i = 1; i <= n; i++) {
            for (int c = 0; c <= remaining; c++) {
                if (f[i + 1][remaining - c] != NEG
                    && a[i][c] + f[i + 1][remaining - c] == f[i][remaining]) {
                    assign[i] = c;
                    remaining -= c;
                    break; // 找到最小的 c 立即退出
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(i).append(' ').append(assign[i]).append('
');
        }
        System.out.print(sb);
    }
}
```

---

### 3.3 方案二：前缀 DP + DFS 剪枝爆搜（简单易懂，小数据无敌）

如果你觉得后缀 DP 状态转移反直觉，也可以采用基础的**前缀 DP**。前缀 DP 状态好写，但表无法直接指引“正向贪心还原”，因此我们在还原阶段用 **DFS 剪枝暴力搜索** 替代贪心，利用 $N \le 10, M \le 15$ 的极小数据量直接通关。

#### ① 前缀 DP
`f[i][j]` = 前 $i$ 个公司分配 $j$ 台设备的最大盈利。
$$f[i][j] = \max_{0 \le c \le j} ( f[i - 1][j - c] + a[i][c] )$$

#### ② 正向 DFS 搜出字典序最小
从公司 $1$ 开始搜，递归深入到 $N + 1$。
* 每一个公司的设备数 $c$ 依然从 $0$ 递增试。
* **剪枝条件：** 当前累积盈利 `val > f[n][m]`（最大盈利）时，由于盈利非负，后续不可能变小，直接剪枝返回。
* **停止条件：** 当搜到第 $N+1$ 阶段，且 `rem == 0 && val == f[n][m]` 时，说明找到了一条合法最优路径。因为我们是先试小分配的，所以**最先触达终点的就是全局字典序最小的路径**，打上 `found = true` 标记直接强行终止整颗搜索树。

#### 前缀 DP + DFS 完整 AC 代码

```java
import java.io.*;
import java.util.*;

public class Main {
    static int n, m;
    static int[][] a, f;
    static int[] cur, best;
    static boolean found;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StreamTokenizer st = new StreamTokenizer(br);
        st.nextToken(); n = (int)st.nval;
        st.nextToken(); m = (int)st.nval;
        a = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                st.nextToken(); a[i][j] = (int)st.nval;
            }
        }

        // 经典前缀 DP
        final int NEG = Integer.MIN_VALUE / 2;
        f = new int[n + 1][m + 1];
        Arrays.fill(f[0], NEG);
        f[0][0] = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                f[i][j] = NEG;
                for (int c = 0; c <= j; c++) {
                    f[i][j] = Math.max(f[i][j], f[i - 1][j - c] + a[i][c]);
                }
            }
        }

        System.out.println(f[n][m]);

        cur = new int[n + 1];
        best = new int[n + 1];
        found = false;
        
        // 正着 DFS，由于 c 从 0 开始增加，找到的第一条通路即最优字典序
        dfs(1, m, 0, f[n][m]);

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(i).append(' ').append(best[i]).append('
');
        }
        System.out.print(sb);
    }

    static void dfs(int i, int rem, int val, int target) {
        if (found) return;
        if (val > target) return; // 剪枝
        
        if (i == n + 1) {
            if (rem == 0 && val == target) {
                found = true;
                best = cur.clone();
            }
            return;
        }
        
        for (int c = 0; c <= rem; c++) { // 字典序保证：分配从小试
            cur[i] = c;
            dfs(i + 1, rem - c, val + a[i][c], target);
            if (found) return; // 找到最优后强行中断所有递归
        }
    }
}
```

---

## 4. 总结：背包问题的解题金律
1. **多看数据范围决定解法**：当看到 $N, M$ 极小时（如本题 $\le 15$），不仅可以用常规 DP，还能在还原时大胆使用 DFS 辅以剪枝。
2. **状态设计为择优服务**：要比功耗、比分配，就设法把功耗/分配台数作为 $DP$ 状态的下标索引或后缀，全局盘点或还原时只需从小到大单向检索即可优雅通关。
3. **输入流优化常备身侧**：写 Java 提交 OJ 题遇到奇奇怪怪的 `RE Runtime Error`，直接复制 `BufferedReader` + `StreamTokenizer` 的模板，不仅速度快 5 到 10 倍，更可以规避正则和区域设置带来的各种非预期异常。