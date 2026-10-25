
### **1. 为什么使用 Spring Boot？相比传统 Spring 有什么优势？**

Spring Boot 是 Spring 的脚手架，核心价值是大幅降低 Spring 应用的搭建和配置成本。传统 Spring 有两个痛点：一是依赖管理，做 Web 开发要手动引入 spring-webmvc、tomcat、jackson 等一长串依赖，还要自己处理版本冲突；二是配置繁杂，要手动注册 DispatcherServlet，配置视图解析器、HandlerMapping、数据源等。Spring Boot 用 starter 把相关依赖打包并统一版本，再用自动配置加约定优于配置，让你引入依赖就能用，大部分配置不用手写。要注意，自动注入依赖是 Spring 自身 IoC 的能力，不是 Boot 独有的，Boot 提供的是 starter 打包依赖和统一版本。

追问问到没有 Boot 要手动做什么。要手动引依赖并解决版本冲突，在 web.xml 或代码里注册 DispatcherServlet，配置 MVC 组件，配置数据源和事务管理器，还要写大量 XML 或 Java 配置类。Boot 用 starter 和自动配置把这些都解决了，所以痛点本质就是繁杂的依赖关系和大量的配置工作。

### **2. Spring、Spring Boot、Spring MVC 三者的关系和区别？**

Spring 是 Java 后端开发框架，是整个生态，核心能力有 IoC、AOP 和事务管理。Spring MVC 是 Spring 里的 Web 模块，基于 Servlet 构建，由 DispatcherServlet 作为前端控制器统一接收 HTTP 请求，再分发给对应的 Controller。Spring Boot 是基于 Spring 的快速开发脚手架，通过 starter 统一管理依赖，通过自动配置加约定优于配置减少手动配置，并内置 Tomcat 等容器，可以直接打成可执行 jar 运行。注意 Spring MVC 说的是表现层框架，别说成前端入口，容易被误解成前端页面。

追问问为什么说 Boot 是脚手架而不是独立框架。因为它没有发明新的东西，只是对 Spring 已有的组件做了封装，主要是四层：依赖层靠 starter 打包并统一版本，配置层靠自动配置省掉手动配置，运行层靠内嵌容器让 java -jar 直接跑，工具层提供 actuator 和 devtools 这类运维开发辅助。

### **3. 怎么理解 Spring Boot 的约定优于配置？**

约定优于配置的意思是，框架提供一套合理的默认约定，你遵守约定就几乎不用写配置，只有需要打破默认时才显式配置。它解决的是传统 Spring 配置繁琐的问题。在 Boot 里体现得很具体，比如默认端口是 8080，默认扫描主启动类所在包及其子包，主类放在包的最外层，配置文件放在 resources 目录，引入 starter 之后自动配置就生效。

追问问约定在哪里会失效。典型场景有几个：端口不是 8080 就要配 server.port；类不在主类所在包下就要用 scanBasePackages 显式指定扫描范围；要自定义拦截器或消息转换器，就实现 WebMvcConfigurer 去扩展；多环境则用 spring.profiles.active 激活对应的 profile。

### **4. @SpringBootApplication 由哪几个注解组成？各自作用是什么？**

SpringBootApplication 是一个组合注解，等价于三个注解叠加。第一个是 SpringBootConfiguration，本质是 Configuration 的派生，标记当前类是配置类，配置类默认会被 CGLIB 代理增强，保证 Bean 方法之间互相调用时拿到的是同一个单例。第二个是 EnableAutoConfiguration，它是自动装配的核心，通过 Import 引入 AutoConfigurationImportSelector，去加载类路径下的自动配置类。第三个是 ComponentScan，默认扫描主启动类所在包及其子包，把 Component、Service、Controller 等注册进容器。

关于注解本身，注解是元数据，不改变代码逻辑，行为由解析方触发。它本质是继承 Annotation 接口的接口，运行时通过反射调用 getAnnotation，JDK 会生成动态代理对象，经 AnnotationInvocationHandler 到成员值的 Map 里查表返回。这几个注解的 Retention 都是 RUNTIME，所以 Spring 在运行时通过反射解析它们。

追问问为什么不直接写三个注解。一是合并成一个入口更简洁，不容易遗漏。二是组合注解里给 ComponentScan 配了 excludeFilters，排除 TypeExcludeFilter 和 AutoConfigurationExcludeFilter，防止自动配置类被组件扫描重复注册，分开写就得自己手动配这些。另外要纠正一点，主启动类并不是靠这个注解识别的，而是 run 方法传入的那个类就是主配置源。

追问二问为什么要用 CGLIB 增强配置类，增强解决了什么问题。追问三问增强后的代码在哪，源码明明一点没变。追问四问同一个 bean 内方法互调会绕过 AOP 代理，那这里为什么不冲突。追问五问什么情况下会真的绕过。这四个追问单独整理成 [[@Configuration 的 CGLIB 原地增强]]，答案要点分别是：增强解决方法互调时拿到重复对象的问题，比如 jdbcTemplate 方法里调 dataSource 会 new 出第二个数据源；增强不在源码也不在磁盘 class，是运行时生成字节码，ConfigurationClassPostProcessor 先把 BeanDefinition 的 beanClass 换成增强类，原方法体改名藏进 CGLIB 前缀方法，只在内存 defineClass；它和 AOP 自调用失效不冲突，因为 AOP 是包装代理有独立的 target，this 指向 target 所以绕过，配置类是原地增强没有 target，this 就是增强实例本身；真会绕过的是 Bean 方法写成 final、private、static，或者 proxyBeanMethods 等于 false，或者写在 Component 类里。

