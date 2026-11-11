-- CNTT-KLCN256 | REVIEW-ONLY; intentionally outside Flyway scan path.
-- Target: MySQL 8.4, exactly 45 application tables after successful migration.
-- Source: V1__initial_schema.sql (12 tables).
--
-- IMPORTANT:
-- 1. This file is intentionally created but has NOT been executed.
-- 2. Spring Flyway is enabled. Do not start the backend against a V1 database
--    until this migration and the corresponding JPA/entity changes are approved.
-- 3. MySQL DDL auto-commits. Take a database backup before eventual execution.
-- 4. Legacy rows lacking checksums/weight provenance are preserved but marked
--    LEGACY_UNVERIFIED; they are not research acceptance evidence.
-- 5. No ComboScore or coefficient columns are created in the target schema.

-- ---------------------------------------------------------------------------
-- A. Isolate the V1 schema so target tables can be created with final names.
-- RENAME TABLE is atomic in MySQL. Constraint names below use a v2 prefix to
-- avoid collisions with FK symbols retained by the legacy tables.
-- ---------------------------------------------------------------------------
RENAME TABLE
    `COMBO_SOURCE` TO `LEGACY_V1_COMBO_SOURCE`,
    `COMBO_ITEM` TO `LEGACY_V1_COMBO_ITEM`,
    `COMBO` TO `LEGACY_V1_COMBO`,
    `PATTERN_ITEM` TO `LEGACY_V1_PATTERN_ITEM`,
    `PATTERN` TO `LEGACY_V1_PATTERN`,
    `RUN` TO `LEGACY_V1_RUN`,
    `ITEM_MAPPING` TO `LEGACY_V1_ITEM_MAPPING`,
    `ORDER_ITEM` TO `LEGACY_V1_ORDER_ITEM`,
    `ORDERS` TO `LEGACY_V1_ORDERS`,
    `PRODUCT` TO `LEGACY_V1_PRODUCT`,
    `CATEGORY` TO `LEGACY_V1_CATEGORY`,
    `CUSTOMER` TO `LEGACY_V1_CUSTOMER`;

