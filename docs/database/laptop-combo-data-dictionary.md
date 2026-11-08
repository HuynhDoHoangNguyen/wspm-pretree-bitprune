# Từ điển dữ liệu — Hệ thống combo laptop

Cập nhật: 2026-11-08. Bản tra cứu rút gọn cho MySQL 8.4; tên bảng/cột, kiểu,
nullability, PK/UQ/FK dưới đây đối chiếu trực tiếp migration V2–V4.
Chỉ đọc nhóm liên quan; DDL là nguồn chuẩn cho toàn bộ CHECK/default/index.

## 1. Trạng thái, nguồn chuẩn và quy ước

| Phần | Trạng thái được ghi nhận |
|---|---|
| V2 | 45 bảng; kiểm tra read-only local ngày 2026-11-03 ghi nhận SUCCESS, checksum 727165286, đủ bảng và không còn LEGACY_V1_* |
| V3 | Thêm ownership/lease, không thêm bảng; checkpoint local WDM-05.7 ngày 2026-11-07 ghi nhận SUCCESS/checksum 1697070581 |
| V4 | Thêm RUN_PROVENANCE và RUN_RESULT_PUBLICATION: tổng 47 bảng ứng dụng, không tính flyway_schema_history; owner deployment NOT VERIFIED |
| Application | Auth và WDM đến 06.11 đã có nghiệm thu theo phạm vi ghi nhận; không suy ra commerce/Combo production hoàn tất |
| WDM-06.12 | Owner yêu cầu làm lại; NOT VERIFIED, không dùng PASS cũ để đóng gate |

Đây là các mốc lịch sử, không phải DB inspection/test chạy lại khi rút gọn.
Mỗi môi trường phải kiểm `flyway_schema_history`; có bảng không có nghĩa đã có API/UI.

- DDL runtime: [V2](../../backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql),
  [V3](../../backend/src/main/resources/db/migration/V3__mining_run_execution_ownership.sql),
  [V4](../../backend/src/main/resources/db/migration/V4__mining_run_metadata_provenance.sql).
- [Thiết kế hệ thống](laptop-combo-system-redesign.md) giải thích nghiệp vụ;
  [WDM contract](../wdm/mining-request-contract.md) quy định run/publication hiện hành;
  [Auth](../auth/README.md) quy định account/session thực thi.
- Giữ Hibernate ddl-auto=validate; không sửa migration đã áp dụng, correction bằng migration mới.
- Ký hiệu `TYPE?` là nullable; không có ? là NOT NULL. Cột cùng kiểu được gom chung.
  PK: khóa chính; UQ: unique tuple; FK: quan hệ có trong DDL. Cardinality/check xuyên bảng
  ngoài DDL vẫn cần application transaction/tests, không tự coi đã được DB bảo đảm.
- ID công khai VARCHAR(40) chứa UUID/ULID; ID nội bộ có thể BIGINT UNSIGNED.
  Tiền DECIMAL + currency, không DOUBLE; trọng số/ngưỡng DOUBLE theo baseline.
  DATETIME(6) theo UTC; version dùng optimistic lock.
- Không hard-delete lịch sử đã tham chiếu. PII không vào mining; password/token chỉ hash.
  Các cột audit người thực hiện FK về USER_ACCOUNT theo DDL, không dùng display name.

## 2. Tài khoản và phân quyền

### 2.1 `USER_ACCOUNT`

- `VARCHAR(40)`: `user_id`; `VARCHAR(80)?`: `username`; `VARCHAR(254)?`: `email`.
- `VARCHAR(32)?`: `phone`; `VARCHAR(255)`: `password_hash`; `VARCHAR(160)`: `display_name`.
- `VARCHAR(24)`: `status`; `DATETIME(6)?`: `email_verified_at`, `phone_verified_at`, `last_login_at`; `BIGINT`: `version`.
- `DATETIME(6)`: `created_at`, `updated_at`.

PK (user_id); UQ (username); UQ (email); UQ (phone).
`status`: PENDING,ACTIVE,LOCKED,DISABLED.
Mặc định: `status`=PENDING.

### 2.2 `ROLE`

- `VARCHAR(40)`: `role_id`, `code`; `VARCHAR(100)`: `name`; `VARCHAR(500)?`: `description`.
- `DATETIME(6)`: `created_at`.

PK (role_id); UQ (code).

### 2.3 `USER_ROLE`

- `VARCHAR(40)`: `user_id`, `role_id`; `DATETIME(6)`: `assigned_at`; `VARCHAR(40)?`: `assigned_by`.

PK (user_id, role_id); FK (user_id) → USER_ACCOUNT; FK (role_id) → ROLE; FK (assigned_by) → USER_ACCOUNT(user_id).

### 2.4 `USER_SESSION`

- `VARCHAR(40)`: `session_id`, `user_id`; `VARCHAR(255)`: `refresh_token_hash`; `VARCHAR(500)?`: `device_info`.
- `VARCHAR(128)?`: `ip_hash`; `DATETIME(6)`: `expires_at`, `created_at`, `last_used_at`; `DATETIME(6)?`: `revoked_at`.

PK (session_id); UQ (refresh_token_hash); FK (user_id) → USER_ACCOUNT.

### 2.5 `USER_ADDRESS`