### **5. Spring Boot 的启动流程是怎样的？SpringApplication.run() 做了什么？**

启动围绕 SpringApplication.run 展开。首先创建 SpringApplication 实例，根据 classpath 判断应用类型，是 Servlet Web、响应式 Web 还是普通应用。然后准备 Environment，把配置文件、系统属性、命令行参数都读进来。接着创建 ApplicationContext，Servlet Web 项目用的是 AnnotationConfigServletWebServerApplicationContext。再进入 prepareContext，把主启动类注册成 BeanDefinition，作为配置源。最核心的是 refreshContext，调用 refresh，其中 ConfigurationClassPostProcessor 解析配置类、扫描业务 Bean、加载并过滤自动配置类，之后注册 BeanPostProcessor、初始化单例 Bean。最后发布启动完成事件，执行 ApplicationRunner 和 CommandLineRunner。

追问问内嵌 Tomcat 在哪一步启动。准确说是在 refresh 的 onRefresh 这个模板方法里，ServletWebServerApplicationContext 覆写了它，调用 createWebServer 创建内嵌容器。但端口真正开始监听，是在 refresh 最后的 finishRefresh 阶段，由 WebServerStartStopLifecycle 触发启动。所以单例 Bean 实例化期间，端口其实还没对外提供服务。


[[Spring Boot 的启动流程是怎样的？SpringApplication.run() 做了什么？【高频】]]

### **6. 为什么主启动类要放在包的最外层？放错位置会出什么问题？**

因为 ComponentScan 默认的扫描范围就是主启动类所在的包及其所有子包，Spring Boot 以主类为锚点往下递归扫描。主类放在顶层包，下面所有子包的组件才都能被扫到。如果放得太深，比如放在某个子包里，扫描范围只覆盖这个子包，兄弟包和外层包里的 Bean 扫不到，启动时就会报找不到 Bean。如果放得太浅，扫描范围过大，可能把不相关的类也扫进来，增加启动时间，甚至误注册其他模块的组件。

追问问默认扫描范围怎么确定，以及自动配置类为什么不会被重复扫描。扫描的 basePackage 是由主类所在包自动推导出来的。自动配置类不被重复扫描，是因为 SpringBootApplication 里的 ComponentScan 配了 excludeFilters，用 AutoConfigurationExcludeFilter 把自动配置类排除掉，它们由 AutoConfigurationImportSelector 单独加载，不需要再扫一遍。

### **7. ApplicationRunner 和 CommandLineRunner 的区别？**

两者都是 Spring Boot 在容器刷新完成、应用启动之后回调的接口，常用来做缓存预热、初始化数据、打印启动信息这类启动后的工作。区别在于参数形态。CommandLineRunner 的 run 方法接收的是 String 数组，就是 main 方法传进来的原始命令行参数。ApplicationRunner 的 run 方法接收的是 ApplicationArguments，是 Boot 解析封装后的对象，能区分选项参数，也就是带双横线的那种，和非选项参数，并提供 getOptionValues、getNonOptionArgs 这些方法。

追问问执行顺序和参数来源。顺序用 Order 注解或者 Ordered 接口控制，值越小越先执行，要注意这两类 Runner 是放在一起统一排序的，不是各排各的。参数来源都是 SpringApplication.run 传入的 args，也就是 main 方法的命令行参数。

### **8. Spring Boot 的自动装配原理是什么？**

核心是 EnableAutoConfiguration 注解，它通过 Import 导入 AutoConfigurationImportSelector。这个类实现的是 DeferredImportSelector，所以会在用户配置类全部解析完之后才被回调，执行 getAutoConfigurationEntry。它会读取所有 jar 包里的 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件，2.7 之前读的是 spring.factories，拿到候选自动配置类的全类名，先去重、应用 exclude，再经过 ConditionalOnClass、ConditionalOnMissingBean 这些条件注解过滤，满足条件的才注册成 BeanDefinition，里面的 Bean 方法最终实例化进容器。

追问问完整链路。可以概括为：组合注解开启 EnableAutoConfiguration，Import 引入选择器，选择器加载候选类，排除并过滤，注册 BeanDefinition，最后实例化。因为是 Deferred 机制，自动配置永远排在用户配置之后，所以用户自定义的 Bean 才能覆盖默认实现。

### **9. AutoConfigurationImportSelector 在自动装配中扮演什么角色？**

它是自动装配里的选类人，自己不注册任何 Bean，只负责把大量候选配置类加载出来，经过去重、排除、过滤、排序后，把全类名清单交回容器。它实现了 DeferredImportSelector，执行时机推迟到用户配置类解析完之后，这是自定义 Bean 能覆盖默认配置的前提。它还实现了 EnvironmentAware、BeanFactoryAware、BeanClassLoaderAware 等感知接口，所以能读环境属性、查容器里已有的 Bean、拿类加载器，这是它能做条件过滤的基础。

