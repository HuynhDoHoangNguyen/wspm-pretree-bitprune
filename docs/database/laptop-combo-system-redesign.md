# Đề xuất thiết kế lại dữ liệu và giao diện hệ thống combo laptop

**Trạng thái:** SCHEMA IMPLEMENTED BY FLYWAY V2 — application/API/frontend vẫn
đang được triển khai và review theo từng lát cắt

Flyway V2 hiện nằm trong runtime classpath. Kiểm tra read-only database local
ngày 2026-11-03 xác nhận V1/V2 đều thành công trong `flyway_schema_history`,
checksum V2 `727165286` khớp file runtime, đủ đúng 45 bảng V2 và không còn bảng
`LEGACY_V1_*`. Kết quả này không chứng minh trạng thái database khác, tính đúng
của toàn bộ dữ liệu nghiệp vụ hoặc việc application flow đã được hiện thực.

**Phạm vi:** backend, database và frontend minh họa khóa luận

**Không thay đổi:** hợp đồng WSPM_PreTree, WSPM_BitPrune hoặc kết quả
`pattern -> exact support` đã được nghiệm thu

Tài liệu đã được rà soát lại với:

- `TV2_KhaoSatBaiToan_HeThongGoiYCombo_V5.docx`;
- `TV2_KhaoSatVaThietKeDuLieu_V3.docx`;
- `ChiTietCacManHinhGiaoDien.docx`;
- `DeXuatChinhSua.docx`;
- mã nguồn backend/frontend hiện hành.

Liên kết trong `docs/link figma.txt` đã được xác định nhưng nội dung Figma chưa
được truy cập trong phiên review này do chưa có kết nối/quyền đọc. Mọi nhận xét
về giao diện dưới đây dựa trên tài liệu chi tiết màn hình và mã nguồn, không giả
vờ là đã xác minh trực quan file Figma.

Danh sách đầy đủ 45 bảng, kiểu dữ liệu đề xuất và ý nghĩa từng trường nằm tại
`docs/database/laptop-combo-data-dictionary.md`.

## 1. Kết luận thiết kế

Schema hiện tại có khung đúng gồm giao dịch, phiên khai phá, mẫu và combo, nhưng
chưa đủ để tạo một luồng ứng dụng có thể giải thích và tái lập. Frontend Electro
lại rộng hơn rất nhiều so với backend và dùng mô hình dữ liệu không tương thích.

Thiết kế đề xuất tập trung vào cửa hàng laptop và hai kịch bản trong đề cương:

1. Khách hàng xem/chọn một sản phẩm và nhận Top-K combo đã công bố mà sản phẩm
   đó đủ điều kiện làm điểm neo theo cấu trúc itemset/thời gian của pattern.
2. Quản trị viên nhập lịch sử mua hàng, chạy thuật toán, xem mẫu nguồn, lọc/xếp
   hạng, duyệt combo và xem số liệu thực nghiệm.

Không triển khai trong phạm vi tối thiểu: nhân sự, kho đa chi nhánh, nhập hàng,
vận chuyển, chat, điểm thưởng và voucher. Schema có contract cho cart, checkout
và payment để hệ thống hoàn chỉnh về mặt thiết kế, nhưng implementation vẫn là
lát cắt thứ cấp sau pipeline mining/combo. Các màn Electro nguyên trạng không
được mặc nhiên xem là yêu cầu của khóa luận.

## 2. Vấn đề của hiện trạng

### 2.1 Database

- `PRODUCT` chỉ có tên, danh mục, trạng thái và giá; thiếu hãng, mã SKU, mô tả,
  ảnh và thông số tối thiểu để hiển thị laptop/phụ kiện.
- `CUSTOMER` chỉ có ID. Có thể đủ để khai phá ẩn danh, nhưng chưa đủ cho màn hình
  quản trị và chưa thể hiện rõ ID này là mã giả danh.
- `RUN` trộn cấu hình mining với cấu hình ứng dụng (`top_k`) và các trường điểm
  tổng hợp đã bị loại khỏi hướng thiết kế mới.
- `RUN` không lưu đầy đủ mode ngưỡng, giá trị người dùng nhập, ngưỡng hiệu dụng,
  thời điểm dữ liệu, checksum và trạng thái/lỗi. Vì vậy khó tái lập một lần chạy.
- Tài liệu TV2 dự kiến `PATTERN.weight = W(α)` theo Eq. (2)/(3), nhưng tên cột
  hiện tại chưa thể hiện chính sách tính; ví dụ tính tay của bài báo lại bất nhất
  với công thức. Cần tên rõ `pattern_weight` và `pattern_weight_policy`.
- `ITEM_MAPPING` là ánh xạ toàn cục. Nếu ánh xạ thay đổi, mẫu cũ có nguy cơ mất
  quan hệ tái lập với dictionary của đúng lần chạy.
- `COMBO.combo_score` và các cột hệ số trong V1 là di sản của thiết kế sơ bộ;
  phải được loại khỏi schema đích, entity, DTO, repository và index liên quan.
- Combo chưa có giá gốc, giá bán, khoảng hiệu lực, lý do duyệt/từ chối, người
  duyệt và thứ hạng đã công bố.
- Chưa lưu tập đơn hàng thực sự thuộc snapshot của lần chạy. Một đường dẫn
  `input_snapshot` đơn lẻ không đủ chứng minh dữ liệu đầu vào còn nguyên vẹn.

### 2.2 Backend/API

- Backend thực tế mới có API đọc category và product.
- DTO `RunResponse`/`RunCreateRequest` không khớp `RUN`; `minWeight` trong request
  không phải tham số đầu vào của thuật toán vì item weight và MeanW được tính từ
  dữ liệu.
- DTO create/update product dùng `unitPrice`, `isActive`, `weight`, trong khi
  entity dùng `price`, `productStatus` và không có `weight`.
- Đã có DTO/entity/repository cho order, run, pattern và combo nhưng chưa có
  application service/controller tạo thành luồng nghiệp vụ.

### 2.3 Frontend

- Frontend thừa kế Electro có nhiều feature không có backend tương ứng.
- Model TypeScript dùng ID số, variant, brand, supplier, image, specification...
  trong khi API hiện tại trả ID chuỗi và product DTO chỉ có 5 trường.
- Frontend gọi `http://localhost:8085`, còn backend và proxy cấu hình cổng 8080.
- Không có model, route hoặc màn hình mining run, pattern, combo review hay gợi ý
  Top-K — đây mới là phần trung tâm của khóa luận.

## 3. Ngữ nghĩa dữ liệu giao dịch

Đây là quyết định bắt buộc trước khi tích hợp mining:

- Một khách hàng giả danh (`customer_id`) tạo thành một sequence.
- Mỗi đơn hàng `COMPLETED` tạo thành một itemset.
- Các đơn của cùng khách hàng được sắp theo
  `(completed_at, order_id)` để có thứ tự xác định.
- Mỗi SKU xuất hiện trong đơn là một item. `quantity` vẫn được lưu cho nghiệp vụ,
  nhưng sự có mặt của item trong itemset chỉ ghi một lần khi chuyển sang SPMF.
- Chỉ `COMPLETED` đi vào dữ liệu mining. `PENDING_PAYMENT`, `PAID`, `PROCESSING`,
  `CANCELLED`, `EXPIRED`, `RETURNED` và các trạng thái khác đều bị loại và được
  đếm theo lý do.
- Không suy diễn thứ tự giữa các sản phẩm trong cùng một đơn hàng.
- Không dùng dữ liệu FIMI transaction để tuyên bố hành vi mua trước/sau nếu không
  có nguồn temporal được tài liệu hóa.

Quy tắc này bảo toàn khác biệt giữa `<(laptop mouse)>` (cùng đơn) và
`<(laptop)(mouse)>` (mua ở đơn sau).

## 4. Mô hình dữ liệu đề xuất

```text
CATEGORY 1---n PRODUCT n---1 BRAND
                    |
                    +---n PRODUCT_IMAGE

USER_ACCOUNT n---n ROLE (qua USER_ROLE)
     |
     +---n USER_SESSION
     +---n USER_ADDRESS
     +---0..1 CUSTOMER

CUSTOMER 1---n ORDERS 1---n ORDER_ITEM n---1 PRODUCT
                     |
                     +---n ORDER_APPLIED_COMBO 1---n ORDER_ITEM
                     +---n ORDER_STATUS_HISTORY

CUSTOMER 1---n CART 1---n CART_ITEM n---1 PRODUCT
                   |
                   +---n CART_APPLIED_COMBO 1---n CART_ITEM

CART 1---n CHECKOUT_SESSION 1---n CHECKOUT_ADDRESS
                         |
                         +---n STOCK_RESERVATION
                         +---0..1 ORDERS 1---n ORDER_ADDRESS

ORDERS 1---0..1 PAYMENT 1---n PAYMENT_ATTEMPT 1---n PAYMENT_TRANSACTION
                              |
                              +---n PAYMENT_WEBHOOK_EVENT

SUPPLIER 1---n PURCHASE_ORDER 1---n PURCHASE_ORDER_ITEM n---1 PRODUCT
                         |
                         +---n GOODS_RECEIPT 1---n GOODS_RECEIPT_ITEM

WAREHOUSE 1---n INVENTORY_BALANCE n---1 PRODUCT
     |
     +---n GOODS_RECEIPT
     +---n STOCK_MOVEMENT n---1 PRODUCT
     +---n STOCK_RESERVATION

DATASET_SNAPSHOT 1---n MINING_RUN 1---n RUN_ITEM_STATISTIC
                              |
                              +---n PATTERN 1---n PATTERN_ITEM
                                       |
                                       +---n COMBO_SOURCE
                                                |
MINING_RUN 1---n COMBO_BATCH 1---n COMBO 1---n COMBO_ITEM
                                        |
                                        +---n ORDER_APPLIED_COMBO

CATEGORY 1---n CATEGORY_EXCLUSION n---1 CATEGORY
USER_ACCOUNT 1---n COMBO (reviewed_by)
```

