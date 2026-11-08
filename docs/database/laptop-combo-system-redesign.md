# Thiết kế hệ thống combo laptop — Quyết định và phạm vi tích hợp

Cập nhật: 2026-11-08. Tài liệu giữ thiết kế đích và các quyết định nghiệp vụ,
không dùng phân tích code cũ làm trạng thái runtime hiện tại.
Tra cột/kiểu/khóa tại [data dictionary](laptop-combo-data-dictionary.md);
đọc [WDM contract](../wdm/mining-request-contract.md) khi sửa mining/result và
[Auth](../auth/README.md) khi sửa account/session.

## 1. Trạng thái và cách đọc

| Phần | Trạng thái ghi nhận | Không được suy ra |
|---|---|---|
| V2, 45 bảng | Read-only local 2026-11-03: V1/V2 SUCCESS; V2 checksum 727165286; đủ bảng, không còn LEGACY_V1_* | Mọi DB khác hoặc application đã hoàn tất |
| V3 ownership | Checkpoint WDM-05.7 local 2026-11-07 ghi nhận SUCCESS | Tự replay/đổi status run legacy |
| V4 metadata | Migration runtime thêm 2 bảng, tổng 47 bảng ứng dụng | Owner deployment đã được kiểm chứng |
| Shared Auth | AUTH-01–06 đã nghiệm thu theo phạm vi ghi nhận | Production deployment/manual test mới đã PASS |
| WDM | Dataset → validation → conversion → execution → persistence/API/UI → export/provider, đến 06.11 đã có nghiệm thu | Combo generation/ranking/commerce đã triển khai |
| WDM-06.12 | Owner quyết định làm lại; NOT VERIFIED | Dùng báo cáo PASS cũ đóng final gate |

Các mốc trên là lịch sử, không phải kiểm DB hoặc chạy test trong lượt rút gọn này.
Runtime Flyway dùng classpath:db/migration, Hibernate ddl-auto=validate.
Không sửa V1/V2 đã áp dụng; thay schema bằng migration mới được review.
Một SQL draft trong docs không phải migration đã chạy.

Thiết kế gốc đã tham chiếu TV2_KhaoSatBaiToan_HeThongGoiYCombo_V5,
TV2_KhaoSatVaThietKeDuLieu_V3, ChiTietCacManHinhGiaoDien và DeXuatChinhSua.
Đó là provenance của review trước, không phải xác nhận vừa đọc lại các nguồn.
Figma chưa được kiểm tra trực quan; không khóa layout theo nhận xét chưa xác minh.

## 2. Mục tiêu, phân lớp và mô hình

Hai actor chính:
- Customer xem sản phẩm neo và Top-K combo đã công bố, có giải thích nguồn.
- Admin chuẩn bị dữ liệu, chạy mining, kiểm pattern, sinh/duyệt/publish combo.

```text
Orders/CSV → Dataset snapshot → Sequence + dictionary → Mining run
  → Full patterns + run-item statistics + provenance → Verified publication
  → Combo batch → Candidate/review/publish → Top-K theo product anchor

Cart → Checkout quote → Reservation + Order snapshots → Payment → Order COMPLETED
PO → Goods receipt POSTED → Inventory balance + append-only stock movement
```

Giữ mining result khoa học tách khỏi quyết định thương mại. Đổi K hoặc lọc/ranking
combo không được đổi pattern hoặc buộc chạy engine lại. Không ComboScore, hệ số
α/β/γ/δ hoặc “% phù hợp” suy ra từ rank.

| Nhóm dữ liệu | Vai trò |
|---|---|
| Identity: USER_ACCOUNT/ROLE/USER_ROLE/USER_SESSION/USER_ADDRESS/CUSTOMER | Shared Auth, quyền và PII tách pseudonymous customer |
| Catalog: CATEGORY/BRAND/PRODUCT/PRODUCT_IMAGE | Một SKU là một mining item; ảnh/specification phục vụ hiển thị |
| Inventory: WAREHOUSE/SUPPLIER/PO/receipt/balance/movement/serial | Tồn vật lý, audit và chống oversell |
| Sales: ORDERS/ORDER_ITEM/ORDER_APPLIED_COMBO/ORDER_ADDRESS/ORDER_STATUS_HISTORY | Hóa đơn/snapshot và transaction input cho mining |
| Cart/checkout/reservation/payment | Estimate → quote → order; idempotency và provider state riêng |
| Mining: ITEM_MAPPING/DATASET_SNAPSHOT/MINING_RUN/RUN_ITEM_STATISTIC/PATTERN/PATTERN_ITEM + V4 metadata | Full output, temporal structure, reproducibility và verified handoff |
| Combo: COMBO_BATCH/COMBO/COMBO_ITEM/COMBO_SOURCE/CATEGORY_EXCLUSION | Lọc/ranking có version, review, giá và provenance |

