-- Abort before any MySQL DDL when existing rows violate the invariants added below.
-- MySQL auto-commits DDL, so running every compatibility check first avoids a
-- partially applied migration that cannot be retried safely.
SET @nm_v7_incompatible_rows =
    (SELECT COUNT(*) FROM item
        WHERE status NOT IN ('AVAILABLE', 'RESERVED', 'COMPLETED'))
    + (SELECT COUNT(*) FROM item_interest
        WHERE status NOT IN ('WAITING', 'CONFIRMED', 'CANCELLED'))
    + (SELECT COUNT(*) FROM handover
        WHERE status NOT IN ('ACTIVE', 'CANCELLED', 'COMPLETED')
           OR NOT (
               (status = 'ACTIVE' AND cancelled_at IS NULL AND completed_at IS NULL)
               OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
               OR (status = 'COMPLETED' AND cancelled_at IS NULL AND completed_at IS NOT NULL)
           ))
    + (SELECT COUNT(*) FROM (
           SELECT item_id
           FROM handover
           WHERE status = 'ACTIVE'
           GROUP BY item_id
           HAVING COUNT(*) > 1
       ) AS duplicate_active_handover)
    + (SELECT COUNT(*)
       FROM item_interest ii
       JOIN pickup_slot ps ON ps.id = ii.slot_id
       WHERE ps.item_id <> ii.item_id)
    + (SELECT COUNT(*)
       FROM handover h
       JOIN item_interest ii ON ii.id = h.interest_id
       WHERE ii.item_id <> h.item_id OR ii.user_id <> h.recipient_id)
    + (SELECT COUNT(*)
       FROM handover h
       JOIN item i ON i.id = h.item_id
       WHERE i.publisher_id <> h.publisher_id)
    + (SELECT COUNT(*)
       FROM item i
       JOIN handover h ON h.id = i.current_handover_id
       WHERE h.item_id <> i.id);

SET @nm_v7_guard_sql = IF(
    @nm_v7_incompatible_rows = 0,
    'SELECT 1',
    'SELECT * FROM nm_v7_blocked_inconsistent_existing_data'
);
PREPARE nm_v7_guard_statement FROM @nm_v7_guard_sql;
EXECUTE nm_v7_guard_statement;
DEALLOCATE PREPARE nm_v7_guard_statement;

ALTER TABLE item
    ADD CONSTRAINT chk_item_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'COMPLETED')),
    ADD UNIQUE KEY uq_item_id_publisher (id, publisher_id);

ALTER TABLE pickup_slot
    ADD UNIQUE KEY uq_pickup_slot_id_item (id, item_id);

ALTER TABLE item_interest
    ADD CONSTRAINT chk_item_interest_status
        CHECK (status IN ('WAITING', 'CONFIRMED', 'CANCELLED')),
    ADD UNIQUE KEY uq_interest_id_item_user (id, item_id, user_id),
    ADD CONSTRAINT fk_interest_slot_same_item
        FOREIGN KEY (slot_id, item_id) REFERENCES pickup_slot (id, item_id);

ALTER TABLE handover
    ADD COLUMN active_item_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN item_id ELSE NULL END) STORED,
    ADD CONSTRAINT chk_handover_status
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'COMPLETED')),
    ADD CONSTRAINT chk_handover_terminal_fields
        CHECK (
            (status = 'ACTIVE' AND cancelled_at IS NULL AND completed_at IS NULL)
            OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND cancelled_at IS NULL AND completed_at IS NOT NULL)
        ),
    ADD UNIQUE KEY uq_handover_active_item (active_item_id),
    ADD UNIQUE KEY uq_handover_id_item (id, item_id),
    ADD CONSTRAINT fk_handover_interest_same_item_user
        FOREIGN KEY (interest_id, item_id, recipient_id)
        REFERENCES item_interest (id, item_id, user_id),
    ADD CONSTRAINT fk_handover_publisher_same_item
        FOREIGN KEY (item_id, publisher_id) REFERENCES item (id, publisher_id);

ALTER TABLE item
    ADD CONSTRAINT fk_item_current_handover_same_item
        FOREIGN KEY (current_handover_id, id) REFERENCES handover (id, item_id);
