# PHASE_B_WSPM_PRETREE.md

# GIAI ĐOẠN B — PHÂN TÍCH VÀ TÁI HIỆN WSPM_PreTree

## 1. Mục tiêu giai đoạn

Mục tiêu của Giai đoạn B là **phân tích, đặc tả và tái hiện WSPM_PreTree sát bài báo 2023 nhất có thể**, trước khi triển khai WSPM_BitPrune.

Tài liệu chính:

- *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding* (2023).(docs\Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf)
- Đề cương khóa luận CNTT-KLCN256.(docs\CNTT-KLCN256_thuật toán WSPM_PreTree_Đã sửa.txt)
- Tài liệu PRISM gốc nếu cần để tái hiện Prime-block Encoding. (docs\Prism_An effective approach for frequent sequence mining prime-block encoding.pdf)
- `AGENTS.md` tại root repository.

Giai đoạn B ưu tiên:

1. tính đúng đắn,
2. khả năng giải thích,
3. khả năng tái lập,
4. kiểm thử theo paper,
5. sau cùng mới là hiệu năng.

Không được triển khai WSPM_BitPrune trong giai đoạn này.

---

# 2. Điều kiện tiên quyết

Chỉ bắt đầu Giai đoạn B khi Giai đoạn A đã hoàn tất.

Phải xác nhận repository có tối thiểu:

- Java JDK 21;
- Maven;
- JUnit 5;
- cấu trúc `src/main/java`;
- cấu trúc `src/test/java`;
- Git hoạt động;
- `mvn test` chạy thành công;
- file `AGENTS.md` tồn tại ở root.

Nếu bất kỳ điều kiện nào chưa đạt, dừng và báo cáo.

---

# 3. Quy tắc bắt buộc

Mọi task trong Phase B phải tuân thủ `AGENTS.md`.

Ngoài ra:

- Không viết toàn bộ WSPM_PreTree trong một task.
- Không dùng Bitmap để thay Prime-block Encoding.
- Không dùng ID-list đơn giản rồi gọi đó là WSPM_PreTree gốc.
- Không thay đổi công thức support, item weight, MaxW, MinW, MeanW.
- Không thay đổi điều kiện weighted sequential pattern của paper.
- Không tự nghĩ ra chi tiết PRISM nếu tài liệu chưa đủ.
- Không benchmark hiệu năng trước khi baseline vượt qua correctness tests.
- Không triển khai Spring Boot, API, database hoặc frontend.
- Không tự động chuyển task tiếp theo.

Sau mỗi task phải dừng lại và chờ người dùng xác nhận.

---

# 4. Nguồn chuẩn của WSPM_PreTree

Paper 2023 mô tả các thành phần cốt lõi:

- Sequence Database.
- Support của sequence.
- Item Weight.
- Itemset Weight.
- Sequence Weight.
- MaxW.
- MinW.
- MeanW.
- Weighted sequential pattern.
- Prefix Tree.
- I-Extension.
- S-Extension.
- DFS.
- Prime-block Encoding từ PRISM.
- Join operations trên Prime-block.
- Tập F1.
- Điều kiện mở rộng node.

Paper cũng cung cấp bộ dữ liệu ví dụ Table 1 dùng làm oracle kiểm thử.

Các giá trị paper cần tái hiện gồm tối thiểu:

- 7 sequences.
- 8 distinct items: `a, b, c, d, e, f, g, h`.
- Item weights ở Table 2.
- `MaxW = W(a)`.
- `MinW = W(h)`.
- `MeanW ≈ 0.181`.
- với `minsup = 14%`, F1 gồm:
  - `<(a)> : 7`
  - `<(b)> : 7`
  - `<(c)> : 6`
- paper báo cáo tổng cộng 9 weighted sequential patterns cho ví dụ này.

Những giá trị này là checkpoint nghiên cứu, không được hard-code vào thuật toán.

---

# 5. Chiến lược thực hiện Phase B

Phase B được chia thành các task nhỏ:

