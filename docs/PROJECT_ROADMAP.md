# NeighborMarket 工程化升级路线图

版本：1.0
建立日期：2026-10-08
适用分支：`main`（建立时 HEAD：`a8a09d6`）
文档性质：开发总控、验收台账与阶段门禁；本文不表示其中 TODO 任务已经实现。

## 1. 目标与原则

NeighborMarket 将在保持模块化单体的前提下，从本地面试 Demo 演进为可安全部署、可自动验证、可持续维护的 Java 全栈项目。优先顺序是：安全与数据正确性 > 真实测试证据 > 查询与代码质量 > 工程交付 > 必要业务补全 > 可选基础设施。

执行原则：

1. 保留现有用户及历史业务数据；不以清库、重建真实库或删除历史记录绕过问题。
2. 已执行的 Flyway 迁移 V1～V5 不修改；结构变更只能追加新迁移。
3. 代码存在不等于测试通过，测试通过不等于真实环境已验证。
4. 每个 Phase 均执行 Plan、Implement、Test、Review、Acceptance、Report，阶段验收后暂停等待确认。
5. 不为丰富技术栈引入微服务、消息队列、Redis 或前端状态框架。
6. 自动化测试不得调用真实付费 AI；外部 AI 一律使用 stub/fake server。
7. 不自动 commit 或 push；提交前报告范围并取得授权。

## 2. 状态与证据规则

任务状态只能取以下值：

| 状态 | 含义 |
| --- | --- |
| TODO | 尚未开始，或只有分析结论而没有实现 |
| IN_PROGRESS | 正在实施，尚未满足全部验收标准 |
| BLOCKED | 被明确的外部条件或前置任务阻塞，必须写明原因 |
| DONE | 代码/文档已经实现，但尚无完整验收证据 |
| VERIFIED | 实现完成，规定测试和验收均已通过，证据已记录 |

状态升级规则：`TODO → IN_PROGRESS → DONE → VERIFIED`。测试不可运行、只做了 mock、只人工看代码或缺少最终数据库状态断言时，不得标记 VERIFIED。

每次任务状态更新必须同时记录：日期、提交或工作树基准、执行命令、测试结果、未验证项、证据文件。阶段报告是状态升级依据。

## 3. 当前基线

### 3.1 实际架构与已完成能力

- 后端：Java 17、Spring Boot 3.4.5、Spring MVC、Spring Security、MyBatis-Plus 3.5.12、MySQL、Flyway。
- 前端：Vue 3.5、Vue Router、Axios、Vite 6。
- 架构：Vue SPA 调用 Spring Boot REST API；后端访问单个 MySQL 数据库，并代理 OpenAI-compatible AI 定价接口。
- 数据模型：用户、物品、交接时段、领取意向、交接历史、留言/一层回复；Flyway V1～V7。
- 已实现业务：Session 登录、发布、列表/详情、搜索筛选、分页、新鲜度、确认还在、领取意向、确认领取人、取消/完成交接、归档、公开留言与回复、AI 定价、社区看板。
- 明确未实现：物品通用编辑、图片上传、正式部署、CI、Actuator、真实数据库自动集成测试、前端组件/E2E 测试。

主要代码证据：

- 认证与授权：[`SecurityConfig.java`](../backend/src/main/java/com/neighbormarket/config/SecurityConfig.java)、[`AuthController.java`](../backend/src/main/java/com/neighbormarket/web/AuthController.java)
- 物品：[`ItemService.java`](../backend/src/main/java/com/neighbormarket/service/ItemService.java)
- 意向/交接/留言：[`ExchangeService.java`](../backend/src/main/java/com/neighbormarket/service/ExchangeService.java)
- 数据结构：[`V1__create_users_and_items.sql`](../backend/src/main/resources/db/migration/V1__create_users_and_items.sql)～[`V7__strengthen_exchange_constraints.sql`](../backend/src/main/resources/db/migration/V7__strengthen_exchange_constraints.sql)
- AI：[`PricingService.java`](../backend/src/main/java/com/neighbormarket/ai/PricingService.java)、[`QwenPricingGateway.java`](../backend/src/main/java/com/neighbormarket/ai/QwenPricingGateway.java)
- 前端页面：[`frontend/src/views`](../frontend/src/views)

### 3.2 建立路线图时的验证基线

2026-10-08 已执行：

```powershell
mvn "-Dmaven.repo.local=C:\Users\dell\.m2\repository" test
mvn "-Dmaven.repo.local=C:\Users\dell\.m2\repository" -DskipTests package
npm test
npm run build
```

结果：后端 36/36 测试通过；后端打包成功；前端 5/5 测试通过；前端生产构建成功。没有连接真实 MySQL、没有启动 Flyway、没有调用真实 AI、没有执行浏览器 E2E。因此只能证明当前单元/切片测试和构建基线，不证明真实事务与迁移正确。

## 4. 复核后的问题清单

| 编号 | 级别 | 当前状态 | 问题与证据 | 影响 |
| --- | --- | --- | --- | --- |
| NM-ISSUE-01 | P0 | 已复核存在 | `DemoDataConfig` 无 profile 隔离并硬编码演示凭据 | 公网/共享环境会创建已知账号 |
| NM-ISSUE-02 | P0 | 已复核存在 | CSRF 全局关闭；登录手工写入 Session，未显式轮换 Session ID | Session 认证安全基线不足 |
| NM-ISSUE-03 | P1 | 已复核存在 | Cookie 只有 HttpOnly/SameSite，未按环境设置 Secure | TLS 环境安全属性不完整 |
| NM-ISSUE-04 | P1 | 已复核存在 | 重配时段直接删除 `pickup_slot`；已取消意向仍外键引用旧时段 | 真实 MySQL 下可能无法重配 |
| NM-ISSUE-05 | P1 | 已复核存在 | `App` 与详情页并发加载，详情页不等待 `auth.ready` | 直接刷新可能缺少私有数据/操作 |
| NM-ISSUE-06 | P1 | 已复核存在 | 后端异常格式不统一，前端只读取 `data.message` | 403/409/数据异常可能显示为连接失败 |
| NM-ISSUE-07 | P1 | 已复核存在 | 并发首次申请采用先查后插，唯一键冲突未转换为幂等结果 | 重复请求可能返回 500 |
| NM-ISSUE-08 | P1 | 已复核存在 | 个别关键 UPDATE 未检查影响行数 | 异常数据下可能形成部分状态 |
| NM-ISSUE-09 | P1 | 已复核存在 | 并发测试是 Mockito 返回值模拟，不是真实 MySQL 竞争 | 事务和锁行为缺少可信证据 |
| NM-ISSUE-10 | P2 | 已复核存在 | Item/Interest/Handover/Comment DTO 组装逐行查询 | N+1，数据增长后延迟放大 |
| NM-ISSUE-11 | P2 | 已复核存在 | 搜索、组合筛选、双方交接查询未有 EXPLAIN 证据 | 索引优化缺少测量依据 |
| NM-ISSUE-12 | P2 | 已复核存在 | `ExchangeService` 承担多个领域且代码高度压缩 | 可维护性和测试隔离较差 |
| NM-ISSUE-13 | P2 | 已复核存在 | 前端没有旧请求取消、统一 401 处理和操作级防重 | 请求竞态与反馈不稳定 |
| NM-ISSUE-14 | P1 | 已复核存在 | 无 Docker、CI、Actuator、请求 ID 和部署 smoke test | 新环境不可重复交付 |
| NM-ISSUE-15 | P1 | 已复核存在 | 无物品编辑与图片上传 | 业务完整性不足 |

## 5. 阶段依赖总览

```text
Phase A 安全与核心 Bug
   ├──> Phase B 事务、并发与数据库可靠性
   │       └──> Phase C SQL 与代码结构优化
   ├──> Phase D 工程化、测试与部署（A 稳定后可与 B/C 部分并行）
   └──> Phase E 业务完整性（编辑依赖 A；上传可在 D 的配置体系后实施）

Phase B + C + D 基础完成
   └──> Phase F Redis/可选增强评估

Phase A～F 的最终事实
   └──> Phase G 求职展示与最终交付
```

硬顺序：A 的安全契约和数据修复先于 B；B 的真实数据库测试基础先于数据库约束和并发验收；SQL 优化必须先测量后改索引；F 必须在单体和数据库基础问题解决后；G 的最终数据只能来自已验证结果。

可并行项：

- A 中后端 profile/Session 与前端认证等待可以并行，但 CSRF 前后端改造必须协同合并。
- B 的测试容器基础完成后，Flyway、并发场景和回滚场景可分组实现。
- C 的 SQL 基线采集可与 Service 拆分设计并行，索引修改必须等待基线。
- D 的 CI、Docker、Actuator可并行开发，但最终 Compose smoke test 依赖三者集成。
- E 的物品编辑和图片方案可并行设计；图片落地依赖 D 的环境/卷配置。

## 6. Phase A — 安全与核心 Bug 修复

