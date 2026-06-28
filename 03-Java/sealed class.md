## Sealed Classes（密封类）

Sealed classes 是 Java 17 正式引入的特性（JEP 409），用于**限制哪些类/接口可以继承或实现某个类型**，从而对类层次结构进行精确控制。

------

### 核心语法

```java
public sealed class Shape
    permits Circle, Rectangle, Triangle {
}
```

- `sealed` — 声明这是密封类
- `permits` — 列出允许继承的子类（同包或同模块内可省略）

------

### 子类必须三选一

每个被 `permits` 的子类，必须用以下三个修饰符之一：

| 修饰符       | 含义                       |
| ------------ | -------------------------- |
| `final`      | 不能再被继承，终止继承链   |
| `sealed`     | 继续密封，进一步限制子类   |
| `non-sealed` | 开放继承，任何类都可继承它 |

```java
public final class Circle extends Shape {
    double radius;
}

public sealed class Rectangle extends Shape
    permits Square {           // Rectangle 继续密封
}

public non-sealed class Triangle extends Shape {
    // 任何人都可以继承 Triangle
}

public final class Square extends Rectangle { }
```

------

### 与 Pattern Matching 结合（最佳拍档）

Sealed class 的最大价值在于与 `switch` 的模式匹配配合，编译器能做**穷举性检查**：

```java
public double area(Shape shape) {
    return switch (shape) {
        case Circle c    -> Math.PI * c.radius * c.radius;
        case Rectangle r -> r.width * r.height;
        case Triangle t  -> 0.5 * t.base * t.height;
        // 无需 default！编译器知道已穷举所有子类
    };
}
```

如果漏掉某个子类，**编译器直接报错**，非常安全。

------

### Sealed Interface

接口也可以 sealed，record 是常见的实现方式：

```java
public sealed interface Result<T>
    permits Result.Ok, Result.Err {

    record Ok<T>(T value)       implements Result<T> {}
    record Err<T>(String error) implements Result<T> {}
}
```

使用：

```java
Result<Integer> res = new Result.Ok<>(42);

String msg = switch (res) {
    case Result.Ok<Integer> ok   -> "成功: " + ok.value();
    case Result.Err<Integer> err -> "失败: " + err.error();
};
```

这种写法非常接近 Rust 的 `enum` / Kotlin 的 `sealed class`。

------

### 与普通继承的对比

|          | 普通类                 | Sealed 类             |
| -------- | ---------------------- | --------------------- |
| 谁能继承 | 任何人（同包或public） | 只有 permits 列出的类 |
| 穷举检查 | ❌ 无法穷举             | ✅ 编译器保证穷举      |
| 适合场景 | 开放扩展               | 固定的类型集合        |

------

### 使用建议

**适合用 sealed 的场景：**

- 领域模型中固定的类型集合（订单状态、支付方式、消息类型）
- 替代 `enum`（需要携带不同字段时）
- 实现代数数据类型（ADT）

**不适合的场景：**

- 需要第三方扩展的框架/库
- 类型集合不固定、会频繁新增的业务

------

### 小结

```
sealed = "我知道我所有的子类" + "编译器帮我检查穷举"
```

配合 record + pattern matching switch，是 Java 向函数式/类型安全方向迈进的重要一步，在写状态机、解析器、领域模型时特别好用。



