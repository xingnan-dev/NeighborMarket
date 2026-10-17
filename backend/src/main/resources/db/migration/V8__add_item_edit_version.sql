ALTER TABLE item
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER current_handover_id,
    ADD CONSTRAINT chk_item_version CHECK (version >= 0);
