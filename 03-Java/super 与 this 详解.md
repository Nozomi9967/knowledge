---
tags:
  - java
  - this
  - super
  - 面试
created: 2026-10-09
updated: 2026-10-09
source: 答疑会话（super 与 this 详解 + super.getClass() 的坑 + 构造器递归）
---

# super 与 this 详解

> `this` 指**当前对象本身**，`super` 指**在当前对象中访问父类的成员**。两者都**不是新的对象**——尤其 `super` 不是"父类实例"，只是"在父类里找这个成员"的一种访问方式。本页同时收掉两个高频坑：`super.getClass()` 为什么返回运行时类、构造方法第一行的 `super()` / `this()` 规则。

---

## 一、`this` 的三种用法

### 1.1 区分成员变量和局部变量（同名时）

```java
class User {
    private String name;

    User(String name) {
        this.name = name;   // 左边成员变量，右边参数
    }
}
```

### 1.2 调用本类的其他构造方法：`this(...)`

```java
class User {
    private String name;
    private int age;

    User() {
        this("未知", 0);     // 调用下面的构造方法
    }

    User(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
```

### 1.3 把当前对象传出去 / 链式调用

```java
return this;        // 返回自己，链式调用（Builder 常见）
list.add(this);     // 把自己传给别人
```

---

## 二、`super` 的三种用法

> 前提根因：内存里**只有一个对象**。`new Child()` 创建的对象既是 `Child` 也是 `Parent`（继承了父类的字段和方法），**不会**额外再 new 一个 `Parent` 对象。`super` 就是"同一个对象里，去父类部分找成员"。

### 2.1 访问父类被隐藏的字段

```java
class Parent { String name = "parent"; }

class Child extends Parent {
    String name = "child";

    void show() {
        System.out.println(name);        // child
        System.out.println(this.name);   // child
        System.out.println(super.name);  // parent
    }
}
```

### 2.2 调用父类被重写的方法：`super.method()`

```java
class Child extends Parent {
    @Override
    void hello() {
        super.hello();                 // 先执行父类逻辑
        System.out.println("child 扩展逻辑");
    }
}
```

这是在子类重写的基础上**复用父类行为**的最常见写法。

### 2.3 调用父类构造方法：`super(...)`

```java
class Child extends Parent {
    Child() {
        super("abc");   // 调父类对应构造方法
    }
}
```

---

## 三、`super` 不是"父类实例"（高频误解）

`super` 是一种**访问方式**，不是另一个对象的引用：

- 它**不能像 `this` 那样单独赋值**：`Object o = super;` 是**不合法**的。
- 它**只能向上访问直接父类**，不能写 `super.super.xxx`。
- 它只能访问父类中**非 `private`** 的成员（`private` 成员虽然存在于对象里，子类不能直接访问）。
- 接口的默认方法要用 `接口名.super.方法名()` 调用，例如 `A.super.hi()`。

---

## 四、构造方法里的规则（面试重点）

1. **构造方法的第一行**要么是 `super(...)`、要么是 `this(...)`（二者只能有一个）。
2. **都没写时**，编译器自动在第一行补上无参的 `super()`。
3. 因此——**每个构造方法最终都会走到某个 `super(...)`，父类一定会先被初始化**。用 `this(...)` 的那个构造方法本身不补 `super()`，但它转去调用的那个构造方法里，仍会有（显式或自动补的）`super(...)`。
4. **父类没有无参构造时**，子类必须显式写 `super(参数)`，否则自动补的 `super()` 找不到对应构造方法 → **编译报错**。
5. 最顶层的 `Object` 没有父类，不再向上调用。

```java
class Child extends Parent {
    Child() {
        // 没写 this(...)/super(...)，编译器自动补 super();
        System.out.println("child");
    }
}
```

### 4.1 构造器递归调用 → 编译报错

`this(...)` 不能在构造方法之间形成环：

```java
class Demo {
    Demo()    { this(1); }   // → Demo(int)
    Demo(int x) { this(); }  // → 又回到 Demo()
}
```

编译报错 `recursive constructor invocation`——调用链 `Demo() → Demo(int) → Demo()` 永远走不到真正创建对象那一步。直接调自己 `Demo(){ this(); }` 同样报错。

