---
背诵状态: 已完成
背诵开始: 2026-08-06 19:38
背诵结束: 2026-08-06 19:48
---


### **它俩其实是父子接口，区别在"能不能加图纸"**

`BeanDefinitionRegistryPostProcessor` 继承自 `BeanFactoryPostProcessor`，是它的子接口。父接口只有一个 `postProcessBeanFactory(beanFactory)`，它的职权是**修改已经存在的 BeanDefinition**——比如改 class、改属性值、改 scope。它拿到的是 `ConfigurableListableBeanFactory`，能摸到图纸，但语义上主要是"改图纸"，而不是"加图纸"。

子接口多了一个 `postProcessBeanDefinitionRegistry(registry)`，参数是 `BeanDefinitionRegistry`。这个方法能干一件父接口干不了的事：**往注册中心里注册全新的 BeanDefinition**，也就是"加图纸"。所以一句话记本质：

- `BeanFactoryPostProcessor`：改已有图纸
- `BeanDefinitionRegistryPostProcessor`：能往里加新图纸（顺带它也是 BeanFactoryPostProcessor，所以改图纸的活它也能接）

### **为什么先加后改？这是逻辑必然，不是规定**

很多人卡在这一步，其实想通一个场景就懂了：假设某个 `BeanFactoryPostProcessor` 想去改某个由 `@Configuration` 扫出来的 Bean 的属性。如果此刻 `ConfigurationClassPostProcessor` 还没把那个 Bean 的图纸注册进来，你改个寂寞？图纸都不存在，无从下手。

所以 Spring 的处理是**先加后改**，而且"加"这一步还套了个 while 循环——因为加图纸的过程中（比如解析某个 `@Import`）可能又冒出来一个新的 `BeanDefinitionRegistryPostProcessor`，必须反复扫直到没有新增为止。等图纸都齐了，再统一跑"改"。

还有一个容易被忽略的点：`BeanDefinitionRegistryPostProcessor` 本身也是 `BeanFactoryPostProcessor`，所以它的 `postProcessBeanFactory()` 也会在第二阶段被再调一次。也就是说 ConfigurationClassPostProcessor 这种角色，`postProcessBeanDefinitionRegistry` 在第一阶段加图纸，`postProcessBeanFactory` 在第二阶段还可以再加工。

### **ConfigurationClassPostProcessor 在哪一拨？**

它实现的是 `BeanDefinitionRegistryPostProcessor`，而且带了 `PriorityOrdered`，所以它在"加图纸"这一拨里**第一个**执行。它的 `postProcessBeanDefinitionRegistry` 干的就是解析 `@Configuration`、`@ComponentScan`、`@Import`、`@Bean`，把扫到的组件注册成 BeanDefinition。换句话说，**大量注解 Bean 的图纸是在这一刻被填进注册中心的**——这之后才轮到各路"改图纸"的后置处理器上场。

| 接口 | 关键方法 | 拿到的参数 | 本质能力 | 对应阶段 |
|---|---|---|---|---|
| `BeanDefinitionRegistryPostProcessor` | `postProcessBeanDefinitionRegistry` | `BeanDefinitionRegistry` | 注册新 BeanDefinition（加图纸） | 第一阶段，含 while 循环 |
| `BeanFactoryPostProcessor` | `postProcessBeanFactory` | `ConfigurableListableBeanFactory` | 修改已有 BeanDefinition（改图纸） | 第二阶段 |

### **面试口述可以这样讲**

"这两个其实是父子接口。父接口 `BeanFactoryPostProcessor` 只能改已有的 BeanDefinition；子接口 `BeanDefinitionRegistryPostProcessor` 多了个方法，能往注册中心里塞新的 BeanDefinition。Spring 先把所有'加图纸'的跑完——因为加的过程中可能还会引入新的 registry post processor，所以这步是循环的——图纸都齐了，再统一跑'改图纸'的。ConfigurationClassPostProcessor 就属于'加图纸'这一拨，而且是带 PriorityOrdered 的，所以最先跑，它负责把 @Configuration、@ComponentScan 扫出来的东西注册成 BeanDefinition。"

这么讲，面试官听到的是"因为先有图纸才能改，所以必然先加后改"，是一个有因果链的理解，而不是背了一个执行顺序表。

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-06 19:38 | 2026-08-06 19:48 |  | 第一轮背记完成 |