- `VARCHAR(40)`: `user_address_id`, `user_id`; `VARCHAR(80)?`: `label`; `VARCHAR(160)`: `recipient_name`, `ward_name`, `district_name`, `province_name`.
- `VARCHAR(32)`: `phone`; `VARCHAR(500)`: `address_line`; `VARCHAR(32)?`: `ward_code`, `district_code`, `province_code`.
- `VARCHAR(20)?`: `postal_code`; `CHAR(2)`: `country_code`; `BOOLEAN`: `is_default`.
- `DATETIME(6)`: `created_at`, `updated_at`.

PK (user_address_id); FK (user_id) → USER_ACCOUNT.
Mặc định: `country_code`=VN.

### 2.6 `CUSTOMER`

- `VARCHAR(40)`: `customer_id`; `VARCHAR(40)?`: `user_id`; `VARCHAR(80)`: `anonymous_code`.
- `VARCHAR(80)?`: `segment`; `DATETIME(6)`: `created_at`.

PK (customer_id); UQ (user_id); UQ (anonymous_code); FK (user_id) → USER_ACCOUNT.

## 3. Catalog sản phẩm

### 3.1 `CATEGORY`

- `VARCHAR(40)`: `category_id`; `VARCHAR(160)`: `name`; `VARCHAR(180)`: `slug`.
- `TEXT?`: `description`; `INT`: `display_order`; `VARCHAR(24)`: `status`.

PK (category_id); UQ (slug).
`status`: ACTIVE,INACTIVE.
Mặc định: `status`=ACTIVE.

### 3.2 `BRAND`

- `VARCHAR(40)`: `brand_id`; `VARCHAR(160)`: `name`; `VARCHAR(180)`: `slug`.
- `VARCHAR(24)`: `status`.

PK (brand_id); UQ (slug).
`status`: ACTIVE,INACTIVE.
Mặc định: `status`=ACTIVE.

### 3.3 `PRODUCT`

- `VARCHAR(40)`: `product_id`, `category_id`; `VARCHAR(80)`: `sku`; `VARCHAR(200)`: `name`.
- `VARCHAR(220)`: `slug`; `VARCHAR(40)?`: `brand_id`; `VARCHAR(1000)?`: `short_description`.
- `TEXT?`: `description`; `JSON?`: `specifications_json`; `DECIMAL(19,2)`: `current_price`.
- `CHAR(3)`: `currency_code`; `VARCHAR(1024)?`: `thumbnail_url`; `BOOLEAN`: `track_serial`.
- `VARCHAR(24)`: `status`; `DATETIME(6)`: `price_updated_at`, `created_at`, `updated_at`; `BIGINT`: `version`.

PK (product_id); UQ (sku); UQ (slug); FK (category_id) → CATEGORY; FK (brand_id) → BRAND.
`status`: DRAFT,ACTIVE,INACTIVE,DISCONTINUED.
Mặc định: `currency_code`=VND; `status`=DRAFT.

### 3.4 `PRODUCT_IMAGE`

- `VARCHAR(40)`: `image_id`, `product_id`; `VARCHAR(1024)`: `image_url`; `VARCHAR(255)?`: `alt_text`.
- `INT`: `display_order`.

PK (image_id); UQ (product_id, display_order); FK (product_id) → PRODUCT.

## 4. Nhà cung cấp, nhập hàng và tồn kho

### 4.1 `WAREHOUSE`

- `VARCHAR(40)`: `warehouse_id`, `code`; `VARCHAR(160)`: `name`; `VARCHAR(500)?`: `address_line`.
- `VARCHAR(32)?`: `phone`; `VARCHAR(24)`: `status`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (warehouse_id); UQ (code).
`status`: ACTIVE,INACTIVE.
Mặc định: `status`=ACTIVE.

### 4.2 `SUPPLIER`

- `VARCHAR(40)`: `supplier_id`, `code`; `VARCHAR(200)`: `name`; `VARCHAR(40)?`: `tax_code`.
- `VARCHAR(160)?`: `contact_name`; `VARCHAR(32)?`: `phone`; `VARCHAR(254)?`: `email`.
- `VARCHAR(500)?`: `address_line`; `VARCHAR(24)`: `status`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (supplier_id); UQ (code).
`status`: ACTIVE,INACTIVE.
Mặc định: `status`=ACTIVE.

### 4.3 `PURCHASE_ORDER`

- `VARCHAR(40)`: `purchase_order_id`, `supplier_id`, `warehouse_id`; `VARCHAR(60)`: `po_number`; `VARCHAR(32)`: `status`.
- `DATETIME(6)?`: `ordered_at`, `expected_at`, `approved_at`; `CHAR(3)`: `currency_code`; `DECIMAL(19,2)`: `subtotal`, `tax_total`, `grand_total`.
- `VARCHAR(1000)?`: `note`; `VARCHAR(40)?`: `created_by`, `approved_by`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (purchase_order_id); UQ (po_number); UQ (purchase_order_id, warehouse_id); FK (supplier_id) → SUPPLIER; FK (warehouse_id) → WAREHOUSE; FK (created_by) → USER_ACCOUNT(user_id); FK (approved_by) → USER_ACCOUNT(user_id).
`status`: DRAFT,APPROVED,PARTIALLY_RECEIVED,RECEIVED,CANCELLED.
Mặc định: `status`=DRAFT; `currency_code`=VND.