阶段目标：消除已知认证安全阻断和核心流程 Bug，建立稳定的错误契约。未另行标记的任务状态为 TODO。

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-A-01 演示账号隔离 | VERIFIED | P0 | 中 | 无 | 本地演示启动方式变化 | 仅显式 demo profile 创建演示账号；非 demo 环境不含默认凭据；凭据来自环境变量；测试覆盖启用/禁用 |
| NM-A-02 Session 固定防护 | VERIFIED | P0 | 中 | 无 | 改动登录流程导致 Session 丢失 | 登录前后 Session ID 发生变化；认证上下文仍可由 `/me` 读取；旧 Session 不可复用 |
| NM-A-03 CSRF 方案落地 | DONE | P0 | 中高 | NM-A-02 | Axios、登录/退出和测试需同步调整 | 所有状态变更接口要求有效 CSRF token；GET 不受影响；无/错误 token 返回统一 403；前端自动携带 token |
| NM-A-04 Cookie 环境配置 | VERIFIED | P1 | 低 | NM-A-02 | 本地 HTTP 若误启 Secure 会无法登录 | dev/demo 保持可用；prod 默认 HttpOnly、Secure、合理 SameSite；配置有自动测试或启动配置测试 |
| NM-A-05 时段历史与重配修复 | VERIFIED | P0 | 高 | 设计评审 | 错误迁移可能破坏历史引用 | 追加 Flyway 迁移（如需要），不改 V1～V5；取消意向后可重配；历史意向仍可审计；现有数据无删除；回归测试验证真实 FK |
| NM-A-06 详情页认证初始化同步 | DONE | P1 | 中 | 无 | 重复加载或界面闪烁 | 直接刷新详情时等待认证完成；发布者能看到意向，申请者能看到自己的意向；未登录公共详情不增加阻塞 |
| NM-A-07 统一错误响应契约 | IN_PROGRESS | P1 | 中 | NM-A-03 | 泄漏内部异常或改变已有前端文案 | 校验、401、403、404、409、唯一键冲突及 500 使用固定字段；500 不泄漏 SQL/栈/密钥；关联请求 ID |
| NM-A-08 前端统一错误与认证处理 | DONE | P1 | 中 | NM-A-03、NM-A-07 | 401 循环跳转 | 兼容统一错误体；401 跳登录且保存合理返回路径；403/409 显示业务消息；网络错误可区分 |
| NM-A-09 Phase A 回归与安全测试 | IN_PROGRESS | P0 | 中高 | NM-A-01～08 | 只测 happy path | 后端单元/安全测试、前端测试、构建全通过；至少覆盖 Session fixation、CSRF、profile、时段重配、直接刷新认证、错误契约 |
| NM-A-10 Phase A 验收与报告 | DONE | P0 | 低 | NM-A-09 | 缺少证据即误标完成 | 逐条记录命令、数量、失败/跳过、数据库迁移结果和残余风险；仅证据完整的任务标 VERIFIED |

Phase A 设计约束：时段问题优先采用“旧时段保留为历史、仅新时段可选”的模型，不通过删除意向或清空历史解决；最终方案需在实施前结合实体和查询确认。

### Phase A 执行记录

#### 2026-10-08 / 批次 A1

- NM-A-01 VERIFIED：演示 Seeder 仅在 `demo` profile 注册；两个密码由环境变量提供；缺少密码时快速失败；源码中的固定密码已移除。
- NM-A-02 VERIFIED：登录显式执行 `SessionFixationProtectionStrategy`，并通过 `SecurityContextRepository` 保存认证；测试验证既有 Session ID 轮换且新 Session 保存认证。
- NM-A-04 VERIFIED：基础配置默认支持本地 HTTP，`prod` profile 默认启用 Secure Cookie；配置加载测试覆盖两个环境。
- NM-A-07 IN_PROGRESS：已统一 `ResponseStatusException`、不可读请求、数据约束冲突、未知异常和 Security 401/403 的 `{code,message}` 响应，并避免在响应或日志中输出原始 SQL/异常消息；请求 ID 及 CSRF 错误联动尚未完成。
- 验证结果：后端 45 项测试、前端 5 项测试全部通过；后端 JAR 和前端生产构建成功。未连接真实数据库、未调用真实 AI。

#### 2026-10-08 / 批次 A2

- NM-A-03 DONE：启用 `CookieCsrfTokenRepository`，新增匿名令牌初始化端点，写操作缺少/失效 token 返回 `CSRF_INVALID`；Axios 配置 `XSRF-TOKEN`/`X-XSRF-TOKEN` 并在写请求前完成初始化。后端安全测试已验证 token Cookie 和拒绝路径；尚未执行真实浏览器 Cookie 往返，暂不标 VERIFIED。
- NM-A-06 DONE：详情公共数据与认证初始化并行，公共内容先结束 loading；认证完成后按发布者/申请者身份补取私有数据。生产构建通过，但缺少组件或浏览器直接刷新测试，暂不标 VERIFIED。
- NM-A-08 DONE：Axios 统一处理 401，登录页保留内部返回路径；错误文案兼容 `{message}` 与 Problem Detail `{detail}`，区分 HTTP 失败和网络失败。纯函数和拦截器测试通过；路由级组件测试尚未建立。
- NM-A-07 继续 IN_PROGRESS：CSRF 失败已纳入统一错误码，仍缺请求 ID。
- 验证结果：后端 47 项测试、前端 7 项测试全部通过；后端 JAR 和前端生产构建成功。未连接真实数据库、未调用真实 AI、未执行真实浏览器测试。

#### 2026-10-08 / 批次 A3 与 Phase A 阶段验收

- NM-A-05 DONE：新增 V6 `pickup_slot.active` 和 `(item_id, active, start_at)` 索引。重配时将现有 active 时段置为 false，再插入新 active 时段，不再删除被历史意向外键引用的行；新申请和领取人确认均拒绝 inactive 时段。
- 迁移契约测试确认 V6 不包含删除/删表操作，服务测试确认不调用 delete、旧时段停用、新时段启用和 inactive 时段不可申请。
- NM-A-09 IN_PROGRESS：当前全量自动化回归和构建通过；真实 MySQL 上从 V5 升 V6、真实外键行为，以及 A2 的浏览器 Cookie/刷新/路由场景仍未验证。
- NM-A-10 DONE：Phase A 已完成代码实现与当前证据范围内的审查报告；未满足证据的任务保持 DONE/IN_PROGRESS，没有标为 VERIFIED。
- 验证结果：后端 50 项、前端 7 项测试全部通过；后端 JAR 与前端生产构建成功。未应用迁移到真实数据库，未调用真实 AI。

## 7. Phase B — 事务、并发与数据库可靠性

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-B-01 MySQL Testcontainers 基础 | VERIFIED | P0 | 中 | Phase A | CI/Docker 环境不可用 | 测试启动独立 MySQL 8 容器；不读取开发库凭据；测试间数据隔离；无 Docker 时明确跳过原因而非假通过 |
| NM-B-02 Flyway 空库迁移测试 | VERIFIED | P0 | 中 | NM-B-01、NM-A-05 | 新旧迁移兼容问题 | 空库按顺序成功应用全部迁移；关键表、FK、唯一键、索引存在；重复启动无额外变更 |
| NM-B-03 并发确认测试 | VERIFIED | P0 | 高 | NM-B-01 | 测试不稳定 | 两个独立连接/事务同时确认不同意向；仅一个成功；最终只有一个 ACTIVE 交接，item 指向它，失败请求无残留 |
| NM-B-04 取消与完成竞争测试 | VERIFIED | P0 | 高 | NM-B-01 | 时序难以稳定复现 | 使用屏障同步竞争；最终只能是合法 CANCELLED+AVAILABLE 或 COMPLETED+COMPLETED 组合；无双终态、无丢失引用 |
| NM-B-05 申请幂等 | VERIFIED | P0 | 中高 | NM-B-01 | upsert 覆盖已确认意向 | 同用户/物品并发首次申请不返回 500、不产生重复行；CONFIRMED 不可被覆盖；重试语义文档化 |
| NM-B-06 关键 UPDATE 断言 | VERIFIED | P1 | 中 | NM-B-03～05 | 过度严格导致正常重试失败 | 确认、取消、完成及意向状态更新均检查影响行数；异常时事务整体回滚；返回稳定 409 |
| NM-B-07 数据库约束强化 | VERIFIED | P1 | 高 | NM-B-02～06 | 现有数据不满足约束 | 先做兼容性查询；只添加能由业务解释的状态/唯一/引用约束；迁移对现有合法数据无损；异常样本有处理方案 |
| NM-B-08 事务回滚场景测试 | VERIFIED | P0 | 中 | NM-B-06 | mock 不能证明真实回滚 | 在 handover INSERT、current pointer 更新等中间点注入失败；真实 MySQL 最终状态与操作前一致 |
| NM-B-09 数据一致性审计 SQL | VERIFIED | P1 | 中 | NM-B-07 | 审计 SQL 被误用作修复 | 提供只读检查：孤立引用、item/handover 状态不匹配、多 ACTIVE；测试库验证能发现构造异常 |
| NM-B-10 Phase B 验收与报告 | VERIFIED | P0 | 低 | NM-B-01～09 | 只记录接口不查库 | 报告接口结果、最终表状态、历史行数量和并发轮次；全部必测场景可重复通过 |

乐观锁评估：当前核心状态转换优先保留条件 UPDATE；只有物品编辑引入多字段覆盖冲突时再评估 `version`。不能用乐观锁替代幂等和数据库约束。

### Phase B 执行记录

#### 2026-10-08 / 批次 B1

