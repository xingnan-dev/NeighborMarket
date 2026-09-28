UPDATE item
SET last_confirmed_at = published_at
WHERE last_confirmed_at IS NULL;

ALTER TABLE item
    MODIFY COLUMN last_confirmed_at DATETIME(6) NOT NULL;

CREATE INDEX idx_item_last_confirmed ON item (status, last_confirmed_at, id);