追问问 selectImports 返回什么，以及怎么过滤和排序。返回的是一个 String 数组，是筛选后真正要注册的配置类全类名。过滤分三层：先排除，包括 spring.autoconfigure.exclude 和注解里的 exclude；再由 AutoConfigurationImportFilter 做粗过滤，典型是 OnClassCondition，classpath 上没有对应类的直接淘汰；最后是 Conditional 系列注解在解析配置类时逐个评估，不满足的就不注册。排序靠 AutoConfigureBefore、AutoConfigureAfter 和 AutoConfigureOrder，最后由 AutoConfigurationSorter 做排序，保证有依赖关系的配置类按正确顺序装配。

### **10. @Conditional 条件注解有哪些？分别在什么场景生效？**

常用的有这些：ConditionalOnClass 和 ConditionalOnMissingClass 判断 classpath 上有没有某个类；ConditionalOnBean 和 ConditionalOnMissingBean 判断容器里有没有某个 Bean；ConditionalOnProperty 判断配置项的值；ConditionalOnResource 判断资源文件是否存在；ConditionalOnWebApplication 和 ConditionalOnNotWebApplication 判断是不是 Web 应用。它们最典型的使用场景是自动配置类，不过本身可以标在任何配置类、Bean 方法或组件上。另外 ConditionalOnBean 对 Bean 的注册顺序敏感，所以基本只在自动配置里用。比如 JdbcTemplate 的自动配置，要求 classpath 上有 DataSource，并且容器里没有用户自己定义的 JdbcTemplate，才提供默认的。

关于注解本质，条件注解同样是元数据，它们的 Retention 是 RUNTIME，由 Spring 在解析配置类时通过反射读取，然后交给对应的 Condition 实现去判断，注解自己不包含任何逻辑。

追问问 ConditionalOnMissingBean 和 ConditionalOnClass 的区别，以及为什么说它留了覆盖口子。ConditionalOnClass 看的是环境，也就是 classpath 上有没有这个类，决定环境支不支持装配。ConditionalOnMissingBean 看的是容器里有没有这个类型的 Bean。用户只要自己定义了同类型的 Bean，默认的那个 Bean 就不再注册，不用改框架也不用排除自动配置。要注意范围：标在 Bean 方法上时只跳过这一个 Bean，标在配置类上才会让整个类失效。实际中两者通常搭配使用，先判断有没有依赖，再判断要不要给默认实现。

### **11. 怎么自定义一个 SpringBoot Starter？核心步骤是什么？**

大致分七步。第一，建 Maven 项目，第三方 starter 的命名习惯是 xxx-spring-boot-starter，spring-boot-starter-xxx 这种前缀是官方保留的。第二，引入 spring-boot-autoconfigure 依赖。第三，写自动配置类，用 Configuration 加 ConditionalOnClass 等条件注解控制生效条件，用 Bean 方法注册默认组件。第四，如果需要读配置，写一个带 ConfigurationProperties 前缀的属性类。第五，在 resources 下的 META-INF/spring 目录创建 org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件，写入自动配置类全类名。第六，用 EnableConfigurationProperties 把属性类注册进容器。第七，打包发布，别的项目引入依赖，自动配置就会生效。实际项目里常把它拆成 autoconfigure 模块和 starter 模块。

追问问两个注解的分工，以及服务类为什么不应该加 Component。ConfigurationProperties 负责绑定，把配置值映射到类的字段，但它不负责把类注册进容器；EnableConfigurationProperties 负责把属性类注册成 Bean。服务类不应该加 Component，有三个原因：starter 的类在 jar 里，不在使用方的扫描范围内，能不能扫到不可控；Component 是硬注册，绕过了条件注解，依赖没引入时可能直接启动失败；它还会堵死 ConditionalOnMissingBean 留给用户的覆盖口子。所以应该由自动配置类的 Bean 方法统一注册。

### **12. 自动配置类的注册文件在哪？Spring Boot 2.7 前后有什么变化？**

2.7 之前，自动配置类注册在 META-INF/spring.factories 里，key 是 org.springframework.boot.autoconfigure.EnableAutoConfiguration，value 是配置类全类名列表。2.7 开始支持新的 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件，每行写一个全类名，不需要 key。到 3.0 就不再支持用 spring.factories 注册自动配置类了，只认 imports 文件。

追问问两种方式的本质区别。spring.factories 是键值格式，一个文件里混着自动配置、监听器、环境后处理器等多种扩展点，key 是字符串，写错只有运行时才发现。imports 文件是纯列表，专门管自动配置，职责单一、更直观。要注意 spring.factories 并没有整体废弃，ApplicationListener、EnvironmentPostProcessor 这些扩展点仍然通过它注册，被移除的只是用它注册自动配置这一种用法。

### **13. @ConfigurationProperties 和 @Value 的区别？**

ConfigurationProperties 是批量的类型安全绑定，把一组相关的配置映射到一个 JavaBean，支持松散绑定、嵌套对象、List 和 Map，还能配合 JSR-303 做校验，并能生成配置元数据让 IDE 提示。Value 是单个值的注入，一个字段一个注解，支持 SpEL 表达式，而 ConfigurationProperties 不支持 SpEL。类型转换都发生在运行时，不是编译期保证。所以一组相关配置，比如数据源、Redis，用 ConfigurationProperties 收拢成一个类；偶尔取一个零散值，用 Value 就够了。

