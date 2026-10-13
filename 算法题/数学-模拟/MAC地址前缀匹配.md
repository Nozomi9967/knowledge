---
tags:
  - 位运算
  - 字符串解析
  - 笔试
  - 前缀匹配
  - 模拟
  - 算法题
created: 2026-07-28
updated: 2026-07-28
source: 笔试复盘 — 特工身份识别系统（MAC地址前缀匹配）
---

### **特工身份识别系统 — MAC 地址前缀匹配**

核心思路是**字符串转 48 位 long + 位运算截断比较**：将 MAC 地址按大端序拼成一个 long 整数，判断前 M 位是否相同只需右移 `48-M` 位后比较。时间复杂度 $O(N \cdot M)$，空间 $O(N)$。

---

### **一、题目核心分析**

- **本质**：将形如 `xx-xx-xx-xx-xx-xx` 的 48 位 MAC 地址转化为 48 位的二进制数（可用 Java 的 `long` 存储）。
- **匹配规则**：给定前缀长度 M，判断两个 48 位数的前 M 位是否完全相同。
- **数据范围**：规则数 $N \le 100,000$，查询数 $Q \le 100$。属于大输入量，必须控制时间复杂度。

---

### **二、核心思路**

1. **字符串转数字**：按顺序读取 12 个十六进制字符，采用**大端序**（先读的是高位）拼接到 `long` 类型变量中。
2. **位运算截断**：要判断前 M 位是否相同，可以将原数右移 `48 - M` 位，把后面不需要的低位丢弃，然后直接比较两个数字是否相等。

---

### **三、踩坑点与反思总结**

#### **1. 字符串指针不递增（死循环 / 读死一个字符）**

**踩坑代码**：

```java
for(int i=0;i<12;i++){
    while(arr[index]=='-') index++;
    int x=fun2(arr[index]);
    res= (res<<4 | x);
    // 忘记写 index++！
}
```

**分析**：`while` 循环跳过 `-` 后，`index` 停在了第一个有效字符上。如果不手动 `index++`，下一轮 `for` 循环依然读的是同一个字符。

**修正**：在 `for` 循环体末尾加上 `index++`，或者直接写在 `for` 的步进条件里 `for(int i=0; i<12; i++, index++)`。

#### **2. 移位拼接逻辑错误（大端序 vs 小端序 / 多移一位）**

**踩坑代码 1（小端序）**：

```java
res |= (x<<(i*4));
```

**分析**：这会把第一个字符放在最低位。如果掩码 M 正好是 4 的倍数碰巧能对上，但如果 M=6，它取的低位是第二字符的最低位，而实际应该取第二字符的最高位。导致非 4 的倍数掩码全部出错。

**踩坑代码 2（多移位）**：

```java
res=((res|(long)x)<<(4));
```

**分析**：先拼后移位，在最后一次循环（第12次）时，拼上最后的 4 位后又左移了 4 位，导致整体数值放大了 16 倍，高位丢失。

**正确做法**：先移位腾出低 4 位空间，再拼入新字符。

```java
res = (res << 4) | x;
```

#### **3. 致命的超时陷阱：$O(N^2)$ 的冗余循环**

**踩坑代码**：

```java
public static boolean isMatch(long y,long[] r,int[] m){
    for(int i=0;i<n;i++){ ... } // 内部已经遍历了所有规则
}

// main函数中
for(int j=0;j<n;j++){
    if(isMatch(y,r,m)){ ... }   // 外部又套了一层 N 次循环！
}
```

**分析**：`isMatch` 方法本身接收了整个数组并内部循环了 N 次，但在 `main` 函数中，你又把它放在了 `for(j=0; j<n; j++)` 里面。如果匹配不到，一条查询会执行 $N \times N = 10^{10}$ 次操作，绝对超时。

**修正**：`isMatch` 只负责比较**单条**规则。把遍历数组的逻辑放在 `main` 里，匹配到立刻 `break`。

#### **4. 隐性容错问题（大小写与空格）**

- **大小写**：虽然题目说明是小写，但在处理 MAC 地址/十六进制时，最好习惯性地把 `A-F` 也兼容进去。
- **空格**：`split("/")` 后的掩码字符串可能带有看不见的尾随空格，导致 `Integer.valueOf` 报错。应该加上 `.trim()`。

