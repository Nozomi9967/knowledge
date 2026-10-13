---
tags:
  - java
  - 序列化
  - 安全
  - 反序列化漏洞
created: 2026-06-25
updated: 2026-06-25
source:
---

# Java 反序列化 Gadget 链详解

## 定义

**Gadget 链**（Gadget chain）是一条从 `ObjectInputStream.readObject()` 入口到危险操作（如 `Runtime.exec()`）的调用路径。路径上的每个类被称为一个 **gadget**（零件）。

每个 gadget 本身是"无辜的"——它只是在自己的某个方法（`hashCode()`、`toString()`、`equals()`、`transform()` 等）里调用了其他方法。但攻击者把多个 gadget 串联起来，从 `readObject()` 一路走到任意代码执行。

---

## 核心原理

```
ObjectInputStream.readObject()            ← 反序列化入口
  → HashMap.readObject()                   ← gadget 1：重建 HashMap 时会 put 键值对
    → HashMap.put() → key.hashCode()       ← gadget 2：put 自动调用 key 的 hashCode()
      → TiedMapEntry.hashCode()            ← gadget 3：hashCode 内部调 getValue()
        → LazyMap.get()                    ← gadget 4：get 内部调 factory.transform()
          → ChainedTransformer.transform()  ← gadget 5：遍历多个 Transformer 链式调用
            → InvokerTransformer.transform()← gadget 6：反射调用任意方法
              → Runtime.exec("rm -rf /")    ← 🎯 危险操作（RCE）
```

**关键洞察**：Java 的标准库和三方库中有大量方法会在反序列化时被自动触发（`readObject()`、`readResolve()`、`readExternal()`、`validateObject()`），只要其中某个方法间接调用了另一个对象的危险方法，就可以串联成链。

---

## 三个要素

| 要素 | 说明 | 例子 |
|------|------|------|
| **入口类** | 实现了 `Serializable`，`readObject()` 里会调用其他方法 | `HashMap`、`Hashtable`、`BadAttributeValueExpException` |
| **链式调用类** | 某个 getter / hashCode / toString 里调用了下一个 gadget | `TiedMapEntry`、`LazyMap`、`ChainedTransformer` |
| **执行类** | 能反射调用任意方法或执行命令 | `InvokerTransformer`、`Runtime`、`ProcessBuilder` |

---

## 经典 Gadget 链：Commons-Collections

历史上最著名的 Java 反序列化漏洞出自 Apache Commons-Collections 库。其中 `InvokerTransformer` 类的 `transform()` 方法可以**通过反射调用任意对象的任意方法**：

```java
// InvokerTransformer.transform() 伪逻辑
public Object transform(Object input) {
    return MethodUtils.invokeMethod(input, this.methodName, this.args);
}
```

结合 `ChainedTransformer`（把多个 Transformer 串起来执行）和 `LazyMap` / `TiedMapEntry`（某个 getter 里自动调 `transform()`），就是一条通往 RCE 的完整链路。

> 2015 年 Apache Commons-Collections 反序列化漏洞（CVE-2015-7501）轰动业界，此后几乎所有 Java 中间件都开始做序列化白名单过滤。

---

## 为什么难以防御

| 难点 | 说明 |
|------|------|
| **gadget 来自三方库** | 你引入的 jar 包里可能包含可被利用的 gadget，你甚至不知道自己引入了它 |
| **调用链是正常的代码路径** | 每个 gadget 调下一个方法都是正常业务逻辑，没有"恶意代码"可检测 |
| **白名单 vs 黑名单** | 黑名单永远追不上新发现的 gadget；白名单才能从根本上阻断，但需要改造通信协议 |
| **不止 RCE** | 有些 gadget 链导致 DOS（反序列化超深嵌套对象图栈溢出）、SSRF（JNDI 注入）等 |

---

## 防御手段

1. **白名单校验**：在 `ObjectInputStream` 的子类中重写 `resolveClass()`，只允许反序列化指定包名
2. **替代序列化协议**：Protobuf、JSON、Hessian 等跨语言序列化方案，不是基于 `readObject()` 回调的
3. **不要暴露反序列化接口**：避免直接接收来自网络的序列化字节流
4. **升级依赖版本**：新版本 commons-collections4 移除了危险 Transformer

```java
// 白名单示例
class WhitelistObjectInputStream extends ObjectInputStream {
    private static final Set<String> ALLOWED =
        Set.of("com.example.", "java.lang.");

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException {
        String name = desc.getName();
        if (ALLOWED.stream().noneMatch(name::startsWith)) {
            throw new InvalidClassException("Unauthorized deserialization", name);
        }
        return super.resolveClass(desc);
    }
}
```

---

## 通俗理解

> 反序列化 gadget 链就像**遥控炸弹**：
> - 每个 gadget 是一段**导线**
> - `readObject()` 是**按下开关**
> - 导线一根连一根，最终引爆**炸弹**（`Runtime.exec()`）
> - 你 classpath 上的三方 jar 就是这座"军火库"，你根本不知道里面哪根线能用

---

## 关联知识点

- [[Java基础面试题]] — Java 基础面试题，包含序列化部分
- [[注解原理详解]] — 注解的原理（同理，反射也是 gadget 链的重要能力来源）
