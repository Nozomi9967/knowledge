---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---
# Record 类详解

## 定义

Record 是 Java 14 正式引入（JEP 395）的一种**特殊的类**，用于**透明地承载不可变数据**。它本质上是"数据载体"，编译器自动帮你生成构造器、getter、`equals()`、`hashCode()`、`toString()`。

→ **通俗理解**：你写一个 record，等于让编译器替你把 POJO 的样板代码（构造函数、getter、equals、hashCode、toString）全部自动生成了。

---

## 基本用法

```java
// 一行定义
public record Point(int x, int y) { }
```

等价于传统写法：

```java
// 等价于这个，但不用你手写
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }   // 注意：不是 getX()，而是 x()
    public int y() { return y; }

    @Override
    public boolean equals(Object o) { ... }
    @Override
    public int hashCode() { ... }
    @Override
    public String toString() { ... }
}
```

## 核心特性

| 特性 | 说明 |
|------|------|
| **不可变** | 所有字段都是 `private final`，没有 setter |
| **紧凑语法** | 一行 = 构造器 + getter + equals + hashCode + toString |
| **getter 命名** | `point.x()` 而不是 `point.getX()` |
| **可自定义** | 可添加静态方法、实例方法，可在构造函数中添加校验 |
| **可继承接口** | `implements` 可以，`extends` 不可以 |
| **不可继承** | record 是 `final` 的，不能被子类化 |

## 带校验的 Record

```java
public record Person(String name, int age) {
    // 紧凑构造器（无需声明参数列表）
    public Person {
        if (age < 0) {
            throw new IllegalArgumentException("age 不能为负数: " + age);
        }
    }
}
```

## 带自定义方法的 Record

```java
public record Color(int r, int g, int b) {
    public String toHex() {
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static Color random() {
        return new Color(
            (int)(Math.random() * 256),
            (int)(Math.random() * 256),
            (int)(Math.random() * 256)
        );
    }
}
```

## 配合模式匹配使用（Java 21+ JEP 440）

Record 最大的亮点在于**配合模式匹配进行解构**：

```java
// 传统方式：需要手动拆组件
if (obj instanceof Point) {
    Point p = (Point) obj;
    int x = p.x();
    int y = p.y();
    System.out.println(x + ", " + y);
}

// Record 模式匹配：直接解构
if (obj instanceof Point(int x, int y)) {
	// Point(int x, int y) 做了两件事情
    // 1. 判断 obj 是不是 Point 类型
    // 2. 如果是，直接把 Point 里的 x、y 解构（拆）出来赋值给局部变量 x、y
    System.out.println(x + ", " + y);  // x、y 直接用
}
```

结合 switch 模式匹配更强大：

```java
// 嵌套解构
record Address(String city, String street) {}
record Person(String name, Address address) {}

void process(Object obj) {
    switch (obj) {
        case Person(var name, Address(var city, var street)) ->
            System.out.println(name + " lives in " + city);
        case null -> System.out.println("null");
        default -> System.out.println("other");
    }
}
```

## 与 Lombok @Data 的区别

| | Record | Lombok @Data |
|--|--------|--------------|
| 关键字 | `record` | `@Data` |
| 可变性 | 不可变（final） | 默认可变（有 setter） |
| 继承 | 不能 extends 他人 | 可以 |
| 编译依赖 | 不需要 | 需要 Lombok 插件 |
| getter 命名 | `x()` | `getX()` |
| 语义 | "我就是这个数据" | "我持有这些数据" |

## 面试话术

> "Record 是 Java 14 正式引入的一种不可变数据载体。它一行代码就替你生成了构造器、accessor（注意命名是 `x()` 而不是 `getX()`）、`equals`、`hashCode` 和 `toString`。在 Java 21 中它最大的价值是配合模式匹配做**解构**，比如 `if (obj instanceof Point(int x, int y))` 直接从对象里拆出组件，省去强转和手动调用 getter。"

---

## 关联知识点