### 4.1 Danh mục bán hàng

#### `CATEGORY`

`category_id`, `name`, `slug`, `description`, `display_order`, `status`.

Danh mục demo nên nhỏ và có ý nghĩa: Laptop, Chuột, Bàn phím, Balo/Túi chống sốc,
Tai nghe, Đế tản nhiệt, Màn hình và Hub/Cáp.

#### `BRAND`

`brand_id`, `name`, `slug`, `status`.

#### `PRODUCT`

`product_id`, `sku`, `name`, `slug`, `category_id`, `brand_id`, `short_description`,
`description`, `specifications_json`, `current_price`, `currency_code`,
`thumbnail_url`, `track_serial`, `status`, `price_updated_at`, `version`,
`created_at`, `updated_at`.

Quy ước: mỗi cấu hình bán được là một SKU/product riêng. Ví dụ hai cấu hình RAM
khác nhau là hai item khác nhau khi mining. `specifications_json` phù hợp cho demo
vì thông số laptop/phụ kiện không đồng nhất; không dùng nó cho khóa ngoại hoặc
thuộc tính cần lọc/xếp hạng chính.

#### `PRODUCT_IMAGE`

`image_id`, `product_id`, `image_url`, `alt_text`, `display_order`.

### 4.2 Nguồn giao dịch

#### `CUSTOMER`

`customer_id`, `user_id` (nullable), `anonymous_code`, `segment`, `created_at`.

Không lưu tên, email hoặc số điện thoại trong bảng phục vụ mining. Tài khoản đăng
nhập nằm ở `USER_ACCOUNT`; `anonymous_code` phải không cho phép suy ngược danh
tính từ dataset trình diễn. Guest vẫn có thể có `CUSTOMER` mà không có `user_id`.

#### `ORDERS`

`order_id`, `customer_id`, `ordered_at`, `completed_at`, `status`, `source`,
`currency_code`, `list_subtotal`, `combo_discount_total`, `grand_total`,
`created_at`, `updated_at`.

Chỉ cho phép sửa item của đơn trước khi hoàn tất. Sau `COMPLETED`, đơn dùng cho
mining phải bất biến; hoàn/đổi được thể hiện bằng trạng thái/sự kiện nghiệp vụ,
không âm thầm viết lại lịch sử đã dùng cho một run.

#### `ORDER_ITEM`

`order_item_id`, `order_id`, `product_id`, `order_applied_combo_id` (nullable),
`quantity`, `list_unit_price`, `allocated_discount_amount`, `line_total`,
`sku_snapshot`, `product_name_snapshot`.

Dùng khóa thay thế `order_item_id`, không dùng `(order_id, product_id)`, vì cùng
một product có thể xuất hiện trong một combo và thêm một dòng mua lẻ. Giá không
đọc lại từ product sau khi đơn đã chốt:

```text
line_total = list_unit_price * quantity - allocated_discount_amount
```

`allocated_discount_amount` là tổng giảm giá của cả dòng. Tổng số tiền phải được
backend kiểm tra lại từ các line, không nhận kết quả tính sẵn từ frontend. Cách
này tránh sai số khi một khoản giảm giá không chia đều được cho từng đơn vị.
Ràng buộc composite `(order_applied_combo_id, order_id)` phải tham chiếu đúng
`ORDER_APPLIED_COMBO` của cùng order, không cho line trỏ sang combo của đơn khác.

### 4.3 Phiên khai phá và khả năng tái lập

#### `ITEM_MAPPING`

`product_id`, `item_id`, `created_at`.

Giữ quyết định của TV2: ánh xạ product sang số nguyên là ổn định, không tái sử
dụng item ID và không xóa vật lý product đã được ánh xạ. Tuy nhiên mỗi snapshot
vẫn phải lưu bản sao dictionary hoặc checksum để mẫu cũ không phụ thuộc mù quáng
vào trạng thái hiện tại của bảng.

#### `DATASET_SNAPSHOT`

`snapshot_id`, `source_type`, `source_name`, `from_time`, `to_time`,
`raw_file_path`, `raw_sha256`, `sequence_file_path`, `sequence_sha256`,
`mapping_file_path`, `mapping_sha256`, `validation_status`,
`conversion_report_json`, `sequence_count`, `single_itemset_sequence_count`,
`multi_itemset_sequence_count`, `itemset_count`, `distinct_item_count`,
`total_item_occurrences`, `min_sequence_length`, `max_sequence_length`,
`average_sequence_length`, `created_at`.

Bảng này phục vụ đúng màn “Chuẩn bị dữ liệu”: upload/validate CSV, báo cáo số
dòng bị loại theo lý do, tải tệp chuỗi và chạy lại trên đúng artifact. Snapshot
phải bất biến sau khi được một run tham chiếu.

#### `MINING_RUN`

Các nhóm trường đề xuất:

- Danh tính: `run_id`, `algorithm`, `status`, `created_at`, `started_at`,
  `finished_at`, `error_message`.
- Phạm vi dữ liệu: `snapshot_id`, `dataset_source`; các thống kê input lấy từ
  snapshot nhưng có thể snapshot lại vào run response để trình bày.
- Ngưỡng: `minsup_mode`, `minsup_configured`, `minsup_relative_fraction`,
  `effective_threshold`.
- Trọng số toàn cục: `max_w`, `min_w`, `mean_w`.
- Kết quả: `pattern_count`, `candidate_count` và các counter BitPrune cần hiển
  thị/đối chiếu.
- Đo lường tách biệt: `dataset_io_ms`, `preprocessing_ms`, `dfs_mining_ms`,
  `complete_algorithm_ms`, `output_io_ms`, `total_execution_ms`,
  `peak_sampled_jvm_heap_bytes`.

Không lưu `minWeight` như input. Không lưu `top_k` hoặc hệ số ranking trong bảng
run thuật toán; ranking là bước ứng dụng riêng sau mining. Đây là chủ ý cải tiến
so với TV2 V3: cùng một tập pattern có thể thử nhiều chính sách xếp hạng mà không
chạy lại thuật toán.

#### `RUN_ITEM_STATISTIC`

`run_id`, `item_id`, `product_id`, `occurrence_count`, `sequence_support`,
`item_weight`, `product_name_snapshot`, `sku_snapshot`.

Khóa chính `(run_id, item_id)`, unique `(run_id, product_id)`. Đây là dictionary
bất biến của đúng lần chạy và là nơi lưu bằng chứng item weight.

#### `PATTERN`

`pattern_id`, `run_id`, `canonical_pattern`, `canonical_pattern_hash`, `support_abs`, `support_ratio`,
`weighted_support`, `pattern_item_count`, `itemset_count`, `pattern_weight`,
`pattern_weight_policy`.

- `weighted_support = support_abs * mean_w` là giá trị dùng trong predicate của
  thuật toán, không phải điểm cá nhân hóa.
- Theo lựa chọn đã ghi trong hai tài liệu TV2, `pattern_weight` được tầng ứng dụng
  tính theo công thức Eq. (2)/(3): trung bình item weight trong từng itemset rồi
  trung bình các itemset. Lưu policy cố định
  `EQ2_EQ3_ARITHMETIC_MEAN_V1`. Đây là **implementation/recommendation policy**,
  không phải output trực tiếp của mining engine và không giải quyết bất nhất ví
  dụ tính tay trong nguồn; cần GVHD phê duyệt trước khi tuyên bố source-confirmed.
- `canonical_pattern` có unique `(run_id, canonical_pattern)` để ngăn lưu trùng và
  giúp đối chiếu trực tiếp complete map.

#### `PATTERN_ITEM`

`pattern_id`, `itemset_order`, `item_order`, `item_id`.

Giữ nguyên biên itemset. Khóa ngoại logic phải được kiểm tra tới dictionary của
cùng `PATTERN.run_id`; không ánh xạ bằng dictionary toàn cục.

### 4.4 Combo và quy trình duyệt

#### `COMBO_BATCH`

`batch_id`, `run_id`, `ranking_policy`, `pattern_weight_policy`, `top_k`,
`category_filter_enabled`, `min_product_count`, `max_product_count`, `status`,
`created_at`, `created_by`, `activated_at`, `activated_by`.

Chỉ hỗ trợ `SUPPORT_THEN_PATTERN_WEIGHT_V1`. `ranking_policy` được lưu để version
hóa đúng quy tắc đã dùng, không phải để chứa một công thức điểm tùy biến. Không
có cột hệ số và không có nhánh `ComboScore` trong thiết kế đích.

Trạng thái batch: `DRAFT`, `ACTIVE`, `ARCHIVED`. Chỉ combo `PUBLISHED` thuộc
batch `ACTIVE` mới được truy vấn cho storefront. Tại một thời điểm chỉ có một
batch active trong phạm vi demo; kích hoạt batch mới phải archive batch cũ trong
cùng transaction để tránh frontend trộn combo của nhiều lần chạy.

#### `COMBO`

