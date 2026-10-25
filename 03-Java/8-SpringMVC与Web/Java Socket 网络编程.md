---
tags:
  - java
  - Socket
  - 网络编程
  - BIO
  - 面试
created: 2026-10-09
updated: 2026-10-09
source: 答疑会话（IO 流程 / accept-read-write / C10K / Socket 示例 / read 返回值）
---

# Java Socket 网络编程

> 补齐 Java 网络编程这一层：`Socket` 是什么、`accept/read/write` 各对应什么、**BIO 回显的完整代码**、一次读写的**端到端数据流**、以及两个高频细节——**非阻塞 `read()` 返回什么**、**AIO 回调 vs Selector 可读通知的区别**。
>
> 底层机制（多路复用、epoll、内核半连接队列）见 [[select 与 epoll 机制详解]]、[[OS八股]]；三种模型的整体对比见 [[Java基础热门面试题-1#39. BIO、NIO、AIO 的区别？【高频】]]。

---

## 一、Socket 是什么

**Socket 描述的是"IP 地址 + 端口号"这个网络通信的端点。** 两台机器通信，本质是两个 Socket 之间建立连接；一条 TCP 连接由**四元组**唯一标识：

```text
（本机 IP，本机端口，对端 IP，对端端口）
```

同一个服务端、同一个端口 8080 能同时服务成千上万个客户端，靠的就是四元组不同。

在操作系统看来，**Socket 就是一个 fd（文件描述符）**，可以被 `read` / `write`。

---

## 二、`accept` / `read` / `write` 各管什么

```text
socket
  ↓
bind      绑定本地地址和端口
  ↓
listen    开始监听连接
  ↓
accept    等待并取出一个客户端连接   ← 管"连接建立"
  ↓
read      从连接读取客户端数据        ← 管"连接上的数据传输"
  ↓
write     向连接写回响应             ← 管"连接上的数据传输"
```

**一句话：`accept` 处理连接建立，`read`/`write` 处理连接上的数据传输。** 具体会不会阻塞，取决于 IO 模型：

| 模型 | `accept()` | `read()` 没数据时 |
|------|-----------|------------------|
| **BIO** | 没连接就等 | 一直等 |
| **NIO 非阻塞** | 立即返回 | 立即返回（非阻塞） |
| **AIO** | 提交后先返回，完成回调 | 提交后先返回，完成回调 |

> 补充：`accept` **不是凭空变出连接**——三次握手是内核在 `listen` 之后自动完成的，`accept` 只是从内核的已建好连接队列里"摘"一个出来，返回代表这条连接的 `Socket`（`conn_fd`）。详见 [[select 与 epoll 机制详解#3.3 服务端用了"两种" socket]]。

---

## 三、BIO 回显（Echo）完整示例

最简单的回声：客户端发一行，服务端加前缀返回。

**服务端（ServerSocket）**

```java
import java.io.*;
import java.net.*;

public class EchoServer {
    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(8888)) {   // 监听 8888
            System.out.println("服务端启动，等待连接...");

            try (Socket socket = serverSocket.accept();              // 阻塞，等客户端连接
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(
                         socket.getOutputStream(), true)) {

                String line = in.readLine();                         // 阻塞，等客户端发数据
                System.out.println("收到：" + line);
                out.println("服务端回复：" + line);                   // 写回
            }
        }
    }
}
```

**客户端（Socket）**

```java
import java.io.*;
import java.net.*;

public class EchoClient {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 8888);          // 连接服务端
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

            out.println("你好");                                      // 发送
            System.out.println(in.readLine());                       // 读取回复
        }
    }
}
```

先跑 `EchoServer` 再跑 `EchoClient`，客户端输出 `服务端回复：你好`。

> 这是 **BIO** 写法：`accept()` 和 `readLine()` 都会阻塞线程，且上面的服务端只处理一个连接就结束。要并发处理多个客户端，需要循环 `accept()` 并为每个连接起一个线程——这正是 **BIO 连接一多就线程爆炸**的原因。

---

## 四、一次读写的端到端数据流

**服务端读取客户端数据**，数据大致这样走：

```text
客户端应用程序缓冲
  ↓ write/send
客户端内核「发送缓冲区」
  ↓ 网络传输
服务端网卡（DMA 写入内存）
  ↓ 协议栈处理
服务端内核「接收缓冲区」           ← 数据到服务器了，但还没进 Java 程序
  ↓ read / 异步读取（内核 → 用户空间拷贝）
服务端程序提供的 ByteBuffer / byte[]  ← 到这里程序才能处理
  ↓
应用程序解析、处理
```

两个关键认知：

1. **"IO 等待"其实有两个阶段**：① 等数据到达内核缓冲区；② 把数据从内核缓冲区**拷贝**到程序缓冲区。三种模型的区别就在这两阶段"谁等、怎么等"。
2. **TCP 是字节流**，不保证一次 `read()` 对应一次对端 `write()`：一次写入可能分多次读到，也可能和后续数据一起读到——所以要用长度或分隔符来界定消息边界。

**写方向大致相反**：程序把字节放进自己的缓冲区 → `write()` 交给内核发送缓冲区 → TCP/IP 协议栈和网卡发出去。**`write()` 返回一般只表示本地这次写操作被接受了，不代表对端已经收到或处理。** 收包侧的内核细节见 [[网卡收包流程详解]]。

---

## 五、非阻塞 `read()` 返回什么（高频细节）

**数据还没到时，非阻塞 `read()` 不会一直等，而是立即返回"当前没有数据可读"。** 用 Java NIO 的 `Channel` 看返回值：

```java
int n = channel.read(buffer);

if (n > 0) {
    // 读到了 n 个字节，数据已放进 buffer
} else if (n == 0) {
    // 此刻没有数据可读（非阻塞），之后再试
} else {
    // n == -1：对端关闭了连接（EOF）
}
```

| 返回值 | 含义 |
|:---:|------|
| `> 0` | 读到的字节数，数据已写入 buffer |
| `0` | 暂时没有数据可读（非阻塞模式） |
| `-1` | 对端关闭连接（EOF） |

NIO 里通常不用死循环 `read()` 试探，而是先用 `Selector` 等"某连接可读"的通知，再调 `read()`。**注意"可读"不等于"数据已被读进 Java 程序"**——它只表示内核接收缓冲区里已有数据、现在 `read` 通常不会阻塞；真正把数据搬进来还得程序自己调 `read()`。

---

## 六、C10K 是什么

**C10K 指"单台服务器如何处理约一万个并发连接"的问题。** 这里的 **C 通常指 Concurrent（并发）**，**10K = 约一万条同时保持打开的连接**（不一定在同时发数据，很多连接大部分时间在等待，也算并发连接）。

传统 BIO 常给每个连接配一个线程，连接一多，线程的内存（每线程栈约 1MB）、调度和上下文切换开销就压垮机器——这就是"10 万连接 × 8MB 栈"那笔账。NIO 的 `Selector`（底层 epoll）让少量线程监听大量连接、只在就绪时处理，因而更适合海量连接场景。详见 [[select 与 epoll 机制详解#3.6 每连接一个线程：传统 BIO 服务端的形状]]。

> ⚠️ NIO 不自动保证能扛住一万连接——实际能力还受硬件、OS 配置、程序处理效率、流量等因素影响。

---

## 七、AIO 回调 vs Selector 可读（一句话对照）

两种"非阻塞"的**通知时机不同**：

| | 通知内容 | 谁去搬数据 |
|---|---------|-----------|
| **NIO + Selector** | "现在**可以尝试读**了"（就绪通知） | 程序自己调 `read()` |
| **AIO** | "这次读**已经完成**了，数据已在目标缓冲区"（完成通知） | 内核已经搬好了 |

AIO 读取示例：

```java
channel.read(buffer, attachment, new CompletionHandler<Integer, Object>() {
    @Override
    public void completed(Integer bytesRead, Object attachment) {
        // 读取已完成；数据已经写入 buffer
    }
    @Override
    public void failed(Throwable error, Object attachment) {
        // 读取失败
    }
});
```

> 一句话：**Selector 通知你"可以读了"，AIO 通知你"已经读完了"。**

---

## 面试话术（口述版）

**问：Socket 是什么？`accept`、`read`、`write` 分别干什么？**

> Socket 描述的是"IP 加端口"这个通信端点，一条 TCP 连接由"本机 IP、本机端口、对端 IP、对端端口"这个四元组唯一标识。在服务端流程里，`new ServerSocket(port)` 对应 socket、bind、listen 三步，`accept()` 从内核已建好的连接队列里摘出一个连接、返回一个新的 Socket——它管的是**连接建立**；而 `read` 和 `write` 管的是**连接上的数据传输**，一个读入一个发出去。具体会不会阻塞要看 IO 模型：BIO 下 `accept` 没连接就等、`read` 没数据也等；NIO 非阻塞模式下这些调用可以立即返回。

**追问：非阻塞 `read()` 没数据时返回什么？AIO 的回调又是什么？**

> 非阻塞 `read()` 没数据时会**立即返回**，Java NIO 里返回 **0**，表示"此刻没有数据可读"；返回正数就是读到的字节数，返回 **-1** 表示对端关闭了连接。AIO 就不一样了：提交读请求后立即返回、线程可以先干别的，等**操作系统把数据读进目标缓冲区、这次读操作完成**之后再通过回调或 Future 通知你。所以两者通知的时机完全不同——**Selector 通知的是"可以尝试读了"，AIO 通知的是"已经读完了"**。

---

## 关联知识点

- [[select 与 epoll 机制详解]] —— 多路复用、socket = fd、四元组、半连接队列、C10K 的底层账
- [[Java基础热门面试题-1#39. BIO、NIO、AIO 的区别？【高频】]] —— 三种模型整体对比 + 同步/异步、阻塞/非阻塞
- [[Java基础热门面试题-1#40. NIO 的核心组件和原理？【中频】]] —— Channel / Buffer / Selector
- [[Java基础面试题#BIO、NIO、AIO区别是什么？]] —— 长版对比 + 餐厅类比
- [[网卡收包流程详解]] —— 收包侧内核链路（DMA → 软中断 → 协议栈 → socket 队列 → read）
- [[OS八股]] —— POSIX 五种 IO 模型（§7.1–7.2）
