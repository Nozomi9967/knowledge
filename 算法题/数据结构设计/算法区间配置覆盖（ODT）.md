---
tags:
  - 算法题
  - 区间覆盖
  - ODT
  - TreeMap
  - 断点涂色
created: 2026-08-14
updated: 2026-08-14
source: 牛客网 / 算法配置区间覆盖问题
---

# 算法配置区间覆盖问题分析与解题笔记

本笔记详细整理了本题的**完整题目描述**、**原代码 Bug 深度剖析**以及利用 **ODT（珂朵尔树 / 动态断点区间涂色）** 实现不限 ID 上界且时空双优的完美解决方案。

---

## 1. 题目描述

### 题目背景
一个系统支持多种算法，每种算法使用一个正整数 ID 来唯一标识。管理员可以分批次地启用或禁用这些算法。
您需要编写一个程序，根据一份操作日志，计算出最终处于"启用"状态的所有算法 ID。

### 时空限制
- **时间限制**：C/C++ 1秒，其他语言2秒
- **空间限制**：C/C++ 256M，其他语言512M

### 输入描述
- 第一行是一个整数 $N$，代表操作日志中的记录总数。
- 接下来的 $N$ 行，每行是一条操作记录。
- 操作记录分为两种：
  1. `algorithm <ranges>`：表示 **启用** 指定范围内的所有算法。
  2. `undo algorithm <ranges>`：表示 **禁用** 指定范围内的所有算法。
- `<ranges>` 是一个由逗号 `,` 分隔的字符串，用于描述算法 ID 的范围。
  - 范围可以是一个单独的 ID，如 `8`。
  - 也可以是一个闭区间，如 `1-100`。
  - 也可以是两者的混合，如 `1-10,15-20` 或 `6,7,8,9-10`。
- 所有算法 ID 均为正整数。

### 输出描述
- 输出一行，代表经过所有操作后，最终处于"启用"状态的算法 ID 集合。
- 输出结果需要被合并为最简的、无重叠的、升序排列的区间。
- 格式与输入的 `<ranges>` 部分相同。
- 如果最终没有启用的算法，则输出一个空行。

### 样例说明
**输入例子**：
```text
5
undo algorithm 59,99-99
algorithm 10,15-37,35
algorithm 73
undo algorithm 72,56-94,62-71
undo algorithm 70,37-90
```
**输出例子**：
```text
10,15-36
```

---

## 2. 为什么位图（Bitset）思路会崩溃？

你最初尝试使用的位图（通过 `long[] m` 每一位表示一个 ID）在算法设计中称为**稠密表示（Dense Representation）**。

### 崩溃原因
1. **数组越界（`ArrayIndexOutOfBoundsException`）**：
   你使用的数组 `m` 长度为 $10,000,000$，每个 `long` 容纳 64 个 ID，最多只能表达约 $6.4$ 亿号 ID。而测试数据中存在接近 $10$ 亿（报错中的索引 `15625117` 代表的 ID 约为 $10.00007$ 亿）的超大 ID，这直接导致了 `apply` 函数中的数组越界。
2. **空间与时间浪费**：
   如果要支持到更大（比如 $2^{31}-1$）的 ID，预分配数组将需要 $33,554,432$ 个 `long`，占用近 268MB 内存。如果 ID 极其稀疏（例如只操作了 1 和 $10^9$），中间会有大量无用的空间浪费，且在最后扫描时需要遍历整个庞大的数组，极大降低了效率。

---

## 3. 核心解决方案：ODT（珂朵尔树/断点区间涂色）

为了支持任意大的 ID，我们需要将稠密表示转换为**稀疏表示（Sparse Representation）**。我们只关心**区间的端点**，而不关心区间内的每一个数字。这种动态维护区间有色段的技巧，常被称为 **ODT（Old Driver Tree / 珂朵尔树）**。

### 3.1 核心思想
使用 `TreeMap<Long, Integer> mp` 来维护区间的颜色：
> **核心约定**：从断点 `key` 开始，直到下一个断点出现之前，整段区间的颜色都是 `value`（$1$ 代表启用，$0$ 代表禁用）。

- 初始状态：`{ 0 -> 0 }`（表示从 0 开始往后全为 0）。
- 不管区间有多大，每次涂色操作最多只会产生 2 个新的断点。空间复杂度仅与**操作数 $N$** 相关，与 **ID 范围大小** 完全无关。

### 3.2 关键机制实现

#### ① 切割操作（`split`）
当我们要在 $[L, R]$ 涂色时，如果 $L$ 或 $R+1$ 原本不是断点，必须强行在此处"切一刀"，使它们成为独立的断点：

