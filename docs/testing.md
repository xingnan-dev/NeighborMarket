# NeighborMarket 测试分层与覆盖率

## 后端测试层次

| 层次 | 位置/命名 | 目的 |
| --- | --- | --- |
| 单元测试 | `service/*Test`、`ai/*Test`、`dashboard/*Test` | 隔离验证业务规则、异常分支和外部 AI 协议，不连接真实服务 |
| Web 与安全测试 | `web/*Test`、`PricingControllerSecurityTest` | 验证参数校验、Session/CSRF、错误响应、请求 ID 和接口权限 |
| 架构/SQL 契约测试 | `*ArchitectureTest`、`*SqlContractTest`、迁移契约测试 | 防止职责边界、关键 SQL 和迁移约束回退 |
| MySQL 集成测试 | `integration/*IntegrationTest` | 使用 Testcontainers MySQL 验证 Flyway、事务、并发、查询次数和最终数据状态 |
| 性能证据测试 | `PerformanceBaselineIntegrationTest`、`SearchStrategyIntegrationTest` | 输出可复现的 EXPLAIN ANALYZE 与基线，不作为不稳定的微秒级门禁 |

完整验证命令：

```powershell
cd backend
mvn --batch-mode --no-transfer-progress verify
```

该命令运行全部测试、打包并在 `target/site/jacoco/index.html` 生成 JaCoCo HTML 报告，同时生成 XML/CSV 供 CI 或后续分析使用。测试依赖 Docker Desktop 提供临时 MySQL，不读取或修改开发数据库，也不调用真实 AI 服务。

## 覆盖率使用原则

覆盖率用于发现未测试分支，不等同于业务正确性。本阶段不设置统一百分比门槛，原因是实体访问器、配置装配和框架入口会稀释数字，而并发一致性、事务回滚和权限边界的测试价值远高于简单行覆盖。

每次报告应优先检查：

1. 交接确认、取消、完成的成功及竞争失败分支。
2. 重复申请、状态变化和事务回滚。
3. 登录、Session、CSRF、越权和统一异常响应。
4. AI 超时、上游失败、响应解析和限流边界。
5. Mapper 自定义 SQL 与 Flyway 从空库迁移。

CI 会保存 JaCoCo 报告14天。新增核心业务代码时，应针对缺口增加有业务断言的测试；禁止仅为提高覆盖率调用无意义代码路径。

## 2026-10-14 基线

完整 `mvn verify` 共运行107项测试，0失败、0错误、0跳过。JaCoCo 分析68个生产类：行覆盖率83.7%（679/811）、分支覆盖率59.9%（175/292）、方法覆盖率84.4%（325/385）、类覆盖率95.6%（65/68）。这些数字是观察基线，不是发布门禁。

报告显示主要缺口位于薄 Controller 委托、`ItemService` 发布/详情路径以及少量 DTO/启动入口。薄 Controller 将由 NM-D-09/NM-D-10 的组件和 E2E 测试覆盖，避免重复模拟实现细节；`ItemService` 后续应优先补充发布事务回滚、用户不存在和详情不存在场景。本批已补充数据库用户加载/不存在分支，以及物品确认时记录不存在、条件 UPDATE 竞争失败分支。

## 2026-10-19 最终复核

当前完整 `mvn verify` 运行134项测试，0失败、0错误、0跳过，并实际启动 Testcontainers MySQL 8.0、执行 Flyway V1～V8、打包 JAR 和生成 JaCoCo。当前覆盖率为行82.6%（927/1,122）、分支61.3%（264/431）、方法85.5%（390/456）、类92.7%（76/82）。前端19项 Node 单元测试、9项 Vue 组件测试与生产构建通过；隔离 Compose 中1项 Playwright 双会话核心 E2E 通过，环境随后销毁。完整分层、环境、CI 链接和限制见 `AUTOMATED_TEST_REPORT.md`。

## 前端测试

`npm test` 先运行 `test/*.test.js` 中的 Node 单元测试，再由 Vitest + jsdom 运行 `test/component/**/*.spec.js` 组件测试。组件测试当前覆盖登录校验、错误反馈、安全跳转、重复提交、详情页匿名/发布者权限状态，以及发布表单前置校验；所有网络调用均 mock，不接触真实后端或 AI 服务。

## 核心浏览器 E2E

Playwright 用两个独立浏览器 Session 验证“发布者登录与发布 → 领取人登录与申请 → 发布者确认 → 完成交接”。运行环境使用 `neighbormarket-e2e` Compose 项目、独立 MySQL 卷和 `neighbor_market_e2e` 数据库，不与开发/演示环境共享数据。用例只发布免费物品，不配置也不调用真实 AI。

PowerShell 本地运行示例（口令只用于该次隔离测试）：

```powershell
$env:DB_PASSWORD='replace-with-e2e-db-password'
$env:E2E_USER_A_PASSWORD='replace-with-e2e-user-a-password'
$env:E2E_USER_B_PASSWORD='replace-with-e2e-user-b-password'
$env:DB_NAME='neighbor_market_e2e'
$env:APP_PORT='18088'
$env:APP_ORIGIN='http://127.0.0.1:18088'
$env:E2E_BASE_URL='http://127.0.0.1:18088'
docker compose -p neighbormarket-e2e -f compose.yml -f compose.e2e.yml up -d --build --wait --wait-timeout 180
cd frontend
npm run test:e2e
cd ..
docker compose -p neighbormarket-e2e -f compose.yml -f compose.e2e.yml down -v
```

最后一条命令只删除命名为 `neighbormarket-e2e` 的临时容器、网络和卷。失败时 Playwright 将诊断资料写入被 Git 忽略的 `frontend/test-results/`；CI 仅在失败时保存这些资料7天，并始终清理隔离环境。
