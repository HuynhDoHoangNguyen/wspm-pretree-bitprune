# WSPM_BitPrune — Module Boundaries & API/Statistics Contract (Task C02)
## Đặc Tả Kiến Trúc, Ranh Giới Module, Hợp Đồng Giao Diện và Thống Kê

**Repository:** `wspm-pretree-bitprune`  
**Phase:** C — WSPM_BitPrune  
**Task ID:** C02  
**Parent Task:** Phase C Gate 0 (Baseline Handoff & Research Design)  
**Deliverable:** `docs/research/wspm-bitprune-c02-architecture-contract.md`  
**Vai trò:** Research Architect, Java 21 Architect, Algorithm Correctness Reviewer  
**Ngôn ngữ tài liệu:** Tiếng Việt  
**Trạng thái đề xuất:** PENDING REVIEWER AUDIT (Chờ Research Reviewer thẩm định nghiệm thu)

---

## 1. Task Identity and Scope (Định Danh và Phạm Vi)

### 1.1. Bối cảnh nghiên cứu và vị trí trong lộ trình
- **Khóa luận tốt nghiệp:** “Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây dựng hệ thống gợi ý combo sản phẩm”.
- **Mục tiêu Phase C:** Nghiên cứu và triển khai thuật toán cải tiến **WSPM_BitPrune** dựa trên cấu trúc Bitmap, chỉ mục vị trí xuất hiện (Vertical Occurrence Index), ma trận đồng xuất hiện hai chiều (CMAP-I và CMAP-S), cơ chế cắt tỉa trễ đa tầng (Multi-tier Lazy Pruning) và xác minh thời gian chính xác (Exact Temporal Verification).
- **Trạng thái tiên quyết:**
  - C00 (Handoff & Readiness Audit): `ACCEPTED`.
  - C01.1 (Semantic Contract): `ACCEPTED`.
  - C01.2 (Pruning Safety Proofs & Test Design): `ACCEPTED`.
  - C01 (Parent Task): `ACCEPTED`.
  - C02: `AUTHORIZED` và đang thực hiện thiết kế kiến trúc.

### 1.2. Mục tiêu duy nhất của C02
1. Thiết lập ranh giới module rõ ràng, bảo vệ tuyệt đối tính đóng băng của baseline WSPM_PreTree (Phase B) và cách ly hoàn toàn WSPM_BitPrune khỏi nội bộ mã nguồn PRISM Prime-block (`vn.edu.wspm.prism.*`).
2. Xác định cấu trúc package và đề xuất hợp đồng API định kiểu tĩnh (`PROPOSED API`) cho các thành phần cốt lõi: SID Mapping, SID Bitmap, Vertical Occurrence Index, CMAP-I/CMAP-S, Exact Temporal Verifier, DFS Miner, Result và Statistics.
3. Thiết lập hợp đồng sở hữu và vòng đời đối tượng (Ownership & Lifecycle Contract) cho luồng duyệt DFS nhằm tối ưu hóa bộ nhớ và ngăn chặn rò rỉ trạng thái.
4. Chuẩn hóa hợp đồng đo lường thời gian (6 scope đo lường thời gian chuẩn hóa kế thừa Phase B: Dataset I/O, Preprocessing, DFS Mining, Complete Algorithm, Output I/O, Total Execution; trong đó Total Execution loại trừ hoàn toàn console printing) và bộ nhớ (MemoryMXBean heap sampling) kế thừa chuẩn mực Phase B để phục vụ so sánh công bằng.
5. Thiết lập ma trận an toàn số học (Numeric Safety Decision Matrix), ma trận thống kê và các bất biến hạch toán ứng viên (Statistics Accounting Invariants) có khả năng tự kiểm chứng.
6. Thiết lập chiến lược kiểm thử tính đúng đắn vi sai (Differential Testing Strategy) và ma trận truy vết (Traceability Matrix) từ các nghĩa vụ chứng minh OBL-01 đến OBL-08.
7. Lập ma trận bàn giao chi tiết cho từng task triển khai từ C03 đến C16.

### 1.3. Ranh giới và Giới hạn Tuyệt đối (Scope Boundaries & Prohibitions)
- **Được phép trong C02:**
  - Khảo sát mã nguồn, tài liệu nghiên cứu, và các kết quả đã được nghiệm thu của Phase B và C01.
  - Phân loại API Phase B thành: Reusable (tái sử dụng), Protected (bảo vệ nguyên vẹn), Forbidden (cấm phụ thuộc).
  - Đề xuất giao diện, kiểu dữ liệu, phương thức và hành vi lỗi (đánh dấu rõ ràng là `PROPOSED API`).
  - Tạo mới duy nhất tệp deliverable: `docs/research/wspm-bitprune-c02-architecture-contract.md`.
- **Tuyệt đối cấm trong C02:**
  - Không viết hoặc chỉnh sửa bất kỳ dòng mã nguồn Java nào (`src/main/java`).
  - Không viết hoặc chỉnh sửa bất kỳ bài kiểm thử JUnit nào (`src/test/java`).
  - Không triển khai Bitmap, CMAP, Positional Index, hay DFS Miner.
  - Không chỉnh sửa tệp lộ trình `PHASE_C_WSPM_BitPrune.md`, `pom.xml`, `.gitignore`, `README.md`, hay tài liệu Phase B.
  - Không chỉnh sửa tài liệu C01.1 (`docs/research/wspm-bitprune-c01-1-semantic-contract.md`) hay C01.2 (`docs/research/wspm-bitprune-c01-2-pruning-safety.md`).
  - Không tạo phụ thuộc từ WSPM_BitPrune sang gói `vn.edu.wspm.prism.*`.
  - Không tự ý giải quyết vấn đề nghiên cứu R6 (`Zero Minimum Support Semantics` tiếp tục giữ trạng thái `OPEN`).
  - Không tự ý chốt chính sách so sánh dấu phẩy động cho Java `double` (`UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`).
  - Không thực hiện các lệnh Git làm thay đổi lịch sử (`commit`, `push`, `reset`, `clean`).
  - Không tự ý đánh dấu C02 là `ACCEPTED` hoặc chuyển sang C03.

---

## 2. Repository Readiness (Xác Minh Trạng Thái Kho Chứa)

### 2.1. Git State Verification
Tại thời điểm bắt đầu task C02, trạng thái Git thực tế được kiểm chứng độc lập:
```text
$ git status --short --branch
## feature/wspm-bitprune...origin/feature/wspm-bitprune
 M src/main/java/vn/edu/wspm/experiments/MainWspmPreTree.java
?? PHASE_C_WSPM_BitPrune.md
?? docs/research/wspm-bitprune-c01-1-semantic-contract.md
?? docs/research/wspm-bitprune-c01-2-pruning-safety.md

$ git rev-parse HEAD
502e1ddb61d37751c2c5f606fbd30e3b90aefb6c

$ git log -1 --oneline
502e1dd feat(wspm): finalize and freeze Phase B baseline
```

### 2.2. Nhận xét về tính toàn vẹn của kho chứa
1. **Freeze Commit Hash:** Commit HEAD hiện tại là `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`, khớp chính xác với điểm đóng băng Phase B (B24 Freeze SHA) được ghi nhận trong `AGENTS.md` và `PHASE_C_WSPM_BitPrune.md`.
2. **Worktree Preservation:** Các thay đổi có sẵn trên nhánh làm việc (`MainWspmPreTree.java` được sửa đổi, `PHASE_C_WSPM_BitPrune.md`, `wspm-bitprune-c01-1-semantic-contract.md`, `wspm-bitprune-c01-2-pruning-safety.md`) thuộc quyền sở hữu của chủ repository và được bảo toàn nguyên vẹn; không thực hiện bất kỳ thao tác `git clean` hay `git checkout` nào.
3. **Prerequisite Confirmation:** C00, C01.1, C01.2 và parent task C01 đã được Research Reviewer thẩm định và nghiệm thu chính thức (`ACCEPTED`).
4. **Deliverable Isolation:** Tệp `docs/research/wspm-bitprune-c02-architecture-contract.md` chưa từng tồn tại trên đĩa và được tạo mới lần đầu trong phiên làm việc này.

---

## 3. Sources Reviewed (Khảo Sát Nguồn Nghiên Cứu và Hiện Trạng Mã Nguồn)

Các nguồn tài liệu nghiên cứu và mã nguồn đã được rà soát trực tiếp trong quá trình thiết kế:
1. `AGENTS.md`: Các nguyên tắc kỹ thuật Java, tính trung thực nghiên cứu, quy định không che giấu tràn số/sai số, ranh giới baseline vs cải tiến.
2. `PHASE_C_WSPM_BitPrune.md`: Mục tiêu Phase C, danh mục task C00–C16, các bất biến an toàn cắt tỉa và yêu cầu đo lường.
3. `pom.xml`: Cấu hình Java 21, Maven Compiler Plugin, JUnit 5.10.2, không sử dụng thư viện ngoài trái phép.
4. `README.md` & `PHASE_B_WSPM_PRETREE.md`: Quy trình đóng băng Phase B, cấu trúc mã nguồn WSPM_PreTree.
5. `docs/research/wspm-bitprune-c01-1-semantic-contract.md`: Hợp đồng ngữ nghĩa chuẩn xác của WSPM_BitPrune, định nghĩa chuỗi con, support theo distinct SID, canonical I-Extension, strict-later S-Extension, Item Weight, global MeanW, weighted acceptance.
6. `docs/research/wspm-bitprune-c01-2-pruning-safety.md`: Các chứng minh an toàn OBL-01 đến OBL-08, phân tích false negative, định lý All-Endpoints (OBL-07), danh mục 6 phản ví dụ (CE-01 đến CE-06) và 13 test vector xác định.
7. `docs/research/phase-b-final-acceptance.md`, `phase-b-reproducibility.md`, `phase-b-baseline-audit.md`: Hồ sơ nghiệm thu Phase B và kết quả kiểm thử tái lập.
8. `docs/research/wspm-pretree-spec.md`, `prism-prime-block.md`, `wspm-pretree-performance-audit.md`: Đặc tả thuật toán gốc WSPM_PreTree và cơ chế Prime-block của PRISM.
9. Bài báo gốc WSPM_PreTree (2023): *“Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding”*.
10. Bản thảo WSPM_BitPrune (2026): *“Tối ưu hóa thuật toán khai phá mẫu tuần tự có trọng số bằng cấu trúc Bitmap và cơ chế cắt tỉa trễ đa tầng”*.
11. Đề cương luận văn tốt nghiệp ngành Khoa học Máy tính.
12. Mã nguồn hiện có trong các gói Java 21:
    - [`vn.edu.wspm.model`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model): `Item`, `Itemset`, `Pattern`, `PatternExtensions`, `Sequence`, `SequenceRecord`, `SequenceDatabase`.
    - [`vn.edu.wspm.config`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/config): `MinimumSupport`, `MinimumSupportMode`, `MinimumSupportResolver`, `ResolvedMinimumSupport`.
    - [`vn.edu.wspm.statistics`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/statistics): `ItemStatistics`, `ItemStatisticsCalculator`, `ItemWeightCalculator`, `ItemWeightResult`, `SequenceDatabaseStatistics`.
    - [`vn.edu.wspm.io`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/io): `SequenceDatabaseParser`, `SequenceDatabaseFormat`, `SpmfSequenceParser`, `FimiTransactionParser`.
    - [`vn.edu.wspm.tree`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/tree): `PrefixTree`, `PrefixTreeNode`, `ExtensionType`.
    - [`vn.edu.wspm.mining`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining): `WspmPreTreeMiner`, `CandidateEngine`, `SingletonEncodingBuilder`, `F1Builder`, `WeightedOnePattern`, `WspmMiningResult`, `ResultRetentionPolicy`, `WspmMiningStatistics`.
    - [`vn.edu.wspm.prism`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/prism): `PatternEncoding`, `PrimeBlockJoins`, `PrimalBlockCodec`, v.v.
    - [`vn.edu.wspm.measurement`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement): `ExecutionMemorySampler`, `PhaseExecutionMetrics`, `WspmAlgorithmMetrics`, `WspmExecutionMetrics`.
    - [`vn.edu.wspm.experiments`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/experiments): `MainWspmPreTree`, `WspmExecutionRun`.

---

## 4. Source-Fidelity Classification (Phân Loại Tính Trung Thực Nguồn Nghiên Cứu)

Mọi quyết định kiến trúc trong C02 được phân định nghiêm ngặt thành ba cấp độ nguồn:

### 4.1. SOURCE-CONFIRMED (Được xác nhận trực tiếp từ tài liệu gốc)
1. **Cấu trúc Prefix Tree:** Cây tiền tố chứa một nút gốc rỗng $\emptyset$, mỗi nút con đại diện cho một mẫu hợp lệ; mở rộng theo thứ tự I-Extension (thêm item vào itemset cuối, ràng buộc thứ tự từ điển) và S-Extension (thêm itemset đơn mới) (Bài báo 2023, Mục 3).
2. **Khái niệm Hỗ Trợ:** $support(P)$ được định nghĩa là số lượng chuỗi (distinct SID) trong CSDL chứa $P$ dưới dạng chuỗi con cấu trúc (Bài báo 2023, Đề cương luận văn).
3. **Công thức Trọng Số:** $w(i) = \frac{T(i)}{\sum_{j} T(j)}$, $MeanW = \frac{MaxW + MinW}{2}$, $WSUP(P) = support(P) \times MeanW$, chấp nhận khi $WSUP(P) \ge min\_sup$ (Bài báo 2023, Bản thảo 2026).
4. **Biểu diễn Bitmap cho Khai phá Dữ liệu:** Mỗi item được gắn với một Bitmap đại diện cho tập các SID chứa item đó; candidate Bitmap được tạo bằng phép giao bitwise AND (Bản thảo 2026, Mục 3.1).
5. **Cơ chế Cắt Tỉa Đa Tầng:** Sử dụng CMAP làm bộ lọc đồng xuất hiện sớm, Bitmap Cardinality làm chặn trên hỗ trợ (Upper Bound), và chỉ kiểm tra thứ tự thời gian chi tiết khi ứng viên thỏa mãn chặn trên (Bản thảo 2026, Mục 3.2).