追问问复杂结构和松散绑定。复杂结构用 ConfigurationProperties 更合适，它天然支持嵌套对象、集合直接绑定，用 Value 要拆成一堆注解或写难维护的 SpEL。松散绑定是因为它按前缀加字段名做整块映射，并有一套名称转换规则，kebab-case、驼峰、下划线、环境变量的大写形式都能对上。Value 是按写死的属性名取值，没有这层映射，换个写法就取不到，不过也要说明，Value 对松散绑定的支持有限，推荐写成规范的 kebab-case 形式。

### **14. Spring Boot 多环境配置怎么实现？**

用 profile 机制。公共配置放在 application.yml，每个环境建一个 application-{profile}.yml，比如 application-dev.yml 和 application-prod.yml。启动时激活某个 profile，Boot 先加载 application.yml 作为基础，再加载激活的 profile 文件做覆盖。

追问问激活方式和覆盖关系。激活方式有配置文件里的 spring.profiles.active、命令行参数 --spring.profiles.active、环境变量 SPRING_PROFILES_ACTIVE，以及 JVM 系统属性 -Dspring.profiles.active。覆盖关系是同名项覆盖，profile 文件里写了什么就覆盖什么，没写的沿用 application.yml 的值。多个 profile 同时激活时，后面的覆盖前面的。

### **15. 配置属性的加载优先级是怎样的？**

大致从高到低是：命令行参数，Java 系统属性，操作系统环境变量，带 profile 的配置文件，application.yml，最后是代码里的默认值。高优先级覆盖低优先级，低优先级只在高优先级没定义时才生效。三个容易混的来源区别如下：系统属性是 -Dserver.port=9090 这种，传给 JVM；命令行参数是 --server.port=9090，传给 Spring Boot 应用，由 SpringApplication 解析；环境变量是操作系统层面的变量，比如 SERVER_PORT。这里是概括性的顺序，细节以官方文档的完整列表为准。

追问问命令行参数和配置文件同时存在谁生效。命令行参数生效。因为 Boot 把各个配置来源按固定顺序组成一条 PropertySource 链，命令行参数排在最前面，取值时从前往后找，先找到就返回。这样设计是因为配置文件是静态的，而部署运维阶段经常要临时改端口、改 profile，直接加参数覆盖就行，不用改代码和配置文件。命令行参数与 Java 系统属性的区别（传给谁、位置要求、优先级、易混点）单独整理成 [[命令行参数与Java系统属性区别]]。

### **16. 内嵌 Web 容器的原理？怎么切换 Tomcat / Jetty / Undertow？**

原理是 Spring Boot 把 Web 容器当成普通依赖打进应用，不再单独安装。它通过 ServletWebServerFactory，比如 TomcatServletWebServerFactory，在启动时用代码创建并启动内嵌的服务器实例，并把 DispatcherServlet 注册进去，应用打成可执行 jar 后直接 java -jar 运行。切换容器的做法是，在 pom 里排除 spring-boot-starter-tomcat，再引入 spring-boot-starter-jetty 或 spring-boot-starter-undertow，Boot 检测到 classpath 上的容器后就会自动切换。

追问问生命周期由谁管理。由 Spring 应用上下文管理，不是独立进程。容器在 refresh 的 onRefresh 阶段创建，在 finishRefresh 阶段真正启动并监听端口；停止则发生在上下文关闭时，通过 WebServer 的 stop 停掉。所以容器的启停完全跟随应用上下文的生命周期。

### **17. WebMvcAutoConfiguration 自动配置了哪些东西？**

它是 Boot 对 Spring MVC 的自动配置核心，装配了一整套 MVC 基础设施，包括 HandlerMapping 和 HandlerAdapter，比如 RequestMappingHandlerMapping 和 RequestMappingHandlerAdapter；静态资源处理，注册一个资源处理器，并告诉它去哪些类路径位置查找文件。`static`、`public`、`resources` 这些目录名是 Spring Boot 约定的查找路径，写在 `ResourceProperties` 中。目录是否存在、里面放什么，都由开发者决定。如果你的项目里没有 `static` 目录，启动时不会报错，只是找不到对应文件而已。，机制单独整理成 [[SpringBoot静态资源处理详解]]；视图解析器，比如 InternalResourceViewResolver 和 ContentNegotiatingViewResolver；HttpMessageConverter，默认带 Jackson 处理 JSON；类型转换器与格式化器；以及错误处理机制。DispatcherServlet 本身及其默认映射 / 是由另一个自动配置类 DispatcherServletAutoConfiguration 负责的，这点别混了。

追问问怎么在不破坏自动配置的前提下自定义拦截器或消息转换器。正确做法是实现 WebMvcConfigurer 接口并注册成 Bean，覆写 addInterceptors 添加拦截器，覆写 extendMessageConverters 追加或调整转换器，覆写 addResourceHandlers 配置资源映射。自动配置会检测到这些 Bean 并合并进默认配置。最大的坑是不要加 EnableWebMvc，它会让 WebMvcAutoConfiguration 整体失效，默认配置全部丢失。