### 4.4 `PURCHASE_ORDER_ITEM`

- `BIGINT UNSIGNED`: `purchase_order_item_id`; `VARCHAR(40)`: `purchase_order_id`, `product_id`; `INT UNSIGNED`: `quantity_ordered`, `quantity_received`.
- `DECIMAL(19,2)`: `unit_cost`, `line_total`; `DECIMAL(7,4)`: `tax_rate`.

PK (purchase_order_item_id); UQ (purchase_order_id, product_id); FK (purchase_order_id) → PURCHASE_ORDER; FK (product_id) → PRODUCT.

### 4.5 `GOODS_RECEIPT`

- `VARCHAR(40)`: `goods_receipt_id`, `purchase_order_id`, `warehouse_id`; `VARCHAR(60)`: `receipt_number`; `VARCHAR(24)`: `status`.
- `VARCHAR(100)?`: `supplier_invoice_number`; `DATETIME(6)?`: `received_at`, `posted_at`; `VARCHAR(1000)?`: `note`.
- `VARCHAR(40)?`: `created_by`, `posted_by`; `DATETIME(6)`: `created_at`.

PK (goods_receipt_id); UQ (receipt_number); FK (purchase_order_id, warehouse_id) → PURCHASE_ORDER; FK (created_by) → USER_ACCOUNT(user_id); FK (posted_by) → USER_ACCOUNT(user_id).
`status`: DRAFT,POSTED,CANCELLED.
Mặc định: `status`=DRAFT.

### 4.6 `GOODS_RECEIPT_ITEM`

- `BIGINT UNSIGNED`: `goods_receipt_item_id`, `purchase_order_item_id`; `VARCHAR(40)`: `goods_receipt_id`, `product_id`; `INT UNSIGNED`: `quantity_received`.
- `DECIMAL(19,2)`: `unit_cost`, `line_total`.

PK (goods_receipt_item_id); UQ (goods_receipt_id, purchase_order_item_id); FK (goods_receipt_id) → GOODS_RECEIPT; FK (purchase_order_item_id, product_id) → PURCHASE_ORDER_ITEM.

### 4.7 `INVENTORY_BALANCE`

- `VARCHAR(40)`: `warehouse_id`, `product_id`; `INT UNSIGNED`: `on_hand_quantity`, `reserved_quantity`; `BIGINT`: `version`.
- `DATETIME(6)`: `updated_at`.

PK (warehouse_id, product_id); FK (warehouse_id) → WAREHOUSE; FK (product_id) → PRODUCT.

### 4.8 `STOCK_MOVEMENT`

- `BIGINT UNSIGNED`: `stock_movement_id`; `VARCHAR(40)`: `warehouse_id`, `product_id`, `reference_type`, `reference_id`; `VARCHAR(32)`: `movement_type`.
- `INT`: `quantity_delta`; `DECIMAL(19,2)?`: `unit_cost`; `INT UNSIGNED`: `balance_after`.
- `DATETIME(6)`: `occurred_at`; `VARCHAR(40)?`: `created_by`; `VARCHAR(1000)?`: `note`.

PK (stock_movement_id); FK (warehouse_id) → WAREHOUSE; FK (product_id) → PRODUCT; FK (created_by) → USER_ACCOUNT(user_id).
`movement_type`: PURCHASE_RECEIPT,SALE_ISSUE,CUSTOMER_RETURN,SUPPLIER_RETURN, ADJUSTMENT_IN,ADJUSTMENT_OUT.

### 4.9 `INVENTORY_SERIAL`

- `VARCHAR(40)`: `inventory_serial_id`, `product_id`, `warehouse_id`; `VARCHAR(160)`: `serial_number`; `BIGINT UNSIGNED`: `goods_receipt_item_id`.
- `BIGINT UNSIGNED?`: `order_item_id`; `VARCHAR(24)`: `status`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (inventory_serial_id); UQ (serial_number); FK (product_id) → PRODUCT; FK (warehouse_id) → WAREHOUSE; FK (goods_receipt_item_id, product_id) → GOODS_RECEIPT_ITEM; FK (order_item_id) → ORDER_ITEM.
`status`: IN_STOCK,RESERVED,SOLD,RETURNED,DAMAGED.

## 5. Đơn hàng và snapshot thương mại

### 5.1 `ORDERS`

- `VARCHAR(40)`: `order_id`, `customer_id`, `source`; `VARCHAR(40)?`: `checkout_id`; `VARCHAR(60)`: `order_number`.
- `DATETIME(6)`: `ordered_at`, `created_at`, `updated_at`; `DATETIME(6)?`: `completed_at`; `VARCHAR(32)`: `status`.
- `CHAR(3)`: `currency_code`; `DECIMAL(19,2)`: `list_subtotal`, `combo_discount_total`, `other_discount_total`, `shipping_fee`, `tax_total`, `grand_total`.

PK (order_id); UQ (checkout_id); UQ (order_number); FK (customer_id) → CUSTOMER.
`status`: PENDING_PAYMENT,PAID,PROCESSING,COMPLETED,CANCELLED, EXPIRED,RETURNED,REFUNDED.
Mặc định: `currency_code`=VND.

### 5.2 `ORDER_ITEM`

