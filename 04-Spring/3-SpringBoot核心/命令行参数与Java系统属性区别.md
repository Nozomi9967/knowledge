---
tags:
  - springboot
  - 配置管理
created: 2026-10-20
updated: 2026-10-20
source: "[[SpringBoot热门面试题-优化纯净版]] Q15 追问展开"
---

# 命令行参数与Java系统属性（-D）区别

## 一句话定位

两者都是"外部覆盖配置"的手段，但**传给的对象不同**：`-D` 是传给 **JVM** 的参数（JVM 自己处理，放进 `System.getProperties()`），`--` 是传给 **Spring Boot 应用**的参数（由 `SpringApplication` 解析后注册成配置源）。

## 对比表

| 维度 | 命令行参数（`--`） | Java 系统属性（`-D`） |
|------|------------------|---------------------|
| 写法 | `--server.port=9090` | `-Dserver.port=9090` |
| 传给谁 | Spring Boot 应用 | JVM |
| 谁解析 | `SpringApplication`（解析 `String[] args`） | JVM 自己，写入 `System.getProperties()` |
| 位置要求 | 必须放在 jar 包**后面** | 必须放在 `-jar` **前面** |
| 读取方式 | Boot 配置体系（Environment） | `System.getProperty()` 或 Boot 配置体系 |
| Boot 优先级 | **最高**（命令行 > 系统属性） | 低于命令行参数 |
| 作用域 | 只影响这个 Boot 应用 | 整个 JVM 全局，任何代码都能读 |

## 实际命令对比

```bash
# 命令行参数：--xxx 跟在 jar 后面
java -jar app.jar --server.port=9090

# 系统属性：-Dxxx 必须在 -jar 之前（它是 JVM 的参数）
java -Dserver.port=9090 -jar app.jar
```

位置放错的后果：

- `-D` 放到 jar 后面 → Boot 把它当成普通的程序 args，JVM 根本收不到，系统属性没设置上
- `--` 放到 jar 前面 → JVM 不认识 `--` 开头的参数，直接启动报错（Unrecognized option）

## 为什么命令行参数优先级更高

Boot 把所有配置来源按固定顺序组成一条 PropertySource 链，命令行参数排在最前面，取值时从前往后找，先找到就返回。设计动机：配置文件是打包前写死的静态内容，而**部署运维阶段经常要临时改端口、改 profile、改开关**，直接加 `--` 参数覆盖就行，不用重新打包或改配置文件，所以它必须压过一切文件类配置。

完整优先级（概括，从高到低）：命令行参数 > Java 系统属性 > 操作系统环境变量 > 带 profile 的配置文件 > application.yml > 代码默认值。（细节以官方文档完整列表为准）

## 三个容易混淆的点

1. **JVM 自身的参数只能走 `-D` / `-X`**。比如调堆大小 `-Xmx2g`、GC 参数 `-XX:+UseG1GC`，这些 Boot 的 `--` 参数完全管不着——`--` 只进入 Boot 的配置体系，对 JVM 没有任何影响。
2. **系统属性是 JVM 全局的**。同一 JVM 里跑的任何代码 `System.getProperty("server.port")` 都能读到；命令行参数本质只是 Boot 收到的 `String[] args`，离开 Boot 配置体系就没意义。
3. **环境变量是第三个易混项**。`SERVER_PORT=9090` 是操作系统层面的变量（注意松散绑定：`server.port` → `SERVER_PORT`），它是给容器化/K8s 部署用的标准方式，优先级低于前两者。

## 面试话术

> 这两个都是外部覆盖配置的方式，区别在于传给谁。`-Dserver.port=9090` 是 JVM 的系统属性，必须写在 `-jar` 前面，由 JVM 处理，整个 JVM 全局可读；`--server.port=9090` 是命令行参数，跟在 jar 包后面，由 SpringApplication 解析成配置源。Boot 的优先级链里命令行参数排最高、系统属性其次，因为运维临时改配置最常用的就是 `--` 形式，Boot 要保证它能压过所有配置文件。另外 JVM 自身的参数比如 `-Xmx` 只能走 JVM 参数，`--` 管不到。

## 关联知识点

- [[SpringBoot热门面试题-优化纯净版]] — Q15 配置属性加载优先级
- [[@SpringBootApplication解析]]
