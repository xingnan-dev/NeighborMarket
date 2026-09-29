-- Read-only verification for the local phase 6 demo dataset.
-- This file contains SELECT statements only.

SELECT 'ITEM_COUNT_AND_STATUS' AS check_name, COUNT(*) AS item_count FROM item;

SELECT status, COUNT(*) AS item_count
FROM item
GROUP BY status
ORDER BY status;

SELECT trade_type, COUNT(*) AS item_count
FROM item
GROUP BY trade_type
ORDER BY trade_type;

SELECT id, title, status, trade_type, price, published_at, completed_at, pickup_location, image_url
FROM item
ORDER BY id;

SELECT i.id, i.title, COUNT(DISTINCT ii.user_id) AS valid_interest_count
FROM item i
LEFT JOIN item_interest ii
    ON ii.item_id = i.id
   AND ii.status IN ('WAITING', 'CONFIRMED')
WHERE i.status <> 'COMPLETED'
GROUP BY i.id, i.title
ORDER BY valid_interest_count DESC, i.id ASC;

SELECT h.id, h.item_id, i.title, h.status AS handover_status, i.status AS item_status,
       h.completed_at AS handover_completed_at, i.completed_at AS item_completed_at,
       CASE
           WHEN h.status = 'COMPLETED' AND i.status = 'COMPLETED'
                AND h.completed_at = i.completed_at THEN 'OK'
           WHEN h.status <> 'COMPLETED' AND i.status <> 'COMPLETED'
                AND h.completed_at IS NULL AND i.completed_at IS NULL THEN 'OK'
           ELSE 'CHECK'
       END AS completion_consistency
FROM handover h
JOIN item i ON i.id = h.item_id
ORDER BY h.id;

SELECT i.id AS item_id, i.title, i.current_handover_id,
       h.id AS handover_id, h.status
FROM item i
LEFT JOIN handover h ON h.id = i.current_handover_id
WHERE i.current_handover_id IS NOT NULL
ORDER BY i.id;

SELECT child.id AS reply_id, child.item_id, child.parent_id,
       parent.id AS parent_comment_id,
       CASE WHEN parent.id IS NOT NULL AND parent.item_id = child.item_id THEN 'OK' ELSE 'CHECK' END AS parent_relation
FROM item_comment child
LEFT JOIN item_comment parent ON parent.id = child.parent_id
WHERE child.parent_id IS NOT NULL
ORDER BY child.id;

SELECT COUNT(*) AS non_demo_image_paths
FROM item
WHERE image_url NOT LIKE '/demo/%';

SELECT id, title
FROM item
WHERE LOWER(title) REGEXP 'test|阶段|验收|并发|测试|时间戳';

SELECT COUNT(*) AS published_this_month
FROM item
WHERE published_at >= DATE_FORMAT(NOW(), '%Y-%m-01')
  AND published_at < DATE_ADD(DATE_FORMAT(NOW(), '%Y-%m-01'), INTERVAL 1 MONTH);

SELECT COUNT(*) AS completed_this_month
FROM handover
WHERE status = 'COMPLETED'
  AND completed_at >= DATE_FORMAT(NOW(), '%Y-%m-01')
  AND completed_at < DATE_ADD(DATE_FORMAT(NOW(), '%Y-%m-01'), INTERVAL 1 MONTH);

SELECT COUNT(*) AS available_now FROM item WHERE status = 'AVAILABLE';

SELECT i.id, i.title,
       TIMESTAMPDIFF(SECOND, i.published_at, h.completed_at) AS duration_seconds,
       h.completed_at
FROM handover h
JOIN item i ON i.id = h.item_id
WHERE h.status = 'COMPLETED'
  AND i.status = 'COMPLETED'
  AND h.completed_at IS NOT NULL
ORDER BY duration_seconds ASC, h.completed_at ASC, i.id ASC
LIMIT 1;

SELECT i.id, i.title, i.status, COUNT(DISTINCT ii.user_id) AS interest_count
FROM item i
JOIN item_interest ii ON ii.item_id = i.id
WHERE i.status <> 'COMPLETED'
  AND ii.status IN ('WAITING', 'CONFIRMED')
GROUP BY i.id, i.title, i.status, i.published_at
ORDER BY interest_count DESC, i.published_at ASC, i.id ASC;
