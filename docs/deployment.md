# NeighborMarket Docker 与部署指南

本文对应 NM-G-07，面向单机 Docker Compose 部署。当前拓扑是 `frontend(Nginx) -> backend(Spring Boot) -> mysql(MySQL 8.0)`；只有前端端口映射到宿主机，MySQL 和后端仅在 Compose 网络内可达。

## 1. 前置条件与边界

- Docker Desktop，或安装了 Docker Engine 与 Compose v2 的 Linux 主机。
- 至少2 CPU、4 GB 可用内存和10 GB 可用磁盘；图片和数据库增长后应单独监控卷空间。
- 仓库完整副本，以及能读取所需镜像/依赖仓库的网络。
- 公网部署需要外部 TLS 反向代理；仓库内 Compose 只提供 HTTP 入口，不负责证书签发或续期。

环境边界：

| 环境 | Spring profile | 用途 | 约束 |
| --- | --- | --- | --- |
| 本地开发 | 默认 | Maven/Vite 与本机 MySQL 联调 | Cookie 可用 HTTP；数据库密码仍外部提供 |
| 本地演示 | `demo` | 创建两个演示用户 | 必须注入两个演示密码；不得用于生产 |
| 自动测试 | 测试类/`compose.e2e.yml` | 单元、Testcontainers、隔离 E2E | 使用临时库和临时卷 |
| Compose 部署 | `prod` | 单机容器运行 | 必需配置缺失时快速失败；不创建演示用户 |

生产 profile 缺少数据库连接或 `APP_ALLOWED_ORIGINS` 时会启动失败。真实秘密只能通过部署平台 Secret、本机忽略的 `.env` 或进程环境注入，不能写入镜像、仓库、日志或工单。

## 2. 首次配置

从仓库根目录执行：

```powershell
Copy-Item .env.example .env
```

编辑被 Git 忽略的 `.env`，Compose 部署至少确认：

```dotenv
DB_NAME=neighbor_market
DB_USERNAME=neighbor_app
DB_PASSWORD=替换为高强度随机密码
APP_PORT=8088
APP_ORIGIN=http://localhost:8088
SESSION_COOKIE_SECURE=false
LLM_API_KEY=
LLM_BASE_URL=
LLM_MODEL=
LLM_PROVIDER=
```

- 本机 HTTP 才能使用 `SESSION_COOKIE_SECURE=false`。
- HTTPS 部署必须把 `APP_ORIGIN` 改为浏览器实际访问的唯一 HTTPS Origin，并设置 `SESSION_COOKIE_SECURE=true`。
- 未使用 AI 时四个 `LLM_*` 值全部留空；使用时必须同时提供密钥、兼容端点、模型和供应商标识。
- Compose 固定启用 `prod`，不会读取示例文件里的本地 `DB_HOST` 或创建演示账号。

先验证变量解析和 Compose 结构，不启动服务：

```powershell
docker compose config --quiet
docker compose config --services
```

第二条命令应只显示 `mysql`、`backend`、`frontend`。`docker compose config` 的完整输出会展开环境变量，因此不要把它粘贴到公开日志。

## 3. 构建、启动与健康检查

```powershell
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

三个服务都应显示 `healthy`。首次构建会下载 Maven、npm 和镜像依赖，耗时取决于网络；Flyway 在后端启动时自动迁移到当前 V8。浏览器访问 `APP_ORIGIN`，默认是 `http://localhost:8088`。

只读健康检查：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8088/actuator/health'
docker compose logs --tail=100 backend
docker compose logs --tail=100 mysql
```

健康响应应为 `{"status":"UP"}`。浏览器入口只转发 `/actuator/health`；其他管理端点不对外公开。数据库异常会使后端整体健康失败，前端健康只说明 Nginx 自身可响应。

如已有一个受控测试用户，可执行包含首页、健康、CSRF、登录、Session 和核心列表的 smoke：

```powershell
$env:SMOKE_PASSWORD = Read-Host 'Smoke user password' -MaskInput
./scripts/smoke-test.ps1 -BaseUrl 'http://127.0.0.1:8088' -Username '受控测试账号'
Remove-Item Env:SMOKE_PASSWORD
```

脚本不会输出口令。没有测试用户时，只执行匿名健康检查，不能把它等同于认证业务 smoke。

## 4. 日常停止、启动与日志

停止应用但保留容器和所有数据：

```powershell
docker compose stop
```

再次启动：

```powershell
docker compose start
docker compose ps
```

删除容器和网络但保留命名卷：

```powershell
docker compose down
```

不要在需要保留数据时执行 `docker compose down -v`。`-v` 会删除 MySQL 和图片卷，且不能靠重新构建镜像恢复。当前持久卷为：

- `neighbormarket_mysql-data`：数据库文件。
- `neighbormarket_image-data`：用户上传的规范化图片。

查看卷和磁盘占用：

```powershell
docker volume inspect neighbormarket_mysql-data
docker volume inspect neighbormarket_image-data
docker system df -v
```

## 5. 一致性备份

数据库记录和图片卷必须成对备份。为避免备份期间发生图片替换或数据库提交，使用短维护窗口：停止前后端，只保留 MySQL；完成两份备份后再恢复服务。

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = New-Item -ItemType Directory -Path (Join-Path 'backups' $stamp)
$backupPath = $backupDir.FullName

docker compose stop frontend backend

docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' |
  Set-Content -LiteralPath (Join-Path $backupPath 'database.sql') -Encoding utf8NoBOM

docker run --rm `
  --mount source=neighbormarket_image-data,target=/data,readonly `
  --mount "type=bind,source=$backupPath,target=/backup" `
  alpine:3.20 tar -czf /backup/images.tgz -C /data .