- B0 — Audit trạng thái repository.
- B1 — Research Contract cho WSPM_PreTree.
- B2 — Domain Model cho sequence database.
- B3 — Parser dữ liệu chuỗi.
- B4 — Table 1 fixture.
- B5 — Thống kê item và support đơn.
- B6 — Item Weight, MaxW, MinW, MeanW.
- B7 — Minimum support semantics.
- B8 — Xây dựng F1.
- B9 — Pattern model + canonical representation.
- B10 — I-Extension và S-Extension.
- B11 — Prefix Tree model.
- B12 — Nghiên cứu PRISM Prime-block Encoding.
- B13 — Prime-block primitives.
- B14 — Prime-block joins và support.
- B15 — Candidate extension engine.
- B16 — DFS WSPM_PreTree.
- B17 — Table 1 end-to-end reproduction.
- B18 — Brute-force oracle cho dữ liệu nhỏ.
- B19 — Differential correctness tests.
- B20 — Baseline logging/statistics.
- B21 — Baseline review và đóng Phase B.

Không được bỏ qua B12–B14 bằng một cấu trúc khác rồi tuyên bố baseline hoàn chỉnh.

---

# 6. B0 — AUDIT REPOSITORY

## Goal

Xác nhận repository sau Phase A đủ điều kiện bắt đầu nghiên cứu thuật toán.

## Tasks

Kiểm tra:

```bash
java -version
mvn -version
git status
mvn test
```

Đọc:

- `pom.xml`
- `README.md`
- `AGENTS.md`
- cấu trúc `src/main/java`
- cấu trúc `src/test/java`

## Acceptance Criteria

- [ ] Java là JDK 21.
- [ ] Maven hoạt động.
- [ ] `mvn test` PASS.
- [ ] JUnit 5 được cấu hình.
- [ ] Không có Spring Boot.
- [ ] Không có database/framework web.
- [ ] `AGENTS.md` tồn tại.

## Output

Chỉ báo cáo trạng thái.

Không viết thuật toán ở task này.

---

# 7. B1 — RESEARCH CONTRACT

## Goal

Tạo tài liệu mô tả chính xác những gì paper xác nhận, những gì cần suy luận và những gì còn thiếu.

## Suggested File

```text
docs/research/wspm-pretree-spec.md
```

## Nội dung bắt buộc

### Definitions

Ghi lại từ paper:

- item;
- itemset;
- sequence;
- subsequence;
- support;
- sequence database;
- prefix;
- Prefix Tree;
- I-Extension;
- S-Extension;
- item weight;
- itemset weight;
- sequence weight;
- MaxW;
- MinW;
- MeanW;
- weighted sequential pattern.

### Algorithm flow

Mô tả pseudocode WSPM_PreTree theo paper.

### Source classification

Mỗi chi tiết phải được đánh dấu:

- `SOURCE-CONFIRMED`
- `REASONABLE-INFERENCE`
- `UNSPECIFIED`

### Open questions

Đặc biệt lập danh sách câu hỏi về:

- Prime-block Encoding;
- encoding SID;
- encoding position;
- itemset-extension join;
- sequence-extension join;
- support extraction;
- overflow behavior;
- lexicographic constraints.

## Acceptance Criteria

- [ ] Không có Java implementation mới.
- [ ] Công thức toán không bị sửa.
- [ ] Các điểm thiếu của paper được ghi rõ.
- [ ] Prime-block được đánh dấu là dependency từ PRISM.
- [ ] Có bảng "Source / Inference / Unspecified".

---

# 8. B2 — DOMAIN MODEL CHO SEQUENCE DATABASE

## Goal

Thiết kế model dữ liệu Java đủ để biểu diễn chính xác semantics của SPM.

## Suggested concepts

Có thể gồm:

```text
Item
Itemset
Sequence
SequenceDatabase
```

Tên class cụ thể có thể khác nếu repository đã có convention.

## Hard Rules

Phải phân biệt:

```text
<(a)(b)>
```

và:

```text
<(ab)>
```

Không được flatten thành cùng một tập item.

Một item có thể xuất hiện:

- tối đa một lần trong cùng itemset;
- nhiều lần ở các itemset khác nhau của cùng sequence.

## Tests

Phải có test chứng minh:

- itemset boundaries được giữ;
- sequence order được giữ;
- duplicate item trong cùng itemset được xử lý theo policy rõ ràng;
- cùng item xuất hiện ở nhiều itemset không bị mất.

