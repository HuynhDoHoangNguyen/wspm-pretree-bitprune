# C11 Differential Correctness Report
## Báo Cáo Tính Đúng Đắn Vi Sai Toàn Diện — WSPM_BitPrune Đối Chiếu WSPM_PreTree và Brute-Force Oracle

**Repository:** `wspm-pretree-bitprune`  
**Branch:** `feature/wspm-bitprune`  
**Reviewed Commit HEAD:** `c7e90039be63d0db2e5fdb8a1f036e0b2ee16686`  
**Report Date:** 2026-10-30  
**Status:** `C11 ACCEPTED — POSITIVE-EFFECTIVE-THRESHOLD OPERATIONAL SCOPE`  
**Repository Owner Decision:** Formally accepted C11.1, C11.2, C11.3, C11.4-R1 and accepted parent C11 for positive-effective-threshold operational scope. R6 zero-threshold semantics remains OPEN and deferred.  
**Independent Research Review Outcome:** `PASS` across all C11 verification units.

---

## 1. Document Identity and Review Context (Định Danh Tài Liệu)

Tài liệu này là báo cáo nghiệm thu kỹ thuật và nghiên cứu chính thức cho task **C11 — Full Differential Correctness & Regression** trong lộ trình Phase C của đề tài khóa luận:

> *“Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây dựng hệ thống gợi ý combo sản phẩm.”*

Báo cáo tổng hợp toàn bộ bằng chứng thực nghiệm vi sai giữa thuật toán cải tiến đề xuất **WSPM_BitPrune** (`BitPruneMiner`), thuật toán chuẩn nghiên cứu Phase B **WSPM_PreTree** (`WspmPreTreeMiner`), và bộ giải độc lập kiểm chứng **Brute-Force Oracle** (`BruteForceWeightedPatternOracle`) qua bốn đơn vị thực thi:
1. **C11.1:** Bảng 1 Ba Oracle Toàn Diện (Table 1 Three-Oracle Complete-Map Differential).
2. **C11.2:** Ma Trận Biên và An Toàn Cắt Tỉa Có Kiểm Soát (Controlled Boundary & Pruning-Safety Matrix).
3. **C11.3:** Không Gian Bounded Exhaustive Toàn Bộ Cơ Sở Dữ Liệu 2-Item (Bounded Exhaustive Differential Correctness).
4. **C11.4-R1:** Kiểm Thử Vi Sai Ngẫu Nhiên Hạt Giống Cố Định 1.200 Lượt Thử (Fixed-Seed Randomized Three-Oracle Full-Map Differential Correctness).

**Giới hạn chấp nhận nghiệm thu:**
Nghiệm thu C11 được xác lập **nghiêm ngặt trong phạm vi ngưỡng hiệu dụng dương (`positive-effective-threshold operational scope`)**. Báo cáo này **không tuyên bố** tính đúng đắn vô điều kiện hoặc phổ quát trên mọi không gian dữ liệu vô hạn. Semantics ngưỡng bằng 0 (R6) tiếp tục giữ trạng thái `OPEN` và được hoãn lại (deferred). Tính an toàn số thực chính xác của toán tử so sánh số thực (`double`) tiếp tục là ranh giới chưa giải quyết.

---

## 2. Research Question and Equivalence Criterion (Câu Hỏi Nghiên Cứu và Tiêu Chí Tương Đương)

### 2.1. Câu hỏi nghiên cứu
Task C11 đánh giá câu hỏi thực nghiệm cốt lõi của Phase C:
> *Khi chạy trên cùng một cơ sở dữ liệu chuỗi `SequenceDatabase` và cùng một cấu hình ngưỡng hỗ trợ tối thiểu `MinimumSupport` (với ngưỡng hiệu dụng $effectiveThreshold > 0$), thuật toán cải tiến `WSPM_BitPrune` có trả về tập kết quả mẫu tuần tự có trọng số chính xác bằng tập kết quả của thuật toán chuẩn `WSPM_PreTree` và `BruteForceWeightedPatternOracle` hay không?*

### 2.2. Tiêu chí tương đương bản đồ toàn vẹn (Complete-Map Equivalence)
Theo Quy tắc Nghiên cứu Số 6 (AGENTS.md) và Hợp đồng Kiến trúc C02, **chỉ so sánh số lượng mẫu tuần tự (pattern count) là hoàn toàn không đủ và không được chấp nhận**. 

Tương đương hoàn toàn giữa hai kết quả khai phá $M_{BitPrune}$ và $M_{Oracle}$ đòi hỏi đẳng thức tập ánh xạ:
$$\text{Map}\langle\text{Pattern}, \text{Integer}\rangle_{BitPrune} \equiv \text{Map}\langle\text{Pattern}, \text{Integer}\rangle_{Oracle}$$

Cụ thể, đẳng thức phải thỏa mãn 5 điều kiện bắt buộc:
1. **Exact Pattern Identity:** Mọi mẫu $P \in M_{BitPrune}$ đều phải có mặt trong $M_{Oracle}$, và ngược lại (không thiếu mẫu — Missing Patterns = 0; không thừa mẫu — Unexpected Patterns = 0).
2. **Exact Itemset Boundaries:** Ranh giới giữa các tập mục (itemset) phải được bảo toàn tuyệt đối; ví dụ $\langle(1)(2)\rangle \neq \langle(1\ 2)\rangle$.
3. **Exact Integer Support:** Với mọi mẫu $P$, giá trị hỗ trợ nguyên $\text{support}(P)$ được đếm bởi BitPrune phải bằng chính xác giá trị hỗ trợ nguyên được đếm bởi WSPM_PreTree và Brute-Force (Support Mismatches = 0).
4. **Weighted Support Acceptance Equivalence:** Trạng thái chấp nhận mẫu theo vị từ số thực operational của Java phải hoàn toàn đồng nhất giữa các thuật toán.
5. **Deterministic Structural Representation:** Thứ tự duyệt cây Prefix Tree và tính bất biến của cấu trúc kết quả phải được bảo toàn.