docker compose start backend frontend
docker compose ps
```

备份后至少检查文件非空，并将其复制到与部署主机故障域不同、访问受控的位置：

```powershell
Get-Item (Join-Path $backupPath 'database.sql'), (Join-Path $backupPath 'images.tgz') |
  Select-Object FullName, Length, LastWriteTime
```

这是一套人工逻辑备份流程，不是自动备份系统。应由实际部署方确定保留周期、加密、异地复制、监控和定期恢复演练。

## 6. 在全新空卷恢复

恢复优先在另一台主机或新的 Compose 项目上演练，确认目标卷为空后再操作。不要把下列流程用于覆盖仍在运行的唯一生产副本。

1. 检出与备份兼容的代码版本，创建 `.env`，保持数据库名和应用账号与备份一致。
2. 只启动空 MySQL，并确认健康。
3. 导入 SQL，再把图片包解压到空图片卷。
4. 启动完整应用，由 Flyway 补齐备份之后的新迁移。

```powershell
$restorePath = (Resolve-Path 'backups\替换为备份目录').Path

docker compose up -d mysql --wait --wait-timeout 120
$mysqlContainer = docker compose ps -q mysql
docker cp (Join-Path $restorePath 'database.sql') "${mysqlContainer}:/tmp/neighbormarket-restore.sql"
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /tmp/neighbormarket-restore.sql'
docker compose exec -T mysql rm -f /tmp/neighbormarket-restore.sql

docker volume create neighbormarket_image-data
docker run --rm `
  --mount source=neighbormarket_image-data,target=/data `
  --mount "type=bind,source=$restorePath,target=/backup,readonly" `
  alpine:3.20 tar -xzf /backup/images.tgz -C /data

docker compose up -d --build --wait --wait-timeout 180
```

随后执行健康检查、认证 smoke、关键图片访问和业务数量核对。只有在隔离环境实际恢复成功，才能声称备份可恢复；“备份命令成功”本身不构成灾备验证。

## 7. 升级与回滚

升级前必须阅读新增 Flyway 迁移、创建成对备份并记录当前 Git 提交：

```powershell
git rev-parse HEAD
docker compose images
docker compose pull --ignore-buildable
docker compose up -d --build --wait --wait-timeout 180
```

升级后检查健康、后端迁移日志和 smoke。应用镜像可回退到旧 Git 提交重新构建，但数据库迁移不支持自动向下迁移；如果新版本已经执行了不兼容迁移，必须停止写入，并用升级前的数据库与图片成对备份恢复。不要只回退 JAR 而让它连接未知兼容性的新版 Schema。

## 8. HTTPS 与反向代理

公网部署必须在受信任的 TLS 反向代理或负载均衡器后运行：

- 只公开 HTTPS 入口，HTTP 重定向到 HTTPS。
- 传递 `Host`、`X-Forwarded-For` 和 `X-Forwarded-Proto`。
- `APP_ORIGIN` 精确设置为外部 HTTPS Origin，不使用通配符。
- `SESSION_COOKIE_SECURE=true`，并验证登录、CSRF 与 Session。
- 不公开 MySQL、后端8080或其他 Actuator 端点。

当前 Compose 没有内置证书和多实例 Session 共享；若扩展为多个后端实例，应先按 `REDIS_DECISION.md` 重新评估 Session 与限流架构。

## 9. 常见故障

| 现象 | 检查 | 处理 |
| --- | --- | --- |
| Compose 提示缺少 `DB_PASSWORD` | `docker compose config --quiet` | 在忽略的 `.env` 或 Secret 中提供，勿写入 Compose |
| MySQL healthy、后端 unhealthy | `docker compose logs backend` | 检查数据库名/账号/密码、V8迁移和卷空间 |
| 登录后立即变匿名 | 浏览器 Cookie 与 `APP_ORIGIN` | HTTPS 环境启用 Secure Cookie；Origin 必须完全匹配 |
| 页面正常但 `/api` 失败 | 前端/后端日志与后端健康 | 检查 backend 健康、Nginx 内部 DNS 和迁移失败 |
| 图片404 | `image-data` 卷、`/media/` 路由 | 确认恢复了匹配的图片备份并检查文件权限 |
| AI 估价不可用 | 四个 `LLM_*` 配置和后端日志分类 | 未配置时使用手工定价；不要在日志中输出密钥/Prompt |
| 磁盘增长 | `docker system df -v`、卷监控 | 先备份并确认所有权，再制定日志/镜像/孤儿图片清理策略 |

## 10. 已验证范围

本地和 CI 已验证双端镜像构建、非 root 运行、三服务健康依赖、Flyway V1～V8、同源页面/API/健康路由、CSRF/Session、核心业务 E2E、隔离卷持久化及重启后 smoke。NM-G-07 还验证了当前 Compose 配置可解析。

尚未宣称完成：正式域名/TLS、自动备份调度、异地备份、生产告警和在用户生产数据上的恢复演练。第5、6节提供可执行的人工流程，但实际部署仍必须独立完成并记录恢复演练。
