# NeighborMarket 邻里闲置

阶段 3 的可运行全栈版本：Vue 3 + Vite 前端、Spring Boot 3 + MyBatis-Plus 后端、MySQL + Flyway 持久化。

## 已实现

- 两个 BCrypt 演示账号与服务端会话登录
- 登录状态下发布物品，发布者身份完全取自后端认证信息
- 免费送、随便给、固定价格的数据库和接口校验
- 默认只展示 `AVAILABLE` 的真实数据库分页广场
- 名称/描述搜索、分类与交易方式组合筛选、详情、我的发布
- 24/72 小时新鲜度、最近确认时间、7 天陈旧提示和发布者“确认还在”
- 加载、空数据、表单校验和接口失败状态
- 移动端底部导航和桌面端顶部导航

已实现交接地点与未来时段、买家意向、发布者确认、交接卡、双方取消、发布者完成归档和公开留言。AI 定价与需求订阅尚未实现，也没有对应假入口。

## 本地配置

需要 Java 17、Maven、Node.js、npm 和 MySQL 8。数据库应为 `neighbor_market`，应用账号只拥有该库所需权限。

PowerShell 当前会话中设置环境变量（密码只在本机填写，不写入文件）：

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3307'
$env:DB_NAME = 'neighbor_market'
$env:DB_USERNAME = 'neighbor_app'
$env:DB_PASSWORD = Read-Host 'Database password' -MaskInput
$env:APP_ALLOWED_ORIGINS = 'http://localhost:5173,http://localhost:5174'
```

仓库根目录的 `.env.example` 仅用于说明变量名。Spring Boot 不会自动读取该文件。

## 启动

后端：

```powershell
Set-Location backend
mvn spring-boot:run
```

前端（另一个终端）：

```powershell
Set-Location frontend
npm install
npm run dev
```

打开 `http://localhost:5174`。演示账号：

- `neighbor_a` / `neighborA123!`
- `neighbor_b` / `neighborB123!`

演示密码只用于本地样例，数据库保存的是 BCrypt 哈希，不可用于真实环境。

## 构建与测试

```powershell
Set-Location backend
mvn test
mvn package
Set-Location ../frontend
npm ci
npm run build
```

## 结构

- `backend/`：REST API、认证、MyBatis-Plus、Flyway
- `frontend/`：Vue 页面与组件
- `docs/architecture.md`：阶段 1 架构和认证风险
- `docs/er.md`：与实际迁移一致的 ER 图


## 阶段 2 时间规则

数据库中的 `DATETIME` 按 Asia/Shanghai 墙上时间写入。API 明确返回 `displayTimeZone: Asia/Shanghai`，前端将无偏移时间按 `+08:00` 解析并使用 Asia/Shanghai 格式化。24 小时内为“刚上架”，恰好 24 小时至不足 72 小时为“新上架”，恰好 72 小时起显示天数；最近确认超过 7 个完整日时显示状态较久提示。
## 局域网真机访问

Vite 固定监听 `0.0.0.0:5174`，手机与电脑连接同一局域网后，通过电脑的局域网 IPv4 加端口 5174 访问。前端 API 保持相对路径 `/api`，由 Vite 代理到仅绑定 `127.0.0.1:8080` 的 Spring Boot；不要把数据库或后端端口暴露给手机或公网。

当前电脑地址示例：`http://10.69.229.113:5174/`。IP 可能在重新连接网络后改变，可用 `ipconfig` 重新确认。
## 阶段 3 交接规则

时间继续使用 Asia/Shanghai 的本地墙上时间。发布必须提供公共地点和 1 至 5 个尚未开始的时段；历史物品可由发布者在详情页补充。意向以 `(item_id, user_id)` 唯一，重复申请更新原记录。发布者确认时使用事务和 `item.status = AVAILABLE AND current_handover_id IS NULL` 条件更新抢占，只有一个请求能进入 `RESERVED`。`handover` 保存地点和时段快照。

取消交接会保留历史、将物品恢复为 `AVAILABLE` 并取消已选意向；发布者完成交接后，物品与交接都进入 `COMPLETED`，详情和留言历史可读但禁止新意向、留言、时段和“确认还在”。广场默认显示可领取，可通过状态筛选进入“待交接”和“已送出归档”。
## 阶段 3.1 公开留言回复

V4 为 `item_comment` 增加可空 `parent_id` 自关联。原留言接口继续创建顶层问题；只有物品发布者可以在同一物品的顶层问题下回复，回复最多一层。`COMPLETED` 物品只展示历史问题与回复，禁止继续提交。留言及回复均在服务端去除首尾空格并限制为 1 至 500 字。
## 阶段 4 AI 定价

AI 定价仅在“标价”发布表单中出现。浏览器把物品名称、分类、原始描述和可选估价信息发送到已登录后端；后端使用 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`、`LLM_PROVIDER` 调用 OpenAI 兼容 Chat Completions。密钥不会返回前端或写入日志。

后端连接超时 5 秒、单次请求超时 45秒、不自动重试，并按登录用户限制 60 秒内最多一次估价。供应商 429、5xx、超时和非法结构会返回不含供应商原文的安全错误；用户仍能手工填写价格并发布。

模型结果只接受 `NEED_MORE_INFO` 或 `ESTIMATE`。金额范围为 0 至 1,000,000 元、最多两位小数，且必须满足 `minPrice <= recommendedPrice <= maxPrice`。前端“填入推荐价”只修改价格框，不会自动提交表单。结果未查询实时二手成交行情，仅供参考。