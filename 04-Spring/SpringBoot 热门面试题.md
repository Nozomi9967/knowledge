---
tags:
  - springboot
  - 面试题
  - java
created: 2026-08-24
updated: 2026-08-24
背诵状态: 背诵中
背诵开始: 2026-08-24
---

# SpringBoot 热门面试题

> **考查范围**：SpringBoot 基础定位 · 启动流程与 @SpringBootApplication · 自动装配原理 · 配置管理 · Web 自动配置与内嵌容器 · 生产实践与扩展
> **题型**：全部为简答题，面试导向
> **建议用时**：70 ~ 90 分钟
> **说明**：先自己作答，答完发给 Claude 批卷评估。每题后的「追问」是面试官可能深挖的点，作答时一并思考。批卷按 CLAUDE.md 面试答题原则执行，落盘「批改分析区 + 参考答案」两区，参考答案为口述化。

---

## 复习速查表（按主题 → 笔记跳转）

- SpringBoot 定位与优势 → [[spring八股]] · [[Spring-interview-guide]]
- @SpringBootApplication 组合注解 → [[@SpringBootApplication解析]]
- 自动装配原理 → [[@SpringBootApplication解析]] · [[Spring-interview-guide]]
- 自定义 Starter → [[自定义SpringBootStarter详解]]
- 启动流程 → [[@SpringBootApplication解析]] · [[Spring-interview-guide]]
- 事务管理 → [[spring八股]] · [[Spring-interview-guide]]
- MVC 自动配置 → [[WebMvcAutoConfiguration]] · [[WebMvcConfigurer]]
- HandlerAdapter → [[HandlerAdapter]] · [[DispatcherServlet详解]]
- IoC 与 Bean → [[Spring ioc]] · [[spring八股]]

---

## 一、SpringBoot 基础与定位（3 题）

### 1. 为什么使用 Spring Boot？相比传统 Spring 有什么优势？【高频】

SpringBoot是一个Spring框架的脚手架，在传统Spring开发中，我们需要自己管理依赖，管理复杂的配置，而SpringBoot遵循约定大于配置，提供了很多默认的约定，很多情况下无需我们手动配置，并且还帮我们管理依赖，自动注入依赖

> **知识点跳转：** [[spring八股]] · [[Spring-interview-guide]]

**追问：** "自动配置"和"约定优于配置"具体解决的是什么痛点？如果没有 Boot，Spring 项目搭建 Web 应用要手动做哪些事？

解决的是繁杂的依赖关系以及大量配置工作这个痛点

---

**批改分析区：**

**评分：7/10**

- ✅ 主问核心答对：SpringBoot 是脚手架、传统 Spring 要自己管理依赖和配置、约定优于配置、默认约定省去手动配置
- ⚠️ "自动注入依赖"表述不准：SpringBoot 帮的是 **starter 打包依赖 + 统一版本**，不是"自动注入依赖"（那是 IoC/DI 的 @Autowired，属于 Spring 本身，不是 Boot 独有）
- ⚠️ 追问只答了"痛点"没答具体场景：**"没有 Boot 要手动做哪些事"没展开**（手动引依赖、注册 DispatcherServlet、配 ViewResolver/HandlerMapping/数据源等）

**参考答案 ✅**

Spring Boot 是 Spring 的脚手架，它的核心价值在于**大幅降低 Spring 应用开发的复杂度与成本**。传统 Spring 项目最大的痛点是两件事：一是**依赖管理**——做 Web 开发得手动引入 spring-webmvc、spring-context、tomcat、jackson 一长串依赖，还要自己处理版本冲突；二是**配置**——要手动注册 DispatcherServlet、配置视图解析器、HandlerMapping、数据源。Spring Boot 用 **starter 机制**把相关依赖打包成一个启动器、版本由 Boot 统一管理，再用**自动配置 + 约定优于配置**让你引入依赖就能用，大部分配置不用手写。直接收益就是开发效率提升、搭建变快、配置大幅减少。

> **追问参考答：** 没有 Boot 搭 Web 应用要手动做：① 手动引 spring-webmvc、servlet-api、tomcat、jackson 等依赖并解决版本冲突；② 在 web.xml 或代码里注册 DispatcherServlet 及映射路径；③ 手动配置 HandlerMapping、ViewResolver、消息转换器等 MVC 组件；④ 手动配数据源和事务管理器；⑤ 写一堆 XML 或 Java 配置类。Boot 用 starter + 自动配置把这些全解决。

### 2. Spring、Spring Boot、Spring MVC 三者的关系和区别？【高频】

Spring是一个Java后端开发框架，是一整个生态
SpringBoot是一个用于快速开发Spring应用的脚手架、启动器
SpringMVC是web应用的前端入口

> **知识点跳转：** [[Spring-interview-guide]] · [[spring八股]]

**追问：** 为什么说 Spring Boot 不是独立框架而是"脚手架"？它基于 Spring 做了哪几层封装？

因为他本身并不是新东西，而且对Spring框架的各个组件、应用进行了封装，添加了快速启动的入口

---

**批改分析区：**

**评分：6.5/10**

- ✅ 三者定位基本对：Spring 是生态框架、SpringBoot 是脚手架/启动器、SpringMVC 是 Web 层
- ⚠️ "SpringMVC 是 web 应用的前端入口"表述含糊：准确说是 Spring 的 **Web 模块 / MVC 表现层框架**（基于 Servlet 处理 HTTP 请求），"前端入口"容易被误会成前端页面
- ⚠️ 追问答了"封装 + 快速启动入口"但**没拆解"哪几层封装"**：应该是依赖层（starter 打包）、配置层（自动配置）、运行层（内嵌容器）、工具层（actuator/devtools）