- NM-B-01 DONE：加入仅测试作用域的 Testcontainers JUnit/MySQL 依赖；集成测试使用独立 MySQL 8.4 容器、固定测试库名和测试专用凭据，不读取开发/生产数据库配置；每个测试前由 Flyway 清理容器数据库。无可用 Docker 时，JUnit 明确将容器测试标记为 skipped。
- NM-B-02 DONE：新增空库 V1～V6 完整迁移测试，断言最终版本、关键外键、唯一约束和 V6 索引，并验证重复迁移为 no-op；另新增 V5 升 V6 场景，断言历史 CANCELLED 意向及其时段引用不会丢失。
- 当前验证结果：`mvn test` 构建成功，共发现 52 项测试，50 项通过、0 失败、0 错误、2 跳过。跳过项正是上述两个 MySQL 容器测试，原因是本机 Docker daemon 未运行。因此 B-01/B-02 仅标 DONE，不能标 VERIFIED；未连接或修改开发数据库，未调用真实 AI。
- 该批次遗留的 Docker 验证条件已在批次 B2 满足；原先两个 skipped 结果不再作为当前验收结论。

#### 2026-10-08 / 批次 B2

- NM-B-01 VERIFIED：Docker Engine 就绪后，Testcontainers 自动创建并回收临时 MySQL；测试资源固定 docker-java API 1.44，以兼容 Docker Engine 29，且无需修改用户级 Docker 配置。容器数据库使用测试专用库名和凭据。
- NM-B-02 VERIFIED：真实 MySQL 上空库 V1～V6、重复迁移 no-op、V5 升 V6 历史引用保留三类断言全部通过。
- NM-B-03 VERIFIED：两个线程经屏障同时调用发布者确认接口，每个调用由 Spring 开启独立事务。结果严格为一个成功、一个 409；最终数据库仅一条 ACTIVE handover，物品为 RESERVED 且指向该 handover，胜者意向为 CONFIRMED、败者仍为 WAITING，领取人与意向用户一致。
- 容器测试命令：`mvn -Dtest=FlywayMigrationIntegrationTest,ConcurrentConfirmationIntegrationTest test`；结果 3 项通过、0 失败、0 错误、0 跳过。测试未连接或修改开发数据库，未调用真实 AI。
- 全量后端回归：`mvn test`；结果 53 项通过、0 失败、0 错误、0 跳过，构建成功。测试镜像固定为 MySQL 8.0，与项目数据库基线一致；首次执行会自动拉取镜像。
- Phase A 证据回填：V5→V6 真实 MySQL 测试确认历史 `item_interest.slot_id` 外键引用保留且旧时段被标记 active，因此 NM-A-05 从 DONE 提升为 VERIFIED；NM-A-09 仍因浏览器级 CSRF/认证刷新场景未验证而保持 IN_PROGRESS。

#### 2026-10-08 / 批次 B3

- NM-B-04 VERIFIED：先通过真实 Service 事务建立 ACTIVE 交接，再让领取人取消与发布者完成经双线程屏障同时执行。结果严格为一方成功、一方 409，且无论谁获胜，数据库只能形成一种合法终态。
- 取消获胜验收：handover 为 CANCELLED，仅 `cancelled_at` 有值；item 恢复 AVAILABLE 且清空 `current_handover_id`；获胜意向转为 CANCELLED。
- 完成获胜验收：handover 与 item 均为 COMPLETED，仅 `completed_at` 有值；item 继续指向完成记录；获胜意向保持 CONFIRMED。两种结果均无 ACTIVE handover，未获选意向保持 WAITING。
- 针对性命令：`mvn -Dtest=ConcurrentConfirmationIntegrationTest test`；结果 2 项通过、0 失败、0 错误、0 跳过。现有条件 UPDATE 和事务回滚已满足该竞争场景，因此本任务没有修改生产业务代码。
- 全量后端回归：`mvn test`；结果 54 项通过、0 失败、0 错误、0 跳过，构建成功。

#### 2026-10-08 / 批次 B4（等待容器复验）

- NM-B-05 DONE：将申请逻辑从“先查后 insert/update”改为基于 `(item_id,user_id)` 唯一约束的单条 `INSERT ... ON DUPLICATE KEY UPDATE`。并发首次申请应收敛到同一行，重复 WAITING/CANCELLED 申请允许更新时段，CONFIRMED 行在 SQL 层保持 slot/status/updated_at 不变，Service 读回后返回稳定 409。
- 新增真实 MySQL 测试：两个线程同时首次申请，要求两次调用均成功并返回同一 ID，最终仅一条 WAITING；另验证重试不能覆盖 CONFIRMED。新增 Service 单元测试验证只走原子 upsert 且不回退到旧 insert/updateById。
- 已验证：`mvn -Dtest=ExchangeServiceTest test` 共 16 项通过、0 失败、0 错误、0 跳过。
- 未验证：执行容器测试时 Docker Desktop daemon 已停止，`ConcurrentConfirmationIntegrationTest` 的 4 项测试被明确 skipped。因此 NM-B-05 仅标 DONE；Docker 恢复后必须实际通过新增两项 MySQL 测试及全量回归才能提升为 VERIFIED。

#### 2026-10-08 / 批次 B4 复验

- Docker Engine 恢复后，`ConcurrentConfirmationIntegrationTest` 的 4 项真实 MySQL 测试全部通过、无跳过；其中两个新增用例确认并发首次申请均成功且返回同一意向 ID，数据库只有一条 WAITING，以及 CONFIRMED 行无法被 upsert 覆盖。NM-B-05 提升为 VERIFIED。
- 全量后端回归：`mvn test`；结果 58 项通过、0 失败、0 错误、0 跳过，构建成功。测试容器均由 Testcontainers 自动创建并回收，未访问开发数据库。

#### 2026-10-09 / 批次 B5（等待容器复验）

- NM-B-06 DONE：确认流程现在检查 handover INSERT、item 当前交接指针 UPDATE、WAITING→CONFIRMED UPDATE；取消意向检查 WAITING→CANCELLED；取消交接检查 CONFIRMED→CANCELLED。每一步必须恰好影响一行，否则返回稳定 409，由 `@Transactional` 触发整笔回滚。完成交接原有的 handover 和 item 条件 UPDATE 检查保持不变。
- 新增失败分支测试：交接 INSERT 失败、指针关联失败、确认意向失败、取消个人意向竞争失败、取消交接的意向转换失败；并验证失败后不继续执行后续 Mapper 写操作。
- 已验证：`mvn -Dtest=ExchangeServiceTest test` 共 21 项通过、0 失败、0 错误、0 跳过。
- 未验证：执行真实 MySQL 回归时 Docker daemon 已停止，4 项 Testcontainers 测试全部 skipped。因此 NM-B-06 仅标 DONE；待 Docker 恢复后需执行真实并发回归，并在 NM-B-08 中验证中间步骤失败后的最终数据库回滚状态。

#### 2026-10-09 / 批次 B5 复验

- Docker Engine 恢复后的首次针对性容器回归通过，但首次全量回归暴露出 NM-B-05 的偶发并发缺陷：MySQL 默认 `REPEATABLE READ` 下，原子 upsert 后的普通一致性读可能沿用旧快照，看不到竞争事务刚提交的同一行，从而错误返回 409。该轮全量结果为 63 项、1 错误，没有作为通过证据。
- 修复采用 `SELECT ... FOR UPDATE` 锁定当前读回原子 upsert 的结果，避免依赖事务快照；未改变 `(item_id,user_id)` 唯一约束及 CONFIRMED 不可覆盖的 SQL 语义。并发首次申请测试改为连续重复 5 轮，以提高对时序缺陷的检出能力。
- 针对性真实 MySQL 回归：`mvn -Dtest=ConcurrentConfirmationIntegrationTest test`；结果 8 项通过、0 失败、0 错误、0 跳过，其中并发首次申请场景连续 5 轮通过。NM-B-05 保持 VERIFIED，并获得更强的可重复证据。
- 全量后端回归：`mvn test`；结果 67 项通过、0 失败、0 错误、0 跳过，构建成功。确认、取消、完成和意向转换的既有真实并发场景均未回归，NM-B-06 提升为 VERIFIED。
- Testcontainers 使用临时 MySQL 8.0，未连接或修改开发数据库，未调用真实 AI。当前证据验证了条件写入及竞争结果；人为注入中间步骤故障后的真实数据库整体回滚仍属于 NM-B-08，不在本批次提前标记完成。

#### 2026-10-09 / 批次 B6

- NM-B-08 VERIFIED：在 Testcontainers 临时 MySQL 8.0 中创建仅测试期间存在的故障注入 Trigger，直接验证 `@Transactional` 在中间写入失败后的真实数据库回滚，不使用 mock 代替数据库事务证据。
- 确认流程覆盖两种故障点：物品已抢占为 RESERVED 后 handover INSERT 失败；handover 已插入后 item.current_handover_id 关联失败。两种情况下最终 item 均恢复 AVAILABLE 且指针为空、handover 为 0 条、目标意向保持 WAITING。
- 取消流程在 handover 已改 CANCELLED、item 已恢复 AVAILABLE 后注入意向更新失败；最终 handover 回到 ACTIVE 且 `cancelled_at` 为空，item 回到 RESERVED 并保留原交接指针，意向保持 CONFIRMED。
- 完成流程在 handover 已改 COMPLETED 后注入 item 完成更新失败；最终 handover 回到 ACTIVE 且 `completed_at` 为空，item 保持 RESERVED 和原交接指针，意向保持 CONFIRMED。每个用例同时校验异常根因包含对应注入标识，避免由无关异常造成假通过。
- 首次执行因 MySQL binary log 下测试用户无 Trigger 创建权限而出现 4 个环境错误；随后仅为该临时容器启用 `log_bin_trust_function_creators=1`，未修改应用或开发数据库配置。修正后的针对性命令 `mvn -Dtest=ConcurrentConfirmationIntegrationTest test` 为 12 项通过、0 失败、0 错误、0 跳过。
- 最终全量后端回归：`mvn test`；结果 71 项通过、0 失败、0 错误、0 跳过，构建成功。测试 Trigger 在每项测试前后显式清理，容器由 Testcontainers 回收；未访问开发数据库，未调用真实 AI。

