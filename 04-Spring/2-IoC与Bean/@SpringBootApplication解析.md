---
tags:
  - spring
created: 2026-06-28
updated: 2026-10-13
---
# @SpringBootApplication 深度解析

## 组合注解本质

`@SpringBootApplication` 是一个**复合注解**，等价于同时使用以下三个注解：

```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
    @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
```

------

## 三大核心注解解析

### 1. @SpringBootConfiguration

```java
@Configuration          // 本质还是 @Configuration
@Indexed
public @interface SpringBootConfiguration { ... }
```

- 标记当前类为**配置类**，等同于 Spring 的 XML 配置文件
- 内部使用 `@Configuration`，底层由 `ConfigurationClassPostProcessor`（`BeanFactoryPostProcessor`）处理
- 被标注的类会通过 **CGLIB 动态代理**增强，保证 `@Bean` 方法的单例语义

> [!tip] 为什么需要代理？——用"找助理"打个比方
>
> 你容易想不通的是："三级缓存不已经保证单例了吗？代理不是多此一举？"
>
> 咱打个比方。**三级缓存 = 你的助理，只有一个办公电脑，谁要电脑都找助理拿。**
>
> 现在 A 部门要电脑，B 部门也要电脑。正常流程：
>
> ```
> 找助理 → 助理给一台 → 下次再有人要，助理说"就这一台，拿走"
> ```
>
> 单例稳稳的，对吧？
>
> 但问题是——A 部门的工位长这样：
>
> ```java
> @Bean
> public A a() {
>     return new A(b());  // A 内部自己又整了一台电脑
> }
> ```
>
> 看到了吗？A 自己写了 `new A(b())`，**它根本没找助理**。它自己悄悄地又弄了一台电脑出来。助理完全不知道这回事。
>
> 所以结果就是：助理手里有一台（三级缓存里的）、A 自己又造了一台——**两台电脑，单例破了**。
>
> **CGLIB 代理干的事，就是给 A 的工位装个摄像头，拦截它"自己造电脑"的企图：**
>
> ```
> A 想自己造电脑
>   └── 被代理拦截 → "等等，你先别自己造，我帮你问问助理"
>         └── 找助理要 → 助理给缓存的那台 → A 拿到，不再自己造
> ```
>
> 所以一句话：**三级缓存是助理，代理是"把想绕开助理的人硬拽回助理面前"的保安。** 没有保安，有助理也没用——因为有人根本不走正门。
>
> 反过来，`@Configuration(proxyBeanMethods = false)` 就是**关掉这个保安**。好处是启动快一点（不用装摄像头），代价是 A 自己造电脑的话，没人管得了。

------

### 2. @EnableAutoConfiguration（核心中的核心）

```java
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration { ... }
```

**工作流程：**

```
启动
  └── @Import(AutoConfigurationImportSelector.class)
        └── selectImports() 方法被调用
              └── getCandidateConfigurations()
                    └── 读取 META-INF/spring/
                        org.springframework.boot.autoconfigure.AutoConfiguration.imports
                          (Spring Boot 2.7+ 新路径，之前是 spring.factories)
                              └── 加载所有候选配置类（~150+ 个）
                                    └── 经过 @Conditional 条件过滤
                                          └── 最终生效的配置类注册到容器
```

**执行步骤详解：**

**① `@Import(AutoConfigurationImportSelector.class)`** — `@Import` 是 Spring 框架层面的注解，作用是把指定类注册为容器内的 BeanDefinition。这里的 `AutoConfigurationImportSelector` 实现了 `DeferredImportSelector` 接口——「延迟」的含义是它会在所有普通配置类（用户自己的 `@Configuration`、`@ComponentScan`）处理**完毕之后**才执行，从而保证**用户自定义 Bean 优先**，自动配置不会覆盖用户的显式定义。

**② `selectImports()` → `getCandidateConfigurations()`** — `DeferredImportSelector` 的 `selectImports()` 是入口方法，内部委托 `getCandidateConfigurations()` 加载候选配置类的全限定名列表，返回 `String[]`，每个元素对应一个自动配置类的全类名。

**③ 读取 `AutoConfiguration.imports` 文件** — Spring Boot 2.7+ 的新路径，取代了旧的 `spring.factories`。原因：`spring.factories` 是 Spring 通用 SPI 机制，所有自动配置类堆在一个文件里，可读性差、多模块合并易冲突；新路径按模块拆分，单一职责、更易维护。文件内容是一行一个全类名。

**④ 加载所有候选配置类（~150+ 个）** — 注意此时只是加载全类名字符串，**并未实例化**。

**⑤ `@Conditional` 条件过滤** — 每个自动配置类上标注了 `@Conditional` 系列注解，由 `ConditionEvaluator` 逐条判断。过滤是**条件组合**：一个配置类可能同时标注多个 `@Conditional`，必须全部满足才生效。

**⑥ 最终生效的配置类注册到容器** — 通过过滤的配置类，其 `@Bean` 方法被调用，BeanDefinition 注册进容器，自动装配完成。

