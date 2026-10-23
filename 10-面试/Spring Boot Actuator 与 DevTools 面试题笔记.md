
### **1. Actuator 是什么？它解决什么问题？**

Actuator 是 Spring Boot 工具层里负责运行时观测的模块，引入 spring-boot-starter-actuator 之后，框架会自动注册一批端点，把应用内部的运行状态通过 HTTP 或 JMX 暴露出来，交给监控系统采集。它不是业务能力，解决的是上线之后看不见应用内部状态的问题，比如应用活没活着、内存和线程池怎么样、某个配置到底有没有生效。常见端点有 health、metrics、info、beans、env、mappings、loggers、threaddump、heapdump。要注意，Actuator 本身只负责暴露数据，真正做监控告警的是 Prometheus、Grafana 这类外部系统。

追问问它和普通日志有什么区别。日志是应用主动输出的事件流，Actuator 是可以随时查询的状态快照和指标，两者互补：日志用来回溯发生了什么，Actuator 用来观察现在是什么状态。

### **2. Actuator 默认暴露哪些端点？怎么开启更多？**

默认情况下，大部分端点虽然已经启用，但通过 Web 暴露的很少，较新的版本里默认只暴露 health，旧版本是 health 和 info，具体以所用版本的官方文档为准。要开放更多端点，用 management.endpoints.web.exposure.include 配置，可以写具体的端点名，比如 health、metrics，也可以写星号全部开放，同时可以用 exclude 排除个别端点。

追问问 health 为什么只显示 UP。因为 health 默认不展示细节，只给总体状态，要看数据库、Redis 等各组件的逐项状态，需要配置 management.endpoint.health.show-details 为 always，或者 when-authorized 配合权限控制。生产环境更推荐 when-authorized，不要对所有人展示细节。

### **3. Actuator 有哪些安全风险？生产环境怎么加固？**

核心风险是敏感端点泄露信息。env 和 configprops 会暴露配置项，beans 和 mappings 会暴露内部结构，heapdump 能下载堆快照，里面可能有密码和用户数据，shutdown 甚至能直接关停应用。加固思路有几层：第一，只暴露必要端点，通常是 health、info 和 metrics；第二，用 management.server.port 把 Actuator 放到独立管理端口，只对内网或运维网段开放；第三，配合 Spring Security 给端点加认证授权；第四，敏感数据靠 sanitize 机制脱敏，不要依赖它兜底。

追问问端点路径能不能改。可以，用 management.endpoints.web.base-path 修改前缀，但这只是增加一点隐蔽性，不能当作安全措施，真正有效的还是网络隔离和认证。

### **4. 怎么用 health 做 K8s 探针？怎么自定义健康检查？**

health 端点汇总各个 HealthIndicator 的结果，只要有一项是 DOWN，整体就是 DOWN，并返回 503 状态码，所以负载均衡或 K8s 可以直接根据 HTTP 状态码判断。较新的版本还支持 liveness 和 readiness 两组探针，存活探针判断进程要不要重启，就绪探针判断要不要把流量切进来，这两个概念要区分开，不要把依赖外部服务的检查放进存活探针，否则外部故障会引发不必要的重启。

追问问怎么自定义。实现 HealthIndicator 接口，在 health 方法里返回 Health.up 或 Health.down，并用 withDetail 附带详情，注册成 Spring Bean 后会自动汇总进 health 端点，也可以继承 AbstractHealthIndicator 简化写法。适合检查第三方接口、消息队列这类依赖，但检查逻辑要轻量并设置超时，否则探针本身会拖垮应用。

### **5. Actuator 怎么对接 Prometheus 做监控？**

metrics 端点背后是 Micrometer，它是一个指标门面，类似监控领域的 SLF4J。引入 micrometer-registry-prometheus 依赖，并开放 prometheus 端点，Actuator 就会在 actuator/prometheus 下输出 Prometheus 格式的指标，Prometheus 定时拉取，再由 Grafana 展示、Alertmanager 告警。指标覆盖 JVM 内存、GC、线程、Tomcat、HTTP 请求耗时、数据源连接池等。

