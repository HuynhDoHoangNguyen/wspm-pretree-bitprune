# Từ điển dữ liệu hệ thống gợi ý combo cửa hàng laptop

**Trạng thái:** V2 SCHEMA CONTRACT + additive V3 execution ownership — application implementation còn từng phần

Schema 45 bảng đã được đưa vào Flyway V2. Kiểm tra read-only database local ngày
2026-11-03 xác nhận V2 thành công, checksum khớp và tập bảng thực tế khớp toàn bộ
45 bảng V2. Trạng thái của môi trường khác chỉ được xác nhận bằng
`flyway_schema_history`. Việc bảng tồn tại không đồng nghĩa entity, DTO, API hoặc
frontend tương ứng đã hoàn tất.

Tài liệu này là danh mục đầy đủ các bảng và trường của schema V2, bổ sung
ownership V3 tại mục 8.3. Nó phải được
đọc cùng `laptop-combo-system-redesign.md`. Các kiểu dữ liệu đã được hiện thực
cho MySQL 8.4 trong V2; mọi điều chỉnh tiếp theo phải dùng migration V3+ và không
được thay đổi mining semantics hoặc làm mất khả năng tái lập.

## 1. Quy ước chung

- ID nghiệp vụ công khai: `VARCHAR(40)` chứa UUID/ULID; không dùng số thứ tự dễ đoán.
- ID dòng nội bộ dung lượng lớn: `BIGINT UNSIGNED AUTO_INCREMENT` khi phù hợp.
- Tiền: `DECIMAL(19,2)`, luôn đi cùng `currency_code CHAR(3)`; không dùng `DOUBLE`.
- Tỷ lệ/trọng số nghiên cứu: `DOUBLE` để tương thích baseline Java `double` đã đóng băng.
- Thời gian: `DATETIME(6)` theo UTC; API chuyển timezone khi hiển thị.
- Trạng thái: lưu `VARCHAR`, ánh xạ Java enum và có CHECK constraint/test transition.
- Bảng mutable có `version BIGINT` cho optimistic locking.
- Không hard-delete product, pattern, order, payment hoặc movement đã được tham chiếu.
- PII không đi vào dataset/export mining; token/password chỉ lưu dạng hash.
- Các cột `created_by`, `approved_by`, `reviewed_by`, `posted_by`, `activated_by`
  tham chiếu `USER_ACCOUNT.user_id`.

## 2. Tài khoản và phân quyền

### 2.1 `USER_ACCOUNT`

Mục đích: tài khoản đăng nhập dùng chung cho customer và nhân sự quản trị.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `user_id` | `VARCHAR(40)` | PK |
| `username` | `VARCHAR(80)` nullable | Tên đăng nhập đã normalize, unique khi có |
| `email` | `VARCHAR(254)` nullable | Email normalize, unique khi có; PII |
| `phone` | `VARCHAR(32)` nullable | Số điện thoại chuẩn hóa, unique khi có; PII |
| `password_hash` | `VARCHAR(255)` | Hash mật khẩu local; không lưu mật khẩu thuần |
| `display_name` | `VARCHAR(160)` | Tên hiển thị |
| `status` | `VARCHAR(24)` | `PENDING`, `ACTIVE`, `LOCKED`, `DISABLED` |
| `email_verified_at` | `DATETIME(6)` nullable | Thời điểm xác minh email |
| `phone_verified_at` | `DATETIME(6)` nullable | Thời điểm xác minh điện thoại |
| `last_login_at` | `DATETIME(6)` nullable | Lần đăng nhập gần nhất |
| `version` | `BIGINT` | Optimistic lock |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit timestamps |

Ít nhất một trong `username/email/phone` phải có giá trị. Không trả
`password_hash` trong bất kỳ response nào.

### 2.2 `ROLE`

Mục đích: danh mục vai trò hệ thống.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `role_id` | `VARCHAR(40)` | PK |
| `code` | `VARCHAR(40)` | Unique; tối thiểu `CUSTOMER`, `ADMIN` |
| `name` | `VARCHAR(100)` | Tên hiển thị |
| `description` | `VARCHAR(500)` nullable | Mô tả quyền |
| `created_at` | `DATETIME(6)` | Audit |

### 2.3 `USER_ROLE`

Mục đích: quan hệ nhiều-nhiều giữa account và role.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `user_id` | `VARCHAR(40)` | PK/FK → `USER_ACCOUNT` |
| `role_id` | `VARCHAR(40)` | PK/FK → `ROLE` |
| `assigned_at` | `DATETIME(6)` | Thời điểm gán |
| `assigned_by` | `VARCHAR(40)` nullable | FK → người quản trị thực hiện |

### 2.4 `USER_SESSION`

Mục đích: quản lý refresh session và revoke đăng nhập.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `session_id` | `VARCHAR(40)` | PK |
| `user_id` | `VARCHAR(40)` | FK → `USER_ACCOUNT` |
| `refresh_token_hash` | `VARCHAR(255)` | Unique hash; không lưu raw token |
| `device_info` | `VARCHAR(500)` nullable | Mô tả thiết bị đã rút gọn |
| `ip_hash` | `VARCHAR(128)` nullable | Hash IP nếu cần audit, không lưu mù quáng IP thô |
| `expires_at` | `DATETIME(6)` | Hết hạn phiên |
| `revoked_at` | `DATETIME(6)` nullable | Phiên đã revoke |
| `created_at`, `last_used_at` | `DATETIME(6)` | Audit hoạt động |

### 2.5 `USER_ADDRESS`

Mục đích: địa chỉ lưu sẵn để điền checkout; không phải snapshot hóa đơn.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `user_address_id` | `VARCHAR(40)` | PK |
| `user_id` | `VARCHAR(40)` | FK → `USER_ACCOUNT` |
| `label` | `VARCHAR(80)` nullable | Ví dụ Nhà, Công ty |
| `recipient_name` | `VARCHAR(160)` | PII |
| `phone` | `VARCHAR(32)` | PII |
| `address_line` | `VARCHAR(500)` | Số nhà/đường; PII |
| `ward_code`, `district_code`, `province_code` | `VARCHAR(32)` nullable | Mã hành chính tại thời điểm lưu |
| `ward_name`, `district_name`, `province_name` | `VARCHAR(160)` | Tên snapshot để hiển thị ổn định |
| `postal_code` | `VARCHAR(20)` nullable | Mã bưu chính |
| `country_code` | `CHAR(2)` | Mặc định `VN` |
| `is_default` | `BOOLEAN` | Tối đa một địa chỉ mặc định/user ở application contract |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

