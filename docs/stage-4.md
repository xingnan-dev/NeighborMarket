# 阶段 4：真实 AI 定价参考

## 范围

只服务 `FIXED_PRICE`，没有数据库迁移；因此 ER 图无需变化。没有实现需求订阅、通知、AI 描述优化或自动发布。

## 协议与安全

实际配置为 Qwen/阿里云百炼工作空间端点。已通过 `/models` 200 响应确认 OpenAI 兼容协议，估价使用 `/chat/completions` 和 `response_format=json_object`。后端持有密钥，连接超时 5 秒、请求超时 20 秒、无自动重试；同一登录用户 10 秒冷却。错误响应不包含 Prompt、密钥或原始供应商响应。

## 结构校验

支持 `NEED_MORE_INFO(missingFields, questions)` 与 `ESTIMATE(minPrice,maxPrice,recommendedPrice,rationale,factors,uncertainty,confidence)`。金额非负、不超过 1,000,000 元、最多两位小数且推荐价位于区间内；文本和数组有长度/数量限制，confidence 只能是 LOW/MEDIUM/HIGH。

## 真实调用记录

- 信息不足输入：模型返回 NEED_MORE_INFO，并给出缺失字段与问题。
- “九阳电磁炉，使用约一年，功能正常，轻微使用痕迹，配件齐全”：使用代码中的最终系统提示返回 ESTIMATE，参考区间 50～150 元、推荐 100 元、置信度 LOW，并返回 8 项因素。结果只代表模型推断，未查询实时成交数据库。

## 自动验证

后端覆盖未登录、非标价拒绝、NEED_MORE_INFO、合法 ESTIMATE、非法 JSON、负数、顺序错误、金额上限、超时、429、供应商 5xx 和频率限制。前端纯函数测试确认“填入推荐价”只写价格、AI失败不进入普通发布载荷。