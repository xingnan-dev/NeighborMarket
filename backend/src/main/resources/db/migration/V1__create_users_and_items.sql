CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    publisher_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(50) NOT NULL,
    trade_type VARCHAR(20) NOT NULL,
    price DECIMAL(10,2) NULL,
    image_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_confirmed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_item_market (status, published_at, id),
    KEY idx_item_publisher (publisher_id, published_at, id),
    CONSTRAINT fk_item_publisher FOREIGN KEY (publisher_id) REFERENCES app_user (id),
    CONSTRAINT chk_item_trade_price CHECK (
        (trade_type = 'FIXED_PRICE' AND price IS NOT NULL AND price > 0)
        OR (trade_type = 'FREE' AND price = 0)
        OR (trade_type = 'NEGOTIABLE' AND price IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