**面试口述版：**

> `@EnableAutoConfiguration` 的核心机制是 `@Import(AutoConfigurationImportSelector.class)`。这个 ImportSelector 实现了 `DeferredImportSelector`——延迟执行，等用户自定义的 `@Configuration` 和 `@ComponentScan` 处理完之后才跑，保证用户 Bean 优先。执行时 `selectImports()` 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，拿到所有候选自动配置类的全类名，再逐个检查 `@Conditional` 条件——`@ConditionalOnClass` 看 classpath 有没有对应依赖类，`@ConditionalOnMissingBean` 看容器里有没有用户自定义的同类型 Bean——只有全部条件满足的配置类才注册进容器。

**关键：@Conditional 条件注解体系**

| 注解                           | 含义                         |
| ------------------------------ | ---------------------------- |
| `@ConditionalOnClass`          | Classpath 存在指定类才生效   |
| `@ConditionalOnMissingBean`    | 容器中不存在指定 Bean 才生效 |
| `@ConditionalOnProperty`       | 配置属性满足条件才生效       |
| `@ConditionalOnWebApplication` | 是 Web 应用才生效            |

这就是为何引入 `spring-boot-starter-web` 就能自动配置 Tomcat、DispatcherServlet。

**@AutoConfigurationPackage：**

```java
@Import(AutoConfigurationPackages.Registrar.class)
```

将主启动类所在包路径注册为"自动配置基础包"，供 JPA 等组件扫描实体类使用。

------

### 3. @ComponentScan

```java
@ComponentScan(excludeFilters = {
    @Filter(TypeExcludeFilter.class),          // 测试用排除
    @Filter(AutoConfigurationExcludeFilter.class) // 防止自动配置类被重复扫描
})
```

- **默认扫描范围**：主启动类所在包及其所有子包
- 扫描 `@Component`、`@Service`、`@Repository`、`@Controller` 等注解的类
- 由 `ClassPathBeanDefinitionScanner` 完成实际扫描工作

#### `AutoConfigurationExcludeFilter` 到底在防什么

`@SpringBootApplication` 上的 `@ComponentScan` 默认会扫启动类所在包及其子包。如果自动配置类（`DataSourceAutoConfiguration`、`WebMvcAutoConfiguration` 这类）落在扫描范围内，它们就会被**当成普通配置类直接注册**，绕过自动配置的正常机制。这个过滤器（`TypeFilter` 接口的实现）就是用来把它们排除在扫描结果之外的。

**判断逻辑只有两个条件，同时满足才排除**：

1. 类上有 `@Configuration`
2. 类名出现在"自动配置候选列表"里

这个候选列表来自 Spring Boot 的元数据：**2.7 之前**在 `META-INF/spring.factories` 的 `EnableAutoConfiguration` 键下，**2.7 之后**改为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

**关键点：它不会排除你自己写的 `@Configuration` 类**——那些类不在候选列表里，照常扫描。

**为什么必须有它**，两个原因：

第一，自动配置类上挂满了 `@ConditionalOnMissingBean`、`@ConditionalOnClass` 这类条件注解，它们依赖 `AutoConfigurationImportSelector` 按固定顺序统一加载。如果被组件扫描提前注册，**条件判断的时机和顺序就乱了**——可能出现用户定义的 Bean 被覆盖，或者容器里出现重复 Bean。

第二，自动配置类应该由框架"按需导入"，而不是因为恰好位于某个扫描路径下就被强行生效。

**什么时候会失效**：只有当你手动写了 `@ComponentScan` 且扫描范围覆盖 `org.springframework.boot.autoconfigure` 包时，这层保护才失效：

```java
// ❌ 错误示范：把自动配置包也扫进来了
@SpringBootApplication
@ComponentScan(basePackages = {"com.example", "org.springframework.boot.autoconfigure"})
public class App { }
```

**失效后的两种典型报错**：

| 情况 | 异常 |
|---|---|
| 两个 Bean 同名（都叫 `dataSource`） | `BeanDefinitionOverrideException`（Spring Boot 2.1+ 默认禁止 Bean 覆盖） |
| 名字不同，但类型相同 | `NoUniqueBeanDefinitionException`（按类型注入时无法确定选哪个） |

**排查与解决**：把扫描范围缩回自己的业务包，让自动配置类只通过 `AutoConfigurationImportSelector` 按条件导入；或者开启 debug 看 `ConditionEvaluationReport`，确认自动配置类是被"扫描"进来的还是被"导入"进来的。

> 顺带区分：`@SpringBootApplication` 里还有另一个 `TypeExcludeFilter`，它允许测试场景（`TestTypeExcludeFilter`）排除部分组件，两个过滤器配合控制最终扫描结果。

------

## 完整启动流程