## Acceptance Criteria

- [ ] Model đúng semantics.
- [ ] Có unit tests.
- [ ] Không có mining logic.
- [ ] Không có Prime-block logic.

---

# 9. B3 — SPMF SEQUENCE PARSER

## Goal

Đọc sequence database theo format:

```text
item item -1 item -1 -2
```

Trong đó:

- `-1` kết thúc itemset;
- `-2` kết thúc sequence.

## Required behavior

Parser phải:

- giữ itemset boundary;
- giữ sequence order;
- không tự sort item giữa các itemset;
- không tạo artificial ordering;
- validate malformed input.

## Tests

Tối thiểu:

1. một sequence một itemset;
2. nhiều itemset;
3. nhiều sequences;
4. item lặp qua nhiều itemset;
5. malformed `-1/-2`;
6. empty input.

## Acceptance Criteria

- [ ] Parse đúng.
- [ ] Fail rõ ràng với input sai.
- [ ] Không dùng Scanner runtime để hỏi người dùng.
- [ ] `mvn test` PASS.

---

# 10. B4 — TABLE 1 FIXTURE

## Goal

Tạo dataset test từ Table 1 của paper 2023.

## Table 1

Phải biểu diễn đúng 7 sequence:

```text
SID 1: <a (abc) (ac) (ad) (acf)>
SID 2: <(abd) c (bc) (ae) b c>
SID 3: <(ef) (ab) (df) c b>
SID 4: <e g (af) (abc) b c>
SID 5: <a (abg) (cdg) (eg) g (gh)>
SID 6: <a (abd) b (ac)>
SID 7: <a (ab) a a a a a a>
```

Việc chuyển sang format internal/SPMF phải giữ nguyên semantics.

## Tests

Kiểm tra:

- 7 sequences;
- 8 distinct items;
- length từng sequence theo paper;
- occurrence của item `a`;
- support của `a`;
- support của ví dụ `<(a)(bc)>` bằng 3.

## Acceptance Criteria

- [ ] Fixture tái hiện Table 1.
- [ ] Không hard-code kết quả mining.
- [ ] Các giá trị sanity-check khớp paper.

---

# 11. B5 — ITEM STATISTICS

## Goal

Tính các thống kê cơ sở từ Sequence Database.

## Required metrics

Cho mỗi item:

- total occurrence count;
- sequence support;
- tổng độ dài của các sequence chứa item.

Phải phân biệt:

```text
occurrence count != sequence support
```

Ví dụ item `a` trong một sequence xuất hiện nhiều lần nhưng support trong sequence đó chỉ đóng góp 1.

## Tests

Dùng Table 1.

Kiểm chứng occurrence của `a` theo paper:

```text
5, 2, 1, 2, 2, 3, 8
```

## Acceptance Criteria

- [ ] Item statistics chính xác.
- [ ] Support không bị đếm theo occurrence.
- [ ] Có tests.

---

# 12. B6 — ITEM WEIGHT, MAXW, MINW, MEANW

## Goal

Cài đặt chính xác công thức weight trong paper.

## Item Weight

Phải bám sát công thức paper.

Với Table 1:

```text
W(a) = 23 / 66 ≈ 0.348
W(b) = 12 / 66 ≈ 0.181
W(c) = 11 / 66 ≈ 0.166
W(d) = 5 / 66 ≈ 0.075
W(e) = 4 / 66 ≈ 0.060
W(f) = 4 / 66 ≈ 0.060
W(g) = 6 / 66 ≈ 0.090
W(h) = 1 / 66 ≈ 0.015
```

Sau đó:

```text
MaxW = W(a)
MinW = W(h)
MeanW = (MaxW + MinW) / 2 ≈ 0.181
```

## Hard Rules

Không hard-code Table 2.

Các giá trị phải được tính từ dataset.

## Numerical policy

Phải quyết định và document:

- dùng `double`, `BigDecimal`, hay representation khác;
- tolerance trong test.

Không được làm tròn sớm làm thay đổi quyết định pruning.

## Acceptance Criteria

- [ ] Table 2 được tái hiện từ computation.
- [ ] MeanW đúng trong tolerance đã document.
- [ ] Có tests cho MaxW/MinW/MeanW.

