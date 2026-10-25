---
tags:
  - 其他
created: 2026-06-28
updated: 2026-06-28
---

# 为啥Paths.get("data","test.txt")结果的路径是项目根目录下的data目录 我的项目是微服务架构，一个父工程下有若干子工程 为啥在子工程也就是微服务中执行Paths.get("data","test.txt") 它的路径竟然是跟本身服务平级的data目录

因为 `Paths.get("data", "test.txt")` 是**相对路径**，它相对的是 **JVM 的工作目录（working directory）**，也就是 `System.getProperty("user.dir")`。

## 为什么是父工程根目录

用 IDE（IDEA）启动时，**working directory 默认被设置为父工程根目录**，而不是子模块目录。

```
nozomi-mall/                ← user.dir 在这里
├── mail-service/
│   └── src/...
├── order-service/
└── data/test.txt           ← 所以文件写到这里了
```

## 验证一下

```java
System.out.println(System.getProperty("user.dir"));
```

跑一下就能看到实际的 working directory 是哪里。

## 解决方案

**方案一：改 IDEA 的 working directory 配置**

Run Configuration → Working directory → 改为 `$MODULE_WORKING_DIR$`，这样就是子模块自己的目录。

**方案二：用绝对路径（推荐生产环境）**

```java
// 通过配置文件注入
@Value("${attachment.save-path:/tmp/attachments}")
private String savePath;
```

```yaml
# application.yml
attachment:
  save-path: /data/nozomi-mail/attachments
```

**方案三：相对于 jar 包位置**

```java
// 获取当前 jar 所在目录，再拼接
String baseDir = new File(
    GmailServiceFactory.class.getProtectionDomain()
        .getCodeSource().getLocation().toURI()
).getParent();

Path path = Paths.get(baseDir, "data", "test.txt");
```

生产部署时一般用方案二，路径通过配置文件或环境变量控制，最灵活。