# C06.R0 — Numeric Comparator Research Policy

**Status:** `PROPOSED — PENDING RESEARCH REVIEW / OWNER APPROVAL`  
**Task:** C06.R0 — Numeric Comparator Research Gate  
**Repository:** `wspm-pretree-bitprune`  
**Date:** 2026-10-29  
**Authorization:** Research-only; tài liệu này không cấp quyền triển khai C06.1/C06.2.

---

## 1. Mục tiêu và ranh giới

Tài liệu này nghiên cứu chính sách so sánh an toàn cho tầng Bitmap Candidate Upper-Bound:

```text
upperBoundSupport = candidateBitmap.cardinality()
upperBoundWSUP = upperBoundSupport × globalMeanW
canPrune iff upperBoundWSUP < effectiveMinSup
```

Mục tiêu an toàn tối thiểu là không loại một candidate mà chuẩn ngữ nghĩa đã được Repository Owner lựa chọn vẫn chấp nhận.

Tài liệu này:

- phân biệt số thực toán học, Java `double`, và frozen baseline operational acceptance;
- chứng minh phạm vi an toàn của Option A;
- đánh giá khả năng exact/rational của Option B;
- xác định điều kiện để Option C thực sự là certified;
- thiết kế oracle và ca kiểm thử cho C06.1/C06.2;
- giữ R6 ở trạng thái `OPEN` và quy định C06 là no-op pruning tại threshold 0.

Tài liệu này không:

- sửa công thức `MeanW`, support, WSUP hay threshold;
- chọn epsilon;
- làm tròn threshold;
- sửa frozen baseline;
- viết Java/JUnit;
- triển khai hoặc cấp quyền triển khai C06;
- giải quyết semantics liệt kê support-zero pattern của R6.

---

## 2. Nguồn và phân loại bằng chứng

### 2.1. SOURCE-CONFIRMED

Theo WSPM_PreTree 2023 và các contract đã được chấp nhận:

```text
w(i) = occurrenceCount(i) / totalDatabaseItemOccurrences
MeanW = (MaxW + MinW) / 2
support(P) = distinct actual supporting SID count
WSUP(P) = support(P) × MeanW
accept(P) iff WSUP(P) >= effectiveMinSup
effectiveMinSup = relativeFraction × sequenceCount
```

Không có `Math.ceil`, `floor`, `round` hay arbitrary epsilon trong các công thức này.

### 2.2. C01/C02-ACCEPTED CONTRACT

1. Candidate bitmap là điều kiện cần:

   ```text
   SupportingSIDs(P) ⊆ CandidateBitmapSIDs(P)
   support(P) ≤ cardinality(BM(P))
   ```

2. Trên số thực và với `MeanW >= 0`:

   ```text
   cardinality(BM(P)) × MeanW < effectiveMinSup
   ```

   là điều kiện đủ để reject candidate và subtree.

3. Tuyệt đối không prune tại equality.
4. Java `double` comparator chưa được C01/C02 phê duyệt.
5. Tại `effectiveMinSup == 0`, C06 không được operational prune.
6. Bitmap presence/cardinality không thay exact temporal verification.

### 2.3. IMPLEMENTATION-CONFIRMED

Hiện trạng frozen/shared API:

- `ItemWeightCalculator` tính mỗi item weight và `MeanW` bằng `double`.
- `ItemWeightResult` chỉ lưu các weight và `MaxW/MinW/MeanW` dạng `double`; không giữ numerator/denominator gốc.
- `MinimumSupport` chỉ giữ `configuredValue` dạng `double`; lexical decimal provenance không còn.
- `MinimumSupportResolver` tính percentage threshold bằng:

  ```java
  (configuredValue / 100.0) * numberOfSequences
  ```

- `ResolvedMinimumSupport.meetsThreshold` kiểm tra:

  ```java
  support * meanWeight >= effectiveThreshold
  ```

  sau khi validate support và `meanWeight`; không dùng epsilon.