**正确写法**：让多个构造方法最终**汇聚到同一个"主构造方法"**，由它完成初始化、不再回指：

```java
class Demo {
    private int x;
    Demo()      { this(1); }   // 调主构造方法
    Demo(int x) { this.x = x; } // 主构造方法，不再 this(...) 回去
}
```

---

## 五、为什么 `super.getClass()` 返回的是运行时类（经典坑）

```java
public class SuperTest extends Date {
    private void test() {
        System.out.println(super.getClass().getName());  // 输出 test.SuperTest
    }
    public static void main(String[] args) {
        new SuperTest().test();
    }
}
```

输出是 **`test.SuperTest`**（全限定名），不是父类 `java.util.Date`。原因：

- `getClass()` 定义在 `Object` 里，是 **`final`** 的，`Date` 没有自己的版本，子类也无法重写——所以 `super.getClass()` 和 `this.getClass()` 调用的是**同一个方法**。
- 它返回的是**这个对象实际是什么类**，也就是运行时类型 `SuperTest`。
- `super.xxx()` 的语义是"去父类里找 `xxx`"；**只有当子类重写了该方法时，`super` 才会显出区别**。`getClass()` 不可重写，所以 `super` 在这里完全没作用。

`getName()` 返回**全限定名** = 包名 + 类名 → `test.SuperTest`；它**不带** `.class` 后缀。想要父类的 Class 对象，用 `getClass().getSuperclass()` 或直接写 `Date.class`。

---

## 六、对比表

| | `this` | `super` |
|---|--------|---------|
| 含义 | 当前对象的引用 | 在当前对象中**访问父类成员**的关键字 |
| 是不是对象 | 是（当前对象） | ❌ 不是独立对象，只是一种访问方式 |
| 能否单独当引用传递 | ✅ 可以 | ❌ 不可以 |
| 访问范围 | 本类及继承来的成员 | **直接父类**的可见（非 private）成员 |
| 调用构造方法 | `this(...)` | `super(...)` |
| 能用于 `static` 吗 | ❌ 不能 | ❌ 不能 |

> `this` 和 `super` **都不能用在 `static` 方法或静态代码块**里——静态成员属于类，没有"当前对象"。

---

## 面试话术（口述版）

**问：`this` 和 `super` 分别是什么？**

> `this` 是当前对象的引用，`super` 是"在当前对象里访问父类成员"的关键字。**关键一点：`super` 不是父类实例**——`new Child()` 只创建了一个对象，它既是 Child 也是 Parent，不存在单独的 Parent 对象；`super` 只是换个地方找成员而已，所以它不能像 `this` 那样单独赋值。用法上，`this` 有三种：区分成员变量和局部变量、用 `this(...)` 调本类其他构造方法、把当前对象传出去做链式调用；`super` 也有三种：访问父类被隐藏的字段、用 `super.method()` 调父类被重写的方法、用 `super(...)` 调父类构造方法。构造方法里有个硬规则：第一行必须是 `super(...)` 或 `this(...)`，都没写的话编译器自动补无参 `super()`——所以父类一定会先初始化；如果父类没有无参构造，就必须显式写 `super(参数)`。另外 `this(...)` 不能在构造器之间绕成环，那样会编译报错。

**追问：`super.getClass()` 返回的是父类还是子类？**

> 返回**运行时类**，也就是子类。因为 `getClass()` 定义在 `Object` 里而且是 `final` 的，不可重写，`super.getClass()` 和 `this.getClass()` 是同一个方法、作用在同一个对象上；`super` 只在子类重写了该方法时才有区别，所以这里 `super` 没有效果。输出的是 `getName()` 返回的**全限定名**，带包名、不带 `.class`。

---

## 关联知识点

- [[编译时类型和运行时类型可能不同]] —— `getClass()` 返回运行时类型、无法被重写
- [[Java内部类]] —— 匿名/局部内部类里也会用到 `this` 与 effectively final
- [[Java变量分类与默认值]] —— 构造方法、成员变量与默认值
- [[Java基础热门面试题-1#16. Object 类有哪些常用方法？【中频】]] —— `getClass()` 与 `Object.class` 的区别
- [[DCL（Double-Checked Locking）单例完整展开]] —— 构造方法私有化与反射
