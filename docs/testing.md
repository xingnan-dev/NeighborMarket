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
