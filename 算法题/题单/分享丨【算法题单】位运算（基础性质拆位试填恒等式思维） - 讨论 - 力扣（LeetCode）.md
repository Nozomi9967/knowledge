---
title: "分享丨【算法题单】位运算（基础/性质/拆位/试填/恒等式/思维） - 讨论 - 力扣（LeetCode）"
source: "https://leetcode.cn/discuss/post/3580371/fen-xiang-gun-ti-dan-wei-yun-suan-ji-chu-nth4/"
author:
published:
created: 2026-07-08
description: "推荐先阅读：从集合论到位运算，常见位运算技巧分类总结！一、基础题3370. 仅含置位位的最小整数 11993827. 统计单比特整数3226. 使两个整数相等的位更改次数 12471356. 根据数字二进制下 1 的数目排序 1258461. 汉明距离 12822220. 转换数字的最少位翻转次数 1282 同 461 题1342. 将数字变成 0 的操作次数 做到 O(1)476. 数字的补数 做到 O(1)1009. 十进制整数的反码 同 476 题868. 二进制间距"
tags:
  - "clippings"
updated: 2026-07-16
---
分享丨【算法题单】位运算（基础/性质/拆位/试填/恒等式/思维）

![](https://assets.leetcode.cn/aliyun-lc-upload/users/endlesscheng/avatar_1690721039.png?x-oss-process=image%2Fformat%2Cwebp)

[灵茶山艾府](https://leetcode.cn/u/endlesscheng/)

163127

2024.02.07

2026.06.15

发布于 未知归属地

推荐先阅读： [从集合论到位运算，常见位运算技巧分类总结！](https://leetcode.cn/circle/discuss/CaOJ45/)

## 一、基础题

- [x] [3370.  仅含置位位的最小整数 1199](https://leetcode.cn/problems/smallest-number-with-all-set-bits/)
- [x] [3827.  统计单比特整数](https://leetcode.cn/problems/count-monobit-integers/)
- [ ] [3226.  使两个整数相等的位更改次数 1247](https://leetcode.cn/problems/number-of-bit-changes-to-make-two-integers-equal/)
- [x] [1356.  根据数字二进制下 1 的数目排序 1258](https://leetcode.cn/problems/sort-integers-by-the-number-of-1-bits/)
- [x] [461.  汉明距离 1282](https://leetcode.cn/problems/hamming-distance/)
- [x] [2220.  转换数字的最少位翻转次数 1282 同 461 题](https://leetcode.cn/problems/minimum-bit-flips-to-convert-number/)
- [x] [1342.  将数字变成 0 的操作次数 做到](https://leetcode.cn/problems/number-of-steps-to-reduce-a-number-to-zero/)
```java
        return num==0?0:Integer.bitCount(num)+31-Integer.numberOfLeadingZeros(num);
```
- [x] [476.  数字的补数 做到](https://leetcode.cn/problems/number-complement/)
`        return ((1<<(31-Integer.numberOfLeadingZeros(num)+1))-1)^num;`
- [x] [1009.  十进制整数的反码 同 476 题](https://leetcode.cn/problems/complement-of-base-10-integer/)
- [x] [868.  二进制间距 1307](https://leetcode.cn/problems/binary-gap/)
- [x] [2917.  找出数组中的 K-or 值 1389](https://leetcode.cn/problems/find-the-k-or-of-an-array/)
[[2917. 找出数组中的 K-or 值 - 力扣（LeetCode）]]
- [x] [2657.  找到两个数组的前缀公共数组](https://leetcode.cn/problems/find-the-prefix-common-array-of-two-arrays/)
[[2657. 找到两个数组的前缀公共数组 - 力扣（LeetCode）]]
- [x] [231.  2 的幂](https://leetcode.cn/problems/power-of-two/)
`        return Integer.bitCount(n)==1 && n>=0;`
- [x] [342.  4 的幂](https://leetcode.cn/problems/power-of-four/)
```java
        if(Integer.bitCount(n)!=1) return false;

        return ((31-Integer.numberOfLeadingZeros(n))&1) ==0;
```
- [x] [693.  交替位二进制数](https://leetcode.cn/problems/binary-number-with-alternating-bits/)
[[693. 交替位二进制数 - 力扣（LeetCode）]]
- [x] [3950.  恰好一对连续置位](https://leetcode.cn/problems/exactly-one-consecutive-set-bits-pair/)
`        return Integer.bitCount((n>>1)&n)==1;`
- [x] [191.  位 1 的个数](https://leetcode.cn/problems/number-of-1-bits/)
![[Pasted image 20260715193408.png]]
- [ ] [338.  比特位计数 也可以 DP](https://leetcode.cn/problems/counting-bits/)
- [ ] [2595.  奇偶位数 做到](https://leetcode.cn/problems/number-of-even-and-odd-bits/)
- [ ] [2154.  将找到的值乘以 2 要求： 时间， 空间](https://leetcode.cn/problems/keep-multiplying-found-values-by-two/)
- [ ] [3211.  生成不含相邻零的二进制字符串](https://leetcode.cn/problems/generate-binary-strings-without-adjacent-zeros/)
- [x] [面试题 05.01. 插入](https://leetcode.cn/problems/insert-into-bits-lcci/)
[[面试题 05.01. 插入 - 力扣（LeetCode）]]

**用位运算代替数组操作** ：

- [ ] [3690.  拆分合并数组](https://leetcode.cn/problems/split-and-merge-array-transformation/)

**反转二进制数** ：

- [ ] [190.  颠倒二进制位](https://leetcode.cn/problems/reverse-bits/)
- [ ] [3750.  最少反转次数得到翻转二进制字符串 1289](https://leetcode.cn/problems/minimum-number-of-flips-to-reverse-binary-string/)
- [ ] [3769.  二进制反射排序 1364](https://leetcode.cn/problems/sort-integers-by-binary-reflection/)

## 二、异或（XOR）的性质

本质是模 剩余系的加法。

- [x] [1486.  数组异或操作 1181](https://leetcode.cn/problems/xor-operation-in-an-array/)
- [x] [1720.  解码异或后的数组 1284](https://leetcode.cn/problems/decode-xored-array/)
- [x] [2433.  找出前缀异或的原始数组 1367](https://leetcode.cn/problems/find-the-original-array-of-prefix-xor/)
```java
class Solution {

    public int[] findArray(int[] pref) {

        int n=pref.length;

        int[] ans=new int[n];

        ans[0]=pref[0];

        for(int i=1;i<n;i++){

            ans[i]=(pref[i]^pref[i-1]);

        }

        return ans;

    }

}
```
- [x] [1310.  子数组异或查询 1460](https://leetcode.cn/problems/xor-queries-of-a-subarray/)
异或版的前缀和
```java
class Solution {

    public int[] xorQueries(int[] arr, int[][] queries) {

        int n=arr.length;

        int[] a=new int[n];

        a[0]=arr[0];

        for(int i=1;i<n;i++) a[i]=(arr[i]^a[i-1]);

        int m=queries.length;

        int[] ans=new int[m];

        for(int i=0;i<m;i++){

            int l=queries[i][0],r=queries[i][1];

            ans[i]=(a[r]^a[l]^arr[l]);

        }

        return ans;

    }

}
```
- [ ] [3702.  按位异或非零的最长子序列 1489](https://leetcode.cn/problems/longest-subsequence-with-non-zero-bitwise-xor/)
```
如果 nums 所有元素的异或和不为 0，那么全选即可满足条件，返回 n。
否则不能全选。如果 nums 中有非零元素，那么去掉这个非零元素，就可以使异或和不为零，返回 n−1。
否则 nums 全为 0，无解，返回 0。

作者：灵茶山艾府
链接：https://leetcode.cn/problems/longest-subsequence-with-non-zero-bitwise-xor/solutions/3798500/nao-jin-ji-zhuan-wan-pythonjavacgo-by-en-fumb/
来源：力扣（LeetCode）
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
```
- [ ] [2683.  相邻值的按位异或 1518](https://leetcode.cn/problems/neighboring-bitwise-xor/)
- [ ] [1829.  每个查询的最大异或值 1523](https://leetcode.cn/problems/maximum-xor-for-each-query/)
- [ ] [2997.  使数组异或和等于 K 的最少操作次数 1525](https://leetcode.cn/problems/minimum-number-of-operations-to-make-array-xor-equal-to-k/)
- [ ] [1442.  形成两个异或相等数组的三元组数目 1525](https://leetcode.cn/problems/count-triplets-that-can-form-two-arrays-of-equal-xor/)
- [ ] [2429.  最小异或 1532](https://leetcode.cn/problems/minimize-xor/)
- [ ] [2527.  查询数组异或美丽值 1550](https://leetcode.cn/problems/find-xor-beauty-of-array/)
- [ ] [2317.  操作后的最大异或和 1679](https://leetcode.cn/problems/maximum-xor-after-operations/)
- [ ] [2588.  统计美丽子数组数目 1697](https://leetcode.cn/problems/count-the-number-of-beautiful-subarrays/)
- [ ] [2564.  子字符串异或查询 1959](https://leetcode.cn/problems/substring-xor-queries/)
- [ ] [1734.  解码异或后的排列 2024](https://leetcode.cn/problems/decode-xored-permutation/)
- [ ] [2857.  统计距离为 k 的点对 2082](https://leetcode.cn/problems/count-pairs-of-points-with-distance-k/)
- [ ] [1803.  统计异或值在范围内的数对有多少 2479](https://leetcode.cn/problems/count-pairs-with-xor-in-a-range/)
- [ ] [3215.  用偶数异或设置位计数三元组 II （会员题）](https://leetcode.cn/problems/count-triplets-with-even-xor-set-bits-ii/)

另见 [数据结构题单](https://leetcode.cn/circle/discuss/mOr1u6/) 中的「 **§6.4 0-1 字典树（异或字典树）** 」。

## 三、与或（AND/OR）的性质

的数越多，结果越小。

的数越多，结果越大。

- [x] [2980.  检查按位或是否存在尾随零 1234](https://leetcode.cn/problems/check-if-bitwise-or-has-trailing-zeros/)
- [x] [1318.  或运算的最小翻转次数 1383](https://leetcode.cn/problems/minimum-flips-to-make-a-or-b-equal-to-c/)
- [ ] [2419.  按位与最大的最长子数组 1496](https://leetcode.cn/problems/longest-subarray-with-maximum-bitwise-and/)
- [ ] [2871.  将数组分割成最多数目的子数组 1750](https://leetcode.cn/problems/split-array-into-maximum-number-of-subarrays/)
- [ ] [2401.  最长优雅子数组 1750](https://leetcode.cn/problems/longest-nice-subarray/)
- [ ] [2680.  最大或值 1912 可以做到 额外空间](https://leetcode.cn/problems/maximum-or/)
- [ ] [3133.  数组最后一个元素的最小值 1935](https://leetcode.cn/problems/minimum-array-end/)
- [ ] [3108.  带权图里旅途的最小代价 2109](https://leetcode.cn/problems/minimum-cost-walk-in-weighted-graph/)
- [ ] [3117.  划分数组得到最小的值之和 2735](https://leetcode.cn/problems/minimum-sum-of-values-by-dividing-array/)
- [ ] [3125.  使得按位与结果为 0 的最大数字 （会员题）](https://leetcode.cn/problems/maximum-number-that-makes-result-of-bitwise-and-zero/)

### AND/OR LogTrick

[LogTrick 入门教程](https://leetcode.cn/link/?target=https%3A%2F%2Fzhuanlan.zhihu.com%2Fp%2F1933215367158830792) ，包含原地写法，以及额外维护一个列表的写法。

如果你不熟悉原地去重算法，可以看 26\. 删除有序数组中的重复项 ， 我的题解 。

模板：

```
# 对于每个右端点 i，计算所有子数组的或值，打印这些或值的分布范围（子数组左端点范围）
# 时间复杂度 O(nlogU)，其中 U = max(nums)
def logTrick(nums: List[int]) -> None:
    or_left = []  # (子数组或值，最小左端点)
    for i, x in enumerate(nums):
        # 计算以 i 为右端点的子数组或值
        for p in or_left:
            p[0] |= x  # **根据题目修改**
        # x 单独一个数作为子数组
        or_left.append([x, i])

        # 原地去重（相同或值只保留最左边的）
        idx = 1
        for j in range(1, len(or_left)):
            if or_left[j][0] != or_left[j - 1][0]:
                or_left[idx] = or_left[j]
                idx += 1
        del or_left[idx:]

        print(i, x)
        for k, (or_val, left) in enumerate(or_left):
            right = or_left[k + 1][1] - 1 if k < len(or_left) - 1 else i
            # 对于左端点在 [left, right]，右端点为 i 的子数组，OR 值都是 or_val
            print(left, right, or_val)

logTrick([4, 2, 1, 5])
```

注：下面的部分题目，可以用 **滑动窗口+栈** 做到更优的 时间复杂度，见 原理讲解（方法二） 。

- [ ] [3171.  找到按位或最接近 K 的子数组 例题](https://leetcode.cn/problems/find-subarray-with-bitwise-or-closest-to-k/)
- [ ] [1521.  找到最接近目标值的函数值 同 3171 题，OR 改成 AND](https://leetcode.cn/problems/find-a-value-of-a-mysterious-function-closest-to-target/)
- [ ] [3097.  或值至少为 K 的最短子数组 II 1891](https://leetcode.cn/problems/shortest-subarray-with-or-at-least-k-ii/)
- [ ] [2411.  按位或最大的最小子数组长度 1938](https://leetcode.cn/problems/smallest-subarrays-with-maximum-bitwise-or/)
- [ ] [3209.  子数组按位与值为 K 的数目 2050](https://leetcode.cn/problems/number-of-subarrays-with-and-value-of-k/)
- [ ] [898.  子数组按位或操作 2133](https://leetcode.cn/problems/bitwise-ors-of-subarrays/)
- [ ] [3878.  统计好子数组 2230](https://leetcode.cn/problems/count-good-subarrays/)

### GCD LogTrick

- [ ] [2447.  最大公因数等于 K 的子数组数目 非暴力做法](https://leetcode.cn/problems/number-of-subarrays-with-gcd-equal-to-k/)
- [ ] [2654.  使数组所有元素变成 1 的最少操作次数 非暴力做法](https://leetcode.cn/problems/minimum-number-of-operations-to-make-all-array-elements-equal-to-1/)
- [ ] [3605.  数组的最小稳定性因子 2410](https://leetcode.cn/problems/minimum-stability-factor-of-array/)
- [ ] [3574.  最大子数组 GCD 分数 非暴力做法](https://leetcode.cn/problems/maximize-subarray-gcd-score/)
- [ ] [2941.  子数组的最大 GCD-Sum （会员题）](https://leetcode.cn/problems/maximum-gcd-sum-of-a-subarray/)

## 四、拆位 / 贡献法

- [ ] [477.  汉明距离总和](https://leetcode.cn/problems/total-hamming-distance/)
- [ ] [1863.  找出所有子集的异或总和再求和 可以做到 时间](https://leetcode.cn/problems/sum-of-all-subset-xor-totals/)
- [ ] [2425.  所有数对的异或和 1622 可以做到 时间](https://leetcode.cn/problems/bitwise-xor-of-all-pairings/)
- [ ] [2275.  按位与结果大于零的最长组合 1642](https://leetcode.cn/problems/largest-combination-with-bitwise-and-greater-than-zero/)
- [ ] [1835.  所有数对按位与结果的异或和 1825 也有恒等式做法](https://leetcode.cn/problems/find-xor-sum-of-all-pairs-bitwise-and/)
- [ ] [3688.  偶数的按位或运算 看我题解中的思考题（解答见评论）](https://leetcode.cn/problems/bitwise-or-of-even-numbers-in-an-array/)
- [ ] [2505.  所有子序列和的按位或 （会员题）](https://leetcode.cn/problems/bitwise-or-of-all-subsequence-sums/)

**十进制拆位**

- [ ] [3153.  所有数对中数位不同之和 1645](https://leetcode.cn/problems/sum-of-digit-differences-of-all-pairs/)

## 五、试填法

- [ ] [421.  数组中两个数的最大异或值](https://leetcode.cn/problems/maximum-xor-of-two-numbers-in-an-array/)
- [ ] [3858.  按位或的最小值 1947](https://leetcode.cn/problems/minimum-bitwise-or-from-grid/)
- [ ] [3806.  增加操作后最大按位与的结果 2259](https://leetcode.cn/problems/maximum-bitwise-and-after-increment-operations/)
- [ ] [2935.  找出强数对的最大异或值 II 2349](https://leetcode.cn/problems/maximum-strong-pair-xor-ii/)
- [ ] [3845.  最大子数组异或值](https://leetcode.cn/problems/maximum-subarray-xor-with-bounded-range/)
- [ ] [3007.  价值和小于等于 K 的最大数字](https://leetcode.cn/problems/maximum-number-that-sum-of-the-prices-is-less-than-or-equal-to-k/)
- [ ] [3145.  大数组元素的乘积 2859 类似钱珀瑙恩数](https://leetcode.cn/problems/find-products-of-elements-of-big-array/)
- [ ] [3022.  给定操作次数内使剩余元素的或值最小 2918](https://leetcode.cn/problems/minimize-or-of-remaining-elements-using-operations/)
- [ ] [3287.  求出数组中最大序列值 做到 ，其中](https://leetcode.cn/problems/find-the-maximum-sequence-value-of-array/)
- [ ] [3344.  最大尺寸数组 （会员题）](https://leetcode.cn/problems/maximum-sized-array/)

## 六、恒等式

- [ ] [1835.  所有数对按位与结果的异或和 1825](https://leetcode.cn/problems/find-xor-sum-of-all-pairs-bitwise-and/)
- [ ] [2354.  优质数对的数目 2076](https://leetcode.cn/problems/number-of-excellent-pairs/)

## 七、线性基

[讲解](https://leetcode.cn/link/?target=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1pm8vzAEXx%2F)

模板（最大异或和）：

```
class XorBasis:
    # n 为值域最大值 U 的二进制长度，例如 U=1e9 时 n=30
    def __init__(self, n: int):
        self.b = [0] * n

    def insert(self, x: int) -> None:
        b = self.b
        # 从高到低遍历，保证计算 max_xor 的时候，参与 XOR 的基的最高位（或者说二进制长度）是互不相同的
        for i in range(len(b) - 1, -1, -1):
            if x >> i:  # 由于大于 i 的位都被我们异或成了 0，所以 x >> i 的结果只能是 0 或 1
                if b[i] == 0:  # x 和之前的基是线性无关的
                    b[i] = x  # 新增一个基，最高位为 i
                    return
                x ^= b[i]  # 保证每个基的二进制长度互不相同
        # 正常循环结束，此时 x=0，说明一开始的 x 可以被已有基表出，不是一个线性无关基

    def max_xor(self) -> int:
        b = self.b
        res = 0
        # 从高到低贪心：越高的位，越必须是 1
        # 由于每个位的基至多一个，所以每个位只需考虑异或一个基，若能变大，则异或之
        for i in range(len(b) - 1, -1, -1):
            if res ^ b[i] > res:  # 手写 max 更快
                res ^= b[i]
        return res
```

- [ ] [3681.  子序列最大 XOR 值 模板题](https://leetcode.cn/problems/maximum-xor-of-subsequences/)
- [ ] [3630.  划分数组得到最大异或运算和与运算之和 2744](https://leetcode.cn/problems/partition-array-for-maximum-xor-and-and/)

## 八、思维题

贪心、脑筋急转弯等。

- [ ] [2546.  执行逐位运算使字符串相等 1605](https://leetcode.cn/problems/apply-bitwise-operations-to-make-strings-equal/)
- [ ] [1558.  得到目标数组的最少函数调用次数 1637](https://leetcode.cn/problems/minimum-numbers-of-function-calls-to-make-target-array/)
- [ ] [2571.  将整数减少到零需要的最少操作数 1649 巧妙结论](https://leetcode.cn/problems/minimum-operations-to-reduce-an-integer-to-0/)
- [ ] [3315.  构造最小位运算数组 II 1715](https://leetcode.cn/problems/construct-the-minimum-bitwise-array-ii/)
- [ ] [2568.  最小无法得到的或值 1754](https://leetcode.cn/problems/minimum-impossible-or/)
- [ ] [3644.  排序排列 1775](https://leetcode.cn/problems/maximum-k-to-sort-a-permutation/)
- [ ] [2509.  查询树中环的长度 1948](https://leetcode.cn/problems/cycle-length-queries-in-a-tree/)
- [ ] [2939.  最大异或乘积 2128](https://leetcode.cn/problems/maximum-xor-product/)
- [ ] [2749.  得到整数零需要执行的最少操作数 2132](https://leetcode.cn/problems/minimum-operations-to-make-the-integer-zero/)
- [ ] [2835.  使子序列的和等于目标的最少操作次数 2207](https://leetcode.cn/problems/minimum-operations-to-form-subsequence-with-target-sum/)
- [ ] [2897.  对数组执行操作使平方和最大 2301](https://leetcode.cn/problems/apply-operations-on-array-to-maximize-sum-of-squares/)
- [ ] [810.  黑板异或游戏 2341](https://leetcode.cn/problems/chalkboard-xor-game/)
- [ ] [3064.  使用按位查询猜测数字 I （会员题）](https://leetcode.cn/problems/guess-the-number-using-bitwise-questions-i/)
- [ ] [3094.  使用按位查询猜测数字 II （会员题）](https://leetcode.cn/problems/guess-the-number-using-bitwise-questions-ii/)

## 九、其他

- [ ] [136.  只出现一次的数字](https://leetcode.cn/problems/single-number/)
- [ ] [260.  只出现一次的数字 III](https://leetcode.cn/problems/single-number-iii/)
- [ ] [2965.  找出缺失和重复的数字](https://leetcode.cn/problems/find-missing-and-repeated-values/)
- [ ] [137.  只出现一次的数字 II](https://leetcode.cn/problems/single-number-ii/)
- [ ] [645.  错误的集合](https://leetcode.cn/problems/set-mismatch/)
- [ ] [371.  两整数之和](https://leetcode.cn/problems/sum-of-two-integers/)
- [ ] [201.  数字范围按位与](https://leetcode.cn/problems/bitwise-and-of-numbers-range/)
- [ ] [2438.  二的幂数组中查询范围内的乘积 1610](https://leetcode.cn/problems/range-product-queries-of-powers/)
- [ ] [1680.  连接连续二进制数字 1630](https://leetcode.cn/problems/concatenation-of-consecutive-binary-numbers/)
- [ ] [1415.  长度为 n 的开心字符串中字典序第 k 小的字符串 做到 时间](https://leetcode.cn/problems/the-k-th-lexicographical-string-of-all-happy-strings-of-length-n/)
- [ ] [1261.  在受污染的二叉树中查找元素 做到 额外空间](https://leetcode.cn/problems/find-elements-in-a-contaminated-binary-tree/)
- [ ] [1545.  找出第 N 个二进制字符串中的第 K 位 做到 时间](https://leetcode.cn/problems/find-kth-bit-in-nth-binary-string/)
- [ ] [89.  格雷编码](https://leetcode.cn/problems/gray-code/)
- [ ] [1238.  循环码排列 1775](https://leetcode.cn/problems/circular-permutation-in-binary-representation/)
- [ ] [2126.  摧毁小行星 做到](https://leetcode.cn/problems/destroying-asteroids/)
- [ ] [3766.  将数字变成二进制回文数的最少操作 非暴力做法](https://leetcode.cn/problems/minimum-operations-to-make-binary-palindrome/)
- [ ] [3782.  交替删除操作后最后剩下的整数 2074](https://leetcode.cn/problems/last-remaining-integer-after-alternating-deletion-operations/)
- [ ] [390.  消除游戏 类似 3782 题](https://leetcode.cn/problems/elimination-game/)
- [ ] [982.  按位与为零的三元组 2085](https://leetcode.cn/problems/triples-with-bitwise-and-equal-to-zero/)
- [ ] [3307.  找出第 K 个字符 II 2232](https://leetcode.cn/problems/find-the-k-th-character-in-string-game-ii/)
- [ ] [1611.  使整数变为 0 的最少操作次数 2345](https://leetcode.cn/problems/minimum-one-bit-operations-to-make-integers-zero/)
- [ ] [3514.  不同 XOR 三元组的数目 II 快速沃尔什变换（FWT）](https://leetcode.cn/problems/number-of-unique-xor-triplets-ii/)
- [ ] [LCP 81. 与非的谜题](https://leetcode.cn/problems/ryfUiz/)
- [ ] [751.  IP 到 CIDR （会员题）](https://leetcode.cn/problems/ip-to-cidr/)
- [ ] [3595.  一次或两次 （会员题）结合 137 和 260 的思路](https://leetcode.cn/problems/once-twice/)
- [ ] [3141.  最大汉明距离 （会员题）](https://leetcode.cn/problems/maximum-hamming-distances/)

## 关联题单

1. [数据结构题单](https://leetcode.cn/circle/discuss/mOr1u6/) 中的「 **§6.4 0-1 字典树（异或字典树）** 」。
2. [链表、二叉树与回溯](https://leetcode.cn/circle/discuss/K0n2gO/) 中的部分回溯题目，可以用 **二进制搜索** 解决。

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