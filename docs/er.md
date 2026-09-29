# NeighborMarket 实际 ER

下图对照 V1～V5 迁移和当前实体字段整理；V5 仅增加索引。

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
    ITEM { BIGINT id PK BIGINT publisher_id FK VARCHAR title VARCHAR description VARCHAR category VARCHAR trade_type DECIMAL price VARCHAR image_url VARCHAR status DATETIME published_at DATETIME last_confirmed_at DATETIME completed_at VARCHAR pickup_location BIGINT current_handover_id FK }
    PICKUP_SLOT { BIGINT id PK BIGINT item_id FK DATETIME start_at DATETIME end_at DATETIME created_at }
    ITEM_INTEREST { BIGINT id PK BIGINT item_id FK BIGINT user_id FK BIGINT slot_id FK VARCHAR status DATETIME created_at DATETIME updated_at }
    HANDOVER { BIGINT id PK BIGINT item_id FK BIGINT publisher_id FK BIGINT recipient_id FK BIGINT interest_id FK DATETIME start_at DATETIME end_at VARCHAR location_snapshot VARCHAR status DATETIME created_at DATETIME cancelled_at DATETIME completed_at }
    ITEM_COMMENT { BIGINT id PK BIGINT item_id FK BIGINT user_id FK BIGINT parent_id FK VARCHAR content DATETIME created_at }
~~~

## 约束与索引

- item_interest(item_id, user_id) 唯一，重复申请会更新原意向。
- item_comment.parent_id 可为空；为空是顶层留言，非空时引用同表顶层留言。服务层只允许一层回复，且只有发布者可回复。
- item.current_handover_id 引用当前交接；取消交接时恢复 AVAILABLE，完成时物品和交接均进入 COMPLETED。
- pickup_slot 的结束时间必须晚于开始时间；应用层限制每件物品 1～5 个未来时段。
- V1～V4 创建表、约束和工作流字段；V5 增加 item.published_at 与 handover(status, completed_at, item_id) 相关索引，用于看板查询。
