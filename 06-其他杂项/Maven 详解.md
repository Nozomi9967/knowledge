---
tags:
  - Maven
  - 构建工具
  - 工程实践
created: 2026-10-03
updated: 2026-10-03
source: Maven 教程会话 + SpringBoot 内嵌 Tomcat / scope 追问
---

# Maven 详解

## 一、Maven 是什么？解决什么问题？

Maven 是 Java 项目的**构建与依赖管理工具**：按约定组织项目、自动下载依赖、把编译/测试/打包串成标准流程。

不用 Maven 时，团队常要手动下载 JAR、处理版本冲突、自己写编译打包脚本。Maven 把这些工作集中到 `pom.xml` 里，让同一项目能用一致命令构建。

核心记四件事：**POM 描述项目，坐标标识项目，依赖声明要用的库，生命周期组织构建步骤。**

## 二、POM 与坐标

`pom.xml`（Project Object Model）是项目根目录的主要配置文件。坐标唯一标识一个构件：`groupId:artifactId:version`。

```text
com.example:hello-app:1.0-SNAPSHOT
```

| 字段 | 含义 |
|:---|:---|
| `groupId` | 组织/项目所属命名空间 |
| `artifactId` | 项目或模块名 |
| `version` | 版本号；`SNAPSHOT` 表示开发中的非正式版（可能继续变化） |
| `packaging` | 产物格式：`jar`/`war`；未指定时默认 `jar` |

**标准目录结构**（按约定识别，不必额外告诉 Maven 源码在哪）：

```text
my-app/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/       # 应用源码
    │   └── resources/  # 应用资源
    └── test/
        ├── java/       # 测试源码
        └── resources/  # 测试资源
```

**仓库：**
- **本地仓库**：默认 `~/.m2/repository`，下载过的依赖缓存在这。
- **远程仓库**：如 Maven Central，按坐标查找构件。
- **私服**：团队/组织内部的远程仓库，代理外部仓库 + 存放内部构件。

## 三、一个简单应用

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>hello-app</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

创建 `src/main/java/com/example/App.java` 后依次运行：

```bash
mvn compile      # 编译主源码
mvn test         # 运行测试（会先执行前面所需阶段）
mvn package      # 打到 JAR，一般在 target/
mvn clean        # 清理 target 输出
mvn clean package
mvn install      # 安装到本地仓库，供本机其他项目用
mvn deploy       # 发布到配置的远程仓库
```

上面 JUnit 依赖：这段配置引入 **JUnit Jupiter 5.11.0 测试框架**（`org.junit.jupiter:junit-jupiter`，`scope=test`），只用于测试编译/运行，不会作为应用运行时依赖打进产物。

## 四、生命周期、插件与依赖管理

### 生命周期：阶段按顺序组织构建
三套内置：`clean`、`default`、`site`。日常最常用 `default`：

```text
validate → compile → test → package → verify → install → deploy
```

运行某阶段会执行它之前的相关阶段（例如 `mvn package` 会先 compile、test，再打包）。

### 插件：实际执行工作的组件
生命周期阶段规定"走到哪一步"，**插件**提供具体操作（编译、跑测试、打包都是插件目标）。可以理解为：**生命周期管顺序，插件管执行。** 构建插件在 `<build><plugins>` 配置；要统一版本/默认配置用 `<pluginManagement>`。

### 依赖范围 scope（重点）
`scope` 决定依赖在哪些阶段可用。用户常被"通常/也可能"这类模糊说法绕晕，这里给**精确四维度**判断表：

| 范围 | 当前项目编译主代码 | 当前项目测试 | Maven 是否打进应用运行时包 | 传给下游项目 |
|:---|:---:|:---:|:---:|:---:|
| `compile`（默认） | ✅ | ✅ | ✅ | **会** |
| `provided` | ✅ | ✅ | **❌**（由运行环境提供） | **不会** |
| `runtime` | ❌ | ✅ | ✅ | 会 |
| `test` | ❌ | ✅ | ❌ | 不会 |

- **`compile`**：进编译/测试/运行 classpath，省略 `scope` 即默认它；下游依赖当前项目时**会传递给下游**。
- **`provided`**：进编译/测试 classpath，但 Maven **不把它作为应用运行时依赖**打进包，也**不传给下游**——前提是运行环境确实提供它（如 Servlet API 由 Tomcat 提供）。若运行环境没提供，运行时可能因缺类报错。
- **`runtime`**：编译主代码不需要，运行时需要（如 JDBC 驱动）。
- **`test`**：仅测试编译/运行需要（如 JUnit）。

> [!note] 表述精确化
> 不要说"运行时是否由 Maven 提供"（易误解成 Maven 在运行时给依赖）。应说成：**应用包里是否包含这个依赖**——`compile` 包含；`provided` 不包含，由运行环境（如 Tomcat）另外提供。
>
> **"运行环境"** = 程序启动/运行时实际提供所需条件的地方：普通 Java 程序是 **JVM**；Web 应用可能是 **Tomcat**（它启动应用并提供相关类库）。

### 依赖传递、排除与冲突
A 依赖 B、B 又依赖 C 时，C 作为**传递依赖**可能被带入。排查先看依赖树：