### 2.6 `CUSTOMER`

Mục đích: định danh sequence khách hàng cho mining, tách khỏi PII đăng nhập.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `customer_id` | `VARCHAR(40)` | PK; sequence identity |
| `user_id` | `VARCHAR(40)` nullable | Unique FK → `USER_ACCOUNT`; null với guest/import ẩn danh |
| `anonymous_code` | `VARCHAR(80)` | Unique pseudonym dùng trong dataset |
| `segment` | `VARCHAR(80)` nullable | Nhãn nghiệp vụ, không dùng mặc định trong mining |
| `created_at` | `DATETIME(6)` | Audit |

## 3. Catalog sản phẩm

### 3.1 `CATEGORY`

Mục đích: danh mục laptop và phụ kiện.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `category_id` | `VARCHAR(40)` | PK |
| `name` | `VARCHAR(160)` | Tên danh mục |
| `slug` | `VARCHAR(180)` | Unique URL slug |
| `description` | `TEXT` nullable | Mô tả |
| `display_order` | `INT` | Thứ tự UI |
| `status` | `VARCHAR(24)` | `ACTIVE`, `INACTIVE` |

### 3.2 `BRAND`

Mục đích: hãng sản phẩm.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `brand_id` | `VARCHAR(40)` | PK |
| `name` | `VARCHAR(160)` | Tên hãng |
| `slug` | `VARCHAR(180)` | Unique URL slug |
| `status` | `VARCHAR(24)` | `ACTIVE`, `INACTIVE` |

### 3.3 `PRODUCT`

Mục đích: SKU bán được và item nghiệp vụ ánh xạ sang mining item.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `product_id` | `VARCHAR(40)` | PK |
| `sku` | `VARCHAR(80)` | Unique mã SKU |
| `name` | `VARCHAR(200)` | Tên sản phẩm/cấu hình |
| `slug` | `VARCHAR(220)` | Unique URL slug |
| `category_id` | `VARCHAR(40)` | FK → `CATEGORY` |
| `brand_id` | `VARCHAR(40)` nullable | FK → `BRAND` |
| `short_description` | `VARCHAR(1000)` nullable | Mô tả ngắn |
| `description` | `TEXT` nullable | Mô tả chi tiết |
| `specifications_json` | `JSON` nullable | Thông số chỉ để render, không làm FK |
| `current_price` | `DECIMAL(19,2)` | Giá bán hiện tại, không âm |
| `currency_code` | `CHAR(3)` | Mặc định `VND` |
| `thumbnail_url` | `VARCHAR(1024)` nullable | Ảnh card |
| `track_serial` | `BOOLEAN` | Laptop/thiết bị cần quản lý serial |
| `status` | `VARCHAR(24)` | `DRAFT`, `ACTIVE`, `INACTIVE`, `DISCONTINUED` |
| `price_updated_at` | `DATETIME(6)` | Dùng kiểm tra quote stale |
| `version` | `BIGINT` | Optimistic lock |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

Không có `stock_quantity`; tồn kho chuẩn nằm tại `INVENTORY_BALANCE`.

### 3.4 `PRODUCT_IMAGE`

Mục đích: gallery ảnh sản phẩm.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `image_id` | `VARCHAR(40)` | PK |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `image_url` | `VARCHAR(1024)` | URL/object key |
| `alt_text` | `VARCHAR(255)` nullable | Accessibility |
| `display_order` | `INT` | Thứ tự; unique `(product_id, display_order)` |

## 4. Nhà cung cấp, nhập hàng và tồn kho

### 4.1 `WAREHOUSE`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `warehouse_id` | `VARCHAR(40)` | PK |
| `code` | `VARCHAR(40)` | Unique mã kho |
| `name` | `VARCHAR(160)` | Tên kho |
| `address_line` | `VARCHAR(500)` nullable | Địa chỉ |
| `phone` | `VARCHAR(32)` nullable | Liên hệ |
| `status` | `VARCHAR(24)` | `ACTIVE`, `INACTIVE` |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

### 4.2 `SUPPLIER`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `supplier_id` | `VARCHAR(40)` | PK |
| `code` | `VARCHAR(40)` | Unique mã nhà cung cấp |
| `name` | `VARCHAR(200)` | Tên pháp lý/hiển thị |
| `tax_code` | `VARCHAR(40)` nullable | Mã số thuế |
| `contact_name` | `VARCHAR(160)` nullable | Người liên hệ |
| `phone`, `email` | `VARCHAR` nullable | Thông tin liên hệ; PII |
| `address_line` | `VARCHAR(500)` nullable | Địa chỉ |
| `status` | `VARCHAR(24)` | `ACTIVE`, `INACTIVE` |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

### 4.3 `PURCHASE_ORDER`

Mục đích: chứng từ đặt mua; chưa làm tăng tồn kho.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `purchase_order_id` | `VARCHAR(40)` | PK |
| `po_number` | `VARCHAR(60)` | Unique số PO |
| `supplier_id` | `VARCHAR(40)` | FK → `SUPPLIER` |
| `warehouse_id` | `VARCHAR(40)` | Kho dự kiến nhận |
| `status` | `VARCHAR(32)` | `DRAFT`, `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED` |
| `ordered_at`, `expected_at` | `DATETIME(6)` nullable | Ngày đặt/dự kiến nhận |
| `currency_code` | `CHAR(3)` | Đồng tiền |
| `subtotal`, `tax_total`, `grand_total` | `DECIMAL(19,2)` | Tổng đặt hàng; không âm |
| `note` | `VARCHAR(1000)` nullable | Ghi chú |
| `created_by`, `approved_by` | `VARCHAR(40)` nullable | FK → `USER_ACCOUNT` |
| `created_at`, `approved_at`, `updated_at` | `DATETIME(6)` | Audit |

