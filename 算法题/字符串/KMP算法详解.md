---
tags:
  - 字符串
  - KMP
  - 模式匹配
  - 算法模板
created: 2026-07-28
updated: 2026-09-21
source: https://leetcode.cn/problems/find-the-index-of-the-first-occurrence-in-a-string/
---

### **KMP 字符串匹配算法（Java 实现）**

KMP 的核心思想是**利用已匹配的部分信息避免重复比较**。通过预处理模式串构建 `lps`（最长公共前后缀）数组，在匹配失配时利用 `lps` 跳转到模式串的适当位置继续匹配，而不是从模式串开头重新开始。时间复杂度 $O(n+m)$，空间 $O(m)$。

---

### **一、buildLps：构建最长公共前后缀数组**

```java
private static int[] buildLps(String pattern) {
    int m = pattern.length();
    int[] lps = new int[m];
    int len = 0;          // 当前公共前后缀长度
    int i = 1;
    while (i < m) {
        if (pattern.charAt(i) == pattern.charAt(len)) {
            lps[i++] = ++len;            // 能延长，记录长度后前进
        } else if (len > 0) {
            len = lps[len - 1];           // 回退到更短的前后缀，不增 i
        } else {
            lps[i++] = 0;                 // 无任何公共前后缀
        }
    }
    return lps;
}
```

`lps[i]` 表示 `pattern[0..i]` 的**最长既是真前缀又是真后缀**的长度。

**构建阶段的回退逻辑**：`len = lps[len - 1]` 是关键。当 `pattern[i] != pattern[len]` 时，不是把 `len` 归零从头来，而是跳到 `lps[len-1]` 继续尝试——这与匹配阶段的失配跳转逻辑完全对称，保证构建过程也是 $O(m)$ 而非 $O(m^2)$。

---

### **二、search：在主串中查找所有匹配**

```java
public static List<Integer> search(String text, String pattern) {
    List<Integer> matches = new ArrayList<>();
    if (pattern.isEmpty()) return matches;

    int[] lps = buildLps(pattern);
    int n = text.length(), m = pattern.length();
    int i = 0, j = 0;       // i 主串指针（只增不减），j 模式指针

    while (i < n) {
        if (text.charAt(i) == pattern.charAt(j)) {
            i++; j++;
            if (j == m) {                       // 完整匹配
                matches.add(i - j);             // 记录起点
                j = lps[j - 1];                 // 继续找后续（可能重叠）匹配
            }
        } else if (j > 0) {
            j = lps[j - 1];                      // 模式跳转，i 不回退
        } else {
            i++;                                 // 连一个字符都没配上，主串前进
        }
    }
    return matches;
}
```

**匹配阶段的三路分支**：

| 条件 | 动作 | 含义 |
|------|------|------|
| `text[i] == pattern[j]` | `i++`, `j++` | 匹配成功，双指针前进 |
| `text[i] != pattern[j] && j > 0` | `j = lps[j-1]` | 失配，模式串跳转，主串不回退 |
| `text[i] != pattern[j] && j == 0` | `i++` | 连一个字符都没配上，主串前进 |

**找到完整匹配后的跳转**：`j = lps[j-1]` 容易漏掉。它的作用是在记录一个匹配后继续搜索后续可能存在的重叠匹配。比如在 `"AAAA"` 中找 `"AA"`，期望得到 `[0, 1, 2]`，漏掉这行就只能找到第一个。

---

### **三、完整可运行代码**

```java
import java.util.ArrayList;
import java.util.List;

public class KMP {

    private static int[] buildLps(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                lps[i++] = ++len;
            } else if (len > 0) {
                len = lps[len - 1];
            } else {
                lps[i++] = 0;
            }
        }
        return lps;
    }

    public static List<Integer> search(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        if (pattern.isEmpty()) return matches;

        int[] lps = buildLps(pattern);
        int n = text.length(), m = pattern.length();
        int i = 0, j = 0;

        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++; j++;
                if (j == m) {
                    matches.add(i - j);
                    j = lps[j - 1];
                }
            } else if (j > 0) {
                j = lps[j - 1];
            } else {
                i++;
            }
        }
        return matches;
    }

    public static void main(String[] args) {
        String text = "ABABDABACDABABCABAB";
        String pattern = "ABABCABAB";

        List<Integer> result = search(text, pattern);
        System.out.println("匹配起点(0-based): " + result);
        for (int pos : result) {
            System.out.println("  位置 " + pos + " -> " + text.substring(pos, pos + pattern.length()));
        }
    }
}
```

---

### **四、一个具体例子走一遍**

以 `text = "ABABDABACDABABCABAB"`，`pattern = "ABABCABAB"`（`m = 9`）为例：