---

## 3. Oracle Models and Independence (Mô Hình Oracle và Tính Độc Lập)

Để bảo đảm tính khách quan khoa học, C11 sử dụng ba nhánh đối chứng độc lập:

```
                          +------------------------------------------------+
                          |      SequenceDatabase + MinimumSupport         |
                          +------------------------------------------------+
                                   /               |               \
                                  /                |                \
                                 v                 v                 v
            +-----------------------+   +--------------------+   +-----------------------+
            |  Table 1 Paper Oracle |   | WSPM_PreTree Miner |   |   Brute-Force Oracle  |
            |   (Bảng 1 2023 gốc)   |   | (Phase B Baseline) |   | (Tổ hợp độc lập C10)  |
            +-----------------------+   +--------------------+   +-----------------------+
            | 9 mẫu kỳ vọng         |   | Cấu trúc PrefixTree|   | Tìm kiếm vét cạn con  |
            | Hỗ trợ chính xác:     |   | Prime-block PRISM  |   | Đếm distinct SID      |
            | <(1)>: 7              |   | GCD/CARD/MASK      |   | Không dùng CMAP       |
            | <(2)>: 7              |   | Mã hóa số nguyên   |   | Không dùng Bitmap     |
            | <(3)>: 6              |   | DFS chuẩn Phase B  |   | Không dùng Verifier   |
            | <(1 2)>: 7            |   |                    |   | Không dùng PrefixTree |
            | <(1)(1)>: 6           |   |                    |   |                       |
            | <(1)(2)>: 7           |   |                    |   |                       |
            | <(1)(3)>: 6           |   |                    |   |                       |
            | <(2)(3)>: 6           |   |                    |   |                       |
            | <(1 2)(3)>: 6         |   |                    |   |                       |
            +-----------------------+   +--------------------+   +-----------------------+
                                  \                |                /
                                   \               |               /
                                    v              v              v
                          +------------------------------------------------+
                          |   WSPM_BitPrune Miner (BitPruneMiner - C10)    |
                          |   Bitmap + PositionalIndex + CMAP + Verifier   |
                          +------------------------------------------------+
                                                   |
                                                   v
                          +------------------------------------------------+
                          |   Complete Map Comparison: M1 == M2 == M3      |
                          +------------------------------------------------+
```

### 3.1. Nhánh 1: Paper / Table 1 Oracle
- Dựa trên dữ liệu chuẩn từ Bảng 1 của bài báo WSPM_PreTree (2023) (`/datasets/wspm/table1.spmf`).
- Gồm đúng 9 mẫu tuần tự có trọng số được tác giả công bố với cấu hình ngưỡng hỗ trợ tối thiểu $minsup = 14\%$ ($effectiveThreshold = 0.98$, $MeanW = 12/66$, số chuỗi $N = 7$, số item riêng biệt $M = 8$).
- Bản đồ mẫu kỳ vọng chuẩn xác từ bài báo (Paper Oracle):
  - $\langle(1)\rangle \to 7$
  - $\langle(2)\rangle \to 7$
  - $\langle(3)\rangle \to 6$
  - $\langle(1\ 2)\rangle \to 7$
  - $\langle(1)(1)\rangle \to 6$
  - $\langle(1)(2)\rangle \to 7$
  - $\langle(1)(3)\rangle \to 6$
  - $\langle(2)(3)\rangle \to 6$
  - $\langle(1\ 2)(3)\rangle \to 6$
- Xác nhận rằng thuật toán cải tiến tái lập hoàn hảo các kết quả nghiên cứu đã công bố.

### 3.2. Nhánh 2: Frozen WSPM_PreTree Baseline (Phase B)
- Thuật toán chuẩn đã được nghiệm thu và đóng băng tuyệt đối tại Phase B (`502e1ddb61d37751c2c5f606fbd30e3b90aefb6c`).
- Sử dụng mã hóa khối nguyên tố Prime-block Encoding của PRISM, các phép biến đổi số học ma trận GCD/CARD/MASK và cấu trúc Prefix Tree gốc.
- Hoàn toàn độc lập với các cấu trúc dữ liệu mới của BitPrune (Bitmap, Vertical Occurrence Index, CMAP, Verifier).

### 3.3. Nhánh 3: BruteForceWeightedPatternOracle
- Được xây dựng độc lập trong gói kiểm thử (`vn.edu.wspm.mining.BruteForceWeightedPatternOracle`), ưu tiên tính minh bạch toán học tuyệt đối hơn hiệu năng.
- Sinh tất cả các mẫu con tổ hợp có độ dài tăng dần, kiểm tra tính nhúng chuỗi con (subsequence embedding) trực tiếp trên từng chuỗi `Sequence` và đếm số lượng SID hỗ trợ riêng biệt (distinct SIDs).
- **Tuyệt đối không chia sẻ mã nguồn**: Không sử dụng CMAP-I, không sử dụng CMAP-S, không sử dụng Bitmap, không sử dụng Verifier vị trí, không sử dụng Candidate Evaluator và không duyệt DFS dựa trên cắt tỉa của BitPrune.

