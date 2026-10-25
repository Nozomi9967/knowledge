---
tags:
  - springboot
  - springmvc
  - 自动配置
created: 2026-10-20
updated: 2026-10-20
source: "[[SpringBoot热门面试题-优化纯净版]] Q17 展开"
---

# SpringBoot 静态资源处理详解

## 一句话定位

静态资源是内容固定、不需要服务端动态计算的文件（`index.html`、`style.css`、`app.js`、`logo.png`）。Spring Boot 靠 **WebMvcAutoConfiguration** 注册资源处理器，让这些文件不写 Controller 就能通过 HTTP 直接访问。

## 工作原理

Spring MVC 收到请求后，先由 HandlerMapping 查找能处理该路径的 Controller。**如果没有任何 `@RequestMapping` 匹配**，请求才落到静态资源处理器上。Boot 注册的这个处理器映射在 `/**` 路径下，但**优先级最低**，所以已有的 Controller 路由不会被它覆盖。

底层实现是 `ResourceHttpRequestHandler`：按顺序在若干类路径位置查找与请求路径同名的文件，找到就直接返回内容，并自动设置 `Content-Type`、支持 `Last-Modified` 和 `ETag` 等缓存相关响应头。

> 这条"先 Controller、后静态资源"的顺序，与 [[SpringBoot热门面试题-优化纯净版]] Q17 说的 WebMvcAutoConfiguration 装配 HandlerMapping 是同一条链——静态资源处理器也是一种 HandlerMapping 能命中的 Handler。

## 默认资源目录

Boot 默认在以下类路径目录中查找（**按此顺序，靠前的优先**，多个目录有同名文件时先找到的生效）：

| 顺序 | 类路径目录 |
|:---:|------------|
| 1 | `classpath:/META-INF/resources/` |
| 2 | `classpath:/resources/` |
| 3 | `classpath:/static/` |
| 4 | `classpath:/public/` |

以 `src/main/resources/static/css/app.css` 为例，访问地址是 `http://localhost:8080/css/app.css`——**目录前缀 `static` 不出现在 URL 中**（URL 只保留 `static/` 里面的相对路径）。

另外，默认目录中存在 `index.html` 时，它被当作**欢迎页**，访问根路径 `/` 自动展示。

## 常用配置项

```yaml
spring:
  mvc:
    static-path-pattern: /static/**   # 修改静态资源的访问路径前缀
  web:
    resources:
      static-locations: classpath:/custom-static/  # 替换默认的查找目录
      add-mappings: false                          # 关闭静态资源映射
      cache:
        period: 3600s                              # 设置浏览器缓存时长
```

注意 `static-path-pattern` 只是改 URL 的匹配规则（响应路径模式），`static-locations` 才是文件在磁盘上的查找位置，两者一个管"URL 长什么样"、一个管"文件放在哪"，配置时别混。

## 与 DispatcherServletAutoConfiguration 的分工

- **WebMvcAutoConfiguration**：负责静态资源处理器——"怎样把文件作为响应返回"
- **DispatcherServletAutoConfiguration**：负责 DispatcherServlet 本身及其默认映射 `/`——"请求能不能进来、映射到哪"

两者分工不同，但在请求处理链上协作：DispatcherServlet 先收请求，自己处理不了（没有 Controller 匹配）再交给资源处理器。

## 面试话术

> Spring Boot 不写 Controller 就能访问 HTML、CSS、JS，靠的是 WebMvcAutoConfiguration 注册的静态资源处理器，底层是 ResourceHttpRequestHandler。请求先由 HandlerMapping 找 Controller，都没有匹配才落到这个处理器上，它映射在 /** 但优先级最低，所以不会抢 Controller 的路由。默认查找目录有四个：META-INF/resources、resources、static、public，按顺序靠前优先，比如 static 下的 css/app.css 直接用 /css/app.css 访问，static 这个前缀不出现在 URL 里。这些都可用 spring.web.resources 和 spring.mvc.static-path-pattern 调整。要注意 DispatcherServlet 和它的映射 / 是 DispatcherServletAutoConfiguration 管的，和 WebMvcAutoConfiguration 分工不同。

## 关联知识点

- [[SpringBoot热门面试题-优化纯净版]] — Q17 WebMvcAutoConfiguration
- [[@SpringBootApplication解析]]
- [[HandlerMapping]]
- [[EnableWebMvcConfiguration]] — 加 `@EnableWebMvc` 会让 WebMvcAutoConfiguration 整体失效，静态资源默认映射也随之丢失
