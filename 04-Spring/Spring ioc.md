---
背诵状态: 已完成
背诵结束: 2026-08-06 19:55
---
# Spring IoC 面试完全版（深度版）

> 这一版每个问题都展开到"面试官追问能接住"的深度，最后专门附一张 **三级缓存 + 循环依赖 完整流程图**。

---

## 一、IoC 与 DI 的本质

### 1. 什么是 IoC？为什么需要它？

**回答框架：定义 → 本质 → 对比 → 举例**

IoC（Inversion of Control，控制反转）是一种**设计思想**，核心是：**对象的创建、依赖关系的维护、生命周期的管理，从应用代码中转移到容器中**。

**为什么需要？**

- 传统方式：`new UserService()` → 类与类强耦合，改一处影响多处
- IoC 方式：通过容器统一管理，对象之间通过抽象（接口/抽象类）依赖
- 本质：**面向接口编程 + 依赖倒置原则的工程实现**

**面试话术：**

> "IoC 不是一种技术，而是一种设计思想。它把对象创建和依赖维护的控制权从代码里反转到了 Spring 容器。本质是为了解耦，符合依赖倒置原则。比如以前 Service 里要 new Dao，现在 Service 只声明一个 Dao 接口，具体用哪个实现由容器决定。"

---

### 2. IoC 和 DI 的关系？

**DI（Dependency Injection）是实现 IoC 的一种方式。**

IoC 是目标（解耦），DI 是手段（容器主动注入依赖）。还有另一种实现方式叫 DL（Dependency Lookup，依赖查找），但 Spring 主要用 DI。

**面试话术：**

> "IoC 是思想，DI 是实现。Spring 通过构造器、setter、字段注入等方式把依赖塞给 Bean，这就是 DI，最终达成 IoC 的目的。"

---

### 3. 三种注入方式对比（常被追问）

| 注入方式 | 优点 | 缺点 | 推荐场景 |
|---------|------|------|---------|
| 构造器注入 | 强制依赖、不可变、利于测试、防循环依赖 | 参数多时臃肿 | 强依赖（必填） |
| Setter 注入 | 灵活、可选依赖 | 可能在使用时为 null | 可选依赖 |
| 字段注入 @Autowired | 简洁 | 无法 final、不利测试、隐藏依赖 | 不推荐 |

**面试加分点：**

- Spring 官方文档**明确推荐构造器注入**（自 Spring 4.x 起）
- 构造器注入可以让依赖在编译期就暴露设计问题（参数过多 = 类职责过重）
- 字段注入无法在反射外使用，对单元测试极不友好

---

## 二、容器与启动流程

### 4. BeanFactory vs ApplicationContext

**BeanFactory** 是 Spring IoC 的顶层接口，只提供最基础的容器能力（getBean、注册 BeanDefinition）。

**ApplicationContext** 是 BeanFactory 的子接口，扩展了大量企业级功能：

- 自动注册 BeanPostProcessor
- 国际化（MessageSource）
- 事件发布（ApplicationEventPublisher）
- 资源加载（ResourceLoader）
- 默认预实例化单例 Bean（非懒加载）

**继承关系：**

```
BeanFactory
   ↑
ListableBeanFactory
   ↑
ApplicationContext  ← 也间接继承自 BeanFactory
```

**面试话术：**

> "BeanFactory 是 Spring 容器的根接口，相当于 IoC 的最基础实现。ApplicationContext 是它的子接口，扩展了 AOP、事件、国际化等能力。实际开发我们用的都是 ApplicationContext，比如 AnnotationConfigApplicationContext、ClassPathXmlApplicationContext。BeanFactory 一般只在源码或极端性能场景下才直接用。"

---

### 5. Spring 容器启动完整流程（高频）

面试口述版本，核心就一句话：**Spring 容器启动的本质是，把配置读进来、翻译成一堆 BeanDefinition 注册到注册中心，然后按顺序实例化、注入、初始化单例 Bean，最后发布刷新事件**。下面我按"宏观流程"和"单个 Bean 的生命周期"两层来讲，再补几个高频追问点。

---

