---
title: "Java基础面试题"
source: "https://www.xiaolincoding.com/interview/java.html#jvm-%E5%92%8C-java-%E6%9C%89%E5%95%A5%E5%8C%BA%E5%88%AB"
author:
  - "[[小林coding]]"
published: undefined
created: 2026-06-04
updated: 2026-10-08
description: "全面的Java基础面试题汇总，涵盖Java核心概念、面向对象编程、JVM原理、集合框架、多线程并发、异常处理、IO流、反射、注解、泛型、Lambda表达式、Stream API以及Java新特性等核心知识点。适合Java开发者面试准备和技术复习。"
tags:
  - java
  - 基础
  - 面试
  - io
  - 容器
  - 集合
---

大家好，我是小林。

Java 基础这块，在面试里的地位有点特殊——它不像 MySQL、Redis 那样每次都是核心考点，但如果你答得不好，会给面试官留下"基本功不扎实"的印象，这个代价其实挺大的。很多人用 Java 写了好几年代码，却说不清楚 JVM 和 JDK 的关系、int 和 Integer 的区别、为什么要重写 hashCode，这些问题看起来简单，但答起来很容易露馅。

这篇文章整理了 Java 基础面试中最常被问到的知识点，涵盖核心概念、数据类型、面向对象、关键字、反射、注解、异常、Java 8 新特性、IO 等内容，覆盖比较全面。内容偏向理解，不是让你背定义，而是帮你搞清楚"这个东西在 Java 里为什么是这样设计的"。

有几块在面试里出现频率特别高，建议重点花时间：

- **面向对象三大特性** ：封装、继承、多态，尤其是多态的几种体现形式、重载和重写的区别，这类题看起来基础，但很多人答得很浅，容易被追问到哑口无言。
- **int 和 Integer** ：为什么要有包装类、自动装箱拆箱的原理、Integer 的缓存机制（-128 到 127），这条线问得很频繁，而且很容易出坑。
- **String、StringBuffer、StringBuilder** ：三者的区别和适用场景，以及 String 不可变性的原理，这是一道老题，但每次还是会问。
- **equals 和 hashCode** ：为什么要配套重写、不重写会有什么问题，在 HashMap 和 HashSet 里的影响，这个很多人只知道结论，答不出背后的原因。
- **Java 8 新特性** ：Lambda 表达式、Stream API、Optional，这些在实际开发里用得很多，面试里也经常问。
- **反射机制** ：是什么、怎么用、实际应用在哪里（Spring 的 IOC 就是典型），这块理解清楚了对后面学框架原理很有帮助。

如果你是第一次系统准备 Java 基础，建议先把面向对象和数据类型搞清楚，再去看反射、注解这些稍微深一点的内容，整体理解起来会顺很多。

---

## 概念

### 说一下Java的特点

主要有以下的特点：

- **平台无关性** ：Java的“编写一次，运行无处不在”哲学是其最大的特点之一。==Java编译器将源代码编译成字节码==（bytecode），该字节码可以在任何安装了Java虚拟机（JVM）的系统上运行。
- **面向对象** ：Java是一门严格的面向对象编程语言，几乎一切都是对象。面向对象编程（OOP）特性使得代码更易于维护和重用，包括类（class）、对象（object）、继承（inheritance）、多态（polymorphism）、抽象（abstraction）和封装（encapsulation）。
- **内存管理** ：Java有自己的垃圾回收机制，自动管理内存和回收不再使用的对象。这样，开发者不需要手动管理内存，从而减少内存泄漏和其他内存相关的问题。

### Java 的优势和劣势是什么？

首先，Java的优势，我记得跨平台应该是一个大点，因为JVM的存在，一次编写到处运行。然后面向对象，这个可能也是优势，不过现在很多语言都支持面向对象，但是Java的设计从一开始就是OOP的。还有强大的生态系统，比如Spring框架，Hibernate，各种库和工具，社区支持大，企业应用广泛。另外，内存管理方面，自动垃圾回收机制，减少了内存泄漏的问题，对开发者友好。还有多线程支持，内置的线程机制，方便并发编程。安全性方面，Java有安全模型，比如沙箱机制，适合网络环境。还有稳定性，企业级应用长期使用，版本更新也比较注重向后兼容。

劣势的话，性能可能是一个，虽然JVM优化了很多，但相比C++或者Rust这种原生编译语言，还是有一定开销。特别是启动时间，比如微服务场景下，可能不如Go之类的快。语法繁琐，比如样板代码多，之前没有lambda的时候更麻烦，现在有了但比起Python还是不够简洁。内存消耗，JVM本身占内存，对于资源有限的环境可能不太友好。还有面向对象过于严格，有时候写简单程序反而麻烦，虽然Java8引入了函数式编程，但不如其他语言自然。还有开发效率，相比动态语言如Python，Java需要更多代码，编译过程也可能拖慢开发节奏。

### Java为什么是跨平台的？

Java 能支持跨平台，主要依赖于 JVM 关系比较大。

JVM也是一个软件，不同的平台有不同的版本。我们编写的Java源码，编译后会生成一种.class 文件，称为字节码文件。==Java虚拟机就是负责将字节码文件翻译成特定平台下的机器码然后运行==。也就是说，只要在不同平台上安装对应的JVM，就可以运行字节码文件，运行我们编写的Java程序。

而这个过程中，我们编写的Java程序没有做任何改变，仅仅是通过JVM这一”中间层“，就能在不同平台上运行，真正实现了”一次编译，到处运行“的目的。

JVM是一个”桥梁“，是一个”中间件“，是实现跨平台的关键，Java代码首先被编译成字节码文件，再由JVM将字节码文件翻译成机器语言，从而达到运行Java程序的目的。

编译的结果不是生成机器码，而是生成字节码，字节码不能直接运行，必须通过JVM翻译成机器码才能运行。不同平台下编译生成的字节码是一样的，但是由JVM翻译成的机器码却不一样。

所以，运行Java程序必须有JVM的支持，因为编译的结果不是机器码，必须要经过JVM的再次翻译才能执行。即使你将Java程序打包成可执行文件（例如.exe），仍然需要JVM的支持。

==跨平台的是Java程序，不是JVM。JVM是用C/C++开发的，是编译后的机器码，不能跨平台，不同平台下需要安装不同版本的JVM。==

