# AI 估价指标与隐私边界

> 任务：NM-F-02
> 日期：2026-10-19

## 1. 目标

AI 估价需要提供足够的成本、可用性和限流证据，同时不能把物品描述、Prompt、供应商响应、用户名或密钥写入指标与日志。本方案记录请求结果和耗时，为 Redis 限流复评及供应商成本分析提供基础数据，但不扩大 Actuator 的公开端点。

## 2. Micrometer 指标

| 指标 | 类型 | 标签 | 含义 |
| --- | --- | --- | --- |
| `neighbormarket.ai.pricing.requests` | Counter | `outcome` | 各结果/失败类别的调用次数 |
| `neighbormarket.ai.pricing.duration` | Timer | `outcome` | 从业务入口到成功或失败的总耗时 |

`outcome` 是固定低基数集合：

- `estimate`
- `need_more_info`
- `local_rate_limited`
- `provider_rate_limited`
- `timeout`
- `not_configured`
- `provider_unsupported`
- `provider_error`
- `request_failed`
- `invalid_response`
- `interrupted`
- `rejected_trade_type`
- `other_error`
- `unexpected_error`

指标标签禁止包含用户名、匿名用户键、物品名称、分类、描述、品牌型号、价格、请求 ID、供应商原始状态文本或异常消息。这样可避免敏感信息泄露和高基数指标失控。

当前 `management.endpoints.web.exposure.include` 继续只包含 `health,info`，不会匿名开放 `/actuator/metrics`。后续接入 Prometheus/OTLP 等受控采集器时，应通过专用网络和认证配置完成，不应为了查看指标扩大公共管理端点。

## 3. 结构化事件

每次估价结束记录一条 INFO 事件，仅包含：

```text
AI pricing completed; userKey=<24位十六进制> outcome=<固定类别> durationMs=<整数>
```

`userKey` 由进程启动时生成的32字节随机盐与用户名计算 SHA-256，再截取前12字节。它在单次进程生命周期内稳定，可辅助识别同一匿名用户的限流模式；重启后变化，不能跨部署长期跟踪，也不写入指标标签。

日志禁止记录：

- 原始用户名或昵称
- 请求 DTO、物品描述或任何 Prompt
- 供应商请求体、响应体或模型生成内容
- API Key、Authorization Header、Endpoint 查询参数
- 完整异常堆栈中的上游响应正文

## 4. 结果分类与成本解释

- `estimate` 与 `need_more_info` 表示供应商调用成功并通过本地 JSON 校验。
- `local_rate_limited` 和 `rejected_trade_type` 在调用供应商前结束，不应计入供应商调用成本。
- `not_configured` 和 `provider_unsupported` 不会发出供应商 HTTP 请求。
- `provider_rate_limited`、`provider_error`、`request_failed`、`timeout`、`invalid_response`、`interrupted` 可能已经产生供应商侧请求，应纳入失败成本观察。
- 当前供应商协议没有返回统一 token 用量字段，不能虚构 token 或人民币成本。若未来接收可信 usage 字段，必须先定义上限、单位和缺失语义，再增加低基数分布指标。

## 5. 告警与复评建议

本阶段不设置未经生产流量校准的硬编码告警阈值。具备真实流量后至少观察：

1. 成功率：`estimate + need_more_info` 占全部已尝试估价的比例。
2. 本地限流比例：判断60秒冷却是否影响正常使用。
3. 供应商限流、超时和无效响应比例：用于供应商可靠性与降级评估。
4. p50/p95/p99 耗时：判断45秒上限与前端反馈是否合理。
5. 单实例与总调用量：后端扩为多实例或需要严格成本上限时，重新执行 `REDIS_DECISION.md`。

AI 指标异常不得阻断手工发布；所有供应商失败仍必须保留手工填写价格的降级路径。

## 6. 自动化证据

`AiPricingTelemetryTest` 验证：

- 成功次数与 Timer 同时记录。
- 本地限流和供应商超时进入不同固定分类。
- 指标标签不包含用户名。
- 匿名用户键同盐同用户稳定、不同用户不同、不同进程盐不同，且不包含原始用户名。

`PricingServiceTest` 与 `PricingControllerSecurityTest` 继续验证既有限流、结果校验和认证边界。