- `BIGINT UNSIGNED`: `order_item_id`; `VARCHAR(40)`: `order_id`, `product_id`; `VARCHAR(40)?`: `order_applied_combo_id`.
- `INT UNSIGNED`: `quantity`; `DECIMAL(19,2)`: `list_unit_price`, `allocated_discount_amount`, `line_total`; `VARCHAR(80)`: `sku_snapshot`.
- `VARCHAR(200)`: `product_name_snapshot`.

PK (order_item_id); FK (order_id) → ORDERS; FK (product_id) → PRODUCT.

### 5.3 `ORDER_APPLIED_COMBO`

- `VARCHAR(40)`: `order_applied_combo_id`, `order_id`, `combo_id`; `VARCHAR(255)`: `combo_name_snapshot`; `VARCHAR(24)`: `discount_type_snapshot`.
- `DECIMAL(19,4)?`: `discount_value_snapshot`; `CHAR(3)`: `currency_code`; `DECIMAL(19,2)`: `list_subtotal`, `discount_base_subtotal`, `discount_amount`, `final_subtotal`.
- `DATETIME(6)`: `applied_at`.

PK (order_applied_combo_id); UQ (order_applied_combo_id, order_id); FK (order_id) → ORDERS; FK (combo_id) → COMBO.

### 5.4 `ORDER_ADDRESS`

- `VARCHAR(40)`: `order_address_id`, `order_id`; `VARCHAR(16)`: `address_type`; `VARCHAR(160)`: `recipient_name`, `ward_name`, `district_name`, `province_name`.
- `VARCHAR(32)`: `phone`; `VARCHAR(500)`: `address_line`; `VARCHAR(32)?`: `ward_code`, `district_code`, `province_code`.
- `VARCHAR(20)?`: `postal_code`; `CHAR(2)`: `country_code`.

PK (order_address_id); UQ (order_id, address_type); FK (order_id) → ORDERS.
`address_type`: SHIPPING,BILLING.
Mặc định: `country_code`=VN.

### 5.5 `ORDER_STATUS_HISTORY`

- `BIGINT UNSIGNED`: `order_status_history_id`; `VARCHAR(40)`: `order_id`; `VARCHAR(32)?`: `from_status`.
- `VARCHAR(32)`: `to_status`; `VARCHAR(1000)?`: `reason`; `VARCHAR(24)`: `changed_by_type`.
- `VARCHAR(80)?`: `changed_by_id`; `DATETIME(6)`: `created_at`.

PK (order_status_history_id); FK (order_id) → ORDERS.
`changed_by_type`: USER,SYSTEM,PROVIDER.

## 6. Cart và checkout

### 6.1 `CART`

- `VARCHAR(40)`: `cart_id`; `VARCHAR(40)?`: `customer_id`; `VARCHAR(255)?`: `guest_token_hash`.
- `VARCHAR(24)`: `status`; `CHAR(3)`: `currency_code`; `BIGINT`: `version`.
- `DATETIME(6)?`: `expires_at`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (cart_id); UQ (guest_token_hash); FK (customer_id) → CUSTOMER.
`status`: ACTIVE,CONVERTED,ABANDONED,EXPIRED.
Mặc định: `status`=ACTIVE; `currency_code`=VND.

### 6.2 `CART_APPLIED_COMBO`

- `VARCHAR(40)`: `cart_applied_combo_id`, `cart_id`, `combo_id`; `VARCHAR(24)`: `status`; `DECIMAL(19,2)`: `estimated_list_subtotal`, `estimated_discount_amount`, `estimated_final_subtotal`.
- `DATETIME(6)`: `calculated_at`; `VARCHAR(500)?`: `invalid_reason`.

PK (cart_applied_combo_id); UQ (cart_applied_combo_id, cart_id); FK (cart_id) → CART; FK (combo_id) → COMBO.
`status`: SELECTED,INVALIDATED,REMOVED.
Mặc định: `status`=SELECTED.

### 6.3 `CART_ITEM`

- `BIGINT UNSIGNED`: `cart_item_id`; `VARCHAR(40)`: `cart_id`, `product_id`; `VARCHAR(40)?`: `cart_applied_combo_id`.
- `INT UNSIGNED`: `quantity`; `DECIMAL(19,2)`: `estimated_unit_price`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (cart_item_id); FK (cart_id) → CART; FK (product_id) → PRODUCT; FK (cart_applied_combo_id, cart_id) → CART_APPLIED_COMBO.

### 6.4 `CHECKOUT_SESSION`

- `VARCHAR(40)`: `checkout_id`, `cart_id`; `VARCHAR(40)?`: `customer_id`; `VARCHAR(128)`: `idempotency_scope`, `idempotency_key`.
- `BIGINT`: `cart_version_snapshot`, `version`; `VARCHAR(32)`: `status`; `CHAR(3)`: `currency_code`.
- `DECIMAL(19,2)`: `list_subtotal`, `combo_discount_total`, `other_discount_total`, `shipping_fee`, `tax_total`, `grand_total`; `DATETIME(6)?`: `quote_expires_at`, `completed_at`; `VARCHAR(80)?`: `failure_code`.
- `DATETIME(6)`: `created_at`, `updated_at`.

PK (checkout_id); UQ (idempotency_scope, idempotency_key); FK (cart_id) → CART; FK (customer_id) → CUSTOMER.
`status`: CREATED,QUOTED,CONFIRMED,PAYMENT_PENDING,COMPLETED, FAILED,CANCELLED,EXPIRED.
Mặc định: `status`=CREATED; `currency_code`=VND.