![img](https://cdn.xiaolincoding.com//picgo/1713860588639-bb89fc8e-30b6-4d18-a329-f3fea52c729a.png)

### JVM、JDK、JRE三者关系？

![image-20240725230247664](https://cdn.xiaolincoding.com//picgo/image-20240725230247664.png)

它们之间的关系如下：

- JVM是Java虚拟机，是Java程序运行的环境。它负责将Java字节码（由Java编译器生成）解释或编译成机器码，并执行程序。JVM提供了内存管理、垃圾回收、安全性等功能，使得Java程序具备跨平台性。
- JDK是Java开发工具包，是开发Java程序所需的工具集合。它包含了JVM、编译器（javac）、调试器（jdb）等开发工具，以及一系列的类库（如Java标准库和开发工具库）。JDK提供了开发、编译、调试和运行Java程序所需的全部工具和环境。
- JRE是Java运行时环境，是Java程序运行所需的最小环境。它包含了JVM和一组Java类库，用于支持Java程序的执行。JRE不包含开发工具，只提供Java程序运行所需的运行环境。

### JVM 和 Java 有啥区别？

Java是语言，JVM是平台，一个是写代码的工具，一个是跑代码的环境，两者分工不同但相互配合。

简单来说，Java是一门编程语言，就是我们平时写代码用的那个语言，像String、List这些都是Java语言提供的。而JVM呢，全称是Java虚拟机，它是用来运行Java程序的一个平台或者说运行环境。

![img](https://cdn.xiaolincoding.com//picgo/1766932182349-87bbbed5-20b8-4273-9611-16a27d5e0895.png)

打个比方，Java就像是我们写文章用的中文，而JVM就像是能够阅读和理解这篇文章的人。我们用Java语言写代码，但代码不能直接运行，得先通过编译器编译成字节码，也就是.class文件，然后JVM再把这些字节码翻译成机器能懂的指令去执行。

这里面还有一个很关键的点，就是Java能够跨平台，也就是"一次编译，到处运行"。这个跨平台能力其实主要是JVM提供的。我们写好的Java代码编译成字节码后，这份字节码可以在Windows的JVM上运行，也可以在Linux的JVM上运行，还可以在Mac上运行。虽然底层操作系统不一样，但因为有JVM这一层，它帮我们屏蔽了这些差异，做好了适配。所以本质上是JVM依赖平台，但Java字节码不依赖平台。

再简单说说它们和其他概念的关系。JDK是开发工具包，里面有编译器、调试工具这些，也包含了JRE。JRE是运行环境，包含了JVM和Java的核心类库。所以关系就是JDK大于JRE大于JVM，一个比一个小。如果你要开发Java程序就得装JDK，如果只是运行别人写好的程序，装个JRE就够了。

还有一点，JVM其实不只能跑Java。因为JVM执行的是字节码，所以像Kotlin、Scala这些语言，虽然语法跟Java不一样，但编译后也是生成Java字节码，一样可以在JVM上运行。所以JVM现在已经不只是Java的专属了，而是整个JVM语言生态的基础平台。

### 为什么 Java 解释和编译都有？

Java 同时保留解释执行和 JIT 编译，核心是为了在 **启动速度** 和 **运行性能** 之间做权衡：

| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯解释执行 | ✅ 启动快，无需编译等待 | ❌ 逐条翻译，热点代码反复翻译浪费 CPU |
| 纯预先编译（AOT） | ✅ 运行性能高 | ❌ 启动慢、内存占用大、缺乏运行时信息难以激进优化 |
| 混合模式（解释+JIT） | ✅ 启动快 + 热点跑得快 | — |

首先在 Java 经过编译之后生成字节码文件，接下来进入 JVM 中，就有两个步骤编译和解释。 如下图：

**编译（javac）** ：

- Java 源代码会先被 `javac` 编译为平台无关的字节码（`.class` ）文件，这一步发生在程序运行之前。

**解释 / JIT** ：

- JVM 启动后由解释器逐条解释执行字节码；同时 JVM 通过方法调用计数器和回边计数器识别热点代码，达到阈值后由 JIT 编译为本地机器码并缓存到 Code Cache 中，后续执行直接走机器码。

所以 Java 既是编译型也是解释性语言，默认采用的是解释器和编译器混合的模式。

> [!info]- 补充：分层编译（Tiered Compilation）
> 现代 JVM 将执行分为多个层级：C0（纯解释）、C1（简单编译，优化少但快）、C2（深度编译，优化多但慢）。方法被调用次数越多，就逐渐从 C0 升级到 C1 再到 C2，平滑过渡，让启动阶段更快、热点代码跑得更快。


### jvm是什么

JVM是 java 虚拟机，==主要工作是解释自己的指令集（即字节码）并映射到本地的CPU指令集和OS的系统调用。==

JVM屏蔽了与操作系统平台相关的信息，使得Java程序只需要生成在Java虚拟机上运行的目标代码（字节码），就可在多种平台上不加修改的运行，这也是Java能够“ **一次编译，到处运行的** ”原因。

### 编译型语言和解释型语言的区别？

编译型语言和解释型语言的区别在于：

- 编译型语言：在程序执行之前，整个源代码会被编译成目标平台的机器码（注：Java 这种"编译为字节码再由虚拟机解释 / JIT 执行"的模式严格说属于编译型与解释型的混合），生成可执行文件。执行时直接运行编译后的代码，速度快，但跨平台性较差。
- 解释型语言：在程序执行时，逐行解释执行源代码，不生成独立的可执行文件。通常由解释器动态解释并执行代码，跨平台性好，但执行速度相对较慢。
- 典型的编译型语言如C、C++，典型的解释型语言如Python、JavaScript。

### Python和Java区别是什么？

- Java 是一种编译与解释混合执行的语言：源代码先被 `javac` 编译成字节码（.class），再由 JVM 加载后通过解释器 + JIT 即时编译器执行。
- Python 是一种解释型语言，运行时由解释器逐行翻译并执行源代码（CPython 内部也会先编译为字节码 `.pyc` ，但整体执行模型仍以解释执行为主）。

### 值传递和引用传递的区别？

在 Java 中，参数传递只有 **值传递** 一种方式，不存在真正的 “引用传递”。但很多人会混淆这两个概念，核心区别在于传递的是 “值的副本” 还是 “引用的副本”。

**值传递（Pass by Value）。 **传递的是** 实际值的副本** ，适用于 **基本数据类型** （如 `int` 、 `char` 等），修改方法内的参数副本， **不会影响原变量的值** 。例子：

```java
public static void main(String[] args) {
    int num = 10;
    changeValue(num);
    System.out.println(num); // 输出 10（原变量未被修改）
}

public static void changeValue(int a) {
    a = 20; // 仅修改副本
}
```

**引用传递的误解（本质仍是值传递）。 **对于** 对象（引用类型）** ，传递的是 **对象引用的副本** （而非对象本身）。

两个引用（原引用和副本）指向 **同一个对象** ，因此通过副本修改对象内部数据， **会影响原对象** 。但如果修改副本的指向（如重新赋值）， **不会影响原引用的指向** 。示例：

```java
public class Person {
    String name;
    Person(String name) { this.name = name; }
}

public static void main(String[] args) {
    Person p = new Person("Alice");
    changeName(p);
    System.out.println(p.name); // 输出 "Bob"（对象内部被修改）
    
    changeReference(p);
    System.out.println(p.name); // 仍输出 "Bob"（原引用指向未变）
}

// 修改对象内部数据
public static void changeName(Person obj) {
    obj.name = "Bob"; // 副本和原引用指向同一个对象
}

// 修改副本的指向（不影响原引用）
public static void changeReference(Person obj) {
    obj = new Person("Charlie"); // 副本指向新对象，原引用仍指向旧对象
}
```

简单来说，Java 中 **所有参数传递都是值传递** ：

- 基本类型传递 “值的副本”，修改副本不影响原值。
- 引用类型传递 “引用的副本”，通过副本可修改对象内容，但无法改变原引用的指向。

## 数据类型

> [!note] 变量名 / 标识符规则（笔试爱考）
> Java 标识符（变量名等）**首字符不能是数字**，可以是**字母、下划线 `_`、美元符号 `$`**，以及符合 Java 标识符规则的 Unicode 字符；后续字符才能含数字。惯例上变量名以小写字母开头（`count`、`studentName`）。

### 八种基本的数据类型

Java支持数据类型分为两类： 基本数据类型和引用数据类型。

基本数据类型共有8种，可以分为三类：

- 数值型：整数类型（byte、short、int、long）和浮点类型（float、double）
- 字符型：char
- 布尔型：boolean

8种基本数据类型的默认值、位数、取值范围，如下表所示：

| 数据类型 | 占用大小（字节） | 位数 | 取值范围 | 默认值 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `byte` | 1 | 8 | \-128（-2^7） 到 127（2^7 - 1） | 0 | 是最小的整数类型，适合用于节省内存，例如在处理文件或网络流时存储小范围整数数据。 |
| `short` | 2 | 16 | \-32768（-2^15） 到 32767（2^15 - 1） | 0 | 较少使用，通常用于在需要节省内存且数值范围在该区间的场景。 |
| `int` | 4 | 32 | \-2147483648（-2^31） 到 2147483647（2^31 - 1） | 0 | 最常用的整数类型，可满足大多数日常编程中整数计算的需求。 |
| `long` | 8 | 64 | \-9223372036854775808（-2^63） 到 9223372036854775807（2^63 - 1） | 0L | 用于表示非常大的整数，当 `int` 类型无法满足需求时使用，定义时数值后需加 `L` 或 `l` 。 |
| `float` | 4 | 32 | \-3.4028235E38 到 3.4028235E38 | 0.0f | 单精度浮点数，用于表示小数，精度相对较低，定义时数值后需加 `F` 或 `f` 。 |
| `double` | 8 | 64 | \-1.7976931348623157E308 到 1.7976931348623157E308 | 0.0d | 双精度浮点数，精度比 `float` 高，是 Java 中表示小数的默认类型。 |
| `char` | 2 | 16 | '\\u0000'（0） 到 '\\uffff'（65535） | '\\u0000' | 用于表示单个字符，采用 Unicode 编码，可表示各种语言的字符。 |
| `boolean` | 无明确字节大小（理论上 1 位） | 无明确位数 | `true` 或 `false` | `false` | 用于逻辑判断，只有两个取值，常用于条件判断和循环控制等逻辑场景。 |

Float 和 Double 的最大值都是以科学记数法的形式输出的，结尾的"E+数字"表示 E 之前的数字要乘以 10 的多少倍。比如 3.14E3 就是 3.14×1000=3140，3.14E-3 就是 3.14/1000=0.00314。注意： `Float.MIN_VALUE` / `Double.MIN_VALUE` 表示的是 **最小正非零值** （如 `1.4E-45` ），并不是负的最小值；浮点类型的最小负值是 `-MAX_VALUE` 。

注意一下几点：

- Java八种基本数据类型的字节数：1字节(byte、boolean)、 2字节(short、char)、4字节(int、float)、8字节(long、double)
- 浮点数的默认类型为double（如果需要声明一个常量为float型，则必须要在末尾加上f或F）
- 整数的默认类型为int（声明Long型在末尾加上l或者L）
- 八种基本数据类型的包装类：除了char的是Character、int类型的是Integer，其他都是首字母大写
- char类型是无符号的，不能为负，所以是0开始的

### int和long是多少位，多少字节的？

- `int` 类型是 32 位（bit），占 4 个字节（byte），int 是有符号整数类型，其取值范围是从 -2^31 到 2^31-1 。例如，在一个简单的计数器程序中，如果使用 `int` 类型来存储计数值，它可以表示的最大正数是 2,147,483,647。如果计数值超过这个范围，就会发生溢出，导致结果不符合预期。
- `long` 类型是 64 位，占 8 个字节， `long` 类型也是有符号整数类型，它的取值范围是从 -2^63 到 2^63 -1 ，在处理较大的整数数值时，果 `int` 类型的取值范围不够，就需要使用 `long` 类型。例如，在一个文件传输程序中，文件的大小可能会很大，使用 `int` 类型可能无法准确表示，而 `long` 类型就可以很好地处理这种情况。

### long和int可以互转吗 ？

可以的，Java中的 `long` 和 `int` 可以相互转换。由于 `long` 类型的范围比 `int` 类型大，因此将 `int` 转换为 `long` 是安全的，而将 `long` 转换为 `int` 可能会导致数据丢失或溢出。

将 `int` 转换为 `long` 可以通过直接赋值或强制类型转换来实现。例如：

```
int intValue = 10;
long longValue = intValue; // 自动转换，安全的
```

将 `long` 转换为 `int` 需要使用强制类型转换，但需要注意潜在的数据丢失或溢出问题。

![image-20240726003850183](https://cdn.xiaolincoding.com//picgo/image-20240726003850183.png)

例如：

```
long longValue = 100L;
int intValue = (int) longValue; // 强制类型转换，可能会有数据丢失或溢出
```

在将 `long` 转换为 `int` 时，如果 `longValue` 的值超出了 `int` 类型的范围，转换结果将是截断后的低位部分。因此，在进行转换之前，建议先检查 `longValue` 的值是否在 `int` 类型的范围内，以避免数据丢失或溢出的问题。

### 数据类型转换方式你知道哪些？

- 自动类型转换（隐式转换）：当目标类型的范围大于源类型时，Java会自动将源类型转换为目标类型，不需要显式的类型转换。例如，将 `int` 转换为 `long` 、将 `float` 转换为 `double` 等。
- 强制类型转换（显式转换）：当目标类型的范围小于源类型时，需要使用强制类型转换将源类型转换为目标类型。这可能导致数据丢失或溢出。例如，将 `long` 转换为 `int` 、将 `double` 转换为 `int` 等。语法为：目标类型 变量名 = (目标类型) 源类型。
- 字符串转换：Java提供了将字符串表示的数据转换为其他类型数据的方法。例如，将字符串转换为整型 `int` ，可以使用 `Integer.parseInt()` 方法；将字符串转换为浮点型 `double` ，可以使用 `Double.parseDouble()` 方法等。
- 数值之间的转换：Java提供了一些数值类型之间的转换方法，如将整型转换为字符型、将字符型转换为整型等。这些转换方式可以通过类型的包装类来实现，例如 `Character` 类、 `Integer` 类等提供了相应的转换方法。
  ```java
  // int → char：取 Unicode 码点对应的字符
  int num = 65;
  char c = (char) num;          // 'A'，65 是 'A' 的 ASCII/Unicode
  System.out.println((char) 97);// 'a'

  // char → int：取字符的 Unicode 码点
  char ch = '中';
  int codePoint = ch;           // 20013，自动类型转换
  int codePoint2 = Character.codePointAt(new char[]{'中'}, 0); // 20013

  // 数字字符 → 实际数值
  char digitChar = '9';
  int digit = digitChar - '0';  // 9，利用 ASCII 码差值
  int digit2 = Character.getNumericValue('9'); // 9

  // 数字 → 对应数字字符
  int val = 7;
  char digitChar2 = (char) ('0' + val); // '7'

  // 包装类工具：进制转换
  String hex = Integer.toHexString(255);   // "ff"
  String bin = Integer.toBinaryString(10); // "1010"
  ```

### 类型互转会出现什么问题吗？

> 基本数据类型转换的问题

当把小范围数据类型赋值给大范围数据类型时，Java 会自动进行类型转换，这种转换一般是安全的。

```
int num = 100;
long bigNum = num; // 自动将int转换为long
```

但是大范围数据类型赋值给小范围数据类型时，会发生数据数据溢出或者精度损失的问题。

- 数据溢出：如果大范围数据类型赋值给小范围数据类型时，当目标类型无法容纳原数据时，就会发生数据溢出。比如下面，byte 类型的取值范围是 - 128 到 127。300 的二进制表示为 `00000001 00101100` ，强制转换为 byte 类型时，会丢弃高位字节，只保留低位的 8 位 `00101100` ，其十进制值为 44。
```
int largeNum = 300;
byte b = (byte) largeNum; // b的值为44
```
- 精度损失：在进行浮点数类型的转换时，可能会发生精度损失。由于浮点数的表示方式不同，将一个单精度浮点数(`float`)转换为双精度浮点数(`double`)时，精度可能会损失，如果 double 转换为 int 也会发生精度损失的问题，如下：
```
double d = 3.14;
int i = (int) d; // i的值为3，小数部分0.14被舍弃
```

> 对象引用转换的问题

向上转型是自动进行的，而且是安全的，如下：

```
class Animal {}
class Dog extends Animal {}

Dog dog = new Dog();
Animal animal = dog; // 自动向上转型
```

但是向下转型需要手动进行，并且存在风险。如果父类对象实际上并不是目标子类的实例，在转型时就会抛出异常：

```
Animal animal = new Animal();
Dog dog = (Dog) animal; // 运行时抛出ClassCastException
```

原因是Java 的对象在运行时会记录其真实类型，当进行向下转型时，Java 会检查对象的实际类型是否与目标类型兼容。如果不兼容，就会抛出 `ClassCastException` 。

解决方式是需要使用 `instanceof` 检查：

```
if (animal instanceof Dog) {
    Dog dog = (Dog) animal; // 只有确认animal是Dog的实例时才进行转型
}
```

### 为什么用bigDecimal 不用double ？

double会出现精度丢失的问题，double执行的是二进制浮点运算，二进制有些情况下不能准确的表示一个小数，就像十进制不能准确的表示1/3(1/3=0.3333...)，也就是说二进制表示小数的时候只能够表示能够用1/(2^n)的和的任意组合，但是0.1不能够精确表示，因为它不能够表示成为1/(2^n)的和的形式。

比如：

```java
System.out.println(0.05 + 0.01);
System.out.println(1.0 - 0.42);
System.out.println(4.015 * 100);
System.out.println(123.3 / 100);

输出：
0.060000000000000005
0.5800000000000001
401.49999999999994
1.2329999999999999
```

可以看到在Java中进行浮点数运算的时候，会出现丢失精度的问题。那么我们如果在进行商品价格计算的时候，就会出现问题。很有可能造成我们手中有0.06元，却无法购买一个0.05元和一个0.01元的商品。因为如上所示，他们两个的总和为0.060000000000000005。这无疑是一个很严重的问题，尤其是当电商网站的并发量上去的时候，出现的问题将是巨大的。可能会导致无法下单，或者对账出现问题。

而 Decimal 是精确计算, 所以一般牵扯到金钱的计算, 都使用 Decimal。

```java
import java.math.BigDecimal;

public class BigDecimalExample {
    public static void main(String[] args) {
        BigDecimal num1 = new BigDecimal("0.1");
        BigDecimal num2 = new BigDecimal("0.2");

        BigDecimal sum = num1.add(num2);
        BigDecimal product = num1.multiply(num2);

        System.out.println("Sum: " + sum);
        System.out.println("Product: " + product);
    }
}

//输出
Sum: 0.3
Product: 0.02
```

在上述代码中，我们创建了两个 `BigDecimal` 对象 `num1` 和 `num2` ，分别表示0.1和0.2这两个十进制数。然后，我们使用 `add()` 方法计算它们的和，并使用 `multiply()` 方法计算它们的乘积。最后，我们通过 `System.out.println()` 打印结果。

这样的使用 `BigDecimal` 可以确保精确的十进制数值计算，避免了使用 `double` 可能出现的舍入误差。需要注意的是，在创建 `BigDecimal` 对象时，应该使用字符串作为参数，而不是直接使用浮点数值，以避免浮点数精度丢失。

### 装箱和拆箱是什么？

装箱（Boxing）和拆箱（Unboxing）是将基本数据类型和对应的包装类之间进行转换的过程。

```
Integer i = 10;  //装箱
int n = i;   //拆箱
```

自动装箱主要发生在两种情况，一种是赋值时，另一种是在方法调用的时候。

> 赋值时

这是最常见的一种情况，在Java 1.5以前我们需要手动地进行转换才行，而现在所有的转换都是由编译器来完成。

```
//before autoboxing
Integer iObject = Integer.valueOf(3);
int iPrimitive = iObject.intValue();

//after java5
Integer iObject = 3; //autoboxing - primitive to wrapper conversion
int iPrimitive = iObject; //unboxing - object to primitive conversion
```

> 方法调用时

当我们在方法调用时，我们可以传入原始数据值或者对象，同样编译器会帮我们进行转换。

```java
public static Integer show(Integer iParam){
   System.out.println("autoboxing example - method invocation i: " + iParam);
   return iParam;
}

//autoboxing and unboxing in method invocation
show(3); //autoboxing
int result = show(3); //unboxing because return type of method is Integer
```

show方法接受Integer对象作为参数，当调用 `show(3)` 时，会将int值转换成对应的Integer对象，这就是所谓的自动装箱，show方法返回Integer对象，而 `int result = show(3);`中result为int类型，所以这时候发生自动拆箱操作，将show方法的返回的Integer对象转换成int值。

> 自动装箱的弊端

自动装箱有一个问题，那就是在一个循环中进行自动装箱操作的情况，如下面的例子就会创建多余的对象，影响程序的性能。

```java
Integer sum = 0; for(int i=1000; i<5000; i++){   sum+=i; }
```

上面的代码 `sum+=i` 可以看成 `sum = sum + i` ，但是 `+` 这个操作符不适用于Integer对象，首先sum进行自动拆箱操作，进行数值相加操作，最后发生自动装箱操作转换成Integer对象。其内部变化如下

```java
int result = sum.intValue() + i; Integer sum = Integer.valueOf(result);
```

由于我们这里声明的 sum 为 Integer 类型，自动装箱实际上由编译器替换为 `Integer.valueOf(...)` 调用，命中 `IntegerCache` （默认 -128~127）时会复用缓存对象，但本例中循环值都已超出缓存范围，因此会创建将近 4000 个 Integer 对象，降低程序性能并加重 GC 负担。因此在编程时需要注意：正确声明变量类型，避免因为自动装箱引起的性能问题（另外， `new Integer(int)` 自 JDK 9 起已被 `@Deprecated` ，应统一使用 `Integer.valueOf(int)` 或直接自动装箱）。

### Java为什么要有Integer？

Integer对应是int类型的包装类，就是把int类型包装成Object对象，对象封装有很多好处，可以把属性也就是数据跟处理这些数据的方法结合在一起，比如Integer就有parseInt()等方法来专门处理int型相关的数据。

另一个非常重要的原因就是在Java中绝大部分方法或类都是用来处理类类型对象的，如ArrayList集合类就只能以类作为他的存储对象，而这时如果想把一个int型的数据存入list是不可能的，必须把它包装成类，也就是Integer才能被List所接受。所以Integer的存在是很必要的。

> 泛型中的应用

在Java中，泛型只能使用引用类型，而不能使用基本类型。因此，如果要在泛型中使用int类型，必须使用Integer包装类。例如，假设我们有一个列表，我们想要将其元素排序，并将排序结果存储在一个新的列表中。如果我们使用基本数据类型int，无法直接使用Collections.sort()方法。但是，如果我们使用Integer包装类，我们就可以轻松地使用Collections.sort()方法。

```java
List<Integer> list = new ArrayList<>();
list.add(3);
list.add(1);
list.add(2);
Collections.sort(list);
System.out.println(list);
```

> 转换中的应用

在Java中，基本类型和引用类型不能直接进行转换，必须使用包装类来实现。例如，将一个int类型的值转换为String类型，必须首先将其转换为Integer类型，然后再转换为String类型。

```java
int i = 10;
Integer integer = Integer.valueOf(i);
String str = integer.toString();
System.out.println(str);
```

> 集合中的应用

Java集合中只能存储对象，而不能存储基本数据类型。因此，如果要将int类型的数据存储在集合中，必须使用Integer包装类。例如，假设我们有一个列表，我们想要计算列表中所有元素的和。如果我们使用基本数据类型int，我们需要使用一个循环来遍历列表，并将每个元素相加。但是，如果我们使用Integer包装类，我们可以直接使用stream()方法来计算所有元素的和。

```java
List<Integer> list = new ArrayList<>();
list.add(3);
list.add(1);
list.add(2);
int sum = list.stream().mapToInt(Integer::intValue).sum();
System.out.println(sum);
```

### Integer相比int有什么优点？

Integer 是 int 的包装类，它的优势在于 int **做不到**的事情：

- **可以为 null**：Integer 默认值为 null，int 默认值为 0。这在数据库 ORM 映射中非常关键——数据库字段可能为 NULL，用 int 无法区分"值是 0"和"没有值"，而 Integer 可以。
- **放入集合框架**：Java 集合（`List`、`Set`、`Map`）只能存储对象，不能存基本类型。所以写 `List<Integer>` 可以，写 `List<int>` 编译报错。
- **支持泛型**：泛型参数必须是引用类型，`Box<Integer>` 合法，`Box<int>` 不合法。
- **提供实用工具方法**：如 `Integer.parseInt()`、`Integer.valueOf()`、`Integer.toHexString()`、`Integer.MAX_VALUE` 等，int 本身没有这些方法。
- **缓存机制**：Integer 对 -128~127 的值做了缓存，用 `valueOf()` 返回缓存对象，减少对象创建（详见下文"Integer 的缓存"）。

> [!warning]- 注意：自动拆箱可能引发 NPE
> Integer 可以为 null，但如果对值为 null 的 Integer 变量进行自动拆箱（如直接赋值给 int 或参与算术运算），会抛出 NullPointerException。这是 Integer 的优势带来的附带风险，使用时需判空。

### 那为什么还要保留int类型？

包装类是引用类型，对象的引用和对象本身是分开存储的，而对于基本类型数据，变量对应的内存块直接存储数据本身。

因此，基本类型数据在读写效率方面，要比包装类高效。

除此之外，在64位JVM上，在开启引用压缩的情况下，一个Integer对象占用16个字节的内存空间，而一个int类型数据只占用4字节的内存空间，前者对空间的占用是后者的4倍。

也就是说，不管是读写效率，还是存储效率，基本类型都比包装类高效。

### 说一下 integer的缓存

Java的Integer类内部实现了一个静态缓存池，用于存储特定范围内的整数值对应的Integer对象。

默认情况下，这个范围是-128至127。当通过Integer.valueOf(int)方法创建一个在这个范围内的整数对象时，并不会每次都生成新的对象实例，而是复用缓存中的现有对象，会直接从内存中取出，不需要新建一个对象。

## 面向对象

### 怎么理解面向对象？简单说说封装继承多态

面向对象是一种编程范式，它的设计思想是以对象为中心，通过对象之间的交互来完成程序的功能，具有灵活性和可扩展性，通过封装和继承可以更好地应对需求变化。

它 **将现实世界中的事物抽象为对象** ，对象具有属性（称为字段或属性）和行为（称为方法）。面向对象编程

Java面向对象的三大特性包括： **封装、继承、多态** ：

- **封装** ：封装是指将对象的属性（数据）和行为（方法）结合在一起，对外隐藏对象的内部细节，仅通过对象提供的接口与外界交互。封装的目的是增强安全性和简化编程，使得对象更加独立。
- **继承** ：继承是一种可以使得子类自动共享父类数据结构和方法的机制。它是代码复用的重要手段，通过继承可以建立类与类之间的层次关系，使得结构更加清晰。
- **多态** ：多态是指允许不同类的对象对同一消息作出响应。即同一个接口，使用不同的实例而执行不同操作。多态性可以分为编译时多态（重载）和运行时多态（重写）。它使得程序具有良好的灵活性和扩展性。

### 多态体现在哪几个方面？

多态在面向对象编程中可以体现在以下几个方面：

- **方法重载：**
	- 方法重载是指同一类中可以有多个同名方法，它们具有不同的参数列表（参数类型、数量或顺序不同）。虽然方法名相同，但根据传入的参数不同，编译器会在编译时确定调用哪个方法。
		- 示例：对于一个 `add` 方法，可以定义为 `add(int a, int b)` 和 `add(double a, double b)` 。
- **方法重写：**
	- 方法重写是指子类能够提供对父类中同名方法的具体实现。在运行时，JVM会根据对象的实际类型确定调用哪个版本的方法。这是实现多态的主要方式。
		- 示例：在一个动物类中，定义一个 `sound` 方法，子类 `Dog` 可以重写该方法以实现 `bark` ，而 `Cat` 可以实现 `meow` 。
- **接口与实现：**
	- 多态也体现在接口的使用上，多个类可以实现同一个接口，并且用接口类型的引用来调用这些类的方法。==这使得程序在面对不同具体实现时保持一贯的调用方式。==
		- 示例：多个类（如 `Dog`, `Cat` ）都实现了一个 `Animal` 接口，当用 `Animal` 类型的引用来调用 `makeSound` 方法时，会触发对应的实现。
- **向上转型和向下转型：**
	- 在Java中，可以使用父类类型的引用指向子类对象，这是向上转型。通过这种方式，可以在运行时期采用不同的子类实现。
		- 向下转型是将父类引用转回其子类类型，但在执行前需要确认引用实际指向的对象类型以避免 `ClassCastException` 。

> [!info] 运行时多态的三个前提（缺一不可）
> 1. **有继承关系**（子类 `extends` 父类，或类 `implements` 接口）；
> 2. **子类重写了父类 / 接口的方法**；
> 3. **父类 / 接口类型的引用指向子类对象**（向上转型）。
>
> 三者齐备，调用时才发生"**编译时看声明类型、运行时看实际类型**"：编译器只放行声明类型（父类 / 接口）里声明了的方法；JVM 执行时按对象的**实际类型**去查虚方法表，调到子类重写的版本。

> [!warning] 运行时多态的边界
> 运行时多态**只针对"被重写的实例方法"**。`static` 方法（静态绑定，按声明类型调）、**字段访问**（按声明类型解析）、`private` 方法、构造器**都不参与**运行时多态。所以 `Animal a = new Dog()` 里 `a.speak()` 走 Dog 的重写版，但如果 `Animal` / `Dog` 有同名字段 `name`，`a.name` 取的是 `Animal` 的。

### 多态解决了什么问题？

多态解决的核心问题是：**调用方无需根据对象的具体类型写 if-else 来判断该执行什么行为**。

**没有多态的时候**：

```java
// 没有多态：每新增一种动物，就得改这个 if-else
public void makeSound(Object animal) {
    if (animal instanceof Dog) {
        ((Dog) animal).bark();
    } else if (animal instanceof Cat) {
        ((Cat) animal).meow();
    } else if (animal instanceof Duck) {
        ((Duck) animal).quack();
    }
}
```
- 每次加新类型都要改这里，**违背开闭原则**
- 调用方被迫了解所有具体类型，**耦合严重**

**有了多态之后**：

```java
public void makeSound(Animal animal) {
    animal.speak(); // 调用方只依赖抽象，不关心具体类型
}
```
- 新增 `Duck` 只需实现 `Animal` 接口，makeSound 方法**零改动**
- 调用方与具体实现解耦，符合**依赖倒置原则**

**一句话总结**：多态让代码从"根据类型判断怎么做"变成"直接告诉对象去做"，消除类型耦合的 if-else，是策略模式、工厂模式、依赖注入等设计模式的底层基础。

### 面向对象的设计原则你知道有哪些吗

面向对象编程中的六大原则：

- **单一职责原则（SRP）** ：一个类应该只有一个引起它变化的原因，即一个类应该只负责一项职责。例子：考虑一个员工类，它应该只负责管理员工信息，而不应负责其他无关工作。
- **开放封闭原则（OCP）** ：软件实体应该对扩展开放，对修改封闭。例子：通过制定接口来实现这一原则，比如定义一个图形类，然后让不同类型的图形继承这个类，而不需要修改图形类本身。
- **里氏替换原则（LSP）** ：子类对象应该能够替换掉所有父类对象。例子：正方形继承矩形——矩形可以分别设置宽和高，但正方形要求宽高相等。如果代码中把一个矩形对象换成正方形对象，`setWidth()` 和 `setHeight()` 的行为就不一致了，这就违反了里氏替换原则。
- **接口隔离原则（ISP）** ：客户端不应该依赖那些它不需要的接口，即接口应该小而专。例子：一个 Worker 接口定义了 `work()`、`eat()`、`sleep()`，但 Robot 类只需要 `work()`，却被迫实现 `eat()` 和 `sleep()`，这就违反了 ISP。应该拆成 `Workable`、`Eatable`、`Sleepable` 多个小接口。
- **依赖倒置原则（DIP）** ：高层模块不应该依赖低层模块，二者都应该依赖于抽象；抽象不应该依赖于细节，细节应该依赖于抽象。

  **通俗理解**："不要依赖具体的东西，要依赖抽象（接口/抽象类）。"

  **没有 DIP 时的问题**：
  ```java
  // 高层模块直接依赖低层模块的具体实现
  class NotificationService {
      private EmailSender sender = new EmailSender();
      public void notify(String msg) {
          sender.send(msg);
      }
  }
  ```
  哪天要换成短信通知，就得改 `NotificationService` 的代码——高层依赖了低层，耦合死了。

  **遵循 DIP 之后**：
  ```java
  // 双方都依赖抽象（接口）
  interface MessageSender {
      void send(String msg);
  }

  class EmailSender implements MessageSender { ... }
  class SmsSender   implements MessageSender { ... }

  class NotificationService {
      private MessageSender sender; // 依赖抽象，不依赖具体实现
      public NotificationService(MessageSender sender) {
          this.sender = sender; // 具体传什么，由调用方决定（依赖注入）
      }
      public void notify(String msg) {
          sender.send(msg);
      }
  }
  ```
  切换通知方式时 `NotificationService` 零改动，只需注入不同的实现即可。这就是**依赖倒置**——高层从"依赖具体类"变为"依赖抽象接口"。
- **最少知识原则 (Law of Demeter)** ：一个对象应当对其他对象有最少的了解，只与其直接的朋友交互。

### 重载与重写有什么区别？

- 重载（Overloading）指的是在同一个类中，可以有多个同名方法，它们具有不同的参数列表（参数类型、参数个数或参数顺序不同），编译器根据调用时的参数类型来决定调用哪个方法。
- 重写（Overriding）指的是子类可以重新定义父类中的方法，方法名、参数列表和返回类型必须与父类中的方法一致，通过@override注解来明确表示这是对父类方法的重写。

重载是指在同一个类中定义多个同名方法，而重写是指子类重新定义父类中的方法。

### 抽象类和普通类区别？

- 实例化：普通类可以直接实例化对象，而抽象类不能被实例化，只能被继承。
- 方法实现：普通类中所有方法**都必须有具体实现**，不能存在未实现的方法；而抽象类中的方法可以有具体实现，也可以有抽象方法（没有方法体，由子类实现）。
- 继承：普通类和抽象类在继承规则上完全一样——都只能被单继承（ `extends` ），都可以实现多个接口（ `implements` ），这一点两者没有区别。


### Java抽象类和接口的区别是什么？

**两者的特点：**

- 抽象类用于描述类的共同特性和行为，可以有成员变量、构造方法和具体方法。适用于有明显继承关系的场景。
- 接口用于定义行为规范，可以多实现，只能有常量和抽象方法（Java 8 以后可以有默认方法和静态方法）。适用于定义类的能力或功能。

**两者的区别：**

- 实现方式：实现接口的关键字为implements，继承抽象类的关键字为extends。一个类可以实现多个接口，但一个类只能继承一个抽象类。所以，使用接口可以间接地实现多重继承。
- 方法方式：Java 8 之前接口只能定义抽象方法，不能有方法体；Java 8 起接口可以定义 `default` 和 `static` 方法（带方法体）。抽象类则一直可以有定义与实现，方法可在抽象类中实现。
- 成员修饰符：接口成员变量默认为 `public static final`，必须赋初值且不可修改；抽象类成员变量默认为 default 权限，可在子类中重新定义或赋值。
- 方法修饰符：接口中的抽象方法默认为 `public abstract`；Java 9 起可定义 `private` 方法辅助 default 方法实现。抽象类中的抽象方法被 `abstract` 修饰，不能用 `private`/`static`/`synchronized`/`native`，以分号结尾、不带花括号。
- 变量类型：抽象类可包含实例变量和静态变量，接口只能包含静态常量。

> [!note] 接口可以"多继承"
> 类只能单继承，但**接口可以用 `extends` 一次继承多个接口**：`interface C extends A, B {}`——实现 `C` 的类要实现 `A`、`B` 的全部抽象方法。JDK 8 起接口能有 default 方法，若两个父接口有**同名 default 方法**，子接口 / 实现类**必须重写**来消除冲突（可用 `A.super.hi()` 明确选一个）。这也正是一道选择题里"B 选项正确"的原因之一：**一个类只能继承一个类，但可实现多个接口；一个接口可继承多个接口**。

> [!note] `abstract` 的作用范围
> **只能修饰类和方法**，不能修饰变量、字段、局部变量或构造器。`abstract class Shape`、`abstract void draw();` 合法；`abstract int count;` 不合法（编译错误）。

> [!tip] 一句话分辨：抽象类是 is-a，接口是 can-do
> - **抽象类**表达 **"是不是一种（is-a）"**：`Dog is-a Animal`，天然带**共享状态和共享实现**（字段、构造器、已实现方法）——它是"**带共享状态的模板**"，适合"一族相关类复用同一套骨架"。
> - **接口**表达 **"能不能做（can-do）"**：`Bird can Fly`、`User can Serializable`——它只声明**能力契约**，不携带实例状态，一个类可以 can-do 很多件事（多实现）。
>
> 选型口诀：**需要复用状态 / 实现 → 抽象类；需要声明能力 / 让无关类共享契约 → 接口**。想要"多继承的效果"时，用接口（多实现）+ 抽象类（单继承模板）组合。

### 抽象类能加final修饰吗？

**不能** ，Java中的抽象类是用来被继承的，而final修饰符用于禁止类被继承或方法被重写，因此，抽象类和final修饰符是互斥的，不能同时使用。

### 接口里面可以定义哪些方法？

- **抽象方法**

抽象方法是接口的核心部分，所有实现接口的类都必须实现这些方法。抽象方法默认是 public 和 abstract，这些修饰符可以省略。

```java
public interface Animal {
    void makeSound();
}
```
- **默认方法**

默认方法是在 Java 8 中引入的，允许接口提供具体实现。实现类可以选择重写默认方法。

```java
public interface Animal {
    void makeSound();
    
    default void sleep() {
        System.out.println("Sleeping...");
    }
}
```
- **静态方法**

静态方法也是在 Java 8 中引入的，它们属于接口本身，可以通过接口名直接调用，而不需要实现类的对象。

```java
public interface Animal {
    void makeSound();
    
    static void staticMethod() {
        System.out.println("Static method in interface");
    }
}
```
- **私有方法**

私有方法是在 Java 9 中引入的，用于在接口中为默认方法或其他私有方法提供辅助功能。这些方法不能被实现类访问，只能在接口内部使用。

```java
public interface Animal {
    void makeSound();
    
    default void sleep() {
        System.out.println("Sleeping...");
        logSleep();
    }
    
    private void logSleep() {
        System.out.println("Logging sleep");
    }
}
```
```java
public interface Animal {
    void makeSound();
}
```

### 抽象类可以被实例化吗？

在Java中，抽象类本身不能被实例化。

这意味着不能使用 `new` 关键字直接创建一个抽象类的对象。抽象类的存在主要是为了被继承，它通常包含一个或多个抽象方法（由 `abstract` 关键字修饰且无方法体的方法），这些方法需要在子类中被实现。

抽象类可以有构造器，这些构造器在==子类实例化时会被调用，以便进行必要的初始化工作==。然而，这个过程并不是直接实例化抽象类，而是创建了子类的实例，间接地使用了抽象类的构造器。

例如：

```java
public abstract class AbstractClass {
    public AbstractClass() {
        // 构造器代码
    }
    
    public abstract void abstractMethod();
}

public class ConcreteClass extends AbstractClass {
    public ConcreteClass() {
        super(); // 调用抽象类的构造器
    }
    
    @Override
    public void abstractMethod() {
        // 实现抽象方法
    }
}

// 下面的代码可以运行
ConcreteClass obj = new ConcreteClass();
```

在这个例子中， `ConcreteClass` 继承了 `AbstractClass` 并实现了抽象方法 `abstractMethod()` 。当我们创建 `ConcreteClass` 的实例时， `AbstractClass` 的构造器被调用，但这并不意味着 `AbstractClass` 被实例化；实际上，我们创建的是 `ConcreteClass` 的一个对象。

简而言之，抽象类不能直接实例化，但通过继承抽象类并实现所有抽象方法的子类是可以被实例化的。

### 接口可以包含构造函数吗？

在接口中，不可以有构造方法,在接口里写入构造方法时，编译器提示：Interfaces cannot have constructors，因为接口不会有自己的实例的，所以不需要有构造函数。

为什么呢？构造函数就是初始化class的属性或者方法，在new的一瞬间自动调用，那么问题来了Java的接口，都不能new 那么要构造函数干嘛呢？根本就没法调用

### 解释Java中的静态变量和静态方法

在Java中，静态变量和静态方法是与类本身关联的，而不是与类的实例（对象）关联。它们在内存中只存在一份，可以被类的所有实例共享。

> 静态变量

静态变量（也称为类变量）是在类中使用 `static` 关键字声明的变量。它们属于类而不是任何具体的对象。主要的特点：

- **共享性** ：所有该类的实例共享同一个静态变量。如果一个实例修改了静态变量的值，其他实例也会看到这个更改。
- **初始化** ：静态变量在类加载时初始化，在程序运行期间 **只分配一次内存**，所有实例共享同一个副本。而实例变量每 `new` 一次就重新分配一次，每个对象有自己独立的副本。
- **访问方式** ：静态变量可以直接通过类名访问，也可以通过实例访问，但推荐使用类名。

示例：

```java
public class MyClass {
    static int staticVar = 0; // 静态变量

    public MyClass() {
        staticVar++; // 每创建一个对象，静态变量自增
    }
    
    public static void printStaticVar() {
        System.out.println("Static Var: " + staticVar);
    }
}

// 使用示例
MyClass obj1 = new MyClass();
MyClass obj2 = new MyClass();
MyClass.printStaticVar(); // 输出 Static Var: 2
```

> 静态方法

静态方法是在类中使用 `static` 关键字声明的方法。类似于静态变量，静态方法也属于类，而不是任何具体的对象。主要的特点：

- **无实例依赖** ：静态方法可以在没有创建类实例的情况下调用。对于静态方法来说，不能直接访问非静态的成员变量或方法，因为静态方法没有上下文的实例。
- **访问静态成员** ：静态方法可以直接调用其他静态变量和静态方法，但不能直接访问非静态成员。
- **多态性** ：静态方法不支持重写（Override），但可以被隐藏（Hide）。
```java
public class MyClass {
    static int count = 0;

    // 静态方法
    public static void incrementCount() {
        count++;
    }

    public static void displayCount() {
        System.out.println("Count: " + count);
    }
}

// 使用示例
MyClass.incrementCount(); // 调用静态方法
MyClass.displayCount();   // 输出 Count: 1
```

> 使用场景

- **静态变量** ：常用于需要在所有对象间共享的数据，如计数器、常量等。
- **静态方法** ：常用于助手方法（utility methods）、获取类级别的信息或者是没有依赖于实例的数据处理。

### 非静态内部类和静态内部类的区别？

**记住一个根因就够了**：非静态内部类**持有外部类实例的引用**（编译器加了个 `this$0` 字段），静态内部类**不持有**。所有区别都从这里推导。

| 区别维度 | 非静态内部类（有外部类引用） | 静态内部类（无外部类引用） |
|---------|---------------------------|--------------------------|
| **如何实例化** | 必须通过外部类实例：`outer.new Inner()` | 独立实例化：`new Outer.Inner()` |
| **访问外部实例成员** | ✅ 直接访问（通过隐含引用） | ❌ 必须通过 `new Outer().xxx` |
| **访问外部静态成员** | ✅ 直接访问 | ✅ 直接访问 |
| **定义静态成员** | ❌ 不能（Java 16 前） | ✅ 可以 |
| **依赖外部类实例** | ✅ 依赖 | ❌ 不依赖 |

**推导记忆法**：

```
非静态内部类有外部类实例引用
  ├→ 必须先有外部类实例才能 new           → 实例化必须 outer.new Inner()
  ├→ 能直接访问外部类的实例变量和方法      → 因为有引用，拿来就用
  └→ 不能定义静态成员                    → 因为静态成员不属于实例，而它自己还依赖外部实例，逻辑矛盾

静态内部类没有外部类实例引用
  ├→ 不依赖外部类实例就能 new            → 独立实例化
  ├→ 不能直接访问外部实例成员             → 因为没有引用
  └→ 可以定义静态成员                    → 它自己就像外部类的静态成员，同级
```

代码示例：

```java
public class Outer {
    private String instanceField = "实例变量";
    private static String staticField = "静态变量";

    // 非静态内部类：持有外部类实例引用
    class InnerNonStatic {
        // static String staticField2 = ""; // ❌ 编译错误：非静态内部类不能定义静态成员
        void access() {
            // ✅ 可以直接访问外部类的实例成员和静态成员
            System.out.println(instanceField); // 直接访问实例变量
            System.out.println(staticField);   // 直接访问静态变量
        }
    }

    // 静态内部类：不持有外部类实例引用
    static class InnerStatic {
        static String staticField2 = "静态内部类的静态成员"; // ✅ 可以定义静态成员
        void access() {
            // System.out.println(instanceField); // ❌ 编译错误：不能直接访问实例变量
            System.out.println(staticField); // ✅ 可以直接访问外部类的静态变量
            // 想访问实例变量？得先拿到外部类实例
            Outer o = new Outer();
            System.out.println(o.instanceField); // ✅ 通过外部类实例间接访问
        }
    }
}

// 使用方式对比
public class Main {
    public static void main(String[] args) {
        // 非静态内部类：必须先有外部类实例才能创建
        Outer outer = new Outer();
        Outer.InnerNonStatic innerNS = outer.new InnerNonStatic();

        // 静态内部类：不依赖外部类实例，直接创建
        Outer.InnerStatic innerS = new Outer.InnerStatic();
    }
}
```

### 非静态内部类可以直接访问外部方法，编译器是怎么做到的？

**非静态内部类可以直接访问外部方法是因为编译器在生成字节码时会为非静态内部类维护一个指向外部类实例的引用。**

这个引用使得非静态内部类能够访问外部类的实例变量和方法。编译器会在生成非静态内部类的构造方法时，将外部类实例作为参数传入，并在内部类的实例化过程中建立外部类实例与内部类实例之间的联系，从而实现直接访问外部方法的功能。

## 关键字

> [!note] 方法声明修饰符顺序（笔试题）
> Java 方法声明的顺序是：`访问修饰符 static final <泛型参数> 返回类型 方法名(...)`。例：`public static final <T> T identity(T value)`。
> - **泛型 `<T>` 写在返回类型前**；`static`、`final` 写在返回类型前，**二者顺序可互换**（`public final static` 也合法）。
> - `final` 表示方法不能被重写；`static` 表示方法属于类（用类名即可调用）。

### Java 中 final 作用是什么？

`final` 关键字主要有以下三个方面的作用：用于修饰类、方法和变量。

- 修饰类：当 `final` 修饰一个类时，表示这个类不能被继承，是类继承体系中的最终形态。例如，Java 中的 `String` 类就是用 `final` 修饰的，这保证了 `String` 类的不可变性和安全性，防止其他类通过继承来改变 `String` 类的行为和特性。
- 修饰方法：用 `final` 修饰的方法不能在子类中被**重写（Override）**，但可以**重载（Overload）**——因为重载是在同一个类中定义不同参数列表的同名方法，不涉及继承。比如 `final void process(int a)` 和 `final void process(String b)` 可以共存，都在同一个类中，各自 final 互不影响。
- 修饰变量：当 `final` 修饰基本数据类型的变量时，该变量一旦被赋值就不能再改变。例如， `final int num = 10;`，这里的 `num` 就是一个常量，不能再对其进行重新赋值操作，否则会导致编译错误。对于引用数据类型， `final` 修饰意味着这个引用变量不能再指向其他对象，但对象本身的内容是可以改变的。例如， `final StringBuilder sb = new StringBuilder("Hello");`，不能让 `sb` 再指向其他 `StringBuilder` 对象，但可以通过 `sb.append(" World");`来修改字符串的内容。

### Java 中 static的作用是什么？

`static` 关键字主要用于修饰类的成员（变量、方法、代码块）和内部类，其核心作用是 **将成员与类本身关联，而非与类的实例（对象）关联** 。具体作用如下：

> 1、修饰变量

被 `static` 修饰的变量属于类本身，而非类的某个实例。所有对象共享同一份静态变量，内存中只存在一份副本。可以通过 `类名.变量名` 直接访问，无需创建对象（也可通过对象访问，但不推荐）。

通常用于存储所有对象共享的数据，如常量、计数器等。

```java
public class Student {
    // 静态变量（所有学生共享同一个学校名称）
    public static String schoolName = "阳光中学";
    // 实例变量（每个学生有自己的姓名）
    private String name;
}

// 访问静态变量
public class Test {
    public static void main(String[] args) {
        System.out.println(Student.schoolName); // 直接通过类名访问
    }
}
```

> 2、修饰方法

静态方法属于类，不属于任何实例，因此 **不能直接访问类中的非静态成员（变量 / 方法）** （因为非静态成员依赖于对象存在），但可以访问静态成员。通过 `类名.方法名` 直接调用，无需创建对象。

通常用于工具类方法（如 `Math.random()` ）、工厂方法等，不需要依赖对象状态即可完成操作。

```java
public class MathUtils {
    // 静态方法（无需创建对象即可调用）
    public static int add(int a, int b) {
        return a + b;
    }
}

// 调用静态方法
public class Test {
    public static void main(String[] args) {
        int result = MathUtils.add(2, 3); // 直接通过类名调用
    }
}
```

> 3、修饰代码块

静态代码块在 **类初始化阶段** （即执行 `<clinit>` 时）执行，且只执行一次（优于对象构造方法），用于初始化静态变量或执行类级别的预处理操作。JVM 的类生命周期为：加载 → 链接（验证、准备、解析）→ 初始化，静态代码块属于"初始化"阶段而非"加载"阶段。

多个静态代码块按定义顺序执行，且先于非静态代码块和构造方法。

```java
public class Database {
    private static String url;
    
    // 静态代码块：初始化静态变量
    static {
        url = "jdbc:mysql://localhost:3306/test";
        System.out.println("数据库连接地址初始化完成");
    }
}
```

> **补充：非静态代码块（构造块 / 实例初始化块）**
>
> 它是写在类里、方法外的一对 `{}`，没有 `static`、没有名字。**每创建一个对象就执行一次，且先于构造方法的方法体**（编译器会把它复制到每个构造方法开头、`super(...)` 之后）。平时少见，是因为大多数初始化直接写在构造方法里。
>
> 有继承关系时的**执行顺序**（面试常考，`new` 子类时）：**父类静态块 → 子类静态块**（类初始化，只一次）→ **父类构造块 → 父类构造方法** → **子类构造块 → 子类构造方法**。
>
> 用途：多个构造方法有共同初始化逻辑时放进构造块避免重复；匿名内部类没有构造方法，也常用它初始化（"双括号初始化"`new ArrayList<>(){{ add("a"); }}`，但会多生成一个匿名类，一般不推荐）。
>
> ```java
> class Demo {
>     { System.out.println("构造块"); }        // 每次 new 都执行
>     Demo() { System.out.println("构造方法"); }
> }
> // new Demo() 输出：构造块 → 构造方法
> ```

> 4、修饰内部类

静态内部类不依赖于外部类的实例，可以独立存在， **不能直接访问外部类的非静态成员** （需通过外部类实例访问）。

==当内部类与外部类的实例无关时使用，避免内部类持有外部类的引用导致的内存泄漏。==

```java
public class OuterClass {
    private static int staticVar = 10;
    private int instanceVar = 20;
    
    // 静态内部类
    public static class StaticInnerClass {
        public void print() {
            System.out.println(staticVar); // 可访问外部类静态变量
            // System.out.println(instanceVar); // 错误：不能直接访问非静态变量
        }
    }
}

// 使用静态内部类
public class Test {
    public static void main(String[] args) {
        OuterClass.StaticInnerClass inner = new OuterClass.StaticInnerClass();
        inner.print();
    }
}
```

## 深拷贝和浅拷贝

### 深拷贝和浅拷贝的区别？

![img](https://cdn.xiaolincoding.com//picgo/1720683675376-c5af6668-4538-479f-84e8-42d4143ab101.webp)

**浅拷贝**：创建一个新对象，将原对象的**所有字段值原样复制**到新对象中。对于基本类型字段复制的是值本身，对于引用类型字段复制的是引用（地址），新旧对象的引用字段指向同一实例，修改时会互相影响。

**深拷贝**：复制对象的同时，递归复制其内部所有引用类型字段所引用的对象，生成一个完全独立的对象副本。新旧对象不共享任何实例，互不干扰。

代码示例：

```java
class Address {
    String city;
    Address(String city) { this.city = city; }
}

class Person implements Cloneable {
    String name;       // 值类型（String 虽然是引用，但不可变）
    Address address;   // 引用类型
    Person(String name, Address address) {
        this.name = name; this.address = address;
    }
    // 浅拷贝
    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone(); // 只复制一层，address 指向同一个对象
    }
}

// 浅拷贝测试
Person p1 = new Person("张三", new Address("北京"));
Person p2 = (Person) p1.clone();

p2.name = "李四";           // ✅ 互不影响，String 不可变
p2.address.city = "上海";   // ❌ 相互影响！p1.address.city 也变成"上海"
System.out.println(p1.address.city); // 输出：上海（被改了）
```

对比一下就清楚了：**浅拷贝只复制了一层 `address` 的引用，深拷贝连 `Address` 对象也复制一份新的**。


### 实现深拷贝的三种方法是什么？

在 Java 中，实现对象深拷贝的方法有以下几种主要方式：

> 实现 Cloneable 接口并重写 clone() 方法

这种方法要求==对象及其所有引用类型字段==都实现 Cloneable 接口，并且重写 clone() 方法。在 clone() 方法中，通过递归克隆引用类型字段来实现深拷贝。

```java
class MyClass implements Cloneable {
    private String field1;
    private NestedClass nestedObject;

    @Override
    protected Object clone() throws CloneNotSupportedException {
        MyClass cloned = (MyClass) super.clone();
        cloned.nestedObject = (NestedClass) nestedObject.clone(); // 深拷贝内部的引用对象
        return cloned;
    }
}

class NestedClass implements Cloneable {
    private int nestedField;

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
```

> 使用序列化和反序列化

通过将对象序列化为字节流，再从字节流反序列化为对象来实现深拷贝。要求对象及其所有引用类型字段都实现 Serializable 接口。

```java
import java.io.*;

class MyClass implements Serializable {
    private String field1;
    private NestedClass nestedObject;

    public MyClass deepCopy() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            oos.flush();
            oos.close();

            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (MyClass) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}

class NestedClass implements Serializable {
    private int nestedField;
}
```

> 手动递归复制

针对特定对象结构，手动递归复制对象及其引用类型字段。适用于对象结构复杂度不高的情况。

```java
class MyClass {
    private String field1;
    private NestedClass nestedObject;

    public MyClass deepCopy() {
        MyClass copy = new MyClass();
        copy.setField1(this.field1);
        copy.setNestedObject(this.nestedObject.deepCopy());
        return copy;
    }
}

class NestedClass {
    private int nestedField;

    public NestedClass deepCopy() {
        NestedClass copy = new NestedClass();
        copy.setNestedField(this.nestedField);
        return copy;
    }
}
```

## 泛型

### 什么是泛型？

泛型是 Java 编程语言中的一个重要特性，它允许类、接口和方法在定义时使用一个或多个类型参数，这些类型参数在使用时可以被指定为具体的类型。

==泛型的主要目的是在编译时提供更强的类型检查（比如往 `List<String>` 里放 `Integer` 会直接编译报错），并通过**类型擦除**移除类型参数，避免了在运行时手动强制转换可能出现的 `ClassCastException`。==


[[泛型擦除]]


用 `List<String>` 来理解泛型的三个阶段：

| 阶段              | 代码表现                                                                                           | 说明                                            |
| --------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------- |
| **源码阶段**        | `List<String> list = new ArrayList<>();`<br>`list.add("hello");`<br>`list.add(123); // ❌ 编译报错` | 泛型做类型检查，放错类型编译期就拦住了                           |
| **编译后（字节码）**    | `List list = new ArrayList();`<br>`list.add("hello");`<br>`String s = (String) list.get(0);`   | 类型参数 `<String>` 被**擦除**，变成裸 `List`，编译器自动插入了强转 |
| **运行时（JVM 执行）** | JVM 看到的是裸 `List`，不知道它原本是 `List<String>`                                                        | 但由于编译时已保证里面全是 String，强转**一定成功**               |
|                 |                                                                                                |                                               |

**对比没有泛型时**：`List` 里可以混放 `"hello"` 和 `123`，取出来强转时 `ClassCastException` 就炸了。泛型就是把这个问题从**运行时报错**提前到了**编译时报错**。


> 为什么需要泛型？

- **适用于多种数据类型执行相同的代码**
```java
private static int add(int a, int b) {
    System.out.println(a + "+" + b + "=" + (a + b));
    return a + b;
}

private static float add(float a, float b) {
    System.out.println(a + "+" + b + "=" + (a + b));
    return a + b;
}

private static double add(double a, double b) {
    System.out.println(a + "+" + b + "=" + (a + b));
    return a + b;
}
```

如果没有泛型，要实现不同类型的加法，每种类型都需要重载一个add方法；通过泛型，我们可以复用为一个方法：

```java
private static <T extends Number> double add(T a, T b) {
    System.out.println(a + "+" + b + "=" + (a.doubleValue() + b.doubleValue()));
    return a.doubleValue() + b.doubleValue();
}
```
- **泛型中的类型在使用时指定，不需要强制类型转换** （ **类型安全** ，编译器会 **检查类型** ）

看下这个例子：

```java
List list = new ArrayList();
list.add("xxString");
list.add(100d);
list.add(new Person());
```

我们在使用上述list中，list中的元素都是Object类型（无法约束其中的类型），所以在取出集合元素时需要人为的强制类型转化到具体的目标类型，且很容易出现java.lang.ClassCastException异常。

引入泛型，它将提供类型的约束，提供编译前的检查：

```java
List<String> list = new ArrayList<String>();

// list中只能放String, 不能放其它类型的元素
```

## 对象

### java创建对象有哪些方式？

在Java中，创建对象的方式有多种，常见的包括：

**1、使用new关键字** ：这是最常见、最基础的创建对象方式。通过调用类的构造器来实例化对象。

```java
// 定义一个类
public class Person {
    private String name;
    
    public Person() {} // 默认构造器
    public Person(String name) { // 带参构造器
        this.name = name;
    }
    
    public void sayHello() {
        System.out.println("Hello, " + name);
    }
}

// 使用 new 创建对象
public class Main {
    public static void main(String[] args) {
        Person person1 = new Person(); // 调用无参构造
        Person person2 = new Person("Alice"); // 调用有参构造
        
        person2.sayHello(); // 输出: Hello, Alice
    }
}
```

优点是简单、直接、明确。缺点是紧密耦合，必须知道具体的类名。

**2、使用Class类或Constructor类的newInstance()方法** ：通过 Java 的反射 API，在运行时动态地创建对象。这种方式不需要在编译时知道具体的类。

[[不需要在编译时知道具体的类-含义]]

```java
MyClass obj = (MyClass) Class.forName("com.example.MyClass").getDeclaredConstructor().newInstance();
```

应用场景：框架设计（如 Spring 的 IOC 容器）、动态代理等。

**注意** ： `Class.newInstance()` 在 JDK 9 后被标记为过时，因为它只能调用无参公有构造器，且会抛出所有受检异常。推荐使用 `Constructor.newInstance()` ，更强大也更灵活。

[[newInstance到底是哪个类的方法]]

```java
Constructor<MyClass> constructor = MyClass.class.getConstructor();
MyClass obj = constructor.newInstance();
```

**3、使用clone()方法** ：通过实现 `Cloneable` 接口并重写 `Object` 类的 `clone()` 方法，可以基于一个现有对象（原型）创建一个新的副本对象。

```java
// 实现 Cloneable 接口
public class Person implements Cloneable {
    private String name;
    
    // ... 构造器和其他方法 ...
    
    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone(); // 浅拷贝
    }
}

public class Main {
    public static void main(String[] args) {
        Person original = new Person("Charlie");
        try {
            Person copy = (Person) original.clone(); // 创建副本
            copy.sayHello(); // 输出: Hello, Charlie
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }
}
```

`Object.clone()` 默认是浅拷贝，对于引用类型的字段，复制的是引用地址，而不是引用的对象本身。如果需要深拷贝，必须在 `clone()` 方法中手动对引用对象进行克隆。

**4、使用反序列化** ：通过 `ObjectInputStream` 从一个字节流（通常是文件或网络）中重建一个对象。

```java
import java.io.*;

// 必须实现 Serializable 接口
public class Person implements Serializable {
    private String name;
    // ... 构造器和其他方法 ...
}

public class Main {
    public static void main(String[] args) {
        Person personToSave = new Person("David");
        
        // 序列化对象到文件
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("person.dat"))) {
            oos.writeObject(personToSave);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 从文件反序列化对象
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("person.dat"))) {
            Person restoredPerson = (Person) ois.readObject(); // 创建新对象
            restoredPerson.sayHello(); // 输出: Hello, David
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
```

特点是不会调用类的任何构造器，类必须实现 `java.io.Serializable` 接口。

**5、使用工厂模式** ：这是一种设计模式，不直接使用 `new` ，而是==通过一个方法来返回对象实例==。 `getInstance()` 、 `valueOf()` 等都是常见的工厂方法。

```java
public class Person {
    private String name;
    
    private Person(String name) { // 构造器可以是私有的
        this.name = name;
    }
    
    // 静态工厂方法
    public static Person createPerson(String name) {
        // 这里可以做一些额外的逻辑，比如缓存、日志、返回子类实例等
        return new Person(name);
    }
}

public class Main {
    public static void main(String[] args) {
        // 不是用 new，而是用工厂方法创建
        Person person = Person.createPerson("Eva");
        person.sayHello();
    }
}
```

优点是将对象的创建与使用分离，降低耦合，还可以隐藏创建对象的复杂逻辑（如池化技术、缓存）。

Java 标准库中的例子： `Integer.valueOf(int)` ， `Calendar.getInstance()` 。

最后来个，总结对比：

| 方式 | 核心原理 | 是否调用构造器？ | 特点与应用场景 |
| --- | --- | --- | --- |
| **`new` 关键字** | JVM 指令 | **是** | 最标准、最常用，紧密耦合 |
| **反射** | 运行时类信息 | **是** (`Constructor`) | 灵活，解耦，用于框架 |
| **`clone()`** | 复制现有对象 | **否** | 基于原型创建副本，需实现 `Cloneable` |
| **反序列化** | 从字节流恢复 | **否** | 用于持久化和网络通信，需实现 `Serializable` |
| **工厂模式** | 方法封装 `new` | **是** (在方法内) | 解耦，隐藏创建逻辑，控制实例 |

### New出的对象什么时候回收？

通过过关键字 `new` 创建的对象，由Java的垃圾回收器（Garbage Collector）负责回收。垃圾回收器的工作是在程序运行过程中自动进行的，它会周期性地检测不再被引用的对象，并将其回收释放内存。

具体来说，Java对象的回收时机是由垃圾回收器根据以下机制来决定的：

1. 可达性分析算法：HotSpot JVM 使用的核心判活算法。从一组 GC Roots（如虚拟机栈中引用的对象、方法区中类静态属性引用的对象、方法区中常量引用的对象、本地方法栈中 JNI 引用的对象等）出发，通过对象之间的引用链进行遍历，如果存在一条引用链到达某个对象，则说明该对象是可达的，反之不可达，不可达的对象将被回收。注意：Java 并 **不使用引用计数法** ，因为引用计数无法解决循环引用问题。
2. 终结器（Finalizer）：如果对象重写了 `finalize()` 方法，垃圾回收器会在回收该对象之前调用 `finalize()` 方法，对象可以在 `finalize()` 方法中进行一些清理操作。然而，终结器机制的使用不被推荐，因为它的执行时间是不确定的，可能会导致不可预测的性能问题， `finalize()` 方法自 Java 9 起已被标记为 `@Deprecated` 。

### 如何获取私有对象？

在 Java 中，私有对象通常指的是类中被声明为 `private` 的成员变量或方法。由于 `private` 访问修饰符的限制，这些成员只能在其所在的类内部被访问。

不过，可以通过下面两种方式来间接获取私有对象。

- 使用公共访问器方法（getter 方法）：如果类的设计者遵循良好的编程规范，通常会为私有成员变量提供公共的访问器方法（即 `getter` 方法），通过调用这些方法可以安全地获取私有对象。
```java
class MyClass {
    // 私有成员变量
    private String privateField = "私有字段的值";

    // 公共的 getter 方法
    public String getPrivateField() {
        return privateField;
    }
}

public class Main {
    public static void main(String[] args) {
        MyClass obj = new MyClass();
        // 通过调用 getter 方法获取私有对象
        String value = obj.getPrivateField();
        System.out.println(value); 
    }
}
```
- 反射机制。反射机制允许在运行时检查和修改类、方法、字段等信息，通过反射可以绕过 `private` 访问修饰符的限制来获取私有对象。
```java
import java.lang.reflect.Field;

class MyClass {
    private String privateField = "私有字段的值";
}

public class Main {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        MyClass obj = new MyClass();
        // 获取 Class 对象
        Class<?> clazz = obj.getClass();
        // 获取私有字段
        Field privateField = clazz.getDeclaredField("privateField");
        // 设置可访问性
        privateField.setAccessible(true);
        // 获取私有字段的值
        String value = (String) privateField.get(obj);
        System.out.println(value); 
    }
}
```

## 反射

### 什么是反射？

Java 反射机制是指在**运行状态**中，对于任意一个类都能获取其完整属性和方法信息，对于任意一个对象都能调用其方法和属性（包括私有成员）。这种动态获取信息、动态调用对象功能的能力称为 Java 的反射机制。

反射具有以下特性：

1. **运行时类信息访问** ：反射机制允许程序在运行时获取类的完整结构信息，包括类名、包名、父类、实现的接口、构造函数、方法和字段等。
2. **动态对象创建** ：可以使用反射API动态地创建对象实例，即使在编译时不知道具体的类名。通常通过 `Constructor.newInstance()` 方法实现（ `Class.newInstance()` 自 Java 9 起已被标记为 `@Deprecated` ，推荐使用 `clazz.getDeclaredConstructor().newInstance()` ）。
3. **动态方法调用** ：可以在运行时动态地调用对象的方法，包括私有方法。这通过Method类的invoke()方法实现，允许你传入对象实例和参数值来执行方法。
4. **访问和修改字段值** ：反射还允许程序在运行时访问和修改对象的字段值，即使是私有的。这是通过Field类的get()和set()方法完成的。

### 反射在你平时写代码或者框架中的应用场景有哪些?

> 加载数据库驱动

我们的项目底层数据库有时是用mysql，有时用oracle，需要动态地根据实际情况加载驱动类，这个时候反射就有用了，假设 com.mikechen.java.myqlConnection，com.mikechen.java.oracleConnection这两个类我们要用。

这时候我们在使用 JDBC 连接数据库时使用 Class.forName()通过反射加载数据库的驱动程序，如果是mysql则传入mysql的驱动类，而如果是oracle则传入的参数就变成另一个了。

```java
//  DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
Class.forName("com.mysql.cj.jdbc.Driver");
```

> 配置文件加载

Spring 框架的 IOC（动态加载管理 Bean），Spring通过配置文件配置各种各样的bean，你需要用到哪些bean就配哪些，spring容器就会根据你的需求去动态加载，你的程序就能健壮地运行。

Spring通过XML配置模式装载Bean的过程：

- 将程序中所有 XML 或 properties 配置文件加载入内存
- 解析配置内容，得到实体类的**全限定名**（如 `"com.example.User"`，此时还只是个字符串）以及相关的属性配置
- 使用反射机制，通过 `Class.forName("com.example.User")` 把这个类名字符串转成真正的 `Class<?>` 实例
- 通过 `clazz.getDeclaredConstructor().newInstance()` 创建对象，并动态注入属性

配置文件

```java
className=com.example.reflectdemo.TestInvoke
methodName=printlnState
```

实体类

```java
public class TestInvoke {
    private void printlnState(){
        System.out.println("I am fine");
    }
}
```

解析配置文件内容

```java
// 解析xml或properties里面的内容，得到对应实体类的字节码字符串以及属性信息
public static String getName(String key) throws IOException {
    Properties properties = new Properties();
    FileInputStream in = new FileInputStream("D:/IdeaProjects/AllDemos/language-specification/src/main/resources/application.properties");
    properties.load(in);
    in.close();
    return properties.getProperty(key);
}
```

利用反射获取实体类的Class实例，创建实体类的实例对象，调用方法

```java
public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException, ClassNotFoundException, InstantiationException {
    // 使用反射机制，根据这个字符串获得Class对象
    Class<?> c = Class.forName(getName("className"));
    System.out.println(c.getSimpleName());
    // 获取方法
    Method method = c.getDeclaredMethod(getName("methodName"));
    // 绕过安全检查
    method.setAccessible(true);
    // 创建实例对象（Class.newInstance() 已过时，使用 Constructor.newInstance()）
    TestInvoke testInvoke = (TestInvoke) c.getDeclaredConstructor().newInstance();
    // 调用方法
    method.invoke(testInvoke);

}
```

运行结果：

## 注解

### 能讲一讲Java注解的原理吗？

> [!abstract]- **注解运行原理 — Java Annotation（注解）**
> 注解本质是继承了 `Annotation` 的特殊接口。运行时获取注解时，返回的是 JVM 动态生成的**代理对象**（`$Proxy`），其方法调用经 `AnnotationInvocationHandler` 转发为 `memberValues` Map 查表，而 Map 的值来自 `.class` 文件的**常量池**。
> 
> **面试高频坑**：⚠ 注解方法不是真正执行的代码，只是查 Map，注意和普通接口区分。详见 [[06-其他杂项/注解原理详解]]

### 对注解解析的底层实现了解吗？

注解本质上是一种特殊的接口，它继承自 `java.lang.annotation.Annotation` 接口， **所以注解也叫声明式接口** ，例如，定义一个简单的注解：

```java
public @interface MyAnnotation {
    String value();
}
```

编译后，Java 编译器会将其转换为一个继承自 `Annotation` 的接口，并生成相应的字节码文件。

根据注解的作用范围，Java 注解可以分为以下几种类型：

- **源码级别注解** ：仅存在于源码中，编译后不会保留（ `@Retention(RetentionPolicy.SOURCE)` ）。
- **类文件级别注解** ：保留在 `.class` 文件中，但运行时不可见（ `@Retention(RetentionPolicy.CLASS)` ）。
- **运行时注解** ：保留在 `.class` 文件中，并且可以通过反射在运行时访问（ `@Retention(RetentionPolicy.RUNTIME)` ）。

只有运行时注解可以通过反射机制进行解析。

当注解被标记为 `RUNTIME` 时，Java 编译器会在生成的 `.class` 文件中保存注解信息。这些信息存储在字节码的属性表（Attribute Table）中，具体包括以下内容：

- **RuntimeVisibleAnnotations** ：存储运行时可见的注解信息。
- **RuntimeInvisibleAnnotations** ：存储运行时不可见的注解信息。
- **RuntimeVisibleParameterAnnotations** 和 **RuntimeInvisibleParameterAnnotations** ：存储方法参数上的注解信息。

通过工具（如 `javap -v` ）可以查看 `.class` 文件中的注解信息。

注解的解析主要依赖于 Java 的反射机制。以下是解析注解的基本流程：

1、获取注册信息：通过反射 API 可以获取类、方法、字段等元素上的注解。例如：

```java
Class<?> clazz = MyClass.class;
MyAnnotation annotation = clazz.getAnnotation(MyAnnotation.class);
if (annotation != null) {
    System.out.println(annotation.value());
}
```

2、底层原理：反射机制的核心类是 `java.lang.reflect.AnnotatedElement` ，它是所有可以被注解修饰的元素（如 `Class` 、 `Method` 、 `Field` 等）的父接口。该接口提供了以下方法：

- `getAnnotation(Class<T> annotationClass)` ：获取指定类型的注解。
- `getAnnotations()` ：获取所有注解。
- `isAnnotationPresent(Class<? extends Annotation> annotationClass)` ：判断是否包含指定注解。

这些方法本身由纯 Java 实现，并不是 native 方法。以 `Class.getAnnotation(...)` 为例，其内部会先触发注解数据的延迟解析：通过 `AnnotationParser.parseAnnotations(...)` 读取 JVM 在类加载阶段从 class 文件属性表（ `RuntimeVisibleAnnotations` 等）中抽取并传回的原始字节，反序列化成 `Map<Class<? extends Annotation>, Annotation>` ，最终以动态代理对象（ `AnnotationInvocationHandler` ）的形式返回给调用方。

JVM 在类加载阶段确实会解析 `.class` 文件中的注解信息，但这部分工作对应的 native 接口位于底层的常量池与属性表读取，而不是 `getAnnotation` 这一层 API。

==因此，注解解析的底层实现主要依赖于 Java 的反射机制和字节码文件的存储。通过 `@Retention` 元注解可以控制注解的保留策略，当使用 `RetentionPolicy.RUNTIME` 时，可以在运行时通过反射 API 来解析注解信息。在 JVM 层面，会从字节码文件中读取注解信息，并创建注解的代理对象来获取注解的属性值。==

### Java注解的作用域呢？

注解的作用域（Scope）指的是注解可以应用在哪些程序元素上，由元注解 `@Target` 配合 `ElementType` 枚举来指定。在 Java 21 中， `ElementType` 共有 12 种取值：

1. `TYPE` ：类、接口、枚举、注解类型
2. `FIELD` ：字段（包括枚举常量）
3. `METHOD` ：方法
4. `PARAMETER` ：方法或构造器的参数
5. `CONSTRUCTOR` ：构造方法
6. `LOCAL_VARIABLE` ：局部变量
7. `ANNOTATION_TYPE` ：注解类型本身（即元注解）
8. `PACKAGE` ：包（写在 `package-info.java` 中）
9. `TYPE_PARAMETER` ：类型参数声明（Java 8 新增，用于泛型形参上）
10. `TYPE_USE` ：任何使用类型的地方（Java 8 新增，常用于配合 Checker Framework 做类型检查）
11. `MODULE` ：模块（Java 9 模块系统引入）

> [!example]- **`TYPE_PARAMETER` vs `TYPE_USE` 示例**
>
> **`TYPE_PARAMETER`** — 只标在泛型形参 `<T>` 上
> ```java
> @Target(ElementType.TYPE_PARAMETER)
> public @interface NotNull { }
>
> public class Box<@NotNull T> {              // ✅ 泛型形参
>     public <@NotNull R> R transform(T v) {}  // ✅ 泛型形参
> }
> ```
>
> **`TYPE_USE`** — 任何出现类型的地方都能标
> ```java
> @Target(ElementType.TYPE_USE)
> public @interface Nullable { }
>
> @Nullable String name;        // ✅ 变量类型
> List<@Nullable String> list;  // ✅ 泛型参数
> new @Nullable String()        // ✅ new 对象
> (@Nullable String) obj;       // ✅ 强转
> ```
>
> **记忆**：`TYPE_PARAMETER` 只认 `<T>` 的 `T`；`TYPE_USE` 认所有出现类型的地方。

> [!tip]- **⚠ 高频易混：`TYPE` vs `ANNOTATION_TYPE`**
> - `TYPE` 是大圈——类、接口、枚举、**注解类型**都能用
> - `ANNOTATION_TYPE` 是小圈——**只能**用在注解上，专给元注解（如 `@Target`、`@Retention`）用的
> - `@Target(ANNOTATION_TYPE)` 写在注解上 ✅，写在普通类上 ❌ 编译报错
> - 面试常考：`TYPE` 包含注解类型，但 `ANNOTATION_TYPE` 的约束更严格，是 `TYPE` 的子集
12. `RECORD_COMPONENT` ：记录类组件（Java 16 引入 record 时新增）

其中最常用的是 `TYPE` 、 `FIELD` 、 `METHOD` 、 `PARAMETER` 这几种。如果定义注解时不显式指定 `@Target` ，那么该注解默认可以用于上述除 `TYPE_PARAMETER` 和 `TYPE_USE` 之外的所有位置。

## 异常

### 介绍一下Java异常

Java异常类层次结构图：

![](https://cdn.xiaolincoding.com//picgo/1720683900898-1d0ce69d-4b5d-41a6-a5df-022e42f8f4c5.webp)

Java 的异常体系主要基于 `Throwable` 及其子类。 `Throwable` 有两个重要的子类： `Error` 和 `Exception` ，它们分别代表了不同类型的异常情况。

1. **`Error` (错误)** ： 表示运行环境的错误，是程序无法处理的严重问题，程序不应该（也 catch 不住）这类错误。例如：
	- **虚拟机错误** — JVM 自身崩溃，如 `OutOfMemoryError`（堆爆了）、`StackOverflowError`（栈溢出）
	- **动态链接库失效** — Java 调用 native 方法时 `.dll` / `.so` 文件找不到或损坏，如 `UnsatisfiedLinkError`
2. **`Exception` (异常)** ： 表示程序本身可以处理的异常情况。异常分为两大类：
	- **非运行时异常（受检异常，Checked Exception）** ： 这类异常在编译时就必须被捕获或者声明抛出。它们通常是外部错误，如文件不存在 (`FileNotFoundException`)、类未找到 (`ClassNotFoundException`) 等。非运行时异常强制程序员处理这些可能出现的问题，增强了程序的健壮性。
	- **运行时异常（非受检异常，Unchecked Exception 或 RuntimeException）** ： 这类异常特指 `RuntimeException` 及其子类。它与 `Error` 一起构成了 Java 中的 **非受检异常** 家族。==运行时异常由程序逻辑错误导致==，如空指针访问 (`NullPointerException`)、数组越界 (`ArrayIndexOutOfBoundsException`) 等。运行时异常是不需要在编译时强制捕获或声明的。

### Java异常处理有哪些？

异常处理是通过使用try-catch语句块来捕获和处理异常。以下是Java中常用的异常处理方式：

- try-catch语句块：用于捕获并处理可能抛出的异常。==try块中包含可能抛出异常的代码，catch块用于捕获并处理特定类型的异常。可以有多个catch块来处理不同类型的异常。==
```java
try {
    // 可能抛出异常的代码
} catch (ExceptionType1 e1) {
    // 处理异常类型1的逻辑
} catch (ExceptionType2 e2) {
    // 处理异常类型2的逻辑
} catch (ExceptionType3 e3) {
    // 处理异常类型3的逻辑
} finally {
    // 可选的finally块，==用于定义无论是否发生异常都会执行的代码==
}
```
- throw语句：用于==手动抛出异常==。可以根据需要在代码中使用throw语句主动抛出特定类型的异常。
```
throw new ExceptionType("Exception message");
```
- throws关键字：==用于在方法声明中声明可能抛出的异常类型。如果一个方法可能抛出异常，但不想在方法内部进行处理，可以使用throws关键字将异常传递给调用者来处理。==
```java
public void methodName() throws ExceptionType {
    // 方法体
}
```
- finally块：==用于定义无论是否发生异常都会执行的代码块== 通常用于释放资源，确保资源的正确关闭。
```java
try {
    // 可能抛出异常的代码
} catch (ExceptionType e) {
    // 处理异常的逻辑
} finally {
    // 无论是否发生异常，都会执行的代码
}
```

### Java为什么区分受检异常和非受检异常？

Java的设计者认为，并非所有异常都是平等的。有些异常是**程序员应该预见并处理**的，有些则是**不可预见的故障**。

**受检异常（Checked Exception）** 代表那些**可恢复的、程序员应该预见的问题**。比如文件不存在、网络连接失败、数据库查询异常——这些都是在正常业务流程中可能发生的情况。Java通过强制编译器检查，确保程序员必须显式处理这些异常，从而提高代码的健壮性和可靠性。这是一种**契约式编程**的体现：方法签名明确告诉调用者"我可能抛出这些异常，你必须处理"。

**非受检异常（Unchecked Exception）** 则代表**程序设计错误或不可恢复的故障**。NullPointerException说明你的代码有bug，ArrayIndexOutOfBoundsException表示逻辑错误。对这类异常强制处理没有意义——你应该通过改进代码设计来避免它们，而不是用try-catch来掩盖问题。OutOfMemoryError这样的系统级错误更是无法真正"恢复"的。

这样区分的目的是**在编译时就暴露问题**，而不是等到运行时才发现。它强制程序员思考"这个方法可能失败吗？如果失败了我该怎么办？"

### throws和try-catch如何选择？各自适用于什么场景？

这两种方式处理的是同一类问题（受检异常），但代表了**不同的职责划分**：

**使用try-catch：当前方法有能力处理异常**

如果你的方法知道如何应对某个异常，就应该在这里捕获并处理它。比如：

```java
public void loadUserData(String userId) {
    try {
        User user = fetchFromDatabase(userId);
        processUser(user);
    } catch (SQLException e) {
        logger.error("数据库查询失败，使用默认数据", e);
        User defaultUser = createDefaultUser();
        processUser(defaultUser);
    }
}
```

这里方法知道如何应对SQLException——记录日志、使用默认数据继续执行。异常被妥善处理，调用者无需担心。

**使用throws：当前方法无法或不应该处理异常**

如果异常不在这个方法的职责范围内，或者上层调用者更适合处理，就应该用throws声明并向上传递：

```java
public User getUserFromAPI(String userId) throws IOException, JSONException {
    // 这个方法专注于API调用逻辑，不关心网络错误如何处理
    // 由调用者决定是重试、使用缓存，还是返回错误给用户
    String response = httpClient.get(apiUrl + userId);
    return parseJSON(response);
}
```

这里方法的职责是获取数据，不是处理网络故障。让调用者根据上下文决定如何处理更合理——可能调用者想重试，可能想用缓存数据，可能想向用户报错。

**实际选择标准：**

| 场景 | 选择 | 原因 |
|------|------|------|
| 方法知道如何恢复 | try-catch | 异常是可预期的、可处理的业务情况 |
| 异常是调用者应该知道的 | throws | 调用者需要根据这个异常做出决策 |
| 异常超出方法职责 | throws | 保持方法的单一职责原则 |
| 想隐藏实现细节 | try-catch | 内部异常不应该暴露给调用者 |
| 需要统一的异常处理策略 | throws | 让上层统一处理，避免分散的try-catch块 |

**一个完整的例子：**

```java
// 底层：数据访问层，抛出异常让上层决定
public User getUserFromDB(String id) throws SQLException {
    // 不处理SQL异常，让业务层决定
    return database.query(id);
}

// 中层：业务层，可能处理也可能继续抛出
public User getUser(String id) throws UserNotFoundException {
    try {
        return getUserFromDB(id);
    } catch (SQLException e) {
        throw new UserNotFoundException("数据库访问失败", e);
    }
}

// 上层：控制层，最终处理异常
public void handleUserRequest(String id) {
    try {
        User user = getUser(id);
        displayUser(user);
    } catch (UserNotFoundException e) {
        // 这里知道如何应对：返回404给前端
        response.setStatus(404);
        response.write("用户不存在");
    }
}
```

核心原则是：**异常应该在最合适的层级被处理**——既不要过早地吞掉异常失去信息，也不要一味地向上抛出让调用者困惑。

### try catch中的语句运行情况

try块中的代码将按顺序执行，如果抛出异常，将在catch块中进行匹配和处理，然后程序将继续执行catch块之后的代码。如果没有匹配的catch块，异常将被传递给上一层调用的方法。

### try{return “a”} finally{return “b”}这条语句返回啥

finally块中的return语句会覆盖try块中的return返回，因此，该语句将返回"b"。

## object

### Object类有哪些方法？

Java Object 类是所有类的超类，默认提供 11 个核心方法，核心用于对象比较、哈希、字符串表示、线程同步等。

首先最常用的是 ==equals 方法，它的默认实现是比较两个对象的内存地址，也就是和 == 的效果一样==，但实际开发中我们常需要按对象的内容比较，比如两个用户对象只要 id 相同就认为相等，这时候就需要重写 equals。比如：

```java
class User {
    private int id;
    private String name;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return id == user.id;
    }
}
```

和 equals 配套的必须重写 hashCode 方法，
因为 Java 的约定是如果两个对象 equals 返回 true，它们的 hashCode 必须相等；
如果 hashCode 不相等，equals 一定返回 false。
如果只重写 equals 不重写 hashCode，会导致对象在 HashMap HashSet 等集合中无法正确存储，比如两个 id 相同的 User 对象，equals 返回 true，但 hashCode 不同，会被当成两个不同元素存入集合。**重写示例：

```java
@Override
public int hashCode() {
    return Integer.hashCode(id);
}
```

然后是 ==toString 方法，默认返回类名加 @加对象的哈希码十六进制表示==，比如 User@1b6d3586，可读性很差。实际开发中重写它是为了返回对象的具体信息，方便日志打印和调试，比如：

```java
@Override
public String toString() {
    return "User{id=" + id + ", name=" + name + "}";
}
```

接下来是 getClass 方法，==它返回对象运行时的实际类对象，和编译时类型可能不同，而且这个方法不能重写==。比如父类 Animal 的子类 Dog，Animal animal = new Dog，animal.getClass 返回的是 Dog 的类对象，==常用于反射场景==，比如通过 getClass 获取类的属性和方法：

[[编译时类型和运行时类型可能不同]]

```java
Animal animal = new Dog();
Class<?> clazz = animal.getClass();
System.out.println(clazz.getName()); // 输出Dog
```

clone 方法用于创建对象的浅拷贝。默认是浅拷贝意味着如果对象有引用类型属性，拷贝后的引用和原引用会指向同一个对象。使用 clone 需要让类实现 Cloneable 接口，否则会抛出 CloneNotSupportedException。

```java
class Product implements Cloneable {
    private String name;
    private double price;

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
```

还有 notify 和 notifyAll 方法，它们都是用于多线程同步的，和 synchronized 配合使用，作用是==唤醒等待当前对象锁的线程==。notify 是随机唤醒一个等待线程，notifyAll 是唤醒所有等待线程，比如生产者消费者模式中，生产者生产完数据后调用 notifyAll，唤醒等待的消费者线程。

wait 方法有三个重载，作用是==让当前持有对象锁的线程释放锁并进入等待状态==，直到被 notify notifyAll 唤醒或者等待时间到期。同样需要在 synchronized 同步块或方法中使用，否则会抛 IllegalMonitorStateException，比如：

```java
synchronized (lockObj) {
    while (条件不满足) {
        lockObj.wait(1000); // 等待1秒，超时自动唤醒
    }
    // 执行业务逻辑
}
```

最后是 finalize 方法，它是对象被垃圾回收器回收前会调用的方法，默认是空实现。但现在基本不推荐使用，因为它的执行时机不确定，可能很久才执行甚至不执行，而且可能导致对象复活，影响垃圾回收效率，Java9 之后已经标记为过时，替代方案是使用 try with resources 或者 PhantomReference 来处理资源释放。

### \== 与 equals 有什么区别？

简单来说， **`==` 比较的是"地址"，而 `equals` 比较的是"内容"** 。但这句话如果不展开讲，其实很容易让人懵，我给你详细说一下。

**先说 `==` 这个运算符。**

`==` 在 Java 里其实是个非常 **简单粗暴** 的比较方式。如果你比较的是 **基本数据类型** （比如 int、double），那它就直接比数值是不是相等，这个没啥问题。

但如果你比较的是 **对象（引用类型）** ，那 `==` 比较的就不是对象里面装的内容了，而是比较这两个变量 **是不是指向内存中同一个对象** ，也就是 **地址是否相同** 。

我举个例子你就明白了：

```
String a = new String("hello");
String b = new String("hello");

System.out.println(a == b);  // 输出 false
```

虽然 a 和 b 里面装的内容都是 "hello"，但因为我用了两次 `new` ，所以在堆内存里创建了两个完全独立的 String 对象。a 和 b 这两个变量分别指向了不同的对象，所以 `==` 一比较，发现地址不一样，就返回 false 了。

**再说 `equals` 方法。**

`equals` 是 Object 类里定义的一个方法，所有的 Java 对象都继承了它。它的 **默认行为其实和 `==` 一模一样** ，也是比地址。

但关键在于，很多常用的类（比如 String、Integer）都把这个方法给 **重写** 了。重写之后， `equals` 就不再比地址了，而是去比较 **对象里面实际存储的内容** 是否相等。

还是刚才那个例子：

```
String a = new String("hello");
String b = new String("hello");

System.out.println(a.equals(b));  // 输出 true
```

虽然 a 和 b 是两个不同的对象，但 String 类重写了 `equals` 方法，会逐个字符去比较内容。发现都是 "hello"，所以返回 true。

**这里有个经典的面试陷阱，就是字符串常量池的问题。**

```
String c = "hello";
String d = "hello";

System.out.println(c == d);  // 输出 true
```

这里为什么 `==` 比较也是 true 呢？因为当你直接用双引号创建字符串的时候，JVM 会把它扔到一个叫"字符串常量池"的地方。如果池子里已经有了 "hello"，那 d 就直接复用 c 指向的那个对象，所以它俩地址是一样的， `==` 自然就返回 true 了。

**总结一下：**

- `==` 是比较 **引用地址** ，适合用来判断两个变量是不是指向同一个对象；
- `equals` 是比较 **对象内容** ，但前提是这个类重写了 equals 方法（比如 String），如果没重写，那它俩效果是一样的。

所以在实际开发中，如果我们要比较两个字符串或者对象的内容是否相等，一定要用 `equals` ，而不是 `==` 。这也是为什么阿里巴巴开发规范里明确要求，判断字符串相等必须用 `equals` 的原因。

### hashcode和equals方法有什么关系？

在 Java 中，==对于重写 `equals` 方法的类，通常也需要重写 `hashCode` 方法==，并且需要遵循以下规定：

- **一致性** ：如果两个对象使用 `equals` 方法比较结果为 `true` ，那么它们的 `hashCode` 值必须相同。也就是说，如果 `obj1.equals(obj2)` 返回 `true` ，那么 `obj1.hashCode()` 必须等于 `obj2.hashCode()` 。
- **非一致性** ：如果两个对象的 `hashCode` 值相同，它们使用 `equals` 方法比较的结果不一定为 `true` 。即 `obj1.hashCode() == obj2.hashCode()` 时， `obj1.equals(obj2)` 可能为 `false` ，这种情况称为哈希冲突。

`hashCode` 和 `equals` 方法是紧密相关的，重写 `equals` 方法时必须重写 `hashCode` 方法，以保证在使用哈希表等数据结构时，对象的相等性判断和存储查找操作能够正常工作。而重写 `hashCode` 方法时，需要确保相等的对象具有相同的哈希码，但相同哈希码的对象不一定相等。

### java 里 string的常用方法有哪些？

我常用的 String 常用方法有这些：

- **获取长度** ： `int length()` ：返回字符串的长度（字符个数）。例： `"abc".length()` → 3
- **判断内容** ： `boolean equals(Object obj)` ：比较两个字符串的内容是否完全相同（区分大小写）。例： `"abc".equals("ABC")` → false
- **截取子串：** `String substring(int beginIndex)` ：从指定索引开始截取到末尾。例： `"hello".substring(2)` → "llo"
- **去除空格：** `String trim()` ：去除字符串首尾的空白字符（空格、制表符等）。例： `" abc ".trim()` → "abc"
- **替换内容：** `String replace(char oldChar, char newChar)` ：替换所有指定字符。例： `"aaa".replace('a', 'b')` → "bbb"
- **判断空字符串：** `boolean isEmpty()` ：判断字符串长度是否为 0（注意： `null` 调用会报错，需先判空）。

### String、StringBuffer、StringBuilder的区别和联系

**1、可变性** ： `String` 是不可变的（Immutable），一旦创建，内容无法修改，每次修改都会生成一个新的对象。 `StringBuilder` 和 `StringBuffer` 是可变的（Mutable），可以直接对字符串内容进行修改而不会创建新对象。

**2、线程安全性** ： ==`String` 因为不可变，天然线程安全。== `StringBuilder` 不是线程安全的，适用于单线程环境。 `StringBuffer` 是线程安全的，其方法通过 `synchronized` 关键字实现同步，适用于多线程环境。

**3、性能** ： `String` 性能最低，尤其是在频繁修改字符串时会生成大量临时对象，增加内存开销和垃圾回收压力。 `StringBuilder` 性能最高，因为它没有线程安全的开销，适合单线程下的字符串操作。 `StringBuffer` 性能略低于 `StringBuilder` ，因为它的线程安全机制引入了同步开销。

**4、使用场景** ：如果字符串内容固定或不常变化，优先使用 `String` 。如果需要频繁修改字符串且在单线程环境下，使用 `StringBuilder` 。如果需要频繁修改字符串且在多线程环境下，使用 `StringBuffer` 。

对比总结如下：

| **特性** | **String** | **StringBuilder** | **StringBuffer** |
| --- | --- | --- | --- |
| **不可变性** | 不可变 | 可变 | 可变 |
| **线程安全** | 是（因不可变） | 否 | 是（同步方法） |
| **性能** | 低（频繁修改时） | 高（单线程） | 中（多线程安全） |
| **适用场景** | 静态字符串 | 单线程动态字符串 | 多线程动态字符串 |

例子代码如下：

```java
// String的不可变性
String str = "abc";
str = str + "def"; // 新建对象，str指向新对象

// StringBuilder（单线程高效）
StringBuilder sb = new StringBuilder();
sb.append("abc").append("def"); // 直接修改内部数组

// StringBuffer（多线程安全）
StringBuffer sbf = new StringBuffer();
sbf.append("abc").append("def"); // 同步方法保证线程安全
```

## Java 新特性

### Java 8 你知道有什么新特性？

下面是 Java 8 主要新特性的整理表格，包含关键改进和示例说明：

| **特性名称**              | **描述**                                     | **示例或说明**                                                                                |
| --------------------- | ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| **Lambda 表达式**        | 简化匿名内部类，支持函数式编程                            | `(a, b) -> a + b` 代替匿名类实现接口                                                              |
| **函数式接口**             | 仅含一个抽象方法的接口，可用 `@FunctionalInterface` 注解标记 | `Runnable`, `Comparator`, 或自定义接口 `@FunctionalInterface interface MyFunc { void run(); }` |
| **Stream API**        | 提供链式操作处理集合数据，支持并行处理                        | `list.stream().filter(x -> x > 0).collect(Collectors.toList())`                          |
| **Optional 类**        | 封装可能为 `null` 的对象，减少空指针异常                   | `Optional.ofNullable(value).orElse("default")`                                           |
| **方法引用**              | 简化 Lambda 表达式，直接引用现有方法                     | `System.out::println` 等价于 `x -> System.out.println(x)`                                   |
| **接口的默认方法与静态方法**      | 接口可定义默认实现和静态方法，增强扩展性                       | `interface A { default void print() { System.out.println("默认方法"); } }`                   |
| **并行数组排序**            | 使用多线程加速数组排序                                | `Arrays.parallelSort(array)`                                                             |
| **重复注解**              | 允许同一位置多次使用相同注解                             | `@Repeatable` 注解配合容器注解使用                                                                 |
| **类型注解**              | 注解可应用于更多位置（如泛型、异常等）                        | `List<@NonNull String> list`                                                             |
| **CompletableFuture** | 增强异步编程能力，支持链式调用和组合操作                       | `CompletableFuture.supplyAsync(() -> "result").thenAccept(System.out::println)`          |

> [!tip]- **递进关系：函数式接口 → Lambda → 方法引用**
> - **函数式接口**不是被简化的，它是 Lambda **存在的前提**（接口只有一个抽象方法，Lambda 才能替代匿名内部类去实现它）
> - **Lambda 表达式** — 对匿名内部类的简化
> - **方法引用** — 对 Lambda 的进一步简化（当 Lambda 体只有一个方法调用时）
>
> ```
> 匿名内部类
>     ↓  前提是接口是函数式接口
> Lambda 表达式  ——简化→ 匿名内部类
>     ↓
> 方法引用     ——简化→ Lambda 表达式
> ```

### 函数式接口

函数式接口（Functional Interface）是指**只有一个抽象方法**的接口，它是 Lambda 表达式能够工作的**前提条件**。

```java
@FunctionalInterface
interface MyFunction {
    int apply(int x, int y);
}
```

关键要点：
- **@FunctionalInterface 注解**：用于标记函数式接口，编译器会检查是否符合条件。不加也合法，但加上更安全、更语义化。
- **Java 内置函数式接口**：`Runnable`、`Comparator`、`Callable`，以及 `java.util.function` 包下的 `Consumer`、`Supplier`、`Function`、`Predicate` 等
- **可以有默认方法和静态方法**：函数式接口只限制抽象方法的数量，不限制默认方法和静态方法
- **Lambda 的基础**：Lambda 表达式本质上就是函数式接口的匿名实现，所以编译器要求目标类型必须是函数式接口

```java
// 常见函数式接口示例
Consumer<String> printer = s -> System.out.println(s);
Supplier<Double> random = () -> Math.random();
Function<String, Integer> parser = s -> Integer.parseInt(s);
Predicate<Integer> isEven = n -> n % 2 == 0;
```

> 面试话术：函数式接口的价值在于——它给 Lambda 表达式提供了一个"类型目标"。没有函数式接口，Lambda 就不知道自己要实现什么。

**补充：Lambda 是"对象"吗？** 是的。Lambda 求值得到的是**函数式接口的一个实例**，因此它也是 `Object`——可以赋给 `Object` 变量、放进集合、当参数传递：

```java
Runnable task = () -> System.out.println("运行");
Object o = task;         // ✅ Lambda 是对象
new Thread(task).start();
```

但**不要依赖**它对应某种具体实现类，也**不要依赖**它是否复用同一个对象——具体机制由 JVM 决定，日常把它当"函数式接口的实例"理解就够。（同一处**不捕获**外部变量的 Lambda，JVM 会常量化为同一实例、`==` 为 `true`；**捕获了**变量的则每次新建、`==` 为 `false`，详见 [[Java基础热门面试题#37. Lambda 表达式的原理？和匿名内部类有什么区别？【中频】]]。）

**补充：匿名内部类实现函数式接口（Lambda 的传统写法）**

```java
@FunctionalInterface
interface Greeting {
    void sayHello(String name);
}

// 匿名内部类
Greeting g1 = new Greeting() {
    @Override
    public void sayHello(String name) {
        System.out.println("你好，" + name);
    }
};

// 等价的 Lambda
Greeting g2 = name -> System.out.println("你好，" + name);
```

匿名内部类写法要显式写出接口名和方法签名；Lambda 只保留"参数 + 方法体"。见 [[Java内部类#2.4 匿名内部类]]。

### Lambda 表达式了解吗？

Lambda 表达式它是一种简洁的语法，用于创建匿名函数，主要用于简化函数式接口（只有一个抽象方法的接口）的使用。其基本语法有以下两种形式：

- `(parameters) -> expression` ：当 Lambda 体只有一个表达式时使用，表达式的结果会作为返回值。
- `(parameters) -> { statements; }` ：当 Lambda 体包含多条语句时，需要使用大括号将语句括起来，若有返回值则需要使用 `return` 语句。

传统的匿名内部类实现方式代码较为冗长，而 Lambda 表达式可以用更简洁的语法实现相同的功能。比如，使用匿名内部类实现 `Runnable` 接口

```java
public class AnonymousClassExample {
    public static void main(String[] args) {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Running using anonymous class");
            }
        });
        t1.start();
    }
}
```

使用 Lambda 表达式实现相同功能：

```java
public class LambdaExample {
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> System.out.println("Running using lambda expression"));
        t1.start();
    }
}
```

可以看到，Lambda 表达式的代码更加简洁明了。

还有，Lambda 表达式能够更清晰地表达代码的意图，尤其是在处理集合操作时，如过滤、映射等。比如，过滤出列表中所有偶数

```java
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReadabilityExample {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6);
        // 使用 Lambda 表达式结合 Stream API 过滤偶数
        List<Integer> evenNumbers = numbers.stream()
                                           .filter(n -> n % 2 == 0)
                                           .collect(Collectors.toList());
        System.out.println(evenNumbers);
    }
}
```

通过 Lambda 表达式，代码的逻辑更加直观，易于理解。

还有，Lambda 表达式使得 Java 支持函数式编程范式，允许将函数作为参数传递，从而可以编写更灵活、可复用的代码。比如定义一个通用的计算函数。

```java
interface Calculator {
    int calculate(int a, int b);
}

public class FunctionalProgrammingExample {
    public static int operate(int a, int b, Calculator calculator) {
        return calculator.calculate(a, b);
    }

    public static void main(String[] args) {
        // 使用 Lambda 表达式传递加法函数
        int sum = operate(3, 5, (x, y) -> x + y);
        System.out.println("Sum: " + sum);

        // 使用 Lambda 表达式传递乘法函数
        int product = operate(3, 5, (x, y) -> x * y);
        System.out.println("Product: " + product);
    }
}
```

虽然 Lambda 表达式优点蛮多的，不过也有一些缺点，比如会增加调试困难，因为 Lambda 表达式是匿名的，在调试时很难定位具体是哪个 Lambda 表达式出现了问题。尤其是当 Lambda 表达式嵌套使用或者比较复杂时，调试难度会进一步增加。

### 方法引用

方法引用（Method Reference）是 Lambda 表达式的**进一步简化语法**——当 Lambda 体只是直接调用一个现有方法时，可以用 `::` 操作符把方法名"引用"过来。

**基本语法：** `类名/对象 :: 方法名`

```java
// Lambda 写法
list.forEach(s -> System.out.println(s));

// 方法引用 — 更简洁
list.forEach(System.out::println);
```

**四种形式：**

| 类型           | 语法         | 示例                                                     |
| ------------ | ---------- | ------------------------------------------------------ |
| 静态方法引用       | `类名::静态方法` | `Math::max` 等价于 `(a, b) -> Math.max(a, b)`             |
| 实例方法引用（特定对象） | `对象::实例方法` | `System.out::println` 等价于 `s -> System.out.println(s)` |
| 实例方法引用（任意对象） | `类名::实例方法` | `String::length` 等价于 `s -> s.length()`                 |
| 构造器引用        | `类名::new`  | `ArrayList::new` 等价于 `() -> new ArrayList()`           |

### 两种实例方法引用的区别

实例方法引用有两种写法，核心区别在于**谁来调用这个方法**：

| | `对象::实例方法`（特定对象） | `类名::实例方法`（任意对象） |
|---|---|---|
| **调用者** | 固定的那个对象 | Lambda 的第一个参数本身 |
| **等价 Lambda** | `s -> 固定对象.方法(s)` | `s -> s.方法()` |
| **典型场景** | `System.out::println` | `String::length` |

**特定对象** — 调用者已经定死了，方法引用只是把参数递给它：

```java
PrintStream out = System.out;
list.forEach(out::println);
// 等价于：s -> out.println(s)
//        ↑        ↑
//    forEach传参   调用者永远是固定的 out
```

**任意对象** — 调用者是 Stream 里的每个元素，每个元素"自调用"：

```java
stream.map(String::length);
// 等价于：s -> s.length()
//        ↑    ↑
//   第一个参数    它自己调用
```

简单记忆：**特定对象 = 我有一个对象 A，你把参数 s 给它就行；任意对象 = 你给我一个对象 s，让它自己干活。**

**使用条件：** 只有当 Lambda 体**只有一行**且该行**直接调用了一个已有的方法**时，才能用方法引用替代。如果 Lambda 体中包含多条语句或复杂逻辑，就不能使用方法引用。

```java
// ✅ 可以：直接调用已有方法
stream.map(s -> Integer.parseInt(s))   →  stream.map(Integer::parseInt)

// ❌ 不可以：Lambda 体中有多条语句或参数转换
stream.map(s -> { String trimmed = s.trim(); return Integer.parseInt(trimmed); })
```

> 面试话术：方法引用不是"新功能"，而是"语法糖的语法糖"——它让原本已经简洁的 Lambda 更紧凑，可读性更强。面试中通常理解概念和几种基本形式就够了。

**补充：方法引用 vs Lambda 的字节码差异**

- **Lambda**：Lambda 体被编译成当前类里一个**私有合成方法**（名字形如 `lambda$main$0`），调用点用 `invokedynamic` 去链接它——所以 Lambda 通常**多一个合成方法**。
- **方法引用**：若 Lambda 体只是"把参数原样转交给一个已有方法"，写成方法引用后**不再生成那个合成方法**，`invokedynamic` 的 bootstrap 直接解析到**目标方法本身**（`Integer::parseInt` 直达 `Integer.parseInt`）——少一层转发，语义上更"零成本"。
- **所以**：`Integer::parseInt` 和 `s -> Integer.parseInt(s)` **行为相同、结果相同**，但**字节码不同**。面试里说方法引用"**可能**略优"即可，**不要**说成"性能一定更好"——两者最终都靠 `invokedynamic` + `LambdaMetafactory` 生成实现，差别通常可忽略。
- **checked 异常**：`Function.apply` 等**不声明** `throws`，所以无论方法引用还是 Lambda，只要内部调用的方法抛 **checked 异常**而又没在 Lambda 体内捕获，都会**编译失败**——两者限制一样。（`parseInt` 抛的是运行时异常 `NumberFormatException`，不涉及此限。）

### Java中stream的API介绍一下

[[StreamAPI]]

Java 8引入了Stream API。**Stream（流）不是集合，它不存储数据，而是数据的一个"视图"**——代表一个元素序列，支持对数据源（集合、数组等）进行声明式的链式操作（过滤、映射、排序等）。且 Stream 只能消费一次，用完即止。

Stream API 是 Java 8 提供的一套**基于函数式编程的数据处理框架**，核心是 **"把数据处理定义成一条流水线"**：

- 数据源 → **中间操作**（filter / map / sorted 等，延迟执行）→ **终端操作**（collect / forEach / reduce 等，触发执行）
- 中间操作是**惰性的**，只有遇到终端操作才会真正遍历数据，避免不必要的计算
- 支持**链式调用**，每一步都是声明式（"做什么"），而不是命令式（"怎么做"）

它提供了一种高效且易于使用的数据处理方式，特别适合集合对象的操作，如过滤、映射、排序等。Stream API不仅可以提高代码的可读性和简洁性，还能利用多核处理器的优势进行并行处理。让我们通过两个具体的例子来感受下Java Stream API带来的便利，对比在Stream API引入之前的传统做法。

> 案例1：过滤并收集满足条件的元素

**问题场景** ：从一个列表中筛选出所有长度大于3的字符串，并收集到一个新的列表中。

**没有Stream API的做法** ：

```java
List<String> originalList = Arrays.asList("apple", "fig", "banana", "kiwi");
List<String> filteredList = new ArrayList<>();

for (String item : originalList) {
    if (item.length() > 3) {
        filteredList.add(item);
    }
}
```

这段代码需要显式地创建一个新的ArrayList，并通过循环遍历原列表，手动检查每个元素是否满足条件，然后添加到新列表中。

**使用Stream API的做法** ：

```java
List<String> originalList = Arrays.asList("apple", "fig", "banana", "kiwi");
List<String> filteredList = originalList.stream()
                                        .filter(s -> s.length() > 3)
                                        .collect(Collectors.toList());
```

这里，我们直接在原始列表上调用`.stream()` 方法创建了一个流，使用`.filter()` 中间操作筛选出长度大于3的字符串，最后使用`.collect(Collectors.toList())` 终端操作将结果收集到一个新的列表中。代码更加简洁明了，逻辑一目了然。

> 案例2：计算列表中所有数字的总和

**问题场景** ：计算一个数字列表中所有元素的总和。

**没有Stream API的做法** ：

```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
int sum = 0;
for (Integer number : numbers) {
    sum += number;
}
```

这个传统的for-each循环遍历列表中的每一个元素，累加它们的值来计算总和。

**使用Stream API的做法** ：

```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
int sum = numbers.stream()
                 .mapToInt(Integer::intValue)
                 .sum();
```

通过Stream API，我们可以先使用`.mapToInt()` 将Integer流转换为IntStream（这是为了高效处理基本类型），然后直接调用`.sum()` 方法来计算总和，极大地简化了代码。

### Stream流的并行API是什么？

是 ParallelStream。

并行流（ParallelStream）就是将源数据分为多个子流对象进行多线程操作，然后将处理的结果再汇总为一个流对象，底层是使用通用的 fork/join 池来实现，即将一个任务拆分成多个“小任务”并行计算，再把多个“小任务”的结果合并成总的计算结果

Stream串行流与并行流的主要区别：

![img](https://cdn.xiaolincoding.com//picgo/1716365522454-4b56a07e-9b54-4cbb-9832-26b099fc35cd.png)

对CPU密集型的任务来说，并行流使用ForkJoinPool线程池，为每个CPU分配一个任务，这是非常有效率的，但是如果任务不是CPU密集的，而是I/O密集的，并且任务数相对线程数比较大，那么直接用ParallelStream并不是很好的选择。

### completableFuture怎么用的？

CompletableFuture是由Java 8引入的，在Java8之前我们一般通过Future实现异步。

- Future用于表示异步计算的结果，只能通过阻塞或者轮询的方式获取结果，而且不支持设置回调方法，Java 8之前若要设置回调一般会使用guava的ListenableFuture，回调的引入又会导致臭名昭著的回调地狱（下面的例子会通过ListenableFuture的使用来具体进行展示）。
- CompletableFuture对Future进行了扩展，可以通过设置回调的方式处理计算结果，同时也支持组合操作，支持进一步的编排，同时一定程度解决了回调地狱的问题。

下面将举例来说明，我们通过ListenableFuture、CompletableFuture来实现异步的差异。假设有三个操作step1、step2、step3存在依赖关系，其中step3的执行依赖step1和step2的结果。

Future(ListenableFuture)的实现（回调地狱）如下：

```java
ExecutorService executor = Executors.newFixedThreadPool(5);
ListeningExecutorService guavaExecutor = MoreExecutors.listeningDecorator(executor);
ListenableFuture<String> future1 = guavaExecutor.submit(() -> {
    //step 1
    System.out.println("执行step 1");
    return "step1 result";
});
ListenableFuture<String> future2 = guavaExecutor.submit(() -> {
    //step 2
    System.out.println("执行step 2");
    return "step2 result";
});
ListenableFuture<List<String>> future1And2 = Futures.allAsList(future1, future2);
Futures.addCallback(future1And2, new FutureCallback<List<String>>() {
    @Override
    public void onSuccess(List<String> result) {
        System.out.println(result);
        ListenableFuture<String> future3 = guavaExecutor.submit(() -> {
            System.out.println("执行step 3");
            return "step3 result";
        });
        Futures.addCallback(future3, new FutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                System.out.println(result);
            }        
            @Override
            public void onFailure(Throwable t) {
            }
        }, guavaExecutor);
    }

    @Override
    public void onFailure(Throwable t) {
    }}, guavaExecutor);
```

CompletableFuture的实现如下：

```java
ExecutorService executor = Executors.newFixedThreadPool(5);
CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> {
    System.out.println("执行step 1");
    return "step1 result";
}, executor);
CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> {
    System.out.println("执行step 2");
    return "step2 result";
});
cf1.thenCombine(cf2, (result1, result2) -> {
    System.out.println(result1 + " , " + result2);
    System.out.println("执行step 3");
    return "step3 result";
}).thenAccept(result3 -> System.out.println(result3));
```

显然，CompletableFuture的实现更为简洁，可读性更好。

CompletableFuture实现了两个接口（如上图所示)：Future、CompletionStage。

- Future表示异步计算的结果，CompletionStage用于表示异步执行过程中的一个步骤（Stage），这个步骤可能是由另外一个CompletionStage触发的，随着当前步骤的完成，也可能会触发其他一系列CompletionStage的执行。
- 从而我们可以根据实际业务对这些步骤进行多样化的编排组合，CompletionStage接口正是定义了这样的能力，我们可以通过其提供的thenApply、thenCompose等函数式编程方法来组合编排这些步骤。

### Java 21 新特性知道哪些？

**新语言特性：**

- **Switch 语句的模式匹配（JEP 441）** ：它允许在 `switch` 的 `case` 标签中使用模式匹配，使操作更加灵活和类型安全，减少了样板代码和潜在错误。例如，对不同账户类型获取余额：

  ```java
  // Java 16 之前：要手动加 break、强转
  switch (account) {
      case SavingsAccount sa:          // ❌ 编译报错，case 里不能写类型
  }

  // 旧写法：if-else + instanceof
  double result;
  if (account instanceof SavingsAccount) {
      result = ((SavingsAccount) account).getSavings();  // 手动强转
  } else if (account instanceof CheckingAccount) {
      result = ((CheckingAccount) account).getChecking();
  } else {
      result = 0;
  }

  // Java 21：switch 模式匹配，case 直接匹配类型+解构
  double result = switch (account) {
      case SavingsAccount sa  -> sa.getSavings();    // 匹配类型 + 自动强转
      case CheckingAccount ca -> ca.getChecking();
      case null               -> 0.0;                // null 安全
      default                 -> 0.0;
  };
  ```
- **记录模式（Record Patterns，JEP 440）** ：将模式匹配扩展到记录类（record），允许在 `instanceof` 或 `switch` 中直接对 record 进行解构。

  > [!abstract]- Record 类详解
  > Record 是 Java 14 引入的**不可变数据载体**，一行代码自动生成构造器、getter、equals、hashCode、toString。Java 21 中结合模式匹配可直接**解构**组件：`if (obj instanceof Point(int x, int y))`。
  >
  > 详见 → [[06-其他杂项/Record类详解]]
- **字符串模板（预览版，JEP 430）** ：提供了一种更可读、更易维护的方式来构建复杂字符串，支持在字符串字面量中直接嵌入表达式。需要通过模板处理器（如 `STR` ）调用，嵌入表达式使用 `\{...}` 语法。例如，以前可能需要使用 `"hello " + name + ", welcome!"` 这样的方式拼接字符串，Java 21 中可以写成 `STR."hello \{name}, welcome!"` 。注意：该特性在 JDK 23 中已被撤回以待重新设计。

**新并发特性方面：**

- **虚拟线程** ：Java 21 正式发布的轻量级线程（JEP 444），采用 N:M 模型——大量虚拟线程挂载到少量平台载体线程上执行。
  > [!abstract]- 虚拟线程详解
  > 虚拟线程是一种**由 JVM 管理的轻量级线程**（不是 OS 线程）。栈在堆上动态分配，执行 I/O 阻塞时**自动卸载到堆**，释放载体线程去执行其他虚拟线程，整个过程纯用户态切换。可创建数百万个，显著提升 I/O 密集型应用吞吐量。
  >
  > 详见 → [[06-其他杂项/虚拟线程详解]]
- **Scoped Values（范围值）** ：提供了一种在线程间共享不可变数据的新方式，避免使用传统的线程局部存储，促进了更好的封装性和线程安全，可用于在不通过方法参数传递的情况下，传递上下文信息，如用户会话或配置设置。

## 序列化

### 怎么把一个对象从一个jvm转移到另一个jvm?

对象跨 JVM 传输，本质是 **序列化 → 传输 → 反序列化** 三步走：

| 步骤 | 作用 |
|------|------|
| **序列化** | 把对象编码成字节流（协议层） |
| **传输** | 把字节流从 JVM A 送到 JVM B（网络层） |
| **反序列化** | 在 JVM B 把字节流恢复成对象 |

Java 原生序列化（`ObjectOutputStream` / `ObjectInputStream`）只解决第一步，传输阶段有多种方案：

### 传输方式

- **Socket 通信**：服务端用 `ServerSocket` 监听，客户端连接后通过 `ObjectOutputStream` 写入字节流，服务端用 `ObjectInputStream` 读取。需自行处理 TCP 粘包拆包，适合简单点对点场景。
- **消息队列**：序列化后投递到 MQ（RabbitMQ / Kafka），消费者取出后反序列化。适合异步解耦、削峰填谷，不保证实时性。
- **RPC 框架**：gRPC（Protobuf + HTTP/2）或 Dubbo（自定义二进制协议）封装了序列化、编解码、传输全流程。生产环境最常用，面试高频考点。
- **共享存储**（另类方案）：将对象存入共享数据库或 Redis，两个 JVM 各自读写。不直接传输对象，适合数据共享但非实时调用的场景。

### 序列化和反序列化让你自己实现你会怎么做?

Java 默认的序列化虽然实现方便，但却存在安全漏洞、不跨语言以及性能差等缺陷。

- 无法跨语言： Java 序列化目前只适用基于 Java 语言实现的框架，其它语言大部分都没有使用 Java 的序列化框架，也没有实现 Java 序列化这套协议。因此，如果是两个基于不同语言编写的应用程序相互通信，则无法实现两个应用服务之间传输对象的序列化与反序列化。
- 容易被攻击：反序列化的危险不在于能实例化任意对象，而在于 `readObject()` 会触发目标类的回调逻辑。攻击者可利用 classpath 上的 [[Java反序列化gadget链详解|gadget 链]]（如 commons-collections），构造恶意字节流连锁触发 `Runtime.exec()` 或 JNDI 注入，实现远程命令执行（RCE）。
- 序列化后的流太大：序列化后的二进制流大小能体现序列化的性能。序列化后的二进制数组越大，占用的存储空间就越多，存储硬件的成本就越高。如果我们是进行网络传输，则占用的带宽就更多，这时就会影响到系统的吞吐量。

我会考虑用主流序列化框架，比如FastJson、Protobuf来替代Java 序列化。

如果追求性能的话，Protobuf 序列化框架会比较合适，Protobuf 的这种数据存储格式，不仅压缩存储数据的效果好， 在编码和解码的性能方面也很高效。Protobuf 的编码和解码过程结合.proto 文件格式，加上 Protocol Buffer 独特的编码格式，只需要简单的数据运算以及位移等操作就可以完成编码与解码。可以说 Protobuf 的整体性能非常优秀。

### 将对象转为二进制字节流具体怎么实现?

其实，像序列化和反序列化，无论这些可逆操作是什么机制，都会有对应的 **处理和解析协议** ，例如加密和解密，TCP的粘包和拆包，序列化机制是通过序列化协议来进行处理的，和 class 文件类似，它其实是定义了序列化后的字节流格式，然后对此格式进行操作，生成符合格式的字节流或者将字节流解析成对象。

在Java中通过序列化对象流来完成序列化和反序列化：

- ObjectOutputStream：通过writeObject(）方法做序列化操作。
- ObjectInputStrean：通过readObject()方法做反序列化操作。

只有实现了Serializable或Externalizable接口的类的对象才能被序列化，否则抛出异常！

实现对象序列化：

- 让类实现Serializable接口：
```java
import java.io.Serializable;

public class MyClass implements Serializable {
    // class code
}
```
- 创建输出流并写入对象：
```java
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

MyClass obj = new MyClass();
try {
    FileOutputStream fileOut = new FileOutputStream("object.ser");
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(obj);
    out.close();
    fileOut.close();
} catch (IOException e) {
    e.printStackTrace();
}
```

实现对象反序列化：

- 创建输入流并读取对象：
```java
import java.io.FileInputStream;
import java.io.ObjectInputStream;

MyClass newObj = null;
try {
    FileInputStream fileIn = new FileInputStream("object.ser");
    ObjectInputStream in = new ObjectInputStream(fileIn);
    newObj = (MyClass) in.readObject();
    in.close();
    fileIn.close();
} catch (IOException | ClassNotFoundException e) {
    e.printStackTrace();
}
```

通过以上步骤，对象 obj 会被序列化并写入到文件 "object.ser" 中，然后通过反序列化操作，从文件中读取字节流并恢复为对象 newObj。这种方式可以方便地将对象转换为字节流用于持久化存储、网络传输等操作。需要注意的是，要确保类实现了 Serializable 接口，并且所有需要序列化的（即非 `static` 、非 `transient` ）成员变量都是 Serializable 的才能被正确序列化；被 `transient` 或 `static` 修饰的字段不会参与默认序列化。

## 设计模式

### volatile和sychronized如何实现单例模式

```java
public class SingleTon {

    // volatile 关键字修饰变量 防止指令重排序
    private static volatile SingleTon instance = null;
    private SingleTon(){}
     
    public static  SingleTon getInstance(){
        if(instance == null){
            //同步代码块 只有在第一次获取对象的时候会执行到 ，第二次及以后访问时 instance变量均非null故不会往下执行了 直接返回啦
            synchronized(SingleTon.class){
                if(instance == null){
                    instance = new SingleTon();
                }
            }
        }
        return instance;
    }
}
```

> [!question]- **为什么用了两个 `if (instance == null)`？**
> - **外层 `if`**：省性能。`synchronized` 是重量级操作，绝大多数情况下 `instance` 已非 null，外层 if 直接返回，避免进同步块
> - **内层 `if`**：保唯一。防止两个线程同时通过外层 if，第一个线程 new 完之后，第二个线程拿到锁再 new 一次破坏单例
>
> 一句话：**外层省性能，内层保唯一。**

正确的双重检查锁定模式需要使用 volatile。volatile主要包含两个功能。

- 保证可见性。使用 volatile 定义的变量，将会保证对所有线程的可见性。
- 禁止指令重排序优化。

由于 volatile 禁止对象创建时指令之间重排序，所以其他线程不会访问到一个未初始化的对象，从而保证安全性。

### 适配器模式是什么？

适配器模式（Adapter Pattern）用于**接口转换**，让原本**不兼容**的类能够一起工作。就像电源转换器——你有个三脚插头，插座是两孔的，中间加个转化插头就能用了。

```java
// 被适配者：老接口
public class OldLogger {
    public void log(String msg) {
        System.out.println("[Old] " + msg);
    }
}

// 目标接口：新系统需要的
public interface NewLogger {
    void info(String msg);
    void error(String msg);
}

// 适配器：把老接口包装成新接口
public class LoggerAdapter implements NewLogger {
    private OldLogger oldLogger = new OldLogger();

    public void info(String msg) {
        oldLogger.log("[INFO] " + msg);  // 转换调用
    }

    public void error(String msg) {
        oldLogger.log("[ERROR] " + msg);
    }
}
```

这样新系统统一用 `NewLogger` 接口，底层是老实现，中间由适配器做转换。

### 代理模式和适配器模式有什么区别？

- **目的不同** ：代理模式主要关注**控制对象的访问**（如延迟加载、权限校验），而适配器模式则用于**接口转换**，让不兼容的类能一起工作。
- **结构不同** ：代理模式一般包含抽象主题、真实主题和代理三个角色，适配器模式包含目标接口、适配器和被适配者三个角色。
- **应用场景不同** ：代理模式常用于添加额外功能或控制对对象的访问，适配器模式常用于让不兼容的接口协同工作。

### 责任链模式使用场景是什么？

责任链模式的使用场景核心很明确，==就是一个请求需要多个独立的处理逻辑来承接，同时不想让请求发起方和所有处理者产生强关联，还得让处理流程能灵活调整==，简单说就是谁能处理就谁来接手，整个处理顺序和参与节点能按需改动。

比如实际开发里最常遇到的接口请求校验，用户调用我们的接口时，可能得先检查登录状态，再验证 token 是否有效，接着确认接口访问权限，最后还要限制请求频率，这些校验逻辑各自独立，而且不同接口需要的校验步骤不一样，比如登录接口只需要验证验证码，查询用户信息的接口得同时过登录和权限校验。要是不用责任链，就得在每个接口里写一堆 if-else 把这些校验串起来，后续想改某个校验规则，所有相关接口都得动，维护起来特别麻烦。

这时候用责任链就很合适，我们可以把每个校验逻辑封装成一个处理节点

先定义一个抽象的处理者类：

```java
// 抽象处理者
abstract class Handler {
    protected Handler next;
    // 设置下一个处理节点
    public void setNext(Handler next) {
        this.next = next;
    }
    // 抽象处理方法
    public abstract boolean handle(Request request);
}
```

然后每个校验逻辑都继承这个类，比如登录校验：

```java
// 登录态校验节点
class LoginHandler extends Handler {
    @Override
    public boolean handle(Request request) {
        if (request.isLogin()) {
            System.out.println("登录态校验通过，交给下一个节点");
            // 校验通过，交给下一个处理者
            return next != null ? next.handle(request) : true;
        } else {
            System.out.println("未登录，直接返回失败");
            // 校验不通过，终止链路
            return false;
        }
    }
}
```

再写个权限校验节点、频率限制节点，最后在使用的时候，根据接口需求动态组装链路：

```java
// 组装链路：登录校验 -> 权限校验 -> 频率限制
Handler loginHandler = new LoginHandler();
Handler authHandler = new AuthHandler();
Handler rateLimitHandler = new RateLimitHandler();
loginHandler.setNext(authHandler);
authHandler.setNext(rateLimitHandler);

// 发起请求
Request request = new Request(true, "admin", 1); // 已登录、管理员权限、第1次请求
boolean result = loginHandler.handle(request);
```

这样一来，请求发起方只需要调用第一个节点，根本不用关心后面有多少校验步骤，也不用知道具体是哪个节点在处理；如果某个接口不需要频率限制，直接去掉 rateLimitHandler 的组装就行，不用修改任何校验节点的代码，流程调整起来特别灵活。

### 介绍一下策略模式和责任链模式，分别用在哪些场景？

这两个设计模式都属于行为型模式，而且在实际开发中，它们往往都是为了解决同一个痛点： **如何消除代码中复杂的** `**if-else**` **或** `**switch-case**` **逻辑，从而让系统更易于扩展。**

> 策略模式

策略模式定义一系列算法，把它们一个个封装起来，并且使它们可相互替换。从而让算法可独立于使用它的客户而变化。

假设你正在开发一个电商网站的支付模块。用户可以选择支付宝、微信支付或者银行卡支付。每种支付方式的底层逻辑（比如调用的 API、加密方式、参数构造）都完全不同，但对于调用支付功能的上层业务逻辑来说，它只需要调用一个统一的 “支付” 方法即可，无需关心具体是哪种支付。

这就是策略模式的用武之地。我们把每种支付方式（支付宝、微信）都封装成一个独立的 “策略” 类，它们都遵循一个共同的接口。上层业务代码通过这个共同的接口来调用支付功能，并可以在运行时根据用户的选择，动态地切换不同的策略。

所以，策略模式 **适用场景如下：**

- 当一个系统需要动态地在几种算法中选择一种时。
- 当一个对象有很多行为，不想使用大量的 `if-else` 或 `switch-case` 语句来选择时。
- 当你希望算法的变化不会影响到使用它的客户端时。

> 责任链模式

责任链模式的核心思想是为请求构建一条由多个处理节点组成的链,**将请求的发送者与具体处理逻辑解耦**。请求沿着链依次传递,**每个节点根据自身职责决定是处理、放行,还是中断传递**。链的终止条件由节点自行决定:可以是某个节点处理完即终止(择一处理),也可以是所有节点依次处理完毕后放行(管道式处理),或在任一节点校验失败时短路。

想象一下公司里的请假审批流程。一个员工要请假，他首先会把请假条交给自己的直属经理。如果请假天数很短（比如 1 天），经理自己就能批准。如果天数较长（比如 5 天），经理没有权限，就需要把请假条上报给部门总监。总监也有自己的审批权限上限，如果超过了，就需要继续上报给总经理。

在这个过程中，请假条（请求）就在一条由经理 -> 总监 -> 总经理（接收者链）上传递，直到某个角色（接收者）能够处理它。发送请假条的员工（客户端）并不知道最终是谁批准了他的假，他只需要把请求提交给链的第一个节点（经理）即可。

所以，责任链模式适用场景总结：

- 当有多个对象可以处理同一个请求，但具体由哪个对象处理需要在运行时决定时。
- 当你希望请求的发送者和接收者解耦，不直接通信时。
- 当你需要动态地指定和修改处理一个请求的对象集合时。

## I/O

### Java怎么实现网络IO高并发编程？

可以用 Java NIO ，是一种同步非阻塞的I/O模型，也是I/O多路复用的基础。

传统的BIO里面socket.read()，如果TCP RecvBuffer里没有数据，函数会一直阻塞，直到收到数据，返回读到的数据， 如果使用BIO要想要并发处理多个客户端的i/o，那么会使用多线程模式，==一个线程专门处理一个客户端 io==，这种模式随着客户端越来越多，所需要创建的线程也越来越多，会急剧消耗系统的性能。

![image-20240820112641716](https://cdn.xiaolincoding.com//picgo/image-20240820112641716.png)

NIO 是基于I/O多路复用实现的，它可以只用一个线程处理多个客户端I/O，如果你需要同时管理成千上万的连接，但是每个连接只发送少量数据，例如一个聊天服务器，用NIO实现会更好一些。

![](https://cdn.xiaolincoding.com//picgo/image-20240820112656259.png)

### BIO、NIO、AIO区别是什么？

- BIO（blocking IO）：就是传统的 java.io 包，它是基于流模型实现的，交互的方式是同步、阻塞方式，也就是说在读入输入流或者输出流时，在读写动作完成之前，线程会一直阻塞在那里，它们之间的调用是可靠的线性顺序。优点是代码比较简单、直观；缺点是 IO 的效率和扩展性很低，容易成为应用性能瓶颈。
- NIO（non-blocking IO） ：Java 1.4 引入的 java.nio 包，提供了 Channel、Selector、Buffer 等新的抽象，可以构建多路复用的、同步非阻塞 IO 程序，同时提供了更接近操作系统底层高性能的数据操作方式。
- AIO（Asynchronous IO） ：也称 NIO.2，是 Java 1.7 引入的，对 NIO 的扩展，提供了异步非阻塞的 IO 操作方式，所以人们叫它 AIO（Asynchronous IO）。异步 IO 是基于事件和回调机制实现的：应用线程发起 IO 操作后立即返回，不阻塞等待；内核完成读写后，直接调用注册的回调函数（`CompletionHandler`）处理结果，整个过程无需应用线程参与阶段 2 的数据拷贝。

### NIO是怎么实现的？


NIO(Non-Blocking IO)是 Java 提供的一种同步非阻塞 IO 模型。要理解它的实现,先厘清两个关键词:

- **非阻塞**:线程发起 IO 操作后**不会被挂起**。如果数据还没准备好,调用立刻返回,线程可以去做别的事,而不是傻等。
- **同步**:数据真正的读写动作仍然由**应用线程自己完成**,而不是由内核异步回调把数据送上门(后者是 AIO)。

---

#### 同步的含义

一次 IO 操作分两个阶段;**"同步 vs 异步"区分的是阶段 2 由谁来做。**

**阶段 1 — 等待数据就绪**  
数据还在网卡或内核缓冲区。这个阶段要**等**——等对端发数据过来,等内核从网卡收上来。

**阶段 2 — 把数据从内核拷贝到用户空间**  
数据已在内核缓冲区,但你的 Java 程序看不见。必须有人执行一次 `内核 → 用户` 的拷贝,数据才能落到你的 `ByteBuffer` 里。

**关键就在阶段 2 是谁做的:**

| 模型 | 阶段 1(等待) | 阶段 2(拷贝) |
|------|-------------|-------------|
| BIO | 线程阻塞等 | 线程自己拷贝 |
| NIO | 线程不阻塞,轮询或用 Selector 监听 | 线程自己拷贝(**这一步仍然阻塞**) |
| IO 多路复用 | Selector 阻塞等,就绪后通知线程 | 线程自己拷贝(**这一步仍然阻塞**) |
| AIO | 线程不管,内核自己等 | **内核自己拷贝**,拷贝完回调通知 |

前三种(包括 NIO),阶段 2 全由应用线程亲自搬;只有 AIO 把这一步也甩给内核。

- **同步 IO** = 阶段 2 应用线程自己搬  
- **异步 IO** = 阶段 2 内核搬完再叫你  
- **阻塞 IO** = 阶段 1 线程挂起,啥也干不了  
- **非阻塞 IO** = 阶段 1 调用立刻返回,线程先去干别的  

故:
- BIO = **同步阻塞**
- NIO = **同步非阻塞**
- AIO = **异步非阻塞**

> 取快递的比喻:  
> **BIO** — 跑去快递点,没到就在门口站着等,到了自己拎回家。  
> **NIO** — 跑去问"到了吗",没到就回家干活,过会儿再来问。到了之后**还是自己拎回家**。  
> **AIO** — 告诉快递员"到了放柜子里,放好发短信"。**收到短信时快递已经在柜子里了**。

一句话收: **NIO 的 Selector 只管"知道数据到了";搬数据还得应用线程亲自调 `channel.read(buffer)`,所以是同步的。**


NIO 的整体实现可以概括为:**用一个 Selector 监听多个 Channel 上的就绪事件,事件到来时由应用线程通过 Buffer 完成实际读写**。三大核心组件各司其职:

#### **1. Channel(通道)**

Channel 是对传统 IO 中"流"的替代,代表一个可以进行 IO 操作的连接(文件、Socket 等)。与流的关键区别有两点:

- **双向**:同一个 Channel 既能读也能写,而 InputStream/OutputStream 是单向的;
- **可配置为非阻塞模式**:这是 NIO 区别于 BIO 的基础。

常用实现包括 `SocketChannel`、`ServerSocketChannel`、`FileChannel` 等。

#### **2. Buffer(缓冲区)**

NIO 中数据不再直接在流上逐字节读写,而是**先进缓冲区再统一搬运**:从 Channel 读数据 → 写入 Buffer;往 Channel 写数据 → 从 Buffer 读出。

Buffer 本质是一块带状态的内存区域,通过 `position`、`limit`、`capacity` 三个指针管理读写位置。这种设计的好处是支持**批量传输**和**零拷贝**(配合 DirectBuffer),性能比逐字节流式处理高得多。

---

#### channel.read/write 的方向感

Buffer 是中转站,Channel 是管子,所有方法名动词(read/write)都**站在 Channel 角度**说的。

```
[程序代码] ←→ [Buffer] ←→ [Channel] ←→ [外部]
```

| 你调的方法 | 数据流向 | 对 Channel 而言 | 对 Buffer 而言 |
|---|---|---|---|
| `channel.read(buffer)` | 外部 → Buffer | 读出来 | 被写入 |
| `channel.write(buffer)` | Buffer → 外部 | 写进去 | 被读出 |

> 记忆点:方法叫 `read` 但 Buffer 的 position 往后移了——对 Buffer 是"写入"。
> 快递柜比喻:收快递时快递员从车里**取出**(Channel 读)包裹**放入**柜子(Buffer 写);寄快递时从柜子**取出**(Buffer 读)包裹**装进**车(Channel 写)。

Buffer 设计给高性能带来的好处:

1. **批量传输**:一次系统调用读一大块,比 BIO 逐字节读高效;
2. **可重复访问**:`flip`/`rewind` 反复读,流式 IO 读过就没;
3. **堆外内存零拷贝**(见下);
4. **统一抽象**:文件、Socket、管道都统一用 `read(buffer)`/`write(buffer)`。

> [!info]+ DirectBuffer 为什么能"省一次拷贝"
> **根因是 GC 会移动对象。** 内核做系统调用时传的是固定内存地址。如果直接传堆内 buffer 的地址,I/O 期间 GC 一旦移动了那块内存(标记-整理、复制算法),内核就会写到错误地址 → 数据损坏。
>
> 所以 JDK 对**堆内 buffer**(HeapByteBuffer)做 I/O 时,先临时申请一块堆外 DirectBuffer,把数据拷过去,再让内核读写这块堆外内存。源码见 `IOUtil.write()` → 非 direct buffer 走 `Util.getTemporaryDirectBuffer()`。
>
> - 堆内: **JVM 拷贝**(堆内→临时堆外) + 内核拷贝
> - 堆外(DirectBuffer): 内核直接 **DMA**,省掉 JVM 那一次拷贝
>
> ⚠ **面试易错点**:这里省的是 **JVM 用户态那一次额外拷贝**,不是内核↔用户态之间的拷贝。真正的"零拷贝"(`sendfile`/`mmap`)省的是后者,和 DirectBuffer 不是一个层面。Netty 说的"DirectBuffer 零拷贝"是"避免堆内外额外拷贝",被追问时别和 `sendfile` 混为一谈。

---



#### **3. Selector(多路复用器)**

Selector 是 NIO 的灵魂。它允许**一个线程同时监听多个 Channel** 上的 IO 事件(连接就绪、数据可读、可写等),底层依赖操作系统提供的多路复用能力(Linux 的 epoll、macOS 的 kqueue、Windows 的 select/IOCP)。

工作流程大致是:

1. 把多个 Channel 注册到 Selector 上,并声明各自关心的事件类型;
2. 应用线程调用 `selector.select()`,该调用会**阻塞**直到至少有一个 Channel 上的事件就绪(或超时);
3. 返回后遍历就绪事件集合(`SelectedKeys`),针对每个事件用 Buffer 完成实际读写。

注意第 2 步——`select()` 本身是阻塞的,所谓"非阻塞"指的是**单个 Channel 的读写不阻塞**,而不是整个线程从不阻塞。这也是为什么严格说 NIO 属于**同步**模型:就绪后还得线程自己动手搬数据。

#### **NIO 相比 BIO 的本质改进**

传统 BIO 是"**一个连接一个线程**":每个 Socket 都要独占一个线程阻塞地等数据,连接数一多,线程数爆炸、上下文切换开销巨大。

NIO 是"**一个线程多个连接**":通过 Selector 把"等待数据就绪"这件事集中起来交给操作系统,应用线程只在真正有事可做时才被唤醒。这样:

- 连接数和线程数解耦,几千上万并发连接也能用少量线程扛住;
- 避免了大量线程空转和切换;
- 适合**高并发、连接数多但单连接数据量不大**的场景(典型如即时通讯、网关、长连接推送)。

Netty、Tomcat NIO connector、Redis(C 实现但思想一致)等高性能网络框架,底层都建立在这种模型之上。


---

![img](https://cdn.xiaolincoding.com//picgo/1716018476312-e5525ca7-acf8-46b1-8fff-8a7d22db5304.webp)

### 你知道有哪个框架用到NIO了吗？

**Netty。**

Netty 的 I/O 模型是基于非阻塞 I/O 实现的，底层依赖的是 NIO 框架的多路复用器 Selector。采用 epoll 模式后，只需要一个线程负责 Selector 的轮询。当有数据处于就绪状态后，需要一个事件分发器（Event Dispather），它负责将读写事件分发给对应的读写事件处理器（Event Handler）。事件分发器有两种设计模式：Reactor 和 Proactor，Reactor 采用同步 I/O， Proactor 采用异步 I/O。

Reactor 实现相对简单，适合处理耗时短的场景，对于耗时长的 I/O 操作容易造成阻塞。Proactor 性能更高，但是实现逻辑非常复杂，适合图片或视频流分析服务器，目前主流的事件驱动模型还是依赖 select 或 epoll 来实现。

## 其他

### 有一个学生类，想按照分数排序，再按学号排序，应该怎么做？

可以使用Comparable接口来实现按照分数排序，再按照学号排序。首先在学生类中实现Comparable接口，并重写compareTo方法，然后在compareTo方法中实现按照分数排序和按照学号排序的逻辑。

```java
public class Student implements Comparable<Student> {
    private int id;
    private int score;

    // 构造方法和其他属性、方法省略

    @Override
    public int compareTo(Student other) {
        if (this.score != other.score) {
            return Integer.compare(other.score, this.score); // 按照分数降序排序
        } else {
            return Integer.compare(this.id, other.id); // 如果分数相同，则按照学号升序排序
        }
    }
}
```

然后在需要对学生列表进行排序的地方，使用Collections.sort()方法对学生列表进行排序即可：

```
List<Student> students = new ArrayList<>();
// 添加学生对象到列表中
Collections.sort(students);
```

### Native方法解释一下

在Java中，native方法是Java中用`native`关键字声明的特殊方法，其实现由C、C++等外部语言提供，供Java代码调用。native关键字是Java语言中的一种声明，用于标记一个方法的实现将在外部定义。

在Java类中，native方法看起来与其他方法相似，只是其方法体由native关键字代替，没有实际的实现代码。例如：

```java
public class NativeExample {
    public native void nativeMethod();
}
```

要实现native方法，你需要完成以下步骤：

1. **生成 JNI 头文件** ：使用 `javac -h <dir>` 选项从你的 Java 类生成 C/C++ 的头文件，这个头文件包含了所有 native 方法的原型（注意：旧的独立工具 `javah` 自 Java 9 起被废弃，Java 10 已正式移除，应改用 `javac -h` ）。
2. **编写本地代码** ：使用C/C++编写本地方法的实现，并确保方法签名与生成的头文件中的原型匹配。
3. **编译本地代码** ：将C/C++代码编译成动态链接库（DLL，在Windows上），共享库（SO，在Linux上）
4. **加载本地库** ：在Java程序中，使用System.loadLibrary()方法来加载你编译好的本地库，这样JVM就能找到并调用native方法的实现了。

### Java 进程是怎么跟操作系统交互的？

Java程序虽然是运行在JVM上的，但JVM本身就是一个操作系统进程，所以Java跟操作系统的交互，本质上是通过JVM这个中间层来完成的。

最基础的交互就是内存管理。JVM启动时会通过系统调用向操作系统申请内存，比如在Linux上用mmap、brk这些系统调用来分配堆内存、栈内存。虽然我们在Java代码里写new对象，但底层还是要跟操作系统打交道获取真实的物理内存。

线程管理也是重要的交互点。Java 中传统的平台线程（platform thread）与操作系统的原生线程是一对一的关系，在 Linux 上会调用 `pthread_create` 来创建线程；线程的调度、同步机制（比如 `synchronized` 底层用的互斥锁）最终都是由操作系统来管理的。注意：Java 21 引入的虚拟线程（virtual thread）属于用户态，不直接对应 OS 线程，而是由 JVM 调度并复用到少量平台载体线程上执行。

IO操作是最频繁的交互场景。文件读写会调用read、write这些系统调用，网络编程底层也是调用socket、bind、listen这些系统调用。Java的NIO用的是操作系统提供的高性能IO机制，Linux上是epoll，Windows上是IOCP。

JNI是另一个重要通道。有些操作Java做不了或者性能不够，就通过JNI调用C/C++代码，这些本地代码可以直接调用操作系统API。JVM本身也大量使用JNI来实现底层功能。

![](https://cdn.xiaolincoding.com//picgo/1766932722511-525ecd38-9480-41b4-9a9e-3d131c7bb1c4.png)

还有信号处理，比如你用kill命令给Java进程发SIGTERM信号，JVM会捕获这个信号然后执行关闭钩子。进程间通信、获取系统资源信息这些，底层也都是通过系统调用完成的。

从用户态和内核态的角度说，Java代码运行在用户态，但IO、网络这些操作需要切换到内核态，这个切换是有开销的。JVM会做很多优化，比如用缓冲区减少系统调用次数。虽然JVM屏蔽了底层差异让Java代码跨平台，但在每个平台上JVM都要适配该平台的系统调用和API。

**面试口述回答：**

Java 程序不直接跟操作系统打交道，中间隔了个 JVM。但 JVM 本身是个进程，所以底层还是要走系统调用。

大致分几个层面：

**内存**——JVM 启动时通过 `mmap`、`brk` 这些系统调用向操作系统申请内存。你 Java 代码里 `new` 一个对象，底层就是操作系统分配物理内存给你。

**线程**——Java 传统线程跟 OS 原生线程是一对一的，Linux 上会调 `pthread_create`。线程调度、`synchronized` 的互斥锁，最终都是 OS 在管。Java 21 的虚拟线程不一样，它在用户态由 JVM 自己调度，不直接对应 OS 线程。

**I/O**——文件读写调 `read`/`write` 系统调用，网络编程底层调 `socket`、`bind`、`listen`。Java NIO 用的是操作系统提供的高性能 I/O 机制，Linux 上是 epoll，Windows 上是 IOCP。

**JNI**——有些活 Java 干不了或者不够快，就走 JNI 调 C/C++，本地代码可以直接调操作系统 API。

**信号**——比如你 `kill` 一个 Java 进程发 SIGTERM，JVM 会捕获这个信号执行关闭钩子。

核心就一句话：**Java 代码跑在用户态，凡是涉及 I/O、网络、线程创建这些操作，都需要切换到内核态走系统调用。JVM 做了跨平台抽象，但底层在每个平台上都要适配对应的系统调用。**

---

## 容器与集合框架（java基础补充）

> [!info]- **来源**：[java基础.md] — Java 基础八股总览，本节内容追加合并

### Java 中流分为几种

在 Java 中，IO 流（Input/Output Stream）的分类非常系统。为了方便记忆，我们可以从**流向**、**处理单位**和**角色**三个维度来进行划分。

#### 1. 按流向划分（最基本划分）

这是站在**程序内存**的角度来看待数据的去向：

- **输入流 (Input Stream)**：数据从外部（文件、网络、键盘）流向程序内存。
- **输出流 (Output Stream)**：数据从程序内存流向外部（文件、磁盘、显示器）。

#### 2. 按处理单位划分（最核心划分）

这是开发中最常用的分类方式，决定了你处理的是二进制数据还是文本数据。

| **分类**       | **字节流 (Byte Stream)**          | **字符流 (Character Stream)** |
| ------------ | ------------------------------ | -------------------------- |
| **处理单位**     | 8 位字节（byte）                    | 16 位字符（char）               |
| **处理对象**     | 视频、音频、图片等**所有**文件              | **纯文本**文件（自动处理编解码）         |
| **基类 (抽象类)** | `InputStream` / `OutputStream` | `Reader` / `Writer`        |

#### 3. 按流的角色划分

- **节点流 (Node Stream)**：直接连接数据源的流，负责读写底层数据。
  - *例如：`FileInputStream`, `FileReader`*
- **处理流/包装流 (Processing Stream)**：不直接连接数据源，而是套在已有的流之上，通过增强功能提高效率（如提供缓冲区）。
  - *例如：`BufferedInputStream`, `BufferedReader`*

#### 4. 常见的 4 大基类及其子类家族

几乎所有的 IO 类都是从这四个抽象基类派生出来的：

**字节流家族**

- **InputStream**：常用子类有 `FileInputStream`（读文件）、`BufferedInputStream`（带缓冲读）。
- **OutputStream**：常用子类有 `FileOutputStream`（写文件）、`BufferedOutputStream`（带缓冲写）、`PrintStream`（如 `System.out`）。

**字符流家族**

- **Reader**：常用子类有 `FileReader`（读文本）、`BufferedReader`（高效读行 `readLine()`）。
- **Writer**：常用子类有 `FileWriter`（写文本）、`BufferedWriter`（高效写缓冲）、`PrintWriter`（格式化输出）。

#### 5. 转换流（桥梁）

当你只有字节流，但需要按字符处理时（例如处理指定字符集的文本文件），会用到**转换流**：

- **`InputStreamReader`**：将字节输入流转为字符输入流。
- **`OutputStreamWriter`**：将字节输出流转为字符输出流。

#### 💡 开发小贴士

1. **优先使用缓冲流**：在处理大文件时，务必使用 `BufferedXXXX`。它能显著减少磁盘 IO 次数，提升性能。

2. **记得关闭流**：在 Java 7 以后，推荐使用 **try-with-resources** 语法，这样就不需要手动在 `finally` 块里关流了，避免内存泄漏。

   ```java
   try (BufferedReader br = new BufferedReader(new FileReader("test.txt"))) {
       // 读取逻辑
   } catch (IOException e) {
       e.printStackTrace();
   }
   ```

3. **场景选型**：
   - 复制图片、音频、PDF -> **字节流**。
   - 读取配置文件、日志、TXT -> **字符流**。

---

### BIO、NIO、AIO 有什么区别？

在 Java 的网络编程和 IO 处理中，这三者代表了处理数据的不同**模型**。它们的区别主要体现在"程序是否需要等待"以及"谁来通知数据准备好"这两个维度上。

我们可以通过一个**"去餐厅吃饭"**的经典类比来快速理解。

#### 1. BIO (Blocking I/O - 同步阻塞)

这是最传统、最基础的模型。

- **机制**：一个连接对应一个线程。当没有数据读写时，线程会一直阻塞在那里。
- **类比（餐厅用餐）**：你（程序）去餐厅点餐，然后在柜台死等，直到饭做好了（数据准备好）才端走。在等的这段时间，你什么也干不了。
- **缺点**：并发量大时，需要创建大量线程，系统开销极大。
- **适用场景**：连接数比较小且固定的架构，对性能要求不高。

#### 2. NIO (Non-blocking/New I/O - 同步非阻塞)

这是 Java 1.4 引入的改进模型，也是目前最主流的选择（如 Netty 框架的基础）。

- **机制**：利用**多路复用器（Selector）**。一个线程可以处理多个连接。它会不断轮询各个通道，看哪个通道有数据准备好了。
- **类比（餐厅用餐）**：你点完餐后领个"取餐叫号机"，然后可以去刷手机。你每隔一会抬头看看大屏幕有没有你的号（轮询）。虽然还是要你去取，但你中途能干别的事。
- **核心组件**：`Channel`（通道）、`Buffer`（缓冲区）、`Selector`（选择器）。
- **适用场景**：适用场景：**连接数多（高并发）且连接活跃度低（数据传输稀疏）**的架构。如聊天服务器、弹幕系统、IoT物联网设备连接。

#### 3. AIO (Asynchronous I/O - 异步非阻塞)

这是 Java 7 引入的真正异步模型（NIO.2）。

- **机制**：程序发起 IO 请求后立即返回，由操作系统完成数据读取和拷贝。当操作完成后，操作系统会通知程序（通过回调函数）。
- **类比（餐厅用餐）**：外卖模式。你点完餐后该干嘛干嘛，等外卖员（操作系统）直接把饭送到你家门口并按响门铃（回调通知）。你完全不需要关注过程。
- **优点**：性能理论上最高，真正实现了异步。
- **适用场景**：连接数多且连接时间长的架构（但在 Linux 下 AIO 的实现效率在某些版本并不如 NIO 配合 Epoll 显著）。

#### 4. 综合对比表

| **特征**     | **BIO**          | **NIO**                      | **AIO**              |
| ------------ | ---------------- | ---------------------------- | -------------------- |
| **IO 模型**  | **同步阻塞**     | **同步非阻塞**               | **异步非阻塞**       |
| **线程利用** | 一个连接一个线程 | 一个请求一个线程（多路复用） | 一个有效请求一个线程 |
| **数据操作** | 面向流 (Stream)  | **面向缓冲区 (Buffer)**      | 面向缓冲区 (Buffer)  |
| **可靠性**   | 低               | 高                           | 高                   |
| **吞吐量**   | 低               | 高                           | 高                   |

#### 💡 开发视角建议

1. **实战选择**：虽然 AIO 听起来最先进，但在 Linux 系统上，AIO 的底层实现并没有比 NIO 强出太多。因此，目前工业界（如 **Netty**, **RocketMQ**）的主流方案依然是 **NIO**。
2. **技术栈**：如果你在做高并发的后端开发，理解 NIO 的 `Selector` 机制以及如何避免"空轮询"导致的 CPU 100% 问题是非常关键的。
3. **连接管理**：BIO 适合小规模测试，但如果要支持万人在线聊天，那就必须上 NIO 架构了。

---

### Files 的常用方法都有哪些？

在 Java 7 引入 NIO.2 之后，`java.nio.file.Files` 成为了操作文件最强大、最方便的工具类。它是一个 **Utility Class（工具类）**，所有方法都是静态的，并且通常配合 `Path` 对象使用。

我们可以将它的常用方法归纳为以下几类：

#### 1. 文件的判断与检查

在操作文件前，通常需要先确认它的状态：

- **`exists(Path path)`**：判断文件或目录是否存在。
- **`isDirectory(Path path)`**：判断是否为目录。
- **`isRegularFile(Path path)`**：判断是否为常规文件（排除目录、链接等）。
- **`isReadable(path)` / `isWritable(path)`**：检查文件读写权限。

#### 2. 文件的创建与删除

- **`createFile(Path path)`**：创建一个新文件。如果文件已存在，会抛出异常。
- **`createDirectory(Path path)`**：创建单级目录。
- **`createDirectories(Path path)`**：**最常用**。递归创建多级目录（类似 Linux 的 `mkdir -p`），如果目录已存在不会报错。
- **`delete(Path path)`**：删除文件或目录。如果不存在会抛出异常。
- **`deleteIfExists(Path path)`**：更安全的删除方式，文件不存在时返回 `false` 而不报错。

#### 3. 文件的复制、移动与重命名

- **`copy(Path source, Path target, CopyOption... options)`**：复制文件。常用选项包括 `StandardCopyOption.REPLACE_EXISTING`（覆盖已有文件）。
- **`move(Path source, Path target, CopyOption... options)`**：移动或重命名文件。

#### 4. 读写文件（核心操作）

对于中小型文件，`Files` 提供了极简的一行代码读写方案：

- **读取：**
  - **`readAllLines(Path path)`**：读取所有行，返回 `List<String>`。
  - **`readAllBytes(Path path)`**：一次性读取为字节数组。
- **写入：**
  - **`write(Path path, byte[] bytes)`**：将字节数组写入文件。
  - **`write(Path path, Iterable<? extends CharSequence> lines)`**：将集合中的字符串按行写入。

#### 5. 高效的数据流操作

处理大文件或需要流式处理时，`Files` 提供了桥梁方法：

- **`newInputStream(Path path)`** / **`newOutputStream(Path path)`**：获取字节流。
- **`newBufferedReader(Path path)`** / **`newBufferedWriter(Path path)`**：获取字符缓冲流。

#### 6. 文件树遍历（进阶必备）

如果你需要搜索文件夹下的所有文件，这两个方法非常强大：

- **`walk(Path start)`**：返回一个 `Stream<Path>`，支持深度优先遍历文件树。
- **`list(Path dir)`**：只列出当前目录下的第一级文件和子目录。

#### 🛠️ 实战对比：旧写法 vs Files 写法

假设你要将一个字符串保存到文件，传统的 `FileOutputStream` 写法需要多行且涉及异常关闭，而使用 `Files`：

```java
Path path = Paths.get("config.txt");
String content = "Hello, Java NIO!";

try {
    // 自动处理创建、打开、写入和关闭
    Files.writeString(path, content); 
    
    // 或者是读取
    String readBack = Files.readString(path);
} catch (IOException e) {
    e.printStackTrace();
}
```

**小建议**：在涉及日志导出或配置文件读取时，建议全面转向 `Files` 类。它的代码更简洁，且内部优化了性能（尤其是配合 `Path` 使用时）。

在使用 `Files.walk()` 进行大规模文件搜索时，记得使用 **try-with-resources**，因为返回的 `Stream` 需要手动释放底层资源。

---

### java 容器都有哪些？

在 Java 中，容器（Collections Framework）是用来存储和操作对象组的统一架构。我们可以将其分为两大核心家族：**Collection**（存储单一元素）和 **Map**（存储键值对）。

#### 1. Collection 接口家族

`Collection` 是存储单一对象的最顶层接口，它派生出三个主要的子接口：

**List（有序、可重复）**

- **ArrayList**：基于**动态数组**实现。查询极快（O(1)），但中间插入和删除较慢，因为需要移动元素。它是最常用的 List 实现。
- **LinkedList**：基于**双向链表**实现。插入和删除极快，但查询较慢（需要遍历）。它还实现了 `Deque` 接口，可以作为栈或队列使用。
- **Vector**：古老的实现，**线程安全**但效率低下，现在很少使用（多线程推荐使用 `CopyOnWriteArrayList`）。

**Set（无序、不可重复）**

- **HashSet**：基于 `HashMap` 实现。查询速度极快，但不保证元素的顺序。
- **LinkedHashSet**：在 `HashSet` 的基础上增加了链表维护，可以保证元素的**插入顺序**。
- **TreeSet**：基于红黑树实现。元素会自动进行**排序**（自然顺序或指定比较器）。

**Queue（队列）**

- **PriorityQueue**：基于堆结构，实现优先级排序。
- **ArrayDeque**：基于循环数组实现的双端队列，比 `Stack` 和 `LinkedList` 做栈时性能更好。

#### 2. Map 接口家族

`Map` 存储的是 **Key-Value**（键值对），Key 不允许重复。

- **HashMap**：最常用的 Map。基于**哈希表**（数组+链表+红黑树）实现。不保证顺序，性能极高。
- **LinkedHashMap**：继承自 `HashMap`，增加了双向链表记录**插入顺序**或访问顺序。
- **TreeMap**：基于红黑树实现，能够根据 Key 进行**排序**。
- **Hashtable**：古老的实现，**线程安全**，但效率低。不允许 `null` 键值。
- **ConcurrentHashMap**：**线程安全**且高性能。在多线程并发场景下，它是替代 `Hashtable` 的首选。

#### 3. 核心容器对比表

| **容器**              | **底层数据结构** | **线程安全** | **性能特征**     | **适用场景**                 |
| --------------------- | ---------------- | ------------ | ---------------- | ---------------------------- |
| **ArrayList**         | 动态数组         | 否           | 读取快，增删慢   | 绝大多数查询频繁的场景       |
| **LinkedList**        | 双向链表         | 否           | 增删快，读取慢   | 频繁在头尾插入/删除          |
| **HashSet**           | 哈希表           | 否           | 读写极快         | 元素去重，不关心顺序         |
| **TreeSet**           | 红黑树           | 否           | 中等（排序开销） | 需要元素自动排序             |
| **HashMap**           | 哈希表           | 否           | 读写极快         | 键值对存储，最常用           |
| **ConcurrentHashMap** | 分段锁/CAS       | **是**       | 高并发下性能优异 | 多线程环境下的缓存、共享数据 |

#### 💡 进阶开发建议

1. **初始化容量**：如果你知道要存多少数据，创建 `ArrayList` 或 `HashMap` 时指定初始容量（如 `new HashMap<>(16)`），可以避免频繁触发扩容（Resize）操作。
2. **重写 equals/hashCode**：当你在 `Set` 或 `Map` 中使用自定义对象作为 Key 时，**必须**重写这两个方法。
3. **线程安全选型**：在普通的 Spring Boot 应用中，如果是方法内部的局部变量，用普通的 `HashMap` 即可；如果是全局共享变量，务必使用 `ConcurrentHashMap`。

---

### Collection 和 Collections 有什么区别？

在 Java 中，这两个词虽然长得非常像，但它们的本质完全不同。简单来说：**Collection 是"接口"（定义规范），而 Collections 是"工具类"（提供服务）。**

#### 1. Collection：容器的"祖宗"接口

`Collection` 是 Java 集合框架（Collections Framework）中除 Map 之外的所有集合的核心**父接口**。

- **身份**：是一个 Interface（接口）。
- **作用**：它定义了所有集合通用的基本操作，比如 `add()`、`remove()`、`contains()`、`size()` 等。
- **子接口**：主要的子接口包括 `List`、`Set` 和 `Queue`。

#### 2. Collections：集合的"工具箱"

`Collections` 是一个专门用来操作 `Collection`（以及 Map）的**静态工具类**。

- **身份**：是一个 Class（类），位于 `java.util` 包。
- **作用**：它包含了大量**静态方法**，用于对集合进行排序、搜索、线程安全化处理等。
- **关键特征**：它的构造方法是私有的，你不能 `new` 它。
- **常用方法**：
  - `sort(List list)`：对集合进行升序排序。
  - `reverse(List list)`：反转集合中的元素顺序。
  - `shuffle(List list)`：随机打乱集合顺序。
  - `max()` / `min()`：根据自然顺序返回最大/最小元素。
  - `synchronizedList()`：将非线程安全的 List 包装成线程安全的。
  - `unmodifiableList()`：返回一个只读的集合。

#### 3. 直观对比

| **特性**     | **Collection**       | **Collections**              |
| ------------ | -------------------- | ---------------------------- |
| **本质**     | **接口 (Interface)** | **静态工具类 (Class)**       |
| **层级**     | 集合框架的根接口     | 独立于继承体系的辅助类       |
| **主要用途** | 规范集合的行为       | 提供操作集合的算法和工具方法 |
| **包含子类** | List, Set, Queue 等  | 不包含，它是单例/工具类      |

**记忆窍门**：复数形式（以 `s` 结尾）的类通常都是对应的工具类，比如 `Arrays` 是操作数组的，`Files` 是操作文件的，`Collections` 就是操作集合的。

---

### 如何实现数组和 List 之间的转换？

可以用 `Arrays.asList()` 进行转换，但这种方式得到的 List 是一个**固定长度**的列表，不能 remove 和 add，如果需要修改的话，得 new 一个新的 ArrayList，然后把 `Arrays.asList(arr)` 作为构造器参数传入进去：

```java
String[] arr = {"a", "b", "c"};

// 固定长度，不可增删
List<String> fixed = Arrays.asList(arr);

// 可变 List
List<String> mutable = new ArrayList<>(Arrays.asList(arr));
mutable.add("d"); // ✅ OK
```

---

## 关联知识点

- [[03-Java/枚举]] — Java 枚举的高级用法与原理
- [[03-Java/sealed class]] — Java 17 sealed class 密封类详解
- [[03-Java/jvm八股]] — JVM 八股总览（内存模型、类加载、GC）
- [[03-Java/Java并发编程面试题]] — Java 并发编程八股（JMM、锁、AQS、线程池）
- [[06-其他杂项/Record类详解]] — Java 14 Record 不可变数据类
- [[06-其他杂项/虚拟线程详解]] — Java 21 虚拟线程（Project Loom）原理
- [[06-其他杂项/注解原理详解]] — Java 注解的编译时/运行时处理原理