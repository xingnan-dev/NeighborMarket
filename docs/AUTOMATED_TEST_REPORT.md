# NeighborMarket 自动化测试报告

> 报告日期：2026-10-19  
> 本地验证基线：`8a2f993` 及其后仅文档变更  
> 结论：本次执行的后端、前端、组件和核心 E2E 全部通过，失败0、错误0、跳过0。

## 1. 执行环境与数据边界

| 项目 | 本次环境 |
| --- | --- |
| 操作系统 | Windows 11 / PowerShell |
| Java / Maven | Eclipse Adoptium 17.0.20.1 / Maven 3.9.16 |
| Node.js / npm | Node.js 24.16.0 / npm 11.13.0 |
| 容器 | Docker Desktop Engine 29.7.2；Testcontainers MySQL 8.0 |
| 浏览器 | Playwright Chromium，无头模式 |
| 数据库迁移 | 所有当前集成测试从空临时库执行 Flyway V1～V8 |

Testcontainers 数据库和 `neighbormarket-g06-e2e` Compose 项目均为本次执行临时创建。E2E 完成后已删除对应容器、网络、MySQL 卷和图片卷。测试未读取或修改开发数据库，未配置或调用真实 AI 服务；AI 协议、超时、错误解析和限流使用 mock/桩验证。

## 2. 本次本地执行结果

| 层次 | 命令 | 通过 | 失败 | 错误 | 跳过 | 主要覆盖 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 后端单元/领域 | `mvn --batch-mode --no-transfer-progress verify` | 67 | 0 | 0 | 0 | AI、物品、图片、交接、状态转换、看板与用户加载 |
| 后端 Web/安全 | 同上 | 15 | 0 | 0 | 0 | Session、CSRF、Actuator、请求校验、异常响应、请求 ID |
| 后端架构/配置/SQL 契约 | 同上 | 14 | 0 | 0 | 0 | Controller/Service 边界、生产配置、Cookie、看板 SQL |
| 后端 MySQL 集成/性能 | 同上 | 38 | 0 | 0 | 0 | Flyway、并发、回滚、查询数、`EXPLAIN ANALYZE`、搜索策略 |
| 前端 Node 单元 | `npm test` | 19 | 0 | 0 | 0 | API 错误、请求竞态、防重、表单、图片、AI 建议、看板 |
| 前端 Vue 组件 | `npm test` | 9 | 0 | 0 | 0 | 登录、详情权限、发布/编辑校验、错误与重复提交 |
| 浏览器核心 E2E | 隔离 Compose 后 `npm run test:e2e` | 1 | 0 | 0 | 0 | 双 Session 登录→发布→申请→确认→完成 |

后端合计134项，前端单元与组件合计28项，浏览器 E2E 1项。后端 `verify` 同时完成 Spring Boot JAR 打包；前端 `npm run build` 成功，Vite 转换98个模块。

## 3. 关键自动化证据

### 3.1 数据库与并发

- `FlywayMigrationIntegrationTest` 覆盖空库迁移、V6/V7/V8 增量迁移、非法历史数据拒绝和迁移修复边界。
- `ConcurrentConfirmationIntegrationTest` 的17项真实 MySQL 测试覆盖确认竞争、取消/完成竞争、重复申请、编辑/申请竞争和事务中途失败回滚。
- `ItemListQueryIntegrationTest` 与 `ExchangeReadQueryIntegrationTest` 使用 MyBatis 语句计数器证明主要列表查询数不随返回行数线性增长。
- `PerformanceBaselineIntegrationTest` 与 `SearchStrategyIntegrationTest` 在固定数据集执行真实 `EXPLAIN ANALYZE`；详细结果见 `PERFORMANCE_BASELINE.md`。

### 3.2 安全与失败路径

- Web 测试覆盖匿名访问、Session 登录、CSRF、管理端点暴露、DTO 参数校验、稳定错误响应和请求 ID。
- 服务测试覆盖所有权、非法状态、旧版本、重复终态操作、价格规则和 AI 超时/上游失败/解析失败。
- 图片测试覆盖 JPEG/PNG、大小与尺寸限制、重编码、文件存储和替换补偿路径；浏览器不直接信任原文件名。

### 3.3 前端与全栈

- Node 单元测试验证最新请求胜出、AbortController 取消、按资源键防重复和失败后可重试。
- jsdom 组件测试验证登录、发布、编辑和详情页的权限/校验分支；HTTP 全部 mock，因此不冒充全栈证据。
- Playwright 使用两个独立浏览器 Context 和真实 Compose 服务完成核心成功路径；失败路径由更稳定的 Web、服务与 MySQL 测试承担。

## 4. 覆盖率

本次 JaCoCo 报告分析82个生产类：

| 指标 | 覆盖 | 比例 |
| --- | ---: | ---: |
| 行 | 927 / 1,122 | 82.6% |
| 分支 | 264 / 431 | 61.3% |
| 方法 | 390 / 456 | 85.5% |
| 类 | 76 / 82 | 92.7% |

覆盖率是缺口发现工具，不是正确性证明，也未设置统一百分比门禁。执行中 JaCoCo 对第三方 JSqlParser 的一个生成型超大方法给出非阻断 instrumentation warning；项目测试、打包和82个生产类报告仍成功完成，该警告不计为测试跳过或业务代码未覆盖。

## 5. CI 证据

GitHub Actions 工作流为 `.github/workflows/ci.yml`，在 push、pull request 和手动触发时运行：

1. Java 17 后端 `mvn verify`，失败时保留 Surefire，始终尝试保存 JaCoCo。
2. Node.js 22 执行 `npm ci`、前端测试和生产构建。
3. 基础 Job 通过后校验 Compose 并构建前后端镜像。
4. 独立 Compose 环境执行部署 smoke、Playwright 核心闭环、整套重启及持久数据 smoke，最后始终清理。

本报告编写前的最新功能提交 `8a2f993` 对应 [CI 运行 39226878935](https://github.com/xingnan-dev/NeighborMarket/actions/runs/39226878935)，结论为 `success`。较早的 Phase D 远端验收运行 `38767299683` 同样成功。CI 页面与运行日志是远端状态的权威来源，文档中的历史状态不能替代分支保护。

## 6. 已知限制与判定

- 核心 E2E 目前只有1条成功路径，不覆盖所有权限、并发和故障组合；这些组合主要由下层自动化覆盖。
- 本地 Node.js 24 与 CI Node.js 22 不同，但两者均执行锁定依赖；Node.js 22 CI 是交付环境证据。
- 性能耗时来自本机 Docker Desktop，只适合同数据集/同环境对照，不是生产 SLA。
- 未执行真实 AI 请求，这是刻意的安全和成本边界；真实供应商连通性仍属于部署环境检查。

本次所有可执行层均为零失败、零错误、零跳过，真实 MySQL、浏览器和构建均实际运行。因此 NM-G-06 可标记为 `VERIFIED`。