>因为EnableWebMvc会导入一种与WebMvcAutoConfiguration同类型的Bean，使得带有@ConditionalOnMissingBean的WebMvcAutoConfiguration放弃注册

### **18. 前后端分离时，@RestController 返回的对象是怎么变成 JSON 的？**

RestController 等于 Controller 加 ResponseBody，所以返回值不会走视图解析。请求进入 DispatcherServlet 后，RequestMappingHandlerAdapter 调用 Controller 方法拿到返回对象，再由 RequestResponseBodyMethodProcessor 处理返回值，它会选择合适的 HttpMessageConverter，默认是 Jackson 的 MappingJackson2HttpMessageConverter，把对象序列化成 JSON 写进响应体，并设置 Content-Type 为 application/json。

追问问转换器怎么选。介入点就是 HandlerAdapter 处理返回值这一步，processor 遍历已注册的转换器，调用 canWrite 找到第一个能处理的。选择依据是返回值的 Java 类型和协商出的媒体类型。响应格式由请求头 Accept 和服务端能产出的类型协商决定，请求头 Content-Type 则是用在 RequestBody 读取请求体时选择转换器的，两者别混淆。比如 Accept 是 application/json，就命中 Jackson；Accept 是 application/xml 并且 classpath 上有对应的 XML 转换器，才会用 XML 转换器。

### **19. Spring Boot 怎么开启和使用事务？**

在方法或类上加 Transactional 注解就行，不需要额外开启，因为 Boot 的事务自动配置已经注册好了事务管理器，有 DataSource 时默认是 DataSourceTransactionManager，用 JPA 时是 JpaTransactionManager。原理是 Spring AOP，被注解的方法会被代理，方法执行前开启事务，正常返回提交，抛异常回滚。

追问有三个点。第一，不用手动加 EnableTransactionManagement，是因为自动配置已经默认开启了事务管理。第二，默认只回滚 RuntimeException 和 Error，受检异常默认不回滚，因为它通常代表业务上可预期的失败，比如库存不足、余额不够；想让受检异常也回滚，要显式写 rollbackFor 等于 Exception.class。受检异常照样会进入事务切面，只是默认不触发回滚。第三，同类里用 this 调用方法会失效，因为容器里注入的是代理对象，外部调用才会经过代理切面，this 指向的是目标对象本身，切面没机会介入。解决办法是把方法拆到另一个 Bean，或者注入自身代理。此外，非 public 方法、异常被 catch 吞掉、数据库引擎不支持事务，也都会导致事务失效。

### **20. Spring Boot 项目怎么做健康检查和监控？**

引入 spring-boot-starter-actuator 依赖即可，它会暴露一组端点：health 查看应用健康状态，metrics 查看 JVM、内存、HTTP 请求等指标，info 查看自定义信息。较新的版本里，Web 端默认只暴露 health，其他端点需要通过 management.endpoints.web.exposure.include 显式开放，具体默认值以所用版本的官方文档为准。

追问问安全考量和自定义指标。安全上核心是不要把敏感端点全量暴露到公网，env、beans、configprops、heapdump、shutdown 这些会泄露配置、内部结构，甚至能关停应用。生产上应只保留 health 等必要端点，用 management.server.port 把 actuator 放到独立端口，再配合 Spring Security 加认证。自定义健康指标就是实现 HealthIndicator 接口，在 health 方法里返回 Health.up 或 Health.down 并附带详情，注册成 Bean 后，Actuator 会自动汇总进 health 端点，很适合检查外部 API 这类依赖是否可用。

### **21. Spring Boot 热部署怎么做？**

最常见的是引入 spring-boot-devtools 依赖。它会监听 classpath 上文件的变化，检测到变化就自动重启应用。在 IDE 里要开启自动编译，比如 IDEA 要勾选自动构建项目，改完代码后编译就会触发重启。还可以配合浏览器的 LiveReload 刷新前端资源。注意，用 java -jar 运行打包后的 jar 时，devtools 默认是禁用的，它主要是开发期工具。

追问问原理，以及为什么改 pom 不触发重启。原理是双类加载器：base classloader 加载不常变的第三方依赖，restart classloader 加载开发者自己的代码。检测到变化后，丢弃旧的 restart classloader，新建一个重新加载业务类，因为依赖不用重新加载，所以比冷启动快很多。改 pom 不触发，是因为 pom 是构建文件，不在运行时 classpath 里，devtools 只监听 classpath 的变化，改完必须重新导入依赖并构建才会生效。另外要纠正一点，改 resources 下的 application.yml 同样会触发重启，默认被排除在重启之外的是 static、templates 这类静态资源目录，它们走 LiveReload，不走重启。

### **22. @SpringBootApplication 上的 @ComponentScan 默认只扫描主启动类所在包及其子包，那为什么还要配置 AutoConfigurationExcludeFilter？请举一个它真正起作用的例子。**

结论是，在常规项目里这个过滤器通常不会命中任何类。Spring Boot 自身的自动配置类位于 org.springframework.boot.autoconfigure 包下，业务主启动类一般放在 com.example.xxx 这样的包里，扫描范围覆盖不到它们。它真正起作用的场景，是扫描根包设置得过宽，把一个本身带有自动配置身份的类也扫了进来。