**Ranh giới khoa học:** Sự đồng nhất giữa các nhánh oracle trên các tập dữ liệu hữu hạn là **bằng chứng thực nghiệm (empirical evidence)** có độ tin cậy cao, không phải là chứng minh toán học hình thức trên không gian mọi cơ sở dữ liệu vô hạn.

---

## 4. Execution Units & Consolidated Evidence (Bằng Chứng Thực Nghiệm Từ Các Đơn Vị Thực Thi)

Toàn bộ kiểm thử vi sai của C11 được phân rã thành bốn đơn vị thực thi tuần tự, tất cả đều đã vượt qua thẩm định độc lập và được chủ repository chính thức chấp nhận.

| Đơn Vị Thực Thi | Mục Tiêu & Bản Chất Kiểm Thử | Quy Mô Cấu Hình / Lượt Thử | So Sánh Bản Đồ Toàn Vẹn | Mismatch | Trạng Thái Thẩm Định | SHA-256 Mã Kiểm Thử |
|---|---|---|---|:---:|:---:|:---:|
| **C11.1** | Table 1 ba oracle vi sai (Paper vs Baseline vs Brute-force vs BitPrune) | 7 tests, 7 chuỗi, 8 item riêng biệt, $minsup = 14\%$ | $9/9$ mẫu khớp tuyệt đối cả 3 oracle | 0 | ACCEPTED & COMMITTED (`c7e9003`) | `73669922C74682DB3C04015922289506C12F69D7F135D4C8811130AFFA679B26` |
| **C11.2** | Ma trận biên 9 họ cơ sở dữ liệu M01–M09, an toàn cắt tỉa CMAP/Bitmap, SID bất quy tắc | 16 tests, 9 họ database, 13 cấu hình khai phá, 13 ma trận 3-oracle | 13 cấu hình ba oracle khớp hoàn toàn | 0 | ACCEPTED (uncommitted prerequisite) | `F2E5073BFEDCF1796315CE5DE9E96B20FDA4C88B89472D7FBD2FB86007477AEA` |
| **C11.3** | Bounded exhaustive vét cạn toàn bộ 156 database từ vũ trụ 2 item, độ dài $\le 2$ | 11 tests, 156 database (12 database 1 chuỗi, 144 database 2 chuỗi), 312 cấu hình | 936 lượt chạy, 936 cặp bản đồ khớp, 1.916 mẫu/oracle | 0 | ACCEPTED (uncommitted prerequisite) | `8506900F5EE140AE98B65ACB648F30441D6BC84A00564FBEBCBCBDF01A07A6C5` |
| **C11.4-R1** | Randomized fixed-seed ($seed = 20261104L$), 1.200 trials ngẫu nhiên, witness nhúng | 4 tests, 1.200 trials (604 ABSOLUTE, 596 PERCENT), 3.600 lượt chạy | 3.600 cặp so sánh bản đồ, 16.283 mẫu/oracle, 3.006 multiEmbed | 0 | ACCEPTED (uncommitted prerequisite) | `4F4457EA0FC2325FC362D58711AEC11BE74493EB1D0A8C11C737483B325BECE6` |

### 4.1. Chi tiết C11.1: Table 1 Three-Oracle Differential
- Cấu hình kiểm thử chuẩn Table 1:
  - Cấu hình ngưỡng hỗ trợ tối thiểu: $14\%$ (`MinimumSupport.percentage(14.0)`).
  - Số lượng chuỗi: 7 chuỗi (`database.size() == 7`).
  - Số lượng item riêng biệt: 8 item (`distinctItemCount == 8`).
  - Trọng số trung bình toàn cục ($MeanW$): $12/66$.
  - Ngưỡng hiệu dụng ($effectiveThreshold$): $0.98$.
  - Số lượng mẫu của Paper Oracle: 9 mẫu.
- Bản đồ chính tắc 9 mẫu kỳ vọng của Paper Oracle:
  - $\langle(1)\rangle \to 7$
  - $\langle(2)\rangle \to 7$
  - $\langle(3)\rangle \to 6$
  - $\langle(1\ 2)\rangle \to 7$
  - $\langle(1)(1)\rangle \to 6$
  - $\langle(1)(2)\rangle \to 7$
  - $\langle(1)(3)\rangle \to 6$
  - $\langle(2)(3)\rangle \to 6$
  - $\langle(1\ 2)(3)\rangle \to 6$
- Cả ba bộ giải (`WspmPreTreeMiner`, `BruteForceWeightedPatternOracle`, `BitPruneMiner`) đều trả về tập kết quả giống nhau 100% về mẫu và giá trị hỗ trợ (khớp hoàn toàn với Paper Oracle).
- Kiểm tra tính đầy đủ của phép chiếu (projection completeness), phân biệt rõ ràng giữa I-extension $\langle(1\ 2)\rangle$ và S-extension $\langle(1)(2)\rangle$, và tính bất biến của cấu trúc Prefix Tree.

