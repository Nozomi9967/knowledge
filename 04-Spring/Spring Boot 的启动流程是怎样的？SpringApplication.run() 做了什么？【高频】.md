---
tags:
  - spring
created: 2026-10-13
updated: 2026-10-13
---

来自[[SpringBoot 热门面试题]]

**30 秒主线版**（先把 run() 骨架立住）：

> SpringApplication.run() 分两块。先构造 SpringApplication：根据 classpath 推断应用类型，从 spring.factories 加载 Initializer 和 Listener，推断主类。run() 里依次是：回调 RunListener 的 starting；准备 Environment，这一步加载 application.yml 这些外化配置；按应用类型创建 ApplicationContext；prepareContext 阶段执行 Initializer、把主启动类注册成 BeanDefinition；然后 refresh 完成容器启动；最后发布 started 事件、执行 ApplicationRunner 和 CommandLineRunner，进入 ready。

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

> [!info] 解释：ConfigurationClassPostProcessor 在 ① 里具体干了什么
> 它是 BeanDefinitionRegistryPostProcessor 的内置实现——正因为实现了这个接口，它才能在 bean 实例化之前往容器里新增 BeanDefinition。它派 ConfigurationClassParser，以 prepareContext 里注册的主启动类为入口，逐个解析注解：
> - 读到 @ComponentScan：扫描业务包，把业务类注册成 BeanDefinition
> - 读到 @EnableAutoConfiguration：它本质是 @Import(AutoConfigurationImportSelector.class)，这个选择器是个 DeferredImportSelector，要等所有业务配置类解析完才轮到它。它读取 AutoConfiguration.imports 清单，按 @ConditionalOnClass 等条件过滤，把留下的自动配置类也注册成 BeanDefinition
> - 最后以普通 BeanFactoryPostProcessor 的身份执行 postProcessBeanFactory：对 @Configuration 做 CGLIB 增强，保证 @Bean 方法互调返回同一单例

[[spring.factories不是废除了吗]]

[[从 spring.factories 加载 Initializer 和 Listener是干嘛的]]

[[SpringBoot是如何根据 classpath 推断应用类型的]]

