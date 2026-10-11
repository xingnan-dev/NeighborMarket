# NeighborMarket Phase C 验收报告

> 报告日期：2026-10-11
> 阶段范围：NM-C-01 ～ NM-C-10
> 当前结论：NM-C-01 ～ NM-C-10 全部 VERIFIED，Phase C 验收通过。

## 1. 验收边界

Phase C 验收 SQL 基线、主要 N+1、关键查询计划、搜索策略、后端职责边界，以及前端请求竞态和操作级防重。它不代表整个项目已经交付：Docker/Compose、CI、Actuator、浏览器组件测试和核心 E2E 属于 Phase D，物品编辑与图片上传属于 Phase E。

本次数据库验证仅使用 Testcontainers 创建的临时 MySQL 8.0；未连接或修改开发/演示数据库，未调用真实 AI 服务。验收没有新增生产数据库迁移、依赖或 API 路径。

## 2. 任务结论与主要证据

| 任务 | 结论 | 主要实现/测试证据 |
| --- | --- | --- |
| NM-C-01 性能基线 | VERIFIED | `PerformanceBaselineIntegrationTest` 固定生成100用户、3,000物品、3,000时段、9,000意向和1,200交接；真实执行 `EXPLAIN ANALYZE` |
| NM-C-02 物品列表 N+1 | VERIFIED | `ItemMapper` 直接投影发布者昵称；市场列表查询数由 `2 + N` 固定为2，我的发布由 `3 + N` 固定为3 |
| NM-C-03 意向/交接/留言 N+1 | VERIFIED | `InterestViewRow`、`HandoverViewRow`、`CommentViewRow` 联表投影；查询数均有固定上界并由 `ExchangeReadQueryIntegrationTest` 验证 |
| NM-C-04 查询与索引 | VERIFIED | `HandoverMapper` 调整驱动顺序，`DashboardMapper` 先聚合意向再关联物品；对候选 SQL 比较结果集与计划，拒绝无收益索引和不等价改写 |
| NM-C-05 搜索策略 | VERIFIED | `SearchStrategyIntegrationTest` 在30,000物品上测量高/低/单/零命中 LIKE，并临时评估 FULLTEXT；当前保留子串语义且不引入 Elasticsearch |
| NM-C-06 Service 拆分 | VERIFIED | 交接时段、领取意向、交接状态、留言、个人交换拆为5个领域服务，`ExchangeService` 保留兼容门面；架构测试和业务回归通过 |
| NM-C-07 Web/DTO/Mapper | VERIFIED | 请求模型集中到 `web.dto`，响应投影不暴露数据库实体；Controller 只做绑定、主体提取、命令转换和调用编排，Mapper 不承载状态规则 |
| NM-C-08 请求竞态 | VERIFIED | `latestRequest.js` 通过 AbortController 与序号只接纳最新结果；市场、详情、我的、看板和 AI 估价接入；3项异步测试通过 |
| NM-C-09 操作防重 | VERIFIED | `operationGuard.js` 按业务资源 key 防止同操作双击，不同资源可并行，失败后可重试；3项异步测试通过 |
| NM-C-10 阶段验收 | VERIFIED | 后端96项、前端13项全部通过且零跳过；后端打包、前端生产构建和 `git diff --check` 通过 |

## 3. SQL 与查询数量证据

### 3.1 固定查询上界

| 业务调用 | 优化前 | 优化后 |
| --- | ---: | ---: |
| 市场列表 N 条 | `2 + N` | `2` |
| 我的发布 N 条 | `3 + N` | `3` |
| 物品意向 N 条 | `2 + 3N` | `3` |
| 单个我的意向 | `5` | `2` |
| 我的交接 H 条 | `2 + 3H` | `2` |
| 交接详情 | `5` | `2` |
| 物品留言 C 条 | `2 + C` | `2` |
| 我的看板 I/H 条 | `4 + 3I + 3H` | `3` |

查询数由测试用 MyBatis `StatementHandler.prepare` 拦截器统计实际 SQL，不是仅按代码推算。列表字段、权限、历史 inactive 时段和稳定排序均有集成断言。

### 3.2 关键计划决策

