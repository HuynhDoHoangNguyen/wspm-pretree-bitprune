-- CNTT-KLCN256 | DRAFT: MySQL 8.4 physical schema proposal for TV2 V3.
-- REVIEW BEFORE APPLYING. This is NOT an approved migration.
-- Exactly 12 logical entities / 63 fields; no Flyway, seed, or extra tables.
-- Physical choices (VARCHAR lengths, NULLability, precision, auto-ID, constraints)
-- are proposals for approval, not explicit V3 requirements.
-- Database must already exist via Docker Compose. No DROP statements.
-- Uppercase table names follow the V3 logical names; on Linux MySQL they are
-- case-sensitive. Use these exact names in SQL or explicit ORM table mappings.
-- GROUP A (6 tables / 18 columns): source transactions + product mappings.
CREATE TABLE `CUSTOMER` (
    `customer_id` VARCHAR(40) NOT NULL,
    PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CATEGORY` (
    `category_id` VARCHAR(40) NOT NULL,
    `category_name` VARCHAR(160) NOT NULL,
    PRIMARY KEY (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PRODUCT` (
    `product_id` VARCHAR(40) NOT NULL,
    `product_name` VARCHAR(200) NOT NULL,
    `category_id` VARCHAR(40) NOT NULL,
    `product_status` VARCHAR(24) NOT NULL,
    `price` DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (`product_id`),
    KEY `idx_product_category` (`category_id`),
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`)
        REFERENCES `CATEGORY` (`category_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_product_price` CHECK (`price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ORDERS` (
    `order_id` VARCHAR(40) NOT NULL,
    `customer_id` VARCHAR(40) NOT NULL,
    `order_time` DATETIME(0) NOT NULL,
    `order_status` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`order_id`),
    KEY `idx_orders_sequence` (`customer_id`, `order_time`, `order_id`),
    CONSTRAINT `fk_orders_customer` FOREIGN KEY (`customer_id`)
        REFERENCES `CUSTOMER` (`customer_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_order_status` CHECK (`order_status` IN
        ('PENDING','COMPLETED','CANCELLED','RETURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ORDER_ITEM` (
    `order_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `unit_price` DECIMAL(18,2) NOT NULL,
    `quantity` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`order_id`, `product_id`),
    KEY `idx_order_item_product` (`product_id`),
    CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_order_item_price` CHECK (`unit_price` >= 0),
    CONSTRAINT `chk_order_item_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ITEM_MAPPING` (
    `product_id` VARCHAR(40) NOT NULL,
    `item_id` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`product_id`),
    UNIQUE KEY `uk_item_mapping_item_id` (`item_id`),
    CONSTRAINT `fk_mapping_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_mapping_item_positive` CHECK (`item_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- GROUP B (3 tables / 28 columns): mining runs, patterns, and ordered items.
CREATE TABLE `RUN` (
    `run_id` VARCHAR(40) NOT NULL,
    `algorithm` VARCHAR(32) NOT NULL,
    `dataset_source` VARCHAR(255) NOT NULL,
    `minsup_ratio` DOUBLE NOT NULL,
    `minsup_abs` DOUBLE NOT NULL,
    `sequence_count` BIGINT UNSIGNED NOT NULL,
    `mean_w` DOUBLE NOT NULL,
    `run_time` DATETIME(0) NOT NULL,
    `input_snapshot` VARCHAR(1024) NOT NULL,
    `execution_time` DOUBLE NULL,
    `memory_usage` DOUBLE NULL,
    `pattern_count` BIGINT UNSIGNED NULL,
    `candidate_count` BIGINT UNSIGNED NULL,
    `top_k` INT UNSIGNED NULL,
    `category_filter` BOOLEAN NOT NULL DEFAULT FALSE,
    `coef_alpha` DOUBLE NULL,
    `coef_beta` DOUBLE NULL,
    `coef_gamma` DOUBLE NULL,
    `coef_delta` DOUBLE NULL,
    PRIMARY KEY (`run_id`),
    KEY `idx_run_dataset_time` (`dataset_source`, `run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PATTERN` (
    `pattern_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` VARCHAR(40) NOT NULL,
    `support` BIGINT UNSIGNED NOT NULL,
    `weight` DOUBLE NOT NULL,
    `pattern_length` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`pattern_id`),
    KEY `idx_pattern_run` (`run_id`),
    CONSTRAINT `fk_pattern_run` FOREIGN KEY (`run_id`)
        REFERENCES `RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_pattern_length` CHECK (`pattern_length` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PATTERN_ITEM` (
    `pattern_id` BIGINT UNSIGNED NOT NULL,
    `itemset_order` INT UNSIGNED NOT NULL,
    `item_order` INT UNSIGNED NOT NULL,
    `item_id` INT NOT NULL,
    PRIMARY KEY (`pattern_id`, `itemset_order`, `item_order`),
    CONSTRAINT `fk_pattern_item_pattern` FOREIGN KEY (`pattern_id`)
        REFERENCES `PATTERN` (`pattern_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_pattern_itemset_order` CHECK (`itemset_order` > 0),
    CONSTRAINT `chk_pattern_item_order` CHECK (`item_order` > 0)
    -- V3 intentionally has NO FK from PATTERN_ITEM.item_id to ITEM_MAPPING.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- GROUP C (3 tables / 17 columns): combo proposals, items, and provenance.
CREATE TABLE `COMBO` (
    `combo_id` VARCHAR(40) NOT NULL,
    `run_id` VARCHAR(40) NOT NULL,
    `combo_name` VARCHAR(255) NOT NULL,
    `combo_type` VARCHAR(32) NOT NULL,
    `combo_score` DOUBLE NULL,
    `combo_price` DECIMAL(18,2) NULL,
    `support` BIGINT UNSIGNED NOT NULL,
    `weight` DOUBLE NOT NULL,
    `combo_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_at` DATETIME(0) NULL,
    PRIMARY KEY (`combo_id`),
    KEY `idx_combo_run` (`run_id`),
    KEY `idx_combo_status_score` (`combo_status`, `combo_score`),
    CONSTRAINT `fk_combo_run` FOREIGN KEY (`run_id`)
        REFERENCES `RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_combo_status` CHECK (`combo_status` IN
        ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT `chk_combo_price` CHECK (`combo_price` IS NULL OR `combo_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `COMBO_ITEM` (
    `combo_id` VARCHAR(40) NOT NULL,
    `itemset_order` INT UNSIGNED NOT NULL,
    `item_order` INT UNSIGNED NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    PRIMARY KEY (`combo_id`, `itemset_order`, `item_order`),
    UNIQUE KEY `uk_combo_item_product` (`combo_id`, `product_id`),
    KEY `idx_combo_item_product` (`product_id`),
    CONSTRAINT `fk_combo_item_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_combo_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_combo_itemset_order` CHECK (`itemset_order` > 0),
    CONSTRAINT `chk_combo_item_order` CHECK (`item_order` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `COMBO_SOURCE` (
    `combo_id` VARCHAR(40) NOT NULL,
    `pattern_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`combo_id`, `pattern_id`),
    KEY `idx_combo_source_pattern` (`pattern_id`),
    CONSTRAINT `fk_combo_source_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_combo_source_pattern` FOREIGN KEY (`pattern_id`)
        REFERENCES `PATTERN` (`pattern_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Verification after explicit approval and execution:
-- SELECT COUNT(*) AS table_count FROM information_schema.tables
-- WHERE table_schema = 'wspm_combo' AND table_type = 'BASE TABLE';
-- SELECT COUNT(*) AS column_count FROM information_schema.columns
-- WHERE table_schema = 'wspm_combo';
-- SHOW TABLES;
