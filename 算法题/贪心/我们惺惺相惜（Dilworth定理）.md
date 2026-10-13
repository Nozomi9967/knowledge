---
tags:
  - 算法
  - Dilworth定理
  - 贪心
  - 子序列
created: 2026-08-30
updated: 2026-08-30
source:
---

# 我们惺惺相惜（Dilworth 定理 + 贪心）

## 题目描述

给定一个长度为 $n$ 的数组 $a$，定义区间 $[l, r]$ 是**好的**，当且仅当这个区间可以分成两个非空的子序列（元素之间相对顺序不变），使得这两个子序列都是**严格单调递增**子序列。

对于给出的多次询问，你需要回答区间是不是好区间。

- **时间限制**：C/C++ 5 秒，其他语言 10 秒
- **空间限制**：C/C++ 1024M，其他语言 2048M

### 输入描述

第一行一个整数 $t$，表示有 $t$ 次询问。

对于每次询问，第一行两个整数 $n, m$，第二行 $n$ 个整数表示数组 $a$。接下来 $m$ 行，每行两个整数 $l, r$，表示询问的区间。

单个测试文件保证 $\sum n$ 和 $\sum m$ 均不超过限制。

### 输出描述

对于每次询问，输出一行，好区间输出 `YES`，否则输出 `NO`。

### 示例

```
输入：              输出：
2
4 2                YES
1 2 3 3            YES
1 3
1 2
5 3                YES
4 5 4 5 3          NO
1 4                YES
1 5
2 4
```

---

## 核心结论

> **不需要线段树。** 关键是 **Dilworth 定理**——区间能拆成两个严格递增子序列 ⟺ 区间内**不存在长度为 3 的严格递减子序列**。每个询问从左到右 $O(\text{区间长度})$ 贪心扫一遍即可。

### 为什么是「无长度为 3 的递减子序列」

**Dilworth 定理**：一个序列最少能被划分成的递增子序列个数 = 它的**最长递减子序列（LDS）长度**。

所以"能分成两个递增子序列"等价于 LDS ≤ 2，即不存在 $a_i > a_j > a_k\ (i<j<k)$。

### 怎么 O(len) 检查

维护两个值：

| 变量 | 含义 |
|------|------|
| `m1` | 目前为止见过的**最小值**（递减链长度为 1 的最小结尾） |
| `m2` | 目前为止最小的、能作为**递减链第二个元素**的值（即某个 `m1` 出现之后遇到的更小值） |

对每个新元素 `x`：

1. 若 `x < m2`：说明 `m1 > m2 > x` 构成长度 3 的递减子序列 → 输出 **NO**
2. 否则若 `x < m1`：`m2 = x`（它可以接在之前某个更大的数后面）
3. 然后 `m1 = min(m1, x)`

**注意严格递减**：相等不算，条件用 `<`。

---

## AC 代码

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        StringBuilder sb = new StringBuilder();
        in.nextToken(); int t = (int) in.nval;
        while (t-- > 0) {
            in.nextToken(); int n = (int) in.nval;
            in.nextToken(); int m = (int) in.nval;
            int[] a = new int[n];
            for (int i = 0; i < n; i++) { in.nextToken(); a[i] = (int) in.nval; }
            while (m-- > 0) {
                in.nextToken(); int x = (int) in.nval - 1;
                in.nextToken(); int y = (int) in.nval - 1;
                sb.append(check(a, x, y) ? "YES" : "NO").append('\n');
            }
        }
        System.out.print(sb);
    }

    static boolean check(int[] a, int l, int r) {
        int m1 = Integer.MAX_VALUE;  // 最小值
        int m2 = Integer.MAX_VALUE;  // 作为递减链第二项的最小值
        for (int i = l; i <= r; i++) {
            int x = a[i];
            if (x < m2) return false;   // m1 > m2 > x，出现长为3的递减子序列
            if (x < m1) m2 = x;         // x 可作递减链第二项
            m1 = Math.min(m1, x);
        }
        return true;
    }
}
```

---

## 原线段树解法为什么错（复盘）

原始思路是用线段树维护「区间分成若干段**连续**递增块，块数 ≤ 2 就行」。

### 错误一：判定模型本身就错了

「好区间」允许**非连续**的划分。反例 `3 1 4 2`：

- 连续递增块有 3 段（`3`、`1 4`、`2`）→ 线段树判 NO
- 但它其实能拆成子序列 `1 2` 和 `3 4` → 实际是好区间 ✓

### 错误二：代码实现层面的 bug

```java
// ❌ 原始 query/qu 的三处 bug
public static void qu(int node,int p,int q,int x,int y,ArrayList<Integer> ns){
    if(x<=p&&q<=y){ ns.add(node); return; }
    int m=(q-p)/2+p;
    if(x<=m){ qu(node*2,p,m,x,y,ns); }      // ❌ 每层只递归一个孩子
    else{ qu(node*2+1,m+1,q,x,y,ns); }      //    没有完整切分区间
}
```

| bug | 说明 |
|-----|------|
| `qu` 单边递归 | 用 `if/else` 只走一个孩子，区间被截断，漏掉跨越 mid 的部分。正确写法是两边**都可能**递归（`if(x<=m) 左; if(y>m) 右;`） |
| `cnt = n1 + n2` | 加的是**节点编号**而不是区间长度/块数，语义完全错位 |
| `System.out.println` 调试输出 | 混进标准输出会污染答案，OJ 直接 WA |

---

## 样例验证

| 区间 | 内容 | 扫描过程 | 结果 |
|------|------|---------|------|
| `1 5`（整段） | `4 5 4 5 3` | 扫到最后的 `3` 时 `m1=4, m2=4`，`3 < 4` → 三连递减 | **NO** ✓ |
| `2 4` | `5 4 5` | `m2` 一直是 MAX，`m1` 最终 4，无三连递减 | **YES** ✓ |

## 复杂度

$$O\left(\sum n + \sum m \cdot len\right)$$

题目保证询问区间长度总和有限制，这样完全够过。

---

## 可迁移心法

**「能否拆成 k 个递增子序列」类问题一律先想 Dilworth 定理**，把"划分数"问题转成"最长反链长度"问题：

| 问题形态 | Dilworth 转化 |
|---------|--------------|
| 最少拆成几个**递增**子序列 | = 最长**递减**子序列长度 |
| 最少拆成几个**递减**子序列 | = 最长**递增**子序列长度 |
| 能否拆成 2 个递增子序列 | ⟺ 不存在长度 3 的递减子序列（本题） |

一旦转成"检查是否存在长度 k 的单调子序列"，就能用 $O(len)$ 的多指针贪心（维护 k−1 个"链尾最小值"）替代 $O(len \log len)$ 的 LIS 或复杂的线段树。

## 关联知识点

- [[最长递增子序列]] — LIS 标准解法（贪心 + 二分）
- [[小美的陡峭值操作]] — 同样是"先找不变量/定理，再降复杂度"的思路