Java 21 Language Specification §15.4 và §15.17.1 quy định biểu thức floating-point được đánh giá nghiêm ngặt, phép nhân finite trước hết tạo exact mathematical product rồi làm tròn tới giá trị biểu diễn gần nhất theo `roundTiesToEven`, có gradual underflow; overflow tạo infinity và bản thân toán tử `*` không ném runtime exception. `ResolvedMinimumSupport` bổ sung validation riêng để từ chối weighted result không finite. Nguồn: <https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17.1>.

### 2.4. UNRESOLVED

Owner/Research Reviewer chưa chọn chuẩn bắt buộc cho C06 giữa:

1. exact-real mathematical semantics;
2. frozen baseline Java `double` operational semantics;
3. một dual-safe conservative policy bảo toàn cả hai.

Tài liệu này đưa ra recommendation nhưng recommendation chưa có hiệu lực cho implementation.

---

## 3. Bốn miền giá trị phải phân biệt

### 3.1. Exact source-real value

Đây là giá trị toán học suy ra từ occurrence counts và threshold input trước rounding nhị phân. Ví dụ:

```text
MeanW_exact = (maxOccurrence + minOccurrence) / (2 × totalOccurrences)
```

Nếu percentage token là decimal chính xác `p`, thì:

```text
threshold_exact = (p / 100) × sequenceCount
```

### 3.2. Stored Java operands

`meanW` và `effectiveThreshold` hiện được truyền dưới dạng IEEE 754 binary64. Mỗi finite `double` tự nó biểu diễn chính xác một số hữu tỷ nhị phân, nhưng số đó có thể khác exact source-real value.

### 3.3. Rounded Java product

Frozen baseline dùng kết quả rounded của phép nhân binary64:

```text
weightedDouble(s) = roundBinary64(s × meanW_double)
```

Sau đó so sánh với `threshold_double`.

### 3.4. Operational acceptance

Frozen baseline chấp nhận support `s` khi:

```text
weightedDouble(s) >= threshold_double
```

Operational equality là equality giữa hai giá trị `double`, không tự động tương đương exact-real equality.

---

## 4. Proof obligations chung

Một comparator chỉ được phê duyệt nếu discharge rõ các nghĩa vụ sau.

| ID | Nghĩa vụ |
|---|---|
| CMP-01 | Với mọi `0 <= support <= bitmapCardinality`, nếu filter prune thì candidate không được chuẩn acceptance đã chọn chấp nhận. |
| CMP-02 | Equality theo chuẩn đã chọn không được prune. |
| CMP-03 | Không coi candidate bitmap cardinality là exact temporal support. |
| CMP-04 | Nonfinite input không được chuyển thành quyết định prune. |
| CMP-05 | Threshold 0 luôn trả về no-op pruning; R6 không bị thay đổi. |
| CMP-06 | Không dùng arbitrary fixed/relative epsilon. |
| CMP-07 | Oracle phải độc lập với production comparator. |
| CMP-08 | Mọi claim exact-real phải có operand provenance hoặc certified enclosure. |
| CMP-09 | Repeated items và temporal false positives chỉ làm cận lỏng; không làm mất upper-bound inclusion. |
| CMP-10 | Candidate rejection và subtree rejection phải được phân biệt trong API/integration documentation. |

---

## 5. Option A — Baseline-relative comparator

### 5.1. Quy tắc đề xuất

```text
canPruneBaselineRelative(c, meanW, minSup)
    = !minSup.meetsThreshold(c, meanW)
```

Trong đó `c = candidateBitmap.cardinality()`.

Không được viết lại predicate bằng một phép tính khác nếu mục tiêu là bit-for-bit parity với frozen baseline.

### 5.2. Semantics được bảo toàn

Option A bảo toàn:

> Không prune candidate nào mà frozen baseline operational acceptance có thể chấp nhận với một exact support `s` thỏa `0 <= s <= c`, với điều kiện các input hợp lệ và phép gọi không ném exception.

Option A không tự bảo toàn exact source-real semantics.

### 5.3. Chứng minh tính đơn điệu theo cardinality

Giả sử:

```text
0 <= s <= c <= numberOfSequences
meanW_double >= 0 và finite
```

Trên số thực:

```text
s × meanW_double <= c × meanW_double
```

Theo Java 21 JLS §15.4/§15.17.1, phép nhân finite dùng exact mathematical product rồi `roundTiesToEven`. Phép ánh xạ làm tròn-nearest này đơn điệu: nếu `x <= y` thì `round(x) <= round(y)`. Vì vậy:

```text
weightedDouble(s) <= weightedDouble(c)
```

Nếu Option A prune thì:

```text
weightedDouble(c) < threshold_double
```

Suy ra với mọi `s` trong `[0, c]`:

```text
weightedDouble(s) <= weightedDouble(c) < threshold_double
```

Do đó frozen baseline cũng reject mọi exact support khả dĩ của candidate. Option A không tạo false negative tương đối với frozen baseline operational acceptance. QED.

### 5.4. Preconditions cần được giữ trong C06.1

- `candidateBitmap != null`.
- `c` nằm trong `[0, minSup.numberOfSequences()]`.
- `meanW` finite và không âm.
- `effectiveThreshold` đã được `MinimumSupport`/resolver bảo đảm finite và không âm theo nghĩa số học; `-0.0` được xem là zero.
- Không bắt exception từ `meetsThreshold` rồi chuyển thành `true`.
- Nếu phép nhân nonfinite, fail-fast hoặc fail-open; tuyệt đối không prune.

Với provenance hợp lệ của repository, `MeanW` thuộc `[0, 1]` và `c <= Integer.MAX_VALUE`, nên `c × MeanW` không overflow. Overflow chỉ có thể xuất hiện từ synthetic/out-of-contract input.

### 5.5. Equality và near-equality

- Nếu rounded product bằng `effectiveThreshold`, `meetsThreshold` trả `true`; Option A không prune.
- `Math.nextDown(threshold)` là just-below theo binary64 và bị reject.
- `Math.nextUp(threshold)` là just-above theo binary64 và được giữ.
- Không thêm epsilon vì epsilon sẽ thay đổi frozen predicate và không có scale-independent proof.

### 5.6. Signed zero

- `+0.0` và `-0.0` so sánh bằng nhau bằng `==` và đều không nhỏ hơn zero theo điều kiện validation hiện hữu.
- Với threshold zero, mọi nonnegative weighted result, kể cả `-0.0`, đều thỏa `>= threshold` theo Java comparison; Option A trả `false`.
- C06.1 nên kiểm tra threshold zero bằng comparison số học, không dựa vào raw sign bit.

### 5.7. NaN, infinity và overflow

- `meanW = NaN`, `+Infinity`, `-Infinity`, hoặc âm: `meetsThreshold` ném `IllegalArgumentException`.
- `effectiveThreshold` nonfinite không thể được tạo bởi public `MinimumSupport` constructor hợp lệ.
- Nếu synthetic `meanW` finite nhưng `c × meanW` overflow thành infinity, `meetsThreshold` ném `IllegalArgumentException`.
- Exception không phải quyết định prune; caller không được map exception thành `canPrune = true`.

### 5.8. Exact-real counterexample chống khái quát hóa Option A

Xét:

```text
MeanW_exact = MeanW_double = 1/2
bitmapCardinality = exactSupport = 7
sequenceCount = 25
configured percentage = 14%
```

Trên số thực:

```text
upperBoundWSUP = 7 × 1/2 = 3.5
threshold = 14/100 × 25 = 3.5
```

Candidate đạt equality và phải được chấp nhận.

Theo expression hiện hữu của Java:

