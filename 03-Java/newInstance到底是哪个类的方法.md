---
tags:
  - java
created: 2026-06-28
updated: 2026-06-28
---
### **`Class.newInstance()` 和 `Constructor.newInstance()` 都可以创建对象，但它们是不同的方法，各有优缺点**

你的疑问指向了一个重要的细节——确实存在两种方式，而且它们的工作原理不同。

## 两种方式的区别

**方式1：`Class.newInstance()`**

```java
Class<?> clazz = Class.forName("com.example.MyClass");
Object obj = clazz.newInstance();  // 直接调用 Class 的方法
```

**方式2：`Constructor.newInstance()`**

```java
Class<?> clazz = Class.forName("com.example.MyClass");
Constructor<?> constructor = clazz.getConstructor();  // 先获取构造器
Object obj = constructor.newInstance();  // 再调用构造器的方法
```

## 为什么有两种方式？

**`Class.newInstance()` 的优点：**
- 代码更简洁，一步到位
- 对于简单场景（无参构造器）足够用

**`Class.newInstance()` 的缺点：**
- ❌ 只能调用**无参构造器**
- ❌ 抛出的异常类型受限（只能抛 `InstantiationException` 和 `IllegalAccessException`）
- ❌ 已在 Java 9+ 被标记为过时（deprecated）

**`Constructor.newInstance()` 的优点：**
- ✅ 可以调用**有参构造器**
- ✅ 异常处理更灵活
- ✅ 是现代 Java 推荐的方式

## 实际对比

**如果类有有参构造器：**

```java
public class MyClass {
    private String name;
    
    // 有参构造器
    public MyClass(String name) {
        this.name = name;
    }
}

// ❌ Class.newInstance() 会报错，因为找不到无参构造器
Object obj1 = clazz.newInstance();

// ✅ Constructor.newInstance() 可以传参
Constructor<?> constructor = clazz.getConstructor(String.class);
Object obj2 = constructor.newInstance("hello");
```

## 为什么教材还在介绍 `Class.newInstance()`？

虽然 `Class.newInstance()` 已过时，但它仍然被广泛提及，原因是：

1. 代码简洁，易于理解反射的基本概念
2. 很多旧代码仍在使用
3. 对于学习反射的入门阶段足够了

## 现代推荐做法

```java
Class<?> clazz = Class.forName("com.example.MyClass");

// 推荐：使用 Constructor.newInstance()
Constructor<?> constructor = clazz.getConstructor();  // 或 getConstructor(参数类型)
Object obj = constructor.newInstance();  // 或 constructor.newInstance(参数值)
```

所以，你的理解是对的——获取构造器再调用 `newInstance()` 是更灵活、更现代的做法。文章中提到 `Class.newInstance()` 可能是为了简化讲解，但在实际项目中应该优先使用 `Constructor.newInstance()`。