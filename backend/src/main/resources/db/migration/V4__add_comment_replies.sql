ALTER TABLE item_comment
    ADD COLUMN parent_id BIGINT NULL AFTER user_id,
    ADD CONSTRAINT fk_item_comment_parent
        FOREIGN KEY (parent_id) REFERENCES item_comment(id),
    ADD INDEX idx_item_comment_parent_created (parent_id, created_at);