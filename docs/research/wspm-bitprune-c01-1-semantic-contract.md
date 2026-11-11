# BẢN ĐẶC TẢ NGỮ NGHĨA VÀ TÍNH TRUY XUẤT NGUỒN WSPM_BITPRUNE
## Semantic Contract & Source Fidelity Specification (C01.1 — Bản Khắc phục C01.1-R3)

---

## 1. Task Identity và Phạm vi Đặc tả

### 1.1. Thông tin định danh tác vụ
- **Repository:** `wspm-pretree-bitprune`
- **Phase:** C — WSPM_BitPrune Research & Implementation
- **Parent Task:** C01 — Source Fidelity & Mathematical Safety Design
- **Task ID:** C01.1 (Đợt khắc phục: C01.1-R3 sau review `PARTIAL — CHƯA ACCEPTED`)
- **Task Name:** Source Fidelity & Semantic Contract
- **Trạng thái:** IN REVIEW / AMENDED FOR C01.1-R3
- **Phase B Freeze Commit SHA:** `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`
- **Ngôn ngữ văn bản:** Tiếng Việt (kèm thuật ngữ khoa học đối chiếu chuẩn quốc tế)

### 1.2. Mục tiêu nghiên cứu của C01.1 và Đợt Khắc phục C01.1-R3
Mục tiêu duy nhất của C01.1 (và đợt khắc phục mục tiêu hẹp C01.1-R3) là xây dựng bản đặc tả ngữ nghĩa (semantic contract) có thể truy xuất nguồn gốc một cách chính xác, toán học và chặt chẽ cho thuật toán cải tiến **WSPM_BitPrune**, đối chiếu trực tiếp với thuật toán gốc **WSPM_PreTree** (bài báo 2023), bản thảo hội thảo quốc gia (2026), đề cương khóa luận (CNTT-KLCN256), tài liệu quy chuẩn Phase B và baseline Java đã được đóng băng ở Phase B.

Bản khắc phục C01.1-R3 xử lý dứt điểm hai phát hiện (findings) còn lại từ review C01.1-R2:
1. **Loại bỏ phát biểu kế thừa sớm còn sót lại tại Source Conflict Record (Mục 11.3):** WSPM_BitPrune chưa được triển khai hoặc kiểm chứng nên không được mô tả như đã kế thừa hành vi sử dụng MeanW của baseline. Chuyển thành yêu cầu chuẩn tắc bắt buộc cho implementation tương lai và khẳng định mức độ bảo toàn thực tế phải chờ C01.2 và kiểm thử vi sai (differential testing) xác minh;
2. **Chuẩn hóa ca kiểm tra biên về chuỗi rỗng tại Ma trận OBL-08 (Mục 18):** Phân định rạch ròi giữa cơ sở dữ liệu rỗng ($|SD| = 0$, hợp lệ về mặt container) và dữ liệu chuỗi rỗng. Theo domain model (`Sequence.java`) và parser SPMF (`SpmfSequenceParser.java`), một sequence rỗng (không chứa itemset nào) là đầu vào không hợp lệ và phải bị từ chối ngay tại ranh giới parser/domain, không phải là một CSDL hợp lệ cho bài toán khai phá.

### 1.3. Giới hạn phạm vi (Scope Boundaries)
- **Được phép trong C01.1-R3:**
  - Chỉnh sửa duy nhất tệp deliverable `docs/research/wspm-bitprune-c01-1-semantic-contract.md`.
  - Chuẩn hóa câu chữ tại Mục 11.3 để loại bỏ phát biểu kế thừa sớm.
  - Phân loại lại ca biên chuỗi rỗng trong bảng ma trận OBL-08 và các mục liên quan.
  - Bổ sung tài liệu kiểm tra parser và sequence rỗng vào Mục 2 và Mục 3.
- **Tuyệt đối không được phép trong C01.1-R3:**
  - Không viết hoặc sửa bất kỳ dòng mã Java nào (`src/main/java`).
  - Không viết hoặc sửa bất kỳ bài kiểm thử JUnit nào (`src/test/java`).
  - Không triển khai cấu trúc dữ liệu Bitmap, CMAP hay Positional Index vào mã nguồn.
  - Không thực hiện chứng minh toán học hình thức (formal proofs) cho các quy tắc cắt tỉa — nghĩa vụ này thuộc về C01.2.
  - Không xây dựng bộ phản ví dụ (counterexample suite) hay bộ vector kiểm thử cuối cùng — thuộc về C01.2.
  - Không phê duyệt bất kỳ lệnh vận hành "loại ngay" nào trong tài liệu ngữ nghĩa.
  - Không thay đổi hành vi hoặc mã nguồn của baseline WSPM_PreTree đã đóng băng.
  - Không thay đổi trạng thái của các vấn đề nghiên cứu R1–R8; giữ nguyên R6 là `OPEN`.
  - Không thay đổi `pom.xml`, `.gitignore`, `PHASE_C_WSPM_BitPrune.md` hay các tài liệu Phase B.
  - Không thực hiện commit, push, reset hay clean git repository.

---

## 2. Danh mục Tài liệu Nghiên cứu Khảo sát (Sources Inspected)

Quá trình xây dựng và khắc phục bản đặc tả C01.1-R3 đã khảo sát và đối chiếu trực tiếp từ các nguồn sau:

### 2.1. Nguồn nghiên cứu xuất bản (Authoritative Published Sources)
1. **[Pham2023]** Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van. *“Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding”*. Vietnam Journal of Computer Science, Vol. 10, No. 3 (2023), pp. 357–372. DOI: `10.1142/S219688882350001X`.
   Tệp lưu trữ: `docs/Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf`.
   *Mục khảo sát:* Trang 362–366 (Mục 3, 4, Hình 1, Bảng 1, Bảng 2).
   *Xác nhận ngữ nghĩa:* Định nghĩa chuỗi, chuỗi con, support theo chuỗi, trọng số item $w(i)$, $\text{MaxW}$, $\text{MinW}$, $\text{MeanW}$ toàn cục, Prefix Tree, I-/S-Extension, và điều kiện chấp nhận $support(P) \times \text{MeanW} \ge minsup$.
   *Phân loại:* `SOURCE-CONFIRMED`.
2. **[Gouda2010]** Karam Gouda, Mosab Hassaan, Mohammed J. Zaki. *“PRISM: An effective approach for frequent sequence mining via prime-block encoding”*. Journal of Computer and System Sciences, Vol. 76, No. 1 (2010), pp. 88–102. DOI: `10.1016/j.jcss.2009.05.008`.
   Tệp lưu trữ: `docs/Prism_An effective approach for frequent sequence mining prime-block encoding.pdf`.
   *Mục khảo sát:* Mục 3, 4 (biểu diễn khối nguyên tố và phép kết nối kiểm tra vị trí nghiêm ngặt).
   *Phân loại:* `SOURCE-CONFIRMED`.

### 2.2. Nguồn đề xuất thuật toán cải tiến và đề cương khóa luận
3. **[BitPrune2026]** Nguyễn Phúc Khang, Đặng Duy Minh, Phạm Thị Lan Thanh, Nguyễn Thị Thu Tâm. *“Tối ưu hóa thuật toán khai phá mẫu tuần tự có trọng số bằng cấu trúc Bitmap và cơ chế cắt tỉa trễ đa tầng”*. Bản thảo Hội thảo Quốc gia, 2026.
   Tệp lưu trữ: `docs/WSPM_BitPrune_HoiThaoQuocGia_V1_2026_05_20.docx`.
   *Mục khảo sát:* Mục 3.1–3.4 (Định nghĩa 1–9, Bổ đề 1–2), Mục 4.2–4.5 (kiến trúc lọc 3 tầng).
   *Khẳng định được khảo sát:* Mảng bit $BM(i)$, phép AND bitmask, ma trận CMAP gộp, cận trên cardinality, ước lượng cận trên WSUP, và đề xuất cắt tỉa trễ.
   *Phân loại:* `SOURCE-CONFIRMED (TRONG BẢN THẢO 2026)`.
   *Giới hạn nhận định:* Các phát biểu về cắt tỉa trễ trong bản thảo chỉ đóng vai trò là "đề xuất thuật toán" (proposed rules/lemmas), chưa phải là chứng minh đã được nghiệm thu trong repository này.
4. **[Proposal2026]** Huỳnh Đổ Hoàng Nguyên, Lê Thị Tuyết Nhi, Nguyễn Quang Thành Toàn; GVHD: TS. Nguyễn Thị Thu Tâm. *“Đề cương chi tiết khóa luận cử nhân ngành CNTT: Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây dựng hệ thống gợi ý combo sản phẩm”* (Mã đề tài: CNTT-KLCN256), Trường ĐH Công Thương TP.HCM, 2026.
   Tệp lưu trữ: `docs/CNTT-KLCN256_thuật toán WSPM_PreTree_Đã sửa.docx`.
   *Mục khảo sát:* Mục tiêu và nội dung nghiên cứu (Mục 1–3, yêu cầu so sánh WSPM_PreTree và WSPM_BitPrune).
   *Phân loại:* `PROJECT SPECIFICATION`.

### 2.3. Quy chế quản trị và tài liệu Phase B
5. **[AGENTS]** `AGENTS.md` — Quy chế kỹ thuật và đạo đức nghiên cứu của repository.
   *Phân loại:* `AUTHORITATIVE GOVERNING RULE`.
6. **[PHASE_C]** `PHASE_C_WSPM_BitPrune.md` — Kế hoạch tổng thể và lộ trình tác vụ Phase C (Mục 5, 6, 8, 12).
   *Phân loại:* `AUTHORITATIVE PHASE ROADMAP`.
7. **[PHASE_B_PRETREE]** `PHASE_B_WSPM_PRETREE.md`
   *Mục khảo sát:* Mục 1 (Mục tiêu giai đoạn), Mục 3 (Quy tắc bắt buộc), Mục 4 (Nguồn chuẩn WSPM_PreTree), Mục 7 (Checklist B0–B24).
   *Nội dung kiểm tra:* Quy tắc bảo vệ baseline, các bất biến toán học của WSPM_PreTree (support, item weight, MaxW, MinW, MeanW, điều kiện chấp nhận mẫu), yêu cầu không dùng Bitmap thay Prime-block trong baseline, và 9 checkpoint chuẩn của Table 1.
   *Phân loại:* `AUTHORITATIVE REPOSITORY INSTRUCTION`.
   *Giới hạn:* Là tài liệu quản trị và quy chuẩn kỹ thuật nội bộ của Phase B; quy định các ràng buộc kế thừa, không phải bài báo khoa học ngoài repository.
8. **[PHASE_B_SPEC]** `docs/research/wspm-pretree-spec.md` — Đặc tả nghiên cứu WSPM_PreTree baseline (Mục 4–9).
   *Phân loại:* `PHASE B RESEARCH SPECIFICATION`.
9. **[PHASE_B_REPRO]** `docs/research/phase-b-reproducibility.md`
   *Mục khảo sát:* Mục 2 (Môi trường JDK 21), Mục 3.1 (Mã băm SHA-256 của `table1.spmf`: `F7E409F9...`), Mục 6.3 (Kết quả chuẩn Table 1 với 9 pattern và exact support), Mục 8 (Bảng trạng thái và ranh giới nghiên cứu của R1–R8).
   *Nội dung kiểm tra:* Tính tái lập của baseline đã đóng băng tại commit `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`; các ranh giới nghiên cứu của R1 (công thức trọng số chuỗi), R2 (quy đổi phần trăm unrounded), R5 (mẫu số độ dài toàn CSDL), R6 (ngữ nghĩa ngưỡng 0 đang `OPEN`).
   *Phân loại:* `REPRODUCIBILITY SPECIFICATION / FROZEN BASELINE AUDIT`.
   *Giới hạn:* Xác nhận tính tái lập của mã nguồn baseline Phase B; không thay thế cho bài báo lý thuyết ngoài repository.
10. **[PHASE_B_AUDIT]** `docs/research/phase-b-baseline-audit.md` và **[PHASE_B_ACCEPT]** `docs/research/phase-b-final-acceptance.md`.