```text
(14.0 / 100.0) × 25 = 3.5000000000000004
7 × 0.5 = 3.5
```

Frozen baseline reject; Option A cũng prune. Đây không phải false negative tương đối với baseline, nhưng là false negative tương đối với exact source-real semantics.

Kết luận bắt buộc: baseline parity không được trình bày như exact-real correctness.

### 5.9. Đánh giá Option A

**Ưu điểm:**

- Có proof no-false-negative tương đối với frozen baseline.
- Khớp cross-algorithm correctness oracle hiện hành.
- Không cần API/provenance mới.
- Equality, threshold zero và validation được kế thừa từ một predicate duy nhất.
- Chi phí thấp và deterministic.

**Giới hạn:**

- Có thể lệch exact-real semantics khi operands đã rounded.
- Không khôi phục decimal provenance.
- Safety claim phải luôn ghi rõ “baseline-relative”.

---

## 6. Option B — Exact/rational arithmetic

### 6.1. MeanW rational chính xác

Với:

```text
maxOccurrence = maxOcc
minOccurrence = minOcc
totalDatabaseItemOccurrences = totalOcc
```

ta có:

```text
MeanW_exact = (maxOcc + minOcc) / (2 × totalOcc)
```

Các toán hạng là số nguyên. Có thể lưu numerator/denominator dưới dạng `long` khi chứng minh không overflow, hoặc `BigInteger` để tránh giả định giới hạn.

### 6.2. Threshold rational chính xác

Nếu configured percentage được bảo toàn như decimal token `unscaled × 10^-scale`:

```text
threshold_exact = unscaled × sequenceCount / (100 × 10^scale)
```

Absolute decimal threshold tương tự:

```text
threshold_exact = unscaled / 10^scale
```

So sánh có thể thực hiện bằng cross multiplication với `BigInteger`, không cần division và không cần epsilon.

### 6.3. Provenance bị thiếu trong API hiện tại

Option B không thể khôi phục exact source values một cách tổng quát từ API hiện tại vì:

- `ItemWeightResult` không giữ `maxOcc`, `minOcc`, `totalOcc`.
- `MinimumSupport` không giữ lexical decimal token hoặc `BigDecimal` gốc.
- Nhiều source decimals hoặc computed values có thể map vào cùng một `double`.
- `BigDecimal.valueOf(double)` chỉ tạo decimal canonical round-trippable của giá trị `double`; nó không chứng minh đó là decimal input ban đầu.
- `new BigDecimal(double)` khôi phục chính xác số hữu tỷ nhị phân đang lưu, không khôi phục exact source-real decimal.

### 6.4. Scope/API cần bổ sung nếu Owner chọn Option B

Tối thiểu cần một immutable numeric provenance model, ví dụ ở mức thiết kế:

```text
ExactMeanWeight(maxOccurrence, minOccurrence, totalOccurrences)
ExactMinimumSupport(decimalConfigurationProvenance, mode, sequenceCount)
```

hoặc một `Rational`/`BigRational` nội bộ C06 với factories nhận raw provenance.

Điều này mở rộng phạm vi ngoài proposed C02 API `evaluate(SidBitmap, double, ResolvedMinimumSupport)` và cần Owner/Reviewer phê duyệt trước. Không được âm thầm sửa shared/frozen classes.

### 6.5. Compatibility với frozen baseline

Exact rational acceptance có thể khác frozen `double` acceptance tại boundary, như counterexample 14% × 25 ở Mục 5.8. Nếu dùng Option B đơn độc để prune:

- có thể giữ candidate mà baseline reject: an toàn nhưng giảm pruning;
- hoặc exact-real reject trong khi baseline accept do rounding theo hướng ngược: có thể phá baseline parity nếu prune.

Vì cross-algorithm rule yêu cầu output giống frozen baseline, Option B đơn độc không được phê duyệt trước khi có policy xử lý mọi disagreement. Sửa baseline để khớp Option B bị cấm trong C06.