### 6.5 `CHECKOUT_ADDRESS`

- `VARCHAR(40)`: `checkout_address_id`, `checkout_id`; `VARCHAR(16)`: `address_type`; `VARCHAR(160)`: `recipient_name`, `ward_name`, `district_name`, `province_name`.
- `VARCHAR(32)`: `phone`; `VARCHAR(500)`: `address_line`; `VARCHAR(32)?`: `ward_code`, `district_code`, `province_code`.
- `VARCHAR(20)?`: `postal_code`; `CHAR(2)`: `country_code`.

PK (checkout_address_id); UQ (checkout_id, address_type); FK (checkout_id) → CHECKOUT_SESSION.
`address_type`: SHIPPING,BILLING.
Mặc định: `country_code`=VN.

### 6.6 `STOCK_RESERVATION`

- `VARCHAR(40)`: `reservation_id`, `checkout_id`, `warehouse_id`, `product_id`; `INT UNSIGNED`: `quantity`; `VARCHAR(24)`: `status`.
- `DATETIME(6)`: `expires_at`, `created_at`; `DATETIME(6)?`: `consumed_at`, `released_at`.

PK (reservation_id); UQ (checkout_id, warehouse_id, product_id); FK (checkout_id) → CHECKOUT_SESSION; FK (warehouse_id) → WAREHOUSE; FK (product_id) → PRODUCT.
`status`: ACTIVE,CONSUMED,RELEASED,EXPIRED.
Mặc định: `status`=ACTIVE.

## 7. Thanh toán

### 7.1 `PAYMENT`

- `VARCHAR(40)`: `payment_id`, `order_id`; `VARCHAR(32)`: `method`, `status`; `CHAR(3)`: `currency_code`.
- `DECIMAL(19,2)`: `amount`, `paid_amount`, `refunded_amount`; `DATETIME(6)`: `created_at`, `updated_at`; `DATETIME(6)?`: `paid_at`.

PK (payment_id); UQ (order_id); FK (order_id) → ORDERS.
`method`: COD,BANK_TRANSFER,E_WALLET,CARD; `status`: PENDING,AUTHORIZED,PAID,PARTIALLY_REFUNDED,REFUNDED, FAILED,CANCELLED.
Mặc định: `status`=PENDING.

### 7.2 `PAYMENT_ATTEMPT`

- `VARCHAR(40)`: `payment_attempt_id`, `payment_id`, `provider`; `VARCHAR(32)`: `method`, `status`; `VARCHAR(128)`: `idempotency_key`.
- `VARCHAR(160)?`: `provider_payment_id`; `DECIMAL(19,2)`: `amount`; `CHAR(3)`: `currency_code`.
- `VARCHAR(2048)?`: `redirect_url`; `DATETIME(6)?`: `expires_at`; `VARCHAR(80)?`: `failure_code`.
- `VARCHAR(1000)?`: `failure_message`; `DATETIME(6)`: `created_at`, `updated_at`.

PK (payment_attempt_id); UQ (idempotency_key); UQ (provider, provider_payment_id); FK (payment_id) → PAYMENT.
`method`: COD,BANK_TRANSFER,E_WALLET,CARD; `status`: CREATED,PENDING,AUTHORIZED,SUCCEEDED,FAILED,CANCELLED,EXPIRED.
Mặc định: `status`=CREATED.

### 7.3 `PAYMENT_TRANSACTION`

- `VARCHAR(40)`: `payment_transaction_id`, `payment_id`, `provider`; `VARCHAR(40)?`: `payment_attempt_id`; `VARCHAR(24)`: `type`, `status`.
- `VARCHAR(160)`: `provider_transaction_id`; `DECIMAL(19,2)`: `amount`; `CHAR(3)`: `currency_code`.
- `DATETIME(6)?`: `processed_at`; `DATETIME(6)`: `created_at`.

PK (payment_transaction_id); UQ (provider, provider_transaction_id); FK (payment_id) → PAYMENT; FK (payment_attempt_id) → PAYMENT_ATTEMPT.
`type`: AUTHORIZE,CAPTURE,REFUND,VOID; `status`: PENDING,SUCCEEDED,FAILED.

### 7.4 `PAYMENT_WEBHOOK_EVENT`

- `BIGINT UNSIGNED`: `webhook_event_id`; `VARCHAR(40)`: `provider`; `VARCHAR(160)`: `provider_event_id`.
- `VARCHAR(40)?`: `payment_attempt_id`; `VARCHAR(80)`: `event_type`; `VARCHAR(128)`: `payload_hash`.
- `BOOLEAN`: `signature_valid`; `VARCHAR(24)`: `status`; `DATETIME(6)`: `received_at`.
- `DATETIME(6)?`: `processed_at`; `VARCHAR(1000)?`: `error_message`.

PK (webhook_event_id); UQ (provider, provider_event_id); FK (payment_attempt_id) → PAYMENT_ATTEMPT.
`status`: RECEIVED,PROCESSED,IGNORED,FAILED.
Mặc định: `status`=RECEIVED.

## 8. Dataset, mining run và pattern

### 8.1 `ITEM_MAPPING`

- `VARCHAR(40)`: `product_id`; `INT UNSIGNED`: `item_id`; `DATETIME(6)`: `created_at`.