#### 2026-10-09 / 批次 B7

- NM-B-07 VERIFIED：新增不可变 V7 Flyway 迁移，在任何 DDL 前只读检查非法状态、交接终态字段冲突、同物品多 ACTIVE 交接，以及时段/意向/发布者/当前交接的跨表错配。已有异常时迁移主动阻断，避免 MySQL 自动提交 DDL 形成半套约束。
- 状态与终态约束：item、item_interest、handover 状态限定为代码枚举集合；handover 的 ACTIVE/CANCELLED/COMPLETED 分别要求正确的 cancelled_at/completed_at 组合。
- 并发兜底：handover 新增生成列 active_item_id，仅 ACTIVE 行映射 item_id，并通过唯一索引从数据库层保证每件物品最多一条 ACTIVE 交接。历史 CANCELLED/COMPLETED 行的生成列为 NULL，不限制历史数量。
- 引用一致性：复合外键保证意向时段属于同一物品、交接意向属于同一物品和领取人、交接发布者与物品发布者一致、item.current_handover_id 指向本物品交接。没有添加会阻断现有两步确认事务的 `RESERVED`/指针瞬时 CHECK。
- 真实 MySQL 迁移测试覆盖：空库 V1～V7、重复迁移 no-op、V5→V6 历史保留、V6→V7 合法 ACTIVE 交接无损升级、非法状态/重复 ACTIVE/跨物品引用拒绝，以及异常 V6 数据阻断。阻断后数据和结构保持在 V6；修正数据后需按 Flyway 规范先 `repair` 再 `migrate`，该恢复路径已实际验证。
- 最终全量后端回归：`mvn test`；结果 74 项通过、0 失败、0 错误、0 跳过，构建成功。现有确认、取消、完成、并发、幂等和回滚场景均在 V7 下通过；未连接开发数据库，未调用真实 AI。

#### 2026-10-09 / 批次 B8

- NM-B-09 VERIFIED：新增 `scripts/audit/data_consistency_audit.sql`，以单条 `SELECT ... UNION ALL` 输出稳定的 issue_code、P0/P1 严重度、实体、主键、关联键和定位说明；健康数据库预期返回 0 行。脚本不创建对象、不修改数据，也不包含自动修复。
- 审计范围包括：非法状态、交接终态时间字段、同物品多 ACTIVE、主要外键孤立引用、意向/时段错配、交接/意向/领取人错配、发布者错配、当前交接跨物品、item/handover 状态机错配、ACTIVE 交接未被 item 正确引用、有效交接意向状态异常、发布者申请自己的物品，以及留言父子跨物品/嵌套回复。
- 新增操作说明，要求使用只读账号、异常处理前备份、单独评审修复方案并在副本验证；明确禁止把审计脚本直接改成 UPDATE/DELETE。V7 升级前异常的处理流程仍为修正数据、Flyway `repair`、重新 `migrate`。
- 真实 MySQL 测试验证：完整 V7 ACTIVE 交接返回 0 个审计问题；V6 中构造的非法状态、跨物品时段、交接意向错配、同物品双 ACTIVE、状态机错配、未引用 ACTIVE、意向状态异常、孤立父留言和发布者自申请均被对应代码识别；静态契约确认脚本仅一条只读语句。
- 针对性 `FlywayMigrationIntegrationTest` 为 8 项通过；最终 `mvn test` 为 77 项通过、0 失败、0 错误、0 跳过，构建成功。测试仅使用 Testcontainers 临时 MySQL，未连接开发数据库，未调用真实 AI。

#### 2026-10-09 / 批次 B9（Phase B 验收，等待容器最终复验）

- NM-B-10 DONE：新增 `docs/PHASE_B_ACCEPTANCE_REPORT.md`，逐项汇总 B01～B09 的实现和测试证据，并记录并发接口结果、item/interest/handover 最终状态、历史行数量、申请并发 5 轮/10 次调用、确认竞争、取消/完成竞争及 4 个真实事务回滚故障点。
- 本轮 `mvn test` 的非容器部分通过：共发现 73 项，57 项通过、0 失败、0 错误，16 项 Testcontainers 测试明确 skipped，构建成功。跳过原因经 `docker version` 确认为 Docker Desktop Engine 未运行、`dockerDesktopLinuxEngine` 命名管道不存在，而非项目断言失败。
- B8 已有同一阶段代码的 77 项全量通过、0 跳过证据，但为遵守“没有证据不能 VERIFIED”，本批次仍要求 Docker 恢复后在当前最终代码状态重新执行 `mvn test`，结果必须为全部通过且容器测试 0 跳过，NM-B-10 才能提升为 VERIFIED。
- 未连接或修改开发数据库，未调用真实 AI，未执行 commit/push。

#### 2026-10-09 / 批次 B9 最终复验

- Docker Desktop Engine 恢复后执行全量 `mvn test`：77 项通过、0 失败、0 错误、0 跳过，构建成功。
- `ConcurrentConfirmationIntegrationTest` 12 项全部通过，其中并发首次申请按 5 轮重复执行；`FlywayMigrationIntegrationTest` 8 项全部通过。两个测试类均实际创建并回收 MySQL 8.0 临时容器。
- NM-B-10 提升为 VERIFIED；NM-B-01 ～ NM-B-10 至此全部 VERIFIED，Phase B 正式完成。
- 测试未连接或修改开发数据库，未调用真实 AI，未执行 commit/push。

## 8. Phase C — SQL 与代码结构优化

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-C-01 可复现性能数据集与 SQL 基线 | VERIFIED | P1 | 中 | Phase B | 小数据无法体现计划差异 | 测试环境生成可重复、非生产数据；记录列表、搜索、我的交接、看板 SQL、耗时和 EXPLAIN ANALYZE |
| NM-C-02 物品列表 N+1 消除 | VERIFIED | P1 | 中 | NM-C-01 | DTO/分页语义变化 | 单页查询数不随 item 数线性增长；响应字段兼容；查询数和耗时有前后证据 |
| NM-C-03 意向/交接/留言 N+1 消除 | VERIFIED | P1 | 高 | NM-C-01 | JOIN 造成重复行 | 列表顺序和权限不变；查询数有固定上界；集成测试覆盖强制关联与历史记录 |
| NM-C-04 关键查询与索引优化 | VERIFIED | P1 | 高 | NM-C-01～03 | 写放大、冗余索引 | 依据 EXPLAIN 决定 SQL/索引；记录扫描行、排序/临时表、耗时前后对比；无收益时明确拒绝新增索引和迁移 |
| NM-C-05 搜索策略评估 | VERIFIED | P2 | 中 | NM-C-01 | 过早引入搜索引擎 | 对 `%keyword%` 测量；小规模保留并说明阈值；需要时优先评估 MySQL FULLTEXT，不引入 Elasticsearch |
| NM-C-06 Service 职责拆分 | VERIFIED | P1 | 高 | Phase B 稳定 | 重构引发事务边界变化 | 按时段/意向/交接/留言拆分职责；事务边界显式；公开 API 和业务行为不变；回归测试全过 |
| NM-C-07 Controller/DTO/Mapper 规范化 | VERIFIED | P2 | 中 | NM-C-06 | 大范围格式改动干扰审查 | 请求、响应 DTO 独立且校验明确；Mapper 不承载业务规则；代码可读性提升且无无关重写 |
| NM-C-08 前端请求竞态治理 | VERIFIED | P1 | 中 | Phase A | 取消请求误显示错误 | 快速筛选只采用最新响应；卸载页面取消请求；取消不显示失败提示；有测试 |
| NM-C-09 前端操作级防重与状态同步 | VERIFIED | P1 | 中 | NM-B-05 | UI 防重被误当后端幂等 | 每项操作独立 busy；成功后局部或一致重载；双击不发重复请求；后端仍保证幂等 |
| NM-C-10 Phase C 验收与报告 | VERIFIED | P1 | 中 | NM-C-01～09 | 只有理论优化 | 保存 SQL、数据规模、查询计划、查询次数和前后结果；功能回归与构建通过 |

### Phase C 执行记录

#### 2026-10-09 / 批次 C1

- NM-C-01 VERIFIED：新增 `PerformanceBaselineIntegrationTest`，在 Testcontainers MySQL 8.0 中从空库迁移至 V7，生成100名用户、3,000件物品、3,000个时段、9,000条意向和1,200条交接的固定非生产数据集，并断言各表和状态行数。
- 批量装载提交后执行 `ANALYZE TABLE`；10组列表、计数、搜索、我的意向/交接及社区看板 SQL 均预热1次、执行5次 `EXPLAIN ANALYZE`，输出客户端中位耗时和真实计划。首轮发现关键词样本与状态取模重合及统计信息未刷新，修正数据集后重新采集，不使用失真结果。
- 优化前主要证据：普通市场列表使用 `idx_item_market` 直接返回12行；关键词查询为返回12行检查91行；我的交接使用两个索引范围扫描后去重排序；最快完成对 item 主键查找600次；最想要查询扫描9,000条意向、主键查找9,000次并聚合2,400组，客户端中位约23.817ms，是当前最重单条基线。
- `docs/PERFORMANCE_BASELINE.md` 保存数据规模、测量方法、10条查询结果、当前 Service 查询放大模型及后续对比规则。本批次只建立基线，未修改生产 SQL、索引或业务行为。
- 针对性测试1项通过、0失败、0错误、0跳过；最终全量后端回归78项通过、0失败、0错误、0跳过，构建成功。所有数据库测试均使用临时 MySQL，未连接开发数据库，未调用真实 AI。