`combo_id`, `batch_id`, `name`, `combo_kind`, `status`, `discount_type`,
`discount_value`, `max_discount_amount`, `stacking_policy`, `currency_code`,
`reference_original_price`, `reference_sale_price`, `support_abs`, `support_ratio`,
`pattern_weight`, `explanation`, `valid_from`, `valid_to`, `created_at`,
`reviewed_at`, `reviewed_by`, `review_note`, `published_at`.

Không lưu `rank_position` trên combo vì thứ hạng Top-K thay đổi theo sản phẩm neo
và tập combo đủ điều kiện của sản phẩm đó. Backend lọc theo anchor trước, sắp xếp
theo policy cố định rồi đánh số thứ hạng trong response.

`combo_kind` tối thiểu:

- `SAME_ORDER_BUNDLE`: pattern chỉ có một itemset, thể hiện thường mua cùng đơn.
- `SEQUENTIAL_CROSS_SELL`: pattern có từ hai itemset, thể hiện hành trình mua
  trước/sau. Khi biến thành gói bán cùng lúc phải ghi rõ đây là suy luận ứng dụng.
- `MIXED_SEQUENCE`: có cả itemset nhiều item và nhiều mốc; chỉ hiển thị khi UI có
  thể giải thích cấu trúc.

Trạng thái: `DRAFT`, `APPROVED`, `REJECTED`, `PUBLISHED`, `ARCHIVED`. Chỉ
`PUBLISHED` và còn hiệu lực mới xuất hiện cho khách hàng.

#### `COMBO_ITEM`

`combo_id`, `product_id`, `itemset_order`, `item_order`, `quantity`,
`unit_price_snapshot`, `is_anchor_eligible`, `is_discount_eligible`.

Giữ vị trí pattern để giải thích nguồn gốc; giá snapshot bảo vệ lịch sử duyệt.
Tạo index `(product_id, is_anchor_eligible, combo_id)` để phục vụ truy vấn Top-K
theo sản phẩm neo; điều kiện trạng thái/batch/thời hạn được lọc ở `COMBO` và
`COMBO_BATCH`.

#### `COMBO_SOURCE`

`combo_id`, `pattern_id`, `is_primary`.

Thông thường một combo có một pattern nguồn chính. Quan hệ nhiều-nhiều vẫn hữu
ích khi nhiều pattern cùng tập sản phẩm được gộp, nhưng phải có đúng một nguồn
chính để support/ranking không mơ hồ.

Không lấy `MAX(support)` và `MAX(weight)` độc lập từ hai pattern khác nhau. Khi
nhiều pattern quy về cùng một combo, chọn một primary pattern bằng chính ranking
tuple, sao chép toàn bộ metrics từ pattern đó và giữ các pattern còn lại làm
provenance. Như vậy không tạo ra một bộ chỉ số tổng hợp chưa từng tồn tại.

#### `CATEGORY_EXCLUSION`

`category_id_low`, `category_id_high`, `reason`, `active`, `created_at`.

Lưu cặp theo thứ tự canonical và unique để quy tắc loại trừ đối xứng, đúng nhu
cầu màn cấu hình category trong tài liệu giao diện.

### 4.5 Tài khoản, phân quyền và địa chỉ lưu sẵn

#### `USER_ACCOUNT`

`user_id`, `username`, `email`, `phone`, `password_hash`, `display_name`, `status`,
`email_verified_at`, `phone_verified_at`, `last_login_at`, `version`, `created_at`,
`updated_at`.

Dùng chung cho customer và admin; không tạo `ADMIN_ACCOUNT` riêng. Ít nhất một
trong username/email/phone là định danh đăng nhập đã chuẩn hóa và unique. Không
lưu mật khẩu thuần. Các cột `reviewed_by`, `created_by`, `activated_by` tham chiếu
`USER_ACCOUNT.user_id`.

#### `ROLE` và `USER_ROLE`

`ROLE`: `role_id`, `code`, `name`, `description`, `created_at`.

`USER_ROLE`: `user_id`, `role_id`, `assigned_at`, `assigned_by`.

Role tối thiểu: `CUSTOMER`, `ADMIN`. Phân quyền API phải kiểm tra tại backend;
frontend ẩn menu không phải biện pháp bảo mật.

#### `USER_SESSION`

`session_id`, `user_id`, `refresh_token_hash`, `device_info`, `ip_hash`,
`expires_at`, `revoked_at`, `created_at`, `last_used_at`.

Chỉ lưu hash refresh token để có thể revoke phiên; access token ngắn hạn không
cần lưu. Không ghi raw token vào log hoặc database.

#### `USER_ADDRESS`

`user_address_id`, `user_id`, `label`, `recipient_name`, `phone`, `address_line`,
`ward_code`, `ward_name`, `district_code`, `district_name`, `province_code`,
`province_name`, `postal_code`, `country_code`, `is_default`, `created_at`,
`updated_at`.

Đây là địa chỉ lưu sẵn để điền checkout. Khi chốt đơn vẫn sao chép sang
`ORDER_ADDRESS`; sửa/xóa `USER_ADDRESS` không được làm đổi hóa đơn cũ. PII không
được xuất sang dataset mining.

### 4.6 Giá, giảm giá combo và đóng băng hóa đơn

Phần này được thiết kế trước để schema không phải thay khóa và làm mất lịch sử
khi bổ sung checkout, nhưng chưa bắt buộc triển khai payment trong demo.

#### Chính sách giá combo

`COMBO.discount_type` nhận một trong:

- `NONE`: chỉ gợi ý, không giảm giá;
- `PERCENTAGE`: giảm `discount_value` phần trăm, có thể giới hạn bởi
  `max_discount_amount`;
- `FIXED_AMOUNT`: giảm một số tiền cố định;
- `FIXED_PRICE`: đặt tổng giá các item đủ điều kiện bằng `discount_value`.

`reference_original_price` và `reference_sale_price` là snapshot khi publish để
review/hiển thị; chúng không phải hóa đơn bất biến. Khi checkout, backend đọc giá
hiện tại, kiểm tra combo còn hiệu lực rồi tính quote mới:

```text
list_subtotal = SUM(PRODUCT.current_price * COMBO_ITEM.quantity)
discount_base = SUM(current_price * quantity WHERE is_discount_eligible = true)

PERCENTAGE:
  discount = MIN(discount_base * discount_value / 100, max_discount_amount nếu có)

FIXED_AMOUNT:
  discount = MIN(discount_value, discount_base)

FIXED_PRICE:
  discount = MAX(0, discount_base - discount_value)

final_subtotal = list_subtotal - discount
```

Mọi cột tiền dùng `DECIMAL`, không dùng `DOUBLE`. Lưu `currency_code` (mặc định
`VND`) và cố định một rounding policy trong backend. `stacking_policy` mặc định
`NON_STACKABLE`: một dòng sản phẩm không nhận hai combo discount. Nếu nhiều combo
cùng hợp lệ, backend phải áp dụng một quy tắc xác định, ví dụ chọn combo giảm
nhiều nhất rồi phá hòa bằng `combo_id`.

Ràng buộc tối thiểu: mọi số tiền không âm; phần trăm nằm trong `(0, 100]`;
`valid_to > valid_from`; các item trong một quote dùng cùng currency; giá sau
giảm không âm. Index đọc storefront gồm `COMBO_BATCH(status)` và
`COMBO(batch_id, status, valid_from, valid_to)`; kết hợp index anchor ở
`COMBO_ITEM` để không quét toàn bộ combo khi mở trang sản phẩm.

Pattern khai phá và khuyến mãi là hai quyết định khác nhau. `SAME_ORDER_BUNDLE`
có thể bật giảm giá cho các item. `SEQUENTIAL_CROSS_SELL` mặc định chỉ gợi ý,
không tự biến thành bundle giảm giá; admin phải quyết định rõ item nào có
`is_discount_eligible = true` nếu muốn chạy chương trình thương mại.

#### `ORDER_APPLIED_COMBO`

`order_applied_combo_id`, `order_id`, `combo_id`, `combo_name_snapshot`,
`discount_type_snapshot`, `discount_value_snapshot`, `currency_code`,
`list_subtotal`, `discount_base_subtotal`, `discount_amount`, `final_subtotal`,
`applied_at`.

Bảng này trả lời được: đơn nào đã dùng combo nào, quy tắc giảm giá lúc đó là gì và
tổng tiền trước/sau giảm là bao nhiêu. Không tính lại hóa đơn cũ từ `COMBO` hoặc
`PRODUCT` hiện tại. Tạo index `(order_id)` và `(combo_id, applied_at)`; thêm index
`ORDER_ITEM(order_id)` và `ORDER_ITEM(order_applied_combo_id)` để đọc hóa đơn mà
không quét bảng dòng hàng.

#### Chốt đơn an toàn

Trong một transaction backend phải:

1. kiểm tra batch `ACTIVE`, combo `PUBLISHED`, thời hạn và đủ item/quantity;
2. kiểm tra sản phẩm còn bán, tồn kho và version giá chưa đổi;
3. tính toàn bộ số tiền ở server;
4. phân bổ `discount_amount` theo tỷ lệ giá trị của các line được giảm; line cuối
   nhận phần dư rounding để tổng phân bổ bằng chính xác discount combo;
5. ghi `ORDER_APPLIED_COMBO`, `ORDER_ITEM` snapshots và totals của `ORDERS`;
6. commit cùng lúc hoặc rollback toàn bộ.

Các bất biến cần test:

```text
SUM(ORDER_ITEM.allocated_discount_amount) = ORDERS.combo_discount_total
SUM(ORDER_ITEM.line_total) = ORDERS.grand_total
ORDER_APPLIED_COMBO.list_subtotal
  - ORDER_APPLIED_COMBO.discount_amount
  = ORDER_APPLIED_COMBO.final_subtotal
```