Catalog/ảnh/brand là engineering inference từ nhu cầu UI, không phải yêu cầu thuật toán.
Checkout/payment/inventory đầy đủ là lát cắt sau core mining/combo; không mặc nhiên
phải triển khai tất cả màn Electro. HR/chat/voucher/loyalty/carrier đầy đủ ngoài core.

## 3. Hợp đồng giao dịch và khả năng tái lập

- Một customer giả danh → một sequence; một order COMPLETED → một itemset.
  Sort (completed_at, order_id); không suy thứ tự sản phẩm trong cùng đơn.
- Dedup SKU trong mỗi đơn; quantity giữ cho thương mại nhưng không nhân occurrence
  khi chuyển SPMF. PAID/PENDING/PROCESSING/CANCELLED/RETURNED/REFUNDED... không vào dataset.
- Giữ <(laptop mouse)> khác <(laptop)(mouse)>; -1 kết thúc itemset, -2 kết thúc sequence.
  Không biến thứ tự mã item trong FIMI transaction thành hành vi mua trước/sau.
- ITEM_MAPPING dương/ổn định/không tái dùng; snapshot lưu raw/sequence/dictionary,
  checksum, kỳ dữ liệu, validation/conversion reports và thống kê.
  Không sửa lịch sử đã được snapshot/run tham chiếu.
- MINING_RUN lưu algorithm, configured/resolved support, global weights, lifecycle,
  metrics đúng scope; top_k thuộc COMBO_BATCH. Không nhận minWeight hay client-computed thresholds.
  Web request hiện có đúng snapshotId/algorithm/minsupMode/minsupValue theo WDM contract.
- Support_abs, support_ratio, weighted_support và pattern_weight là bốn đại lượng riêng.
  Acceptance mining giữ nguyên support(P) * MeanW >= effectiveMinSup.
- Persist full pattern→exact support map, temporal coordinates và all input-item stats,
  không chỉ số lượng/top-K. RUN_ITEM_STATISTIC giữ weights và immutable name/SKU snapshots
  theo capture policy 06.5, không tuyên bố là nhãn tại thời điểm mua hoặc engine chạy.
- RUN_PROVENANCE_V1 bind input; RUN_RESULT_PUBLICATION chỉ COMMITTED khi full result
  đã được publish/verify. SUCCEEDED riêng lẻ chưa đủ cho Combo.
  Empty published result export được nhưng không Combo eligible.
- Không thay frozen baseline/BitPrune để hợp UI; R1 source inconsistency, R6 zero-effective
  threshold và exact-real comparator equivalence không được coi đã giải quyết.
  JVM heap sampling không phải hardware RAM; web metrics không thay scientific benchmark standalone.

## 4. Pattern → combo và ranking

### Candidate và temporal semantics

1. Business filter tối thiểu: 2–4 sản phẩm khác nhau, ánh xạ đúng dictionary/run,
   product đang bán; laptop demo có đúng một Laptop và ít nhất một phụ kiện.
2. Có thể loại product lặp khi tạo bundle, nhưng giữ nguyên pattern khoa học.
   Benchmark dataset không tự sinh combo thương mại.
3. SAME_ORDER_BUNDLE: một itemset, thường mua cùng đơn.
   SEQUENTIAL_CROSS_SELL: nhiều itemset, hành trình trước/sau.
   MIXED_SEQUENCE: nhiều mốc và có itemset nhiều item; UI phải giải thích được.
4. Không gộp <(ab)> và <(a)(b)> chỉ vì cùng tập sản phẩm.
   Cross-sell không tự biến thành bundle giảm giá; cần quyết định ứng dụng rõ ràng.
5. Combo bị reject không xóa pattern. COMBO_SOURCE có đúng một primary pattern.
   Nếu gộp candidate, chọn primary bằng ranking tuple và lấy mọi metrics từ cùng pattern;
   không ghép MAX(support) với MAX(weight) độc lập.

