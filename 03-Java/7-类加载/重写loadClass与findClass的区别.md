---
tags:
  - java
  - jvm
  - 类加载
  - 双亲委派
created: 2026-06-10
updated: 2026-06-10
---

# 重写 loadClass 与重写 findClass 的区别

## 一句话

- **重写 `findClass()`** — 在双亲委派框架内做扩展，是标准做法 ✔
- **重写 `loadClass()`** — 真正打破双亲委派，要自己实现委派逻辑 ✗

---

## 源码看本质

`ClassLoader` 的核心逻辑在 `loadClass()` 里：

```java
// ClassLoader.java（JDK 源码，简化版）
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

    // 1. 检查是否已加载
    Class<?> c = findLoadedClass(name);
    if (c == null) {
        try {
            // 2. 双亲委派核心：先让 parent 加载
            if (parent != null) {
                c = parent.loadClass(name, false);
            } else {
                // parent = null → 交给 Bootstrap 加载
                c = findBootstrapClassOrNull(name);
            }
        } catch (ClassNotFoundException e) {
            // parent 加载不到 → 跳过
        }

        if (c == null) {
            // 3. parent 加载不到 → 自己调用 findClass 加载
            c = findClass(name);
        }
    }
    // ...
    return c;
}
```

**`findClass()` 的默认实现：**

```java
// ClassLoader.java
protected Class<?> findClass(String name) throws ClassNotFoundException {
    throw new ClassNotFoundException(name);  // 啥也不干，直接抛异常
}
```

---

## 重写 `findClass()` — 不破坏双亲委派

```java
public class MyClassLoader extends ClassLoader {

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 只负责"怎么加载类"，不管"什么时候加载"
        byte[] bytes = loadClassData(name);    // 从自定义位置读取字节码
        return defineClass(name, bytes, 0, bytes.length);
    }
}
```

**为什么不破坏？** 因为 `findClass()` 在 `loadClass()` 的流程里是最后一步——只在 `findLoadedClass()` 没找到、`parent.loadClass()` 也没加载到之后才调用。

```
loadClass() 被调用
    ↓
1. findLoadedClass() — 检查缓存
    ↓ 没找到
2. parent.loadClass() — 委派给父加载器
    ↓ 父加载器也加载不到
3. findClass() ← 你的扩展逻辑在这里执行
```

双亲委派的链没有断——你的类加载器仍然先问父加载器，父加载器管不了的才由 `findClass()` 兜底。这叫**在双亲委派框架内安全扩展**。

---

## 重写 `loadClass()` — 真正打破双亲委派

```java
public class BreakingClassLoader extends ClassLoader {

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {

        // 1. 检查已加载
        Class<?> c = findLoadedClass(name);
        if (c != null) return c;

        // 2. ⚠ 自己先尝试加载，不委派给 parent
        try {
            c = findClass(name);        // 优先自己加载！
            if (c != null) return c;
        } catch (ClassNotFoundException e) {
            // 自己加载不到再委派
        }

        // 3. 自己加载不到 → 再走 parent
        if (parent != null) {
            c = parent.loadClass(name, false);
        } else {
            c = findBootstrapClassOrNull(name);
        }
        return c;
    }
}
```

**为什么破坏了？** 因为原本 `parent.loadClass()` → 失败 → `findClass()` 的顺序被调换了。

```
标准顺序：              破坏后的顺序：
parent 先尝试           自己先尝试（findClass）
    ↓                       ↓
parent 失败 → findClass   自己失败 → parent
```

这样一来，你的类加载器可以**优先加载自己的类**，即使父加载器也能加载同一个类，也不会去委派。Tomcat 的 `WebappClassLoader` 就是这么做的——每个 WebApp 的类加载器优先加载自己 `WEB-INF/classes/` 和 `WEB-INF/lib/` 下的类，实现应用隔离。

---

## 对比总结

| 维度 | 重写 `findClass()` | 重写 `loadClass()` |
|------|:---:|:---:|
| **是否破坏双亲委派** | ❌ 不破坏 | ✅ 破坏 |
| **典型用途** | 自定义加载来源（网络、加密、指定目录） | 需要优先加载自己的类（容器隔离） |
| **委派顺序** | parent → self（不变） | self → parent（反转） |
| **安全性** | 核心类仍由 Bootstrap 保护 | 可能覆盖核心类（危险） |
| **JDK 推荐** | ✅ 官方推荐的自定义加载器方式 | ⚠ 仅在确实需要破坏时使用 |

---

## 为什么 `findClass()` 是标准做法？

JDK 设计 `loadClass()` → `findClass()` 的分离就是为了让大多数开发者**只重写 `findClass()`**，不必关心双亲委派的逻辑。源码注释也明确说：

> `loadClass()` 实现了双亲委派算法，子类应**覆写 `findClass()`** 而不是 `loadClass()`。

如果你只需要改变"从哪里读 .class 文件"，重写 `findClass()` 就够了。比如：

- 从加密 jar 里加载类（运行时解密）
- 从网络 URL 加载类
- 从数据库 BLOB 加载类
- 从自定义目录（非 classpath）加载类

这些都不需要破坏双亲委派，`findClass()` 就是干这个的。

---

## 面试话术

> **问：重写 `findClass()` 和重写 `loadClass()` 有什么区别？**
>
> 答：`loadClass()` 实现了双亲委派的完整流程——先查已加载、再委派父加载器、最后自己加载。而 `findClass()` 是这个流程的最后一步，是默认抛异常的，让子类自己去实现"从哪读字节码"。所以只重写 `findClass()` 是在双亲委派框架内做扩展，父加载器优先的规则不变。重写 `loadClass()` 则是把整个委派逻辑替换掉，可以调整 loading 顺序——比如 Tomcat 的 WebappClassLoader 优先自己加载，父加载器兜底，这就真正打破了双亲委派。

> **问：什么时候需要打破双亲委派？**
>
> 最典型的场景是 Tomcat：不同的 WebApp 可能依赖同一个 jar 的不同版本（比如 Spring 4 和 Spring 5），每个 WebApp 需要加载自己那份。如果不打破，双亲委派会优先加载第一个被加载的版本，其他 WebApp 就只能用那个版本。Tomcat 的 WebappClassLoader 通过重写 `loadClass()` 优先加载 `WEB-INF/` 下的类，实现了应用级别的依赖隔离。

---

## 关联知识点

- [[三层类加载器与classpath]] — 三层加载器结构与双亲委派基础
- [[线程上下文类加载器（TCCL）]] — 另一种打破双亲委派的方式（上层→下层）
- [[打破双亲委派的经典场景]] — 打破双亲委派两大场景汇总（重写 loadClass 的下层打破入口）
- [[jvm八股]] — JVM 八股总览（类加载部分）
- [[jvm_review]] — JVM 模拟面试批改（含本知识点原始出处）
