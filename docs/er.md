# NeighborMarket 实际 ER

本图逐项对照 Flyway V1～V8 与当前实体字段。可直接查看 [PNG 图](assets/NeighborMarket_ER.png) 或 [PDF 图](assets/NeighborMarket_ER.pdf)；两份资产由同一 PDF 源渲染，内容一致。

~~~mermaid
erDiagram
    APP_USER ||--o{ ITEM : publishes
    APP_USER ||--o{ ITEM_INTEREST : expresses
    APP_USER ||--o{ ITEM_COMMENT : writes
    ITEM ||--o{ PICKUP_SLOT : offers
    ITEM ||--o{ ITEM_INTEREST : receives
    PICKUP_SLOT ||--o{ ITEM_INTEREST : selected
    ITEM ||--o{ HANDOVER : history
    ITEM_INTEREST ||--o{ HANDOVER : creates
    APP_USER ||--o{ HANDOVER : publisher
    APP_USER ||--o{ HANDOVER : recipient
    ITEM ||--o{ ITEM_COMMENT : has
    ITEM_COMMENT ||--o{ ITEM_COMMENT : replies
    APP_USER { BIGINT id PK VARCHAR username UK VARCHAR password_hash VARCHAR nickname DATETIME created_at }
    ITEM { BIGINT id PK BIGINT publisher_id FK VARCHAR title VARCHAR description VARCHAR category VARCHAR trade_type DECIMAL price VARCHAR image_url VARCHAR status DATETIME published_at DATETIME last_confirmed_at DATETIME completed_at VARCHAR pickup_location BIGINT current_handover_id FK BIGINT version }
    PICKUP_SLOT { BIGINT id PK BIGINT item_id FK DATETIME start_at DATETIME end_at BOOLEAN active DATETIME created_at }
    ITEM_INTEREST { BIGINT id PK BIGINT item_id FK BIGINT user_id FK BIGINT slot_id FK VARCHAR status DATETIME created_at DATETIME updated_at }
    HANDOVER { BIGINT id PK BIGINT item_id FK BIGINT publisher_id FK BIGINT recipient_id FK BIGINT interest_id FK DATETIME start_at DATETIME end_at VARCHAR location_snapshot VARCHAR status BIGINT active_item_id UK_GENERATED DATETIME created_at DATETIME cancelled_at DATETIME completed_at }
    ITEM_COMMENT { BIGINT id PK BIGINT item_id FK BIGINT user_id FK BIGINT parent_id FK VARCHAR content DATETIME created_at }
~~~

## 约束与索引

- item_interest(item_id, user_id) 唯一，重复申请会更新原意向。
- item_comment.parent_id 可为空；为空是顶层留言，非空时引用同表顶层留言。服务层只允许一层回复，且只有发布者可回复。
- item.current_handover_id 引用当前交接；取消交接时恢复 AVAILABLE，完成时物品和交接均进入 COMPLETED。
- pickup_slot 的结束时间必须晚于开始时间；应用层限制每件物品 1～5 个未来有效时段。重配时旧时段改为 inactive 而不删除，使已取消或历史意向的外键仍然有效。
- V7 使用 CHECK 限制 item、item_interest、handover 的状态集合，并约束 ACTIVE/CANCELLED/COMPLETED 对应的交接终态时间字段。
- V7 的 handover.active_item_id 是仅 ACTIVE 状态返回 item_id 的生成列；唯一索引保证每件物品最多一条 ACTIVE 交接。
- V7 复合外键保证：意向选择的时段属于同一物品；交接关联的意向属于同一物品和领取人；交接发布者是物品发布者；item.current_handover_id 指向本物品的交接。
- V8 增加 `item.version BIGINT NOT NULL DEFAULT 0` 与非负 CHECK。编辑和领取申请携带 `expectedVersion`，并在同一物品行锁顺序下竞争，防止旧页面覆盖更新。
- V1～V4 创建表、基础约束和工作流字段；V5 增加看板索引；V6 保留时段历史；V7 强化交易一致性；V8 增加物品乐观并发版本。

## 图中字段与约束边界

- 图中展示所有业务表、字段、直接外键、主要唯一键、状态约束与 V8 版本字段。
- 普通查询索引未全部画在线条上，避免把访问路径误解为实体关系；完整索引以 Flyway 迁移为准。
- `active_item_id` 是仅在交接状态为 `ACTIVE` 时返回 `item_id` 的存储生成列，唯一键使每件物品最多存在一条 ACTIVE 交接。
- `current_handover_id` 可为空；V7 的 `(current_handover_id, id)` 复合外键保证当前交接属于同一物品。
- `parent_id` 可为空并自关联留言；数据库允许自关联，服务层进一步限制只能由发布者创建一层回复。
