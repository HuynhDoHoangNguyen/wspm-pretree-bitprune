# BÁO CÁO CHỨNG MINH TOÁN HỌC VÀ THIẾT KẾ KIỂM THỬ AN TOÀN CẮT TỈA WSPM_BITPRUNE
## Pruning Safety Proofs & Test Design Specification (C01.2 — Bản Khắc phục C01.2-R2)

---

## 1. Task Identity và Phạm vi Đặc tả

### 1.1. Thông tin định danh tác vụ
- **Repository:** `wspm-pretree-bitprune`
- **Phase:** C — WSPM_BitPrune Research & Implementation
- **Parent Task:** C01 — Source Fidelity & Mathematical Safety Design
- **Task ID:** C01.2 (Đợt khắc phục: C01.2-R2 sau review `PARTIAL — NOT ACCEPTED`)
- **Task Name:** Pruning Safety Proofs & Test Design
- **Tiền đề bắt buộc (Prerequisite):** Task C01.1 đã được Research Reviewer chính thức nghiệm thu (`ACCEPTED`) sau vòng khắc phục C01.1-R3.
- **Trạng thái tác vụ:** SUBMITTED FOR REVIEW / AWAITING RESEARCH REVIEWER ACCEPTANCE
- **Phase B Freeze Commit SHA:** `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`
- **Ngôn ngữ văn bản:** Tiếng Việt (kèm đối chiếu thuật ngữ khoa học quốc tế và ký hiệu hình thức KaTeX)

### 1.2. Mục tiêu nghiên cứu của C01.2 và Đợt Khắc phục C01.2-R2
Mục tiêu của C01.2 là tiếp nhận nguyên vẹn bản đặc tả ngữ nghĩa (Semantic Contract) đã được nghiệm thu tại `docs/research/wspm-bitprune-c01-1-semantic-contract.md` để:
1. Thực hiện các chứng minh toán học hình thức (formal proofs), bác bỏ (refutations), hoặc xác định trạng thái chưa giải quyết (`UNRESOLVED`) cho toàn bộ 8 nghĩa vụ nghiên cứu đã bàn giao: **OBL-01 đến OBL-08**.
2. Thiết lập sổ đăng ký giả thiết (Assumption Registry) chi tiết cho từng mệnh đề và quy tắc cắt tỉa.
3. Phân tích định lượng và định tính rủi ro sai sót loại bỏ nhầm mẫu hợp lệ (False-Negative Risk).
4. Phân biệt tường minh giữa việc từ chối một ứng viên đơn lẻ (**Candidate Pruning**) và việc cắt tỉa toàn bộ nhánh cây con tìm kiếm DFS (**Subtree Pruning**).
5. Xây dựng danh mục phản ví dụ (Counterexample Catalogue) tối thiểu nhằm bác bỏ các giả định nén điểm mốc (endpoint compression) hoặc các ngộ nhận cắt tỉa không an toàn.
6. Thiết kế danh mục vector kiểm thử tất định (Deterministic Test Vector Catalogue) hoàn chỉnh với tập SID hỗ trợ kỳ vọng, làm tiêu chuẩn nghiệm thu độc lập cho các tác vụ hiện thực hóa ở các Gate tiếp theo.

Đợt khắc phục C01.2-R2 xử lý dứt điểm 3 residual findings do Research Reviewer phát hiện sau đợt R1:
- **Finding 1 (C01.2-R2):** Chuẩn hóa expected exception message của TV-11B thành `"-1 cannot close an empty itemset"` (chứa cụm từ `"empty itemset"`), phù hợp chính xác với thông báo thực tế của `SpmfSequenceParser.java:82`.
- **Finding 2 (C01.2-R2):** Hoàn thiện TV-09 thành deterministic vector hoàn chỉnh với actual SID universe `Universe(SD) = {1, 2, ..., 65}`, nội dung chuỗi cụ thể ($S_1 \dots S_{64} = \langle(b)\rangle$, $S_{65} = \langle(a)\rangle$), ánh xạ song ánh `sidToIndex`, expected supporting SIDs `{65}`, exact support = 1 và expected Bitmap/cardinality/padding bits.
- **Finding 3 (C01.2-R2):** Thu hẹp phát biểu an toàn cắt tỉa trong OBL-08 (Bảng 12.1) thành phát biểu có điều kiện: operational pruning chỉ an toàn khi toàn bộ assumptions của từng OBL được thỏa mãn; OBL-04 được phê duyệt ở mức toán học trên số thực với bất đẳng thức nghiêm ngặt $<$; chính sách comparator cho Java `double` vẫn `UNRESOLVED`; đồng thời chuẩn hóa ngôn ngữ trung lập trong phần Risks/Deviations không tự ý gán quyền cho C02/C03.

### 1.3. Ranh giới và Giới hạn Tuyệt đối (Scope Boundaries & Prohibitions)
- **Được phép trong C01.2-R2:**
  - Chỉnh sửa duy nhất tệp văn bản nghiên cứu: `docs/research/wspm-bitprune-c01-2-pruning-safety.md`.
  - Khắc phục đúng 3 findings được yêu cầu.
- **Tuyệt đối không được phép trong C01.2-R2:**
  - Không sửa đổi tài liệu ngữ nghĩa C01.1 (`docs/research/wspm-bitprune-c01-1-semantic-contract.md`). Mọi định nghĩa ngữ nghĩa được tiếp nhận nguyên vẹn làm cơ sở bất biến.
  - Không chỉnh sửa lộ trình `PHASE_C_WSPM_BitPrune.md`, `AGENTS.md` hay các tài liệu của Phase B.
  - Không viết hoặc chỉnh sửa bất kỳ dòng mã nguồn Java nào (`src/main/java`).
  - Không viết hoặc chỉnh sửa bất kỳ bài kiểm thử JUnit nào (`src/test/java`).
  - Không triển khai các cấu trúc dữ liệu Bitmap, CMAP, Positional Index hay thiết kế module C02/C03.
  - Không thay đổi trạng thái của các vấn đề nghiên cứu toàn cục R1–R8; vấn đề R6 tiếp tục là `Zero Minimum Support Semantics — OPEN`.
  - Không tự ý đánh dấu C01.2 hay C01 là `ACCEPTED` (thẩm quyền duy nhất thuộc về Research Reviewer).
  - Không thực hiện các lệnh git làm thay đổi lịch sử commit (`git commit`, `git push`, `git reset`, `git clean`).

---

## 2. Danh mục Tài liệu Nghiên cứu Khảo sát (Required Sources Reviewed)

Quá trình thực hiện các chứng minh và thiết kế kiểm thử trong C01.2-R2 dựa trên sự khảo sát trực tiếp các nguồn tài liệu khoa học và mã nguồn chuẩn tắc:

### 2.1. Nguồn nghiên cứu xuất bản (Authoritative Published Sources)
1. **[Pham2023]** Thi-Thiet Pham, Thuy-Duong Vu, Tai-Du Nguyen, Bao Huynh, Trang Van. *“Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding”*. Vietnam Journal of Computer Science, Vol. 10, No. 3 (2023), pp. 357–372.
   - *Phần khảo sát:* Trang 362–366 (Mục 3, 4; Bảng 1, Bảng 2).
   - *Giá trị đối chiếu:* Định nghĩa chuỗi con, quan hệ nhúng, độ hỗ trợ chuỗi theo distinct SID, công thức $w(i)$, hằng số toàn cục $MeanW$, điều kiện chấp nhận mẫu và cấu trúc duyệt cây tiền tố.
   - *Phân loại:* `SOURCE-CONFIRMED`.
2. **[BitPrune2026]** Nguyễn Phúc Khang, Đặng Duy Minh, Phạm Thị Lan Thanh, Nguyễn Thị Thu Tâm. *“Tối ưu hóa thuật toán khai phá mẫu tuần tự có trọng số bằng cấu trúc Bitmap và cơ chế cắt tỉa trễ đa tầng”*. Bản thảo Hội thảo Quốc gia, 2026.
   - *Phần khảo sát:* Mục 3.1–3.4 (Định nghĩa 1–9, Bổ đề 1, Bổ đề 2) và Mục 4.2–4.5 (kiến trúc lọc 3 tầng).
   - *Giá trị đối chiếu:* Các mệnh đề đề xuất về mảng bit $BM$, phép AND bitmask, cận trên cardinality, ước lượng cận trên WSUP, và ma trận CMAP gộp.
   - *Phân loại:* `PROPOSED ALGORITHM DESIGN (UNVERIFIED IN PAPER)`.
3. **[Proposal2026]** Đề cương chi tiết khóa luận tốt nghiệp CNTT-KLCN256.

### 2.2. Tài liệu chuẩn tắc và semantic contract đã nghiệm thu
4. **[C01_1_CONTRACT]** `docs/research/wspm-bitprune-c01-1-semantic-contract.md` (Bản nghiệm thu C01.1-R3).
   - *Giá trị đối chiếu:* Hợp đồng ngữ nghĩa bất biến về cấu trúc pattern, SID mapping, I/S-Extension, hệ đơn vị ngưỡng, phân tách support-only membership vs positional state, và ma trận bàn giao OBL-01 đến OBL-08.
   - *Phân loại:* `ACCEPTED RESEARCH SPECIFICATION`.
5. **[PHASE_C]** `PHASE_C_WSPM_BitPrune.md` (Mục 5, 6, 7).
6. **[AGENTS]** `AGENTS.md` (Quy chuẩn kỹ thuật và nguyên tắc nghiên cứu).

### 2.3. Mã nguồn và chính sách domain model của repository (Implementation-Confirmed)
7. **[DOM_SEQ]** `src/main/java/vn/edu/wspm/model/Sequence.java` (ràng buộc chuỗi không rỗng chứa ít nhất 1 itemset; ném `IllegalArgumentException`).
   - *Phân loại:* `IMPLEMENTATION-CONFIRMED / DOMAIN POLICY`.
8. **[DOM_REC]** `src/main/java/vn/edu/wspm/model/SequenceRecord.java` (SID kiểu nguyên thủy `int`).
   - *Phân loại:* `IMPLEMENTATION-CONFIRMED / DOMAIN MODEL`.
9. **[DOM_DB]** `src/main/java/vn/edu/wspm/model/SequenceDatabase.java` (tính duy nhất của SID, chấp nhận SID noncontiguous).
   - *Phân loại:* `IMPLEMENTATION-CONFIRMED / DOMAIN MODEL`.
10. **[IO_PARSER]** `src/main/java/vn/edu/wspm/io/SpmfSequenceParser.java` (từ chối chuỗi rỗng tại biên parser: `-2` ném `empty sequence`, `-1` đầu dòng ném `empty itemset`).
    - *Phân loại:* `IMPLEMENTATION-CONFIRMED / PARSER POLICY`.
11. **[CFG_RES]** `src/main/java/vn/edu/wspm/config/ResolvedMinimumSupport.java` và `MinimumSupportResolver.java` (xử lý ngưỡng phần trăm vs tuyệt đối, phép so sánh `meetsThreshold`).
    - *Phân loại:* `IMPLEMENTATION-CONFIRMED / DOMAIN MODEL`.
12. **[DOM_WGT]** `src/main/java/vn/edu/wspm/statistics/ItemWeightCalculator.java` (hành vi `empty()` trên CSDL rỗng).
    - *Phân loại:* `IMPLEMENTATION-CONFIRMED / DOMAIN POLICY`.
13. **[ORACLE_BRUTE]** `src/test/java/vn/edu/wspm/mining/BruteForceWeightedPatternOracle.java` (chuẩn đối sánh độ hỗ trợ chính xác theo distinct SID).
    - *Phân loại:* `TEST EVIDENCE / ORACLE`.

---

## 3. Các Định nghĩa Tiếp nhận Nguyên vẹn từ C01.1 (Definitions Imported Unchanged)

Tất cả các định nghĩa dưới đây được nhập nguyên văn từ Semantic Contract C01.1 đã được nghiệm thu và giữ vai trò là nền tảng tiên đề bất biến cho mọi chứng minh của C01.2:

- **Định nghĩa 1 (Item và Itemset):**
  $I = \{i_1, \dots, i_m\}$ là tập hợp các item được định danh bằng số nguyên dương phân biệt với thứ tự tự nhiên $i_1 < \dots < i_m$. Một itemset $s \subseteq I$ là một tập con không rỗng của $I$, biểu diễn dưới dạng canonical $(x_1 x_2 \dots x_p)$ với $x_1 < x_2 < \dots < x_p$. Các phần tử trong itemset xảy ra đồng thời.
- **Định nghĩa 2 (Sequence và Database):**
  Một sequence $S = \langle s_1, s_2, \dots, s_n \rangle$ ($n \ge 1$) là một danh sách có thứ tự các itemset. Theo domain model (`Sequence.java:16`), chuỗi rỗng ($n = 0$) bị từ chối tại ranh giới domain model/parser. Kích thước chuỗi $size(S) = n$, độ dài $L(S) = \sum_{j=1}^n |s_j|$.
  Một cơ sở dữ liệu chuỗi $SD$ là tập hợp các bản ghi $\langle sid, S \rangle$ với $sid \in \text{Universe}(SD) \subset \mathbb{Z}$ là các số nguyên duy nhất phân biệt (có thể không liên tục, không nhất thiết dương). Kích thước CSDL là $|SD|$.
- **Định nghĩa 3 (Khác biệt cấu trúc):**
  $\langle(a)(b)\rangle \ne \langle(ab)\rangle$. Pattern là danh sách có thứ tự của các itemset; không được làm phẳng thành tập item không thứ tự.
- **Định nghĩa 4 (Subsequence Embedding):**
  Pattern $P = \langle p_1, \dots, p_k \rangle$ là chuỗi con của $S = \langle s_1, \dots, s_m \rangle$ ($P \sqsubseteq S$) khi và chỉ khi tồn tại dãy chỉ số nhúng $\mathbf{j} = (j_1, \dots, j_k)$ thỏa mãn $1 \le j_1 < \dots < j_k \le m$ và $\forall t \in [1, k]: p_t \subseteq s_{j_t}$. Chỉ số $j_k$ là điểm mốc kết thúc (endpoint) của nhúng.
- **Định nghĩa 5 (Exact Support theo Distinct Supporting SIDs):**
  $$\text{SupportingSIDs}(P) = \{ sid \in \text{Universe}(SD) \mid P \sqsubseteq S_{sid} \}$$
  $$support(P) = |\text{SupportingSIDs}(P)|$$
  Mỗi $sid$ chỉ đóng góp tối đa 1 đơn vị support, bất kể số lượng nhúng hợp lệ trong chuỗi đó.
- **Định nghĩa 6 (I-Extension và S-Extension):**
  - **I-Extension ($P \oplus_I x$):** Thêm item $x$ vào itemset cuối $p_k$: $P' = \langle p_1, \dots, p_{k-1}, p_k \cup \{x\} \rangle$. Ràng buộc canonical: $x > \text{lastItem}(p_k)$. Yêu cầu nhúng: $p_k \cup \{x\} \subseteq s_{sid, j_k}$ tại cùng vị trí endpoint $j_k$.
  - **S-Extension ($P \oplus_S x$):** Thêm singleton itemset $(x)$ vào cuối chuỗi: $P' = \langle p_1, \dots, p_k, (x) \rangle$. Cho phép item lặp lại ($x \in P$). Yêu cầu nhúng: $x \in s_{sid, j_{k+1}}$ với $j_k < j_{k+1} \le size(S_{sid})$.
- **Định nghĩa 7 (Item Weight, MeanW và WSUP):**
  Khi $totalLength(SD) > 0$:
  $$w(i) = \frac{T(i)}{totalLength(SD)}, \quad \text{MeanW} = \frac{\max_{i, T(i)>0} w(i) + \min_{i, T(i)>0} w(i)}{2}$$
  $$WSUP(P) = support(P) \times \text{MeanW}$$
  $\text{MeanW}$ là hằng số toàn cục không âm duy nhất của CSDL. Khi $|SD| = 0$, $\text{MeanW}$ không tồn tại (`ItemWeightResult.empty()`).
