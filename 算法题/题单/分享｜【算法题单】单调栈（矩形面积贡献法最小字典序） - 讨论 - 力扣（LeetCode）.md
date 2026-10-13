---
title: "分享｜【算法题单】单调栈（矩形面积/贡献法/最小字典序） - 讨论 - 力扣（LeetCode）"
source: "https://leetcode.cn/discuss/post/3579480/ti-dan-dan-diao-zhan-ju-xing-xi-lie-zi-d-u4hk/"
author:
published:
created: 2026-07-02
description: "他向远方望去，无法看到高山背后的矮山，只看到一座座更高的山峰。推荐先做做 数据结构题单 中的「枚举右，维护左」以及第三章「栈」的基础题目后，再来刷本题单。一、单调栈§1.1 基础原理讲解：单调栈【基础算法精讲 26】模板：739. 每日温度1475. 商品折扣后的最终价格 非暴力做法496. 下一个更大元素 I503. 下一个更大元素 II901. 股票价格跨度 1709853. 车队§1.2 进阶1019. 链表中的下一个更大节点 1571654. 最大二叉树 做到 O("
tags:
  - "clippings"
updated: 2026-07-11
---
分享｜【算法题单】单调栈（矩形面积/贡献法/最小字典序）

![](https://assets.leetcode.cn/aliyun-lc-upload/users/endlesscheng/avatar_1690721039.png?x-oss-process=image%2Fformat%2Cwebp)

[灵茶山艾府](https://leetcode.cn/u/endlesscheng/)

234215

2024.01.03

2026.05.04

发布于 未知归属地

> 他向远方望去，无法看到高山背后的矮山，只看到一座座更高的山峰。

推荐先做做 [数据结构题单](https://leetcode.cn/circle/discuss/mOr1u6/) 中的「枚举右，维护左」以及第三章「栈」的基础题目后，再来刷本题单。

## 一、单调栈

### §1.1 基础

原理讲解： [单调栈【基础算法精讲 26】](https://leetcode.cn/link/?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1VN411J7S7%2F)

模板：

```
# 返回 left，其中 left[i] 是 nums[i] 左侧最近的严格大于 nums[i] 的数的下标，若不存在则为 -1
# 时间复杂度 O(len(nums))
def left_greater(nums: list[int]) -> list[int]:
    n = len(nums)
    left = [-1] * n
    st = []
    for i, x in enumerate(nums):
        while st and nums[st[-1]] <= x:  # 如果求严格小于，改成 >=
            st.pop()
        if st:
            left[i] = st[-1]
        st.append(i)
    return left

# 返回 right，其中 right[i] 是 nums[i] 右侧最近的严格大于 nums[i] 的数的下标，若不存在则为 len(nums)
# 时间复杂度 O(len(nums))
def right_greater(nums: list[int]) -> list[int]:
    n = len(nums)
    right = [n] * n
    st = []
    for i in range(n - 1, -1, -1):
        x = nums[i]
        while st and nums[st[-1]] <= x:  # 如果求严格小于，改成 >=
            st.pop()
        if st:
            right[i] = st[-1]
        st.append(i)
    return right
```

- [ ] [739. 每日温度](https://leetcode.cn/problems/daily-temperatures/)
[[739. 每日温度 - 力扣（LeetCode）]]
- [ ] [1475. 商品折扣后的最终价格 非暴力做法](https://leetcode.cn/problems/final-prices-with-a-special-discount-in-a-shop/)
同上
- [ ] [496. 下一个更大元素 I](https://leetcode.cn/problems/next-greater-element-i/)
[[496. 下一个更大元素 I - 力扣（LeetCode）]]
- [ ] [503. 下一个更大元素 II](https://leetcode.cn/problems/next-greater-element-ii/)
[[503. 下一个更大元素 II - 力扣（LeetCode）]]
- [ ] [901. 股票价格跨度 1709](https://leetcode.cn/problems/online-stock-span/)
直接常规来了
- [ ] [853. 车队](https://leetcode.cn/problems/car-fleet/)
单调栈,按照position排序，对于当前位置，前面位置的车只要到目的地时间比当前位置的车时间要相等或者更短，那么就一定能与当前车形成一个车队。那么问题就转变成对按照位置排序后的时间数组，每个时间求前面有多少时间是连续小于等于当前时间的。也就是找左边第一个大于当前值的下标。

### §1.2 进阶

- [ ] [1019. 链表中的下一个更大节点 1571](https://leetcode.cn/problems/next-greater-node-in-linked-list/)
```java
class Solution {
    private int[] ans;
    private final Deque<Integer> st = new ArrayDeque<>(); // 单调栈（节点值）

    public int[] nextLargerNodes(ListNode head) {
        f(head, 0);
        return ans;
    }

    private void f(ListNode node, int i) {
        if (node == null) {
            ans = new int[i]; // i 就是链表长度
            return;
        }
        f(node.next, i + 1);
        while (!st.isEmpty() && st.peek() <= node.val)
            st.pop(); // 弹出无用数据
        if (!st.isEmpty())
            ans[i] = st.peek(); // 栈顶就是第 i 个节点的下一个更大元素
        st.push(node.val);
    }
}

作者：灵茶山艾府
链接：https://leetcode.cn/problems/next-greater-node-in-linked-list/solutions/2217563/tu-jie-dan-diao-zhan-liang-chong-fang-fa-v9ab/
来源：力扣（LeetCode）
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
```
- [ ] [654. 最大二叉树 做到](https://leetcode.cn/problems/maximum-binary-tree/)
[[654. 最大二叉树 - 力扣（LeetCode）]]
- [ ] [768. 最多能完成排序的块 II 1788](https://leetcode.cn/problems/max-chunks-to-make-sorted-ii/)
[[768. 最多能完成排序的块 II - 力扣（LeetCode）]]
- [ ] [3814. 预算下的最大总容量 1796](https://leetcode.cn/problems/maximum-capacity-within-budget/)
- [ ] [3676. 碗子数组的数目 1848](https://leetcode.cn/problems/count-bowl-subarrays/)
- [ ] [2054. 两个最好的不重叠活动 1883](https://leetcode.cn/problems/two-best-non-overlapping-events/)
- [ ] [456. 132 模式 约 2000](https://leetcode.cn/problems/132-pattern/)
- [ ] [3113. 边界元素是最大值的子数组数目 2046](https://leetcode.cn/problems/find-the-number-of-subarrays-where-boundary-elements-are-maximum/)
- [ ] [2866. 美丽塔 II 2072](https://leetcode.cn/problems/beautiful-towers-ii/)
- [ ] [975. 奇偶跳 2079](https://leetcode.cn/problems/odd-even-jump/)
- [ ] [1944. 队列中可以看到的人数 2105](https://leetcode.cn/problems/number-of-visible-people-in-a-queue/)
- [ ] [2454. 下一个更大元素 IV 2175](https://leetcode.cn/problems/next-greater-element-iv/)
- [ ] [1130. 叶值的最小代价生成树 做到](https://leetcode.cn/problems/minimum-cost-tree-from-leaf-values/)
- [ ] [2289. 使数组按非递减顺序排列 2482](https://leetcode.cn/problems/steps-to-make-array-non-decreasing/)
- [ ] [1776. 车队 II 2531](https://leetcode.cn/problems/car-fleet-ii/)
- [ ] [2736. 最大和查询 2533](https://leetcode.cn/problems/maximum-sum-queries/)
- [ ] [3420. 统计 K 次操作以内得到非递减子数组的数目 2855 树形结构](https://leetcode.cn/problems/count-non-decreasing-subarrays-after-k-operations/)
- [ ] [3221. 最大数组跳跃得分 II （会员题）](https://leetcode.cn/problems/maximum-array-hopping-score-ii/)
- [ ] [1966. 未排序数组中的可被二分搜索的数 （会员题）](https://leetcode.cn/problems/binary-searchable-numbers-in-an-unsorted-array/)
- [ ] [2832. 每个元素为最大值的最大范围 （会员题）](https://leetcode.cn/problems/maximal-range-that-each-element-is-maximum-in-it/)
- [ ] [1063. 有效子数组的数目 （会员题）](https://leetcode.cn/problems/number-of-valid-subarrays/)
- [ ] [1950. 所有子数组最小值中的最大值 （会员题）](https://leetcode.cn/problems/maximum-of-minimum-values-in-all-subarrays/)
- [ ] [2282. 在一个网格中可以看到的人数 （会员题）](https://leetcode.cn/problems/number-of-people-that-can-be-seen-in-a-grid/)
- [ ] [3555. 排序每个滑动窗口中最小的子数组 （会员题）非暴力做法](https://leetcode.cn/problems/smallest-subarray-to-sort-in-every-sliding-window/)
- [ ] [2355. 你能拿走的最大图书数量 （会员题）](https://leetcode.cn/problems/maximum-number-of-books-you-can-take/)

**思维扩展** ：

- [ ] [962. 最大宽度坡 1608](https://leetcode.cn/problems/maximum-width-ramp/)
- [ ] [3542. 将所有元素变为 0 的最少操作次数 1890](https://leetcode.cn/problems/minimum-operations-to-convert-all-elements-to-zero/)
- [ ] [1124. 表现良好的最长时间段 1908](https://leetcode.cn/problems/longest-well-performing-interval/)
- [ ] [1340. 跳跃游戏 V 做到](https://leetcode.cn/problems/jump-game-v/)
- [ ] [2863. 最长半递减子数组的长度 （会员题）](https://leetcode.cn/problems/maximum-length-of-semi-decreasing-subarrays/)

## 二、矩形

- [ ] [84. 柱状图中最大的矩形](https://leetcode.cn/problems/largest-rectangle-in-histogram/)
[[84. 柱状图中最大的矩形 - 力扣（LeetCode）]]
- [ ] [1793. 好子数组的最大分数 1946](https://leetcode.cn/problems/maximum-score-of-a-good-subarray/)
[[1793. 好子数组的最大分数 - 力扣（LeetCode）]]
- [ ] [85. 最大矩形](https://leetcode.cn/problems/maximal-rectangle/)
- [ ] [221. 最大正方形](https://leetcode.cn/problems/maximal-square/)
- [ ] [42. 接雨水 做法不止一种](https://leetcode.cn/problems/trapping-rain-water/)
- [ ] [1504. 统计全 1 子矩形](https://leetcode.cn/problems/count-submatrices-with-all-ones/)
- [ ] [1277. 统计全为 1 的正方形子矩阵](https://leetcode.cn/problems/count-square-submatrices-with-all-ones/)
- [ ] [755. 倒水 （会员题）](https://leetcode.cn/problems/pour-water/)

**思维扩展** ：

- [ ] [1727. 重新排列后的最大子矩阵 1927 注：不是单调栈，但有一些共同的思路](https://leetcode.cn/problems/largest-submatrix-with-rearrangements/)

## 三、贡献法

- [ ] [907. 子数组的最小值之和 1976](https://leetcode.cn/problems/sum-of-subarray-minimums/)
[[907. 子数组的最小值之和 - 力扣（LeetCode）]]
- [ ] [2104. 子数组范围和（最大值-最小值） 做法难度大约 2000](https://leetcode.cn/problems/sum-of-subarray-ranges/)
- [ ] [1856. 子数组最小乘积的最大值 2051](https://leetcode.cn/problems/maximum-subarray-min-product/)
- [ ] [3878. 统计好子数组 2230](https://leetcode.cn/problems/count-good-subarrays/)
- [ ] [2818. 操作使得分最大 2397](https://leetcode.cn/problems/apply-operations-to-maximize-score/)
- [ ] [2281. 巫师的总力量和（最小值×和） 2621](https://leetcode.cn/problems/sum-of-total-strength-of-wizards/)
- [ ] [3430. 最多 K 个元素的子数组的最值之和 2645](https://leetcode.cn/problems/maximum-and-minimum-sums-of-at-most-size-k-subarrays/)
- [ ] [3359. 查找最大元素不超过 K 的有序子矩阵 （会员题）矩形](https://leetcode.cn/problems/find-sorted-submatrices-with-maximum-element-at-most-k/)

**思维扩展** ：

- [ ] [2334. 元素值大于变化阈值的子数组 2381](https://leetcode.cn/problems/subarray-with-elements-greater-than-varying-threshold/)
- [ ] [2962. 统计最大元素出现至少 K 次的子数组·我的题解 中的思考题（解答见评论）](https://leetcode.cn/problems/count-subarrays-where-max-element-appears-at-least-k-times/)

## 四、最小字典序

- [ ] [402. 移掉 K 位数字 约 1800](https://leetcode.cn/problems/remove-k-digits/)
[[402. 移掉 K 位数字 - 力扣（LeetCode）]]
- [ ] [1673. 找出最具竞争力的子序列 1802](https://leetcode.cn/problems/find-the-most-competitive-subsequence/)
[[1673. 找出最具竞争力的子序列 - 力扣（LeetCode）]]
- [ ] [316. 去除重复字母 2185](https://leetcode.cn/problems/remove-duplicate-letters/)
- [ ] [316 扩展：重复个数不超过 limit](https://leetcode.cn/contest/tianchi2022/problems/ev2bru/)
- [ ] [1081. 不同字符的最小子序列 同 316 题](https://leetcode.cn/problems/smallest-subsequence-of-distinct-characters/)
- [ ] [3816. 删除重复字符后的字典序最小字符串 2377](https://leetcode.cn/problems/lexicographically-smallest-string-after-deleting-duplicate-characters/)
- [ ] [321. 拼接最大数](https://leetcode.cn/problems/create-maximum-number/)
- [ ] [2030. 含特定字母的最小子序列 2562](https://leetcode.cn/problems/smallest-k-length-subsequence-with-occurrences-of-a-letter/)

## 关联题单

- **单调栈优化 DP** ：见 [动态规划题单](https://leetcode.cn/circle/discuss/tXLS3i/) 的「§11.2 单调栈优化 DP」。
- **单调队列** ：见 [数据结构题单](https://leetcode.cn/circle/discuss/mOr1u6/) 的「§4.3 单调队列」。

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

[我的题解精选（已分类）](https://github.com/EndlessCheng/codeforces-go/blob/master/leetcode/SOLUTIONS.md)

欢迎关注 [B站@灵茶山艾府](https://leetcode.cn/link/?target=https%3A%2F%2Fspace.bilibili.com%2F206214)

如果你发现有题目可以补充进来，欢迎评论反馈。