-- ---------------------------------------------------------------------------
-- B. Identity and authorization (6 tables including CUSTOMER).
-- ---------------------------------------------------------------------------
CREATE TABLE `USER_ACCOUNT` (
    `user_id` VARCHAR(40) NOT NULL,
    `username` VARCHAR(80) NULL,
    `email` VARCHAR(254) NULL,
    `phone` VARCHAR(32) NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `display_name` VARCHAR(160) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    `email_verified_at` DATETIME(6) NULL,
    `phone_verified_at` DATETIME(6) NULL,
    `last_login_at` DATETIME(6) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_v2_user_username` (`username`),
    UNIQUE KEY `uk_v2_user_email` (`email`),
    UNIQUE KEY `uk_v2_user_phone` (`phone`),
    CONSTRAINT `chk_v2_user_identity` CHECK
        (`username` IS NOT NULL OR `email` IS NOT NULL OR `phone` IS NOT NULL),
    CONSTRAINT `chk_v2_user_status` CHECK
        (`status` IN ('PENDING','ACTIVE','LOCKED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ROLE` (
    `role_id` VARCHAR(40) NOT NULL,
    `code` VARCHAR(40) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_v2_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `USER_ROLE` (
    `user_id` VARCHAR(40) NOT NULL,
    `role_id` VARCHAR(40) NOT NULL,
    `assigned_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `assigned_by` VARCHAR(40) NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_v2_user_role_role` (`role_id`),
    KEY `idx_v2_user_role_assigner` (`assigned_by`),
    CONSTRAINT `fk_v2_user_role_user` FOREIGN KEY (`user_id`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_user_role_role` FOREIGN KEY (`role_id`)
        REFERENCES `ROLE` (`role_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_user_role_assigner` FOREIGN KEY (`assigned_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `USER_SESSION` (
    `session_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `refresh_token_hash` VARCHAR(255) NOT NULL,
    `device_info` VARCHAR(500) NULL,
    `ip_hash` VARCHAR(128) NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `revoked_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `last_used_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`session_id`),
    UNIQUE KEY `uk_v2_session_refresh_hash` (`refresh_token_hash`),
    KEY `idx_v2_session_user_expiry` (`user_id`, `expires_at`),
    CONSTRAINT `fk_v2_session_user` FOREIGN KEY (`user_id`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `USER_ADDRESS` (
    `user_address_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `label` VARCHAR(80) NULL,
    `recipient_name` VARCHAR(160) NOT NULL,
    `phone` VARCHAR(32) NOT NULL,
    `address_line` VARCHAR(500) NOT NULL,
    `ward_code` VARCHAR(32) NULL,
    `ward_name` VARCHAR(160) NOT NULL,
    `district_code` VARCHAR(32) NULL,
    `district_name` VARCHAR(160) NOT NULL,
    `province_code` VARCHAR(32) NULL,
    `province_name` VARCHAR(160) NOT NULL,
    `postal_code` VARCHAR(20) NULL,
    `country_code` CHAR(2) NOT NULL DEFAULT 'VN',
    `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_address_id`),
    KEY `idx_v2_user_address_owner` (`user_id`, `is_default`),
    CONSTRAINT `fk_v2_user_address_user` FOREIGN KEY (`user_id`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CUSTOMER` (
    `customer_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `anonymous_code` VARCHAR(80) NOT NULL,
    `segment` VARCHAR(80) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`customer_id`),
    UNIQUE KEY `uk_v2_customer_user` (`user_id`),
    UNIQUE KEY `uk_v2_customer_anonymous` (`anonymous_code`),
    CONSTRAINT `fk_v2_customer_user` FOREIGN KEY (`user_id`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- C. Catalog (4 tables).
-- ---------------------------------------------------------------------------
CREATE TABLE `CATEGORY` (
    `category_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(160) NOT NULL,
    `slug` VARCHAR(180) NOT NULL,
    `description` TEXT NULL,
    `display_order` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (`category_id`),
    UNIQUE KEY `uk_v2_category_slug` (`slug`),
    CONSTRAINT `chk_v2_category_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `BRAND` (
    `brand_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(160) NOT NULL,
    `slug` VARCHAR(180) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (`brand_id`),
    UNIQUE KEY `uk_v2_brand_slug` (`slug`),
    CONSTRAINT `chk_v2_brand_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PRODUCT` (
    `product_id` VARCHAR(40) NOT NULL,
    `sku` VARCHAR(80) NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `slug` VARCHAR(220) NOT NULL,
    `category_id` VARCHAR(40) NOT NULL,
    `brand_id` VARCHAR(40) NULL,
    `short_description` VARCHAR(1000) NULL,
    `description` TEXT NULL,
    `specifications_json` JSON NULL,
    `current_price` DECIMAL(19,2) NOT NULL,
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `thumbnail_url` VARCHAR(1024) NULL,
    `track_serial` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `price_updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`product_id`),
    UNIQUE KEY `uk_v2_product_sku` (`sku`),
    UNIQUE KEY `uk_v2_product_slug` (`slug`),
    KEY `idx_v2_product_category_status` (`category_id`, `status`),
    KEY `idx_v2_product_brand_status` (`brand_id`, `status`),
    CONSTRAINT `fk_v2_product_category` FOREIGN KEY (`category_id`)
        REFERENCES `CATEGORY` (`category_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_product_brand` FOREIGN KEY (`brand_id`)
        REFERENCES `BRAND` (`brand_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_product_price` CHECK (`current_price` >= 0),
    CONSTRAINT `chk_v2_product_status` CHECK
        (`status` IN ('DRAFT','ACTIVE','INACTIVE','DISCONTINUED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PRODUCT_IMAGE` (
    `image_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `image_url` VARCHAR(1024) NOT NULL,
    `alt_text` VARCHAR(255) NULL,
    `display_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`image_id`),
    UNIQUE KEY `uk_v2_product_image_order` (`product_id`, `display_order`),
    CONSTRAINT `fk_v2_product_image_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- D. Supplier, purchasing and inventory base (8 tables here; serial later).
-- ---------------------------------------------------------------------------
CREATE TABLE `WAREHOUSE` (
    `warehouse_id` VARCHAR(40) NOT NULL,
    `code` VARCHAR(40) NOT NULL,
    `name` VARCHAR(160) NOT NULL,
    `address_line` VARCHAR(500) NULL,
    `phone` VARCHAR(32) NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`warehouse_id`),
    UNIQUE KEY `uk_v2_warehouse_code` (`code`),
    CONSTRAINT `chk_v2_warehouse_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `SUPPLIER` (
    `supplier_id` VARCHAR(40) NOT NULL,
    `code` VARCHAR(40) NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `tax_code` VARCHAR(40) NULL,
    `contact_name` VARCHAR(160) NULL,
    `phone` VARCHAR(32) NULL,
    `email` VARCHAR(254) NULL,
    `address_line` VARCHAR(500) NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`supplier_id`),
    UNIQUE KEY `uk_v2_supplier_code` (`code`),
    CONSTRAINT `chk_v2_supplier_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PURCHASE_ORDER` (
    `purchase_order_id` VARCHAR(40) NOT NULL,
    `po_number` VARCHAR(60) NOT NULL,
    `supplier_id` VARCHAR(40) NOT NULL,
    `warehouse_id` VARCHAR(40) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `ordered_at` DATETIME(6) NULL,
    `expected_at` DATETIME(6) NULL,
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `subtotal` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `tax_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `grand_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `note` VARCHAR(1000) NULL,
    `created_by` VARCHAR(40) NULL,
    `approved_by` VARCHAR(40) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `approved_at` DATETIME(6) NULL,
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`purchase_order_id`),
    UNIQUE KEY `uk_v2_po_number` (`po_number`),
    UNIQUE KEY `uk_v2_po_identity_warehouse` (`purchase_order_id`, `warehouse_id`),
    KEY `idx_v2_po_supplier_status` (`supplier_id`, `status`),
    KEY `idx_v2_po_warehouse_status` (`warehouse_id`, `status`),
    CONSTRAINT `fk_v2_po_supplier` FOREIGN KEY (`supplier_id`)
        REFERENCES `SUPPLIER` (`supplier_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_po_warehouse` FOREIGN KEY (`warehouse_id`)
        REFERENCES `WAREHOUSE` (`warehouse_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_po_creator` FOREIGN KEY (`created_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_po_approver` FOREIGN KEY (`approved_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_po_status` CHECK (`status` IN
        ('DRAFT','APPROVED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    CONSTRAINT `chk_v2_po_money` CHECK
        (`subtotal` >= 0 AND `tax_total` >= 0 AND `grand_total` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PURCHASE_ORDER_ITEM` (
    `purchase_order_item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `purchase_order_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `quantity_ordered` INT UNSIGNED NOT NULL,
    `quantity_received` INT UNSIGNED NOT NULL DEFAULT 0,
    `unit_cost` DECIMAL(19,2) NOT NULL,
    `tax_rate` DECIMAL(7,4) NOT NULL DEFAULT 0,
    `line_total` DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (`purchase_order_item_id`),
    UNIQUE KEY `uk_v2_po_item_identity_product`
        (`purchase_order_item_id`, `product_id`),
    UNIQUE KEY `uk_v2_po_item_product` (`purchase_order_id`, `product_id`),
    KEY `idx_v2_po_item_product` (`product_id`),
    CONSTRAINT `fk_v2_po_item_order` FOREIGN KEY (`purchase_order_id`)
        REFERENCES `PURCHASE_ORDER` (`purchase_order_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_po_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_po_item_quantity` CHECK
        (`quantity_ordered` > 0 AND `quantity_received` <= `quantity_ordered`),
    CONSTRAINT `chk_v2_po_item_money` CHECK
        (`unit_cost` >= 0 AND `tax_rate` >= 0 AND `line_total` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `GOODS_RECEIPT` (
    `goods_receipt_id` VARCHAR(40) NOT NULL,
    `receipt_number` VARCHAR(60) NOT NULL,
    `purchase_order_id` VARCHAR(40) NOT NULL,
    `warehouse_id` VARCHAR(40) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `supplier_invoice_number` VARCHAR(100) NULL,
    `received_at` DATETIME(6) NULL,
    `note` VARCHAR(1000) NULL,
    `created_by` VARCHAR(40) NULL,
    `posted_by` VARCHAR(40) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `posted_at` DATETIME(6) NULL,
    PRIMARY KEY (`goods_receipt_id`),
    UNIQUE KEY `uk_v2_receipt_number` (`receipt_number`),
    KEY `idx_v2_receipt_po_status` (`purchase_order_id`, `status`),
    KEY `idx_v2_receipt_warehouse` (`warehouse_id`, `received_at`),
    CONSTRAINT `fk_v2_receipt_po_warehouse`
        FOREIGN KEY (`purchase_order_id`, `warehouse_id`)
        REFERENCES `PURCHASE_ORDER` (`purchase_order_id`, `warehouse_id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_receipt_creator` FOREIGN KEY (`created_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_receipt_poster` FOREIGN KEY (`posted_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_receipt_status` CHECK
        (`status` IN ('DRAFT','POSTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `GOODS_RECEIPT_ITEM` (
    `goods_receipt_item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `goods_receipt_id` VARCHAR(40) NOT NULL,
    `purchase_order_item_id` BIGINT UNSIGNED NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `quantity_received` INT UNSIGNED NOT NULL,
    `unit_cost` DECIMAL(19,2) NOT NULL,
    `line_total` DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (`goods_receipt_item_id`),
    UNIQUE KEY `uk_v2_receipt_item_identity_product`
        (`goods_receipt_item_id`, `product_id`),
    UNIQUE KEY `uk_v2_receipt_po_item` (`goods_receipt_id`, `purchase_order_item_id`),
    KEY `idx_v2_receipt_item_product` (`product_id`),
    CONSTRAINT `fk_v2_receipt_item_receipt` FOREIGN KEY (`goods_receipt_id`)
        REFERENCES `GOODS_RECEIPT` (`goods_receipt_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_receipt_item_po_product`
        FOREIGN KEY (`purchase_order_item_id`, `product_id`)
        REFERENCES `PURCHASE_ORDER_ITEM` (`purchase_order_item_id`, `product_id`)
        ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_receipt_item_quantity` CHECK (`quantity_received` > 0),
    CONSTRAINT `chk_v2_receipt_item_money` CHECK (`unit_cost` >= 0 AND `line_total` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `INVENTORY_BALANCE` (
    `warehouse_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `on_hand_quantity` INT UNSIGNED NOT NULL DEFAULT 0,
    `reserved_quantity` INT UNSIGNED NOT NULL DEFAULT 0,
    `version` BIGINT NOT NULL DEFAULT 0,
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`warehouse_id`, `product_id`),
    KEY `idx_v2_inventory_product` (`product_id`),
    CONSTRAINT `fk_v2_inventory_warehouse` FOREIGN KEY (`warehouse_id`)
        REFERENCES `WAREHOUSE` (`warehouse_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_inventory_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_inventory_quantities` CHECK
        (`on_hand_quantity` >= `reserved_quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `STOCK_MOVEMENT` (
    `stock_movement_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `warehouse_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `movement_type` VARCHAR(32) NOT NULL,
    `quantity_delta` INT NOT NULL,
    `unit_cost` DECIMAL(19,2) NULL,
    `balance_after` INT UNSIGNED NOT NULL,
    `reference_type` VARCHAR(40) NOT NULL,
    `reference_id` VARCHAR(40) NOT NULL,
    `occurred_at` DATETIME(6) NOT NULL,
    `created_by` VARCHAR(40) NULL,
    `note` VARCHAR(1000) NULL,
    PRIMARY KEY (`stock_movement_id`),
    KEY `idx_v2_movement_stock_time` (`warehouse_id`, `product_id`, `occurred_at`),
    KEY `idx_v2_movement_reference` (`reference_type`, `reference_id`),
    CONSTRAINT `fk_v2_movement_warehouse` FOREIGN KEY (`warehouse_id`)
        REFERENCES `WAREHOUSE` (`warehouse_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_movement_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_movement_creator` FOREIGN KEY (`created_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_movement_type` CHECK (`movement_type` IN
        ('PURCHASE_RECEIPT','SALE_ISSUE','CUSTOMER_RETURN','SUPPLIER_RETURN',
         'ADJUSTMENT_IN','ADJUSTMENT_OUT')),
    CONSTRAINT `chk_v2_movement_delta` CHECK (`quantity_delta` <> 0),
    CONSTRAINT `chk_v2_movement_cost` CHECK (`unit_cost` IS NULL OR `unit_cost` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- E. Sales base (ORDERS/ORDER_ITEM first; checkout/combo FKs are added later).
-- ---------------------------------------------------------------------------
CREATE TABLE `ORDERS` (
    `order_id` VARCHAR(40) NOT NULL,
    `checkout_id` VARCHAR(40) NULL,
    `order_number` VARCHAR(60) NOT NULL,
    `customer_id` VARCHAR(40) NOT NULL,
    `ordered_at` DATETIME(6) NOT NULL,
    `completed_at` DATETIME(6) NULL,
    `status` VARCHAR(32) NOT NULL,
    `source` VARCHAR(40) NOT NULL,
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `list_subtotal` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `combo_discount_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `other_discount_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `shipping_fee` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `tax_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `grand_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_v2_order_checkout` (`checkout_id`),
    UNIQUE KEY `uk_v2_order_number` (`order_number`),
    KEY `idx_v2_order_sequence` (`customer_id`, `completed_at`, `order_id`),
    KEY `idx_v2_order_status_time` (`status`, `ordered_at`),
    CONSTRAINT `fk_v2_order_customer` FOREIGN KEY (`customer_id`)
        REFERENCES `CUSTOMER` (`customer_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_order_status` CHECK (`status` IN
        ('PENDING_PAYMENT','PAID','PROCESSING','COMPLETED','CANCELLED',
         'EXPIRED','RETURNED','REFUNDED')),
    CONSTRAINT `chk_v2_order_money` CHECK
        (`list_subtotal` >= 0 AND `combo_discount_total` >= 0
         AND `other_discount_total` >= 0 AND `shipping_fee` >= 0
         AND `tax_total` >= 0 AND `grand_total` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ORDER_ITEM` (
    `order_item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `order_applied_combo_id` VARCHAR(40) NULL,
    `quantity` INT UNSIGNED NOT NULL,
    `list_unit_price` DECIMAL(19,2) NOT NULL,
    `allocated_discount_amount` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `line_total` DECIMAL(19,2) NOT NULL,
    `sku_snapshot` VARCHAR(80) NOT NULL,
    `product_name_snapshot` VARCHAR(200) NOT NULL,
    PRIMARY KEY (`order_item_id`),
    KEY `idx_v2_order_item_order` (`order_id`),
    KEY `idx_v2_order_item_product` (`product_id`),
    KEY `idx_v2_order_item_combo_order` (`order_applied_combo_id`, `order_id`),
    CONSTRAINT `fk_v2_order_item_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_order_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_order_item_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `chk_v2_order_item_money` CHECK
        (`list_unit_price` >= 0 AND `allocated_discount_amount` >= 0
         AND `line_total` >= 0
         AND `allocated_discount_amount` <= `list_unit_price` * `quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ORDER_ADDRESS` (
    `order_address_id` VARCHAR(40) NOT NULL,
    `order_id` VARCHAR(40) NOT NULL,
    `address_type` VARCHAR(16) NOT NULL,
    `recipient_name` VARCHAR(160) NOT NULL,
    `phone` VARCHAR(32) NOT NULL,
    `address_line` VARCHAR(500) NOT NULL,
    `ward_code` VARCHAR(32) NULL,
    `ward_name` VARCHAR(160) NOT NULL,
    `district_code` VARCHAR(32) NULL,
    `district_name` VARCHAR(160) NOT NULL,
    `province_code` VARCHAR(32) NULL,
    `province_name` VARCHAR(160) NOT NULL,
    `postal_code` VARCHAR(20) NULL,
    `country_code` CHAR(2) NOT NULL DEFAULT 'VN',
    PRIMARY KEY (`order_address_id`),
    UNIQUE KEY `uk_v2_order_address_type` (`order_id`, `address_type`),
    CONSTRAINT `fk_v2_order_address_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_order_address_type` CHECK
        (`address_type` IN ('SHIPPING','BILLING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ORDER_STATUS_HISTORY` (
    `order_status_history_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id` VARCHAR(40) NOT NULL,
    `from_status` VARCHAR(32) NULL,
    `to_status` VARCHAR(32) NOT NULL,
    `reason` VARCHAR(1000) NULL,
    `changed_by_type` VARCHAR(24) NOT NULL,
    `changed_by_id` VARCHAR(80) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`order_status_history_id`),
    KEY `idx_v2_order_history_order_time` (`order_id`, `created_at`),
    CONSTRAINT `fk_v2_order_history_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_order_history_actor` CHECK
        (`changed_by_type` IN ('USER','SYSTEM','PROVIDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- F. Reproducibility and mining (6 tables).
-- ---------------------------------------------------------------------------
CREATE TABLE `ITEM_MAPPING` (
    `product_id` VARCHAR(40) NOT NULL,
    `item_id` INT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`product_id`),
    UNIQUE KEY `uk_v2_mapping_item` (`item_id`),
    CONSTRAINT `fk_v2_mapping_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_mapping_item` CHECK (`item_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `DATASET_SNAPSHOT` (
    `snapshot_id` VARCHAR(40) NOT NULL,
    `source_type` VARCHAR(32) NOT NULL,
    `source_name` VARCHAR(255) NOT NULL,
    `from_time` DATETIME(6) NULL,
    `to_time` DATETIME(6) NULL,
    `raw_file_path` VARCHAR(1024) NULL,
    `raw_sha256` CHAR(64) NULL,
    `sequence_file_path` VARCHAR(1024) NULL,
    `sequence_sha256` CHAR(64) NULL,
    `mapping_file_path` VARCHAR(1024) NULL,
    `mapping_sha256` CHAR(64) NULL,
    `validation_status` VARCHAR(32) NOT NULL,
    `conversion_report_json` JSON NOT NULL,
    `sequence_count` BIGINT UNSIGNED NOT NULL,
    `single_itemset_sequence_count` BIGINT UNSIGNED NULL,
    `multi_itemset_sequence_count` BIGINT UNSIGNED NULL,
    `itemset_count` BIGINT UNSIGNED NULL,
    `distinct_item_count` BIGINT UNSIGNED NULL,
    `total_item_occurrences` BIGINT UNSIGNED NULL,
    `min_sequence_length` INT UNSIGNED NULL,
    `max_sequence_length` INT UNSIGNED NULL,
    `average_sequence_length` DOUBLE NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`snapshot_id`),
    KEY `idx_v2_snapshot_source_time` (`source_name`, `created_at`),
    CONSTRAINT `chk_v2_snapshot_validation` CHECK
        (`validation_status` IN ('PENDING','VALID','INVALID','LEGACY_UNVERIFIED')),
    CONSTRAINT `chk_v2_snapshot_window` CHECK
        (`from_time` IS NULL OR `to_time` IS NULL OR `to_time` >= `from_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `MINING_RUN` (
    `run_id` VARCHAR(40) NOT NULL,
    `snapshot_id` VARCHAR(40) NOT NULL,
    `algorithm` VARCHAR(32) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `dataset_source` VARCHAR(255) NOT NULL,
    `minsup_mode` VARCHAR(32) NOT NULL,
    `minsup_configured` DOUBLE NOT NULL,
    `minsup_relative_fraction` DOUBLE NULL,
    `effective_threshold` DOUBLE NOT NULL,
    `max_w` DOUBLE NULL,
    `min_w` DOUBLE NULL,
    `mean_w` DOUBLE NOT NULL,
    `pattern_count` BIGINT UNSIGNED NULL,
    `candidate_count` BIGINT UNSIGNED NULL,
    `dataset_io_ms` BIGINT UNSIGNED NULL,
    `preprocessing_ms` BIGINT UNSIGNED NULL,
    `dfs_mining_ms` BIGINT UNSIGNED NULL,
    `complete_algorithm_ms` BIGINT UNSIGNED NULL,
    `output_io_ms` BIGINT UNSIGNED NULL,
    `total_execution_ms` BIGINT UNSIGNED NULL,
    `peak_sampled_jvm_heap_bytes` BIGINT UNSIGNED NULL,
    `legacy_metadata_json` JSON NULL,
    `error_message` VARCHAR(2000) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `started_at` DATETIME(6) NULL,
    `finished_at` DATETIME(6) NULL,
    PRIMARY KEY (`run_id`),
    KEY `idx_v2_run_snapshot_algorithm` (`snapshot_id`, `algorithm`),
    KEY `idx_v2_run_status_time` (`status`, `created_at`),
    CONSTRAINT `fk_v2_run_snapshot` FOREIGN KEY (`snapshot_id`)
        REFERENCES `DATASET_SNAPSHOT` (`snapshot_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_run_algorithm` CHECK
        (`algorithm` IN ('WSPM_PRETREE','WSPM_BITPRUNE')),
    CONSTRAINT `chk_v2_run_status` CHECK
        (`status` IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','LEGACY_UNVERIFIED')),
    CONSTRAINT `chk_v2_run_minsup` CHECK
        (`minsup_configured` >= 0 AND `effective_threshold` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `RUN_ITEM_STATISTIC` (
    `run_id` VARCHAR(40) NOT NULL,
    `item_id` INT UNSIGNED NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `occurrence_count` BIGINT UNSIGNED NOT NULL,
    `sequence_support` BIGINT UNSIGNED NOT NULL,
    `item_weight` DOUBLE NULL,
    `product_name_snapshot` VARCHAR(200) NOT NULL,
    `sku_snapshot` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`run_id`, `item_id`),
    UNIQUE KEY `uk_v2_run_stat_product` (`run_id`, `product_id`),
    KEY `idx_v2_run_stat_product` (`product_id`),
    CONSTRAINT `fk_v2_run_stat_run` FOREIGN KEY (`run_id`)
        REFERENCES `MINING_RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_run_stat_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PATTERN` (
    `pattern_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id` VARCHAR(40) NOT NULL,
    `canonical_pattern` TEXT NOT NULL,
    `canonical_pattern_hash` CHAR(64) NOT NULL,
    `support_abs` BIGINT UNSIGNED NOT NULL,
    `support_ratio` DOUBLE NOT NULL,
    `weighted_support` DOUBLE NOT NULL,
    `pattern_item_count` INT UNSIGNED NOT NULL,
    `itemset_count` INT UNSIGNED NOT NULL,
    `pattern_weight` DOUBLE NOT NULL,
    `pattern_weight_policy` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`pattern_id`),
    UNIQUE KEY `uk_v2_pattern_run_hash` (`run_id`, `canonical_pattern_hash`),
    KEY `idx_v2_pattern_run_rank` (`run_id`, `support_abs`, `pattern_weight`),
    CONSTRAINT `fk_v2_pattern_run` FOREIGN KEY (`run_id`)
        REFERENCES `MINING_RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_pattern_counts` CHECK
        (`pattern_item_count` > 0 AND `itemset_count` > 0),
    CONSTRAINT `chk_v2_pattern_support` CHECK
        (`support_ratio` >= 0 AND `support_ratio` <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PATTERN_ITEM` (
    `pattern_id` BIGINT UNSIGNED NOT NULL,
    `itemset_order` INT UNSIGNED NOT NULL,
    `item_order` INT UNSIGNED NOT NULL,
    `item_id` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`pattern_id`, `itemset_order`, `item_order`),
    CONSTRAINT `fk_v2_pattern_item_pattern` FOREIGN KEY (`pattern_id`)
        REFERENCES `PATTERN` (`pattern_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_pattern_item_order` CHECK
        (`itemset_order` > 0 AND `item_order` > 0 AND `item_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- G. Combo generation and publication (5 tables).
-- ---------------------------------------------------------------------------
CREATE TABLE `COMBO_BATCH` (
    `batch_id` VARCHAR(40) NOT NULL,
    `run_id` VARCHAR(40) NOT NULL,
    `ranking_policy` VARCHAR(80) NOT NULL,
    `pattern_weight_policy` VARCHAR(80) NOT NULL,
    `top_k` INT UNSIGNED NOT NULL,
    `category_filter_enabled` BOOLEAN NOT NULL DEFAULT FALSE,
    `min_product_count` INT UNSIGNED NOT NULL DEFAULT 2,
    `max_product_count` INT UNSIGNED NOT NULL DEFAULT 4,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `created_by` VARCHAR(40) NULL,
    `activated_by` VARCHAR(40) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `activated_at` DATETIME(6) NULL,
    PRIMARY KEY (`batch_id`),
    KEY `idx_v2_batch_run_status` (`run_id`, `status`),
    KEY `idx_v2_batch_active` (`status`, `activated_at`),
    CONSTRAINT `fk_v2_batch_run` FOREIGN KEY (`run_id`)
        REFERENCES `MINING_RUN` (`run_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_batch_creator` FOREIGN KEY (`created_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_batch_activator` FOREIGN KEY (`activated_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_batch_status` CHECK (`status` IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT `chk_v2_batch_size` CHECK
        (`top_k` > 0 AND `min_product_count` >= 2
         AND `max_product_count` >= `min_product_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `COMBO` (
    `combo_id` VARCHAR(40) NOT NULL,
    `batch_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `combo_kind` VARCHAR(40) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    `discount_type` VARCHAR(24) NOT NULL DEFAULT 'NONE',
    `discount_value` DECIMAL(19,4) NULL,
    `max_discount_amount` DECIMAL(19,2) NULL,
    `stacking_policy` VARCHAR(24) NOT NULL DEFAULT 'NON_STACKABLE',
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `reference_original_price` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `reference_sale_price` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `support_abs` BIGINT UNSIGNED NOT NULL,
    `support_ratio` DOUBLE NOT NULL,
    `pattern_weight` DOUBLE NOT NULL,
    `explanation` VARCHAR(1000) NOT NULL,
    `valid_from` DATETIME(6) NULL,
    `valid_to` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `reviewed_at` DATETIME(6) NULL,
    `reviewed_by` VARCHAR(40) NULL,
    `review_note` VARCHAR(1000) NULL,
    `published_at` DATETIME(6) NULL,
    PRIMARY KEY (`combo_id`),
    KEY `idx_v2_combo_batch_status_validity`
        (`batch_id`, `status`, `valid_from`, `valid_to`),
    KEY `idx_v2_combo_rank` (`batch_id`, `support_abs`, `pattern_weight`),
    CONSTRAINT `fk_v2_combo_batch` FOREIGN KEY (`batch_id`)
        REFERENCES `COMBO_BATCH` (`batch_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_combo_reviewer` FOREIGN KEY (`reviewed_by`)
        REFERENCES `USER_ACCOUNT` (`user_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_combo_kind` CHECK (`combo_kind` IN
        ('SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL','MIXED_SEQUENCE')),
    CONSTRAINT `chk_v2_combo_status` CHECK (`status` IN
        ('DRAFT','APPROVED','REJECTED','PUBLISHED','ARCHIVED')),
    CONSTRAINT `chk_v2_combo_discount_type` CHECK (`discount_type` IN
        ('NONE','PERCENTAGE','FIXED_AMOUNT','FIXED_PRICE')),
    CONSTRAINT `chk_v2_combo_stacking` CHECK (`stacking_policy` IN ('NON_STACKABLE')),
    CONSTRAINT `chk_v2_combo_money` CHECK
        (`reference_original_price` >= 0 AND `reference_sale_price` >= 0
         AND (`discount_value` IS NULL OR `discount_value` >= 0)
         AND (`max_discount_amount` IS NULL OR `max_discount_amount` >= 0)),
    CONSTRAINT `chk_v2_combo_percentage` CHECK
        (`discount_type` <> 'PERCENTAGE'
         OR (`discount_value` > 0 AND `discount_value` <= 100)),
    CONSTRAINT `chk_v2_combo_validity` CHECK
        (`valid_from` IS NULL OR `valid_to` IS NULL OR `valid_to` > `valid_from`),
    CONSTRAINT `chk_v2_combo_support_ratio` CHECK
        (`support_ratio` >= 0 AND `support_ratio` <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `COMBO_ITEM` (
    `combo_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `itemset_order` INT UNSIGNED NOT NULL,
    `item_order` INT UNSIGNED NOT NULL,
    `quantity` INT UNSIGNED NOT NULL DEFAULT 1,
    `unit_price_snapshot` DECIMAL(19,2) NOT NULL,
    `is_anchor_eligible` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_discount_eligible` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`combo_id`, `itemset_order`, `item_order`),
    UNIQUE KEY `uk_v2_combo_item_product` (`combo_id`, `product_id`),
    KEY `idx_v2_combo_item_anchor` (`product_id`, `is_anchor_eligible`, `combo_id`),
    CONSTRAINT `fk_v2_combo_item_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_combo_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_combo_item` CHECK
        (`itemset_order` > 0 AND `item_order` > 0 AND `quantity` > 0
         AND `unit_price_snapshot` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `COMBO_SOURCE` (
    `combo_id` VARCHAR(40) NOT NULL,
    `pattern_id` BIGINT UNSIGNED NOT NULL,
    `is_primary` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`combo_id`, `pattern_id`),
    KEY `idx_v2_combo_source_pattern` (`pattern_id`),
    CONSTRAINT `fk_v2_combo_source_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_combo_source_pattern` FOREIGN KEY (`pattern_id`)
        REFERENCES `PATTERN` (`pattern_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CATEGORY_EXCLUSION` (
    `category_id_low` VARCHAR(40) NOT NULL,
    `category_id_high` VARCHAR(40) NOT NULL,
    `reason` VARCHAR(500) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`category_id_low`, `category_id_high`),
    CONSTRAINT `fk_v2_exclusion_low` FOREIGN KEY (`category_id_low`)
        REFERENCES `CATEGORY` (`category_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_exclusion_high` FOREIGN KEY (`category_id_high`)
        REFERENCES `CATEGORY` (`category_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_exclusion_order` CHECK (`category_id_low` < `category_id_high`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Applied combo snapshots can now reference the final COMBO table.
CREATE TABLE `ORDER_APPLIED_COMBO` (
    `order_applied_combo_id` VARCHAR(40) NOT NULL,
    `order_id` VARCHAR(40) NOT NULL,
    `combo_id` VARCHAR(40) NOT NULL,
    `combo_name_snapshot` VARCHAR(255) NOT NULL,
    `discount_type_snapshot` VARCHAR(24) NOT NULL,
    `discount_value_snapshot` DECIMAL(19,4) NULL,
    `currency_code` CHAR(3) NOT NULL,
    `list_subtotal` DECIMAL(19,2) NOT NULL,
    `discount_base_subtotal` DECIMAL(19,2) NOT NULL,
    `discount_amount` DECIMAL(19,2) NOT NULL,
    `final_subtotal` DECIMAL(19,2) NOT NULL,
    `applied_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`order_applied_combo_id`),
    UNIQUE KEY `uk_v2_order_combo_identity` (`order_applied_combo_id`, `order_id`),
    KEY `idx_v2_order_combo_order` (`order_id`),
    KEY `idx_v2_order_combo_source` (`combo_id`, `applied_at`),
    CONSTRAINT `fk_v2_order_combo_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_order_combo_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_order_combo_money` CHECK
        (`list_subtotal` >= 0 AND `discount_base_subtotal` >= 0
         AND `discount_amount` >= 0 AND `final_subtotal` >= 0
         AND `list_subtotal` - `discount_amount` = `final_subtotal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `ORDER_ITEM`
    ADD CONSTRAINT `fk_v2_order_item_applied_combo`
        FOREIGN KEY (`order_applied_combo_id`, `order_id`)
        REFERENCES `ORDER_APPLIED_COMBO` (`order_applied_combo_id`, `order_id`)
        ON DELETE RESTRICT;

-- Final inventory table depends on ORDER_ITEM and GOODS_RECEIPT_ITEM.
CREATE TABLE `INVENTORY_SERIAL` (
    `inventory_serial_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `serial_number` VARCHAR(160) NOT NULL,
    `warehouse_id` VARCHAR(40) NOT NULL,
    `goods_receipt_item_id` BIGINT UNSIGNED NOT NULL,
    `order_item_id` BIGINT UNSIGNED NULL,
    `status` VARCHAR(24) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`inventory_serial_id`),
    UNIQUE KEY `uk_v2_inventory_serial_number` (`serial_number`),
    KEY `idx_v2_inventory_serial_stock` (`warehouse_id`, `product_id`, `status`),
    CONSTRAINT `fk_v2_inventory_serial_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_inventory_serial_warehouse` FOREIGN KEY (`warehouse_id`)
        REFERENCES `WAREHOUSE` (`warehouse_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_inventory_serial_receipt_product`
        FOREIGN KEY (`goods_receipt_item_id`, `product_id`)
        REFERENCES `GOODS_RECEIPT_ITEM` (`goods_receipt_item_id`, `product_id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_inventory_serial_order_item` FOREIGN KEY (`order_item_id`)
        REFERENCES `ORDER_ITEM` (`order_item_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_inventory_serial_status` CHECK (`status` IN
        ('IN_STOCK','RESERVED','SOLD','RETURNED','DAMAGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- H. Cart and checkout (6 tables).
-- ---------------------------------------------------------------------------
CREATE TABLE `CART` (
    `cart_id` VARCHAR(40) NOT NULL,
    `customer_id` VARCHAR(40) NULL,
    `guest_token_hash` VARCHAR(255) NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `version` BIGINT NOT NULL DEFAULT 0,
    `expires_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`cart_id`),
    UNIQUE KEY `uk_v2_cart_guest_hash` (`guest_token_hash`),
    KEY `idx_v2_cart_customer_status` (`customer_id`, `status`),
    CONSTRAINT `fk_v2_cart_customer` FOREIGN KEY (`customer_id`)
        REFERENCES `CUSTOMER` (`customer_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_cart_owner` CHECK
        ((`customer_id` IS NOT NULL) <> (`guest_token_hash` IS NOT NULL)),
    CONSTRAINT `chk_v2_cart_status` CHECK
        (`status` IN ('ACTIVE','CONVERTED','ABANDONED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CART_APPLIED_COMBO` (
    `cart_applied_combo_id` VARCHAR(40) NOT NULL,
    `cart_id` VARCHAR(40) NOT NULL,
    `combo_id` VARCHAR(40) NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'SELECTED',
    `estimated_list_subtotal` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `estimated_discount_amount` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `estimated_final_subtotal` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `calculated_at` DATETIME(6) NOT NULL,
    `invalid_reason` VARCHAR(500) NULL,
    PRIMARY KEY (`cart_applied_combo_id`),
    UNIQUE KEY `uk_v2_cart_combo_identity` (`cart_applied_combo_id`, `cart_id`),
    KEY `idx_v2_cart_combo_cart_status` (`cart_id`, `status`),
    CONSTRAINT `fk_v2_cart_combo_cart` FOREIGN KEY (`cart_id`)
        REFERENCES `CART` (`cart_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_cart_combo_combo` FOREIGN KEY (`combo_id`)
        REFERENCES `COMBO` (`combo_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_cart_combo_status` CHECK
        (`status` IN ('SELECTED','INVALIDATED','REMOVED')),
    CONSTRAINT `chk_v2_cart_combo_money` CHECK
        (`estimated_list_subtotal` >= 0 AND `estimated_discount_amount` >= 0
         AND `estimated_final_subtotal` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CART_ITEM` (
    `cart_item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `cart_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `cart_applied_combo_id` VARCHAR(40) NULL,
    `quantity` INT UNSIGNED NOT NULL,
    `estimated_unit_price` DECIMAL(19,2) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`cart_item_id`),
    KEY `idx_v2_cart_item_cart` (`cart_id`),
    KEY `idx_v2_cart_item_product` (`product_id`),
    KEY `idx_v2_cart_item_combo_cart` (`cart_applied_combo_id`, `cart_id`),
    CONSTRAINT `fk_v2_cart_item_cart` FOREIGN KEY (`cart_id`)
        REFERENCES `CART` (`cart_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_cart_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_cart_item_combo` FOREIGN KEY (`cart_applied_combo_id`, `cart_id`)
        REFERENCES `CART_APPLIED_COMBO` (`cart_applied_combo_id`, `cart_id`)
        ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_cart_item_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `chk_v2_cart_item_price` CHECK (`estimated_unit_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CHECKOUT_SESSION` (
    `checkout_id` VARCHAR(40) NOT NULL,
    `cart_id` VARCHAR(40) NOT NULL,
    `customer_id` VARCHAR(40) NULL,
    `idempotency_scope` VARCHAR(128) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `cart_version_snapshot` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    `currency_code` CHAR(3) NOT NULL DEFAULT 'VND',
    `list_subtotal` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `combo_discount_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `other_discount_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `shipping_fee` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `tax_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `grand_total` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `quote_expires_at` DATETIME(6) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(80) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    `completed_at` DATETIME(6) NULL,
    PRIMARY KEY (`checkout_id`),
    UNIQUE KEY `uk_v2_checkout_idempotency` (`idempotency_scope`, `idempotency_key`),
    KEY `idx_v2_checkout_cart_status` (`cart_id`, `status`),
    KEY `idx_v2_checkout_customer_status` (`customer_id`, `status`),
    CONSTRAINT `fk_v2_checkout_cart` FOREIGN KEY (`cart_id`)
        REFERENCES `CART` (`cart_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_checkout_customer` FOREIGN KEY (`customer_id`)
        REFERENCES `CUSTOMER` (`customer_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_checkout_status` CHECK (`status` IN
        ('CREATED','QUOTED','CONFIRMED','PAYMENT_PENDING','COMPLETED',
         'FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT `chk_v2_checkout_money` CHECK
        (`list_subtotal` >= 0 AND `combo_discount_total` >= 0
         AND `other_discount_total` >= 0 AND `shipping_fee` >= 0
         AND `tax_total` >= 0 AND `grand_total` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `CHECKOUT_ADDRESS` (
    `checkout_address_id` VARCHAR(40) NOT NULL,
    `checkout_id` VARCHAR(40) NOT NULL,
    `address_type` VARCHAR(16) NOT NULL,
    `recipient_name` VARCHAR(160) NOT NULL,
    `phone` VARCHAR(32) NOT NULL,
    `address_line` VARCHAR(500) NOT NULL,
    `ward_code` VARCHAR(32) NULL,
    `ward_name` VARCHAR(160) NOT NULL,
    `district_code` VARCHAR(32) NULL,
    `district_name` VARCHAR(160) NOT NULL,
    `province_code` VARCHAR(32) NULL,
    `province_name` VARCHAR(160) NOT NULL,
    `postal_code` VARCHAR(20) NULL,
    `country_code` CHAR(2) NOT NULL DEFAULT 'VN',
    PRIMARY KEY (`checkout_address_id`),
    UNIQUE KEY `uk_v2_checkout_address_type` (`checkout_id`, `address_type`),
    CONSTRAINT `fk_v2_checkout_address_checkout` FOREIGN KEY (`checkout_id`)
        REFERENCES `CHECKOUT_SESSION` (`checkout_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_checkout_address_type` CHECK
        (`address_type` IN ('SHIPPING','BILLING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `STOCK_RESERVATION` (
    `reservation_id` VARCHAR(40) NOT NULL,
    `checkout_id` VARCHAR(40) NOT NULL,
    `warehouse_id` VARCHAR(40) NOT NULL,
    `product_id` VARCHAR(40) NOT NULL,
    `quantity` INT UNSIGNED NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    `expires_at` DATETIME(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `consumed_at` DATETIME(6) NULL,
    `released_at` DATETIME(6) NULL,
    PRIMARY KEY (`reservation_id`),
    UNIQUE KEY `uk_v2_reservation_item` (`checkout_id`, `warehouse_id`, `product_id`),
    KEY `idx_v2_reservation_expiry` (`status`, `expires_at`),
    KEY `idx_v2_reservation_stock` (`warehouse_id`, `product_id`, `status`),
    CONSTRAINT `fk_v2_reservation_checkout` FOREIGN KEY (`checkout_id`)
        REFERENCES `CHECKOUT_SESSION` (`checkout_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_reservation_warehouse` FOREIGN KEY (`warehouse_id`)
        REFERENCES `WAREHOUSE` (`warehouse_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_reservation_product` FOREIGN KEY (`product_id`)
        REFERENCES `PRODUCT` (`product_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_reservation_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `chk_v2_reservation_status` CHECK
        (`status` IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `ORDERS`
    ADD CONSTRAINT `fk_v2_order_checkout` FOREIGN KEY (`checkout_id`)
        REFERENCES `CHECKOUT_SESSION` (`checkout_id`) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------
-- I. Payments (4 tables).
-- ---------------------------------------------------------------------------
CREATE TABLE `PAYMENT` (
    `payment_id` VARCHAR(40) NOT NULL,
    `order_id` VARCHAR(40) NOT NULL,
    `method` VARCHAR(32) NOT NULL,
    `currency_code` CHAR(3) NOT NULL,
    `amount` DECIMAL(19,2) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `paid_amount` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `refunded_amount` DECIMAL(19,2) NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    `paid_at` DATETIME(6) NULL,
    PRIMARY KEY (`payment_id`),
    UNIQUE KEY `uk_v2_payment_order` (`order_id`),
    KEY `idx_v2_payment_status_time` (`status`, `created_at`),
    CONSTRAINT `fk_v2_payment_order` FOREIGN KEY (`order_id`)
        REFERENCES `ORDERS` (`order_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_payment_method` CHECK
        (`method` IN ('COD','BANK_TRANSFER','E_WALLET','CARD')),
    CONSTRAINT `chk_v2_payment_status` CHECK (`status` IN
        ('PENDING','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED',
         'FAILED','CANCELLED')),
    CONSTRAINT `chk_v2_payment_money` CHECK
        (`amount` >= 0 AND `paid_amount` >= 0 AND `refunded_amount` >= 0
         AND `refunded_amount` <= `paid_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PAYMENT_ATTEMPT` (
    `payment_attempt_id` VARCHAR(40) NOT NULL,
    `payment_id` VARCHAR(40) NOT NULL,
    `provider` VARCHAR(40) NOT NULL,
    `method` VARCHAR(32) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `provider_payment_id` VARCHAR(160) NULL,
    `amount` DECIMAL(19,2) NOT NULL,
    `currency_code` CHAR(3) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    `redirect_url` VARCHAR(2048) NULL,
    `expires_at` DATETIME(6) NULL,
    `failure_code` VARCHAR(80) NULL,
    `failure_message` VARCHAR(1000) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`payment_attempt_id`),
    UNIQUE KEY `uk_v2_attempt_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_v2_attempt_provider_payment` (`provider`, `provider_payment_id`),
    KEY `idx_v2_attempt_payment_status` (`payment_id`, `status`),
    CONSTRAINT `fk_v2_attempt_payment` FOREIGN KEY (`payment_id`)
        REFERENCES `PAYMENT` (`payment_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_attempt_method` CHECK
        (`method` IN ('COD','BANK_TRANSFER','E_WALLET','CARD')),
    CONSTRAINT `chk_v2_attempt_status` CHECK (`status` IN
        ('CREATED','PENDING','AUTHORIZED','SUCCEEDED','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT `chk_v2_attempt_amount` CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PAYMENT_TRANSACTION` (
    `payment_transaction_id` VARCHAR(40) NOT NULL,
    `payment_id` VARCHAR(40) NOT NULL,
    `payment_attempt_id` VARCHAR(40) NULL,
    `provider` VARCHAR(40) NOT NULL,
    `type` VARCHAR(24) NOT NULL,
    `provider_transaction_id` VARCHAR(160) NOT NULL,
    `amount` DECIMAL(19,2) NOT NULL,
    `currency_code` CHAR(3) NOT NULL,
    `status` VARCHAR(24) NOT NULL,
    `processed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`payment_transaction_id`),
    UNIQUE KEY `uk_v2_transaction_provider_id` (`provider`, `provider_transaction_id`),
    KEY `idx_v2_transaction_payment_type` (`payment_id`, `type`, `status`),
    CONSTRAINT `fk_v2_transaction_payment` FOREIGN KEY (`payment_id`)
        REFERENCES `PAYMENT` (`payment_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_v2_transaction_attempt` FOREIGN KEY (`payment_attempt_id`)
        REFERENCES `PAYMENT_ATTEMPT` (`payment_attempt_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_transaction_type` CHECK
        (`type` IN ('AUTHORIZE','CAPTURE','REFUND','VOID')),
    CONSTRAINT `chk_v2_transaction_status` CHECK
        (`status` IN ('PENDING','SUCCEEDED','FAILED')),
    CONSTRAINT `chk_v2_transaction_amount` CHECK (`amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `PAYMENT_WEBHOOK_EVENT` (
    `webhook_event_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `provider` VARCHAR(40) NOT NULL,
    `provider_event_id` VARCHAR(160) NOT NULL,
    `payment_attempt_id` VARCHAR(40) NULL,
    `event_type` VARCHAR(80) NOT NULL,
    `payload_hash` VARCHAR(128) NOT NULL,
    `signature_valid` BOOLEAN NOT NULL,
    `status` VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    `received_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `processed_at` DATETIME(6) NULL,
    `error_message` VARCHAR(1000) NULL,
    PRIMARY KEY (`webhook_event_id`),
    UNIQUE KEY `uk_v2_webhook_provider_event` (`provider`, `provider_event_id`),
    KEY `idx_v2_webhook_attempt` (`payment_attempt_id`),
    KEY `idx_v2_webhook_status_time` (`status`, `received_at`),
    CONSTRAINT `fk_v2_webhook_attempt` FOREIGN KEY (`payment_attempt_id`)
        REFERENCES `PAYMENT_ATTEMPT` (`payment_attempt_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_v2_webhook_status` CHECK
        (`status` IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- J. Copy V1 data into the target schema.
-- No synthetic admin, catalog, warehouse, payment or mining result data is
-- seeded here. That belongs to an explicit profile-specific seed workflow.
-- ---------------------------------------------------------------------------
INSERT INTO `CUSTOMER` (`customer_id`, `user_id`, `anonymous_code`, `segment`, `created_at`)
SELECT
    `customer_id`,
    NULL,
    CONCAT('legacy-', LEFT(SHA2(`customer_id`, 256), 64)),
    NULL,
    CURRENT_TIMESTAMP(6)
FROM `LEGACY_V1_CUSTOMER`;

INSERT INTO `CATEGORY`
    (`category_id`, `name`, `slug`, `description`, `display_order`, `status`)
SELECT
    `category_id`,
    `category_name`,
    CONCAT('legacy-', LEFT(SHA2(`category_id`, 256), 40)),
    NULL,
    0,
    'ACTIVE'
FROM `LEGACY_V1_CATEGORY`;

INSERT INTO `PRODUCT`
    (`product_id`, `sku`, `name`, `slug`, `category_id`, `brand_id`,
     `short_description`, `description`, `specifications_json`, `current_price`,
     `currency_code`, `thumbnail_url`, `track_serial`, `status`,
     `price_updated_at`, `version`, `created_at`, `updated_at`)
SELECT
    `product_id`,
    CONCAT('LEGACY-', LEFT(SHA2(`product_id`, 256), 32)),
    `product_name`,
    CONCAT('legacy-', LEFT(SHA2(`product_id`, 256), 40)),
    `category_id`,
    NULL,
    NULL,
    NULL,
    NULL,
    `price`,
    'VND',
    NULL,
    FALSE,
    CASE
        WHEN `product_status` IN ('ACTIVE','INACTIVE','DRAFT','DISCONTINUED')
            THEN `product_status`
        ELSE 'INACTIVE'
    END,
    CURRENT_TIMESTAMP(6),
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM `LEGACY_V1_PRODUCT`;

INSERT INTO `ORDERS`
    (`order_id`, `checkout_id`, `order_number`, `customer_id`, `ordered_at`,
     `completed_at`, `status`, `source`, `currency_code`, `list_subtotal`,
     `combo_discount_total`, `other_discount_total`, `shipping_fee`, `tax_total`,
     `grand_total`, `created_at`, `updated_at`)
SELECT
    `order_id`,
    NULL,
    CONCAT('LEGACY-', `order_id`),
    `customer_id`,
    `order_time`,
    CASE WHEN `order_status` = 'COMPLETED' THEN `order_time` ELSE NULL END,
    CASE `order_status`
        WHEN 'PENDING' THEN 'PENDING_PAYMENT'
        WHEN 'COMPLETED' THEN 'COMPLETED'
        WHEN 'CANCELLED' THEN 'CANCELLED'
        WHEN 'RETURNED' THEN 'RETURNED'
        ELSE 'CANCELLED'
    END,
    'IMPORT',
    'VND',
    0, 0, 0, 0, 0, 0,
    `order_time`,
    `order_time`
FROM `LEGACY_V1_ORDERS`;

INSERT INTO `ORDER_ITEM`
    (`order_id`, `product_id`, `order_applied_combo_id`, `quantity`,
     `list_unit_price`, `allocated_discount_amount`, `line_total`,
     `sku_snapshot`, `product_name_snapshot`)
SELECT
    oi.`order_id`,
    oi.`product_id`,
    NULL,
    oi.`quantity`,
    oi.`unit_price`,
    0,
    oi.`unit_price` * oi.`quantity`,
    p.`sku`,
    p.`name`
FROM `LEGACY_V1_ORDER_ITEM` oi
JOIN `PRODUCT` p ON p.`product_id` = oi.`product_id`;

UPDATE `ORDERS` o
JOIN (
    SELECT `order_id`, SUM(`line_total`) AS `calculated_total`
    FROM `ORDER_ITEM`
    GROUP BY `order_id`
) t ON t.`order_id` = o.`order_id`
SET
    o.`list_subtotal` = t.`calculated_total`,
    o.`grand_total` = t.`calculated_total`;

INSERT INTO `ORDER_STATUS_HISTORY`
    (`order_id`, `from_status`, `to_status`, `reason`,
     `changed_by_type`, `changed_by_id`, `created_at`)
SELECT
    `order_id`, NULL, `status`, 'Migrated from V1', 'SYSTEM', 'flyway-v2', `created_at`
FROM `ORDERS`;

INSERT INTO `ITEM_MAPPING` (`product_id`, `item_id`, `created_at`)
SELECT `product_id`, `item_id`, CURRENT_TIMESTAMP(6)
FROM `LEGACY_V1_ITEM_MAPPING`;

INSERT INTO `DATASET_SNAPSHOT`
    (`snapshot_id`, `source_type`, `source_name`, `from_time`, `to_time`,
     `raw_file_path`, `raw_sha256`, `sequence_file_path`, `sequence_sha256`,
     `mapping_file_path`, `mapping_sha256`, `validation_status`,
     `conversion_report_json`, `sequence_count`,
     `single_itemset_sequence_count`, `multi_itemset_sequence_count`,
     `itemset_count`, `distinct_item_count`, `total_item_occurrences`,
     `min_sequence_length`, `max_sequence_length`, `average_sequence_length`,
     `created_at`)
SELECT
    r.`run_id`,
    'LEGACY_PATH',
    r.`dataset_source`,
    NULL,
    NULL,
    r.`input_snapshot`,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'LEGACY_UNVERIFIED',
    JSON_OBJECT(
        'migration', 'V2',
        'reason', 'V1 did not store content checksums or conversion statistics',
        'legacyInputSnapshot', r.`input_snapshot`
    ),
    r.`sequence_count`,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    r.`run_time`
FROM `LEGACY_V1_RUN` r;

INSERT INTO `MINING_RUN`
    (`run_id`, `snapshot_id`, `algorithm`, `status`, `dataset_source`,
     `minsup_mode`, `minsup_configured`, `minsup_relative_fraction`,
     `effective_threshold`, `max_w`, `min_w`, `mean_w`, `pattern_count`,
     `candidate_count`, `dataset_io_ms`, `preprocessing_ms`, `dfs_mining_ms`,
     `complete_algorithm_ms`, `output_io_ms`, `total_execution_ms`,
     `peak_sampled_jvm_heap_bytes`, `legacy_metadata_json`, `error_message`,
     `created_at`, `started_at`, `finished_at`)
SELECT
    r.`run_id`,
    r.`run_id`,
    CASE
        WHEN r.`algorithm` IN ('WSPM_PRETREE','WSPM_BITPRUNE') THEN r.`algorithm`
        ELSE 'WSPM_PRETREE'
    END,
    'LEGACY_UNVERIFIED',
    r.`dataset_source`,
    'LEGACY_V1',
    r.`minsup_ratio`,
    r.`minsup_ratio`,
    r.`minsup_abs`,
    NULL,
    NULL,
    r.`mean_w`,
    r.`pattern_count`,
    r.`candidate_count`,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    JSON_OBJECT(
        'legacyAlgorithm', r.`algorithm`,
        'legacyExecutionTime', r.`execution_time`,
        'legacyMemoryUsage', r.`memory_usage`,
        'legacyTopK', r.`top_k`,
        'legacyCategoryFilter', r.`category_filter`,
        'legacyCoefAlpha', r.`coef_alpha`,
        'legacyCoefBeta', r.`coef_beta`,
        'legacyCoefGamma', r.`coef_gamma`,
        'legacyCoefDelta', r.`coef_delta`
    ),
    NULL,
    r.`run_time`,
    r.`run_time`,
    r.`run_time`
FROM `LEGACY_V1_RUN` r;

-- V1 lacks reproducible item-weight provenance. Preserve dictionary rows used by
-- patterns, but leave item_weight NULL and keep the run LEGACY_UNVERIFIED.
INSERT INTO `RUN_ITEM_STATISTIC`
    (`run_id`, `item_id`, `product_id`, `occurrence_count`, `sequence_support`,
     `item_weight`, `product_name_snapshot`, `sku_snapshot`)
SELECT DISTINCT
    lp.`run_id`,
    lpi.`item_id`,
    im.`product_id`,
    0,
    0,
    NULL,
    p.`name`,
    p.`sku`
FROM `LEGACY_V1_PATTERN` lp
JOIN `LEGACY_V1_PATTERN_ITEM` lpi ON lpi.`pattern_id` = lp.`pattern_id`
JOIN `ITEM_MAPPING` im ON im.`item_id` = lpi.`item_id`
JOIN `PRODUCT` p ON p.`product_id` = im.`product_id`;

SET SESSION group_concat_max_len = 1048576;

INSERT INTO `PATTERN`
    (`pattern_id`, `run_id`, `canonical_pattern`, `canonical_pattern_hash`,
     `support_abs`, `support_ratio`, `weighted_support`, `pattern_item_count`,
     `itemset_count`, `pattern_weight`, `pattern_weight_policy`)
SELECT
    lp.`pattern_id`,
    lp.`run_id`,
    c.`canonical_pattern`,
    SHA2(c.`canonical_pattern`, 256),
    lp.`support`,
    CASE WHEN lr.`sequence_count` = 0 THEN 0
         ELSE lp.`support` / lr.`sequence_count` END,
    lp.`support` * lr.`mean_w`,
    lp.`pattern_length`,
    c.`itemset_count`,
    lp.`weight`,
    'LEGACY_V1_WEIGHT'
FROM `LEGACY_V1_PATTERN` lp
JOIN `LEGACY_V1_RUN` lr ON lr.`run_id` = lp.`run_id`
JOIN (
    SELECT
        grouped.`pattern_id`,
        COUNT(*) AS `itemset_count`,
        CONCAT(
            '<',
            GROUP_CONCAT(
                CONCAT('(', grouped.`itemset_items`, ')')
                ORDER BY grouped.`itemset_order` SEPARATOR ''
            ),
            '>'
        ) AS `canonical_pattern`
    FROM (
        SELECT
            `pattern_id`,
            `itemset_order`,
            GROUP_CONCAT(`item_id` ORDER BY `item_order` SEPARATOR ' ') AS `itemset_items`
        FROM `LEGACY_V1_PATTERN_ITEM`
        GROUP BY `pattern_id`, `itemset_order`
    ) grouped
    GROUP BY grouped.`pattern_id`
) c ON c.`pattern_id` = lp.`pattern_id`;

INSERT INTO `PATTERN_ITEM` (`pattern_id`, `itemset_order`, `item_order`, `item_id`)
SELECT `pattern_id`, `itemset_order`, `item_order`, CAST(`item_id` AS UNSIGNED)
FROM `LEGACY_V1_PATTERN_ITEM`;

INSERT INTO `COMBO_BATCH`
    (`batch_id`, `run_id`, `ranking_policy`, `pattern_weight_policy`, `top_k`,
     `category_filter_enabled`, `min_product_count`, `max_product_count`,
     `status`, `created_by`, `activated_by`, `created_at`, `activated_at`)
SELECT
    r.`run_id`,
    r.`run_id`,
    'LEGACY_V1_UNVERIFIED',
    'LEGACY_V1_WEIGHT',
    COALESCE(NULLIF(r.`top_k`, 0), 5),
    r.`category_filter`,
    2,
    4,
    'ARCHIVED',
    NULL,
    NULL,
    r.`run_time`,
    NULL
FROM `LEGACY_V1_RUN` r;

INSERT INTO `COMBO`
    (`combo_id`, `batch_id`, `name`, `combo_kind`, `status`, `discount_type`,
     `discount_value`, `max_discount_amount`, `stacking_policy`, `currency_code`,
     `reference_original_price`, `reference_sale_price`, `support_abs`,
     `support_ratio`, `pattern_weight`, `explanation`, `valid_from`, `valid_to`,
     `created_at`, `reviewed_at`, `reviewed_by`, `review_note`, `published_at`)
SELECT
    lc.`combo_id`,
    lc.`run_id`,
    lc.`combo_name`,
    CASE
        WHEN lc.`combo_type` IN
            ('SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL','MIXED_SEQUENCE')
            THEN lc.`combo_type`
        WHEN COALESCE(ci.`max_itemset_order`, 1) <= 1 THEN 'SAME_ORDER_BUNDLE'
        ELSE 'SEQUENTIAL_CROSS_SELL'
    END,
    CASE lc.`combo_status`
        WHEN 'PENDING' THEN 'DRAFT'
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE 'ARCHIVED'
    END,
    'NONE',
    NULL,
    NULL,
    'NON_STACKABLE',
    'VND',
    COALESCE(lc.`combo_price`, 0),
    COALESCE(lc.`combo_price`, 0),
    lc.`support`,
    CASE WHEN lr.`sequence_count` = 0 THEN 0
         ELSE lc.`support` / lr.`sequence_count` END,
    lc.`weight`,
    'Migrated from V1; ranking and weight provenance are legacy-unverified.',
    NULL,
    NULL,
    lc.`created_at`,
    lc.`reviewed_at`,
    NULL,
    NULL,
    NULL
FROM `LEGACY_V1_COMBO` lc
JOIN `LEGACY_V1_RUN` lr ON lr.`run_id` = lc.`run_id`
LEFT JOIN (
    SELECT `combo_id`, MAX(`itemset_order`) AS `max_itemset_order`
    FROM `LEGACY_V1_COMBO_ITEM`
    GROUP BY `combo_id`
) ci ON ci.`combo_id` = lc.`combo_id`;

INSERT INTO `COMBO_ITEM`
    (`combo_id`, `product_id`, `itemset_order`, `item_order`, `quantity`,
     `unit_price_snapshot`, `is_anchor_eligible`, `is_discount_eligible`)
SELECT
    lci.`combo_id`,
    lci.`product_id`,
    lci.`itemset_order`,
    lci.`item_order`,
    1,
    p.`current_price`,
    CASE WHEN lci.`itemset_order` = 1 THEN TRUE ELSE FALSE END,
    FALSE
FROM `LEGACY_V1_COMBO_ITEM` lci
JOIN `PRODUCT` p ON p.`product_id` = lci.`product_id`;

UPDATE `COMBO` c
JOIN (
    SELECT `combo_id`, SUM(`unit_price_snapshot` * `quantity`) AS `original_price`
    FROM `COMBO_ITEM`
    GROUP BY `combo_id`
) total ON total.`combo_id` = c.`combo_id`
SET
    c.`reference_original_price` = total.`original_price`,
    c.`reference_sale_price` = CASE
        WHEN c.`reference_sale_price` = 0 THEN total.`original_price`
        ELSE c.`reference_sale_price`
    END;

INSERT INTO `COMBO_SOURCE` (`combo_id`, `pattern_id`, `is_primary`)
SELECT
    lcs.`combo_id`,
    lcs.`pattern_id`,
    CASE WHEN lcs.`pattern_id` = primary_source.`primary_pattern_id`
         THEN TRUE ELSE FALSE END
FROM `LEGACY_V1_COMBO_SOURCE` lcs
JOIN (
    SELECT `combo_id`, MIN(`pattern_id`) AS `primary_pattern_id`
    FROM `LEGACY_V1_COMBO_SOURCE`
    GROUP BY `combo_id`
) primary_source ON primary_source.`combo_id` = lcs.`combo_id`;

-- ---------------------------------------------------------------------------
-- K. Remove isolated V1 tables after all transferable rows have been copied.
-- ---------------------------------------------------------------------------
DROP TABLE `LEGACY_V1_COMBO_SOURCE`;
DROP TABLE `LEGACY_V1_COMBO_ITEM`;
DROP TABLE `LEGACY_V1_COMBO`;
DROP TABLE `LEGACY_V1_PATTERN_ITEM`;
DROP TABLE `LEGACY_V1_PATTERN`;
DROP TABLE `LEGACY_V1_RUN`;
DROP TABLE `LEGACY_V1_ITEM_MAPPING`;
DROP TABLE `LEGACY_V1_ORDER_ITEM`;
DROP TABLE `LEGACY_V1_ORDERS`;
DROP TABLE `LEGACY_V1_PRODUCT`;
DROP TABLE `LEGACY_V1_CATEGORY`;
DROP TABLE `LEGACY_V1_CUSTOMER`;

-- Expected post-migration verification (manual; do not execute as part of review):
-- SELECT COUNT(*) AS table_count
-- FROM information_schema.tables
-- WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
--   AND table_name <> 'flyway_schema_history'; -- expected application tables: 45
--
-- SELECT COUNT(*) FROM MINING_RUN WHERE status = 'LEGACY_UNVERIFIED';
-- SELECT COUNT(*) FROM DATASET_SNAPSHOT
-- WHERE validation_status = 'LEGACY_UNVERIFIED';
--
-- No seed/demo rows are inserted by this schema migration.