### 4.4 `PURCHASE_ORDER_ITEM`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `purchase_order_item_id` | `BIGINT UNSIGNED` | PK |
| `purchase_order_id` | `VARCHAR(40)` | FK → `PURCHASE_ORDER` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT`; unique/product trong PO |
| `quantity_ordered` | `INT UNSIGNED` | Số lượng đặt, > 0 |
| `quantity_received` | `INT UNSIGNED` | Counter từ receipt posted |
| `unit_cost` | `DECIMAL(19,2)` | Giá vốn đặt mua |
| `tax_rate` | `DECIMAL(7,4)` | Tỷ lệ thuế |
| `line_total` | `DECIMAL(19,2)` | Tổng dòng theo policy tiền |

### 4.5 `GOODS_RECEIPT`

Mục đích: phiếu nhận hàng vào kho.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `goods_receipt_id` | `VARCHAR(40)` | PK |
| `receipt_number` | `VARCHAR(60)` | Unique số phiếu nhập |
| `purchase_order_id` | `VARCHAR(40)` | FK → `PURCHASE_ORDER` |
| `warehouse_id` | `VARCHAR(40)` | FK → `WAREHOUSE`; phải khớp PO hoặc policy chuyển kho |
| `status` | `VARCHAR(24)` | `DRAFT`, `POSTED`, `CANCELLED` |
| `supplier_invoice_number` | `VARCHAR(100)` nullable | Số hóa đơn NCC |
| `received_at` | `DATETIME(6)` nullable | Thời điểm nhận |
| `note` | `VARCHAR(1000)` nullable | Ghi chú |
| `created_by`, `posted_by` | `VARCHAR(40)` nullable | FK → `USER_ACCOUNT` |
| `created_at`, `posted_at` | `DATETIME(6)` | Audit |

### 4.6 `GOODS_RECEIPT_ITEM`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `goods_receipt_item_id` | `BIGINT UNSIGNED` | PK |
| `goods_receipt_id` | `VARCHAR(40)` | FK → `GOODS_RECEIPT` |
| `purchase_order_item_id` | `BIGINT UNSIGNED` | FK → `PURCHASE_ORDER_ITEM` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT`; phải khớp PO item |
| `quantity_received` | `INT UNSIGNED` | Số lượng nhận, > 0 |
| `unit_cost` | `DECIMAL(19,2)` | Giá vốn thực nhận snapshot |
| `line_total` | `DECIMAL(19,2)` | Tổng giá trị nhận |

### 4.7 `INVENTORY_BALANCE`

Mục đích: số dư tồn hiện tại theo kho và SKU.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `warehouse_id` | `VARCHAR(40)` | PK/FK → `WAREHOUSE` |
| `product_id` | `VARCHAR(40)` | PK/FK → `PRODUCT` |
| `on_hand_quantity` | `INT UNSIGNED` | Tồn vật lý |
| `reserved_quantity` | `INT UNSIGNED` | Đang giữ cho checkout/order |
| `version` | `BIGINT` | Lock chống oversell/lost update |
| `updated_at` | `DATETIME(6)` | Audit |

`available_quantity = on_hand_quantity - reserved_quantity` được tính, không lưu.

### 4.8 `STOCK_MOVEMENT`

Mục đích: sổ cái tồn kho append-only.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `stock_movement_id` | `BIGINT UNSIGNED` | PK |
| `warehouse_id` | `VARCHAR(40)` | FK → `WAREHOUSE` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `movement_type` | `VARCHAR(32)` | Receipt, sale issue, return hoặc adjustment |
| `quantity_delta` | `INT` | Số dương nhập, số âm xuất |
| `unit_cost` | `DECIMAL(19,2)` nullable | Giá vốn snapshot nếu có |
| `balance_after` | `INT UNSIGNED` | On-hand sau movement để audit |
| `reference_type` | `VARCHAR(40)` | Loại chứng từ nguồn |
| `reference_id` | `VARCHAR(40)` | ID chứng từ nguồn; index cùng type |
| `occurred_at` | `DATETIME(6)` | Thời điểm nghiệp vụ |
| `created_by` | `VARCHAR(40)` nullable | FK → `USER_ACCOUNT`/system |
| `note` | `VARCHAR(1000)` nullable | Lý do/ghi chú |

### 4.9 `INVENTORY_SERIAL`

Mục đích: quản lý serial của laptop/thiết bị `track_serial = true`.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `inventory_serial_id` | `VARCHAR(40)` | PK |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `serial_number` | `VARCHAR(160)` | Unique serial |
| `warehouse_id` | `VARCHAR(40)` | FK → kho hiện tại |
| `goods_receipt_item_id` | `BIGINT UNSIGNED` | FK → nguồn nhập |
| `order_item_id` | `BIGINT UNSIGNED` nullable | FK → dòng bán khi đã xuất |
| `status` | `VARCHAR(24)` | `IN_STOCK`, `RESERVED`, `SOLD`, `RETURNED`, `DAMAGED` |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

## 5. Đơn hàng và snapshot thương mại

### 5.1 `ORDERS`