**参考答案 ✅**

三者关系可以这样理解：**Spring 是一个 Java 后端开发框架，Spring MVC 是其中的 Web 模块，Spring Boot 是基于 Spring 的快速开发脚手架。** Spring 的核心能力包括 IoC（控制反转）、AOP（面向切面）和事务管理——IoC 让对象的创建与依赖关系由容器统一管理，AOP 将日志、事务等横切逻辑从业务代码中抽离出来。Spring MVC 基于 Servlet 构建，由 DispatcherServlet 作为前端控制器统一接收 HTTP 请求，再分发给对应的 Controller 处理。Spring Boot 则通过 **starter 机制**统一管理依赖版本，通过**自动配置 + 约定优于配置**大幅减少手动配置，并内置 Tomcat 等 Web 容器，使应用可直接打包为可执行 jar 运行。

> **追问参考答：** Spring Boot 对 Spring 做了3层封装——① 依赖层：starter 把功能相关的依赖打包并统一版本；② 配置层：自动配置 + 约定优于配置，省掉大量手动 XML/Java 配置；③ 运行层：内嵌 Tomcat/Jetty/Undertow，`java -jar` 直接跑；

### 3. 怎么理解 Spring Boot 的"约定优于配置"？【中频】

> **知识点跳转：** [[spring八股]]

**追问：** 约定的默认值在哪被打破？"约定"失效需要显式配置的典型场景有哪些？

---

**批改分析区：**

**评分：0/10**

- ❌ **空着**——中频题，至少答出"约定优于配置 = 框架给默认值、遵守约定就不用配、打破默认才显式配"，并举例（端口、包扫描、数据源）

**参考答案 ✅**

约定优于配置的核心思想是：**框架提供一套合理的默认约定，你遵守约定就几乎不用写配置，只有在需要打破默认时才显式配置。** 它解决的是传统 Spring 配置繁琐的问题——大部分情况什么都不用配，框架按约定自动处理。

Spring Boot 里体现得很具体：**项目结构**有默认约定（主类放包最外层、配置放 resources 目录）、**默认端口** 8080、**默认扫描**主类所在包及子包、**引入 starter 依赖即自动配置生效**。

> **追问参考答：** 约定失效需要显式配置的典型场景——① 端口不是 8080 时配 `server.port`；② 类不在主类所在包及子包下时，@ComponentScan 扫不到，要显式 `scanBasePackages`；③ 连真实数据库要配数据源 url/username/password（默认是 HikariCP 连本地）；④ 要自定义拦截器/消息转换器时，通过实现 WebMvcConfigurer 扩展而不重写自动配置；⑤ 多环境用 `spring.profiles.active` 激活指定 profile。

---

## 二、@SpringBootApplication 与启动流程（4 题）

### 4. @SpringBootApplication 由哪几个注解组成？各自作用是什么？【高频】

@SpringBootConfiguration，标记配置类
@EnableAutoConfiguraion，实现自动装配
@ComponentScan，扫描启动类所在包及其子包下的类

> **知识点跳转：** [[@SpringBootApplication解析]]

**追问：** 为什么不直接在启动类上写那三个注解？组合注解相比分开写有什么额外作用？

---

**批改分析区：**

**评分：7/10**

- ✅ 三个注解答全：@SpringBootConfiguration 标记配置类、@EnableAutoConfiguration 开自动装配、@ComponentScan 扫主类所在包及子包
- ❌ 拼写错误：**@EnableAutoConfiguraion → @EnableAutoConfiguration**（拼错专有名词扣分）
- ⚠️ @SpringBootConfiguration 只答"标记配置类"不够深：它本质是 **@Configuration 的衍生**，被标注的类会被 **CGLIB 代理**保证 @Bean 方法单例语义
- ⚠️ 追问空着：组合注解的意义没答——**一个 @SpringBootApplication 顶三个注解**，统一启动入口、防止遗漏，且 Spring Boot 靠它定位主类

**参考答案 ✅**

@SpringBootApplication 是一个**组合注解**，等价于同时写三个注解。① @SpringBootConfiguration，本质就是 @Configuration，标记当前类是一个配置类，而且这个类会被 **CGLIB 动态代理增强**，保证里面的 @Bean 方法返回的都是单例——这是"配置类里 @Bean 方法互相调用时拿到同一个实例"的关键。② @EnableAutoConfiguration，这是最核心的，负责开启**自动装配**，它通过 @Import 引入 AutoConfigurationImportSelector，去扫描并加载类路径下所有自动配置类。③ @ComponentScan，默认扫描**主启动类所在包及其所有子包**，把 @Component、@Service、@Controller 这些组件注册进容器。

> **追问参考答：** 为什么不直接写三个注解？因为 @SpringBootApplication 把三个注解**合并成一个入口**，更简洁、防止遗漏，而且它是 Spring Boot 识别"哪个是主启动类"的**标志**——run() 方法通过它定位主类、确定包扫描起点。组合注解还有额外作用：@SpringBootApplication 里给 @ComponentScan 配了 excludeFilters，把 TypeExcludeFilter 和 AutoConfigurationExcludeFilter 排除掉，防止自动配置类被重复扫描。写三个注解就要手动配这些排除逻辑，用组合注解一键搞定。

### 5. Spring Boot 的启动流程是怎样的？SpringApplication.run() 做了什么？【高频】

先根据当前的应用类型确定上下文容器的类型，创建完ApplicationContext后，进入prepareContext阶段，在此阶段会将主启动类注册为BeanDefinition，接着执行refresh方法，这里会回调所有的BeanFactoryPostProcessor，其中ConfigurationClassPostProcessor是真正干活的，他会加载配置类，扫描并注册业务bean，加载并过滤自动配置类
接着会注册所有的BeanPostProcessor，最后执行模版方法onRefresh