### 4.2. IMPLEMENTATION POLICY / INFERENCE (Quy ước Thiết Kế và Suy Luận Kỹ Thuật)
1. **Phân tách CMAP thành CMAP-I và CMAP-S:** Bản thảo 2026 mô tả ma trận co-occurrence gộp chung quan hệ “trước hoặc cùng lúc”. Tuy nhiên, C01.1 và C01.2 đã chứng minh rằng gộp chung sẽ làm yếu đáng kể điều kiện cần của I-Extension. Do đó, thiết kế kiến trúc bắt buộc phải tách thành hai ma trận độc lập: `CmapI` (cùng itemset) và `CmapS` (itemset đứng trước nghiêm ngặt).
2. **Ánh Xạ SID Song Ánh Bất Biến (Bijective SID Mapping):** Để hỗ trợ các CSDL có SID là số nguyên bất kỳ (kể cả số âm hoặc không liên tục), kiến trúc quy định một thành phần ánh xạ song ánh xác định `SidMapping` chuyển đổi giữa `actual SID (int)` và `compact bit index [0, |SD|)`.
3. **Bảo toàn Tập Vị Trí Đầy Đủ (All-Endpoints State):** Dựa trên định lý OBL-07 và các phản ví dụ CE-01, CE-02 trong C01.2, kiến trúc nghiêm cấm nén vị trí kiểu earliest-only hoặc latest-only; trạng thái vị trí của mẫu bắt buộc phải lưu trữ toàn bộ các điểm kết thúc khả dĩ (feasible endpoints).
4. **Cô Lập Module Tuyệt Đối:** WSPM_BitPrune được xây dựng trong không gian tên riêng `vn.edu.wspm.bitprune.*`, không được kế thừa hay tái sử dụng bất kỳ lớp nào trong `vn.edu.wspm.prism.*`.
5. **Hợp Đồng Thống Kê Loại Trừ Lẫn Nhau (First-Failure Routing):** Mỗi ứng viên khi được sinh ra sẽ đi qua chuỗi phễu lọc cố định (CMAP $\to$ Bitmap Bound $\to$ Exact Verification) và được ghi nhận vào đúng một biến đếm kết quả cuối cùng.

### 4.3. UNRESOLVED / IMPLEMENTATION POLICY REQUIRED (Vấn Đề Chưa Chốt / Cần Quyết Định Bổ Sung)
1. **Chính Sách So Sánh Dấu Phẩy Động (Java `double` Comparator Policy):** OBL-04 chỉ được chứng minh toán học trên trường số thực $\mathbb{R}$ với bất đẳng thức nghiêm ngặt $<$. Sai số làm tròn dấu phẩy động của kiểu `double` trong Java chưa được chứng minh là an toàn tuyệt đối với mọi giá trị biên. Do đó, chính sách so sánh này giữ trạng thái `UNRESOLVED`; task C06 không được triển khai cho đến khi có đặc tả so sánh an toàn được thẩm định.
2. **Ngữ Nghĩa Ngưỡng Hỗ Trợ Bằng 0 (Research Issue R6):** Khi $effectiveMinSup == 0$, hành vi khai phá mẫu không xuất hiện trong CSDL (support = 0) vẫn giữ trạng thái `OPEN`. Mọi quy tắc cắt tỉa operational pruning dựa trên ngưỡng bằng 0 bị cấm áp dụng.

---

## 5. Existing Phase B API Inventory (Kiểm Kê và Phân Loại API Hiện Hữu)

Dựa trên khảo sát trực tiếp mã nguồn trong thư mục `src/main/java/vn/edu/wspm/`, toàn bộ các thành phần hiện hữu được phân loại thành 3 nhóm rõ ràng:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   PHASE B / SHARED REPOSITORY APIS                     │
├────────────────────────────────────────────────────────────────────────┤
│ 1. REUSABLE SHARED APIS (Tái sử dụng nguyên vẹn)                      │
│    - model: Item, Itemset, Pattern, PatternExtensions, Sequence,       │
│             SequenceRecord, SequenceDatabase                          │
│    - config: MinimumSupport, MinimumSupportMode,                       │
│              MinimumSupportResolver, ResolvedMinimumSupport           │
│    - statistics: ItemStatistics, ItemStatisticsCalculator,            │
│                  ItemWeightCalculator, ItemWeightResult,               │
│                  SequenceDatabaseStatistics                            │
│    - io: SequenceDatabaseParser, SequenceDatabaseFormat,               │
│          SpmfSequenceParser, FimiTransactionParser, v.v.               │
│    - tree: PrefixTree, PrefixTreeNode, ExtensionType                   │
│    - measurement: ExecutionMemorySampler, PhaseExecutionMetrics,       │
│                   WspmAlgorithmMetrics, WspmExecutionMetrics           │
│    - mining (Frozen Shared Sub-APIs): F1Builder, WeightedOnePattern    │
├────────────────────────────────────────────────────────────────────────┤
│ 2. PROTECTED BASELINE APIS (Đóng băng, cấm sửa đổi hay kết hợp)       │
│    - mining: WspmPreTreeMiner, CandidateEngine, CandidateResult,       │
│              SingletonEncodingBuilder, MeasuredMiningRun               │
│    - mining: WspmMiningResult (bị gắn chặt với PatternEncoding),       │
│              ResultRetentionPolicy (javadoc gắn với Prime-block)       │
│    - experiments: MainWspmPreTree, WspmExecutionRun                    │
├────────────────────────────────────────────────────────────────────────┤
│ 3. FORBIDDEN PRISM APIS (Cấm WSPM_BitPrune phụ thuộc)                  │
│    - prism: PatternEncoding, EncodedVector, PositionBlock,             │
│             PositionCoordinateSystem, PrimalBlockCodec,                │
│             PrimeBlockJoins, SequenceBlock                            │
└────────────────────────────────────────────────────────────────────────┘
```

### 5.1. Nhóm 1: Reusable Shared APIs (Tái sử dụng trực tiếp)
- **Shared Domain Model (`vn.edu.wspm.model`):**
  - [`Item`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/Item.java): Đại diện cho item định danh nguyên không âm; bất biến, có `Comparable`.
  - [`Itemset`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/Itemset.java): Tập hợp các item không rỗng, tự sắp xếp tăng dần và không chứa trùng lặp; bất biến.
  - [`Pattern`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/Pattern.java): Chuỗi các `Itemset`; bảo toàn cấu trúc tuần tự và ranh giới itemset, hỗ trợ `length()`, `size()`, `serialize()`.
  - [`PatternExtensions`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/PatternExtensions.java): Thực hiện thao tác mở rộng cấu trúc `iExtend` và `sExtend` chuẩn tắc, tuân thủ đúng thứ tự từ điển.
  - [`SequenceRecord`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/SequenceRecord.java), [`SequenceDatabase`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/model/SequenceDatabase.java): CSDL chuỗi với SID bất kỳ.
- **Configuration & Resolution (`vn.edu.wspm.config`):**
  - [`MinimumSupportResolver`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/config/MinimumSupportResolver.java): Giải quyết cấu hình minsup tuyệt đối hoặc tương đối.
  - [`ResolvedMinimumSupport`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/config/ResolvedMinimumSupport.java): Lưu trữ ngưỡng hiệu dụng số thực `effectiveThreshold` không qua bất kỳ phép làm tròn nào; cung cấp phương thức `meetsThreshold(support, meanWeight)` áp dụng phép so sánh bao hàm: `support * meanWeight >= effectiveThreshold` không sử dụng epsilon.
- **Database Statistics & Weight Calculation (`vn.edu.wspm.statistics`):**
  - [`ItemStatisticsCalculator`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/statistics/ItemStatisticsCalculator.java): Đếm tần suất xuất hiện và support chuỗi cho từng item.
  - [`ItemWeightCalculator`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/statistics/ItemWeightCalculator.java): Tính toán $w(i)$, $MaxW$, $MinW$, và global $MeanW$.
- **Structural Tree (`vn.edu.wspm.tree`):**
  - [`PrefixTree`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/tree/PrefixTree.java), [`PrefixTreeNode`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/tree/PrefixTreeNode.java), [`ExtensionType`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/tree/ExtensionType.java): Cây tiền tố thuần túy cấu trúc, hoàn toàn không phụ thuộc thuật toán khai phá hay biểu diễn mã hóa PRISM.
- **Measurement Protocol (`vn.edu.wspm.measurement`):**
  - [`ExecutionMemorySampler`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement/ExecutionMemorySampler.java), [`PhaseExecutionMetrics`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement/PhaseExecutionMetrics.java), [`WspmAlgorithmMetrics`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement/WspmAlgorithmMetrics.java), [`WspmExecutionMetrics`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement/WspmExecutionMetrics.java): Giao thức đo đạc thời gian bằng `nanoTime` và đo đạc heap sử dụng bằng `MemoryMXBean`.
- **Phân tích riêng: `F1Builder` và `WeightedOnePattern`:**
  - `F1Builder` và `WeightedOnePattern` hiện nằm trong gói `vn.edu.wspm.mining`. Tuy nhiên, mã nguồn của `F1Builder` chỉ phụ thuộc vào `SequenceDatabaseStatistics`, `ItemWeightResult`, và `ResolvedMinimumSupport`, hoàn toàn không chứa bất kỳ phụ thuộc nào vào PRISM.
  - **Quyết định kiến trúc:** Để bảo toàn quy tắc đóng băng Phase B (không refactor di chuyển file của baseline), WSPM_BitPrune được phép tái sử dụng trực tiếp `F1Builder` và `WeightedOnePattern` như một API dùng chung đã đóng băng. Nếu trong tương lai cần sự độc lập tuyệt đối giữa các package, có thể cung cấp một wrapper mỏng trong `vn.edu.wspm.bitprune.mining`.

### 5.2. Nhóm 2: Protected Baseline APIs (Bảo vệ đóng băng, cấm coupling)
- [`WspmPreTreeMiner`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining/WspmPreTreeMiner.java): Động cơ khai phá DFS đặc thù của WSPM_PreTree baseline.
- [`CandidateEngine`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining/CandidateEngine.java): Sinh ứng viên dựa trên phép nối Prime-block (`PrimeBlockJoins.iJoin`, `sJoin`).
- [`SingletonEncodingBuilder`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining/SingletonEncodingBuilder.java): Xây dựng mã hóa Prime-block cho $F1$ (yêu cầu giả định SID liên tục $1..N$).
- [`WspmMiningResult`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining/WspmMiningResult.java): Lớp kết quả này chứa trường `Map<Pattern, PatternEncoding> encodings` và phương thức công khai trả về `PatternEncoding`. Do đó, `WspmMiningResult` bị gắn chặt với PRISM và không thể dùng làm kết quả cho WSPM_BitPrune.
- [`ResultRetentionPolicy`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/mining/ResultRetentionPolicy.java): Javadoc định nghĩa tường minh chính sách lưu trữ mã hóa Prime-block (`FULL_ENCODING` lưu Prime-block encodings). WSPM_BitPrune cần một chính sách lưu trữ riêng phù hợp với cấu trúc Bitmap.

### 5.3. Nhóm 3: Forbidden PRISM APIs (Cấm tuyệt đối phụ thuộc)
- Toàn bộ gói `vn.edu.wspm.prism.*`: `PatternEncoding`, `EncodedVector`, `PositionBlock`, `PositionCoordinateSystem`, `PrimalBlockCodec`, `PrimeBlockJoins`, `SequenceBlock`.
- **Ràng buộc kiến trúc bất biến:** Bất kỳ import nào từ `vn.edu.wspm.prism.*` vào `vn.edu.wspm.bitprune.*` đều cấu thành vi phạm kiến trúc nghiêm trọng và sẽ bị reject trong quá trình kiểm tra.

---

## 6. Module Boundary and Dependency Matrix (Ranh Giới Module và Ma Trận Phụ Thuộc)

### 6.1. Chiều phụ thuộc kiến trúc mục tiêu (Target Dependency Direction)
Kiến trúc luồng dữ liệu và phụ thuộc của hệ thống tuân thủ nghiêm ngặt nguyên lý đơn hướng:

```
[Shared Domain, Config, I/O, Statistics, Tree, Measurement]
                          │
                          ▼
           [BitPrune Preprocessing Structures]
      (SidMapping, SidBitmap, VerticalOccurrenceIndex, CmapI, CmapS)
                          │
                          ▼
             [Candidate Filters & Verifiers]
      (BitmapUpperBoundFilter, ExactTemporalVerifier)
                          │
                          ▼
               [BitPrune DFS Mining Engine]
                 (BitPruneCandidateEngine, BitPruneMiner)
                          │
                          ▼
            [BitPrune Result & Statistics]
             (BitPruneMiningResult, BitPruneStatistics)
                          │
                          ▼
            [Standalone Experiment Runner]
                 (MainWspmBitPrune)
