# NeighborMarket Phase B 验收报告

> 报告日期：2026-10-09
> 阶段范围：NM-B-01 ～ NM-B-10
> 当前结论：NM-B-01 ～ NM-B-10 全部 VERIFIED，Phase B 验收通过。

## 1. 验收边界

Phase B 只验收事务、并发、幂等、数据库迁移、约束与一致性审计。它不代表整个项目已经完成：浏览器 E2E、SQL 性能基线、CI、Compose 和可观测性分别属于后续阶段。

所有数据库集成测试均配置为 Testcontainers 临时 MySQL 8.0，使用测试专用数据库和凭据，不读取开发/生产数据库连接。AI 测试不调用真实付费服务。

## 2. 任务结论与证据

| 任务 | 结论 | 主要实现/测试证据 |
| --- | --- | --- |
| NM-B-01 Testcontainers | VERIFIED | `ConcurrentConfirmationIntegrationTest`、`FlywayMigrationIntegrationTest` 均声明 MySQL 8.0 容器；Docker 不可用时显式 skipped |
| NM-B-02 Flyway 空库迁移 | VERIFIED | 空库 V1→V7、重复 migrate no-op、V5→V6、V6→V7 均有真实 MySQL 测试 |
| NM-B-03 并发确认 | VERIFIED | 两个独立线程/事务竞争确认；一个成功、一个 409；最终仅一条 ACTIVE handover |
| NM-B-04 取消/完成竞争 | VERIFIED | 双线程屏障竞争；只能收敛到 CANCELLED+AVAILABLE 或 COMPLETED+COMPLETED |
| NM-B-05 申请幂等 | VERIFIED | 唯一键配合原子 upsert，随后 `SELECT ... FOR UPDATE` 当前读；并发首次申请重复 5 轮 |
| NM-B-06 条件写入断言 | VERIFIED | 确认、取消、完成的关键写入均检查影响行数，竞争失败稳定返回 409 |
| NM-B-07 数据库约束 | VERIFIED | V7 状态 CHECK、终态字段 CHECK、复合外键、每物品最多一条 ACTIVE 的唯一生成列索引 |
| NM-B-08 真实事务回滚 | VERIFIED | 4 个 MySQL Trigger 故障注入场景验证整个事务恢复到操作前状态 |
| NM-B-09 一致性审计 | VERIFIED | 单条只读审计 SELECT；健康 V7 数据返回 0 行，构造的 V6 异常可被识别 |
| NM-B-10 阶段验收 | VERIFIED | 本报告已核对接口结果、最终表状态、历史行数量和并发轮次；最终全量容器复验 77 项全部通过、0 跳过 |

## 3. 核心场景的最终数据库状态

| 场景 | 接口/事务结果 | item 最终状态 | item_interest 最终状态 | handover 历史结果 |
| --- | --- | --- | --- | --- |
| 两人并发确认 | 1 成功、1 个 409 | RESERVED，指向胜者 handover | 胜者 CONFIRMED，败者 WAITING | 总计 1 条且仅 1 条 ACTIVE |
| 两次并发首次申请 | 两次成功并返回同一 interest ID | AVAILABLE | 总计 1 条 WAITING | 0 条 |
| 重试已确认申请 | 返回 409 | 原状态不变 | 原 CONFIRMED 的 slot/status/updated_at 不被覆盖 | 原记录不变 |
| 取消赢得竞争 | 取消成功、完成 409 | AVAILABLE，指针清空 | 获胜意向 CANCELLED，其他意向保持 WAITING | 原 ACTIVE 变为 CANCELLED；历史记录保留 |
| 完成赢得竞争 | 完成成功、取消 409 | COMPLETED，保留完成交接指针 | 获胜意向保持 CONFIRMED | 原 ACTIVE 变为 COMPLETED；历史记录保留 |
| 确认中间失败 | 整体异常并回滚 | AVAILABLE，指针为空 | 目标意向 WAITING | 0 条新增记录 |
| 取消中间失败 | 整体异常并回滚 | RESERVED，保留原指针 | CONFIRMED | 原交接恢复 ACTIVE，终态时间为空 |
| 完成中间失败 | 整体异常并回滚 | RESERVED，保留原指针 | CONFIRMED | 原交接恢复 ACTIVE，终态时间为空 |

