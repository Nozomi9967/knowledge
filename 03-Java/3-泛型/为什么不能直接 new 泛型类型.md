---
tags:
  - java
created: 2026-10-13
updated: 2026-10-13
---

# 为什么不能直接 new 泛型类型

## 一、类型擦除

Java 泛型是编译期机制。编译器检查类型参数的正确性后，会把类型参数 `T` 替换成它的上界（通常是 `Object`），这个过程叫类型擦除。编译生成的字节码中已经没有 `T` 的具体类型信息，JVM 在运行时无法知道 `T` 到底是 `String` 还是 `User`。

## 二、为什么 `new T()` 不可行

`new T()` 需要在字节码中写明要实例化的具体类，而 `T` 在运行时已经不存在，编译器无法生成对应指令，因此直接报错。

```java
public class Box<T> {
    private T value;

    public T create() {
        // return new T();  // 编译错误：无法实例化类型参数 T
        return null;
    }
}
```

同类限制还包括：

- 不能用 `new T[10]` 创建泛型数组。
- `instanceof T` 不被允许。

这些操作都需要运行时的确切类型信息。

## 三、常见替代方案

### 1. 显式传入 `Class<T>`，通过反射创建

```java
public <T> T create(Class<T> clazz) throws Exception {
    return clazz.getDeclaredConstructor().newInstance();
}
```

### 2. 使用 `Supplier<T>` 工厂

由调用方提供创建逻辑，更灵活，也避免了反射的异常处理和性能开销。

```java
public <T> T create(Supplier<T> supplier) {
    return supplier.get();
}

// 调用：create(ArrayList::new)
```

### 3. 创建泛型数组

- 使用 `(T[]) new Object[n]` 强制转换。注意转换可能在使用时抛出 `ClassCastException`，尤其是把数组返回给外部代码时。
- 传入 `Class<T>` 后使用 `Array.newInstance` 创建。

## 四、为什么 `Class<T>` 中的 T 不会被擦成 Object

类型擦除移除的是泛型参数的静态类型信息，传入的值本身并不会因此变成 `Object`。

- `Class<T>` 编译后变成原始类型 `Class`，但传入的参数是真实存在的 `Class` 对象，例如 `String.class`，它在运行时记录着 `String` 这一类型。
- 编译器在调用处根据实参 `String.class` 推断出 T 为 `String`，并在返回值上插入 `checkcast`，运行时靠这个转换保证类型安全。
- `new T()` 缺少的是运行时的类型信息，而 `Class<T>` 把类型信息作为普通对象传入，JVM 可以通过它找到构造器并创建实例。
- 类文件中保留了泛型签名（Signature 属性），`Method.getGenericReturnType()` 等反射 API 可以读取到 `Class<T>` 中的 T，Spring、Jackson 等框架正是基于此获取泛型信息。

## 五、核心结论

- 泛型只在编译期存在，运行时被擦除，JVM 不知道 `T` 的具体类型，因此 `new T()` 无法编译。
- 擦除移除的是泛型参数的静态类型，`Class<T>` 传递的是运行时对象，这个对象本身携带具体类型信息，因此可以用来创建实例。
- 要创建 T 类型的对象，外部必须提供类型信息，即 `Class<T>` 或 `Supplier<T>`。