PK (product_id); UQ (item_id); FK (product_id) → PRODUCT.

### 8.2 `DATASET_SNAPSHOT`

- `VARCHAR(40)`: `snapshot_id`; `VARCHAR(32)`: `source_type`, `validation_status`; `VARCHAR(255)`: `source_name`.
- `DATETIME(6)?`: `from_time`, `to_time`; `VARCHAR(1024)?`: `raw_file_path`, `sequence_file_path`, `mapping_file_path`; `CHAR(64)?`: `raw_sha256`, `sequence_sha256`, `mapping_sha256`.
- `JSON`: `conversion_report_json`; `BIGINT UNSIGNED`: `sequence_count`; `BIGINT UNSIGNED?`: `single_itemset_sequence_count`, `multi_itemset_sequence_count`, `itemset_count`, `distinct_item_count`, `total_item_occurrences`.
- `INT UNSIGNED?`: `min_sequence_length`, `max_sequence_length`; `DOUBLE?`: `average_sequence_length`; `DATETIME(6)`: `created_at`.

PK (snapshot_id).
`validation_status`: PENDING,VALID,INVALID,LEGACY_UNVERIFIED.

### 8.3 `MINING_RUN`

- `VARCHAR(40)`: `run_id`, `snapshot_id`; `VARCHAR(32)`: `algorithm`, `status`, `minsup_mode`; `VARCHAR(255)`: `dataset_source`.
- `DOUBLE`: `minsup_configured`, `effective_threshold`, `mean_w`; `DOUBLE?`: `minsup_relative_fraction`, `max_w`, `min_w`; `BIGINT UNSIGNED?`: `pattern_count`, `candidate_count`, `dataset_io_ms`, `preprocessing_ms`, `dfs_mining_ms`, `complete_algorithm_ms`, `output_io_ms`, `total_execution_ms`, `peak_sampled_jvm_heap_bytes`.
- `JSON?`: `legacy_metadata_json`; `VARCHAR(2000)?`: `error_message`; `DATETIME(6)`: `created_at`.
- `DATETIME(6)?`: `started_at`, `finished_at`, `execution_lease_until`; `VARCHAR(40)?`: `execution_owner_id`, `execution_attempt_id`.

PK (run_id); FK (snapshot_id) → DATASET_SNAPSHOT.
`algorithm`: WSPM_PRETREE,WSPM_BITPRUNE; `status`: QUEUED,RUNNING,SUCCEEDED,FAILED,LEGACY_UNVERIFIED.

### 8.4 `RUN_ITEM_STATISTIC`

- `VARCHAR(40)`: `run_id`, `product_id`; `INT UNSIGNED`: `item_id`; `BIGINT UNSIGNED`: `occurrence_count`, `sequence_support`.
- `DOUBLE?`: `item_weight`; `VARCHAR(200)`: `product_name_snapshot`; `VARCHAR(80)`: `sku_snapshot`.

PK (run_id, item_id); UQ (run_id, product_id); FK (run_id) → MINING_RUN; FK (product_id) → PRODUCT.

### 8.5 `PATTERN`

- `BIGINT UNSIGNED`: `pattern_id`, `support_abs`; `VARCHAR(40)`: `run_id`; `TEXT`: `canonical_pattern`.
- `CHAR(64)`: `canonical_pattern_hash`; `DOUBLE`: `support_ratio`, `weighted_support`, `pattern_weight`; `INT UNSIGNED`: `pattern_item_count`, `itemset_count`.
- `VARCHAR(80)`: `pattern_weight_policy`.

PK (pattern_id); UQ (run_id, canonical_pattern_hash); FK (run_id) → MINING_RUN.

### 8.6 `PATTERN_ITEM`

- `BIGINT UNSIGNED`: `pattern_id`; `INT UNSIGNED`: `itemset_order`, `item_order`, `item_id`.

PK (pattern_id, itemset_order, item_order); FK (pattern_id) → PATTERN.

### 8.7 `RUN_PROVENANCE` — V4

- `VARCHAR(40)`: `run_id`; `VARCHAR(80)`: `provenance_version`; `JSON`: `provenance_json`.
- `DATETIME(6)`: `captured_at`.

PK (run_id); FK (run_id) → MINING_RUN.

### 8.8 `RUN_RESULT_PUBLICATION` — V4

- `VARCHAR(40)`: `run_id`; `VARCHAR(24)`: `publication_status`; `VARCHAR(80)?`: `manifest_version`, `pattern_weight_policy`.
- `CHAR(64)?`: `result_sha256`; `BIGINT UNSIGNED?`: `pattern_count`, `pattern_item_count`, `run_item_count`, `publication_elapsed_ms`; `DATETIME(6)?`: `committed_at`.

PK (run_id); FK (run_id) → MINING_RUN.
Mặc định: `publication_status`=NOT_PUBLISHED.

## 9. Sinh, duyệt và công bố combo

### 9.1 `COMBO_BATCH`

- `VARCHAR(40)`: `batch_id`, `run_id`; `VARCHAR(80)`: `ranking_policy`, `pattern_weight_policy`; `INT UNSIGNED`: `top_k`, `min_product_count`, `max_product_count`.
- `BOOLEAN`: `category_filter_enabled`; `VARCHAR(24)`: `status`; `VARCHAR(40)?`: `created_by`, `activated_by`.
- `DATETIME(6)`: `created_at`; `DATETIME(6)?`: `activated_at`.