```

### 6.2. Ma Trận Phụ Thuộc Chi Tiết

| Module / Package | Trách Nhiệm Chính | Depends On (Được phép phụ thuộc) | Không Được Phụ Thuộc (Forbidden) | Phase B API Tái Sử Dụng | Rủi Ro Kiến Trúc & Biện Pháp Kiểm Soát |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`bitprune.bitmap`** | Quản lý biểu diễn mảng bit 64-bit, ánh xạ SID song ánh, phép giao bitwise AND, đếm cardinality. | `model` (`SequenceDatabase`, `SequenceRecord`) | `prism.*`, `mining.*`, `tree.*` | `SequenceDatabase`, `SequenceRecord` | **Rủi ro:** SID âm/không liên tục hoặc bit đệm rác ngoài $|SD|$.<br>**Kiểm soát:** `SidMapping` song ánh tất định; ép 0 toàn bộ padding bits ngoài $|SD|$. |
| **`bitprune.index`** | Lưu trữ chỉ mục vị trí xuất hiện (item $\to$ SID $\to$ sorted positions), quản lý trạng thái vị trí ứng viên $\mathcal{E}(P, s)$. | `model` (`Item`, `SequenceDatabase`) | `prism.*`, `mining.*`, `tree.*` | `Item`, `SequenceDatabase` | **Rủi ro:** Tràn bộ nhớ khi lưu toàn bộ All-Endpoints cho CSDL lặp dày đặc.<br>**Kiểm soát:** Giải phóng trạng thái con ngay khi duyệt xong DFS frame trong chế độ `SUPPORT_ONLY`. |
| **`bitprune.cmap`** | Xây dựng và tra cứu ma trận điều kiện cần CMAP-I (cùng itemset) và CMAP-S (trước nghiêm ngặt). | `model` (`Item`, `SequenceDatabase`), `statistics` | `prism.*`, `mining.*`, `tree.*` | `Item`, `SequenceDatabase`, `SequenceDatabaseStatistics` | **Rủi ro:** Nhầm lẫn giữa quan hệ cùng itemset và trước sau.<br>**Kiểm soát:** Tách biệt thành 2 ma trận độc lập `CmapI` và `CmapS`; hỗ trợ $a=b$ cho CMAP-S. |
| **`bitprune.verification`** | Bộ lọc chặn trên Bitmap Cardinality và bộ kiểm chứng thời gian chính xác (Exact Temporal Join). | `model`, `config`, `bitprune.bitmap`, `bitprune.index`, `statistics` | `prism.*`, `tree.*` | `Item`, `Pattern`, `ResolvedMinimumSupport`, `ItemWeightResult` | **Rủi ro:** False negative do sai số dấu phẩy động hoặc nén vị trí sai.<br>**Kiểm soát:** Dùng bất đẳng thức nghiêm ngặt $<$; bảo toàn All-Endpoints theo OBL-07. |
| **`bitprune.mining`** | Điều phối sinh ứng viên hợp lệ và duyệt không gian tìm kiếm DFS trên cây tiền tố Prefix Tree. | `model`, `config`, `tree`, `statistics`, `bitprune.*` (trừ runner), `measurement` | `prism.*`, `mining.CandidateEngine`, `mining.SingletonEncodingBuilder`, `mining.WspmPreTreeMiner` | `PrefixTree`, `PrefixTreeNode`, `PatternExtensions`, `F1Builder`, `WeightedOnePattern`, `ExecutionMemorySampler` | **Rủi ro:** Sinh ứng viên trùng lặp hoặc vi phạm thứ tự từ điển I-Extension.<br>**Kiểm soát:** Ủy quyền sinh ứng viên cho `PatternExtensions`; kiểm tra tính duy nhất trên Prefix Tree. |
| **`bitprune.result`** | Đóng gói kết quả khai phá, danh sách mẫu, bảng hỗ trợ chính xác và các trạng thái kiểm toán bản địa. | `model`, `config`, `tree`, `bitprune.statistics`, `bitprune.bitmap` | `prism.*`, `mining.WspmMiningResult` | `Pattern`, `PrefixTree`, `ResolvedMinimumSupport` | **Rủi ro:** Rò rỉ phụ thuộc vào mã hóa PRISM.<br>**Kiểm soát:** Tạo lớp `BitPruneMiningResult` độc lập; chỉ lưu trữ cấu trúc bản địa của BitPrune. |
| **`bitprune.statistics`**| Thu thập và bảo đảm các bất biến hạch toán ứng viên loại trừ lẫn nhau (First-failure accounting). | Không phụ thuộc module khác (thuần dữ liệu thống kê). | `prism.*` | Không | **Rủi ro:** Đếm trùng ứng viên bị loại qua nhiều tầng hoặc đếm nhầm hậu duệ bị cắt tỉa cùng cây.<br>**Kiểm soát:** Áp dụng công thức bất biến đóng; kiểm tra tại constructor. |
| **`experiments`** | Điểm khởi chạy thực nghiệm độc lập (`MainWspmBitPrune`), cấu hình tham số và xuất báo cáo. | `model`, `config`, `io`, `measurement`, `bitprune.*` | `prism.*` | `SequenceDatabaseParser`, `ExecutionMemorySampler`, `WspmExecutionMetrics` | **Rủi ro:** Đo lường không tương thích với Phase B.<br>**Kiểm soát:** Tái sử dụng cùng giao thức đo 6 scope thời gian (loại trừ hoàn toàn console printing) và bộ nhớ heap của Phase B. |

---

## 7. Proposed Package Structure (Cấu Trúc Package Đề Xuất)

Để bảo đảm tính đóng gói cao, tách biệt trách nhiệm và dễ dàng kiểm thử đơn vị, cấu trúc package của WSPM_BitPrune được quy hoạch như sau:

```text
src/main/java/vn/edu/wspm/
├── bitprune/
│   ├── bitmap/                 // [C03] Biểu diễn Bitmap, ánh xạ SID song ánh, bitwise operations
│   │   ├── SidMapping.java
│   │   ├── SidBitmap.java
│   │   └── package-info.java
│   ├── index/                  // [C04] Chỉ mục vị trí xuất hiện dọc và trạng thái All-Endpoints
│   │   ├── VerticalOccurrenceIndex.java
│   │   ├── PatternEndpoints.java
│   │   └── package-info.java
│   ├── cmap/                   // [C05] Ma trận đồng xuất hiện CMAP-I và CMAP-S
│   │   ├── CmapI.java
│   │   ├── CmapS.java
│   │   ├── CmapBuilder.java
│   │   └── package-info.java
│   ├── verification/           // [C06, C07, C08] Bộ lọc chặn trên và kiểm chứng thời gian
│   │   ├── BitmapUpperBoundFilter.java
│   │   ├── ExactTemporalVerifier.java
│   │   ├── VerificationResult.java
│   │   └── package-info.java
│   ├── mining/                 // [C09, C10] Động cơ sinh ứng viên và điều phối DFS
│   │   ├── BitPruneCandidateEngine.java
│   │   ├── BitPruneMiner.java
│   │   ├── BitPruneCandidateResult.java
│   │   └── package-info.java
│   ├── result/                 // [C14] Hợp đồng kết quả và chính sách lưu trữ
│   │   ├── BitPruneMiningResult.java
│   │   ├── BitPruneRetentionPolicy.java
│   │   ├── BitPrunePatternState.java
│   │   └── package-info.java
│   └── statistics/             // [C12] Thống kê khai phá và bộ tích lũy bất biến
│       ├── BitPruneStatistics.java
│       ├── BitPruneStatisticsAccumulator.java
│       └── package-info.java
└── experiments/                // [C15] Runner thực nghiệm độc lập
    ├── MainWspmBitPrune.java
    └── BitPruneExecutionRun.java
