# NeighborMarket 项目演示数据包

这组脚本只适用于本地演示数据库。它会清理当前业务数据并重建 6 件自然的社区物品，覆盖发布、想要、交接、完成、留言和社区看板。

## 重要警告

- reset_demo_data.sql 会删除当前 item、pickup_slot、item_interest、handover 和 item_comment 业务数据。
- 执行前必须先完整备份 neighbor_market。
- 严禁在生产数据库执行。
- 脚本不会删除 app_user，但仍应先确认连接的数据库确实是本地演示库。
- 脚本只需要现有应用账号对 neighbor_market 项目库的表读写权限，不需要 CREATE ROUTINE、ALTER ROUTINE 或管理员权限。
- 清理顺序为：先删 item_comment 回复，再删顶层留言；随后清空 item.current_handover_id，再删 handover、item_interest、pickup_slot，最后删 item。
- 本轮只提供脚本和说明，不自动执行任何 SQL。

## 备份

在本机 PowerShell 或终端中执行，密码由客户端交互提示，不要把密码写在命令、文件或聊天中：

~~~powershell
mysqldump --host 127.0.0.1 --port 3307 --user neighbor_app --password --single-transaction --routines --triggers neighbor_market > neighbor_market_before_demo.sql
~~~

如果数据库地址、端口或用户名不同，请替换对应非敏感参数；不要把密码拼接在命令行中。

## 在 MySQL Workbench 中执行

1. 连接本机的 neighbor_market 演示数据库。
2. 打开并确认 reset_demo_data.sql 顶部警告。
3. 再次确认当前连接不是生产环境。
4. 在 Workbench 中执行整个脚本。
5. 脚本完成后打开 verify_demo_data.sql，只读执行并检查结果。

## 命令行执行

密码仍由客户端交互输入，不要使用带真实密码的参数：

~~~powershell
Get-Content -Raw scripts/demo/reset_demo_data.sql | mysql --host 127.0.0.1 --port 3307 --user neighbor_app --password neighbor_market
Get-Content -Raw scripts/demo/verify_demo_data.sql | mysql --host 127.0.0.1 --port 3307 --user neighbor_app --password neighbor_market
~~~

## 恢复备份

停止演示应用后，在本机执行：

~~~powershell
Get-Content -Raw neighbor_market_before_demo.sql | mysql --host 127.0.0.1 --port 3307 --user neighbor_app --password neighbor_market
~~~

恢复前后都要确认数据库名称和连接环境；密码只在客户端提示时输入。

## 演示数据内容

脚本使用 NOW(6) 相对计算时间，因此以后执行仍会生成“刚上架”“新上架”“已上架多日”、未来交接和合理完成耗时。再次执行会先清理并重建，不会重复累积。

图片路径使用 /demo/...，需要把对应文件放入 frontend/public/demo/。没有图片时现有前端仍会显示无图占位。