PK (batch_id); FK (run_id) → MINING_RUN; FK (created_by) → USER_ACCOUNT(user_id); FK (activated_by) → USER_ACCOUNT(user_id).
`status`: DRAFT,ACTIVE,ARCHIVED.
Mặc định: `status`=DRAFT.

### 9.2 `COMBO`

- `VARCHAR(40)`: `combo_id`, `batch_id`, `combo_kind`; `VARCHAR(255)`: `name`; `VARCHAR(24)`: `status`, `discount_type`, `stacking_policy`.
- `DECIMAL(19,4)?`: `discount_value`; `DECIMAL(19,2)?`: `max_discount_amount`; `CHAR(3)`: `currency_code`.
- `DECIMAL(19,2)`: `reference_original_price`, `reference_sale_price`; `BIGINT UNSIGNED`: `support_abs`; `DOUBLE`: `support_ratio`, `pattern_weight`.
- `VARCHAR(1000)`: `explanation`; `DATETIME(6)?`: `valid_from`, `valid_to`, `reviewed_at`, `published_at`; `DATETIME(6)`: `created_at`.
- `VARCHAR(40)?`: `reviewed_by`; `VARCHAR(1000)?`: `review_note`.

PK (combo_id); FK (batch_id) → COMBO_BATCH; FK (reviewed_by) → USER_ACCOUNT(user_id).
`combo_kind`: SAME_ORDER_BUNDLE,SEQUENTIAL_CROSS_SELL,MIXED_SEQUENCE; `status`: DRAFT,APPROVED,REJECTED,PUBLISHED,ARCHIVED; `discount_type`: NONE,PERCENTAGE,FIXED_AMOUNT,FIXED_PRICE; `stacking_policy`: NON_STACKABLE.
Mặc định: `status`=DRAFT; `discount_type`=NONE; `stacking_policy`=NON_STACKABLE; `currency_code`=VND.

### 9.3 `COMBO_ITEM`

- `VARCHAR(40)`: `combo_id`, `product_id`; `INT UNSIGNED`: `itemset_order`, `item_order`, `quantity`; `DECIMAL(19,2)`: `unit_price_snapshot`.
- `BOOLEAN`: `is_anchor_eligible`, `is_discount_eligible`.

PK (combo_id, itemset_order, item_order); UQ (combo_id, product_id); FK (combo_id) → COMBO; FK (product_id) → PRODUCT.

### 9.4 `COMBO_SOURCE`

- `VARCHAR(40)`: `combo_id`; `BIGINT UNSIGNED`: `pattern_id`; `BOOLEAN`: `is_primary`.

PK (combo_id, pattern_id); FK (combo_id) → COMBO; FK (pattern_id) → PATTERN.

### 9.5 `CATEGORY_EXCLUSION`

- `VARCHAR(40)`: `category_id_low`, `category_id_high`; `VARCHAR(500)`: `reason`; `BOOLEAN`: `active`.
- `DATETIME(6)`: `created_at`.

PK (category_id_low, category_id_high); FK (category_id_low) → CATEGORY(category_id); FK (category_id_high) → CATEGORY(category_id).

## 10. Bất biến nghiệp vụ và mining

### Identity, catalog, sales

- USER_ACCOUNT có ít nhất một username/email/phone; normalized identity unique khi có.
  Registration Auth hiện yêu cầu username/email theo contract riêng; không suy mọi lựa chọn
  nullable trong schema đều được public API chấp nhận. Không serialize password_hash.
- CUSTOMER là pseudonymous sequence identity, có thể không liên kết account.
  USER_ADDRESS là địa chỉ lưu sẵn; CHECKOUT_ADDRESS/ORDER_ADDRESS là snapshot độc lập.
  Tối đa một default address/user là application contract.
- PRODUCT là một SKU/mining item; specifications_json chỉ render, không làm FK.
  Tồn kho không nằm trên PRODUCT. ITEM_MAPPING.item_id dương, ổn định, không tái dùng;
  mỗi snapshot/run giữ dictionary riêng.
- ORDERS chỉ COMPLETED mới vào mining. Một customer → một sequence; một order → một
  itemset, sort (completed_at, order_id), dedup SKU/order, không dùng quantity làm occurrence.
  Không sửa âm thầm order/item đã được dùng bởi snapshot.
- ORDER_ITEM phải tham chiếu applied combo cùng order; CART_ITEM tương tự cùng cart.
  Order lưu SKU/name/giá/discount snapshots; không tính lại hóa đơn từ catalog hiện tại.

### Inventory, checkout và payment

- available = on_hand_quantity - reserved_quantity >= 0; không lưu available riêng.
  PO không tăng tồn; receipt POSTED cập nhật balance + append-only movement atomically.
  Receipt item phải khớp PO/product/kho; không over-receipt khi chưa có policy.
- Reservation chỉ đổi reserved; sale issue consume reservation, giảm on-hand và ghi movement
  cùng transaction. Retry không double-post; serial không bán hai lần.
- Cart totals chỉ estimate. Guest checkout phải có CUSTOMER giả danh trước tạo ORDERS.
  Checkout idempotency dùng server-derived scope + key; cart version/quote expiry phải kiểm lại.
  Unique reservation theo checkout/warehouse/product; không oversell khi cạnh tranh.
