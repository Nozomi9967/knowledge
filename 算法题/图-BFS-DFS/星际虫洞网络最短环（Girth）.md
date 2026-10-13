---
tags:
  - 算法题
  - 图-BFS-DFS
  - 位运算
  - 鸽笼原理
  - girth
created: 2026-08-14
updated: 2026-08-14
source: 牛客网 / 星尘航路先锋公司笔试
---

# 星际虫洞网络：基于位运算降维与 BFS 求无向图最短环（Girth）

> [!abstract] 核心套路
> 位运算的稀疏性（鸽笼原理）把百万级节点瞬间缩到 ≤120，再用 BFS 层序遍历性质在线性时间内求最短环。

---

## 一、问题重述

### 1. 核心模型
- **节点（星系）**：n 个节点，每个节点 i 拥有一个量子签名 aᵢ（0 ≤ aᵢ ≤ 10¹⁸）。
- **边（虫洞）**：无向边。节点 i 和 j（i ≠ j）之间存在边的充要条件是它们的签名按位与（Bitwise AND）结果不为零：
  ```
  aᵢ & aⱼ ≠ 0
  ```
- **目标**：寻找该图中长度最短的简单环（即图论中的 **Girth，围长**）。一个有效的环至少包含 3 个节点。若图中无环，输出 `-1`。

### 2. 约束挑战
- 数据规模：1 ≤ n ≤ 10⁶。
- 时空限制：C/C++ 3秒，Java 6秒；内存 512M/1024M。
- **难点**：节点数高达 10⁶，若直接建图，边数可能达到 O(n²) 级别，普通的求最短环算法（如对每个点跑 Dijkstra 或 BFS）会彻底超时。

---

## 二、核心破局点：位运算降维（鸽笼原理）

这是本题最精妙的"降维魔法"，它利用位运算的物理限制，将百万级的数据瞬间缩减到百级别。

### 1. 比特位数的上限
已知量子签名 aᵢ ≤ 10¹⁸。
由于 2⁶⁰ ≈ 1.15 × 10¹⁸ > 10¹⁸，每个签名在二进制下最多占用 **60 个比特位**（即二进制的第 0 到 59 位）。

### 2. 鸽笼原理与三角形判定
- 签名值为 0 的星系不与任何其他星系产生共振（AND 全为 0），不产生任何边，可直接剔除。
- 每一个非零签名 aᵢ 至少点亮（值为 1）这 60 个比特位中的 **1 个**。
- 假设剔除零值后，剩余的非零星系数量超过了 120 个（例如 **121 个**）。

我们将这 121 个非零值看作 121 只"鸽子"，它们点亮的比特位看作"鸽笼"。因为每只鸽子至少占用 1 个笼子，而总共只有 60 个笼子：

```
⌈121 只鸽子 / 60 个笼子⌉ = 3
```

根据**鸽笼原理**，**必然存在至少一个比特位，被至少 3 个不同的星系同时点亮**。

设这三个星系分别为 p, q, r，它们共同拥有比特位 b。那么：
- p & q ≠ 0（至少在第 b 位都是 1）→ p, q 连边
- q & r ≠ 0（至少在第 b 位都是 1）→ q, r 连边
- p & r ≠ 0（至少在第 b 位都是 1）→ p, r 连边

这三个点两两相连，在图上立刻构成了一个**三角形**（3 环）。
由于简单图里最短的环长度只能是 3，**此时最短环长度必为 3**。

### 3. 极速降维结论
- **若非零值数量 > 120**：无需建图，答案直接锁定为 **3**。
- **若非零值数量 ≤ 120**：图的规模变得极小（节点数 m ≤ 120）。我们直接对这 ≤ 120 个点建立邻接表，并运行求最短环算法。

---

## 三、BFS 求最短环（Girth）的数学原理

在降维到 ≤ 120 个节点的图后，我们需要寻找其精确的最短环。

### 1. BFS 树的边分类
从某源点 s 开始进行广度优先搜索（BFS），可以得到一棵 **BFS 搜索树**。
图中的所有边可以被分为两类：
- **树边**：用于扩展未访问节点的边。若 dist[w] == -1，则通过树边 (u, w) 扩展，并记录 parent[w] = u。
- **非树边**：当扫描 u 的邻居 w 时，发现 w 已经访问过且 w ≠ parent[u]，这就对应一条非树边。