- **Định nghĩa 8 (Hệ đơn vị Ngưỡng và Điều kiện Chấp nhận Mẫu):**
  - Ngưỡng hiệu dụng nội bộ: $effectiveMinSup = \theta_{rel} \times |SD|$ (với chế độ `PERCENT`, $|SD| > 0$) hoặc $effectiveMinSup = A$ (chế độ `ABSOLUTE`, $A \ge 0$).
  - Điều kiện chấp nhận mẫu: $WSUP(P) \ge effectiveMinSup \iff support(P) \times \text{MeanW} \ge effectiveMinSup$.
- **Định nghĩa 9 (Ánh xạ Song ánh SID và SID Candidate Bitmap):**
  - $\text{sidToIndex} : \text{Universe}(SD) \to \{0, 1, \dots, |SD|-1\}$ và $\text{indexToSid}$ là cặp ánh xạ song ánh tất định.
  - Mảng bit đơn $BM(i) \in \{0, 1\}^{|SD|}$ với $BM(i)[\text{sidToIndex}(sid)] = 1 \iff$ chuỗi $S_{sid}$ chứa item $i$.
  - Mảng bit ứng viên $BM(P) = \bigwedge_{u \in Items(P)} BM(u)$. $cardinality(BM(P))$ là số lượng bit 1 trong $BM(P)$.
- **Định nghĩa 10 (CMAP-I và CMAP-S):**
  - $\text{CMAP-I}(a, b) = true \iff \exists sid \in \text{Universe}(SD), \exists t \in [1, size(S_{sid})]: \{a, b\} \subseteq s_{sid, t}$ (với canonical order $a < b$).
  - $\text{CMAP-S}(a, b) = true \iff \exists sid \in \text{Universe}(SD), \exists 1 \le t < u \le size(S_{sid}): a \in s_{sid, t} \land b \in s_{sid, u}$ (kể cả $a = b$).

---

## 4. Sổ Đăng Ký Giả Thiết Nghiên Cứu (Assumption Registry)

Mỗi chứng minh hoặc phân tích an toàn trong tài liệu này chỉ có giá trị toán học khi các giả thiết tương ứng được thỏa mãn đầy đủ:

| Mã Giả Thiết | Nội Dung Chi Tiết | Nguồn Gốc / Ranh Giới Kiểm Tra | Trạng Thái Bắt Buộc |
| :--- | :--- | :--- | :--- |
| **ASM-01** | CSDL chuỗi $SD$ là hợp lệ, gồm các bản ghi phân biệt có $|SD| > 0$. | [DOM_DB], [DOM_REC] | Bắt buộc cho tính toán MeanW và WSUP end-to-end. |
| **ASM-02** | Độ hỗ trợ được định nghĩa chính xác theo số lượng distinct actual SIDs: $support(P) = \|\text{SupportingSIDs}(P)\|$. | [Pham2023], Mục 7 C01.1 | Bắt buộc cho toàn bộ OBL-01 đến OBL-08. |
| **ASM-03** | Mọi chuỗi trong CSDL tuân thủ invariant của domain model: chứa ít nhất 1 itemset ($size(S) \ge 1$). | [DOM_SEQ:16], [IO_PARSER:93] | Bắt buộc; chuỗi rỗng bị loại ở biên parser. |
| **ASM-04** | $MeanW$ là một hằng số thực toàn cục duy nhất, hữu hạn và không âm: $\text{MeanW} \in [0, +\infty)$. | [Pham2023], Mục 10 C01.1 | Bắt buộc cho OBL-02 và OBL-04. |
| **ASM-05** | Hai vế của điều kiện so sánh ngưỡng sử dụng cùng hệ đơn vị hiệu dụng ($WSUP$ và $effectiveMinSup$). | Mục 10 C01.1, [CFG_RES] | Bắt buộc cho OBL-02, OBL-04, OBL-08. |
| **ASM-06** | Ánh xạ `sidToIndex` và `indexToSid` là song ánh toán học hoàn hảo trên tập $\text{Universe}(SD)$. | Mục 4 & 12 C01.1 | Bắt buộc cho OBL-03 và OBL-04. |
| **ASM-07** | Mảng bit $BM(P)$ có các bit ngoài phạm vi $[0, \|SD\|-1]$ (nếu có do căn lề 64-bit) luôn bằng 0. | Mục 8 C01.2 | Bắt buộc để $cardinality(BM)$ chuẩn xác. |
| **ASM-08** | Ngưỡng hiệu dụng là số thực dương nghiêm ngặt: $effectiveMinSup > 0$. | Mục 10 C01.1, Mục 13 C01.2 | Bắt buộc để phê duyệt các quy tắc operational pruning; tại $=0$ bị chặn bởi R6. |
| **ASM-09** | Phép so sánh cắt tỉa cận trên là bất đẳng thức nghiêm ngặt ($<$). | Mục 6.2 PHASE_C, Mục 9 C01.2 | Bắt buộc; tuyệt đối không cắt tỉa tại đẳng thức ($=$). |
| **ASM-10** | Thứ tự duyệt I-Extension tuân thủ nghiêm ngặt canonical item ordering $x > \text{lastItem}(p_k)$. | Mục 8 C01.1, [TEST_EXT] | Bắt buộc để tránh sinh trùng lặp không gian tìm kiếm. |

---

## 5. Chứng Minh Nghĩa Vụ OBL-01 — Support Anti-Monotonicity

### 5.1. Phát biểu Định lý (Theorem Statement)
Cho cơ sở dữ liệu chuỗi $SD$ thỏa mãn ASM-01, ASM-02 và ASM-03. Cho pattern $P$ và $Q$. Nếu $Q$ là một mở rộng hợp lệ trực tiếp (I-Extension hoặc S-Extension) của $P$ ($P \sqsubseteq Q$), thì:
$$\text{SupportingSIDs}(Q) \subseteq \text{SupportingSIDs}(P)$$
và do đó:
$$support(Q) \le support(P)$$

Hệ quả quy nạp: Nếu $Q^*$ là một hậu duệ tùy ý của $P$ trong không gian tìm kiếm cây tiền tố (tạo thành từ $m \ge 1$ bước mở rộng I/S liên tiếp), thì:
$$\text{SupportingSIDs}(Q^*) \subseteq \text{SupportingSIDs}(P) \implies support(Q^*) \le support(P)$$

### 5.2. Các Định nghĩa Sử dụng
- Định nghĩa 4 (Subsequence Embedding).
- Định nghĩa 5 (Exact Support theo Distinct Supporting SIDs).
- Định nghĩa 6 (I-Extension và S-Extension).

