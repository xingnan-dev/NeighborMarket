# NeighborMarket 技术栈简介

## 技术选型

- 前端：Vue 3、Vue Router、Vite、原生 CSS。
- 后端：Java 17、Spring Boot、Spring Security Session、Bean Validation。
- 数据访问：MyBatis-Plus。
- 数据库：MySQL 8，Flyway 管理 V1～V8 迁移。
- AI：后端调用 OpenAI-compatible Chat Completions，前端只接收经过校验的 DTO。

## 数据与权限

用户、物品、交接时段、意向、交接和留言均有明确外键。item_interest(item_id, user_id) 唯一约束避免重复意向；V7 的生成列唯一索引限制每件物品最多一条 ACTIVE 交接，复合外键防止跨物品或领取人错配；item_comment.parent_id 实现一层回复。后端从 Session 用户确定发布者、领取人和留言权限，前端不直接连接数据库。

## 并发控制

确认领取人时使用物品状态为 AVAILABLE 且 current_handover_id 为空的条件更新，只有一个请求能成功进入 RESERVED。取消和完成交接也按当前状态条件更新，并在事务中同步物品、意向和交接状态。

## AI 安全降级

API Key 和供应商配置只从环境变量读取。后端限制输入与结构化输出，设置 45 秒请求超时、每用户 60 秒频率限制，不记录密钥或供应商原始响应。信息不足、429、超时、供应商错误或非法结果均允许用户手工定价继续发布。

## 看板与测试

社区看板由 DashboardMapper 使用 SQL 聚合生成 DTO，不加载全表到 Java 计算；V5 提供看板索引，V6 通过 active 时段保留意向历史，V7 强化交易数据一致性，V8 增加物品版本字段。后端使用 `mvn verify`，前端使用 `npm test`、`npm run test:component` 和 `npm run build` 验证，并有真实 MySQL 与核心浏览器 E2E。项目提供本地 Docker Compose 部署；正式公网域名、TLS、自动化备份恢复和生产级指标采集仍需由部署环境配置。
