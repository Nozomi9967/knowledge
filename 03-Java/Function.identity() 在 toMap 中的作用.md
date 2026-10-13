---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---


先看 `Collectors.toMap` 的签名：

```java
public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(
    Function<? super T, ? extends K> keyMapper,    // 第一个参数：怎么提取 key
    Function<? super T, ? extends U> valueMapper   // 第二个参数：怎么提取 value
)
```

两个参数**都是 Function**，告诉 Stream 每个元素怎么变成 key、怎么变成 value。

---

## 拆解那行代码

```java
Map<String, User> userMap = users.stream()
    .collect(Collectors.toMap(
        User::getId,          // keyMapper：每个 User → 取它的 id 作为 key
        Function.identity()   // valueMapper：每个 User → 还是它自己作为 value
    ));
```

`Function.identity()` 的源码极其简单：

```java
static <T> Function<T, T> identity() {
    return t -> t;   // 入参是什么，返回就是什么
}
```

所以上面等价于：

```java
.collect(Collectors.toMap(
    User::getId,
    user -> user    // 直接写 lambda 也完全可以
));
```

---

## 为什么不直接写 `user -> user`

两种写法完全等价，`Function.identity()` 只是更语义化：

```java
// 写法1：lambda
.collect(Collectors.toMap(User::getId, user -> user));

// 写法2：identity（更清晰地表达"value 就是元素本身，不做任何转换"）
.collect(Collectors.toMap(User::getId, Function.identity()));
```

实际项目里两种都能见到，`identity()` 在代码 review 时可读性稍好一点。

---

## 对比：value 需要转换的情况

```java
// value 是 User 本身 → identity
Map<String, User>    byId   = users.stream()
    .collect(toMap(User::getId, Function.identity()));

// value 是 User 的某个字段 → 普通 Function
Map<String, String>  byId   = users.stream()
    .collect(toMap(User::getId, User::getName));

// value 是加工后的对象 → lambda
Map<String, UserDTO> byId   = users.stream()
    .collect(toMap(User::getId, user -> new UserDTO(user)));
```

`identity()` 就是这三种里"不加工"那种情况的标准写法。




---

## 先搞清楚 T、K、U 是什么

`toMap` 是个泛型方法，三个类型参数各有分工：

```java
Collector<T, ?, Map<K, U>> toMap(...)
//         T                K  U
```

|字母|含义|对应例子 `toMap(User::getId, User::getName)`|
|---|---|---|
|T|Stream 里每个**元素**的类型|`User`|
|K|生成的 Map 的 **key** 类型|`String`（id）|
|U|生成的 Map 的 **value** 类型|`String`（name）|

这三个字母 Java 编译器会**自动推断**，你不用手写，只要传对 lambda 它自己能算出来。

---

## 再看 `? super T` 和 `? extends K`

```java
Function<? super T, ? extends K> keyMapper
```

拆成两半：

**入参：`? super T`**

> 这个 Function 接收的入参，必须是 T 或 T 的**父类**

```java
// T = User，? super User 可以是：
Function<User, ?>       // ✅ 正好是 T
Function<Object, ?>     // ✅ Object 是 User 的父类
Function<Animal, ?>     // ✅ 假设 User extends Animal
Function<String, ?>     // ❌ String 和 User 没有继承关系
```

为什么要允许父类？因为如果你有一个处理 `Object` 的函数，它当然也能处理 `User`，没必要拒绝它。

---

**返回值：`? extends K`**

> 这个 Function 的返回值，必须是 K 或 K 的**子类**

```java
// K = String，? extends String 可以是：
Function<?, String>          // ✅ 正好是 K
Function<?, VarcharString>   // ✅ 假设 VarcharString extends String
```

为什么要允许子类？因为你要的是 `String`，给你个 `String` 的子类当然也能用。

---

## 但实际写代码时根本不用管这些

```java
// 你只需要写这个：
.collect(Collectors.toMap(User::getId, User::getName))

// 编译器自动推断：
// T = User（Stream 元素类型）
// K = String（getId 返回 String）
// U = String（getName 返回 String）
// ? super T 和 ? extends K 的约束自动满足
```