### 4.2. Chi tiết C11.2: Controlled Boundary & Pruning-Safety Matrix
- Khảo sát 9 họ cơ sở dữ liệu có cấu trúc biên thực tế (M01 đến M09):
  - **M01 (I/S Extension & Compound Extension):**
    - Database:
      - SID 1: $\langle(1\ 2)(3)\rangle$
      - SID 2: $\langle(1)(2)(3)\rangle$
      - SID 3: $\langle(4)\rangle$
    - Kiểm tra I-extension $\langle(1\ 2)\rangle$, S-extension $\langle(1)(2)\rangle$, và compound extension $\langle(1\ 2)(3)\rangle$; phân biệt cùng tập mục vs chuỗi thứ tự.
  - **M02 (Repeated Item Sequences):**
    - Database:
      - SID 1: $\langle(1)(1)(1)\rangle$
      - SID 2: $\langle(1)(1)\rangle$
    - Kiểm tra các mẫu lặp item $\langle(1)(1)\rangle$ và $\langle(1)(1)(1)\rangle$; kiểm chứng nhiều phép nhúng (multiple embeddings) và ngữ nghĩa hỗ trợ đếm distinct SID.
  - **M03 (Sparse Database):**
    - Database:
      - SID 1: $\langle(1)(2)\rangle$
      - SID 2: $\langle(3)(4)\rangle$
      - SID 3: $\langle(1)(4)\rangle$
    - Cơ sở dữ liệu thưa; kiểm chứng các mẫu liên cấu trúc không thể xảy ra đều vắng mặt chính xác.
  - **M04 (Dense Multi-item Itemsets):**
    - Database:
      - SID 1: $\langle(1\ 2\ 3)(1\ 2\ 3)\rangle$
      - SID 2: $\langle(1\ 2\ 3)\rangle$
    - Cấu trúc chuỗi dày đặc lặp lại; kiểm chứng không có mẫu bị thiếu hoặc trùng lặp khóa.
  - **M05 (Unusual SIDs & Normalization Invariance):**
    - Database với thứ tự SID thô chính xác: `[42, -5, 999, 0]`.
    - Kiểm tra SIDs âm, zero, dương và không liên tục; chứng minh tính bất biến chuẩn hóa và Bitmap/Verifier độc lập hoàn toàn với giá trị SID.
  - **M06 (Threshold Equality):**
    - Database: SID 1: $\langle(1)\rangle$, SID 2: $\langle(1)\rangle$, SID 3: $\langle(2)\rangle$ với $MeanW = 0.50$.
    - Kiểm chứng tính bao gồm tại biên chính xác khi $weightedSupport == effectiveThreshold$.
  - **M07 (CMAP-I Loose Witness):**
    - Database: SID 1: $\langle(1)(2)\rangle$, SID 2: $\langle(2\ 3)\rangle$.
    - CMAP-I true chỉ là điều kiện cần lỏng; kiểm chứng nhân chứng có hỗ trợ chính xác bằng 0 bị loại bỏ an toàn.
  - **M08 (Bitmap/CMAP-S Pass Followed by Exact Rejection):**
    - Database với raw SIDs `[-5, 10]`: SID -5: $\langle(3)(1)(2)\rangle$, SID 10: $\langle(2)(3)\rangle$.
    - CMAP-S thông qua, Cardinality/Chặn trên Bitmap thông qua, nhưng Exact Temporal Verifier từ chối chính xác (kết quả evaluator: `EXACT_REJECTED`).
  - **M09 (Empty Database):**
    - Database rỗng với ngưỡng tuyệt đối dương ($0.50$).
    - Cả ba bản đồ kết quả đều rỗng; cây Prefix Tree đóng băng chỉ gồm nút gốc rỗng.
- Toàn bộ 13 cấu hình ba oracle đều đạt kết quả tương đương hoàn hảo với 0 sai lệch.

### 4.3. Chi tiết C11.3: Bounded Exhaustive Differential Correctness
- Kiểm thử vét cạn không sót bất kỳ trường hợp nào trong không gian hữu hạn:
  - Vũ trụ item: $\Sigma = \{1, 2\}$.
  - Tập mục chính tắc (canonical itemsets): $(1), (2), (1\ 2)$.
  - Chuỗi có tối đa 2 tập mục: $3 + 3 \times 3 = 12$ chuỗi khả dĩ.
  - Cơ sở dữ liệu: Tất cả các cơ sở dữ liệu có 1 chuỗi ($12$) và 2 chuỗi ($12 \times 12 = 144$), tổng cộng 156 cơ sở dữ liệu.
  - Mỗi cơ sở dữ liệu được kiểm thử với 2 chế độ ngưỡng ($ABSOLUTE$ và $PERCENT$).
- Tổng cộng $156 \times 2 = 312$ cấu hình khai phá, tương ứng $312 \times 3 = 936$ lượt chạy thuật toán và 936 cặp so sánh bản đồ hoàn chỉnh.
- Tổng số mẫu được chấp nhận qua tất cả các cấu hình là 1.916 mẫu cho mỗi oracle, với **0 missing pattern, 0 unexpected pattern, 0 support mismatch**.
- Đạt độ phủ đầy đủ trên các biên: ngưỡng bằng số nguyên, ngưỡng có phần thập phân, ngưỡng chính xác bằng hỗ trợ, cơ sở dữ liệu cho tập kết quả rỗng và tập kết quả không rỗng.

