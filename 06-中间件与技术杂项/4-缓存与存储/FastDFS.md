---
tags:
  - 其他
created: 2026-06-28
updated: 2026-10-13
---
# FastDFS

## 介绍

淘宝架构师编写，淘宝用的分布式文件系统。

优点：相比通用的分布式文件系统，它非常适用于存储小文件，不分块，从而性能高。

## 架构图

![[image-20260313151427941.png]]

- **/home/FastDFS** 为虚拟机真实目录
- **/usr/bin/fdfs_storaged**、**/usr/bin/fdfs_trackerd** 为可执行程序
- **/etc/fdfs/storage.conf**、**/etc/fdfs/tracker.conf** 为配置文件

![[image-20260313151442174.png]]

## 版本对应关系

> [!warning] 软件版本必须互相匹配，否则编译报错。

| 组件 | 版本 |
|------|------|
| libfastcommon | V1.0.43 |
| FastDFS | V5.12 |
| fastdfs-nginx-module | V1.20 |
| Nginx | 1.20.1（源码编译） |

> [!todo] 补充详细步骤

## 安装步骤顺序

1. 安装 libfastcommon V1.0.43
2. 安装 FastDFS V5.12
3. 手动创建数据目录 `/home/FastDFS/fdfs_storage`
4. 先启动 tracker，再启动 storage（顺序不能反）
5. 修改 fastdfs-nginx-module 的 `config` 文件，把头文件路径改为 `/usr/include/fastdfs` 和 `/usr/include/fastcommon`
6. 源码编译 Nginx，加上 `--add-module` 参数
7. 配置 `mod_fastdfs.conf`（tracker 地址、url_have_group_name、store_path0）
8. 在 nginx.conf 里加 location，且要放在其他 location 前面，避免被 `.png/.jpg` 规则拦截
9. 重启 Nginx，上传文件后用浏览器访问验证

## 启动、停止命令

> 存在先后关系：先 tracker，后 storage。

```shell
/usr/bin/fdfs_trackerd /etc/fdfs/tracker.conf start
/usr/bin/fdfs_trackerd /etc/fdfs/tracker.conf stop
/usr/bin/fdfs_storaged /etc/fdfs/storage.conf start
/usr/bin/fdfs_storaged /etc/fdfs/storage.conf stop
```

## 自启动配置

1. 将 FastDFS 目录下的文件移动到 `/etc/systemd/system`
2. 重新加载 systemd 并启用服务：
   ```shell
   systemctl daemon-reload

   systemctl enable fdfs_trackerd
   systemctl enable fdfs_storaged
   systemctl enable nginx

   systemctl start fdfs_trackerd
   systemctl start fdfs_storaged
   systemctl start nginx
   ```
3. 查看状态：
   ```shell
   systemctl status fdfs_trackerd   # 确认 active (running)
   systemctl status fdfs_storaged
   systemctl status nginx
   ```
4. 重启后验证自启：
   ```shell
   reboot
   ps -ef | grep fdfs | grep -v grep
   ps -ef | grep nginx | grep -v grep
   ```

## libfastcommon 的作用？

libfastcommon 是 FastDFS 的基础公共库，提供一些底层工具函数，比如字符串处理、链表、哈希表、线程池、网络通信、日志等。FastDFS 本身依赖它，没有它 FastDFS 编译不了。相当于 FastDFS 的工具包底座。

## Nginx 的作用？

在这套架构里 Nginx 的作用是提供 HTTP 访问入口。FastDFS 本身只负责存储和管理文件，没有 HTTP 服务能力，无法直接用浏览器访问文件。加上 fastdfs-nginx-module 模块之后，Nginx 收到图片请求就转交给 FastDFS 客户端去取文件，然后返回给浏览器。简单说就是让文件能通过 HTTP URL 直接访问。