> **知识点跳转：** [[@SpringBootApplication解析]] · [[Spring-interview-guide]]

**追问：** refreshContext() 这一步具体触发了什么？内嵌 Tomcat 是在哪一步启动的？

在onRefresh

---

**批改分析区：**

**评分：7.5/10**

- ✅ 启动流程答得完整：确定上下文类型 → 创建 ApplicationContext → prepareContext 注册主类 → refresh（BeanFactoryPostProcessor，其中 ConfigurationClassPostProcessor 干核心活）→ 注册 BeanPostProcessor → onRefresh
- ✅ 主回答已经覆盖了 refresh 内部细节（配置类加载、业务 Bean 扫描、自动配置类加载过滤），追问不重复扣分
- ⚠️ 追问"Tomcat 在哪一步启动"只答"onRefresh"——方向对，但可以说透一点：**onRefresh 是模板方法，Web 环境下由 ServletWebServerApplicationContext 覆写，在里面调用 createWebServer() 创建并启动内嵌 Tomcat**

**参考答案 ✅

具体：[[Spring Boot 的启动流程是怎样的？SpringApplication.run() 做了什么？【高频】]]

Spring Boot 的启动流程，围绕 SpringApplication.run() 展开。① 创建 SpringApplication 实例，根据当前依赖和 classpath 判断应用类型——是 Servlet Web、WebFlux 还是普通应用，不同类型后面创建不同的 ApplicationContext。② 准备运行环境 Environment，把 application.yml 的配置、系统参数、命令行参数都读进来。③ 创建 ApplicationContext，Web 项目是 AnnotationConfigServletWebServerApplicationContext。④ prepareContext，把主启动类注册成一个 BeanDefinition，作为配置源告诉容器"去哪里找配置"。⑤ refreshContext，这是最核心的一步——调用 AbstractApplicationContext.refresh()，解析配置类、扫描注册业务 Bean、加载并过滤自动配置类、初始化所有单例 Bean。⑥ 内嵌 Tomcat 在 onRefresh 中启动（ServletWebServerApplicationContext 覆写了 onRefresh 模板方法，调用 createWebServer() 创建并启动内嵌容器）。最后，发布启动完成事件，执行 ApplicationRunner / CommandLineRunner。

> **追问参考答：** refreshContext() 调用的是 Spring 的 refresh() 模板方法，内部依次做：invokeBeanFactoryPostProcessors（ConfigurationClassPostProcessor 在这里解析 @SpringBootApplication、触发 @ComponentScan 和 @EnableAutoConfiguration）、registerBeanPostProcessors（注册 Bean 生命周期后置处理器）、finishBeanFactoryInitialization（实例化所有单例 Bean）等。内嵌 Tomcat 在 **onRefresh()** 这一步启动——onRefresh 是 refresh() 里的一个模板钩子，ServletWebServerApplicationContext 覆写了它，在里面调用 createWebServer() 创建并启动内嵌容器。所以答"Tomcat 在 onRefresh 启动"是对的，补上 createWebServer 更完整。

### 6. 为什么主启动类要放在包的最外层？放错位置会出什么问题？【中频】

> **知识点跳转：** [[@SpringBootApplication解析]]

**追问：** @ComponentScan 的默认扫描范围是怎么确定的？自动配置类为什么不会被重复扫描？

---

**批改分析区：**

**评分：0/10**

- ❌ **空着**——中频题，核心一句：主类在最外层是因为 @ComponentScan **默认扫描主类所在包及其子包**，放子包会漏扫兄弟包组件、放外层会多扫无关包

**参考答案 ✅**

主启动类必须放在包的最外层，是因为 @ComponentScan 的**默认扫描范围就是主启动类所在的包及其所有子包**。Spring Boot 以主类为"锚点"，从这个包开始往下递归扫描所有 @Component、@Service、@Controller、@Repository。所以主类必须放在**顶层包**（业务代码的最外层包），这样它下面的所有子包都能被扫到。

如果放错位置会出两类问题：主类放**太深（子包里）**，扫描范围就只覆盖那个子包及以下，兄弟包、外层包里的 Bean 全部扫不到，启动时报 NoSuchBeanDefinition 找不到 Bean；主类放**太浅（太外层）**，扫描范围过大，可能把不相关的类也扫进来，甚至把别的模块的组件误注册，增加启动时间和不确定性。

> **追问参考答：** 默认扫描范围 = **主启动类所在包 + 所有子包**，由 @ComponentScan 的 basePackage 决定，Spring Boot 用主类所在包自动推导。自动配置类不会被重复扫描，是因为 @SpringBootApplication 里的 @ComponentScan 配了 excludeFilters，用 **AutoConfigurationExcludeFilter** 把自动配置类排除在外——自动配置类是通过 AutoConfigurationImportSelector 单独加载的，不需要组件扫描再捡一遍，排除避免重复注册。

### 7. ApplicationRunner 和 CommandLineRunner 的区别？【低频·加分】