### 4.4. Chi tiết C11.4-R1: Fixed-Seed Randomized Full-Map Differential
- Sử dụng hạt giống giả ngẫu nhiên xác định `FIXED_SEED = 20261104L` để sinh 1.200 cơ sở dữ liệu và cấu hình ngẫu nhiên (604 ABSOLUTE, 596 PERCENT).
- Kích thước cơ sở dữ liệu biến thiên từ 1 đến 4 chuỗi, số tập mục mỗi chuỗi từ 1 đến 4, số item mỗi tập mục từ 1 đến 3, vũ trụ item từ $\{1..2\}$ đến $\{1..6\}$.
- Tổng cộng 3.600 lượt chạy thuật toán và 3.600 cặp so sánh bản đồ hoàn chỉnh.
- Tổng số mẫu được chấp nhận là 16.283 mẫu cho mỗi oracle, độ dài mẫu lớn nhất ghi nhận là 8, với **0 missing pattern, 0 unexpected pattern, 0 support mismatch**.
- **Xác minh độc lập nhân chứng đa nhúng (True Multiple-Embedding Witness):**
  - Thuật toán kiểm tra độc lập `countDistinctEmbeddings` ghi nhận **3.006 trường hợp** mẫu tuần tự được chấp nhận có $\ge 2$ phép nhúng hợp lệ trong cùng một SID.
  - Trong cả 3.006 trường hợp, giá trị hỗ trợ $\text{actualSupport}$ được khẳng định bằng chính xác số lượng bản ghi SID hỗ trợ (`supportingRecordCount`), và nhỏ hơn nghiêm ngặt tổng số phép nhúng (`actualSupport < totalEmbeddingSum`), chứng minh tính đúng đắn của ngữ nghĩa hỗ trợ đếm SID một lần duy nhất.
- **Tính xác định của thứ tự duyệt và tuần tự hóa:**
  - Khai phá lặp lại 2 lần độc lập trên cùng cấu hình chứng minh thứ tự tuần tự hóa `pattern.serialize() + "=" + support` và thứ tự duyệt tiền thứ tự Prefix Tree (`preOrderPatterns`) của BitPrune là hoàn toàn đồng nhất và xác định.

---

## 5. Required Behavior Coverage Matrix (Ma Trận Độ Phủ Hành Vi Bắt Buộc)

| Hành Vi Khai Phá / Thuộc Tính Cấu Trúc | Nguồn Minh Chứng & Đơn Vị Đo Lường | Trạng Thái Độ Phủ & Kết Quả |
|---|---|:---:|
| **I-Extension** (mở rộng cùng tập mục) | C11.1, C11.2 (M01), C11.3, C11.4-R1 (`acceptedI = 10,166`) | ĐÃ ĐỘ PHỦ — Tương đương hoàn toàn |
| **S-Extension** (mở rộng tập mục kế tiếp) | C11.1, C11.2 (M01), C11.3, C11.4-R1 (`acceptedS = 12,841`) | ĐÃ ĐỘ PHỦ — Tương đương hoàn toàn |
| **Same-itemset vs Sequential Identity** | C11.1 ($\langle(1\ 2)\rangle \neq \langle(1)(2)\rangle$), C11.2 (M01), C11.3, C11.4-R1 | ĐÃ ĐỘ PHỦ — Phân biệt rõ $\langle(1\ 2)\rangle \neq \langle(1)(2)\rangle$ |
| **Repeated Items Across Itemsets** | C11.2 (M02), C11.4-R1 (`repeatedItem = 1,346`, `acceptedRepeat = 9,959`) | ĐÃ ĐỘ PHỦ — Nhận diện chính xác mẫu lặp item |
| **Multiple Embeddings within One SID** | C11.2 (M02), C11.4-R1 (`multiEmbed = 3,006` nhân chứng độc lập) | ĐÃ ĐỘ PHỦ — Ngữ nghĩa đếm SID duy nhất được bảo toàn |
| **SID-Count-Once Support Semantics** | C11.2 (M02), C11.4-R1 (`actualSupport < totalEmbeddingSum` trên 3.006 ca) | ĐÃ ĐỘ PHỦ — Không đếm trùng lặp hỗ trợ |
| **Sparse Database Structures** | C11.2 (M03), C11.4-R1 (`sparse = 57`) | ĐÃ ĐỘ PHỦ — Không bỏ sót mẫu khi khoảng cách lớn |
| **Dense Database Structures** | C11.2 (M04), C11.4-R1 (`dense = 434`) | ĐÃ ĐỘ PHỦ — Xử lý an toàn nhiều item đồng xuất hiện |
| **Negative, Zero & Non-contiguous SIDs** | C11.2 (M05: SIDs `[42, -5, 999, 0]`) | ĐÃ ĐỘ PHỦ — Bitmap/Verifier độc lập với giá trị SID |
| **Threshold Equality Boundary** | C11.2 (M06: $MeanW = 0.50$), C11.3 (nhân chứng thực thi & test riêng) | ĐÃ ĐỘ PHỦ — Biên bao gồm chấp nhận chính xác |
| **ABSOLUTE Minimum Support Mode** | C11.2 (M01–M09), C11.3 (156 cấu hình), C11.4-R1 (`abs = 604`) | ĐÃ ĐỘ PHỦ — Ngưỡng nguyên và số thực khớp |
| **PERCENT Minimum Support Mode** | C11.1 ($14\%$), C11.3 (156 cấu hình), C11.4-R1 (`pct = 596`) | ĐÃ ĐỘ PHỦ — Tính toán tỷ lệ phần trăm chuẩn xác |
| **CMAP Loose / False Positive Cases** | C11.2 (M07: CMAP-I true nhưng hỗ trợ chính xác là 0) | ĐÃ ĐỘ PHỦ — Tầng lọc an toàn, không gây false negative |
| **Bitmap Upper Bound Loose Cases** | C11.2 (M08: Bound passes nhưng Verifier từ chối) | ĐÃ ĐỘ PHỦ — Chặn trên an toàn, không cắt tỉa sai |
| **Exact Temporal Verification Rejection**| C11.2 (M08: `EXACT_REJECTED` sau khi CMAP/Bitmap pass) | ĐÃ ĐỘ PHỦ — Verifier loại bỏ chính xác sai lệch thứ tự |
| **Deterministic Repeated Execution** | C11.4-R1 (Khai phá lặp lại 2 lần trên 20 trial mẫu) | ĐÃ ĐỘ PHỦ — Bản đồ kết quả đồng nhất 100% |
| **Immutable Result Maps** | C11.4-R1 (`assertThrows(UnsupportedOperationException)`) | ĐÃ ĐỘ PHỦ — Bản đồ kết quả đóng băng an toàn |
| **Frozen PrefixTree Integration** | C11.4-R1 (`tree.isFrozen() == true`, 1:1 non-root node) | ĐÃ ĐỘ PHỦ — Cây Prefix Tree đồng bộ với bản đồ |
| **Deterministic Traversal Order** | C11.4-R1 (`preOrderPatterns()` và map entry iteration) | ĐÃ ĐỘ PHỦ — Thứ tự duyệt xác định, tái lập |