Mục đích: đơn bán hàng và nguồn transaction cho mining sau khi `COMPLETED`.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `order_id` | `VARCHAR(40)` | PK |
| `checkout_id` | `VARCHAR(40)` nullable | Unique FK → `CHECKOUT_SESSION`; null với dữ liệu import |
| `order_number` | `VARCHAR(60)` | Unique mã hiển thị |
| `customer_id` | `VARCHAR(40)` | FK → `CUSTOMER`; bắt buộc để tạo sequence |
| `ordered_at` | `DATETIME(6)` | Thời điểm đặt |
| `completed_at` | `DATETIME(6)` nullable | Thời điểm hoàn tất, dùng sắp sequence |
| `status` | `VARCHAR(32)` | `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `RETURNED`, `REFUNDED` |
| `source` | `VARCHAR(40)` | `WEB`, `IMPORT`, `ADMIN`... |
| `currency_code` | `CHAR(3)` | Đồng tiền hóa đơn |
| `list_subtotal` | `DECIMAL(19,2)` | Tổng trước giảm/thuế/phí |
| `combo_discount_total` | `DECIMAL(19,2)` | Tổng giảm từ combo |
| `other_discount_total` | `DECIMAL(19,2)` | Giảm khác, tách khỏi combo |
| `shipping_fee` | `DECIMAL(19,2)` | Phí vận chuyển |
| `tax_total` | `DECIMAL(19,2)` | Thuế |
| `grand_total` | `DECIMAL(19,2)` | Tổng phải trả |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

Chỉ `COMPLETED` được chiếu sang dataset mining. Sau khi một snapshot dùng order,
không được âm thầm sửa lịch sử order/item đó.

### 5.2 `ORDER_ITEM`

Mục đích: dòng sản phẩm và giá thực tế đóng băng của đơn.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `order_item_id` | `BIGINT UNSIGNED` | PK |
| `order_id` | `VARCHAR(40)` | FK → `ORDERS` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `order_applied_combo_id` | `VARCHAR(40)` nullable | FK composite cùng `order_id` |
| `quantity` | `INT UNSIGNED` | > 0 |
| `list_unit_price` | `DECIMAL(19,2)` | Giá niêm yết snapshot lúc chốt |
| `allocated_discount_amount` | `DECIMAL(19,2)` | Tổng discount phân bổ vào dòng |
| `line_total` | `DECIMAL(19,2)` | `price * quantity - allocated discount` |
| `sku_snapshot` | `VARCHAR(80)` | SKU lúc bán |
| `product_name_snapshot` | `VARCHAR(200)` | Tên lúc bán |

### 5.3 `ORDER_APPLIED_COMBO`

Mục đích: combo và chính sách giá thực sự áp dụng cho order.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `order_applied_combo_id` | `VARCHAR(40)` | PK |
| `order_id` | `VARCHAR(40)` | FK → `ORDERS`; unique cùng ID cho composite FK |
| `combo_id` | `VARCHAR(40)` | FK → `COMBO`; combo không hard-delete |
| `combo_name_snapshot` | `VARCHAR(255)` | Tên tại thời điểm mua |
| `discount_type_snapshot` | `VARCHAR(24)` | Policy tại thời điểm mua |
| `discount_value_snapshot` | `DECIMAL(19,4)` | % hoặc số tiền/giá cố định theo type |
| `currency_code` | `CHAR(3)` | Đồng tiền |
| `list_subtotal` | `DECIMAL(19,2)` | Tổng list của các item combo |
| `discount_base_subtotal` | `DECIMAL(19,2)` | Phần đủ điều kiện giảm |
| `discount_amount` | `DECIMAL(19,2)` | Số tiền giảm chính xác |
| `final_subtotal` | `DECIMAL(19,2)` | Sau giảm |
| `applied_at` | `DATETIME(6)` | Audit |

### 5.4 `ORDER_ADDRESS`

Mục đích: snapshot địa chỉ hóa đơn/giao hàng, độc lập `USER_ADDRESS`.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `order_address_id` | `VARCHAR(40)` | PK |
| `order_id` | `VARCHAR(40)` | FK → `ORDERS` |
| `address_type` | `VARCHAR(16)` | `SHIPPING`, `BILLING`; unique/order/type |
| `recipient_name`, `phone` | `VARCHAR` | PII snapshot |
| `address_line` | `VARCHAR(500)` | PII snapshot |
| `ward_code/name`, `district_code/name`, `province_code/name` | `VARCHAR` | Snapshot hành chính |
| `postal_code` | `VARCHAR(20)` nullable | Mã bưu chính |
| `country_code` | `CHAR(2)` | Quốc gia |

### 5.5 `ORDER_STATUS_HISTORY`

Mục đích: audit append-only các chuyển trạng thái đơn.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `order_status_history_id` | `BIGINT UNSIGNED` | PK |
| `order_id` | `VARCHAR(40)` | FK → `ORDERS` |
| `from_status` | `VARCHAR(32)` nullable | Trạng thái trước |
| `to_status` | `VARCHAR(32)` | Trạng thái sau |
| `reason` | `VARCHAR(1000)` nullable | Lý do |
| `changed_by_type` | `VARCHAR(24)` | `USER`, `SYSTEM`, `PROVIDER` |
| `changed_by_id` | `VARCHAR(80)` nullable | User/provider reference |
| `created_at` | `DATETIME(6)` | Thời điểm chuyển |

## 6. Cart và checkout

### 6.1 `CART`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `cart_id` | `VARCHAR(40)` | PK |
| `customer_id` | `VARCHAR(40)` nullable | FK → `CUSTOMER` khi đăng nhập |
| `guest_token_hash` | `VARCHAR(255)` nullable | Hash token guest; không lưu raw |
| `status` | `VARCHAR(24)` | `ACTIVE`, `CONVERTED`, `ABANDONED`, `EXPIRED` |
| `currency_code` | `CHAR(3)` | Một currency/cart |
| `version` | `BIGINT` | Optimistic lock |
| `expires_at` | `DATETIME(6)` nullable | Hết hạn guest/inactive cart |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

Cart thuộc customer hoặc guest token. Totals cart chỉ là estimate, không phải hóa đơn.
Khi guest confirm checkout, backend tạo/ánh xạ một `CUSTOMER` giả danh trước khi
tạo `ORDERS`, để mọi order hoàn tất luôn có sequence identity hợp lệ.

### 6.2 `CART_APPLIED_COMBO`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `cart_applied_combo_id` | `VARCHAR(40)` | PK |
| `cart_id` | `VARCHAR(40)` | FK → `CART`; unique cùng ID cho composite FK |
| `combo_id` | `VARCHAR(40)` | FK → `COMBO` |
| `status` | `VARCHAR(24)` | `SELECTED`, `INVALIDATED`, `REMOVED` |
| `estimated_list_subtotal` | `DECIMAL(19,2)` | Estimate trước giảm |
| `estimated_discount_amount` | `DECIMAL(19,2)` | Estimate giảm |
| `estimated_final_subtotal` | `DECIMAL(19,2)` | Estimate sau giảm |
| `calculated_at` | `DATETIME(6)` | Lúc tính estimate |
| `invalid_reason` | `VARCHAR(500)` nullable | Giá/combo/item thay đổi |

### 6.3 `CART_ITEM`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `cart_item_id` | `BIGINT UNSIGNED` | PK |
| `cart_id` | `VARCHAR(40)` | FK → `CART` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `cart_applied_combo_id` | `VARCHAR(40)` nullable | Composite FK cùng cart |
| `quantity` | `INT UNSIGNED` | > 0 |
| `estimated_unit_price` | `DECIMAL(19,2)` | Cache UI, không tin khi checkout |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

### 6.4 `CHECKOUT_SESSION`

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `checkout_id` | `VARCHAR(40)` | PK |
| `cart_id` | `VARCHAR(40)` | FK → `CART` |
| `customer_id` | `VARCHAR(40)` nullable | FK → `CUSTOMER` |
| `idempotency_scope` | `VARCHAR(128)` | Server-derived customer/guest scope |
| `idempotency_key` | `VARCHAR(128)` | Unique cùng scope |
| `cart_version_snapshot` | `BIGINT` | Phát hiện cart đổi sau quote |
| `status` | `VARCHAR(32)` | `CREATED`, `QUOTED`, `CONFIRMED`, `PAYMENT_PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED` |
| `currency_code` | `CHAR(3)` | Đồng tiền quote |
| `list_subtotal` | `DECIMAL(19,2)` | Tổng list |
| `combo_discount_total` | `DECIMAL(19,2)` | Giảm combo |
| `other_discount_total` | `DECIMAL(19,2)` | Giảm khác |
| `shipping_fee` | `DECIMAL(19,2)` | Phí vận chuyển |
| `tax_total` | `DECIMAL(19,2)` | Thuế |
| `grand_total` | `DECIMAL(19,2)` | Tổng quote |
| `quote_expires_at` | `DATETIME(6)` | Hết hạn giá |
| `version` | `BIGINT` | Optimistic lock |
| `failure_code` | `VARCHAR(80)` nullable | Mã lỗi ổn định, không chứa secret |
| `created_at`, `updated_at`, `completed_at` | `DATETIME(6)` | Audit |

### 6.5 `CHECKOUT_ADDRESS`

Mục đích: địa chỉ đang dùng trong checkout, sẽ copy sang order.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `checkout_address_id` | `VARCHAR(40)` | PK |
| `checkout_id` | `VARCHAR(40)` | FK → `CHECKOUT_SESSION` |
| `address_type` | `VARCHAR(16)` | `SHIPPING`, `BILLING` |
| `recipient_name`, `phone`, `address_line` | `VARCHAR` | PII |
| `ward_code/name`, `district_code/name`, `province_code/name` | `VARCHAR` | Địa chỉ hành chính |
| `postal_code` | `VARCHAR(20)` nullable | Mã bưu chính |
| `country_code` | `CHAR(2)` | Quốc gia |

### 6.6 `STOCK_RESERVATION`

Mục đích: giữ tồn có thời hạn cho checkout.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `reservation_id` | `VARCHAR(40)` | PK |
| `checkout_id` | `VARCHAR(40)` | FK → `CHECKOUT_SESSION` |
| `warehouse_id` | `VARCHAR(40)` | FK → `WAREHOUSE` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `quantity` | `INT UNSIGNED` | > 0 |
| `status` | `VARCHAR(24)` | `ACTIVE`, `CONSUMED`, `RELEASED`, `EXPIRED` |
| `expires_at` | `DATETIME(6)` | Hạn giữ |
| `created_at`, `consumed_at`, `released_at` | `DATETIME(6)` | Audit |

Unique `(checkout_id, warehouse_id, product_id)`.

## 7. Thanh toán

### 7.1 `PAYMENT`

Mục đích: trạng thái thanh toán tổng hợp của order.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `payment_id` | `VARCHAR(40)` | PK |
| `order_id` | `VARCHAR(40)` | Unique FK → `ORDERS` |
| `method` | `VARCHAR(32)` | `COD`, `BANK_TRANSFER`, `E_WALLET`, `CARD` |
| `currency_code` | `CHAR(3)` | Phải khớp order |
| `amount` | `DECIMAL(19,2)` | Số phải thanh toán |
| `status` | `VARCHAR(32)` | `PENDING`, `AUTHORIZED`, `PAID`, `PARTIALLY_REFUNDED`, `REFUNDED`, `FAILED`, `CANCELLED` |
| `paid_amount` | `DECIMAL(19,2)` | Tổng capture thành công |
| `refunded_amount` | `DECIMAL(19,2)` | Tổng refund thành công |
| `created_at`, `updated_at`, `paid_at` | `DATETIME(6)` | Audit |

### 7.2 `PAYMENT_ATTEMPT`

Mục đích: từng lần thử thanh toán/provider redirect.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `payment_attempt_id` | `VARCHAR(40)` | PK |
| `payment_id` | `VARCHAR(40)` | FK → `PAYMENT` |
| `provider` | `VARCHAR(40)` | Gateway/COD handler |
| `method` | `VARCHAR(32)` | Phương thức của attempt |
| `idempotency_key` | `VARCHAR(128)` | Unique |
| `provider_payment_id` | `VARCHAR(160)` nullable | Unique opaque provider reference |
| `amount` | `DECIMAL(19,2)` | Số tiền attempt |
| `currency_code` | `CHAR(3)` | Đồng tiền |
| `status` | `VARCHAR(32)` | `CREATED`, `PENDING`, `AUTHORIZED`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED` |
| `redirect_url` | `VARCHAR(2048)` nullable | URL ngắn hạn, không chứa secret dài hạn |
| `expires_at` | `DATETIME(6)` nullable | Hết hạn thanh toán |
| `failure_code`, `failure_message` | `VARCHAR` nullable | Lỗi đã lọc dữ liệu nhạy cảm |
| `created_at`, `updated_at` | `DATETIME(6)` | Audit |

