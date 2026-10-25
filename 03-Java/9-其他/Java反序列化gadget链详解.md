---
tags:
  - java
  - 序列化
  - 安全
  - 反序列化漏洞
created: 2026-06-25
updated: 2026-10-14
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

**更推荐的做法：`ObjectInputFilter`（JEP 290）**。Java 9 起（并回移到 8u121）官方给出标准过滤器，不用自己继承 `ObjectInputStream`——直接在流上设置规则，能限制：可反序列化的类、数组最大长度、**对象图最大深度**、引用总数、流字节大小；除代码设置外，还可用 `-Djdk.serialFilter=...` 全局配置：

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
    "com.example.dto.*;java.base/*;!*"   // 只允许指定包，其余全部拒绝
);
ois.setObjectInputFilter(filter);
Object o = ois.readObject();
```

推荐用**白名单**：黑名单永远追不上新发现的 gadget。这也是「哪项做法最有效地防止反序列化攻击」类面试题的标准答案。

---

## 通俗理解

> 反序列化 gadget 链就像**遥控炸弹**：
> - 每个 gadget 是一段**导线**
> - `readObject()` 是**按下开关**
> - 导线一根连一根，最终引爆**炸弹**（`Runtime.exec()`）
> - 你 classpath 上的三方 jar 就是这座"军火库"，你根本不知道里面哪根线能用

---

## 辨析：`readResolve` 与 `transient` 能防住反序列化攻击吗？

**都不能——它们是序列化机制的功能钩子，不是安全防线。** 常见选择题的选项 A 和 D 就埋在这里：

- **`readResolve()`**：在反序列化**已经创建出新对象之后**才被调用，用途是"用返回的对象替换刚还原的对象"——最典型是**保护单例**（不写它，反序列化会造出第二个实例）：`private Object readResolve() { return INSTANCE; }`。但它执行时，恶意类的 `readObject` 调用链**早就跑完了**，所以防不了攻击。
  - 签名：`ANY-ACCESS-MODIFIER Object readResolve() throws ObjectStreamException`；`private` 时子类不继承，`protected` / `public` 时子类会继承（可能被替换成父类的单例，注意）。
  - 与之对应的 `writeReplace()` 在**序列化前**替换要写出的对象；用 `enum` 写单例时 JVM 已保证反序列化唯一性，不需要手写 `readResolve`。
- **`transient`**：只是让字段**不参与序列化**（常用于密码/密钥、可重算的缓存、线程/连接等进程相关资源）。它能防**敏感数据泄露**，但攻击利用的是类的方法调用链（gadget）、不依赖字段数据——把字段全设 `transient` 只能让序列化失去意义，防不了攻击。
  - 注意：它只对 Java 原生序列化生效（JSON/Protobuf 有各自的注解，如 Jackson 的 `@JsonIgnore`）；反序列化后字段是默认值，需要恢复就实现 `readObject`、在 `defaultReadObject()` 之后补赋值。

---

## 关联知识点

- [[Java基础面试题]] — Java 基础面试题，包含序列化部分
- [[注解原理详解]] — 注解的原理（同理，反射也是 gadget 链的重要能力来源）
