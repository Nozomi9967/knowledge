---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---
Stream 是 Java 8 引入的一套用于“声明式处理集合数据”的 API。它不是存储数据的容器，也不会修改原集合；可以把它理解成一条数据处理流水线：从数据源取数据，经过一系列中间操作加工，最后用终止操作得到结果。

常见的数据源包括 `List`、`Set`、数组、文件行等。例如：

```java
List<String> names = Arrays.asList("Tom", "Alice", "Bob");
names.stream();
```

Stream 的操作主要分为两类。

第一类是**中间操作**，它返回一个新的 Stream，可以继续拼接，而且通常是惰性执行的，只有遇到终止操作时才真正开始计算。常用的有：

- `filter`：按条件过滤数据  
  ```java
  stream.filter(x -> x.length() > 3)
  ```

- `map`：一对一转换元素，比如把对象转成某个字段  
  ```java
  users.stream().map(User::getName)
  ```

- `flatMap`：把多个子流扁平化为一个流，常用于处理嵌套集合  
  ```java
  listOfLists.stream().flatMap(Collection::stream)
  ```

- `distinct`：去重，依赖 `equals` 和 `hashCode`  
  ```java
  stream.distinct()
  ```

- `sorted`：排序，可以传比较器  
  ```java
  stream.sorted(Comparator.comparing(User::getAge))
  ```

- `limit` 和 `skip`：分别用于截取前 N 条和跳过前 N 条，常用于分页  
  ```java
  stream.skip(10).limit(10)
  ```

- `peek`：用于在流处理过程中观察元素，一般适合调试，不建议承担业务副作用。  
  ```java
  stream.peek(System.out::println)
  ```

第二类是**终止操作**。它会触发整条流水线的执行，并产出最终结果或副作用。常见的有：

- `forEach`：遍历处理  
  ```java
  stream.forEach(System.out::println)
  ```

- `collect`：收集为 List、Set、Map 等，是最常用的终止操作  
  ```java
  List<String> result = stream.collect(Collectors.toList());
  ```

  例如按对象 ID 转成 Map：
  ```java
  Map<Long, User> map = users.stream()
      .collect(Collectors.toMap(User::getId, Function.identity()));
  ```
  面试中可以补充：如果 key 重复，`toMap` 默认会抛异常，需要指定合并策略。

- `count`：统计数量。  
- `reduce`：把多个元素归约为一个值，比如求和、求最大值。  
  ```java
  int sum = numbers.stream().reduce(0, Integer::sum);
  ```

- `min`、`max`：求最小值和最大值。  
- `findFirst`、`findAny`：查找元素，返回 `Optional`。  
- `anyMatch`、`allMatch`、`noneMatch`：判断是否匹配条件。  

一个典型例子是：从用户列表中筛选成年用户，按年龄排序，取姓名：

```java
List<String> names = users.stream()
    .filter(user -> user.getAge() >= 18)
    .sorted(Comparator.comparing(User::getAge))
    .map(User::getName)
    .collect(Collectors.toList());
```

面试时还可以重点强调几个特点：

第一，Stream 是**一次性消费**的。一个 Stream 执行终止操作后就不能再次使用，否则会抛 `IllegalStateException`。

第二，Stream 支持**惰性求值和短路操作**。例如 `filter`、`map` 这类中间操作不会马上执行；`findFirst`、`anyMatch`、`limit` 属于短路操作，满足条件后可以提前结束，不必处理所有元素。

第三，Stream 分为串行流和并行流。普通 `stream()` 是串行处理，`parallelStream()` 或 `stream().parallel()` 可以并行处理。并行流适合数据量大、计算密集、任务彼此独立的场景；但它有线程调度、拆分和合并成本，而且默认使用公共 ForkJoinPool，所以不能认为用了并行流就一定更快。涉及共享可变状态、顺序敏感逻辑、IO 操作时通常不建议直接使用并行流。

第四，Stream 和 Collection 的区别是：Collection 主要负责“存储数据”，Stream 主要负责“计算和处理数据”。Collection 可以重复遍历，Stream 通常只能消费一次。

最后，实际开发中要避免在 Stream 里写复杂业务逻辑、修改外部变量，或者滥用并行流。简单的数据转换、过滤、分组、聚合很适合 Stream；如果逻辑非常复杂，传统 `for` 循环往往更容易调试和维护。


