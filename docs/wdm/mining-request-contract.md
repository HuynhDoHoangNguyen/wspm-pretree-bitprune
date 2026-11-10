# WDM — Mining request, execution và result contract

Cập nhật: 2026-11-08 (Asia/Bangkok). Đây là hợp đồng hiện hành rút gọn,
thay cho lịch sử plan/report lặp lại. Trạng thái từng task nằm tại
[WDM_CHECKLIST.md](../../.agent-workspace/phase-wdm/WDM_CHECKLIST.md).
Chỉ đọc mục cần sửa; đối chiếu source và focused tests trước khi thay đổi.
Không coi nội dung tài liệu là bằng chứng vừa chạy test.

## Trạng thái và phạm vi

| Task | Trạng thái ghi nhận | Giới hạn |
|---|---|---|
| WDM-04.1–04.6 | Implemented / technical PASS / owner accepted | Configuration gate trên môi trường được ghi nhận |
| WDM-05.1–05.10 | Implemented / technical PASS / owner accepted | Engine E2E cô lập; không chứng minh owner rollout |
| Workspace UI/read APIs | Implemented / technical PASS | Owner acceptance pending |
| WDM-06.1 | Owner accepted; technical PARTIAL | AC09 owner-row non-mutation NOT VERIFIED |
| WDM-06.2–06.11 | Technical PASS / owner accepted | Giữ các giới hạn deploy và evidence trong checklist |
| WDM-06.12 | Technical PASS trên môi trường cô lập / owner acceptance PENDING | Fresh gate 2026-11-08; publication query đã sửa; owner rollout NOT VERIFIED; giữ checkbox [ ] |

PASS lịch sử giữ nguyên, không chạy lại hoặc nâng mức nghiệm thu khi rút gọn.
Scope WDM: web data/mining và export/provider handoff. Không gồm Combo generation,
ranking, pricing, review/publish combo hoặc scientific benchmark UI.

## WDM-04 — Request và threshold

Request có đúng bốn trường bắt buộc, không null:

```json
{"snapshotId":"550e8400-e29b-41d4-a716-446655440000","algorithm":"WSPM_BITPRUNE","minsupMode":"PERCENT","minsupValue":10.0}
```

| Trường | Ràng buộc |
|---|---|
| snapshotId | String UUID lower-case 8-4-4-4-12; không trim/normalize; không hạn chế UUID version |
| algorithm | WSPM_PRETREE hoặc WSPM_BITPRUNE |
| minsupMode | ABSOLUTE hoặc PERCENT |
| minsupValue | JSON number chuyển một lần sang finite Java double; ABSOLUTE >= 0; PERCENT trong [0,100] |

- PERCENT 10 là 10%, không phải 0.10. ABSOLUTE có thể là số lẻ, theo đơn vị
  predicate `support(P) * MeanW >= effectiveThreshold`; không đồng nhất với integer support.
- Không nhận minSup/minWeight legacy, client-resolved threshold, path, count hoặc field lạ.
  Không aliases/defaults/coercion. Strict reader từ chối duplicate keys, trailing values,
  sai root/type, malformed JSON, NaN/Infinity. DTO direct Java callers cũng phải validate;
  constructor tự nó không chạy Bean Validation.
- Chuyển double có thể underflow về zero; signed zero được parse như zero.
  Overflow thành infinity bị từ chối. Không clamp/round/epsilon hay comparator khác.
- RunThresholdResolutionService gọi MinimumSupportResolver của core; không chép công thức
  sang backend/frontend. Giữ configured và resolved values tách biệt.
  Sequence count resolver: 0..Integer.MAX_VALUE; runnable dataset phải không rỗng.
- Preview/configuration-validation dùng cùng snapshot conversion và threshold resolver,
  không chạy engine. Gate 422 chặn effective threshold zero, signed zero/underflow và
  cấu hình ngoài operational scope trước insert/engine. Parse hợp lệ không đồng nghĩa runnable.