- “我的交接”在固定数据集上的同轮对比从约7.709ms降至2.952ms；关键收益是先把交接筛到60行，再关联展示数据，避免用户表驱动产生10,000行中间组合。
- “最想要 Top 5”从约23.036ms降至16.443ms；物品主键查找由9,000次降至3,000次。验收复跑受本机 Docker 抖动影响，绝对耗时变化，但执行计划仍保持先聚合、再按3,000个 item 关联的结构。
- `UNION ALL` 交接候选在基线中更慢，拒绝采用；以 `item.completed_at` 替代 `handover.completed_at` 虽更快但历史语义不等价，拒绝采用。
- 当前索引已经支持采纳方案。没有足够证据证明新增覆盖索引能抵消写放大，因此 Phase C 没有为“展示优化”增加 Flyway 迁移。

### 3.3 搜索结论

30,000条 AVAILABLE 数据中，高命中首页可提前满足 LIMIT；单命中和零命中会扫描30,000行，验收复跑中约43～46ms。临时 FULLTEXT 对完整 token 只读取390条候选，但不能保持任意词中子串语义。

当前继续使用 LIKE。接近100,000条可搜索物品、搜索 p95 超过200ms、数据库 CPU 明显升高，或产品接受分词/前缀语义时，优先以真实中文语料评估 MySQL ngram FULLTEXT；当前没有引入 Elasticsearch 的依据。

## 4. 最终执行验证

| 命令 | 结果 |
| --- | --- |
| `set DOCKER_API_VERSION=1.44&& mvn test` | BUILD SUCCESS；96 passed，0 failures，0 errors，0 skipped；实际启动 MySQL 8 容器并执行 Flyway V1→V7、并发、读取路径和性能测试 |
| `mvn -DskipTests package` | BUILD SUCCESS；生成可执行 Spring Boot JAR |
| `npm.cmd test` | 13 passed，0 failed，0 skipped |
| `npm.cmd run build` | Vite BUILD SUCCESS；93 modules transformed |
| `git diff --check` | 退出码0；只有 Windows LF→CRLF 提示，无空白错误 |

第一次后端测试命令因 Windows `cmd` 对显式本地 Maven 仓库参数的引号解析失败，在 Maven 启动前退出；随后使用上表简化命令完整重跑成功。该命令错误没有访问数据库，也不计为项目测试失败。

## 5. API、数据库与兼容性审查

- Phase C 未新增、删除或改名 HTTP 路径，现有 JSON 字段保持兼容。
- Phase C 未新增生产 Flyway 迁移；V6/V7 是 Phase A/B 已验收的历史保留与一致性约束迁移。
- Service 拆分保留 `ExchangeService` 公开门面和响应记录，事务注解位于实际写工作流服务；条件 UPDATE、幂等与数据库约束仍是最终一致性防线。
- 前端操作防重只改善体验，不替代服务端幂等；每次成功后重新读取服务端状态。
- Git 工作树包含此前各阶段未提交修改，本阶段未覆盖或清理用户改动，也未执行 commit、push、reset 或 clean。

## 6. 残余风险与后续任务

| 风险/缺口 | 级别 | 后续任务 |
| --- | --- | --- |
| Testcontainers 仅在本机验证，尚无 CI 持续执行 | P1 | NM-D-05 |
| 前端13项为 Node 单元测试，尚无 Vue 组件浏览器环境和全栈 E2E | P1 | NM-D-09、NM-D-10 |
| 当前没有可复现的一键 Compose 部署和部署 smoke | P1 | NM-D-01～03、NM-D-11 |
| 搜索在更大数据量下仍是线性扫描 | P2 | 按 NM-C-05 阈值复评，不提前引入缓存或搜索引擎 |
| 本机性能耗时受 Docker Desktop 调度影响，不能作为生产 SLA | P2 | NM-G-05 保留固定数据与计划证据，后续在统一环境复测 |
| 浏览器级认证、CSRF 和核心流程仍缺 E2E 闭环 | P1 | NM-A-09、NM-D-10 |

## 7. Phase C 完成判定

NM-C-01 ～ NM-C-10 均具备代码、自动化测试或可复现实验依据。主要 N+1 已消除，关键 SQL 优化有结果集和真实执行计划证据，无收益或语义不等价方案被明确拒绝；后端边界和前端请求一致性得到强化。最终全量回归和双端构建均通过，因此 Phase C 正式完成。
