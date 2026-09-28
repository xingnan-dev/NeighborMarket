ALTER TABLE item
    ADD COLUMN pickup_location VARCHAR(200) NULL AFTER completed_at,
    ADD COLUMN current_handover_id BIGINT NULL AFTER pickup_location;
CREATE TABLE pickup_slot (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, item_id BIGINT NOT NULL, start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_pickup_slot_time CHECK (end_at > start_at), CONSTRAINT fk_pickup_slot_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE CASCADE,
 INDEX idx_pickup_slot_item_start (item_id,start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE item_interest (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, item_id BIGINT NOT NULL, user_id BIGINT NOT NULL, slot_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT uq_item_interest_user UNIQUE(item_id,user_id), CONSTRAINT fk_interest_item FOREIGN KEY(item_id) REFERENCES item(id),
 CONSTRAINT fk_interest_user FOREIGN KEY(user_id) REFERENCES app_user(id), CONSTRAINT fk_interest_slot FOREIGN KEY(slot_id) REFERENCES pickup_slot(id),
 INDEX idx_interest_item_status(item_id,status), INDEX idx_interest_user_updated(user_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE handover (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, item_id BIGINT NOT NULL, publisher_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, interest_id BIGINT NOT NULL,
 start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, location_snapshot VARCHAR(200) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), cancelled_at DATETIME(6) NULL, completed_at DATETIME(6) NULL,
 INDEX idx_handover_interest(interest_id), CONSTRAINT chk_handover_time CHECK(end_at > start_at),
 CONSTRAINT fk_handover_item FOREIGN KEY(item_id) REFERENCES item(id), CONSTRAINT fk_handover_publisher FOREIGN KEY(publisher_id) REFERENCES app_user(id),
 CONSTRAINT fk_handover_recipient FOREIGN KEY(recipient_id) REFERENCES app_user(id), CONSTRAINT fk_handover_interest FOREIGN KEY(interest_id) REFERENCES item_interest(id),
 INDEX idx_handover_item_status(item_id,status), INDEX idx_handover_parties(publisher_id,recipient_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE item ADD CONSTRAINT fk_item_current_handover FOREIGN KEY(current_handover_id) REFERENCES handover(id);
CREATE TABLE item_comment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, item_id BIGINT NOT NULL, user_id BIGINT NOT NULL, content VARCHAR(500) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT fk_comment_item FOREIGN KEY(item_id) REFERENCES item(id),
 CONSTRAINT fk_comment_user FOREIGN KEY(user_id) REFERENCES app_user(id), INDEX idx_comment_item_created(item_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;