### **宏观流程：`refresh()` 方法的几个关键阶段**

我会这样跟面试官讲：入口是创建一个 `ApplicationContext`，比如注解驱动就用 `AnnotationConfigApplicationContext`，它的核心是调用了父类 `AbstractApplicationContext` 的 `refresh()` 方法。这个方法是整个启动的模板骨架，我重点记几个阶段：

**第一步，准备阶段**。`prepareRefresh()` 会设置容器启动时间、把 active 标志位设为 true，还会初始化一些环境属性、校验必需属性。这一步主要是"做准备工作"，把容器的状态置为就绪。

**第二步，获取并准备 BeanFactory**。`obtainFreshBeanFactory()` 拿到一个 `DefaultListableBeanFactory`；接着 `prepareBeanFactory()` 给这个工厂配置标准特性，比如设置 ClassLoader、注册 `ApplicationContextAwareProcessor` 这个 BeanPostProcessor、配置忽略的Aware 接口，以及注册环境相关的 Bean（Environment、系统属性等）。

**第三步，执行 BeanFactoryPostProcessor**。这一步是 `invokeBeanFactoryPostProcessors()`，非常关键。它会先执行 `BeanDefinitionRegistryPostProcessor`，再执行普通的 `BeanFactoryPostProcessor`。其中最重要的一位就是 `ConfigurationClassPostProcessor`——它会去解析 `@Configuration`、`@ComponentScan`、`@Import`、`@Bean` 等注解，把扫描到的组件真正注册成 BeanDefinition。也就是说，**注解模式下大量 BeanDefinition 的注册其实是在这一步完成的**，这一步改的是"图纸"，还没真正造 Bean。

>[[关于BeanDefinitionRegistryPostProcessor和BeanFactoryPostProcessor]]


**第四步，注册 BeanPostProcessor**。`registerBeanPostProcessors()` 把各种 BeanPostProcessor 实例化并放进工厂里，但注意，**这里只是注册、排队，真正执行是在后面 Bean 初始化的时候**。像处理 `@Autowired` 的 `AutowiredAnnotationBeanPostProcessor`、处理 `@Resource` 的 `CommonAnnotationBeanPostProcessor` 都在这里注册。

**第五步，初始化事件广播器、注册监听器**。`initApplicationEventMulticaster()` 创建事件广播器，`registerListeners()` 把 `ApplicationListener` 注册上去。

**第六步，实例化所有非懒加载的单例 Bean**，也就是 `finishBeanFactoryInitialization()`，这一步是整个启动里**最耗时、最核心**的一步，调到 `preInstantiateSingletons()`，遍历所有 BeanDefinition 逐个 `getBean`，这才真正把 Bean 造出来。具体单个 Bean 怎么造，我下面单独讲。

**第七步，完成刷新**。`finishRefresh()` 会发布 `ContextRefreshedEvent`，还会注册 LifecycleProcessor，标志着容器启动完成，可以对外提供服务了。

**面试话术：**

> "核心是 refresh() 方法，里面分 13 个步骤。最关键的两个：一是 invokeBeanFactoryPostProcessors，这里会扫描注解、把所有 BeanDefinition 注册到容器；二是 finishBeanFactoryInitialization，这里真正调用 getBean 实例化所有非懒加载单例。中间还穿插了 BeanPostProcessor 注册、事件机制初始化等。"

---

## 三、Bean 的生命周期（必考重点）

### 6. 完整生命周期（标准答案）

```
1. 实例化（Instantiation）
   → 调用构造方法 / 工厂方法，得到"原始对象"
   → InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation（可短路）
   → 构造器调用
   → InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation
2. 属性填充（Populate Properties）
   → 处理 @Autowired / @Resource / @Value
   → InstantiationAwareBeanPostProcessor.postProcessProperties
3. Aware 接口回调
   → BeanNameAware.setBeanName
   → BeanClassLoaderAware.setBeanClassLoader
   → BeanFactoryAware.setBeanFactory
   → （ApplicationContextAware 通过 ApplicationContextAwareProcessor 触发）
4. BeanPostProcessor.postProcessBeforeInitialization
   → （如 @PostConstruct 由 CommonAnnotationBeanPostProcessor 在这里触发）
5. 初始化方法
   → InitializingBean.afterPropertiesSet
   → 自定义 init-method
6. BeanPostProcessor.postProcessAfterInitialization
   → 【关键】AOP 代理对象就在这里生成（AbstractAutoProxyCreator）
7. 使用中（in use）
8. 销毁
   → @PreDestroy
   → DisposableBean.destroy
   → 自定义 destroy-method
```