```

### Phân tích Trade-off Kiến Trúc:
- **Tách riêng `verification` khỏi `mining`:** Giúp việc kiểm thử đơn vị cho OBL-03, OBL-04 (C06), OBL-07 (C07, C08) được thực hiện độc lập mà không cần khởi tạo toàn bộ cây Prefix Tree hay luồng duyệt DFS.
- **Không tạo package con trong `prism`:** Giữ nguyên vẹn tính độc lập khoa học; WSPM_BitPrune là một giải thuật thay thế hoàn toàn PRISM bằng Bitmap và Lazy Pruning.

---

## 8. Proposed API Contract Table (Bảng Hợp Đồng API Đề Xuất)

> **Lưu ý chuẩn tắc:** Mọi kiểu dữ liệu và phương thức trong bảng dưới đây là **gợi ý kiến trúc (`PROPOSED API`)** phục vụ thiết kế cho các task tiếp theo, không phải là mã nguồn đã tồn tại trong repository.

| Proposed Type / Interface | Input | Output | Preconditions | Postconditions | Ownership | Failure Behavior | Planned Task |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`SidMapping`**<br>`of(SequenceDatabase)` | `SequenceDatabase database` | `SidMapping` | `database != null` | Tạo song ánh xác định giữa actual SID và index `[0, |SD|)`; immutable; triển khai `equals` dựa trên vũ trụ và thứ tự actual SID. | Caller sở hữu; dùng chung toàn run. | Ném `NullPointerException` nếu database null. | C03 |
| **`SidBitmap`**<br>`of(SidMapping, long[])` | `SidMapping mapping, long[] words` | `SidBitmap` | `mapping != null`, `words != null` | Hỗ trợ empty SID universe (`sequenceCount = 0`, `wordCount = 0`, `words.length = 0`, `cardinality = 0`). Với non-empty universe, `words.length == wordCount` và bit ngoài $|SD|$ luôn bằng 0. Immutable value. | Độc quyền của instance; chia sẻ an toàn. | Ném `IllegalArgumentException` nếu `sequenceCount > 0` mà `words.length != wordCount`, hoặc nếu `sequenceCount == 0` mà `words.length != 0`. | C03 |
| **`SidBitmap.and`**<br>`(SidBitmap other)` | `SidBitmap other` | `SidBitmap` | `other != null`, `this.mapping.equals(other.mapping)` | Trả về bitmap mới đại diện cho tập giao; cardinality được cập nhật. Empty bitmap AND empty bitmap cùng universe trả về empty bitmap. | Trả về object mới (copy-on-write). | Ném `IllegalArgumentException` nếu khác SID Universe (`!mapping.equals`). | C03 |
| **`VerticalOccurrenceIndex`**<br>`build(SequenceDatabase, SidMapping)` | `SequenceDatabase db, SidMapping mapping` | `VerticalOccurrenceIndex` | `db != null`, `mapping != null` | Lưu trữ toàn bộ vị trí xuất hiện của mọi item theo SID; sắp xếp tăng dần; immutable. | Shared read-only toàn bộ quá trình khai phá. | Ném ngoại lệ nếu dữ liệu sequence không nhất quán. | C04 |
| **`PatternEndpoints`**<br>`of(int sid, int[] endpoints)` | `int sid, int[] endpoints` | `PatternEndpoints` | `endpoints` tăng dần, không rỗng, không trùng | Đại diện cho toàn bộ điểm kết thúc khả dĩ $\mathcal{E}(P, s)$ trong 1 SID. | Thuộc sở hữu của DFS stack frame. | Ném `IllegalArgumentException` nếu mảng không được sắp xếp. | C04 |
| **`CmapI`** / **`CmapS`**<br>`build(SequenceDatabase, ...)` | `SequenceDatabase db, F1 items` | `CmapI`, `CmapS` | `db != null`, `f1 != null` | Ma trận boolean 2 chiều xác định điều kiện cần co-occurrence; immutable. | Shared read-only toàn bộ quá trình khai phá. | Ném ngoại lệ nếu input null hoặc không hợp lệ. | C05 |
| **`BitmapUpperBoundFilter`**<br>`evaluate(SidBitmap, double, ResolvedMinimumSupport)` | `SidBitmap candidateBitmap, double meanW, ResolvedMinimumSupport minSup` | `boolean canPrune` | `candidateBitmap != null`, `meanW >= 0` | Trên mô hình số thực $\mathbb{R}$: $cardinality \times MeanW < effectiveMinSup \implies WSUP(P) < effectiveMinSup$. Không cắt tỉa tại đẳng thức. Java `double` comparator vẫn `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED` (cấm triển khai Java pruning decision tại boundary khi chưa có chính sách comparator). | Stateless component. | Trả về `false` nếu $effectiveMinSup == 0$ (tuân thủ R6). | C06 |
| **`ExactTemporalVerifier`**<br>`verifyIExtension(...)` | `Pattern parent, PatternEndpoints parentEp, Item item, VerticalOccurrenceIndex idx` | `VerificationResult` | Parent và item hợp lệ theo canonical order | Nối thời gian cùng itemset; trả về supporting SIDs và tập endpoints mới $\mathcal{E}(P', s)$. | Trả về result mới cho DFS frame kế tiếp. | Ném ngoại lệ nếu vi phạm giả định cấu trúc. | C07 |
| **`ExactTemporalVerifier`**<br>`verifySExtension(...)` | `Pattern parent, PatternEndpoints parentEp, Item item, VerticalOccurrenceIndex idx` | `VerificationResult` | Parent và item hợp lệ | Nối thời gian itemset đứng sau nghiêm ngặt ($p > e$); bảo toàn All-Endpoints. | Trả về result mới cho DFS frame kế tiếp. | Ném ngoại lệ nếu vi phạm giả định cấu trúc. | C08 |
| **`BitPruneCandidateEngine`**<br>`generateCandidates(...)` | `Pattern parent, DFS Context` | `List<BitPruneCandidateResult>` | Parent đã được chấp nhận trong Prefix Tree | Sinh ứng viên theo thứ tự chuẩn tắc: I-Extension trước, S-Extension sau. | Trả về immutable list cho miner. | Ném ngoại lệ nếu parent rỗng. | C09 |
| **`BitPruneMiner`**<br>`mine(SequenceDatabase, MinimumSupport, ...)` | `SequenceDatabase, MinimumSupport, BitPruneRetentionPolicy` | `BitPruneMiningResult` | Input CSDL và cấu hình hợp lệ | Duyệt DFS toàn bộ không gian mẫu; kết quả đồng nhất tuyệt đối với baseline (cả 2 mode retention tạo cùng accepted patterns và exact supports). | Caller sở hữu kết quả cuối cùng. | Ném ngoại lệ nếu cấu hình không hợp lệ. | C10 |
| **`BitPrunePatternState`** | `SidBitmap candidateBitmap, int[] supportingSids, Map<Integer, int[]> allEndpoints` | `BitPrunePatternState` | `candidateBitmap != null`, `supportingSids != null`, `allEndpoints != null` | Lưu trữ trạng thái kiểm toán bản địa của BitPrune cho mẫu được chấp nhận trong `FULL_ENCODING`: upper-bound bitmap, exact supporting SIDs và full All-Endpoints. | Caller sở hữu trong kết quả; immutable. | Ném ngoại lệ nếu thành phần null hoặc không hợp lệ. | C14 |
| **`BitPruneMiningResult`** | Các thành phần kết quả khai phá | `BitPruneMiningResult` | Các bất biến thống kê và PrefixTree khớp nhau | Chứa frozen `PrefixTree`, pre-order patterns, exact immutable `Map<Pattern, Integer>`, $MeanW$, `resolvedMinimumSupport`, `distinctItemCount`, `BitPruneStatistics`, và bổ sung audit `patternStates` trong `FULL_ENCODING`. | Caller sở hữu; immutable. | Ném ngoại lệ nếu vi phạm bất biến kết quả. | C14 |

---

## 9. SID Mapping and Bitmap Contract (Hợp Đồng Ánh Xạ SID và Biểu Diễn Bitmap)

### 9.1. Yêu Cầu Cho Ánh Xạ SID Song Ánh (`SidMapping`)
Trong các CSDL SPMF thực tế, mã định danh chuỗi (`actual SID`) có thể là các số nguyên bất kỳ, không liên tục, thậm chí là số âm (theo chuẩn `SequenceRecord` của repository). Trong khi đó, biểu diễn mảng bit yêu cầu chỉ số liên tục trong đoạn $[0, |SD| - 1]$.

1. **Tính Song Ánh và Đơn Điệu:**
   - $\text{sidToIndex}: \text{ActualSID} \to [0, |SD| - 1]$ là một song ánh (bijective mapping).
   - $\text{indexToSid}: [0, |SD| - 1] \to \text{ActualSID}$ là hàm ngược duy nhất.
2. **Thứ Tự Xác Định (Deterministic Ordering):**
   - Ánh xạ được xây dựng dựa trên thứ tự xuất hiện của các bản ghi trong `SequenceDatabase.records()`: Bản ghi thứ $k$ (với $0 \le k < |SD|$) có index bit là $k$, và actual SID là `record.sid()`.
3. **Bất Biến Vũ Trụ SID (SID Universe Invariant):**
   - Mỗi đối tượng `SidMapping` gắn liền với một CSDL cụ thể trong một lần chạy khai phá.
   - `SidMapping` cài đặt `equals` và `hashCode` xác định dựa trên danh sách và thứ tự các actual SIDs.
   - Mọi thao tác bitwise AND giữa hai Bitmap bắt buộc phải xác thực rằng cả hai đều thuộc cùng một vũ trụ SID thông qua `this.mapping.equals(other.mapping)`. Cấm thực hiện phép toán bit giữa hai vũ trụ SID khác nhau (`!this.mapping.equals(other.mapping)` ném `IllegalArgumentException`).

### 9.2. Biểu Diễn Mảng Bit 64-bit (`SidBitmap`)
1. **Cấu Trúc Lưu Trữ và Tính Toán Số Từ Máy An Toàn (Word Count Overflow Safety):**
   - Sử dụng mảng số nguyên 64-bit (`long[] words`).
   - Để ngăn ngừa nguy cơ tràn số nguyên 32-bit khi `sequenceCount` lớn (phép cộng `sequenceCount + 63` trong kiểu `int` có thể tràn âm nếu $sequenceCount > \text{Integer.MAX\_VALUE} - 63$), số lượng từ máy bắt buộc phải được tính toán an toàn qua kiểu `long`:
     $$\text{wordCountLong} = ((\text{long}) \text{sequenceCount} + 63\text{L}) \mathbin{>>>} 6$$
   - Kiểm tra ràng buộc và ép kiểu có kiểm soát:
     - Yêu cầu `sequenceCount >= 0` (ném `IllegalArgumentException` nếu âm).
     - Kiểm tra `wordCountLong <= MAX_SUPPORTED_ARRAY_LENGTH` (giới hạn mảng Java khả dụng, ví dụ `Integer.MAX_VALUE - 8`; ném ngoại lệ rõ ràng nếu vượt quá giới hạn mảng).
     - Ép kiểu về `int`: `int wordCount = (int) wordCountLong;`.
     - Độ dài mảng thực tế `words.length` bắt buộc phải bằng chính xác `wordCount`.
   - Tuyệt đối không tuyên bố bitmap không có rủi ro kích thước chỉ vì `sequenceCount` là kiểu `int`.

2. **Hợp Đồng Biểu Diễn Mảng Bit Rỗng (Empty SID Universe Contract):**
   - Hỗ trợ đầy đủ trường hợp CSDL rỗng ($|SD| = 0$ / `sequenceCount == 0`):
     - `sequenceCount = 0`
     - `wordCount = 0`
     - `words.length = 0` (mảng rỗng `new long[0]` là biểu diễn canonical chuẩn tắc duy nhất khi SID universe rỗng).
     - `cardinality = 0`
   - `SidBitmap` tuyệt đối không được ném lỗi chỉ vì mảng rỗng `words.length == 0` khi vũ trụ SID rỗng.
   - Phép toán `emptyBitmapA.and(emptyBitmapB)` giữa hai bitmap cùng vũ trụ rỗng (`this.mapping.equals(other.mapping)`) trả về một empty bitmap mới thuộc cùng vũ trụ.
   - Phép giao giữa hai bitmap khác vũ trụ SID (`!this.mapping.equals(other.mapping)`) bắt buộc phải ném `IllegalArgumentException`.
   - **Ranh giới khai phá toàn trình trên CSDL rỗng (Empty Database Control Flow theo C01.2):**
     Việc `SidBitmap` hỗ trợ biểu diễn rỗng là hợp đồng cấu trúc dữ liệu nền tảng; điều này phân định chặt chẽ các nhánh xử lý của pipeline khai phá theo C01.2 (Mục 12) và domain model:
     1. Parser (`SpmfSequenceParser`) có thể phân tích một tệp dữ liệu rỗng thành `SequenceDatabase` hợp lệ với `sequenceCount = 0`; parser không mặc định từ chối CSDL rỗng. Parser vẫn phải từ chối đầu vào sai cú pháp, bao gồm nhưng không giới hạn các trường hợp empty-sequence và empty-itemset được xác định tại TV-11.
     2. Với cấu hình minsup tỷ lệ phần trăm (`MinimumSupportMode.PERCENT`) khi `sequenceCount = 0`, `MinimumSupportResolver` bắt buộc từ chối và ném `IllegalArgumentException` vì không có cơ sở toán học hợp lệ để quy đổi phần trăm thành ngưỡng support hữu hiệu.
     3. Với cấu hình minsup tuyệt đối (`MinimumSupportMode.ABSOLUTE`), `MinimumSupportResolver` giữ nguyên giá trị cấu hình nếu giá trị đó hợp lệ ($A \ge 0$).
     4. Bước tính toán trọng số `ItemWeightCalculator` trên database rỗng trả về kết quả rỗng `ItemWeightResult.empty()` (không tồn tại $MaxW$, $MinW$, hay hằng số toàn cục $MeanW$).
     5. Mining pipeline dừng trước DFS và tuyệt đối không được bắt đầu đệ quy DFS vì không có $MeanW$ hợp lệ hoặc không gian tìm kiếm rỗng ($F1 = \emptyset$).
     6. Hệ thống tuyệt đối không được tạo pattern hoặc statistics giả để mô phỏng một mining run thành công trên database rỗng.
     7. Toàn bộ exception và failure mode phải tuân thủ nghiêm ngặt các quy định đã được phê duyệt trong C01.2 (Bảng 12.1).

3. **Quy Tắc Padding Bits và Căn Lề Từ Máy (Word Alignment Boundary):**
   - Với non-empty universe ($|SD| > 0$), các chỉ số bit từ $0$ đến $|SD| - 1$ tương ứng với các chuỗi thực tế trong CSDL.
   - **Bất biến Zero-Padding:** Mọi bit đệm trong khoảng từ $|SD|$ đến $\text{wordCount} \times 64 - 1$ **bắt buộc phải luôn luôn bằng 0**.
   - Mọi phép toán phủ định bit hoặc khởi tạo mảng đều phải áp dụng mặt nạ bit (bitmask) ở từ máy cuối cùng để xóa sạch các bit đệm ngoài phạm vi $|SD|$.

4. **Tính Bất Biến (Immutability):**
   - `SidBitmap` được thiết kế như một đối tượng giá trị bất biến (immutable value object). Phép toán `and(SidBitmap other)` tạo ra một thể hiện `SidBitmap` mới, không làm biến đổi thể hiện hiện tại.

5. **Đếm Cardinality Hiệu Quả:**
   - Cardinality được tính bằng tổng số bit bật của các từ máy thông qua hàm nội tại tối ưu của phần cứng:
     $$\text{cardinality} = \sum_{w=0}^{\text{wordCount}-1} \text{Long.bitCount}(words[w])$$
   - Với empty universe (`wordCount == 0`), vòng lặp không chạy và `cardinality` bằng 0 xác định.

6. **Khôi Phục Danh Sách Actual SIDs Từ Set Bits:**
   - Cung cấp phương thức trả về `int[] toSupportingSids()` bằng cách quét các bit 1, tra cứu qua `mapping.indexToSid(bitIndex)` và lưu vào mảng kết quả. Với empty universe, trả về mảng rỗng `new int[0]`.

---

## 10. Vertical Occurrence and All-Endpoints Contract (Hợp Đồng Chỉ Mục Dọc và All-Endpoints)

### 10.1. Chỉ Mục Vị Trí Xuất Hiện Dọc (`VerticalOccurrenceIndex`)
1. **Mục Đích:** Phục vụ bước tiền xử lý nhằm lập chỉ mục vị trí của từng item xuất hiện trong CSDL, loại bỏ hoàn toàn việc phải quét lại CSDL dạng bản ghi trong quá trình duyệt DFS.
2. **Mô Hình Dữ Liệu:**
   $$\text{item} \to \text{actual SID} \to \text{sorted itemset positions } [p_1, p_2, \dots, p_k]$$
   - Vị trí itemset được đánh số 1-based theo thứ tự thời gian ($1 \le p_1 < p_2 < \dots \le |S|$).
   - Bảo toàn tuyệt đối mọi lần xuất hiện lặp lại của item ở các itemset khác nhau trong cùng một chuỗi.

### 10.2. Trạng Thái Vị Trí Mẫu Đầy Đủ (`PatternEndpoints` / All-Endpoints)
Dựa trên chứng minh định lý OBL-07 và các phản ví dụ CE-01 (Earliest-Only thất bại), CE-02 (Latest-Only thất bại) trong C01.2:
1. **Định Nghĩa Tập Điểm Kết Thúc Khả Dĩ:**
   Với chuỗi $s$ và mẫu $P$, tập điểm kết thúc khả dĩ $\mathcal{E}(P, s)$ là tập hợp chỉ số của itemset cuối cùng trong mọi phép nhúng hợp lệ của $P$ vào $s$:
   $$\mathcal{E}(P, s) = \{ e \in [1, |s|] \mid \exists \text{ embedding of } P \text{ ending at itemset } e \}$$
2. **Quy Tắc Mở Rộng Thời Gian Chính Xác:**
   - **I-Extension với item $x$ ($P' = P \oplus_I x$):**
     Một điểm kết thúc $e \in \mathcal{E}(P, s)$ sẽ trở thành điểm kết thúc của $P'$ nếu và chỉ nếu itemset thứ $e$ của chuỗi $s$ có chứa item $x$:
     $$\mathcal{E}(P', s) = \{ e \in \mathcal{E}(P, s) \mid x \in s[e] \}$$
   - **S-Extension với item $x$ ($P' = P \oplus_S (x)$):**
     Với mỗi điểm kết thúc $e \in \mathcal{E}(P, s)$, mọi vị trí xuất hiện $p$ của item $x$ trong chuỗi $s$ thỏa mãn $p > e$ đều tạo thành điểm kết thúc hợp lệ mới của $P'$:
     $$\mathcal{E}(P', s) = \{ p \in \text{Occurrences}(x, s) \mid \exists e \in \mathcal{E}(P, s) : p > e \}$$
3. **Bất Biến Tập Hợp Điểm Kết Thúc:**
   - Mọi mảng `endpoints` trong `PatternEndpoints` bắt buộc phải được loại bỏ trùng lặp (distinct) và sắp xếp tăng dần nghiêm ngặt.
   - Chuỗi $s$ hỗ trợ $P$ nếu và chỉ nếu $\mathcal{E}(P, s) \ne \emptyset$. Số chuỗi có $\mathcal{E}(P, s) \ne \emptyset$ chính là $support(P)$.

---

## 11. CMAP-I / CMAP-S Contract (Hợp Đồng Bộ Lọc Co-Occurrence CMAP)

### 11.1. Phân Tách Hai Quan Hệ Đồng Xuất Hiện
1. **`CMAP-I(a, b)` (Quan Hệ Cùng Itemset):**
   - Điều kiện: Item $a$ và item $b$ cùng xuất hiện trong ít nhất một itemset của ít nhất một chuỗi trong CSDL, với ràng buộc thứ tự từ điển chuẩn tắc $a < b$.
   - Ý nghĩa: Nếu $\text{CMAP-I}(a, b) == \text{false}$, thì không thể tồn tại bất kỳ chuỗi nào chứa itemset có cả $a$ và $b$. Do đó, mọi ứng viên I-Extension thêm $b$ vào itemset cuối đang có item tối đại là $a$ đều có $support = 0$ theo định lý toán học (OBL-05). Khi $effectiveMinSup > 0$, ứng viên và toàn bộ cây con của nó bị loại bỏ an toàn theo OBL-01, OBL-02 và quy tắc R-02 (không cắt tỉa cây con của nút cha $P$).
2. **`CMAP-S(a, b)` (Quan Hệ Đứng Trước Nghiêm Ngặt):**
   - Điều kiện: Item $a$ xuất hiện tại itemset thứ $t$, item $b$ xuất hiện tại itemset thứ $u$ trong cùng một chuỗi, với $t < u$.
   - **Trường hợp item lặp lại ($a = b$):** $\text{CMAP-S}(a, a)$ có giá trị `true` nếu và chỉ nếu có ít nhất một chuỗi chứa item $a$ ở hai itemset khác nhau.
   - Ý nghĩa: Nếu $\text{CMAP-S}(a, b) == \text{false}$, thì không có chuỗi nào chứa $b$ ở itemset đứng sau $a$. Mọi ứng viên S-Extension thêm $(b)$ vào mẫu có item cuối là $a$ đều có $support = 0$ theo định lý toán học (OBL-06, bao gồm cả trường hợp item lặp lại $a=b$). Khi $effectiveMinSup > 0$, ứng viên và toàn bộ cây con của nó bị loại bỏ an toàn theo OBL-01, OBL-02 và quy tắc R-03 (không cắt tỉa cây con của nút cha $P$).

### 11.2. Ranh Giới An Toàn Của CMAP
- CMAP chỉ là **bộ lọc điều kiện cần (necessary condition)**, hoàn toàn không phải là bằng chứng hỗ trợ thời gian chính xác.
- Khi $\text{CMAP}(a, b) == \text{true}$, ứng viên vẫn có thể có $support(P) = 0$ trên các mẫu dài do xung đột thứ tự nhiều bước (như đã chứng minh trong phản ví dụ CE-03 và CE-04 của C01.2). Do đó, CMAP không bao giờ thay thế bước kiểm chứng thời gian chính xác.
- Quy tắc cắt tỉa dựa trên CMAP chỉ được kích hoạt khi $effectiveMinSup > 0$. Khi ngưỡng bằng 0, việc cắt tỉa bị tạm dừng tuân thủ R6.

---

## 12. Candidate / DFS / Prefix Tree Data Flow (Luồng Dữ Liệu và Phễu Cắt Tỉa)

Luồng xử lý từ CSDL đầu vào đến khi hoàn tất cây Prefix Tree được thiết kế gồm 14 bước tuần tự, không nhảy cóc:

```
[1. SequenceDatabase]
        │
        ▼
[2. Database Statistics & Item Weights Calculation] ──> MeanW, F1 items
        │
        ▼
[3. SidMapping & Singleton Bitmaps Construction] ──> Bitmaps cho từng item F1
        │
        ▼
