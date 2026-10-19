# NeighborMarket

NeighborMarket 是一个面向社区闲置物品流转的全栈项目，采用 Vue 3 + Vite 前端、Spring Boot REST 后端、MyBatis-Plus、MySQL 8 和 Flyway。项目重点展示认证与授权、事务和并发正确性、安全图片处理、真实 MySQL 测试、可复现部署及有依据的技术取舍。

## 已实现功能

- Session 登录与两个本地演示账号（neighbor_a、neighbor_b）
- 物品发布、名称搜索、分类筛选、交易方式筛选和状态筛选
- 带版本冲突检测的物品编辑，以及 JPEG/PNG 主图选择、预览、上传、替换和删除
- 24/72 小时新鲜度提示，以及发布者确认“还在”
- 想要、取消想要和重新申请
- 公共交接地点与 1～5 个未来交接时段
- 发布者确认领取人
- 交接双方取消交接
- 发布者标记已送出并完成归档
- 公开留言与发布者一层回复
- 固定价格物品的 AI 估价、信息不足追问和手工定价降级
- 社区数据看板：本月发布、本月成交、当前在售、最快成交和未成交意向排行
- 桌面、平板与移动端响应式界面
- 可重复执行的演示数据脚本和 frontend/public/demo/ 本地图片

核心交换流程通过同一 MySQL 事务内的行锁、条件 UPDATE、影响行数检查和数据库约束保证一致性。当前保持模块化单体；没有为展示技术栈而引入 Redis、Spring Cloud、Kafka 或 RabbitMQ，采用与复评条件见文档索引。

## 快速启动

### Docker Compose

需要 Docker Desktop。复制示例环境文件并至少设置高强度数据库密码：

```powershell
Copy-Item .env.example .env
# 编辑 .env；未使用 AI 时清空 LLM_API_KEY、LLM_BASE_URL、LLM_MODEL 和 LLM_PROVIDER
docker compose up -d --build --wait
```

浏览器访问 <http://localhost:8088>。Compose 默认启用 `prod` profile，不会创建演示账号；生产式环境的用户应由受控初始化流程提供。停止但保留 MySQL 和图片卷使用 `docker compose stop`，不要在需要保留数据时执行 `docker compose down -v`。完整说明见 [docs/deployment.md](docs/deployment.md)。

### 本地开发与演示账号

需要 Java 17、Maven、Node.js、npm 和 MySQL 8。数据库名默认为 neighbor_market。

