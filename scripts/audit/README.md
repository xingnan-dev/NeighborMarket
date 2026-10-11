# 数据一致性只读审计

`data_consistency_audit.sql` 用于检查 NeighborMarket 的历史数据和状态机一致性。健康数据库应返回 **0 行**。

脚本只有一条 `SELECT ... UNION ALL` 查询，不会创建对象、修改数据或执行自动修复。建议使用只有 `SELECT` 权限的数据库账号运行，并在处理任何异常前单独备份。

示例：

```powershell
mysql --host=127.0.0.1 --port=3306 --user=<只读用户> --password neighbor_market --table < scripts/audit/data_consistency_audit.sql
```

输出字段：

- `issue_code`：稳定的问题代码，便于检索和记录。
- `severity`：P0 表示核心交易或引用一致性风险；P1 表示权限/留言层级等业务异常。
- `entity_type`、`entity_id`：主要异常记录。
- `related_id`：相关物品、时段、意向、交接或父留言。
- `details`：定位信息，不包含密码或其他密钥。

处理原则：

1. 先停止相关写入并备份，不要直接把审计 SQL 改成 UPDATE/DELETE。
2. 根据 `issue_code` 查询关联记录，确认是历史数据、人工写入还是程序缺陷。
3. 编写一次性修复方案并在数据库副本验证；生产修复需要单独评审和授权。
4. 修复后重新执行审计，必须确认目标问题消失且未产生新问题。
5. 如果 V7 迁移因兼容性守卫失败，修正数据后执行 Flyway `repair`，再重新 `migrate`；不要修改 V7 文件或清空历史数据。

V7 已在数据库层阻止大部分状态、ACTIVE 唯一性和跨表错配问题。本脚本仍保留这些检查，用于升级前数据库、曾关闭外键检查的数据库，以及人工核验。