[4. Vertical Occurrence Index & CMAP-I/S Construction]
        │
        ▼
[5. Prefix Tree Initialization] ──> Chèn các mẫu 1-pattern F1 vào root
        │
        ▼ (Bắt đầu đệ quy DFS cho từng nhánh F1)
[6. DFS Parent State (Pattern, Candidate Bitmap, PatternEndpoints)]
        │
        ▼
[7. Canonical Candidate Generation (I-Extension & S-Extension)]
        │
        ├──> [8. Tầng 1: CMAP Filter]
        │         └──> FALSE ──> [Outcome: CMAP_REJECTED] (Dừng nhánh ứng viên)
        │         └──> TRUE
        │                │
        ▼                ▼
[9. Candidate Bitmap Intersection: parentBitmap AND itemBitmap]
        │
        ├──> [10. Tầng 2: Weighted Upper-Bound Filter]
        │         └──> (cardinality * MeanW < effectiveMinSup) ──> [Outcome: BITMAP_REJECTED]
        │         └──> (cardinality * MeanW >= effectiveMinSup)
        │                │
        ▼                ▼
[11. Tầng 3: Exact Temporal Verification (Nối All-Endpoints trên Occurrence Index)]
        │
        ├──> Exact Support Calculation
        │         └──> (exactSupport * MeanW < effectiveMinSup) ──> [Outcome: EXACT_REJECTED]
        │         └──> (exactSupport * MeanW >= effectiveMinSup)
        │                │
        ▼                ▼
[12. Outcome: ACCEPTED_EXTENSION]
        │
        ▼
[13. Insert Child into Prefix Tree & Update Exact Support Map]
        │
        ▼
[14. Recursive DFS Expansion on Child Node] (Giải phóng intermediate state khi quay lui)
```

---

## 13. Ownership and Lifecycle (Hợp Đồng Sở Hữu và Vòng Đời Đối Tượng)

Bảng phân tích chi tiết vòng đời của từng đối tượng dữ liệu trong quá trình thực thi WSPM_BitPrune:

| Đối Tượng Dữ Liệu | Thành Phần Khởi Tạo | Chủ Sở Hữu (Owner) | Tính Biến Đổi | Phạm Vi Vòng Đời (Lifecycle Scope) | Cơ Chế Thu Hồi / Giải Phóng | Hành Vi Trong `SUPPORT_ONLY` | Hành Vi Trong `FULL_ENCODING` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`SidMapping`** | Preprocessing Stage | Miner run context | Immutable | Toàn bộ mining run | Giải phóng khi kết thúc run | Được giữ trong run context | Được giữ trong run context |
| **`Singleton Bitmaps`** | Preprocessing Stage | Preprocessing cache | Immutable | Toàn bộ mining run | Giải phóng khi kết thúc run | Được giữ để tái sử dụng phép AND | Được giữ để tái sử dụng phép AND |
| **`Vertical Occurrence Index`** | Preprocessing Stage | Preprocessing cache | Immutable | Toàn bộ mining run | Giải phóng khi kết thúc run | Được giữ phục vụ exact verification | Được giữ phục vụ exact verification |
| **`CMAP-I` & `CMAP-S`** | Preprocessing Stage | Preprocessing cache | Immutable | Toàn bộ mining run | Giải phóng khi kết thúc run | Được giữ phục vụ tầng lọc 1 | Được giữ phục vụ tầng lọc 1 |
| **`Parent Positional State`** | DFS Stack Frame | DFS Execution Stack | Immutable / Unmodifiable | Tồn tại suốt thời gian nhánh con đang duyệt | Giải phóng khi frame cha pop khỏi stack | Bắt buộc giữ nguyên cho đến khi tất cả con duyệt xong | Bắt buộc giữ nguyên cho đến khi tất cả con duyệt xong |
| **`Candidate SID Bitmap`** (Upper-Bound Bitmap) | Candidate Engine | DFS Stack Frame | Immutable value | Tồn tại trong frame ứng viên | Thu hồi bởi GC khi nhánh ứng viên kết thúc | Không giữ trong kết quả cuối | Lưu trữ trong đối tượng audit `BitPrunePatternState` nếu mẫu được chấp nhận |
| **`Candidate Endpoints`** (All-Endpoints $\mathcal{E}(P, s)$) | Exact Verifier | DFS Stack Frame | Immutable list/map | Tồn tại trong frame ứng viên | Thu hồi bởi GC khi nhánh con duyệt xong | **Giải phóng ngay** khi subtree của nút con hoàn tất duyệt DFS | Lưu trữ toàn bộ feasible endpoints trong `BitPrunePatternState` nếu mẫu được chấp nhận |
| **`Candidate Supporting SIDs`** (Exact Membership) | Exact Verifier | DFS Stack Frame | Immutable set/array | Tồn tại trong frame ứng viên | Thu hồi bởi GC khi nhánh con duyệt xong | Không giữ riêng; chỉ giữ support count | Lưu trữ tập actual SIDs phân biệt trong `BitPrunePatternState` |
| **`PrefixTree`** | Miner | `BitPruneMiningResult` | Mutable khi dựng, Freeze khi xong | Tồn tại trong kết quả cuối | Chuyển giao cho caller | Giữ nguyên cấu trúc cây tiền tố | Giữ nguyên cấu trúc cây tiền tố |
| **`Supports Map`** | Miner | `BitPruneMiningResult` | Mutable khi dựng, Freeze khi xong | Tồn tại trong kết quả cuối | Chuyển giao cho caller | Giữ toàn bộ cặp `Pattern -> exactSupport` | Giữ toàn bộ cặp `Pattern -> exactSupport` |

### Quy Tắc An Toàn Sống Còn (Safety Invariants for Lifecycle):
1. **Không giải phóng sớm:** Tuyệt đối không được giải phóng `PatternEndpoints` của nút cha khi vẫn còn ít nhất một ứng viên con của nút cha đó chưa hoàn thành duyệt DFS.
2. **Không lưu trữ ứng viên bị loại:** Mọi ứng viên bị loại bởi CMAP, Bitmap Bound hoặc Exact Verification phải được bỏ rơi ngay lập tức để GC thu hồi; không lưu trữ bất kỳ tham chiếu nào trong bộ nhớ kết quả.
3. **Bất biến tương đương giữa hai chế độ lưu trữ:** Chính sách lưu trữ bộ nhớ (`ResultRetentionPolicy`: `SUPPORT_ONLY` hay `FULL_ENCODING`) tuyệt đối không được làm thay đổi thứ tự duyệt không gian DFS hoặc các quyết định cắt tỉa / chấp nhận mẫu; cả hai chế độ bắt buộc phải sinh ra cùng danh sách mẫu được chấp nhận `acceptedPatterns`, cùng bảng hỗ trợ chính xác `Map<Pattern, Integer>`, cùng cấu trúc cây `PrefixTree` và cùng kết quả thống kê `BitPruneStatistics`.

---

## 14. Numeric Safety Decision Matrix (Ma Trận An Toàn Số Học)

| Thao Tác / Biến Số | Miền Giá Trị Toán Học | Kiểu Dữ Liệu Java Đề Xuất | Rủi Ro Tràn Số / Làm Tròn | Quyết Định Thiết Kế Hiện Tại | Điều Kiện Bảo Vệ (Guard / STOP) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Actual SID** | $\mathbb{Z}$ (Số nguyên bất kỳ) | `int` | Tràn số nếu vượt dải số nguyên 32-bit. | Giữ nguyên `int` theo chuẩn `SequenceRecord` Phase B. | Kiểm tra không null; chấp nhận số âm và không liên tục. |
| **Compact Bit Index** | $[0, |SD| - 1]$ | `int` | Không có ($|SD| \le \text{Integer.MAX\_VALUE}$). | Chỉ số liên tục từ 0. | `assert index >= 0 && index < sequenceCount`. |
| **Itemset Position** | $[1, |S|]$ | `int` | Không có (chiều dài chuỗi $< 2^{31}-1$). | Vị trí 1-based lưu trong mảng `int[]`. | `assert position >= 1`. |
| **Bitmap Word Count** | $[0, \lceil |SD| / 64 \rceil]$ | `int` | Tràn số nếu $|SD| + 63$ tràn `int` (khi `sequenceCount` lớn). | Tính an toàn qua `long`: `wordCountLong = ((long) sequenceCount + 63L) >>> 6`. Hỗ trợ canonical 0 khi `sequenceCount == 0`. | Kiểm tra `sequenceCount >= 0`, `wordCountLong <= MAX_ARRAY_LENGTH`, ép kiểu `(int)` có kiểm soát; báo lỗi giới hạn mảng. |
| **Cardinality / Support** | $[0, |SD|]$ | `int` | Không có (luôn $\le |SD|$). | Dùng `int` cho support và cardinality. | `assert support >= 0 && support <= sequenceCount`. |
| **Occurrence Count** | $[0, \sum |s|]$ | `long` | Có thể vượt `int` trên CSDL rất lớn. | Dùng `long` cho tổng số lần xuất hiện item. | Dùng `Math.addExact` khi cộng dồn tần suất. |
| **Candidate Counters** | $[0, \infty)$ | `long` | Không gian mẫu tổ hợp lớn có thể vượt `int`. | Dùng `long` cho toàn bộ các bộ đếm thống kê. | Dùng `Math.addExact` trong accumulator; ném `ArithmeticException` nếu tràn. |
| **Thời Gian (Nanos)** | $[0, \infty)$ | `long` | Không có (nanotime an toàn trong hàng trăm năm). | Dùng `long` cho `System.nanoTime()`. | `Math.max(0L, end - start)`. |
| **Heap Memory (Bytes)** | $[0, \text{MaxHeap}]$ | `long` | Không có. | Dùng `long` biểu diễn byte bộ nhớ. | `assert bytes >= 0L`. |
| **Quy Đổi MB** | $[0, \infty)$ | `double` | Sai số chia thực tế không đáng kể. | `bytes / 1_000_000.0` (Decimal SI theo chuẩn Phase B). | Cấm dùng 1024*1024 để bảo đảm tương thích với baseline. |
| **Global MeanW** | $[0.0, 1.0]$ | `double` | Sai số biểu diễn dấu phẩy động IEEE 754. | Tính $MeanW = (MaxW + MinW) / 2.0$ bằng `double`. | `Double.isFinite(meanW) && meanW >= 0.0`. |
| **Effective Threshold** | $[0.0, |SD|]$ | `double` | Sai số chuyển đổi ngưỡng tương đối. | Giữ nguyên ngưỡng số thực: $effectiveMinSup = (\text{percentage} / 100.0) \times |SD|$ (mode PERCENT) hoặc giá trị tuyệt đối không co giãn (mode ABSOLUTE). Tuyệt đối không làm tròn số nguyên (không làm tròn lên, làm tròn xuống, hay làm tròn gần nhất). `ResolvedMinimumSupport.meetsThreshold` dùng so sánh bao hàm: $support \times MeanW \ge effectiveThreshold$. | Không làm tròn số nguyên; giữ threshold là số thực; comparator Java `double` vẫn `UNRESOLVED` (không tự ý thêm epsilon). |
| **Weighted Upper Bound** | $[0.0, |SD|]$ | `double` | Trên mô hình số thực: $cardinality \times MeanW < effectiveMinSup \implies WSUP(P) < effectiveMinSup$. | **Bất đẳng thức nghiêm ngặt $<$. Tuyệt đối cấm cắt tỉa tại đẳng thức ($==$).** | **STOP:** Không tuyên bố Java `double` comparator an toàn tuyệt đối. Chính sách comparator vẫn `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`; không chọn epsilon cố định hay relative epsilon; không scale/integerize khi chưa có chứng minh; cấm triển khai Java pruning decision tại boundary của C06. |
| **Zero Threshold (R6)** | $effectiveMinSup == 0$| `double` | Phá vỡ tính an toàn của operational pruning. | **Cấm áp dụng pruning** khi $effectiveMinSup == 0$. | R6 tiếp tục `OPEN`; trả về `canPrune = false` tại ngưỡng 0. |

---

## 15. Statistics Contract and Invariants (Hợp Đồng Thống Kê và Bất Biến Hạch Toán Ứng Viên)

### 15.1. Danh Mục Các Bộ Đếm và Sự Kiện Tăng Xác Định
Thống kê của WSPM_BitPrune được định nghĩa bằng các sự kiện cụ thể, không đếm trùng lặp và áp dụng cơ chế điều hướng lỗi đầu tiên (**First-Failure Routing**):

1. `sequenceCount`: Số lượng chuỗi thực tế trong CSDL ($|SD|$).
2. `f1Count`: Số lượng mẫu 1-pattern có trọng số đạt ngưỡng ($|F1|$), tương đương số nút con trực tiếp của root trong cây Prefix Tree.
3. `generatedExtensions`: Tổng số ứng viên mở rộng cấu trúc thực tế được sinh ra từ các nút cha trong quá trình duyệt DFS. (Lưu ý: Không bao gồm $F1$).
4. `generatedIExtensions`: Số ứng viên sinh ra từ phép mở rộng I-Extension.
5. `generatedSExtensions`: Số ứng viên sinh ra từ phép mở rộng S-Extension.
6. `cmapRejected`: Số ứng viên bị loại bỏ ngay tại **Tầng 1 (CMAP Filter)** do vi phạm quan hệ đồng xuất hiện cần thiết.
7. `bitmapRejected`: Số ứng viên vượt qua Tầng 1 nhưng bị loại bỏ tại **Tầng 2 (Weighted Upper-Bound Filter)** do $cardinality \times MeanW < effectiveMinSup$.
8. `exactRejected`: Số ứng viên vượt qua Tầng 2, đi vào Tầng 3 và xác định được exact support, nhưng bị loại do không đạt điều kiện chấp nhận cuối cùng ($exactSupport \times MeanW < effectiveMinSup$).
9. `acceptedExtensions`: Số ứng viên mở rộng vượt qua Tầng 3 và được chấp nhận là mẫu tuần tự có trọng số hợp lệ.
10. `expandedNodes`: Số lượng nút mẫu hợp lệ thực sự được chọn làm nút cha để mở rộng DFS (không tính nút gốc rỗng).
11. `patternCount`: Tổng số mẫu tuần tự có trọng số được lưu trong kết quả cuối cùng.
12. `exactVerifications`: **Số lượng ứng viên mở rộng (extension candidates) thực sự đi vào bước kiểm chứng thời gian chính xác (Tầng 3).** (Tuyệt đối không đếm số lượng SID được kiểm tra, số phép nhúng, số endpoints, số phép so sánh vị trí, hay số lần lặp bên trong verifier vào biến đếm này).

### 15.2. Các Bất Biến Thống Kê Bắt Buộc và Điều Kiện Tiên Quyết (Strict Accounting Invariants & Preconditions)

#### Phân Định Biến Đếm Cơ Sở và Chỉ Số Dẫn Xuất:
- Hệ thống duy trì đúng **12 biến đếm cơ sở (primary counters)** độc lập như đã định nghĩa ở Mục 15.1.
- `rejectedExtensions` **không phải là biến đếm cơ sở thứ 13**, mà là một **chỉ số dẫn xuất (derived statistic / convenience property)** được tính toán tất định theo công thức:
  $$\text{rejectedExtensions} = \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected}$$

#### Các Phương Trình Bất Biến Bắt Buộc:
Mọi lần chạy hoàn tất của WSPM_BitPrune bắt buộc phải thỏa mãn các phương trình bất biến sau; vi phạm bất kỳ đẳng thức nào sẽ khiến quá trình khởi tạo kết quả ném `IllegalStateException`:

$$\begin{aligned}
\text{generatedExtensions} &= \text{generatedIExtensions} + \text{generatedSExtensions} \\
\text{generatedExtensions} &= \text{cmapRejected} + \text{bitmapRejected} + \text{exactRejected} + \text{acceptedExtensions} \\
\text{exactVerifications} &= \text{exactRejected} + \text{acceptedExtensions} \\
\text{patternCount} &= \text{f1Count} + \text{acceptedExtensions}
\end{aligned}$$

#### Điều Kiện Tiên Quyết Áp Dụng Bất Biến (Invariant Preconditions):
1. **Đối với đẳng thức $\text{exactVerifications} = \text{exactRejected} + \text{acceptedExtensions}$:**
   - Chỉ áp dụng khi contract bảo đảm:
     a) Mọi candidate đi vào exact stage đều hoàn thành trọn vẹn bước exact verification.
     b) Mỗi candidate đó nhận đúng một terminal outcome duy nhất: hoặc `EXACT_REJECTED`, hoặc `ACCEPTED_EXTENSION`.
     c) Không tồn tại bất kỳ outcome thứ ba nào trong một completed mining run.
     d) Bất kỳ run nào bị dừng sớm do ngoại lệ (`Exception`) hoặc bị hủy bỏ (`Abort`) đều tuyệt đối không được đóng gói như một completed statistics object.
2. **Đối với đẳng thức $\text{patternCount} = \text{f1Count} + \text{acceptedExtensions}$:**
   - Chỉ áp dụng khi quá trình sinh ứng viên là duy nhất (canonical candidate generation không sinh trùng lặp) và mỗi mẫu được chấp nhận được chèn vào cây Prefix Tree và kết quả chính xác một lần duy nhất (không có duplicate insertion).

### 15.3. Quy Tắc Phân Biệt Cắt Tỉa Ứng Viên và Cắt Tỉa Cây Con
- Các biến đếm `cmapRejected`, `bitmapRejected`, `exactRejected` **chỉ đếm các ứng viên trực tiếp sinh ra** bị loại tại tầng tương ứng.
- Tuyệt đối không suy diễn hay đếm các hậu duệ tiềm năng trong không gian tìm kiếm chưa từng được sinh ra vào các biến đếm này.

---

## 16. Result Retention Contract (Hợp Đồng Lưu Trữ Kết Quả Khai Phá)

Để đáp ứng cả hai mục tiêu: nghiên cứu kiểm toán chi tiết (auditability) và thực nghiệm hiệu năng cao (benchmark scalability), kiến trúc WSPM_BitPrune đề xuất hai chế độ lưu trữ kết quả độc lập thông qua enum `BitPruneRetentionPolicy` (cả hai chế độ đều ở mức **`PROPOSED API`**):

### 16.1. Chế Độ `SUPPORT_ONLY` (Khuyên dùng cho Benchmark và Sản Phẩm)
- **Dữ liệu lưu giữ trong kết quả:**
  1. Cấu trúc cây tiền tố đóng băng `PrefixTree` (gồm cấu trúc cha-con, loại mở rộng).
  2. Danh sách mẫu tuần tự được chấp nhận theo thứ tự duyệt trước tất định (deterministic pre-order patterns).
  3. Bảng ánh xạ hỗ trợ chính xác bất biến `Map<Pattern, Integer> supports`.
  4. Toàn bộ siêu dữ liệu: $MeanW$, số lượng item phân biệt, `ResolvedMinimumSupport`, thống kê khai phá `BitPruneStatistics`.
- **Dữ liệu giải phóng:**
  - Toàn bộ Candidate SID Bitmaps và trạng thái vị trí (`PatternEndpoints`) trung gian được giải phóng ngay sau khi nhánh con DFS (subtree) không còn cần chúng để sinh hậu duệ.
  - Không giữ lại bất kỳ mảng vị trí hay bitmap nào trong bộ nhớ heap sau khi hoàn tất khai phá.

### 16.2. Chế Độ `FULL_ENCODING` (Khuyên dùng cho Nghiên Cứu và Differential Audit)
- **Dữ liệu lưu giữ:**
  - Lưu giữ toàn bộ dữ liệu của `SUPPORT_ONLY`.
  - Bổ sung bảng ánh xạ lưu trữ trạng thái kiểm toán bản địa của BitPrune cho từng mẫu được chấp nhận thông qua kiểu dữ liệu đề xuất rõ ràng:
    `PROPOSED BitPrunePatternState`:
    - `SidBitmap candidateBitmap`: Candidate SID Bitmap đại diện cho chặn trên hỗ trợ (upper-bound bitmap).
    - `int[] supportingSids` (hoặc `Set<Integer>`): Tập hợp các actual SID phân biệt thực sự hỗ trợ mẫu sau bước kiểm chứng thời gian. (Phân biệt rạch ròi giữa candidate bitmap upper bound và exact supporting SID set!).
    - `Map<Integer, int[]>` (hoặc cấu trúc immutable tương đương: actual SID $\to$ sorted distinct feasible endpoints): Trạng thái vị trí All-Endpoints toàn vẹn $\mathcal{E}(P, s)$ cho từng supporting SID; bảo toàn toàn bộ feasible endpoints, tuyệt đối không dùng earliest-only hay latest-only.
- **Ranh giới cấm tuyệt đối:**
  1. Tuyệt đối không dùng `PatternEncoding`.
  2. Tuyệt đối không dùng Prime-block.
  3. Tuyệt đối không nhập (`import`) bất kỳ kiểu dữ liệu nào từ gói `vn.edu.wspm.prism.*`.
- **Quản lý vòng đời trong DFS:**
  - Tuyệt đối không giải phóng positional state của nút cha khi luồng duyệt DFS vẫn cần nó để sinh hậu duệ (khi các nhánh con của nút cha đó chưa duyệt xong).

### 16.3. Bất Biến Tương Đương Kết Quả Giữa Hai Chế Độ
1. Với cùng một CSDL và cấu hình minsup, cả hai chế độ `SUPPORT_ONLY` và `FULL_ENCODING` **bắt buộc phải tạo ra cùng**:
   - `acceptedPatterns` (thứ tự và danh sách mẫu chấp nhận đồng nhất 100%),
   - `supports` (`Map<Pattern, Integer>` đồng nhất 100%),
   - Cấu trúc cây tiền tố `PrefixTree` đồng nhất 100%,
   - Kết quả thống kê `BitPruneStatistics` đồng nhất 100%:
   $$\text{Result}_{\text{SUPPORT\_ONLY}}.\text{supports}() \equiv \text{Result}_{\text{FULL\_ENCODING}}.\text{supports}()$$
2. Chính sách lưu trữ kết quả (`BitPruneRetentionPolicy`) **tuyệt đối không được làm thay đổi** thứ tự duyệt DFS hay bất kỳ quyết định cắt tỉa / chấp nhận mẫu nào.

---

## 17. Measurement Contract (Hợp Đồng Đo Lường Thời Gian và Bộ Nhớ)

Để bảo đảm tính công bằng khoa học tuyệt đối khi so sánh thực nghiệm với baseline Phase B, WSPM_BitPrune kế thừa nguyên vẹn giao thức đo lường 6 scope thời gian và bộ nhớ heap của Phase B:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   TOTAL EXECUTION TIME (Scope 6)                       │
│             (Total Scope: Excludes console printing)                   │
├───────────────────┬────────────────────────────────┬───────────────────┤
│ 1. Dataset I/O    │ 4. Complete Algorithm Time     │ 5. Output I/O     │
│    Time           ├───────────────┬────────────────┤    Time           │
│    (Đọc file CSDL)│ 2. Preprocess │ 3. DFS Mining  │    (Ghi file mẫu) │
│                   │    Time       │    Time        │                   │
└───────────────────┴───────────────┴────────────────┴───────────────────┘
```