---

# 13. B7 — MINIMUM SUPPORT SEMANTICS

## Goal

Xác định rõ semantics của minsup trước khi dùng vào mining.

## Configuration

Dự kiến:

```java
MIN_SUP
MIN_SUP_IS_PERCENT
```

## Required distinction

Phải phân biệt:

- percentage;
- relative threshold;
- absolute support threshold;
- weighted condition.

Paper dùng điều kiện dạng:

```text
support(P) * MeanW >= minsup
```

Task này phải xác định internal representation phù hợp để không trộn đơn vị.

## Required tests

Các case:

- 14%;
- absolute support;
- boundary equality;
- decimal threshold;
- rounding boundary.

## Hard Rule

Nếu semantics của `14%` trong paper và internal conversion chưa thể chứng minh, không được tự quyết định im lặng.

Phải ghi lại research decision.

## Acceptance Criteria

- [ ] Conversion rule rõ ràng.
- [ ] Có boundary tests.
- [ ] Không dùng `ceil/floor/round` tùy tiện.

---

# 14. B8 — F1 CONSTRUCTION

## Goal

Xây dựng tập weighted sequential 1-patterns.

Theo paper, với Table 1 và `minsup = 14%`:

```text
F1 = {
  <(a)> : 7,
  <(b)> : 7,
  <(c)> : 6
}
```

## Tasks

- tính support từng single item;
- áp điều kiện paper;
- canonical ordering;
- trả về F1.

## Acceptance Criteria

- [ ] F1 khớp paper.
- [ ] Không hard-code `a,b,c`.
- [ ] Pattern và support được lưu cùng nhau hoặc truy xuất deterministic.

---

# 15. B9 — PATTERN MODEL

## Goal

Tạo representation ổn định cho sequential pattern.

Pattern phải biểu diễn được:

```text
<(a)>
<(a)(b)>
<(ab)>
<(a)(bc)(d)>
```

## Requirements

Phải hỗ trợ:

- equality;
- hashCode;
- deterministic serialization;
- itemset boundaries;
- last item;
- last itemset;
- length;
- size.

## Canonical format

Chọn một format ổn định, ví dụ:

```text
<(a)(bc)>
```

hoặc:

```text
[a] -> [b,c]
```

Nhưng phải dùng nhất quán.

## Acceptance Criteria

- [ ] `<(a)(b)> != <(ab)>`.
- [ ] serialization deterministic.
- [ ] thuận tiện differential testing.

---

# 16. B10 — I-EXTENSION VÀ S-EXTENSION

## Goal

Cài đặt chính xác hai phép mở rộng pattern.

## I-Extension

Thêm item vào itemset cuối.

Phải tuân lexicographic constraint theo paper để tránh duplicate.

Ví dụ:

```text
<(a)> +I b -> <(ab)>
```

## S-Extension

Thêm item thành itemset mới.

Ví dụ:

```text
<(a)> +S b -> <(a)(b)>
```

## Tests

Phải test:

- pattern output;
- itemset count;
- item count;
- duplicate prevention;
- lexicographic restriction.

## Acceptance Criteria

- [ ] I/S extension đúng semantics.
- [ ] Không tạo duplicate branch.
- [ ] Không liên quan support computation ở task này.

---

# 17. B11 — PREFIX TREE MODEL

## Goal

Cài đặt cấu trúc Prefix Tree độc lập với Prime-block join.

## Node tối thiểu cần cân nhắc

- pattern;
- parent;
- children;
- extension type;
- support metadata;
- encoding metadata sau này.

## Hard Rules

Root đại diện empty pattern.

Level 1 chứa 1-pattern.

Child phải bảo toàn prefix relationship.

## Tests

- root;
- insert child;
- parent-child;
- depth;
- I-extension child;
- S-extension child;
- deterministic traversal.

## Acceptance Criteria

- [ ] Prefix Tree hoạt động.
- [ ] Chưa cần mining hoàn chỉnh.
- [ ] DFS traversal test được.

---

# 18. B12 — PRISM RESEARCH GATE

## Goal

Trước khi code Prime-block, xác định paper hiện có đủ chi tiết hay không.

WSPM_PreTree paper nói việc xây dựng Prime-block Encoding và join được mô tả chi tiết trong PRISM.

