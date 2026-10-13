---
tags:
  - java
created: 2026-10-13
updated: 2026-10-13
---
# Class 类详解

## 一、Class 是什么

`Class` 是 JVM 用来描述类型本身的类，位于 `java.lang` 包下，是反射机制的入口。每个被加载的类，JVM 都会在堆中为其创建一个唯一的 `Class` 对象。它描述的是类的结构信息，包括类名、父类、接口、字段、方法和构造器；而 `new User()` 创建的是 `User` 的实例。

## 二、获取 Class 对象的三种方式

```java
Class<String> c1 = String.class;                 // 类字面量
Class<?> c2 = "hello".getClass();                // 通过实例获取
Class<?> c3 = Class.forName("java.lang.String"); // 通过全限定名加载
```

同一个类无论通过哪种方式获取，得到的都是同一个 `Class` 对象，因此可以用 `==` 比较。

## 三、常用能力

- 获取类名：`getName()`、`getSimpleName()`
- 判断父类和接口：`getSuperclass()`、`getInterfaces()`
- 获取构造器和方法：`getDeclaredConstructor()`、`getMethods()`
- 创建实例：`getDeclaredConstructor().newInstance()`

## 四、Class<T> 中的 T

`Class<T>` 中的 T 表示该 `Class` 对象所描述的类型，例如 `Class<String>` 描述 `String`，`Class<User>` 描述 `User`。这正是泛型方法能借助它创建正确类型对象的原因。