举个例子。假设公司有一个公共组件库 company-redis-starter，其中有类 com.example.common.redis.CompanyRedisAutoConfiguration，它加了 Configuration，并登记在 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 文件中，作为自动配置生效。如果业务主启动类放在 com.example.app 下，扫描范围是 com.example.app 及其子包，就扫不到它。但如果主启动类放在 com.example 下，扫描范围就覆盖了 com.example.common.redis，这个类会被当成普通配置类扫进来。

这里需要纠正一个说法。同一个类通过扫描和自动配置导入两条路径进入时，Spring 会按类做合并，一般不会真的注册两份 Bean，所以问题不在于重复注册，而在于控制权和时机。扫描出来的类被当作用户配置处理，发生在自动配置之前，它上面的 ConditionalOnMissingBean 等条件，以及 AutoConfigureBefore、AutoConfigureAfter 定义的顺序，就不再按自动配置的语义执行。结果可能是本应让位于用户自定义 Bean 的配置反而提前生效，或者排序失效导致覆盖关系错乱。AutoConfigurationExcludeFilter 的作用，就是在扫描阶段识别出登记在导入文件中的类并将其排除，让它们只通过自动配置机制进入容器。

面试时还有两个容易被追问的点。其一，TypeExcludeFilter 主要服务于 WebMvcTest、DataJpaTest 等测试切片，与自动配置的关系较小，两者通常成对出现。其二，过滤器只作用于扫描得到的类，对显式通过 Import 引入的类无效。真正稳妥的做法是把主启动类放在合适的包层级，或者通过 scanBasePackages 明确扫描范围，而不是依赖过滤器兜底。

### **23. Spring 是怎么解决循环依赖的？**

循环依赖指 A 依赖 B、B 又依赖 A，形成闭环。Spring 只能解决单例 Bean 通过 setter 或字段注入形成的循环，构造器注入和 prototype 作用域都无法解决，会直接报错。解决方式是三级缓存加提前暴露半成品，三个缓存都定义在 DefaultSingletonBeanRegistry 中。singletonObjects 是一级缓存，存放完全初始化好的成品；earlySingletonObjects 是二级缓存，存放已经提前暴露的早期引用，可能是代理对象；singletonFactories 是三级缓存，存放生成早期引用的 ObjectFactory。其中只有一级缓存是 ConcurrentHashMap，二三级缓存都是 HashMap，它们的读写都包在 synchronized 锁内，靠锁保证原子性，而不是依靠集合本身的并发能力。

以 A 依赖 B、B 依赖 A 为例，getBean(A) 时先查三级缓存，都没有命中，于是把 A 标记为正在创建，通过构造器得到原始对象 A。随后把 A 的 ObjectFactory 放入三级缓存，这一步只是埋点，工厂还没有执行。A 填充属性时发现需要 B，于是开始创建 B，B 同样实例化后把工厂放入三级缓存，填充属性时又需要 A，于是再次调用 getBean(A)。这时一级和二级缓存都为空，三级缓存命中，调用工厂得到早期引用。如果 A 需要 AOP，就在这一步生成代理对象 A′，并把它从三级移入二级，返回给 B。B 拿到引用后完成初始化并进入一级缓存，A 随后拿到完整的 B 完成自身的属性填充和初始化，最终以 A′ 或原始对象的身份放入一级缓存，二三级中的残留数据随之清理。

singletonCurrentlyInCreation 是一个基于 ConcurrentHashMap 包装的线程安全 Set，记录当前正在创建的单例 Bean 名称，它只是状态标记，本身不存放对象。Bean 在 beforeSingletonCreation 中加入，创建完成后在 afterSingletonCreation 中移除。它有两个作用：一是作为提前引用的准入条件，getSingleton 在一级缓存未命中时，只有该 Bean 处于创建中，才会继续查找二三级缓存并调用工厂，不在创建中的 Bean 即使三级残留了工厂也不会被取出；二是识别无法解决的循环，比如构造器注入形成的循环，Bean 在未暴露任何早期引用的情况下被再次请求，就会在 beforeSingletonCreation 阶段抛出 BeanCurrentlyInCreationException。

提前暴露的到底是什么，答案是 ObjectFactory 工厂而不是成品。真正的对象要等工厂被调用时才确定，可能是原始对象，也可能是提前生成的代理。因此准确的说法不是三级缓存单独解决了循环依赖，而是三级缓存、提前暴露工厂和创建状态标记三者配合的结果，缓存只是存放工厂的载体。从代码位置看，提前暴露发生在 doCreateBean 中，实例化之后调用 addSingletonFactory 把工厂放入三级缓存，负责埋点；断环发生在 doGetBean 的入口，getSingleton 按一级、二级、三级的顺序查找，三级命中时调用工厂并把结果移入二级，负责取用。

最后需要注意版本差异，Spring Boot 2.6 之后默认禁止循环依赖，启动时会直接报错，可以通过 spring.main.allow-circular-references 开启，但这只是兜底手段。更好的做法是重构设计，比如抽出公共服务，或者用事件机制解耦两个 Bean 之间的直接引用。

### **24. 为什么需要三级缓存，二级缓存不够吗？**