### **口述版：单 Bean 生命周期**

**实例化**——调构造方法，得到一个"裸对象"，属性还没填。

**属性填充**——把 `@Autowired`、`@Value` 这些依赖塞进去。到这一步，Bean 的依赖才真正就位。

**Aware 回调**——如果 Bean 实现了 `BeanNameAware`、`ApplicationContextAware` 这类接口，Spring 会把容器相关的信息回调给它。这个可以一句带过，体现你知道"Bean 能感知到容器"。

**初始化前的增强**——这里会触发 `@PostConstruct`。顺带能补一句：`@PostConstruct` 在 `afterPropertiesSet` 之前，因为它走的是 `BeanPostProcessor` 的 before 阶段，而 `afterPropertiesSet` 是后面单独调的。

**初始化方法**——先 `afterPropertiesSet`，再自定义 `init-method`。

**初始化后的增强**——**AOP 代理就在这一步生成**。这一个点非常值得主动提，因为面试官最爱问"代理对象到底啥时候创建的"，你能准确答出"在初始化之后、放进单例池之前"，就说明你不是背的。

**销毁**——容器关闭时，`@PreDestroy` → `DisposableBean` → 自定义 `destroy-method`。

---

### **整段连起来说**

"单个 Bean 的生命周期，我记六个节点：实例化、属性填充、Aware 回调、初始化前增强、初始化方法、初始化后增强，最后销毁。实例化就是调构造方法拿到裸对象；属性填充把依赖注进去；Aware 回调让 Bean 能感知到容器；初始化前增强这步会触发 @PostConstruct；初始化方法先 afterPropertiesSet 再 init-method；初始化后增强是关键，AOP 代理对象就在这一步生成——所以容器里真正用的其实是代理后的对象。销毁时按 @PreDestroy、DisposableBean、destroy-method 的顺序。"


---

### 7. BeanPostProcessor 与 InstantiationAwareBeanPostProcessor 区别

| 维度 | BeanPostProcessor | InstantiationAwareBeanPostProcessor |
|------|-------------------|--------------------------------------|
| 触发时机 | 初始化前后 | 实例化前后 + 属性注入时 |
| 接口位置 | 顶层基础扩展点 | BeanPostProcessor 子接口 |
| 典型应用 | AOP 代理生成 | @Autowired 注入（AutowiredAnnotationBeanPostProcessor）|

**面试加分点：**

- `AutowiredAnnotationBeanPostProcessor` 实现的是 `InstantiationAwareBeanPostProcessor`，所以它在属性填充阶段才介入
- `CommonAnnotationBeanPostProcessor` 处理 `@PostConstruct`、`@PreDestroy`、`@Resource`
- `AbstractAutoProxyCreator`（AOP 核心）实现 `BeanPostProcessor`，在后置方法生成代理

---

## 四、循环依赖 + 三级缓存（吊打重点）

### 8. 什么是循环依赖？

A 依赖 B，B 又依赖 A。Spring 仅支持**单例 + setter/字段注入**的循环依赖，**构造器注入 + prototype 不支持**。

---

### 9. 三级缓存（核心源码级答案）

`DefaultSingletonBeanRegistry` 中维护三个 Map：

```java
/** 一级缓存：存放完全初始化好的成品 Bean */
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
/** 二级缓存：存放早期暴露的半成品 Bean（可能是代理，也可能是原始对象）*/
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
/** 三级缓存：存放 ObjectFactory，用于生成早期引用（关键：包含 AOP 判断逻辑）*/
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
```

**面试加分点：**

