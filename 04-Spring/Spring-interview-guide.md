---
背诵状态: 背诵中
背诵开始: 2026-08-07 16:26
---
# Spring 后端开发面试热门面试题（详细口述版）

> **整理说明**：本文档基于最新后端开发面经整理，涵盖 Spring Core、Bean 生命周期、AOP、事务、Spring MVC、Spring Boot 及 Spring Cloud 七大核心模块。答案采用**口述风格**，适合面试时直接复述，重点突出“原理理解”与“实战坑点”。

## 目录

1. [IoC 与核心原理](#1-ioc-与核心原理)
2. [Bean 生命周期与作用域](#2-bean-生命周期与作用域)
3. [为什么推荐构造器注入？](#3-为什么推荐构造器注入)
4. [Spring 用了哪些设计模式？](#4-spring-用了哪些设计模式)

---

## 1. IoC 与核心原理

### 1.1 说一下你对 Spring 的理解？
**口述回答**：
Spring 本质上是一个 Java 后端开发框架，核心作用是帮我们**管理 Java 对象**。它最重要的特性是 **IoC（控制反转）**：以前我们要用对象就得自己 `new`，有了 Spring 之后，只需要告诉容器需要什么对象，它就会自动创建并注入。比如 Service 里要用 Dao，加个 `@Autowired` 就行。
其次是 **AOP**，把日志、权限、事务这些横切逻辑统一抽出来，避免每个方法里重复写。
Spring 生态也很丰富：Spring Boot 快速搭建项目、Spring MVC 处理 Web 请求、Spring Data 简化数据库操作、Spring Cloud 做微服务。

### 1.2 什么是 IoC？和 DI 有什么区别？
**口述回答**：
**IoC 即控制反转**，这里的“控制”指对象创建和依赖关系管理的控制权。传统开发中 A 需要 B 就在 A 里 `new B`，控制权在 A 手里；用 IoC 后，对象的创建交给外部容器，A 只需声明依赖，控制权“反转”给了容器。
**DI（依赖注入）** 则是实现 IoC 这种思想的具体技术手段：通过构造器、Setter 或字段把依赖对象传进来。
**一句话总结**：IoC 是思想，DI 是手段。Spring 里用 `@Autowired` 就是在用 DI 的字段注入方式。好处是降低耦合，A 不再关心 B 怎么来的，只专注自己的业务。

### 1.3 IoC 的实现机制是什么？
**口述回答**：
核心是**工厂模式加反射机制**。
1.  **加载 Bean 定义信息**：扫描 `@Component`、`@Service` 等注解的类，封装成 `BeanDefinition` 对象注册到容器。
2.  **创建 BeanFactory**：如 `DefaultListableBeanFactory` 负责创建管理 Bean。
3.  **实例化和初始化**：对单例 Bean 先查缓存，没有就通过反射调用构造方法创建实例，然后属性注入（依赖注入也是反射，`@Autowired` 标注的字段用 `Field.set` 设置），最后执行初始化回调。
整个生命周期由 IoC 容器控制，还用三级缓存解决循环依赖。

>核心是工厂模式 + 反射。容器启动时扫描带 `@Component` 等注解的类，封装成 BeanDefinition 注册进来；然后通过 BeanFactory 用反射调用构造方法创建实例，再通过反射做属性注入（`@Autowired`），最后执行初始化回调。整个 Bean 的创建和依赖关系管理都由容器控制。

### 1.4 BeanFactory 和 ApplicationContext 有什么区别？
**口述回答**：
-   **BeanFactory** 是 Spring 最底层的 IoC 容器，提供最基本的 `getBean` 能力，采用**懒加载**，只有真正获取时才创建 Bean。
-   **ApplicationContext** 是 BeanFactory 的子接口，是它的超集，额外提供国际化、事件发布、AOP、资源加载等企业级功能；它采用**预加载**，容器启动时就把所有单例 Bean 创建好，启动稍慢但运行时性能更好，而且自动管理 Bean 的初始化和销毁。
实际开发中几乎都用 ApplicationContext（如 `AnnotationConfigApplicationContext`），Spring Boot 里通过 `@Autowired` 或 `ApplicationContextAware` 获取。

### 1.5 @Autowired 和 @Resource の区别？
**口述回答**：
-   **来源不同**：`@Autowired` 是 Spring 提供的，`@Resource` 是 JDK 自带（JSR-250）的，所以 IDEA 对 `@Autowired` 字段注入会提示"不推荐"，对 `@Resource` 不提示。
-   **注入方式不同**：`@Autowired` 默认**按类型（byType）**注入，当存在多个同类型 Bean 时需要配合 `@Qualifier` 指定名称；`@Resource` 默认**按名称（byName）**注入，找不到再按类型。

### 1.6 为什么推荐构造器注入？
**口述回答**：
推荐用**构造器注入**而不是字段注入，原因有三：
1.  **利于单元测试**——构造器是一个普通 Java 方法，不需要 Spring 容器就能直接 `new` 对象并传入 mock 依赖。测试时你可以完全脱离 Spring，手动传什么进去都行，单元测试跑得快、写得干净。
2.  **能在启动时暴露循环依赖**——构造器注入无法被三级缓存解决，遇到循环依赖会直接启动失败，从而强制暴露设计问题；而字段/Setter 注入会被三级缓存"掩盖"循环依赖，让问题潜伏下来。因此推荐构造器注入是为了**尽早暴露循环依赖、倒逼重构**，而不是"延迟报错"。
3.  **可用 `final` 保证不可变**——字段可以声明为 `final`，赋值后永远不能再改。字段注入做不到这一点：`@Autowired` 是先创建空对象再通过反射赋值，而 `final` 字段要求在声明时或构造器中完成赋值，不允许后续修改，Spring 的反射赋值发生在构造之后，编译阶段就会报错。

### 1.7 Spring 用了哪些设计模式？
**口述回答**：
-   **工厂模式**：BeanFactory/ApplicationContext 创建管理 Bean。
-   **单例模式**：Bean 默认单例，通过 `singletonObjects`（ConcurrentHashMap）实现，容器级别单例。
-   **代理模式**：AOP 底层用 JDK 动态代理或 CGLIB。
-   **模板方法模式**：JdbcTemplate、RestTemplate 等定义流程骨架，子类填细节。
-   **观察者模式**：ApplicationEvent + ApplicationListener 事件机制。
				-   **适配器模式**：SpringMVC 的 HandlerAdapter 适配不同类型 Controller、AOP 的 Advice 适配器。
-   **装饰器模式**：TransactionAwareCacheDecorator 等。
-   **责任链模式**：拦截器链。

---

## 2. Bean 生命周期与作用域

### 2.1 说一下 Bean 的生命周期？
**口述回答**：
分五大阶段：
1.  **实例化**：根据 BeanDefinition 通过反射调用构造方法创建对象，此时只是空壳。
2.  **属性赋值**：通过 `@Autowired`、`@Resource` 等注解注入依赖，以及 `@Value` 注入配置值。
3.  **初始化**：依次执行 Aware 接口回调（BeanNameAware、BeanFactoryAware、ApplicationContextAware 等） → `@PostConstruct` 方法（早于 afterPropertiesSet） → `InitializingBean` 接口的 `afterPropertiesSet()` → `init-method` 指定的方法（或 `@Bean(initMethod = "...")`），四个阶段按顺序执行。
4.  **使用 Bean**。
5.  **销毁**：容器关闭时，`@PreDestroy` → DisposableBean 的 `destroy` → `destroy-method`。

> [!info] 补充：`afterPropertiesSet` 和 `init-method` 都是属性注入后的初始化回调，前者需要实现 Spring 接口（侵入框架），后者是自定义方法（无侵入），推荐用后者。初始化前后还会经过 BeanPostProcessor 的 `postProcessBeforeInitialization` 和 `postProcessAfterInitialization`，**AOP 代理就在后置处理阶段生成**。常见 BeanPostProcessor：AutowiredAnnotationBeanPostProcessor（处理 `@Autowired`、`@Value`）、CommonAnnotationBeanPostProcessor（处理 `@PostConstruct`、`@PreDestroy`、`@Resource`）、AbstractAutoProxyCreator（生成 AOP 代理）、ApplicationContextAwareProcessor（注入 Aware 回调）。`DisposableBean` 是销毁回调接口，实现 `destroy()` 方法，容器关闭时释放资源，销毁顺序 `@PreDestroy` → `destroy()` → `destroy-method`，与初始化阶段对称。

### 2.2 Bean 的作用域有哪些？
**口述回答**：
-   **singleton（默认）**：整个容器只有一个实例，Service、Dao 这些无状态组件都用它。
-   **prototype**：每次获取都创建新实例，适合有状态对象。
-   **request**：每个 HTTP 请求一个实例（Web 环境）。
-   **session**：每个用户会话一个实例，如购物车。
-   **application**：ServletContext 范围内单例。
-   **websocket**：WebSocket 会话内单例。
**注意**：singleton Bean 注入 prototype Bean 时存在"只注入一次"的坑，详见 [[2.3 singleton 注入 prototype 的问题]]。

### 2.3 singleton Bean 注入 prototype Bean 会怎样？怎么解决？
**口述回答**：
**问题现象**：singleton Bean 在整个容器生命周期只创建一次，它的依赖注入也只在创建时执行一次。如果注入的是 prototype Bean，字段里存的引用是**创建时那一个实例**，之后每次使用拿到的都是同一个对象——prototype 作用域失效了，不会每次调用都新建。

**根本原因**：依赖注入发生在容器创建 singleton Bean 的那一刻，注入完成后字段引用就被固定。prototype 的"每次获取新建"只对每次 `getBean()` 生效，但注入只发生了一次，所以只有第一个实例被注入。

**解决方案**：
1.  **`@Lookup` 注解**：标注在方法上，Spring 生成 CGLIB 子类重写该方法，每次调用都从容器获取新实例。
2.  **注入 `ObjectProvider`**：延迟获取，每次调用 `getObject()` 拿新实例。

**面试加分**：构造器注入解决不了这个问题，因为构造时就已经固定引用；`@Lookup` 和 ObjectProvider 的本质都是**延迟到使用时才去容器获取**，详见 [[2.4 @Lookup 和 ObjectProvider 的原理]]。

### 2.4 @Lookup 和 ObjectProvider 的原理是什么？
**口述回答**：
两种方式的本质相同：**把"获取 Bean"的动作从注入时延迟到方法调用时**，绕开"注入一次就固定"的问题。区别在于实现机制：

**`@Lookup` 注解**：Spring 对标注 `@Lookup` 的方法所在的 Bean 生成 **CGLIB 子类**，子类重写该方法，方法体改为 `getBean(返回类型)` 从容器获取新实例。调用方持有的实际是子类实例，所以每次调用都拿到新对象。注意要求：方法返回类型必须是目标 Bean 类型，且 Spring 会替换掉方法原来的实现。

例子：

```java
@Component
@Scope("prototype")          // PrototypeBean 是 prototype 作用域
public class PrototypeBean {
}

@Component
public class SingletonBean {
    // 方法体写什么都无所谓，会被 CGLIB 子类重写覆盖
    @Lookup
    public PrototypeBean getPrototype() {
        return null; // 永远执行不到
    }

    public void use() {
        PrototypeBean p1 = getPrototype();  // 每次调用都是新实例
        PrototypeBean p2 = getPrototype();  // p1 != p2
    }
}
```

Spring 启动时不会调用 `getPrototype()`，而是生成一个 `SingletonBean` 的 CGLIB 子类（类似 `SingletonBean$$EnhancerByCGLIB`），重写这个方法为 `return container.getBean(PrototypeBean.class)`。你 `@Autowired` 拿到的是子类实例，所以调用 `getPrototype()` 实际执行的是重写后的方法——每次从容器取新实例。

**`ObjectProvider<T>`**：Spring 提供的**延迟引用对象**，它本身被正常注入（注入的是 ObjectProvider 这个"获取器"，而不是目标 Bean），目标 Bean 的获取推迟到调用 `getObject()` / `getIfAvailable()` 时。多线程下每次调用 `getObject()` 都走一次 `getBean()`，prototype 场景自然每次新建。

例子：

```java
@Component
public class SingletonBean {
    @Autowired
    private ObjectProvider<PrototypeBean> prototypeProvider; // 注入的是获取器，不是 PrototypeBean 本身

    public void use() {
        PrototypeBean p1 = prototypeProvider.getObject();  // 每次调用都走 getBean，都是新实例
        PrototypeBean p2 = prototypeProvider.getObject();  // p1 != p2
    }
}
```

注入的 `prototypeProvider` 本身是 singleton，但它内部不持有 PrototypeBean，只持有指向容器的引用。每次 `getObject()` 才真正执行 `getBean(PrototypeBean.class)`，所以 prototype 每次都是新实例。`getIfAvailable()` 则是在容器里没有这个 Bean 时返回 null，适合处理可选依赖。

**对比**：

| 方式 | 机制 | 适用 |
|------|------|------|
| `@Lookup` | CGLIB 子类重写方法，方法体内 getBean | 简单场景，方法级获取 |
| `ObjectProvider` | 注入获取器对象，调用时 getBean | 更灵活，还支持 `getIfAvailable` 判空、多实例选择 |

### 2.5 单例 Bean 会有线程安全问题吗？怎么解决？
**口述回答**：
Spring 容器保证了 Bean 创建过程线程安全，但创建后的使用过程它不管。如果单例 Bean 有可变成员变量，多线程下就可能有安全问题。
**解决方式**：
1.  用**无状态 Bean**，状态都通过方法参数传递。
2.  用 **ThreadLocal** 保存线程相关状态。
3.  用 JUC 线程安全类，如 `ConcurrentHashMap`、`AtomicInteger`、`CopyOnWriteArrayList`。
4.  复杂操作用 `synchronized` 或 `ReentrantLock`。
5.  改为 **prototype** 作用域。
如果成员变量是 `final` 或只有依赖注入的无状态字段，则天然线程安全。

### 2.6 Spring 怎么解决循环依赖？
**口述回答**：
通过**三级缓存**解决单例 Bean 的 setter/字段注入循环依赖（构造器注入和多例无法解决）。三级缓存都在 `DefaultSingletonBeanRegistry`：
-   **一级 `singletonObjects`**：存完全初始化好的 Bean。
-   **二级 `earlySingletonObjects`**：存提前暴露的早期引用（可能已代理）。
-   **三级 `singletonFactories`**：存 `ObjectFactory` 工厂。

**流程**：以 A 依赖 B、B 依赖 A 为例：创建 A 实例后把 A 的 ObjectFactory 放三级缓存 → 注入属性时发现需要 B → 创建 B 实例并放三级缓存 → B 注入时发现需要 A，依次查一、二、三级缓存，在三级找到 A 的工厂 → 调用工厂 `getObject()`，若 A 需 AOP 代理则提前生成代理对象放入二级缓存并移除三级 → B 拿到 A 引用完成注入 → B 初始化完入一级缓存 → 回到 A，注入完整 B，A 初始化完入一级缓存。

### 2.7 为什么用三级缓存而不是两级？
**口述回答**：
核心是为了**正确处理需要 AOP 代理的 Bean**。若只有两级缓存，当 B 注入 A 时拿到的是 A 的原始对象；但 A 初始化完成后会被包装成代理对象放进容器，导致 B 持有原始对象 A、容器里却是代理对象 A，同一 Bean 出现两个实例，违反单例语义。
三级缓存放的不是实例而是 `ObjectFactory`，调用它的 `getObject()` 时会判断：需要代理就提前生成代理对象，不需要就返回原始对象，保证 B 拿到的和最终容器里的是同一个对象。二级缓存则用于缓存第一次工厂生成的早期引用，避免多次调用工厂重复创建代理（多循环依赖场景）。

> [!info] 澄清："两级缓存"指的是 `singletonObjects` + `earlySingletonObjects`（缺三级工厂）。逻辑链：两级（1+2）方案里，A 一实例化就把**原始对象**放进二级缓存 → B 索要时只能拿到原始对象 → A 初始化完成被代理后进一级 → B 手里是原始对象、容器里是代理 → 两个实例。加三级工厂后，"要不要代理"的决策延迟到 B 索要时（`getEarlyBeanReference`），B 拿到提前代理 = 最终对象，一致了。

### 2.8 为什么还需要二级缓存？早期引用为什么不能放一级缓存？
**口述回答**：
**问题背景**：三级缓存解决了"B 拿到的和最终容器里的是同一个对象"。但如果假设没有二级缓存（只有一级 `singletonObjects` + 三级 `singletonFactories`），会出现新问题。场景：A 同时被 B、C 循环引用（A→B→A、A→C→A）。

**为什么早期引用不能放一级缓存**：
1.  **一级缓存语义是"完全初始化好的成品"**——所有 `getBean()` 最终都从一级取。而 B 索要 A 时，A 还停在属性注入阶段，是**半成品**（依赖没注入、初始化没执行）。半成品被其他 Bean 当成成品用，本身就是错的。
2.  **放进去也会被覆盖**：假设把早期引用 A1 放一级——A 走完流程后，后置处理阶段生成最终代理 A2，`addSingleton(A2)` 会**覆盖**一级里的 A1。B、C 手里是 A1，容器里是 A2 → 两个实例，问题原样复现。

**没有二级缓存会发生什么**：早期引用不能放一级、又没有二级可放 → B 调用工厂拿到 A1 后只能用完即弃。等 C 索要 A 时：一级没有（A 还在创建）、三级工厂还在 → **再调一次工厂** → 生成**第二个**代理 A1'。B 拿 A1、C 拿 A1' → 同一 Bean 两个代理实例，单例语义照样被破坏。

**二级缓存的作用**：把第一次 `getObject()` 的结果存进二级缓存，C 索要时先查二级直接命中 → 工厂只被调用一次 → 全链路只有一个早期引用。

**总结**：
-   **三级缓存**解决"拿到的和最终的是同一个"（延迟代理决策，见 [[2.7 为什么用三级缓存而不是两级？]]）
-   **二级缓存**解决"工厂产物只生成一次"（缓存早期引用）
-   两个问题互不重叠、缺一不可，所以需要三级缓存的完整形态

---

## 3. AOP 面向切面编程

### 3.1 什么是 AOP？解决什么问题？
**口述回答**：
AOP 即**面向切面编程**，把日志、权限、事务这些和业务无关但又要在多处重复出现的横切逻辑抽出来，统一管理，让业务代码更清爽。它是 OOP 的补充：OOP 沿继承链抽象“强共性”，AOP 统一处理横跨多个类的“弱共性”。比如 100 个 Service 方法都要加日志和事务，不用 AOP 每个方法都要写一遍 try-catch 提交回滚，用 AOP 定义一个切面就全覆盖。Spring AOP 基于动态代理，运行期织入，无需专用编译器。

### 3.2 什么是织入？Spring AOP 为什么无需专用编译器？
**口述回答**：
**织入（Weaving）** 是把切面代码应用到目标对象上的过程。有**三种织入时机**：
-   **编译期织入**：编译时直接把切面写进目标类的字节码，普通 javac 做不到，需要 AspectJ 的专用编译器 `ajc`。
-   **类加载期织入**：JVM 加载类时通过 javaagent 在字节码里织入切面（AspectJ 的 LTW，load-time weaving）。
-   **运行期织入**：运行时动态生成代理对象拦截方法调用（Spring AOP 用的就是这种）。

**Spring AOP 为什么不需要编译器**：它用的是**运行期织入**——JVM 跑起来之后，用 JDK 动态代理（`java.lang.reflect.Proxy`）或 CGLIB 在内存中生成目标类的代理对象，把切面逻辑包在代理里，方法调用被代理拦截后转发到切面再进目标方法。这完全基于 Java 原生能力和普通类库，**不需要改造编译流程、不需要额外工具链**，所以加个依赖就能用。

**对比 AspectJ**：编译期织入必须用 `ajc` 编译，类加载期织入要配 javaagent，工具链重；换来的是能力强——方法、字段、构造方法、异常处理任何位置都能织入，还能织第三方 jar 里的类。Spring AOP 零工具成本，但只能方法级、只能拦 Spring 容器管理的 Bean。

### 3.3 Spring AOP 的核心概念有哪些？
**口述回答**：
-   **切面 Aspect**：`@Aspect` 标注的类，包含切点 and 通知。
-   **连接点 JoinPoint**：程序执行点，Spring 只支持方法级。
-   **切点 Pointcut**：`@Pointcut` 定义，用 execution 表达式匹配哪些方法。
-   **通知 Advice**：`@Before` 前置、`@After` 后置（无论是否异常）、`@AfterReturning` 返回后、`@AfterThrowing` 异常后、`@Around` 环绕（最强大，可控制是否执行、修改参数返回值，通过 `proceed()` 控制）。
-   **织入 Weaving**：把切面应用到目标对象，Spring 是运行时通过动态代理织入。
-   **目标对象 Target**：被代理的原始对象。
-   **AOP 代理 Proxy**：JDK 或 CGLIB 生成的代理对象。

### 3.4 JDK 动态代理和 CGLIB 的区别？
**口述回答**：
-   **使用条件**：JDK 动态代理要求目标类必须实现至少一个接口，基于接口创建代理；CGLIB 不要求接口，通过继承目标类生成子类代理。
-   **实现原理**：JDK 用 `java.lang.reflect.Proxy` + `InvocationHandler`，反射调用目标方法；CGLIB 是第三方字节码生成库，用 ASM 生成子类重写方法。
-   **性能**：CGLIB 创建代理稍慢但方法调用快，JDK 相反。
-   **选择**：有接口首选 JDK，没接口只能 CGLIB。注意 Spring Boot 2.0 起 `spring.aop.proxy-target-class` 默认 true，即无论是否实现接口都优先 CGLIB，简化开发者心智负担。

>**ASM 是一个 Java 字节码操作库**——直接读写、修改 `.class` 文件（字节码）的底层工具库。

### 3.5 Spring AOP 和 AspectJ 的区别？
**口述回答**：
Spring AOP 基于运行时动态代理，只支持方法级拦截，且只能拦截 Spring 容器管理的 Bean；AspectJ 是独立的 AOP 框架，支持编译期、类加载期织入，能拦截方法、字段、构造方法、异常处理等任何位置，性能更好。Spring 借鉴了 AspectJ 的概念和注解（`@Aspect`、`@Pointcut` 等），但底层实现不同。实际项目大部分用 Spring AOP 即可，只有在需要织入第三方 jar 或字段级监控时才引入 AspectJ。

### 3.6 AOP 的应用场景有哪些？
**口述回答**：
-   **事务管理**：最常见场景，`@Transactional` 一加 Spring 自动管理开启/提交/回滚。
-   **日志记录**：统一打印接口入参出参、执行耗时，方便溯源和调优。
-   **权限控制**：方法执行前校验登录状态或角色权限。
-   **性能监控**：统计方法耗时、慢接口告警。
-   **缓存处理**：`@Cacheable` 等统一缓存。
-   **限流**：自定义注解 + 切面实现接口限流。
本质都是把通用、可复用的横切逻辑抽出来无侵入增强业务。

---

## 4. Spring 事务管理

### 4.1 说说你对 Spring 事务的理解？
**口述回答**：
Spring 提供编程式和声明式两种事务。编程式用 `TransactionTemplate` 或 `PlatformTransactionManager` 手动控制边界，灵活但繁琐；声明式只需在方法上加 `@Transactional`，Spring 通过 AOP 自动处理整个生命周期：方法前开启事务、正常返回提交、抛异常回滚。底层是 AOP 代理，调用的是代理对象方法，由 `TransactionInterceptor` 拦截，根据注解属性（传播行为、隔离级别）获取事务、调用真实方法、提交或回滚。优点是业务代码不掺杂事务逻辑，缺点是最细粒度只能到方法级。

### 4.2 @Transactional 在哪些情况下会失效？
**口述回答**：
常见坑：
1.  **注解用在非 public 方法上**：Spring AOP 代理无法代理 private（子类不可见），protected/default 也不推荐。
2.  **同类内部方法调用（this 调用）**：方法 A 里 `this.方法 B()`，走的是原始对象绕过代理，B 上的 `@Transactional` 不生效，解决方式是把自身作为 Bean 注入再调用，或用 `AopContext.currentProxy()`。
3.  **异常被 try-catch 吞掉没重新抛出**：代理感知不到异常，事务正常提交。
4.  **抛出受检异常（如 IOException）默认不回滚**：默认只回滚 RuntimeException 和 Error，需 `@Transactional(rollbackFor = Exception.class)` 显式声明。
5.  **多数据源事务管理配置不当**。
6.  **数据库引擎不支持事务**（如 MyISAM）。

### 4.3 Spring 事务的隔离级别有哪些？
**口述回答**：
Spring 在 `TransactionDefinition` 中定义五种：
-   **DEFAULT**：用底层数据库默认（MySQL InnoDB 默认可重复读，Oracle 默认读已提交）。
-   **READ_UNCOMMITTED**：读未提交，会出现脏读。
-   **READ_COMMITTED**：读已提交，解决脏读但会不可重复读。
-   **REPEATABLE_READ**：可重复读，解决不可重复读但理论上会幻读（MySQL InnoDB 通过临键锁很大程度上解决幻读）。
-   **SERIALIZABLE**：串行化，解决所有并发问题但性能最差。
通过 `@Transactional(isolation = Isolation.XXX)` 设置，实际项目很少手动调，一般用 DEFAULT。

### 4.4 说说 Spring 的事务传播机制？
**口述回答**：
当一个事务方法 A 调用另一个事务方法 B 时，B 的事务如何运行就是传播机制解决的问题。七种行为：
-   **REQUIRED（默认）**：有事务加入，没有就新建。
-   **REQUIRES_NEW**：无论有没有都新开独立事务，挂起当前事务，常用于日志记录不受主业务回滚影响。
-   **NESTED**：嵌套事务，基于 Savepoint，父回滚子必回滚，子回滚不影响父。
-   **SUPPORTS**：有就用没有就不用。
-   **NOT_SUPPORTED**：以非事务方式执行，挂起当前事务，避免长事务占用连接。
-   **MANDATORY**：必须在事务中，否则抛异常。
-   **NEVER**：不能在事务中，否则抛异常。
**注意**：传播通过 ThreadLocal 实现，新线程会失效。

---

## 5. Spring MVC 工作流程

### 5.1 Spring MVC 的核心组件有哪些？
**口述回答**：
-   **DispatcherServlet**：前端控制器，统一入口，Spring Boot 自动注册并映射到 `/`。
-   **HandlerMapping**：处理器映射，根据 URL 和请求方法找到对应的 Controller 方法（RequestMappingHandlerMapping 解析 `@RequestMapping`）。
-   **Handler**：处理器，即 Controller 方法。
-   **HandlerAdapter**：处理器适配器，统一调用不同类型处理器并处理参数绑定、类型转换（RequestMappingHandlerAdapter）。
-   **ViewResolver**：视图解析器，前后端分离时主要用于返回 JSON。
-   **HandlerExceptionResolver**：异常处理器，配合 `@ControllerAdvice` + `@ExceptionHandler` 统一异常处理。
-   **MultipartResolver**：文件上传解析器。
-   **HandlerInterceptor**：拦截器。

### 5.2 说一下 Spring MVC 的工作流程？
**口述回答**：
1.  用户请求到达 **DispatcherServlet**。
2.  DispatcherServlet 调 **HandlerMapping**，根据 URL 找到 Handler 并生成 `HandlerExecutionChain`（含处理器和拦截器）返回。
3.  DispatcherServlet 根据 Handler 找到对应的 **HandlerAdapter**。
4.  HandlerAdapter 执行参数封装、数据转换、校验后调用 **Controller 方法**。
5.  Controller 执行业务返回 ModelAndView（前后端分离时 `@ResponseBody` 直接返回数据）。
6.  HandlerAdapter 把结果返回 DispatcherServlet。
7.  若是视图，**ViewResolver** 解析视图名得到 View 并渲染；若是 JSON，由 `HttpMessageConverter`（如 MappingJackson2HttpMessageConverter）序列化。
8.  DispatcherServlet 响应客户端。

### 5.3 为什么需要 HandlerAdapter？
**口述回答**：
因为 Spring MVC 支持多种风格的处理器：基于 `@Controller` 注解的、实现 Controller 接口的、实现 HttpRequestHandler 接口的等，调用方式各不相同。如果没有适配器，DispatcherServlet 就要硬编码每种处理器的调用逻辑，新增一种 Controller 类型就得改核心代码，非常僵化。引入 HandlerAdapter 屏蔽差异，给 DispatcherServlet 提供统一调用入口 `handle()`。如 `SimpleControllerHandlerAdapter` 适配 Controller 接口实现，`RequestMappingHandlerAdapter` 适配 `@RequestMapping` 注解方法。这是**适配器模式**的典型应用。

### 5.4 过滤器和拦截器有什么区别？
**口述回答**：
-   **规范不同**：过滤器是 Servlet 规范（javax.servlet.Filter，Spring Boot 3.x 为 jakarta.servlet.Filter），拦截器是 Spring MVC（HandlerInterceptor）。
-   **作用范围**：过滤器对所有请求包括静态资源生效，拦截器只拦截 Spring MVC 控制器请求。
-   **执行顺序**：完整链路 Filter 前置 → DispatcherServlet → Interceptor.preHandle → Controller → Interceptor.postHandle → 视图渲染 → Interceptor.afterCompletion → Filter 后置。
-   **依赖注入**：拦截器支持直接注入 Spring Bean，过滤器需间接获取。
-   **场景**：过滤器做编码、全局日志、安全；拦截器做权限、参数校验、业务相关。

---

## 6. Spring Boot 自动装配

### 6.1 SpringBoot 的自动装配原理是什么？
**口述回答**：
核心是 `@EnableAutoConfiguration` 注解（包含在 `@SpringBootApplication` 中）。启动时由 `AutoConfigurationImportSelector` 扫描类路径下的元数据文件：
-   Spring Boot 2.7 前是 `META-INF/spring.factories`。
-   2.7 起推荐 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
-   **3.0 已彻底废弃从 spring.factories 加载自动配置**。
文件中列出所有自动配置类全限定名，然后通过 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 等条件注解判断是否生效，满足条件才注册对应 Bean。比如引入 `spring-boot-starter-web` 就加载 `WebMvcAutoConfiguration` 自动配置 DispatcherServlet、视图解析器等。开发者可用 `@SpringBootApplication` 的 exclude 排除或自定义配置覆盖。

### 6.2 SpringBoot 的启动流程了解吗？
**口述回答**：
围绕 `@SpringBootApplication` 注解和 `SpringApplication.run()` 两个核心。注解是组合注解：`@SpringBootConfiguration`（标记配置类）+ `@EnableAutoConfiguration`（开启自动装配）+ `@ComponentScan`（扫描主类所在包及子包）。
**run() 流程**：
1.  创建 SpringApplication 实例，识别应用类型（Servlet Web 或 WebFlux），准备监听器。
2.  准备运行环境 Environment。
3.  创建 ApplicationContext。
4.  prepareContext：加载主类作为配置源。
5.  refreshContext：刷新上下文，触发 Bean 实例化和自动装配。
6.  若引入 Web 依赖，创建并启动内嵌 Tomcat，完成端口监听。
7.  触发启动完成事件，调用 ApplicationRunner/CommandLineRunner。
**默认包扫描路径**是主类所在包及子包。

### 6.3 如何自定义一个 SpringBoot Starter？
**口述回答**：
官方建议第三方命名 `xxx-spring-boot-starter`。步骤：
1.  创建 Maven 项目，依赖 `spring-boot-autoconfigure` 和 `spring-boot-starter`。
2.  写配置属性类，用 `@ConfigurationProperties(prefix="xxx")` 绑定配置。
3.  写服务类提供业务逻辑。
4.  写自动配置类，`@Configuration` + `@EnableConfigurationProperties`，用 `@Bean` + `@ConditionalOnMissingBean` 注册服务 Bean。
5.  在 `src/main/resources/META-INF` 下创建注册文件：2.7 前是 `spring.factories`，2.7+ 用 `AutoConfiguration.imports` 文件每行一个类名。
6.  `mvn install` 打包。
7.  其他项目引入依赖，在 application.yml 配置属性即可注入使用。

### 6.4 Spring Boot 和 Spring、SpringMVC 的区别？
**口述回答**：
-   **Spring** 是完整的应用开发框架，提供 IoC、AOP 等模块。
-   **Spring Boot** 不是独立框架，而是基于 Spring 的脚手架，目标是简化开发和部署。Spring 需手动管理 jar 版本易冲突，Spring Boot 通过 starter 机制把相关依赖打包好且版本兼容（如 `spring-boot-starter-web` 包含 Spring MVC、Tomcat 等）。
-   **SpringMVC** 是 Spring 的 Web 模块处理 HTTP 请求，Spring Boot 通过自动装配省去手动配置 DispatcherServlet、ViewResolver 等繁琐步骤，还内置 Tomcat，打成可执行 jar 用 `java -jar` 直接运行。**约定大于配置**是 Boot 核心理念。

### 6.5 SpringBoot 开启事务怎么做？
**口述回答**：
Spring Boot 通过 `TransactionAutoConfiguration` 已自动启用事务管理，无需手动加 `@EnableTransactionManagement`。事务依赖通常通过 `spring-boot-starter-jdbc` 或 `spring-boot-starter-data-jpa` 引入（会带入 PlatformTransactionManager）。使用时只需在 Service 方法上加 `@Transactional` 注解，方法成功执行自动提交，抛 RuntimeException 或 Error 自动回滚。注意：默认不回滚受检异常（如 IOException），需 `@Transactional(rollbackFor = Exception.class)` 显式声明；同类内部 this 调用会绕过代理导致失效；非 public 方法上也无效。

---

## 7. Spring Cloud 微服务

### 7.1 Spring Cloud 和 Spring Boot 的关系？
**口述回答**：
-   **Spring Boot** 专注快速开发单个微服务个体，提供自动配置、starter、内嵌容器，约定大于配置。
-   **Spring Cloud** 是基于 Spring Boot 的微服务全家桶，关注全局服务治理，把分布式系统基础设施做了拿来即用的封装。
-   **关系**：Spring Boot 可以独立使用开发项目，但 Spring Cloud 离不开 Spring Boot，属于依赖关系。Spring Cloud 提供服务注册发现、配置管理、负载均衡、熔断限流、网关、链路追踪等。常用 Spring Cloud Alibaba 套件：Nacos（注册 + 配置）、OpenFeign/Dubbo（通信）、Sentinel（熔断限流）、Gateway（网关）、Seata（分布式事务）。

### 7.2 什么是服务熔断和服务降级？
**口述回答**：
-   **服务熔断**是链路保护机制，类似保险丝。当某个服务调用频繁超时或失败达到阈值，熔断器打开，直接短路掉对该服务的请求返回 mock 值，防止故障扩散引发雪崩。Hystrix 默认：10 秒滚动窗口内请求数达 20 次且错误率超 50% 就熔断，一段时间后进入半开状态试探恢复。
-   **服务降级**是主动策略，当服务器压力剧增时，有策略地不处理或简化处理一些非核心服务，释放资源保核心业务。
-   **区别**：熔断是被动的故障保护，降级是主动的资源调度；熔断是整体级别，降级可方法级。两者常配合使用。

### 7.3 负载均衡有哪些算法？
**口述回答**：
-   **简单轮询**：按顺序分发，不关心服务器状态。
-   **加权轮询**：按性能设权重，高性能处理更多。
-   **简单随机**：随机分发，请求越多越平均。
-   **加权随机**：按权重随机分发。
-   **一致性哈希**：根据客户端 IP 或请求参数哈希取模映射，保证同一客户端每次命中同一台服务器，适合有状态场景。
-   **最小活跃数**：分发给当前正在处理请求数最少的服务器。
Spring Cloud Alibaba 早期用 Ribbon，新版本（2020.x+）切换为 Spring Cloud LoadBalancer，并可结合 Nacos 的权重和健康状态元数据实现加权负载均衡。

### 7.4 Eureka 和 Zookeeper 做注册中心的区别？
**口述回答**：
核心是 **CAP 取舍不同**。
-   **Zookeeper 保证 CP**（一致性 + 分区容错）：当 master 节点网络故障与其他节点失联，剩余节点重新选举 leader，选举期间整个集群不可用（30~120 秒），在云部署环境下因网络问题丢失 master 概率较高，漫长的选举不可容忍。
-   **Eureka 保证 AP**（可用性 + 分区容错）：各节点平等，节点间复制注册信息，某节点故障不影响其他节点提供服务，即使返回几分钟前的注册信息也能接受，因为服务注册对可用性要求高于一致性。Eureka 还有自我保护机制：短时间内丢失过多心跳时不剔除服务，避免网络抖动误删。微服务场景更适合 AP。

---
*文档生成时间：2026-08-07 | 资料来源：小林 coding、二哥的 Java 进阶之路、JavaGuide*