要分两层看。如果 Bean 不需要 AOP 代理，二级缓存确实够了，早期引用直接放二级，一级不能放。但一旦需要代理，三级才是必须的，否则同一个 Bean 会出现两个实例。

根因在代理的生成时机。正常流程里，代理是 AbstractAutoProxyCreator 在 postProcessAfterInitialization 阶段通过 wrapIfNecessary 生成的，也就是初始化之后才代理，这是 Spring 的设计原则。而循环依赖时，B 索要 A 发生在 A 初始化之前，根本等不到那个后置处理。假设只有一级加二级：A 实例化后就把原始对象放进二级，B 拿到的是原始 A；等 A 走完初始化被包装成代理 A′ 进入一级，容器里是 A′，B 手里是原始 A，同一个 Bean 两个身份，单例语义和事务切面一起失效。

三级缓存的解法是把要不要代理这个决策延迟。三级里存的不是对象，是 ObjectFactory，它包裹的是 SmartInstantiationAwareBeanPostProcessor 的 getEarlyBeanReference。B 来索要时才调工厂，由它判断这个 Bean 需不需要代理，需要就当场生成 A′ 返回，不需要就返回原始对象。这样 B 拿到的就是最终会进容器的那个对象，同时也保住了代理在初始化后生成的设计原则，只有被循环依赖时才提前。

这里要纠正一处歧义。面试里说二级不够，指的是一级成品加二级早期引用这个组合，缺的是三级工厂，不是说缓存数量从三个减到两个就一定错，讨论的对象别搞混。

追问问那二级缓存为什么还必须保留，不是有三级就够了吗。不够，因为三级工厂每被调用一次都可能产出新对象。场景是 A 同时被 B 和 C 循环依赖，B 触发工厂拿到 A′ 之后，如果结果没地方存，C 再来索要时三级里的工厂还在，会再调一次，生成第二个代理，B 拿一个、C 拿另一个，单例又被破坏。二级的作用就是存第一次 getObject 的结果，C 直接命中二级，工厂全链路只执行一次。所以这是两个互不重叠的问题：三级解决拿到的和最终的是同一个，二级解决工厂产物只生成一次。

追问二问早期引用为什么不干脆放进一级。两个原因。第一是语义，一级缓存的定义是完全初始化好的成品，所有 getBean 最终都从这里取，半成品放进去，等于让别的 Bean 把依赖没注入完的对象当成品用。第二是会被覆盖，A 初始化完成后 addSingleton 会把最终对象写进一级，覆盖掉之前的早期引用，B 和 C 手里还是旧的那个，问题原样复现。

### **25. 为什么构造器注入不能解决循环依赖？**

因为三级缓存的前提是对象已经 new 出来了。构造器注入时 A 的构造器参数就是 B，必须先把 B 造出来才能调完 A 的构造器；而 B 的构造器参数又是 A，它也要等 A。两个构造器互相等待，谁都执行不完，连原始对象都拿不到，addSingletonFactory 那一步根本走不到，缓存里没有东西可查。Spring 在实例化阶段就会抛 BeanCurrentlyInCreationException，应用直接启动失败。

和字段注入对比一下更清楚。字段和 setter 注入是先调无参构造把空对象造出来，再做属性填充，实例化和注入是两个阶段，中间存在窗口可以把引用提前暴露出去；构造器注入把依赖塞进了实例化内部，实例化本身就要求依赖已经存在，窗口根本不存在。

追问问那官方为什么还推荐构造器注入。两个理由。一是它能强制暴露循环依赖，让设计问题在启动时就炸出来，而不是被三级缓存掩盖、上线后才以诡异的方式暴露；二是能配合 final 保证依赖不可变，字段注入是先创建空对象再反射赋值，final 字段根本不给你这个机会。所以推荐构造器注入不是因为它能解决循环依赖，恰恰是因为它解决不了，反过来逼你把循环依赖改掉。

追问二问 prototype 为什么也不行。prototype 不进缓存，每次 getBean 都新建实例，没有地方存早期引用，也就没有可以提前暴露的对象，闭环照样断不了，Spring 遇到 prototype 的循环依赖同样直接抛异常。另外从 Spring Boot 2.6 开始循环依赖默认是被禁止的，spring.main.allow-circular-references 默认为 false，要保留旧行为必须显式打开，这一点面试时提到能加分。

追问三问构造器注入形成循环依赖时到底会发生什么，能不能举个例子。假设 A 的构造器需要 B，写成 public A(B b)，B 的构造器又需要 A，写成 public B(A a)。容器启动时要创建 A，A 还没有实例化出来，无法提前放入三级缓存，因为构造器本身就需要 B 作为参数。于是 Spring 先去创建 B，B 的构造器需要 A，这时 A 处于正在创建的状态，但三级缓存里还没有 A 的工厂，因为 A 的对象根本没有生成过。此时 beforeSingletonCreation 检测到 A 已经在 singletonCurrentlyInCreation 中，又被再次请求创建，且无法提供任何早期引用，就直接抛出 BeanCurrentlyInCreationException，启动失败。对比 setter 注入，A 先通过无参构造器完成实例化，就能把工厂放入三级缓存，B 拿到的正是这个工厂生成的早期引用，循环因此可以被打破。