### 17.1. Định Nghĩa 6 Scope Đo Lường Thời Gian Chuẩn Hóa
Sáu scope đo lường thời gian không phải là các nhóm "độc lập" mà có quan hệ phân cấp và bao chứa được định nghĩa rõ ràng:
1. **Dataset I/O Time (Scope 1):** Thời gian đọc và phân tích cú pháp CSDL từ tệp đĩa thành `SequenceDatabase`. Không bao gồm thời gian khởi tạo thuật toán hay tiền xử lý.
2. **Preprocessing Time (Scope 2):** Thời gian tiền xử lý dữ liệu: tính toán thống kê item, trọng số $w(i)$, hằng số toàn cục $MeanW$, giải quyết ngưỡng `ResolvedMinimumSupport`, lọc các mẫu $F1$, xây dựng ánh xạ song ánh `SidMapping`, xây dựng Singleton Bitmaps, `VerticalOccurrenceIndex`, và hai ma trận co-occurrence `CmapI`, `CmapS`.
3. **DFS Mining Time (Scope 3):** Thời gian thực thi đệ quy duyệt không gian tìm kiếm DFS: sinh ứng viên mở rộng chuẩn tắc (I-Extension và S-Extension), lọc đa tầng (CMAP filter, Bitmap upper-bound filter), kiểm chứng thời gian chính xác (Exact Temporal Verification), chấp nhận mẫu và mở rộng nút trên cây Prefix Tree.
4. **Complete Algorithm Time (Scope 4):** Tổng thời gian thực thi thuật toán: bao gồm Preprocessing Time (Scope 2) + DFS Mining Time (Scope 3) + đóng băng cây Prefix Tree và hoàn tất tạo đối tượng kết quả `BitPruneMiningResult`.
5. **Output I/O Time (Scope 5):** Thời gian xuất danh sách mẫu và độ hỗ trợ chính xác ra tệp đĩa (khi `EXPORT_PATTERNS == true`). Mặc định bằng 0 nếu xuất file bị tắt.
6. **Total Execution Time (Scope 6):** Thời gian thực thi toàn trình:
   $$\text{Total Execution Time} = \text{Dataset I/O} + \text{Complete Algorithm} + \text{Output I/O} + \text{coordination overhead}$$
   **Ranh giới đo lường tuyệt đối (Total Scope): Định dạng console và in ấn kết quả/thống kê ra màn hình (`Console formatting and console printing`) NẰM HOÀN TOÀN NGOÀI PHẠM VI ĐO LƯỜNG (`Total Scope: Excludes console printing`), khớp chính xác 100% với chuẩn mực Phase B (`MainWspmPreTree.java` và `docs/research/phase-b-reproducibility.md`).**