### 5.3. Các Bước Chứng Minh Hình thức (Formal Proof Steps)
Lấy một mã định danh bất kỳ $sid \in \text{SupportingSIDs}(Q)$.
Theo Định nghĩa 5, điều này tương đương với $Q \sqsubseteq S_{sid}$.
Theo Định nghĩa 4, tồn tại ít nhất một nhúng hợp lệ $\mathbf{j}' = (j'_1, j'_2, \dots, j'_r)$ của $Q = \langle q_1, q_2, \dots, q_r \rangle$ trong chuỗi $S_{sid} = \langle s_1, s_2, \dots, s_m \rangle$ thỏa mãn:
1. $1 \le j'_1 < j'_2 < \dots < j'_r \le m = size(S_{sid})$;
2. $\forall t \in [1, r]: q_t \subseteq s_{j'_t}$.

Ta xét hai trường hợp mở rộng cấu trúc:

#### Trường hợp 1: $Q$ là I-Extension của $P$ ($Q = P \oplus_I x$)
- Theo Định nghĩa 6, $size(Q) = size(P) = k$, do đó $r = k$.
- Với mọi $t \in [1, k-1]$, ta có $p_t = q_t$. Vì $q_t \subseteq s_{j'_t}$, suy ra $p_t \subseteq s_{j'_t}$.
- Tại vị trí cuối cùng $t = k$, ta có $q_k = p_k \cup \{x\}$. Vì $q_k \subseteq s_{j'_k}$ và $p_k \subseteq p_k \cup \{x\}$, theo tính chất bắc cầu của quan hệ bao hàm tập hợp, ta có $p_k \subseteq s_{j'_k}$.
- Chọn dãy chỉ số $\mathbf{j} = (j_1, j_2, \dots, j_k)$ với $j_t = j'_t$ cho mọi $t \in [1, k]$.
- Dãy $\mathbf{j}$ thỏa mãn đồng thời:
  1. $1 \le j_1 < j_2 < \dots < j_k \le m$ (kế thừa trực tiếp từ tính tăng nghiêm ngặt của $\mathbf{j}'$);
  2. $\forall t \in [1, k]: p_t \subseteq s_{j_t}$.
- Do đó, $\mathbf{j}$ là một nhúng hợp lệ của $P$ trong $S_{sid}$.
- Suy ra $P \sqsubseteq S_{sid}$, tức là $sid \in \text{SupportingSIDs}(P)$.

#### Trường hợp 2: $Q$ là S-Extension của $P$ ($Q = P \oplus_S x$)
- Theo Định nghĩa 6, $size(Q) = size(P) + 1 = k + 1$, do đó $r = k + 1$.
- Cấu trúc của $Q$ là: $q_t = p_t$ với mọi $t \in [1, k]$, và $q_{k+1} = (x)$.
- Xét dãy con gồm $k$ chỉ số đầu tiên của $\mathbf{j}'$: $\mathbf{j} = (j'_1, j'_2, \dots, j'_k)$.
- Vì $\mathbf{j}'$ tăng nghiêm ngặt: $1 \le j'_1 < j'_2 < \dots < j'_k < j'_{k+1} \le m$, ta có ngay:
  1. $1 \le j'_1 < j'_2 < \dots < j'_k \le m$;
  2. $\forall t \in [1, k]: p_t = q_t \subseteq s_{j'_t}$.
- Do đó, dãy $\mathbf{j} = (j'_1, \dots, j'_k)$ là một nhúng hợp lệ của $P$ trong chuỗi $S_{sid}$.
- Suy ra $P \sqsubseteq S_{sid}$, tức là $sid \in \text{SupportingSIDs}(P)$.

#### Tổng hợp quan hệ tập hợp và độ hỗ trợ:
Trong cả hai trường hợp mở rộng, mệnh đề sau luôn nghiệm đúng:
$$\forall sid \in \text{SupportingSIDs}(Q) \implies sid \in \text{SupportingSIDs}(P)$$
Điều này xác lập quan hệ tập con toán học:
$$\text{SupportingSIDs}(Q) \subseteq \text{SupportingSIDs}(P)$$
Theo tính đơn điệu của lực lượng tập hợp hữu hạn ($A \subseteq B \implies |A| \le |B|$):
$$support(Q) = |\text{SupportingSIDs}(Q)| \le |\text{SupportingSIDs}(P)| = support(P)$$

#### Mở rộng cho hậu duệ đa tầng (Bằng Quy nạp):
Giả sử mệnh đề đúng cho mọi hậu duệ sau $h$ bước mở rộng. Xét hậu duệ $Q^{(h+1)}$ mở rộng trực tiếp từ $Q^{(h)}$. Áp dụng chứng minh trên, ta có $\text{SupportingSIDs}(Q^{(h+1)}) \subseteq \text{SupportingSIDs}(Q^{(h)})$. Theo giả thiết quy nạp, $\text{SupportingSIDs}(Q^{(h)}) \subseteq \text{SupportingSIDs}(P)$. Theo tính chất bắc cầu của quan hệ tập con, $\text{SupportingSIDs}(Q^{(h+1)}) \subseteq \text{SupportingSIDs}(P)$. Suy ra $support(Q^{(h+1)}) \le support(P)$ với mọi $h \ge 1$. $\blacksquare$

### 5.4. Hệ quả đối với Cắt Tỉa (Pruning Consequences)
- **Candidate Pruning:** Không áp dụng trực tiếp (đây là tính chất phản đơn điệu của không gian tìm kiếm, không phải bộ lọc từ chối một ứng viên cụ thể).
- **Subtree Pruning:** Nếu bản thân mẫu $P$ có $support(P) = 0$, thì với mọi hậu duệ $Q^*$ của $P$, ta có $support(Q^*) \le support(P) = 0 \implies support(Q^*) = 0$. Khi ngưỡng hiệu dụng $effectiveMinSup > 0$ và $\text{MeanW} \ge 0$, ta có $WSUP(Q^*) = 0 \times \text{MeanW} = 0 < effectiveMinSup$. Do đó, việc loại bỏ toàn bộ cây con xuất phát từ một nút có $support = 0$ là an toàn khi ngưỡng dương. (Lưu ý: Không dùng biến số $k_{min}$ chưa định nghĩa; trường hợp $support(P) > 0$ nhưng $WSUP(P) < effectiveMinSup$ thuộc về quy tắc R-05).

### 5.5. Phân tích Điều kiện Biên và Ca Đặc thù
1. **Đa nhúng trong cùng một chuỗi (Multiple Embeddings):** Giả sử chuỗi $S_{sid}$ chứa 10 nhúng hợp lệ của $P$, nhưng chỉ chứa đúng 1 nhúng của $Q$. Vì Định nghĩa 5 quy định $support$ đếm theo số lượng $sid$ phân biệt (không đếm số lượng nhúng), chuỗi $sid$ chỉ đóng góp đúng 1 vào $support(P)$ và đúng 1 vào $support(Q)$. Quan hệ bao hàm tập hợp $\text{SupportingSIDs}(Q) \subseteq \text{SupportingSIDs}(P)$ vẫn giữ nguyên tính chính xác tuyệt đối.
2. **Item lặp lại (Repeated Items):** Xét $P = \langle(a)\rangle$ và $Q = \langle(a)(a)\rangle$. Mọi chuỗi chứa ít nhất hai lần xuất hiện của $a$ tại hai itemset khác nhau hiển nhiên phải chứa ít nhất một lần xuất hiện của $a$. Do đó $\text{SupportingSIDs}(\langle(a)(a)\rangle) \subseteq \text{SupportingSIDs}(\langle(a)\rangle)$. Bất đẳng thức $support(\langle(a)(a)\rangle) \le support(\langle(a)\rangle)$ luôn đúng.
3. **Mã SID không liên tục (Noncontiguous SIDs):** Bản chứng minh hoàn toàn hoạt động trên lý thuyết tập hợp tổng quát, độc lập với giá trị đại số hay tính liên tục của $sid$.

### 5.6. Trạng thái Nghiên cứu của OBL-01
- **Phân loại:** `PROVED IN C01.2`.
- **Mức độ chắc chắn:** Chứng minh toán học hoàn tất; không còn khoảng trống lý thuyết.

---

## 6. Chứng Minh Nghĩa Vụ OBL-02 — Global-MeanW WSUP Anti-Monotonicity

### 6.1. Phát biểu Định lý (Theorem Statement)
Cho cơ sở dữ liệu chuỗi $SD$ thỏa mãn ASM-01 đến ASM-05. Định nghĩa điểm số hỗ trợ có trọng số:
$$WSUP(P) = support(P) \times \text{MeanW}$$
Nếu $Q$ là một mở rộng hợp lệ trực tiếp hoặc gián tiếp của $P$ ($P \sqsubseteq Q$), thì:
$$WSUP(Q) \le WSUP(P)$$
Hệ quả: Với ngưỡng hiệu dụng $effectiveMinSup > 0$ (ASM-08), nếu $WSUP(P) < effectiveMinSup$, thì với mọi hậu duệ $Q$ của $P$, ta luôn có:
$$WSUP(Q) < effectiveMinSup$$

### 6.2. Các Bước Chứng Minh Hình thức
1. Theo kết quả đã chứng minh tại OBL-01 (Mục 5), ta có:
   $$support(Q) \le support(P)$$
2. Theo giả thiết ASM-04, $MeanW$ là một đại lượng hằng số toàn cục, độc lập với cấu trúc hay độ dài của pattern $P$ và $Q$, đồng thời thỏa mãn điều kiện không âm: $\text{MeanW} \ge 0$.
3. Trong đại số số thực, khi nhân cả hai vế của một bất đẳng thức với một số thực không âm $c \ge 0$, chiều của bất đẳng thức được bảo toàn:
   $$\forall x, y \in \mathbb{R}, c \in \mathbb{R}_{\ge 0}: x \le y \implies x \cdot c \le y \cdot c$$
4. Áp dụng tiên đề trên với $x = support(Q)$, $y = support(P)$ và $c = \text{MeanW}$:
   $$support(Q) \times \text{MeanW} \le support(P) \times \text{MeanW}$$
5. Theo Định nghĩa 7 về điểm số $WSUP$, bất đẳng thức trên tương đương chính xác với:
   $$WSUP(Q) \le WSUP(P)$$
6. Giả sử $WSUP(P) < effectiveMinSup$. Theo tính chất bắc cầu của quan hệ thứ tự nghiêm ngặt trên tập số thực $\mathbb{R}$ ($a \le b \land b < c \implies a < c$):
   $$WSUP(Q) \le WSUP(P) < effectiveMinSup \implies WSUP(Q) < effectiveMinSup$$
   $\blacksquare$

### 6.3. Phân tích Riêng Biệt các Trường Hợp Trọng số và Ngưỡng
1. **Trường hợp $\text{MeanW} > 0$:** Phép nhân bảo toàn nghiêm ngặt quan hệ thứ tự. Nếu $support(Q) < support(P)$, ta có $WSUP(Q) < WSUP(P)$.
2. **Trường hợp đại số lý thuyết $\text{MeanW} = 0$ (Abstract Algebraic Boundary):**
   - *Lưu ý quan trọng về Domain Model:* Trên một CSDL chuỗi hợp lệ không rỗng hiện tại ($|SD| > 0$, mỗi chuỗi chứa ít nhất một itemset không rỗng), mọi item xuất hiện đều có $T(i) \ge 1$ và $totalLength(SD) \ge 1 \implies w(i) > 0$. Do đó, bộ tính toán trọng số `ItemWeightCalculator` của baseline **không thể sinh ra giá trị $\text{MeanW} = 0$** trên dữ liệu hợp lệ.
   - *Phân tích đại số:* Về mặt đại số thuần túy trên số thực, nếu xét một tham số nhân tạo $\text{MeanW} = 0.0$, ta có $WSUP(P) = support(P) \times 0.0 = 0.0$ và $WSUP(Q) = support(Q) \times 0.0 = 0.0$. Bất đẳng thức $0 \le 0$ vẫn nghiệm đúng. Đây là phân tích biên đại số, không phải hành vi CSDL end-to-end có thể đạt được trong pipeline hiện tại.
3. **Trường hợp $\text{MeanW} < 0$ (Nằm ngoài Domain):** Nếu gán trọng số âm ($MeanW < 0$), phép nhân với số âm sẽ **đổi chiều bất đẳng thức**: $support(Q) \le support(P) \implies support(Q) \times MeanW \ge support(P) \times MeanW$, làm sụp đổ hoàn toàn tính phản đơn điệu! Tuy nhiên, vì $T(i) \ge 0$ và $totalLength(SD) > 0$, ta có $w(i) \ge 0 \implies MaxW \ge 0, MinW \ge 0 \implies MeanW \ge 0$. Do đó, trọng số âm không thể xảy ra trong domain hợp lệ.
4. **Trường hợp $\text{MeanW}$ là NaN hoặc vô hạn ($\pm\infty$):** Nằm ngoài domain toán học; bị chặn bởi ASM-04.
5. **CSDL rỗng ($|SD| = 0$):** `ItemWeightCalculator` trả về `empty()`, $MeanW$ không tồn tại. Do đó $WSUP$ không xác định trong pipeline khai phá chuẩn (không tự bịa đặt giá trị 0).
6. **Ngưỡng hiệu dụng có phần thập phân:** Bất đẳng thức trên số thực $\mathbb{R}$ hoàn toàn nghiệm đúng cho mọi giá trị thực của $effectiveMinSup$.
7. **Cảnh báo Tuyệt đối về Trọng số phụ thuộc Pattern (Pattern-dependent Weights):**
   Khẳng định nghiên cứu: **Bản chứng minh này CHỈ NGHIỆM ĐÚNG dưới chính sách trọng số toàn cục $\text{MeanW}$ của WSPM_PreTree.** Nó **HOÀN TOÀN KHÔNG ÁP DỤNG** cho bài toán WSPM truyền thống sử dụng trọng số phụ thuộc chuỗi $W(s)$ (như công thức Eq. (3) trong bài báo [Pham2023] hay Utility Mining), nơi một chuỗi mở rộng có thể có trọng số lớn hơn chuỗi tiền tố dẫn đến $WSUP(Q) > WSUP(P)$ (xem Phản ví dụ CE-06 tại Mục 14).

### 6.4. Hệ quả đối với Cắt Tỉa (Pruning Consequences)
- **Candidate Pruning:** Nếu $WSUP(P) < effectiveMinSup$, ứng viên $P$ không thỏa mãn điều kiện chấp nhận mẫu ($WSUP(P) \ge effectiveMinSup$). Loại bỏ $P$ là an toàn khi ngưỡng dương.
- **Subtree Pruning:** Nếu $WSUP(P) < effectiveMinSup$ và $effectiveMinSup > 0$, mọi hậu duệ $Q$ đều có $WSUP(Q) < effectiveMinSup$. Do đó, **cắt tỉa toàn bộ nhánh con (subtree pruning) xuất phát từ $P$ trong không gian DFS là HOÀN TOÀN AN TOÀN**, không gây bất kỳ sai sót loại bỏ nhầm nào (No False Negatives).

### 6.5. Trạng thái Nghiên cứu của OBL-02
- **Phân loại:** `PROVED IN C01.2`.
- **Mức độ chắc chắn:** Chứng minh toán học hoàn tất dưới các giả thiết ASM-01 đến ASM-05.

---

## 7. Chứng Minh Nghĩa Vụ OBL-03 — SID Bitmap Cardinality Upper Bound

### 7.1. Phát biểu Bổ đề (Lemma Statement)
Cho cơ sở dữ liệu chuỗi $SD$ thỏa mãn ASM-01, ASM-03, ASM-06 và ASM-07. Cho pattern ứng viên $P$ có tập hợp các item cấu thành là $Items(P) = \{u_1, u_2, \dots, u_r\} \subseteq I$. Mảng bit ứng viên $BM(P)$ được xây dựng bởi phép hội logic từng bit:
$$BM(P) = \bigwedge_{u \in Items(P)} BM(u)$$
Khi đó:
$$\text{SupportingSIDs}(P) \subseteq \{ \text{indexToSid}(c) \mid c \in [0, |SD|-1] \land BM(P)[c] = 1 \}$$
và do đó:
$$support(P) \le cardinality(BM(P))$$

### 7.2. Các Bước Chứng Minh Hình thức
1. Lấy một mã định danh chuỗi bất kỳ $sid \in \text{SupportingSIDs}(P)$.
2. Theo Định nghĩa 5, $P \sqsubseteq S_{sid}$.
3. Theo Định nghĩa 4 về Subsequence Embedding, tồn tại dãy chỉ số nhúng $\mathbf{j} = (j_1, \dots, j_k)$ trong chuỗi $S_{sid}$ sao cho với mọi itemset $p_t$ của $P$, ta có $p_t \subseteq s_{sid, j_t}$ ($t \in [1, k]$).
4. Xét một item bất kỳ $u \in Items(P)$. Theo định nghĩa của tập hợp item trong pattern:
   $$Items(P) = \bigcup_{t=1}^k p_t$$
   Do đó, tồn tại ít nhất một chỉ số itemset $t^* \in [1, k]$ sao cho $u \in p_{t^*}$.
5. Vì $p_{t^*} \subseteq s_{sid, j_{t^*}}$, theo quan hệ tập con, ta có:
   $$u \in s_{sid, j_{t^*}}$$
   Điều này chứng minh rằng chuỗi $S_{sid}$ bắt buộc phải chứa item $u$ tại itemset ở vị trí $j_{t^*}$.
6. Theo Định nghĩa 9 về mảng bit đơn $BM(u)$ qua ánh xạ song ánh $\text{sidToIndex}$:
   $$BM(u)[\text{sidToIndex}(sid)] = 1$$
7. Vì kết luận tại bước 6 đúng với **mọi** item $u \in Items(P)$, nên theo định nghĩa của phép hội logic (Bitwise AND):
   $$BM(P)[\text{sidToIndex}(sid)] = \prod_{u \in Items(P)} BM(u)[\text{sidToIndex}(sid)] = \prod_{u \in Items(P)} 1 = 1$$
8. Đặt tọa độ bit $c = \text{sidToIndex}(sid)$. Vì $sid \in \text{Universe}(SD)$, theo ASM-06, $c \in \{0, 1, \dots, |SD|-1\}$. Kết hợp với bước 7, ta có:
   $$c \in \{ c' \in [0, |SD|-1] \mid BM(P)[c'] = 1 \}$$
9. Áp dụng ánh xạ ngược $\text{indexToSid}$ lên cả hai vế. Vì $\text{indexToSid}(\text{sidToIndex}(sid)) = sid$, ta có:
   $$sid \in \{ \text{indexToSid}(c') \mid c' \in [0, |SD|-1] \land BM(P)[c'] = 1 \}$$
10. Vì mệnh đề trên đúng với mọi $sid \in \text{SupportingSIDs}(P)$, ta xác lập quan hệ tập con toán học:
    $$\text{SupportingSIDs}(P) \subseteq \{ \text{indexToSid}(c') \mid c' \in [0, |SD|-1] \land BM(P)[c'] = 1 \}$$
11. Lấy lực lượng (cardinality) của hai tập hợp. Vì ánh xạ $\text{indexToSid}$ là song ánh, số lượng phần tử của tập hợp đích bằng đúng số lượng bit 1 trong $BM(P)$ trên phạm vi $[0, |SD|-1]$, ký hiệu là $cardinality(BM(P))$. Do đó:
    $$support(P) = |\text{SupportingSIDs}(P)| \le cardinality(BM(P))$$
    $\blacksquare$

### 7.3. Phân tích Ranh giới Kỹ thuật và Nguy cơ Cận Lỏng
1. **Mã SID âm hoặc không liên tục (Negative / Noncontiguous SIDs):** Bản chứng minh phụ thuộc vào tính song ánh của $\text{sidToIndex}$, hoàn toàn không phụ thuộc vào giá trị số nguyên của $sid$. Dù $sid = -100$ hay $sid = 9999$, nó đều được ánh xạ 1-1 vào tọa độ bit compact $c \in [0, |SD|-1]$.
2. **Xuất hiện nhiều lần trong cùng một chuỗi:** Nếu item $u$ xuất hiện 10 lần trong chuỗi $S_{sid}$, bit tại vị trí $\text{sidToIndex}(sid)$ vẫn chỉ được bật đúng 1 lần (giá trị 1). Do đó $cardinality(BM(u))$ không bị thổi phồng bởi tần suất xuất hiện nội tại của chuỗi.
3. **Item lặp lại trong pattern (Repeated Items):** Xét pattern $\langle(a)(a)\rangle$. Tập $Items(\langle(a)(a)\rangle) = \{a\}$. Khi đó:
   $$BM(\langle(a)(a)\rangle) = BM(a) \implies cardinality(BM(\langle(a)(a)\rangle)) = cardinality(BM(a))$$
   Nếu một chuỗi $S_{sid}$ chỉ chứa đúng 1 lần item $a$, bit của chuỗi này trong $BM(a)$ vẫn bằng 1, mặc dù chuỗi đó không hỗ trợ $\langle(a)(a)\rangle$.
   *Hệ quả:* Cận trên $cardinality(BM(P))$ đối với pattern có item lặp lại sẽ **bị lỏng (loose bound)**. Tuy nhiên, nó **TUYỆT ĐỐI KHÔNG BAO GIỜ NHỎ HƠN** support thực tế ($support \le cardinality$ luôn đúng).
4. **Căn lề từ máy 64-bit (Word Alignment & Padding Bits):**
   Trong kiến trúc máy tính 64-bit, mảng bit thường được lưu trữ dưới dạng mảng `long[]`. Nếu $|SD|$ không chia hết cho 64, từ máy cuối cùng sẽ chứa $64 - (|SD| \pmod{64})$ bit đệm thừa.
   *Yêu cầu bắt buộc (ASM-07):* Tất cả các bit đệm ngoài phạm vi $[0, |SD|-1]$ **bắt buộc phải luôn được giữ bằng 0 (zero-padded)**, hoặc hàm đếm bit `cardinality()` chỉ được đếm trong phạm vi $|SD|$. Nếu để rác bit 1 lọt vào vùng đệm, $cardinality$ sẽ bị đếm thừa, làm lỏng cận nhưng không gây false negative; nhưng nếu hàm xử lý bitmask xóa nhầm bit trong khoảng $[0, |SD|-1]$ thì sẽ gây **False Negative nghiêm trọng**.

### 7.4. Trạng thái Nghiên cứu của OBL-03
- **Phân loại:** `PROVED IN C01.2`.
- **Mức độ chắc chắn:** Chứng minh toán học hoàn tất dưới giả thiết ASM-06 và ASM-07.

---

## 8. Chứng Minh Nghĩa Vụ OBL-04 — Weighted Upper-Bound Pruning

### 8.1. Phát biểu Định lý (Theorem Statement)
Cho pattern ứng viên $P$ và giả sử OBL-03 cùng các giả thiết ASM-01 đến ASM-09 được thỏa mãn. Định nghĩa ước lượng cận trên hỗ trợ có trọng số:
$$WSUP_{UB}(P) = cardinality(BM(P)) \times \text{MeanW}$$
Nếu:
$$WSUP_{UB}(P) < effectiveMinSup$$
thì:
$$WSUP(P) < effectiveMinSup$$
và ứng viên $P$ chắc chắn không thể thỏa mãn điều kiện chấp nhận mẫu ($WSUP(P) \ge effectiveMinSup$).

Hệ quả đối với Cây con (Subtree Pruning Theorem): Với mọi hậu duệ $Q$ của $P$ trong không gian tìm kiếm, ta luôn có:
$$WSUP(Q) \le WSUP_{UB}(Q) \le WSUP_{UB}(P) < effectiveMinSup$$
Do đó, toàn bộ cây con xuất phát từ $P$ có thể được cắt tỉa an toàn mà không cần thực hiện bước xác minh thời gian tốn kém.

### 8.2. Các Bước Chứng Minh Hình thức
1. Theo kết quả Bổ đề OBL-03 (Mục 7), ta có:
   $$support(P) \le cardinality(BM(P))$$
2. Theo giả thiết ASM-04, $\text{MeanW} \ge 0$. Nhân cả hai vế của bất đẳng thức trên với $\text{MeanW}$:
   $$support(P) \times \text{MeanW} \le cardinality(BM(P)) \times \text{MeanW}$$
3. Theo Định nghĩa 7 và định nghĩa của $WSUP_{UB}$, điều này tương đương với:
   $$WSUP(P) \le WSUP_{UB}(P)$$
4. Theo giả thiết của quy tắc cắt tỉa (ASM-09), $WSUP_{UB}(P) < effectiveMinSup$.
5. Theo tính chất bắc cầu của quan hệ thứ tự nghiêm ngặt trên tập số thực $\mathbb{R}$:
   $$WSUP(P) \le WSUP_{UB}(P) < effectiveMinSup \implies WSUP(P) < effectiveMinSup$$
6. Vì điều kiện để một pattern được chấp nhận là $WSUP(P) \ge effectiveMinSup$, kết quả ở bước 5 khẳng định $P$ không thể thỏa mãn điều kiện này. Việc từ chối ứng viên $P$ là chính xác tuyệt đối.
7. Xét một hậu duệ tùy ý $Q$ của $P$. Vì $Items(P) \subseteq Items(Q)$, theo định nghĩa của phép AND bitmask:
   $$BM(Q) = BM(P) \wedge \bigwedge_{u \in Items(Q) \setminus Items(P)} BM(u)$$
   Phép hội logic chỉ có thể giữ nguyên hoặc làm giảm số lượng bit 1:
   $$cardinality(BM(Q)) \le cardinality(BM(P))$$
8. Nhân cả hai vế với $\text{MeanW} \ge 0$:
   $$WSUP_{UB}(Q) \le WSUP_{UB}(P)$$
9. Kết hợp với bước 3 và bước 4:
   $$WSUP(Q) \le WSUP_{UB}(Q) \le WSUP_{UB}(P) < effectiveMinSup \implies WSUP(Q) < effectiveMinSup$$
   Điều này chứng minh mọi hậu duệ $Q$ của $P$ đều không đạt ngưỡng. Việc cắt tỉa toàn bộ nhánh con là an toàn. $\blacksquare$

### 8.3. Phân tích Chi tiết Điểm Đẳng Thức ($=$) và Cấm Cắt Tỉa tại Đẳng Thức
- **Mệnh đề Cảnh báo:** Nếu $WSUP_{UB}(P) == effectiveMinSup$, **TUYỆT ĐỐI KHÔNG ĐƯỢC CẮT TỈA!**
- **Chứng minh nguy cơ False Negative:**
  Hoàn toàn có thể xảy ra trường hợp mọi chuỗi có bit 1 trong $BM(P)$ đều thỏa mãn đúng thứ tự thời gian của $P$. Khi đó:
  $$support(P) = cardinality(BM(P)) \implies WSUP(P) = WSUP_{UB}(P) = effectiveMinSup$$
  Theo điều kiện chấp nhận mẫu (Định nghĩa 8), điều kiện là $WSUP(P) \ge effectiveMinSup$. Vì $effectiveMinSup \ge effectiveMinSup$ là mệnh đề đúng, nên $P$ là một **mẫu tuần tự có trọng số hợp lệ**.
  Nếu một thuật toán sử dụng phép so sánh sai lầm $\le$ thay vì $<$ để cắt tỉa (tức cắt tỉa khi $WSUP_{UB} \le effectiveMinSup$), thì mẫu hợp lệ $P$ này sẽ bị loại bỏ $\implies$ **PHÁT SINH FALSE NEGATIVE NGHIÊM TRỌNG!** (Xem Phản ví dụ CE-05 tại Mục 14).

### 8.4. Phân tích Sai Số Dấu Phẩy Động (IEEE 754 Floating-Point Precision)
Trong môi trường máy tính Java sử dụng kiểu `double` 64-bit, các phép nhân và chia số thực có thể phát sinh sai số làm tròn (rounding error):
- *Tình huống rủi ro biên:* Giả sử giá trị toán học lý thuyết là $cardinality \times MeanW = 0.98$ và $effectiveMinSup = 0.98$. Do sai số biểu diễn nhị phân, biểu thức có thể bị tính thành $0.9799999999999999$. Khi đó phép so sánh `< 0.98` trong Java sẽ trả về `true`, dẫn đến việc cắt tỉa nhầm một mẫu thực tế đạt ngưỡng!
- *Ranh giới thẩm định của C01.2:*
  1. Trên tập số thực lý thuyết $\mathbb{R}$: Định lý OBL-04 được chứng minh an toàn tuyệt đối dưới bất đẳng thức nghiêm ngặt (`PROVED ON REALS`).
  2. Về chính sách triển khai trên Java `double`: C01.2 **không tự ý phê duyệt hay áp đặt một giá trị fixed epsilon** (như $10^{-9}$). Một giá trị fixed epsilon chưa được chứng minh là an toàn ở mọi thang đo độ lớn (scale-sensitive) và có thể gây sai lệch ngữ nghĩa so với baseline đã đóng băng.
  3. Trạng thái phân loại: `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`.
  4. Yêu cầu cho các giai đoạn tiếp theo: Chính sách so sánh dấu phẩy động trong mã nguồn phải được đặc tả độc lập, đối chiếu vi sai chặt chẽ với baseline WSPM_PreTree đã đóng băng, và kiểm thử nghiêm ngặt rủi ro False Negative trước khi triển khai.

### 8.5. Tác động của Ngưỡng Bằng Không ($effectiveMinSup = 0$) và Vấn đề R6
Nếu $effectiveMinSup = 0$:
- Bất đẳng thức cắt tỉa đòi hỏi $WSUP_{UB}(P) < 0$.
- Tuy nhiên, vì $cardinality(BM(P)) \ge 0$ và $\text{MeanW} \ge 0$, ta luôn có $WSUP_{UB}(P) \ge 0$.
- Do đó, điều kiện $WSUP_{UB}(P) < 0$ là **bất khả thi (không bao giờ xảy ra)**.
- Nếu $cardinality(BM(P)) == 0$, ta có $WSUP_{UB}(P) = 0 == effectiveMinSup$. Rơi vào điểm đẳng thức!
- Vì R6 đang `OPEN`, không thể áp dụng quy tắc cắt tỉa Bitmap Upper Bound khi ngưỡng bằng 0.

### 8.6. Trạng thái Nghiên cứu của OBL-04
- **Phân loại:** `PROVED IN C01.2 (TRÊN SỐ THỰC VỚI BẤT ĐẲNG THỨC NGHIÊM NGẶT)`.
- **Ranh giới:** Cấm cắt tỉa tại điểm đẳng thức; chính sách comparator cho Java `double` là `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`; không áp dụng tại $effectiveMinSup = 0$ do R6.

---

## 9. Chứng Minh Nghĩa Vụ OBL-05 — CMAP-I Necessary Condition

### 9.1. Phát biểu Định lý Toán học (Mathematical Theorem Statement)
Cho pattern $P = \langle p_1, \dots, p_k \rangle$ với $a = \text{lastItem}(p_k)$, và $b \in I$ thỏa mãn $a < b$ (theo thứ tự chuẩn tắc canonical của I-Extension).
Ma trận $\text{CMAP-I}$ được xây dựng đúng theo Định nghĩa 10.
Mệnh đề toán học:
$$\text{CMAP-I}(a, b) = false \implies support(P \oplus_I b) = 0$$
*Định lý toán học này là phổ quát và độc lập hoàn toàn với bất kỳ giá trị ngưỡng nào ($effectiveMinSup$).*

### 9.2. Các Bước Chứng Minh Hình thức
1. Giả sử phản chứng rằng $support(P \oplus_I b) > 0$.
2. Theo Định nghĩa 5, tồn tại ít nhất một mã chuỗi $sid \in \text{Universe}(SD)$ sao $sid \in \text{SupportingSIDs}(P \oplus_I b)$, nghĩa là:
   $$(P \oplus_I b) \sqsubseteq S_{sid}$$
3. Theo Định nghĩa 6 về mở rộng I-Extension, pattern mở rộng có cấu trúc:
   $$P' = P \oplus_I b = \langle p_1, p_2, \dots, p_{k-1}, p_k \cup \{b\} \rangle$$
4. Theo Định nghĩa 4 về Subsequence Embedding, để $P' \sqsubseteq S_{sid}$, bắt buộc phải tồn tại một nhúng hợp lệ $\mathbf{j} = (j_1, \dots, j_k)$ trong chuỗi $S_{sid} = \langle s_1, \dots, s_m \rangle$ thỏa mãn:
   $$\forall t \in [1, k-1]: p_t \subseteq s_{j_t} \quad \text{và} \quad p_k \cup \{b\} \subseteq s_{j_k}$$
5. Xét itemset tại vị trí endpoint $j_k$: ta có $p_k \cup \{b\} \subseteq s_{j_k}$.
6. Vì $a = \text{lastItem}(p_k)$, hiển nhiên $a \in p_k$, suy ra $a \in p_k \cup \{b\}$.
7. Đồng thời, $b \in p_k \cup \{b\}$.
8. Từ quan hệ tập con $p_k \cup \{b\} \subseteq s_{j_k}$, suy ra:
   $$a \in s_{j_k} \quad \text{và} \quad b \in s_{j_k} \iff \{a, b\} \subseteq s_{j_k}$$
9. Điều này chứng minh rằng: tồn tại một chuỗi $S_{sid}$ trong CSDL (với $sid \in \text{Universe}(SD)$) và tồn tại một vị trí itemset $t = j_k \in [1, size(S_{sid})]$ sao cho cả hai item $a$ và $b$ cùng xuất hiện đồng thời trong $s_{sid, t}$.
10. Theo đúng Định nghĩa 10 về ma trận $\text{CMAP-I}$:
    $$\text{CMAP-I}(a, b) = true$$
11. Kết quả ở bước 10 mâu thuẫn trực tiếp với giả thiết ban đầu là $\text{CMAP-I}(a, b) = false$.
12. Do đó, điều giả sử phản chứng là sai. Ta bắt buộc phải có:
    $$support(P \oplus_I b) = 0$$
    $\blacksquare$

### 9.3. Phân Tách Quyết Định Cắt Tỉa Thực Thi (Operational Pruning Consequences)
Cần phân biệt rõ giữa định lý toán học và hành vi cắt tỉa thực thi của thuật toán:
1. **Khi ngưỡng dương ($effectiveMinSup > 0$ và $\text{MeanW} \ge 0$):**
   - Ta có $WSUP(P \oplus_I b) = support(P \oplus_I b) \times \text{MeanW} = 0 \times \text{MeanW} = 0 < effectiveMinSup$.
   - Do đó, ứng viên $P' = P \oplus_I b$ bị từ chối an toàn (**Candidate Pruning**).
   - Theo OBL-01, mọi hậu duệ $Q$ của $P'$ đều có $support(Q) \le support(P') = 0 \implies WSUP(Q) = 0 < effectiveMinSup$. Do đó, việc cắt tỉa toàn bộ cây con của $P'$ (**Subtree Pruning của $P'$**) là hoàn toàn an toàn.
   - **Cảnh báo Subtree Pruning đối với nút cha $P$:** CÂY CON CỦA NÚT CHA $P$ TUYỆT ĐỐI KHÔNG ĐƯỢC CẮT TỈA, vì $P$ có thể có các nhánh mở rộng khác với item $c \ne b$ hoặc các mở rộng S-Extension hoàn toàn hợp lệ.
2. **Khi ngưỡng bằng không ($effectiveMinSup = 0$):**
   - Định lý toán học $support(P \oplus_I b) = 0$ **vẫn đúng 100%**.
   - Tuy nhiên, phép so sánh theo nghĩa đen $support \times MeanW \ge 0$ trở thành $0 \ge 0$ (cho kết quả `true`).
   - Việc quyết định có loại bỏ ứng viên có support = 0 tại ngưỡng 0 hay không bị chặn bởi vấn đề nghiên cứu toàn cục R6.
   - Trạng thái operational pruning tại ngưỡng 0: `UNRESOLVED / BLOCKED BY ISSUE R6`.

### 9.4. Trạng thái Nghiên cứu của OBL-05
- **Định lý Toán học:** `PROVED IN C01.2 (ĐỘC LẬP THRESHOLD)`.
- **Operational Pruning:** `APPROVABLE KHI EFFECTIVE MINSUP > 0; UNRESOLVED TẠI MINSUP = 0 (DO R6)`.

---

## 10. Chứng Minh Nghĩa Vụ OBL-06 — CMAP-S Necessary Condition

### 10.1. Phát biểu Định lý Toán học (Mathematical Theorem Statement)
Cho pattern $P = \langle p_1, \dots, p_k \rangle$ với $a = \text{lastItem}(p_k)$, và $b \in I$ (trong đó $b$ có thể trùng $a$ hoặc khác $a$).
Ma trận $\text{CMAP-S}$ được xây dựng đúng theo Định nghĩa 10.
Mệnh đề toán học:
$$\text{CMAP-S}(a, b) = false \implies support(P \oplus_S b) = 0$$
*Định lý toán học này là phổ quát và độc lập hoàn toàn với bất kỳ giá trị ngưỡng nào ($effectiveMinSup$), bao phủ cả trường hợp item lặp lại $a = b$.*

### 10.2. Các Bước Chứng Minh Hình thức
1. Giả sử phản chứng rằng $support(P \oplus_S b) > 0$.
2. Theo Định nghĩa 5, tồn tại ít nhất một $sid \in \text{Universe}(SD)$ sao cho $(P \oplus_S b) \sqsubseteq S_{sid}$.
3. Theo Định nghĩa 6 về mở rộng S-Extension, pattern mở rộng có cấu trúc:
   $$P' = P \oplus_S b = \langle p_1, p_2, \dots, p_k, (b) \rangle$$
   với $size(P') = k + 1$, $p'_{k+1} = (b)$.
4. Theo Định nghĩa 4 về Subsequence Embedding, để $P' \sqsubseteq S_{sid}$, bắt buộc phải tồn tại một dãy chỉ số nhúng $\mathbf{j} = (j_1, \dots, j_k, j_{k+1})$ trong chuỗi $S_{sid} = \langle s_1, \dots, s_m \rangle$ thỏa mãn:
   1. $1 \le j_1 < \dots < j_k < j_{k+1} \le m$;
   2. $\forall t \in [1, k]: p_t \subseteq s_{j_t}$;
   3. $(b) \subseteq s_{j_{k+1}} \iff b \in s_{j_{k+1}}$.
5. Vì $a = \text{lastItem}(p_k) \in p_k$, từ $p_k \subseteq s_{j_k}$, ta suy ra:
   $$a \in s_{j_k}$$
6. Đặt $t = j_k$ và $u = j_{k+1}$.
7. Từ điều kiện 1 của dãy nhúng, ta có quan hệ thứ tự nghiêm ngặt giữa hai vị trí itemset:
   $$1 \le t < u \le m = size(S_{sid})$$
8. Kết hợp với bước 4 và bước 5, ta có:
   $$a \in s_{sid, t} \quad \text{và} \quad b \in s_{sid, u} \quad \text{với} \quad t < u$$
9. Điều này chứng minh rằng: trong chuỗi $S_{sid}$ (với $sid \in \text{Universe}(SD)$), tồn tại hai vị trí itemset $t$ và $u$ thỏa mãn $t < u$ sao cho item $a$ xuất hiện ở itemset $t$ và item $b$ xuất hiện ở itemset $u$.
10. Theo đúng Định nghĩa 10 về ma trận $\text{CMAP-S}$:
    $$\text{CMAP-S}(a, b) = true$$
11. Kết quả ở bước 10 mâu thuẫn trực tiếp với giả thiết ban đầu là $\text{CMAP-S}(a, b) = false$.
12. Do đó, điều giả sử phản chứng là sai. Ta bắt buộc phải có:
    $$support(P \oplus_S b) = 0$$
    $\blacksquare$

### 10.3. Xử lý Trường hợp Item Lặp Lại ($a = b$)
- Xét trường hợp $a = b$, tức phép mở rộng là $P \oplus_S a$.
- Theo Định nghĩa 10:
  $$\text{CMAP-S}(a, a) = true \iff \exists sid \in \text{Universe}(SD), \exists 1 \le t < u \le size(S_{sid}): a \in s_{sid, t} \land a \in s_{sid, u}$$
- Nếu $\text{CMAP-S}(a, a) = false$, điều đó có nghĩa là trong **toàn bộ cơ sở dữ liệu**, không có bất kỳ chuỗi nào chứa item $a$ ở hai itemset khác nhau dọc theo trục thời gian. Mọi chuỗi chỉ chứa $a$ tối đa một lần (hoặc không chứa $a$).
- Khi đó, rõ ràng không thể tìm được hai chỉ số $j_k < j_{k+1}$ đều chứa $a$. Mệnh đề $support(P \oplus_S a) = 0$ là tuyệt đối chính xác!
- *Lưu ý quan trọng:* Nếu một chuỗi chỉ có itemset $(a)$ duy nhất thì nó không thể hỗ trợ $\langle(a)(a)\rangle$. Điều kiện $t < u$ (thay vì $t \le u$) là tối quan trọng để CMAP-S loại trừ được các trường hợp này.

### 10.4. Phân Tách Quyết Định Cắt Tỉa Thực Thi (Operational Pruning Consequences)
Tương tự CMAP-I:
1. **Khi ngưỡng dương ($effectiveMinSup > 0$):** Candidate $P' = P \oplus_S b$ bị từ chối an toàn và toàn bộ cây con của $P'$ bị cắt tỉa an toàn (do $support = 0 \implies WSUP = 0 < effectiveMinSup$). Cây con của nút cha $P$ không được cắt tỉa.
2. **Khi ngưỡng bằng không ($effectiveMinSup = 0$):** Định lý $support = 0$ vẫn đúng hoàn toàn, nhưng quyết định loại bỏ ứng viên bị chặn bởi R6 (`UNRESOLVED`).

### 10.5. Trạng thái Nghiên cứu của OBL-06
- **Định lý Toán học:** `PROVED IN C01.2 (ĐỘC LẬP THRESHOLD)`.
- **Operational Pruning:** `APPROVABLE KHI EFFECTIVE MINSUP > 0; UNRESOLVED TẠI MINSUP = 0 (DO R6)`.

---

## 11. Đánh Giá Xác Minh Thời Gian và Bác Bỏ Nén Điểm Mốc (OBL-07)

### 11.1. Phân Tách Hai Khái Niệm Cốt Lõi
Để bảo vệ an toàn toán học của thuật toán, C01.2 tái khẳng định sự phân tách bắt buộc giữa hai khái niệm:
1. **Support-Only Membership Verification:** Chỉ nhằm xác định xem $sid$ có thuộc $\text{SupportingSIDs}(P)$ hay không. Thuật toán được phép dừng ngay lập tức (short-circuit) khi tìm thấy **nhúng hợp lệ đầu tiên** trong chuỗi $S_{sid}$.
2. **Positional-State Maintenance:** Nhằm duy trì thông tin vị trí các điểm mốc kết thúc (endpoints) của pattern $P$ trong chuỗi $S_{sid}$ để phục vụ cho các phép mở rộng $P \oplus_I x$ và $P \oplus_S x$ ở các tầng sâu hơn của cây DFS.

### 11.2. Định nghĩa Tập Hợp Điểm Mốc Khả Dĩ Toàn Vẹn
Với mỗi chuỗi $S_{sid}$ hỗ trợ pattern $P = \langle p_1, \dots, p_k \rangle$, tập hợp tất cả các điểm mốc kết thúc khả dĩ (feasible endpoints) của $P$ trong $S_{sid}$ được định nghĩa:
$$\mathcal{E}(P, S_{sid}) = \{ j_k \mid (j_1, \dots, j_k) \text{ là nhúng hợp lệ của } P \text{ trong } S_{sid} \}$$

### 11.3. Bác Bỏ Chính Sách "Chỉ Giữ Điểm Mốc Sớm Nhất" (Earliest Endpoint Only Refutation)
- **Đề xuất chính sách:** Thuật toán chỉ lưu trữ một điểm mốc duy nhất là điểm mốc sớm nhất:
  $$j_k^{min} = \min \mathcal{E}(P, S_{sid})$$
- **Kết luận:** **BÁC BỎ — KHÔNG AN TOÀN (REFUTED AS UNSAFE).**
- **Chứng minh bằng Phản ví dụ (Counterexample CE-01):**
  - Xét CSDL chỉ gồm 1 chuỗi với $sid = 1$:
    $$S_1 = \langle (a), (ab) \rangle \quad (\text{vị trí 1 có itemset } (a), \text{vị trí 2 có itemset } (ab))$$
  - Xét pattern tiền tố $P = \langle(a)\rangle$.
  - Tập các điểm mốc kết thúc của $P$ trong $S_1$ là: $\mathcal{E}(P, S_1) = \{1, 2\}$ (vì $a \in s_1$ và $a \in s_2$).
  - Nếu áp dụng chính sách Earliest Endpoint Only, hệ thống nén tập mốc thành:
    $$j^{min} = 1$$
    và loại bỏ điểm mốc 2.
  - Bây giờ xét phép mở rộng I-Extension với item $b$:
    $$P' = P \oplus_I b = \langle(ab)\rangle$$
  - Theo Định nghĩa 6, I-Extension yêu cầu item $b$ phải xuất hiện tại chính điểm mốc đã lưu.
  - Thuật toán kiểm tra vị trí $j^{min} = 1$: $s_1 = (a)$, rõ ràng $b \notin s_1$.
  - Thuật toán kết luận: Chuỗi $S_1$ không hỗ trợ $\langle(ab)\rangle$, suy ra $support(\langle(ab)\rangle) = 0$.
  - **Mâu thuẫn thực tế:** Trong chuỗi $S_1$, vị trí 2 có itemset là $(ab)$, chứa cả $a$ và $b$. Theo Định nghĩa 4 và 5, $S_1$ thực tế **CÓ HỖ TRỢ** $\langle(ab)\rangle$, do đó $support(\langle(ab)\rangle) = 1$.
  - **Hậu quả:** Việc nén mốc thời gian thành Earliest Endpoint Only đã gây ra **FALSE NEGATIVE NGHIÊM TRỌNG** (bỏ sót hoàn toàn mẫu $\langle(ab)\rangle$ đạt ngưỡng).

### 11.4. Bác Bỏ Chính Sách "Chỉ Giữ Điểm Mốc Muộn Nhất" (Latest Endpoint Only Refutation)
- **Đề xuất chính sách:** Thuật toán chỉ lưu trữ một điểm mốc duy nhất là điểm mốc muộn nhất:
  $$j_k^{max} = \max \mathcal{E}(P, S_{sid})$$
- **Kết luận:** **BÁC BỎ — KHÔNG AN TOÀN (REFUTED AS UNSAFE).**
- **Chứng minh bằng Phản ví dụ (Counterexample CE-02):**
  - Xét CSDL chỉ gồm 1 chuỗi với $sid = 1$:
    $$S_1 = \langle (a), (b), (a) \rangle \quad (\text{vị trí 1: } (a), \text{vị trí 2: } (b), \text{vị trí 3: } (a))$$
  - Xét pattern tiền tố $P = \langle(a)\rangle$.
  - Tập các điểm mốc kết thúc của $P$ trong $S_1$ là: $\mathcal{E}(P, S_1) = \{1, 3\}$.
  - Nếu áp dụng chính sách Latest Endpoint Only, hệ thống chỉ lưu $j^{max} = 3$ và loại bỏ điểm mốc 1.
  - Bây giờ xét phép mở rộng S-Extension với item $b$:
    $$P' = P \oplus_S b = \langle(a)(b)\rangle$$
  - Theo Định nghĩa 6, S-Extension yêu cầu $b$ phải xuất hiện tại vị trí $u > j_k$.
  - Vì điểm mốc duy nhất được lưu là $j^{max} = 3$, thuật toán tìm kiếm $b$ tại các vị trí $u > 3$. Nhưng chuỗi chỉ có 3 itemset, không có vị trí nào $> 3$.
  - Thuật toán kết luận: $S_1$ không hỗ trợ $\langle(a)(b)\rangle \implies support = 0$.
  - **Mâu thuẫn thực tế:** Nhúng của $a$ tại vị trí 1 cho phép mở rộng sang $b$ tại vị trí 2 ($1 < 2$). Do đó $S_1$ thực tế **CÓ HỖ TRỢ** $\langle(a)(b)\rangle$ với $support = 1$.
  - **Hậu quả:** Chính sách Latest Endpoint Only gây ra **FALSE NEGATIVE NGHIÊM TRỌNG**.

### 11.5. Chứng Minh Tính An Toàn của Tập Toàn Bộ Điểm Mốc (All-Endpoints Policy)
- **Định lý (All-Endpoints Sufficiency Theorem):**
  Nếu trạng thái vị trí của pattern $P$ trong chuỗi $S_{sid}$ lưu trữ đầy đủ tập hợp $\mathcal{E}(P, S_{sid})$, thì:
  1. Với I-Extension: $P \oplus_I x \sqsubseteq S_{sid} \iff \exists j \in \mathcal{E}(P, S_{sid}): x \in s_{sid, j}$.
  2. Với S-Extension: $P \oplus_S x \sqsubseteq S_{sid} \iff \exists j \in \mathcal{E}(P, S_{sid}), \exists u \in [j+1, size(S_{sid})]: x \in s_{sid, u}$.
- **Chứng minh:**
  Theo Định nghĩa 6 và Mục 5, mọi nhúng của $P \oplus_I x$ bắt buộc phải suy biến thành một nhúng của $P$ có cùng điểm kết thúc $j$. Vì $\mathcal{E}(P, S_{sid})$ chứa tất cả các điểm kết thúc khả dĩ của $P$, nên điểm kết thúc của nhúng mở rộng bắt buộc phải thuộc $\mathcal{E}(P, S_{sid})$.
  Tương tự, mọi nhúng của $P \oplus_S x$ bắt buộc phải là sự nối tiếp của một nhúng của $P$ có điểm kết thúc $j < u$. Vì $j \in \mathcal{E}(P, S_{sid})$, nên nhúng mở rộng chắc chắn được tìm thấy.
  Không có bất kỳ điểm mốc khả dĩ nào bị bỏ quên. Do đó, **CHÍNH SÁCH ALL-ENDPOINTS BẢO TOÀN ĐẦY ĐỦ CÁC NHÚNG MỞ RỘNG (NO FALSE NEGATIVES)**. $\blacksquare$

### 11.6. Trạng thái Nghiên cứu của OBL-07
- **Earliest Endpoint Only:** `REFUTED AS UNSAFE`.
- **Latest Endpoint Only:** `REFUTED AS UNSAFE`.
- **All-Endpoints Policy:** `PROVED SAFE IN C01.2`.
- **Cấu trúc nén trung gian khác:** `UNRESOLVED / DEFERRED TO FUTURE DESIGN`.

---

## 12. Phân Tích Điều Kiện Biên và Ngữ Nghĩa Ngưỡng 0 (OBL-08)

### 12.1. Ma trận Phân tích 8 Tình huống Biên
Dưới đây là bảng phân tích toàn diện 8 tình huống biên về ngưỡng và dữ liệu đầu vào:

| STT | Tình Huống Biên | Hiện Trạng Xử Lý Trong Repository | Khả Năng Thực Thi Pipeline Khai Phá | Đánh Giá An Toàn Cắt Tỉa (Pruning Safety) | Trạng Thái Nghiên Cứu |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | $effectiveMinSup > 0$ | Hoạt động tiêu chuẩn. | Đầy đủ, hợp lệ. | Với $effectiveMinSup > 0$, các hệ quả operational pruning liên quan được chứng minh an toàn khi toàn bộ assumptions của từng OBL được thỏa mãn. OBL-04 được phê duyệt ở mức toán học trên số thực với bất đẳng thức nghiêm ngặt $<$; chính sách comparator cho Java `double` vẫn UNRESOLVED và chưa được phê duyệt cho implementation. | `PROVED MATHEMATICALLY ON REALS UNDER ASSUMPTIONS; COMPARATOR UNRESOLVED` |
| **2** | $effectiveMinSup = 0$ | Phương thức `meetsThreshold(support, MeanW)` kiểm tra $support \times MeanW \ge 0$. | Bị chặn bởi xung đột R6. | Cắt tỉa candidate có support 0 làm mâu thuẫn với literal comparison $\ge 0$. | `UNRESOLVED (R6 OPEN)` |
| **3** | Điểm đẳng thức $WSUP_{UB} == effectiveMinSup$ | `meetsThreshold` trả về `true`. | Hoạt động bình thường. | Cấm cắt tỉa. Cắt tỉa tại đẳng thức gây False Negative. | `PROVED / PRUNING FORBIDDEN` |
| **4** | CSDL rỗng ($|SD| = 0$) | Không có bản ghi. | Không thể chạy pipeline vì $MeanW$ không tồn tại. | Không có không gian khai phá; không áp dụng cắt tỉa. | `PROVED / PIPELINE BLOCKED` |
| **5** | Ngưỡng tuyệt đối $A > 0$ trên CSDL rỗng | Resolver trả về $effectiveMinSup = A$. `meetsThreshold(0, MeanW) == false`. | Pipeline không chạy do thiếu $MeanW$. | Không sinh mẫu nào. | `CONSISTENT WITH DOMAIN MODEL` |
| **6** | Ngưỡng tuyệt đối $A = 0$ trên CSDL rỗng | Resolver trả về $effectiveMinSup = 0$. `meetsThreshold(0, MeanW) == true`. | Xung đột R6; thiếu $MeanW$. | Xung đột lý thuyết chấp nhận pattern support 0 khi CSDL rỗng. | `UNRESOLVED (R6 OPEN)` |
| **7** | Ngưỡng tỷ lệ phần trăm trên CSDL rỗng | `MinimumSupportResolver` ném `IllegalArgumentException`. | Bị từ chối ngay tại resolver. | Không thể tính toán (phép chia cho 0). | `IMPLEMENTATION-CONFIRMED / DOMAIN POLICY REJECTED` |
| **8** | Dữ liệu đầu vào chuỗi rỗng | `Sequence` ném `IllegalArgumentException`; `SpmfSequenceParser` ném ngoại lệ. | Bị từ chối ngay tại biên parser. | Không phải là CSDL khai phá hợp lệ. | `IMPLEMENTATION-CONFIRMED / DOMAIN POLICY REJECTED` |

### 12.2. Phân Tích Chuyên Sâu Mâu Thuẫn Ngưỡng 0 (Issue R6)
1. **Bản chất mâu thuẫn:**
   Điều kiện chấp nhận mẫu là $support(P) \times \text{MeanW} \ge effectiveMinSup$.
   Khi $effectiveMinSup = 0$, bất đẳng thức trở thành:
   $$support(P) \times \text{MeanW} \ge 0$$
   Vì $support(P) \in \mathbb{N}$ và $\text{MeanW} \ge 0$, tích số luôn $\ge 0$. Bất đẳng thức nghiệm đúng với cả các mẫu có $support(P) = 0$!
   Nếu chấp nhận điều này theo nghĩa đen, thuật toán sẽ phải liệt kê **vô số các pattern không hề xuất hiện trong CSDL** (ví dụ $\langle(x)(x)\dots(x)\rangle$).
2. **Xung đột với các bộ lọc của BitPrune:**
   - Bộ lọc $\text{CMAP-I} = false$ hoặc $\text{CMAP-S} = false$ chứng minh rằng $support = 0$.
   - Bộ lọc $|BM(P)| = 0$ chứng minh rằng $support = 0$.
   - Nếu áp dụng các bộ lọc này để cắt tỉa ứng viên có support = 0 khi $effectiveMinSup = 0$, ta đang loại bỏ các mẫu mà theo nghĩa đen của toán tử $\ge 0$ là "đạt chuẩn"!
3. **Chính sách Tuân thủ Nghiên cứu:**
   - C01.2 **KHÔNG TỰ Ý ĐỔI TOÁN TỬ** $\ge$ thành $>$.
   - C01.2 **KHÔNG TỰ Ý CẤM NGƯỠNG 0**.
   - C01.2 khẳng định: **Mọi quy tắc cắt tỉa dựa trên support = 0 (CMAP, Bitmap) đều CHƯA THỂ PHÊ DUYỆT tại $effectiveMinSup = 0$ khi vấn đề R6 chưa được giải quyết.**
   - Trạng thái: `R6 — Zero Minimum Support Semantics TIẾP TỤC OPEN`.

---

## 13. Bảng Quyết Định Cắt Tỉa: Candidate Pruning vs Subtree Pruning

Bảng dưới đây là tiêu chuẩn phê duyệt chuẩn tắc duy nhất cho các quy tắc cắt tỉa trong thuật toán WSPM_BitPrune:

| Quy Tắc Cắt Tỉa (Pruning Rule) | Giả Thiết Bắt Buộc (Assumptions) | Candidate Rejection Safe? | Subtree Rejection Safe? | Ràng Buộc Ngưỡng (Threshold Restrictions) | Hành Vi Tại Điểm Đẳng Thức (Equality Behavior) | Tác Động R6 (Minsup = 0) | Phụ Thuộc OBL | Trạng Thái Phê Duyệt (Rule Status) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **R-01: Exact Temporal Verification Rejection (Support = 0)** | ASM-01, 02, 03. Đã duyệt toàn bộ các nhúng trong All-Endpoints và không tìm thấy bất kỳ chuỗi nào hỗ trợ $P$ ($\text{SupportingSIDs}(P) = \emptyset \implies support(P) = 0$). | **AN TOÀN KHI MINSUP > 0** (vì $support(P) = 0 \implies WSUP(P) = 0 < effectiveMinSup$). | **AN TOÀN CHO CÂY CON CỦA P KHI MINSUP > 0** (theo OBL-01, mọi hậu duệ có $support \le 0 \implies WSUP = 0 < \theta$). | $effectiveMinSup > 0$. | Khi $effectiveMinSup > 0$, $WSUP(P) = 0 < effectiveMinSup$ là bất đẳng thức nghiêm ngặt, không có điểm đẳng thức với ngưỡng dương. | Tại minsup = 0, $WSUP = 0 \ge 0$ thỏa literal $\ge 0 \implies$ Pruning bị chặn bởi R6 (`UNRESOLVED`). | OBL-01, OBL-02, OBL-07, R6 | `APPROVABLE (CHO MINSUP > 0; ÁP DỤNG KHI SUPPORT = 0)` |
| **R-02: CMAP-I False Filter** | ASM-01, 02, 03, 10. $\text{CMAP-I}(a, b) == false$ với $a = \text{lastItem}(p_k), a < b$. Định lý chứng minh $support(P') = 0$. | **AN TOÀN KHI MINSUP > 0** cho $P' = P \oplus_I b$ (vì $support(P') = 0 \implies WSUP = 0 < \theta$). | **AN TOÀN CHO CÂY CON CỦA $P'$ KHI MINSUP > 0**; **CẤM cắt tỉa cây con của nút cha $P$**. | $effectiveMinSup > 0$. | Không có điểm đẳng thức (kết quả là boolean). | Định lý $support = 0$ vẫn đúng; nhưng operational pruning bị chặn bởi R6 (literal $\ge 0$). | OBL-01, OBL-05, R6 | `PROVED THEOREM; OPERATIONAL PRUNING APPROVABLE CHO MINSUP > 0 (UNRESOLVED TẠI MINSUP = 0)` |
| **R-03: CMAP-S False Filter** | ASM-01, 02, 03. $\text{CMAP-S}(a, b) == false$ với $a = \text{lastItem}(p_k)$ (kể cả $a = b$). Định lý chứng minh $support(P') = 0$. | **AN TOÀN KHI MINSUP > 0** cho $P' = P \oplus_S b$ (vì $support(P') = 0 \implies WSUP = 0 < \theta$). | **AN TOÀN CHO CÂY CON CỦA $P'$ KHI MINSUP > 0**; **CẤM cắt tỉa cây con của nút cha $P$**. | $effectiveMinSup > 0$. | Không có điểm đẳng thức (kết quả là boolean). | Định lý $support = 0$ vẫn đúng; nhưng operational pruning bị chặn bởi R6 (literal $\ge 0$). | OBL-01, OBL-06, R6 | `PROVED THEOREM; OPERATIONAL PRUNING APPROVABLE CHO MINSUP > 0 (UNRESOLVED TẠI MINSUP = 0)` |
| **R-04: Bitmap Weighted Upper Bound Filter** | ASM-01 đến ASM-09. $WSUP_{UB}(P) < effectiveMinSup$ (so sánh nghiêm ngặt trên số thực). | **AN TOÀN** (vì $WSUP(P) \le WSUP_{UB}(P) < \theta$). | **AN TOÀN CHO TOÀN BỘ CÂY CON CỦA P** (vì $WSUP_{UB}(Q) \le WSUP_{UB}(P)$). | $effectiveMinSup > 0$. | **TUYỆT ĐỐI CẤM CẮT TỈA TẠI ĐẲNG THỨC ($==$).** | Không thể xảy ra vì $WSUP_{UB} \ge 0$. | OBL-02, OBL-03, OBL-04 | `APPROVABLE (CHO MINSUP > 0 VÀ SO SÁNH NGHIÊM NGẶT)` |
| **R-05: Node WSUP Below Threshold (Support > 0)** | ASM-01 đến ASM-05. Đã tính exact $support(P) > 0$ nhưng $WSUP(P) = support(P) \times MeanW < effectiveMinSup$. | **AN TOÀN** (bản thân $P$ không đạt chuẩn). | **AN TOÀN CHO TOÀN BỘ CÂY CON CỦA P** (theo OBL-02, $WSUP(Q) \le WSUP(P) < \theta$). | $effectiveMinSup > 0$. | Tại đẳng thức, mẫu được chấp nhận, cấm cắt tỉa. | Không áp dụng được khi minsup = 0. | OBL-01, OBL-02 | `APPROVABLE (CHO MINSUP > 0)` |
| **R-06: Endpoint-State Elimination (Nén mốc)** | Nén điểm mốc chỉ giữ Earliest hoặc Latest. | **KHÔNG AN TOÀN** (gây mất nhúng ở tầng sau). | **KHÔNG AN TOÀN** (phát sinh False Negatives). | Mọi cấu hình ngưỡng. | Không áp dụng. | Không áp dụng. | OBL-07 | `REJECTED (BÁC BỎ HOÀN TOÀN)` |

---

## 14. Danh Mục Phản Ví Dụ Nghiên Cứu (Counterexample Catalogue)

Dưới đây là 6 phản ví dụ chi tiết chứng minh ranh giới an toàn và bác bỏ các ngộ nhận thiết kế:

### Phản Ví Dụ CE-01: Earliest-Endpoint Compression Gây False Negative cho I-Extension
- **Mục tiêu:** Bác bỏ chính sách chỉ lưu điểm mốc sớm nhất ($j^{min}$).
- **Cơ sở dữ liệu:** Gồm 1 chuỗi $S_1 = \langle (a), (ab) \rangle$ tại vị trí 1 và 2 ($sid = 1$).
- **Pattern tiền tố:** $P = \langle(a)\rangle$.
- **Các nhúng hợp lệ của $P$:**
  - Nhúng 1: $j_1 = 1$ (endpoint 1).
  - Nhúng 2: $j_1 = 2$ (endpoint 2).
- **Điểm mốc bị giữ và bị loại:** Giữ $j^{min} = 1$; loại bỏ điểm mốc 2.
- **Phép mở rộng tiếp theo:** I-Extension $P' = P \oplus_I b = \langle(ab)\rangle$.
- **Tập SID hỗ trợ thực tế:** $\text{SupportingSIDs}(P') = \{1\}$, exact support = 1.
- **Kết quả khi áp dụng Earliest Endpoint:** Tại vị trí 1, $s_1 = (a)$ không chứa $b$. Thuật toán kết luận $support = 0$.
- **False Negative phát sinh:** Mẫu hợp lệ $\langle(ab)\rangle$ bị loại bỏ nhầm!

### Phản Ví Dụ CE-02: Latest-Endpoint Compression Gây False Negative cho S-Extension
- **Mục tiêu:** Bác bỏ chính sách chỉ lưu điểm mốc muộn nhất ($j^{max}$).
- **Cơ sở dữ liệu:** Gồm 1 chuỗi $S_1 = \langle (a), (b), (a) \rangle$ tại các vị trí 1, 2, 3 ($sid = 1$).
- **Pattern tiền tố:** $P = \langle(a)\rangle$.
- **Các nhúng hợp lệ của $P$:** Nhúng 1 tại vị trí 1; Nhúng 2 tại vị trí 3.
- **Điểm mốc bị giữ và bị loại:** Giữ $j^{max} = 3$; loại bỏ điểm mốc 1.
- **Phép mở rộng tiếp theo:** S-Extension $P' = P \oplus_S b = \langle(a)(b)\rangle$.
- **Tập SID hỗ trợ thực tế:** $\text{SupportingSIDs}(P') = \{1\}$, exact support = 1 (do nhúng $1 < 2$).
- **Kết quả khi áp dụng Latest Endpoint:** Vị trí sau 3 không còn itemset nào. Thuật toán kết luận $support = 0$.
- **False Negative phát sinh:** Mẫu hợp lệ $\langle(a)(b)\rangle$ bị loại bỏ nhầm!

### Phản Ví Dụ CE-03: Bitmap Cardinality Không Phân Biệt Thứ Tự Thời Gian (Temporal False Positive)
- **Mục tiêu:** Chứng minh Bitmap chỉ là điều kiện cần, không thể thay thế bước xác minh thời gian.
- **Cơ sở dữ liệu:** Gồm 1 chuỗi $S_1 = \langle (b), (a) \rangle$ ($sid = 1$).
- **Pattern kiểm tra:** $P = \langle(a)(b)\rangle$.
- **Bitmap:** Chuỗi 1 chứa cả $a$ và $b$, do đó $BM(a)[0] = 1, BM(b)[0] = 1 \implies BM(P)[0] = 1$.
- **Cardinality:** $cardinality(BM(P)) = 1$.
- **Thực tế thời gian:** $b$ xuất hiện ở vị trí 1, $a$ ở vị trí 2. Không tồn tại nhúng thỏa mãn $a$ đứng trước $b$.
- **Kết quả:** $\text{SupportingSIDs}(P) = \emptyset$, exact support = 0.
- **Ý nghĩa:** $cardinality = 1 > support = 0$. Khẳng định tính chất cận trên đúng đắn ($support \le cardinality$), nhưng cho thấy Bitmap bị dương tính giả (false positive) về mặt thời gian. Bắt buộc phải có bước Exact Verification.

### Phản Ví Dụ CE-04: Bitmap Cardinality Đối Với Item Lặp Lại $\langle(a)(a)\rangle$ (Multiplicity False Positive)
- **Mục tiêu:** Chứng minh cận trên Bitmap bị lỏng khi có item lặp lại nhưng vẫn bảo toàn tính bao hàm.
- **Cơ sở dữ liệu:** Gồm 1 chuỗi $S_1 = \langle (a), (b) \rangle$ ($sid = 1$).
- **Pattern kiểm tra:** $P = \langle(a)(a)\rangle$.
- **Bitmap:** $BM(P) = BM(a) \wedge BM(a) = BM(a)$. Do $a \in S_1$, $BM(P)[0] = 1 \implies cardinality(BM(P)) = 1$.
- **Thực tế thời gian:** Chuỗi $S_1$ chỉ có 1 item $a$, không thể hỗ trợ mẫu $\langle(a)(a)\rangle$.
- **Kết quả:** $\text{SupportingSIDs}(P) = \emptyset$, exact support = 0.
- **Ý nghĩa:** $cardinality = 1 > support = 0$. Cận trên không bị vi phạm.

### Phản Ví Dụ CE-05: Cắt Tỉa Tại Điểm Đẳng Thức Gây False Negative
- **Mục tiêu:** Chứng minh việc cắt tỉa khi $WSUP_{UB} \le effectiveMinSup$ là sai lầm nghiêm trọng.
- **Cơ sở dữ liệu:** Gồm 2 chuỗi $S_1 = \langle(a)(b)\rangle, S_2 = \langle(a)(b)\rangle$ ($sid \in \{1, 2\}$). Giả sử $MeanW = 0.5$.
- **Cấu hình ngưỡng:** $effectiveMinSup = 1.0$.
- **Pattern ứng viên:** $P = \langle(a)(b)\rangle$.
- **Tính toán:** $cardinality(BM(P)) = 2 \implies WSUP_{UB}(P) = 2 \times 0.5 = 1.0$.
- **So sánh:** $WSUP_{UB}(P) == effectiveMinSup$ ($1.0 == 1.0$).
- **Nếu cắt tỉa tại đẳng thức:** Thuật toán loại bỏ $P$.
- **Thực tế:** Cả 2 chuỗi đều hỗ trợ $P \implies support(P) = 2 \implies WSUP(P) = 2 \times 0.5 = 1.0 \ge 1.0$. Mẫu $P$ là mẫu hoàn toàn đạt chuẩn!
- **Hậu quả:** Mất mẫu đạt chuẩn $\implies$ **FALSE NEGATIVE**.

### Phản Ví Dụ CE-06: Sụp Đổ Tính Phản Đơn Điệu Khi Trọng Số Phụ Thuộc Pattern
- **Mục tiêu:** Chứng minh tính phản đơn điệu của WSUP không tồn tại trong bài toán dùng trọng số mẫu $W(s)$.
- **Giả thiết phản chứng:** Trọng số mẫu $W(P)$ bằng trung bình cộng trọng số item: $W(\langle(a)\rangle) = w(a) = 0.1$, $W(\langle(a)(b)\rangle) = \frac{w(a)+w(b)}{2} = \frac{0.1 + 0.9}{2} = 0.5$.
- **CSDL:** 10 chuỗi chứa $\langle(a)\rangle$, trong đó có 8 chuỗi chứa $\langle(a)(b)\rangle$.
- **Tính toán:**
  - $WSUP(\langle(a)\rangle) = 10 \times 0.1 = 1.0$.
  - $WSUP(\langle(a)(b)\rangle) = 8 \times 0.5 = 4.0$.
- **Kết quả:** $WSUP(\langle(a)(b)\rangle) = 4.0 > 1.0 = WSUP(\langle(a)\rangle)$!
- **Hệ quả:** Trọng số tăng làm $WSUP$ của chuỗi con dài hơn lại LỚN HƠN chuỗi tiền tố. Tính giảm bao đóng bị phá vỡ hoàn toàn!
- **Ý nghĩa:** Khẳng định OBL-02 CHỈ ĐÚNG nhờ WSPM_PreTree sử dụng hằng số toàn cục $MeanW$.

---

## 15. Danh Mục Vector Kiểm Thử Tất Định (Deterministic Test Vector Catalogue)

Dưới đây là 13 test vectors chi tiết (trong đó TV-11 chia thành 3 subcases rõ ràng) làm cơ sở nghiệm thu cho việc hiện thực hóa thuật toán WSPM_BitPrune:

### TV-01: Phân Biệt Cấu Trúc $\langle(a)(b)\rangle$ và $\langle(ab)\rangle$
- **Target & OBL:** Kiểm tra OBL-01, EV-03; phân biệt quan hệ đồng thời và quan hệ thứ tự.
- **Database:**
  - `SID 1`: $\langle (ab) \rangle$
  - `SID 2`: $\langle (a), (b) \rangle$
- **Candidates:** $P_1 = \langle(ab)\rangle$, $P_2 = \langle(a)(b)\rangle$.
- **Expected Supporting SIDs:**
  - $\text{SupportingSIDs}(P_1) = \{1\} \implies exactSupport = 1$.
  - $\text{SupportingSIDs}(P_2) = \{2\} \implies exactSupport = 1$.
- **Candidate Bitmap:** $BM(a) = \{1, 2\}$, $BM(b) = \{1, 2\} \implies BM(P_1) = BM(P_2) = \{1, 2\}$, $cardinality = 2$.
- **CMAP:** $\text{CMAP-I}(a, b) = true$ (do SID 1), $\text{CMAP-S}(a, b) = true$ (do SID 2).
- **Pruning Decision:** Giữ lại cả 2 để exact verification; exact verification phân loại chính xác từng SID.
- **Classification:** Positive Case / Structural Distinction.

### TV-02: Đa Nhúng Trong Cùng Một Chuỗi (Multiple Embeddings)
- **Target & OBL:** OBL-01, OBL-07; kiểm tra nguyên tắc distinct SID support.
- **Database:**
  - `SID 10`: $\langle (a), (a), (ab), (b) \rangle$ (Chứa ít nhất 3 nhúng của $\langle(a)(b)\rangle$).
  - `SID 20`: $\langle (c) \rangle$
- **Candidate:** $P = \langle(a)(b)\rangle$.
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \{10\} \implies exactSupport = 1$.
- **Candidate Bitmap:** $BM(P) = \{10\} \implies cardinality = 1$.
- **Pruning Decision:** Không cắt tỉa; SID 10 chỉ đóng góp đúng 1 vào support.
- **Classification:** Positive Case / Exact Support Semantics.

### TV-03: Repeated Items Dọc Theo Trục Thời Gian
- **Target & OBL:** OBL-01, OBL-06, OBL-07; kiểm tra $\langle(a)(a)\rangle$ và $\langle(a)(b)(a)\rangle$.
- **Database:**
  - `SID 1`: $\langle (a), (b), (a) \rangle$
  - `SID 2`: $\langle (a), (b) \rangle$
- **Candidates:** $P_1 = \langle(a)(a)\rangle$, $P_2 = \langle(a)(b)(a)\rangle$.
- **Expected Supporting SIDs:**
  - $\text{SupportingSIDs}(P_1) = \{1\} \implies exactSupport = 1$.
  - $\text{SupportingSIDs}(P_2) = \{1\} \implies exactSupport = 1$.
- **Candidate Bitmap:** $BM(P_1) = BM(a) = \{1, 2\}$ ($cardinality = 2$). $BM(P_2) = BM(a) \wedge BM(b) = \{1, 2\}$ ($cardinality = 2$).
- **CMAP-S:** $\text{CMAP-S}(a, a) = true$ (do SID 1 có $a$ tại vị trí 1 và 3).
- **Pruning Decision:** Bitmap cho $cardinality = 2$, qua exact verification SID 2 bị loại, giữ đúng SID 1.
- **Classification:** Positive Case / Repeated Item Semantics.

### TV-04: Lọc Điều Kiện Cần CMAP-I False
- **Target & OBL:** OBL-05; cắt tỉa ứng viên I-Extension khi không có đồng xuất hiện trong itemset.
- **Database:**
  - `SID 1`: $\langle (a), (b) \rangle$
  - `SID 2`: $\langle (a), (c) \rangle$
- **Candidate:** $P = \langle(ab)\rangle$ (tức $\langle(a)\rangle \oplus_I b$).
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \emptyset \implies exactSupport = 0$.
- **Candidate Bitmap:** $BM(a) \wedge BM(b) = \{1\} \implies cardinality = 1$.
- **CMAP-I:** $\text{CMAP-I}(a, b) = false$ (không có chuỗi nào chứa $a$ và $b$ trong cùng một itemset).
- **Pruning Decision:**
  - Tại $effectiveMinSup > 0$: Ứng viên $P$ bị **CẮT TỈA NGAY LẬP TỨC** bởi bộ lọc CMAP-I mà không cần kiểm tra vị trí thời gian.
  - Subtree của $P$ bị cắt tỉa.
- **Classification:** Negative Case / Safe Pruning by CMAP-I.

### TV-05: Lọc Điều Kiện Cần CMAP-S False Kể Cả Item Lặp Lại
- **Target & OBL:** OBL-06; cắt tỉa S-Extension và repeated item khi không có quan hệ trước sau.
- **Database:**
  - `SID 1`: $\langle (ba) \rangle$ (tức itemset chứa cả $a$ và $b$, $size = 1$).
  - `SID 2`: $\langle (b), (a) \rangle$ ($b$ trước $a$).
- **Candidates:** $P_1 = \langle(a)(b)\rangle$, $P_2 = \langle(a)(a)\rangle$.
- **Expected Supporting SIDs:**
  - $\text{SupportingSIDs}(P_1) = \emptyset \implies exactSupport = 0$.
  - $\text{SupportingSIDs}(P_2) = \emptyset \implies exactSupport = 0$.
- **CMAP-S:**
  - $\text{CMAP-S}(a, b) = false$ ($a$ không bao giờ xuất hiện trước $b$ ở itemset khác).
  - $\text{CMAP-S}(a, a) = false$ ($a$ không bao giờ xuất hiện ở 2 itemset khác nhau).
- **Pruning Decision:** Cả $P_1$ và $P_2$ đều bị **CẮT TỈA NGAY LẬP TỨC** bởi CMAP-S khi $effectiveMinSup > 0$.
- **Classification:** Negative Case / Safe Pruning by CMAP-S.

### TV-06: Cắt Tỉa Cận Trên Bitmap và Bảo Vệ Điểm Đẳng Thức (Deterministic Complete Vector)
- **Target & OBL:** OBL-04, CE-05; kiểm tra so sánh nghiêm ngặt và cấm cắt tỉa tại điểm đẳng thức.
- **Database:** Gồm 4 chuỗi với các actual SID cụ thể $\text{Universe}(SD) = \{1, 2, 3, 4\}$:
  - `SID 1`: $\langle (a), (b), (d) \rangle$
  - `SID 2`: $\langle (a), (b) \rangle$
  - `SID 3`: $\langle (a), (d) \rangle$
  - `SID 4`: $\langle (c) \rangle$
- **Tham số kiểm thử cấp quy tắc (Rule-Level Synthetic Parameters):**
  - $\text{MeanW} = 0.5$ (gán trực tiếp ở mức kiểm tra quy tắc đại số, không phải tính toán end-to-end từ CSDL).
  - Ngưỡng hiệu dụng: $effectiveMinSup = 1.0$.
- **Candidate 1 ($P_1 = \langle(b)(d)\rangle$):**
  - $BM(b) = \{1, 2\}$, $BM(d) = \{1, 3\}$.
  - Expected Candidate Bitmap: $BM(P_1) = BM(b) \wedge BM(d) = \{1\} \implies cardinality = 1$.
  - Expected Supporting SIDs: $\text{SupportingSIDs}(P_1) = \{1\}$ (tại SID 1, $b$ ở vị trí 2, $d$ ở vị trí 3 $\implies$ có nhúng $2 < 3$).
  - Expected Exact Support: $support(P_1) = 1$.
  - Expected WSUP Upper Bound: $WSUP_{UB}(P_1) = 1 \times 0.5 = 0.5$.
  - So sánh: $0.5 < 1.0 \implies WSUP_{UB}(P_1) < effectiveMinSup$.
  - Quyết định: **CẮT TỈA AN TOÀN** candidate $P_1$ và toàn bộ subtree của $P_1$.
- **Candidate 2 ($P_2 = \langle(a)(b)\rangle$):**
  - $BM(a) = \{1, 2, 3\}$, $BM(b) = \{1, 2\}$.
  - Expected Candidate Bitmap: $BM(P_2) = BM(a) \wedge BM(b) = \{1, 2\} \implies cardinality = 2$.
  - Expected Supporting SIDs: $\text{SupportingSIDs}(P_2) = \{1, 2\}$ (tại SID 1: $1 < 2$; tại SID 2: $1 < 2$).
  - Expected Exact Support: $support(P_2) = 2$.
  - Expected WSUP Upper Bound: $WSUP_{UB}(P_2) = 2 \times 0.5 = 1.0$.
  - So sánh: $1.0 == 1.0 == effectiveMinSup \implies$ Điểm đẳng thức chính xác!
  - Quyết định: **TUYỆT ĐỐI CẤM CẮT TỈA**. Chuyển sang exact verification.
  - Sau exact verification: $WSUP(P_2) = 2 \times 0.5 = 1.0 \ge 1.0 \implies$ $P_2$ là mẫu hợp lệ được chấp nhận.
- **Classification:** Deterministic Boundary Test / Equality Protection.

### TV-07: Phân Tích Đại Số Lý Thuyết với MeanW = 0 (Abstract Algebraic Boundary / Synthetic Method-Level Input)
- **Target & OBL:** OBL-02, OBL-04; kiểm tra tính chất đại số của phép nhân với hằng số 0 ($support \times 0 = 0$).
- **Bản chất kỹ thuật:** Ca kiểm thử này là **đại số giả định cấp phương thức** (synthetic method-level input), **KHÔNG PHẢI** là một trạng thái CSDL end-to-end hợp lệ của `ItemWeightCalculator` (vì CSDL hợp lệ không rỗng luôn có $T(i) \ge 1 \implies w(i) > 0 \implies MeanW > 0$).
- **Tham số giả định:**
  - CSDL đồ chơi gồm 3 chuỗi: `SID 1`: $\langle(a)\rangle$, `SID 2`: $\langle(a)\rangle$, `SID 3`: $\langle(a)\rangle$ với $\text{Universe}(SD) = \{1, 2, 3\}$.
  - Tham số nhân tạo cấp phương thức: $\text{meanWeight} = 0.0$.
  - Ngưỡng hiệu dụng nhân tạo: $effectiveMinSup = 0.5$.
- **Candidate:** $P = \langle(a)\rangle$.
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \{1, 2, 3\} \implies exactSupport = 3$.
- **Tính toán điểm số đại số:** $WSUP(P) = 3 \times 0.0 = 0.0$.
- **Quyết định cắt tỉa cấp phương thức:** $WSUP(P) = 0.0 < 0.5 = effectiveMinSup \implies$ Candidate $P$ không đạt ngưỡng dương $0.5$. Cắt tỉa an toàn trong bối cảnh phương thức nhận tham số này.
- **Classification:** Abstract Algebraic Boundary / Synthetic Method-Level Input.

### TV-08: Mã SID Âm và Không Liên Tục (Noncontiguous & Negative SIDs)
- **Target & OBL:** OBL-03, ASM-06; kiểm tra tính độc lập của ánh xạ song ánh `sidToIndex`.
- **Database:** Gồm 3 chuỗi với các SID thực tế: $\text{Universe}(SD) = \{-5, 0, 42\}$.
  - `SID -5`: $\langle (a) \rangle$
  - `SID 0`: $\langle (a), (b) \rangle$
  - `SID 42`: $\langle (b) \rangle$
- **Ánh xạ song ánh:**
  - $\text{sidToIndex}(-5) = 0, \text{indexToSid}(0) = -5$
  - $\text{sidToIndex}(0) = 1, \text{indexToSid}(1) = 0$
  - $\text{sidToIndex}(42) = 2, \text{indexToSid}(2) = 42$
- **Candidate:** $P = \langle(a)(b)\rangle$.
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \{0\} \implies exactSupport = 1$.
- **Candidate Bitmap:** $BM(a) = [1, 1, 0]$, $BM(b) = [0, 1, 1] \implies BM(P) = [0, 1, 0]$ (chỉ số 1 có bit 1).
- **Khôi phục SID:** $\text{indexToSid}(1) = 0$. Kết quả hoàn toàn trùng khớp với tập hỗ trợ thực tế.
- **Classification:** Positive Case / Noncontiguous Bijective Mapping.

### TV-09: Kích Thước CSDL Không Chia Hết Cho 64 (Deterministic Word-Alignment Boundary)
- **Target & OBL:** OBL-03, ASM-07; kiểm tra an toàn căn lề 64-bit và zero-padding cho bit đệm ngoài phạm vi $|SD|$.
- **Actual SID Universe:** $\text{Universe}(SD) = \{1, 2, \dots, 65\}$ ($|SD| = 65$, gồm 65 chuỗi phân biệt).
- **Database:**
  - `SID 1` đến `SID 64`: Mỗi chuỗi chứa đúng 1 itemset là $(b)$: $\forall i \in [1, 64]: S_i = \langle (b) \rangle$.
  - `SID 65`: Chuỗi chứa đúng 1 itemset là $(a)$: $S_{65} = \langle (a) \rangle$.
- **Candidate:** $P = \langle(a)\rangle$.
- **SID Mapping:**
  - $\text{sidToIndex}(i) = i - 1$ với mọi $i \in [1, 65]$: $\text{sidToIndex}(1) = 0, \dots, \text{sidToIndex}(65) = 64$.
  - $\text{indexToSid}(64) = 65$.
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \{65\}$.
- **Expected Exact Support:** $support(P) = 1$.
- **Candidate Bitmap:**
  - Biểu diễn bằng 2 từ máy 64-bit (`long[2]`, tổng 128 bit).
  - Từ 0 (chỉ số bit 0–63): Tất cả bằng 0 (vì chuỗi 1–64 chỉ chứa $b$).
  - Từ 1: Chỉ số bit 64 bằng 1 (chuỗi 65 chứa $a$).
  - Bit đệm ngoài phạm vi (chỉ số bit 65–127): Bắt buộc tất cả bằng 0 (zero-padded).
- **Expected Cardinality:** $cardinality(BM(P)) = 1$ (chỉ đếm đúng 1 bit tại tọa độ 64, không bị đếm sai do bit đệm).
- **Expected Pruning Meaning:** Vector này kiểm tra tính toàn vẹn của ánh xạ SID, căn lề 64-bit và zero-padding; không tự đưa ra weighted pruning decision nếu chưa khai báo MeanW và effective threshold.
- **Classification:** Implementation Safety / Deterministic Word-Alignment Boundary.

### TV-10: Cơ Sở Dữ Liệu Rỗng ($|SD| = 0$)
- **Target & OBL:** OBL-08; kiểm tra hành vi CSDL rỗng.
- **Database:** $|SD| = 0$.
- **Hành vi kiểm tra:**
  - Với cấu hình `PERCENT`: `MinimumSupportResolver` ném `IllegalArgumentException`.
  - Với cấu hình `ABSOLUTE`, $A = 2.0$: `ResolvedMinimumSupport` có $effectiveThreshold = 2.0$, `meetsThreshold(0, 1.0) == false`.
  - `ItemWeightCalculator` trả về `empty()`, pipeline khai phá bị dừng vì không có $MeanW$.
- **Classification:** Edge Case / Empty Database Pipeline Block.

### TV-11: Kiểm Thử Từ Chối Cú Pháp và Domain Model cho Chuỗi Rỗng (Phân tách 3 Subcases)
- **Target & OBL:** OBL-08, EV-21; kiểm tra phân định ranh giới parser syntax và domain model invariant.
- **TV-11A — Empty Sequence Delimiter (`-2` ngay lập tức):**
  - Input: Dữ liệu SPMF chứa token `-2` ngay đầu dòng.
  - Expected Behavior: `SpmfSequenceParser` ném ngoại lệ cú pháp `SpmfParseException` với thông điệp chứa `"-2 cannot close an empty sequence"`. Luồng đọc tệp bị chặn, dữ liệu không đi vào mining engine.
  - Classification: `PARSER SYNTAX REJECTION / EMPTY SEQUENCE DELIMITER`.
- **TV-11B — Empty Itemset Delimiter (`-1 -2`):**
  - Input: Dữ liệu SPMF chứa chuỗi `-1 -2`.
  - Expected Behavior: `SpmfSequenceParser` phát hiện token kết thúc itemset `-1` khi chưa có item nào trong itemset hiện tại và ném `SpmfParseException` với thông điệp chứa `"-1 cannot close an empty itemset"` (chứa cụm từ `"empty itemset"`). Luồng đọc tệp bị chặn ngay tại `-1`, không gọi đến domain constructor.
  - Classification: `PARSER SYNTAX REJECTION / EMPTY ITEMSET DELIMITER`.
- **TV-11C — Direct Domain Construction Invariant (Tạo trực tiếp đối tượng rỗng):**
  - Input: Gọi trực tiếp constructor `new Sequence(Collections.emptyList())` ở cấp mã nguồn Java.
  - Expected Behavior: Constructor của lớp `Sequence` ném `IllegalArgumentException` với thông điệp `"a sequence must contain at least one itemset"`.
  - Classification: `DOMAIN MODEL INVARIANT REJECTION / DIRECT OBJECT CONSTRUCTION`.

### TV-12: An Toàn All-Endpoints Đối Kháng Với Earliest/Latest Compression
- **Target & OBL:** OBL-07, CE-01, CE-02; kiểm tra All-Endpoints bảo vệ thành công cả I-Extension và S-Extension.
- **Database:**
  - `SID 1`: $\langle (a), (ab), (b) \rangle$ (vị trí 1, 2, 3).
- **Pattern tiền tố:** $P = \langle(a)\rangle$.
- **All-Endpoints lưu trữ:** $\mathcal{E}(P, S_1) = \{1, 2\}$.
- **Kiểm tra 1 (I-Extension với $b$):** $P \oplus_I b = \langle(ab)\rangle$. Tìm thấy $b \in s_2$ với $2 \in \mathcal{E} \implies$ Chấp nhận SID 1 ($support = 1$).
- **Kiểm tra 2 (S-Extension với $b$):** $P \oplus_S b = \langle(a)(b)\rangle$. Tìm thấy $b \in s_3$ với $3 > 1$ ($1 \in \mathcal{E}$) $\implies$ Chấp nhận SID 1 ($support = 1$).
- **Kết luận:** Chính sách All-Endpoints không làm mất bất kỳ mẫu nào.
- **Classification:** Positive Case / All-Endpoints Verification.

### TV-13: Ngưỡng Hiệu Dụng Bằng Không và Mâu Thuẫn Literal Acceptance (Issue R6 Boundary Test)
- **Target & OBL:** OBL-08, R6; kiểm tra mâu thuẫn giữa toán tử chấp nhận $\ge 0$ và bộ lọc cắt tỉa khi ngưỡng bằng 0.
- **Database:** Gồm 2 chuỗi với actual SIDs $\text{Universe}(SD) = \{1, 2\}$:
  - `SID 1`: $\langle (a) \rangle$
  - `SID 2`: $\langle (b) \rangle$
- **Candidate:** $P = \langle(a)(b)\rangle$.
- **Expected Supporting SIDs:** $\text{SupportingSIDs}(P) = \emptyset \implies exactSupport = 0$.
- **Candidate Bitmap:** $BM(P) = BM(a) \wedge BM(b) = \{1\} \wedge \{2\} = \emptyset \implies cardinality = 0$.
- **CMAP:** $\text{CMAP-S}(a, b) = false$ ($a$ không bao giờ xuất hiện trước $b$).
- **Cấu hình ngưỡng:** $effectiveMinSup = 0.0$ (với tham số giả định $\text{MeanW} = 0.5$).
- **So sánh theo nghĩa đen (Literal Comparison):**
  $$WSUP(P) = 0 \times 0.5 = 0.0 \ge 0.0 = effectiveMinSup \implies \text{true (theo nghĩa đen!)}$$
- **So sánh điều kiện cắt tỉa cận trên:**
  $$WSUP_{UB}(P) = 0 \times 0.5 = 0.0 < 0.0 \implies \text{false!}$$
- **Đánh giá quyết định cắt tỉa:**
  - *Candidate Pruning:* `UNRESOLVED / NOT APPROVED (DO R6)`. Nếu cắt tỉa ứng viên có support = 0 thì vi phạm điều kiện so sánh $\ge 0$; nếu giữ lại thì bùng nổ vô số pattern không xuất hiện.
  - *Subtree Pruning:* `UNRESOLVED / NOT APPROVED (DO R6)`.
- **Liên kết:** Dẫn chiếu trực tiếp vấn đề nghiên cứu toàn cục `R6 — Zero Minimum Support Semantics: OPEN`.
- **Classification:** Unresolved Boundary Test / Issue R6 Audit.

---

## 16. Phân Loại Mức Độ Chắc Chắn và Nguồn Gốc (Classification Summary)

Toàn bộ các kết luận nghiên cứu trong tài liệu C01.2 được phân loại minh bạch như sau:

### 16.1. SOURCE-CONFIRMED (Xác nhận từ Nguồn Nghiên cứu Xuất bản [Pham2023])
- Định nghĩa chuỗi con, nhúng và support theo distinct SID (trang 362).
- Khác biệt cấu trúc $\langle(a)(b)\rangle \ne \langle(ab)\rangle$ (trang 363).
- Công thức Item Weight $w(i)$, hằng số toàn cục $MeanW$, và điều kiện chấp nhận mẫu $WSUP \ge minsup$ (trang 364).

### 16.2. IMPLEMENTATION-CONFIRMED / DOMAIN POLICY (Xác nhận từ Mã Nguồn và Chính sách Domain của Repository)
- `Sequence.java:16` từ chối sequence rỗng không chứa itemset (`IllegalArgumentException`).
- `SpmfSequenceParser.java:93` từ chối token `-2` đóng sequence rỗng (`SpmfParseException`).
- `SpmfSequenceParser.java:82` từ chối token `-1` khi itemset rỗng (`SpmfParseException`).
- `MinimumSupportResolver.java` từ chối giải mã percentage support trên CSDL rỗng (`IllegalArgumentException`).
- `ItemWeightCalculator.java` trả về `empty()` khi CSDL rỗng.

### 16.3. PROVED IN C01.2 (Chứng minh Hình thức Thành công trong C01.2)
- **OBL-01:** Tính phản đơn điệu của Exact Support theo distinct SID ($support(Q) \le support(P)$).
- **OBL-02:** Tính giảm bao đóng của $WSUP$ dưới hằng số toàn cục $\text{MeanW} \ge 0$.
- **OBL-03:** Cận trên Cardinality của SID Candidate Bitmap ($support(P) \le cardinality(BM(P))$).
- **OBL-04 (trên số thực $\mathbb{R}$):** Tính an toàn của quy tắc cắt tỉa cận trên trọng số dưới bất đẳng thức nghiêm ngặt ($WSUP_{UB} < \theta$).
- **OBL-05 (Định lý toán học):** Tính chất điều kiện cần của bộ lọc $\text{CMAP-I}$ ($\text{CMAP-I}(a, b) = false \implies support(P \oplus_I b) = 0$, độc lập với threshold).
- **OBL-06 (Định lý toán học):** Tính chất điều kiện cần của bộ lọc $\text{CMAP-S}$ ($\text{CMAP-S}(a, b) = false \implies support(P \oplus_S b) = 0$, độc lập với threshold, bao gồm cả $a = b$).
- **OBL-07 (Phần All-Endpoints):** Tính an toàn và đầy đủ của chính sách duy trì toàn bộ điểm mốc khả dĩ $\mathcal{E}(P, S_{sid})$.

### 16.4. REFUTED / COUNTEREXAMPLE FOUND (Bác bỏ Bằng Phản Ví Dụ)
- **Earliest Endpoint Only Policy:** Bị bác bỏ bởi CE-01 (gây False Negative cho I-Extension).
- **Latest Endpoint Only Policy:** Bị bác bỏ bởi CE-02 (gây False Negative cho S-Extension).
- **Cắt tỉa tại điểm đẳng thức ($WSUP_{UB} == \theta$):** Bị bác bỏ bởi CE-05 (gây False Negative).
- **Ngộ nhận về Downward Closure khi trọng số phụ thuộc pattern:** Bị bác bỏ bởi CE-06.

### 16.5. ABSTRACT ALGEBRAIC BOUNDARY / SYNTHETIC METHOD-LEVEL INPUT (Phân Tích Đại Số Giả Định)
- Phân tích $\text{MeanW} = 0$ và TV-07: Là tính chất đại số của phép nhân với 0 ($support \times 0 = 0$) trong môi trường phương thức nhận tham số trực tiếp; không phải trạng thái CSDL end-to-end hợp lệ của `ItemWeightCalculator`.

### 16.6. IMPLEMENTATION POLICY / INFERENCE (Chính sách Cài đặt Đề xuất)
- Tách biệt ma trận CMAP thành hai cấu trúc độc lập $\text{CMAP-I}$ và $\text{CMAP-S}$.
- Ánh xạ song ánh `sidToIndex` và `indexToSid` để hỗ trợ SID noncontiguous.

### 16.7. UNRESOLVED / RESEARCH ISSUE REMAINS OPEN (Vấn đề Chưa giải quyết)
- **Vấn đề R6 (Zero Minimum Support Semantics):** Toàn bộ các quyết định operational pruning đối với candidate có $support = 0$ (khi $\text{CMAP} = false$ hoặc $|BM| = 0$) đều **CHƯA THỂ PHÊ DUYỆT** khi $effectiveMinSup = 0$. R6 tiếp tục giữ nguyên trạng thái `OPEN`.
- **Chính sách so sánh số thực trên Java `double`:** Rủi ro sai số làm tròn của kiểu `double` được xác nhận; việc lựa chọn chính sách so sánh (dung sai epsilon tỷ lệ, số nguyên hóa, v.v.) chưa được phê duyệt trong C01.2 và là `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`.
- **Cấu trúc nén bộ nhớ cho All-Endpoints:** `UNRESOLVED / DEFERRED TO FUTURE DESIGN`.

---

## 17. Bằng Chứng Nghiệm Thu Tác Vụ (Acceptance Evidence)

Tài liệu này cung cấp đầy đủ bằng chứng khoa học để đáp ứng toàn bộ các tiêu chí nghiệm thu của C01.2-R2:
- [x] Finding 1 (C01.2-R2) được khắc phục: TV-11B có expected exception message chuẩn xác: `"-1 cannot close an empty itemset"` (chứa cụm từ `"empty itemset"`).
- [x] Finding 2 (C01.2-R2) được khắc phục: TV-09 là deterministic vector hoàn chỉnh với actual SID universe $\{1, 2, \dots, 65\}$, nội dung chuỗi cụ thể ($S_1 \dots S_{64} = \langle(b)\rangle, S_{65} = \langle(a)\rangle$), ánh xạ song ánh `sidToIndex`, expected supporting SIDs $\{65\}$, exact support = 1, Bitmap index 64 = 1, padding bits 65–127 = 0.
- [x] Finding 3 (C01.2-R2) được khắc phục: OBL-08 (Bảng 12.1) chỉ tuyên bố operational pruning an toàn dưới assumptions và mô hình số thực; comparator Java `double` vẫn `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`; ngôn ngữ trong Risks/Deviations trung lập, không tự ý giao comparator policy cho C02/C03.
- [x] Định lý toán học CMAP-I và CMAP-S ($support = 0$) được tách độc lập với threshold và đánh dấu `PROVED`; operational pruning được giới hạn cho $effectiveMinSup > 0$ và ghi rõ `UNRESOLVED` tại minsup = 0 do R6.
- [x] TV-07 và phân tích $MeanW = 0$ được phân loại chính xác là `ABSTRACT ALGEBRAIC BOUNDARY / SYNTHETIC METHOD-LEVEL INPUT`; không còn mô tả CSDL bất khả thi ($support > 0$ nhưng $T(i) = 0$).
- [x] TV-06 là deterministic vector đầy đủ với actual SIDs $\{1, 2, 3, 4\}$, itemsets và expected supporting SIDs; có TV-13 cho ngưỡng bằng không gắn với R6; số lượng test vector là 13.
- [x] Quy tắc R-01 được định nghĩa chặt chẽ theo $support(P) = 0$, không có biến số $k_{min}$ chưa định nghĩa; phân định rõ ranh giới với R-05.
- [x] Không đề xuất giá trị fixed epsilon `1e-9`; chuyển trạng thái chính sách so sánh `double` về `UNRESOLVED / IMPLEMENTATION POLICY REQUIRED`.
- [x] TV-11 phân tách thành 3 subcases rõ ràng: TV-11A (parser empty sequence `-2`), TV-11B (parser empty itemset `-1 -2`), TV-11C (direct domain construction `Sequence`).
- [x] Phân loại nguồn chuẩn xác giữa `SOURCE-CONFIRMED` từ bài báo và `IMPLEMENTATION-CONFIRMED / DOMAIN POLICY` từ mã nguồn repository.
- [x] Toàn bộ các chứng minh đúng trước đó (OBL-01, OBL-02, OBL-03, OBL-04 trên số thực, OBL-07 All-Endpoints) được bảo toàn nguyên vẹn.
- [x] Danh mục 6 phản ví dụ (CE-01 đến CE-06) được bảo toàn nguyên vẹn.
- [x] Vấn đề R6 tiếp tục giữ nguyên trạng thái `Zero Minimum Support Semantics — OPEN`.
- [x] Không sửa đổi mã nguồn Java, bài kiểm thử JUnit, POM, `.gitignore` hay baseline.
- [x] Không sửa đổi tài liệu ngữ nghĩa C01.1 hay lộ trình Phase C.
- [x] Không tự ý đánh dấu C01.2 là ACCEPTED.

---

## 18. Rủi Ro Nghiên Cứu và Độ Lệch (Risks and Deviations)

1. **Rủi ro sai số dấu phẩy động (Floating-Point Risk):** Khi triển khai code ở Gate 1 và Gate 2, nếu lập trình viên sử dụng trực tiếp toán tử `<` của Java trên kiểu `double` mà không có chính sách xử lý sai số làm tròn được kiểm chứng, có thể phát sinh False Negative tại các điểm biên xấp xỉ ngưỡng. Một tác vụ thiết kế hoặc triển khai tương lai phù hợp phải đặc tả và kiểm chứng comparator policy trước khi quy tắc này được triển khai bằng Java `double`.
2. **Rủi ro bùng nổ bộ nhớ của All-Endpoints:** Việc lưu trữ toàn bộ các điểm mốc khả dĩ $\mathcal{E}(P, S_{sid})$ là an toàn tuyệt đối về mặt toán học nhưng có thể tiêu tốn bộ nhớ khi chuỗi rất dài và có nhiều item lặp lại. Các kỹ thuật tối ưu hóa biểu diễn tập mốc cần được nghiên cứu trong các tác vụ thiết kế tương lai mà không được làm mất điểm mốc.
3. **Độ lệch (Deviations):** Không có bất kỳ độ lệch nào so với Semantic Contract C01.1 đã được nghiệm thu.

---

## 19. Xác Nhận Dừng Triển Khai (STOP Confirmation)

Tôi xác nhận tuân thủ tuyệt đối quy tắc dừng của Hợp đồng giao việc C01.2-R2:
1. **Dừng triển khai ngay lập tức** sau khi hoàn thành tài liệu `docs/research/wspm-bitprune-c01-2-pruning-safety.md`.
2. **Không tự ý chuyển sang C02/C03** hay bất kỳ tác vụ nào tiếp theo.
3. **Không tự ý đánh dấu C01.2 là ACCEPTED** (chờ Research Reviewer phê duyệt).
4. **Không viết hoặc sửa bất kỳ tệp Java nào** trong `src/main/java`.
5. **Không viết hoặc sửa bất kỳ bài kiểm thử JUnit nào** trong `src/test/java`.
6. **Không thực hiện commit, push, git reset hay git clean**.
7. Toàn bộ mã nguồn và commit HEAD được bảo toàn nguyên vẹn tại commit freeze Phase B: `502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`.