*Lưu ý khoa học:* Chế độ "tắt cắt tỉa hoàn toàn" (pruning-disabled mode) không được thực thi trong C11 vì cấu trúc BitPrune được thiết kế gắn liền với cơ chế đa tầng; báo cáo không đưa ra tuyên bố về chế độ không cắt tỉa.

---

## 6. Mismatch Disposition and Fail-Fast Diagnostics (Xử Lý Sai Lệch và Chẩn Đoán Lỗi)

### 6.1. Thống kê sai lệch tổng hợp
Qua toàn bộ quá trình kiểm thử vi sai của C11:
- **Missing Patterns:** `0`
- **Unexpected Patterns:** `0`
- **Support Mismatches:** `0`
- **Unexplained Mismatches:** `0`

Không có bất kỳ trường hợp sai lệch nào xuất hiện. Tuyệt đối không có hành vi chỉnh sửa kết quả kỳ vọng để che giấu sai lệch thuật toán.

### 6.2. Cơ chế chẩn đoán fail-fast
Mỗi bài kiểm thử C11 đều cài đặt bộ xây dựng chẩn đoán chi tiết (`buildMismatchDiagnostic`), sẵn sàng xuất thông tin toàn diện ngay khi phát hiện sai lệch đầu tiên:
- Định danh Task và Hạt giống ngẫu nhiên (`seed`).
- Chỉ số lượt thử (`trialIndex`) và chuỗi dữ liệu thô kèm SID.
- Thứ tự bản ghi và cấu hình ngưỡng (`mode`, `configuredValue`, `effectiveThreshold`, `MeanW`).
- Bản đồ kết quả của Baseline, Brute-force và BitPrune.
- Danh sách chi tiết các mẫu bị thiếu (`missing`), bị thừa (`unexpected`), và mẫu đầu tiên bị sai hỗ trợ (`firstSupportMismatch`).
- Thứ tự duyệt tiền thứ tự của cây Prefix Tree.

---

## 7. Numeric Comparison Policy (Chính Sách So Sánh Số Học)

Chính sách so sánh số học trong C11 tuân thủ nghiêm ngặt các quyết định đã được xác lập từ Phase B và C06:
1. **Vị từ so sánh sản phẩm:** Sử dụng vị từ chuẩn Java-double thực thi đã được chấp nhận (`accepted operational Java-double predicate`) theo quy định của frozen baseline để quyết định tư cách thành viên của mẫu trong bản đồ hoàn chỉnh.
2. **Không làm thay đổi vị từ sản phẩm:** Tuyệt đối không có giá trị epsilon hay sai số dung sai nào làm thay đổi vị từ chấp nhận sản phẩm; không có epsilon nào được dùng để quyết định việc một mẫu có thuộc bản đồ kết quả hoàn chỉnh hay không; không có comparator thay thế hay chính sách làm tròn nào được đưa vào sản phẩm.
3. **Phân biệt dung sai kiểm thử và vị từ sản phẩm:** Các bài kiểm thử C11.1 và C11.2 sử dụng dung sai $10^{-12}$ duy nhất cho các phép khẳng định chẩn đoán và kiểm tra siêu dữ liệu số thực dẫn xuất (`derived double metadata`), hoàn toàn không can thiệp vào vị từ chấp nhận mẫu của thuật toán.
4. **Đẳng thức thực thi trong C11.3:** C11.3 ghi nhận ít nhất một nhân chứng đẳng thức số học thực thi biên và bao gồm một bài kiểm thử riêng biệt `exact_threshold_equality_is_retained`, sử dụng so sánh thực thi trực tiếp `Double.compare == 0` để phân loại độ phủ biên.
5. **So sánh công bằng:** Cả WSPM_PreTree baseline và WSPM_BitPrune đều được đánh giá dưới cùng một vị từ so sánh số thực của JVM.
6. **Giới hạn số thực:** Báo cáo ghi nhận rằng sự tương đương vi sai này được thiết lập trên biểu diễn IEEE-754 của Java `double`. Kết quả này **không đồng nghĩa** với việc chứng minh tính an toàn tương đương số thực toán học lý thuyết (exact-real mathematical arithmetic) trong mọi điều kiện biên vô hạn.