非树边的出现意味着：**从源点 s 出发，有两条不同的树路径在 u 和 w 处相遇了。加上非树边 (u, w) 后，必定闭合形成环。**

### 2. 候选环长度公式
对于非树边 (u, w)，它所形成的环由 s ↝ u、非树边 (u, w)、以及 w ↝ s 拼成。
其环长候选值为：

```
cand = dist[u] + dist[w] + 1
```

> [!warning] 隐患：路径重叠问题
> 如果 s ↝ u 和 s ↝ w 的树路径在汇合前，早就通过了某个比 s 更深的最近公共祖先（LCA）x，那么从 s 到 x 的路径就会被重复计算，这在图上会形成"折返跑"而非简单环。

**解决方案与正确性证明：**
设 x = LCA(u, w)。因为树的性质保证，从 x 分叉往下到 u 和 w 的路径是互不相交的，因此真正的简单环是 x ↝ u → (u, w) → w ↝ x，其实际长度为：

```
RealLength = (dist[u] - dist[x]) + (dist[w] - dist[x]) + 1
```

由于距离 dist[x] ≥ 0，因此有：

```
RealLength = dist[u] + dist[w] + 1 - 2·dist[x] ≤ dist[u] + dist[w] + 1
```

**这说明，公式 dist[u] + dist[w] + 1 算出的结果永远是真实简单环长的一个上界**（可能因为 LCA 偏深而算大，但绝不会算小）。

### 3. 为什么枚举所有源点能取得精确值？
当我们的**源点 s 恰好挑选在最短环上**时，环会被切成两条弧，分别从 s 的左侧和右侧往下延伸。这两条弧在底部闭合时，它们的 LCA 恰好就是根节点 s。
此时 dist[x] = dist[s] = 0，上界刚好变紧，公式计算出的长度 **恰好等于真实的最短环长**。

因此，我们**枚举每一个节点作为源点 s 跑一遍 BFS**，并对所有非树边算出来的 `dist[u] + dist[w] + 1` 取全局最小值。那些不在最短环上的源点会给出偏大的上界，而一旦源点落在最短环上，就会精确命中真实 girth。

---

## 四、具体演算示例

我们来看看为什么不同奇偶长度的环，其闭合边（非树边）在 BFS 中呈现出极其规律的层级特征。

### 1. 奇数环（以 5 环为例）
设环为 a-b-c-d-e-a，源点 s = a。

```
          a (dist=0)
         / \
    (p) b   e (q) (dist=1)
       /     \
  (u) c       d (w) (dist=2)
       \_____/  <-- 非树边 (c,d)
```

- BFS 树结构：parent[c]=b, parent[d]=e。
- 处理到节点 c（dist=2）时，扫描邻居 d。发现 d 已访问（dist=2），且 d ≠ parent[c]。
- 公式计算：dist[c] + dist[d] + 1 = 2 + 2 + 1 = 5。精确命中。
- **特征**：非树边连接的两个端点处于**同一层**（dist[u] = dist[w]）。

### 2. 偶数环（以 6 环为例）
设环为 a-b-c-d-e-f-a，源点 s = a。

```
            a (dist=0)
           / \
          b   f (dist=1)
         /     \
        c       e (dist=2)
         \     /
      (u) d___/ (w) (dist=3, parent[d]=c)
```

- 处理节点 d（dist=3）时，扫描邻居 e（dist=2），满足 e ≠ parent[d]。
- 公式计算：dist[u] + dist[w] + 1 = 3 + 2 + 1 = 6。精确命中。
- **特征**：非树边连接的两个端点处于**相邻层**（|dist[u] - dist[w]| = 1）。

---

