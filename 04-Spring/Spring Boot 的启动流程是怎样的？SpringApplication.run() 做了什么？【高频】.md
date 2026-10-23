---
tags:
  - spring
created: 2026-10-13
updated: 2026-10-13
---

来自[[SpringBoot 热门面试题]]

**30 秒主线版**（先把 run() 骨架立住）：

> SpringApplication.run() 分两块。先构造 SpringApplication：根据 classpath 推断应用类型，从 spring.factories 加载 Initializer 和 Listener，推断主类。run() 里依次是：回调 RunListener 的 starting方法；准备 Environment，这一步加载 application.yml 这些外化配置；按应用类型创建 ApplicationContext；prepareContext 阶段执行 Initializer、把主启动类注册成 BeanDefinition；然后 refresh 完成容器启动；最后发布 started 事件、执行 ApplicationRunner 和 CommandLineRunner，进入 ready。

**90 秒完整版**（refresh 部分用你原来的内容修正）：

> refresh 里挑重点说，分五步：
>
> **① invokeBeanFactoryPostProcessors**——执行 BeanFactory 后置处理器。核心是 ConfigurationClassPostProcessor，它负责把业务类和自动配置类注册成 BeanDefinition，并对 @Configuration 做 CGLIB 增强（具体干了什么见下方解释）。
> **② registerBeanPostProcessors**——注册 Bean 后置处理器。AOP 等 BeanPostProcessor 在这一步注册，为后面实例化时的依赖注入、AOP 代理做准备。
> **③ onRefresh**——Boot 覆写了这个方法，在这里 createWebServer 创建内嵌 Tomcat。
> **④ finishBeanFactoryInitialization**——实例化所有非懒加载单例。依赖注入、初始化回调、AOP 代理都发生在这一步。
> **⑤ finishRefresh**——发布 ContextRefreshedEvent，并通过 Lifecycle 机制真正 start Tomcat、开始监听端口。
>
> 回到 run()，发布 started 事件、执行 Runner，应用进入 running。


refresh里的重点是：
首先是执行BeanFactory后置处理器，核心是ConfigurationClassPostProcessor
他负责把业务类和自动配置类注册为BeanDefinition，并对@Configuration做CGLIB增强
然后是注册Bean后置处理器，AOP等BeanPostProcessor都是在这里注册的，为后面的依赖注入、AOP代理做准备
接着是onRefresh这个钩子方法，在这里Web应用会创建内嵌Tomcat
然后是finishBeanFactoryInitialization，在这里会实例化所有非懒加载实例，属性注入、初始化回调、AOP代理都发生在这一步
最后是finishRefresh，发布ContextRefreshedEvent，并通过Lifecycle机制真正的start Tomcat，监听端口
回到run方法，最后会发布started事件、执行Runner，发布ready，应用启动完毕

最后，ConfigurationClassPostProcessor 还会以普通 BeanFactoryPostProcessor 的身份执行 postProcessBeanFactory，对 @Configuration 类做 CGLIB 增强。增强的目的是让配置类内部的 @Bean 方法互相调用时，也能经过容器拦截，返回同一个单例，而不是每次调用都新建对象。这一步发生在所有 BeanDefinition 注册完成之后、Bean 实例化之前。

面试时需要注意两点。第一，它本身不负责扫描和注册业务类，这些工作由 ClassPathBeanDefinitionScanner 完成，它只是调度者。第二，它分两个阶段工作：先作为 BeanDefinitionRegistryPostProcessor 注册定义，再作为 BeanFactoryPostProcessor 增强配置类，两个阶段的职责不同，不要混为一谈。

> [!ConfigurationClassPostProcessor] 


ConfigurationClassPostProcessor 是 Spring 内置的 BeanDefinitionRegistryPostProcessor 实现，正因为实现了这个接口，它才能在 Bean 实例化之前向容器里新增 BeanDefinition。它以 run 方法传入并已注册的主启动类为入口，交给 ConfigurationClassParser 逐个解析配置类上的注解。遇到 @ComponentScan 时，解析器会触发类路径扫描，由 ClassPathBeanDefinitionScanner 遍历业务包，把带有 @Component 及其派生注解的业务类注册成 BeanDefinition，这一步干的活其实是扫描器，解析器只是上层调度。