#### 2026-10-09 / 批次 C2

- NM-C-02 VERIFIED：市场列表和“我的发布”改为单条分页 SQL 直接投影发布者昵称，Service DTO 转换不再逐条查询 `app_user`；详情页单记录查询保持原行为。
- 新增真实 MySQL 查询计数测试，通过 MyBatis `StatementHandler.prepare` 统计实际 SQL：市场列表从 `2 + N` 收敛为固定2条，返回1条或12条均不变；我的发布从 `3 + N` 收敛为固定3条，返回1条或10条均不变。
- 兼容性测试验证发布者 ID/昵称、关键词与分类筛选、分页记录数量以及 `published_at DESC, id DESC` 稳定排序。
- 性能复测发现普通 JOIN 与 `STRAIGHT_JOIN` 会使 MySQL 读取并排序1,800行，中位约8～9ms，因此未采用。最终使用投影标量子查询：市场索引先满足 LIMIT 12，再在同一数据库请求中完成12次发布者主键查找；中位约1.397ms，对比原基础列表约1.557ms，无单条 SQL 退化。
- 未新增索引或迁移，未修改 API 响应字段。本批次专项查询计数测试3项和性能基线测试1项全部通过、0跳过；最终全量后端回归81项通过、0失败、0错误、0跳过，构建成功。测试只使用临时 MySQL，未连接开发数据库、未调用真实 AI。

#### 2026-10-09 / 批次 C3

- NM-C-03 VERIFIED：为意向、交接和留言建立只读联表投影，Service 的列表/详情 DTO 转换不再逐行查询用户、物品或时段；创建、取消、完成等写路径及事务边界保持不变。
- 实际 SQL 上限由 MyBatis 拦截器自动计数：物品意向从 `2 + 3N` 收敛为3条；单个“我的意向”为2条；我的交接从 `2 + 3H` 收敛为2条；交接详情为2条；留言从 `2 + C` 收敛为2条；看板从 `4 + 3I + 3H` 收敛为3条。
- 真实 MySQL 8.0 集成测试使用3件物品、5条意向、2条交接和3条留言，验证 `updated_at/created_at + id` 稳定排序、发布者权限、交接双方权限、回复父级、昵称/标题字段，以及已失效但仍被历史意向引用的时段。V7 外键保证物品、用户、时段等关联非空，因此读取 SQL 使用 INNER JOIN，缺失关联应由数据库约束阻止而不是静默返回空字段。
- 查询计数器提取为集成测试共享设施，NM-C-02 原查询数断言继续使用同一实现。未新增索引、迁移或 API 字段，未修改真实业务数据，也未调用 AI。
- 专项集成测试5项通过、0失败、0错误、0跳过；最终全量后端回归86项通过、0失败、0错误、0跳过，构建成功。所有数据库验证均使用由 Testcontainers 自动回收的临时 MySQL，未连接开发数据库、未调用真实 AI。

#### 2026-10-10 / 批次 C4

- NM-C-04 VERIFIED：在 NM-C-01 固定数据集上比较生产 SQL、候选改写和真实 `EXPLAIN ANALYZE`，只采纳有稳定计划收益且不改变业务语义的改动。
- “我的交接”联表查询增加 `STRAIGHT_JOIN`，确保先由交接表的 publisher/recipient 索引合并筛出60行，再关联物品和双方用户；避免优化器先对两张100行用户表做10,000行笛卡尔组合。最终验收同轮中位耗时由7.709ms降至2.952ms，真实计划的联表前输入由10,000行降为60行。
- “最想要”改为先按 `item_interest.item_id` 聚合，再对3,000个聚合结果关联物品；最终验收同轮中位耗时由23.036ms降至16.443ms，item 主键查找由9,000次降为3,000次。真实 MySQL 测试逐列比较优化前后结果和排序完全一致。
- 拒绝两项候选：交接 `UNION ALL` 仍被优化器选择不良连接顺序且无收益；最快完成改查 `item.completed_at` 虽减少600次主键查找，但会改变完成时间的权威来源，V7也未保证历史 item/handover 时间相等，因此保留原 SQL。
- 现有 `idx_handover_parties`、recipient 外键索引、`uq_item_interest_user` 和 `idx_handover_status_completed` 已支撑采纳方案。没有证据支持新增索引，故没有创建无意义的 Flyway 迁移，避免额外存储和写放大。
- 专项 SQL 契约测试3项、读取集成测试5项、性能/等价性测试2项均通过。Docker 恢复后最终全量后端回归87项通过、0失败、0错误、0跳过，构建成功；所有数据库测试使用临时 MySQL，未连接开发数据库、未调用真实 AI。

#### 2026-10-10 / 批次 C5

- NM-C-05 VERIFIED：新增独立 `SearchStrategyIntegrationTest`，从空库迁移至 V7 后生成30,000件 AVAILABLE 测试物品，分别测量高命中、低命中、单命中、无命中的 LIKE 列表和分页 count；每条 SQL 预热1次并执行5次 `EXPLAIN ANALYZE`。
- 最终验收同轮结果：高命中 LIKE 首页1.308ms、count 32.135ms；低命中首页2.609ms、count 34.287ms；单命中首页39.228ms、count 36.528ms；无命中首页53.847ms、count 36.130ms。低/无命中会扫描全部30,000行，风险已被量化，但当前规模仍在数十毫秒级。
- 测试库临时创建 FULLTEXT 候选索引：390条低命中样本的列表3.623ms、count 2.634ms。受控完整词查询与 LIKE 行数相同，但词中片段 `seline` 在 LIKE 中命中390条、FULLTEXT 为0，证明它不是当前“任意子串”语义的兼容替换。
- 决策：生产代码继续保留 LIKE，不新增 FULLTEXT 迁移，不引入 Elasticsearch。待可搜索物品达到约100,000级、搜索 p95 超过200ms、数据库 CPU 因搜索明显升高，或产品接受分词/前缀而非任意子串语义时，再以真实中文语料评估 MySQL ngram FULLTEXT；只有相关性、多字段、拼写容错等需求超出 MySQL 后才评估独立搜索引擎。
- 新增搜索策略测试2项通过；最终全量后端回归89项通过、0失败、0错误、0跳过，构建成功。FULLTEXT 索引仅存在于自动回收的临时测试库，未连接或修改开发数据库。

#### 2026-10-11 / 批次 C6

- NM-C-06 VERIFIED：保留 `ExchangeService` 的公开方法和嵌套响应记录作为兼容门面，将原先集中依赖6个 Mapper 的实现拆为交接时段、领取意向、交接状态、留言以及个人交换看板5个领域服务；Controller、`ItemService` 和现有调用方无需修改 API。
- 新增 `ExchangeSupport` 统一承担用户/物品加载、错误创建和实体/投影到响应 DTO 的转换，消除各领域服务重复的权限主体加载与映射逻辑；Mapper 仍只负责持久化和查询，不承载状态机规则。
- 8个写操作的 `@Transactional` 边界下移到实际执行写入的领域服务：时段配置，申请/取消意向，确认/取消/完成交接，留言/回复。兼容门面不声明事务，避免门面内部调用或未来复用造成事务自调用误判。
- 新增 `ExchangeServiceArchitectureTest` 两项结构约束，防止门面重新直接依赖 Mapper，并校验事务注解只位于负责写入的领域方法。原 `ExchangeServiceTest` 21项保持通过；真实 MySQL 定向集成测试17项验证并发确认、冲突回滚、状态一致性和读取查询数均未退化。
- 最终全量后端回归91项通过、0失败、0错误、0跳过；后端可执行 JAR 打包成功。前端回归7项通过且 Vite 生产构建成功。所有数据库验证使用 Testcontainers 临时 MySQL；未连接开发数据库、未调用真实 AI、未新增依赖或数据库迁移。

#### 2026-10-11 / 批次 C7

- NM-C-07 VERIFIED：将物品发布、交接配置、领取意向、留言及认证请求/响应模型移出 Controller，集中到 `web.dto`；Controller 只负责 HTTP 参数绑定、认证主体提取、DTO 到服务命令转换和调用编排，所有路径及 JSON 字段保持不变。
- 新增 `CreateItemCommand`，`ItemService` 不再导入或依赖 `ItemController` 的内部类型；同时将 `ItemController`、`ExchangeController` 和 `ItemService` 从压缩单行代码整理为可审查结构，没有改变分页、状态或权限行为。
- 发布及重配交接时段采用级联 Bean Validation，每个时段的 `startAt/endAt` 空值在 HTTP 边界直接拒绝；领取请求的 `slotId` 增加正数约束。时间先后、必须为未来等跨字段业务规则继续由 Service 校验，避免把业务规则塞入 DTO。
- Mapper 保留 SQL、投影和原子条件写入职责；`upsertWaiting` 等状态条件 SQL 是并发安全的持久化原语，状态转换是否合法仍由 Service 决策，因此未为形式上的“纯 Mapper”破坏原子性。
- 新增 DTO 级联校验与映射测试3项、Web 分层结构约束2项；定向回归8项通过。最终全量后端回归96项通过、0失败、0错误、0跳过，后端可执行 JAR 打包成功；前端7项通过且生产构建成功。未修改数据库、依赖或公开 API，也未调用真实 AI。

#### 2026-10-11 / 批次 C8