### 7.3 `PAYMENT_TRANSACTION`

Mục đích: sổ giao dịch tiền với provider.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `payment_transaction_id` | `VARCHAR(40)` | PK |
| `payment_id` | `VARCHAR(40)` | FK → `PAYMENT` |
| `payment_attempt_id` | `VARCHAR(40)` nullable | FK → attempt |
| `provider` | `VARCHAR(40)` | Provider/COD handler |
| `type` | `VARCHAR(24)` | `AUTHORIZE`, `CAPTURE`, `REFUND`, `VOID` |
| `provider_transaction_id` | `VARCHAR(160)` | Unique chống cộng tiền hai lần |
| `amount` | `DECIMAL(19,2)` | Giá trị transaction |
| `currency_code` | `CHAR(3)` | Đồng tiền |
| `status` | `VARCHAR(24)` | `PENDING`, `SUCCEEDED`, `FAILED` |
| `processed_at`, `created_at` | `DATETIME(6)` | Audit |

### 7.4 `PAYMENT_WEBHOOK_EVENT`

Mục đích: inbox/audit webhook idempotent.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `webhook_event_id` | `BIGINT UNSIGNED` | PK |
| `provider` | `VARCHAR(40)` | Provider |
| `provider_event_id` | `VARCHAR(160)` | Unique cùng provider |
| `payment_attempt_id` | `VARCHAR(40)` nullable | FK sau khi mapping được |
| `event_type` | `VARCHAR(80)` | Loại event provider |
| `payload_hash` | `VARCHAR(128)` | Hash payload phục vụ audit |
| `signature_valid` | `BOOLEAN` | Kết quả xác minh chữ ký |
| `status` | `VARCHAR(24)` | `RECEIVED`, `PROCESSED`, `IGNORED`, `FAILED` |
| `received_at`, `processed_at` | `DATETIME(6)` | Audit |
| `error_message` | `VARCHAR(1000)` nullable | Lỗi đã lọc secret/PII |