上述断言不仅检查接口异常，还直接查询最终表状态和行数。历史取消/完成记录不会通过删除数据来“恢复”状态。

## 4. 并发轮次和测试证据

- 并发首次申请使用 `@RepeatedTest(5)`：每次均由两个线程同时申请，因此一次完整类执行覆盖 5 轮、10 次并发调用。
- 并发确认执行 1 轮、2 次竞争调用，并断言唯一 ACTIVE、物品指针及胜负双方意向。
- 取消与完成执行 1 轮、2 次竞争调用，并接受且验证两种合法终态。
- 事务回滚覆盖 4 个独立中间故障点。
- 2026-10-09 最终全量回归为 77 项通过、0 失败、0 错误、0 跳过，构建成功。
- `ConcurrentConfirmationIntegrationTest` 为 12 项通过，包含 5 轮并发首次申请；`FlywayMigrationIntegrationTest` 为 8 项通过。两个测试类均实际启动 MySQL 8.0 容器。
- 最终复验前曾因 Docker Engine 停止出现 16 项明确 skipped；启动 Engine 后已在相同代码状态重新执行并取得上述零跳过结果，因此不再构成验收阻塞。

Docker 恢复后必须在同一代码状态执行：

```powershell
cd E:\NeighborMarket\backend
mvn test
```

验收要求：77 项通过，0 failures、0 errors、0 skipped，并确认两个集成测试类各自实际启动 MySQL 容器。若测试数量因新增测试变化，则以“全部通过且容器测试无跳过”为准。

## 5. 当前设计结论

- 核心状态转换继续采用事务内条件 UPDATE，而不是为了展示技术而增加乐观锁。现有写入是离散状态机转换，条件写入能直接表达前置状态并获得受影响行数。
- `(item_id, user_id)` 唯一约束负责物理去重；原子 upsert 负责申请重试；`SELECT ... FOR UPDATE` 避免 MySQL `REPEATABLE READ` 旧快照读不到竞争事务结果。
- V7 的生成列唯一索引是“每物品最多一个 ACTIVE handover”的数据库最终防线；Service 条件 UPDATE 仍是正常竞争控制路径。
- V7 在 DDL 前先执行兼容性守卫，避免 MySQL DDL 自动提交后只落下一部分约束。
- 一致性审计只负责发现问题，不自动 UPDATE/DELETE；异常修复必须单独评审并先备份。

## 6. 残余风险与后续阶段

| 风险/缺口 | 级别 | 后续任务 |
| --- | --- | --- |
| 当前没有 CI 持续运行 Testcontainers，Docker 停止会使本地集成测试跳过 | P1 | NM-D-05 GitHub Actions CI |
| 并发测试是可控双线程竞争，不是长时间压力/混沌测试 | P2 | Phase D 测试分层；必要时再增加稳定性轮次 |
| 生产已有库执行 V7 前仍需先跑只读审计并备份 | P1 | 部署操作手册 / NM-D-04、NM-G-07 |
| 列表、看板、交接查询尚未取得 EXPLAIN ANALYZE 基线 | P1 | NM-C-01～NM-C-04 |
| 前端按钮防重复和服务端状态刷新仍需系统化 | P1 | NM-C-09 |
| 浏览器级认证、CSRF 与核心业务 E2E 尚未闭环 | P1 | NM-A-09、NM-D-07 |

## 7. Phase B 完成判定

业务代码、数据库迁移、约束与自动化测试已经达到 Phase B 的设计目标。NM-B-01 ～ NM-B-10 均具备真实 MySQL 或相应自动化测试证据并标记为 VERIFIED，Phase B 正式完成。后续可以按路线图进入 Phase C，但仍须单独规划、实施和验收，不能把本报告解释为整个项目已完成。