Do đó task này là **research gate bắt buộc**.

## Tasks

1. Đọc phần Prime-block trong WSPM_PreTree paper.
2. Đối chiếu reference PRISM.
3. Kiểm tra repository có PRISM paper/source chưa.
4. Lập danh sách chính xác các primitive cần tái hiện:
   - sequence block;
   - position block;
   - prime assignment;
   - block construction;
   - support extraction;
   - I-extension join;
   - S-extension join.

## Stop condition

Nếu không có đủ thông tin:

```text
STATUS: BLOCKED
REASON: PRISM SOURCE REQUIRED
```

Không được code giả định.

## Acceptance Criteria

Một trong hai:

### PASS

- [ ] đủ nguồn để implement chính xác;

hoặc:

### BLOCKED

- [ ] xác định chính xác tài liệu còn thiếu;
- [ ] không code approximation.

---

# 19. B13 — PRIME-BLOCK PRIMITIVES

> Chỉ thực hiện nếu B12 PASS.

## Goal

Cài đặt primitive structures của Prime-block Encoding.

## Rule

Task này chưa implement toàn bộ miner.

Chỉ tập trung:

- representation;
- block creation;
- primitive operations;
- decoding/support helper nếu paper yêu cầu.

## Tests

Dùng ví dụ nhỏ từ PRISM/WSPM paper nếu có.

## Acceptance Criteria

- [ ] primitive behavior khớp source.
- [ ] có unit tests từ ví dụ paper.
- [ ] numeric limits được document.

---

# 20. B14 — PRIME-BLOCK JOIN VÀ SUPPORT

## Goal

Cài đặt join operations phục vụ:

- I-Extension;
- S-Extension;
- support computation.

## Hard Rules

Không thay thế join bằng brute-force scan rồi coi là final baseline.

Có thể dùng brute force trong test oracle, nhưng không phải implementation của Prime-block baseline.

## Tests

Cho một số pattern nhỏ:

```text
support via Prime-block == support via direct subsequence scan
```

## Acceptance Criteria

- [ ] I-join đúng.
- [ ] S-join đúng.
- [ ] support đúng.
- [ ] differential unit tests với direct scan.

---

# 21. B15 — CANDIDATE EXTENSION ENGINE

## Goal

Kết nối:

- Pattern;
- I/S Extension;
- Prefix Tree;
- Prime-block support.

## Required behavior

Từ một prefix hợp lệ:

1. sinh candidate;
2. tính support bằng Prime-block;
3. áp weighted condition;
4. candidate hợp lệ mới được thêm vào tree.

## Statistics

Bắt đầu định nghĩa rõ:

- generated candidate;
- accepted candidate;
- rejected candidate.

Không double count.

## Acceptance Criteria

- [ ] candidate generation deterministic.
- [ ] support correctness.
- [ ] acceptance condition đúng paper.

---

# 22. B16 — DFS WSPM_PreTree

## Goal

Hoàn thiện recursive/iterative DFS theo paper.

## Flow

Dự kiến:

```text
Build F1
Create root
Attach F1
For each F1 node:
    DFS extend
        I-Extension
        S-Extension
        Prime-block support
        weighted-condition check
        recurse
```

Tên method/class có thể khác.

## Hard Rules

- Không dùng Bitmap.
- Không dùng CMAP.
- Không Lazy Pruning.
- Không optimization của BitPrune.

## Acceptance Criteria

- [ ] DFS hoàn chỉnh.
- [ ] không duplicate patterns.
- [ ] tree/result deterministic.
- [ ] tests pass.

---

# 23. B17 — TABLE 1 END-TO-END REPRODUCTION

## Goal

Đây là checkpoint quan trọng nhất Phase B.

Chạy WSPM_PreTree trên Table 1.

## Must verify

### Input

```text
Sequences = 7
Distinct items = 8
minsup = 14%
```

### Weights

Khớp Table 2.

### MeanW

```text
≈ 0.181
```

### F1

```text
<(a)> : 7
<(b)> : 7
<(c)> : 6
```

### Final result

Paper báo cáo:

```text
9 weighted sequential patterns
```

Nhưng chỉ khớp tổng số là chưa đủ.