在当前 PowerShell 会话中设置环境变量；密码只在本机交互输入，不写入仓库：

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_NAME = 'neighbor_market'
$env:DB_USERNAME = 'neighbor_app'
$env:DB_PASSWORD = Read-Host 'Database password' -MaskInput
$env:APP_ALLOWED_ORIGINS = 'http://localhost:5173,http://localhost:5174'
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:DEMO_USER_A_PASSWORD = Read-Host 'neighbor_a demo password' -MaskInput
$env:DEMO_USER_B_PASSWORD = Read-Host 'neighbor_b demo password' -MaskInput
```

AI 配置只从环境变量读取，不能把密钥写入代码、文档或日志：

```powershell
$env:LLM_API_KEY = Read-Host 'LLM API key' -MaskInput
$env:LLM_BASE_URL = 'https://your-openai-compatible-endpoint'
$env:LLM_MODEL = 'your-model-name'
$env:LLM_PROVIDER = 'openai-compatible'
```

启动后端：

```powershell
Set-Location backend
mvn spring-boot:run
```

启动前端（另开终端）：

```powershell
Set-Location frontend
npm ci
npm run dev
```

浏览器访问 <http://localhost:5174>。只有显式启用 `demo` profile 时才会创建 `neighbor_a` 和 `neighbor_b`；密码就是本机环境变量中交互输入的值。不要把密码写入文档、仓库或命令历史，非演示环境不要启用该 profile。

AI 估价单次请求超时为 45 秒；同一登录用户 60 秒内最多请求一次。供应商错误、429、超时或信息不足时，页面保留手工填写价格的发布路径。

## 测试与构建

```powershell
Set-Location backend
mvn --batch-mode --no-transfer-progress verify
Set-Location ../frontend
npm ci
npm test
npm run build
```

最近一次本地全量验证为后端 134 项测试全部通过，包含 MySQL 8 Testcontainers、Flyway V1→V8、并发竞争、迁移、回滚、查询和性能测试；前端已有 19 项单元测试、9 项组件测试及 Playwright 核心流程。CI 会执行后端 `verify`、前端测试与构建、双端镜像构建、隔离 Compose smoke、浏览器核心闭环和重启后数据持久化验证。

后端提供最小化 Actuator 健康端点，浏览器入口仅转发 `/actuator/health`，不会暴露环境或配置详情。前后端使用多阶段非 root 镜像，MySQL 与图片分别使用持久卷。

仓库包含 GitHub Actions CI：后端使用 Java 17 执行包含 Testcontainers 的 `mvn verify`，前端基于锁文件执行测试和生产构建，最后校验 Compose 并构建双端镜像。工作流文件见 `.github/workflows/ci.yml`。

## 演示数据

演示数据脚本和图片说明位于 [scripts/demo/README.md](scripts/demo/README.md)。脚本会清理并重建本地业务数据，执行前必须备份；不要在演示过程中执行 reset 脚本。图片放在 frontend/public/demo/，由物品记录使用 /demo/... 路径引用。

## 文档索引

- [docs/architecture.md](docs/architecture.md)：最终系统架构与边界
- [docs/er.md](docs/er.md)：Flyway V1～V8 实体关系、字段与关键约束
- [docs/IMAGE_STORAGE_AND_SECURITY.md](docs/IMAGE_STORAGE_AND_SECURITY.md)：图片存储抽象、上传限制、威胁模型与生命周期规则
- [docs/STATE_TRANSITION_AUDIT.md](docs/STATE_TRANSITION_AUDIT.md)：物品、意向与交接的状态转换、权限及 409 边界
- [docs/STATE_MACHINE_AND_CONCURRENCY.md](docs/STATE_MACHINE_AND_CONCURRENCY.md)：状态机、并发竞争、失败回滚与幂等语义
- [docs/PERFORMANCE_BASELINE.md](docs/PERFORMANCE_BASELINE.md)：可复现数据集、SQL 执行计划、查询数与优化证据
- [docs/AUTOMATED_TEST_REPORT.md](docs/AUTOMATED_TEST_REPORT.md)：自动化测试分层、执行环境、数量、覆盖率、跳过项与 CI 证据
- [docs/REDIS_DECISION.md](docs/REDIS_DECISION.md)：Redis、Session 和共享限流的采用门槛
- [docs/CACHE_DECISION.md](docs/CACHE_DECISION.md)：物品、搜索、看板与图片缓存决策
- [docs/ARCHITECTURE_BOUNDARY_DECISION.md](docs/ARCHITECTURE_BOUNDARY_DECISION.md)：模块化单体、微服务与消息队列取舍
- [docs/AI_PRICING_METRICS.md](docs/AI_PRICING_METRICS.md)：AI 结果、耗时、限流分类及隐私边界
- [docs/PHASE_B_ACCEPTANCE_REPORT.md](docs/PHASE_B_ACCEPTANCE_REPORT.md)：事务、并发、幂等与数据库可靠性验收
- [docs/PHASE_C_ACCEPTANCE_REPORT.md](docs/PHASE_C_ACCEPTANCE_REPORT.md)：查询治理、模块边界与性能验收
- [docs/PHASE_D_REPORT.md](docs/PHASE_D_REPORT.md)：测试、CI、容器与可观测性验收
- [docs/PHASE_E_ACCEPTANCE_REPORT.md](docs/PHASE_E_ACCEPTANCE_REPORT.md)：编辑、图片、响应式与可访问性验收
- [docs/PHASE_F_ACCEPTANCE_REPORT.md](docs/PHASE_F_ACCEPTANCE_REPORT.md)：可选技术阶段的最终验收证据
- [docs/stage-4.md](docs/stage-4.md)：AI 估价协议与校验记录
- [docs/demo-guide.md](docs/demo-guide.md)：5 分钟项目演示脚本
- [docs/technical-stack.md](docs/technical-stack.md)：技术栈与工程实践简介
- [docs/deployment.md](docs/deployment.md)：环境隔离、后端镜像与健康检查约定
- [docs/testing.md](docs/testing.md)：后端测试分层、JaCoCo 报告与覆盖率使用原则
- [scripts/demo/README.md](scripts/demo/README.md)：演示数据备份、重建和验证说明
- [scripts/audit/README.md](scripts/audit/README.md)：只读数据一致性审计与异常处理原则

## 时间规则

数据库使用 Asia/Shanghai 业务墙上时间。接口声明 displayTimeZone: Asia/Shanghai；前端按该时区格式化时间。发布后 4 小时内显示“刚上架”，24～72 小时显示“新上架”，更早记录显示天数；超过 7 天未确认的物品会提示较久未确认。

## 已知限制

- 后端已支持单张 JPEG/PNG 主图上传、替换和删除；演示数据仍使用 frontend/public/demo/ 下的本地静态图片。
- Session 写操作使用 Cookie CSRF Token；前端启动时获取令牌并由 Axios 通过 `X-XSRF-TOKEN` 回传。
- AI 估价不代表实时二手市场成交价，仅提供辅助参考。
- 当前部署为单个后端实例；Session 和 AI 冷却不跨实例共享。达到文档中的扩容或严格配额条件后再复评 Redis。
- AI 指标只记录固定结果分类、耗时和进程内匿名用户键，不记录 Prompt、物品内容或供应商响应；当前供应商协议没有可信 token 用量，因此不虚构货币成本。
- 尚未配置正式公网域名、TLS 终止、自动备份恢复和生产级指标采集。公网部署必须位于 HTTPS 反向代理后，并启用 Secure Session Cookie。