### 2.4. Mã nguồn và bài kiểm thử chuẩn của Phase B
11. `src/main/java/vn/edu/wspm/model/Pattern.java`, `PatternExtensions.java`, `Itemset.java`.
12. **[DOM_SEQ]** `src/main/java/vn/edu/wspm/model/Sequence.java`
    *Nội dung kiểm tra:* Khai báo `public Sequence(Collection<Itemset> itemsets)`. Dòng 15–17 ném `IllegalArgumentException("a sequence must contain at least one itemset")` nếu danh sách itemset rỗng. Sequence rỗng là đối tượng không hợp lệ trong domain model.
    *Phân loại:* `IMPLEMENTATION DOMAIN MODEL (BASELINE)`.
13. **[DOM_REC]** `src/main/java/vn/edu/wspm/model/SequenceRecord.java`
    *Nội dung kiểm tra:* Khai báo `public record SequenceRecord(int sid, Sequence sequence)`. `SequenceRecord` chỉ kiểm tra tính không null của `sequence`, hoàn toàn không áp đặt điều kiện $sid > 0$.
    *Phân loại:* `IMPLEMENTATION DOMAIN MODEL (BASELINE)`.
14. **[DOM_DB]** `src/main/java/vn/edu/wspm/model/SequenceDatabase.java`
    *Nội dung kiểm tra:* Constructor kiểm tra tính không null và tính duy nhất của mã $sid$ (`duplicate sequence identifier`), hoàn toàn không yêu cầu các $sid$ phải liên tục hay phải là số dương.
    *Phân loại:* `IMPLEMENTATION DOMAIN MODEL (BASELINE)`.
15. **[IO_PARSER]** `src/main/java/vn/edu/wspm/io/SpmfSequenceParser.java`
    *Nội dung kiểm tra:* Dòng 92–95 kiểm tra `if (sequenceItemsets.isEmpty()) throw malformed("-2 cannot close an empty sequence")`. Dữ liệu SPMF chứa chuỗi rỗng bị từ chối ngay tại tầng phân tích cú pháp.
    *Phân loại:* `IMPLEMENTATION / IO PARSER (BASELINE)`.
16. **[TEST_PARSER]** `src/test/java/vn/edu/wspm/io/SpmfSequenceParserTest.java`
    *Nội dung kiểm tra:* Phương thức `rejectsEmptyItemsetsAndEmptySequences()` (dòng 141–145) kiểm chứng việc từ chối chuỗi rỗng: `assertMalformed("-2").getMessage().contains("empty sequence")`.
    *Phân loại:* `IMPLEMENTATION / TEST EVIDENCE (BASELINE)`.
17. `src/main/java/vn/edu/wspm/config/MinimumSupport.java`.
18. **[CFG_RES]** `src/main/java/vn/edu/wspm/config/ResolvedMinimumSupport.java` và `MinimumSupportResolver.java`
    *Nội dung kiểm tra:* Phương thức `meetsThreshold(int support, double meanWeight)` kiểm tra `support * meanWeight >= effectiveThreshold`. Chế độ `PERCENT` ném ngoại lệ trên CSDL có kích thước 0. Chế độ `ABSOLUTE` không nhân kích thước CSDL.
    *Phân loại:* `IMPLEMENTATION DOMAIN MODEL (BASELINE)`.
19. `src/main/java/vn/edu/wspm/statistics/ItemWeightCalculator.java`, `ItemStatisticsCalculator.java`.
20. `src/main/java/vn/edu/wspm/mining/F1Builder.java`, `CandidateEngine.java`, `WspmPreTreeMiner.java`.
21. `src/test/java/vn/edu/wspm/mining/BruteForceWeightedPatternOracle.java`, `Table1EndToEndCorrectnessTest.java`, `WspmDifferentialCorrectnessTest.java`.
22. **[TEST_EXT]** `src/test/java/vn/edu/wspm/model/PatternExtensionTest.java`
    *Nội dung kiểm tra:* Cấu trúc I-/S-Extension, canonical order, repeated items, empty pattern behavior.
    *Phân loại:* `IMPLEMENTATION / TEST EVIDENCE (BASELINE)`.
23. **[TEST_RES]** `src/test/java/vn/edu/wspm/config/MinimumSupportResolverTest.java`
    *Nội dung kiểm tra:* Phương thức `rejectsPercentageResolutionForEmptyDatabaseButAllowsAbsoluteResolution()` (dòng 109–116): chế độ `PERCENT` ném ngoại lệ khi $|SD| = 0$, chế độ `ABSOLUTE` với $A = 2.0$ cho `effectiveThreshold = 2.0` và `meetsThreshold(0, 1.0) == false`.
    *Phân loại:* `IMPLEMENTATION / TEST EVIDENCE (BASELINE)`.
24. **[TEST_WGT]** `src/test/java/vn/edu/wspm/statistics/ItemWeightCalculatorTest.java`
    *Nội dung kiểm tra:* Phương thức `representsEmptyDatabaseWithoutFabricatedSummaryValues()` (dòng 98–106): CSDL rỗng trả về `ItemWeightResult.empty()`, không tự bịa đặt $MeanW = 0$.
    *Phân loại:* `IMPLEMENTATION / TEST EVIDENCE (BASELINE)`.
25. `src/test/resources/datasets/wspm/table1.spmf`.

---

## 3. Bảng Phân loại Bằng chứng Nguồn (Source-Evidence Ledger)

Mọi khẳng định, định nghĩa và quyết định cấu trúc đều được ghi nhận vào sổ bằng chứng dưới đây:

| Claim ID | Khái niệm / Định nghĩa | Đường dẫn tài liệu / Mã nguồn | Vị trí (Trang / Mục / Lớp / Phương thức) | Tóm tắt bằng chứng | Phân loại | Xung đột / Giới hạn nhận định |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **EV-01** | Khái niệm Item, Itemset | [Pham2023] | Trang 362, Mục 3 | $I = \{i_1, \dots, i_m\}$, itemset $s_j \subseteq I$ không rỗng, kích thước là số item. | `SOURCE-CONFIRMED` | Không chỉ định cấu trúc dữ liệu bộ nhớ. |
| **EV-02** | Khái niệm Sequence, kích thước và độ dài | [Pham2023], [DOM_SEQ] | Trang 362, Mục 3; `Sequence.java` | $S = \langle s_1, \dots, s_n \rangle$ ($n \ge 1$), $s_1$ xảy ra trước $s_2$. Kích thước = số itemset, độ dài = tổng số item. Sequence bắt buộc có ít nhất 1 itemset. | `SOURCE-CONFIRMED (TOÁN HỌC) / IMPLEMENTATION (DOMAIN MODEL)` | Sequence rỗng không được chấp nhận trong domain model. |
| **EV-03** | Khác biệt $\langle(a)(b)\rangle \ne \langle(ab)\rangle$ | [Pham2023] | Trang 363, Mục 3 | Nêu rõ $\langle(a)(b)\rangle$ là chuỗi 2 itemset, $\langle(ab)\rangle$ là chuỗi 1 itemset gồm 2 item đồng thời. | `SOURCE-CONFIRMED` | Không được làm phẳng thành tập item không thứ tự. |
| **EV-04** | Subsequence Embedding | [Pham2023] | Trang 362, Mục 3 | $\alpha \sqsubseteq \beta \iff \exists 1 \le j_1 < \dots < j_n \le m: a_k \subseteq b_{j_k}$. | `SOURCE-CONFIRMED` | Các chỉ số itemset phải tăng nghiêm ngặt ($j_k < j_{k+1}$). |
| **EV-05** | Exact Support theo Distinct SID | [Pham2023] | Trang 362–363, Mục 3 | Support là số chuỗi trong CSDL chứa pattern. Mỗi chuỗi chỉ đóng góp tối đa 1. | `SOURCE-CONFIRMED` | Số embedding không được làm tăng support của 1 SID. |
| **EV-06** | Mở rộng I-Extension | [Pham2023], [TEST_EXT] | Trang 363–365; `createsDistinctIAndSExtensions...` | Thêm item vào itemset cuối, kích thước chuỗi không đổi, độ dài tăng 1. | `SOURCE-CONFIRMED` | Item thêm vào phải lớn hơn item cuối trong itemset cuối. |
| **EV-07** | Mở rộng S-Extension | [Pham2023], [TEST_EXT] | Trang 363–365; `allowsRepeatedItem...` | Thêm singleton itemset mới vào cuối chuỗi, kích thước và độ dài đều tăng 1. | `SOURCE-CONFIRMED` | Item có thể lặp lại nếu xuất hiện ở itemset sau. |
| **EV-08** | Thứ tự từ điển định danh số trong Itemset | [PHASE_B_SPEC], [TEST_EXT] | `PatternExtensions.java`; `realizesLexicographicGate...` | Sử dụng thứ tự tự nhiên tăng dần của mã số nguyên `Item.id()` để hiện thực canonical order. | `IMPLEMENTATION POLICY / INFERENCE` | Bài báo 2023 dùng ký tự chữ cái `a < b < ...`, baseline chuẩn hóa thành số nguyên. |
| **EV-09** | Công thức Item Weight $w(i)$ | [Pham2023], [TEST_WGT] | Trang 364, Công thức (1) & Bảng 2; `reproducesEveryTableTwoWeight...` | $W(i) = T(i) / \sum L(S_j)$. Bảng 2 chia cho tổng độ dài toàn CSDL (66). | `SOURCE-CONFIRMED` | Văn bản ghi "chiều dài các chuỗi chứa $i$" nhưng Bảng 2 dùng tổng chiều dài toàn bộ chuỗi (R5 đã RESOLVED). |
| **EV-10** | Công thức MaxW, MinW, MeanW | [Pham2023], [TEST_WGT] | Trang 364, Mục 3; `computesMaxMinAndMean...` | $MaxW = \max_k W(i_k)$, $MinW = \min_k W(i_k)$, $MeanW = (MaxW + MinW)/2$. | `SOURCE-CONFIRMED` | $MeanW$ là hằng số toàn cục của CSDL, không phụ thuộc pattern. Không tồn tại khi CSDL rỗng. |
| **EV-11** | Điều kiện chấp nhận mẫu có trọng số | [Pham2023] | Trang 364, Mục 3; Hình 1 | $support(P) \times MeanW \ge minsup$. | `SOURCE-CONFIRMED` | Dùng $MeanW$ toàn cục, không dùng $W(s)$ (R1 ghi nhận). |
| **EV-12** | Quy đổi đơn vị Percentage minsup | [PHASE_B_SPEC], [TEST_RES] | `MinimumSupportResolver.java`; `resolvesTableOneFourteenPercent...` | $effectiveMinSup = (configuredMinSup / 100.0) \times \|SD\|$. So sánh: $support(P) \times MeanW \ge effectiveMinSup$. | `IMPLEMENTATION POLICY / INFERENCE` | Bài báo không ghi công thức quy đổi, nhưng $0.98$ tái lập chính xác Table 1 (R2 đã RESOLVED). |
| **EV-13** | Dạng chuẩn hóa WSUP trong bản thảo 2026 | [BitPrune2026] | Mục 3.1, Định nghĩa 6 | Định nghĩa WSUP là $\frac{sup(P)}{\|SDB\|} \times MeanW$. | `SOURCE-CONFIRMED (TRONG BẢN THẢO 2026)` | Chỉ tương đương toán học với baseline khi $\|SD\| > 0$. Phép chia không xác định khi CSDL rỗng. |
| **EV-14** | Mệnh đề Downward Closure dưới MeanW | [BitPrune2026] vs [Pham2023] | Mục 1, Mục 3.1 vs Mục 3 | Bản thảo 2026 phát biểu trọng số làm mất tính giảm bao đóng; nhưng bài toán baseline dùng MeanW toàn cục. | `UNRESOLVED / SOURCE CONFLICT` | Xung đột lý thuyết: chuyển thành nghĩa vụ chứng minh hình thức trong C01.2. |
| **EV-15** | Cấu trúc SID Candidate Bitmap & Ánh xạ | [BitPrune2026], [DOM_REC], [DOM_DB] | Mục 3.2, Định nghĩa 7–8; `SequenceRecord.java`; `SequenceDatabase.java` | Mảng bit đại diện các SID chứa item. Ánh xạ song ánh `sidToIndex` với tập SID nguyên phân biệt (có thể noncontiguous). | `SOURCE-CONFIRMED (BITMASK) / IMPLEMENTATION POLICY (MAPPING)` | Chỉ là tập cha chứa các SID ứng viên; không xác minh thứ tự thời gian. |
| **EV-16** | Đề xuất Cận trên Cardinality của Bitmap | [BitPrune2026] | Mục 3.3, Bổ đề 1–2 | $support(P) \le \|BM(P)\|$; $upperBoundWSUP(P) = \|BM(P)\| \times MeanW$. | `PROPOSED LEMMA (BẢN THẢO 2026)` | Là đề xuất của bản thảo 2026, chuyển giao sang C01.2 chứng minh hình thức. |
| **EV-17** | Ma trận CMAP (Co-occurrence MAP) | [BitPrune2026] | Mục 3.2, Định nghĩa 9 | $CMAP(A, B) = true$ nếu A từng đứng trước hoặc cùng lúc với B. | `SOURCE-CONFIRMED (TRONG BẢN THẢO 2026)` | Gộp chung "cùng lúc" và "đứng trước" làm giảm độ chặt chẽ; cần phân tách CMAP-I và CMAP-S. |
| **EV-18** | Tách biệt CMAP-I và CMAP-S | `PHASE_C_WSPM_BitPrune.md`, Mục 6.3 | Mục 6.3; Task C01.1 | Tách thành CMAP-I (đồng xuất hiện trong cùng itemset) và CMAP-S (xuất hiện trước ở itemset khác). | `IMPLEMENTATION POLICY / INFERENCE` | Nâng cao độ chặt của bộ lọc so với bản thảo 2026; chuyển giao nghĩa vụ chứng minh an toàn lọc sang C01.2. |
| **EV-19** | Exact Temporal Verification & Bằng chứng Kiểm thử Baseline | [BitPrune2026], [Pham2023], `Table1EndToEndCorrectnessTest`, `WspmDifferentialCorrectnessTest` | Mục 4.4, Lớp 3; Trang 362–363; Bộ kiểm thử vi sai Phase B | Giao tập vị trí thời gian để xác nhận thứ tự chuỗi. Các bài test Phase B cung cấp bằng chứng hồi quy trên Table 1 và các bộ dữ liệu vi sai đã chạy. | `SOURCE-CONFIRMED (KIẾN TRÚC) / IMPLEMENTATION / TEST EVIDENCE (HỒI QUY BASELINE)` | Bằng chứng kiểm thử thực nghiệm trên dữ liệu đã chạy, không phải chứng minh toán học phổ quát cho mọi CSDL. |
| **EV-20** | Ngữ nghĩa ngưỡng 0 (Zero minsup) | [PHASE_B_SPEC], [PHASE_C], [TEST_RES] | Mục 5.6; Issue R6; `rejectsPercentageResolution...` | Phép so sánh bao gồm $\ge 0$ chấp nhận mẫu có support 0 nếu không có điều kiện chặn dưới. | `UNRESOLVED` | Vấn đề R6 tiếp tục giữ trạng thái `OPEN`. Không được tự ý đổi thành $> 0$. |
| **EV-21** | Từ chối Sequence rỗng tại Parser và Domain Model | [DOM_SEQ], [IO_PARSER], [TEST_PARSER] | `Sequence.java:16`; `SpmfSequenceParser.java:93`; `SpmfSequenceParserTest.java:144` | Constructor ném ngoại lệ nếu sequence rỗng; Parser SPMF từ chối `-2` đóng chuỗi rỗng. | `IMPLEMENTATION DOMAIN MODEL / PARSER CONSTRAINT` | Chuỗi rỗng là dữ liệu không hợp lệ, không phải CSDL hợp lệ cho mining. |