> **知识点跳转：** [[Spring-interview-guide#6.2.1 ApplicationRunner vs CommandLineRunner + 选项参数/非选项参数]]

**追问：** 多个 Runner 的执行顺序怎么控制？它们的参数来源是什么？

---

**批改分析区：**

**评分：0/10**

- ❌ **空着**——低频加分题，知道"两者都是应用启动完成后执行的钩子，一个接原始 args、一个接封装后的 ApplicationArguments"即可

**参考答案 ✅**

ApplicationRunner 和 CommandLineRunner 都是 Spring Boot 在**应用启动完成、容器刷新之后**执行的回调接口，用来做启动后的初始化工作（预热缓存、初始化数据、打印启动信息等）。区别在**接收参数的形态**：CommandLineRunner 的 run() 接收的是 **String[] args**，就是 main 方法传进来的原始命令行参数，原样给你；ApplicationRunner 的 run() 接收的是 **ApplicationArguments** 对象，是 Spring Boot 对原始参数做了解析封装的结果，可以区分"选项参数（--xxx=yyy）"和"非选项参数"，还提供了 getOptionValues()、getNonOptionArgs() 等便捷方法。

> **追问参考答：** 多个 Runner 的执行顺序用 **@Order 注解**控制，值越小越先执行（也可以用 Ordered 接口）。它们的参数来源都是 **main 方法传入的命令行参数**，也就是 `SpringApplication.run(Class, args)` 里那个 args——CommandLineRunner 直接收 String[]，ApplicationRunner 收的是封装好的 ApplicationArguments。

---

## 三、自动装配原理（5 题）

### 8. Spring Boot 的自动装配原理是什么？【高频】

核心是@EnableAutoConfiguration注解
它的子注解@Import(AutoConfigurationImportSelector.class)
在执行selectImports()方法时，会解析META-INF/org.springframe.boot.autoconfiguraion.AutoConfiguration.imports文件，加载里面候选自动配置类的全类名，并通过@Conditional注解进行筛选，满足条件的才会被装配

> **知识点跳转：** [[Spring-interview-guide]] · [[@SpringBootApplication解析]]

**追问：** 自动配置类是怎么被"发现"的？从 @EnableAutoConfiguration 到真正注册 Bean 的完整链路？

> 推荐回答

“`@SpringBootApplication` 是复合注解，核心是 `@EnableAutoConfiguration`，它通过 `@Import` 导入了 `AutoConfigurationImportSelector`。这个类实现的是 `DeferredImportSelector`，所以它会在容器 refresh、用户配置类全部解析完之后才被回调，执行 `getAutoConfigurationEntry()`，读取所有 jar 包里 `META-INF/spring/...AutoConfiguration.imports` 文件——2.7 之前是 `spring.factories`——拿到候选配置类的全类名，先去重、应用 exclude，再经过 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 这些条件注解过滤，满足条件的才注册成 BeanDefinition，里面的 `@Bean` 最终实例化进容器。也正因为 Deferred 的机制，自动配置永远排在用户配置后面，我们的自定义 Bean 才能覆盖默认实现。”

### 9. AutoConfigurationImportSelector 在自动装配中扮演什么角色？【中频】

> **知识点跳转：** [[spring八股]] · [[@SpringBootApplication解析]]

**追问：** selectImports() 返回的是什么？这些配置类怎么被过滤和排序？

>推荐回答

AutoConfigurationImportSelector 是自动装配的“选类人”：它不注册任何 Bean，只负责把一百多个候选配置类加载出来，经过去重、排除、过滤、排序后，把全类名清单交回容器。

- 它实现了 `DeferredImportSelector`，执行时机推迟到用户配置类全部解析完之后。这是自定义 Bean 能覆盖默认配置的前提。
- 它还实现了 `EnvironmentAware`、`BeanFactoryAware`、`BeanClassLoaderAware` 等感知接口，所以它读得到环境属性、查得到容器里已有的 Bean、拿得到类加载器——这就是它“凭什么做条件过滤”的底气。


> **追问参考答：** selectImports() 返回的是一个 `String[]`，里面是**经过筛选后真正要注册的自动配置类全类名**——它不是把 imports 文件里的候选类原样照搬，中间过了三层关卡。
>
> ① **排除**：`spring.autoconfigure.exclude` 配置项、`@SpringBootApplication(exclude = ...)` 里排除的类，直接踢掉。② **AutoConfigurationImportFilter 粗过滤**：最典型的就是 `OnClassCondition`——看 classpath 上有没有对应的类，比如 DataSource 自动配置要求 classpath 上有 DataSource 类，没有的直接淘汰，根本不用等到实例化阶段。③ **@Conditional 条件注解逐配置类评估**：`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 这些在配置类被解析成 BeanDefinition 时逐个评估，不满足条件的整个配置类就不注册；`@ConditionalOnMissingBean` 也是在这里判断"容器里已经存在用户自定义的 Bean，那这个自动配置就跳过"——这就是给使用方留覆盖口子的地方。
>
> 排序靠三个注解：`@AutoConfigureBefore`、`@AutoConfigureAfter` 声明依赖关系，`@AutoConfigureOrder` 定数字（数字越小越靠前，默认最低优先级）。这些 before/after 关系最后由 AutoConfigurationImportSelector 内部的 **AutoConfigurationSorter** 做拓扑排序，保证有依赖的配置类按正确顺序装配。

### 10. @Conditional 条件注解有哪些？分别在什么场景生效？【中频】

Spring Boot 自动配置里的条件注解有很多，最常用的：@ConditionalOnClass 和 @ConditionalOnMissingClass 判断 classpath 上有没有某个类，@ConditionalOnBean 和 @ConditionalOnMissingBean 判断容器里有没有某个 Bean，@ConditionalOnProperty 判断配置项有没有指定值，@ConditionalOnResource 判断资源文件是否存在，@ConditionalOnWebApplication 和 @ConditionalOnNotWebApplication 判断当前是不是 Web 应用。它们全部用在自动配置类上，控制"这个配置类在什么条件下才生效"——比如 JdbcTemplate 的自动配置类上有 @ConditionalOnClass(DataSource.class) 和 @ConditionalOnMissingBean(JdbcTemplate.class)，意思是 classpath 上有数据源才考虑装，而且容器里没有用户自定义的 JdbcTemplate 我才给默认的。

> **知识点跳转：** [[@SpringBootApplication解析]]

**追问：** @ConditionalOnMissingBean 和 @ConditionalOnClass 的区别？为什么说 @ConditionalOnMissingBean 是"给使用方留覆盖口子"？

> **追问参考答：** 两者评估的东西不一样。@ConditionalOnClass 看的是 **classpath**——类路径上有没有某个类，比如自动配置类上写着 `@ConditionalOnClass(RedisTemplate.class)`，意思是"classpath 上有这个类才装配"。它管的是"这个环境支不支持装"，不关心容器里有没有 Bean。@ConditionalOnMissingBean 看的是**容器里有没有某个 Bean**——比如 `@ConditionalOnMissingBean(RedisTemplate.class)`，意思是"容器里还没有用户自定义的 RedisTemplate，我就来配一个默认的"。
>
> 为什么说 @ConditionalOnMissingBean 是留覆盖口子？因为**用户的自定义 Bean 优先级永远高于自动配置**：你只要在项目里自己定义一个 RedisTemplate 的 @Bean，容器里已经有这个类型的 Bean 了，自动配置类上的 `@ConditionalOnMissingBean` 评估不通过，整个自动配置就不生效，你那个自定义 Bean 直接被采用——不用改框架、不用排除自动配置，声明一个 Bean 就完成了覆盖。这就是"口子"的含义：框架留了一个空位，使用方自己塞进去就顶掉默认实现。反过来，@ConditionalOnClass 没有这个能力，它只判断环境不判断人，classpath 上类在就装，跟你自定义了没有半点关系。搭配使用是标配：先用 @ConditionalOnClass 判断环境里有没有依赖，再用 @ConditionalOnMissingBean 判断要不要给默认实现。

### 11. 怎么自定义一个 SpringBoot Starter？核心步骤是什么？【中频】

自定义一个 Starter 分七步：

1. **建 Maven 项目**，命名规则是 `xxx-spring-boot-starter`，比如 `my-redis-spring-boot-starter`。
2. **引入 `spring-boot-autoconfigure` 依赖**，自动配置的注解都在这里面。
3. **写一个自动配置类**，用 `@Configuration` 加 `@ConditionalOnClass` 等条件注解控制生效条件，在里面用 `@Bean` 定义要给容器注册的默认组件。
4. **写属性类**，如果需要从配置文件读取参数，写一个 `@ConfigurationProperties` 前缀标注的属性类，比如 `@ConfigurationProperties(prefix = "my.redis")`。
5. **注册自动配置类**，在 `resources/META-INF/spring/` 目录下创建 `org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，把你的自动配置类全类名写进去——Spring Boot 启动时会扫描这个文件加载它。
6. **注册属性类**，用 `@EnableConfigurationProperties(MyProperties.class)` 把属性类注册进容器，让它生效。
7. **打包发布**，项目打包，其他项目引入这个 starter 依赖就能用，自动配置会自动生效。

> **知识点跳转：** [[自定义SpringBootStarter详解]]

**追问：** @ConfigurationProperties 和 @EnableConfigurationProperties 各自解决什么问题？服务类为什么不能加 @Component？

> **追问参考答：** @ConfigurationProperties 负责 **绑定**——把配置文件里的值（比如 `my.redis.host=127.0.0.1`）映射到 Java 类的字段上，标注 `prefix = "my.redis"` 就能自动把 host 填进去。但它只是标注了"这个类要绑定配置"，**不负责把类注册进容器**。@EnableConfigurationProperties 就是干这个的——它把 @ConfigurationProperties 标注的类注册成一个 Bean。所以套路是：属性类用 @ConfigurationProperties 声明绑定规则，然后在自动配置类或启动类上写 @EnableConfigurationProperties(MyProps.class)，让属性类进入容器，这样自动配置类里才能 @Autowired 注入它。
>
> 服务类不能加 @Component 有三个原因。第一，**包扫描不保证扫到**——@Component 的注册依赖 @ComponentScan 扫描，但 Starter 的类在 jar 包里，不在使用方主类的包及子包下，能不能注册完全看使用方有没有配置额外扫描路径，不可控。第二，**绕过条件注解，失去按需加载**——自动配置类上有 @ConditionalOnClass 判断环境是否支持，但 @Component 是硬注册，没有条件判断，如果 Starter 的服务类被扫到就无条件进容器，依赖没引入时 new 依赖直接启动失败。第三，**违背 @ConditionalOnMissingBean 的覆盖口子设计**——Starter 的 Bean 应该由自动配置类的 @Bean 方法统一注册，配上 @ConditionalOnMissingBean 让用户自定义能覆盖；如果直接 @Component，用户想覆盖还得手动排除，口子就堵死了。所以 Starter 里的服务类都不能加 @Component，全部由自动配置类的 @Bean 统一注册。

### 12. 自动配置类的注册文件在哪？Spring Boot 2.7 前后有什么变化？【中频】

Spring Boot 2.7 之前，自动配置类注册在 `META-INF/spring.factories` 文件里，key 是 `org.springframework.boot.autoconfigure.EnableAutoConfiguration`，value 是自动配置类的全类名列表，一行一个。2.7 之后改为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，每行一个配置类全类名，不需要 key 了。3.0 彻底废弃了 spring.factories 方式，只认 imports 文件。

> **知识点跳转：** [[自定义SpringBootStarter详解]] · [[@SpringBootApplication解析]]

**追问：** spring.factories 和 AutoConfiguration.imports 两种注册方式的本质区别？3.0 为什么彻底废弃了 spring.factories？

> **追问参考答：** 本质区别有两个。① spring.factories 是 **KV 格式**、一个文件管所有 SPI 扩展点（自动配置、故障分析、环境后处理器等都混在一起），AutoConfiguration.imports 是 **纯列表**、一个文件只管自动配置，职责单一、定位更快。② spring.factories 的 key 是字符串匹配，**编译期不可检查**，手写拼错 key 或全类名只有运行时才知道，imports 文件直接写全类名行，不需要 key，以后有条件可以工具检查。3.0 废弃 spring.factories 最直接的原因是**兼容 Spring Framework 6 的新基础设施**：Spring 6 把自动配置的加载机制重构了，新的 AutoConfigurationImportSelector 直接读取 imports 文件，不再读 spring.factories。另外 spring.factories 把多个扩展点混在同一个文件里，影响了启动性能（一个文件要解析全部 key），拆成独立文件也更快。

---

## 四、配置管理（3 题）

### 13. @ConfigurationProperties 和 @Value 的区别？【中频】

@ConfigurationProperties 是**类型安全的配置绑定**，把配置文件里一组相关的属性批量映射到一个 JavaBean 的字段上，支持松散绑定、嵌套对象、List/Map，类型在编译期就有保证。@Value 是**单个值的注入**，一个字段加一个注解，直接把某个属性的值注入进来，只做简单的字符串到目标类型转换。所以场景很清晰：一组相关配置（如数据源、Redis）用 @ConfigurationProperties 收拢成一个类；偶尔取一个零散的配置值用 @Value 就够。

> **知识点跳转：** [[自定义SpringBootStarter详解]]

**追问：** 复杂结构（list/map/嵌套对象）的配置绑定用哪个更合适？为什么 @ConfigurationProperties 支持松散绑定而 @Value 不行？

> **追问参考答：** 复杂结构用 @ConfigurationProperties 更合适——它天然支持嵌套 POJO、List、Map 的直接绑定，@Value 只能取单个标量值，想绑一个嵌套对象要么拆成一大堆 @Value、要么写很难维护的 SpEL 表达式，基本不可用。松散绑定（relaxed binding）的差异根子在绑定的方式：@ConfigurationProperties 是按"整个属性前缀 + 字段名"做**整块映射**的，它会附带定义一套属性名到字段名的转换规则——`server.port` 可以绑到 `serverPort`，`server.port` 也能写成环境变量 `SERVER_PORT`，kebab-case、驼峰、大写都能对上；@Value 是直接按属性名取值字符串，没有这层名称映射，你写 `@Value("${server.port}")` 就只能认 `server.port` 这个名字，换个写法就取不到。

### 14. Spring Boot 多环境配置怎么实现？【中频】

用 profile 机制实现。公共配置放在 `application.yml` 里，每个环境建一个 `application-{profile}.yml`，比如 `application-dev.yml`、`application-prod.yml`。启动时指定激活哪个 profile，Spring Boot 会先加载 `application.yml` 作为基础配置，再加载被激活的 `application-{profile}.yml` 做覆盖。指定方式：在 `application.yml` 里写 `spring.profiles.active=dev`，或者启动命令加 `--spring.profiles.active=prod`。

> **知识点跳转：** [[Spring-interview-guide]]

**追问：** profile 激活有哪几种方式？application.yml 和 application-{profile}.yml 的覆盖关系？

> **追问参考答：** profile 激活有四种方式：① `application.yml` 里的 `spring.profiles.active` 配置项；② 启动命令行参数 `--spring.profiles.active=prod`；③ 环境变量 `SPRING_PROFILES_ACTIVE=prod`；④ 部署平台/脚本里在启动时指定。覆盖关系是**单层覆盖**：`application.yml` 是基础配置，被激活的 `application-{profile}.yml` 只覆盖其中**同名**的配置项——profile 文件里写了什么就覆盖什么，没写的项沿用一个 `application.yml` 的值。也可以多个 profile 同时激活，后激活的 profile 文件覆盖先激活的。

### 15. 配置属性的加载优先级是怎样的？【低频·加分】

Spring Boot 的配置来源优先级从高到低大致是：**命令行参数 > Java 系统属性（-D）> 操作系统环境变量 > application-{profile}.yml > application.yml > 默认值**。后面的来源给前面让路，即高优先级覆盖低优先级，低优先级的配置项在高优先级没有定义时才会生效。其中前三者易混，对比如下：

| 来源 | 写法 | 本质 |
|------|------|------|
| Java 系统属性 | `-Dserver.port=9090` | 传给 **JVM** 的键值对 |
| 命令行参数 | `--server.port=9090` | 传给 **Spring Boot 应用**的参数（SpringApplication.run 会解析） |
| 环境变量 | `export SERVER_PORT=9090` | 操作系统层面的变量 |

> **知识点跳转：** [[Spring-interview-guide]]

**追问：** 命令行参数和配置文件同时存在时谁生效？为什么？

> **追问参考答：** **命令行参数生效**，覆盖配置文件里的同名配置。原因是 Spring Boot 启动时会把配置来源按固定顺序构建成一个 PropertySource 链，命令行参数对应的 source 排在链的**最前面**，Spring 的 PropertySource 解析规则是从前到后找，先找到就先返回——所以命令行参数优先级最高。这样设计的价值在于：配置文件是静态的、写死的，而命令行参数是运行时临时传入的，部署和运维阶段（比如临时改端口、指定 profile）不应该去改配置文件，直接加参数覆盖就行，不用动代码和配置文件。

---

## 五、Web 自动配置与内嵌容器（3 题）

### 16. 内嵌 Web 容器的原理？怎么切换 Tomcat / Jetty / Undertow？【中频】

原理：Spring Boot 把 Web 容器（Tomcat 等）作为**普通依赖**打进应用，不再单独安装容器。它通过 `ServletWebServerFactory`（比如 `TomcatServletWebServerFactory`）在应用启动时**以代码方式创建并启动**一个内嵌的 Web 服务器实例，把 DispatcherServlet 注册进去，应用打包成可执行 jar 后 `java -jar` 直接跑。切换容器：在 pom 里排除 `spring-boot-starter-tomcat`，再引入 `spring-boot-starter-jetty` 或 `spring-boot-starter-undertow`，Boot 检测到 classpath 上的容器就会自动切换。

> **知识点跳转：** [[spring八股]] · [[Spring-interview-guide]]

**追问：** 内嵌容器的生命周期由谁管理？它是什么时候启动、什么时候停止的？

> **追问参考答：** 内嵌容器的生命周期**由 Spring 应用上下文管理**，不是独立的进程。启动时机在 `AbstractApplicationContext.refresh()` 的 `onRefresh()` 模板钩子里——`ServletWebServerApplicationContext` 覆写了 `onRefresh()`，在里面调用 `createWebServer()` 创建并启动内嵌 Tomcat，所以容器是在 refresh 过程中、单例 Bean 实例化之前启动的。停止时机对应地发生在上下文 `close()` 时，同样通过钩子（`onClose()`）调用 `WebServer.stop()` 停掉容器。所以容器的启停完全跟随 Spring 应用上下文的生命周期。

### 17. WebMvcAutoConfiguration 自动配置了哪些东西？【中频】

`WebMvcAutoConfiguration` 是 Spring Boot 对 Spring MVC 的自动配置核心，它装配了：**DispatcherServlet** 及默认映射 `/`、**HandlerMapping 和 HandlerAdapter**（RequestMappingHandlerMapping、RequestMappingHandlerAdapter 等）、**静态资源处理**（`/static`、`/public`、`/resources` 等默认目录）、**ViewResolver**（InternalResourceViewResolver、ContentNegotiatingViewResolver）、**HttpMessageConverter**（默认注册 Jackson 转 JSON 等）、**类型转换器与格式化器**、**错误页机制**等一整套 MVC 基础设施。

> **知识点跳转：** [[WebMvcAutoConfiguration]] · [[WebMvcConfigurer]]

**追问：** 如果我想自定义一个拦截器或消息转换器，怎么在不破坏自动配置的前提下扩展？

> **追问参考答：** 正确做法是**实现 `WebMvcConfigurer` 接口**并注册成一个 @Configuration 的 Bean：在里面覆写 `addInterceptors()` 注册自定义拦截器、覆写 `extendMessageConverters()`（或 `configureMessageConverters()`）追加/调整消息转换器、覆写 `addResourceHandlers()` 等。Spring Boot 的自动配置会自动检测到容器里的 `WebMvcConfigurer` Bean，把它的扩展合并进默认配置，所以原有默认功能都保留。**关键雷区**：不要在配置类上加 `@EnableWebMvc`——它会把 WebMvc 推到"完全手动接管"模式，`WebMvcAutoConfiguration` 整体失效，那些默认配置就都没了。

### 18. 前后端分离时，@RestController 返回的对象是怎么变成 JSON 的？【中频】

流程是：请求进入 DispatcherServlet，HandlerAdapter 里的 `RequestMappingHandlerAdapter` 调用 Controller 方法拿到返回对象（通常是 DTO），再交给 **`HttpMessageConverter`** 处理——具体是 `RequestResponseBodyMethodProcessor` 选择合适的转换器，默认用 Jackson 的 `MappingJackson2HttpMessageConverter` 把对象**序列化成 JSON 字符串**写入响应体，并设置 `Content-Type: application/json`。因为 @RestController = @Controller + @ResponseBody，返回值直接进消息转换器，不再走视图解析。

> **知识点跳转：** [[HandlerAdapter]] · [[DispatcherServlet详解]]

**追问：** HttpMessageConverter 在哪个环节介入？Content-Type 和 Accept 如何决定用哪个转换器？

> **追问参考答：** HttpMessageConverter 在 **HandlerAdapter 处理返回值**这个环节介入——具体是 `RequestResponseBodyMethodProcessor` 在写返回值时，遍历容器里注册的所有 converter，调用 `canWrite(返回值类型, MediaType)` 找到能处理的第一个来序列化。选择依据有两个维度：**返回值的 Java 类型**（能不能转成这个类）和**媒体类型**——客户端在请求头 `Accept` 里声明它能接受什么格式（`application/json`、`application/xml` 等），服务端会结合 `Accept` 和 `Content-Type` 协商出最合适的媒体类型，再选对应 converter。比如请求 `Accept: application/xml` 且 classpath 有 JAXB，就选 XML converter；绝大多数前后端分离场景 `Accept: application/json`，就命中 Jackson 那个。

---

## 六、生产实践与扩展（3 题）

### 19. Spring Boot 怎么开启和使用事务？【高频】

Spring Boot 使用事务非常简单：**在方法或类上加 `@Transactional` 注解即可**，不需要任何额外的开启步骤——Boot 的 `TransactionAutoConfiguration` 已经自动配置好了事务管理器（DataSource 场景默认是 `DataSourceTransactionManager`）。原理是 Spring AOP：被注解的方法会被代理，代理在方法执行前开启事务、正常返回时提交、抛出异常时回滚。

> **知识点跳转：** [[spring八股]] · [[Spring-interview-guide]]

**追问：** 为什么 Spring Boot 不用手动加 @EnableTransactionManagement？@Transactional 默认回滚哪些异常？同类 this 调用为什么失效？

> **追问参考答：** 三个问题分别答。① 不用手动加 `@EnableTransactionManagement`，因为 `TransactionAutoConfiguration` 已经默认开启了事务管理，并自动注册了合适的事务管理器（有 DataSource 就配 DataSourceTransactionManager，有 JPA 就配 JpaTransactionManager），所以 @Transactional 直接就能生效。② 默认回滚规则：**只回滚 RuntimeException（及其子类）和 Error**，受检异常（Exception 的子类）默认**不回滚**——因为 Spring 认为受检异常表示"业务上可预期的失败"，不该撤销整个事务；想让受检异常也回滚必须显式写 `@Transactional(rollbackFor = Exception.class)`。

> 补充：为什么受检异常是"可预期的失败"？受检异常（checked exception）由编译器强制检查——会抛它的代码必须 try-catch 或 throws 才能编译通过，所以它代表的通常是**业务上提前能想到的情况**：库存不足、余额不够、订单号不存在这类业务异常，是业务正常流程里会发生的事，不应该把整个事务回滚。而运行时异常（NPE 等）是**没料到的 bug**，状态已不可信，必须回滚防止脏数据。一句话记忆：**受检 = 可预期的业务失败，不回滚；运行时 = 没料到的 bug，必回滚**。易错点：受检异常不是"不能被事务管理"，它照样进切面，只是默认不回滚，想回滚就显式 `rollbackFor`。③ 同类 `this` 调用失效，是因为 @Transactional 基于 **AOP 代理**：容器里注入的是代理对象，外部调用方法会先过代理切面；但类内部 `this.foo()` 直接调用的是目标对象自己，`this` 不是代理，切面根本没机会介入，事务自然不生效——解决方式是把方法拆到另一个 Bean 里调用，或者注入自身代理。

### 20. Spring Boot 项目怎么做健康检查和监控？【中频】

引入 `spring-boot-starter-actuator` 依赖即可。它会暴露一组 HTTP 端点：`/actuator/health` 查看应用健康状态（UP/DOWN 及各组件状态）、`/actuator/metrics` 查看 JVM、内存、HTTP 请求等指标、`/actuator/info` 查看自定义信息。默认只暴露 `health` 和 `info` 两个端点，其余需要通过 `management.endpoints.web.exposure.include` 显式开放。

> **知识点跳转：** [[Spring-interview-guide]]

**追问：** Actuator 暴露的端点有哪些安全考量？怎么自定义一个健康检查指标？

> **追问参考答：** 安全考量核心是**别把敏感端点全量暴露到公网**：`env`、`beans`、`configprops`、`heapdump`、`shutdown` 这些会泄露配置信息、内部 Bean、甚至允许关闭应用，生产环境应通过 `management.endpoints.web.exposure.include/exclude` 只保留 `health`、`info` 等必要端点，并用 `management.server.port` 把 actuator 放到独立端口、配合 Spring Security 加认证，避免未授权访问。自定义健康检查指标：实现 `HealthIndicator` 接口，重写 `health()` 方法返回 `Health.up().withDetail(...)` 或 `Health.down(...)`，把这个类注册成 Spring Bean，Actuator 会自动收集它并汇总进 `/actuator/health`，很适合检查依赖的第三方服务（如外部 API、定时任务）是否可用。

### 21. Spring Boot 热部署怎么做？【低频·加分】

最常见的是引入 `spring-boot-devtools` 依赖。devtools 会监听 classpath 上文件的变化（编译后的 .class、resources），检测到变化就**自动重启**应用。IDE 里需要开启自动编译（IDEA 勾选 Build project automatically），改完代码保存就会触发重启。也可以搭配浏览器 LiveReload 实现前端资源热更新。

> **知识点跳转：** [[Spring-interview-guide]]

**追问：** devtools 的自动重启原理是什么？为什么改配置或 pom 不触发重启？

> **追问参考答：** 原理是**双类加载器**：devtools 用两个 classloader——`base classloader` 加载不常变的依赖（第三方 jar、框架），`restart classloader` 加载开发者自己的代码。它监听 classpath 文件的变化，一旦检测到开发者的类或资源变了，就**丢弃旧的 restart classloader、new 一个新的重新加载**这些类，因为依赖没变所以不用全量加载，比冷启动快得多。为什么不触发重启分两种：**改 pom** 不触发，是因为 pom.xml 是构建配置文件、不在运行时的 classpath 里，devtools 只监听 classpath 文件变化，改 pom 必须重新构建（重新导入依赖）才会反映到 classpath；**改配置**方面，devtools 默认会排除一些不参与重启的路径（如静态资源 /static、模板 /templates 走 LiveReload 不走重启），而且有些配置（端口、日志等）即使重启 classloader 也不会重新生效，需要完全冷重启才行。

---

## 关联知识点

- [[spring八股]] — Spring / SpringBoot 八股总览
- [[Spring-interview-guide]] — Spring 七大模块面试题口述版
- [[@SpringBootApplication解析]] — 组合注解 + 自动装配 + 启动流程
- [[自定义SpringBootStarter详解]] — 自定义 Starter 七步完整示例
- [[WebMvcAutoConfiguration]] — MVC 自动配置原理
- [[WebMvcConfigurer]] — MVC 自定义扩展接口
- [[HandlerAdapter]] — 适配器模式 + MVC 调用链
- [[DispatcherServlet详解]] — 前端控制器分发流程
- [[Spring ioc]] — IoC 容器与 Bean 生命周期