- R6 vẫn OPEN; ứng dụng từ chối zero không định nghĩa semantics zero cho thuật toán.

## WDM-05.3 — Run creation API

- `POST /api/v1/admin/mining-runs`: shared Auth/ADMIN trước đọc body; không query parameters.
  Thành công 202 theo ApiResponse hiện hành và relative Location sau DB commit + activation.
- Snapshot/conversion integrity được verify ngoài transaction, binding được recheck dưới lock;
  worker verify input lại trước engine. Không tin client count/path/resolved values.
- Không provider, không admission capacity hoặc DB/admission không xác định: fail closed 503;
  không tuyên bố đã tạo thành công. Không automatic POST replay hoặc cam kết idempotency.
- HTTP: 400 invalid request, 401 unauthenticated, 403 forbidden, 404 missing resource,
  409 inconsistent state/artifact, 413 resource budget, 422 unsupported configuration,
  503 unavailable. Broken JSON: MALFORMED_REQUEST; shape/type/range: VALIDATION_FAILED.
- Dùng envelope/error code hiện hành và request ID; không lộ parser exception, SQL, path,
  token hoặc raw personal data. Protected responses/downloads dùng no-store và nosniff.
  JSON attachment export không bọc ApiResponse.

## WDM-05.4–05.7 — Execution, lifecycle và metrics

- Execution opt-in `wdm.execution.enabled`, mặc định false. Bounded parallelism/queue
  và retained outcomes cùng chịu capacity accounting; không bỏ complete maps bằng TTL.
- V3 ownership: owner/attempt/lease + CAS cho QUEUED → RUNNING → SUCCEEDED/FAILED.
  DB UTC; lease 60s, heartbeat 10s, recovery batch 100; lease >= 3 × heartbeat.
  Lease không phải mining timeout. Expired owned work → FAILED/EXECUTION_LEASE_EXPIRED;
  không tự replay engine. DB uncertainty phải reconcile, terminal state không ghi đè.
- Failure categories: ADMISSION_FAILED, CLAIM_NOT_CONFIRMED, INPUT_REVERIFICATION_FAILED,
  ENGINE_EXECUTION_FAILED, EXECUTION_LEASE_EXPIRED.
- Outcome immutable chứa full maps. Snapshot execution state và outcome lifecycle confirmation
  là các mốc khác nhau; xác nhận terminal bền vững trước acknowledge đúng token.
  Publication không được suy từ engine SUCCEEDED.
- Giữ riêng Dataset I/O, preprocessing, DFS, complete algorithm, output I/O, total execution.
  Worker total không gồm queue/claim/DB publication. Floor nanos→millis riêng từng metric;
  chưa đo dùng null, không dùng 0; output_io_ms chưa đo vẫn null.
- JVM heap sampling không phải hardware RAM. Publication elapsed riêng, không so sánh
  số liệu web thành scientific performance claim.

## Mining workspace UI/read APIs

ADMIN-only list snapshots/runs: page mặc định 1, tối đa 100000; size mặc định 20,
tối đa 100; literal search tối đa 255; filters theo status/algorithm/snapshotId.
Sort createdAt DESC, UUID DESC. Count/page cùng read transaction; không hứa frozen
pagination qua nhiều HTTP requests. Storage paths chỉ là metadata quản trị hiện tại,
không chứng minh provenance lịch sử. Capacity là current JVM, không lộ tokens/maps;
execution-disabled có thể không có execution snapshot. Owner acceptance vẫn pending.

## WDM-06.2 — Run Metadata Persistence

- Không sửa V2 đã áp dụng; V3 dành execution ownership, V4 thêm RUN_PROVENANCE/
  RUN_RESULT_PUBLICATION. Không backfill legacy thành verified.
- RUN_PROVENANCE_V1 giữ conversion identity/policies, artifact references/hashes/stats,
  configured/resolved input. Không suy historical provenance từ catalog hiện tại.
- Metadata-only persistence không đồng nghĩa published result; owner V4 deployment
  không được suy từ test migration trên DB cô lập.