Thuế, phí vận chuyển và voucher phải là thành phần tiền riêng; không gộp mơ hồ
vào combo discount. Các bảng dưới đây làm schema sẵn sàng cho checkout/payment,
dù implementation có thể thực hiện sau phần mining cốt lõi.

### 4.7 Cart, checkout và payment

#### `CART`

`cart_id`, `customer_id` (nullable), `guest_token_hash` (nullable), `status`,
`currency_code`, `version`, `expires_at`, `created_at`, `updated_at`.

Trạng thái: `ACTIVE`, `CONVERTED`, `ABANDONED`, `EXPIRED`. Cart phải thuộc một
customer đăng nhập hoặc một guest token ngẫu nhiên chỉ lưu dạng hash. `version`
dùng optimistic locking. Không lưu totals của cart như dữ liệu kế toán; totals
trên cart chỉ là estimate và luôn được tính lại khi checkout.

#### `CART_ITEM`

`cart_item_id`, `cart_id`, `product_id`, `cart_applied_combo_id` (nullable),
`quantity`, `estimated_unit_price`, `created_at`, `updated_at`.

Dùng ID riêng để cùng product có thể nằm trong combo và được mua thêm riêng.
Ràng buộc `quantity > 0`; index `(cart_id)` và `(product_id)`.

#### `CART_APPLIED_COMBO`

`cart_applied_combo_id`, `cart_id`, `combo_id`, `status`, `estimated_list_subtotal`,
`estimated_discount_amount`, `estimated_final_subtotal`, `calculated_at`,
`invalid_reason`.

Trạng thái: `SELECTED`, `INVALIDATED`, `REMOVED`. Các giá trị chỉ phục vụ UI; khi
giá, quantity, combo hoặc batch thay đổi thì đánh dấu invalid và quote lại. Cart
item liên kết vào record này để xác định chính xác những dòng thuộc lần áp dụng
combo, thay vì suy đoán chỉ từ `product_id`.
Ràng buộc composite `(cart_applied_combo_id, cart_id)` phải bảo đảm applied combo
thuộc đúng cart của item.

#### `CHECKOUT_SESSION`

`checkout_id`, `cart_id`, `customer_id` (nullable), `idempotency_scope`,
`idempotency_key`, `cart_version_snapshot`, `status`,
`currency_code`, `list_subtotal`, `combo_discount_total`, `other_discount_total`,
`shipping_fee`, `tax_total`, `grand_total`, `quote_expires_at`, `version`,
`failure_code`, `created_at`, `updated_at`, `completed_at`.

Trạng thái: `CREATED`, `QUOTED`, `CONFIRMED`, `PAYMENT_PENDING`, `COMPLETED`,
`FAILED`, `CANCELLED`, `EXPIRED`. Unique `(idempotency_scope, idempotency_key)`
ngăn double-click tạo hai checkout. `idempotency_scope` là định danh ổn định của
customer hoặc guest, không phải dữ liệu người dùng tự chọn. Cart thay đổi sau
khi quote (`CART.version != cart_version_snapshot`) thì checkout cũ phải hết
hiệu lực. Khi `CONFIRMED`, backend tạo order snapshot đúng một lần và gắn
`ORDERS.checkout_id`; retry cùng idempotency key truy vấn và trả lại cùng order.

```text
grand_total = list_subtotal
            - combo_discount_total
            - other_discount_total
            + shipping_fee
            + tax_total
```

#### `CHECKOUT_ADDRESS`

`checkout_address_id`, `checkout_id`, `address_type`, `recipient_name`,
`phone`, `address_line`, `ward_code`, `ward_name`, `district_code`,
`district_name`, `province_code`, `province_name`, `postal_code`, `country_code`.

`address_type`: `SHIPPING` hoặc `BILLING`. Đây là dữ liệu người dùng đang nhập;
khi tạo order phải sao chép sang `ORDER_ADDRESS`, không để hóa đơn cũ phụ thuộc
vào địa chỉ khách hàng có thể sửa sau này.
Tên, số điện thoại và địa chỉ là PII: không đưa vào dataset/export mining, phải
mask trong log, giới hạn quyền đọc và có retention policy riêng cho checkout hết
hạn chưa tạo order.

#### `STOCK_RESERVATION`

`reservation_id`, `checkout_id`, `warehouse_id`, `product_id`, `quantity`, `status`,
`expires_at`, `created_at`, `consumed_at`, `released_at`.

Trạng thái: `ACTIVE`, `CONSUMED`, `RELEASED`, `EXPIRED`. Unique
`(checkout_id, warehouse_id, product_id)`. Reservation có thời hạn giúp tránh giữ tồn kho vô
hạn khi khách bỏ thanh toán; confirm/payment phải lock hoặc cập nhật tồn kho có
điều kiện để không bán vượt số lượng.

#### Bổ sung cho `ORDERS` và `ORDER_ADDRESS`

`ORDERS` thêm `checkout_id` unique, `order_number` unique, `shipping_fee`,
`tax_total`, `other_discount_total` và các trạng thái `PENDING_PAYMENT`, `PAID`,
`PROCESSING`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `RETURNED`, `REFUNDED`.

`ORDER_ADDRESS` gồm `order_address_id`, `order_id`, `address_type` và toàn bộ
trường snapshot giống `CHECKOUT_ADDRESS`. Chỉ `COMPLETED` order mới được đưa vào
dataset mining; `PAID` chưa đồng nghĩa giao dịch đã hoàn tất nghiệp vụ.

`ORDER_STATUS_HISTORY` gồm `order_status_history_id`, `order_id`, `from_status`,
`to_status`, `reason`, `changed_by_type`, `changed_by_id`, `created_at`. Mọi đổi
trạng thái order được ghi append-only để audit; không suy lịch sử chỉ từ trạng
thái cuối cùng trên `ORDERS`.

#### `PAYMENT`

`payment_id`, `order_id`, `method`, `currency_code`, `amount`, `status`,
`paid_amount`, `refunded_amount`, `created_at`, `updated_at`, `paid_at`.

`order_id` unique để một order có tối đa một payment aggregate trong phạm vi
hiện tại. `method` tối thiểu:
`COD`, `BANK_TRANSFER`, `E_WALLET`, `CARD`. Trạng thái: `PENDING`, `AUTHORIZED`,
`PAID`, `PARTIALLY_REFUNDED`, `REFUNDED`, `FAILED`, `CANCELLED`. Payment status
độc lập order status; thanh toán thành công không tự động biến order thành
`COMPLETED`.

#### `PAYMENT_ATTEMPT`

`payment_attempt_id`, `payment_id`, `provider`, `method`, `idempotency_key`,
`provider_payment_id`, `amount`, `currency_code`, `status`, `redirect_url`,
`expires_at`, `failure_code`, `failure_message`, `created_at`, `updated_at`.

Một payment có thể retry nhiều lần nhưng mỗi attempt có idempotency key unique;
`provider_payment_id` unique khi khác null. Trạng thái: `CREATED`, `PENDING`,
`AUTHORIZED`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`.

#### `PAYMENT_TRANSACTION`

`payment_transaction_id`, `payment_id`, `payment_attempt_id` (nullable), `type`,
`provider_transaction_id`, `amount`, `currency_code`, `status`, `processed_at`,
`created_at`.

`type`: `AUTHORIZE`, `CAPTURE`, `REFUND`, `VOID`. Mỗi giao dịch provider có ID
unique để callback/retry không cộng tiền hai lần. Tổng capture thành công tạo
`paid_amount`; tổng refund thành công tạo `refunded_amount`.

#### `PAYMENT_WEBHOOK_EVENT`

`webhook_event_id`, `provider`, `provider_event_id`, `payment_attempt_id`
(nullable), `event_type`, `payload_hash`, `signature_valid`, `status`,
`received_at`, `processed_at`, `error_message`.

Unique `(provider, provider_event_id)` bảo đảm webhook idempotent. Trạng thái:
`RECEIVED`, `PROCESSED`, `IGNORED`, `FAILED`. Chỉ lưu payload đã lọc/được mã hóa
nếu thật sự cần audit; mặc định lưu hash và các trường cần thiết.

Không lưu số thẻ đầy đủ, CVV, mật khẩu ngân hàng hoặc access token thanh toán.
Với CARD/E_WALLET, dùng trang/token do provider quản lý và chỉ lưu opaque provider
reference. Dữ liệu nhạy cảm phải được che trong log, response và error message.

#### Luồng transaction và idempotency

```text
ACTIVE CART
  -> QUOTED CHECKOUT (giá có hạn)
  -> CONFIRMED (tạo ORDER + snapshots + reservation đúng một lần)
  -> PAYMENT_PENDING
  -> PAID/PROCESSING khi provider xác nhận
  -> COMPLETED khi nghiệp vụ bán hàng hoàn tất