- 一级和二级是 `ConcurrentHashMap`，三级是 `HashMap`（因为三级缓存只在单线程 createBean 流程中使用）
- 三个缓存配合 `synchronized` + `singletonCurrentlyInCreation` 集合来保证并发安全

---

### 10. 三级缓存解决循环依赖完整流程图

假设场景：**A 依赖 B，B 依赖 A，且 A 配置了 AOP 切面（比如 @Transactional）**

```
┌──────────────────────────────────────────────────────────────┐
│  1. getBean(A)                                               │
│     - 查一级缓存：null                                        │
│     - 查二、三级缓存：null                                    │
│     - 标记 A 正在创建（singletonCurrentlyInCreation）         │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  2. createBean(A)                                            │
│     - 调用构造器，得到【原始对象 A】（此时还未代理）          │
│     - 将 A 的 ObjectFactory 放入【三级缓存】singletonFactories│
│       （ObjectFactory 内部逻辑：如果有 AOP → 返回代理，否则   │
│        返回原始对象。这一步尚未执行，只是"埋点")               │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  3. 属性填充 A → 发现依赖 B                                   │
│     - getBean(B)                                             │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  4. createBean(B)                                            │
│     - 构造器创建【原始对象 B】                                 │
│     - B 的 ObjectFactory 放入三级缓存                         │
│     - B 属性填充 → 发现依赖 A                                  │
│                                                              │
│     ┌─────────────────────────────────────────────────────┐  │
│     │  5. getBean(A) （从 B 内部触发）                      │  │
│     │    - 查一级缓存：null                                  │  │
│     │    - 查二级缓存：null                                  │  │
│     │    - 查三级缓存：找到 A 的 ObjectFactory ✓            │  │
│     │    - 调用 ObjectFactory.getObject()                  │  │
│     │      → 检测 A 需要 AOP 代理                            │  │
│     │      → 生成【代理对象 A*】                            │  │
│     │    - 将【代理对象 A*】放入【二级缓存】                  │  │
│     │    - 三级缓存移除 A                                    │  │
│     │    - 返回 A* 给 B                                       │  │
│     └─────────────────────────────────────────────────────┘  │
│     - B 完成属性填充                                          │
│     - B 完成初始化                                            │
│     - B 放入【一级缓存】 singletonObjects                     │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  6. 回到 A 的属性填充                                         │
│     - A 拿到 B 的引用                                         │
│     - A 完成属性填充                                          │
│     - A 完成初始化                                            │
│     - 【注意】A 此时不能再次生成代理，因为早期暴露的就是 A*    │
│     - 调用 postProcessAfterInitialization 检测：              │
│       若 A 已被代理过（wrapChecked 标记），则跳过代理生成      │
│     - 将 A* 放入【一级缓存】，移除二、三级缓存                │
└──────────────────────────────────────────────────────────────┘
                          ↓
                  流程结束，A 和 B 都可用
```

---

### 11. 为什么需要三级缓存？二级不够吗？

**这是高频追问。**

**答：** 仅考虑普通 Bean 的循环依赖，二级缓存确实够了。但**一旦涉及 AOP，必须三级缓存**。

原因：

- AOP 代理是通过 `AbstractAutoProxyCreator` 在 `postProcessAfterInitialization` 阶段生成的
- 如果没有三级缓存，Spring 就必须在实例化后**立即**判断是否生成代理 → 破坏了 Spring "代理应该在初始化后生成" 的设计
- 三级缓存存的是 `ObjectFactory`（一个 lambda / 匿名类），它在被调用时才真正执行 AOP 判断逻辑 → **实现了"按需代理"**

**面试话术：**

> "三级缓存的 value 是 ObjectFactory，本质是个延迟执行的工厂。它包裹的逻辑是 SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference。当 A 被循环依赖时，三级缓存才会被触发，决定要不要返回代理对象。这样既保留了 Spring 的设计原则（代理在初始化后生成），又解决了循环依赖时 A 早期暴露的引用必须是代理对象的问题。"

---

### 12. 为什么二级缓存也要保留？

因为**当 A 同时被 B 和 C 循环依赖时**，避免重复调用 ObjectFactory 生成不同代理对象。

