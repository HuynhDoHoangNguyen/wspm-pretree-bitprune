# Documentation map

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

## Database documents

`database/schema_v3_draft_review.sql` là đề xuất schema để review, không phải
migration đã được phê duyệt và không được Docker/Spring tự động áp dụng.

`database/laptop-combo-system-redesign.md` là phân tích hiện trạng và đề xuất
thiết kế lại database/API/frontend cho phạm vi cửa hàng laptop. Tài liệu này cũng
là proposal để review, chưa phải migration và không thay đổi mining semantics.

`database/laptop-combo-data-dictionary.md` là từ điển toàn bộ bảng/trường của
schema đích, gồm tài khoản, catalog, kho/nhập hàng, giao dịch, cart/checkout,
payment, mining và combo. Đây vẫn là logical/physical proposal, chưa phải migration.

`database/laptop-combo-seed-data-plan.md` phân loại bảng nào cần reference seed,
fixture giao dịch, dữ liệu sinh từ pipeline hoặc chỉ phát sinh runtime; tài liệu
ngăn việc seed thủ công pattern/combo làm sai bằng chứng khóa luận.

`database/V2__complete_laptop_combo_schema.sql` là một Flyway V2 review draft duy
nhất để chuyển V1 sang schema 45 bảng. File được giữ ngoài classpath migration để
đáp ứng yêu cầu chưa chạy; chỉ chuyển vào backend sau khi entity/DTO/test sẵn sàng.

## Documentation maintenance

- Cập nhật `../README.md` khi lệnh chạy hoặc prerequisite thay đổi.
- Cập nhật `../ARCHITECTURE.md` khi module/dependency/port thay đổi.
- Cập nhật file này khi current accepted state hoặc canonical document thay đổi.
- Không ghi kết quả benchmark mới nếu chưa có lệnh, môi trường và bằng chứng tái
  lập.
- Không xoá acceptance evidence chỉ vì Git history còn tồn tại; archive trước,
  rồi review link và hash manifest.