---

## 8. Research Boundary R6 and Excluded Scope (Ranh Giới Nghiên Cứu R6 và Phạm Vi Loại Trừ)

### 8.1. R6 Zero Minimum Support Semantics: OPEN
Vấn đề nghiên cứu R6 liên quan đến ngữ nghĩa khi ngưỡng hỗ trợ hiệu dụng bằng 0 ($effectiveThreshold = 0.0$):
- Khi ngưỡng bằng 0, không gian mẫu thỏa mãn về mặt lý thuyết có thể trở nên vô hạn hoặc bùng nổ tổ hợp (mọi chuỗi con đều có trọng số $\ge 0$).
- Thuật toán duyệt DFS của BitPrune và Baseline chưa có chính sách cắt tỉa chặn trên được phê duyệt chính thức cho trường hợp ngưỡng bằng 0.
- Do đó, **ngữ nghĩa ngưỡng hỗ trợ bằng 0 tiếp tục giữ trạng thái `OPEN`** và được **loại trừ hoàn toàn khỏi phạm vi nghiệm thu của C11**.

### 8.2. Phân loại mục kiểm tra
Checklist ban đầu của C11 có mục:
> `[ ] Test zero-threshold policy where resolved`

Mục này được phân loại chính thức là:
$$\textbf{DEFERRED / NOT APPLICABLE UNTIL R6 IS RESOLVED}$$
Mục này không được đánh dấu hoàn thành và sẽ được xem xét trong một hợp đồng nghiên cứu riêng biệt do Chủ repository ủy quyền khi có quyết định về R6.

Phạm vi nghiệm thu của C11 áp dụng cho **tất cả các cấu hình có ngưỡng hỗ trợ hiệu dụng dương nghiêm ngặt ($effectiveThreshold > 0$)**. Hành vi trên cơ sở dữ liệu rỗng tiếp tục tuân thủ các ranh giới tiền xử lý đã được nghiệm thu tại C10.4.

---

## 9. Verification Commands and Reproducible Results (Lệnh Kiểm Tra và Kết Quả Tái Lập)

Tất cả các kết quả kiểm thử được thực thi và tái lập độc lập trên môi trường chuẩn (Java JDK 21, Apache Maven, Windows):

### 9.1. Bộ kiểm thử vi sai C11 tập trung
```powershell
mvn -q clean "-Dtest=BitPruneTable1ThreeOracleDifferentialTest,BitPruneControlledDifferentialCorrectnessTest,BitPruneBoundedExhaustiveDifferentialTest,BitPruneFixedSeedRandomizedDifferentialTest" test
```
**Kết quả thực tế ghi nhận từ Surefire XML:**
- Số lượng tệp báo cáo: `4`
- Tổng số bài kiểm thử: `38`
- Thất bại (Failures): `0`
- Lỗi (Errors): `0`
- Bỏ qua (Skipped): `0`
- Trạng thái: **BUILD SUCCESS**