## WDM-06.3 — Pattern Persistence

- Persist nguyên full output map; giữ canonical identity/hash, exact support, total count,
  itemset boundaries và deterministic ordering. Không top-K, flatten hoặc sửa engine result.
- Pattern, temporal rows, all run-item statistics được compose atomically; artifact work
  ngoài DB transaction, run/parent locks và mandatory repositories trong transaction.
- Staging theo nguyên tắc insert complete set hoặc so khớp existing set rồi no-op;
  partial/conflicting rows bị từ chối, không upsert/repair âm thầm.
  Parent-only stage có thể được bổ sung complete temporal set theo boundary đã chấp thuận.
- Projection limits: 10000 patterns, 100000 temporal rows, 10000 input items;
  projection budget 8 MiB; canonical UTF-8 tối đa 65535 bytes.
  Vượt budget reject toàn bộ, không truncate.

## WDM-06.4–06.6 — Temporal rows, statistics và weight

- Temporal coordinates 1-based, contiguous; item IDs tăng trong mỗi itemset.
  Cùng item ở các itemset khác nhau phải giữ nguyên; <(a)(b)> khác <(ab)>.
- All input-item statistics gồm cả item không xuất hiện trong output patterns.
  Name/SKU được capture một lần thành historical snapshots; retry/read không refresh catalog.
- Pattern weight dùng policy ứng dụng `EQ2_EQ3_ARITHMETIC_MEAN_V1` đã được chọn;
  không biến thành mining acceptance rule mới. Giữ riêng support_abs,
  weighted_support và pattern_weight; không epsilon hoặc TS tự tính lại.
- Exact double policy giữ nguyên. Reader chấp nhận positive UINT64 IDs bằng decimal strings,
  SQL sort theo numeric ID; không dùng JS Number cho IDs/counts ngoài safe range.
  Writer signed-long boundary không chứng minh writer hỗ trợ toàn UINT64.
  Sequence count dương trong signed-int; support <= sequence count;
  temporal coordinates positive int; occurrences/counts theo ràng buộc DTO hiện hành.

## WDM-06.7 — Result Query APIs

- ADMIN routes dưới `/api/v1/admin/mining-runs/{runId}`: metadata, patterns,
  pattern detail và export theo controller hiện hành. Unknown/repeated query keys reject.
- Pattern list: support DESC, pattern_weight DESC, ID DESC; page/size limits như workspace.
  Count/page/detail kiểm tra cùng repeatable-read transaction; detail validate structural rows.
- Published view là backend default. Staging chỉ cho SUCCEEDED + NOT_PUBLISHED,
  completeness UNVERIFIED và không Combo eligible.
- Legacy thiếu cả metadata/provenance không được nâng thành verified; malformed/partial
  metadata reject 409. Unknown manifest không được đọc như supported published data.
- Supported COMMITTED manifest phải verify digest, policy và row counts trong cùng read
  transaction rồi mới trả COMPLETE_VERIFIED/published=true.
  comboEligible chỉ khi verified published và patternCount > 0.
- Empty output vẫn complete/exportable với all input-item stats; không eligible cho Combo.
  Read không sửa/repair result, không chạy engine.

## WDM-06.8–06.10 — Admin UI

- Routes: `/admin/mining-runs` (history tab), `/admin/patterns?runId&page&view`,
  `/admin/patterns/:runId/:patternId?page&view`.
- Explorer/detail giữ run/page/view khi điều hướng. Old bookmarks không view giữ staging
  compatibility; published view phải được truyền xuyên suốt.
- Detail render local pages 10 itemsets/50 items; canonical preview 512 chars,
  copy dùng full canonical. Không suy engine counts từ số đang render.
- Response phải bind đúng session/token/run; abort/ignore late responses, clear protected
  data khi session/role mất. Không dùng role từ localStorage làm bằng chứng phân quyền.
- UI hiển thị execution, publication và completeness riêng; không gọi staging là handoff.

## WDM-06.11 — Export & Combo Handoff

