-- ============================================================================
-- !!! LOCAL DEMO ONLY !!!
-- This script DELETES current business data before rebuilding demo records.
-- BACK UP neighbor_market FIRST. NEVER RUN THIS SCRIPT IN PRODUCTION.
-- It preserves app_user rows but removes item workflow and comment data.
-- ============================================================================

START TRANSACTION;

SET @user_a_id = (SELECT id FROM app_user WHERE username = 'neighbor_a' LIMIT 1);
SET @user_b_id = (SELECT id FROM app_user WHERE username = 'neighbor_b' LIMIT 1);
SET @now = NOW(6);

-- Clear children before parents. Foreign keys remain enabled throughout.
-- item_comment.parent_id references item_comment.id, so remove replies first.
DELETE FROM item_comment
WHERE parent_id IS NOT NULL;
DELETE FROM item_comment
WHERE parent_id IS NULL;
-- item.current_handover_id references handover, so clear it before handover deletion.
UPDATE item SET current_handover_id = NULL;
DELETE FROM handover;
DELETE FROM item_interest;
DELETE FROM pickup_slot;
DELETE FROM item;

-- 1. 儿童绘本一套（8册）：刚上架、免费送、可领取
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_a_id, '儿童绘本一套（8册）',
    '适合学龄前孩子的绘本 8 册，内页保存完整，统一打包送给需要的邻居。',
    '图书', 'FREE', 0, '/demo/books.jpg',
    'AVAILABLE', DATE_SUB(@now, INTERVAL 4 HOUR), DATE_SUB(@now, INTERVAL 1 HOUR),
    NULL, '社区活动室门口', NULL
);
SET @books_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@books_id, DATE_ADD(@now, INTERVAL 24 HOUR), DATE_ADD(@now, INTERVAL 25 HOUR), @now);

-- 2. 手冲咖啡壶，配滤杯：新上架、随便给、有效意向
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_a_id, '手冲咖啡壶，配滤杯',
    '玻璃壶身配滤杯和量匙，使用痕迹轻微，适合刚开始在家手冲的邻居。',
    '日用', 'NEGOTIABLE', NULL, '/demo/coffee-set.jpg',
    'AVAILABLE', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 8 HOUR),
    NULL, '南门保安亭旁', NULL
);
SET @coffee_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@coffee_id, DATE_ADD(@now, INTERVAL 48 HOUR), DATE_ADD(@now, INTERVAL 49 HOUR), @now);
SET @coffee_slot_id = LAST_INSERT_ID();
INSERT INTO item_interest (item_id, user_id, slot_id, status, created_at, updated_at)
VALUES (@coffee_id, @user_b_id, @coffee_slot_id, 'WAITING', DATE_SUB(@now, INTERVAL 3 HOUR), DATE_SUB(@now, INTERVAL 1 HOUR));

-- 3. 九成新空气炸锅：较早上架、固定价格、有效意向
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_b_id, '九成新空气炸锅',
    '容量适合两三人使用，功能正常，内胆只有轻微使用痕迹，已清洁。',
    '家电', 'FIXED_PRICE', 129.00, '/demo/air-fryer.jpg',
    'AVAILABLE', DATE_SUB(@now, INTERVAL 9 DAY), DATE_SUB(@now, INTERVAL 1 DAY),
    NULL, '一期大厅前台', NULL
);
SET @air_fryer_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@air_fryer_id, DATE_ADD(@now, INTERVAL 72 HOUR), DATE_ADD(@now, INTERVAL 73 HOUR), @now);
SET @air_fryer_slot_id = LAST_INSERT_ID();
INSERT INTO item_interest (item_id, user_id, slot_id, status, created_at, updated_at)
VALUES (@air_fryer_id, @user_a_id, @air_fryer_slot_id, 'WAITING', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 1 DAY));

-- 4. 宜家白色床头柜：较早上架、已确认、待交接
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_a_id, '宜家白色床头柜',
    '白色床头柜，抽屉推拉顺畅，尺寸适合小卧室，搬家后闲置转让。',
    '日用', 'FIXED_PRICE', 120.00, '/demo/bedside-table.jpg',
    'RESERVED', DATE_SUB(@now, INTERVAL 7 DAY), DATE_SUB(@now, INTERVAL 1 DAY),
    NULL, '北门长椅', NULL
);
SET @bedside_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@bedside_id, DATE_ADD(@now, INTERVAL 26 HOUR), DATE_ADD(@now, INTERVAL 27 HOUR), @now);
SET @bedside_slot_id = LAST_INSERT_ID();
INSERT INTO item_interest (item_id, user_id, slot_id, status, created_at, updated_at)
VALUES (@bedside_id, @user_b_id, @bedside_slot_id, 'CONFIRMED', DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 20 HOUR));
SET @bedside_interest_id = LAST_INSERT_ID();
INSERT INTO handover (
    item_id, publisher_id, recipient_id, interest_id, start_at, end_at,
    location_snapshot, status, created_at, cancelled_at, completed_at
) VALUES (
    @bedside_id, @user_a_id, @user_b_id, @bedside_interest_id,
    DATE_ADD(@now, INTERVAL 26 HOUR), DATE_ADD(@now, INTERVAL 27 HOUR),
    '北门长椅', 'ACTIVE', DATE_SUB(@now, INTERVAL 20 HOUR), NULL, NULL
);
SET @bedside_handover_id = LAST_INSERT_ID();
UPDATE item SET current_handover_id = @bedside_handover_id WHERE id = @bedside_id;

