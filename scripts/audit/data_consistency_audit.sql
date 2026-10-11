-- NeighborMarket read-only data consistency audit.
-- Expected healthy result: zero rows. This script never repairs or mutates data.

SELECT 'ITEM_INVALID_STATUS' AS issue_code, 'P0' AS severity,
       'item' AS entity_type, CAST(i.id AS CHAR) AS entity_id,
       NULL AS related_id, CONCAT('status=', i.status) AS details
FROM item i
WHERE i.status NOT IN ('AVAILABLE', 'RESERVED', 'COMPLETED')

UNION ALL
SELECT 'INTEREST_INVALID_STATUS', 'P0', 'item_interest', CAST(ii.id AS CHAR),
       CAST(ii.item_id AS CHAR), CONCAT('status=', ii.status)
FROM item_interest ii
WHERE ii.status NOT IN ('WAITING', 'CONFIRMED', 'CANCELLED')

UNION ALL
SELECT 'HANDOVER_INVALID_STATUS', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.item_id AS CHAR), CONCAT('status=', h.status)
FROM handover h
WHERE h.status NOT IN ('ACTIVE', 'CANCELLED', 'COMPLETED')

UNION ALL
SELECT 'HANDOVER_TERMINAL_FIELDS_INVALID', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.item_id AS CHAR), CONCAT('status=', h.status)
FROM handover h
WHERE NOT (
    (h.status = 'ACTIVE' AND h.cancelled_at IS NULL AND h.completed_at IS NULL)
    OR (h.status = 'CANCELLED' AND h.cancelled_at IS NOT NULL AND h.completed_at IS NULL)
    OR (h.status = 'COMPLETED' AND h.cancelled_at IS NULL AND h.completed_at IS NOT NULL)
)

UNION ALL
SELECT 'MULTIPLE_ACTIVE_HANDOVERS', 'P0', 'item', CAST(h.item_id AS CHAR),
       NULL, CONCAT('active_count=', COUNT(*))
FROM handover h
WHERE h.status = 'ACTIVE'
GROUP BY h.item_id
HAVING COUNT(*) > 1

UNION ALL
SELECT 'ORPHAN_ITEM_PUBLISHER', 'P0', 'item', CAST(i.id AS CHAR),
       CAST(i.publisher_id AS CHAR), 'publisher user does not exist'
FROM item i
LEFT JOIN app_user u ON u.id = i.publisher_id
WHERE u.id IS NULL

UNION ALL
SELECT 'ORPHAN_PICKUP_SLOT_ITEM', 'P0', 'pickup_slot', CAST(ps.id AS CHAR),
       CAST(ps.item_id AS CHAR), 'item does not exist'
FROM pickup_slot ps
LEFT JOIN item i ON i.id = ps.item_id
WHERE i.id IS NULL

UNION ALL
SELECT 'ORPHAN_INTEREST_REFERENCE', 'P0', 'item_interest', CAST(ii.id AS CHAR),
       CAST(ii.item_id AS CHAR), 'item, user, or slot does not exist'
FROM item_interest ii
LEFT JOIN item i ON i.id = ii.item_id
LEFT JOIN app_user u ON u.id = ii.user_id
LEFT JOIN pickup_slot ps ON ps.id = ii.slot_id
WHERE i.id IS NULL OR u.id IS NULL OR ps.id IS NULL

UNION ALL
SELECT 'ORPHAN_HANDOVER_REFERENCE', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.item_id AS CHAR), 'item, publisher, recipient, or interest does not exist'
FROM handover h
LEFT JOIN item i ON i.id = h.item_id
LEFT JOIN app_user p ON p.id = h.publisher_id
LEFT JOIN app_user r ON r.id = h.recipient_id
LEFT JOIN item_interest ii ON ii.id = h.interest_id
WHERE i.id IS NULL OR p.id IS NULL OR r.id IS NULL OR ii.id IS NULL

UNION ALL
SELECT 'ORPHAN_ITEM_CURRENT_HANDOVER', 'P0', 'item', CAST(i.id AS CHAR),
       CAST(i.current_handover_id AS CHAR), 'current handover does not exist'
FROM item i
LEFT JOIN handover h ON h.id = i.current_handover_id
WHERE i.current_handover_id IS NOT NULL AND h.id IS NULL

UNION ALL
SELECT 'ORPHAN_COMMENT_REFERENCE', 'P1', 'item_comment', CAST(c.id AS CHAR),
       CAST(c.parent_id AS CHAR), 'item, author, or parent comment does not exist'
FROM item_comment c
LEFT JOIN item i ON i.id = c.item_id
LEFT JOIN app_user u ON u.id = c.user_id
LEFT JOIN item_comment parent ON parent.id = c.parent_id
WHERE i.id IS NULL OR u.id IS NULL OR (c.parent_id IS NOT NULL AND parent.id IS NULL)

