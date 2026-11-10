# Documentation map

Demo ứng dụng: [Combo đủ ba loại và giới hạn lưu nháp](demo/combo-demo/README.md)
gồm CSV theo catalog hiện tại, runner DB riêng và tài khoản xem thử.

Đây là điểm vào cho tài liệu nghiên cứu. Không đọc toàn bộ `docs/research/` cho
mọi task; chọn tài liệu theo phạm vi để tránh làm đầy context bằng lịch sử không
liên quan.

## Current accepted state

- Phase B — WSPM_PreTree baseline: `COMPLETED / ACCEPTED`.
- Phase C — WSPM_BitPrune: `COMPLETED / ACCEPTED` trên positive-effective-
  threshold operational scope đã được mô tả.
- Baseline là scientific reference và được bảo vệ khỏi thay đổi hành vi âm thầm.
- BitPrune phải đồng nhất hoàn toàn với baseline về pattern identity, exact
  support và pattern count trong cấu hình so sánh được hỗ trợ.
- R6 — zero minimum support semantics: `OPEN`.
- Numeric upper-bound policy: an toàn tương đối với frozen Java `double`
  predicate; exact-real equivalence chưa được chứng minh.

Backend/frontend integration trên nhánh hiện tại không thay đổi trạng thái hay
proof obligations của hai thuật toán.

## Reading routes

### Sửa hoặc đánh giá WSPM_PreTree

1. [WSPM_PreTree research contract](research/wspm-pretree-spec.md)
2. [PRISM / Prime-block contract](research/prism-prime-block.md)
3. [Phase B reproducibility manifest](research/phase-b-reproducibility.md)
4. [Phase B final acceptance](research/phase-b-final-acceptance.md)

Đọc thêm baseline/performance audits chỉ khi task liên quan đến provenance,
dataset reproduction hoặc measurement history.

### Sửa hoặc đánh giá WSPM_BitPrune

1. [Semantic contract](research/wspm-bitprune-c01-1-semantic-contract.md)
2. [Pruning-safety contract](research/wspm-bitprune-c01-2-pruning-safety.md)
3. [Architecture/statistics contract](research/wspm-bitprune-c02-architecture-contract.md)
4. [Final acceptance report](research/wspm-bitprune-c16-final-acceptance-report.md)
5. [Independent closure review](research/wspm-bitprune-c16-independent-review-report.md)

Chỉ đọc báo cáo C11–C15 khi cần trace bằng chứng chi tiết cho differential,
statistics, output retention hoặc standalone runner.

### Sửa backend, database hoặc frontend

1. [Repository README](../README.md)
2. [Architecture map](../ARCHITECTURE.md)
3. Cấu hình và test của module liên quan

Không cần nạp toàn bộ research contracts nếu task không thay đổi mining
semantics hoặc kết quả thuật toán.

## Active research documents

Auth local setup, step-by-step manual tests, SQL provisioning, shared API contract,
verification history and the proposed Auth/refactor versus WDM-01 commit boundary
are consolidated in [Shared Auth](auth/README.md). AUTH-01–06 owner acceptance and
refactor automated evidence are recorded there; owner manual testing after the
refactor is pending. The three older api-refactor documents were consolidated
and removed with explicit owner approval; historical evidence/logs are retained.
Mining contracts are unchanged.

### Baseline contracts and acceptance

| File | Vai trò |
|---|---|
| `wspm-pretree-spec.md` | Định nghĩa toán học, Table 1 oracle, baseline behavior |
| `prism-prime-block.md` | Prime-block representation và join contract |
| `phase-b-reproducibility.md` | Môi trường, lệnh và artefact tái lập |
| `phase-b-final-acceptance.md` | Quyết định đóng Phase B |

### BitPrune contracts and acceptance

| File | Vai trò |
|---|---|
| `wspm-bitprune-c01-1-semantic-contract.md` | Ngữ nghĩa canonical |
| `wspm-bitprune-c01-2-pruning-safety.md` | Proof obligations và false-negative safety |
| `wspm-bitprune-c02-architecture-contract.md` | API, module, statistics và measurement contract |
| `wspm-bitprune-c16-final-acceptance-report.md` | Final Phase C evidence |
| `wspm-bitprune-c16-independent-review-report.md` | Independent closure decision |

Các Markdown khác trong `research/` hiện là bằng chứng chi tiết hoặc audit lịch
sử. Chúng được giữ để bảo toàn scientific traceability nhưng không phải context
mặc định.

## Historical roadmaps

- [Phase B execution roadmap](archive/roadmaps/PHASE_B_WSPM_PRETREE.md)
- [Phase C execution roadmap](archive/roadmaps/PHASE_C_WSPM_BitPrune.md)

Roadmap giữ nguyên các trạng thái trung gian tại thời điểm thực hiện. Không dùng
các câu như `NOT STARTED` hay `IN REVIEW` bên trong roadmap để suy ra trạng thái
hiện tại; dùng mục “Current accepted state” ở đầu file này và báo cáo C16.

