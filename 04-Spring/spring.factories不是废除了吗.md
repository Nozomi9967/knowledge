


### **spring.factories 没有被整体废除——Spring Boot 3.0 废掉的只是「用 spring.factories 注册自动配置类」这一项功能；Initializer、Listener、EnvironmentPostProcessor 等扩展点在 3.x 里至今仍然注册在 spring.factories 中**

#### **准确的时间线：废除的是「一项用途」，不是「这个文件」**

Spring Boot 2.7 引入了 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，同时在 spring.factories 里注册自动配置类的写法被标记废弃，两个文件并存过渡；3.0 正式移除了对 `EnableAutoConfiguration` 这个 key 的 spring.factories 支持，这是官方 GitHub issue 里明确的决定，自动配置类从此只能走 imports 文件。[GitHub](https://github.com/spring-projects/spring-boot/issues/29699)

但注意范围：被移除的仅仅是「自动配置类」这一种注册。spring.factories 文件本身和 `SpringFactoriesLoader` 这套加载机制在 3.x 里继续服役，职责划分变成了下面这样：

| 注册的内容 | Boot 2.7 之前 | Boot 3.x 现状 |
| --- | --- | --- |
| 自动配置类（xxxAutoConfiguration） | spring.factories | **AutoConfiguration.imports** |
| ApplicationContextInitializer | spring.factories | spring.factories（未变） |
| ApplicationListener | spring.factories | spring.factories（未变） |
| EnvironmentPostProcessor | spring.factories | spring.factories（未变） |
| FailureAnalyzer、PropertySourceLoader 等 | spring.factories | spring.factories（未变） |

证据很直接：Spring Boot 3.3 的 EnvironmentPostProcessor 官方 Javadoc 仍写着「必须在 META-INF/spring.factories 中注册」，3.5 的官方 How-to 文档也还在教你往 spring.factories 里配 EnvironmentPostProcessor。[Spring API Docs](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/env/EnvironmentPostProcessor.html) [Spring Boot Docs](https://docs.spring.io/spring-boot/how-to/application.html)

所以我上一条回答里说「构造 SpringApplication 时从 spring.factories 加载 Initializer 和 Listener」，在 2.x 和 3.x 里都是成立的——3.x 的 SpringApplication 构造器源码至今仍然是 `getSpringFactoriesInstances(ApplicationContextInitializer.class)` 和 `getSpringFactoriesInstances(ApplicationListener.class)`，底层就是读 spring.factories。这一点不冲突。

#### **为什么只把自动配置迁走**

官方拆分的主要动机有两个：一是性能，imports 文件里只有全限定类名、一种类型一个文件，不用像 spring.factories 那样把所有 key 的配置全量解析；二是静态可分析性，AOT / GraalVM native image 需要在构建期就确定要加载哪些类，纯粹一列类名的 imports 文件对构建工具和 AOT 处理器友好得多。另外 Spring Framework 6.0 也给 SpringFactoriesLoader 增强了从任意自定义文件加载的能力，为后续各扩展点逐步拆文件铺路——但截至 3.x，这个迁移只完成了自动配置这一块。[CSDN](https://blog.csdn.net/AWen_Jack/article/details/146455281) [博客园](https://www.cnblogs.com/jingzh/p/18793407)

#### **面试时怎么说最稳**

「废除了吗」这个问题本身就是面试官爱挖的坑，标准答法是把粒度说清：

> spring.factories 没有 abolished，被移除的只是自动配置注册。2.7 开始自动配置类迁到 `META-INF/spring/...AutoConfiguration.imports`，3.0 起旧写法彻底失效；但 SpringApplication 构造时加载 Initializer 和 Listener、ConfigDataEnvironmentPostProcessor 加载配置文件用的 PropertySourceLoader、还有 FailureAnalyzer 这些扩展点，在 3.x 里依然注册在 spring.factories，走的还是 SpringFactoriesLoader。

这样答能同时展示两件事：你知道 3.0 的变化（知识是新的），也知道变化的确切边界（理解是准的）。反过来，如果面试时笼统地说「spring.factories 废了」，遇到追问「那 3.x 里 EnvironmentPostProcessor 注册在哪」就会当场卡住——这正是把「听说过迁移」和「真读过源码」区分开的那一问。