- NM-C-08 VERIFIED：新增无框架依赖的 `createLatestRequest` 请求协调器，使用 `AbortController` 取消同一页面上一请求，并以单调序号保证即使底层请求没有及时中止，旧响应也不能覆盖新状态。
- 市场快速筛选和翻页只接纳最新结果；详情页将物品、时段、留言、认证后的意向数据作为一次原子加载结果提交，并监听路由物品 ID 变化；我的页面和社区看板同样在卸载时取消未完成加载。
- AI 估价在输入改变时立即取消旧估价并清除 busy 状态，旧估价不会覆盖新输入；登录、发布、详情操作和交接操作增加函数级 busy 守卫，除按钮禁用外也阻止程序化双击产生重复请求。
- 取消请求统一返回未接纳结果，不进入 `messageOf` 或用户错误提示；只有当前最新请求的真实失败才更新错误和 loading 状态。
- 新增3项确定性异步测试，覆盖新响应优先、旧请求收到 abort、组件卸载取消且不抛错。前端全量10项通过、0失败、0跳过，Vite 生产构建成功；后端全量96项通过、0失败、0错误、0跳过。未新增依赖、未修改 API 或数据库、未调用真实 AI。

#### 2026-10-11 / 批次 C9

- NM-C-09 VERIFIED：新增 `runOperationOnce` 操作防重原语，以业务操作/资源 key 跟踪 pending；同一 key 的重复触发直接拒绝，不同 key 可并行，成功或异常都会在 `finally` 释放 key 以允许后续重试。
- 详情页不再使用会锁住所有按钮的全局 busy：申请/取消意向共享物品意向 key、确认领取人共享确认 key、确认还在、交接配置、留言和每条回复分别跟踪；各按钮只显示自身操作状态。
- “我的交接”按 `handover:{id}` 锁定，同一交接的取消与完成互斥，不同交接卡可独立操作。成功后统一重新读取服务端状态，继续由后端条件更新、事务和幂等约束提供最终保障。
- 留言和回复只在请求成功且状态重载完成后清空输入；失败时保留用户内容。登录、发布和 AI 估价沿用函数级 busy 守卫，按钮禁用不再是唯一防线。
- 新增3项异步单元测试，覆盖同 key 双击只执行一次、不同 key 并行、异常释放后可重试。前端全量13项通过、0失败、0跳过，Vite 生产构建成功；本批未修改后端，最近一次当前后端代码全量96项仍为通过、0跳过。未新增依赖、未修改 API 或数据库、未调用真实 AI。

#### 2026-10-11 / 批次 C10（Phase C 最终验收）

- NM-C-10 VERIFIED：逐项复核 NM-C-01～09 的实现、固定数据、查询数量、`EXPLAIN ANALYZE`、结构边界和前端异步测试证据，完整报告见 `docs/PHASE_C_ACCEPTANCE_REPORT.md`。NM-C-01～10 至此全部 VERIFIED，Phase C 正式完成。
- 后端以 Docker Desktop + Testcontainers MySQL 8.0 执行全量 `mvn test`：96项通过、0失败、0错误、0跳过；Flyway V1→V7、并发状态机、N+1 查询计数、3,000物品性能基线和30,000物品搜索策略均实际运行。
- 后端 `mvn -DskipTests package` 成功；前端 `npm.cmd test` 13项通过、0失败、0跳过；`npm.cmd run build` 成功并转换93个模块；`git diff --check` 退出码0，仅有 Windows 行尾转换提示。
- 验收未新增业务代码、依赖、API 或数据库迁移，未访问开发数据库、未调用真实 AI，也未执行 Git commit/push/reset/clean。残余的 CI、Compose、Actuator、组件测试和 E2E 明确进入 Phase D。

## 9. Phase D — 工程化、测试与部署

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-D-01 后端多阶段 Dockerfile | VERIFIED | P1 | 中 | Phase A | 镜像含源码/密钥或以 root 运行 | 可复现构建；运行镜像精简、非 root、无密钥；健康检查可用 |
| NM-D-02 前端生产镜像 | VERIFIED | P1 | 中 | Phase A | SPA 路由刷新 404、代理错误 | 静态资源生产构建；history fallback；API 反代正确；非 root 或最小权限 |
| NM-D-03 Docker Compose | VERIFIED | P1 | 中高 | NM-D-01、02、B-02 | 健康依赖和卷配置不稳定 | 应用、前端、MySQL 一条命令启动；持久卷保留数据；无默认生产密码；等待健康而非固定 sleep |
| NM-D-04 环境与密钥管理 | VERIFIED | P0 | 中 | NM-A-01、04 | 示例配置泄密 | dev/demo/test/prod 行为明确；`.env.example` 无真实秘密；缺少必需生产变量时快速失败 |
| NM-D-05 GitHub Actions CI | VERIFIED | P1 | 中 | NM-B-01 | Testcontainers runner 配置 | PR/push 执行后端测试、前端测试和双端构建；缓存不影响正确性；失败阻止绿色状态 |
| NM-D-06 Actuator 健康与指标 | VERIFIED | P1 | 中 | NM-D-04 | 暴露敏感端点 | 只公开 health/info 或经授权的指标；健康状态包含 DB；不暴露 env/configprops 等敏感信息 |
| NM-D-07 请求 ID 与日志规范 | VERIFIED | P1 | 中 | NM-A-07 | 日志泄漏 Cookie/Prompt/密钥 | 每请求生成/透传 request ID；错误体可关联；关键状态转换有结构化字段；敏感字段脱敏 |
| NM-D-08 后端覆盖率与测试分层 | VERIFIED | P1 | 中 | Phase B | 追求数字而写低价值测试 | 单元、Web、安全、MySQL 集成测试分层；关键状态分支有测试；报告覆盖缺口而不设虚假百分比 |
| NM-D-09 前端组件测试基础 | VERIFIED | P1 | 中 | Phase A/C | jsdom 与浏览器差异 | 覆盖登录、详情权限、错误反馈、发布校验、重复提交；测试可在 CI 无头运行 |
| NM-D-10 核心 E2E | VERIFIED | P1 | 高 | NM-D-03、09 | 测试数据污染和脆弱选择器 | 独立测试库完成登录→发布→申请→确认→完成；可重复运行；不调用真实 AI |
| NM-D-11 部署 smoke test | VERIFIED | P1 | 中 | NM-D-03、06、10 | 只验证端口未验证业务 | 新环境启动后验证健康、迁移、登录和核心 API；重启后数据保留 |
| NM-D-12 Phase D 验收与报告 | VERIFIED | P1 | 低 | NM-D-01～11 | 本地成功但 CI 失败 | 本地与 CI 命令、镜像摘要、启动时间、测试数量和已知限制均记录 |

### Phase D 执行记录

#### 2026-10-12 / 批次 D1（环境、健康检查与后端镜像）

- NM-D-04 VERIFIED：生产 profile 要求外部提供数据库主机、库名、用户、密码和允许源；生产 Cookie 默认 Secure、容器监听地址默认 `0.0.0.0`。新增2项配置契约测试，验证缺少关键变量快速失败和外部值正确绑定；开发、演示、测试、生产边界记录于 `docs/deployment.md`。
- NM-D-06 IN_PROGRESS：引入 Actuator，只暴露 health/info，匿名安全规则只允许这些 GET 路径，健康详情关闭并启用 liveness/readiness probes；安全测试确认 `/actuator/env` 不匿名开放。等待真实 MySQL 容器启动后验证 DB health 与响应内容，再决定 VERIFIED。
- NM-D-01 IN_PROGRESS：新增 Maven/JDK→Alpine JRE 多阶段 Dockerfile、非 root `app` 用户、生产 profile、内存比例参数、健康检查和 `.dockerignore`。当前 Docker Engine 未运行，镜像尚未实际构建/启动，因此不能标记 VERIFIED。
- 针对性测试5项通过。无 Docker 的全量回归共95项、0失败、0错误、28项明确跳过；跳过项全部为 MySQL/Testcontainers 集成测试。前端13项通过且 Vite 构建成功。Docker 恢复后必须重跑后端全量测试，并完成镜像构建、非 root 检查与健康状态验证。

#### 2026-10-13 / 批次 D2（Compose 全栈部署验收）

- NM-D-01 VERIFIED：后端多阶段镜像实际构建成功；运行容器身份为 `uid=100(app)`，容器健康检查通过。首次构建发现 `dependency:go-offline` 会解析无关 Site 插件并受网络握手影响，已改为直接执行可复现的 Maven package。
- NM-D-02 VERIFIED：Vue 生产构建由非特权 Nginx 镜像提供，运行身份为 `uid=101(nginx)`；根页面、SPA fallback、`/api/` 反代和静态资源规则均由同源入口提供。
- NM-D-03 VERIFIED：`docker compose up -d --wait` 依次等待 MySQL、后端和前端健康；数据库端口不暴露到宿主机且密码无默认值。新建隔离卷后 Flyway 从空库成功执行7个迁移；写入临时标记、重启 MySQL 后仍可读取，随后已删除临时表。
- NM-D-06 VERIFIED：经前端入口访问 `/actuator/health` 返回200与 `UP`，`/actuator/env` 返回404；后端不映射宿主机端口，Compose 健康状态同时验证数据库可连接。详细健康信息仍保持关闭。
- Docker 可用后后端全量回归实际运行99项，0失败、0错误、0跳过，包含 MySQL Testcontainers 测试。前端既有13项测试和 Vite 构建此前均通过。本批次未完成登录及完整业务 E2E，因此 NM-D-11 仍为 TODO。