```java
static void split(long pos) {
    if (mp.containsKey(pos)) return;        // 已经是断点，直接返回
    Map.Entry<Long, Integer> f = mp.floorEntry(pos);   // 找到覆盖 pos 的前驱断点
    mp.put(pos, (f == null) ? 0 : f.getValue());       // 复制前驱断点的颜色，在此处新建断点
}
```

#### ② 赋值操作（`assign`）
将区间 $[L, R]$ 的颜色修改为 $c$（$1$ 或 $0$）：
1. 在 $L$ 处 `split(L)`，保证起点独立。
2. 在 $R+1$ 处 `split(R+1)`，保证终点的下一位独立。
3. 清除开区间 $(L, R+1)$ 之间所有旧的内部断点（因为这些内部断点即将被大区间统一染色，不再重要）。
4. 将起点 $L$ 的颜色设为 $c$。

```java
static void assign(long L, long R, int c) {
    split(L);
    split(R + 1);
    mp.subMap(L, false, R + 1, false).clear(); // 移除内部冗余断点
    mp.put(L, c);
}
```

#### ③ 顺序扫描合并
`TreeMap` 内部天然按 key 升序排列。我们只需线性扫描所有 Entry，当遇到 `value == 1` 时记录起点，当遇到 `value == 0` 时将上一段 $1$ 区间输出。

---

## 4. 完整的 Java 通关代码

本实现不再受制于任何 ID 上限，且能完美通过所有大数值测试数据：

```java
import java.io.*;
import java.util.*;

public class Main {
    // 维护断点及对应的颜色（1 = 启用，0 = 禁用）
    static TreeMap<Long, Integer> mp = new TreeMap<>();
    
    static { 
        mp.put(0L, 0); // 哨兵节点：确保 floorEntry 永远不会返回 null
    }

    // 在指定位置切分区间
    static void split(long pos) {
        if (mp.containsKey(pos)) return;
        Map.Entry<Long, Integer> f = mp.floorEntry(pos);
        mp.put(pos, (f == null) ? 0 : f.getValue());
    }

    // 将区间 [L, R] 赋值为颜色 c
    static void assign(long L, long R, int c) {
        split(L);
        split(R + 1);
        mp.subMap(L, false, R + 1, false).clear(); // 清理内部碎断点
        mp.put(L, c);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String firstLine = br.readLine();
        if (firstLine == null) return;
        
        int n = Integer.parseInt(firstLine.trim());
        for (int i = 0; i < n; i++) {
            String line = br.readLine();
            if (line == null) break;
            String[] t = line.split(" ");
            String[] ranges = t[t.length - 1].split(",");
            boolean enable = (t.length == 2); // 2段代表 algorithm (启用)，3段代表 undo algorithm (禁用)
            
            for (String s : ranges) {
                long a, b;
                int dash = s.indexOf('-');
                if (dash < 0) {
                    a = b = Long.parseLong(s);
                } else {
                    a = Long.parseLong(s.substring(0, dash));
                    b = Long.parseLong(s.substring(dash + 1));
                }
                assign(a, b, enable ? 1 : 0);
            }
        }

        // 升序扫描合并并生成最终的最简区间格式
        StringBuilder sb = new StringBuilder();
        long start = -1;
        int cur = 0;
        
        for (Map.Entry<Long, Integer> e : mp.entrySet()) {
            long k = e.getKey();
            int v = e.getValue();
            if (v == 1) {
                if (cur == 0) start = k; // 记录连续 1 区间的起点
                cur = 1;
            } else {
                if (cur == 1) { // 连续 1 区间结束，输出
                    long end = k - 1;
                    if (sb.length() > 0) sb.append(",");
                    if (start == end) sb.append(start);
                    else sb.append(start).append("-").append(end);
                    cur = 0;
                }
            }
        }
        System.out.println(sb.toString()); // 无启用算法时自动输出空行
    }
}
```

---

## 5. 复杂度与优势总结

1. **时空复杂度**：
   - **空间复杂度**：$O(N)$，其中 $N$ 是操作总数。内存消耗与算法 ID 范围完全脱耦，极其安全。
   - **时间复杂度**：单次区间修改为摊还 $O(\log N)$（因为每次分裂最多增加 2 个断点，多余的内部断点被删除后不会重复处理）。总时间复杂度 $O(N \log N)$，2秒内轻松跑完 $10^5$ 级别的数据。
2. **免越界设计**：由于使用 `Long` 动态作为 `Map` 的键，即便是面临 $10^9$ 或大至 `Long.MAX_VALUE` 的边界，程序也绝对不会抛出越界异常。

---

## 关联知识点

- [[228.汇总区间]] — 区间合并输出格式
- [[二分查找/410. 分割数组的最大值 TODO]] — 区间/划分相关问题
- [[树状数组和线段树]] — 区间操作的另一种高阶结构（线段树可覆盖区间染色）