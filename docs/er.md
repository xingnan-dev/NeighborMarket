# 阶段 3 实际 ER 图

以下字段与 V1、V2、V3 迁移一致。

```mermaid
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
```

关键约束：`item_comment.parent_id` 为空表示顶层留言，非空时自关联同表；服务端限制最多一层且只能由物品发布者回复。时段结束晚于开始；`item_interest(item_id,user_id)` 唯一；当前有效交接由 `item.current_handover_id` 引用，并由确认事务的条件更新保证单一；handover 永久保存地点/时间快照与取消、完成历史。