---

## 4. Thuật ngữ và Ký hiệu Toán học Thống nhất (Terminology & Notation)

Nhằm đảm bảo sự nhất quán xuyên suốt Phase C và phù hợp tuyệt đối với domain model hiện tại của repository, các thuật ngữ và ký hiệu sau được quy chuẩn:

- **$I = \{i_1, i_2, \dots, i_m\}$:** Tập hợp toàn cục gồm $m$ sự kiện (items) phân biệt, được sắp thứ tự định danh số nguyên dương tăng dần: $i_1 < i_2 < \dots < i_m$.
- **$s$ (Itemset):** Một tập con không rỗng của $I$, ký hiệu $(x_1 x_2 \dots x_p)$ với $x_1 < x_2 < \dots < x_p$. Kích thước của itemset là $|s| = p$.
- **$S$ (Sequence):** Một danh sách có thứ tự các itemset: $S = \langle s_1, s_2, \dots, s_n \rangle$ ($n \ge 1$). Theo domain model (`Sequence.java`), một sequence bắt buộc phải chứa ít nhất một itemset; chuỗi rỗng ($n = 0$) là dữ liệu không hợp lệ.
- **$size(S)$:** Kích thước chuỗi, bằng số lượng itemset trong chuỗi: $size(S) = n$.
- **$length(S)$ hay $L(S)$:** Độ dài chuỗi, bằng tổng số lần xuất hiện của các item trong chuỗi: $L(S) = \sum_{j=1}^n |s_j|$.
- **$SD$ hay $SDB$:** Cơ sở dữ liệu chuỗi (Sequence Database), tập hợp các bản ghi $\langle sid, S \rangle$ (được biểu diễn bằng `SequenceRecord(int sid, Sequence sequence)`) trong đó $sid$ là mã định danh số nguyên phân biệt của chuỗi. $|SD|$ là tổng số chuỗi trong CSDL (`SequenceDatabase.size()`).
- **$\text{Universe}(SD)$ (Không gian mã định danh chuỗi thực tế):**
  $$\text{Universe}(SD) = \{ record.sid() \mid record \in SD \}, \quad |\text{Universe}(SD)| = |SD|$$
  Các mã định danh $sid$ là các số nguyên phân biệt kiểu Java `int`:
  - **Tính duy nhất:** Constructor của `SequenceDatabase` bắt buộc mọi $record.sid()$ phải là duy nhất trong CSDL (ném ngoại lệ `IllegalArgumentException` nếu trùng lặp).
  - **Tính không liên tục (Noncontiguous SIDs):** Các mã $sid$ trong $\text{Universe}(SD)$ có thể không liên tục và không nhất thiết bắt đầu từ 0 hay 1 (ví dụ: $\{10, 25, 100\}$ hoặc $\{-5, 0, 12\}$).
  - **Ranh giới Domain Model:** Lớp `SequenceRecord` lưu trữ $sid$ kiểu nguyên thủy `int` và không kiểm tra điều kiện $sid > 0$. Parser cụ thể (như `SpmfSequenceParser`) có thể sinh SID dương và liên tục từ 1 trên các tệp dữ liệu SPMF, nhưng đó là đặc tính của parser/fixture, không phải invariant tổng quát của `SequenceDatabase`.
  - **Chính sách đầu vào tương lai:** Nếu sau này BitPrune giới hạn chỉ tiếp nhận SID dương ($sid > 0$), điều đó phải được phân loại là **`PROPOSED INPUT POLICY — NOT YET APPROVED`** và chuyển sang C02/C03 phê duyệt; C01.1 không tự ý áp đặt điều kiện $sid \in \mathbb{N}^*$ vào semantic contract tổng quát.
  - **Không phụ thuộc chỉ số bit:** Mã $sid$ thực tế hoàn toàn độc lập với chỉ số bit nội bộ. Mọi kết quả khai phá cuối cùng và hàm đếm hỗ trợ $support(P)$ phải trả về tập hợp các mã định danh thực tế $sid \in \text{Universe}(SD)$ và lực lượng của nó.
- **Hệ tọa độ bit nội bộ và Ánh xạ song ánh (SID Mapping Contract):**
  Để biểu diễn thông tin chuỗi trên mảng bit có kích thước đúng $|SD|$ bit, ta sắp xếp các mã $sid$ thực tế trong $\text{Universe}(SD)$ theo thứ tự tự nhiên tăng dần của số nguyên:
  $$sid_{(0)} < sid_{(1)} < \dots < sid_{(|SD|-1)}$$
  và thiết lập cặp ánh xạ song ánh (bijective mapping) tất định:
  $$\text{sidToIndex} : \text{Universe}(SD) \to \{0, 1, \dots, |SD|-1\}, \quad \text{sidToIndex}(sid_{(c)}) = c$$
  $$\text{indexToSid} : \{0, 1, \dots, |SD|-1\} \to \text{Universe}(SD), \quad \text{indexToSid}(c) = sid_{(c)}$$
  Đảm bảo tính chất khả nghịch: $\forall sid \in \text{Universe}(SD): \text{indexToSid}(\text{sidToIndex}(sid)) = sid$.
- **$T(i)$:** Tổng tần suất xuất hiện thực tế của item $i$ trên toàn bộ CSDL (bao gồm cả các lần lặp lại ở các itemset khác nhau).
- **$totalLength(SD)$:** Tổng độ dài của tất cả các chuỗi trong CSDL: $totalLength(SD) = \sum_{S \in SD} L(S)$.
- **$w(i)$:** Trọng số của item $i$, tính theo $w(i) = T(i) / totalLength(SD)$ khi $totalLength(SD) > 0$.
- **$MaxW, MinW$:** Lần lượt là trọng số lớn nhất và nhỏ nhất của các item có mặt trong CSDL:
  $$\text{MaxW} = \max_{i \in I, T(i) > 0} w(i), \quad \text{MinW} = \min_{i \in I, T(i) > 0} w(i)$$
- **$MeanW$:** Trọng số trung bình toàn cục của CSDL:
  $$\text{MeanW} = \frac{\text{MaxW} + \text{MinW}}{2}$$
  Khi CSDL rỗng hoặc không có item, $MeanW$ không tồn tại (không tự bịa đặt giá trị 0).
- **$P$ (Pattern / Candidate):** Một chuỗi ứng viên cần kiểm tra độ hỗ trợ và trọng số: $P = \langle p_1, p_2, \dots, p_k \rangle$.
- **$\text{SupportingSIDs}(P)$:** Tập hợp các mã định danh thực tế $sid \in \text{Universe}(SD)$ chứa ít nhất một subsequence embedding hợp lệ của $P$.
- **$support(P)$ hay $sup(P)$:** Độ hỗ trợ tuyệt đối của $P$, bằng lực lượng của tập SID hỗ trợ thực tế: $support(P) = |\text{SupportingSIDs}(P)|$.
- **$WSUP(P)$:** Điểm số hỗ trợ có trọng số của pattern $P$: $WSUP(P) = support(P) \times \text{MeanW}$.
- **$effectiveMinSup$:** Ngưỡng hỗ trợ có trọng số hiệu dụng nội bộ (Internal Effective Threshold), cùng thứ nguyên với $WSUP(P)$.
- **$BM(i)$:** Mảng bit (Bitmap/Bitmask) kích thước $|SD|$ bit, với chỉ số bit nội bộ $c \in \{0, 1, \dots, |SD|-1\}$. Giá trị bit tại tọa độ $c = \text{sidToIndex}(sid)$ được định nghĩa:
  $$BM(i)[\text{sidToIndex}(sid)] = \begin{cases} 1 & \text{nếu chuỗi } sid \text{ chứa item } i \\ 0 & \text{ngược lại} \end{cases}$$
- **$BM(P)$:** Bitmap ứng viên của pattern $P$, tạo thành từ phép hội logic (Bitwise AND) của các Bitmap đơn lẻ của các item tạo nên $P$.
- **$\text{CMAP-I}(a, b)$:** Giá trị boolean chỉ định liệu item $a$ và item $b$ có cùng xuất hiện trong ít nhất một itemset của cùng một chuỗi hay không.
- **$\text{CMAP-S}(a, b)$:** Giá trị boolean chỉ định liệu item $a$ có xuất hiện ở một itemset đứng trước nghiêm ngặt một itemset chứa item $b$ trong cùng một chuỗi hay không.

---

## 5. Đặc tả Ngữ nghĩa Cấu trúc Pattern và Itemset (Pattern & Itemset Semantics)

