# NeighborMarket 部署配置

## 1. 环境边界

| 环境 | Spring profile | 用途 | 约束 |
| --- | --- | --- | --- |
| 本地开发 | 默认 | IDE/Maven 与本机 MySQL 联调 | Cookie 可使用 HTTP；数据库密码仍必须外部提供 |
| 演示 | `demo` | 创建两个演示用户 | 必须提供两个演示密码；不得用于生产 |
| 自动测试 | 测试类配置 | 单元、Web 与 Testcontainers | 使用测试值和临时 MySQL，不读取开发库密码 |
| 生产 | `prod` | 容器或正式部署 | 数据库连接与允许源必须外部提供；Cookie 默认 Secure；监听所有容器接口 |

生产 profile 缺少 `DB_HOST`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 或 `APP_ALLOWED_ORIGINS` 时应快速失败。真实秘密只能通过部署环境、Secret 管理或本机忽略的 env 文件注入，不能写入镜像、仓库、Compose 文件或命令历史。

## 2. Docker Compose 一键启动

首次使用时，从示例生成一个被 Git 忽略的本机配置文件，并至少替换数据库密码：

```powershell
Copy-Item .env.example .env
# 编辑 .env：设置高强度 DB_PASSWORD；本地 HTTP 保持 SESSION_COOKIE_SECURE=false
docker compose up -d --build --wait
```

浏览器访问 `http://localhost:8088`（可通过 `APP_PORT` 修改）。Compose 项目名固定为 `neighbormarket`，因此会直接显示在 Docker Desktop 的 Containers 页面。查看状态与日志：

```powershell
docker compose ps
docker compose logs --tail=100 backend
```

停止服务但保留数据：

```powershell
docker compose stop
```

再次启动使用 `docker compose up -d --wait`。MySQL 数据保存在命名卷 `neighbormarket_mysql-data`；普通重启或重新创建容器不会丢失数据。不要执行 `docker compose down -v`，除非明确要永久删除该 Docker 环境的数据。MySQL 和后端端口均不映射到宿主机，只有前端同源入口对外开放。

## 3. 镜像设计

从仓库根目录构建：

```powershell
docker build -t neighbormarket-backend:local backend
```

镜像使用 Maven/JDK 构建阶段和 Alpine JRE 运行阶段。运行容器使用非 root 的 `app` 用户，镜像不复制源码、Maven 缓存或环境文件，默认启用 `prod` profile。

前端镜像使用 Node 构建阶段和非特权 Nginx 运行阶段；Nginx 提供 SPA fallback，并把 `/api/` 转发至内部后端。两个应用容器均以非 root 用户运行。不要用带明文密码的 `docker run -e DB_PASSWORD=...` 命令作为正式部署方式。

## 4. 健康端点

浏览器入口公开：

- `/actuator/health`

容器内部还启用 liveness/readiness probes 和 `info`。健康详情不向匿名请求展示；Nginx 对其他 `/actuator/` 路径直接返回404，`env`、`configprops` 等敏感管理端点没有暴露。Dockerfile 的健康检查请求容器内部 `/actuator/health`；数据库不可用时 readiness/整体健康会反映异常。

## 5. 已验证范围与边界

2026-10-16 已实际验证：前后端镜像构建、三个服务健康依赖、7个 Flyway 迁移、前后端非 root 运行、同源页面/API/健康路由、管理端点拦截、CSRF/登录/Session/核心列表 smoke，以及整套容器重启后的数据卷持久化。验证使用全新的项目专属 Docker 卷，不连接或覆盖开发数据库。

部署后可以下列只读命令快速验证。口令仅通过进程环境传入，脚本不会输出口令：

```powershell
$env:SMOKE_PASSWORD='replace-with-deployment-test-user-password'
./scripts/smoke-test.ps1 -BaseUrl 'http://127.0.0.1:8088' -Username 'neighbor_a'
```

当前仍未完成正式 HTTPS 终止和自动备份/恢复演练。公网环境必须在 TLS 反向代理后运行，并设置 `SESSION_COOKIE_SECURE=true` 与真实 HTTPS `APP_ORIGIN`。
