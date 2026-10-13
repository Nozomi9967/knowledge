---
title: "分享丨【算法题单】网格图（DFS/BFS/综合应用） - 讨论 - 力扣（LeetCode）"
source: "https://leetcode.cn/discuss/post/3580195/fen-xiang-gun-ti-dan-wang-ge-tu-dfsbfszo-l3pa/"
author:
published:
created: 2026-07-05
description: "小小贪吃蛇，移动花样多。平移加旋转，为把迷宫破。代码复代码，bug 何其多。六步化一步，AC 定我夺。—— 1210. 穿过迷宫的最少移动次数一、网格图 DFS适用于需要计算连通块个数、大小的题目。部分题目做法不止一种，也可以用 BFS 或并查集解决。二叉树 DFS 与网格图 DFS 的区别：二叉树网格图递归入口根节点网格图的某个格子递归方向左儿子和右儿子一般为左右上下的相邻格子递归边界空节点（或者叶节点）出界、遇到障碍或者已访问模板（计算每个连通块的大小）：200. 岛屿数量6"
tags:
  - "clippings"
  - "待办"
updated: 2026-07-13
---
分享丨【算法题单】网格图（DFS/BFS/综合应用）

![](https://assets.leetcode.cn/aliyun-lc-upload/users/endlesscheng/avatar_1690721039.png?x-oss-process=image%2Fformat%2Cwebp)


> 小小贪吃蛇，移动花样多。  
> 平移加旋转，为把迷宫破。  
> 代码复代码，bug 何其多。  
> 六步化一步，AC 定我夺。  
> —— 1210\. 穿过迷宫的最少移动次数

## 一、网格图 DFS

适用于需要计算连通块个数、大小的题目。

部分题目做法不止一种，也可以用 BFS 或并查集解决。

二叉树 DFS 与网格图 DFS 的区别：

|  | **二叉树** | **网格图** |
| --- | --- | --- |
| **递归入口** | 根节点 | 网格图的某个格子 |
| **递归方向** | 左儿子和右儿子 | 一般为左右上下的相邻格子 |
| **递归边界** | 空节点（或者叶节点） | 出界、遇到障碍或者已访问 |

模板（计算每个连通块的大小）：

```
# 返回网格图 grid 每个连通块的大小
# 时间复杂度 O(mn)
def dfsGrid(grid: List[List[str]]) -> List[int]:
    m, n = len(grid), len(grid[0])
    vis = [[False] * n for _ in range(m)]

    # 返回当前连通块的大小
    def dfs(i: int, j: int) -> int:
        vis[i][j] = True
        size = 1
        for x, y in (i, j - 1), (i, j + 1), (i - 1, j), (i + 1, j):  # 左右上下
            # 这里 grid[x][y] == '.' 根据题意修改
            if 0 <= x < m and 0 <= y < n and grid[x][y] == '.' and not vis[x][y]:
                size += dfs(x, y)
        return size

    comp_size = []
    for i, row in enumerate(grid):
        for j, ch in enumerate(row):
            if ch != '.' or vis[i][j]:  # ch != '.' 根据题意修改
                continue
            size = dfs(i, j)
            comp_size.append(size)
    return comp_size
```

- [ ] [200.  岛屿数量](https://leetcode.cn/problems/number-of-islands/)
[[200.岛屿数量]]
- [ ] [695.  岛屿的最大面积](https://leetcode.cn/problems/max-area-of-island/)
[[695. 岛屿的最大面积 - 力扣（LeetCode）]]
- [ ] [3619.  总价值可以被 K 整除的岛屿数目 1461](https://leetcode.cn/problems/count-islands-with-total-value-divisible-by-k/)
- [ ] [2658.  网格图中鱼的最大数目 1490](https://leetcode.cn/problems/maximum-number-of-fish-in-a-grid/)
- [ ] [面试题 16.19. 水域大小](https://leetcode.cn/problems/pond-sizes-lcci/)
不难
- [ ] [LCS 03. 主题空间](https://leetcode.cn/problems/YesdPw/)
在fun判断一下是否遇到过边界节点即可
- [ ] [463.  岛屿的周长 思维扩展： 892\. 三维形体的表面积](https://leetcode.cn/problems/island-perimeter/)
在fun多加一个if判断即可，判断上下左右是否有水，有就+1
- [ ] [733.  图像渲染](https://leetcode.cn/problems/flood-fill/)
栈溢出时想想是不是没定义visitied数组
- [ ] [1034.  边界着色 1579](https://leetcode.cn/problems/coloring-a-border/)
[[1034. 边界着色 - 力扣（LeetCode）]]
这个还是值得看的
- [ ] [1020.  飞地的数量 1615](https://leetcode.cn/problems/number-of-enclaves/)
[[1020. 飞地的数量 - 力扣（LeetCode）]]
- [ ] [2684.  矩阵中移动的最大次数 1626](https://leetcode.cn/problems/maximum-number-of-moves-in-a-grid/)
- [ ] [1254.  统计封闭岛屿的数目 1659](https://leetcode.cn/problems/number-of-closed-islands/)
- [ ] [130.  被围绕的区域](https://leetcode.cn/problems/surrounded-regions/)
- [ ] [1905.  统计子岛屿 1679](https://leetcode.cn/problems/count-sub-islands/)
- [ ] [1391.  检查网格中是否存在有效路径 1746](https://leetcode.cn/problems/check-if-there-is-a-valid-path-in-a-grid/)
[[1391. 检查网格中是否存在有效路径 - 力扣（LeetCode）]]
- [ ] [417.  太平洋大西洋水流问题](https://leetcode.cn/problems/pacific-atlantic-water-flow/)
- [ ] [529.  扫雷游戏](https://leetcode.cn/problems/minesweeper/)
- [ ] [1559.  二维网格图中探测环 1838](https://leetcode.cn/problems/detect-cycles-in-2d-grid/)
- [ ] [827.  最大人工岛 1934 变形题见我的题解](https://leetcode.cn/problems/making-a-large-island/)
- [ ] [LCP 63. 弹珠游戏 也可以不用 DFS](https://leetcode.cn/problems/EXvqDp/)
- [ ] [305.  岛屿数量 II （会员题）](https://leetcode.cn/problems/number-of-islands-ii/)
- [ ] [2061.  扫地机器人清扫过的空间个数 （会员题）也可以迭代](https://leetcode.cn/problems/number-of-spaces-cleaning-robot-cleaned/)
- [ ] [2852.  所有单元格的远离程度之和 （会员题）](https://leetcode.cn/problems/sum-of-remoteness-of-all-cells/)
- [ ] [489.  扫地机器人 （会员题）](https://leetcode.cn/problems/robot-room-cleaner/)

**思维扩展** ：

- [ ] [1970.  你能穿过矩阵的最后一天 2124 做法不止一种](https://leetcode.cn/problems/last-day-where-you-can-still-cross/)

## 二、网格图 BFS

适用于需要计算最短距离（最短路）的题目。

DFS 是不撞南墙不回头；BFS 是往水塘中扔石头（起点），荡起一圈圈涟漪（先访问近的，再访问远的）。

模板（单源最短路）：

```
# 返回从 (start_x, start_y) 出发，到其余格子的最短距离
# 时间复杂度 O(mn)
def bfsGrid(grid: List[List[str]], start_x: int, start_y: int) -> List[List[int]]:
    m, n = len(grid), len(grid[0])
    dis = [[-1] * n for _ in range(m)]

    dis[start_x][start_y] = 0
    q = deque([(start_x, start_y)])

    while q:
        i, j = q.popleft()
        for x, y in (i, j - 1), (i, j + 1), (i - 1, j), (i + 1, j):  # 左右上下
            # 这里 grid[x][y] == '.' 根据题意修改
            if 0 <= x < m and 0 <= y < n and grid[x][y] == '.' and dis[x][y] < 0:
                dis[x][y] = dis[i][j] + 1
                q.append((x, y))

    return dis
```

- [ ] [1926.  迷宫中离入口最近的出口 1638](https://leetcode.cn/problems/nearest-exit-from-entrance-in-maze/)
[[1926. 迷宫中离入口最近的出口 - 力扣（LeetCode）]]
- [ ] [1091.  二进制矩阵中的最短路径 1658](https://leetcode.cn/problems/shortest-path-in-binary-matrix/)
[[1091. 二进制矩阵中的最短路径 - 力扣（LeetCode）]]
- [ ] [1162.  地图分析 1666](https://leetcode.cn/problems/as-far-from-land-as-possible/)
[[1162. 地图分析 - 力扣（LeetCode）]]
- [ ] [542.  01 矩阵](https://leetcode.cn/problems/01-matrix/)
[[542. 01 矩阵 - 力扣（LeetCode）]]
- [ ] [994.  腐烂的橘子](https://leetcode.cn/problems/rotting-oranges/)
[[994. 腐烂的橘子 - 力扣（LeetCode）]]
- [ ] [1765.  地图中的最高点 1783](https://leetcode.cn/problems/map-of-highest-peak/)
[[1765. 地图中的最高点 - 力扣（LeetCode）]]
- [ ] [934.  最短的桥 1826](https://leetcode.cn/problems/shortest-bridge/)
- [ ] [2146.  价格范围内最高排名的 K 样物品 1837](https://leetcode.cn/problems/k-highest-ranked-items-within-a-price-range/)
- [ ] [1293.  网格中的最短路径 1967](https://leetcode.cn/problems/shortest-path-in-a-grid-with-obstacles-elimination/)
- [ ] [909.  蛇梯棋 2020](https://leetcode.cn/problems/snakes-and-ladders/)
- [ ] [1210.  穿过迷宫的最少移动次数 2022](https://leetcode.cn/problems/minimum-moves-to-reach-target-with-rotations/)
- [ ] [675.  为高尔夫比赛砍树](https://leetcode.cn/problems/cut-off-trees-for-golf-event/)
- [ ] [2812.  找出最安全路径 2154](https://leetcode.cn/problems/find-the-safest-path-in-a-grid/)
- [ ] [749.  隔离病毒 2277](https://leetcode.cn/problems/contain-virus/)
- [ ] [1730.  获取食物的最短路径 （会员题）](https://leetcode.cn/problems/shortest-path-to-get-food/)
- [ ] [286.  墙与门 （会员题）](https://leetcode.cn/problems/walls-and-gates/)
- [ ] [490.  迷宫 （会员题）](https://leetcode.cn/problems/the-maze/)
- [ ] [505.  迷宫 II （会员题）](https://leetcode.cn/problems/the-maze-ii/)
- [ ] [499.  迷宫 III （会员题）](https://leetcode.cn/problems/the-maze-iii/)
- [ ] [317.  离建筑物最近的距离 （会员题）](https://leetcode.cn/problems/shortest-distance-from-all-buildings/)
- [ ] [2814.  避免淹死并到达目的地的最短时间 （会员题）](https://leetcode.cn/problems/minimum-time-takes-to-reach-destination-without-drowning/)

## 三、网格图 0-1 BFS

边权只有 和 的题目，也可以用 BFS 做。

- [ ] [3286.  穿越网格图的安全路径 做到](https://leetcode.cn/problems/find-a-safe-walk-through-a-grid/)
- [ ] [2290.  到达角落需要移除障碍物的最小数目 2138](https://leetcode.cn/problems/minimum-obstacle-removal-to-reach-corner/)
- [ ] [1368.  使网格图至少有一条有效路径的最小代价 2069](https://leetcode.cn/problems/minimum-cost-to-make-at-least-one-valid-path-in-a-grid/)
- [ ] [3552.  网格传送门旅游 2036](https://leetcode.cn/problems/grid-teleportation-traversal/)
- [ ] [1824.  最少侧跳次数](https://leetcode.cn/problems/minimum-sideway-jumps/)
- [ ] [LCP 56. 信物传送](https://leetcode.cn/problems/6UEx57/)

## 四、网格图 Dijkstra

见 [图论题单](https://leetcode.cn/circle/discuss/01LUak/) 中的「 **§3.1 单源最短路：Dijkstra 算法** 」，我标记了网格图的题目。

## 五、综合应用

- [ ] [1631.  最小体力消耗路径 1948](https://leetcode.cn/problems/path-with-minimum-effort/)
- [ ] [778.  水位上升的泳池中游泳 2097](https://leetcode.cn/problems/swim-in-rising-water/)
- [ ] [329.  矩阵中的最长递增路径](https://leetcode.cn/problems/longest-increasing-path-in-a-matrix/)
- [ ] [3568.  清理教室的最少移动 2143 状压 BFS（分层图最短路）](https://leetcode.cn/problems/minimum-moves-to-clean-the-classroom/)
- [ ] [1036.  逃离大迷宫 2165](https://leetcode.cn/problems/escape-a-large-maze/)
- [ ] [864.  获取所有钥匙的最短路径 2259 状压 BFS（分层图最短路）](https://leetcode.cn/problems/shortest-path-to-get-all-keys/)
- [ ] [1263.  推箱子 2297](https://leetcode.cn/problems/minimum-moves-to-move-a-box-to-their-target-location/)
- [ ] [2258.  逃离火灾 2347](https://leetcode.cn/problems/escape-the-spreading-fire/)
- [ ] [2556.  二进制矩阵中翻转最多一次使路径不连通 2369](https://leetcode.cn/problems/disconnect-path-in-a-binary-matrix-by-at-most-one-flip/)
- [ ] [2577.  在网格图中访问一个格子的最少时间 2382](https://leetcode.cn/problems/minimum-time-to-visit-a-cell-in-a-grid/)
- [ ] [2617.  网格图中最少访问的格子数 2582](https://leetcode.cn/problems/minimum-number-of-visited-cells-in-a-grid/)
- [ ] [LCP 13. 寻宝 状压 DP](https://leetcode.cn/problems/xun-bao/)
- [ ] [LCP 31. 变换的迷宫](https://leetcode.cn/problems/Db3wC1/)
- [ ] [LCP 45. 自行车炫技赛场](https://leetcode.cn/problems/kplEvH/)
- [ ] [LCP 75. 传送卷轴](https://leetcode.cn/problems/rdmXM7/)
- [ ] [1778.  未知网格中的最短路径 （会员题）](https://leetcode.cn/problems/shortest-path-in-a-hidden-grid/)
- [ ] [694.  不同岛屿的数量 （会员题）](https://leetcode.cn/problems/number-of-distinct-islands/)
- [ ] [711.  不同岛屿的数量 II （会员题）](https://leetcode.cn/problems/number-of-distinct-islands-ii/)
- [ ] [1102.  得分最高的路径 （会员题）](https://leetcode.cn/problems/path-with-maximum-minimum-value/)

## 思考题

1. 对于 行 列的网格图，BFS 的队列最多消耗多少空间？DFS 的递归栈最多消耗多少空间？
2. 构造一个网格图，让 DFS 的递归深度尽量大。如果起点是 要如何构造？如果起点是 要如何构造？

欢迎在评论区发表你的思路。

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
