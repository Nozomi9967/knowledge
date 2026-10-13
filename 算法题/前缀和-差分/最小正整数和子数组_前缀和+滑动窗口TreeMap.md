---
tags:
  - 算法题
  - 前缀和-差分
created: 2026-06-28
updated: 2026-06-28
---
# 最小正整数和子数组 · 前缀和 + 滑动窗口 TreeMap

## 题意

在数组中找一个长度在 `[l, r]` 之间、**元素和最小且为正数**的子数组，返回该最小正整数和。

---

## 核心转化：前缀和

子数组 `nums[i..j]` 的和 = `s[j+1] - s[i]`

问题转化为：找两个下标 `i < j`，使得：
- `s[j] - s[i] > 0`（和为正）
- 差值尽量小
- 子数组长度约束：`l ≤ j - i ≤ r`，即 `j - r ≤ i ≤ j - l`

---

## 滑动窗口是什么

> **外层 `j` 只是普通枚举，滑动窗口是 TreeMap 里维护的合法 `i` 的集合。**

固定右端点 `j`，合法的左端点 `i` 范围是：

$$[j - r, \quad j - l]$$

窗口大小固定为 `r - l + 1`，随 `j` 增大整体右移：

```
j=5, l=2, r=4:  i ∈ [1, 3]
j=6, l=2, r=4:  i ∈ [2, 4]
j=7, l=2, r=4:  i ∈ [3, 5]
```

---

## 每轮 j 的三个操作

```java
// ① 加入新右边界：i = j-l 成为合法候选
cnt.merge(s[j - l], 1, Integer::sum);

// ② 查询：找最大的、严格小于 s[j] 的候选值
Integer lower = cnt.lowerKey(s[j]);
if (lower != null) {
    ans = Math.min(ans, s[j] - lower);
}

// ③ 删除旧左边界：i = j-r 下一轮将不再合法
if (j >= r) {
    int v = s[j - r];
    int c = cnt.get(v);
    if (c == 1) cnt.remove(v);
    else cnt.put(v, c - 1);
}
```

| 操作 | 对应边界 | 时机 |
|------|----------|------|
| ① 加入 `s[j-l]` | 右边界进窗口 | 为当前轮查询服务 |
| ② `lowerKey(s[j])` | 查询 | 此时窗口恰好完整 |
| ③ 删除 `s[j-r]` | 左边界出窗口 | 为下一轮查询扫尾 |

---

## 操作顺序为什么不能乱？

### ① 必须在 ② 前

① 加入的 `s[j-l]` 是当前轮合法的右边界，必须先加入再查询，否则漏掉这个候选。

### ③ 必须在 ② 后（当 `l == r` 时）

当 `l == r` 时，`j - l == j - r`，①和③操作的是**同一个元素**。

若把③移到②前：
1. ① 加入 `s[j-l]`
2. ③ 立刻删除 `s[j-r]`（= `s[j-l]`，同一个！）
3. ② 查询时窗口为空 ❌

### `l != r` 时 ③ 的位置

③ 删除的是 `s[j-r]`，加入的是 `s[j-l]`，两者不同，对当前轮查询无影响。
但 ③ 必须在**下一轮 ② 之前**执行，放在当前轮 ② 后天然满足。

---

## 填充阶段（j < r）

`j < r` 时 `j - r < 0`，没有需要删除的左边界，窗口只进不出，大小从 1 增长到 `r - l + 1`。
这是正常的填充阶段，此时所有已加入的 `i` 都在合法范围内。

---

## 为什么用 TreeMap？

需要高效查询「严格小于 `s[j]` 的最大值」→ `lowerKey()`，TreeMap 基于红黑树，单次 `O(log n)`。

用计数 `cnt` 而非 `Set` 是因为前缀和可能有**重复值**。

---

## 复杂度

| 项目 | 复杂度 |
|------|--------|
| 时间 | O(n log n) |
| 空间 | O(n) |

---

## 完整代码

```java
class Solution {
    public int minimumSumSubarray(List<Integer> nums, int l, int r) {
        Integer[] a = nums.toArray(Integer[]::new);
        int ans = Integer.MAX_VALUE;
        int n = a.length;
        int[] s = new int[n + 1];
        TreeMap<Integer, Integer> cnt = new TreeMap<>();
        for (int j = 1; j <= n; j++) {
            s[j] = s[j - 1] + a[j - 1];
            if (j < l) continue;
            cnt.merge(s[j - l], 1, Integer::sum);       // ① 右边界进
            Integer lower = cnt.lowerKey(s[j]);          // ② 查询
            if (lower != null) {
                ans = Math.min(ans, s[j] - lower);
            }
            if (j >= r) {                                // ③ 左边界出
                int v = s[j - r];
                int c = cnt.get(v);
                if (c == 1) cnt.remove(v);
                else cnt.put(v, c - 1);
            }
        }
        return ans == Integer.MAX_VALUE ? -1 : ans;
    }
}
```

---

## Tags

#算法 #前缀和 #滑动窗口 #TreeMap #LeetCode