遇到 @EnableAutoConfiguration 时，它本质上是 @Import(AutoConfigurationImportSelector.class)。这个选择器是 DeferredImportSelector，会等所有业务配置类解析完毕后再执行。它读取 AutoConfiguration.imports 清单，经过 @ConditionalOnClass 等条件过滤后，把留下的自动配置类交给解析器，最终注册为 BeanDefinition。



---






[[spring.factories不是废除了吗]]

[[从 spring.factories 加载 Initializer 和 Listener是干嘛的]]

[[SpringBoot是如何根据 classpath 推断应用类型的]]

---

**追问：run 方法传入的那个类起什么作用？为什么说 @SpringBootApplication 并不是框架识别启动类的依据？**

结论是，run 方法的第一个参数决定了主配置源，框架从这个类开始加载配置。注解本身不负责告诉框架"这是启动类"，它只是元数据，描述这个类应该如何被配置、扫描哪些包、是否开启自动配置。真正触发加载的是 run 方法的调用，注解只有在被解析时才生效。

原理上，SpringApplication 在构造时会把 run 的参数保存为 primarySources。启动过程中，框架通过 BeanDefinitionLoader 把这些类注册为 Bean 定义，然后像处理普通配置类一样解析它们身上的注解。SpringBootConfiguration、ComponentScan、EnableAutoConfiguration 正是在这一步才产生作用。所以即使把一个只标了 Configuration 的类传给 run，它同样能作为配置源被加载，只是不会自动带上组件扫描和自动配置。反过来，一个标了 SpringBootApplication 却没有被传给 run 的类，只是普通类，不会启动应用。

这里容易混淆的地方有两点。第一，框架在日志、横幅等场景下会通过调用栈推断出包含 main 方法的类，即 mainApplicationClass，但这个推断结果只用于展示和辅助信息，并不决定配置源，配置源始终以 run 的参数为准。第二，ComponentScan 在未指定 basePackages 时，扫描起点是被注解类所在的包，因此"配置源由 run 参数决定"和"扫描范围由注解类位置决定"是两件事。日常开发中两者通常是同一个类，所以容易混为一谈，面试时应当分开说清楚。



**追问：讲一下@Import(AutoConfigurationImportSelector.class)，不要过深**

`@Import(AutoConfigurationImportSelector.class)` 的作用是告诉 Spring：去执行这个选择器，由它返回一批类名，这些类都当作配置类来处理。`@Import` 本身支持三种形式，普通配置类、`ImportSelector` 和 `ImportBeanDefinitionRegistrar`。这里传入的是 `ImportSelector`，所以 Spring 会调用它的 `selectImports` 方法拿到类名数组，再逐个解析。`@EnableAutoConfiguration` 之所以能开启自动配置，就是靠这一层间接的导入。

`AutoConfigurationImportSelector` 实现的是 `DeferredImportSelector`，这个接口的特点是延迟执行。普通的 `ImportSelector` 在解析到注解时就会立即执行，而 `DeferredImportSelector` 会被暂存起来，等所有业务配置类都解析完毕后再统一处理。这样安排的目的是，让用户自己定义的 Bean 先进入容器，自动配置的条件判断才能看到这些结果，比如 `@ConditionalOnMissingBean` 判断用户是否已经定义了某个 Bean。

选择器的核心逻辑大致分为几步。它先读取自动配置候选类清单，在 Spring Boot 2.7 及之后的版本中，清单位于 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件里，较早的版本则主要读取 spring.factories。接着它会去掉用户通过 spring.autoconfigure.exclude 等配置排除的类，然后交给一组过滤器，依据 @ConditionalOnClass 这类条件判断哪些类的依赖已经存在于 classpath 中，把不满足条件的筛掉。最后，剩下的类名以数组的形式返回给 Spring。