### 17.2. Giao Thức Đo Lường Bộ Nhớ (Memory Measurement Protocol)
- Sử dụng [`ExecutionMemorySampler`](file:///e:/University/HK1-2026-2027/Khoa-Luan/wspm-pretree-bitprune/src/main/java/vn/edu/wspm/measurement/ExecutionMemorySampler.java) thuộc sở hữu của caller để lấy mẫu định kỳ heap sử dụng thông qua `java.lang.management.MemoryMXBean.getHeapMemoryUsage().getUsed()`.
- Chu kỳ lấy mẫu mặc định: `5 ms`.
- Báo cáo rõ ràng:
  - `Heap Before Mining (MB)`
  - `Heap After Mining (MB)`
  - `Observed Peak Heap (MB)` (giá trị lớn nhất quan sát được qua các lần lấy mẫu định kỳ).
- **Quy tắc khoa học trung thực:**
  - Không tự ý gọi `System.gc()` để làm giả đỉnh bộ nhớ.
  - Luôn ghi chú rõ: Bộ nhớ heap được lấy mẫu qua JVM MemoryMXBean là xấp xỉ mức tiêu thụ heap của máy ảo, không phải là kích thước trang nhớ thực tế (Resident Set Size - RSS) của hệ điều hành.
  - Đơn vị MB được tính theo chuẩn thập phân SI: $\text{MB} = \text{bytes} \mathbin{/} 1{,}000{,}000.0$.

---

## 18. Correctness and Test Strategy (Chiến Lược Kiểm Thử Tính Đúng Đắn)

Chiến lược kiểm thử được thiết kế toàn diện, tập trung vào việc ngăn chặn rủi ro false negative và bảo đảm tính tương đương vi sai:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   CORRECTNESS & TEST STRATEGY                          │
├────────────────────────────────────────────────────────────────────────┤
│ TẦNG 1: UNIT & BOUNDARY TESTS (C03 - C08)                              │
│ - SID âm, SID không liên tục, CSDL rỗng, CSDL 1, 63, 64, 65 chuỗi      │
│ - Zero-padding bits ngoài |SD|, empty bitmap, same-universe validation │
│ - Vi sai Bitmap AND/cardinality với java.util.Set<Integer>             │
│ - Item lặp lại, đa phép nhúng, phân biệt <(a)(b)> và <(ab)>            │
│ - Phản ví dụ CE-01 (Earliest fail), CE-02 (Latest fail), CE-03, CE-04  │
│ - Ma trận CMAP-I, CMAP-S đối xứng/hướng, trường hợp CMAP-S(a,a)        │
│ - Bất đẳng thức nghiêm ngặt '<' vs biên bằng, placeholder comparator   │
├────────────────────────────────────────────────────────────────────────┤
│ TẦNG 2: ENGINE & INTEGRATION TESTS (C09 - C10)                         │
│ - Thứ tự sinh ứng viên chuẩn tắc (I trước, S sau)                      │
│ - Bất biến Prefix Tree: không trùng lặp nút, đóng băng read-only       │
│ - Tính tương đương giữa SUPPORT_ONLY và FULL_ENCODING                  │
│ - Kiểm thử chặn pruning tại threshold = 0 theo R6                      │
├────────────────────────────────────────────────────────────────────────┤
│ TẦNG 3: DIFFERENTIAL ORACLE TESTS (C11, C16)                           │
│ - Table 1 Oracle (Bài báo 2023): So khớp 9 mẫu tuần tự có trọng số     │
│ - Differential với Brute-Force Oracle trên CSDL mini ngẫu nhiên        │
│ - Differential tuyệt đối với WSPM_PreTree Baseline trên Snake, Sign,   │
│   Pumsb: So khớp chính xác từng cặp (Pattern -> exactSupport)          │
└────────────────────────────────────────────────────────────────────────┘
```

### Danh Mục Các Ca Kiểm Thử Cụ Thể Được Thiết Kế Cho Các Task Tiếp Theo:
1. **Kiểm thử SID Boundary (C03):** CSDL với actual SID $\{-5, 0, 12, 100\}$, kiểm chứng `SidMapping` bảo toàn tính song ánh và đếm đúng support.
2. **Kiểm thử Word-Alignment Boundary (C03 / TV-09):** CSDL $|SD| = 65$, chuỗi 65 chứa item mục tiêu, kiểm chứng bit 64 bật và các bit 65–127 luôn bằng 0.
3. **Kiểm thử Differetial Bitmap AND (C03):** So sánh kết quả `bitmapA.and(bitmapB).cardinality()` với `setA.retainAll(setB).size()` trên 1,000 vector ngẫu nhiên.
4. **Kiểm thử CMAP-S Phản Xạ (C05):** Chuỗi $\langle (a)(a) \rangle \implies \text{CMAP-S}(a, a) == \text{true}$; chuỗi $\langle (a) \rangle \implies \text{CMAP-S}(a, a) == \text{false}$.
5. **Kiểm thử All-Endpoints Trọng Yếu (C07, C08 / CE-01, CE-02):**
   - **Tái lập phản ví dụ chuẩn CE-01 (Bác bỏ Earliest-Only cho I-Extension):**
     - CSDL gồm 1 chuỗi với $sid = 1$: $S_1 = \langle (a), (ab) \rangle$ (vị trí 1 có itemset $(a)$, vị trí 2 có itemset $(ab)$).
     - Pattern tiền tố: $P = \langle(a)\rangle$.
     - Tập điểm kết thúc khả dĩ: $\mathcal{E}(P, S_1) = \{1, 2\}$ (vì $a \in s_1$ và $a \in s_2$).
     - Ứng viên I-Extension: $P' = P \oplus_I b = \langle(ab)\rangle$.
     - Phân tích rủi ro False Negative: Nếu áp dụng chính sách Earliest-Only, hệ thống chỉ lưu $j^{min} = 1$ và loại bỏ điểm mốc 2. Tại vị trí 1, $s_1 = (a)$ không chứa $b$, thuật toán kết luận $support(\langle(ab)\rangle) = 0$. Nhưng trên thực tế tại vị trí 2, $s_2 = (ab)$ chứa cả $a$ và $b$, do đó $\text{SupportingSIDs}(P') = \{1\} \implies exactSupport = 1$. Việc nén earliest-only làm mất điểm mốc 2 và gây False Negative nghiêm trọng.
     - Kết luận: Bắt buộc duy trì All-Endpoints $\mathcal{E}(P, S_1) = \{1, 2\}$ để I-Extension tìm thấy nhúng hợp lệ tại vị trí 2.
   - **Tái lập phản ví dụ chuẩn CE-02 (Bác bỏ Latest-Only cho S-Extension):**
     - CSDL gồm 1 chuỗi với $sid = 1$: $S_1 = \langle (a), (b), (a) \rangle$ (vị trí 1: $(a)$, vị trí 2: $(b)$, vị trí 3: $(a)$).
     - Pattern tiền tố: $P = \langle(a)\rangle$.
     - Tập điểm kết thúc khả dĩ: $\mathcal{E}(P, S_1) = \{1, 3\}$ (vì $a \in s_1$ và $a \in s_3$).
     - Ứng viên S-Extension: $P' = P \oplus_S b = \langle(a)(b)\rangle$.
     - Phân tích rủi ro False Negative: Nếu áp dụng chính sách Latest-Only, hệ thống chỉ lưu $j^{max} = 3$ và loại bỏ điểm mốc 1. Sau vị trí 3 không còn itemset nào chứa $b$, thuật toán kết luận $support(\langle(a)(b)\rangle) = 0$. Nhưng trên thực tế, nhúng của $a$ tại vị trí 1 cho phép mở rộng sang $b$ tại vị trí 2 ($1 < 2$), do đó $\text{SupportingSIDs}(P') = \{1\} \implies exactSupport = 1$. Việc nén latest-only làm mất điểm mốc 1 và gây False Negative nghiêm trọng.
     - Kết luận: Bắt buộc duy trì All-Endpoints $\mathcal{E}(P, S_1) = \{1, 3\}$ để S-Extension tìm thấy nhúng hợp lệ tại vị trí 2.
6. **Kiểm thử Ranh Giới Bằng Ngưỡng (C06 / TV-12):** Ứng viên có $cardinality \times MeanW == effectiveMinSup$ **không được phép bị cắt tỉa**.
7. **Kiểm thử Ngưỡng Bằng Không Bị Chặn (C06 / TV-13 / R6):** Khi cấu hình $min\_sup = 0$, bộ lọc upper-bound không thực hiện cắt tỉa.
8. **Kiểm Thử Oracle Table 1 (C11):** Chạy WSPM_BitPrune trên CSDL Bảng 1 của bài báo 2023 (`src/test/resources/datasets/wspm/table1.spmf`) với cấu hình chuẩn mực của repository:
   - **Tệp dữ liệu:** `src/test/resources/datasets/wspm/table1.spmf`
   - **Số lượng chuỗi ($|SD|$):** 7 chuỗi (actual SIDs: $1, 2, 3, 4, 5, 6, 7$)
   - **Số item phân biệt:** 8 items (items $1..8$, tổng độ dài CSDL = 66)
   - **Trọng số bài báo:** $MaxW = 23/66$, $MinW = 1/66 \implies MeanW = 12/66 \approx 0.1818181818$
   - **Ngưỡng cấu hình:** 14% (`MinimumSupport.percentage(14.0)`)
   - **Ngưỡng hiệu dụng số thực:** $effectiveMinSup = (14.0 / 100.0) \times 7 = 0.98$ (không làm tròn số nguyên)
   - **Mẫu 1-pattern F1:** $\langle(1)\rangle: 7$, $\langle(2)\rangle: 7$, $\langle(3)\rangle: 6$
   - **So khớp chuẩn tắc:** So khớp bắt buộc phải sử dụng `Map<Pattern, Integer>` bất biến hoàn chỉnh, không chỉ so khớp số lượng mẫu (`patternCount = 9`). Bảng ánh xạ chính xác 9 mẫu tuần tự có trọng số:
     - $\langle(1)\rangle \to 7$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6, 7\}$)
     - $\langle(2)\rangle \to 7$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6, 7\}$)
     - $\langle(3)\rangle \to 6$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6\}$)
     - $\langle(1\ 2)\rangle \to 7$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6, 7\}$)
     - $\langle(1)(1)\rangle \to 6$ (Supporting SIDs: $\{1, 2, 4, 5, 6, 7\}$)
     - $\langle(1)(2)\rangle \to 7$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6, 7\}$)
     - $\langle(1)(3)\rangle \to 6$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6\}$)
     - $\langle(2)(3)\rangle \to 6$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6\}$)
     - $\langle(1\ 2)(3)\rangle \to 6$ (Supporting SIDs: $\{1, 2, 3, 4, 5, 6\}$)
   - Khi chạy ở chế độ `FULL_ENCODING`, kiểm chứng bổ sung tập supporting SIDs thực tế của từng mẫu khớp chính xác với danh sách SID ở trên.

---

## 19. Traceability Matrix (Ma Trận Truy Vết OBL-01 đến OBL-08)

Bảng ma trận truy vết từ các nghĩa vụ chứng minh toán học của C01.2 sang các thành phần thiết kế kiến trúc C02, task triển khai và task kiểm chứng tương ứng:

| Mã Nghĩa Vụ (OBL Canonical) | Khẳng Định Toán Học Được Duyệt Trong C01.2 | Thành Phần Kiến Trúc C02 Đảm Nhận | Task Triển Khai Dự Kiến | Task Kiểm Chứng Dự Kiến | Trạng Thái Rủi Ro & Điều Kiện Chặn |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OBL-01**<br>Support Anti-Monotonicity | $P \sqsubseteq Q \implies \text{SupportingSIDs}(Q) \subseteq \text{SupportingSIDs}(P) \implies support(Q) \le support(P)$. Hậu duệ có support không tăng. | `vn.edu.wspm.bitprune.mining.BitPruneCandidateEngine`, `vn.edu.wspm.bitprune.mining.BitPruneMiner` | C09, C10 | C10, C11, C12 | **APPROVED / PROVED IN C01.2**.<br>Cơ sở lý thuyết cho cắt tỉa nhánh con (subtree pruning) khi $support = 0$ và $effectiveMinSup > 0$. |
| **OBL-02**<br>Global-MeanW WSUP Anti-Monotonicity | $MeanW \ge 0 \implies WSUP(Q) \le WSUP(P)$. Nếu $WSUP(P) < effectiveMinSup$ thì mọi hậu duệ $Q$ đều có $WSUP(Q) < effectiveMinSup$. | `vn.edu.wspm.bitprune.verification.BitmapUpperBoundFilter`, `vn.edu.wspm.bitprune.mining.BitPruneMiner` | C06, C10 | C06, C10, C11 | **APPROVED / PROVED IN C01.2**.<br>Cắt tỉa subtree an toàn khi $effectiveMinSup > 0$ và MeanW là hằng số toàn cục. |
| **OBL-03**<br>SID Bitmap Cardinality Upper Bound | $\text{SupportingSIDs}(P) \subseteq CandidateBitmapSID(P) \implies support(P) \le cardinality(BM(P))$. | `vn.edu.wspm.bitprune.bitmap.SidBitmap`, `SidMapping` | C03 | C03, C06, C11 | **APPROVED / PROVED IN C01.2**.<br>Bảo đảm zero-padding cho bit ngoài $|SD|$; hỗ trợ empty SID universe. |
| **OBL-04**<br>Weighted Upper-Bound Pruning | $cardinality(BM(P)) \times MeanW < effectiveMinSup \implies WSUP(P) < effectiveMinSup$. | `vn.edu.wspm.bitprune.verification.BitmapUpperBoundFilter` | C06 | C06, C11 | **APPROVED ON REALS UNDER ASSUMPTIONS**.<br>**BLOCKER:** Comparator cho Java `double` vẫn `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`; cấm triển khai Java pruning decision tại boundary của C06. |
| **OBL-05**<br>CMAP-I Necessary Condition | $\text{CMAP-I}(a, x) = false \implies support(P \oplus_I x) = 0$ (với $a = \text{lastItem}(p_k), a < x$). | `vn.edu.wspm.bitprune.cmap.CmapI` | C05 | C05, C10, C11 | **APPROVED (ĐỊNH LÝ TOÁN HỌC)**.<br>Operational candidate & subtree pruning an toàn theo OBL-01/OBL-02/R-02 khi $effectiveMinSup > 0$; tại minsup = 0 bị chặn bởi R6. Cấm cắt tỉa cây con của nút cha $P$. |
| **OBL-06**<br>CMAP-S Necessary Condition | $\text{CMAP-S}(a, x) = false \implies support(P \oplus_S x) = 0$ (với $a = \text{lastItem}(p_k)$, kể cả $a = x$). | `vn.edu.wspm.bitprune.cmap.CmapS` | C05 | C05, C10, C11 | **APPROVED (ĐỊNH LÝ TOÁN HỌC)**.<br>Bắt buộc hỗ trợ $a=x$ cho item lặp lại; operational candidate & subtree pruning an toàn theo OBL-01/OBL-02/R-03 khi $effectiveMinSup > 0$; tại minsup = 0 bị chặn bởi R6. Cấm cắt tỉa cây con của nút cha $P$. |
| **OBL-07**<br>Exact Temporal Verification and Endpoint Compression | $P \sqsubseteq s \iff \mathcal{E}(P, s) \ne \emptyset$. Duy trì toàn bộ feasible endpoints $\mathcal{E}(P, s)$ là điều kiện cần và đủ để nối thời gian chính xác, bảo toàn nhúng mở rộng. | `vn.edu.wspm.bitprune.index.PatternEndpoints`, `vn.edu.wspm.bitprune.verification.ExactTemporalVerifier` | C04, C07, C08 | C04, C07, C08, C11 | **APPROVED (ALL-ENDPOINTS PROVED SAFE)**.<br>Bác bỏ triệt để earliest-only (CE-01) và latest-only (CE-02). |
| **OBL-08**<br>Zero-Threshold and Input Boundaries | Phân tích 8 tình huống biên về minsup và dữ liệu đầu vào theo C01.2: Cắt tỉa có điều kiện khi $effectiveMinSup > 0$; cấm cắt tỉa tại equality ($==$); cấm operational pruning tại threshold 0 do R6. Với CSDL rỗng ($|SD| = 0$): Parser có thể tạo database hợp lệ rỗng; Resolver từ chối mode PERCENT (`IllegalArgumentException`) và giữ nguyên mode ABSOLUTE; `ItemWeightCalculator` trả về `empty()`; Pipeline dừng trước DFS (không có MeanW/search space rỗng), không sinh pattern/statistics giả. | `vn.edu.wspm.config.MinimumSupportResolver`, `vn.edu.wspm.statistics.ItemWeightCalculator`, `vn.edu.wspm.bitprune.verification.BitmapUpperBoundFilter`, `vn.edu.wspm.bitprune.mining.BitPruneMiner` | C06, C10 | C06, C10, C11, C16 | **APPROVED UNDER CONDITIONAL ASSUMPTIONS**.<br>**BLOCKER:** R6 (`Zero Minimum Support Semantics`) tiếp tục `OPEN`. |

---

## 20. C03…C16 Handoff Matrix (Ma Trận Bàn Giao Triển Khai)

Kế hoạch chi tiết phân công trách nhiệm cho các task tiếp theo trong Phase C:

| Task ID | Tên Task | Đầu Vào Nhận Từ C02 | Deliverables Cần Tạo | Điều Kiện Tiên Quyết Phải Đáp Ứng |
| :--- | :--- | :--- | :--- | :--- |
| **C03** | SID Bitmap Representation & Bounds | Mục 8, 9, 14: Hợp đồng `SidMapping`, `SidBitmap`, mảng `long[]`, zero-padding, tính `wordCountLong` an toàn qua `long`, hợp đồng empty SID universe (`words.length == 0`). | Mã nguồn `SidMapping.java`, `SidBitmap.java` và bài kiểm thử JUnit tương ứng. | C02 được Reviewer `ACCEPTED`. Không phụ thuộc PRISM. (OBL-03) |
| **C04** | Vertical Occurrence Index | Mục 8, 10: Hợp đồng `VerticalOccurrenceIndex`, `PatternEndpoints` (All-Endpoints). | Mã nguồn `VerticalOccurrenceIndex.java`, `PatternEndpoints.java` và JUnit test. | C03 `ACCEPTED`. Bảo toàn toàn bộ feasible endpoints. (OBL-07) |
| **C05** | CMAP-I / CMAP-S Construction | Mục 8, 11: Hợp đồng ma trận `CmapI` (OBL-05), `CmapS` (OBL-06), điều kiện cần co-occurrence. | Mã nguồn `CmapI.java`, `CmapS.java`, `CmapBuilder.java` và JUnit test. | C03 `ACCEPTED`. Hỗ trợ quan hệ $a=b$ cho CMAP-S. (OBL-05, OBL-06) |
| **C06** | Bitmap Candidate Upper-Bound Filter | Mục 8, 14, 19: Hợp đồng `BitmapUpperBoundFilter`, bất đẳng thức $<$, cấm cắt tỉa tại đẳng thức, điều kiện chặn R6. | Mã nguồn `BitmapUpperBoundFilter.java` và JUnit test bao phủ ranh giới. | **Tiền đề:** Phải có văn bản giải quyết chính sách Java `double` comparator trước khi bắt đầu. (OBL-03, OBL-04, OBL-08) |
| **C07** | Exact I-Extension Temporal Join | Mục 8, 10: Hợp đồng so khớp cùng itemset của `ExactTemporalVerifier`. | Mã nguồn phương thức nối I-Extension và JUnit test với CE-01, CE-02. | C04 `ACCEPTED`. Kết quả khớp oracle độc lập. (OBL-07) |
| **C08** | Exact S-Extension Temporal Join | Mục 8, 10: Hợp đồng so khớp itemset sau nghiêm ngặt ($p > e$) của `ExactTemporalVerifier`.| Mã nguồn phương thức nối S-Extension và JUnit test với CE-01, CE-02. | C04 `ACCEPTED`. Đếm distinct SID duy nhất. (OBL-07) |
| **C09** | Candidate Generation & Prefix Tree | Mục 8, 12: Hợp đồng `BitPruneCandidateEngine`, thứ tự sinh chuẩn tắc, tích hợp `PrefixTree`.| Mã nguồn `BitPruneCandidateEngine.java` và JUnit test. | C07, C08 `ACCEPTED`. Tái sử dụng `PrefixTree` Phase B. (OBL-01, OBL-02) |
| **C10** | Multi-tier Lazy Pruning DFS Miner | Mục 8, 12, 13: Điều phối đệ quy DFS, kết hợp 3 tầng lọc, quản lý vòng đời stack. | Mã nguồn `BitPruneMiner.java` hoàn chỉnh và JUnit test end-to-end. | C05, C06, C09 `ACCEPTED`. Khớp oracle trên tập dữ liệu nhỏ. (OBL-01, OBL-02, OBL-05, OBL-06, OBL-07, OBL-08) |
| **C11** | Full Differential Correctness | Mục 18: Danh mục test vi sai, Oracle Bảng 1 (14%, threshold 0.98, 9 pattern $\to$ support), so sánh với WSPM_PreTree baseline. | Báo cáo kiểm thử vi sai `docs/research/wspm-bitprune-c11-differential-report.md`. | C10 `ACCEPTED`. `Map<Pattern, Integer>` đồng nhất 100%. |
| **C12** | Mining Statistics & Accounting | Mục 8, 15: Hợp đồng `BitPruneStatistics`, 12 primary counters, derived `rejectedExtensions`, preconditions cho invariants. | Mã nguồn `BitPruneStatistics.java`, `BitPruneStatisticsAccumulator.java` và test. | C10 `ACCEPTED`. Mọi ứng viên có đúng 1 terminal outcome. |
| **C13** | Time/Memory Instrumentation Parity | Mục 8, 17: Giao thức 6 scope thời gian (loại trừ console printing), `ExecutionMemorySampler`, heap sampling. | Tích hợp đo đạc vào miner và kiểm thử tính tương thích đo lường. | C10, C12 `ACCEPTED`. Không làm sai lệch kết quả khai phá. |
| **C14** | Result Retention & Stable Output | Mục 8, 16: Hợp đồng `BitPruneMiningResult`, `BitPruneRetentionPolicy` (`SUPPORT_ONLY` và `FULL_ENCODING`), `BitPrunePatternState` (audit state). | Mã nguồn `BitPruneMiningResult.java`, `BitPruneRetentionPolicy.java`, `BitPrunePatternState.java` và test. | C10 `ACCEPTED`. Kết quả mẫu và support giống nhau giữa 2 mode. |
| **C15** | Standalone Java Main Runner | Mục 8, 17: Runner độc lập `MainWspmBitPrune`, cấu hình không dùng Scanner. | Mã nguồn `MainWspmBitPrune.java` trong package `experiments`. | C13, C14 `ACCEPTED`. Chạy trực tiếp được trong IDE/terminal. |
| **C16** | Final Phase C Regression Acceptance | Toàn bộ các tiêu chí nghiệm thu của Phase C roadmap. | Báo cáo tổng kết nghiệm thu Phase C và đóng băng mã nguồn WSPM_BitPrune. | C11 đến C15 đều `ACCEPTED`. Sẵn sàng cho Phase D. |

---

## 21. Risks and Unresolved Decisions (Rủi Ro Kiến Trúc và Các Vấn Đề Mở)

### 21.1. Rủi ro sai số dấu phẩy động trong Java `double` (Floating-Point Precision Risk)
- **Mô tả:** OBL-04 chỉ được chứng minh toán học trên trường số thực $\mathbb{R}$ với bất đẳng thức nghiêm ngặt $<$. Trên máy tính sử dụng kiểu `double` 64-bit của Java, phép nhân và phép so sánh có thể xuất hiện sai số biểu diễn nhị phân IEEE 754. C02 không tuyên bố việc triển khai bằng Java `double` là an toàn tuyệt đối. Điểm đẳng thức ($==$) tuyệt đối không được cắt tỉa vì gây False Negative. Việc lựa chọn dung sai (fixed epsilon, relative epsilon) hay kỹ thuật số nguyên hóa (integerization/scaling) chưa được chứng minh an toàn ở mọi thang đo.
- **Trạng thái:** `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`.
- **Rào chắn kiến trúc:** C02 không tự ý đưa ra hằng số fixed epsilon (như $10^{-9}$), không tự ý chọn relative epsilon, và không tự ý scale/integerize threshold. Task C06 tại boundary bị khóa (BLOCKED) cho đến khi có văn bản đặc tả chính sách so sánh an toàn được Research Reviewer phê duyệt.

### 21.2. Rủi ro bộ nhớ của All-Endpoints trên CSDL có chuỗi lặp item dày đặc
- **Mô tả:** Định lý OBL-07 yêu cầu giữ toàn bộ các điểm kết thúc khả dĩ $\mathcal{E}(P, s)$. Trên các CSDL có chuỗi cực dài và một item xuất hiện hàng nghìn lần trong cùng một chuỗi, kích thước của mảng `endpoints` trong quá trình duyệt DFS có thể tăng cao.
- **Biện pháp giảm thiểu:** Trong chế độ `SUPPORT_ONLY`, các mảng `endpoints` của nút con sẽ được giải phóng ngay khi nhánh con duyệt xong. Các kỹ thuật nén mảng vị trí (như khoảng cách bit hoặc interval run-length) được bảo lưu cho nghiên cứu tối ưu hóa chuyên sâu trong tương lai, với điều kiện tiên quyết là phải tương đương toán học với OBL-07.

### 21.3. Vấn đề nghiên cứu R6: Zero Minimum Support Semantics
- **Mô tả:** Khi $min\_sup = 0$, không gian mẫu về mặt lý thuyết bao gồm mọi tổ hợp chuỗi con có thể tạo ra được từ các item trong CSDL, kể cả các mẫu không có chuỗi nào hỗ trợ ($support = 0$).
- **Trạng thái:** `OPEN`.
- **Rào chắn kiến trúc:** Kiến trúc quy định rõ ràng rằng khi $effectiveMinSup == 0$, mọi cơ chế cắt tỉa dựa trên ngưỡng hỗ trợ đều bị vô hiệu hóa; thuật toán không được phép tự tiện loại bỏ các mẫu có support = 0 trừ khi có quyết định chính thức từ hội đồng nghiên cứu.

---

## 22. Acceptance Checklist (Danh Mục Tự Kiểm Tra Nghiệm Thu C02)

- [x] **Xác minh kho chứa:** Commit HEAD `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c` và trạng thái worktree được ghi nhận chính xác, không giả định worktree sạch.
- [x] **Khảo sát mã nguồn hiện hữu:** Mọi API Phase B được khảo sát trực tiếp từ mã nguồn Java, đối chiếu constructor, visibility và signature.
- [x] **Ranh giới module rõ ràng:** Phân định dứt khoát 3 nhóm API: Reusable Shared, Protected Baseline, và Forbidden PRISM.
- [x] **Cách ly PRISM tuyệt đối:** Không có bất kỳ phụ thuộc nào từ `vn.edu.wspm.bitprune.*` sang `vn.edu.wspm.prism.*`.
- [x] **Hợp đồng SID Mapping song ánh:** Hỗ trợ SID âm và không liên tục; cấm đồng nhất actual SID với bit index.
- [x] **Hợp đồng SID Bitmap:** Quy định rõ căn lề từ máy 64-bit, zero-padding cho bit ngoài $|SD|$, xác thực cùng universe SID qua `equals`, tính `wordCountLong` an toàn qua `long` không tràn số, và hỗ trợ canonical empty SID universe (`words.length == 0`).
- [x] **Chỉ mục vị trí và All-Endpoints:** Bảo toàn item lặp lại giữa các itemset; tuân thủ nghiêm ngặt OBL-07 (lưu giữ toàn bộ feasible endpoints); bác bỏ earliest-only và latest-only.
- [x] **Phân tách CMAP:** Tách riêng `CMAP-I` (OBL-05) và `CMAP-S` (OBL-06); hỗ trợ đầy đủ `CMAP-S(a, a)`; operational pruning an toàn theo OBL-01/OBL-02/R-02/R-03 khi minsup > 0; R6 minsup = 0 tiếp tục OPEN.
- [x] **Bản chất bộ lọc điều kiện cần:** Xác định rõ Bitmap Upper Bound và CMAP là bộ lọc điều kiện cần, không bao giờ thay thế bước kiểm chứng thời gian chính xác.
- [x] **Ranh giới bằng ngưỡng và bảo toàn số thực:** Ngưỡng hiệu dụng số thực không qua làm tròn (`(percentage / 100.0) * |SD|` hoặc absolute); bất đẳng thức upper-bound sử dụng dấu nghiêm ngặt $<$; cấm cắt tỉa tại equality ($==$).
- [x] **Trạng thái an toàn số học:** Không tuyên bố Java `double` comparator an toàn tuyệt đối; chính sách comparator là `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED` và khóa C06; R6 zero-threshold tiếp tục `OPEN`.
- [x] **Hợp đồng thống kê loại trừ lẫn nhau:** Định nghĩa 12 primary counters theo first-failure routing; `rejectedExtensions` là derived statistic; `exactVerifications` chỉ đếm extension candidates đi vào Tầng 3; thỏa mãn các điều kiện tiên quyết cho các bất biến.
- [x] **Hợp đồng lưu trữ kết quả:** Đề xuất `SUPPORT_ONLY` và `FULL_ENCODING` với `BitPrunePatternState` (candidate upper-bound bitmap, exact supporting SIDs, All-Endpoints); bảo đảm tương đương 100% về tập mẫu, exact support, PrefixTree và statistics; không dùng PRISM.
- [x] **Bảo toàn giao thức đo lường:** Định nghĩa 6 scope đo lường thời gian (loại trừ hoàn toàn console printing) và lấy mẫu heap định kỳ bằng MemoryMXBean kế thừa chuẩn Phase B.
- [x] **Chiến lược kiểm thử vi sai & Oracle Table 1:** Thiết kế kiểm thử chi tiết bao phủ các trường hợp biên, phản ví dụ CE-01–CE-06 và Oracle Table 1 chính xác (7 sequences, 14%, threshold 0.98, MeanW = 12/66, 9 pattern $\to$ exact support).
- [x] **Ma trận truy vết OBL:** Khôi phục đúng danh tính chuẩn tắc OBL-01 đến OBL-08 từ C01.2 và liên kết sang các task triển khai C03–C16.
- [x] **Ma trận bàn giao C03–C16:** Xác định rõ đầu vào, deliverable và điều kiện tiên quyết cho từng task kế tiếp.
- [x] **Giới hạn phạm vi tuyệt đối:** Không viết mã nguồn Java, không viết bài kiểm thử JUnit, không chạy benchmark, không sửa tệp lộ trình hay baseline. Tệp deliverable duy nhất được tạo là `docs/research/wspm-bitprune-c02-architecture-contract.md`.

---

## 23. STOP Confirmation (Xác Nhận Dừng Lại Chuẩn Tắc)

Tuân thủ nghiêm ngặt quy định tại `AGENTS.md` và Hợp đồng giao việc C02:
1. Nhiệm vụ thiết kế kiến trúc của Task C02 đã hoàn tất toàn diện với việc tạo lập văn bản đặc tả chuẩn tắc `docs/research/wspm-bitprune-c02-architecture-contract.md`.
2. Antigravity **DỪNG LẠI TẠI ĐÂY**, không tự ý chuyển sang Task C03, không tự ý đánh dấu C02 là `ACCEPTED`, không thực hiện bất kỳ lệnh Git mutation nào (`commit`, `push`, `reset`, `clean`).
3. Toàn bộ hồ sơ thiết kế kiến trúc được bàn giao cho **Research Reviewer** để tiến hành thẩm định và nghiệm thu chính thức.