**构建 `lps` 数组：**

`pattern = A B A B C A B A B`

| i | pattern[i] | len | lps[i] | 说明 |
|---|-----------|-----|--------|------|
| 0 | A | 0 | 0 | 初始 |
| 1 | B | 0 | 0 | A≠B，len=0 → lps[1]=0 |
| 2 | A | 0 | 1 | A==A（len=0 时的 pattern[0]）→ len=1, lps[2]=1 |
| 3 | B | 1 | 2 | B==B（len=1 时的 pattern[1]）→ len=2, lps[3]=2 |
| 4 | C | 2 | 0 | C≠A（len=2 时的 pattern[2]），回退 len=lps[1]=0；C≠A，len=0 → lps[4]=0 |
| 5 | A | 0 | 1 | A==A → len=1, lps[5]=1 |
| 6 | B | 1 | 2 | B==B → len=2, lps[6]=2 |
| 7 | A | 2 | 3 | A==A → len=3, lps[7]=3 |
| 8 | B | 3 | 4 | B==B → len=4, lps[8]=4 |

`lps = [0, 0, 1, 2, 0, 1, 2, 3, 4]`

**匹配过程（关键步骤）：**

在主串位置 10 处完成完整匹配（`text[10..18] = "ABABCABAB"`），记录起点 10。`j` 跳转到 `lps[8] = 4`，继续向后搜索。

最终输出：匹配起点 `[10]`。

---

### **五、实现要点**

- **`charAt` 优于切片**：Java 中用 `charAt(i)` 逐字符访问，相比 Python 的切片更省内存，无需为每次比较创建新的 `String` 对象。如果模式串是固定常量、需要极高性能，也可以预先转成 `char[]`，避免 `charAt` 的边界检查开销。
- **`lps` 回退与匹配跳转对称**：`len = lps[len - 1]`（构建阶段）和 `j = lps[j - 1]`（匹配阶段）逻辑完全对称，这是 KMP 正确性的核心。
- **空模式保护**：方法开头对 `pattern.isEmpty()` 做了保护，直接返回空列表。接口语义可按约定调整。

---

### **六、面试话术**

1. **KMP 比暴力匹配快在哪？** — 暴力匹配失配时主串回退，KMP 利用 `lps` 数组让模式串跳转、主串不回退，避免重复比较。
2. **`lps` 和 `next` 数组有什么区别？** — 本质相同，都是最长公共前后缀长度。不同教材叫法不同，Java 实现常用 `lps`（Longest Prefix Suffix）。
3. **为什么构建 `lps` 也是 $O(m)$？** — `len` 最多增加 `m` 次、减少 `m` 次，每次循环要么 `i++` 要么 `len` 回退，总步数不超过 $2m$。
4. **重叠匹配怎么处理？** — 找到完整匹配后 `j = lps[j-1]` 而不是 `j = 0`，这样模式串跳到最长公共前后缀处继续匹配，能找出所有重叠出现。

---

### 七、`for` 循环写法（buildNext，与上文 while 版等价）

上文 §一 的 `buildLps` 是 `while` 单指针写法；很多资料（《算法导论》的前缀函数 $\pi$）用 `for` 循环写，把「已匹配长度」显式命名为 `j`。两者产出的数组完全相同，只是循环骨架不同——`for` 版和匹配主流程长得几乎一模一样，好记：

```java
public static int[] buildNext(String pattern) {
    int m = pattern.length();
    int[] next = new int[m];
    // i: 当前正在求 next 的位置；j: 已经匹配上的相等前后缀长度
    for (int i = 1, j = 0; i < m; i++) {
        // 失配：退到次长的相等前后缀继续尝试
        while (j > 0 && pattern.charAt(i) != pattern.charAt(j)) {
            j = next[j - 1];
        }
        // 匹配成功：最长相等前后缀长度 +1
        if (pattern.charAt(i) == pattern.charAt(j)) {
            j++;
        }
        next[i] = j;
    }
    return next;
}
```

`i` 从 1 开始，`next[0]` 天然为 0（单字符无真前后缀）。虽然套了一层 `while`，但 `j` 每次最多 +1，减少总量不超过增加总量，均摊 O(m)。**这个 `while(j>0 && 失配) j=next[j-1]` 的结构和匹配阶段一字不差——记住一个就记住了两个。**

> **对照记忆：** 求 next 的本质就是「让模式串自己匹配自己」（错位自匹配），所以它和匹配主流程共用同一套失配回退骨架。§一的 while 版把 `j` 叫 `len`、用 `else if/else` 分三支；本节 for 版把回退收进内层 while，更贴近匹配代码。二选一背熟即可。

---

### 八、失配的正确性依据（为什么 j 退到 next[j-1]）