Không lưu PAN, CVV, mật khẩu ngân hàng hoặc access token provider trong các bảng trên.

## 8. Dataset, mining run và pattern

### 8.1 `ITEM_MAPPING`

Mục đích: ánh xạ ổn định SKU sang item nguyên dương cho SPMF.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `product_id` | `VARCHAR(40)` | PK/FK → `PRODUCT` |
| `item_id` | `INT UNSIGNED` | Unique, > 0, không tái sử dụng |
| `created_at` | `DATETIME(6)` | Audit |

Mỗi snapshot vẫn xuất dictionary/checksum riêng; pattern cũ không phụ thuộc mù
quáng vào trạng thái mapping hiện tại.

### 8.2 `DATASET_SNAPSHOT`

Mục đích: artifact input bất biến và báo cáo chuyển dữ liệu DB/CSV sang sequence.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `snapshot_id` | `VARCHAR(40)` | PK |
| `source_type` | `VARCHAR(32)` | `DATABASE`, `CSV`, `BENCHMARK`... |
| `source_name` | `VARCHAR(255)` | Tên/provenance nguồn |
| `from_time`, `to_time` | `DATETIME(6)` nullable | Cửa sổ giao dịch |
| `raw_file_path`, `sequence_file_path`, `mapping_file_path` | `VARCHAR(1024)` nullable | Artifact paths/object keys; legacy V1 có thể thiếu |
| `raw_sha256`, `sequence_sha256`, `mapping_sha256` | `CHAR(64)` nullable | Checksum tái lập; bắt buộc với snapshot mới hợp lệ |
| `validation_status` | `VARCHAR(32)` | `PENDING`, `VALID`, `INVALID`, `LEGACY_UNVERIFIED` |
| `conversion_report_json` | `JSON` | Số dòng hợp lệ/bị loại và lý do |
| `sequence_count` | `BIGINT UNSIGNED` | Số customer sequence |
| `single_itemset_sequence_count` | `BIGINT UNSIGNED` | Sequence chỉ một itemset |
| `multi_itemset_sequence_count` | `BIGINT UNSIGNED` | Sequence nhiều itemset |
| `itemset_count` | `BIGINT UNSIGNED` | Tổng order/itemset |
| `distinct_item_count` | `BIGINT UNSIGNED` | Số item khác nhau |
| `total_item_occurrences` | `BIGINT UNSIGNED` | Tổng lần xuất hiện sau dedup/order |
| `min_sequence_length`, `max_sequence_length` | `INT UNSIGNED` | Biên độ dài |
| `average_sequence_length` | `DOUBLE` | Trung bình mô tả dataset |
| `created_at` | `DATETIME(6)` | Audit |

Snapshot bất biến sau khi có run tham chiếu.

### 8.3 `MINING_RUN`

Mục đích: cấu hình, trạng thái, kết quả tổng và phép đo một lần chạy thuật toán.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `run_id` | `VARCHAR(40)` | PK |
| `snapshot_id` | `VARCHAR(40)` | FK → `DATASET_SNAPSHOT` |
| `algorithm` | `VARCHAR(32)` | `WSPM_PRETREE`, `WSPM_BITPRUNE` |
| `status` | `VARCHAR(32)` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `LEGACY_UNVERIFIED` |
| `dataset_source` | `VARCHAR(255)` | Nhãn nguồn để trình bày |
| `minsup_mode` | `VARCHAR(24)` | Absolute/relative/percentage theo contract |
| `minsup_configured` | `DOUBLE` | Giá trị người dùng nhập |
| `minsup_relative_fraction` | `DOUBLE` nullable | Giá trị relative đã chuẩn hóa |
| `effective_threshold` | `DOUBLE` | Ngưỡng nội bộ thực sự dùng |
| `max_w`, `min_w`, `mean_w` | `DOUBLE` | Trọng số toàn cục của run |
| `pattern_count` | `BIGINT UNSIGNED` nullable | Tổng pattern exact |
| `candidate_count` | `BIGINT UNSIGNED` nullable | Counter ứng viên theo contract |
| `dataset_io_ms` | `BIGINT UNSIGNED` nullable | Thời gian đọc dataset |
| `preprocessing_ms` | `BIGINT UNSIGNED` nullable | Tiền xử lý |
| `dfs_mining_ms` | `BIGINT UNSIGNED` nullable | DFS mining |
| `complete_algorithm_ms` | `BIGINT UNSIGNED` nullable | Toàn thuật toán, không output I/O |
| `output_io_ms` | `BIGINT UNSIGNED` nullable | Ghi output |
| `total_execution_ms` | `BIGINT UNSIGNED` nullable | Tổng scope đã định nghĩa |
| `peak_sampled_jvm_heap_bytes` | `BIGINT UNSIGNED` nullable | JVM heap sampling, không gọi là RAM chính xác |
| `legacy_metadata_json` | `JSON` nullable | Chỉ giữ provenance V1 không ánh xạ được; run mới để null |
| `error_message` | `VARCHAR(2000)` nullable | Lỗi đã lọc dữ liệu nhạy cảm |
| `created_at`, `started_at`, `finished_at` | `DATETIME(6)` | Audit vòng đời |
| `execution_owner_id` | `VARCHAR(40)` nullable | V3: UUID của coordinator nhận run |
| `execution_attempt_id` | `VARCHAR(40)` nullable | V3: UUID của lần nhận; fence cùng owner |
| `execution_lease_until` | `DATETIME(6)` nullable | V3: hạn lease theo UTC DB clock |

V3 yêu cầu ba trường ownership cùng null hoặc cùng có giá trị, index
`(status, execution_lease_until)`, không backfill hoặc sửa status run V2.
Runtime migration: `backend/src/main/resources/db/migration/V3__mining_run_execution_ownership.sql`.
Chỉ run có ownership và lease hết hạn, ở QUEUED/RUNNING mới thuộc recovery.
Run legacy/unowned giữ nguyên. WDM-05.5 dùng mã lỗi enum cố định trong
`error_message`; SUCCEEDED chưa xác nhận durable pattern storage. Upgrade V3
đã được kiểm thử trên MySQL riêng; trạng thái database owner vẫn NOT VERIFIED.