追问问怎么自定义业务指标。注入 MeterRegistry，创建 Counter、Gauge、Timer 等，比如用 Counter 统计下单次数，用 Timer 记录某个方法耗时。要注意 tag 的基数不能太高，比如把用户 ID 作为 tag，会产生海量时间序列，拖垮监控系统。

### **6. 怎么在线上动态修改日志级别？**

用 loggers 端点。先开放 loggers 端点，然后向 actuator/loggers/某个包名或类名发送 POST 请求，请求体里指定 configuredLevel，比如 DEBUG，立即生效，不需要重启，排查完再改回去或者置空恢复默认。它适合线上临时排查问题，但要注意两点：修改只存在于内存，重启后就失效；这个端点具备修改能力，必须放在认证保护之下，否则别人也能随意改你的日志级别。

追问问和配置中心改日志级别有什么区别。配置中心适合持久化和集群统一下发，loggers 端点适合针对单个实例快速临时调整，两者场景不同。

### **7. DevTools 是什么？自动重启的原理是什么？**

DevTools 是只为开发效率服务的模块，核心能力是自动重启，另外还有 LiveReload 和开发期属性默认值。它的原理是双类加载器：base ClassLoader 加载不常变的第三方依赖，restart ClassLoader 加载开发者自己的项目类。检测到 classpath 上的文件变化后，只丢弃并重建 restart ClassLoader，重新加载业务类并重新启动 Spring 容器，依赖 jar 不用重新加载，所以比冷启动快很多。要注意它监听的是 classpath，所以 IDE 必须先把 class 编译出来，IDEA 里需要触发构建或开启自动构建。

追问问它重启时会不会重新创建 Spring 容器。会，整个应用上下文都会重建，所以内存里的状态全部丢失，这也是它和真热部署的本质区别。

### **8. DevTools 是热部署吗？和 HotSwap、JRebel 有什么区别？**

严格说不是，DevTools 是自动重启，不是热部署。HotSwap 是 JVM 调试功能，在不重启进程的情况下替换类字节码，但只能修改方法体，不能增删方法和字段。JRebel 这类商业工具能做到更完整的热替换，并尽量保留状态。DevTools 则是重建类加载器并重启容器，进程不变但应用状态全部丢失。所以回答时要说清：DevTools 速度快于冷启动，但不保留状态，属于重启而不是热替换。

追问问什么时候该用哪个。改动小、只改方法体时，IDE 自带的 HotSwap 就够了；需要频繁改结构又想保留状态，可以考虑 JRebel；日常开发想要零配置的快速反馈，用 DevTools 最省事。

### **9. DevTools 有哪些常见的坑？哪些改动不会触发重启？**

第一，静态资源和模板，比如 static、templates 目录下的文件，默认不触发重启，而是走 LiveReload 刷新浏览器。第二，改 pom 不会触发，因为它不在运行时 classpath 里，需要重新导入依赖并重新构建，甚至手动重启。第三，改 resources 下的 application.yml 会触发重启，这点常被说错。第四，可以通过 spring.devtools.restart.exclude 和 additional-paths 调整监听范围。第五，类加载器分离可能带来类转换异常，比如同一个类被两个加载器加载，遇到这种问题可以在 META-INF 下配置 spring-devtools.properties，把相关 jar 纳入重启加载器。

追问问重启慢怎么办。项目大了之后，重启仍然会慢，这时可以缩小监听范围，或者干脆关闭自动重启，只保留 LiveReload。

### **10. 生产环境怎么保证 DevTools 不生效？**

有双重保险。第一，依赖声明时加上 optional 为 true，这样 DevTools 不会传递给依赖本项目的其他模块；在 Maven 的 Spring Boot 打包插件里，默认也会把 devtools 排除在可执行 jar 之外，除非显式配置 excludeDevtools 为 false。第二，DevTools 在检测到应用是通过 java -jar 这类完整打包方式运行时，会自动禁用重启功能，因为它认为这是生产运行方式。反过来，用 IDE 或者 spring-boot:run 启动时，它会被认为是开发环境而启用。不要依赖自动禁用来兜底，仍然应该在构建层面确保它不进入生产包。

追问问 optional 为 true 具体解决什么问题。它阻止依赖传递，假如你的项目是一个被其他项目引用的公共模块，没有这个设置，别的项目引入你时也会被带进 DevTools，造成意外的重启行为甚至上线风险。