```
main() → SpringApplication.run()
    │
    ├── 1. 创建 ApplicationContext
    │       (AnnotationConfigServletWebServerApplicationContext)
    │
    ├── 2. prepareContext()
    │       └── 注册主类为 BeanDefinition
    │
    ├── 3. refreshContext() → AbstractApplicationContext.refresh()
    │       │
    │       ├── invokeBeanFactoryPostProcessors()
    │       │     └── ConfigurationClassPostProcessor 处理
    │       │           ├── 解析 @SpringBootConfiguration
    │       │           ├── 触发 @ComponentScan → 扫描并注册 BeanDefinition
    │       │           └── 触发 @EnableAutoConfiguration
    │       │                 └── AutoConfigurationImportSelector
    │       │                       └── 加载、过滤、注册自动配置类
    │       │
    │       ├── registerBeanPostProcessors()
    │       │     └── 注册 Bean 生命周期处理器
    │       │
    │       └── onRefresh()
    │             └── 创建并启动内嵌 Web 服务器（Tomcat）
    │
    └── 4. 发布 ApplicationStartedEvent，运行 Runner
```

**执行步骤详解：**

**① 创建 ApplicationContext** — `SpringApplication.run()` 启动时先判断应用类型（Servlet / Reactive / 非 Web），据此创建对应的上下文实现。因为引入了 web starter，这里创建的是 `AnnotationConfigServletWebServerApplicationContext`——一个"基于注解配置 + 内嵌 Servlet Web 服务器"的上下文。

**② `prepareContext()`** — 上下文预准备阶段。核心动作是把**主启动类本身**注册为 BeanDefinition。主启动类是一个 `@SpringBootConfiguration` 配置类，必须先注册进去，后续 `ConfigurationClassPostProcessor` 才能解析它。同时这里还会把 `springApplicationArguments`、`springBootBanner` 等作为 Bean 注册，并加载 `initializers`、`listeners`。

**③ `refreshContext()` → `AbstractApplicationContext.refresh()`** — 整个启动的核心，调用的是 Spring 框架标准的容器刷新方法（模板方法模式，`ApplicationContext` 统一入口）。刷新失败会自动销毁已创建的 Bean 并关闭上下文。三个关键子步骤：

- **`invokeBeanFactoryPostProcessors()`**：回调所有 `BeanFactoryPostProcessor`。其中 `ConfigurationClassPostProcessor` 是真正干活的——它解析主配置类，依次触发 `@SpringBootConfiguration`（识别配置类）、`@ComponentScan`（扫描并注册业务 Bean）、`@EnableAutoConfiguration`（加载并过滤自动配置类）。
- **`registerBeanPostProcessors()`**：注册所有 `BeanPostProcessor`——这是 Bean 实例化之后的生命周期钩子，`@Autowired` 注入、`@Value` 解析、AOP 代理等都是在这里注册的处理器在后续生效。
- **`onRefresh()`**：模板方法，留给子类扩展。`ServletWebServerApplicationContext` 在这里创建并启动**内嵌 Tomcat**，同时初始化 `DispatcherServlet` 等 Web 组件。

> [!note] refresh() 里省略的步骤
> 完整 refresh() 还有 `finishBeanFactoryInitialization()`（实例化所有非懒加载单例 Bean）、`finishRefresh()`（发布 `ContextRefreshedEvent`）等。`invokeBeanFactoryPostProcessors` 在前、单例实例化在后，是"先有定义、再实例化"的分层设计。

**④ 发布 `ApplicationStartedEvent`，运行 Runner** — `refresh()` 完成后，`SpringApplication` 继续收尾：广播 `ApplicationStartedEvent`，然后运行容器里的 `ApplicationRunner` / `CommandLineRunner`（常用于启动后执行一次性任务，如预热缓存）。

**面试口述版：**

> 启动流程的核心是 `SpringApplication.run()` 里的 `refresh()`。run 会先根据应用类型创建对应的 ApplicationContext——比如 Web 项目就是 `AnnotationConfigServletWebServerApplicationContext`。然后 `prepareContext` 把主启动类注册成配置类 BeanDefinition。接着调用 Spring 标准的 `refresh()` 刷新容器，这是整个启动的关键：里面 `invokeBeanFactoryPostProcessors` 阶段，`ConfigurationClassPostProcessor` 会解析主配置类，触发 `@ComponentScan` 扫描业务 Bean，触发 `@EnableAutoConfiguration` 加载自动配置；`registerBeanPostProcessors` 注册 BeanPostProcessor，为后面的依赖注入和 AOP 做准备；`onRefresh` 是模板方法，内嵌 Tomcat 就是在这里启动的。refresh 完成后再发布 `ApplicationStartedEvent`，运行 Runner 收尾。

------

## 关键设计思想

**约定优于配置（Convention over Configuration）**

自动配置类遵循严格的条件化原则：

- 用户**自定义的 Bean 优先**（`@ConditionalOnMissingBean`）
- 只在相关依赖**存在时才配置**（`@ConditionalOnClass`）
- 所有默认值都可通过 `application.properties` **覆盖**

```java
// 典型自动配置类示例
@AutoConfiguration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration { ... }
```

这套机制让 Spring Boot 实现了"引入依赖即可用"的开发体验，同时保持了完全的可定制性。