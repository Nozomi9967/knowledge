# 最长子序列：任意三元组绝对值差之和 ≥ k

> 题意：给定数组，从中选出一个子序列（保持原顺序，但条件与顺序无关），使得任意三个被选元素的两两绝对值差之和 ≥ k。长度小于 3 的子序列自动满足条件。求最长子序列的长度。共 t 组数据。

## 一、化简三元组条件

设三个元素排序后为 $x \le y \le z$，两两绝对值差之和：

$$
|x-y|+|y-z|+|x-z| = (y-x)+(z-y)+(z-x) = 2(z-x)
$$

**中间那个数会被消掉**，所以条件只取决于最大值与最小值：

$$
2(\max - \min) \ge k
$$

## 二、把"任意三元组"缩小到"相邻三元组"

条件与顺序无关，只需看被选集合排序后的结果 $b_1 \le b_2 \le \dots \le b_m$。

任取三元组下标 $p < q < r$，其跨度为 $b_r - b_p$。由于 $r \ge p+2$ 且数组不减：

$$
b_r - b_p \ \ge\ b_{p+2} - b_p
$$

即**任何三元组的跨度都不小于某个相邻三元组的跨度**。因此：

> 所有相邻三元组 $(b_p, b_{p+1}, b_{p+2})$ 都满足 $2(b_{p+2}-b_p)\ge k$ ⟺ 任意三元组都满足。

注意：这里的"相邻"是指**被选元素排序后相邻**，不是原数组相邻。有重复值时结论依然成立。

这个结论的意义：约束只涉及被选序列中连续的三个元素，因此 DP 状态只需记录**最后两个被选元素**，不必检查更早的所有组合。

## 三、DP 设计

将数组**排序**后，设：

$$
dp[i][j] = \text{以 } a_j, a_i \ (j<i) \text{ 作为最后两个被选元素时的最大选出个数}
$$

- 初始值：任意 $dp[i][j] \ge 2$（两个元素总满足条件）。
- 转移：往末尾加入 $i$，新出现的三元组是 $(h, j, i)$。由于排序后 $a_h \le a_j \le a_i$，条件为

$$
2(a_i - a_h) \ge k
$$

$$
dp[i][j] = \max_{h<j,\ 2(a_i-a_h)\ge k} dp[j][h] + 1
$$

- 答案 = 所有 $dp[i][j]$ 的最大值（至少为 $\min(n,2)$）。

### 为什么固定 i、合法 h 是一个前缀

转移约束同时含 $i$ 和 $h$，先**固定 $i$**，$a_i$ 变为常数，条件改写为阈值形式：

$$
a_h \le a_i - \frac{k}{2} \triangleq T_i
$$

数组排序后 $a_h$ 随 $h$ 增大而不减，所以：**若 $h$ 合法，则更小的 $h‘$ 也合法**（$a_{h’} \le a_h \le T_i$）。合法下标构成从 0 开始的连续前缀 $[0, L_i]$。

换一种说法：$h$ 越小，$a_i - a_h$ 越大，越容易满足条件，所以合法集合从最前面开始。

再加上 $h<j$ 的限制，实际范围是：

$$
h \in [0,\ \min(L_i,\ j-1)]
$$

同一 $i$ 下所有 $j$ 共用同一个 $L_i$，只需在外层算一次。

## 四、复杂度优化

1. **双指针**：$i$ 增大时 $T_i = a_i - k/2$ 不减，$L_i$ 只会右移，所以 $L_i$ 总共 $O(n)$ 次更新即可维护。
2. **前缀最大值**：转移是对第 $j$ 行的区间 $[0, \min(L_i, j-1)]$ 取 max。每算完一行 $dp[j][\cdot]$，就**原地**转成前缀最大值数组 $\text{pm}_j$，之后每次转移 $O(1)$ 查询：

$$
dp[i][j] = \text{pm}_j\big(\min(L_i,\ j-1)\big) + 1
$$

**总复杂度**：排序 $O(n\log n)$ + DP $O(n^2)$；空间约 $O(n^2/2)$（锯齿数组，只存下三角）。

### 举例

$a = [1,2,4,7]$，$k=4$（阈值差 2）：

- $i=2$（$a_2=4$），$j=1$：$h=0$，$2(4-1)=6\ge4$ ✓，$dp[2][1]=dp[1][0]+1=3$，即 $\{1,2,4\}$。
- $i=3$（$a_3=7$），$j=2$：阈值 $5$，$h\in\{0,1\}$ 均合法，$dp[2]$ 的前缀最大值为 3，故 $dp[3][2]=4$，即全选 $\{1,2,4,7\}$（最大跨度 6 ≥ 2）。