### 6.6. Đánh giá Option B

**Ưu điểm:** exact-real proof rõ ràng nếu provenance đầy đủ; reproducible; không epsilon.

**Giới hạn:** provenance hiện thiếu; API expansion đáng kể; chi phí `BigInteger`; có nguy cơ khác frozen baseline; vượt file scope C06.1 dự kiến.

---

## 7. Option C — Certified conservative comparison

### 7.1. Định nghĩa certified

Một interval/bound chỉ được gọi là certified nếu chứng minh được:

```text
trueUpperBoundWSUP ∈ [ubLower, ubUpper]
trueThreshold ∈ [thetaLower, thetaUpper]
```

Khi đó chỉ được prune nếu:

```text
ubUpper < thetaLower
```

Nếu hai interval overlap hoặc không tạo được enclosure, phải fail-open:

```text
canPrune = false
```

`Math.nextDown`/`Math.nextUp` bao quanh một kết quả `double` không tự động bao quanh exact source-real value nếu không có proof về toàn bộ chuỗi rounding và input provenance.

### 7.2. Dual-gate conservative policy

Nếu Owner yêu cầu bảo toàn đồng thời baseline operational semantics và exact-real semantics, policy bảo thủ phải có ít nhất hai gate:

```text
baselineRejectsUpperBound
AND
certifiedExactRealRejectsUpperBound
```

Chỉ khi cả hai đúng mới prune. Nếu exact provenance không có, gate thứ hai là unknown và kết quả phải là no-prune.

### 7.3. Certified đối với stored dyadic operands

Có thể giải mã chính xác finite binary64 thành rational dyadic và so sánh:

```text
c × exactDyadic(meanW_double) < exactDyadic(threshold_double)
```

Điều này certified đối với stored double operands trước multiplication rounding, nhưng vẫn không certified đối với original decimal/source-real values. Claim phải ghi đúng phạm vi này.

### 7.4. Đánh giá Option C

**Ưu điểm:** có thể fail-open; có thể bảo toàn nhiều chuẩn cùng lúc; boundary không chắc chắn không gây false prune.

**Giới hạn:** muốn exact-source certification vẫn cần provenance của Option B; phức tạp hơn; có thể giảm mạnh pruning; cần result state `PRUNE / KEEP / UNKNOWN` hoặc quy ước unknown → keep.

---

## 8. Comparator Decision Matrix

| Tiêu chí | Option A — Baseline-relative | Option B — Exact/rational | Option C — Certified conservative |
|---|---|---|---|
| Mathematical exact-real correctness | Không bảo đảm tổng quát | Có, nếu provenance đầy đủ | Có, nếu enclosure chứa true source values |
| Frozen baseline compatibility | Cao; cùng predicate | Có thể khác tại boundary | Cao nếu baseline gate bắt buộc |
| False-negative risk so với baseline | Đã chứng minh bằng monotonicity | Có nếu dùng đơn độc và disagreement | Không nếu baseline gate + fail-open đúng |
| False-negative risk so với exact-real | Có counterexample | Không với exact provenance | Không với certified source enclosure |
| Equality | Giữ theo rounded baseline equality | Giữ exact equality | Giữ khi interval overlap/equality |
| Threshold 0 | No-op tự nhiên | Phải quy định no-op | No-op/fail-open |
| Arbitrary epsilon | Không | Không | Không |
| API changes | Không đáng kể | Lớn | Trung bình đến lớn |
| Runtime cost | O(1), thấp | BigInteger/rational | O(1) đến rational/interval |
| Reproducibility | Cao theo JVM predicate | Cao khi provenance được lưu | Cao nếu bounds/provenance được ghi |
| C06.1 readiness hiện tại | Có thể, sau Owner approval | Chưa; thiếu provenance | Chưa nếu yêu cầu source-real certification |