```

Mọi callback có thể đến lặp hoặc sai thứ tự. Backend chỉ cho phép state transition
hợp lệ, khóa record cần thiết và xử lý cùng provider event nhiều lần nhưng chỉ tạo
một hiệu ứng. Không dựa vào redirect từ trình duyệt để đánh dấu đã thanh toán;
nguồn xác nhận là API/webhook đã kiểm tra chữ ký của provider.

### 4.8 Nhà cung cấp, nhập hàng và quản lý tồn kho

#### `WAREHOUSE`

`warehouse_id`, `code`, `name`, `address_line`, `phone`, `status`, `created_at`,
`updated_at`.

Demo có thể chỉ dùng một kho nhưng vẫn lưu `warehouse_id` để không phải đổi khóa
khi mở rộng. Trạng thái: `ACTIVE`, `INACTIVE`.

#### `SUPPLIER`

`supplier_id`, `code`, `name`, `tax_code`, `contact_name`, `phone`, `email`,
`address_line`, `status`, `created_at`, `updated_at`.

Thông tin supplier phục vụ nhập hàng, không tham gia mining hoặc ranking combo.

#### `PURCHASE_ORDER`

`purchase_order_id`, `po_number`, `supplier_id`, `warehouse_id`, `status`,
`ordered_at`, `expected_at`, `currency_code`, `subtotal`, `tax_total`, `grand_total`,
`note`, `created_by`, `approved_by`, `created_at`, `approved_at`, `updated_at`.

Trạng thái: `DRAFT`, `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`.
Purchase order là chứng từ đặt mua, chưa làm tăng tồn kho.

#### `PURCHASE_ORDER_ITEM`

`purchase_order_item_id`, `purchase_order_id`, `product_id`, `quantity_ordered`,
`quantity_received`, `unit_cost`, `tax_rate`, `line_total`.

`quantity_received` là counter được cập nhật từ các receipt đã `POSTED`; không cho
vượt `quantity_ordered` nếu chưa có nghiệp vụ over-receipt được phê duyệt.

#### `GOODS_RECEIPT`

`goods_receipt_id`, `receipt_number`, `purchase_order_id`, `warehouse_id`,
`status`, `supplier_invoice_number`, `received_at`, `note`, `created_by`,
`posted_by`, `created_at`, `posted_at`.

Trạng thái: `DRAFT`, `POSTED`, `CANCELLED`. Chỉ khi `POSTED` mới tăng tồn. Receipt
đã posted không xóa/sửa trực tiếp; sai sót phải tạo movement đảo hoặc chứng từ
điều chỉnh có audit.

#### `GOODS_RECEIPT_ITEM`

`goods_receipt_item_id`, `goods_receipt_id`, `purchase_order_item_id`,
`product_id`, `quantity_received`, `unit_cost`, `line_total`.

Cho phép một PO được nhận nhiều lần. Product phải khớp PO item; unit cost thực nhận
được snapshot để phục vụ giá vốn/audit, không lấy lại từ PO sau này.

#### `INVENTORY_BALANCE`

`warehouse_id`, `product_id`, `on_hand_quantity`, `reserved_quantity`, `version`,
`updated_at`.

Khóa chính `(warehouse_id, product_id)`. Không lưu `available_quantity` dư thừa:

```text
available_quantity = on_hand_quantity - reserved_quantity
```

`PRODUCT` không còn `stock_quantity`. API catalog tổng hợp available từ các kho
đang active. Mọi thay đổi balance phải đi qua service transaction và có movement;
không cung cấp CRUD sửa số lượng trực tiếp.

#### `STOCK_MOVEMENT`

`stock_movement_id`, `warehouse_id`, `product_id`, `movement_type`,
`quantity_delta`, `unit_cost`, `balance_after`, `reference_type`, `reference_id`,
`occurred_at`, `created_by`, `note`.

Sổ cái append-only. `movement_type` tối thiểu: `PURCHASE_RECEIPT`, `SALE_ISSUE`,
`CUSTOMER_RETURN`, `SUPPLIER_RETURN`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`.
`reference_type/reference_id` liên kết logic tới receipt, order hoặc chứng từ điều
chỉnh. Không update/delete movement đã ghi; sửa bằng movement đảo có tham chiếu.

Reservation chỉ tăng/giảm `reserved_quantity`, chưa phải movement vật lý. Khi
order được xuất kho, transaction giảm cả `reserved_quantity` và `on_hand_quantity`,
đồng thời ghi `SALE_ISSUE`. Receipt `POSTED` tăng `on_hand_quantity` và ghi
`PURCHASE_RECEIPT` trong cùng transaction.

#### `INVENTORY_SERIAL`

`inventory_serial_id`, `product_id`, `serial_number`, `warehouse_id`,
`goods_receipt_item_id`, `order_item_id` (nullable), `status`, `created_at`,
`updated_at`.

Dùng cho laptop/sản phẩm có `PRODUCT.track_serial = true`. `serial_number` unique;
trạng thái: `IN_STOCK`, `RESERVED`, `SOLD`, `RETURNED`, `DAMAGED`. Phụ kiện không
quản lý serial thì chỉ dùng số lượng trong `INVENTORY_BALANCE`.

#### Luồng nhập và xuất kho

```text
PURCHASE_ORDER APPROVED
  -> GOODS_RECEIPT DRAFT
  -> GOODS_RECEIPT POSTED
  -> STOCK_MOVEMENT PURCHASE_RECEIPT
  -> INVENTORY_BALANCE.on_hand_quantity tăng

CHECKOUT CONFIRMED
  -> STOCK_RESERVATION ACTIVE
  -> ORDER xử lý/xuất kho
  -> STOCK_MOVEMENT SALE_ISSUE
  -> reservation giảm, on_hand giảm
```

Khóa/cập nhật có điều kiện phải bảo đảm `reserved_quantity >= 0` và
`on_hand_quantity >= reserved_quantity`; hai receipt hoặc hai checkout chạy đồng
thời không được làm mất cập nhật.

## 5. Quy tắc pattern thành combo

Một quy tắc tối thiểu, kiểm thử được cho cửa hàng laptop:

1. Chỉ nhận pattern có 2–4 item khác nhau.
2. Với dataset demo, mọi item phải ánh xạ được qua `RUN_ITEM_STATISTIC` và product
   đang bán. Benchmark chỉ lưu pattern kỹ thuật, không sinh combo thương mại.
3. Loại pattern chứa cùng một product lặp lại nếu mục tiêu là bundle sản phẩm
   khác nhau; vẫn giữ pattern gốc trong kho kết quả nghiên cứu.
4. Với combo hướng laptop, yêu cầu đúng một sản phẩm thuộc danh mục Laptop và ít
   nhất một phụ kiện. Đây là business filter, không phải pruning của thuật toán.
5. Không gộp `<(a)(b)>` và `<(ab)>`; nếu tập sản phẩm giống nhau vẫn là hai bằng
   chứng temporal khác nhau.
6. Không xóa pattern khi combo bị từ chối. Pattern là kết quả khoa học; combo là
   quyết định ứng dụng có vòng đời riêng.

## 6. Xếp hạng theo support rồi trọng số mẫu

### 6.1 Chính sách đề xuất

Xếp hạng từ cao xuống thấp theo tuple xác định:

```text
(support_abs DESC,
 pattern_weight DESC,
 canonical_pattern ASC)
```

Đây là thứ tự ưu tiên từ điển, không phải công thức cộng điểm. Nó bám đúng
`DeXuatChinhSua.docx`: support trước, khi bằng nhau mới xét trọng số mẫu. Giá và
độ dài không tham gia relevance ranking. `canonical_pattern` chỉ là khóa phá hòa
kỹ thuật để cùng input luôn sinh cùng thứ tự; không được diễn giải là tiêu chí
phù hợp khách hàng.

### 6.2 Vì sao support đứng trước

`support_abs` là số sequence khách hàng chứa pattern, mỗi sequence chỉ được tính
một lần. Đặt support trước ưu tiên bằng chứng lặp lại trên nhiều khách hàng và dễ
giải thích hơn một điểm tổng hợp không có hợp đồng nghiên cứu.

Nên hiển thị cả `support_ratio = support_abs / sequence_count`, vì support tuyệt
đối của hai run có kích thước dữ liệu khác nhau không so sánh trực tiếp được.

### 6.3 Điều kiện để tie-break bằng pattern weight có giá trị

Trong implementation mining hiện hành, điều kiện chấp nhận là:

```text
support(P) * MeanW >= effectiveMinSup
```

`MeanW` là hằng số toàn cục của một run. Vì vậy nếu gọi
`support(P) * MeanW` là “weighted score”, hai pattern cùng support trong cùng run
sẽ luôn bằng điểm; nó không phá hòa được.

Hai tài liệu TV2 đã chọn `W(α)` theo Eq. (2)/(3): trung bình item weight trong
từng itemset, sau đó trung bình các itemset. Đại lượng này có thể khác nhau khi
hai pattern cùng support nên có khả năng phá hòa. Tuy nhiên ví dụ tính tay của
bài báo mâu thuẫn với công thức và repository vẫn ghi nhận vấn đề nghiên cứu này
chưa được giải quyết. Do đó:

- phải đặt tên `pattern_weight`, không dùng `weight` mơ hồ;
- phải lưu `pattern_weight_policy = EQ2_EQ3_ARITHMETIC_MEAN_V1`;
- phải tái lập đúng 20 pattern, 12 candidate combo và W(α) của fixture TV2 trước
  khi dùng policy;
- báo cáo phải phân loại đây là quyết định implementation/recommendation của
  nhóm cần GVHD phê duyệt, không gọi là kết quả duy nhất được nguồn xác nhận.

### 6.4 Vì sao không tạo điểm tổng hợp

- Tài liệu nguồn không cung cấp một phương pháp tổng hợp đã được kiểm chứng cho
  bài toán chọn combo của hệ thống này.
- Một điểm tổng hợp sẽ che mất lý do combo đứng trước: do xuất hiện phổ biến hay
  do trọng số mẫu.
- Giá và độ dài là tiêu chí kinh doanh/UI, không phải bằng chứng để gọi một combo
  phù hợp hơn trong policy đã chọn.