Chi tiết từng lớp kiểm thử:
- `BitPruneTable1ThreeOracleDifferentialTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- `BitPruneControlledDifferentialCorrectnessTest`: 16 tests, 0 failures, 0 errors, 0 skipped.
- `BitPruneBoundedExhaustiveDifferentialTest`: 11 tests, 0 failures, 0 errors, 0 skipped.
- `BitPruneFixedSeedRandomizedDifferentialTest`: 4 tests, 0 failures, 0 errors, 0 skipped.

### 9.2. Toàn bộ hồi quy hệ thống (Full Regression Suite)
```powershell
mvn -q test
```
**Kết quả thực tế ghi nhận từ Surefire XML:**
- Số lượng tệp báo cáo: `61`
- Tổng số bài kiểm thử: `845`
- Thất bại (Failures): `0`
- Lỗi (Errors): `0`
- Bỏ qua (Skipped): `0`
- Trạng thái: **BUILD SUCCESS**

---

## 10. Limitations and Claim Boundaries (Giới Hạn và Tuyên Bố Khoa Học)

Tuân thủ nghiêm ngặt Quy tắc Số 11 và 29 của AGENTS.md:

### 10.1. Tuyên bố được phép (Authorized Claims)
- WSPM_BitPrune trả về tập kết quả $\text{Map}\langle\text{Pattern}, \text{Integer}\rangle$ giống hệt 100% so với WSPM_PreTree baseline và Brute-Force Oracle trên toàn bộ các cấu hình đã được kiểm thử với ngưỡng hỗ trợ hiệu dụng dương.
- Không phát hiện bất kỳ sai lệch nào về mẫu bị thiếu, mẫu bị thừa hoặc giá trị hỗ trợ trong các tập dữ liệu có kiểm soát, vét cạn không gian hữu hạn (156 database) và 1.200 lượt thử ngẫu nhiên.
- Các cơ chế cắt tỉa đa tầng (CMAP, Bitmap Upper Bound, Exact Temporal Verifier) không gây ra hiện tượng cắt tỉa sai (false negative) trên toàn bộ không gian kiểm thử.

### 10.2. Tuyên bố bị cấm (Forbidden Claims)
- **Không tuyên bố:** BitPrune đã được "chứng minh toán học đúng trên mọi cơ sở dữ liệu vô hạn".
- **Không tuyên bố:** Kiểm thử ngẫu nhiên tương đương với chứng minh hình thức.
- **Không tuyên bố:** BitPrune "nhanh hơn", "tiết kiệm bộ nhớ hơn", hay "có khả năng mở rộng tốt hơn". Mọi so sánh hiệu năng thuộc phạm vi riêng của Phase D và đòi hỏi quy trình đo lường công bằng độc lập.
- **Không tuyên bố:** Vấn đề ngưỡng bằng 0 (R6) đã được giải quyết.
- **Không tuyên bố:** Toán tử Java `double` tương đương hoàn toàn với số thực toán học trong mọi trường hợp.
- **Không tuyên bố:** C11 bao gồm việc thu thập thống kê (C12) hay đo lường tài nguyên (C13).

---

## 11. Acceptance Decision (Quyết Định Nghiệm Thu)

### C11 ACCEPTED — POSITIVE-EFFECTIVE-THRESHOLD OPERATIONAL SCOPE

**Căn cứ nghiệm thu:**
1. Đẳng thức bản đồ hoàn chỉnh $\text{Map}\langle\text{Pattern}, \text{Integer}\rangle$ được xác nhận trên toàn bộ các cấu hình có ngưỡng hiệu dụng dương.
2. Tái lập thành công 100% kết quả Bảng 1 của bài báo WSPM_PreTree (2023) với 9 mẫu tuần tự có trọng số chuẩn xác ($minsup = 14\%$, $effectiveThreshold = 0.98$, $MeanW = 12/66$).
3. Vượt qua 100% các ca kiểm thử của Ma trận biên có kiểm soát C11.2 (9 họ dữ liệu M01–M09, 13 cấu hình ba oracle).
4. Vượt qua 100% không gian vét cạn có giới hạn C11.3 (156 cơ sở dữ liệu, 312 cấu hình, 1.916 mẫu/oracle).
5. Vượt qua 100% không gian ngẫu nhiên C11.4-R1 (1.200 trials, 3.600 lượt chạy, 16.283 mẫu/oracle, 3.006 nhân chứng đa nhúng).
6. Toàn bộ 38/38 kiểm thử vi sai C11 và 845/845 kiểm thử toàn hệ thống đều đạt `PASS` tuyệt đối.
7. Không còn bất kỳ sai lệch nào chưa được giải thích.

**Phạm vi hoãn lại (Deferred Scope):**
- Ngữ nghĩa ngưỡng hỗ trợ tối thiểu bằng 0 (R6 Zero Minimum Support Semantics) tiếp tục giữ trạng thái `OPEN`.
- Tính tương đương số thực chính xác lý thuyết (Exact-real comparator safety) tiếp tục là ranh giới mở.

---

## 12. Artifact Integrity and Repository State (Tính Toàn Vẹn Mã Nguồn)

- **Nhánh làm việc:** `feature/wspm-bitprune`
- **Reviewed Commit HEAD:** `c7e90039be63d0db2e5fdb8a1f036e0b2ee16686`
- **Trạng thái Git Index:** Trống (`git diff --cached --name-status` không có tệp nào được stage).
- **Trạng thái mã nguồn kiểm thử C11.2, C11.3, C11.4:** Là các tệp chưa được commit (untracked) có chủ đích theo quyết định của Chủ repository, đóng vai trò điều kiện tiên quyết hợp lệ.

**Bảng mã băm SHA-256 các thành phần được bảo vệ:**
- C11.1 (`BitPruneTable1ThreeOracleDifferentialTest.java`): `73669922C74682DB3C04015922289506C12F69D7F135D4C8811130AFFA679B26`
- C11.2 (`BitPruneControlledDifferentialCorrectnessTest.java`): `F2E5073BFEDCF1796315CE5DE9E96B20FDA4C88B89472D7FBD2FB86007477AEA`
- C11.3 (`BitPruneBoundedExhaustiveDifferentialTest.java`): `8506900F5EE140AE98B65ACB648F30441D6BC84A00564FBEBCBCBDF01A07A6C5`
- C11.4-R1 (`BitPruneFixedSeedRandomizedDifferentialTest.java`): `4F4457EA0FC2325FC362D58711AEC11BE74493EB1D0A8C11C737483B325BECE6`
- Dirty artifact lịch sử (`MainWspmPreTree.java`): `66200B07EEDB73DE6208972D2DDBFA01A60D6C0D544C74A96B63F36F6EB8A52D`
- Lộ trình Phase C (`PHASE_C_WSPM_BitPrune.md`): `86859E5D2BEE4B301B2B73B7352C1618D605334365384D39A843BEC66D67FD06`

---

## 13. Next Gate Authorization (Chuyển Giao Giai Đoạn Tiếp Theo)

- Task **C11 chính thức khép lại** và đạt trạng thái `ACCEPTED`.
- Task **C12 — Mining Statistics & Accounting Invariants** tiếp tục giữ trạng thái **`NOT STARTED`**.
- Tuyệt đối không tự ý bắt đầu triển khai C12, không viết mã thu thập thống kê, và không thực hiện đo lường hiệu năng.
- Việc thực hiện C12 đòi hỏi một quy trình đánh giá sẵn sàng (Readiness Audit) và hợp đồng triển khai riêng biệt do Chủ repository phê duyệt.