---

## 9. Recommendation đề xuất

### 9.1. Recommendation chính

Đề xuất Owner chọn **Option A** làm policy operational của C06 với tên và claim tường minh:

> `BASELINE_RELATIVE_MONOTONE_UPPER_BOUND`

Lý do:

1. Phase C bắt buộc full output parity với frozen WSPM_PreTree.
2. Option A có proof no-false-negative đối với chính acceptance predicate của baseline.
3. Không cần sửa baseline, accepted C01/C02 contracts, hoặc shared numeric APIs.
4. No-op threshold 0 phát sinh tự nhiên từ inclusive baseline predicate.
5. Option A không dùng epsilon hoặc rounding mới.

### 9.2. Giới hạn bắt buộc đi kèm approval

Nếu Option A được duyệt:

- mọi Javadoc/report phải ghi “safe relative to frozen baseline Java `double` acceptance”;
- không được tuyên bố comparator exact-real safe;
- C06 differential tests phải kiểm tra mọi `support ∈ [0, cardinality]` bằng frozen predicate;
- exact-rational audit phải chạy song song trên deterministic boundary catalogue để phát hiện disagreement;
- khi exact-real và baseline disagree, test phải ghi nhận classification, không sửa expected value để che xung đột;
- performance claim không được dùng comparator policy để tuyên bố numerical exactness.

### 9.3. Khi nào phải chọn Option C thay vì A

Nếu Owner yêu cầu C06 đồng thời không prune candidate được exact-real semantics chấp nhận, Option A không đủ. Khi đó phải chọn dual-gate Option C và cấp quyền bổ sung exact provenance theo Option B. C06.1 tiếp tục BLOCKED cho đến khi scope/API này được duyệt.

---

## 10. Numerical boundary and counterexample catalogue

### NB-01 — Operational equality

```text
c = 2, meanW = 0.5, threshold = 1.0
weightedDouble(c) = 1.0
```

Expected: không prune.

### NB-02 — Just below/above binary64 threshold

Với finite positive `theta`:

```text
below = Math.nextDown(theta)
above = Math.nextUp(theta)
```

- product `below`: baseline-relative prune;
- product `theta`: keep;
- product `above`: keep.

Test phải tạo operands có kết quả product đã xác định; không giả định mọi giá trị `below` có thể biểu diễn thành `c × meanW` với một `c` cụ thể.

### NB-03 — Non-binary-exact decimal exact equality disagreement

Counterexample Mục 5.8:

```text
c = 7, meanW = 0.5, N = 25, percentage = 14
real: 3.5 == 3.5
double: 3.5 < 3.5000000000000004
```

Expected classification:

- Option A: prune, baseline-relative safe;
- exact-real: không được prune;
- chứng minh hai semantics không tương đương.

### NB-04 — Empty candidate bitmap

```text
c = 0
```

- positive threshold: Option A prune; mọi `support` chỉ có thể là 0 và baseline reject;
- zero threshold: không prune do inclusive `0 >= 0` và R6.

### NB-05 — MeanW zero

- positive threshold: mọi support có WSUP 0; baseline-relative prune.
- zero threshold: no-op pruning.
- non-empty valid repository database thực tế có present items nên `MeanW > 0`; zero là synthetic method-level boundary hoặc trạng thái không có valid MeanW.

### NB-06 — Very small positive threshold / `Double.MIN_VALUE`

```text
threshold = Double.MIN_VALUE
```

- `c = 0`: product 0, prune.
- `c = 1`, `meanW = Double.MIN_VALUE`: equality, keep.
- các support/cardinality cao hơn: kiểm tra monotonicity và subnormal rounding.

### NB-07 — Large finite boundaries và overflow