Thiết kế đích loại hoàn toàn `ComboScore`; frontend cũng không nhận hoặc hiển thị
trường điểm tổng hợp hay “% phù hợp”.

### 6.5 Giới hạn của tuyên bố

Combo đứng đầu theo quy tắc trên là combo có thứ hạng cao nhất theo chính sách và
dữ liệu đã quan sát. Không có đủ bằng chứng để gọi nó là “phù hợp nhất với từng
khách hàng”. Hệ thống hiện là Top-K liên quan theo sản phẩm neo và pattern toàn
cục, chưa phải recommender cá nhân hóa.

Đánh giá tối thiểu nên báo cáo:

- coverage: tỷ lệ sản phẩm neo có ít nhất một combo;
- số combo hợp lệ/bị loại qua từng business filter;
- stability: Top-K thay đổi thế nào khi đổi minsup hoặc cửa sổ dữ liệu;
- case study thủ công cho laptop + phụ kiện;
- nếu có dữ liệu đủ lớn: temporal holdout, dùng quá khứ sinh combo và kiểm tra
  khách hàng tương lai có mua phần còn lại của combo hay không.

### 6.6 Lọc theo sản phẩm đang xem phải có hướng

Quy tắc trong TV2 “combo chứa product ở bất kỳ vị trí nào” phù hợp cho đồng mua
nhưng chưa đúng hoàn toàn cho cross-sell tuần tự. Nếu khách đang xem A:

- combo một itemset: A có thể ở bất kỳ vị trí nào;
- combo nối tiếp/hỗn hợp: A phải thuộc context đầu/prefix, và phần gợi ý là các
  sản phẩm cùng itemset còn lại hoặc ở itemset sau;
- không đảo `<(A)(B)>` thành gợi ý A khi khách đang xem B.

Nếu chưa có đăng nhập/lịch sử mua, đây vẫn là gợi ý theo sản phẩm neo chứ không
phải gợi ý cá nhân hóa theo toàn bộ lịch sử khách hàng.

## 7. API tối thiểu

### Khách hàng

- `GET /api/v1/catalog/categories`
- `GET /api/v1/catalog/products?category=&brand=&q=`
- `GET /api/v1/catalog/products/{id}`
- `GET /api/v1/recommendations/combos?productId=&limit=`
- `GET /api/v1/recommendations/combos/{id}`
- `POST /api/v1/recommendations/combos/{id}/quote`
- `GET /api/v1/cart`
- `POST /api/v1/cart/items`, `PATCH /api/v1/cart/items/{id}`,
  `DELETE /api/v1/cart/items/{id}`
- `POST /api/v1/cart/combos`, `DELETE /api/v1/cart/combos/{id}`
- `POST /api/v1/checkout-sessions`
- `GET /api/v1/checkout-sessions/{id}`
- `POST /api/v1/checkout-sessions/{id}/confirm`
- `GET /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/payment-attempts`
- `GET /api/v1/payments/{id}`

Các POST tạo checkout/payment nhận header `Idempotency-Key`. Webhook provider dùng
endpoint riêng `POST /api/v1/payment-webhooks/{provider}`, xác minh chữ ký trước
khi ghi state và không dùng authentication/session của khách hàng.

Response combo phải có items, giá, support tuyệt đối/tỷ lệ, loại temporal và một
lời giải thích ngắn. UI nên ghi “xuất hiện ở X/Y khách trong tập dữ liệu” hoặc
“thường mua cùng/sau”, không chuyển rank thành phần trăm phù hợp giả tạo.

### Quản trị

- CRUD tối thiểu cho category, brand, product và import order.
- Quản lý `USER_ACCOUNT`/role theo quyền Admin; user thường chỉ tự sửa profile,
  session và địa chỉ của mình.
- CRUD supplier/warehouse; tạo-duyệt purchase order; tạo-post goods receipt; đọc
  inventory balance/movement. Không có endpoint sửa trực tiếp số lượng balance.
- `POST /api/v1/admin/mining-runs`
- `GET /api/v1/admin/mining-runs` và `GET .../{id}`
- `GET /api/v1/admin/mining-runs/{id}/patterns`
- `POST /api/v1/admin/mining-runs/{id}/combo-batches`
- `GET /api/v1/admin/combo-batches/{id}`
- `PATCH /api/v1/admin/combo-batches/{id}/activate`
- `GET /api/v1/admin/combos?batchId=&status=&kind=&sort=`
- `PATCH /api/v1/admin/combos/{id}/review`
- `PATCH /api/v1/admin/combos/{id}/publish`
- `GET /api/v1/admin/experiments/compare?baselineRunId=&bitPruneRunId=`

Backend phải tính ranking; frontend chỉ hiển thị kết quả, không tự cài lại quy
tắc. Với thiết kế đã chốt, frontend không gửi hệ số hay policy tùy ý; backend
luôn dùng `SUPPORT_THEN_PATTERN_WEIGHT_V1` cho relevance.

## 8. Giao diện vừa đủ cho khóa luận

### 8.1 Khách hàng

1. Trang chủ/catalog: danh mục laptop/phụ kiện, tìm kiếm, card có ảnh, tên, giá
   và trạng thái. Đây là entry point để đi tới màn 1 trong tài liệu UI.
2. Màn 1 — chi tiết sản phẩm và combo gợi ý.
3. Màn 2 — chi tiết combo, giữ nhóm “Cùng một đơn hàng” và “Sau đó”.
4. Cart — nhóm rõ item mua lẻ và item thuộc combo, hiển thị estimate cùng cảnh
   báo khi giá/combo thay đổi.
5. Checkout — địa chỉ, quote có thời hạn, tổng tiền tách riêng discount, thuế và
   phí; trang kết quả poll trạng thái order/payment thay vì tin redirect.

Checkout không bắt buộc để chứng minh thuật toán gợi ý, nhưng là lát cắt ứng dụng
hoàn chỉnh có thể triển khai sau khi pipeline mining/combo ổn định.

### 8.2 Quản trị

1. Màn 3 — đăng nhập Admin. Quên mật khẩu qua email là ngoài core demo.
2. Màn 4 — chuẩn bị dữ liệu: nguồn/kỳ, CSV validation, conversion report,
   snapshot/checksum và tải artifact.
3. Màn 5 — cấu hình mining: minsup mode/value, K của batch, MeanW và ngưỡng hiệu
   dụng. Quy tắc support trước/trọng số sau được hiển thị dạng chỉ đọc ở bước sinh
   combo, không phải input mining và không cho nhập hệ số.
4. Màn 6 — chạy khai phá: trạng thái `QUEUED/RUNNING/SUCCEEDED/FAILED`, kết quả
   và measurement đúng scope. “Tiến độ %” chỉ hiển thị nếu backend có total work
   đáng tin; nếu không dùng trạng thái/phase.
5. Màn 7 — pattern explorer: canonical pattern, itemset, support, W(α), độ dài.
6. Màn 8 — sinh/review combo: policy version chỉ đọc, lọc, sort, primary source,
   duyệt, từ chối và publish.
7. Màn 9/9b — lịch sử và so sánh thực nghiệm. Chỉ so hiệu năng sau khi xác nhận
   cùng snapshot/config và complete pattern map bằng nhau.
8. Các trang hỗ trợ catalog Admin: category, brand và product. Chúng phục vụ dữ
   liệu hiển thị nhưng không làm loãng menu nghiên cứu chính.

Các feature Electro ngoài các lát cắt này nên ẩn/xóa theo feature slice sau khi
kiểm tra import và license, không giữ menu giả gọi API không tồn tại.

### 8.3 Ma trận tái sử dụng frontend hiện tại

| Quyết định | Thành phần hiện tại | Cách dùng |
|---|---|---|
| Giữ | React Router, Mantine theme/providers, React Query, notifications, modal | Hạ tầng UI hiện có phù hợp |
| Giữ và sửa | `Client`, `ClientHeader`, `ClientFooter`, home/category/search | Thu gọn thành storefront laptop/phụ kiện |
| Giữ và sửa mạnh | `ClientProduct`, carousel, product card, specification/description | Thay model/API; thêm khối combo, bỏ phụ thuộc review/variant và nối cart mới khi API sẵn sàng |
| Giữ và sửa | `Admin`, `DefaultHeader`, `DefaultNavbar` | Menu nghiên cứu: Dữ liệu, Cấu hình/Run, Pattern, Combo, Thực nghiệm; catalog là nhóm hỗ trợ |
| Tái sử dụng component | `ManageTable`, `FilterPanel`, pagination, error/loading | Dùng cho pattern, combo và run history sau khi tổng quát ID chuỗi |
| Viết mới | Dataset preparation, mining configuration/run, pattern explorer, combo review/detail, experiment comparison | Đây là giá trị chính của khóa luận, frontend hiện không có |
| Viết lại theo contract mới | cart, checkout, payment result/status | Không tái sử dụng model/API Electro; backend là nguồn giá và payment state duy nhất |
| Viết lại theo contract mới | user/role/session/address, supplier, purchase order, goods receipt, inventory balance/movement | Chỉ tái sử dụng component bảng/form; không giữ model nghiệp vụ Electro |
| Ẩn trước, xóa sau | employee, waybill, reward, chat, voucher, notification và các route liên quan | Ngoài phạm vi khóa luận hoặc chưa có backend tương ứng |
| Không tái sử dụng nguyên trạng | model `Product`, `Order`, `Variant`, `ResourceURL`, `FetchUtils` | Contract ID/field hiện khác backend; cần định nghĩa DTO TypeScript theo OpenAPI/API thật |

