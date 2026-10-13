---
title: "分享丨【算法题单】图论算法（DFS/BFS/拓扑排序/基环树/最短路/最小生成树/网络流） - 讨论 - 力扣（LeetCode）"
source: "https://leetcode.cn/discuss/post/3581143/fen-xiang-gun-ti-dan-tu-lun-suan-fa-dfsb-qyux/"
author:
published:
created: 2026-07-08
description: "一、图的遍历§1.1 深度优先搜索（DFS）找连通块、判断是否有环（如 207 题）等。部分题目做法不止一种。模板（计算每个连通块的大小）：547. 省份数量1971. 寻找图中是否存在路径797. 所有可能的路径 13831306. 跳跃游戏 III 1397841. 钥匙和房间 14122316. 统计无向图中无法互相到达点对数 16041319. 连通网络的操作次数 16332492. 两个城市间路径的最小分数 16803310. 移除可疑的方法 17112685. 统"
tags:
  - "clippings"
updated: 2026-07-14
---
分享丨【算法题单】图论算法（DFS/BFS/拓扑排序/基环树/最短路/最小生成树/网络流）

![](https://assets.leetcode.cn/aliyun-lc-upload/users/endlesscheng/avatar_1690721039.png?x-oss-process=image%2Fformat%2Cwebp)

[灵茶山艾府](https://leetcode.cn/u/endlesscheng/)

325765

2024.03.05

2026.02.09

发布于 未知归属地

## 一、图的遍历

### §1.1 深度优先搜索（DFS）

找连通块、判断是否有环（如 207 题）等。部分题目 **做法不止一种** 。

模板（计算每个连通块的大小）：

```java
class Solution {
    public List<Integer> solve(int n, int[][] edges) {
        // 节点编号从 0 到 n-1
        List<Integer>[] g = new ArrayList[n];
        Arrays.setAll(g, _ -> new ArrayList<>());
        for (int[] e : edges) {
            int x = e[0];
            int y = e[1];
            g[x].add(y);
            g[y].add(x); // 无向图
        }

        // 计算每个连通块的大小
        List<Integer> ans = new ArrayList<>();
        boolean[] vis = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!vis[i]) { // i 没有访问过
                int size = dfs(i, vis, g);
                ans.add(size);
            }
        }
        return ans;
    }

    private int dfs(int x, boolean[] vis, List<Integer>[] g) {
        vis[x] = true; // 避免重复访问节点
        int size = 1;
        for (int y : g[x]) {
            if (!vis[y]) {
                size += dfs(y, vis, g);
            }
        }
        return size;
    }
}
```

- [x] 547\. 省份数量
[[547. 省份数量 - 力扣（LeetCode）]]
- [ ] [1971.  寻找图中是否存在路径](https://leetcode.cn/problems/find-if-path-exists-in-graph/)
[[1971. 寻找图中是否存在路径 - 力扣（LeetCode）]]
- [ ] [797.  所有可能的路径 1383](https://leetcode.cn/problems/all-paths-from-source-to-target/)
[[797. 所有可能的路径 - 力扣（LeetCode）]]
- [ ] [1306.  跳跃游戏 III 1397](https://leetcode.cn/problems/jump-game-iii/)
[[1306. 跳跃游戏 III - 力扣（LeetCode）]]
- [ ] [841.  钥匙和房间 1412](https://leetcode.cn/problems/keys-and-rooms/)
- [ ] [2316.  统计无向图中无法互相到达点对数 1604](https://leetcode.cn/problems/count-unreachable-pairs-of-nodes-in-an-undirected-graph/)
[[2316. 统计无向图中无法互相到达点对数 - 力扣（LeetCode）]]
- [ ] [1319.  连通网络的操作次数 1633](https://leetcode.cn/problems/number-of-operations-to-make-network-connected/)
- [ ] [2492.  两个城市间路径的最小分数 1680](https://leetcode.cn/problems/minimum-score-of-a-path-between-two-cities/)
- [ ] [3310.  移除可疑的方法 1711](https://leetcode.cn/problems/remove-methods-from-project/)
[[3310. 移除可疑的方法 - 力扣（LeetCode）]]
- [ ] [2685.  统计完全连通分量的数量 1769](https://leetcode.cn/problems/count-the-number-of-complete-components/)
- [ ] [2192.  有向无环图中一个节点的所有祖先 1788](https://leetcode.cn/problems/all-ancestors-of-a-node-in-a-directed-acyclic-graph/)
[[2192. 有向无环图中一个节点的所有祖先 - 力扣（LeetCode）]]
- [ ] [3387.  两天自由外汇交易后的最大货币数 1788](https://leetcode.cn/problems/maximize-amount-after-two-days-of-conversions/)
- [ ] [924.  尽量减少恶意软件的传播 1869 附思考题](https://leetcode.cn/problems/minimize-malware-spread/)
- [ ] [2101.  引爆最多的炸弹 1880](https://leetcode.cn/problems/detonate-the-maximum-bombs/)
- [ ] [721.  账户合并](https://leetcode.cn/problems/accounts-merge/)
- [ ] [207.  课程表 三色标记法判环](https://leetcode.cn/problems/course-schedule/)
- [ ] [802.  找到最终的安全状态 1962](https://leetcode.cn/problems/find-eventual-safe-states/)
- [ ] [3695.  交换元素后的最大交替和 1984](https://leetcode.cn/problems/maximize-alternating-sum-using-swaps/)
- [ ] [928.  尽量减少恶意软件的传播 II 1985](https://leetcode.cn/problems/minimize-malware-spread-ii/)
- [ ] [2092.  找出知晓秘密的所有专家 2004](https://leetcode.cn/problems/find-all-people-with-secret/)
- [ ] [3108.  带权图里旅途的最小代价 2109](https://leetcode.cn/problems/minimum-cost-walk-in-weighted-graph/)
- [ ] [LCP 07. 传递信息](https://leetcode.cn/problems/chuan-di-xin-xi/)
- [ ] [261.  以图判树 （会员题）](https://leetcode.cn/problems/graph-valid-tree/)
- [ ] [323.  无向图中连通分量的数目 （会员题）](https://leetcode.cn/problems/number-of-connected-components-in-an-undirected-graph/)

**思维扩展** ：

- [ ] [1298.  你能从盒子里获得的最大糖果数 1825](https://leetcode.cn/problems/maximum-candies-you-can-get-from-boxes/)

### §1.2 广度优先搜索（BFS）

求最短路等。要求边权都是 （或者说都是同一个正数）。

模板（单源最短路）：

```java
class Solution {
    // 计算从 start 到各个节点的最短路长度
    // 如果节点不可达，则最短路长度为 -1
    // 节点编号从 0 到 n-1，边权均为 1
    public int[] bfs(int n, int[][] edges, int start) {
        List<Integer>[] g = new ArrayList[n];
        Arrays.setAll(g, _ -> new ArrayList<>());
        for (int[] e : edges) {
            int x = e[0], y = e[1];
            g[x].add(y);
            g[y].add(x); // 无向图
        }

        int[] dis = new int[n];
        Arrays.fill(dis, -1); // -1 表示尚未访问到
        Queue<Integer> q = new ArrayDeque<>();
        dis[start] = 0;
        q.offer(start);
        while (!q.isEmpty()) {
            int x = q.poll();
            for (int y : g[x]) {
                if (dis[y] < 0) {
                    dis[y] = dis[x] + 1;
                    q.offer(y);
                }
            }
        }
        return dis;
    }
}
```

- [ ] [3243.  新增道路查询后的最短距离 I 1568](https://leetcode.cn/problems/shortest-distance-after-road-addition-queries-i/)
[[3243. 新增道路查询后的最短距离 I - 力扣（LeetCode）]]
- [x] 1311\. 获取你好友已观看的视频 1653
[[1311. 获取你好友已观看的视频 - 力扣（LeetCode）]]
- [ ] [3015.  按距离统计房屋对数目 I 1658](https://leetcode.cn/problems/count-the-number-of-houses-at-a-certain-distance-i/)
- [ ] [1129.  颜色交替的最短路径 1780](https://leetcode.cn/problems/shortest-path-with-alternating-colors/)
[[1129. 颜色交替的最短路径 - 力扣（LeetCode）]]这个很妙
- [ ] [2039.  网络空闲的时刻 1865](https://leetcode.cn/problems/the-time-when-the-network-becomes-idle/)
- [ ] [2608.  图中的最短环 1904](https://leetcode.cn/problems/shortest-cycle-in-a-graph/)
- [ ] [815.  公交路线 1964](https://leetcode.cn/problems/bus-routes/)
- [ ] [3807.  修复边以遍历图的最小成本 （会员题）](https://leetcode.cn/problems/minimum-cost-to-repair-edges-to-traverse-a-graph/)

### §1.3 图论建模 + BFS 最短路

把状态抽象成图上的点，用 BFS 遍历这张图，计算从初始状态到目标状态的最短路长度。

可以锻炼状态设计能力。

- [ ] [433.  最小基因变化](https://leetcode.cn/problems/minimum-genetic-mutation/)
- [ ] [1284.  转化为全零矩阵的最少反转次数 1811](https://leetcode.cn/problems/minimum-number-of-flips-to-convert-binary-matrix-to-zero-matrix/)
[[1284. 转化为全零矩阵的最少反转次数 - 力扣（LeetCode）]]
- [x] [773.  滑动谜题 1815](https://leetcode.cn/problems/sliding-puzzle/)
[[773. 滑动谜题 - 力扣（LeetCode）]]
- [ ] [752.  打开转盘锁 1878](https://leetcode.cn/problems/open-the-lock/)
- [ ] [3690.  拆分合并数组 1982](https://leetcode.cn/problems/split-and-merge-array-transformation/)
[[3690. 拆分合并数组 - 力扣（LeetCode）]]
- [ ] [301.  删除无效的括号](https://leetcode.cn/problems/remove-invalid-parentheses/)
- [ ] [514.  自由之路](https://leetcode.cn/problems/freedom-trail/)
- [ ] [847.  访问所有节点的最短路径 2201](https://leetcode.cn/problems/shortest-path-visiting-all-nodes/)
- [ ] [854.  相似度为 K 的字符串 2377](https://leetcode.cn/problems/k-similar-strings/)
- [ ] [127.  单词接龙 双向广搜](https://leetcode.cn/problems/word-ladder/)
- [ ] [488.  祖玛游戏](https://leetcode.cn/problems/zuma-game/)
- [ ] [1197.  进击的骑士 （会员题）](https://leetcode.cn/problems/minimum-knight-moves/)
- [ ] [3141.  最大汉明距离 （会员题）](https://leetcode.cn/problems/maximum-hamming-distances/)

**专题：跳跃游戏**

- [ ] [2998.  使 X 和 Y 相等的最少操作次数 1795](https://leetcode.cn/problems/minimum-number-of-operations-to-make-x-and-y-equal/)
- [ ] [1345.  跳跃游戏 IV 1810](https://leetcode.cn/problems/jump-game-iv/)
- [ ] [2059.  转化数字的最小运算数 1850](https://leetcode.cn/problems/minimum-operations-to-convert-number/)
- [ ] [1654.  到家的最少跳跃次数 2124](https://leetcode.cn/problems/minimum-jumps-to-reach-home/)
- [ ] [3629.  通过质数传送到达终点的最少跳跃次数 2139](https://leetcode.cn/problems/minimum-jumps-to-reach-end-via-prime-teleportation/)
- [ ] [LCP 09. 最小跳跃次数](https://leetcode.cn/problems/zui-xiao-tiao-yue-ci-shu/)

注：关于 **网格图** 的 DFS 和 BFS，请看 [网格图题单](https://leetcode.cn/circle/discuss/YiXPXW/) 。

## 二、拓扑排序

把拓扑排序想象成一个黑盒，给它一堆杂乱的先修课约束，它会给你一个井井有条的课程学习安排。

这一种在图上的「排序」，可以把杂乱的点排成一排。前提条件是图中无环，从而保证每条边都是从排在前面的点，指向排在后面的点。即对于任意有向边 ， 一定在 之前。

### §2.1 拓扑排序

模板：

```java
class Solution {
    // 返回有向无环图（DAG）的其中一个拓扑序
    // 如果图中有环，返回空列表
    // 节点编号从 0 到 n-1
    public List<Integer> topologicalSort(int n, int[][] edges) {
        List<Integer>[] g = new ArrayList[n];
        Arrays.setAll(g, _ -> new ArrayList<>());
        int[] inDeg = new int[n];
        for (int[] e : edges) {
            int x = e[0];
            int y = e[1];
            g[x].add(y);
            inDeg[y]++; // 统计 y 的先修课数量
        }

        Queue<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (inDeg[i] == 0) { // 没有先修课，可以直接上
                q.offer(i); // 加入学习队列
            }
        }

        List<Integer> topoOrder = new ArrayList<>();
        while (!q.isEmpty()) {
            int x = q.poll();
            topoOrder.add(x);
            for (int y : g[x]) {
                inDeg[y]--; // 修完 x 后，y 的先修课数量减一
                if (inDeg[y] == 0) { // y 的先修课全部上完
                    q.offer(y); // 加入学习队列
                }
            }
        }

        if (topoOrder.size() < n) { // 图中有环
            return List.of();
        }
        return topoOrder;
    }
}
```

学习拓扑排序前，请先完成 1557\. 可以到达所有点的最少点数目 ，有助于理解拓扑排序。

- [x] [210.  课程表 II](https://leetcode.cn/problems/course-schedule-ii/)
[[210. 课程表 II - 力扣（LeetCode）]]
- [x] [2115.  从给定原材料中找到所有可以做出的菜 1679](https://leetcode.cn/problems/find-all-possible-recipes-from-given-supplies/)
[[2115. 从给定原材料中找到所有可以做出的菜 - 力扣（LeetCode）]]
- [ ] [2392.  给定条件下构造矩阵 1961](https://leetcode.cn/problems/build-a-matrix-with-conditions/)
- [ ] [802.  找到最终的安全状态 1962](https://leetcode.cn/problems/find-eventual-safe-states/)
- [ ] [1591.  奇怪的打印机 II 2291](https://leetcode.cn/problems/strange-printer-ii/)
- [ ] [1203.  项目管理 2419](https://leetcode.cn/problems/sort-items-by-groups-respecting-dependencies/)
- [ ] [1632.  矩阵转换后的排名 2530](https://leetcode.cn/problems/rank-transform-of-a-matrix/)
- [ ] [2603.  收集树中金币 2712](https://leetcode.cn/problems/collect-coins-in-a-tree/)
- [ ] [LCR 114. 火星词典](https://leetcode.cn/problems/Jf1JuT/)
- [ ] [444.  序列重建 （会员题）拓扑序是否唯一](https://leetcode.cn/problems/sequence-reconstruction/)
- [ ] [3481.  应用替换 （会员题）也可以记忆化搜索](https://leetcode.cn/problems/apply-substitutions/)
- [ ] [269.  火星词典 （会员题）](https://leetcode.cn/problems/alien-dictionary/)
- [ ] [2371.  最小化网格中的最大值 （会员题）同 1632 题](https://leetcode.cn/problems/minimize-maximum-value-in-a-grid/)

**思维扩展** ：

- [ ] [310.  最小高度树](https://leetcode.cn/problems/minimum-height-trees/)
- [ ] [1361.  验证二叉树](https://leetcode.cn/problems/validate-binary-tree-nodes/)

### §2.2 在拓扑序上 DP

一般是刷表法。

- [ ] [851.  喧闹和富有 1783](https://leetcode.cn/problems/loud-and-rich/)
- [ ] [2050.  并行课程 III 2084](https://leetcode.cn/problems/parallel-courses-iii/)
- [ ] [3620.  恢复网络路径](https://leetcode.cn/problems/network-recovery-pathways/)
- [ ] [1857.  有向图中最大颜色值 2313](https://leetcode.cn/problems/largest-color-value-in-a-directed-graph/)
- [ ] [1136.  并行课程 （会员题）](https://leetcode.cn/problems/parallel-courses/)

### §2.3 基环树

基环树介绍

- [ ] [2359.  找到离给定两个节点最近的节点 1715](https://leetcode.cn/problems/find-closest-node-to-given-two-nodes/)
- [ ] [2360.  图中的最长环 1897](https://leetcode.cn/problems/longest-cycle-in-a-graph/)
- [ ] [684.  冗余连接 做法不止一种](https://leetcode.cn/problems/redundant-connection/)
- [ ] [685.  冗余连接 II](https://leetcode.cn/problems/redundant-connection-ii/)
- [ ] [2876.  有向图访问计数 2210](https://leetcode.cn/problems/count-visited-nodes-in-a-directed-graph/)
- [ ] [2127.  参加会议的最多员工数 2449](https://leetcode.cn/problems/maximum-employees-to-be-invited-to-a-meeting/)
- [ ] [2836.  在传球游戏中最大化函数值 2769 做法不止一种](https://leetcode.cn/problems/maximize-value-of-function-in-a-ball-passing-game/)
- [ ] [LCP 21. 追逐游戏](https://leetcode.cn/problems/Za25hA/)
- [ ] [2204.  无向图中到环的距离 （会员题）](https://leetcode.cn/problems/distance-to-a-cycle-in-undirected-graph/)

**思维扩展** ：

- [ ] [287.  寻找重复数](https://leetcode.cn/problems/find-the-duplicate-number/)

## 三、最短路

### §3.1 单源最短路：Dijkstra 算法

Dijkstra 算法介绍

模板：

```java
class Solution {
    // 返回从起点 start 到每个点的最短路长度 dis，如果节点 x 不可达，则 dis[x] = Integer.MAX_VALUE
    // 要求：没有负数边权
    // 时间复杂度 O(n + mlogm)，注意堆中有 O(m) 个元素
    private int[] shortestPathDijkstra(int n, int[][] edges, int start) {
        // 注：如果节点编号从 1 开始（而不是从 0 开始），可以把 n 加一
        List<int[]>[] g = new ArrayList[n]; // 邻接表
        Arrays.setAll(g, _ -> new ArrayList<>());
        for (int[] e : edges) {
            int x = e[0];
            int y = e[1];
            int wt = e[2];
            g[x].add(new int[]{y, wt});
            // g[y].add(new int[]{x, wt}); // 无向图加上这行
        }

        int[] dis = new int[n]; // **如果数据范围大，改成 long[]**
        Arrays.fill(dis, Integer.MAX_VALUE);
        // 堆中保存 (起点到节点 x 的最短路长度，节点 x)
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        dis[start] = 0; // 起点到自己的距离是 0
        pq.offer(new int[]{0, start});

        while (!pq.isEmpty()) {
            int[] p = pq.poll();
            int disX = p[0];
            int x = p[1];
            if (disX > dis[x]) { // x 之前出堆过
                continue;
            }
            for (int[] e : g[x]) {
                int y = e[0];
                int wt = e[1];
                int newDisY = disX + wt;
                if (newDisY < dis[y]) {
                    dis[y] = newDisY; // 更新 x 的邻居的最短路
                    // 懒更新堆：只插入数据，不更新堆中数据
                    // 相同节点可能有多个不同的 newDisY，除了最小的 newDisY，其余值都会触发上面的 continue
                    pq.offer(new int[]{newDisY, y});
                }
            }
        }

        return dis;
    }
}
```

- [x] [743.  网络延迟时间](https://leetcode.cn/problems/network-delay-time/)
[[743. 网络延迟时间 - 力扣（LeetCode）]]
- [ ] [3341.  到达最后一个房间的最少时间 I 1721 网格图](https://leetcode.cn/problems/find-minimum-time-to-reach-last-room-i/)
[[3341. 到达最后一个房间的最少时间 I - 力扣（LeetCode）]]
- [x] [3112.  访问消失节点的最少时间 1757 理解原理](https://leetcode.cn/problems/minimum-time-to-visit-disappearing-nodes/)
[[3112. 访问消失节点的最少时间 - 力扣（LeetCode）]]
- [ ] [2642.  设计可以求最短路径的图类 1811](https://leetcode.cn/problems/design-graph-with-shortest-path-calculator/)
- [x] [3604.  有向图中到达终点的最少时间 1845](https://leetcode.cn/problems/minimum-time-to-reach-destination-in-directed-graph/)
[[3604. 有向图中到达终点的最少时间 - 力扣（LeetCode）]]
- [ ] [1514.  概率最大的路径 1846](https://leetcode.cn/problems/path-with-maximum-probability/)
- [ ] [3650.  边反转的最小路径总成本 1854](https://leetcode.cn/problems/minimum-cost-path-with-edge-reversals/)
- [ ] [3342.  到达最后一个房间的最少时间 II 1862 网格图](https://leetcode.cn/problems/find-minimum-time-to-reach-last-room-ii/)
- [x] [1631.  最小体力消耗路径 1948 网格图 做法不止一种](https://leetcode.cn/problems/path-with-minimum-effort/)
[[1631. 最小体力消耗路径 - 力扣（LeetCode）]]
- [ ] [1786.  从第一个节点出发到最后一个节点的受限路径数 2079](https://leetcode.cn/problems/number-of-restricted-paths-from-first-to-last-node/)
- [ ] [3123.  最短路径中的边 2093](https://leetcode.cn/problems/find-edges-in-shortest-paths/)
- [ ] [1976.  到达目的地的方案数 2095](https://leetcode.cn/problems/number-of-ways-to-arrive-at-destination/)
[[1976. 到达目的地的方案数 - 力扣（LeetCode）]]
这题有东西
- [ ] [778.  水位上升的泳池中游泳 2097 网格图 做法不止一种](https://leetcode.cn/problems/swim-in-rising-water/)
- [ ] [2662.  前往目标的最小代价 2154](https://leetcode.cn/problems/minimum-cost-of-a-path-with-special-roads/)
- [ ] [3377.  使两个整数相等的数位操作 2186](https://leetcode.cn/problems/digit-operations-to-make-two-integers-equal/)
- [ ] [2045.  到达目的地的第二短时间 2202 也可以 BFS](https://leetcode.cn/problems/second-minimum-time-to-reach-destination/)
- [ ] [3419.  图的最大边权的最小值 2243 做法不止一种](https://leetcode.cn/problems/minimize-the-maximum-edge-weight-of-graph/)
- [ ] [882.  细分图中的可到达节点 2328](https://leetcode.cn/problems/reachable-nodes-in-subdivided-graph/)
- [ ] [2203.  得到要求路径的最小带权子图 2364](https://leetcode.cn/problems/minimum-weighted-subgraph-with-the-required-paths/)
- [ ] [2577.  在网格图中访问一个格子的最少时间 2382 网格图](https://leetcode.cn/problems/minimum-time-to-visit-a-cell-in-a-grid/)
- [ ] [818.  赛车 2392](https://leetcode.cn/problems/race-car/)
- [ ] [1928.  规定时间内到达终点的最小花费 2413](https://leetcode.cn/problems/minimum-cost-to-reach-destination-in-time/)
- [ ] [787.  K 站中转内最便宜的航班 类似 1928 题](https://leetcode.cn/problems/cheapest-flights-within-k-stops/)
- [ ] [2699.  修改图中的边权 2874](https://leetcode.cn/problems/modify-graph-edge-weights/)
- [ ] [1810.  隐藏网格下的最小消耗路径 （会员题）](https://leetcode.cn/problems/minimum-path-cost-in-a-hidden-grid/)
- [ ] [2093.  前往目标城市的最小费用 （会员题）](https://leetcode.cn/problems/minimum-cost-to-reach-city-with-discounts/)
- [ ] [2473.  购买苹果的最低成本 （会员题）](https://leetcode.cn/problems/minimum-cost-to-buy-apples/)
- [ ] [2737.  找到最近的标记节点 （会员题）](https://leetcode.cn/problems/find-the-closest-marked-node/)

**分层图最短路** ：

- [ ] [LCP 35. 电动车游城市](https://leetcode.cn/problems/DFPeFJ/)
- [ ] [3599.  划分数组得到最小 XOR 做法不止一种](https://leetcode.cn/problems/partition-array-to-minimize-xor/)
- [ ] [3594.  所有人渡河所需的最短时间 2604](https://leetcode.cn/problems/minimum-time-to-transport-all-individuals/)
- [ ] [3778.  排除一个最大权重边的最小距离 （会员题）](https://leetcode.cn/problems/minimum-distance-excluding-one-maximum-weighted-edge/)
- [ ] [2714.  找到 K 次跨越的最短路径 （会员题）](https://leetcode.cn/problems/find-shortest-path-with-k-hops/)

**SPFA 与差分约束** ：

> 力扣上的 SPFA 题很少。

- [ ] [2589.  完成所有任务的最少时间 2381 做法不止一种](https://leetcode.cn/problems/minimum-time-to-complete-all-tasks/)

### §3.2 全源最短路：Floyd 算法

Floyd 算法本质是三维 DP。理解 DP 一定要基于空间优化前的写法。

带你发明 Floyd 算法：从记忆化搜索到递推

模板：

```java
class Solution {
    // 返回一个二维列表，其中 (i,j) 这一项表示从 i 到 j 的最短路长度
    // 如果无法从 i 到 j，则最短路长度为 Long.MAX_VALUE / 2
    // 允许负数边权
    // 如果计算完毕后，存在 i，使得从 i 到 i 的最短路长度小于 0，说明图中有负环
    // 节点编号从 0 到 n-1
    // 时间复杂度 O(n^3 + m)，其中 m 是 edges 的长度
    public long[][] shortestPathFloyd(int n, int[][] edges) {
        final long INF = Long.MAX_VALUE / 2; // 防止加法溢出
        long[][] f = new long[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(f[i], INF);
            f[i][i] = 0;
        }

        for (int[] e : edges) {
            int x = e[0];
            int y = e[1];
            int wt = e[2];
            f[x][y] = Math.min(f[x][y], wt); // 如果有重边，取边权最小值
            f[y][x] = Math.min(f[y][x], wt); // 无向图
        }

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (f[i][k] == INF) { // 针对稀疏图的优化
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    f[i][j] = Math.min(f[i][j], f[i][k] + f[k][j]);
                }
            }
        }
        return f;
    }
}
```

- [x] [2642.  设计可以求最短路径的图类 1811 动态加边](https://leetcode.cn/problems/design-graph-with-shortest-path-calculator/)
[[2642. 设计可以求最短路径的图类 - 力扣（LeetCode）]]
- [x] [1334.  阈值距离内邻居最少的城市 1855](https://leetcode.cn/problems/find-the-city-with-the-smallest-number-of-neighbors-at-a-threshold-distance/)
[[1334. 阈值距离内邻居最少的城市 - 力扣（LeetCode）]]
- [ ] [2976.  转换字符串的最小成本 I 1882](https://leetcode.cn/problems/minimum-cost-to-convert-string-i/)
- [ ] [2959.  关闭分部的可行集合数目 2077](https://leetcode.cn/problems/number-of-possible-sets-of-closing-branches/)
- [ ] [2977.  转换字符串的最小成本 II 2696](https://leetcode.cn/problems/minimum-cost-to-convert-string-ii/)

**Bitset 优化 Floyd**

- [ ] [1462.  课程表 IV 1693](https://leetcode.cn/problems/course-schedule-iv/)
- [ ] [2101.  引爆最多的炸弹](https://leetcode.cn/problems/detonate-the-maximum-bombs/)

## 四、最小生成树

涉及到 Kruskal 算法和 Prim 算法。前者一般用于稀疏图，后者一般用于稠密图。

> 注：如果要求最大生成树，把边权从大到小排序。

Kruskal 算法模板（用到了并查集，完整模板见 [数据结构题单](https://leetcode.cn/circle/discuss/mOr1u6/) ）：

```java
class UnionFind {
    private final int[] fa; // 代表元
    public int cc; // 连通块个数

    UnionFind(int n) {
        // 一开始有 n 个集合 {0}, {1}, ..., {n-1}
        // 集合 i 的代表元是自己
        fa = new int[n];
        for (int i = 0; i < n; i++) {
            fa[i] = i;
        }
        cc = n;
    }

    // 返回 x 所在集合的代表元
    // 同时做路径压缩，也就是把 x 所在集合中的所有元素的 fa 都改成代表元
    public int find(int x) {
        // 如果 fa[x] == x，则表示 x 是代表元
        if (fa[x] != x) {
            fa[x] = find(fa[x]); // fa 改成代表元
        }
        return fa[x];
    }

    // 把 from 所在集合合并到 to 所在集合中
    // 返回是否合并成功
    public boolean merge(int from, int to) {
        int x = find(from);
        int y = find(to);
        if (x == y) { // from 和 to 在同一个集合，不做合并
            return false;
        }
        fa[x] = y; // 合并集合。修改后就可以认为 from 和 to 在同一个集合了
        cc--; // 成功合并，连通块个数减一
        return true;
    }
}

class Solution {
    // 计算图的最小生成树的边权之和
    // 如果图不连通，返回 Long.MAX_VALUE
    // 节点编号从 0 到 n-1
    // 时间复杂度 O(n + mlogm)，其中 m 是 edges 的长度
    public long mstKruskal(int n, int[][] edges) {
        Arrays.sort(edges, Comparator.comparingInt(e -> e[2]));

        UnionFind uf = new UnionFind(n);
        long sumWt = 0;
        for (int[] e : edges) {
            int x = e[0], y = e[1], wt = e[2];
            if (uf.merge(x, y)) {
                sumWt += wt;
            }
        }

        if (uf.cc > 1) { // 图不连通
            return Long.MAX_VALUE;
        }
        return sumWt;
    }
}
```

- [ ] [1584.  连接所有点的最小费用 1858](https://leetcode.cn/problems/min-cost-to-connect-all-points/)
- [ ] [3600.  升级后最大生成树稳定性 2301 做法不止一种](https://leetcode.cn/problems/maximize-spanning-tree-stability-with-upgrades/)
- [ ] [1489.  找到最小生成树里的关键边和伪关键边 2572](https://leetcode.cn/problems/find-critical-and-pseudo-critical-edges-in-minimum-spanning-tree/)
- [ ] [1135.  最低成本连通所有城市 （会员题）](https://leetcode.cn/problems/connecting-cities-with-minimum-cost/)
- [ ] [1168.  水资源分配优化 （会员题）](https://leetcode.cn/problems/optimize-water-distribution-in-a-village/)

**思维扩展**

- [ ] [3219.  切蛋糕的最小总开销 II](https://leetcode.cn/problems/minimum-cost-for-cutting-cake-ii/)

## 五、欧拉路径/欧拉回路

涉及到 Hierholzer 算法。

- [ ] [332.  重新安排行程](https://leetcode.cn/problems/reconstruct-itinerary/)
- [ ] [753.  破解保险箱 2274](https://leetcode.cn/problems/cracking-the-safe/)
- [ ] [2097.  合法重新排列数对 2651](https://leetcode.cn/problems/valid-arrangement-of-pairs/)

## 六、强连通分量/双连通分量

涉及到 Tarjan 算法。

- [ ] [1192.  查找集群内的关键连接 2085](https://leetcode.cn/problems/critical-connections-in-a-network/)
- [ ] [1568.  使陆地分离的最少天数 2209](https://leetcode.cn/problems/minimum-number-of-days-to-disconnect-island/)
- [ ] [LCP 54. 夺回据点](https://leetcode.cn/problems/s5kipK/)
- [ ] [3383.  施法所需最低符文数量 （会员题）](https://leetcode.cn/problems/minimum-runes-to-add-to-cast-spell/)

## 七、二分图染色

模板（交替染色法）：

```java
class Solution {
    // 返回图的二染色
    // 如果是二分图，返回每个节点的颜色，用 1 和 2 表示两种颜色
    // 如果不是二分图，返回空列表
    // 时间复杂度 O(n+m)，n 是点数，m 是边数
    private int[] colorBipartite(int n, int[][] edges) {
        // 建图（节点编号从 0 到 n-1）
        List<Integer>[] g = new ArrayList[n];
        Arrays.setAll(g, _ -> new ArrayList<>());
        for (int[] e : edges) {
            int x = e[0], y = e[1];
            g[x].add(y);
            g[y].add(x);
        }

        // colors[i] = 0 表示未访问节点 i
        // colors[i] = 1 表示节点 i 为红色
        // colors[i] = 2 表示节点 i 为蓝色
        int[] colors = new int[n];

        for (int i = 0; i < n; i++) {
            if (colors[i] == 0 && !dfs(i, 1, g, colors)) {
                // 从节点 i 开始递归，发现 i 所在连通块不是二分图
                return new int[]{};
            }
        }
        return colors;
    }

    private boolean dfs(int x, int c, List<Integer>[] g, int[] colors) {
        colors[x] = c; // 节点 x 染成颜色 c
        for (int y : g[x]) {
            // 邻居 y 的颜色与 x 的相同，说明不是二分图，返回 false
            // 或者继续递归，发现不是二分图，返回 false
            if (colors[y] == c || 
                colors[y] == 0 && !dfs(y, 3 - c, g, colors)) { // 1 和 2 交替染色
                return false;
            }
        }
        return true;
    }
}
```

- [ ] [785.  判断二分图 1625](https://leetcode.cn/problems/is-graph-bipartite/)
- [ ] [886.  可能的二分法 1795](https://leetcode.cn/problems/possible-bipartition/)
- [ ] [3710.  最大划分因子 2135](https://leetcode.cn/problems/maximum-partition-factor/)

关于二分图的最大匹配，见下面网络流的题目。其中标有「一对一」的题目也可以用带权二分图最大匹配做。

## 八、网络流

由于有其他做法（比如状压 DP），难度分仅供参考。

- [ ] [1947.  最大兼容性评分和 1704 一对一](https://leetcode.cn/problems/maximum-compatibility-score-sum/)
[[1947. 最大兼容性评分和 - 力扣（LeetCode）]]
- [ ] [3376.  破解锁的最少时间 I 1793 一对一](https://leetcode.cn/problems/minimum-time-to-break-locks-i/)
- [ ] [2850.  将石头分散到网格图的最少移动次数 2001 一对多](https://leetcode.cn/problems/minimum-moves-to-spread-stones-over-grid/)
- [ ] [1879.  两个数组最小的异或值之和 2145 一对一](https://leetcode.cn/problems/minimum-xor-sum-of-two-arrays/)
- [ ] [1349.  参加考试的最大学生数 2386 二分图最大独立集](https://leetcode.cn/problems/maximum-students-taking-exam/)
- [ ] [2172.  数组的最大与和 2392 多对一](https://leetcode.cn/problems/maximum-and-sum-of-array/)
- [ ] [3276.  选择矩阵中单元格的最大得分 2403](https://leetcode.cn/problems/select-cells-in-grid-with-maximum-score/)
- [ ] [1595.  连通两组点的最小成本 2538 带权二分图最小边覆盖](https://leetcode.cn/problems/minimum-cost-to-connect-two-groups-of-points/)
- [ ] [3257.  放三个车的价值之和最大 II 2553](https://leetcode.cn/problems/maximum-value-sum-by-placing-three-rooks-ii/)
- [ ] [LCP 04. 覆盖 二分图最大匹配·模板题](https://leetcode.cn/problems/broken-board-dominoes/)
- [ ] [LCP 38. 守卫城堡 最小割](https://leetcode.cn/problems/7rLGCR/)
- [ ] [1820.  最多邀请的个数 （会员题）二分图最大匹配·模板题](https://leetcode.cn/problems/maximum-number-of-accepted-invitations/)
- [ ] [2403.  杀死所有怪物的最短时间 （会员题）同 3376 题](https://leetcode.cn/problems/minimum-time-to-kill-all-monsters/)
- [ ] [3385.  破解锁的最少时间 II （会员题）同 3376 题](https://leetcode.cn/problems/minimum-time-to-break-locks-ii/)
- [ ] [1066.  校园自行车分配 II （会员题）一对一，但不是完美匹配](https://leetcode.cn/problems/campus-bikes-ii/)
- [ ] [2123.  使矩阵中的 1 互不相邻的最小操作数 （会员题）二分图最大独立集](https://leetcode.cn/problems/minimum-operations-to-remove-adjacent-ones-in-matrix/)

**模拟费用流**

- [ ] [2463.  最小移动总距离 做到](https://leetcode.cn/problems/minimum-total-distance-traveled/)

## 九、其他

- [ ] [1042.  不邻接植花 1712](https://leetcode.cn/problems/flower-planting-with-no-adjacent/)
- [ ] [1761.  一个图中连通三元组的最小度数 2005](https://leetcode.cn/problems/minimum-degree-of-a-connected-trio-in-a-graph/)
- [ ] [2508.  添加边使所有节点度数都为偶数 2060](https://leetcode.cn/problems/add-edges-to-make-degrees-of-all-nodes-even/)
- [ ] [1579.  保证图可完全遍历 2132](https://leetcode.cn/problems/remove-max-number-of-edges-to-keep-graph-fully-traversable/)
- [ ] [2065.  最大化一张图中的路径价值 2178](https://leetcode.cn/problems/maximum-path-quality-of-a-graph/)
- [ ] [1697.  检查边长度限制的路径是否存在 2300](https://leetcode.cn/problems/checking-existence-of-edge-length-limited-paths/)
- [ ] [2242.  节点序列的最大得分 2304](https://leetcode.cn/problems/maximum-score-of-a-node-sequence/)
- [ ] [2493.  将节点分成尽可能多的组 2415 **推荐**](https://leetcode.cn/problems/divide-nodes-into-the-maximum-number-of-groups/)
- [ ] [1782.  统计点对的数目 2457](https://leetcode.cn/problems/count-pairs-of-nodes/)
- [ ] [3666.  使二进制字符串全为 1 的最少操作次数 2477 进阶 BFS / Gale-Ryser 定理](https://leetcode.cn/problems/minimum-operations-to-equalize-binary-string/)
- [ ] [2612.  最少翻转操作数 2824 进阶 BFS](https://leetcode.cn/problems/minimum-reverse-operations/)
- [ ] [3435.  最短公共超序列的字母出现频率 3028](https://leetcode.cn/problems/frequencies-of-shortest-supersequences/)
- [ ] [466.  统计重复个数 做到](https://leetcode.cn/problems/count-the-repetitions/)
- [ ] [LCP 16. 游乐园的游览计划](https://leetcode.cn/problems/you-le-yuan-de-you-lan-ji-hua/)
- [ ] [277.  搜寻名人 （会员题）](https://leetcode.cn/problems/find-the-celebrity/)
- [ ] [1724.  检查边长度限制的路径是否存在 II （会员题）](https://leetcode.cn/problems/checking-existence-of-edge-length-limited-paths-ii/)
- [ ] [2077.  殊途同归 （会员题）三元环计数](https://leetcode.cn/problems/paths-in-maze-that-lead-to-same-room/)
- [ ] [3656.  判断是否存在简单图 （会员题）Erdős–Gallai 定理](https://leetcode.cn/problems/determine-if-a-simple-graph-exists/)

## 十、树上算法

见 [链表、树、回溯](https://leetcode.cn/circle/discuss/K0n2gO/) 题单的第三章节。

## 关联题单

- 关于 **网格图** 的 DFS 和 BFS，见 [网格图题单](https://leetcode.cn/circle/discuss/YiXPXW/) 。

## 算法题单

[如何科学刷题？](https://leetcode.cn/circle/discuss/RvFUtj/)

1. [滑动窗口与双指针（定长/不定长/单序列/双序列/三指针/分组循环）](https://leetcode.cn/circle/discuss/0viNMK/)
2. [二分算法（二分答案/最小化最大值/最大化最小值/第K小）](https://leetcode.cn/circle/discuss/SqopEo/)
3. [单调栈（基础/矩形面积/贡献法/最小字典序）](https://leetcode.cn/circle/discuss/9oZFK9/)
4. [网格图（DFS/BFS/综合应用）](https://leetcode.cn/circle/discuss/YiXPXW/)
5. [位运算（基础/性质/拆位/试填/恒等式/思维）](https://leetcode.cn/circle/discuss/dHn9Vk/)
6. [图论算法（DFS/BFS/拓扑排序/基环树/最短路/最小生成树/网络流）](https://leetcode.cn/circle/discuss/01LUak/)
7. [动态规划（入门/背包/划分/状态机/区间/状压/数位/数据结构优化/树形/博弈/概率期望）](https://leetcode.cn/circle/discuss/tXLS3i/)
8. [常用数据结构（前缀和/差分/栈/队列/堆/字典树/并查集/树状数组/线段树）](https://leetcode.cn/circle/discuss/mOr1u6/)
9. [数学算法（数论/组合/概率期望/博弈/计算几何/随机算法）](https://leetcode.cn/circle/discuss/IYT3ss/)
10. [贪心与思维（基本贪心策略/反悔/区间/字典序/数学/思维/脑筋急转弯/构造）](https://leetcode.cn/circle/discuss/g6KTKL/)
11. [链表、树与回溯（前后指针/快慢指针/DFS/BFS/直径/LCA）](https://leetcode.cn/circle/discuss/K0n2gO/)
12. [字符串（KMP/Z函数/Manacher/字符串哈希/AC自动机/后缀数组/子序列自动机）](https://leetcode.cn/circle/discuss/SJFwQI/)

如果你发现有题目可以补充进来，欢迎评论反馈。