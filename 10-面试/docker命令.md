---
tags:
  - 面试
created: 2026-09-29
updated: 2026-09-29
---

### **部署实习里常见的 Docker 命令**

下面按“看状态、看日志、启动服务、排查问题”整理。你可以先对照自己记得的操作，挑确实用过的来准备；命令记得多，不等于每个都需要说成实习中用过。

| 用途 | 命令 | 作用 |
|---|---|---|
| 查看运行中的容器 | `docker ps` | 查看正在运行的容器 |
| 查看所有容器 | `docker ps -a` | 包括已退出的容器 |
| 查看本地镜像 | `docker images` | 列出本机已有镜像 |
| 拉取镜像 | `docker pull 镜像名:标签` | 从镜像仓库拉取镜像 |
| 启动容器 | `docker start 容器名或ID` | 启动已有容器 |
| 停止容器 | `docker stop 容器名或ID` | 停止运行中的容器 |
| 重启容器 | `docker restart 容器名或ID` | 重启已有容器 |
| 查看容器日志 | `docker logs 容器名或ID` | 查看容器输出日志 |
| 持续查看日志 | `docker logs -f 容器名或ID` | 实时跟踪日志 |
| 查看最近日志 | `docker logs --tail 100 容器名或ID` | 只看最近 100 行 |
| 进入运行中的容器 | `docker exec -it 容器名或ID /bin/bash` | 进入容器交互终端 |
| 进入精简容器 | `docker exec -it 容器名或ID /bin/sh` | 容器没有 Bash 时使用 |
| 查看容器详情 | `docker inspect 容器名或ID` | 查看配置、网络、挂载等信息 |
| 查看容器资源情况 | `docker stats` | 查看容器 CPU、内存等使用情况 |
| 查看容器端口映射 | `docker port 容器名或ID` | 查看容器端口映射 |
| 删除已停止容器 | `docker rm 容器名或ID` | 删除容器；运行中的容器通常要先停止 |
| 删除镜像 | `docker rmi 镜像名或ID` | 删除本地镜像，需确认没有容器依赖 |
| 查看 Docker 版本 | `docker version` | 查看客户端和服务端版本 |
| 查看 Docker 信息 | `docker info` | 查看 Docker 服务和运行环境信息 |

如果部署手册用的是 **Docker Compose**，也可能接触过这些命令：

| 用途 | 命令 | 作用 |
|---|---|---|
| 启动编排服务 | `docker compose up -d` | 在后台启动 Compose 项目 |
| 查看编排服务状态 | `docker compose ps` | 查看项目中的容器状态 |
| 查看编排服务日志 | `docker compose logs -f` | 持续查看服务日志 |
| 停止编排服务 | `docker compose stop` | 停止服务但保留容器 |
| 停止并移除编排资源 | `docker compose down` | 停止并移除容器、网络等项目资源 |
| 重新创建服务 | `docker compose up -d --force-recreate` | 强制重建容器，使用前确认部署要求 |

面试时可以这样概括：

> 我主要用 Docker 命令查看镜像和容器状态、启动或停止容器、看日志，以及按部署手册启动服务。如果服务异常，我会先用 `docker ps -a` 确认状态，再用 `docker logs` 看启动日志；需要进一步检查时再查看容器配置或进入容器核对文件和进程。实际使用的命令以项目当时的部署方式为准。

特别留意：**`docker run` 是创建并启动新容器，`docker start` 是启动已有容器**；**`docker exec` 是进入正在运行的容器执行命令，`docker attach` 是连接到容器主进程的标准输入输出**。面试官问命令时，说清楚用途比背一长串命令更有帮助。

## 关联知识点

- [[针对实习的追问]] — 这条命令清单服务的面试主体：运维部署实习的职责表述原则与各轮问答话术
- [[Linux 网络模式详解]] — Docker 五模式（bridge/host/none/container/overlay）与 `-p 8080:80` 的 DNAT/SNAT 数据流
- [[OS八股]] — Linux 排障命令背景