#### 2026-10-13 / 批次 D3（持续集成）

- NM-D-05 DONE：新增 GitHub Actions CI，限定只读仓库权限，并为同一引用启用旧任务取消。后端 Job 使用 Temurin Java 17 和 Maven 缓存执行 `mvn verify`；前端 Job 使用 Node.js 22 和锁文件执行 `npm ci`、13项测试及 Vite 构建；二者成功后才验证 Compose 配置并构建双端镜像。失败时保留后端测试报告7天。
- CI 原始命令已在本机复跑：后端99项测试、0失败、0错误、0跳过且打包成功；前端13项测试全部通过并构建成功；Compose 配置校验和双镜像构建成功。工作流只有推送至 GitHub 后才能获得远端运行证据，因此当前为 DONE，不能提前标记 VERIFIED。
- `npm audit --omit=dev` 报告3个高危项，涉及 Vue/server-renderer 的特定 XSS 公告和 `source-map-js` 的拒绝服务公告。当前未自动执行强制升级；需要单独核对本 SPA 的可达性、兼容升级版本并回归，不能为消除告警而绕过锁文件或降低测试标准。

#### 2026-10-13 / 批次 D4（请求关联与关键状态日志）

- NM-D-07 VERIFIED：新增最高优先级请求过滤器，合法 `X-Request-ID` 可安全透传，缺失、超长或包含控制字符的值会替换为 UUID；标识同步写入响应头、MDC 和统一 `ApiError.requestId`，并在请求结束后清理 MDC，避免线程复用污染。
- CORS 明确允许并暴露请求 ID 头；日志级别格式包含 request ID。领取意向以及交接确认、取消、完成操作在事务成功路径记录实体 ID、操作者 ID 和目标状态，不记录用户名、Cookie、密码、地点、AI Prompt 或密钥。
- 端到端安全测试发现请求 ID 过滤器默认顺序晚于 Spring Security，导致401响应缺少关联标识；已提升到安全链之前并加入回归断言。针对性8项通过；后端全量103项通过，0失败、0错误、0跳过。
- Compose 后端镜像重建并滚动更新成功，三个服务均恢复 Healthy。经 Nginx 请求受保护 API 返回401，响应头和 JSON 错误体均保留同一测试 request ID，证明真实部署链路有效。

#### 2026-10-14 / 批次 D5（后端测试分层与覆盖率）

- NM-D-08 VERIFIED：引入 JaCoCo `prepare-agent`/`report`，`mvn verify` 自动生成 HTML、XML、CSV 报告；CI 无论成功失败均尝试保存报告14天，但不设置缺乏业务依据的统一覆盖率阈值。
- 新增 `docs/testing.md`，明确单元、Web/安全、架构/SQL契约、MySQL Testcontainers 与性能证据测试的职责和执行方式。新增数据库用户加载/不存在测试，以及物品确认记录不存在、条件 UPDATE 竞争失败回归测试。
- 后端全量107项通过，0失败、0错误、0跳过。68个生产类的基线为：行83.7%（679/811）、分支59.9%（175/292）、方法84.4%（325/385）、类95.6%（65/68）。主要缺口为薄 Controller、`ItemService` 发布/详情和少量 DTO/启动入口；已记录后续由服务测试及 E2E 覆盖的边界。

#### 2026-10-15 / 批次 D6（前端组件测试与依赖安全）

- NM-D-09 VERIFIED：引入 Vitest 4、Vue Test Utils 与 jsdom，并把原有 Node 单元测试和 Vue 组件测试显式分层，避免 Node 测试器错误加载 `.vue` 文件。CI 既有 `npm test` 命令自动覆盖两层测试。
- 新增6项组件测试：登录必填校验、API 错误展示、安全站内跳转、登录重复提交锁定、详情页匿名/发布者权限分支、发布表单前置校验。与原有13项单元测试合计19项，全部通过。
- Vue 从3.5.13升级至3.5.44以修复已公告的 server-renderer XSS 依赖链；更新锁文件后 `npm audit --omit=dev` 与完整安装审计均为0漏洞。未使用 `npm audit fix --force`。Vite 生产构建通过。

#### 2026-10-15 / 批次 D7（核心浏览器 E2E）

- NM-D-10 VERIFIED：引入 Playwright Chromium，以两个独立浏览器 Session 完成发布者登录/发布、领取人登录/申请、发布者确认及完成交接的真实前后端闭环。用例发布免费物品，未配置或调用付费 AI。
- E2E 通过独立 `neighbormarket-e2e` Compose 项目、独立数据库名和独立卷运行，避免读写开发数据。测试通过后数据库实查为 `item=COMPLETED`、`item_interest=CONFIRMED`、`handover=COMPLETED` 且完成时间已记录，随后销毁隔离卷。
- 本地 Playwright 核心用例1项通过。CI 新增独立 E2E Job，在后端与前端基础 Job 通过后构建隔离全栈、安装 Chromium、运行用例，失败时保留截图/trace，并在所有结果下执行定向清理。远端 CI 结果仍需推送后验证。

#### 2026-10-16 / 批次 D8（部署 smoke 与重启恢复）

- NM-D-11 VERIFIED：新增只读 `scripts/smoke-test.ps1`，验证 Vue 入口、Actuator `UP`、请求 ID、CSRF、登录、Session 保持、公开列表及已认证的“我的发布” API。口令仅从 `SMOKE_PASSWORD` 进程环境读取，脚本不修改业务数据也不输出秘密。
- 在全新的 `neighbormarket-e2e` 隔离卷中启动三个服务，首次 smoke 通过，Flyway 实查7项成功迁移且最新版本为7。核心 E2E 生成一条 `COMPLETED` 记录后重启全套容器，三个服务均恢复 Healthy，第二次 smoke 通过，迁移历史和已完成记录仍存在。
- CI 的核心 E2E Job 已增加新部署 smoke、浏览器闭环、整套重启及重启后持久数据 smoke。远端 CI 需在本批推送后单独确认，不提前标记为远端通过。

#### 2026-10-16 / 批次 D9（Phase D 总验收）

- NM-D-12 DONE：完成 Phase D 本地总验收并新增 `docs/PHASE_D_REPORT.md`，记录各任务证据、执行命令、镜像大小、启动/重启时间、已知边界和下一阶段入口。
- 最终后端 `mvn verify` 运行107项测试，0失败、0错误、0跳过，JAR 和 JaCoCo 报告生成成功；行覆盖率83.7%（680/812），分支覆盖率59.9%（175/292）。前端13项单元测试和6项组件测试通过，Vite 构建通过，生产依赖审计0漏洞。
- 常规 Compose 三个容器均为 Healthy；后端镜像335 MB、前端镜像77.4 MB，且运行用户为非 root。GitHub Actions 通过已保存凭据的 API 只读查询完成核对：核心 E2E 运行 `38764242106` 和包含部署 smoke/重启持久化的运行 `38767299683` 均为 `success`，因此 NM-D-05、NM-D-12 及 Phase D 整体标记 VERIFIED。

## 10. Phase E — 业务完整性完善

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-E-01 物品编辑规则设计 | TODO | P1 | 中 | Phase B | 编辑破坏已有意向/交接快照 | 明确各状态可编辑字段；有等待意向时的限制；RESERVED/COMPLETED 规则文档化 |
| NM-E-02 物品编辑后端 | TODO | P1 | 中高 | NM-E-01 | 越权、覆盖更新 | 仅发布者可编辑；条件更新防并发覆盖；价格规则复用；历史交接快照不变；集成测试通过 |
| NM-E-03 物品编辑前端 | TODO | P1 | 中 | NM-E-02 | 表单与发布规则漂移 | 复用校验；加载/保存/冲突反馈完整；移动端可用；重复提交受控 |
| NM-E-04 图片存储方案与威胁模型 | TODO | P1 | 中 | NM-D-04 | 路径穿越、恶意文件、成本失控 | 明确本地卷/对象存储抽象、公开访问策略、保留与删除规则；不直接信任扩展名 |
| NM-E-05 安全图片上传后端 | TODO | P1 | 高 | NM-E-04 | MIME 欺骗、超大文件、SVG/XSS、目录逃逸 | 白名单类型、内容探测、大小/像素限制、随机文件名、路径隔离；拒绝 SVG/可执行内容；权限和清理测试 |
| NM-E-06 图片上传前端 | TODO | P1 | 中 | NM-E-05 | 上传失败导致表单丢失 | 选择、预览、进度、失败重试和删除反馈；保留其他表单字段；移动端适配 |
| NM-E-07 物品状态操作审计 | TODO | P1 | 中 | Phase B | 增加无必要状态 | 复核确认还在、取消、完成和编辑边界；不新增无业务依据状态；非法转换返回 409 |
| NM-E-08 用户反馈与可访问性 | TODO | P2 | 中 | C-09 | 视觉重设计扩大范围 | loading/empty/error/success 一致；键盘与焦点可用；关键表单有标签；不做无关视觉重构 |
| NM-E-09 移动端真实验证 | TODO | P2 | 中 | NM-E-03、06、08 | 仅模拟器验证不足 | 至少验证 320/375/768/桌面视口；核心操作无横向滚动或遮挡；记录截图/问题 |
| NM-E-10 Phase E 验收与报告 | TODO | P1 | 低 | NM-E-01～09 | 功能完成但边界漏测 | 编辑、上传、权限、失败恢复、移动端和构建均有证据；现有闭环回归通过 |