### 8.4 Kiểm tra database với dữ liệu frontend cần hiển thị

Kết luận: mô hình database đề xuất **đủ cho frontend khóa luận sau khi viết lại
DTO/API**, nhưng **không tương thích trực tiếp với frontend Electro hiện tại**.
Không mang nguyên trạng variant, supplier, review, promotion, cart và inventory
của Electro sang schema mới. Cart/checkout/payment dùng contract gọn ở mục 4.7.

| Lát cắt frontend | Dữ liệu cần trả | Nguồn trong schema đề xuất | Đánh giá |
|---|---|---|---|
| Catalog/card sản phẩm | ID chuỗi, slug, tên, thumbnail, giá hiện tại, trạng thái bán, category, brand | `PRODUCT`, `CATEGORY`, `BRAND` | Đủ |
| Chi tiết sản phẩm | short description, description, danh sách ảnh, thông số | `PRODUCT`, `PRODUCT_IMAGE` | Đủ; `specifications_json` chỉ dùng render |
| Tài khoản/địa chỉ | profile, role, session và địa chỉ lưu sẵn | `USER_ACCOUNT`, `ROLE`, `USER_ROLE`, `USER_SESSION`, `USER_ADDRESS`, `CUSTOMER` | Đủ; PII tách khỏi mining export |
| Nhập hàng/tồn kho | supplier, PO, receipt, available stock, movement và serial | `SUPPLIER`, `WAREHOUSE`, `PURCHASE_ORDER*`, `GOODS_RECEIPT*`, `INVENTORY_BALANCE`, `STOCK_MOVEMENT`, `INVENTORY_SERIAL` | Đủ cho một hoặc nhiều kho |
| Khối Top-K trên sản phẩm | rank theo anchor, tên/loại combo, giá, support X/Y, pattern weight, giải thích, item preview | `COMBO`, `COMBO_ITEM`, `COMBO_SOURCE`, `PATTERN`, `PRODUCT` | Đủ sau join; rank tính trong query, không lưu DB |
| Chi tiết combo | sản phẩm theo itemset, cùng đơn/mua sau, giá snapshot, trạng thái hiệu lực | `COMBO`, `COMBO_ITEM`, `PRODUCT` | Đủ |
| Chuẩn bị dữ liệu | tên nguồn, kỳ dữ liệu, validation, số dòng loại, checksum, artifact | `DATASET_SNAPSHOT` | Đủ |
| Cấu hình/chạy mining | algorithm, minsup configured/resolved, trạng thái, lỗi, thời gian từng scope | `MINING_RUN` | Đủ |
| Pattern explorer | canonical pattern, itemset, support, weight, item mapping | `PATTERN`, `PATTERN_ITEM`, `RUN_ITEM_STATISTIC` | Đủ |
| Review/publish combo | nguồn chính, support/weight kế thừa, note, người duyệt, hiệu lực | `COMBO_BATCH`, `COMBO`, `COMBO_SOURCE`, `USER_ACCOUNT` | Đủ |
| Quote/tính tiền combo | giá hiện tại, discount rule, snapshot và phân bổ giảm giá | `PRODUCT`, `COMBO`, `COMBO_ITEM`, `ORDER_APPLIED_COMBO`, `ORDER_ITEM` | Đã có contract mở rộng; checkout có thể làm sau |
| Cart | item mua lẻ/thuộc combo, estimate, invalid reason và optimistic version | `CART`, `CART_ITEM`, `CART_APPLIED_COMBO` | Đủ |
| Checkout | quote expiry, địa chỉ, reservation, totals và order được tạo | `CHECKOUT_SESSION`, `CHECKOUT_ADDRESS`, `STOCK_RESERVATION`, `ORDERS` | Đủ |
| Payment result | aggregate status, attempt, transaction và webhook audit | `PAYMENT`, `PAYMENT_ATTEMPT`, `PAYMENT_TRANSACTION`, `PAYMENT_WEBHOOK_EVENT` | Đủ; không lộ dữ liệu nhạy cảm |
| So sánh thực nghiệm | snapshot/config, complete map status, counters, runtime và JVM heap sample | `DATASET_SNAPSHOT`, `MINING_RUN` | Đủ nếu backend tạo comparison response |

Target DTO tối thiểu nên độc lập với model Electro:

```text
ProductSummary:
  productId, slug, name, thumbnailUrl, currentPrice, saleable,
  category{id,name,slug}, brand{id,name,slug}

ProductDetail:
  ProductSummary + shortDescription + description + images[] + specifications

ComboRecommendation:
  comboId, rank, name, kind, originalPrice, salePrice,
  discountType, discountValue, currencyCode, priceCalculatedAt,
  supportAbs, supportRatio, sequenceCount, patternWeight,
  explanation, items[]

ComboItem:
  product{productId,slug,name,thumbnailUrl,currentPrice},
  itemsetOrder, itemOrder, anchorEligible, discountEligible

CartView:
  cartId, version, currencyCode, items[], appliedCombos[], estimate,
  warnings[]

CheckoutView:
  checkoutId, status, quoteExpiresAt, address, items[],
  listSubtotal, comboDiscountTotal, shippingFee, taxTotal, grandTotal,
  orderId

PaymentView:
  paymentId, orderId, method, status, amount, currencyCode,
  paidAmount, refundedAmount, latestAttemptStatus
```

Frontend hiện dùng `productId: number`, còn backend/schema dùng ID chuỗi. Thiết
kế đích chọn ID chuỗi nhất quán ở DB, Java DTO, URL và TypeScript. Route
`/product/:slug` vẫn giữ được vì `PRODUCT.slug` đã có unique; API nội bộ có thể
dùng ID chuỗi, còn API storefront tra theo slug.

`PRODUCT.current_price` cùng tổng available từ `INVENTORY_BALANCE` đủ cho demo
một SKU là một item.
Nếu sau này bắt buộc chọn RAM/SSD như variant trên cùng trang, đó là mở rộng mô
hình catalog riêng và phải quyết định mỗi variant hay mỗi product là item mining;
không nên giữ variant Electro nửa vời trong giai đoạn hiện tại.

### 8.5 Khoảng cách từ code hiện tại tới contract đích

Schema V2 và frontend đích phù hợp về mặt dữ liệu, nhưng code hiện tại chưa nối
được end-to-end. Migration schema local đã được xác minh read-only ngày
2026-11-03; các thay đổi application còn lại phải được triển khai theo task riêng:

1. Không sửa lịch sử `V1__initial_schema.sql` hoặc V2 đã áp dụng. V2 đã loại
   `RUN.coef_*`, `COMBO.combo_score`, index theo score và chuyển `top_k` sang
   `COMBO_BATCH`; mọi correction schema tiếp theo phải dùng V3+.
2. Hoàn tất loại bỏ các field hệ số khỏi DTO/application code; xóa `comboScore`
   khỏi API/repository còn liên quan. Repository xếp theo support giảm dần, sau đó
   pattern weight giảm dần và canonical pattern tăng dần.
3. Sửa `RunCreateRequest`: bỏ `minWeight`; nhận rõ `minsupMode` và
   `minsupConfigured`, còn MeanW/ngưỡng hiệu dụng do backend resolve và trả về.
4. Mở rộng `ProductEntity`/DTO theo catalog tối thiểu ở mục 4.1; hiện API chỉ trả
   năm trường nên chưa render được trang sản phẩm hiện có.
5. Tạo TypeScript DTO mới dùng ID chuỗi; không cố ép `models/Product.ts` và
   `types/ClientUI.ts` hiện tại vì chúng phụ thuộc variant/review/promotion.
6. Thêm endpoint recommendation và các endpoint Admin ở mục 7; hiện backend mới
   có health/category/product read và chưa có application flow mining/combo.
7. Viết mới aggregate/service cho cart, checkout, order pricing, stock reservation
   và payment; không tái sử dụng DTO Electro hoặc coi redirect là payment success.
8. Thay menu/routes Electro bằng các vertical slice ở mục 8 và chỉ gọi endpoint
   đã được backend cung cấp.

Do đó câu trả lời chính xác là: **schema V2 đã có trong Flyway và được xác minh
đã áp dụng thành công trên database local, nhưng repository chưa tích hợp
end-to-end; entity/DTO/API và TypeScript contract vẫn cần được đồng bộ trong các
task riêng.**

Không sao chép toàn bộ màn Electro rồi giả lập API. Việc chuyển đổi nên theo
vertical slice: route + model + query + component + API + test cho từng màn.

## 9. Thứ tự triển khai an toàn

1. Phê duyệt ngữ nghĩa transaction và ranking policy.
2. **Đã thực hiện:** đưa Flyway V2 theo hướng rename/copy có kiểm soát vào
   classpath và chạy trên database local. Không sửa V1/V2 đã áp dụng; dùng V3+
   cho correction schema và kiểm tra `flyway_schema_history` theo từng môi trường.
3. Hoàn tất đồng bộ entity và DTO với schema; dùng enum cho status/mode/algorithm.
4. Cài `USER_ACCOUNT`/role/session và authorization test trước các API Admin.
5. Cài catalog, supplier và warehouse.
6. Cài PO → goods receipt → inventory balance/movement và concurrency test.
7. Cài order import và test phép chiếu DB sang SPMF.
8. Cài mining-run application boundary, snapshot và lưu complete pattern map.
9. Cài pattern-to-combo/ranking cùng regression test.
10. Cài API combo review/recommendation.
11. Cài pricing quote và snapshot order; xác minh toàn bộ bất biến tiền trước khi
   nối payment provider.
