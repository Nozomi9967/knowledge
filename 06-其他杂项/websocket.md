



## 消息推送常用方式



1. 轮询

   1. 浏览器指定时间间隔向服务器发送HTTP请求，服务器返回实时数据

2. 长轮询

   1. 浏览器发出ajax请求，服务器会阻塞请求，直到有数据或者超时才返回

3. SSE（Server-Sent-Event）服务器发送事件

   1. SSE在服务器和客户端之间打开一个单向通道
   2. 服务器发的不再是数据包，而是数据流信息

   3. 当数据变更时，发送数据流信息

4. websocket





## websocket介绍

![八股\images\image-20260128182736007](C:\Users\q1948\Desktop\八股\images\image-20260128182736007.png)

![八股\images\image-20260128182901021](C:\Users\q1948\Desktop\八股\images\image-20260128182901021.png)

![八股\images\image-20260128183557694](C:\Users\q1948\Desktop\八股\images\image-20260128183557694.png)

![八股\images\image-20260128183701666](C:\Users\q1948\Desktop\八股\images\image-20260128183701666.png)

![八股\images\image-20260128184103235](C:\Users\q1948\Desktop\八股\images\image-20260128184103235.png)

![八股\images\image-20260128184217035](C:\Users\q1948\Desktop\八股\images\image-20260128184217035.png)

![八股\images\image-20260128184513734](C:\Users\q1948\Desktop\八股\images\image-20260128184513734.png)





## 实现

![八股\images\image-20260128184707149](C:\Users\q1948\Desktop\八股\images\image-20260128184707149.png)

![八股\images\image-20260128185057727](C:\Users\q1948\Desktop\八股\images\image-20260128185057727.png)

![八股\images\image-20260128185206515](C:\Users\q1948\Desktop\八股\images\image-20260128185206515.png)

![八股\images\image-20260128185305949](C:\Users\q1948\Desktop\八股\images\image-20260128185305949.png)



