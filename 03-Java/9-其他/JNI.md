---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---
### **JNI（Java Native Interface）是 Java 调用本地代码（C/C++）的标准接口**

  

简单说，JNI 是 JVM 提供的一座"桥"，让 Java 代码能调用用 C、C++、Rust 等编译成的本地库（Linux 上是 `.so`，Windows 上是 `.dll`，macOS 上是 `.dylib`），反过来 native 代码也能回调 Java 方法、操作 Java 对象。

  

---

  

**为什么需要它**

  

Java 跑在 JVM 里，本身访问不了操作系统底层能力，也做不了极致性能的计算。遇到这些场景就得靠 JNI：

  

- **操作系统能力**：文件系统高级特性、原生网络栈、硬件设备访问。其实 JDK 自己的 `FileInputStream`、`Socket`、`Thread` 底层全是 JNI 调 libc。

- **复用已有 C/C++ 库**：比如 OpenSSL（`netty-tcnative`）、SQLite（`sqlite-jdbc`）、图像编解码、机器学习推理（TensorFlow Java、ONNX Runtime）、压缩库（Snappy、Zstd）、数据库引擎（RocksDB）。

- **性能敏感的计算**：加解密、视频转码、科学计算，用 C 写完 JNI 暴露给 Java。

  

**典型写法**

  

Java 侧声明 `native` 方法并加载库：

  

```java

public class Hello {

static { System.loadLibrary("hello"); } // 加载 libhello.so

public native String sayHi(String name);

}

```

  

C 侧实现对应函数（函数名有固定规则 `Java_包名_类名_方法名`），编译成 `.so`，运行时 JVM 通过符号找到它并跳转执行。

  

---

  

**和面试题的关联——为什么 JNI 会导致堆外内存泄漏**

  

这是关键：**JNI 调用的 native 代码用 `malloc` 分配的内存，完全脱离 JVM 管理**。

  

- JVM 的 GC 管不到 native 堆，Java 对象被回收不代表底层 C 结构体也被 `free`。

- 很多 native 库要求你手动调一个 `close()` / `dispose()` / `release()` 方法去触发 native 侧的释放，忘了调就是纯泄漏。

- `jmap`、堆 dump 都看不到这部分内存，只能靠 `pmap`、`jemalloc profiling`、`NMT`（部分可见）、`perf` 这类系统级工具才能定位。

  

举个真实场景：用 `netty-tcnative` 跑 HTTPS，OpenSSL 的 SSL context 是 native 分配的，如果连接没正确关闭、或者证书反复加载不释放，RSS 就会一路涨，堆里啥也看不出来，最后容器被 OOMKilled——这就是典型的 JNI 泄漏。

  

一句话总结：**JNI = Java 调 native 的官方机制，好处是能力强、性能高，代价是内存和生命周期得自己管，是堆外泄漏的高发区**。