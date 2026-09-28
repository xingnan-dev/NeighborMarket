# 阶段 3.1：公开留言一层回复

## 数据与接口

V4 只修改 `item_comment`，增加可空 `parent_id` 外键和 `(parent_id, created_at)` 索引。`parent_id IS NULL` 是顶层问题；非空记录是发布者回复。V1、V2、V3 保持不变。

- `POST /api/items/{itemId}/comments`：登录用户创建顶层留言。
- `POST /api/items/{itemId}/comments/{commentId}/replies`：仅物品发布者回复顶层留言。
- `GET /api/items/{itemId}/comments`：公开读取按创建时间排序的留言与回复。

服务端校验物品状态、发布者身份、父留言存在、父留言属于当前物品且自身必须是顶层留言。内容 trim 后为 1 至 500 字。没有删除、编辑、点赞、实时聊天或多层回复。

## 自动化状态

后端 15 项测试通过，Maven package 与前端生产构建通过。V4 已在真实 MySQL 执行。

真实双账号记录：item 10 中 neighbor_b 创建顶层留言 2，neighbor_a 创建回复 3，回复 parent_id=2；内容首尾空格已移除。未登录回复 401、非发布者回复 403、回复一条回复 400、跨物品回复 400。物品完成后顶层留言和回复仍公开可读，新增留言和回复均返回 409。

后端再次重启后：item 10 保持 COMPLETED，顶层留言 2 与回复 3 均存在，回复 parent_id 仍为 2；未登录回复仍返回 401。

待人工验证：手机端真实布局与交互。