需要注意的是，返回类名并不等于这些 Bean 已经生效。这些自动配置类同样要被当作配置类解析，其中的 @Bean 方法和各种条件注解还要在注册阶段逐一评估，条件不满足的不会生成 BeanDefinition。另外，各个自动配置类之间的先后顺序，由 @AutoConfigureBefore、@AutoConfigureAfter 和 @AutoConfigureOrder 决定，选择器返回前会按这些规则排序。




**哪些阶段会解析业务配置类**

主要发生在 ConfigurationClassPostProcessor 执行的那一轮 BeanDefinitionRegistryPostProcessor 阶段，也就是 Bean 实例化之前。它从主启动类开始，把所有候选配置类交给 ConfigurationClassParser 解析。业务配置类在这一阶段有三种进入方式：主启动类本身作为入口最先被解析；解析主启动类时，@ComponentScan 扫描到的带有 @Configuration 的业务类会被加入候选，并递归解析；通过 @Import 直接导入的普通配置类也会在解析过程中被处理。

延迟导入是另一个关键节点。AutoConfigurationImportSelector 作为 DeferredImportSelector，会被暂存到解析器的延迟队列中，直到当前所有已发现的业务配置类都解析完毕，才统一执行。这时自动配置类才被加入，并同样按配置类的规则解析。解析器内部是一个循环，只要过程中又新发现了候选配置类，比如自动配置类自身带有 @Import 或扫描注解，或者导入的类又引入了新的配置类，就会继续处理，直到没有新的候选为止。

所以业务配置类并不是一次性全部解析完的，而是随着扫描、导入和延迟导入逐步被发现并处理，最终都在这一阶段完成 BeanDefinition 的注册。面试时可以概括为：主启动类入口解析，扫描和普通导入即时处理，延迟的自动配置导入最后处理，并在这个过程中循环补充新发现的配置类。



**selectImports方法干了什么**

`selectImports` 本身并不去扫描类路径，它的工作是组织一条流水线，把候选类名一步步筛选出来。入口处它先判断自动配置开关是否开启，关闭时直接返回空数组。开启后，它调用 getAutoConfigurationEntry 方法，这个方法依次完成几件事。

第一步是拿到候选清单。在 Spring Boot 2.7 及之后的版本中，框架通过 ImportCandidates 读取各个依赖 jar 包中 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件里登记的类名，把所有 jar 的结果合并成一个列表。较早的版本则通过 SpringFactoriesLoader 读取 spring.factories 中 EnableAutoConfiguration 对应的条目，思路相同。

第二步是去重，并收集需要排除的类。选择器会读取主启动类上 @EnableAutoConfiguration 的 exclude 和 excludeName 属性，再加上配置文件中 spring.autoconfigure.exclude 的设置，把这些类从候选列表中移除。如果排除的类在候选中并不存在，启动时还会给出提示。

第三步是按条件过滤。剩下的候选类要经过一组 AutoConfigurationImportFilter，其中最主要的是 OnClassCondition，它检查 @ConditionalOnClass 等注解声明的类是否存在于 classpath 中。为了提高速度，框架会优先读取构建时生成的 spring-autoconfigure-metadata.properties 文件，提前拿到这些条件信息，避免为每个类都加载字节码。被过滤掉的类不会进入后续流程。

第四步是排序。通过过滤的类名会由 AutoConfigurationSorter 根据 @AutoConfigureOrder、@AutoConfigureBefore 和 @AutoConfigureAfter 进行排序，保证依赖关系靠前的自动配置先被处理。最后，框架发布一个自动配置导入事件，并把结果转换成字符串数组返回给 Spring，由 Spring 把这些类当作配置类继续解析。

>AutoConfigurationImportFilter 是一个过滤器接口，框架在批量过滤候选自动配置类时调用它。它的实现类之一就是 OnClassCondition