流程：B 触发三级缓存 → 生成代理 A* → 放入二级缓存 → 三级缓存移除 A。然后 C 也来 getBean(A) → 直接从二级缓存取到 A*，避免重复代理。

---

### 13. 为什么构造器注入不能解决循环依赖？

构造器注入时，A 还没实例化（构造器没执行完），无法把"半成品 A"放入三级缓存。三级缓存的 ObjectFactory 必须基于已经 new 出来的对象。

**面试话术：**

> "Spring 解决循环依赖的前提是：对象至少要能 new 出来。构造器注入时 A 构造器要先调 B 的构造器，B 又要调 A 的构造器 → 死循环，连'原始对象'都拿不到，三级缓存没法埋点。Spring 在 createBeanInstance 阶段就抛 BeanCurrentlyInCreationException。"

---

### 14. prototype 作用域为什么不能解决循环依赖？

prototype 不缓存，每次 getBean 都新建，所以无法借助缓存打破循环。

---

## 五、AOP 与代理

### 15. Spring AOP 用什么代理？

- **接口** → JDK 动态代理（`Proxy.newProxyInstance`）
- **类** → CGLIB（继承生成子类）

`ProxyFactory` 内部通过 `AdvisedSupport` 的 `proxyTargetClass` 决定。

Spring Boot 2.x 起 `spring.aop.proxy-target-class` 默认 true，即默认 CGLIB。

---

### 16. AOP 代理生成时机（与三级缓存呼应）

**两个时机：**

1. **正常流程**：`AbstractAutoProxyCreator.postProcessAfterInitialization()` → `wrapIfNecessary()` → 生成代理
2. **循环依赖场景**：`getEarlyBeanReference()` 提前生成代理 → 三级缓存中 ObjectFactory 调用

为了防止重复代理，Spring 通过 `advisedBeans` 缓存 + 二级缓存配合去重。

**口述版本：**

> "AOP 代理有两个生成时机。正常情况下，在 Bean 初始化之后，`AbstractAutoProxyCreator` 的 `postProcessAfterInitialization` 方法被调用，内部走 `wrapIfNecessary`，判断这个 Bean 有没有匹配的切面，有就生成代理对象。
>
> 但在循环依赖场景下就不一样了——B 需要 A 的时候 A 还没初始化完，来不及走正常的后置处理。这时候三级缓存里的 ObjectFactory 会提前调用 `getEarlyBeanReference` 来生成代理，保证 B 拿到的 A 引用已经是代理对象。
>
> 那会不会重复代理？Spring 用了一个 `advisedBeans` 集合来记录哪些 Bean 已经被代理过了，加上二级缓存的配合，确保同一个 Bean 无论被几个其他 Bean 循环依赖，都只生成一次代理对象。"

---

### 17. AOP 相关核心组件

| 组件 | 作用 |
|------|------|
| `Advisor` | 切面 = Pointcut + Advice |
| `Pointcut` | 切点（哪些方法被增强）|
| `Advice` | 通知（Before/After/Around 等）|
| `AspectJAutoProxyCreator` | AOP 核心后置处理器 |
| `Advised` | 代理对象实现的接口，可动态加通知 |

---

## 六、Bean 的查找与创建

### 18. getBean() → doGetBean() 完整流程

```
1. getBean(name)
2. 转换别名
3. doGetBean：
   a. 先查 singletonObjects（一级缓存）
   b. 再查 earlySingletonObjects（二级缓存）
   c. 再查 singletonFactories（三级缓存），调用 getObject() 后移到二级缓存
   d. 如果是 prototype → 直接 createBean（不缓存，每次新建）
   e. 如果是单例 → createBean → 单例模式下 synchronized 保护
4. createBean：
   a. resolveBeanClass → 解析 Class
   b. resolveBeforeInstantiation → InstantiationAwareBeanPostProcessor 前置（可能短路）
   c. doCreateBean → 真正创建
      i.   createBeanInstance → 实例化（构造器）
      ii.  applyMergedBeanDefinitionPostProcessors → 处理 @Autowired、@Resource 元数据
      iii. addSingletonFactory → 放入三级缓存（关键：早期暴露）
      iv.  populateBean → 属性填充
      v.   initializeBean → Aware → 前置 → init → 后置
      vi.  放入一级缓存，清理二、三级缓存
```