### Publication và retained outcome

- Opt-in `WDM_RESULT_PUBLICATION_ENABLED=false` mặc định; cần execution enabled.
  Một consumer worker, initial delay 1s rồi 2s; 5 transient attempts,
  backoff 4/8/16/32s. Permanent failure giữ blocked token.
- Java-only retryRetained yêu cầu đúng token; không thêm HTTP retry route.
  Shutdown drain current publication, không bắt đầu job tiếp theo.
  Failed engine outcomes giữ lifecycle cũ. Chưa committed outcome mất theo JVM restart
  vì chưa có durable outcome queue; không hứa restart sẽ tự replay engine.
- Prepare/verify projection ngoài TX. Publication dùng READ_COMMITTED,
  REQUIRES_NEW, timeout 30s, lock run rồi marker; all three result tables + verify +
  COMMITTED marker atomically. COMMITTED retry phải so full retained outcome;
  không ghi lại rows hoặc refresh snapshots.
- Commit xác nhận xong mới acknowledge đúng outcome. Commit/ack uncertainty:
  giữ và reconcile/retry, không tự coi success hoặc discard.
- Publication elapsed là monotonic time qua preparation/readback trước marker,
  loại commit/ack; retry giữ persisted elapsed, không thay engine metrics.

### Manifest và bounded read/export

- `WDM_RESULT_MANIFEST_V1`: SHA-256 trên typed canonical binary của ordered run binding,
  provenance, parent patterns, temporal rows, all item snapshots; gồm DB IDs và name/SKU.
  Sort canonical-hash parents, hash/position temporal rows, numeric item IDs.
  Không hash publication marker/timestamps/timing.
- Codec: null tag0; string tag1+8-byte length+UTF-8; raw-double tag2;
  integer tag3+decimal string; booleans tag4/5; sorted-key map tag6; ordered list tag7.
  Reject nonfinite floats; JSON map key order không đổi digest.
- resultSha256 là stored-result attestation phía server, không phải checksum tự tính
  từ public export JSON (thiếu internal provenance references).
  Không hứa chống coherent rewrite cả DB data lẫn manifest.
- Reader transaction timeout 15s, soft processing deadline 10s; export mặc định 16 MiB,
  cấu hình 256 bytes..64 MiB; bounded row limits, parent/item allocation budgets
  12 MiB mỗi nhóm, provenance 65536 bytes. Chuẩn bị toàn bộ payload trước response:
  vượt giới hạn reject, không partial attachment/CSV/truncation.

### Export và provider

| Version/purpose | Nội dung và điều kiện |
|---|---|
| V1 / STAGING_INSPECTION | Metadata + patterns; phục vụ inspection, không Combo eligibility |
| V2 / COMBO_HANDOFF | Verified published metadata + all patterns + all runItems |

Combo provider Java trả immutable published bundle; reject staging, corrupted/unknown
manifest và empty output. Không tạo Combo DB rows, ranking hoặc pricing.
Export/UI/provider dùng cùng validated published snapshot và metadata binding.

## WDM-06.12 — Final Result E2E: isolated verification and owner review

Owner yêu cầu làm lại 06.12 ngày 2026-11-08; lượt này không dùng PASS/evidence cũ
để nghiệm thu và không xóa evidence có sẵn. Technical verification PASS trên
package/Edge/Auth/MySQL cô lập; owner acceptance PENDING, checkbox giữ [ ].
Toàn bộ inventory, commands/exit codes, failure attempts, AC mapping và giới hạn:
[RESULT mới](../../.agent-workspace/phase-wdm/WDM-06.12-publication-fix-06a8db110b/RESULT.json).
Raw browser evidence nằm tại
[WDM-06.12-6RUscC](../../.agent-workspace/phase-wdm/WDM-06.12-6RUscC/RESULT.json).

