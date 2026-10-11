# NeighborMarket 最终架构

## 请求链路

~~~text
Vue 3 + Vite
        │  /api（Vite 开发代理）
        ▼
Spring Boot REST
        │
        ├── Spring Security Session
        ├── MyBatis-Plus
        └── Dashboard SQL 聚合
                │
                ▼
          MySQL + Flyway
~~~

前端只调用后端 REST API，不直接访问 MySQL，也不直接访问模型供应商。开发环境由 Vite 将 /api 代理到 Spring Boot；生产部署尚未配置。

## 认证与数据边界

- 登录成功后由 Spring Security 使用 HttpOnly、SameSite=Lax Session Cookie 维持会话。
- 发布、想要、确认领取人、交接、留言回复和 AI 估价接口在后端依据当前认证用户授权。
- 前端不提交或保存 publisherId 作为权限依据，发布者身份由后端 Session 用户解析。
- CSRF 防护已启用：前端先获取 CSRF Cookie，并在写请求头中回传 Token；登录成功后由 Spring Security 更换 Session ID。生产 profile 要求 Secure Cookie，演示账号仅在显式 demo profile 下初始化。

## 业务模块

- 广场与详情：物品查询、筛选、时段、意向和公开留言。
- 发布：物品基本信息、公共交接地点、未来时段和 AI 估价辅助。
- 交接：发布者确认领取人，双方可取消，发布者可标记已送出。
- 看板：DashboardService 调用 DashboardMapper 的 SQL 聚合，直接从数据库计算本月发布、本月完成、当前在售及两个排行。

## 后端服务边界

交换业务保持模块化单体，不拆分微服务。`ExchangeService` 是面向 Controller 和现有调用方的兼容门面，只保留稳定的公开方法与响应记录；具体规则按职责拆分：

- `PickupOptionService`：有效时段查询、发布时初始时段和发布者重配时段。
- `InterestWorkflowService`：申请、取消以及发布者/申请人的意向查询。
- `HandoverWorkflowService`：确认领取人、双方取消、发布者完成及交接查询。
- `CommentWorkflowService`：公开留言和发布者一层回复。
- `PersonalExchangeService`：组合当前用户的意向与交接看板。
- `ExchangeSupport`：共享的用户/物品加载、错误创建和响应映射，不承载业务状态转换。

事务边界声明在实际执行写入的领域服务方法上，而不是兼容门面上；Controller 不直接访问 Mapper。并发安全仍由事务内条件 UPDATE、影响行数检查和 V7 数据库约束共同保证。

## HTTP 与 DTO 边界

- `web.dto` 保存 HTTP 请求/认证响应模型和 Bean Validation 约束；Controller 不再内嵌传输记录。
- Controller 只绑定请求、读取认证主体、转换命令并调用 Service，不直接访问 Mapper 或编写业务状态转换。
- `CreateItemCommand` 是物品发布的服务层输入，解除 `ItemService` 对 Web Controller 类型的反向依赖。
- DTO 校验负责必填、长度、数字格式和嵌套时段空值；未来时间、结束晚于开始、价格与交易方式组合等跨字段业务规则仍由 Service 验证。
- Mapper 负责 CRUD、查询投影及事务所需的原子条件 SQL；是否允许执行状态转换由 Service 决定。

## 前端请求一致性

- 页面级读取通过 `createLatestRequest` 管理；发起新请求时中止旧请求，并以请求序号拒绝迟到响应。
- 市场筛选、详情、我的页面、社区看板和 AI 估价在组件卸载时取消未完成请求，取消不会显示成网络错误。
- 详情页的公开数据与认证后意向数据完成后一次性提交到页面状态，避免混合不同物品或不同加载轮次的数据。
- 登录、发布、估价和状态操作同时使用按钮禁用与函数级 busy 守卫，降低双击及程序化重复触发风险；服务端事务、幂等和条件更新仍是最终一致性保障。
- 操作级 pending 使用业务资源 key：同一物品的意向操作、同一交接的取消/完成彼此互斥，不同留言、交接或配置操作不会被一个全局 busy 无关阻塞。
- 写操作成功后重新读取服务端最终状态；表单内容只在成功后清理，失败时保留以便用户修正和重试。前端防重只改善交互，不能替代服务端幂等与并发控制。

## AI 估价边界

调用链为 PublishView -> POST /api/ai/pricing -> PricingService -> PricingGateway -> OpenAI-compatible Chat Completions。LLM 调用只发生在后端，API Key、供应商原始响应和 Prompt 不下发前端、不写入日志。后端校验 NEED_MORE_INFO 与 ESTIMATE 结构，设置 45 秒请求超时及每个登录用户 60 秒频率限制；429、超时、供应商错误、非法结构或信息不足均回到可手工填写价格的降级路径。

## 数据库演进

Flyway 按 V1～V7 顺序执行：

1. V1 创建用户和物品基础表。
2. V2 强化 last_confirmed_at 并增加索引。
3. V3 增加交接时段、意向、交接、交接地点和当前交接引用。
4. V4 增加公开留言的一层回复自关联。
5. V5 只增加看板查询索引，不增加业务表或业务流程。
6. V6 为交接时段增加 active 标志；重配只停用旧时段，不删除仍被历史意向引用的记录。
7. V7 增加状态 CHECK、单物品 ACTIVE 交接唯一约束及同物品/用户复合外键；迁移在任何 DDL 前检查已有数据兼容性。