12. Thu gọn frontend và xây các màn hình theo lát cắt 1–9/9b đã chốt ở mục 8;
   ưu tiên màn 1, 2, 4, 5, 6, 7, 8 và 9/9b, còn catalog là phần hỗ trợ.
13. Cài cart → checkout → stock reservation → order theo vertical slice.
14. Cài COD trước, sau đó mới tích hợp một payment provider sandbox cùng webhook
    idempotency; không tích hợp nhiều gateway trong khóa luận.
15. Chạy test tích hợp trên fixture laptop nhỏ, sau đó mới nhập dataset demo lớn.

Không trộn thay đổi mining-core với migration/UI trong cùng một task review.

## 10. Tiêu chí nghiệm thu cho giai đoạn tích hợp

- Một fixture có ít nhất ba khách hàng, nhiều đơn theo thời gian và cả I/S pattern.
- Xuất snapshot hai lần từ cùng dữ liệu cho cùng checksum và SPMF.
- Baseline và BitPrune trả cùng complete `pattern -> support` map trên snapshot.
- Mỗi pattern lưu lại có dictionary item đúng run và đúng biên itemset.
- Ranking xác định: cùng input/policy luôn cho cùng thứ tự.
- Chỉ combo `PUBLISHED` còn hiệu lực được trả cho khách hàng.
- Truy vấn theo product neo chỉ trả combo mà product đó đủ điều kiện làm neo:
  có thể ở bất kỳ vị trí nào đối với `SAME_ORDER_BUNDLE`, nhưng phải thuộc
  context đầu/prefix đối với `SEQUENTIAL_CROSS_SELL` và `MIXED_SEQUENCE`.
- UI không gọi endpoint chưa tồn tại và không tính score riêng.
- API Admin từ chối user không có role; token/session bị revoke không còn truy cập.
- PO không làm tăng tồn; chỉ receipt `POSTED` tạo movement và tăng balance đúng
  một lần. Retry post cùng idempotency key không nhập kho hai lần.
- Tổng movement theo warehouse/product đối chiếu được balance; movement đã ghi
  không bị update/delete và serial không xuất bán hai lần.
- Quote combo luôn được backend tính lại từ giá hiện tại; frontend không được gửi
  `originalPrice`, `discountAmount` hoặc `grandTotal` làm giá trị tin cậy.
- Test `PERCENTAGE`, `FIXED_AMOUNT`, `FIXED_PRICE`, giới hạn giảm, rounding,
  combo hết hạn, giá đổi đồng thời và hai combo dùng chung một product.
- Sau khi chốt đơn, tổng discount và line total thỏa các bất biến ở mục 4.6; thay
  giá product/combo sau đó không làm thay đổi hóa đơn cũ.
- Retry cùng checkout `Idempotency-Key` chỉ tạo đúng một order; cart/version hoặc
  quote đã cũ bị từ chối và yêu cầu quote lại.
- Hai checkout cạnh tranh không thể reserve/consume vượt available inventory.
- Webhook gửi lặp hoặc sai thứ tự chỉ tạo một hiệu ứng; tổng capture/refund thành
  công khớp `PAYMENT.paid_amount`/`refunded_amount` và không vượt amount hợp lệ.
- Không có PAN/CVV, token ngân hàng, secret hoặc payload nhạy cảm trong DB/log/API.
- Chỉ order `COMPLETED` đi vào phép chiếu mining; cart, checkout, payment pending
  và order cancelled không làm thay đổi dataset.
- Các số đo thời gian và JVM heap có nhãn đúng scope; không gọi JVM heap là RAM
  phần cứng chính xác.

## 11. Quyết định còn mở

1. GVHD phê duyệt việc dùng `EQ2_EQ3_ARITHMETIC_MEAN_V1` làm tie-break và cách
   trình bày bất nhất giữa công thức với ví dụ tính tay trong nguồn. Nếu chưa
   duyệt thì chưa công bố Top-K theo trọng số mẫu; không tự thay bằng công thức
   khác.
2. Mẫu nhiều itemset được bán như combo cùng lúc hay chỉ dùng làm cross-sell theo
   giai đoạn. UI và đánh giá phải phản ánh lựa chọn này.
3. Dataset giao dịch laptop thực tế/giả lập, nguồn, giấy phép và quy trình ẩn danh.
4. Đối chiếu trực quan file Figma sau khi có kết nối/quyền đọc, rồi mới khóa
   layout, component states và responsive behavior.
5. Chốt rounding scale/mode cho VND và cách chọn khi nhiều combo cùng áp dụng được.
6. Chọn phương thức demo (`COD` và tối đa một provider sandbox), chính sách hết
   hạn reservation/checkout và quy trình refund; không tự đoán contract gateway.
7. Chốt demo một hay nhiều kho, có cho over-receipt hay không và phương pháp giá
   vốn nếu báo cáo lợi nhuận (`FIFO`/bình quân); schema hiện chỉ snapshot unit cost,
   chưa tuyên bố là hệ thống kế toán giá vốn hoàn chỉnh.

Không nên tạo migration bổ sung làm thay đổi các semantics còn mở hoặc tuyên bố
chất lượng recommendation trước khi các quyết định này được ghi nhận. V2 hiện
hữu chỉ xác lập schema; nó không tự giải quyết các quyết định application/research
trong danh sách trên.

## 12. Phán quyết đối với thiết kế sơ bộ

| Phán quyết | Nội dung | Lý do |
|---|---|---|
| Giữ | Một customer là một sequence; mỗi đơn `COMPLETED` là một itemset; sắp theo thời gian và `order_id`; loại trùng item trong một đơn | Bảo toàn đúng ngữ nghĩa tuần tự và có thể kiểm thử |
| Giữ | Phân biệt `<(a b)>` với `<(a)(b)>`; lưu `PATTERN_ITEM.itemset_order` | Đây là contract nghiên cứu bắt buộc |
| Giữ | Luồng dữ liệu → mining → pattern → combo review → customer; hai actor Customer/Admin; các màn 1–9/9b | Phù hợp mục tiêu khóa luận và đủ để demo end-to-end |
| Giữ và nâng cấp | 12 thực thể cốt lõi trong TV2 | Khung đúng, nhưng cần thêm snapshot, batch ranking, catalog display và audit lifecycle |
| Thay đổi | Tách `MINING_RUN` khỏi `COMBO_BATCH` | Đổi Top-K/policy không được làm thay đổi hay buộc chạy lại kết quả khoa học |
| Thay đổi | `APPROVED` và `PUBLISHED` là hai trạng thái riêng | Duyệt nội bộ chưa đồng nghĩa đang hiển thị cho khách hàng |
| Thay đổi | Lọc combo theo điểm neo có hướng, không chỉ kiểm tra “chứa sản phẩm” | Tránh đảo ngược bằng chứng `<(A)(B)>` thành gợi ý A khi đang xem B |
| Thay đổi | Chọn đúng một primary source pattern và sao chép toàn bộ metrics từ nó | Tránh ghép `MAX(support)` và `MAX(weight)` từ hai bằng chứng khác nhau |
| Thay đổi | Hiển thị `X/Y khách hàng`, loại temporal và lời giải thích | Dễ kiểm chứng hơn một “% phù hợp” suy ra từ rank |
| Loại bỏ | `ComboScore`, các cột hệ số và index xếp theo score | Không thuộc quy tắc support trước/trọng số sau đã chọn và chưa có bằng chứng kiểm chứng |
| Không dùng | Biến rank thành “85% phù hợp” | Rank không phải xác suất hoặc độ tin cậy đã hiệu chuẩn |
| Thiết kế sẵn, triển khai sau core | Cart, checkout, payment và refund | Hoàn thiện ứng dụng nhưng không phải bằng chứng cho đóng góp khai phá dữ liệu |
| Ngoài core demo | Chat, thưởng, kho-vận đa chi nhánh, nhân sự, voucher và quên mật khẩu qua email | Không cần cho pipeline combo hoặc checkout tối thiểu |

Các trường `BRAND`, `PRODUCT_IMAGE`, `sku`, mô tả và thông số laptop là
`REASONABLE-INFERENCE` từ nhu cầu hiển thị của frontend hiện tại, không phải
đầu ra của thuật toán hay yêu cầu được bài báo xác nhận. Chúng cần cho một demo
cửa hàng laptop khả dụng nhưng phải được giữ ngoài contract mining.

## 13. Fixture và bằng chứng cần khóa trước khi triển khai

Fixture TV2 hiện mô tả 15 khách hàng, 39 đơn (36 hợp lệ), 16 sản phẩm và 55 lần
xuất hiện; với cấu hình minh họa, tài liệu kỳ vọng 20 pattern và 12 candidate
combo. Các con số này nên được chuyển thành integration fixture có version,
checksum và assertions thay vì chỉ tồn tại trong tài liệu Word.

Fixture phải kiểm chứng đồng thời:

1. CSV validation và phép chiếu sang SPMF, gồm cả số dòng bị loại và lý do;
2. complete `pattern -> exact support` map giữa baseline và BitPrune;
3. chính sách tính `pattern_weight` được chọn;
4. tập candidate sau từng business filter;
5. thứ tự lexicographic cuối cùng và tính xác định khi chạy lại;
6. API chỉ công bố combo đúng trạng thái, thời hạn và anchor direction.

Các bộ Sign, Pumsb và T40I chỉ nên dùng cho đo hiệu năng khi provenance/format
đã được ghi nhận. Nếu thứ tự item chỉ là thứ tự mã item trong transaction thì
không được diễn giải chúng thành hành vi mua trước/sau của khách hàng.
