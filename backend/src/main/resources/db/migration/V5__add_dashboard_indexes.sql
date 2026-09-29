CREATE INDEX idx_item_published ON item (published_at, id);

CREATE INDEX idx_handover_status_completed
    ON handover (status, completed_at, item_id);