```bash
mvn dependency:tree
```

不想要的传递依赖（如 `org.example:unwanted-library`）可在对应依赖下排除：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-library</artifactId>
    <version>1.2.3</version>
    <exclusions>
        <exclusion>
            <groupId>org.example</groupId>
            <artifactId>unwanted-library</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

冲突时**别只凭猜测改版本**，先 `mvn dependency:tree` 看引入路径，再用版本管理或排除策略解决。

### 多模块与版本统一
一个父 POM 聚合多个模块，父项目用 `<packaging>pom</packaging>`。两个易混配置：

- **`<dependencies>`**：声明当前项目**实际使用**的依赖。
- **`<dependencyManagement>`**：集中管理依赖**版本/默认信息**，供子模块继承或采用；**它本身不代表子模块自动引入**这些依赖——子模块仍要在自己的 `<dependencies>` 里声明要用的依赖。
- 类似地，`<pluginManagement>` 集中管理插件配置，插件是否实际应用仍看具体声明。

## 五、SpringBoot 内嵌 Tomcat 与 provided（重点）

**默认 Spring Boot Web 应用，不是把 JAR 部署到外部 Tomcat，而是把内嵌 Tomcat 一起打进可执行 JAR。**

```text
spring-boot-starter-web
        ↓ 间接引入
spring-boot-starter-tomcat
        ↓
打包进 Spring Boot 可执行 JAR（运行 java -jar app.jar 时由应用启动内嵌 Tomcat）
```

| 部署方式 | Tomcat 来源 | scope 对应 |
|:---|:---|:---|
| **默认可执行 JAR** | 内嵌 Tomcat 在应用包里，随包带上 | **`compile`**（作为应用运行依赖一起带上） |
| **打 WAR 部署到外部 Tomcat** | 外部已单独安装并运行的 Tomcat 提供 | **`provided`**（应用包不再带一份 Tomcat） |

> 你之所以觉得"当前 JAR 本身就包含 Tomcat"，正是默认 Spring Boot Web 应用的常见情况——这说得对。`provided` 是另一种部署方式（WAR 放外部 Tomcat）才用的。

## 六、Maven Wrapper（mvn vs mvnw）

Maven Wrapper = **项目自带的 Maven 启动器**。平时 `mvn package` 用电脑已装的 Maven，不同开发者版本可能不同；项目带 Wrapper 后改用 `./mvnw package`（macOS/Linux）或 `mvnw.cmd package`（Windows），会**按项目指定版本**启动 Maven，版本不对就先下载再跑。项目目录常见 `mvnw`、`mvnw.cmd`、`.mvn/wrapper/`。

> 它**不是依赖库，也不是运行时需要的东西**，只是构建时用。简单记：**`mvn` = 电脑装的 Maven；`mvnw` = 项目指定的 Maven。**

## 七、常见实践与排错

- 重要依赖/插件用统一版本管理，避免各模块声明不同版本。
- 团队用 Maven Wrapper 统一 Maven 版本。
- 改 POM 后遇依赖问题，按顺序查：坐标对不对 → 版本是否存在 → scope 是否合适 → 仓库配置/网络，再 `mvn dependency:tree` 看传递依赖。

## 八、面试题与参考回答

1. **Maven 是什么、解决什么？** —— 构建与依赖管理工具；用 POM 描述配置、坐标定位构件、生命周期+插件组织编译测试打包。
2. **坐标组成部分？** —— `groupId:artifactId:version`，`packaging` 说明产物类型。
3. **生命周期和插件的区别？** —— 生命周期定义有序构建阶段，插件提供具体任务实现；执行阶段时调用对应插件目标。
4. **`mvn package` 会发生什么？** —— 执行 `default` 生命周期到 `package` 为止的前序阶段（验证/编译/测试/打包），生成构件。
5. **`dependencies` 和 `dependencyManagement` 区别？** —— 前者声明实际使用的依赖；后者集中管理版本，供继承，但不自动引入依赖。
6. **`compile/provided/runtime/test` 差异？** —— 看四维度表：编译/测试/是否打进运行包/是否传下游。
7. **依赖传递？如何排查冲突？** —— 依赖的依赖可能被带入；`mvn dependency:tree` 看路径，再统一版本或排除。
8. **`install` 和 `deploy` 区别？** —— install 装到本地仓库；deploy 发布到远程仓库（团队共享/发布）。
9. **`SNAPSHOT` 是什么？** —— 开发中的非正式版，可能继续变化；注意仓库更新策略。
10. **父 POM 和多模块聚合的区别？** —— 父 POM 强调配置复用/继承；聚合强调从一个入口构建多个模块；常放同一 POM，但概念不同。

**实践顺序：** 手写 POM 跑 `compile`/`test`/`package` → 观察依赖树 → 拆一个父子多模块项目。

## 关联知识点

- [[SpringBoot 热门面试题]] — 内嵌 Tomcat、自动装配与 starter 原理
- [[spring八股]] — Spring Boot 与 Spring Cloud Alibaba 依赖关系、starter 机制
- [[Git 详解]] — 同为工程工具（版本控制），与构建工具互补
- [[笔试]] — 常见零散笔试/实操知识点