Cập nhật WDM-05.7 (2026-11-07): đã xác minh local `127.0.0.1:3307/wspm_combo`
có V3 SUCCESS/checksum `1697070581`; Flyway validate/migrate(target=3)/validate
PASS, 0 migration mới vì V3 đã có trước lượt này. Ba cột nullable, CHECK cùng
null/cùng có giá trị và index lease đều tồn tại. Không chạy lại DDL hoặc repair.

WDM-05.6 dùng các cột metrics V2 hiện có, không thêm migration. Với run mới thành
công, status/timestamps và toàn bộ metrics commit cùng CAS ownership/lease:
dataset_io_ms đo ba verified storage read (raw/sequence/mapping) trong worker;
preprocessing/dfs/complete_algorithm lấy nguyên phép đo core. total_execution_ms
đo từ sau claim, trước refresh đến engine return, loại trừ queue, pre-admission
và terminal DB finalization. Các scope có phần lồng nhau, không cộng tất cả.
Nanos chuyển millis bằng chia nguyên 1.000.000. output_io_ms vẫn null; failure
không có complete metrics thì để null. Peak heap là JVM-wide sampling trong
thuật toán, không phải toàn worker hoặc RAM riêng. Không backfill dữ liệu cũ.

Không có `top_k`, `minWeight`, hệ số ranking hoặc ComboScore trong run.

### 8.4 `RUN_ITEM_STATISTIC`

Mục đích: dictionary và bằng chứng item weight bất biến của đúng run.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `run_id` | `VARCHAR(40)` | PK/FK → `MINING_RUN` |
| `item_id` | `INT UNSIGNED` | PK; item trong dictionary run |
| `product_id` | `VARCHAR(40)` | Unique/run, FK logic → product |
| `occurrence_count` | `BIGINT UNSIGNED` | Số occurrence theo preprocessing contract |
| `sequence_support` | `BIGINT UNSIGNED` | Số sequence chứa item |
| `item_weight` | `DOUBLE` | Trọng số item dùng tính pattern weight |
| `product_name_snapshot` | `VARCHAR(200)` | Tên lúc chạy |
| `sku_snapshot` | `VARCHAR(80)` | SKU lúc chạy |

### 8.5 `PATTERN`

Mục đích: complete output pattern của một run.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `pattern_id` | `BIGINT UNSIGNED` | PK |
| `run_id` | `VARCHAR(40)` | FK → `MINING_RUN` |
| `canonical_pattern` | `TEXT` | Chuỗi đầy đủ bảo toàn itemset boundaries |
| `canonical_pattern_hash` | `CHAR(64)` | SHA-256, unique/run để index toàn pattern |
| `support_abs` | `BIGINT UNSIGNED` | Exact sequence support |
| `support_ratio` | `DOUBLE` | `support_abs / sequence_count` |
| `weighted_support` | `DOUBLE` | `support_abs * mean_w` theo predicate hiện hành |
| `pattern_item_count` | `INT UNSIGNED` | Tổng item occurrences trong pattern |
| `itemset_count` | `INT UNSIGNED` | Số itemset |
| `pattern_weight` | `DOUBLE` | Trọng số mẫu tầng ứng dụng |
| `pattern_weight_policy` | `VARCHAR(80)` | Ví dụ `EQ2_EQ3_ARITHMETIC_MEAN_V1` |

Không gộp `<(a b)>` với `<(a)(b)>`.

### 8.6 `PATTERN_ITEM`

Mục đích: cấu trúc có thứ tự của pattern.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `pattern_id` | `BIGINT UNSIGNED` | PK/FK → `PATTERN` |
| `itemset_order` | `INT UNSIGNED` | PK; thứ tự itemset, bắt đầu 1 |
| `item_order` | `INT UNSIGNED` | PK; thứ tự ổn định trong itemset |
| `item_id` | `INT UNSIGNED` | Phải tồn tại trong dictionary cùng run |

## 9. Sinh, duyệt và công bố combo

### 9.1 `COMBO_BATCH`

Mục đích: một lần biến pattern thành candidate/ranking mà không chạy lại mining.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `batch_id` | `VARCHAR(40)` | PK |
| `run_id` | `VARCHAR(40)` | FK → `MINING_RUN` |
| `ranking_policy` | `VARCHAR(80)` | Chỉ `SUPPORT_THEN_PATTERN_WEIGHT_V1` |
| `pattern_weight_policy` | `VARCHAR(80)` | Policy đã dùng |
| `top_k` | `INT UNSIGNED` | Giới hạn công bố/truy vấn theo anchor |
| `category_filter_enabled` | `BOOLEAN` | Bật business filter |
| `min_product_count`, `max_product_count` | `INT UNSIGNED` | Kích thước combo hợp lệ |
| `status` | `VARCHAR(24)` | `DRAFT`, `ACTIVE`, `ARCHIVED` |
| `created_by`, `activated_by` | `VARCHAR(40)` nullable | FK → `USER_ACCOUNT` |
| `created_at`, `activated_at` | `DATETIME(6)` | Audit |

Trong demo chỉ một batch `ACTIVE`; activate batch mới archive batch cũ cùng transaction.

### 9.2 `COMBO`

