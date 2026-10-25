# Java 访问权限修饰符学习笔记

## 一、四种修饰符总览

| 修饰符 | 同一个类 | 同一个包 | 不同包的子类 | 不同包的非子类 |
|---|---|---|---|---|
| `private` | ✅ | ❌ | ❌ | ❌ |
| default（不写） | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

可见范围从大到小：`public` > `protected` > default > `private`。

## 二、各修饰符详解

### 1. private
- 只能在声明它的同一个顶层类内部访问。
- 可修饰字段、方法、构造器、内部类；**不能修饰顶层类和局部变量**。
- 同一顶层类中的外部类与内部类之间可以互相访问私有成员。
- `private` 成员不会被继承，子类无法直接访问，也谈不上重写。
- 典型用途：隐藏实现细节 + getter/setter；单例模式把构造器设为 `private`。

```java
class Account {
    private double balance;   // 只能在 Account 内部访问
}
```

### 2. default（包级私有）
- **不是关键字**，是不写任何访问修饰符时的默认状态。
- 同包内所有类可访问；跨包不可见（即使子类也不行）。
- 顶层类不写修饰符即包级私有，只能被同包类使用。
- 不能修饰局部变量；局部变量没有访问修饰符概念。
- 注意例外：**接口中的方法默认是 `public`**，接口字段默认 `public static final`。

```java
package pkg1;
class Helper {        // 只能在 pkg1 中使用
    void help() { }
}
```

### 3. protected
- 同包所有类 + 不同包的子类可以访问。
- **关键限制**：跨包子类访问 `protected` 成员时，只能通过子类类型（或子类自身）的引用访问，不能通过父类类型引用访问。
  - `this.x`、`new B().x` ✅；`new A().x`、`A a; a.x` ❌
- `protected` 构造器：不同包的子类只能通过 `super(...)` 调用，**不能直接 `new A()` 创建父类对象**（JLS 6.6.2.2）。同包内可直接 `new`。

```java
package pkg1;
public class A {
    protected int x = 10;
    protected A() {}
}
```

### 4. public
- 任何类在任何包中都可访问。
- 一个 `.java` 文件中最多只能有一个 `public` 顶层类，且文件名必须与之相同；非 public 顶层类可以有多个。

## 三、重写与访问权限

- 重写方法的访问权限**不能低于**父类被重写方法，只能相同或扩大。
  - 父类 `protected` → 子类可以是 `protected` 或 `public`，不能是 default 或 `private`。
  - 接口方法默认 `public`，实现类必须写 `public`，否则编译错误。
- 父类 `private` 方法不参与重写：子类同名方法只是新方法，加 `@Override` 会报错。

## 四、接口的默认权限

- 接口中的方法（抽象/默认/静态）默认 `public`。
- 接口中的字段默认 `public static final`。

## 五、内部类相关

### 1. 修饰符规则
- 顶层类只能用 `public` 或 default；`private`、`protected`、`static` 不能修饰顶层类。
- 内部类可以用全部四种访问修饰符，还可以加 `static`。

### 2. 静态内部类 vs 非静态内部类
- **静态内部类**：属于外部类本身，不依赖外部实例，创建方式 `new Outer.Inner()`。
- **非静态内部类**：每个对象隐含持有一个外部类实例的引用，创建必须先有外部实例：

```java
Outer o = new Outer();
Outer.Inner in = o.new Inner();   // 外部对象.new 内部类()
```

- ❌ `Outer.new Inner()`：`new` 前必须是外部类**实例**，不能是类名。
- ❌ 对静态内部类写 `o.new Inner()`：静态内部类不需要外部实例。
- `()` 属于被创建类的构造器调用：`o.new Inner(5)` 中括号跟在 `Inner` 后，`o.` 只是限定外部实例的引用。
- 类名写法：`MyList<String>.Itr`（泛型外部类的内部类需带类型参数）。

### 3. 非静态内部类典型场景
内部类需要读写外部对象的实例字段，如自定义迭代器：

```java
public class MyList<T> implements Iterable<T> {
    private Object[] data = new Object[10];
    private int size = 0;

    class Itr implements Iterator<T> {
        private int cursor = 0;
        public boolean hasNext() { return cursor < size; }   // 直接访问外部实例字段
        @SuppressWarnings("unchecked")
        public T next() { return (T) data[cursor++]; }
    }
}
// 外部创建：MyList<String>.Itr it = list.new Itr();
```

## 六、题目回顾（含答案）

### 选择题
1. **C** — 顶层类只能 `public` 或默认。
2. **B** — default 同包可访问；不能修饰局部变量。
3. **C** — 重写权限不能低于父类，`void run()` 低于 `protected` 报错。
4. **D** — 父类 private 方法不参与重写，子类同名方法是新方法。
5. **C** — private 不能修饰局部变量。
6. **C** — 接口方法默认 public。
7. **B** — 实现接口方法必须 public，权限不能更低。

### 代码分析题
8. **A（仅①⑥）** — 跨包子类 B 中：`this.x`（子类引用访问 protected）✅；`y` 跨包不可见 ❌；`z` 私有 ❌；`new A()` 调 protected 构造器 ❌；`a.x` 父类引用 ❌；`b.x` 子类引用 ✅。
9. **B** — protected 构造器跨包只能通过 `super()` 调用，不能 `new A()`；编译器提示 `A() has protected access`。
10. **B** — 单例构造器 private + 静态方法获取实例。

### 判断题
11. 正确 — protected = 同包所有类 + 不同包子类。
12. 正确 — 同一顶层类内部可互访私有成员。
13. 正确 — private 成员不被继承，子类无法直接调用。

### 填空题
14. `public`、`protected`、default、`private`。
15. **大于等于**（只能扩大不能缩小）。

### 综合分析题
16. 行 A ❌（private 字段外部类不可访问）；行 B ❌（Inner 是 private 内部类，外部不可见）；行 C ❌（value 是 private）。修正：Inner 改为 public/static 可见 + value 改为 public 或提供 getter。注意：即使 Inner 改为 public static，`new Outer.Inner()` 写法本身合法，但 `in.value` 仍报错。
17. 正确 — 一个 .java 文件最多一个 public 顶层类，文件名须与之相同；default 顶层类可有多个。

## 七、易错点清单

1. `default` 是默认状态，不是关键字；接口成员的默认权限是 `public`，与普通类不同。
2. 跨包子类访问 protected 成员，只能用子类类型引用；`new A()` 还受 protected 构造器规则限制。
3. 重写权限只能放大不能缩小；private 方法不参与重写，`@Override` 会报错。
4. 非静态内部类创建语法是 `外部实例.new Inner()`，括号跟在被创建的类名后。
5. 顶层类不能加 `private` / `protected` / `static`；局部变量不能加任何访问修饰符。
6. 一个源文件只能有一个 public 顶层类，且文件名必须与之一致。