Phải xuất:

```text
pattern -> support
```

và đối chiếu từng pattern với Figure 2 / mô tả paper khi dữ liệu cho phép.

## Acceptance Criteria

- [ ] item weights đúng.
- [ ] MeanW đúng.
- [ ] F1 đúng.
- [ ] pattern count đúng.
- [ ] exact pattern/support set được kiểm tra.
- [ ] mọi deviation được ghi lại.

Nếu count đúng nhưng set sai:

```text
FAIL
```

---

# 24. B18 — BRUTE-FORCE ORACLE

## Goal

Tạo oracle độc lập cho tiny datasets.

## Important

Brute force không được tái sử dụng Prime-block support logic.

Nó phải xác định subsequence bằng logic trực tiếp, dễ đọc.

## Purpose

So sánh:

```text
WSPM_PreTree result
vs
BruteForce result
```

## Acceptance Criteria

- [ ] tiny database tests.
- [ ] pattern set equality.
- [ ] support equality.
- [ ] oracle implementation độc lập.

---

# 25. B19 — DIFFERENTIAL CORRECTNESS TESTS

## Goal

Tăng độ tin cậy của baseline trước dataset thật.

## Suggested datasets

Tạo nhiều tiny datasets:

- single sequence;
- repeated item across itemsets;
- multiple items same itemset;
- competing I/S extensions;
- low-support high-weight style cases;
- boundary minsup.

Có thể sử dụng deterministic generated datasets nhỏ.

## Compare

```text
Prime-block WSPM_PreTree
vs
Brute-force oracle
```

## Acceptance Criteria

- [ ] exact pattern equality.
- [ ] exact support equality.
- [ ] regression tests cho bug phát hiện được.

---

# 26. B20 — BASELINE LOGGING VÀ STATISTICS

## Goal

Chuẩn hóa thông tin đầu ra trước Phase C.

## Required terminal sections

### Input Configuration

- Algorithm name.
- Dataset path.
- Number of sequences.
- Number of distinct items.
- Minimum support input.
- Effective threshold.
- MeanW.
- Output flags.

### Mining Statistics

Tối thiểu baseline:

- generated candidates;
- rejected candidates;
- number of weighted sequential patterns.

Các counters BitPrune như:

- pruned by CMAP;
- pruned by Bitmap Upper Bound;

ở baseline phải:

- không tồn tại;

hoặc:

- hiển thị `N/A`;

không giả lập bằng `0` nếu dễ gây hiểu nhầm.

### Time

Phân tách:

1. Dataset I/O.
2. Preprocessing.
3. Mining.
4. Output I/O.
5. Total.

### Memory

Document phương pháp đo.

## Acceptance Criteria

- [ ] mining time không gồm file I/O.
- [ ] counters có định nghĩa.
- [ ] output deterministic.
- [ ] `EXPORT_PATTERNS=false` vẫn in pattern count.

---

# 27. B21 — PHASE B FINAL AUDIT

## Goal

Quyết định baseline đã đủ tin cậy để sang Phase C chưa.

## Review checklist

### Research Fidelity

- [ ] Formula đúng paper.
- [ ] Prefix Tree đúng.
- [ ] I/S Extension đúng.
- [ ] Prime-block đúng PRISM.
- [ ] Không dùng BitPrune optimization.

### Table 1

- [ ] Weights đúng.
- [ ] MeanW đúng.
- [ ] F1 đúng.
- [ ] Pattern/support set đúng.

### Testing

- [ ] Unit tests.
- [ ] Paper reproduction.
- [ ] Brute force.
- [ ] Differential tests.
- [ ] Regression tests.

### Engineering

- [ ] `mvn clean test` PASS.
- [ ] deterministic output.
- [ ] configuration rõ.
- [ ] logging rõ.
- [ ] deviations documented.

### Research documentation

- [ ] assumptions.
- [ ] unspecified points.
- [ ] PRISM interpretation.
- [ ] numeric limitations.
- [ ] minsup conversion.
- [ ] known limitations.

## PASS condition

Chỉ đánh dấu:

```text
PHASE B: PASS
```

nếu tất cả critical criteria đạt.

Nếu còn lỗi correctness:

```text
PHASE B: FAIL
```

