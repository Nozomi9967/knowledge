---
tags:
  - SpringBoot
  - Starter
  - 自动装配
created: 2026-08-10
updated: 2026-08-10
source: 04-Spring/Spring-interview-guide.md
背诵状态: 已完成
背诵开始: 2026-08-10 13:50
背诵结束: 2026-08-10 14:05
---

# 自定义 SpringBoot Starter 详解（带例子）

> [!abstract] SpringBoot Starter — Spring Boot 启动器
> Starter 是 Spring Boot 的"依赖打包 + 自动配置"机制：把某个功能需要的所有依赖和默认配置封装成一个 jar，别人引入后开箱即用。本文用「短信发送 starter」的例子把 6.3 的七个步骤走一遍。

---

## 场景设定

我们要做一个 `sms-spring-boot-starter`：别人引入它、配几行参数，就能直接注入一个 `SmsService` 发短信。

```
sms-spring-boot-starter/
├── pom.xml
└── src/main/java/com/example/sms/
    ├── SmsProperties.java      ← 配置属性类
    ├── SmsService.java         ← 服务类（业务逻辑）
    ├── SmsAutoConfiguration.java ← 自动配置类
    └── SmsAutoConfiguration.imports ← 注册文件（resources/META-INF/spring/ 下）
```

---

## 第 1 步：创建 Maven 项目，引入两个依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
```

**为什么是这两个？** `spring-boot-autoconfigure` 提供自动配置相关的注解（`@ConditionalOnMissingBean` 等）；`spring-boot-starter` 是基础 starter，传递引入 spring-core 等。注意：**不用引 spring-boot-maven-plugin**，starter 不需要打成可执行 jar，打成普通 jar 就行。

## 第 2 步：写配置属性类，绑定配置前缀

```java
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {
    private String accessKey;    // sms.access-key
    private String secretKey;    // sms.secret-key
    // getter / setter 省略
}
```

**作用**：别人在 application.yml 里写 `sms.access-key=xxx`，Spring 会自动绑定到这个类的字段上。

```yaml
# 使用方配置
sms:
  access-key: my-access-key
  secret-key: my-secret-key
```

## 第 3 步：写服务类

```java
public class SmsService {
    private final SmsProperties properties;

    public SmsService(SmsProperties properties) {
        this.properties = properties;
    }

    public void send(String phone, String content) {
        // 用 properties 里的密钥调短信平台 API
        System.out.println("发送短信到 " + phone + "：" + content);
    }
}
```

**注意**：服务类**不加 `@Component`**，它不应该被组件扫描捡到（别人可能根本扫描不到你的包），而是由自动配置类显式创建。

## 第 4 步：写自动配置类

```java
@Configuration
@EnableConfigurationProperties(SmsProperties.class)  // 让 SmsProperties 成为 Bean
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean   // 使用者自己定义了 SmsService 就不创建
    public SmsService smsService(SmsProperties properties) {
        return new SmsService(properties);
    }
}
```

**关键点**：
- `@EnableConfigurationProperties`：让 `@ConfigurationProperties` 的类注册成 Bean
- `@ConditionalOnMissingBean`：别人想用自己的实现时，条件不满足，我们的 Bean 不生效——**给使用方留了覆盖口子**
- 还可以加 `@ConditionalOnProperty(prefix = "sms", name = "enabled")`：配置了才生效

## 第 5 步：创建注册文件

Spring Boot 需要知道"自动配置类有哪些"，通过类路径下的元数据文件注册：

**Spring Boot 3.x / 2.7+**：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.example.sms.SmsAutoConfiguration
```

（Spring Boot 2.7 前是在 `META-INF/spring.factories` 里写 `EnableAutoConfiguration=...`，3.0 已彻底废弃。）



注册文件是自动配置类与 Spring Boot 之间的"注册表"：Spring Boot 启动时通过 `AutoConfigurationImportSelector` 扫描类路径下所有 `META-INF/spring/*.imports` 文件，把里面声明的自动配置类加载进候选集合，再经过条件注解筛选，最终生效。相当于把组件扫描覆盖不到的 jar 内配置类，显式登记给 Spring Boot 知道。

## 第 6 步：打包

```bash
mvn install
```

打包成普通 jar 装进本地仓库（或 deploy 到私服）。

## 第 7 步：别人使用

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>sms-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
@RestController
public class UserController {
    @Autowired
    private SmsService smsService;   // 直接用！不用自己 new

    @PostMapping("/send")
    public String send(@RequestParam String phone) {
        smsService.send(phone, "验证码：123456");
        return "ok";
    }
}
```

---

## 完整工作流程（use 侧）

```
引入 starter 依赖
  → 启动时 Spring Boot 读 META-INF/spring/*.imports
  → 发现 SmsAutoConfiguration
  → @ConditionalOnMissingBean 判断通过
  → 创建 SmsProperties Bean（绑定 yml 里的 sms.* 配置）
  → 创建 SmsService Bean
  → 使用方 @Autowired 注入即用
```

## 面试话术

> 自定义 Starter 本质就是"自动配置类 + 注册文件"。核心三步：写自动配置类用 `@Bean` + `@ConditionalOnMissingBean` 注册服务；写 `@ConfigurationProperties` 绑定外部配置；在 `META-INF/spring/` 下用 AutoConfiguration.imports 注册自动配置类。`@ConditionalOnMissingBean` 很关键——保证使用方可以覆盖我们的默认实现。

## 关联知识点

- [[Spring-interview-guide]] 6.3 如何自定义一个 SpringBoot Starter
- [[@SpringBootApplication解析]] — @EnableAutoConfiguration 的触发机制
- [[WebMvcAutoConfiguration]] — 官方 Starter 的自动配置类长什么样

## 背诵记录

| 开始时间 | 结束时间 | 内容 | 状态 |
|------|------|------|------|
| 2026-08-10 13:50 | 2026-08-10 14:05 | 自定义 SpringBoot Starter 核心为自动配置类加注册文件，需通过@ConfigurationProperties 绑定配置、@ConditionalOnMissingBean 控制 Bean 创建，并在 META-INF/spring/ 目录下注册自动配置类实现自动装配。 | 第一轮背记完成 |
