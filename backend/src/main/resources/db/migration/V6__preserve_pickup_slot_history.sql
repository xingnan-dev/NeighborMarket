ALTER TABLE pickup_slot
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER end_at;

CREATE INDEX idx_pickup_slot_item_active_start
    ON pickup_slot (item_id, active, start_at);