Mục đích: candidate thương mại có vòng đời riêng với pattern khoa học.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `combo_id` | `VARCHAR(40)` | PK |
| `batch_id` | `VARCHAR(40)` | FK → `COMBO_BATCH` |
| `name` | `VARCHAR(255)` | Tên hiển thị |
| `combo_kind` | `VARCHAR(40)` | `SAME_ORDER_BUNDLE`, `SEQUENTIAL_CROSS_SELL`, `MIXED_SEQUENCE` |
| `status` | `VARCHAR(24)` | `DRAFT`, `APPROVED`, `REJECTED`, `PUBLISHED`, `ARCHIVED` |
| `discount_type` | `VARCHAR(24)` | `NONE`, `PERCENTAGE`, `FIXED_AMOUNT`, `FIXED_PRICE` |
| `discount_value` | `DECIMAL(19,4)` nullable | % hoặc amount/target price theo type |
| `max_discount_amount` | `DECIMAL(19,2)` nullable | Trần cho percentage |
| `stacking_policy` | `VARCHAR(24)` | Mặc định `NON_STACKABLE` |
| `currency_code` | `CHAR(3)` | Đồng tiền |
| `reference_original_price` | `DECIMAL(19,2)` | Snapshot khi publish |
| `reference_sale_price` | `DECIMAL(19,2)` | Snapshot sau discount khi publish |
| `support_abs` | `BIGINT UNSIGNED` | Kế thừa primary pattern |
| `support_ratio` | `DOUBLE` | Kế thừa primary pattern |
| `pattern_weight` | `DOUBLE` | Kế thừa primary pattern |
| `explanation` | `VARCHAR(1000)` | Lời giải thích không phóng đại cá nhân hóa |
| `valid_from`, `valid_to` | `DATETIME(6)` nullable | Khoảng hiệu lực |
| `created_at`, `reviewed_at`, `published_at` | `DATETIME(6)` | Audit |
| `reviewed_by` | `VARCHAR(40)` nullable | FK → `USER_ACCOUNT` |
| `review_note` | `VARCHAR(1000)` nullable | Lý do duyệt/từ chối |

Không có `combo_score` hoặc `rank_position`. Rank được tính sau khi lọc product anchor.

### 9.3 `COMBO_ITEM`

Mục đích: sản phẩm, vị trí temporal và quyền giảm giá trong combo.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `combo_id` | `VARCHAR(40)` | PK/FK → `COMBO` |
| `product_id` | `VARCHAR(40)` | FK → `PRODUCT` |
| `itemset_order` | `INT UNSIGNED` | PK; bảo toàn temporal structure |
| `item_order` | `INT UNSIGNED` | PK; thứ tự ổn định |
| `quantity` | `INT UNSIGNED` | Số lượng bundle, mặc định 1 |
| `unit_price_snapshot` | `DECIMAL(19,2)` | Giá khi sinh/duyệt combo |
| `is_anchor_eligible` | `BOOLEAN` | Có được dùng làm product context |
| `is_discount_eligible` | `BOOLEAN` | Có nằm trong discount base |

Index `(product_id, is_anchor_eligible, combo_id)` phục vụ Top-K storefront.

### 9.4 `COMBO_SOURCE`

Mục đích: provenance combo ↔ pattern.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `combo_id` | `VARCHAR(40)` | PK/FK → `COMBO` |
| `pattern_id` | `BIGINT UNSIGNED` | PK/FK → `PATTERN` |
| `is_primary` | `BOOLEAN` | Đúng một primary source/combo |

Tất cả metrics của combo phải đến từ cùng primary pattern; không ghép MAX độc lập.

### 9.5 `CATEGORY_EXCLUSION`

Mục đích: business rule loại cặp danh mục không được tạo combo.

| Trường | Kiểu đề xuất | Ý nghĩa/ràng buộc |
|---|---|---|
| `category_id_low` | `VARCHAR(40)` | PK/FK → `CATEGORY`; canonical smaller ID |
| `category_id_high` | `VARCHAR(40)` | PK/FK → `CATEGORY`; canonical larger ID |
| `reason` | `VARCHAR(500)` | Lý do loại |
| `active` | `BOOLEAN` | Hiệu lực |
| `created_at` | `DATETIME(6)` | Audit |

## 10. Danh sách đầy đủ và ranh giới dữ liệu

Schema V2 có 45 bảng:

1. Identity: `USER_ACCOUNT`, `ROLE`, `USER_ROLE`, `USER_SESSION`, `USER_ADDRESS`,
   `CUSTOMER`.
2. Catalog: `CATEGORY`, `BRAND`, `PRODUCT`, `PRODUCT_IMAGE`.
3. Inventory/purchasing: `WAREHOUSE`, `SUPPLIER`, `PURCHASE_ORDER`,
   `PURCHASE_ORDER_ITEM`, `GOODS_RECEIPT`, `GOODS_RECEIPT_ITEM`,
   `INVENTORY_BALANCE`, `STOCK_MOVEMENT`, `INVENTORY_SERIAL`.
4. Sales: `ORDERS`, `ORDER_ITEM`, `ORDER_APPLIED_COMBO`, `ORDER_ADDRESS`,
   `ORDER_STATUS_HISTORY`.
5. Cart/checkout: `CART`, `CART_APPLIED_COMBO`, `CART_ITEM`, `CHECKOUT_SESSION`,
   `CHECKOUT_ADDRESS`, `STOCK_RESERVATION`.
6. Payment: `PAYMENT`, `PAYMENT_ATTEMPT`, `PAYMENT_TRANSACTION`,
   `PAYMENT_WEBHOOK_EVENT`.
7. Mining: `ITEM_MAPPING`, `DATASET_SNAPSHOT`, `MINING_RUN`,
   `RUN_ITEM_STATISTIC`, `PATTERN`, `PATTERN_ITEM`.
8. Combo: `COMBO_BATCH`, `COMBO`, `COMBO_ITEM`, `COMBO_SOURCE`,
   `CATEGORY_EXCLUSION`.

### Bất biến xuyên bảng bắt buộc

- `available = on_hand - reserved >= 0`.
- Receipt `POSTED` cập nhật balance và movement trong cùng transaction.
- Sale issue giảm reservation/on-hand và ghi movement trong cùng transaction.
- Totals order bằng tổng line và các thành phần tiền đã khai báo.
- Retry checkout/payment cùng idempotency key không tạo hiệu ứng thứ hai.
- Webhook cùng `(provider, provider_event_id)` chỉ xử lý một lần.
- Chỉ order `COMPLETED` và item dedup theo order được chiếu sang mining.
- Pattern lưu đủ itemset boundaries và exact support.
- Chỉ combo `PUBLISHED` thuộc batch `ACTIVE`, đúng thời hạn và anchor mới lên UI.
- User/address/payment PII hoặc secret không được vào mining artifact/log.

### Chưa nằm trong schema này

- Nhân sự/HR, chấm công, lương.
- Vận đơn/carrier tracking đầy đủ.
- Voucher engine và loyalty points.
- Chat/notification center.
- Kế toán tổng hợp, công nợ nhà cung cấp và giá vốn FIFO/average hoàn chỉnh.
- Multi-provider payment production certification.

Các phần này có thể mở rộng bằng module riêng; không nên thêm bảng giả chỉ để làm
schema có vẻ lớn hơn khi không có nghiệp vụ, API và test tương ứng.
