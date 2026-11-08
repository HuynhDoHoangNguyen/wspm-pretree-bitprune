# WDM — Dataset/CSV, validation và conversion contract

Cập nhật: 2026-11-08. Contract CSV: `wdm-order-csv-v1`.
Đây là hợp đồng dữ liệu ứng dụng, không phải thay đổi thuật toán mining.
Chỉ đọc mục liên quan; trạng thái task nằm tại
[WDM_CHECKLIST.md](../../.agent-workspace/phase-wdm/WDM_CHECKLIST.md).

## 1. Nguồn chuẩn và trạng thái

- Schema: [Flyway V2](../../backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql).
  [Data dictionary](../database/laptop-combo-data-dictionary.md) mô tả kiểu/khóa;
  [Auth](../auth/README.md#api-contract) quy định envelope/identity/guards.
  [Mining contract](mining-request-contract.md) quy định bước chạy/result tiếp theo.
- **Owner chọn ordered_at ngày 2026-11-04 cho cả chọn kỳ và chronology của WDM.**
  Quyết định riêng này ưu tiên hơn mô tả completed_at tổng quát trong tài liệu database.
  COMPLETED vẫn là status bắt buộc; không tự đổi time field.
- WDM-01.1/01.2/01.4–01.8, WDM-02.1–02.7 và WDM-03.1–03.8 đã được owner accepted
  theo phạm vi ghi nhận. WDM-01.3 có technical evidence nhưng checkbox owner acceptance
  riêng còn mở. Auth shared-provider đã triển khai, BLK-01 closed 2026-11-05.
- Các checkpoint “chưa có Auth/storage/converter; trả 503” trong lịch sử đã được thay thế
  bằng implementation hiện hành dưới đây. Không dùng chúng mô tả runtime hiện tại.
- Các mốc PASS bên dưới là lịch sử, không phải tests chạy lại khi rút gọn.
  WDM-06.12 owner yêu cầu làm lại; NOT VERIFIED.
- Không đổi support/weights/threshold/pruning/Prime-block. R6 và exact-real equivalence
  vẫn mở; chính sách commercial grouping/cleaning/tie-break là quyết định ứng dụng.

## 2. CSV v1 — Record và 13 cột

Một record là một ORDER_ITEM lịch sử, không phải một order/product.
Export/upload giữ nguyên line, không dedup/aggregate/nhân quantity.

```text
order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code
```

Header case-sensitive, đúng thứ tự và đủ 13 cột, không extra/missing/duplicate.
Mỗi giá trị bắt buộc không rỗng/blank; structural parsing và business validity là hai bước riêng.

| Cột | Nguồn/giới hạn |
|---|---|
| order_id | ORDERS.order_id; opaque text <=40 Unicode code points |
| order_item_id | ORDER_ITEM ID; canonical ASCII decimal 1..18446744073709551615 |
| customer_code | CUSTOMER.anonymous_code; stable pseudonym <=80 code points, không raw customer/user ID |
| ordered_at | ORDERS.ordered_at; UTC ISO-8601, đúng 6 fractional digits + uppercase Z |
| status | Chính xác COMPLETED |
| product_id | ORDER_ITEM.product_id; opaque text <=40 code points, catalog ID, không phải mining item ID |
| sku_snapshot | Historical SKU <=80 code points |
| product_name_snapshot | Historical name <=200 code points |
| quantity | Canonical ASCII decimal 1..4294967295 |
| list_unit_price | Historical per-unit DECIMAL(19,2), không âm |
| allocated_discount_amount | Historical discount cho cả line, DECIMAL(19,2), không âm |
| line_total | Historical stored line total, DECIMAL(19,2), không âm |
| currency_code | Đúng 3 uppercase ASCII letters; không tự chuyển đổi currency |

Không trim/case-fold/Unicode-normalize opaque text. Blank được xét bằng
Character.isWhitespace hoặc isSpaceChar, gồm NBSP; không tự bỏ zero-width space.
Pseudonym không phải cam kết ẩn danh không thể đảo ngược; không export tên người,
email/phone/address/user ID. Không suy customer identity từ tên.

Dùng BigInteger/long cho unsigned integers, BigDecimal cho tiền; không đi qua JS Number
hoặc binary float. Integer không sign/leading zeros; money đúng mẫu
`(0|[1-9][0-9]{0,16})\.[0-9]{2}`, không exponent/grouping/rounding.
Timestamp parse calendar strict, không 24:00/leap-second normalization hoặc timezone đoán.

Export đọc giá/SKU/name đã lưu ở ORDER_ITEM, không fallback current PRODUCT.
Giữ stored line_total; không tính lại để “sửa” lịch sử. Business validation kiểm exact:

```text
gross = list_unit_price * quantity
allocated_discount_amount <= gross
line_total = gross - allocated_discount_amount
```

Intermediate gross không bị ép về DECIMAL(19,2). Chỉ kiểm quan hệ khi cả bốn numeric
inputs hợp lệ; có thể báo đồng thời hai relationship failures. Thuế/phí/order-level
amounts ngoài các line amounts này; currency không trở thành mining weight.

## 3. Chọn kỳ, export bytes và upload transport

### Export

`GET /api/v1/admin/order-exports?fromTime=...&toTime=...` nhận đúng một value cho mỗi
filter bắt buộc; không extra/status/sort/pagination. Offset timestamps phải explicit,
<=microsecond precision (kể cả trailing zeros), normalize UTC, fromTime < toTime,
nằm trong MySQL DATETIME years 1000..9999. Dấu + trong URL offset encode thành %2B.
Sai filter/time trả 400 VALIDATION_FAILED, không truncate/round.

Selection: current exact status COMPLETED và ordered_at trong **[fromTime,toTime)**,
không phải status “as of toTime”. Completion có thể xảy ra sau kỳ. Date-only UI phải
map midnight bằng display timezone tường minh; không +23:59:59 hoặc host timezone.
DATETIME(6) lưu UTC wall-clock: offset → Instant → UTC LocalDateTime; đọc gắn UTC.
Unknown/mixed-zone legacy phải báo trước, không đoán.

Một read-only REPEATABLE_READ query materialize consistent rows; missing lines trên
eligible order fail thay vì bỏ order. Không per-order query hoặc đọc current catalog.
Sort customer_code, ordered_at, order_id ASC; order_item_id numeric ASC.
Opaque strings dùng exact Unicode code-point order/binary UTF-8, không default collation.
Equal-time khác order vẫn là itemsets riêng; order_id tie-break chỉ bảo đảm tái lập.

Canonical export: UTF-8 **một BOM đầu EF BB BF** cho Excel tiếng Việt, comma, CRLF và
một final CRLF; quote khi có comma/quote/CR/LF, escape quote bằng double quote.
Preserve embedded newlines/text. Empty export = BOM + header + CRLF.
200 text/csv;charset=UTF-8, server UUID attachment filename, exact Content-Length,
no-store/request ID; bytes hoàn tất trước response commit. Export không tạo snapshot.

SQL lấy maxRows+1 sentinel; vượt row hoặc UTF-8 byte budget trả 413, không truncate.
Byte count gồm BOM/header/quotes/newlines. Buffers nằm memory; không bảo đảm concurrent
heap/DB scan cost hoặc 100 MiB capacity chỉ từ config limits.

### Upload

`POST /api/v1/admin/dataset-snapshots`: multipart/form-data, đúng một nonempty part
`file`, không query/extra text/file parts. ADMIN guard trước lazy multipart parsing.
Filename plain/nonblank <=255 Java UTF-16 units, không slash/backslash/colon/ISO controls,
suffix .csv case-insensitive; filename chỉ là display metadata, không path.
Part MIME chấp nhận text/csv, application/csv, application/vnd.ms-excel,
application/octet-stream hoặc absent; base type case-insensitive, parameters cho phép.

MIME/suffix không chứng minh CSV hợp lệ. Arbitrary nonempty bytes, header-only hoặc
invalid UTF-8 đều có thể được acquire PENDING; zero-byte bị từ chối.
Missing/empty/duplicate/unsafe shape → 400; malformed multipart → 400 MALFORMED_REQUEST;
sai media/suffix → 415; oversize → 413. Không automatic POST replay.

Upload parser về sau nhận UTF-8 optional một leading BOM, LF/CRLF (có thể mixed),
quoted fields/newlines; không autodetect delimiter/encoding hoặc spreadsheet conversion.
Không prefix-mutate =,+,-,@; đây là data-exchange artifact, consumer phải import text as text.

## 4. Raw snapshot — Storage, provenance và read

### Acquisition/lifecycle

Upload stream original bytes vào private generated `<snapshotId>/raw.csv`,
CREATE_NEW/NOFOLLOW_LINKS; độc lập mỗi upload, kể cả cùng checksum.
Đo actual bytes và SHA-256 lowercase, force/close file và input trước DB persistence.
Không parse/reserialize/overwrite/checksum-dedup.

Storage root `wdm.snapshot-storage.root` / WDM_SNAPSHOT_STORAGE_ROOT,
mặc định `${user.home}/.wspm/snapshots`; absolute normalized, ngoài working tree,
không static mapping. Reject link/reparse/special ancestors. Service cần private OS access;
không chống privileged OS writer hoặc sửa ACL root có sẵn.

REQUIRES_NEW acquisition commit xong mới trả **201 ApiResponse**:
snapshotId, sourceType/sourceName, validationStatus=PENDING,
conversionStatus=NOT_STARTED, statisticsStatus=NOT_COMPUTED, rawSha256/rawSizeBytes,
sequenceCount=null, fromTime/toTime=null, server UTC createdAt (microseconds).
Không public paths/arbitrary conversion_report_json; không seed giả validation.

Upload luôn source_type=CSV/acquisition=CSV_UPLOAD, kể cả upload file app export.
DATABASE/DATABASE_EXPORT chỉ cho acquisition backend thực sự thực hiện.
Initial root JSON giữ contractVersion, conversionStatus, statisticsStatus và provenance:
acquisition, sourceTimeZone=null (UTC nếu trusted DB), timeField=ordered_at,
statusPolicy=COMPLETED_CURRENT_AT_ACQUISITION, actual rawSizeBytes string.
Internal sequence_count=0 chỉ placeholder; public count null đến COMPUTED.
Sequence/mapping refs/hashes/derived nullable statistics ban đầu null.

Filesystem và DB không atomic: rollback chắc chắn trước commit có thể cleanup current
receipt; UNKNOWN/COMMITTED hoặc sau beforeCommit giữ file để reconcile.
Partial storage failures chỉ cleanup allocation của request; verify identity, không recurse
arbitrary tree/old artifacts. Lost response có thể sau commit thật; 503/transport error
không chứng minh snapshot chưa được tạo. Không exactly-once/sweeper tự động.
Operator reconcile snapshotId/DB reference/hash/exact bytes trước quyết định cleanup.
File force không chứng minh crash/power-loss durability toàn hệ thống.

### Read API

| Route dưới /api/v1/admin | Kết quả |
|---|---|
| GET /dataset-snapshots/{id} | ApiResponse snapshot detail với whitelisted provenance |
| GET /dataset-snapshots/{id}/artifacts/raw | Original verified bytes, text/csv attachment |

Canonical lowercase UUID: malformed 400, missing 404. Detail nhận conversion states
NOT_STARTED/RUNNING/SUCCEEDED/FAILED; stats NOT_COMPUTED/COMPUTED, exact counts strings.
Unknown metadata thành null, không invented defaults hoặc expose path-like legacy name.
Không nhầm validation VALID với đã COMPUTED statistics.

rawAvailability AVAILABLE/MISSING/UNAVAILABLE là probe path/type/size, **không fresh hash proof**.
Raw download mới check safe generated ref/no-follow, actual size + SHA-256, buffer toàn bộ
trước success; trả đúng byte array đã verify, không mở lại path.
Missing raw 404; unsafe/unreadable/size-hash mismatch 503, không repair/update metadata.
Limit raw read theo current multipart max-file-size và Java array bound; giảm limit có thể
làm file cũ unavailable. Buffer không giới hạn tổng heap của concurrent requests.
Filename snapshot-<UUID>-raw.csv, exact length, no-store. Transfer lỗi sau commit không append JSON.

## 5. Validation pipeline

### Parser — WDM-02.1

OrderCsvParser độc lập Spring/DB/HTTP; caller sở hữu stream. Header decoded phải đúng
13 names/order (quoted header cho phép); fields vẫn strings, không clean/reorder/skip rows.
REPORT invalid UTF-8, bỏ tối đa một leading BOM, reject double BOM; giữ BOM trong data.
Bare CR ngoài quotes bị reject; quoted CR/LF/CRLF giữ nguyên.
Quote chỉ mở ở field start; sau closing quote chỉ quote escape/comma/newline/EOF,
không spaces. Cho phép final record không newline; blank record không bị bỏ qua.
Header-only parse 0 rows; empty/BOM-only thiếu header.

Fail-first; callback có thể đã nhận provisional prefix: chỉ dùng dataset sau EOF success,
không publish từ từng callback. Consumer/source I/O failures propagate, không giả CSV-invalid.
maxRecordCharacters đếm UTF-16 gồm syntax/terminators, không BOM; là resource budget.
Positions: logical recordNumber 1-based (header=1), inclusive physical start/end lines,
CRLF một break. Invalid UTF-8 chỉ DECODER_CURSOR accuracy, không exact offending byte.

Structural diagnostics:
`CSV_INVALID_UTF8, CSV_INVALID_BOM, CSV_MISSING_HEADER, CSV_HEADER_MISMATCH,
CSV_UNCLOSED_QUOTE, CSV_INVALID_QUOTE, CSV_INVALID_RECORD_SEPARATOR,
CSV_COLUMN_COUNT_MISMATCH, CSV_RECORD_LIMIT_EXCEEDED`.

### Business và consistency — WDM-02.2/02.3

Validator giữ nguyên fields, kiểm rules mục 2 và exact catalog product_id.
Không thêm current product status/price/SKU/name hoặc customer existence conditions;
imported order/line IDs không cần tồn tại trong live ORDERS.
Catalog lookup ID-only, chunks <=256, exact Java intersection chống collation aliases,
không per-row queries. Lookup failure là operational failure, không PRODUCT_NOT_FOUND.
Không áp future-date/acquisition-window rule hoặc ISO currency whitelist cho CSV rows.

Business diagnostics theo column order, required/length lỗi suppress dependent checks:
`FIELD_REQUIRED, FIELD_TOO_LONG, INVALID_INTEGER_FORMAT, VALUE_OUT_OF_RANGE,
STATUS_NOT_COMPLETED, INVALID_TIMESTAMP, INVALID_MONEY_FORMAT,
DISCOUNT_EXCEEDS_GROSS, LINE_TOTAL_MISMATCH, INVALID_CURRENCY_CODE, PRODUCT_NOT_FOUND`.

Checker nhận complete snapshot và matching business results, giữ cả invalid rows.
Chỉ field không có business issue mới được dùng làm evidence/index; không so invalid text
để tạo conflict giả. Exact line IDs xét toàn file, không chỉ adjacent:

| Diagnostic | Quy tắc |
|---|---|
| ORDER_FIELD_CONFLICT | Cùng usable order_id khác customer_code/ordered_at/currency_code, từng field |
| DUPLICATE_LINE_ID | Line ID lặp, kể cả exact copies |
| LINE_ID_ORDER_CONFLICT | Line ID thuộc nhiều order IDs |
| LINE_ID_CONTENT_CONFLICT | Line ID có payload khác ngoài order_id/order_item_id, từng comparable field |

Khác line ID nhưng cùng product không phải duplicate; equal timestamp không merge orders.
Giữ safe numeric source positions và deterministic findings; affectedOrderIds không phải
rejection list và hasConsistencyIssues không phải dataset-valid predicate.
Matching positions không chứng minh cryptographic content identity; handoff completeness do caller.

### Cleaning — WDM_ORDER_CLEANING_V1

1. Bất kỳ row không có usable order_id → BLOCKED toàn snapshot, không partial clean.
   Unassignable row reason UNATTRIBUTABLE_ORDER_ID, phần còn lại SNAPSHOT_BLOCKED.
2. Bất kỳ business-invalid row → reject **cả order** với BUSINESS_RULE_FAILURE.
   Order-field conflict reject order; line-ID cross-order/content conflict reject mọi order liên quan.
   Reasons accumulate; phải index toàn input trước chọn representatives.
3. Với surviving orders, chỉ collapse cùng line ID và exact 13-field payload, mọi copies
   business-valid; giữ lowest recordNumber, giữ toàn origin positions. Không cộng quantity/tiền lại.
4. Khác line IDs giữ nguyên kể cả cùng product; product-group views chỉ tham chiếu lines,
   không chế giá/tên lịch sử đại diện. Không repair values hoặc reassign customer/time.

Outcomes: READY (usable orders), EMPTY_INPUT, EMPTY_AFTER_CLEANING,
BLOCKED_UNATTRIBUTABLE_ORDER. Mỗi source row đúng một disposition:
KEPT, DEDUPLICATED (IDENTICAL_LINE_REPETITION), REJECTED_ORDER hoặc BLOCKED.
READY chưa phải persisted VALID hoặc mining permission. Cleaning chưa chronology-sort;
trace order theo source positions, không thay raw/checksum.

### Report persistence và validation API — WDM-02.5/02.6

WDM_VALIDATION_REPORT_V1 bind snapshotId/rawSha256/rawSizeBytes, CSV/cleaning versions;
completedAt là server completion, không DB commit timestamp.
READY → VALID dù raw có bad rows; empty/blocked/structural errors → INVALID.
Operational storage/catalog/DB/budget errors không giả INVALID.
Structural fail: countsComplete=false, parsedRecords chỉ complete prefix, unknown totals null,
không partial manifest. inputOrders null khi có unusable order key; rejectedOrders null khi blocked.
Counts decimal strings; businessInvalidRecords đếm rows, issueCount đếm entries.

Private manifest giữ mọi source disposition/representative/reasons; public diagnostics chỉ
stage/code/fixed field/numeric positions, sort record→stage→code→field, không raw IDs/values.
Report nằm validation namespace của existing root JSON; preserve acquisition/other namespaces.
Compute/I/O ngoài TX; short REQUIRES_NEW/NOWAIT/5s lock recheck PENDING/raw/root/no artifacts,
report + status commit cùng nhau. Local same-snapshot guard/concurrency semaphore;
nhiều instance có thể compute nhưng chỉ một publisher thắng, không distributed exactly-once.

POST/GET `.../dataset-snapshots/{id}/validation`: POST không body/query, 200 sau commit
kể cả INVALID; GET summary + diagnostics page, chưa report → 409.
GET chỉ page canonical >=0 (default 0), size 1..100 (default 20);
unknown/repeated/out-of-range → 400, beyond-last page rỗng đúng totals.
Busy/stale state 409; operational/lock failure có thể 503. Ambiguous commit: đọc status trước retry.
Replay verify stored binding/version/policy/status, không đọc raw/catalog lại hoặc overwrite.
Catalog đổi không revalidate tự động; muốn validation mới thì upload snapshot mới.
Replay sau conversion vẫn read-only, không fresh physical raw attestation.

## 6. Conversion — Gate, mapping, grouping và SPMF

| Stage/policy | Hợp đồng hiện hành |
|---|---|
| Gate (02.7) | VALID/READY/countsComplete, >=1 kept row/order; supported report/cleaning/raw binding + fresh integrity; full manifest/partition/dedup links khớp every record/span |
| Mapping | GLOBAL_PRODUCT_ITEM_INT_V1 |
| Grouping | CUSTOMER_ORDER_ITEMSET_V1 |
| Ordering | CUSTOMER_ORDER_TIME_CODEPOINT_V1 |
| SPMF | ORDERED_CUSTOMER_SPMF_UTF8_LF_V1 |
| Artifacts | CONVERSION_ARTIFACT_V1 |

Gate cho conversion mới yêu cầu NOT_STARTED/NOT_COMPUTED, placeholder zero, chưa output;
verify raw và reread state. Trust private validated manifest, không rerun current catalog/business
cleaning hoặc bảo vệ fully forged private DB. VerifiedConversionInput immutable giữ matching
raw/report/kept records, không public paths; đây không phải reservation.
Conversion service phải atomically claim/recheck trước work; gate-only eligible-503 chỉ là lịch sử.

### Stable mapping

ITEM_MAPPING global append-only: identity exact product_id, không SKU/name/quantity/price/status.
Web IDs 1..2147483647; core ability dùng zero không thay đổi. Existing global max vượt int → 409;
không wrap/repair/renumber snapshot. Reuse mappings; new products sort **Java String.compareTo
(UTF-16)** rồi allocate trên global max. Case/accent/space-only catalog aliases reject.
Dictionary là immutable bijection, numeric item-ID order, đúng kept product set.

Complete batch REQUIRES_NEW/READ_COMMITTED/10s; contention/duplicate retry fresh transaction
tối đa 4 attempts, hết retry/ID capacity → 503; incompatible state → 409, non-contention không retry.
Committed global IDs tồn tại sau conversion rollback; không recycle/update/delete.
Khác registry histories/concurrent batches có thể khác IDs: tái lập phải dùng frozen dictionary,
không dựng lại từ current catalog. Current product price/status không thêm eligibility rule.

### Grouping và chronology

Một exact customer_code → sequence; một exact order_id → itemset.
Order metadata customer/time/currency phải đồng nhất; order ID không thuộc hai customers.
Dictionary keys phải bằng kept product set; giữ mỗi kept source line/position đúng một lần.
Same product trên nhiều kept lines hoặc quantity>1 thành **một item/order**; item ở order khác vẫn giữ.
Không tổng tiền/quantity/weights; different currencies không tách customer.

Sort customers theo Unicode **code points**, rồi orders theo ordered_at Instant,
tie theo order_id code points. Không dùng UTF-16 comparator mapping ở bước này:
U+E000 trước U+10000; order-10 trước order-2 khi equal time.
Giữ microseconds, equal-time orders riêng; tie-break không chứng minh chronology nhỏ hơn microsecond.
Item IDs ascending/distinct trong itemset; source record encounter order chỉ provenance,
không SID/chronology. Shuffled raw có thể khác raw hash nhưng cùng canonical output trong cùng registry.

### Canonical SPMF

```text
1 2 -1 3 -1 -2
2 12 -1 -2
```

Một customer/line, một order/nonempty itemset; token -1 rồi sequence -2, một ASCII space,
không trailing space, UTF-8/LF, final LF, không BOM/header/comments/CR/blank lines.
Giữ supplied customer/order structure và repeated items qua itemsets; không flatten.
No zero/negative/duplicate/descending item IDs; invalid handoff fail closed, không normalize.
Customer/order/text/currency/quantity/price không thành tokens.
Core parser unchanged gán SID 1-based theo sequence order; không customer ID trong SPMF.
Empty utility input không cho phép publish empty snapshot hoặc định nghĩa R6.

## 7. Conversion publication, read và API

Claim row bằng short REQUIRES_NEW: recheck validation digest/source/raw, unpublished fields
và không MINING_RUN tham chiếu; commit RUNNING + fresh attempt UUID trước mapping/file work.
Không TX kéo dài qua pipeline/I/O. Publish recheck cùng attempt/binding/no runs.

```text
<snapshotId>/conversions/<attemptId>/sequences.spmf
<snapshotId>/conversions/<attemptId>/mapping.json

NOT_STARTED → RUNNING → SUCCEEDED / COMPUTED
                      → FAILED / NOT_COMPUTED
```

Exclusive attempt files, không overwrite. mapping.json canonical UTF-8 + final LF:
formatVersion=CONVERSION_ARTIFACT_V1, policyVersion=GLOBAL_PRODUCT_ITEM_INT_V1,
items [{itemId,productId}] numeric item-ID order; không current display labels/prices.
Decode reconstruct canonical bytes, reject unsupported/duplicate/invalid mappings.

Force/close/readback verify size/SHA; core parser full SID/itemset/item comparison với ordered input,
dictionary item-set equality; raw integrity recheck trước publish. Một DB transaction ghi cả hai
refs/hashes, nine statistics, operation sizes/timestamps và SUCCEEDED/COMPUTED.
ValidationStatus vẫn VALID nếu conversion operational failure.

Statistics từ readback core database: sequenceCount, singleItemsetSequenceCount,
multiItemsetSequenceCount, itemsetCount, distinctItemCount, totalItemOccurrences,
minSequenceLength, maxSequenceLength, averageSequenceLength.
Length là tổng items qua itemsets, không orders/quantity/source lines; cross-order repeats tính riêng.
Average=(double)totalItemOccurrences/sequenceCount chỉ descriptive metric; empty không publish.

Work failure trước publish chỉ mark FAILED cho owning claim; cleanup receipt sau confirmed failure.
Claim/failure/publish commit uncertain → giữ claim/files, không blind retry/delete.
Khi đã invoke publication, retain artifacts dù báo rollback; no automatic FAILED retry,
abandoned RUNNING takeover/recovery worker. Global mapping đã commit không rollback theo snapshot.
Replay SUCCEEDED verify policies/digest/raw/artifact paths/sizes/hashes/dictionary/SPMF/stats
và fresh metadata reread; không allocate attempt hoặc query current mapping/catalog.
Missing/tampered/incompatible fail closed, không repair/downgrade metadata.

| Route dưới /api/v1/admin/dataset-snapshots/{id} | Hiện hành |
|---|---|
| POST /conversion | Synchronous, 200 confirmed SUCCEEDED; SUCCEEDED repeat verify/no allocation; RUNNING/FAILED/ineligible 409 |
| GET /conversion | Read-only status; SUCCEEDED trả fully verified result |
| GET /artifacts/sequence | Verified text/plain;charset=UTF-8 bytes, snapshot-<UUID>.spmf |
| GET /artifacts/mapping | Verified application/json bytes, snapshot-<UUID>-mapping.json |

Cả bốn route không body/query; no force/overwrite/recovery/algorithm inputs, không async 202.
Malformed UUID 400, missing 404, not-ready download 409; corrupt metadata 409,
missing/tampered/unreadable artifacts 503. ADMIN trước request checks/lookup/I/O.
Downloads dùng verified buffered bytes, không reopen path hoặc JSON success envelope;
exact length, no-store/nosniff, errors trước attachment commit.

ConversionResponse chỉ snapshotId/status/stats, safe UTC timestamps/failureCode=CONVERSION_FAILED,
supported artifactVersion, statistics và descriptors {sha256,sizeBytes,downloadUrl}.
Integer counts/sizes/lengths strings; average finite JSON number.
Pending states không stats/downloads. Không attempt ID/path/validation digest/private report.
GET không claim/fail/map/create/convert; shared Auth session lifecycle vẫn có thể ghi riêng.

## 8. Resource budgets và HTTP conventions

| Boundary/config | Default |
|---|---|
| Export WDM_ORDER_EXPORT_MAX_ROWS / MAX_BYTES | 100000 data records / 104857600 bytes |
| Upload WDM_CSV_UPLOAD_MAX_FILE_BYTES / MAX_REQUEST_BYTES | 104857600 / 105906176 bytes (100/101 MiB) |
| Validation WDM_VALIDATION_MAX_RECORD_CHARACTERS | 16384 UTF-16 units |
| WDM_VALIDATION_MAX_ROWS / MAX_RAW_BYTES | 100000 complete records / 16777216 bytes (16 MiB) |
| WDM_VALIDATION_MAX_REPORT_BYTES | 8388608 bytes, toàn serialized root JSON |
| WDM_VALIDATION_MAX_DURATION_MILLIS / MAX_CONCURRENT | 30000 (max 300000) / 2 per instance |
| Conversion WDM_CONVERSION_MAX_ARTIFACT_BYTES | 67108864 bytes/file (64 MiB) |

Spring multipart overrides được honor qua actual MultipartConfigElement; request > file limit,
positive finite limits; export row limit phải chừa sentinel trong signed int.
Multipart lazy/disk threshold 0; servlet temp parts khác durable snapshot.
Upload 100 MiB không có nghĩa validation 16 MiB hoặc raw preview đều nhận được.

Validation/conversion reuse bounded permits/cooperative checkpoints, không hard-preempt blocked
filesystem/JDBC; local semaphore không phải global distributed capacity. Report budget đếm bằng
bounded byte sink trước persistence; không truncate diagnostics/manifest để lọt budget.
Budget/operational validation-conversion rejection 503, không giả INVALID input;
export/upload size overflow 413. Conversion artifacts/decoder/token/occurrence budgets vẫn áp dụng khi replay.

JSON dùng shared ApiResponse + error {status,code,message,path,requestId,details}.
Không raw success DTO convention cũ, raw values/PII/path/SQL/secret logs.
Anonymous 401, CUSTOMER 403 trước disclosure; missing provider/multiple-provider fail closed,
không fallback role header/localStorage. Public attachments vẫn raw bytes, không envelope.

## 9. Admin UI và request lifecycle

/admin/datasets: chọn kỳ bằng timezone tường minh, export raw Blob, upload File với receipt
và link snapshot; /admin/datasets/:snapshotId GET persisted detail khi load/reload.
Validate response binding/schema/UUID/exact count strings, không dựng detail từ receipt.
Buttons/status phân biệt raw available, validation usable subset và conversion computed.
Shared Auth + ADMIN; unknown private report fields bị loại.

Không automatic POST khi mount/tab change hoặc transport uncertainty. Validation POST reconcile
GET report; conversion POST reconcile verified GET rồi refresh snapshot metadata.
Immediate request lock/consumed-POST guard ngăn double submission; RUNNING poll sequential 2s,
budget 60s rồi manual refresh. FAILED không retry/takeover. Abort client không cancel server work.
Snapshot/raw/session change hoặc unmount abort waiting/reads/downloads, ignore late results,
clear protected content. Hidden tab panels giữ state/locks; tab URL không replay POST.

Client tự dựng canonical authenticated artifact URLs, không follow arbitrary response link.
Downloads validate MIME/filename/length, dùng Blob URL và revoke đúng lifecycle.
SPMF preview explicit <=1 MiB, bounded stream (kể cả Content-Length sai/thiếu), không unbounded
Blob fallback; tối đa 20 complete sequence lines/4000 chars, không cắt sequence thành output giả.
Nếu first sequence không fit thì yêu cầu download; render escaped text, không HTML/parser JS.
UI hiển thị server hashes/stats, không tuyên bố browser đã verify checksum hoặc tính mining weights.
Guide/account/receipt/detail responsive là presentation; trạng thái mining bước sau theo WDM contract
hiện hành, không giữ mô tả cũ “mining chưa triển khai”.

## 10. Verification, fixtures và giới hạn

Các số liệu sau giữ từ hồ sơ trước, không phải kết quả vừa chạy lại:

| Mốc | Bằng chứng đã ghi nhận |
|---|---|
| CSV contract | verify_contract.py 6/6; export repository 10 integration cases PASS |
| WDM-01.5 acquisition | Backend focused 172 PASS; original bytes/checksum/rollback/uncertain commit |
| WDM-01.7 | Backend 183/core 1182 PASS; frontend 182 PASS +2 existing skips |
| WDM-01.8 | Dataset actual Edge/HTTPS/shared Auth/MySQL 7/7 PASS; six functional ACs, owner accepted |
| WDM-02.1/02.2/02.3/02.4 | Parser 36, business 65 + lookup 4, consistency 31, cleaning 32 focused PASS; 02.2 MySQL follow-up superseded earlier Docker skips |
| WDM-02.5 | Final new engine/service/MVC/MySQL 26 PASS; final selected backend 284/core 1182 PASS; default smoke/availability excluded |
| WDM-02.6/02.7 | UI frontend 244 PASS +2 skips; gate backend focused 59, selected backend 327/core 1182 PASS; Dataset browser 12/12 PASS |
| WDM-03.1/03.2/03.3 | Focused 27/43/56 PASS; selected backend 542/558/571, core 1182 PASS |
| WDM-03.5 | Pipeline 71 PASS; selected backend 610/core 1182 PASS |
| WDM-03.6 | Shared Auth/HTTP pipeline 77 PASS; selected backend 626/core 1182 PASS; browser 12/12 PASS |
| WDM-03.7 | Frontend 313 PASS +2 existing skips; Dataset browser 15/15 PASS |
| WDM-03.8 | Focused core parser 15/backend serializer+HTTP 14 PASS; selected backend 627/core 1182 PASS; frontend 313 +2 skips; browser 16/16 PASS |
| UI refresh sau 03.8 | Frontend 322 PASS +2 skips; types/lint/build PASS; Dataset 17/17 + Auth 12/12 PASS; visual owner acceptance riêng |

Round-trip fixtures tại [csv-v1](../../backend/src/test/resources/wdm/csv-v1):
round-trip.csv, round-trip-shuffled.csv và round-trip-expected.json được viết tay độc lập.
12 rows/7 orders/3 customers → kept 9 rows/6 orders, reject 2 rows/1 order, dedup 1.
Expected database: 3 sequences (1 single/2 multi), 6 itemsets, 3 distinct items,
8 occurrences, min/max length 2/4, average 8/3.
Original/shuffled cùng canonical artifacts trong cùng registry; không assume item IDs bắt đầu 1.

HTTP và browser downloads phải được unchanged core parser so **full ordered SID→itemsets→items**
với expected product layouts resolve qua verified mapping, không dùng output làm oracle.
Test-only SpmfRoundTripVerifier kiểm actual downloaded bytes; ba wrong expectations giữ
aggregate counts nhưng đổi SID/temporal order/itemset boundaries đều phải fail.
Giữ raw/report immutability và repeat publication không new attempt.

Historical evidence dưới .agent-workspace/phase-wdm: WDM-03.8-native-confirmed.log,
WDM-03.8-regression.log, WDM-01.8-KFuv35/RESULT.json; UI refresh:
WDM-01.8-nqBYJL/RESULT.json và UI-UX-*-reviewed.log.
Owner đã duyệt dọn workspace: phải kiểm evidence còn tồn tại trước review;
bảng lịch sử không thay raw logs nếu đã mất.

Lệnh kiểm tra lại từ repo root, dùng **datasource/storage/key cô lập**, không DB dev:

```powershell
python backend/src/test/resources/wdm/csv-v1/verify_contract.py
mvn -B -pl backend -am '-Dtest=ConversionRoundTripIntegrationTest,SpmfDatasetSerializerTest,SpmfSequenceParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -B -pl backend -am '-Dtest=*,!BackendApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
npm --prefix frontend test -- --watchAll=false --runInBand
npm --prefix frontend run build
npm --prefix frontend/test/e2e run test:dataset
```

Đọc [E2E README](../../frontend/test/e2e/README.md) để chuẩn bị dependencies/build,
browser/JDK và unique log directory trước chạy. Default-datasource BackendApplicationTests
bị loại ở selected 03.x regression, không được gọi là đã PASS. Các skip phải giải thích;
package -DskipTests là preparation, không test. Normal build PASS không chứng minh CI=true build PASS.

NOT VERIFIED trừ khi có evidence riêng: production/owner rollout, desktop Excel interaction,
cross-browser/WCAG đầy đủ, concurrent 100 MiB capacity, legacy UTC compliance toàn DB,
hostile filesystem/ACL/power-loss durability và abandoned conversion recovery.
Fault injection không phải physical outage. Scientific mining/benchmark acceptance không được
suy từ dataset gate; không tự đóng R6 hoặc bắt đầu task kế tiếp.