---

### **四、最终正确代码**

```java
import java.util.Scanner;

public class Main {
    // 将单个十六进制字符转为整数
    public static int fun2(char c){
        if(c >= '0' && c <= '9') return c - '0';
        if(c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if(c >= 'A' && c <= 'F') return 10 + (c - 'A'); // 兼容大写
        return 0;
    }

    // 将 MAC 字符串转为 48 位 long（大端序）
    public static long fun1(String s){
        long res = 0;
        int index = 0;
        char[] arr = s.toCharArray();
        for(int i = 0; i < 12; i++){
            while(index < arr.length && (arr[index] == '-' || arr[index] == ':')) {
                index++;
            }
            int x = fun2(arr[index]);
            res = (res << 4) | x; // 先左移腾位，再或上新的值
            index++; // 读完一个字符，指针必须后移
        }
        return res;
    }

    // 比较单个规则：判断 y 和 x 的前 m 位是否相同
    public static boolean isMatch(long y, long x, int m){
        if (m == 0) return true; // 特判 M=0，匹配所有
        int shift = 48 - m;
        // 右移抹除低位后，直接比较高位是否相等
        return (x >> shift) == (y >> shift);
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        if (!in.hasNextInt()) return;
        int n = in.nextInt();
        in.nextLine();

        long[] r = new long[n];
        int[] m = new long[n];
        for(int i = 0; i < n; i++){
            String[] tem = in.nextLine().split("/");
            r[i] = fun1(tem[0]);
            m[i] = Integer.parseInt(tem[1].trim()); // trim 防御性编程
        }

        int M = in.nextInt();
        in.nextLine();
        for(int i = 0; i < M; i++){
            long y = fun1(in.nextLine().trim());
            boolean flag = false;

            // 只需要一层 O(N) 遍历，匹配到立刻 break
            for(int j = 0; j < n; j++){
                if(isMatch(y, r[j], m[j])){
                    flag = true;
                    break;
                }
            }
            System.out.println(flag ? "YES" : "NO");
        }
    }
}
```

---

### **五、复杂度分析**

- **时间复杂度**：$O(N \cdot 12 + Q \cdot N)$。预处理每条规则 $O(12) = O(1)$，每条查询遍历所有规则 $O(N)$，$Q \le 100$，$N \le 10^5$，总计约 $10^7$ 次操作，可过。
- **空间复杂度**：$O(N)$，存储 $N$ 条规则的 long 值和掩码 int 值。

---

### **六、面试话术**

1. **为什么用 long 而不是 String 比较？** — 48 位二进制正好 fit 进 Java 的 64 位 long，位运算比较比字符串逐字符比较快得多，且前缀匹配只需右移后直接 `==`。
2. **大端序为什么重要？** — MAC 地址 `01-23-45-...` 中 `01` 是最高位。如果用 `res |= (x << (i*4))`（小端序），第一个字符会被放到最低位，导致非 4 的倍数掩码全部取错位。
3. **`isMatch` 的右移原理** — 保留高 M 位、丢弃低 48-M 位：`(x >> (48-M)) == (y >> (48-M))`。如果 M=48 则不移动，直接 `==`；如果 M=0 特判为 true（前 0 位当然相同）。
4. **为什么不能 O(N²)？** — `isMatch` 接收整个数组并内部循环，再被 `main` 的 for 循环调用 → $O(N^2)$。$N=10^5$ 时 $10^{10}$ 次操作远超 1 秒限制。
5. **如果 M 更大怎么办？** — 可以把规则按前缀长度分组，每组用 `HashMap<Long, List<Integer>>` 索引，查询时只匹配相同 M 的组。但本题 $M \le 48, N \le 10^5, Q \le 100$，线性扫描足够。

## 关联知识点

- [[69.x的平方根]] — 位运算逼近，同样用移位处理数值精度
- [[201. 数字范围按位与]] — 位移找公共前缀，与本题右移截断思想相通
- [[242.有效的字母异位词]] — 字符频率统计，与十六进制字符转整数思路类似