---

## 七、注解对比

### 19. @Autowired vs @Resource vs @Inject

| 注解 | 来源 | 匹配策略 |
|------|------|---------|
| `@Autowired` | Spring | 先 byType，多个则 byName + @Qualifier + @Primary |
| `@Resource` | JSR-250 | 先 byName，找不到再 byType |
| `@Inject` | JSR-330 | byType，可配合 @Named 限名 |

**底层后置处理器：**

- `@Autowired` → `AutowiredAnnotationBeanPostProcessor`
- `@Resource` → `CommonAnnotationBeanPostProcessor`

---

### 20. @Component / @Service / @Repository / @Controller 区别

**语义层面区别**（技术实现完全一致，都是 `@Component` 的元注解）：

- `@Controller`：标注 Web 控制器，配合 `@RequestMapping`
- `@Service`：标注业务层
- `@Repository`：标注 DAO 层，**自动异常转换**（把 SQL 异常转 DataAccessException）
- `@Component`：通用组件

---

## 八、扩展与设计

### 21. BeanPostProcessor 与 BeanFactoryPostProcessor 区别

| 类型 | 作用对象 | 时机 |
|------|---------|------|
| BeanFactoryPostProcessor | BeanDefinition（元数据） | Bean 实例化前 |
| BeanPostProcessor | Bean 实例 | Bean 初始化前后 |

典型：

- `ConfigurationClassPostProcessor` 是 BeanFactoryPostProcessor，扫描 `@Configuration`
- `AutowiredAnnotationBeanPostProcessor` 是 BeanPostProcessor，处理 `@Autowired`

---

### 22. 手写简易 IoC 容器（设计思路）

```java
// 1. 扫描注解
List<Class<?>> classes = scanPackage("com.xxx");
// 2. 注册 BeanDefinition
Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();
for (Class<?> c : classes) {
    if (c.isAnnotationPresent(Component.class)) {
        BeanDefinition bd = new BeanDefinition();
        bd.setBeanClass(c);
        bd.setScope("singleton");
        beanDefinitionMap.put(c.getSimpleName(), bd);
    }
}
// 3. 实例化 + 依赖注入（构造器 / 字段）
Map<String, Object> singletonMap = new HashMap<>();
for (Entry<String, BeanDefinition> e : beanDefinitionMap.entrySet()) {
    Object bean = e.getValue().getBeanClass().newInstance();
    singletonMap.put(e.getKey(), bean);
}
// 二次扫描填充依赖
for (Object bean : singletonMap.values()) {
    for (Field f : bean.getClass().getDeclaredFields()) {
        if (f.isAnnotationPresent(Autowired.class)) {
            f.setAccessible(true);
            f.set(bean, singletonMap.get(f.getType().getSimpleName()));
        }
    }
}
```

**面试加分点：** 提到"两阶段"思想 —— 先全部实例化，再统一注入，**这就是 Spring 三级缓存思想的雏形**。

---

## 九、最终总结（面试结尾）

可以这样说收尾：

> "Spring IoC 我总结为**三句话**：
> 1. **本质**：用容器管理对象的生命周期和依赖关系，实现解耦；
> 2. **核心机制**：BeanDefinition 描述元数据 + 反射创建实例 + Map 缓存 + BeanPostProcessor 提供扩展；
> 3. **最精妙设计**：三级缓存 + ObjectFactory 解决 AOP 场景下的单例循环依赖，兼顾了设计原则和实际可用性。"

---

如果还想往 **AOP 源码（拦截器链 CGLIB/Invoke）** 或 **事务 `@Transactional` 失效场景** 延伸，告诉我，我再单独出一份。

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-05 20:55 | 2026-08-06 19:55 | Spring IoC 核心在于容器管理对象生命周期与依赖关系，重点掌握容器启动流程、Bean 生命周期及三级缓存解决循环依赖的机制。 | 第一轮背记完成 |