### 5.1. Định nghĩa Itemset
Một itemset $s$ là một tập hợp con không rỗng của $I$: $s \subseteq I, s \ne \emptyset$.
- Một item $x$ chỉ có thể xuất hiện tối đa một lần trong một itemset: $\forall x \in s$, số lần xuất hiện của $x$ trong $s$ bằng 1.
- Itemset không biểu diễn quan hệ thời gian giữa các phần tử của nó. Các phần tử trong itemset xảy ra **đồng thời**.
- Để đảm bảo tính duy nhất và biểu diễn chuẩn tắc (canonical representation), các item trong itemset luôn được lưu trữ và duyệt theo thứ tự định danh số nguyên tăng dần:
  $$s = (x_1, x_2, \dots, x_p) \quad \text{với} \quad x_1 < x_2 < \dots < x_p$$
- Phân loại: `SOURCE-CONFIRMED` cho tính đồng thời và duy nhất; `IMPLEMENTATION POLICY / INFERENCE` cho thứ tự định danh số nguyên nội bộ.

### 5.2. Định nghĩa Pattern (Chuỗi mẫu)
Một pattern $P$ là một danh sách có thứ tự các itemset:
$$P = \langle p_1, p_2, \dots, p_k \rangle \quad (k \ge 1, \forall t \in [1, k]: p_t \text{ là một itemset hợp lệ})$$
- Quan hệ thời gian được xác định bởi thứ tự chỉ số: itemset $p_1$ xảy ra trước $p_2$, $p_2$ xảy ra trước $p_3$, v.v.
- **Đồng nhất cấu trúc (Structural Identity):** Hai pattern $P = \langle p_1, \dots, p_k \rangle$ và $Q = \langle q_1, \dots, q_r \rangle$ là bằng nhau ($P = Q$) khi và chỉ khi $k = r$ và $p_t = q_t$ với mọi $t \in [1, k]$ (tức cùng kích thước và các itemset tương ứng tại mọi vị trí thời gian chứa cùng tập hợp item).
- **Phân biệt rạch ròi giữa quan hệ đồng thời và quan hệ kế tiếp:**
  $$\langle(a)(b)\rangle \ne \langle(ab)\rangle$$
  - Pattern $\langle(a)(b)\rangle$ có $size = 2$, $length = 2$: sự kiện $a$ xảy ra ở một thời điểm, sau đó sự kiện $b$ xảy ra ở một thời điểm muộn hơn nghiêm ngặt.
  - Pattern $\langle(ab)\rangle$ có $size = 1$, $length = 2$: hai sự kiện $a$ và $b$ cùng xảy ra đồng thời trong một giao dịch/itemset duy nhất.
- **Sự lặp lại của item (Repeated Items):** Một item $x$ có thể xuất hiện lặp lại ở **các itemset khác nhau** trong cùng một pattern (ví dụ $\langle(a)(a)\rangle$ hay $\langle(a)(b)(a)\rangle$), nhưng không thể lặp lại trong cùng một itemset.
- **Pattern rỗng:** Pattern rỗng $\langle\rangle$ ($size = 0, length = 0$) chỉ đóng vai trò là phần tử cấu trúc đại diện cho nút gốc (root) của cây tiền tố (Prefix Tree) ở mức 0, không phải là một mẫu xuất kết quả.

---

## 6. Đặc tả Ngữ nghĩa Nhúng Chuỗi Con (Subsequence Embedding)

### 6.1. Định nghĩa Subsequence Embedding
Cho pattern $P = \langle p_1, p_2, \dots, p_k \rangle$ và chuỗi dữ liệu trong CSDL $S = \langle s_1, s_2, \dots, s_m \rangle$.
Pattern $P$ được gọi là một chuỗi con (subsequence) của $S$, ký hiệu $P \sqsubseteq S$, khi và chỉ khi tồn tại một dãy các chỉ số nguyên $(j_1, j_2, \dots, j_k)$ thỏa mãn đồng thời hai điều kiện:
1. **Thứ tự thời gian tăng nghiêm ngặt (Strictly Increasing Indices):**
   $$1 \le j_1 < j_2 < \dots < j_k \le m$$
2. **Quan hệ bao hàm tập con (Itemset Inclusion):**
   $$p_1 \subseteq s_{j_1}, \quad p_2 \subseteq s_{j_2}, \quad \dots, \quad p_k \subseteq s_{j_k}$$

Dãy chỉ số $\mathbf{j} = (j_1, j_2, \dots, j_k)$ được gọi là một **nhúng hợp lệ (valid embedding)** của $P$ trong chuỗi $S$. Chỉ số $j_k$ được gọi là **điểm mốc kết thúc (endpoint position)** của nhúng $\mathbf{j}$.

### 6.2. Phân biệt Đa nhúng (Multiple Embeddings) và Đóng góp Độ hỗ trợ
- Trong một chuỗi $S$, có thể tồn tại **nhiều nhúng hợp lệ khác nhau** cho cùng một pattern $P$.
  - *Ví dụ:* Xét chuỗi $S = \langle (a), (ab), (b) \rangle$ với các vị trí $1, 2, 3$. Pattern $P = \langle (a)(b) \rangle$ có các nhúng hợp lệ:
    - Nhúng 1: $j_1 = 1, j_2 = 2$ (vì $a \subseteq s_1, b \subseteq s_2$).
    - Nhúng 2: $j_1 = 1, j_2 = 3$ (vì $a \subseteq s_1, b \subseteq s_3$).
    - Nhúng 3: $j_1 = 2, j_2 = 3$ (vì $a \subseteq s_2, b \subseteq s_3$).
- **Nguyên tắc cốt lõi về độ hỗ trợ:** Bất kể chuỗi $S$ (hay định danh $sid$) có chứa bao nhiêu nhúng hợp lệ của $P$, $sid$ đó **chỉ đóng góp đúng 1 đơn vị** vào độ hỗ trợ của $P$.
- Tuyệt đối không được lấy tổng số lượng nhúng (total embedding occurrences) làm độ hỗ trợ.

---

## 7. Đặc tả Ngữ nghĩa Độ Hỗ Trợ Chuẩn Xác (Exact SID Support Contract)

### 7.1. Tập SID hỗ trợ và Độ hỗ trợ thực tế
Với mỗi pattern $P$, tập hợp các mã định danh chuỗi thực tế hỗ trợ $P$ trong CSDL $SD$ được định nghĩa là:
$$\text{SupportingSIDs}(P) = \{ sid \in \text{Universe}(SD) \mid P \sqsubseteq S_{sid} \}$$

Độ hỗ trợ chuẩn xác (Exact Support) của pattern $P$, ký hiệu $support(P)$, được định nghĩa bằng số lượng chuỗi phân biệt chứa $P$:
$$support(P) = |\text{SupportingSIDs}(P)|$$
- Kết quả độ hỗ trợ luôn là lực lượng của tập mã định danh thực tế $sid \in \text{Universe}(SD)$, tuyệt đối không phụ thuộc vào thứ tự hay cách thức đánh chỉ số bit nội bộ.

### 7.2. Đối chiếu với Baseline và Nghiên cứu Gốc
1. **Bài báo gốc [Pham2023]:** Định nghĩa support là số chuỗi trong $SD$ chứa chuỗi con (trang 362). Nêu rõ ví dụ item $a$ xuất hiện 5 lần trong chuỗi 1 nhưng chỉ đóng góp support là 1 (trang 363).
2. **BruteForceWeightedPatternOracle:** Phương thức `countSupport(database, pattern)` duyệt qua từng bản ghi của CSDL và tăng biến đếm support lên 1 khi `containsSubsequence(record.sequence(), pattern)` trả về `true` (dòng 115–121).
3. **Table 1 Oracle Checkpoints:**
   - Pattern $\langle(a)(bc)\rangle$ xuất hiện trong các SID 1, 2, 4 $\implies support = 3$.
   - Mẫu $F_1$: $\langle(a)\rangle: 7$, $\langle(b)\rangle: 7$, $\langle(c)\rangle: 6$.
   - Mẫu kết quả 2-pattern và 3-pattern: $\langle(a)(a)\rangle: 6$ (SIDs 1, 2, 4, 5, 6, 7; vắng SID 3), $\langle(ab)\rangle: 7$ (tất cả SIDs), $\langle(ab)(c)\rangle: 6$ (SIDs 1, 2, 3, 4, 5, 6).
4. **Baseline Encoding Support (Bằng chứng Kiểm thử Hồi quy):** Thuật toán WSPM_PreTree tính support dựa trên lực lượng số SID mã hóa trong Sequence Block: $\sum \|v(i)\|_G$. Các bài kiểm thử Phase B (`Table1EndToEndCorrectnessTest`, `WspmDifferentialCorrectnessTest`) cung cấp bằng chứng hồi quy thực nghiệm rằng Prime-block support và direct structural SID support cho kết quả giống nhau trên dữ liệu Table 1 và 146 cấu hình kiểm thử vi sai (differential configurations) đã thực thi. Bằng chứng kiểm thử này là `IMPLEMENTATION / TEST EVIDENCE (BASELINE)`, không phải là chứng minh toán học phổ quát cho mọi cơ sở dữ liệu, mọi pattern hoặc mọi bản cài đặt trong tương lai.

---

## 8. Đặc tả Hợp đồng Mở rộng I-Extension (I-Extension Contract)

