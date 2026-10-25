---
tags:
  - spring-boot
  - spring-mvc
  - 自动配置
created: 2026-07-02
updated: 2026-07-02
source:
---

# WebMvcAutoConfiguration

> [!abstract] Spring Boot MVC 自动配置
> Spring Boot 在 classpath 中存在 `spring-webmvc` 时,自动配置 Spring MVC 的九大策略组件(HandlerMapping、HandlerAdapter、ViewResolver 等)。

## 核心要点

- 条件注解 `@ConditionalOnClass({ DispatcherServlet.class, WebMvcConfigurer.class })` — 只在有 Spring MVC 时生效
- 条件注解 `@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)` — **只要用户自定义了 `WebMvcConfigurationSupport`(如加了 `@EnableWebMvc`),自动配置整体跳过**
- 通过 `@Import(EnableWebMvcConfiguration.class)` 引入实际的配置类

## 与 @EnableWebMvc 的关系

| 场景 | 结果 |
|------|------|
| 不加 `@EnableWebMvc` | `WebMvcAutoConfiguration` 生效,注册全套默认 Bean |
| 加了 `@EnableWebMvc` | 自动配置被禁用,需自己实现 `WebMvcConfigurer` 定制 |

## 注册的默认 Bean

- `RequestMappingHandlerMapping`
- `RequestMappingHandlerAdapter`
- `ExceptionHandlerExceptionResolver`
- `HttpMessageConverters` (JSON 序列化等)

## 关联知识点

- [[HandlerAdapter]] — 自动配置注册的核心组件之一
- [[WebMvcConfigurer]] — 用户扩展 MVC 配置的接口
- [[EnableWebMvcConfiguration]] — 手动配置时的配置类