Một số research report cũ ghi đường dẫn roadmap tại root và hash của phiên bản
trước khi archive. Các dòng đó là historical evidence, không phải broken current
configuration và không được sửa chỉ để đổi đường dẫn.

## Locally acquired sources

Các paper, proposal, bản thảo DOCX và dataset lớn có thể tồn tại local dưới
`docs/` hoặc `mining-core/src/main/resources/datasets/` nhưng bị `.gitignore`
loại trừ. Khi task yêu cầu một nguồn chưa có trong clone:

1. báo rõ nguồn đang thiếu;
2. không suy đoán công thức;
3. không commit tài liệu có hạn chế bản quyền hoặc thông tin cá nhân nếu chưa
   được phép;
4. ghi lại provenance/checksum nếu tài liệu được dùng cho thực nghiệm.

## Web Data & Mining contracts

- [Client combo contract](wdm/client-combo-contract.md): HISTORY_PREFIX_V1,
  manual publication, public discovery, authenticated next-step recommendations,
  exact order/itemset matching and client acceptance evidence. This application
  policy does not alter mining support, weights or the frozen algorithms.

- [Hướng dẫn combo ba sản phẩm](wdm/combo-three-items-guide.md): kiểm tra bước
  công bố G502 → Bose, chuẩn bị CSV synthetic theo catalog hiện có và thực hiện
  đủ ba trường hợp cùng đơn, theo thứ tự, kết hợp mà không seed kết quả mining.

- [Persistence schema audit](wdm/persistence-schema-audit.md): WDM-06.1
  inventories all 52 columns, keys and constraints of the V2/V3 run/pattern/item
  statistics boundary, identifies missing writers/mappings and publication
  decisions, and records isolated MySQL/Flyway/Hibernate verification. It does
  not implement WDM-06 durable result persistence or approve a new migration.

- [Mining engine boundary](wdm/mining-engine-contract.md): WDM-05.1 synchronous
  Java request/result contract, public core APIs, typed statistics, retention and
  measurement ownership; WDM-05.2 adds the concrete adapter, preflight checks,
  engine dispatch and sampler lifecycle. WDM-05.4 calls this adapter from an
  opt-in bounded executor; WDM-05.5 integrates fenced DB lifecycle/recovery
  outside the adapter. Durable pattern persistence remains later work.

- [Mining run request contract](wdm/mining-request-contract.md): WDM-04.1's
  four-field input plus accepted WDM-04 resolver/preview/gate evidence. WDM-06.2
  adds V4 provenance/publication metadata, atomic creation, pre-engine binding
  verification and small retained handoff context; production pattern publication
  remains later work and owner V4 deployment is separate. WDM-06.3 adds an explicit
  bounded PATTERN writer/readback seam, verified item-weight context and the
  owner-selected arithmetic-mean policy fixture. It has no automatic execution
  consumer, complete-publication marker or acknowledgement. WDM-06.4 adds an
  explicit atomic parent/temporal seam with strict structural readback and
  parent-only staging compatibility. WDM-06.5 adds atomic three-table staging
  with verified core item statistics and first-successful-persistence catalog
  name/SKU snapshots preserved on retry. Complete publication and automatic
  handoff remain subsequent tasks. WDM-06.6 verifies the owner-selected weight
  policy/lineage. WDM-06.7 adds ADMIN result metadata, bounded parent pages,
  temporal detail and versioned JSON export over explicitly requested staging;
  default published reads remain blocked pending a supported manifest verifier.
  Legacy metadata remains unverified, and no automatic persistence/publication
  or acknowledgement is implied. See the WDM-06.7 contract and execution evidence.
  WDM-06.8 reuses the history/detail workspace, adds direct snapshot filtering,
  labels engine counts separately, and displays 06.7 result metadata/provenance
  with explicit staging/legacy/unsupported-publication semantics. It adds no
  pattern browsing/export UI or automatic handoff. See its verification section
  in the same contract. WDM-06.9 adds the staging-only Pattern Explorer on
  /admin/patterns with run-bound server pagination, canonical itemset boundaries,
  separate support/weight fields and exact decimal IDs/counts. Metadata-gated
  navigation preserves history context. Detail/export/Combo and complete
  publication remain separate; see the 06.9 evidence in the same contract.
  WDM-06.10 adds guarded pattern detail with ordered itemset/item rendering,
  historical name/SKU and item statistics, response-bound provenance, local
  display pagination and Explorer/history return context. It remains staging
  inspection; export/Combo/publication are separate. See the 06.10 section.
  WDM-06.11 adds opt-in atomic complete-result publication, manifest V1 verification,
  acknowledgement after commit, published reads/export V2 and a Java Combo input
  provider. Published UI navigation/download retains staging inspection. Empty
  results are publishable but not Combo eligible; actual Combo generation remains
  separate. See the 06.11 contract and execution evidence.
  WDM-05.3
  adds POST run creation, verified preparation and V2 transaction/admission
  protocol. WDM-05.4 adds opt-in real managed execution and bounded completion
  retention; default disabled mode remains 503/no insertion. WDM-05.5 adds CAS
  transitions, safe failures, V3 ownership/lease and conservative recovery.
  WDM-05.6 adds worker/input timing capture and atomic success+metrics persistence,
  preserving native counters/algorithm sampled JVM heap. Durable patterns
  remain later-task obligations. WDM-05.7 implements the ADMIN GET
  status/config/metrics snapshot with exact BIGINT strings and safe diagnostics;
  its evidence and the separate owner V3 verification are in the same contract.
  WDM-05.8 adds explicit create, guarded detail/open-by-ID, bounded GET polling
  and safe uncertain-outcome handling in the React Admin UI. Browser smoke is
  UI with mocked HTTP; full engine/browser/API/DB verification remains 05.10.
  WDM-05.9 renders counts, six timing scopes and sampled heap from the same
  snapshot, preserving exact strings/nulls and current-vs-legacy provenance.
  WDM-05.10 adds the real built-UI/Auth/API/MySQL/Flyway engine gate. A test-only
  same-JVM observer captures original full maps for comparison with direct
  PreTree/BitPrune and the independent tiny-data oracle. See the execution
  evidence/AC table in the request contract and reproduction commands in
  [browser E2E README](../frontend/test/e2e/README.md). This is not WDM-06 durable
  pattern persistence or a performance benchmark.
  The authorized workspace follow-up adds server-backed newest-first snapshot/
  run lists, current backend file locations and instance-local capacity, with a
  clearer create/history UI. It preserves the WDM-06 persistence boundary and
  explicitly labels historical publication information as unverified. See the
  workspace section in the request contract for API/read semantics and evidence.

