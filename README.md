# NeighborMarket

NeighborMarket 是一个面向社区闲置物品流转的最终面试展示版本，采用 Vue 3 + Vite 前端、Spring Boot REST 后端、MyBatis-Plus、MySQL 和 Flyway。

## 已实现功能

- Session 登录与两个本地演示账号（neighbor_a、neighbor_b）
- 物品发布、名称搜索、分类筛选、交易方式筛选和状态筛选
- 24/72 小时新鲜度提示，以及发布者确认“还在”
- 想要、取消想要和重新申请
- 公共交接地点与 1～5 个未来交接时段
- 发布者确认领取人
- 交接双方取消交接
- 发布者标记已送出并完成归档
- 公开留言与发布者一层回复
- 固定价格物品的 AI 估价、信息不足追问和手工定价降级
- 社区数据看板：本月发布、本月成交、当前在售、最快成交和未成交意向排行
- 桌面端与移动端响应式界面
- 可重复执行的演示数据脚本和 frontend/public/demo/ 本地图片

## 本地运行

需要 Java 17、Maven、Node.js、npm 和 MySQL 8。数据库名默认为 neighbor_market。

在当前 PowerShell 会话中设置环境变量；密码只在本机交互输入，不写入仓库：

~~~powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_NAME = 'neighbor_market'
$env:DB_USERNAME = 'neighbor_app'
$env:DB_PASSWORD = Read-Host 'Database password' -MaskInput
$env:APP_ALLOWED_ORIGINS = 'http://localhost:5173,http://localhost:5174'
~~~

AI 配置只从环境变量读取，不能把密钥写入代码、文档或日志：

~~~powershell
$env:LLM_API_KEY = Read-Host 'LLM API key' -MaskInput
$env:LLM_BASE_URL = 'https://your-openai-compatible-endpoint'
$env:LLM_MODEL = 'your-model-name'
$env:LLM_PROVIDER = 'openai-compatible'
~~~

启动后端：

~~~powershell
Set-Location backend
mvn spring-boot:run
~~~

启动前端（另开终端）：

~~~powershell
Set-Location frontend
npm install
npm run dev
~~~

浏览器访问 http://localhost:5174。本地演示账号为 neighbor_a 和 neighbor_b，密码请使用本机已有的演示凭据，不要写入文档或命令历史。

AI 估价单次请求超时为 45 秒；同一登录用户 60 秒内最多请求一次。供应商错误、429、超时或信息不足时，页面保留手工填写价格的发布路径。

## 测试与构建

~~~powershell
Set-Location backend
mvn test
mvn package
Set-Location ../frontend
npm test
npm run build
~~~

## 演示数据

演示数据脚本和图片说明位于 [scripts/demo/README.md](scripts/demo/README.md)。脚本会清理并重建本地业务数据，执行前必须备份；不要在演示过程中执行 reset 脚本。图片放在 frontend/public/demo/，由物品记录使用 /demo/... 路径引用。

## 文档索引

- [docs/architecture.md](docs/architecture.md)：最终系统架构与边界
- [docs/er.md](docs/er.md)：V1～V5 对应的实体关系
- [docs/stage-4.md](docs/stage-4.md)：AI 估价协议与校验记录
- [docs/demo-guide.md](docs/demo-guide.md)：5 分钟面试演示脚本
- [docs/technical-stack.md](docs/technical-stack.md)：技术栈与工程实践简介
- [scripts/demo/README.md](scripts/demo/README.md)：演示数据备份、重建和验证说明

## 时间规则

数据库使用 Asia/Shanghai 业务墙上时间。接口声明 displayTimeZone: Asia/Shanghai；前端按该时区格式化时间。发布后 4 小时内显示“刚上架”，24～72 小时显示“新上架”，更早记录显示天数；超过 7 天未确认的物品会提示较久未确认。

## 已知限制

- 当前未实现图片上传，演示使用 frontend/public/demo/ 下的本地静态图片。
- 当前 CSRF 防护关闭，仅适用于本地面试演示，不适合作为正式公网部署配置。
- AI 估价不代表实时二手市场成交价，仅提供辅助参考。
- 尚未配置正式公网生产部署、域名、TLS 和生产级监控。