- [[Java基础面试题#Java 21 新特性知道哪些？]]
- [[06-其他杂项/注解原理详解]]

---
# 🎯 面试题 · 2026-07-17 13:49（基于《Record类详解》）

> [!question]- 面试题 1：什么是Java Record类？它主要用来解决什么问题？
> Record是Java 14正式引入、通过JEP 395规范的一种特殊类，核心定位是透明承载不可变数据的数据载体。它主要解决传统POJO开发中需要手动编写大量样板代码的问题：你只需要一行record定义，编译器就会自动帮你生成构造器、访问器、equals()、hashCode()和toString()方法，不需要再手写这些重复的模板代码，大幅简化了纯数据承载类的开发。

> [!question]- 面试题 2：Record类有哪些核心特性？和传统普通类相比它有什么限制？
> Record的核心特性主要有五点：第一它是不可变的，所有组件字段默认都是private final，没有setter方法；第二语法非常紧凑，一行record定义就等同于完成了构造器、访问器、equals、hashCode、toString的全部生成；第三它的访问器命名和传统类不同，是直接拿字段名作为方法名，比如Point的x()而不是getX()；第四它支持自定义，可以添加静态方法、实例方法，也可以在构造器中添加校验逻辑；第五它只能实现接口，不能继承其他类，而且Record类本身是final的，不允许被继承。这些限制是它的设计取舍，为了保证不可变性和语义的纯粹性，它本身就是用来做纯数据载体的，不需要复杂的继承体系。

> [!question]- 面试题 3：Record类支持自定义逻辑吗？可以如何实现？
> Record是支持自定义逻辑的，主要有两种实现方式：第一种是在紧凑构造器中添加校验逻辑，不需要重复声明参数列表，直接写入参校验代码即可，比如定义`Person(String name, int age)`的时候，可以在紧凑构造器里判断age是否小于0，不合法就抛出异常完成入参校验；第二种是添加自定义方法，包括实例方法和静态方法，比如可以给Color record加一个toHex()实例方法把RGB值转成十六进制颜色码，也可以加一个random()静态方法生成随机颜色的Color实例，这些自定义逻辑都不会影响编译器自动生成的组件、访问器等基础能力。

> [!question]- 面试题 4：Record类和Lombok的@Data注解有什么区别？
> 两者虽然都能简化数据类的样板代码，但设计定位和特性有本质区别：首先实现方式不同，Record是Java原生的关键字，而@Data是Lombok提供的注解；其次可变性不同，Record默认是不可变的，所有字段都是final且没有setter，而Lombok的@Data默认会生成setter，类是默认可变的；第三继承能力不同，Record不能继承其他类，只能实现接口，而Lombok注解的普通类支持正常的继承体系；第四编译依赖不同，使用Record不需要任何额外插件，JDK原生支持，而Lombok需要依赖对应的编译插件才能工作；第五访问器命名不同，Record的访问器是直接拿字段名作为方法名，比如x()，而Lombok生成的getter是遵循JavaBean规范的getX()；最后语义不同，Record的语义是“我就是这个数据”，是纯数据载体，而Lombok的@Data只是帮你简化了代码，类的语义还是普通的Java类。

> [!question]- 面试题 5：Record类在Java 21中配合模式匹配有什么优势？请结合例子说明。
> Record在Java 21中配合JEP 440引入的模式匹配，最大的优势是支持直接解构数据组件，大幅简化类型判断和取值逻辑。传统的instanceof判断后还需要手动强转，再逐个调用getter取值，比如判断obj是Point类型后，要先强转成Point，再调用x()、y()拿到坐标值；而用Record的模式匹配，可以直接在instanceof条件里完成类型判断和解构，比如写成`if (obj instanceof Point(int x, int y))`，这行代码既会判断obj是不是Point类型，如果是的话会直接把Point里的x、y组件赋值给局部变量x、y，后续直接就能用，不需要强转和手动调用访问器。在switch表达式里这个能力更突出，比如嵌套的record类型，可以直接用`case Person(var name, Address(var city, var street)) ->`这样的写法，一层层把嵌套的组件拆出来，代码可读性更强，也减少了冗余的取值代码。