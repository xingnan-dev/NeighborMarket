# 阶段 3：核心交接闭环

状态流：`AVAILABLE -> RESERVED -> COMPLETED`，取消有效交接时 `RESERVED -> AVAILABLE`。意向状态为 `WAITING / CONFIRMED / CANCELLED`；交接状态为 `ACTIVE / CANCELLED / COMPLETED`。

服务端强制校验登录、归属关系、物品状态、时段归属和未来性。发布者不能申请自己的物品；只有发布者可查看完整意向、确认和完成；只有交接双方可查看或取消交接。公开留言长度 1 至 500 字，COMPLETED 只读。

并发确认通过事务内对 item 执行带 `AVAILABLE` 与空 current_handover_id 条件的原子更新实现；失败请求返回 409，不创建第二条有效交接。地点和时间复制到 handover，后续物品配置不会改变历史。

自动化验证包括后端测试/Maven 打包和前端生产构建。真实 MySQL 迁移、双账号浏览器流程、并发请求和重启持久性需要在本机密码环境变量仍存在的后端重启后执行并记录。
## 2026-09-28 实际验收记录

- V3 已由真实 MySQL 后端应用；时段与归档 API 返回 200。
- 意向主动取消后重新申请复用同一 interest id；交接取消后 item 恢复 AVAILABLE；published_at 未改变。
- 两个独立发布者会话同时确认同一意向：响应为 200/409，ACTIVE 交接数为 1。
- 发布者完成后 handover 与 item 均为 COMPLETED，归档筛选可查询。
- 未登录意向/留言 401；发布者申请自己的物品 403；非发布者读取完整意向 403；领取人完成交接 403；COMPLETED 下意向、留言、确认还在均 409；过去时段 400。
- 后端 10 项测试通过，Maven package 与前端生产构建通过。
- 后端重启后 item 6 保持 AVAILABLE、item 7 保持 COMPLETED，handover 1 保持 CANCELLED、handover 2 保持 COMPLETED；时段和归档查询仍存在。
- 未验证：第三方用户查看/取消交接的真实 API 验证（目前只有两个演示账号且正好是交接双方；服务端所有权校验已实现，但没有增加第三个演示账号）。
## 2026-09-29 交接权限补充验证

自动化测试创建纯测试身份 `test_outsider`（不是正式演示账号）。该身份既不是发布者也不是领取人：读取交接详情、取消交接、完成交接均返回 403，且不会执行 handover 或 item 更新。

状态引用断言：取消交接的 item 更新包含 `current_handover_id = NULL`，恢复后的实体引用为空；完成交接的 item 更新以对应 `current_handover_id` 为条件且不覆盖该字段，完成后的实体仍指向已完成 handover。并发失败路径验证条件抢占失败后不插入 handover，因此同一物品不会产生第二条有效交接。