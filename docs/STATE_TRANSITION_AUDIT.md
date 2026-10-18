# 状态操作审计

本文记录 NM-E-07 对物品、领取意向和交接状态的复核。结论是保留现有三套最小状态机，不新增“下架”“过期”“删除”等缺少完整业务语义的状态。

## 1. 状态与允许转换

| 聚合 | 当前状态 | 允许操作 | 下一状态 | 操作者 |
| --- | --- | --- | --- | --- |
| 物品 | `AVAILABLE` | 发布者确认一个 `WAITING` 意向 | `RESERVED` | 发布者 |
| 物品 | `RESERVED` | 取消当前活动交接 | `AVAILABLE` | 交接任一方 |
| 物品 | `RESERVED` | 完成当前活动交接 | `COMPLETED` | 发布者 |
| 物品 | `COMPLETED` | 无状态转换 | — | — |
| 意向 | 不存在或 `CANCELLED` | 申请有效时段 | `WAITING` | 非发布者本人 |
| 意向 | `WAITING` | 主动取消 | `CANCELLED` | 申请人 |
| 意向 | `WAITING` | 发布者确认 | `CONFIRMED` | 发布者 |
| 意向 | `CONFIRMED` | 取消对应活动交接 | `CANCELLED` | 交接任一方 |
| 交接 | 不存在 | 确认等待意向 | `ACTIVE` | 发布者 |
| 交接 | `ACTIVE` | 取消 | `CANCELLED` | 交接任一方 |
| 交接 | `ACTIVE` | 完成 | `COMPLETED` | 发布者 |
| 交接 | `CANCELLED` / `COMPLETED` | 无状态转换 | — | — |

## 2. 非状态操作边界

- “确认还在”只允许发布者对 `AVAILABLE` 物品执行，仅刷新 `last_confirmed_at`，不制造新状态。
- 物品字段、图片与交接时段只允许在 `AVAILABLE` 且无 `WAITING` 意向时修改；它们不改变业务状态。
- `RESERVED` 和 `COMPLETED` 物品不可编辑、替换图片或重新确认还在。
- `COMPLETED` 物品的留言与回复只读；历史意向、时段和交接记录不删除。
- 取消活动交接会清空 `current_handover_id` 并恢复物品为 `AVAILABLE`；完成交接保留该引用指向已完成记录。

## 3. 并发和一致性

- 申请意向先锁定物品行并校验 `expectedItemVersion`；编辑和图片操作采用相同物品锁顺序。
- 确认、取消和完成均以期望旧状态作为条件更新，并要求影响行数为1；竞争失败统一返回 HTTP 409。
- 交接取消在同一事务内完成 `ACTIVE → CANCELLED`、`RESERVED → AVAILABLE`、`CONFIRMED → CANCELLED`。
- 交接完成在同一事务内完成 `ACTIVE → COMPLETED` 与 `RESERVED → COMPLETED`。
- V7 数据库约束限制合法枚举、终态时间字段、每件物品至多一个活动交接，以及物品、意向、时段、交接之间的归属关系。

## 4. HTTP 语义

- 资源不存在：404。
- 已登录但不是允许的操作者：403。
- 状态已经终结、前置状态不符、版本过期或并发竞争失败：409。
- 请求字段或资源归属参数本身无效：400。

状态错误不得静默成功，也不得把重复取消或重复完成当作幂等成功；调用方必须刷新后展示当前真实状态。