### Ranking cố định và anchor có hướng

Policy đích SUPPORT_THEN_PATTERN_WEIGHT_V1:

```text
support_abs DESC → pattern_weight DESC → canonical_pattern ASC
```

Lọc đủ điều kiện theo anchor trước, rồi xếp hạng và lấy K; không lưu rank_position
cố định trên COMBO. Đây là ranking combo đề xuất, không thay sort contract của pattern API.

- Same-order: anchor có thể nằm bất kỳ vị trí trong itemset.
- Sequential/mixed: anchor phải thuộc context đầu/prefix; không đảo <(A)(B)> thành
  gợi ý A khi đang xem B. is_anchor_eligible và is_discount_eligible độc lập.
- Chỉ combo PUBLISHED, batch ACTIVE, còn hiệu lực mới tới storefront.
  Demo có một ACTIVE batch; activate mới/archive cũ cùng transaction.
  APPROVED chưa đồng nghĩa PUBLISHED.
- Giá/độ dài không thuộc relevance tuple. MeanW toàn cục làm support * MeanW
  không phá hòa được hai pattern cùng support trong một run.
- Tie-break dùng EQ2_EQ3_ARITHMETIC_MEAN_V1: mean item weights trong itemset,
  rồi mean itemset means. Implementation policy đã được owner chọn cho persistence;
  GVHD approval cho recommendation và bất nhất ví dụ nguồn vẫn là mục riêng chưa đóng.

Hiển thị “X/Y khách trong tập dữ liệu”, temporal kind và explanation; không gọi rank
là xác suất hay cá nhân hóa toàn lịch sử. Đánh giá cần coverage, số bị loại qua filter,
Top-K stability, laptop/accessory case studies; temporal holdout nếu có dữ liệu đủ.
Support_abs của hai run khác sequence count không được so trực tiếp thiếu bối cảnh.

## 5. Giá, order snapshots, checkout và payment

### Quote và giảm giá

Giá publish là reference snapshot để review; quote/checkout phải tính lại giá hiện tại
ở backend, không tin originalPrice/discount/grandTotal từ frontend.

```text
L = SUM(current_price * quantity)
B = SUM(current_price * quantity của item discount-eligible)
NONE:          D = 0
PERCENTAGE:    D = MIN(B * discount_value / 100, max_discount_amount nếu có)
FIXED_AMOUNT:  D = MIN(discount_value, B)
FIXED_PRICE:   D = MAX(0, B - discount_value)
final_subtotal = L - D
```

Tiền DECIMAL/cùng currency, không âm; percentage trong (0,100], valid_to > valid_from.
Chốt rounding scale/mode ở backend trước triển khai. NON_STACKABLE mặc định:
một line không nhận hai combo discounts; cách chọn khi nhiều combo cùng hợp lệ
còn phải chốt, “giảm nhiều nhất rồi combo_id” chỉ là ví dụ đề xuất.

Chốt đơn trong một transaction: verify batch/combo/thời hạn/items → lock/check
product/price version và tồn → tính tiền → phân bổ discount theo line eligible
(line cuối nhận rounding remainder) → lưu ORDER_APPLIED_COMBO/ORDER_ITEM/address
snapshots và ORDERS totals → commit hoặc rollback toàn bộ.

```text
SUM(line allocated combo discounts) = combo_discount_total
SUM(line_total) = list_subtotal - combo_discount_total
grand_total = SUM(line_total) - other_discount_total + shipping_fee + tax_total
applied_combo.list_subtotal - discount_amount = final_subtotal
```

Hai dòng đầu áp dụng khi line discount là phần combo theo contract này; không trộn
giảm khác vào allocated combo discount. SUM(line_total)=grand_total chỉ khi các thành phần
giảm khác/thuế/phí bằng 0. Cần khóa allocation contract khi bổ sung giảm giá khác.
Không tính lại hóa đơn cũ từ PRODUCT/COMBO/USER_ADDRESS hiện tại.

### Checkout, payment và idempotency

- Cart thuộc customer hoặc guest-token hash; totals chỉ estimate, version phát hiện stale.
  Guest confirm phải tạo/ánh xạ CUSTOMER giả danh trước ORDERS.