## 五、完整的 Java 实现代码

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // 使用 BufferedReader 加快输入读取
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine().trim());
        StringTokenizer st = new StringTokenizer(br.readLine());

        // 收集非零量子签名
        List<Long> v = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long val = Long.parseLong(st.nextToken());
            if (val != 0) {
                v.add(val);
            }
        }

        // 核心优化 1：利用鸽笼原理快速短路
        // 121 个 non-zero 在 60 位上必产生至少一个被 3 个及以上值共享的比特位 -> 必有三角形
        if (v.size() > 120) {
            System.out.println(3);
            return;
        }

        // 精确检查：看是否有某个 bit 确实被 >= 3 个数共享
        int[] bitCnt = new int[60];
        for (long val : v) {
            for (int b = 0; b < 60; b++) {
                if (((val >> b) & 1) != 0) {
                    if (++bitCnt[b] >= 3) {
                        System.out.println(3);
                        return;
                    }
                }
            }
        }

        // 此时图的规模极小，m <= 120
        int m = v.size();
        if (m < 3) {
            System.out.println(-1);
            return;
        }

        // 建图：邻接表
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            adj.add(new ArrayList<>());
        }

        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if ((v.get(i) & v.get(j)) != 0) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }

        // BFS 求最小环 (Girth)
        int girth = Integer.MAX_VALUE;
        for (int s = 0; s < m; s++) {
            int[] dist = new int[m];
            int[] par = new int[m];
            Arrays.fill(dist, -1);
            Arrays.fill(par, -1);

            dist[s] = 0;
            Deque<Integer> q = new ArrayDeque<>();
            q.add(s);

            while (!q.isEmpty()) {
                int u = q.poll();

                // 剪枝优化 2：深度剪枝
                // 因为 dist[w] >= dist[u] - 1，所以后续任何环长度至少为 2 * dist[u]
                // 若已经超过当前最优 girth，可提前结束本次 BFS
                if (2 * dist[u] >= girth) {
                    break;
                }

                for (int w : adj.get(u)) {
                    if (dist[w] == -1) {
                        // 未访问节点，沿着树边继续扩展
                        dist[w] = dist[u] + 1;
                        par[w] = u;
                        q.add(w);
                    } else if (w != par[u]) {
                        // 发现非树边，计算候选环长并更新全局最优解
                        girth = Math.min(girth, dist[u] + dist[w] + 1);

                        // 核心优化 3：一旦找到 3 环（理论极限最小环），可直接终止
                        if (girth == 3) {
                            System.out.println(3);
                            return;
                        }
                    }
                }
            }
        }

        System.out.println(girth == Integer.MAX_VALUE ? -1 : girth);
    }
}
```

---

## 六、复杂度分析

| 阶段 | 时间复杂度 | 空间复杂度 | 说明 |
| :--- | :--- | :--- | :--- |
| **过滤与读取** | O(n) | O(n) | 线性过滤零值。 |
| **短路判定** | O(1) | O(1) | 当 v.size() > 120 时即刻常数返回。 |
| **精确 Bit 过滤** | O(120 × 60) ≈ O(1) | O(60) ≈ O(1) | 极大减小不必要的后续建图规模。 |
| **建图阶段** | O(m²) ≤ O(120²) = 14400 | O(m²) | 节点两两 AND 检查是否连边。 |
| **求环阶段 (BFS)** | O(m·(m+E)) ≤ O(120 × 7320) | O(m) | 跑 m 次 BFS，总执行次数约 10⁵ 级。 |

---

## 面试话术

1. **为什么非零节点 > 120 就能直接答案是 3？** → 鸽笼原理：121 个非零数点亮的 60 个比特位，必有一个位被 ≥3 个数共享 → 这三个数两两 AND ≠ 0 → 构成三角形 → 最短环必为 3。
2. **为什么 BFS 枚举所有源点能求出精确 girth？** → 源点落在最短环上时，环被切成两条弧，LCA 就是源点本身（dist=0），公式 dist[u]+dist[w]+1 上界变紧，恰好等于真实环长。
3. **公式 dist[u]+dist[w]+1 会不会算大？** → 会，当两条路径的 LCA 比源点深时算大。但它是上界，枚举所有源点后最短环上的源点能精确命中，所以全局最小值就是答案。
4. **深度剪枝的原理？** → 任何环长 ≥ 2·dist[u]，若已超当前 girth 则本次 BFS 后续不可能再更新答案，提前 break。
5. **什么时候输出 -1？** → 图不连通或全是树结构（无环），girth 保持 Integer.MAX_VALUE。

---

## 关联知识点

- [[虫洞网络]] — 同名"虫洞"但不同题：那题是虚拟节点建图 + Dijkstra 最短路，这题是鸽笼原理降维 + BFS 求最短环
- [[BFS 最短路径]] — BFS 层序遍历性质
- [[位运算]] — 按位与判定连边
- [[图论建模 + BFS 最短路]] — 图论建模
