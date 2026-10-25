---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---

这两个都是 Java 8 引入的函数式接口，位于 `java.util.function` 包，是 CompletableFuture、Stream 等 API 的参数类型基础。

---

## 一、核心定义

```java
@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);               // 核心方法：有入参，有返回值
    
    default <V> Function<V, R> compose(Function<V, T> before) { ... }
    default <V> Function<T, V> andThen(Function<R, V> after)  { ... }
    static  <T>  Function<T, T> identity()                    { ... }
}

@FunctionalInterface
public interface Consumer<T> {
    void accept(T t);           // 核心方法：有入参，无返回值
    
    default Consumer<T> andThen(Consumer<T> after) { ... }
}
```

最本质的区别就一点：**Function 有返回值，Consumer 没有**。

---

## 二、整个 function 包的家族关系

![[Pasted image 20260629170013.png]]

---

## 三、Function 的三个默认方法

这是面试最容易考的细节。

### `andThen` — 先执行自己，再执行 after

```java
Function<String, Integer> strToInt = Integer::parseInt;
Function<Integer, String> intToHex = i -> "0x" + Integer.toHexString(i);

Function<String, String> combined = strToInt.andThen(intToHex);
combined.apply("255");  // "255" → 255 → "0xff"
```

### `compose` — 先执行 before，再执行自己

```java
Function<String, String> combined2 = intToHex.compose(strToInt);
combined2.apply("255");  // 结果完全一样，只是组合方向写法不同
```

两者关系：`f.andThen(g)` 等价于 `g.compose(f)`，数据流方向相反，仅此而已。

### `identity` — 返回入参本身

```java
Function<String, String> id = Function.identity();
id.apply("hello");  // "hello"

// 实际用途：toMap 时 value 就是元素本身
Map<String, User> userMap = users.stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));
```

[[Function.identity() 在 toMap 中的作用]]

---

## 四、Consumer 的 `andThen`

```java
Consumer<String> print  = System.out::println;
Consumer<String> log    = s -> logger.info(s);

Consumer<String> both = print.andThen(log);
both.accept("hello");  // 先 print，再 log
```

注意 Consumer 只有 `andThen`，没有 `compose`

---

## 五、和 CompletableFuture 的对应关系

```java
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "hello");

// thenApply 接收 Function<T, R>
cf.thenApply(s -> s.toUpperCase());          // Function<String, String>
cf.thenApply(s -> s.length());               // Function<String, Integer>

// thenAccept 接收 Consumer<T>
cf.thenAccept(s -> System.out.println(s));   // Consumer<String>

// thenRun 接收 Runnable（不关心上游结果），但前后执行
cf.thenRun(() -> System.out.println("done")); // Runnable

// thenCompose 接收 Function<T, CompletableFuture<U>>
cf.thenCompose(s -> CompletableFuture.supplyAsync(() -> s + " world"));

// handle 接收 BiFunction<T, Throwable, R>
cf.handle((result, ex) -> ex != null ? "error" : result);

// whenComplete 接收 BiConsumer<T, Throwable>
cf.whenComplete((result, ex) -> log.info("result={}, ex={}", result, ex));
```

---

## 六、一个容易踩的坑

**Consumer 里不能抛 checked exception**：

```java
// 编译报错：forEach 接收 Consumer，Consumer.accept 没有声明抛异常
list.forEach(s -> {
    Files.write(path, s.getBytes());  // throws IOException → 编译失败
});

// 解决方案1：try-catch 包裹
list.forEach(s -> {
    try { Files.write(path, s.getBytes()); }
    catch (IOException e) { throw new RuntimeException(e); }
});

// 解决方案2：自定义 ThrowingConsumer 工具方法
@FunctionalInterface
interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
}

static <T> Consumer<T> wrap(ThrowingConsumer<T> tc) {
    return t -> {
        try { tc.accept(t); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}

list.forEach(wrap(s -> Files.write(path, s.getBytes())));
```

**Function 同理**，`java.util.function` 里所有接口的函数方法都不声明 checked exception，遇到 IO、SQL 操作必须自己处理或封装。

---

## 七、记忆口诀

```
有进有出  →  Function    (apply)
有进无出  →  Consumer    (accept)
无进有出  →  Supplier    (get)
无进无出  →  Runnable    (run)
返回bool  →  Predicate   (test)
```

两个入参就在前面加 `Bi`：`BiFunction`、`BiConsumer`。 入参出参同类型就用 `Operator`：`UnaryOperator`、`BinaryOperator`。