UNION ALL
SELECT 'INTEREST_SLOT_ITEM_MISMATCH', 'P0', 'item_interest', CAST(ii.id AS CHAR),
       CAST(ii.slot_id AS CHAR), CONCAT('interest_item=', ii.item_id, ', slot_item=', ps.item_id)
FROM item_interest ii
JOIN pickup_slot ps ON ps.id = ii.slot_id
WHERE ii.item_id <> ps.item_id

UNION ALL
SELECT 'HANDOVER_INTEREST_MISMATCH', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.interest_id AS CHAR),
       CONCAT('handover_item/user=', h.item_id, '/', h.recipient_id,
              ', interest_item/user=', ii.item_id, '/', ii.user_id)
FROM handover h
JOIN item_interest ii ON ii.id = h.interest_id
WHERE h.item_id <> ii.item_id OR h.recipient_id <> ii.user_id

UNION ALL
SELECT 'HANDOVER_PUBLISHER_MISMATCH', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.publisher_id AS CHAR),
       CONCAT('handover_publisher=', h.publisher_id, ', item_publisher=', i.publisher_id)
FROM handover h
JOIN item i ON i.id = h.item_id
WHERE h.publisher_id <> i.publisher_id

UNION ALL
SELECT 'ITEM_CURRENT_HANDOVER_MISMATCH', 'P0', 'item', CAST(i.id AS CHAR),
       CAST(i.current_handover_id AS CHAR),
       CONCAT('item_id=', i.id, ', handover_item=', h.item_id)
FROM item i
JOIN handover h ON h.id = i.current_handover_id
WHERE i.id <> h.item_id

UNION ALL
SELECT 'ITEM_HANDOVER_STATE_MISMATCH', 'P0', 'item', CAST(i.id AS CHAR),
       CAST(i.current_handover_id AS CHAR),
       CONCAT('item_status=', i.status, ', handover_status=', COALESCE(h.status, 'NULL'))
FROM item i
LEFT JOIN handover h ON h.id = i.current_handover_id
WHERE (i.status = 'AVAILABLE' AND (i.current_handover_id IS NOT NULL OR i.completed_at IS NOT NULL))
   OR (i.status = 'RESERVED' AND (
          i.current_handover_id IS NULL OR COALESCE(h.status, '') <> 'ACTIVE'
      ))
   OR (i.status = 'COMPLETED' AND (
          i.current_handover_id IS NULL OR COALESCE(h.status, '') <> 'COMPLETED'
          OR i.completed_at IS NULL
      ))
   OR (i.status <> 'COMPLETED' AND i.completed_at IS NOT NULL)

UNION ALL
SELECT 'ACTIVE_HANDOVER_NOT_CURRENT', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.item_id AS CHAR),
       CONCAT('item_status=', COALESCE(i.status, 'NULL'),
              ', current_handover_id=', COALESCE(CAST(i.current_handover_id AS CHAR), 'NULL'))
FROM handover h
LEFT JOIN item i ON i.id = h.item_id
WHERE h.status = 'ACTIVE'
  AND (i.id IS NULL OR i.status <> 'RESERVED' OR i.current_handover_id <> h.id
       OR i.current_handover_id IS NULL)

UNION ALL
SELECT 'ACTIVE_OR_COMPLETED_INTEREST_MISMATCH', 'P0', 'handover', CAST(h.id AS CHAR),
       CAST(h.interest_id AS CHAR), CONCAT('interest_status=', COALESCE(ii.status, 'NULL'))
FROM handover h
LEFT JOIN item_interest ii ON ii.id = h.interest_id
WHERE h.status IN ('ACTIVE', 'COMPLETED')
  AND (ii.id IS NULL OR ii.status <> 'CONFIRMED')

UNION ALL
SELECT 'PUBLISHER_SELF_INTEREST', 'P1', 'item_interest', CAST(ii.id AS CHAR),
       CAST(ii.item_id AS CHAR), 'publisher applied for own item'
FROM item_interest ii
JOIN item i ON i.id = ii.item_id
WHERE ii.user_id = i.publisher_id

UNION ALL
SELECT 'COMMENT_PARENT_ITEM_MISMATCH', 'P1', 'item_comment', CAST(c.id AS CHAR),
       CAST(c.parent_id AS CHAR),
       CONCAT('comment_item=', c.item_id, ', parent_item=', parent.item_id)
FROM item_comment c
JOIN item_comment parent ON parent.id = c.parent_id
WHERE c.item_id <> parent.item_id

UNION ALL
SELECT 'COMMENT_REPLY_DEPTH_INVALID', 'P1', 'item_comment', CAST(c.id AS CHAR),
       CAST(c.parent_id AS CHAR), 'reply points to another reply'
FROM item_comment c
JOIN item_comment parent ON parent.id = c.parent_id
WHERE parent.parent_id IS NOT NULL

ORDER BY severity, issue_code, entity_type, entity_id;