Nếu chỉ còn vấn đề không ảnh hưởng correctness nhưng cần ghi chú:

```text
PHASE B: PARTIAL
```

và liệt kê chính xác blocker.

Không tự động bắt đầu Phase C.

---

# 28. Quy trình thực hiện mỗi task

Đối với từng task Bx:

## 1. Read

Đọc:

- `AGENTS.md`;
- file Phase B này;
- code hiện tại;
- tests hiện tại;
- phần paper liên quan.

## 2. Summarize understanding

Trước khi sửa code, báo cáo:

```text
Task:
Goal:
Relevant paper rule:
Files likely affected:
Unknowns:
```

## 3. Mini-plan

Tối đa khoảng 3–7 bước.

## 4. Implement

Chỉ sửa phạm vi task.

## 5. Self-review

Kiểm tra:

- paper fidelity;
- correctness;
- unit boundaries;
- scope;
- naming;
- deterministic behavior.

## 6. Test

Tối thiểu:

```bash
mvn test
```

Nếu task thay đổi core algorithm:

```bash
mvn clean test
```

## 7. Report

Dùng format sau.

---

# 29. Mẫu báo cáo bắt buộc sau mỗi task

```markdown
## Task Result

**Task:** Bx — Task Name

**Status:** PASS / PARTIAL / FAIL / BLOCKED

### Understanding

...

### Implemented

- ...

### Files Changed

- ...

### Tests Executed

```text
mvn test
...
```

### Test Results

- ...

### Paper / Research Fidelity

#### Source-confirmed
- ...

#### Reasonable inference
- ...

#### Unspecified
- ...

### Acceptance Criteria

- [x] ...
- [ ] ...

### Risks / Deviations

- ...

### Blockers

- ...

### Recommended Next Task

Bx+1 — ...

**WAITING FOR USER APPROVAL**
```

Không tự thực hiện `Recommended Next Task`.

---

# 30. Điều kiện dừng toàn Phase B

AI phải dừng ngay nếu gặp một trong các tình huống:

1. Không đủ tài liệu để tái hiện Prime-block.
2. Code hiện tại dùng Bitmap nhưng gọi là WSPM_PreTree.
3. Support tính khác paper.
4. MeanW/F1 không khớp Table 1.
5. Table 1 final patterns khác paper.
6. Brute-force và baseline khác nhau.
7. Candidate bị mất do optimization không có trong paper.
8. Dataset semantics không chắc chắn.
9. Minsup conversion không rõ.
10. Agent định sửa sang WSPM_BitPrune trước khi Phase B PASS.

Báo cáo blocker thay vì tự suy đoán.

---

# 31. Definition of Done cho Giai đoạn B

Phase B chỉ hoàn tất khi có thể trả lời bằng bằng chứng cho các câu hỏi sau:

1. Sequence database được biểu diễn đúng chưa?
2. Parser giữ nguyên itemset boundaries chưa?
3. Item occurrence và support khác nhau đúng chưa?
4. Item weight khớp Table 2 chưa?
5. MaxW/MinW/MeanW đúng chưa?
6. `minsup=14%` được hiểu đúng chưa?
7. F1 có chính xác `a,b,c` chưa?
8. I-Extension có đúng semantics chưa?
9. S-Extension có đúng semantics chưa?
10. Prefix Tree đúng parent-child chưa?
11. Prime-block Encoding có thực sự là PRISM không?
12. Prime-block join tính đúng support không?
13. DFS có sinh đủ và không sinh trùng không?
14. Table 1 có cho đúng toàn bộ pattern/support không?
15. Brute-force có xác nhận baseline không?
16. `mvn clean test` có PASS không?
17. Có tài liệu đầy đủ về assumptions/deviations không?

Nếu một câu critical chưa trả lời được:

```text
PHASE B IS NOT COMPLETE
```

---

# 32. Task bắt đầu

Sau khi đọc file này và `AGENTS.md`, AI chỉ được bắt đầu:

```text
B0 — AUDIT REPOSITORY
```

Không triển khai B1 hoặc các task sau trong cùng một lần.

Sau khi B0 hoàn thành, báo cáo kết quả theo template và:

```text
WAITING FOR USER APPROVAL
```