- Payment amount/currency gắn order; attempt/transaction provider references unique theo
  đúng tuple DDL (provider, reference), không coi reference là global unique.
  Webhook unique (provider, provider_event_id), verify signature/state transition trước effects.
  Không tin browser redirect là payment success; capture/refund không được cộng lặp hoặc vượt mức.
- Không lưu PAN/CVV, raw token, credential provider hoặc PII payload vào mining/logs.
  Chi tiết quote/rounding/discount allocation xem thiết kế hệ thống; đây chưa phải bằng chứng
  checkout/payment implementation đã nghiệm thu.

### Run, temporal patterns và publication

- MINING_RUN giữ configured/resolved threshold, không có top_k/minWeight/ranking coefficients.
  Web request hiện chỉ ABSOLUTE/PERCENT; không suy enum request từ phạm vi VARCHAR của schema.
  R6 zero-effective threshold còn OPEN; không comparator/epsilon mới.
- V3 ownership/attempt/lease phải cùng null hoặc cùng có giá trị; index (status, lease).
  Recovery chỉ áp dụng owned QUEUED/RUNNING hết lease; không backfill/replay legacy.
- Metrics giữ Dataset I/O/preprocessing/DFS/complete/output/total riêng; scope lồng nhau
  không cộng tất cả. Worker total loại queue/admission/terminal DB commit; nanos→millis
  chia nguyên, chưa đo để null; output_io_ms hiện null. JVM heap sampling không phải hardware RAM.
- RUN_ITEM_STATISTIC gồm all verified input items, kể cả output rỗng; counts/weights từ core.
  WDM-06.5 chụp PRODUCT.name/SKU với shared locks tại transaction statistics thành công đầu tiên,
  không phải nhãn CSV hoặc thời điểm engine chạy. Retry giữ snapshots; rollback có thể capture lại.
  Không có capture timestamp riêng, không suy từ RUN_PROVENANCE.captured_at.
- PATTERN giữ full canonical + exact support; <(ab)> khác <(a)(b)>.
  PATTERN_ITEM coordinates 1-based/contiguous, item IDs tăng trong itemset;
  item lặp ở itemset khác vẫn giữ. Dictionary phải thuộc cùng run.
- EQ2_EQ3_ARITHMETIC_MEAN_V1: mean item weights trong mỗi itemset rồi mean itemset means;
  cộng Java double theo item ID tăng và temporal order, không làm tròn trước khi lưu.
  pattern_weight khác global mean_w và weighted_support = support_abs * mean_w.
  Đây là policy ứng dụng đã chọn, không thay mining predicate hoặc giải quyết bất nhất R1.
- RUN_PROVENANCE_V1 insert-only, version JSON khớp cột; bind conversion/artifact refs,
  hashes/stats/policies. captured_at sao chép run.created_at tại creation transaction,
  không phải timestamp capture tên/SKU; không raw bytes/PII/name snapshots. Không legacy backfill.
- RUN_RESULT_PUBLICATION mặc định NOT_PUBLISHED, completion fields toàn null.
  COMMITTED phải đủ manifest/hash/counts/policy/timing; zero counts hợp lệ.
  WDM-06.11 đã có writer/consumer publish full result + marker atomically, khác checkpoint 06.2.
  Schema CHECK không tự chứng minh manifest hay row completeness.
- Published reader verify WDM_RESULT_MANIFEST_V1 trước COMPLETE_VERIFIED.
  SUCCEEDED không đồng nghĩa published. Legacy thiếu cả hai metadata rows là UNVERIFIED;
  thiếu một hoặc malformed metadata reject. Empty published output export được,
  không Combo eligible. Xem WDM contract cho budget, retry/ack và export/provider.

### Combo và ranh giới

- Top-K thuộc COMBO_BATCH; policy SUPPORT_THEN_PATTERN_WEIGHT_V1.
  Demo có một ACTIVE batch, activate mới/archive cũ cùng transaction.
- COMBO có lifecycle riêng, không combo_score hoặc rank_position lưu cứng.
  Lọc batch ACTIVE + combo PUBLISHED + thời hạn + anchor rồi backend mới xếp rank.
- COMBO_ITEM giữ temporal positions, quantity và anchor/discount eligibility riêng;
  index (product_id, is_anchor_eligible, combo_id) phục vụ storefront.
- COMBO_SOURCE phải có đúng một primary pattern; mọi metrics cùng nguồn,
  không ghép MAX(support) và MAX(weight) từ hai pattern. Đây còn cần service/tests.
- CATEGORY_EXCLUSION lưu canonical low/high pair để loại trừ đối xứng.
- Ngoài core: HR, carrier tracking đầy đủ, voucher/loyalty, chat/notifications,
  kế toán tổng hợp/công nợ/FIFO-average đầy đủ và multi-provider certification.

## 11. Cách kiểm chứng

Đây là tra cứu schema và application invariants, không phải báo cáo acceptance cho
từng module. Khi thay đổi: đọc DDL + entity/repository/tests liên quan; ghi riêng
static review, Flyway validate/migrate, Hibernate validate, integration/E2E và owner rollout.
Tiêu chí thiếu bằng chứng ghi NOT VERIFIED; không seed pattern/combo giả để thay mining thật.