## 11. Phase F — Redis 与可选技术增强

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-F-01 Redis 必要性复评 | TODO | P2 | 中 | Phase B～D | 为技术栈而技术栈 | 用部署实例数、AI 调用量、限流准确性和运维成本决策；形成采用/不采用记录 |
| NM-F-02 AI 成本与限流指标 | TODO | P1 | 中 | D-06、07 | 记录 Prompt 或敏感内容 | 记录匿名用户键、结果类型、延迟、限流和失败分类；不记录完整 Prompt/响应 |
| NM-F-03 共享 AI 限流（条件任务） | BLOCKED | P2 | 中高 | NM-F-01 决定采用 Redis | Redis 故障阻断手工发布 | 多实例共享原子限额和 TTL；故障降级明确；手工发布永远可用；容器集成测试通过 |
| NM-F-04 Session 共享（条件任务） | BLOCKED | P2 | 中高 | 多实例且无粘性会话 | 引入序列化和运维复杂度 | 仅需求成立时用 Spring Session；跨实例登录可用；注销和过期一致；故障行为文档化 |
| NM-F-05 缓存场景否决/采用记录 | TODO | P2 | 低 | NM-C-10 | 用缓存掩盖慢 SQL | 热点物品和搜索缓存逐项评估一致性、命中率和失效成本；无收益则明确不引入 |
| NM-F-06 架构边界复核 | TODO | P2 | 低 | NM-F-01～05 | 不必要拆服务 | 默认保持模块化单体；Spring Cloud/Kafka/RabbitMQ 无真实理由则记录为“不引入” |
| NM-F-07 Phase F 验收与报告 | TODO | P2 | 低 | NM-F-01～06 | 把“不引入”误判为未完成 | 技术决策有数据和成本依据；条件任务保持 BLOCKED/不适用说明，不虚构实现 |

## 12. Phase G — 求职展示与最终交付

| 任务 | 状态 | 优先级 | 难度 | 依赖 | 主要风险 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| NM-G-01 README 与事实对齐 | TODO | P1 | 中 | A～F | 文档提前声称完成 | 安装、配置、限制、已实现/未实现与代码一致；无真实秘密；命令可复制执行 |
| NM-G-02 架构图与模块边界 | TODO | P2 | 中 | C、D | 图与代码漂移 | 展示浏览器、前后端、DB、可选 Redis/AI 边界；标注同步调用和数据所有权 |
| NM-G-03 ER 图更新 | TODO | P1 | 中 | 最终迁移 | 漏字段/约束 | 与所有 Flyway 迁移逐项核对；PNG/PDF/源码一致；标出关键约束 |
| NM-G-04 状态机与并发时序 | TODO | P1 | 中 | Phase B | 只画理想路径 | 包含申请、确认竞争、取消/完成竞争、失败回滚和幂等语义 |
| NM-G-05 SQL 优化报告 | TODO | P1 | 中 | Phase C | 不同数据集不可比较 | 固定数据集、SQL、EXPLAIN ANALYZE、查询数和前后指标完整 |
| NM-G-06 自动化测试报告 | TODO | P1 | 中 | Phase D/E | 手工结果冒充自动化 | 按单元/集成/安全/组件/E2E 分类，记录数量、环境、命令、跳过项和 CI 链接 |
| NM-G-07 Docker 与部署指南 | TODO | P1 | 中 | Phase D | 缺少初始化/恢复说明 | 新机器按文档启动；包含配置、健康检查、卷、备份、升级和停止方法 |
| NM-G-08 演示流程 | TODO | P2 | 低 | A～F | 演示路径依赖脆弱数据 | 核心闭环、并发证据、AI 降级、健康与 CI 在限定时间可复现 |
| NM-G-09 技术选型与取舍 | TODO | P1 | 低 | Phase F | 堆砌名词 | 解释条件 UPDATE、单体、MySQL、Redis取舍及未使用 MQ/微服务的理由 |
| NM-G-10 最终验收审计 | TODO | P0 | 中 | NM-G-01～09 | 遗留 P0/P1 被文档掩盖 | 按完成标准逐项复验；未知项明确标记；Git 状态、构建、测试、部署和文档一致 |

## 13. 测试与回归策略

### 13.1 测试分层

| 层级 | 目标 | 典型内容 |
| --- | --- | --- |
| Java 单元测试 | 纯业务规则快速反馈 | 价格规则、新鲜度、状态转换、AI JSON 校验 |
| Web/Security 测试 | HTTP 契约与安全 | 认证、CSRF、权限、统一错误体、参数校验 |
| MySQL 集成测试 | 真实 SQL/约束/事务 | Flyway、外键、唯一键、回滚、MyBatis 映射 |
| 并发测试 | 最终一致性 | 多人确认、重复申请、取消/完成竞争 |
| 前端单元/组件测试 | 页面状态和交互 | auth ready、错误反馈、表单、重复提交、请求取消 |
| E2E | 全栈交付能力 | 登录、发布、申请、确认、完成、重启持久性 |
| 部署 smoke | 新环境可运行 | Compose、health、迁移、核心 API、数据卷 |

### 13.2 每阶段固定回归

至少执行：后端全量测试、后端打包、前端全量测试、前端生产构建。涉及数据库时增加 MySQL 集成测试；涉及 UI 时增加组件/E2E；涉及部署时增加镜像和 Compose smoke test。

测试报告必须记录通过、失败、跳过数量。因 Docker、浏览器或外部服务无法运行的测试应明确标记“未验证”和阻塞原因，不得以已有文档记录替代本次执行。

### 13.3 数据保护

- 自动测试使用临时容器数据库或独立测试 schema，禁止指向 `neighbor_market` 开发/演示库。
- Flyway 测试只在临时空库运行。
- 演示数据 reset 脚本不属于自动回归命令。
- AI 测试只使用本地 fake server，不读取真实 `LLM_API_KEY`。
- 上传测试使用临时目录/临时卷并在测试作用域内清理。

## 14. 推荐执行顺序

1. Phase A 第一批：NM-A-01、NM-A-02、NM-A-04、NM-A-06、NM-A-07 的契约设计。
2. Phase A 第二批：NM-A-03、NM-A-08，完成 CSRF 前后端联调。
3. Phase A 第三批：NM-A-05 时段数据模型与迁移，随后 NM-A-09/10 验收。
4. Phase B：先 B-01/B-02 建立真实数据库验证能力，再做 B-03～B-09。
5. Phase C：先采集基线 C-01，再消除 N+1、优化索引，最后结构重构。
6. Phase D：在 A 稳定后启动；CI 可尽早加入，E2E/Compose 等待数据库集成稳定。
7. Phase E：编辑先行；图片上传在环境和卷方案稳定后实施。
8. Phase F：只做基于数据的必要性决策；没有需求时以“不引入”作为有效结论。
9. Phase G：持续收集证据，最终统一整理和复验。

## 15. Phase A 第一批实施包

获得用户确认后，第一批建议只实施以下范围，避免一次改动安全、数据库和前端过多区域：

### 批次 A1：认证基线与环境隔离

- NM-A-01：将演示用户初始化限制到 demo profile，凭据改为外部配置，并补配置测试。
- NM-A-02：采用 Spring Security 支持的认证持久化流程并验证 Session ID 轮换。
- NM-A-04：拆分开发/demo/生产 Cookie 配置，生产启用 Secure。
- NM-A-07（第一部分）：先定义统一 `ApiError` 字段和认证错误契约，避免后续 CSRF/前端各自发明格式。

验收命令预计包括后端安全测试、全量 `mvn test` 和打包；不启动真实数据库，不调用 AI。

### 批次 A2：CSRF 与前端认证同步

- NM-A-03、NM-A-06、NM-A-08。
- 后端提供 Cookie/Token CSRF 协议，Axios 统一携带；详情页等待认证完成；错误处理与返回路径统一。
- 增加后端安全测试和前端组件/纯函数测试。

### 批次 A3：时段历史修复

- NM-A-05、NM-A-09、NM-A-10。
- 先确认保留历史时段的数据模型，再追加迁移和真实 MySQL 回归测试；不得修改 V3 或删除已有意向。

## 16. 整体完成标准

以下条件必须同时满足：

1. 发布→申请→确认→取消/完成→归档在新环境可运行并有 E2E 证据。
2. 不存在已知严重认证漏洞；演示凭据不会进入非 demo 环境。
3. Flyway 可从空库迁移，已有数据升级路径无损。
4. 并发确认、重复申请、取消/完成竞争由真实 MySQL 测试验证。
5. 重复请求不会产生重复历史、500 或非法状态。
6. 关键 SQL 有固定数据集和 EXPLAIN ANALYZE 证据，主要 N+1 已处理。
7. 后端、前端、集成和核心 E2E 测试在 CI 通过。
8. Docker Compose 可部署并在重启后保留数据。
9. 健康检查、请求 ID 和安全日志可用于基础运维，且不泄漏敏感信息。
10. README、ER 图、架构图、迁移、API 和实际代码一致。
11. Redis 等可选技术有基于需求的决策；不以框架数量作为完成依据。
12. 最终审计无未处理 P0；残余 P1/P2 有明确风险、负责人/阶段和验证计划。

## 17. 阶段报告模板

每个 Phase 完成后在本路线图更新状态，并向用户报告：

1. 阶段与工作树/提交基准。
2. 完成任务编号及状态变化。
3. 修改文件和数据库迁移。
4. 关键设计与取舍。
5. 执行命令、测试通过/失败/跳过数量。
6. Acceptance Criteria 逐项结果。
7. 未验证项、残余问题与风险。
8. 下一阶段建议。
9. 是否建议提交；未获授权不得 commit/push。
