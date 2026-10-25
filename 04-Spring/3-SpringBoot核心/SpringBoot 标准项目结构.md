---
tags:
  - springboot
  - maven
  - 项目结构
created: 2026-08-27
updated: 2026-08-27
source:
---

# SpringBoot 标准项目结构（Maven）

> 一个标准的 Maven + Spring Boot 项目结构，含启动类位置、包分层、resources 约定，面试常问"项目结构怎么分"。

## 目录结构

```
my-app/
├── pom.xml                          # Maven 构建配置
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myapp/   # 包结构（主类所在包）
│   │   │       ├── MyApplication.java       # 启动类（@SpringBootApplication）
│   │   │       ├── controller/               # 控制层（接收 HTTP 请求）
│   │   │       ├── service/                  # 业务层（业务逻辑）
│   │   │       ├── mapper/ 或 repository/    # 数据访问层（MyBatis/JPA）
│   │   │       ├── entity/ 或 model/         # 实体类（POJO/DO）
│   │   │       ├── dto/                      # 传输对象（前端交互）
│   │   │       ├── vo/                       # 视图对象（返回给前端）
│   │   │       ├── config/                   # 配置类（@Configuration）
│   │   │       ├── common/ 或 util/          # 通用工具、统一返回、异常处理
│   │   │       └── aspect/                   # 切面（日志、事务等）
│   │   └── resources/
│   │       ├── application.yml               # 主配置文件
│   │       ├── application-dev.yml           # 开发环境配置（profile）
│   │       ├── application-prod.yml          # 生产环境配置
│   │       ├── static/                       # 静态资源（HTML/CSS/JS，前后端不分离时）
│   │       ├── templates/                    # 模板文件（Thymeleaf 等）
│   │       ├── mapper/                       # MyBatis XML 映射文件
│   │       ├── logback-spring.xml            # 日志配置
│   │       └── banner.txt                    # 启动 banner（可选）
│   └── test/
│       └── java/
│           └── com/example/myapp/
│               └── MyApplicationTests.java   # 单元测试
└── .gitignore, README.md 等
```

## 核心约定

### 1. 启动类必须放在包最外层

`MyApplication` 放在 `com.example.myapp` 根下。`@SpringBootApplication` 里的 `@ComponentScan` 默认扫描**主类所在包及子包**，放错位置会导致 Controller / Service 扫不到。

### 2. 包结构分层职责清晰

controller（web 层）→ service（业务层）→ mapper/repository（数据层）的三层架构是最主流的分层，面试常让画结构或问职责边界。

| 层 | 包 | 职责 |
|----|----|------|
| Web 层 | `controller/` | 接收 HTTP 请求、参数校验、调用 service |
| 业务层 | `service/` | 业务逻辑、事务边界 |
| 数据层 | `mapper/` / `repository/` | 数据库访问 |

### 3. resources 下的固定目录名

`static/`（静态资源）、`templates/`（模板）、`application.yml`（配置）是 Spring Boot 的默认约定，目录名不能乱改，改名需要手动配置。

### 4. 主配置多环境拆分

公共配置放 `application.yml`，环境差异用 `application-{profile}.yml`（见 [[SpringBoot 热门面试题#14. Spring Boot 多环境配置怎么实现？]]）。

### 5. test 目录与 main 目录镜像

测试类路径与主代码包名保持一致，方便定位和 Maven 默认构建规则。

## 关联知识点

- [[@SpringBootApplication解析]] — 启动类上的组合注解 + 包扫描起点
- [[SpringBoot热门面试题-优化纯净版]] — SpringBoot 全域面试题（Q5 启动流程、Q14 多环境）
- [[自定义SpringBootStarter详解]] — Starter 依赖与自动配置（依赖放 classpath 上，条件注解检测）