- Với valid `MeanW <= 1` và `c <= Integer.MAX_VALUE`, product luôn finite.
- Synthetic `meanW = Double.MAX_VALUE`, `c >= 2` làm overflow; expected là exception/fail-open, không prune.
- Absolute threshold `Double.MAX_VALUE` là finite và hợp lệ theo constructor; valid candidate products nhỏ hơn nó nên baseline-relative reject. Test phải xác nhận không overflow trong threshold storage.

### NB-08 — Nonfinite inputs

`NaN`, infinities và negative meanW phải bị từ chối. Không được trả `true`.

### NB-09 — Signed zero

Kiểm tra `+0.0` và `-0.0` cho cả meanW/threshold. Expected numerical semantics giống zero; threshold-zero pruning vẫn no-op.

### NB-10 — Repeated items

`BM(<(a)(a)>) = BM(a)` có thể làm cardinality lớn hơn exact support. Test phải chứng minh:

```text
exactSupport <= cardinality
```

và mọi exact support trong khoảng đều được oracle xét.

### NB-11 — Temporal false positive

Candidate bitmap có thể có cardinality 1 trong khi exact support 0 do thứ tự ngược hoặc same-itemset mismatch. Upper bound vẫn an toàn; filter không được gọi đây là exact rejection evidence.

### NB-12 — Table 1

```text
MeanW_exact = 12/66 = 2/11
threshold_exact = 14/100 × 7 = 0.98
```

Existing double code tạo cùng support cutoff thực tế:

- support 5: dưới threshold;
- support 6: trên threshold.

Table 1 không phải equality stress case cho weighted decision; nó chỉ xác nhận current formula/provenance path.

### NB-13 — SIGN

Existing independent numeric audit ghi:

```text
sequenceCount = 730
MeanW_exact = 681/75916
threshold_exact = 73/25 = 2.92
configured percentage = 0.4%
```

Exact rational và current double predicate đều có first accepted support bằng 326:

```text
support 325 -> rejected
support 326 -> accepted
```

Đây là evidence agreement cho một dataset/boundary cụ thể, không phải proof tổng quát rằng double và exact-real luôn tương đương.

---

## 11. Independent oracle design

### 11.1. Oracle O-A — Frozen baseline operational oracle

Oracle gọi trực tiếp accepted predicate:

```text
baselineAccepts(s) = minSup.meetsThreshold(s, meanW)
```

Với mỗi case và `c`:

```text
expectedCanPrune = for all s in [0, c]: !baselineAccepts(s)
```

Production filter không được dùng vòng lặp này; oracle dùng exhaustive enumeration để độc lập với shortcut monotonicity.

### 11.2. Oracle O-B — Exact rational oracle

Tạo rational từ raw counts và lexical decimal provenance trong test fixture, không từ production doubles:

```text
exactAccepts(s) = s × MeanW_exact >= threshold_exact
```

Oracle này phát hiện disagreement giữa exact-real và baseline operational semantics.

### 11.3. Oracle O-C — Direct structural support oracle

Trên tiny sequence databases:

1. Tạo candidate bitmap bằng independent `Set<Integer>` intersection.
2. Tính exact temporal support bằng direct subsequence scan, không dùng bitmap/filter logic.
3. Xác nhận `exactSupport <= bitmapCardinality`.
4. Nếu production filter prune, kiểm tra acceptance theo semantics đã được Owner duyệt.

### 11.4. Exhaustive domain

Tối thiểu enumerate:

- `sequenceCount` nhỏ, ví dụ 0–8;
- mọi `cardinality` trong `[0, sequenceCount]`;
- mọi exact support trong `[0, cardinality]`;
- deterministic `meanW/threshold` catalogue, gồm zero, subnormal, exact binary, non-binary decimal, nextDown/nextUp và large finite;
- fixed-seed randomized cases ghi rõ seed/trial count.

Invariant kiểm thử chính:

```text
canPrune(c) => for every s in [0, c], selectedAcceptance(s) == false
```

---

## 12. Proposed C06.1 behavior contract nếu Option A được duyệt