- [Dataset/CSV contract v1](wdm/dataset-csv-contract.md): WDM-01.1 columns,
  historical sources, period/status/time semantics, raw snapshot lifecycle and
  executable synthetic fixtures. This is a contract; export/upload/validation
  endpoints and DB/Auth integration require their own later-task evidence.

## Database documents

`database/schema_v3_draft_review.sql` là đề xuất schema để review, không phải
migration đã được phê duyệt và không được Docker/Spring tự động áp dụng.

`database/laptop-combo-system-redesign.md` là phân tích hiện trạng và đề xuất
thiết kế lại database/API/frontend cho phạm vi cửa hàng laptop. Phần schema đã
được hiện thực bằng Flyway V2; các phần API/frontend và application flow vẫn phải
được đánh giá theo code/test thực tế. Tài liệu không thay đổi mining semantics.

`database/laptop-combo-data-dictionary.md` là từ điển toàn bộ bảng/trường của
schema đích, gồm tài khoản, catalog, kho/nhập hàng, giao dịch, cart/checkout,
payment, mining và combo. Flyway V2 hiện thực schema 45 bảng từ tài liệu này;
việc một bảng có trong schema không chứng minh backend/frontend đã triển khai
nghiệp vụ tương ứng.

`database/laptop-combo-seed-data-plan.md` phân loại bảng nào cần reference seed,
fixture giao dịch, dữ liệu sinh từ pipeline hoặc chỉ phát sinh runtime; tài liệu
ngăn việc seed thủ công pattern/combo làm sai bằng chứng khóa luận.

`database/V2__complete_laptop_combo_schema.sql` là bản tham chiếu tài liệu của
Flyway V2 chuyển V1 sang schema 45 bảng. Bản runtime nằm tại
`../backend/src/main/resources/db/migration/V2__complete_laptop_combo_schema.sql`
và được Flyway quét. Kiểm tra read-only database local ngày 2026-11-03 xác nhận
V2 có `success = 1`, checksum `727165286` khớp file runtime, đủ 45 bảng V2 và
không còn bảng `LEGACY_V1_*`. Kết quả này không chứng minh trạng thái database
khác hoặc tính đúng của toàn bộ dữ liệu nghiệp vụ. Không sửa file V2 runtime đã
áp dụng vì sẽ làm thay đổi checksum; mọi correction phải đi qua migration phiên
bản kế tiếp.

WDM-05.7 verification on 2026-11-07 found local `127.0.0.1:3307/wspm_combo`
already at V3 SUCCESS/checksum `1697070581`. Actual Flyway validate/migrate
(target 3)/validate passed, executing zero new migrations; V1/V2 checksums
remain unchanged. This updates the local environment evidence only.

## Documentation maintenance

- Cập nhật `../README.md` khi lệnh chạy hoặc prerequisite thay đổi.
- Cập nhật `../ARCHITECTURE.md` khi module/dependency/port thay đổi.
- Cập nhật file này khi current accepted state hoặc canonical document thay đổi.
- Không ghi kết quả benchmark mới nếu chưa có lệnh, môi trường và bằng chứng tái
  lập.
- Không xoá acceptance evidence chỉ vì Git history còn tồn tại; archive trước,
  rồi review link và hash manifest.