`? super T` 和 `? extends K` 是给**框架设计者**用的约束，让 API 更灵活，**调用方几乎感知不到**。

---

## 一句话记住

```
? super T   →  可以比 T 更宽（父类），因为"能处理父类的函数肯定能处理子类"
? extends K →  可以比 K 更窄（子类），因为"返回子类也能当父类用"
```

这两个规则有个名字叫 **PECS**（Producer Extends, Consumer Super），面试偶尔会考，知道即可，日常写业务代码遇到这种签名直接忽略通配符，只看 T K U 三个字母就够了。



---

## 里氏替换原则

Java 里有一条基本规则：

```java
// 子类对象可以赋值给父类引用
Object obj = new User();  // ✅ User 是 Object 的子类，没问题
```

**子类对象可以当父类用，反过来不行。**

---

## 代入到 Function

```java
Function<Object, String> f = obj -> obj.toString();
```

这个函数声明接收 `Object`，你传一个 `User` 进去：

```java
f.apply(new User());  // ✅ 没问题，User 是 Object 的子类
```

完全合法，因为 `User` 本来就是 `Object`。

---

## 所以 `? super T` 的意思

```java
Function<? super T, ? extends K> keyMapper
// T = User

// 以下都合法：
Function<User, ?>    ✅  // 正好是 T
Function<Object, ?>  ✅  // Object 是 User 父类，传 User 进去没问题
```

**允许父类，是因为你实际传进函数的永远是 T（User），函数能接收 T 的父类，自然也能接收 T。**

---

一句话：`? super T` 放宽了限制，让"能接收更大范围类型"的函数也能用，没别的。



# ？和T，到底哪个是我传的


好问题，这正是最容易混的地方。

---

## 场景还原

```java
List<User> users = ...;
users.stream()
     .collect(Collectors.toMap(User::getId, Function.identity()));
```

Stream 里装的是 `User`，**你传进去的永远是 `User`**。

---

## `?` 是函数声明的，不是你传的

```java
Function<? super T, ? extends K> keyMapper
```

这里的 `?` 是在描述 **keyMapper 这个函数本身** 的类型，不是你传给函数的数据。

拆开读：

```
keyMapper 是一个 Function
    它的入参类型：是 T 或 T 的某个父类（? super T）
    它的返回类型：是 K 或 K 的某个子类（? extends K）
```

---

## 对应到实际

```
T = User，K = String

你传的数据：User 对象          ← Stream 里流出来的
函数的入参声明：? super User   ← 可以是 User、Object 等
```

||是什么|
|---|---|
|你传的数据|`User` 对象（T）|
|`?` 描述的|keyMapper 函数的入参类型声明|

---

## 一句话

> **你传的是 T，`?` 描述的是函数能接收什么类型。** 只要函数能接收 T 或 T 的父类，你传 T 进去就没问题。


# ？我传的是T，不就意味着函数只接收到了T吗？

对！你理解得完全正确。

---

从**调用方**角度看，确实就是：

```
你传 User → 函数收到 User
```

---

## 那 `? super T` 存在的意义是什么

是为了让下面这种情况也能编译通过：

```java
// 假设你已经有一个现成的函数，声明接收 Object
Function<Object, String> f = obj -> obj.toString();

// 能不能把它传给 toMap 用？
users.stream().collect(Collectors.toMap(f, Function.identity()));
```

如果 `toMap` 的签名写死成 `Function<T, K>`，即 `Function<User, String>`，那上面这行**编译报错**，因为 `Function<Object, String>` ≠ `Function<User, String>`。

写成 `Function<? super T, ...>` 之后，编译器就能接受它——反正你传进去的数据是 `User`，函数能处理 `Object`，`User` 是 `Object`，没问题。

---

## 所以

`? super T` 不是给你（调用方传数据）用的，是为了让**函数本身的类型**更灵活，复用已有的父类函数。

日常写 lambda 时完全感知不到它，只有当你把一个 `Function<Object, ?>` 当参数传进来时才有意义。