| AC | Điều phải chứng minh | Kết quả kỹ thuật / bằng chứng mới |
|---|---|---|
| AC01 | Isolated real Auth/Flyway/Hibernate; exact packaged core/sources; helpers ngoài production JAR | PASS — PREPARATION.json, FLYWAY.tsv, server logs; source inventory unchanged, packaged/direct core SHA-256 bằng nhau |
| AC02 | Hai engine × ABSOLUTE 0.1 / PERCENT 14 / PERCENT 100, actual UI-created runs | PASS — RESULT.runs: sáu run UI/HTTP202; mỗi engine lần lượt 15 / 3 / 0 patterns |
| AC03 | Full maps/counts original/direct baseline/direct BitPrune/oracle/DB/API/UI/export bằng nhau | PASS — mười *-ORACLE.log và 30 comparisons (initial gồm numeric-item-order, restart, execution-disabled); provider bundle cũng khớp |
| AC04 | Temporal boundaries/repeats, all item stats, policy, configured/resolved raw-double binding/provenance | PASS — original-*.properties/spmf/map.tsv, *-binding.json và toàn bộ DB temporal/statistics/detail comparisons |
| AC05 | Bounded >20-pattern fixture; mọi API/UI page và detail, không chỉ sample/count | PASS — mỗi engine có 28 patterns / 2 pages; tất cả details được duyệt trên desktop/mobile; fixture core test PASS |
| AC06 | Verified publication, correct ack/capacity recycling; execution và publication độc lập | PASS — mười run matrix liên tiếp, committed/ack logs, capacity.used=0; focused consumer tests và staging control |
| AC07 | Empty output; restart + execution-disabled reads/export/provider; không engine replay/result writes | PASS — hai empty runs, hai fresh JVMs, CALLS không xuất hiện khi đọc; hashes mining/result/Combo tables không đổi; historical snapshots giữ nguyên |
| AC08 | Real 401/403/ADMIN; safe errors/request IDs; session/run changes và downloads | PASS — bốn route families × real roles; ba CDP-throttled real 200 exports bị hủy khi đổi run/logout/mất quyền; security screenshots và frontend regression |
| AC09 | DB corruption và copied-artifact negative controls fail đúng layer; first differing pattern | PASS — 8 DB + 7 copied-artifact controls; first pattern + expected/actual support trong RESULT; unsupported manifest metadata được giữ UNVERIFIED, published consumption bị chặn |
| AC10 | Whole-export budgets, exact numeric serialization; zero/signed-zero/underflow chặn trước engine/insert | PASS — actual HTTP413 không attachment; 422/no insert/no engine entry; RunResultQueryServiceTest và frontend numeric tests |
| AC11 | Fresh full core/backend, frontend regression/type/lint/build; mọi skip có lý do | PASS — core 1186 / backend 1374, fail/error/skip=0; frontend 650 PASS, 2 live tests skipped do thiếu opt-in URLs; tsc/lint/build/package exit 0 |
| AC12 | Final traceability/evidence matrix, scoped file inventory và owner acceptance boundary | PASS — RESULT tổng hợp, STARTING/FINAL status, REVIEW.diff và REVIEW-SOURCES.zip; owner acceptance PENDING |

Các tên evidence ngắn trong bảng nằm dưới WDM-06.12-6RUscC, trừ frontend/focused/
build/inventory logs được link từ RESULT tổng hợp. Frontend/type/lint/build được giữ
từ lượt trước cùng task vì hash toàn bộ 621 frontend source files không đổi.
Không cộng focused counts vào full regression. Browser có 46 nhóm kiểm tra,
30 full-layer comparisons và 336 lượt detail UI; các số này không phải số test
JUnit/Jest độc lập. JAR mới được package riêng và đã chạy qua gate; source inventory
không đổi trong gate.

UI RunResultMetadataPanel nay làm mới GET mỗi 2 giây trong lúc chờ publication,
dừng khi published hoặc gặp lỗi và dùng cùng cơ chế abort/session/visibility hiện
có. Regression xác nhận nút xem published xuất hiện khi metadata chuyển trạng thái.
Không thay đổi production mining-core, migration hoặc nghiệp vụ Combo.

