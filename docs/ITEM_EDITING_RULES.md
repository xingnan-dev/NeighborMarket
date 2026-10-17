# 物品编辑规则与并发语义

## 1. 目标与边界

物品编辑必须保证发布者不能在领取人表达意向后悄然改变物品、价格或交接条件，也不能破坏已有意向与交接历史。本设计不新增状态、审核、通知或版本历史业务；图片上传由 NM-E-04～06 单独设计。

当前代码证据：

- `ItemStatus` 仅有 `AVAILABLE` / `RESERVED` / `COMPLETED`。
- `HandoverWorkflowService.confirm` 使用条件 UPDATE 将物品从 `AVAILABLE` 改为 `RESERVED`，交接表保存地点和时段快照。
- `PickupOptionService.configure` 已拒绝 `RESERVED` / `COMPLETED` 和存在 `WAITING` 意向时的时段修改，并通过 `active=false` 保留旧时段。
- 当前 `item` 无版本列，编辑功能和编辑 API 均未实现。

## 2. 可编辑规则

| 物品状态 | 意向情况 | 标题/描述/分类 | 交易方式/价格 | 地点/时段 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| AVAILABLE | 无 WAITING 意向 | 允许 | 允许 | 允许 | 可完整编辑 |
| AVAILABLE | 存在 WAITING 意向 | 禁止 | 禁止 | 禁止 | 返回 409，避免意向提交后更换条件 |
| AVAILABLE | 仅有 CANCELLED 历史 | 允许 | 允许 | 允许 | 旧时段保留为 inactive，不删历史 |
| RESERVED | 已有 ACTIVE 交接 | 禁止 | 禁止 | 禁止 | 交接期间冻结物品条件 |
| COMPLETED | 已归档 | 禁止 | 禁止 | 禁止 | 归档记录只读 |

编辑不改变 `published_at`、`last_confirmed_at`、`status`、`completed_at`、`current_handover_id` 或发布者。`last_confirmed_at` 只由“确认还在”操作更新，不应被滥用为编辑版本。

## 3. API 契约

计划新增：

```text
PUT /api/items/{id}
```

请求为完整替换契约，包含 `title`、`description`、`category`、`tradeType`、`price`、`pickupLocation`、`slots` 和必填 `expectedVersion`。校验与发布接口保持一致，价格继续复用 `ItemService.normalizePrice`。成功返回最新 `ItemView`及递增后的 `version`。

错误语义：

- 404：物品不存在。
- 403：当前用户不是发布者。
- 409：物品已非 AVAILABLE、存在 WAITING 意向、版本已变化，或与确认/取消/完成并发竞争失败。
- 400：字段、价格或时段不合法。

前端遇到 409 必须保留用户输入并提示刷新后重新核对，不能自动覆盖新版本。

## 4. 版本与并发协议

1. 通过新 Flyway 迁移向 `item` 增加 `version BIGINT NOT NULL DEFAULT 0`；不修改已执行的 V1～V7。
2. 编辑时在一个事务内锁定物品行，重新检查发布者、状态、`expectedVersion` 和 WAITING 意向；物品字段、旧时段失效和新时段插入必须同事务提交。
3. 物品 UPDATE 仍使用 `id + publisher_id + status=AVAILABLE + version=expectedVersion` 条件并检查影响行数为1；成功时 `version=version+1`。行锁与条件 UPDATE 分别提供业务序列化和最后防线。
4. 仅给编辑加版本不足以防止“基于旧价格申请”。意向申请也必须携带详情页读到的 `expectedItemVersion`，并与编辑使用相同的物品行锁顺序。版本不同时返回409，要求用户重新核对。
5. 确认领取人与编辑也必须按“物品行→意向行”的固定顺序获取锁，避免新增死锁。现有条件 UPDATE 影响行数检查保留。

## 5. 时段与历史一致性

- 编辑时段时不删除旧 `pickup_slot`，只将当前 active 时段改为 false，然后插入新时段。
- 因存在 WAITING 意向时禁止编辑，不会使等待意向指向失效时段。CANCELLED 意向仍可通过外键追溯原时段。
- `handover.location_snapshot/start_at/end_at` 永远不随物品后续变化。RESERVED/COMPLETED 禁止编辑进一步保护当时交易语义。
- 新时段仍限制1～5个、必须尚未开始且结束晚于开始。

## 6. 实现分解与验收

NM-E-02 后端建议拆分为：

1. V8 `item.version` 迁移、实体/列表 DTO 映射和空库迁移测试。
2. `UpdateItem` DTO/命令、共享发布校验和 `PUT /api/items/{id}`。
3. 物品行锁、条件 UPDATE、时段替换事务与日志。
4. 意向申请的 `expectedItemVersion` 契约和锁顺序改造。
5. MySQL 集成测试：越权、各状态、WAITING/CANCELLED 意向、版本冲突、编辑与申请/确认并发、时段历史、中途失败回滚。

验收必须同时断言 HTTP 结果、物品最终版本/状态、active 时段集合、历史时段可追溯性、意向与交接数量，以及事务失败后无部分更新。
