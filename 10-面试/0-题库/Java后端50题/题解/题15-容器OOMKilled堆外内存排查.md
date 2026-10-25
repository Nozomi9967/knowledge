---
tags:
  - java
created: 2026-07-02
updated: 2026-07-02
---

### **4C8G 指容器分配了 4 核 CPU 和 8GB 内存，OOMKilled 是内核因 RSS 超过 8G 把进程杀掉，和 JVM 堆 OOM 是两码事**

  

---

  

先说 **4C8G**：就是容器的资源规格，4 Core CPU + 8 GB Memory，是 K8s 或 Docker 里给 Pod/容器设的 limit。一旦容器进程的 RSS（常驻物理内存）超过 8G，cgroup 的 OOM killer 就会把它 kill 掉，日志里表现为 `OOMKilled`，退出码 137。

  

关键点：**堆只用了 2G，但进程被杀，说明剩下的 6G 花在堆外**。JVM 的总内存 ≈ Heap + Metaspace + Code Cache + Direct Memory + Native (JNI/Netty/glibc) + Thread Stack + GC 结构 + Symbol/String Table。任何一块失控都会撑爆容器。

  

---

  

### **堆外可能的泄漏点**

  

**1. DirectByteBuffer（NIO 直接内存）**

`ByteBuffer.allocateDirect()` 分配的内存不在堆里，靠 `Cleaner`（PhantomReference）在 DirectByteBuffer 对象被 GC 时释放。如果老年代 GC 不触发，Cleaner 就不跑，Direct 内存会一直涨。常见于 NIO、Kafka Producer、gRPC。默认上限由 `-XX:MaxDirectMemorySize` 控制，不设的话等于 Xmx。

  

**2. Netty（PooledByteBufAllocator）**

Netty 默认用池化的 Direct Memory，一个 Chunk 就是 16MB。如果 `ByteBuf` 用完没 `release()`（引用计数没减到 0），或者业务没关连接，池会一直扩张。另外 Netty 有自己的计数器 `PlatformDependent.DIRECT_MEMORY_COUNTER`，不走 JDK 的 MaxDirectMemorySize。

  

**3. JNI / Native 库**

比如用了 `sqlite-jdbc`、`snappy`、`rocksdb`、`netty-tcnative(OpenSSL)`、图像处理 JNI，这些在 native 侧 malloc，Java 层完全看不到。还有 glibc 的 `ptmalloc` arena 碎片问题——多线程下每个线程一个 arena，容器里表现为 RSS 远大于实际使用，换成 `jemalloc` 或 `tcmalloc` 通常能压下来。

  

**4. Metaspace**

类元数据放在这。动态生成类（CGLIB、Javassist、Groovy、频繁热部署、大量 Lambda）会让 Metaspace 无限增长。默认不限，`-XX:MaxMetaspaceSize` 要显式设。同时 CodeCache（JIT 编译产物）也是堆外，默认 240MB 左右，微服务一般够用。

  

**5. 线程栈**

每个线程栈默认 1MB（`-Xss`）。如果线程池配置不当，比如 Tomcat maxThreads 拉到几千，或者用了大量阻塞 IO 造出几千个线程，光线程栈就吃掉几个 G。`ps -eLf | wc -l` 数一下线程数就知道。

  

---

  

### **排查工具**

  

先看**总量分布**，用 `jcmd <pid> VM.native_memory summary`——前提是启动加了 `-XX:NativeMemoryTracking=summary`。它会把 JVM 内部的 Heap / Class / Thread / Code / GC / Internal / Direct 分别列出来，一眼看出哪块异常。

  

看**Direct Memory 具体谁在占**，用 Arthas 的 `vmtool` 或者反射读 `sun.misc.VM.getDirectBufferPool`，也可以 dump 堆之后在 MAT 里搜 `DirectByteBuffer` 看 GC Root。Netty 单独看，加 `-Dio.netty.leakDetection.level=paranoid` 开泄漏检测，日志会打出未释放的 ByteBuf 分配栈。

  

**Native 层**得上系统工具：`pmap -x <pid>` 看进程地址空间分布，异常大的匿名段就是 native malloc；更精细用 `jemalloc` 带的 profiling，或者 `perf`、`bpftrace`、`async-profiler --alloc` 抓 native 分配栈。glibc arena 问题可以设 `MALLOC_ARENA_MAX=2` 验证。

  

**Metaspace 和线程**相对简单：`jcmd <pid> GC.heap_info`、`jstat -gc` 看 Metaspace 使用，`jstack` 或 `jcmd Thread.print` 数线程数，Arthas `thread` 命令也行。

  

**容器视角**用 `cat /sys/fs/cgroup/memory.current`（cgroup v2）或 `memory.usage_in_bytes`（v1）对比 JVM 自报的内存，差值就是 JVM 没管到的部分。dmesg 里能看到 OOMKiller 的具体记录。

  

排查顺序我一般是：**NMT 先定位大类 → 差值大的话上 pmap + jemalloc profiling → Netty 开 leak detector → 最后再看 Metaspace 和线程数**。