当 `text[i] != pattern[j]` 时，前面 j 个字符必然已匹配，即：

$$text[i-j..i-1] = pattern[0..j-1]$$

既然 `pattern[0..j-1]` 有一段长度为 `next[j-1]` 的后缀等于它自己的前缀，那么 `text` 末尾那 `next[j-1]` 个字符也必然等于 `pattern` 的前 `next[j-1]` 个——**这段可以直接认定匹配、不用再比**。于是把 `j` 退到 `next[j-1]`、`i` 留在原地继续，这就是「i 永不回退」的全部依据。

极端反例说明暴力慢在哪：主串 `"aaaaaaaaab"`、模式 `"aaab"`，每次都在第 4 个字符失配，暴力法把已比过的三个 `a` 白白丢掉、退回去重比，总比较达 O(n×m)；KMP 靠 next 把「已知匹配的信息」留住，降到 O(n+m)。

---

### 九、nextval 优化（削常数，不改复杂度）

当 `pattern[next[j-1]] == pattern[j]` 时，回退后要比较的字符和刚失配的字符**完全相同、必然再次失配**，这次比较可以直接跳过。把这层判断预先烘焙进一张失败转移表，就是教材里的 `nextval`：

```java
/** fail[j]：已匹配 j 个字符后失配时，j 应回退到的新值（合并掉必然再次失配的跳转） */
public static int[] buildFail(String p) {
    int m = p.length();
    int[] next = buildNext(p);
    int[] fail = new int[m + 1];
    for (int j = 1; j <= m; j++) {
        int cand = next[j - 1];
        // 回退后要比的字符与刚失配的字符相同 → 必然再失配，继续退
        while (cand > 0 && j < m && p.charAt(cand) == p.charAt(j)) {
            cand = fail[cand];
        }
        fail[j] = cand;
    }
    return fail;
}
```

对 `"aaaa"` 这类高度自相似的串效果最明显：失配后一步退到 0，省掉那几次注定失败的比较。**它只削减常数、不改变 O(n+m) 上界**，面试提一句「可以用 nextval 进一步优化重复串的常数」即可，不必强背。

---

### 十、next 数组的其它用途（这才是它的高频考点）

next 不止用于匹配，它编码了模式串自身的循环结构：

- **求所有 border（相等真前后缀）**：长度依次是 `next[m-1]`、`next[next[m-1]-1]`、`next[...]-1]`……一路沿 next 链跳到 0，路径上每个值都是一个 border 长度。
- **O(1) 判最小循环节**：设 `len = m - next[m-1]`，若 `m % len == 0`，则整串由长度 `len` 的片段循环 `m/len` 次而成；否则不是完整循环。这是 [[459.重复的子字符串（待建）]] 类题目的标准解法——比如 `"abcabcabc"`，`next[m-1]=6`，`len=9-6=3`，`9%3==0`，循环节就是 `"abc"`。

> **面试加分**：被问「KMP 还能干嘛」时，答「next 数组顺带给出所有 border 和最小循环节」——这能把一道模板题拔高到「理解了 next 的结构含义」。

---

### 十一、两个最容易踩的坑

**坑 1：约定不一致（next[0] 到底是 0 还是 -1）。** 有的教材/C 实现把 next 整体右移一位并令 `next[0] = -1`（配合 1-based 下标）；本笔记和《算法导论》的 $\pi$ 保持 `next[0] = 0`。两套等价，但**代码绝不能混抄**——混了会莫名死循环或越界。认准一套（推荐 `next[0]=0`）从一而终。

**坑 2：findAll 时 j 的重置（§二已提，这里强调）。** 找到一次完整匹配后写 `j = 0` 会漏掉重叠匹配（`text="aaaaa"`, `pattern="aa"` 应得 `[0,1,2,3]`）；正确是 `j = next[j-1]`，保留「还能继续用」的最长前缀。

---

### 十二、KMP 与工程实现、进阶算法的关系

- **Java 的 `String.indexOf` 并不用 KMP**，用的是偏工程优化的 Boyer-Moore 启发式变体（跳得更远、常数更小）。KMP 的价值是**思想基础**：用模式串自身结构信息指导跳转。
- 理解了 KMP 的「失配指针」，再看 **Z 函数、Aho-Corasick（AC 自动机，多模式匹配）、AC 自动机上跑 DP** 会发现它们共享同一套失配骨架——AC 自动机的 fail 指针就是 KMP next 在 Trie 上的推广。

## 关联知识点

- [[28.找出字符串中第一个匹配项的下标]] — LeetCode 经典 KMP 题
- [[DP状态转移——状态机模型]] — KMP 的有限状态机视角
- [[459.重复的子字符串（待建）]] — next 数组判最小循环节的直接应用