- Quote có expiry và cart_version_snapshot; confirm cùng Idempotency-Key trong
  server-derived scope chỉ tạo một order/reservation set. Stale quote/cart yêu cầu quote lại.
- Tách trạng thái checkout, order và payment:
  QUOTED → CONFIRMED/order+snapshots+reservation → PAYMENT_PENDING → PAID/PROCESSING
  → COMPLETED theo nghiệp vụ bán hàng. PAID chưa phải input mining.
- PAYMENT tổng hợp; PAYMENT_ATTEMPT là lần thử; PAYMENT_TRANSACTION là authorize/
  capture/refund/void; PAYMENT_WEBHOOK_EVENT là inbox chống xử lý lặp.
- Provider reference/event uniqueness theo tuple DDL; webhook verify chữ ký, amount/
  currency/ownership/state transitions, chịu callback lặp/sai thứ tự.
  Không lấy browser redirect làm payment success; capture/refund phải reconcile.
- Không PAN/CVV/raw bank token/secret trong DB/API/log. COD trước, tối đa một sandbox
  provider khi đã có contract; production gateway certification ngoài phạm vi.

## 6. Kho và nhập hàng

- PURCHASE_ORDER chỉ là đặt mua, không tăng tồn. Receipt DRAFT → POSTED mới tăng tồn.
  Receipt item phải đúng PO/product/kho; không nhận vượt số đặt nếu chưa được duyệt policy.
- Trong transaction post: lock/verify PO và receipt → update quantity_received/balance
  → append STOCK_MOVEMENT + serials → commit. Retry không nhập hai lần.
- available = on_hand - reserved >= 0; reservation chỉ đổi reserved.
  Sale issue consume reservation, giảm on-hand và ghi movement cùng transaction.
  Lock/optimistic version chống oversell; không endpoint sửa balance trực tiếp.
- Movement append-only, reference source và balance_after để audit; corrections bằng
  movement mới. Serial unique, track IN_STOCK/RESERVED/SOLD/RETURNED/DAMAGED,
  không bán serial hai lần.
- Đối chiếu ledger với balance theo product/kho. Unit cost snapshot chưa phải hệ thống
  kế toán FIFO/bình quân/công nợ hoàn chỉnh.

## 7. API và UI đích

Bảng này là thiết kế integration, không phải xác nhận mọi route đã tồn tại.
Auth/WDM hiện dùng contract/module thật; không copy request schema lịch sử.

| Nhóm | Route/chức năng đích |
|---|---|
| Catalog | GET /api/v1/catalog/categories; products?category=&brand=&q=; products/{id} |
| Recommendation | GET /api/v1/recommendations/combos?productId=&limit=; GET /{id}; POST /{id}/quote |
| Cart | GET /api/v1/cart; POST/PATCH/DELETE items; POST/DELETE combos |
| Checkout/order | POST /api/v1/checkout-sessions; GET /{id}; POST /{id}/confirm; GET /api/v1/orders/{id} |
| Payment | POST /api/v1/orders/{id}/payment-attempts; GET /api/v1/payments/{id}; POST /api/v1/payment-webhooks/{provider} |
| Admin WDM | Dataset/validation/conversion, POST/GET mining-runs, status/pattern/detail/export theo WDM contract |
| Admin Combo | POST mining-runs/{id}/combo-batches; GET combo-batches/{id}; PATCH /activate; GET combos?batchId=&status=&kind=&sort=; PATCH combos/{id}/review hoặc /publish |
| Admin hỗ trợ | Catalog/account/role; supplier/warehouse; PO approve/receipt post; balance/movement read; experiments compare chỉ sau equivalent-input/full-map gate |

Admin routes dưới /api/v1/admin. Checkout/payment tạo mới dùng Idempotency-Key;
webhook riêng, xác minh provider signature, không dựa customer session.
Backend làm Auth/ADMIN guard, pricing và ranking; frontend không tự tính hoặc gửi coefficients.

DTO đích: IDs là string xuyên DB/Java/URL/TypeScript; UINT64/count cần string theo API
để không mất chính xác. Catalog gồm SKU/slug/brand/images/specifications/price/saleable;
combo gồm rank/kind/prices/currency/supportAbs/supportRatio/sequenceCount/patternWeight/
explanation/items với temporal positions và eligibility; cart có version/estimates/warnings;
checkout có expiry/totals/orderId; payment có paid/refunded/latest-attempt status.
Product variants RAM/SSD là mở rộng riêng, phải quyết định đơn vị mining item trước.

