# 阶段 1 架构

浏览器中的 Vue 3 单页应用通过 `/api` 调用 Spring Boot。开发环境由 Vite 将请求代理到 `localhost:8080`。后端使用 Spring Security 的服务端 `HttpSession` 认证、MyBatis-Plus 访问 MySQL、Flyway 管理结构。

- 前端不接受或保存 `publisherId`；发布接口从已认证用户名查询用户 ID。
- 浏览器只持有 HttpOnly、SameSite=Lax 的会话 Cookie；刷新页面后通过 `/api/auth/me` 恢复界面状态。
- 数据库连接全部来自环境变量。仓库只包含无真实凭据的示例。
- 阶段 1 仅包含登录、发布、广场、搜索/交易方式筛选、详情和我的发布。

会话 Cookie 可降低脚本直接读取凭据的风险，但仍需 HTTPS、CSRF 防护、会话过期和密钥管理才能用于生产。本地阶段因前端代理形成同源请求；当前 CSRF 暂时关闭，只适合本地演示。
