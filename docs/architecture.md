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
- 当前 CSRF 防护关闭，仅用于本地面试演示；正式部署前必须重新启用 CSRF、HTTPS、会话策略和密钥管理。

## 业务模块

- 广场与详情：物品查询、筛选、时段、意向和公开留言。
- 发布：物品基本信息、公共交接地点、未来时段和 AI 估价辅助。
- 交接：发布者确认领取人，双方可取消，发布者可标记已送出。
- 看板：DashboardService 调用 DashboardMapper 的 SQL 聚合，直接从数据库计算本月发布、本月完成、当前在售及两个排行。

## AI 估价边界

调用链为 PublishView -> POST /api/ai/pricing -> PricingService -> PricingGateway -> OpenAI-compatible Chat Completions。LLM 调用只发生在后端，API Key、供应商原始响应和 Prompt 不下发前端、不写入日志。后端校验 NEED_MORE_INFO 与 ESTIMATE 结构，设置 45 秒请求超时及每个登录用户 60 秒频率限制；429、超时、供应商错误、非法结构或信息不足均回到可手工填写价格的降级路径。

## 数据库演进

Flyway 按 V1～V5 顺序执行：

1. V1 创建用户和物品基础表。
2. V2 强化 last_confirmed_at 并增加索引。
3. V3 增加交接时段、意向、交接、交接地点和当前交接引用。
4. V4 增加公开留言的一层回复自关联。
5. V5 只增加看板查询索引，不增加业务表或业务流程。
