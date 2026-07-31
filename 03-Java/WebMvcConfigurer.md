---
tags:
  - spring-mvc
  - 扩展点
  - 配置
created: 2026-07-02
updated: 2026-07-02
source:
---

# WebMvcConfigurer

> [!abstract] WebMvcConfigurer — Spring MVC 配置扩展接口
> 允许用户在不引入 `@EnableWebMvc`(不关闭自动配置)的前提下,对 Spring MVC 进行定制化配置。

## 核心要点

- 实现该接口并注册为 Spring Bean,Spring 会自动检测并回调
- **不需要加 `@EnableWebMvc`** — 加的话会关闭 Boot 自动配置,导致很多默认 Bean 丢失
- 所有方法都是 `default` 方法,按需覆写即可

## 常用方法

| 方法 | 用途 |
|------|------|
| `addViewControllers` | 简化 ViewController 注册(如 `/` → `index`) |
| `addInterceptors` | 注册拦截器 |
| `addCorsMappings` | 跨域配置 |
| `configureMessageConverters` | 自定义消息转换器 |
| `addArgumentResolvers` | 自定义参数解析器 |
| `addReturnValueHandlers` | 自定义返回值处理器 |
| `configurePathMatch` | 路径匹配配置(如后缀匹配) |

## 与 EnableWebMvc 的区别

```java

// ✅ 推荐:只实现 WebMvcConfigurer,不关自动配置

@Configuration

public class WebConfig implements WebMvcConfigurer {

@Override

public void addInterceptors(InterceptorRegistry registry) {

registry.addInterceptor(new LoginInterceptor()).addPathPatterns("/**");

}

}

// ⚠ 不推荐:加了 @EnableWebMvc 会关闭自动配置

@EnableWebMvc  // 这行会导致 WebMvcAutoConfiguration 整体跳过

@Configuration

public class WebConfig implements WebMvcConfigurer { ... }

```

## 关联知识点

- [[HandlerAdapter]] — 可通过 `addArgumentResolvers` 扩展
- [[WebMvcAutoConfiguration]] — 不引入 `@EnableWebMvc` 时生效的自动配置
- [[HandlerInterceptor]] — 通过 `addInterceptors` 注册