**Lỗi publication đã xác định và sửa trong source:** owner log báo
`INVALID_RESOURCE_STATE` dù engine tạo 40 patterns. Truy vấn snapshot có
`CAST(item_id AS CHAR) AS item_id ... ORDER BY item_id`; ORDER BY lấy alias chuỗi,
xếp 1,10,...,2, làm digest khác projection sắp theo số và rollback publication.
Sửa hẹp thành `ORDER BY s.item_id` dùng cột số, giữ nguyên manifest và comparator.
Quy tắc alias được mô tả trong [MySQL SELECT](https://dev.mysql.com/doc/refman/8.4/en/select.html).

Regression MySQL với mười sequence độc lập, mỗi sequence một item, fail trước sửa
(`NUMERIC-ORDER-RED.log`) và PASS sau sửa (`NUMERIC-ORDER-GREEN.log`, cả hai engine).
Toàn bộ publication integration class: 14 PASS. Gate thêm hai run UI với item IDs
7–16, mỗi run 10 patterns; cả 30 full-layer comparisons đều khớp. Không seed mined output.

**Owner rollout NOT VERIFIED:** hai run local
`e5bb7283-dd2d-4134-ac33-fbedbbee286c` và `75c783c7-5053-437f-828c-a20c78998f61`
chỉ được đọc để chẩn đoán; không restart owner process, ghi owner DB hoặc migrate.
Không coi isolated PASS là bằng chứng các run cũ đã được công bố/full-map verified.
Outcome giữ trong RAM không được khôi phục qua restart; sau khi owner tự dừng backend
cũ, chạy từ repository root và tạo run MỚI với cùng snapshot/config:

```powershell
java -jar ".agent-workspace/phase-wdm/WDM-06.12-publication-fix-06a8db110b/backend-build/backend-1.0-SNAPSHOT.jar" --spring.profiles.active=local
```

Không cần migration cho bản sửa truy vấn này. Default target repackage không rename
được artifact đang dùng; dùng JAR riêng đã package thành công và được gate kiểm tra.
Lượt gate `WDM-06.12-WRUFNB` bị VS Code shutdown giữa Maven nên không tính PASS;
đã chạy lại toàn bộ bằng gate `WDM-06.12-6RUscC`, exit 0.

Review phải bind actual original engine capture trước consumer ack với đúng run/input hash,
không dựng original metadata từ DB. Giữ independently runnable 05.10/06.11 gates.
Sáu tiny cases và multipage fixture không được seed hand-authored mined output.
Restart phải chứng minh manifest/content giữ nguyên, không live Outcome/engine replay;
shared Auth session writes nằm ngoài assertion mining/result read-only.

Negative controls cần cover support, temporal boundary/order, add/remove/duplicate row,
snapshot/provenance/manifest mutation, wrong run binding và one-ULP metadata mismatch.
Tách DB manifest rejection, DTO structure checking và independent oracle checking;
không tuyên bố public JSON chứng minh internal manifest.
Dừng ngay khi full map sai; không sửa threshold/epsilon để test xanh.
Owner deployment không thuộc bằng chứng cô lập và vẫn NOT VERIFIED.

## Research fidelity và cách tiếp tục

Frozen baseline/BitPrune giữ nguyên pattern identity + exact support + count.
R6 và exact-real comparator equivalence còn OPEN; Java double baseline predicate
không thay đổi. Application contracts không thay thế authoritative research sources.
Không scientific performance claim từ test logs/web metrics.

Đọc checklist để chọn task được yêu cầu; khi làm task mới chỉ mở controller/service/
repository/UI/test trực tiếp liên quan. Khi nghiệm thu ghi rõ AC PASS/PARTIAL/NOT VERIFIED,
lệnh/log/phạm vi môi trường và owner acceptance. Không append lịch sử plan hoặc raw logs.
Bằng chứng cũ bị xóa sẽ không còn kiểm tra lại được; tóm tắt này không thay thế raw evidence.