Đây chỉ là draft behavior, không phải execution authorization.

1. Validate non-null bitmap/minimum support.
2. Validate bitmap universe size khớp `minSup.numberOfSequences()`.
3. Validate finite, nonnegative global MeanW.
4. Nếu `effectiveThreshold == 0.0`, trả `false` và không giải quyết R6 ngoài tầng filter.
5. Đánh giá duy nhất bằng frozen predicate tại bitmap cardinality.
6. Trả `true` chỉ khi predicate trả false.
7. Không compute exact support.
8. Không dùng CMAP, endpoints, PRISM hoặc Prefix Tree.
9. Exception/unknown không được chuyển thành prune.
10. Javadoc giới hạn safety claim thành baseline-relative.

---

## 13. Research Reviewer / Owner questions

Các quyết định sau phải có phê duyệt rõ trước C06.1:

1. Chuẩn bắt buộc của C06 là frozen baseline operational semantics hay exact source-real semantics?
2. Có chấp nhận recommendation Option A với claim giới hạn `BASELINE_RELATIVE_MONOTONE_UPPER_BOUND` không?
3. Nếu chọn Option A, có yêu cầu exact-rational audit chỉ để phát hiện/report disagreement, không điều khiển pruning không?
4. Counterexample 14% × 25 có được chấp nhận như documented baseline-vs-real limitation không, hay bắt buộc dual-safe Option C?
5. Có phê duyệt việc gọi/delegate trực tiếp `ResolvedMinimumSupport.meetsThreshold(cardinality, meanW)` để tránh duplicate comparator semantics không?
6. C06.1 nên fail-fast hay fail-open đối với synthetic product overflow? Dù chọn cách nào, `canPrune=true` bị cấm.
7. Có canonicalize `-0.0` hay chỉ xử lý bằng numerical equality với zero?
8. Có bắt buộc result object chứa `cardinality` và computed rounded upper bound để audit, hay giữ API boolean theo C02?
9. Nếu yêu cầu exact-real/dual safety, Owner có cấp quyền mở rộng API để giữ raw occurrence và decimal provenance không?
10. Xác nhận threshold 0 chỉ làm C06 no-op; R6 enumeration semantics tiếp tục `OPEN` và nằm ngoài C06.R0/C06.1.

---

## 14. Acceptance status của C06.R0 draft

- [x] Phân biệt real-number, binary64 stored operands, rounded product và baseline acceptance.
- [x] Chứng minh Option A monotone và baseline-relative no-false-negative.
- [x] Cung cấp counterexample bác bỏ việc đồng nhất Option A với exact-real safety.
- [x] Phân tích NaN/infinity, signed zero, overflow và equality.
- [x] Phân tích exact rational provenance và giới hạn API hiện hữu.
- [x] Không coi `BigDecimal.valueOf(double)` là khôi phục source value.
- [x] Định nghĩa điều kiện bắt buộc để Option C được gọi là certified.
- [x] Thiết kế independent oracle và exhaustive support interval test.
- [x] Bao phủ empty bitmap, zero MeanW/threshold, subnormal, large finite, repeated items và temporal false positives.
- [x] Đối chiếu Table 1 và SIGN.
- [x] Không dùng epsilon, `Math.ceil`, hoặc thay đổi threshold semantics.
- [x] Không giải quyết R6.
- [ ] Comparator policy được Research Reviewer chấp nhận.
- [ ] Comparator policy được Repository Owner phê duyệt.
- [ ] C06.1 được phép triển khai.

---

## 15. STOP confirmation

C06.R0 dừng ở mức policy draft. Không có production code, JUnit, baseline behavior, accepted C01/C02 contract hay research issue nào bị thay đổi. C06 vẫn `NOT STARTED`.

**STOP — chờ Research Reviewer thẩm định C06.R0 và Repository Owner phê duyệt comparator policy trước khi lập hợp đồng triển khai C06.1.**