-- 5. 折叠晾衣架：已完成，发布到完成耗时约 27 小时
SET @rack_published = DATE_SUB(@now, INTERVAL 30 HOUR);
SET @rack_completed = DATE_SUB(@now, INTERVAL 3 HOUR);
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_b_id, '折叠晾衣架',
    '三层折叠晾衣架，收起来不占地方，关节灵活，适合阳台或出租屋使用。',
    '日用', 'FREE', 0, '/demo/drying-rack.jpg',
    'COMPLETED', @rack_published, @rack_completed, @rack_completed, '社区服务中心门口', NULL
);
SET @rack_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@rack_id, DATE_SUB(@rack_completed, INTERVAL 1 HOUR), @rack_completed, @rack_published);
SET @rack_slot_id = LAST_INSERT_ID();
INSERT INTO item_interest (item_id, user_id, slot_id, status, created_at, updated_at)
VALUES (@rack_id, @user_a_id, @rack_slot_id, 'CONFIRMED', DATE_SUB(@rack_completed, INTERVAL 2 HOUR), DATE_SUB(@rack_completed, INTERVAL 1 HOUR));
SET @rack_interest_id = LAST_INSERT_ID();
INSERT INTO handover (
    item_id, publisher_id, recipient_id, interest_id, start_at, end_at,
    location_snapshot, status, created_at, cancelled_at, completed_at
) VALUES (
    @rack_id, @user_b_id, @user_a_id, @rack_interest_id,
    DATE_SUB(@rack_completed, INTERVAL 1 HOUR), @rack_completed,
    '社区服务中心门口', 'COMPLETED', DATE_SUB(@rack_completed, INTERVAL 2 HOUR), NULL, @rack_completed
);
SET @rack_handover_id = LAST_INSERT_ID();
UPDATE item SET current_handover_id = @rack_handover_id WHERE id = @rack_id;

-- 6. 暖光可调台灯：新上架、随便给、可领取
INSERT INTO item (
    publisher_id, title, description, category, trade_type, price, image_url,
    status, published_at, last_confirmed_at, completed_at, pickup_location, current_handover_id
) VALUES (
    @user_b_id, '暖光可调台灯',
    '三档暖光亮度可调，灯罩完整，适合书桌和床头，插电即可使用。',
    '家电', 'NEGOTIABLE', NULL, '/demo/warm-lamp.jpg',
    'AVAILABLE', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 6 HOUR),
    NULL, '东门快递柜旁', NULL
);
SET @lamp_id = LAST_INSERT_ID();
INSERT INTO pickup_slot (item_id, start_at, end_at, created_at)
VALUES (@lamp_id, DATE_ADD(@now, INTERVAL 50 HOUR), DATE_ADD(@now, INTERVAL 51 HOUR), @now);

-- Public question and publisher reply for the coffee set.
INSERT INTO item_comment (item_id, user_id, parent_id, content, created_at)
VALUES (@coffee_id, @user_b_id, NULL, '请问滤杯也一起送吗？', DATE_SUB(@now, INTERVAL 2 HOUR));
SET @coffee_comment_id = LAST_INSERT_ID();
INSERT INTO item_comment (item_id, user_id, parent_id, content, created_at)
VALUES (@coffee_id, @user_a_id, @coffee_comment_id, '一起送，已经清洗干净了，交接时可以当面确认。', DATE_SUB(@now, INTERVAL 90 MINUTE));

-- Public question and publisher reply for the air fryer.
INSERT INTO item_comment (item_id, user_id, parent_id, content, created_at)
VALUES (@air_fryer_id, @user_a_id, NULL, '请问内胆有明显划痕吗？', DATE_SUB(@now, INTERVAL 20 HOUR));
SET @air_fryer_comment_id = LAST_INSERT_ID();
INSERT INTO item_comment (item_id, user_id, parent_id, content, created_at)
VALUES (@air_fryer_id, @user_b_id, @air_fryer_comment_id, '只有轻微使用痕迹，没有明显划痕，欢迎交接时查看。', DATE_SUB(@now, INTERVAL 19 HOUR));

COMMIT;