### 8.1. Định nghĩa cấu trúc
Cho pattern tiền tố $P = \langle p_1, p_2, \dots, p_k \rangle$ ($k \ge 1$) và một item mở rộng $x \in I$.
Phép mở rộng trong itemset (I-Extension), ký hiệu $P \oplus_I x$, tạo ra một pattern mới $P' = \langle p_1, p_2, \dots, p'_k \rangle$ trong đó:
$$p'_k = p_k \cup \{x\}$$
- Kích thước pattern không đổi: $size(P') = size(P) = k$.
- Độ dài pattern tăng 1: $length(P') = length(P) + 1$.
- Các itemset từ $p_1$ đến $p_{k-1}$ hoàn toàn đồng nhất với $P$.

### 8.2. Quy tắc sắp xếp chuẩn tắc (Canonical Ordering Gate)
Để đảm bảo không sinh lặp lại các nhánh tương đương trong cây tiền tố (ví dụ không sinh cả $\langle(ab)\rangle$ và $\langle(ba)\rangle$), phép mở rộng I-Extension áp dụng điều kiện chặn nghiêm ngặt:
$$x > \text{lastItem}(p_k)$$
trong đó $>$ là thứ tự tự nhiên của mã số định danh nguyên `Item.id()`.
- Nếu $x \le \text{lastItem}(p_k)$ hoặc $x \in p_k$, phép mở rộng $P \oplus_I x$ là **không hợp lệ (invalid)** và bị từ chối ngay lập tức (`PatternExtensionTest.java`).
- Phân loại bằng chứng: `SOURCE-CONFIRMED` cho việc bắt buộc item mới phải lớn hơn item cuối cùng theo thứ tự từ điển (Hình 1 bài báo 2023); `IMPLEMENTATION POLICY / INFERENCE` cho việc chuẩn hóa thứ tự từ điển này bằng thứ tự số nguyên tăng dần của ID.

### 8.3. Ràng buộc nhúng thời gian đối với I-Extension
Để một chuỗi $S_{sid}$ hỗ trợ pattern mở rộng $P' = P \oplus_I x$, không chỉ cần $S_{sid}$ hỗ trợ $P$ và chứa item $x$, mà bắt buộc phải tồn tại một nhúng $\mathbf{j} = (j_1, \dots, j_k)$ của $P$ sao cho:
$$p_k \cup \{x\} \subseteq s_{sid, j_k}$$
- Tức là: Item $x$ phải xuất hiện tại **chính cùng một vị trí itemset điểm mốc (endpoint position $j_k$)** đã khớp với $p_k$.
- **Hệ quả ngữ nghĩa quan trọng:** Một chuỗi chứa cả $P$ và chứa cả $x$ ở một itemset khác (trước hoặc sau $j_k$) nhưng không có itemset nào chứa đồng thời $p_k \cup \{x\}$ thì chuỗi đó **không hỗ trợ** $P'$.

---

## 9. Đặc tả Hợp đồng Mở rộng S-Extension (S-Extension Contract)

### 9.1. Định nghĩa cấu trúc
Cho pattern tiền tố $P = \langle p_1, p_2, \dots, p_k \rangle$ ($k \ge 0$) và một item mở rộng $x \in I$.
Phép mở rộng chuỗi (S-Extension), ký hiệu $P \oplus_S x$, tạo ra một pattern mới $P' = \langle p_1, p_2, \dots, p_k, p_{k+1} \rangle$ trong đó:
$$p_{k+1} = (x) \quad (\text{singleton itemset})$$
- Kích thước pattern tăng 1: $size(P') = size(P) + 1 = k + 1$.
- Độ dài pattern tăng 1: $length(P') = length(P) + 1$.
- Trường hợp đặc biệt: Nếu $P = \langle\rangle$ (gốc rỗng), $P \oplus_S x = \langle(x)\rangle$ (tạo ra 1-pattern ở mức 1; `PatternExtensionTest.java`).

### 9.2. Quy tắc lặp lại item trong S-Extension
Khác với I-Extension, S-Extension không yêu cầu $x > \text{lastItem}(p_k)$. Mọi item $x \in F_1$ đều là ứng viên hợp lệ cho S-Extension.
- Item $x$ hoàn toàn có thể trùng với một item đã có mặt trong $P$ (kể cả item trong $p_k$).
  - *Ví dụ:* $\langle(a)\rangle \oplus_S a = \langle(a)(a)\rangle$. Pattern này hoàn toàn hợp lệ và có $support = 6$ trong Table 1 (`PatternExtensionTest.java`).

### 9.3. Ràng buộc nhúng thời gian đối với S-Extension
Để một chuỗi $S_{sid}$ hỗ trợ pattern mở rộng $P' = P \oplus_S x$, bắt buộc phải tồn tại một nhúng $\mathbf{j} = (j_1, \dots, j_k)$ của $P$ và một chỉ số vị trí mới $j_{k+1}$ sao cho:
$$j_k < j_{k+1} \le size(S_{sid}) \quad \text{và} \quad x \in s_{sid, j_{k+1}}$$
- Tức là: Item $x$ phải xuất hiện tại một itemset xảy ra **muộn hơn nghiêm ngặt (strictly later)** so với điểm mốc kết thúc $j_k$ của $P$.
- **Hệ quả ngữ nghĩa quan trọng:** Sự xuất hiện của $x$ tại cùng vị trí $j_k$ hoặc trước $j_k$ không thỏa mãn điều kiện S-Extension.

---

## 10. Đặc tả Hợp đồng Trọng số và Hệ đơn vị Ngưỡng (Weight & Threshold Units)

### 10.1. Công thức Trọng số Thực tế
1. **Item Weight $w(i)$:**
   Khi $totalLength(SD) > 0$:
   $$w(i) = \frac{T(i)}{totalLength(SD)} = \frac{T(i)}{\sum_{S \in SD} L(S)}$$
   - Mẫu số là tổng độ dài của mọi chuỗi trong toàn bộ cơ sở dữ liệu (`ItemWeightCalculatorTest.java`).
   - Trọng số $w(i)$ luôn là số thực không âm trong đoạn $[0, 1]$.
2. **MaxW và MinW:**
   $$\text{MaxW} = \max_{i \in I, T(i) > 0} w(i), \quad \text{MinW} = \min_{i \in I, T(i) > 0} w(i)$$
3. **Global MeanW:**
   $$\text{MeanW} = \frac{\text{MaxW} + \text{MinW}}{2}$$
   - $MeanW$ là một **hằng số toàn cục (global constant)**, phụ thuộc duy nhất vào dữ liệu CSDL ban đầu, độc lập hoàn toàn với bất kỳ pattern hay ứng viên nào trong quá trình khai phá.
   - Vì $w(i) \ge 0$, ta có $\text{MeanW} \ge 0$.
   - Khi CSDL rỗng ($|SD| = 0$ hoặc không có item), $MeanW$ không tồn tại và không được định nghĩa.

### 10.2. Điểm số Hỗ trợ có Trọng số (WSUP)
Điểm số hỗ trợ có trọng số của pattern $P$, ký hiệu $WSUP(P)$, được xác định thống nhất:
$$WSUP(P) = support(P) \times \text{MeanW}$$
- Vì $support(P) \in \mathbb{N}$ và $\text{MeanW} \in \mathbb{R}_{\ge 0}$, nên $WSUP(P) \in \mathbb{R}_{\ge 0}$.

### 10.3. Hệ Đơn vị Ngưỡng và Quy đổi Tương đương
Hệ thống hỗ trợ hai chế độ cấu hình ngưỡng người dùng thông qua đối tượng `MinimumSupport`:
1. **Chế độ Tỷ lệ Phần trăm (Percentage Mode — `PERCENT`):**
   - Người dùng nhập giá trị cấu hình $C \in [0, 100]$ (ví dụ $14\%$).
   - Tỷ lệ tương đối: $\theta_{rel} = C / 100.0$ (ví dụ $0.14$).
   - Khi $|SD| > 0$, ngưỡng hiệu dụng nội bộ (Effective Internal Threshold) là:
     $$effectiveMinSup = \theta_{rel} \times |SD| = \frac{C}{100.0} \times |SD|$$
2. **Chế độ Số tuyệt đối (Absolute Mode — `ABSOLUTE`):**
   - Người dùng nhập giá trị tuyệt đối $A \ge 0$.
   - Ngưỡng hiệu dụng nội bộ:
     $$effectiveMinSup = A$$

### 10.4. Điều kiện Chấp nhận Mẫu (Acceptance Condition)
Một pattern $P$ được chấp nhận là mẫu tuần tự có trọng số (Weighted Sequential Pattern) khi và chỉ khi:
$$WSUP(P) \ge effectiveMinSup \iff support(P) \times \text{MeanW} \ge effectiveMinSup$$

### 10.5. Đối chiếu với Bản thảo 2026, Phân tích CSDL Rỗng và Yêu cầu Chuẩn tắc
Trong bản thảo [BitPrune2026], Định nghĩa 6 và Mục 3.4 nêu dạng chuẩn hóa:
$$WSUP_{norm}(P) = \frac{support(P)}{|SD|} \times \text{MeanW} \ge \theta_{rel}$$
Biến đổi đại số sang dạng baseline:
$$\frac{support(P)}{|SD|} \times \text{MeanW} \ge \theta_{rel} \iff support(P) \times \text{MeanW} \ge \theta_{rel} \times |SD| = effectiveMinSup$$

**1. Ràng buộc Toán học về Kích thước CSDL ($|SD| > 0$):**
- Phép nhân hai vế với $|SD|$ và phép chia cho $|SD|$ **chỉ có giá trị toán học khi và chỉ khi $|SD| > 0$** và hai vế sử dụng cùng thứ nguyên.

**2. Phân tích Chi tiết Trường hợp CSDL Rỗng ($|SD| = 0$):**
- **Chế độ Tỷ lệ phần trăm (`PERCENT`):** Khi $|SD| = 0$, việc giải mã ngưỡng bị từ chối (`MinimumSupportResolver` ném `IllegalArgumentException("percentage minimum support cannot be resolved for an empty database")`). Dạng chuẩn hóa $\frac{support(P)}{|SD|}$ rơi vào phép chia cho 0, không xác định về mặt toán học. Do đó, tuyệt đối không áp dụng phép biến đổi chuẩn hóa/hiệu dụng khi $|SD| = 0$.
- **Chế độ Tuyệt đối (`ABSOLUTE`):** Chế độ này có thể giải mã ngưỡng trên CSDL rỗng vì giá trị ngưỡng hiệu dụng không phụ thuộc vào kích thước CSDL ($effectiveMinSup = A$). Tuy nhiên, cần phân biệt rõ giữa khả năng giải mã cấu hình ngưỡng (threshold resolution) và khả năng thực thi một lượt khai phá hoàn chỉnh (complete mining execution):
  - *Đối với tính toán MeanW trên CSDL rỗng:* CSDL rỗng không có bản ghi và không có item nào; `ItemWeightCalculator` trả về `ItemWeightResult.empty()` với `meanWeight().isEmpty()`. Hệ thống **không tự tạo ra** một giá trị $MeanW = 0$ giả mạo (`ItemWeightCalculatorTest.java`, dòng 98–106). Vì không có $MeanW$, quy trình khai phá hoàn chỉnh (mining pipeline) không thể đánh giá điều kiện weighted acceptance $support(P) \times \text{MeanW} \ge effectiveMinSup$ để sinh ra mẫu.
  - *Nếu xét riêng phương thức `ResolvedMinimumSupport.meetsThreshold(support, meanWeight)` với một giá trị `meanWeight` hợp lệ được cung cấp từ bên ngoài và $support = 0$ (vì CSDL rỗng có $|SD| = 0$, nên $support \in [0, 0]$ theo hợp đồng kiểm tra tham số của phương thức):*
    1. **Trường hợp ngưỡng dương ($A > 0$):** Bất đẳng thức $0 \times meanWeight \ge A$ cho kết quả `false` (đây chính là phạm vi kiểm thử của ca $A = 2.0$ trong `MinimumSupportResolverTest.java`, dòng 113–115: `meetsThreshold(0, 1.0) == false`).
    2. **Trường hợp ngưỡng bằng không ($A = 0$):** Phép so sánh theo nghĩa đen (literal comparison) $0 \times meanWeight \ge 0$ trả về `true`! Đây chính là mâu thuẫn cốt lõi của vấn đề nghiên cứu R6 (Zero Minimum Support Semantics) khi áp dụng toán tử bao gồm $\ge 0$. C01.1 tuyệt đối không che giấu mâu thuẫn này hay tự ý đổi $\ge$ thành $>$. Vấn đề R6 tiếp tục được giữ nguyên trạng thái `OPEN`.

**3. Yêu cầu Chuẩn tắc đối với WSPM_BitPrune:**
- WSPM_BitPrune **bắt buộc phải bảo toàn** contract về support, MeanW, hệ đơn vị ngưỡng và điều kiện chấp nhận mẫu của baseline Phase B. C01.1 lựa chọn dạng hiệu dụng $support(P) \times \text{MeanW} \ge effectiveMinSup$ làm semantic contract bắt buộc cho implementation tương lai, thay vì dạng chuẩn hóa chia cho $|SD|$ trên từng ứng viên.
- Mức độ bảo toàn này là một nghĩa vụ nghiên cứu cần được kiểm chứng nghiêm ngặt thông qua các nghĩa vụ chứng minh toán học của C01.2 và các bộ kiểm thử vi sai (differential correctness tests) trong các tác vụ triển khai tiếp theo; tài liệu này không tuyên bố trước bất kỳ khẳng định nào về mức độ kế thừa hay tương thích của một bản cài đặt chưa ra đời.
- **Lưu ý nghiên cứu:** Việc đối chiếu đơn vị này thuần túy là chuẩn hóa đại số dưới điều kiện $|SD| > 0$, không cấu thành bằng chứng chứng minh an toàn cắt tỉa.

---

## 11. Mệnh đề MeanW Toàn cục và Tính Giảm Bao đóng (Global MeanW & Downward Closure Proposition)

### 11.1. Phát biểu Mệnh đề (Proposition Statement)
C01.1 thiết lập mệnh đề toán học cốt lõi sau để bàn giao cho C01.2 thực hiện chứng minh hình thức:

> **Mệnh đề 1 (Bảo toàn Tính chất Giảm bao đóng dưới MeanW toàn cục — Proposition OBL-02):**
> Cho $P$ và $Q$ là hai pattern hợp lệ sao cho $Q$ là một mở rộng trực tiếp (I-Extension hoặc S-Extension) của $P$ ($P \sqsubseteq Q$). Giả sử độ hỗ trợ $support(\cdot)$ được tính theo số lượng SID phân biệt, và $MeanW$ là một hằng số toàn cục không âm ($\text{MeanW} \ge 0$).
> Khi đó:
> $$support(Q) \le support(P)$$
> và do đó:
> $$WSUP(Q) \le WSUP(P)$$
> Hệ quả: Nếu $WSUP(P) < effectiveMinSup$, thì với mọi hậu duệ $Q$ mở rộng từ $P$, ta luôn có:
> $$WSUP(Q) < effectiveMinSup$$
> **Trạng thái:** `PROPOSITION / DEFERRED TO C01.2 — NOT YET APPROVED FOR IMPLEMENTATION`.

### 11.2. Danh mục Tiền đề Bắt buộc (Mandatory Assumptions)
Để Mệnh đề 1 có hiệu lực toán học, bắt buộc phải thỏa mãn đầy đủ các giả định sau:
1. **Quan hệ tiền tố:** Phép mở rộng cấu trúc (I-Extension hoặc S-Extension) bảo toàn quan hệ chuỗi con: nếu $Q$ mở rộng từ $P$, thì mọi embedding của $Q$ suy biến thành một embedding của $P$ ($P \sqsubseteq Q$).
2. **Ngữ nghĩa đếm theo SID phân biệt:** Độ hỗ trợ được định nghĩa là lực lượng của tập SID hỗ trợ: $support(P) = |\text{SupportingSIDs}(P)|$.
3. **Tính bất biến của trọng số:** $MeanW$ là một hằng số toàn cục duy nhất cho toàn bộ quá trình khai phá CSDL, không phụ thuộc vào độ dài hay cấu trúc của pattern $P$ hay $Q$.
4. **Tính không âm và hữu hạn:** $\text{MeanW} \in \mathbb{R}$, $0 \le \text{MeanW} < +\infty$.
5. **Tính nhất quán thứ nguyên:** Cả hai vế của bất đẳng thức so sánh ngưỡng đều sử dụng cùng một hệ đơn vị ($WSUP$ và $effectiveMinSup$).
6. **Không khái quát hóa:** Kết luận này **không áp dụng** cho các bài toán khai phá trọng số phụ thuộc chuỗi (sequence-dependent weight như $W(s)$) hoặc bài toán tiện ích cao (Utility Mining), nơi trọng số thay đổi theo từng mẫu.

### 11.3. Ghi nhận Xung đột Nguồn (Source Conflict Record)
- **Tài liệu [BitPrune2026] (Tóm tắt & Mục 1):** Tuyên bố *"Việc tích hợp trọng số làm mất đi tính giảm bao đóng trong Khai phá mẫu tuần tự (WSPM), dẫn đến bùng nổ không gian tìm kiếm..."*.
- **Phân tích đối chiếu:** Nhận định trên chỉ đúng với bài toán WSPM truyền thống sử dụng trọng số phụ thuộc mẫu $W(s)$ (như công thức Eq. (3) trong bài báo 2023). Ngược lại, thuật toán WSPM_PreTree sử dụng $MeanW$ toàn cục làm hệ số nhân tuyến tính với $support(P)$. Theo semantic contract tại Mục 10.3 và Mục 10.5, implementation WSPM_BitPrune tương lai bắt buộc phải bảo toàn định nghĩa này; mức độ bảo toàn thực tế chưa được xác minh và phải chờ kiểm chứng bởi C01.2 và differential testing. Dưới giả định $MeanW$ toàn cục là một hằng số không âm, tính chất phản đơn điệu của $support$ kéo theo tính giảm bao đóng của $WSUP$.
- **Giới hạn phạm vi C01.1:** C01.1 ghi nhận xung đột này, không tự ý viết chứng minh hình thức mà chuyển giao toàn bộ nghĩa vụ chứng minh Mệnh đề 1 sang C01.2.

---

## 12. Đặc tả Ngữ nghĩa Cấu trúc SID Candidate Bitmap (Bitmap Contract)

### 12.1. Định nghĩa Bitmap của Sự kiện Đơn qua Ánh xạ Song ánh
Với mỗi sự kiện $i \in I$, mảng bit $BM(i)$ là một vector nhị phân có độ dài đúng $|SD|$ bit:
$$BM(i) \in \{0, 1\}^{|SD|}$$
Chỉ số bit $c \in \{0, 1, \dots, |SD|-1\}$ là tọa độ nội bộ compact. Giá trị bit tại tọa độ $c = \text{sidToIndex}(sid)$ được định nghĩa chuẩn xác:
$$BM(i)[\text{sidToIndex}(sid)] = \begin{cases} 1 & \text{nếu chuỗi có mã định danh } sid \text{ chứa item } i \\ 0 & \text{ngược lại} \end{cases}$$
- **Lưu ý cốt lõi:** Tuyệt đối không giả định chỉ số bit $c$ bằng giá trị $sid$. Các mã $sid \in \text{Universe}(SD)$ là các số nguyên phân biệt (distinct integers), có thể không liên tục (noncontiguous) và không nhất thiết bắt đầu từ 0 hay 1. Ánh xạ $\text{sidToIndex}$ bảo toàn tính toàn vẹn và độc lập của dữ liệu.

### 12.2. Định nghĩa Bitmap Ứng viên cho Pattern (Candidate Pattern Bitmap)
Cho pattern ứng viên $P$ được hình thành từ tập hợp các sự kiện phân biệt $Items(P) = \{u_1, u_2, \dots, u_r\} \subseteq I$.
Bitmap ứng viên của $P$, ký hiệu $BM(P)$, được xác định thông qua phép toán hội logic từng bit (Bitwise AND):
$$BM(P) = \bigwedge_{u \in Items(P)} BM(u) \iff BM(P)[c] = \prod_{u \in Items(P)} BM(u)[c] \quad (\forall c \in [0, |SD|-1])$$
Khi mở rộng $P' = P \oplus_I x$ hoặc $P' = P \oplus_S x$:
$$BM(P') = BM(P) \ \& \ BM(x)$$

### 12.3. Ngữ nghĩa Cốt lõi: Điều kiện Cần và Đề xuất Cận trên Cardinality
Cấu trúc Bitmap trong BitPrune được đặc tả là một **bộ lọc điều kiện cần (necessary-condition filter)**, tuyệt đối không phải là biểu diễn hỗ trợ thời gian chuẩn xác:
1. **Bao hàm tập SID (Mệnh đề OBL-03):** Tập hợp các SID có bit 1 trong $BM(P)$ tạo thành một tập cha (superset) chứa toàn bộ các SID hỗ trợ thực tế của $P$:
   $$\text{SupportingSIDs}(P) \subseteq \{ \text{indexToSid}(c) \mid BM(P)[c] = 1 \}$$
2. **Cận trên của độ hỗ trợ:** Số lượng bit 1 trong $BM(P)$ (ký hiệu $|BM(P)|$ hay $cardinality(BM(P))$) là một **cận trên (upper bound)** của độ hỗ trợ thực tế:
   $$support(P) \le |BM(P)|$$
3. **Cận trên của WSUP:**
   $$WSUP_{UB}(P) = |BM(P)| \times \text{MeanW} \ge support(P) \times \text{MeanW} = WSUP(P)$$
4. **Đề xuất Quy tắc Cắt tỉa Cận trên Trọng số (Proposed Rule — DEFERRED TO C01.2):**
   Bản thảo [BitPrune2026] (Bổ đề 2 và Mục 3.4) đề xuất:
   $$\text{Nếu } WSUP_{UB}(P) < effectiveMinSup \implies WSUP(P) < effectiveMinSup$$
   ứng viên $P$ được đề xuất loại bỏ sớm mà không cần bước xác minh thời gian.
   **Trạng thái phê duyệt:** `PROPOSITION / DEFERRED TO C01.2 — NOT YET APPROVED FOR IMPLEMENTATION`.
   *Giới hạn C01.1:* C01.1 chỉ đặc tả mệnh đề toán học về cận trên. C01.1 tuyệt đối không phê duyệt quyết định cắt tỉa candidate hay subtree. Quyết định loại bỏ ứng viên còn phụ thuộc vào phân tích an toàn tại điểm đẳng thức ($=$), sai số dấu phẩy động, và đặc biệt là ngữ nghĩa ngưỡng 0 của vấn đề R6 (tại $effectiveMinSup = 0$, mẫu có support 0 vẫn thỏa mãn literal $0 \times MeanW \ge 0$). Toàn bộ chứng minh thuộc thẩm quyền của C01.2.

### 12.4. Những yếu tố Bitmap KHÔNG xác minh
Bitmap hoàn toàn **bỏ qua** và **không thể xác minh** các thông tin ngữ nghĩa sau:
1. **Ranh giới itemset (Itemset boundaries):** Không phân biệt được hai item nằm trong cùng một itemset hay ở hai itemset khác nhau.
2. **Thứ tự thời gian (Temporal order):** Không phân biệt được item $a$ xảy ra trước item $b$ hay ngược lại.
3. **Số lần xuất hiện lặp lại (Repeated-item multiplicity):** Với pattern có item lặp lại như $\langle(a)(a)\rangle$, phép toán $BM(a) \ \& \ BM(a) = BM(a)$ không thể phát hiện chuỗi có chứa ít nhất 2 lần xuất hiện của $a$ ở các thời điểm khác nhau hay không.
4. **Tính hợp lệ của cấu trúc nhúng:** Không đảm bảo sự tồn tại của nhúng thỏa mãn $j_1 < j_2 < \dots < j_k$.

---

## 13. Đặc tả Ngữ nghĩa Ma trận Đồng xuất hiện Đồng thời (CMAP-I Contract)

### 13.1. Mục đích và Bản chất Ngữ nghĩa
Ma trận đồng xuất hiện đồng thời $\text{CMAP-I}$ là một cấu trúc bảng 2 chiều kích thước $|I| \times |I|$ lưu trữ các giá trị boolean, đóng vai trò là **bộ lọc điều kiện cần mức $O(1)$** cho phép mở rộng trong itemset (I-Extension).

### 13.2. Định nghĩa Toán học của CMAP-I
Cho hai sự kiện $a, b \in I$ với $a < b$ (theo canonical order).
$$\text{CMAP-I}(a, b) = true \iff \exists sid \in \text{Universe}(SD), \exists t \in [1, size(S_{sid})] \text{ sao cho } \{a, b\} \subseteq s_{sid, t}$$
Ngược lại, $\text{CMAP-I}(a, b) = false$.

### 13.3. Các Ràng buộc Ngữ nghĩa Bắt buộc
1. **Cùng chuỗi và cùng itemset:** $a$ và $b$ bắt buộc phải cùng xuất hiện trong **chính cùng một itemset** $s_{sid, t}$ của một chuỗi dữ liệu nào đó.
2. **Không kết hợp chéo SID:** Tuyệt đối không được kết hợp sự xuất hiện của $a$ từ chuỗi này với $b$ từ chuỗi khác.
3. **Thứ tự chuẩn tắc canonical:** Vì I-Extension chỉ sinh ứng viên với $a < b$, $\text{CMAP-I}$ chỉ cần định nghĩa và tra cứu cho các cặp $(a, b)$ với $a < b$.
4. **Đề xuất Mệnh đề Lọc CMAP-I (Proposition OBL-05 — DEFERRED TO C01.2):**
   Cho cặp $(a, b)$ với $a < b$. Nếu $\text{CMAP-I}(a, b) = false$, thì với mọi pattern $P$ có item cuối cùng của itemset cuối là $a$, ta có:
   $$support(P \oplus_I b) = 0$$
   **Trạng thái phê duyệt:** `PROPOSITION / DEFERRED TO C01.2 — NOT YET APPROVED FOR IMPLEMENTATION`.
   *Giới hạn C01.1:* C01.1 chỉ đặc tả mệnh đề toán học về sự triệt tiêu độ hỗ trợ ($support = 0$). C01.1 không phê duyệt hành vi operational "loại ngay lập tức" ứng viên. Quyết định loại ứng viên có support 0 còn chịu ràng buộc bởi vấn đề R6 tại ngưỡng 0. Nghĩa vụ chứng minh không có false negative thuộc về C01.2.

---

## 14. Đặc tả Ngữ nghĩa Ma trận Đồng xuất hiện Kế tiếp (CMAP-S Contract)

### 14.1. Mục đích và Bản chất Ngữ nghĩa
Ma trận đồng xuất hiện kế tiếp $\text{CMAP-S}$ là một cấu trúc bảng 2 chiều kích thước $|I| \times |I|$ lưu trữ các giá trị boolean, đóng vai trò là **bộ lọc điều kiện cần mức $O(1)$** cho phép mở rộng chuỗi (S-Extension).

### 14.2. Định nghĩa Toán học của CMAP-S
Cho hai sự kiện $a, b \in I$ (trong đó $a$ và $b$ có thể trùng nhau hoặc khác nhau).
$$\text{CMAP-S}(a, b) = true \iff \exists sid \in \text{Universe}(SD), \exists 1 \le t < u \le size(S_{sid}) \text{ sao cho } a \in s_{sid, t} \text{ và } b \in s_{sid, u}$$
Ngược lại, $\text{CMAP-S}(a, b) = false$.

### 14.3. Các Ràng buộc Ngữ nghĩa Bắt buộc
1. **Thứ tự trước sau nghiêm ngặt ($t < u$):** Itemset chứa $a$ phải xảy ra **trước nghiêm ngặt** so với itemset chứa $b$.
2. **Không chấp nhận đồng thời:** Nếu $a$ và $b$ chỉ cùng xuất hiện trong một itemset duy nhất ($t = u$) trong chuỗi và không bao giờ xuất hiện ở hai itemset khác nhau với $t < u$, thì chuỗi đó **không đóng góp** giá trị $true$ cho $\text{CMAP-S}(a, b)$.
3. **Xử lý sự kiện lặp lại ($a = b$):**
   $\text{CMAP-S}(a, a) = true$ khi và chỉ khi tồn tại ít nhất một chuỗi trong CSDL chứa item $a$ ở ít nhất **hai itemset khác nhau** tại các thời điểm $t < u$. Nếu mọi chuỗi trong CSDL chỉ chứa $a$ tối đa 1 lần, thì $\text{CMAP-S}(a, a) = false$.
4. **Không kết hợp chéo SID:** $a$ tại $t$ và $b$ tại $u$ bắt buộc phải thuộc về cùng một chuỗi $S_{sid}$.
5. **Đề xuất Mệnh đề Lọc CMAP-S (Proposition OBL-06 — DEFERRED TO C01.2):**
   Cho cặp $(a, b)$. Nếu $\text{CMAP-S}(a, b) = false$, thì với mọi pattern $P$ có item cuối cùng của itemset cuối là $a$, ta có:
   $$support(P \oplus_S b) = 0$$
   (bao gồm cả trường hợp $a = b$ đối với sự kiện lặp lại).
   **Trạng thái phê duyệt:** `PROPOSITION / DEFERRED TO C01.2 — NOT YET APPROVED FOR IMPLEMENTATION`.
   *Giới hạn C01.1:* Tương tự CMAP-I, C01.1 không phê duyệt lệnh operational "loại ngay". Toàn bộ chứng minh không có false negative và xử lý ngưỡng 0 thuộc thẩm quyền C01.2.
6. **Đối chiếu và làm rõ so với Bản thảo 2026:**
   - Trong bản thảo [BitPrune2026], Định nghĩa 9 định nghĩa CMAP gộp: *"CMAP(A, B) = true nếu A từng đứng trước hoặc cùng lúc với B ít nhất một lần"*.
   - *Phân loại:* Việc phân tách thành hai ma trận độc lập **CMAP-I** (đồng thời) và **CMAP-S** (kế tiếp nghiêm ngặt) là một quyết định **`IMPLEMENTATION POLICY / INFERENCE`** của hệ thống, giúp nâng cao độ chặt của lưới lọc điều kiện cần mà vẫn bảo toàn quan hệ chuỗi. Tính an toàn cắt tỉa của phân tách này được chuyển giao thành nghĩa vụ chứng minh trong C01.2.

---

## 15. Đặc tả Hợp đồng Xác minh Trình tự Thời gian Chuẩn xác (Exact Temporal Verification Contract)

Để loại bỏ hoàn toàn sự nhập nhằng giữa việc xác định độ hỗ trợ và việc duy trì trạng thái vị trí thời gian cho các bước mở rộng tiếp theo, C01.1 phân tách tường minh hai hợp đồng độc lập:

### 15.1. Hợp đồng Xác minh Tư cách Hỗ trợ (Support-Only Membership Verification Contract)
- **Mục tiêu:** Xác định tập hợp $\text{SupportingSIDs}(P')$ và tính giá trị độ hỗ trợ chuẩn xác:
  $$support(P') = |\text{SupportingSIDs}(P')|$$
- **Vị từ kiểm tra trên từng chuỗi:** Với mỗi $sid \in \text{Universe}(SD)$ mà $BM(P')[\text{sidToIndex}(sid)] = 1$, kiểm tra vị từ tồn tại:
  $$\text{existsEmbedding}(P', S_{sid}) \in \{true, false\}$$
- **Quy tắc ngắn mạch hỗ trợ (Support Short-Circuit Rule):**
  - Khi duyệt tìm các nhúng của $P'$ trong chuỗi $S_{sid}$, ngay khi thuật toán phát hiện được **nhúng hợp lệ đầu tiên** $\mathbf{j} = (j_1, \dots, j_k)$, việc kiểm tra chuỗi $S_{sid}$ cho mục đích đếm độ hỗ trợ **được phép dừng lại (short-circuit)**.
  - Chuỗi $sid$ này được thêm vào $\text{SupportingSIDs}(P')$ và đóng góp đúng 1 đơn vị vào $support(P')$.
- **Giới hạn nghiêm ngặt:** Việc short-circuit này **chỉ có hiệu lực đối với việc tính support của chính $P'$**. Tuyệt đối không được suy diễn rằng các nhúng khác hoặc các điểm mốc (endpoints) khác trong chuỗi $S_{sid}$ là không cần thiết cho các bước mở rộng sâu hơn của cây tiền tố DFS.

### 15.2. Hợp đồng Xây dựng Trạng thái Vị trí Thời gian (Positional-State Construction Contract)
- **Mục tiêu:** Cung cấp thông tin vị trí thời gian cần thiết để phục vụ cho các phép mở rộng $P' \oplus_I x$ và $P' \oplus_S x$ ở các tầng đệ quy tiếp theo trong không gian DFS.
- **Ràng buộc về Đa điểm mốc (Multiple Endpoints Constraint):**
  - Một chuỗi $S_{sid}$ có thể chứa nhiều nhúng hợp lệ của $P'$ với các điểm mốc kết thúc khác nhau:
    $$\mathcal{E}(P', S_{sid}) = \{ j_k \mid (j_1, \dots, j_k) \text{ là nhúng hợp lệ của } P' \text{ trong } S_{sid} \}$$
  - Phép mở rộng S-Extension tiếp theo $P' \oplus_S x$ yêu cầu tìm $x$ tại vị trí $j_{k+1} > j_k$.
  - Phép mở rộng I-Extension tiếp theo $P' \oplus_I x$ yêu cầu tìm $x$ tại chính vị trí $j_k$.
- **Cấm ngắn mạch trạng thái vị trí (No Short-Circuit for Positional State):**
  - Tuyệt đối **không được áp dụng short-circuit** để chỉ lưu lại điểm mốc đầu tiên khi xây dựng positional state cho các mở rộng tiếp theo, trừ phi có chứng minh hình thức rằng việc bỏ qua các điểm mốc khác không làm mất bất kỳ mẫu mở rộng hợp lệ nào.
- **Tình trạng của Chính sách Nén Điểm mốc (Endpoint Compression Status):**
  - Các chính sách nén mốc thời gian như **Earliest Endpoint Policy** ($j_k^{min}$) hay **Latest Endpoint Policy** ($j_k^{max}$) **HIỆN CHƯA ĐƯỢC PHÊ DUYỆT**.
  - Việc chứng minh tính bảo toàn hoặc xây dựng phản ví dụ bác bỏ chính sách nén điểm mốc thuộc nghĩa vụ chứng minh **OBL-07 của C01.2**.
  - C01.1 không thiết kế lớp Java hay cấu trúc bộ nhớ cho positional state; việc này thuộc thẩm quyền của C02/C04/C07/C08 sau khi C01.2 nghiệm thu.

---

## 16. Ngữ nghĩa của Repeated Items và Multiple Embeddings

### 16.1. Khả năng xuất hiện hợp pháp của Repeated Items
Trong bài toán khai phá mẫu tuần tự, sự lặp lại của item dọc theo trục thời gian mang ý nghĩa thực tiễn to lớn (ví dụ: một khách hàng mua lại cùng một sản phẩm ở các đơn hàng kế tiếp).
- Pattern $\langle(a)(a)\rangle$: Item $a$ lặp lại ở 2 itemset khác nhau.
- Pattern $\langle(a)(b)(a)\rangle$: Item $a$ lặp lại sau khi có sự kiện $b$.
- Pattern $\langle(ab)(a)\rangle$: Item $a$ xuất hiện đồng thời với $b$, sau đó lặp lại ở itemset sau.

### 16.2. Thách thức Ngữ nghĩa đối với Cấu trúc Dữ liệu
1. **Đối với Bitmap:**
   $$BM(\langle(a)(a)\rangle) = BM(a) \ \& \ BM(a) = BM(a)$$
   Bitmap của $\langle(a)(a)\rangle$ giống hệt Bitmap của $\langle(a)\rangle$. Bitmap không thể phân biệt được chuỗi chỉ có một item $a$ với chuỗi có nhiều item $a$. Do đó, Cardinality của Bitmap đối với mẫu có item lặp lại sẽ lỏng hơn (upper bound rộng hơn), nhưng vẫn đảm bảo là một cận trên toán học ($ActualSupportingSIDs \subseteq BM$, Mệnh đề OBL-03).
2. **Đối với CMAP-S:**
   $\text{CMAP-S}(a, a)$ phản ánh xem trong toàn bộ CSDL có tồn tại chuỗi nào có $a$ xuất hiện ở ít nhất hai thời điểm khác nhau hay không. Nếu $\text{CMAP-S}(a, a) = false$, mệnh đề toán học cho thấy không có chuỗi nào hỗ trợ việc nối tiếp $a$ sau $a$ ($support = 0$). Việc đề xuất loại nhánh này thuộc Proposition OBL-06 và phải chờ C01.2 chứng minh.
3. **Đối với Exact Temporal Verification:**
   Cơ chế duyệt vị trí thời gian bắt buộc phải theo dõi các chỉ số vị trí khác nhau để đảm bảo $j_{k+1} > j_k$, tránh việc nhúng trùng lặp cùng một lần xuất hiện vật lý của $a$ cho cả hai vị trí trong pattern.

---

## 17. Các Vấn đề Chưa giải quyết và Ngữ nghĩa Ngưỡng 0 (Unresolved Issues & R6)

### 17.1. Bảng Trạng thái Danh mục Vấn đề Nghiên cứu Toàn cục (R1–R8)
Theo quy định bất biến của repository, C01.1 bảo toàn nguyên vẹn trạng thái của các vấn đề nghiên cứu từ Phase B:

- **R1 — Sequence-Weight Discrepancy:** `OPEN`.
- **R2 — Percentage Minimum Support:** `RESOLVED FOR IMPLEMENTATION`.
- **R3 — Prime-Block Mechanics:** `RESOLVED`.
- **R4 — Complete Table 1 Oracle:** `RESOLVED`.
- **R5 — Item-Weight Denominator:** `RESOLVED`.
- **R6 — Zero Minimum Support Semantics:** `OPEN`.
- **R7 — Transaction-to-Sequence Mapping:** `OPEN`.
- **R8 — SIGN Paper Benchmark Reproduction Discrepancy:** `OPEN`.

Tuyệt đối không thay đổi trạng thái của bất kỳ vấn đề nào trong tác vụ C01.1, C01.1-R1 hay C01.1-R3.

### 17.2. Phân tích Ngữ nghĩa Xung đột quanh Ngưỡng 0 (Issue R6)
Vấn đề R6 liên quan đến ngữ nghĩa toán học khi cấu hình ngưỡng hiệu dụng bằng 0 ($effectiveMinSup = 0$):
1. **Xung đột điều kiện toán học (Literal Comparison):**
   Điều kiện chấp nhận mẫu là:
   $$support(P) \times \text{MeanW} \ge effectiveMinSup$$
   Nếu $effectiveMinSup = 0$, bất đẳng thức trở thành:
   $$support(P) \times \text{MeanW} \ge 0$$
   Vì $support(P) \ge 0$ và $\text{MeanW} \ge 0$, bất đẳng thức này theo nghĩa đen nghiệm đúng với mọi pattern candidate có $support(P) = 0$ (tức các mẫu hoàn toàn không xuất hiện trong CSDL).
2. **Xung đột với cấu trúc lọc của BitPrune:**
   - Nếu $\text{CMAP}(a, b) = false$ hoặc $|BM(P)| = 0$, điều đó phản ánh chính xác rằng $support(P) = 0$.
   - Nếu ta áp dụng quy tắc cắt tỉa loại bỏ ứng viên khi $\text{CMAP} = false$ hoặc khi $|BM| = 0$, thì về mặt cấu trúc dữ liệu, ta đang loại bỏ một ứng viên có support = 0.
   - Nhưng theo nghĩa đen (literal inclusive $\ge 0$), một ứng viên có support = 0 vẫn thỏa mãn $0 \times \text{MeanW} \ge 0$!
3. **Quy tắc An toàn Nghiên cứu của C01.1:**
   - C01.1 **không tự ý sửa đổi** toán tử so sánh $\ge$ thành $>$.
   - C01.1 **không tự ý cấm** cấu hình ngưỡng 0.
   - C01.1 **không phê duyệt** các quy tắc cắt tỉa CMAP hay Bitmap tại ngưỡng 0.
   - Giữ nguyên trạng thái R6 là **`OPEN`**.
   - Toàn bộ việc phân tích toán học, đánh giá hành vi biên tại ngưỡng 0 và thiết kế giải pháp an toàn được chuyển giao toàn bộ sang **C01.2** và thẩm định của **Research Reviewer**.

---

## 18. Danh mục Nghĩa vụ Chuyển giao sang C01.2 (C01.2 Proof-Obligation Handoff Matrix)

Mọi mệnh đề toán học, phân tích điều kiện biên và thiết kế kiểm thử false-negative được đóng gói vào ma trận chuyển giao trách nhiệm dưới đây. Không có chứng minh hình thức nào được thực hiện trong C01.1.

| Obligation ID | Mệnh đề cần chứng minh hoặc bác bỏ | Tiền đề / Giả định toán học (Assumptions) | Nguy cơ sai sót (False-Negative Risk) | Nguồn / Phụ thuộc ngữ nghĩa | Ca kiểm thử biên cần thiết kế (Edge Cases) | Trạng thái chuyển giao |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **OBL-01** | **Tính phản đơn điệu của Support:**
$Q$ là mở rộng hợp lệ từ $P$ $\implies support(Q) \le support(P)$. | - Mở rộng I/S bảo toàn chuỗi con.
- Support đếm distinct actual SID trong $\text{Universe}(SD)$. | Cực cao: Nếu sai, không thể cắt tỉa nhánh cây tìm kiếm DFS. | [Pham2023], Trang 362; Mục 6 & 7 của C01.1. | - Repeated items ($\langle(a)(a)\rangle$).
- Multiple embeddings trong cùng SID. | `DEFERRED TO C01.2` |
| **OBL-02** | **Tính giảm bao đóng của WSUP dưới MeanW:**
$Q$ là mở rộng từ $P$, $\text{MeanW} \ge 0 \implies WSUP(Q) \le WSUP(P)$.
Nếu $WSUP(P) < \theta \implies WSUP(Q) < \theta$. | - $MeanW$ là hằng số toàn cục $\ge 0$.
- OBL-01 nghiệm đúng.
- Cùng đơn vị đo lường.
- $\|SD\| > 0$. | Rất cao: Nếu sai, việc dừng duyệt DFS tại nút không đạt ngưỡng sẽ làm mất mẫu hợp lệ. | [BitPrune2026] xung đột với [Pham2023]; Mục 10 & 11 của C01.1. | - $\text{MeanW} = 0$.
- Pattern có $support = 1$.
- Ngưỡng hiệu dụng có phần thập phân. | `DEFERRED TO C01.2` |
| **OBL-03** | **Cận trên Cardinality của SID Candidate Bitmap:**
$\text{SupportingSIDs}(P) \subseteq \text{indexToSid}(BM(P)) \implies support(P) \le \|BM(P)\|$. | - $BM(i)$ bật bit cho mọi SID chứa $i$.
- Ánh xạ song ánh `sidToIndex` chuẩn xác trên $\text{Universe}(SD)$.
- $BM(P)$ tạo bởi phép AND bitmask. | Cực cao: Nếu $BM(P)$ thiếu SID hỗ trợ thực tế, mẫu hợp lệ sẽ bị bỏ sót. | [BitPrune2026], Bổ đề 1; Mục 12 của C01.1. | - Mã SID không liên tục (noncontiguous SIDs).
- Item xuất hiện nhiều lần trong 1 chuỗi.
- CSDL có số chuỗi không chia hết cho 64. | `DEFERRED TO C01.2` |
| **OBL-04** | **Phân tích An toàn Cắt tỉa Cận trên Trọng số (Bitmap WSUP UB):**
Chứng minh: Nếu $\|BM(P)\| \times \text{MeanW} < effectiveMinSup \implies WSUP(P) < effectiveMinSup$. | - OBL-03 nghiệm đúng.
- $\text{MeanW} \ge 0$.
- Bất đẳng thức nghiêm ngặt ($<$). | Cực cao: Cắt tỉa nhầm nếu so sánh sai dấu hoặc cắt tỉa tại đẳng thức ($=$). | [BitPrune2026], Bổ đề 2; Mục 12 của C01.1. | - Điểm đẳng thức chính xác ($\|BM\| \times MeanW = \theta$).
- Sai số dấu phẩy động làm tròn.
- Trường hợp ngưỡng 0 ($effectiveMinSup = 0$). | `DEFERRED TO C01.2` |
| **OBL-05** | **Phân tích Bảo toàn của Bộ lọc CMAP-I:**
Chứng minh: $\text{CMAP-I}(a, b) = false \implies support(P \oplus_I b) = 0$. | - $\text{CMAP-I}$ xây dựng đúng định nghĩa Mục 13.
- Canonical item order $a < b$. | Rất cao: Lọc nhầm một cặp item có đồng xuất hiện thực tế nhưng CMAP không bật. | [BitPrune2026], Định nghĩa 9; Mục 13 của C01.1. | - Cặp item xuất hiện ở SID khác nhau nhưng không cùng SID.
- Cặp item chỉ xuất hiện ở itemset khác nhau.
- Ngưỡng 0 (xung đột R6). | `DEFERRED TO C01.2` |
| **OBL-06** | **Phân tích Bảo toàn của Bộ lọc CMAP-S:**
Chứng minh: $\text{CMAP-S}(a, b) = false \implies support(P \oplus_S b) = 0$. | - $\text{CMAP-S}$ xây dựng đúng định nghĩa Mục 14 ($t < u$).
- Xét riêng trường hợp $a = b$. | Rất cao: Lọc nhầm một cặp có quan hệ thứ tự thời gian. | [BitPrune2026], Định nghĩa 9; Mục 14 của C01.1. | - Repeated items $\text{CMAP-S}(a, a)$.
- Cặp chỉ xuất hiện đồng thời trong cùng 1 itemset.
- Ngưỡng 0 (xung đột R6). | `DEFERRED TO C01.2` |
| **OBL-07** | **Đặc tả Xác minh Thời gian và Đánh giá Nén Điểm mốc (Endpoint Compression):**
Chứng minh hoặc xây dựng phản ví dụ cho chính sách nén earliest/latest endpoint. Xác định cấu trúc vị trí thời gian tối thiểu an toàn. | - Phân tách Support-only và Positional-state.
- Ngữ nghĩa nhúng chuỗi con của Mục 6.
- Multiple embeddings và repeated items. | Rất cao: Giả định sai về nén điểm mốc sẽ gây false negative ở các tầng DFS sâu hơn. | [Pham2023], Trang 362; Mục 15 & 16 của C01.1. | - Pattern $\langle(a)(b)(a)\rangle$ trong chuỗi $\langle(a)(a)(b)(a)\rangle$.
- Nhiều nhúng đan xen có điểm kết thúc khác nhau. | `DEFERRED TO C01.2` |
| **OBL-08** | **An toàn Toán học tại Ngưỡng 0 và Phân định Biên Dữ liệu (Zero-Threshold & Input Boundaries):**
Phân tích tính an toàn của các đề xuất cắt tỉa CMAP và Bitmap khi $effectiveMinSup = 0$ và khi CSDL rỗng. | - Bảo toàn R6 là `OPEN`.
- Không tự ý thay đổi ngữ nghĩa $\ge$.
- Phân biệt resolution và complete mining execution.
- Phân biệt CSDL rỗng ($|SD| = 0$) và dữ liệu chuỗi rỗng không hợp lệ. | Trung bình / Cao: Mâu thuẫn giữa lý thuyết chấp nhận pattern support 0 và kỹ thuật lọc. | `PHASE_C_WSPM_BitPrune.md`, Mục 5.6; Mục 10, 17 của C01.1; `Sequence.java`; `SpmfSequenceParser.java`. | - Cấu hình `minsup = 0%`.
- CSDL rỗng ($|SD| = 0$, $A > 0$ vs $A = 0$).
- Dữ liệu đầu vào biểu diễn sequence rỗng (phải bị từ chối tại parser/domain boundary; không được đưa vào mining như một SequenceDatabase hợp lệ). | `DEFERRED TO C01.2` |

---

## 19. Bảng Kiểm Tra Nghiệm Thu (Acceptance Checklist C01.1-R3)

- [x] Residual Finding 3 (Finding 1 của C01.1-R3) được khắc phục: Loại bỏ hoàn toàn cụm từ "(và kế thừa sang WSPM_BitPrune)" tại Mục 11.3; khẳng định WSPM_PreTree sử dụng MeanW toàn cục và implementation WSPM_BitPrune tương lai bắt buộc phải bảo toàn định nghĩa này; mức độ bảo toàn thực tế phải chờ C01.2 và differential testing xác minh.
- [x] Invalid boundary vector (Finding 2 của C01.1-R3) được khắc phục: Ma trận OBL-08 tại Mục 18 và Mục 3 (EV-21) không mô tả chuỗi rỗng như một CSDL mining hợp lệ; phân định rạch ròi giữa CSDL rỗng ($|SD| = 0$) và dữ liệu chuỗi rỗng (bị từ chối tại ranh giới parser/domain theo `Sequence.java:16` và `SpmfSequenceParser.java:93`).
- [x] Không còn bất kỳ phát biểu nào trình bày hành vi BitPrune chưa triển khai như sự thật đã kiểm chứng.
- [x] Toàn bộ các phát hiện trước đó của C01.1-R1 và C01.1-R2 tiếp tục được bảo toàn nghiêm ngặt (hệ đơn vị CSDL rỗng, phạm vi test evidence, miền giá trị SID kiểu `int`, phân tách support-only membership vs positional-state).
- [x] Bảo toàn nguyên vẹn danh mục R1–R8; giữ nguyên R6 là `OPEN`.
- [x] Không thực hiện trước các chứng minh hình thức hoặc thiết kế kiểm thử thuộc thẩm quyền C01.2.
- [x] Không chỉnh sửa baseline WSPM_PreTree đã đóng băng.
- [x] Không sửa mã nguồn Java, bài kiểm thử JUnit, POM hay tài liệu roadmap.
- [x] Chỉ tệp deliverable `docs/research/wspm-bitprune-c01-1-semantic-contract.md` được chỉnh sửa.
- [x] Không bắt đầu tác vụ C01.2 hoặc C02.

---

## 20. Xác Nhận Dừng Triển Khai (STOP Confirmation)

Tôi xác nhận tuân thủ tuyệt đối quy tắc dừng của Hợp đồng khắc phục C01.1-R3:
1. **Không viết hoặc sửa bất kỳ tệp Java nào** trong `src/main/java`.
2. **Không viết hoặc sửa bất kỳ bài kiểm thử JUnit nào** trong `src/test/java`.
3. **Không cài đặt các cấu trúc dữ liệu** Bitmap, CMAP, Positional Index hay thuật toán khai phá BitPrune.
4. **Không thực hiện chứng minh an toàn cắt tỉa hình thức** và không xây dựng bộ test vector thuộc thẩm quyền của C01.2.
5. **Không sửa đổi trạng thái R1–R8**; vấn đề R6 vẫn hoàn toàn `OPEN`.
6. **Không thực hiện commit, push, git reset hay git clean**.
7. **Không tự ý chuyển sang C01.2 hoặc C02**.
8. **Đã dừng hoàn toàn** sau khi hoàn thành việc khắc phục hai findings trên deliverable C01.1 để chờ nghiệm thu từ Research Reviewer.