追问四问那为什么不给 A 先创建一个工厂。不是 Spring 不想给 A 创建工厂，而是 A 的对象在构造器执行完成之前根本不存在，工厂就无从谈起。三级缓存里的 ObjectFactory 需要引用一个已经实例化的原始对象，才能在 getObject 时返回它或生成它的代理。而构造器注入时，A 的实例化本身就需要先拿到 B 作为参数，B 又需要 A，这是一个无法先完成的前置条件，JVM 没有办法在调用 A 的构造器之前得到 A 的引用。埋点只能发生在实例化之后，而构造器注入中实例化和依赖注入发生在同一步，中间没有可以埋点的窗口。如果 Spring 强行为未实例化的 A 提供引用，只能返回 null 或占位对象，这会让 B 持有无效引用，违背 Bean 完整性的原则，因此选择在启动阶段直接报错。

追问五问注入方式不同，创建一个 Bean 的步骤也不同吗。是的，关键在于实例化和属性注入能不能被拆开。以 AbstractAutowireCapableBeanFactory 为例，创建 Bean 大致分三步：第一步是实例化，调用构造器得到原始对象，由 createBeanInstance 负责；第二步是属性填充，通过 populateBean 把依赖注入到字段或 setter 中；第三步是初始化，执行 Aware 回调、BeanPostProcessor 的前后处理以及 init 方法。在 setter 或字段注入的场景下，这三步有先后顺序，实例化完成后对象已经存在，Spring 可以在属性填充之前把工厂放入三级缓存，这个窗口正是提前暴露的基础。构造器注入则把依赖解析放进了第一步，createBeanInstance 在调用构造器前就要解析出 B，而解析 B 又需要 A，两者的实例化彼此卡住，中间没有可拆分的阶段。Spring 的生命周期框架对两种方式是一样的，变化的只是依赖在哪一步被满足。

### **26. @Async 是什么，它是怎么工作的？**

@Async 是 Spring 提供的注解，作用是让被标注的方法在独立的线程中异步执行，调用方不必等待方法返回就能继续往下走。使用时需要配合 @EnableAsync，通常放在配置类上开启异步功能。这个注解本身只是元数据，不会改变方法的业务逻辑。Spring 启动时通过反射读取它，然后为所在的 Bean 生成代理对象。调用方拿到的其实是代理，代理在方法调用前后插入异步逻辑，把任务交给线程池执行。所以它的本质是 AOP 加线程池，底层由 AsyncAnnotationAdvisor 和 AsyncExecutionInterceptor 完成调度。

使用时最容易出错的是自调用问题。同一个类中一个普通方法直接调用另一个 @Async 方法，调用不经过代理，所以仍然是同步执行的，解决办法是把异步方法拆到另一个 Bean 中，或者通过注入自身代理来调用。异步方法通常需要是 public 的，且不能是 final，否则代理无法正常拦截。另外，ThreadLocal 数据、安全上下文、MDC 日志链路 ID 都不会自动传递到新线程，需要通过 TaskDecorator 手动传递。异步方法还会在新线程里开启独立的事务，无法加入调用方的事务，调用方回滚时异步方法的操作不会跟着回滚，设计时必须考虑清楚。

### **27. 返回值为 void 的 @Async 方法，异常怎么处理？**

如果 @Async 方法返回 void，调用方无法拿到方法的执行结果，方法里抛出的异常也不会传回调用方，因为调用方早已继续往下执行了。这类异常只能由 Spring 的 AsyncUncaughtExceptionHandler 接口来捕获和处理，通常需要实现 AsyncConfigurer 接口并重写 getAsyncUncaughtExceptionHandler 方法来注册自定义的处理逻辑，比如记录日志或发送告警。

对比来看，如果方法返回 Future 或 CompletableFuture，异常会包装在返回的对象里，调用方调用 get 或 join 时就能感知到，所以不需要这个处理器兜底。

### **28. 什么叫自动配置的 ThreadPoolTaskExecutor？**

ThreadPoolTaskExecutor 是 Spring 对 Java 线程池的封装，它实现了 TaskExecutor 接口，底层持有一个 ThreadPoolExecutor。所谓自动配置，指的是引入 Spring Boot 的相关启动器后，不需要自己编写 Bean 定义，Spring Boot 会根据 application.yml 中 spring.task.execution 相关的配置，自动创建一个 ThreadPoolTaskExecutor 并注册到容器中，Bean 名通常是 taskExecutor。这一过程由 TaskExecutionAutoConfiguration 完成，它只在用户没有自定义 TaskExecutor 时才生效，一旦你自己声明了线程池 Bean，它就会退让。

@Async 在没有指定线程池名称时，会优先查找容器中唯一的 TaskExecutor 或名为 taskExecutor 的 Bean，因此自动配置出来的线程池会被默认使用。如果容器里没有任何执行器，Spring 才会退化为 SimpleAsyncTaskExecutor，它每次都新建线程且不复用，这正是生产环境要避免的情况。自动配置的默认参数比较保守，具体数值会随 Spring Boot 版本变化，实际项目中应以官方文档或源码为准。因此生产环境通常还是建议自己定义线程池，明确核心线程数、最大线程数、队列容量和拒绝策略，并通过 @Async("线程池Bean名") 指定使用它。