UI core:
- Client: catalog/product → related combos → combo detail giữ “cùng đơn/sau đó”.
- Admin: login → data preparation → configuration/run → pattern explorer →
  combo generation/review → history/experiments. K ở batch, không input engine.
- Không progress % nếu thiếu total-work đáng tin. Chỉ so performance khi cùng snapshot/
  config và full maps bằng nhau; không mở scientific benchmark UI trong WDM.
- Cart/checkout/payment result là slice sau core; status từ server, không từ redirect.

Tái dùng Router/Mantine/Query/providers, header/layout và table/loading/error components.
Thay model/API Electro sai ID/field/port; không chuyển legacy 8085 sang API mới máy móc.
Auth/WDM pages đã có: review theo trạng thái hiện hành, không ghi lại là chưa bắt đầu.
Commerce/cart/payment/Combo chưa có bằng chứng hoàn tất thì xây từng vertical slice.
Ẩn feature ngoài scope; xóa source chỉ theo danh sách được duyệt, không giữ menu giả.

## 8. Thứ tự thực hiện và tiêu chí nghiệm thu

Đọc checklist trước; không tự chạy lại các task đã accepted.
Ưu tiên tiếp theo theo quyết định owner: làm lại WDM-06.12, rồi mới các task Combo
được duyệt. Các slice catalog/inventory/checkout/payment làm riêng theo nhu cầu;
không trộn migration/UI với thay đổi mining-core.

| Gate | Bằng chứng cần có |
|---|---|
| Data/reproducibility | Fixture >=3 customers, nhiều mốc và I/S patterns; cùng input tạo cùng SPMF/checksums; đúng excluded rows/reasons |
| Mining/result | Full baseline/BitPrune/oracle maps + support/count, temporal/dictionary lineage; 06.12 fresh DB/API/UI/export/provider gate |
| Combo | Deterministic candidate/filter/ranking, một primary source; PUBLISHED/ACTIVE/valid/anchor đúng; không xóa pattern bị business reject |
| Auth/UI | ADMIN 401/403; revoked sessions bị chặn; đúng DTO/string IDs, không fake endpoints/client-side score |
| Price/order | Các discount types/caps/rounding, overlap, stale price/quote và immutable snapshots; mọi money invariant khớp |
| Inventory | Receipt retry chỉ một effect, ledger/balance reconcile, concurrent reserve không oversell, serial không bán lặp |
| Checkout/payment | Scoped idempotency tạo một order; duplicate/out-of-order webhook không double capture/refund; không secret/PII leakage |

Mỗi gate ghi PASS/PARTIAL/NOT VERIFIED và môi trường/log; thiết kế này không tự tick
acceptance. Không có bằng chứng tương ứng thì NOT VERIFIED.

## 9. Quyết định và bằng chứng còn mở

- GVHD phê duyệt recommendation tie-break policy, cách báo cáo bất nhất Eq.(2)/(3)
  với ví dụ nguồn; owner chọn policy persistence không tự đóng research question.
- Multi-itemset bán cùng lúc hay chỉ cross-sell theo giai đoạn; discount eligibility/UI/evaluation.
- Nguồn/giấy phép/ẩn danh dataset laptop; trực quan Figma/layout/responsive.
- Rounding VND, overlapping-combo selection, reservation/checkout expiry/refund policy.
- Một hay nhiều kho, over-receipt và cost method; contract provider sandbox.
- WDM-06.12 fresh gate và deployment từng môi trường: NOT VERIFIED đến khi có evidence.

Fixture TV2 mô tả 15 customers, 39 orders (36 hợp lệ), 16 products, 55 occurrences,
kỳ vọng 20 patterns/12 candidates: đây là **kỳ vọng nguồn, chưa phải kết quả tái lập**.
Cần version/checksum và assertions cho projection, full maps, weight policy, từng filter,
ranking và publish/anchor API. Không seed kết quả mong đợi để giả mining thật.
Sign/Pumsb/T40I chỉ dùng đo theo provenance/format phù hợp; không diễn giải transaction
item ordering thành lịch sử mua. Không tuyên bố chất lượng cá nhân hóa hoặc performance
trước khi đủ nguồn, equivalence và measurements.