---
# 🎯 面试题 · 2026-07-17 13:29（基于《StreamAPI》）
> [!question]- 面试题 1：什么是 Stream API？它和 Collection 集合有什么区别？
> Stream 是 Java 8 引入的一套用于“声明式处理集合数据”的 API，它本身不是存储数据的容器，也不会修改原集合，可以理解成一条数据处理流水线：从数据源取数据，经过一系列中间操作加工，最后用终止操作得到结果。而 Collection 集合的核心职责是存储数据，支持重复遍历；和 Collection 相比，Stream 的核心职责是计算和处理数据，并且 Stream 属于一次性消费，执行完终止操作后就不能再次使用，否则会抛出 IllegalStateException，而 Collection 可以多次遍历。

> [!question]- 面试题 2：Stream 的操作分为哪两类？分别有什么特点？请各举几个例子。
> Stream 的操作主要分为中间操作和终止操作两类。中间操作会返回一个新的 Stream，支持链式拼接，通常是惰性执行的，也就是说调用中间操作时不会真正处理数据，只有遇到终止操作时才会启动整个流水线的计算。常见的中间操作有 filter（按条件过滤元素）、map（一对一转换元素，比如提取对象字段）、flatMap（将嵌套集合扁平化为单个流）、distinct（去重）、sorted（排序）、limit/skip（截取/跳过前 N 条元素，常用于分页）、peek（用于调试观察流内元素）等。终止操作会触发整条流水线的执行，产出最终结果或者产生副作用，常见的终止操作有 forEach（遍历处理元素）、collect（将流收集为 List、Set、Map 等集合，是最常用的终止操作）、count（统计元素数量）、reduce（将多个元素归约为单个值，比如求和、求最大值）、min/max（求最值）、findFirst/findAny（查找元素返回 Optional）、anyMatch/allMatch/noneMatch（判断元素是否匹配条件）等。

> [!question]- 面试题 3：Stream 中的 map 和 flatMap 有什么区别？分别适用于什么场景？
> map 和 flatMap 都是 Stream 的中间操作，但适用场景和效果有明显区别。map 是一对一的元素转换，它会将流中的每个元素映射为另一个元素，最终得到的流和原流的元素个数一致，比如把 User 对象流转换为用户名流，就可以用 `users.stream().map(User::getName)` 实现。而 flatMap 主要用于处理嵌套集合结构，它会把每个元素对应的子流扁平化为一个统一的流，最终得到的流元素个数是所有子流元素的总和，比如处理 `List<List<String>>` 类型的嵌套列表，想要得到所有元素的平铺流，就可以用 `listOfLists.stream().flatMap(Collection::stream)` 实现，如果用 map 的话会得到嵌套的 List 结果，无法直接获取所有平铺的元素。

> [!question]- 面试题 4：使用 Stream 的 Collectors.toMap 将集合转为 Map 时，如果出现 key 重复的情况会怎么样？如何解决？
> 使用 `Collectors.toMap` 将集合转为 Map 时，如果出现 key 重复的情况，默认会抛出 `IllegalStateException` 异常。这是因为 toMap 的默认逻辑无法处理重复 key 的 value 合并规则。解决这个问题需要给 toMap 传递第三个参数作为合并函数，指定重复 key 时的 value 处理逻辑，比如如果希望保留第一个出现的 value，就可以写成 `users.stream().collect(Collectors.toMap(User::getId, Function.identity(), (existing, replacement) -> existing))`，这里的合并函数接收两个重复 key 对应的 value，返回需要保留的结果，可以根据实际业务需求自定义合并逻辑，比如取最新的、拼接字符串等。

> [!question]- 面试题 5：Stream 有哪些核心特点？在实际开发中应该如何合理选择使用 Stream 还是传统的 for 循环？
> Stream 的核心特点主要有四个：第一是**一次性消费**，一个 Stream 执行完终止操作后就不能再次使用，否则会抛出 IllegalStateException；第二是**惰性求值与短路操作**，中间操作不会立即执行，只有遇到终止操作才会启动计算，而 findFirst、anyMatch、limit 这类短路操作在满足条件后可以提前结束处理，不需要遍历所有元素；第三是区分串行流和并行流，普通 `stream()` 是串行处理，`parallelStream()` 或者 `stream().parallel()` 可以开启并行处理，并行流适合数据量大、计算密集、任务彼此独立的场景，但有线程调度、拆分合并的成本，且默认使用公共 ForkJoinPool，不能默认认为并行流一定更快；第四是 Stream 和 Collection 的定位不同，Collection 负责存储数据，Stream 负责计算处理数据。在实际开发中，如果只是做简单的数据过滤、转换、分组、聚合这类逻辑清晰的操作，优先使用 Stream，代码更简洁声明式；如果逻辑非常复杂、涉及修改外部可变状态、包含 IO 操作、或者对执行顺序有严格要求，传统 for 循环往往更容易调试和维护，不建议强行用 Stream 实现。另外涉及共享可变状态、顺序敏感逻辑、IO 操作的场景，也不建议直接使用并行流。