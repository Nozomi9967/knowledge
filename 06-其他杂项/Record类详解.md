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