## 五、Java 实现

```java
import java.util.*;
import java.io.*;

public class Main {

    public static void main(String[] args) throws IOException {
        StreamTokenizer st = new StreamTokenizer(new BufferedInputStream(System.in));
        st.nextToken();
        int t = (int) st.nval;
        StringBuilder sb = new StringBuilder();

        while (t-- > 0) {
            st.nextToken();
            int n = (int) st.nval;
            st.nextToken();
            long k = (long) st.nval;

            long[] a = new long[n];
            for (int i = 0; i < n; i++) {
                st.nextToken();
                a[i] = (long) st.nval;
            }
            Arrays.sort(a);
            sb.append(solve(a, k)).append('\n');
        }
        System.out.print(sb);
    }

    static int solve(long[] a, long k) {
        int n = a.length;
        if (n <= 2) {
            return n;
        }

        // pm[i][x] = max(dp[i][0..x])，dp[i][j] 表示最后两个选中元素下标为 j 和 i (j < i)
        int[][] pm = new int[n][];
        int best = 2;

        // p: 对当前 i，满足 2*(a[i]-a[h]) >= k 的最大下标 h（合法前缀的右边界）
        int p = -1;

        for (int i = 0; i < n; i++) {
            // 双指针：a[i] 不减，p 只右移
            while (p + 1 < i && 2 * (a[i] - a[p + 1]) >= k) {
                p++;
            }

            int[] row = new int[i];           // dp[i][0..i-1]
            for (int j = 0; j < i; j++) {
                int hi = Math.min(p, j - 1);  // h 的范围 [0, min(L_i, j-1)]
                if (hi >= 0) {
                    row[j] = 1 + pm[j][hi];   // O(1) 前缀最大值查询
                } else {
                    row[j] = 2;               // 无合法前驱，初始值
                }
                best = Math.max(best, row[j]);
            }

            // 原地转成前缀最大值（best 已在此前更新，不会误统计）
            for (int x = 1; x < i; x++) {
                row[x] = Math.max(row[x], row[x - 1]);
            }
            pm[i] = row;
        }
        return best;
    }
}
```

### 代码要点

| 细节 | 说明 |
|---|---|
| `new int[n][]` | 锯齿数组：只分配外层，内层按 `new int[i]` 逐行创建，总空间约 $n^2/2$。语法上省略的维度必须在后面（`new int[n][]` 合法，`new int[][n]` 不合法） |
| `pm[j][hi]` 安全性 | $j<i$，第 $j$ 行在第 $j$ 轮已赋值；`hi ≤ j-1` 不会越界 |
| `p + 1 < i` | $h$ 必须严格小于 $i$，不能把 $a_i$ 自己配进去 |
| `best` 更新时机 | 在前缀化**之前**更新，避免把前缀最大值误当成真实 dp 值 |
| 输入格式假设 | 第一行 t，每组先 n、k，再 n 个整数；格式不同只需改读入部分 |

## 六、易错点清单

1. **忘记排序**：条件化简和前缀性质都依赖排序后的数组。
2. **"相邻"理解错**：是被选元素排序后相邻，不是原数组相邻。
3. **k 为 0 或负数**：条件恒成立，答案为 n（代码中双指针会把 p 推到 i-1，dp 全链增长，结果自然正确，但可特判加速）。
4. **n ≤ 2**：直接返回 n，代码已特判。
5. **重复元素**：排序保留重复即可，不影响任何推导。
6. **溢出**：$2(a_i - a_h)$ 在值域大时可能超 int，代码用 long。
7. **空间**：约 $O(n^2/2)$ 个 int，n 达几千时注意内存限制；n 更大需进一步寻找结构性优化。

## 七、复杂度总结

- 时间：$O\big(\sum n\log n + \sum n^2\big)$
- 空间：$O(n^2/2)$

## 附：力扣原型

没有找到与本题完全一致的力扣原题（本题更像 Codeforces / 牛客风格的多组数据题）。相关但方向不同的题：

- [LC 2099. Find Subsequence of Length K With the Largest Sum](https://leetcode.com/problems/find-subsequence-of-length-k-with-the-largest-sum)
- [LC 1755. Closest Subsequence Sum](https://leetcode.com/problems/closest-subsequence-sum)

可在题库中以"子序列 + 最大最小差限制 + DP"关键词搜索类似题。
