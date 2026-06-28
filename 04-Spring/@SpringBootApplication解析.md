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