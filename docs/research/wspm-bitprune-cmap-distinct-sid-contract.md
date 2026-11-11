# CMAP đếm distinct SID — hợp đồng và tiêu chí kiểm chứng

Ngày: 2026-11-11.

**Trạng thái:** Đã triển khai và kiểm chứng module ngày 2026-11-11 (mục 11).
Đây là phần mở rộng có chủ đích so với
CMAP boolean hiện hành, không thay thế hồi tố các hợp đồng hoặc nghiệm thu Phase C.
Mục 1–9 là hợp đồng; mục 10 bổ sung kế hoạch sửa mã và kiểm thử theo yêu cầu
tiếp theo; mục 11 ghi bằng chứng thực thi. Chưa có independent research acceptance
riêng cho thay đổi này.

## 1. Phạm vi và nguồn

Phạm vi: ngữ nghĩa CMAP-I/S, cận trên pair support, chính sách ngưỡng, thống kê
và điều kiện nghiệm thu. Không thay đổi baseline WSPM_PreTree, Prime-block,
trọng số, sinh ứng viên, thứ tự DFS, exact verifier, backend, frontend hoặc DB.

| Nguồn | Bằng chứng và giới hạn |
|---|---|
| [WSPM_PreTree 2023](../Mining_weighted_sequential_patterns_based_on_prefix_tree_and_prism_encoding_2023.pdf), trang in 360 (PDF trang 4), 365–366 | CMAP được giới thiệu trong tổng quan CM-SPADE, dẫn nguồn số 13. Không phải bước CMAP của thuật toán WSPM_PreTree được đề xuất. |
| [Fournier-Viger et al., PAKDD 2014](https://www.philippe-fournier-viger.com/spmf/PAKDD2014_sequential_pattern_mining_CM-SPADE_CM-SPAM.pdf), mục 3.1–3.3, trang 46–48 | CMAP phân biệt I/S, dựa trên số chuỗi chứa cặp. Mô tả ma trận đếm và biểu diễn tập các cặp đạt minsup; không bắt buộc mọi biểu diễn phải giữ số đếm. |
| [C01.1](wspm-bitprune-c01-1-semantic-contract.md), mục 13–14; [C01.2](wspm-bitprune-c01-2-pruning-safety.md), OBL-05/06 | Hợp đồng boolean hiện hành và chứng minh khi không có quan hệ thì support bằng 0. |
| [C02](wspm-bitprune-c02-architecture-contract.md); [final acceptance](wspm-bitprune-c16-final-acceptance-report.md); [independent review](wspm-bitprune-c16-independent-review-report.md) | Bối cảnh kiến trúc/thống kê và trạng thái được chấp nhận trước thay đổi; không phải bằng chứng nghiệm thu CMAP đếm. |
| `CmapBuilder`, `CmapI`, `CmapS`, `BitPruneCandidateEvaluator`, `ResolvedMinimumSupport`, `BitPruneStatistics` trong mining-core | Mã hiện hành: quan hệ boolean, hai đường evaluator, predicate Java double, first-failure accounting. |

SHA-256 của PDF WSPM_PreTree local đã đọc:
`B1F019867FD61E21F4E951F464A5CBE79B3917E09CF99DD3B67C8AB18C119C41`.

**SOURCE-CONFIRMED:** phân biệt I/S, đếm số chuỗi, lọc theo tần suất cặp trong
nguồn 2014; support và weighted acceptance trong nguồn WSPM_PreTree 2023.
**REASONABLE-INFERENCE:** giữ số đếm không phụ thuộc ngưỡng và đưa pair-support
bound qua predicate weighted hiện hành của BitPrune; các quyết định API dưới đây.
Không quy kết thay đổi này là khôi phục một bước bị thiếu trong baseline 2023.

## 2. Định nghĩa bắt buộc

Cho database D gồm N sequence records có SID duy nhất; mỗi sequence là danh sách
itemset không rỗng. U là đúng tập F1 do caller cung cấp, không tự tính lại F1.
Vị trí itemset dùng trong công thức bắt đầu từ 1.

```text
SI(a,b) = {sid ∈ SID(D) | ∃t: {a,b} ⊆ D[sid][t]}       (a,b ∈ U, a < b)
SS(a,b) = {sid ∈ SID(D) | ∃t<u: a ∈ D[sid][t], b ∈ D[sid][u]}
CI(a,b) = |SI(a,b)|
CS(a,b) = |SS(a,b)|                                    (a,b ∈ U)
```

CI là exact support của `<(ab)>`; CS là exact support của `<(a)(b)>`.
Chúng không phải exact support của một pattern dài hơn chứa cặp tương ứng.

- Mỗi SID đóng góp tối đa 1 cho mỗi cặp và mỗi loại I/S, bất kể số embedding.
- Hai SID khác nhau có sequence giống nhau vẫn đóng góp riêng.
- Không nối bằng chứng qua SID; không tạo thứ tự bên trong itemset.
- I chỉ nhận cặp canonical `a < b`; không tự đổi chỗ, không nhận `(a,a)`.
- S có hướng, không bắt buộc `a < b`; `(a,a)` hợp lệ nếu có hai itemset khác nhau.
- Một SID có thể đóng góp cho cả CI và CS của cùng cặp, hoặc cả hai hướng S.
- Với truy vấn hợp lệ: `0 <= count <= N`; vắng bằng chứng trả 0.
- Quan hệ boolean cũ phải khôi phục được chính xác bằng `count > 0`.
- Thứ tự records, nhãn SID và thứ tự cung cấp U không thay đổi kết quả.

## 3. Ranh giới dữ liệu và truy vấn

Hợp đồng hành vi, chưa chốt tên method hoặc cấu trúc lưu trữ Java:

- Cung cấp truy vấn số đếm nguyên chính xác cho cặp hợp lệ. `int` đủ trong
  domain hiện tại có N kiểu int; không tăng bộ đếm theo số embedding rồi ép kiểu.
- Nếu giữ `contains`, nghĩa vẫn là `count > 0`, không đổi thành đạt ngưỡng.
- Null database/U/phần tử/query: fail fast; phần tử U trùng hoặc query ngoài U:
  fail fast. I-pair không canonical: fail fast. Không trả 0 để che input sai.
- U có item không xuất hiện: giữ trong universe, count tương ứng bằng 0.
  Item trong D nhưng ngoài U không tạo cặp trong CMAP.
- D rỗng hoặc U rỗng: cấu trúc rỗng hợp lệ; không suy ra MeanW giả hoặc cho phép
  chạy mining end-to-end trên D rỗng. Query vẫn tuân thủ kiểm tra universe.
- Kết quả bất biến sau xây dựng; không giữ tham chiếu có thể bị caller sửa đổi.
  Mỗi run/database có cấu trúc riêng, không tích lũy số đếm giữa các run.
- CI/CS được xây từ cùng D và U với bitmaps, index, F1 và ngưỡng đã resolve.
  Cùng N không đủ chứng minh cùng database; caller phải bảo đảm provenance này.
- Số đếm không phụ thuộc minsup/MeanW. Không xóa count dương dưới ngưỡng rồi
  coi nó là 0; biểu diễn sparse được phép bỏ các entry thực sự bằng 0.

Khử trùng lặp bằng chứng theo từng SID là bắt buộc. Cách lưu ma trận, map, tập
tạm hoặc dấu SID là lựa chọn triển khai sau này; chưa có tuyên bố hiệu năng.

## 4. Chứng minh cận trên và phạm vi cắt tỉa

Cho P không rỗng, a là item cuối của itemset cuối, b là item mở rộng và Q là
ứng viên hợp lệ. Với I cần `a < b`.

**OBL-CSID-1 (I):** mọi SID hỗ trợ Q = P ⊕I b đều có itemset kết thúc embedding
chứa a và b. Do đó `SupportingSIDs(Q) ⊆ SI(a,b)`, suy ra `support(Q) <= CI(a,b)`.

**OBL-CSID-2 (S):** mọi SID hỗ trợ Q = P ⊕S b đều có a tại endpoint của P và b
ở itemset sau nghiêm ngặt. Do đó `SupportingSIDs(Q) ⊆ SS(a,b)`, suy ra
`support(Q) <= CS(a,b)`, kể cả `a == b`.

**OBL-CSID-3 (subtree):** mọi descendant R của Q chứa Q như subsequence, nên
`support(R) <= support(Q) <= pairCount`. Bound dùng MeanW toàn cục không âm;
không chuyển sang trọng số phụ thuộc pattern. Vì vậy khi bound không đạt
weighted threshold, có thể loại Q và subtree của Q. Không loại các sibling
hoặc mọi cách mở rộng khác của P chỉ vì cặp vừa xét thất bại.

Phân loại operational rule: **upper-bound pruning**. Trường hợp count = 0 còn
là bằng chứng exact support = 0; quyết định loại vẫn chịu ràng buộc ngưỡng.
Đây là mở rộng của OBL-05/06 cũ, không gán lại danh tính các nghĩa vụ cũ.

CMAP qua ngưỡng chỉ là điều kiện cần. Tiếp tục bitmap bound và exact temporal
verification với toàn bộ feasible endpoints. Không đưa pairCount thành support
của Q, không bỏ verifier, không thay đổi candidate universe/lexicographic gates.
Hợp đồng này chỉ dùng cặp `(parentLastItem, extensionItem)` như evaluator hiện tại;
kiểm tra thêm mọi item của prefix nằm ngoài phạm vi.

## 5. Ngưỡng và Java double

Gọi θ là `effectiveThreshold`, M là global MeanW. Khi θ > 0:

```text
pairCount = CI(a,b) hoặc CS(a,b), theo loại extension
prune = !minimumSupport.meetsThreshold(pairCount, M)
```

**OBL-CSID-4:** với `0 <= support(Q) <= pairCount <= N`, M finite không âm
và tích finite, phép chuyển int sang double là chính xác trong domain int,
phép nhân làm tròn IEEE 754 với cùng M không âm là không giảm. Nếu predicate
chấp nhận support(Q), nó cũng chấp nhận pairCount. Phản đảo cho thấy rule trên
không loại mẫu đạt frozen predicate. Đây là an toàn tương đối với Java double,
không phải chứng minh tương đương số thực chính xác.

- Predicate hiện hành là `support * M >= θ`; đạt đẳng thức phải đi tiếp.
- Dùng đúng resolved threshold của run, không so raw count với phần trăm,
  không tự chia θ/M rồi ceil, không thêm epsilon/comparator khác.
- Input numeric không hợp lệ hoặc overflow/nonfinite phải theo validation và
  exception policy hiện hành; không bắt exception rồi diễn giải thành prune.
- Với θ bằng `+0.0` hoặc `-0.0`: Tier 1 no-op như hiện tại; không định nghĩa
  enumeration/acceptance mới. R6 vẫn OPEN; zero-threshold end-to-end không được
  coi là tiêu chí nghiệm thu đầy đủ cho thay đổi này.

## 6. Pipeline và thống kê

Cả đường diagnostic và prepared/hot trong `BitPruneCandidateEvaluator` phải
áp dụng cùng count, predicate, thứ tự Tier 1 → Tier 2 → Tier 3 và outcome.
Không đổi nơi ghi nhận một generated extension hoặc thứ tự DFS.

- Tier 1 fail: đúng một `CMAP_REJECTED`; chưa tính bitmap intersection hoặc
  exact verification của ứng viên này.
- Tier 1 pass: tiếp tục Tier 2; pass CMAP không đồng nghĩa accepted.
- `cmapRejected` mở rộng nghĩa sang mọi rejection bởi pair-support bound,
  bao gồm count dương không đạt ngưỡng. Không coi nó là số cặp count = 0.
- Không yêu cầu counters bằng phiên bản boolean: ứng viên trước đây bị reject
  ở Tier 2/3 có thể chuyển sang Tier 1. Cần ghi rõ phiên bản ngữ nghĩa khi so
  sánh statistics; không diễn giải chênh lệch counters là đo tốc độ.
- Xây CMAP vẫn thuộc preprocessing; lookup/pruning thuộc DFS. Không thêm
  counter/timing scope mới trong hợp đồng này.

Giữ các bất biến trên mỗi run thành công:

```text
generatedExtensions = generatedIExtensions + generatedSExtensions
generatedExtensions = cmapRejected + bitmapRejected + exactRejected + acceptedExtensions
exactVerifications = exactRejected + acceptedExtensions
patternCount = f1Count + acceptedExtensions
```

## 7. Vector xác định cho kiểm chứng

Mỗi hàng là một database riêng. Ký hiệu `(ab)` là cùng itemset; các số ở trước
dấu `:` là SID. F1/universe cấp primitive gồm các item được query trong hàng.

| ID | Database / input | Kết quả bắt buộc |
|---|---|---|
| V01 | `1:<(a)(b)(b)>; 2:<(ab)>; 3:<(a)(b)>` | CI(a,b)=1; CS(a,b)=2; CS(b,a)=0; CS(b,b)=1. |
| V02 | `1:<(ab)(ab)(ab)>` | CI(a,b)=1; CS(a,b)=CS(b,a)=CS(a,a)=CS(b,b)=1; không đếm 3 hoặc số embedding. |
| V03 | `1:<(ab)>` | CI(a,b)=1; mọi S-pair trên {a,b} bằng 0. |
| V04 | `1:<(a)>; 2:<(b)>` | CI(a,b)=CS(a,b)=CS(b,a)=0. |
| V05 | `-5:<(b)(a)>; 0:<(a)(b)>; 42:<(a)(b)>` | CS(a,b)=2; CS(b,a)=1; CI(a,b)=0. |
| V06 | `1:<(a)(b)>; 2:<(a)(b)>` | CS(a,b)=2 dù hai sequence có cùng nội dung. |
| V07 | `1:<(a)(b)(c)>; 2:<(ab)>; 3:<(b)(c)>`, P=`<(ab)>`, S-extension c | CS(b,c)=2 nhưng support(`<(ab)(c)>`)=0; với ngưỡng cho đi qua cả hai bound, verifier phải reject. |
| V08 | U chứa item z không có trong D; D có item ngoài U | Count hợp lệ liên quan z bằng 0; query ngoài U ném lỗi; không tự mở rộng U. |
| V09 | D rỗng; hoặc U rỗng; query I đảo chiều/tự cặp; null/trùng U | Rỗng và lỗi đúng mục 3, không giả MeanW hay normalize I-pair. |
| V10 | Numeric synthetic: N=4, M=0.25, θ=0.5 | count=0/1 prune; count=2 equality pass; count=3/4 pass. Không tuyên bố M này được suy ra từ D cụ thể. |
| V11 | Numeric synthetic như V10, θ lần lượt nextDown(0.5), 0.5, nextUp(0.5) | count=2 lần lượt pass, pass, prune; kết quả đúng predicate hiện hành. |
| V12 | θ=+0.0 hoặc -0.0; numeric invalid/overflow | Zero: no-op Tier 1; invalid/overflow: validation/exception, không thành rejection. Đây là test primitive, không giải quyết R6. |

V07 integration dùng counts item a=2, b=3, c=2, tổng appearances=7, nên
M=(3/7+2/7)/2 (tính theo đúng thứ tự phép toán của baseline), và cấu hình
ABSOLUTE θ=0.25. P có support=1 và đạt ngưỡng. Candidate bitmap intersection
chứa SID 1, cardinality=1: cả pair bound và bitmap bound đều qua, nhưng không
có SID nào hỗ trợ Q; exact verifier phải reject. Test
cấp phương thức dùng numeric synthetic phải ghi nhãn riêng. Kiểm chứng thêm
snapshot bất biến sau caller mutation, run isolation, item ID 0/MAX_VALUE,
hoán vị records/U và đổi nhãn SID không liên tục.

## 8. Oracle và tiêu chí nghiệm thu triển khai tương lai

Oracle số đếm phải độc lập: duyệt từng SID và tìm tồn tại vị trí thỏa định nghĩa,
increment đúng một lần/SID. Không tái sử dụng builder hoặc pair-key helper đang
được kiểm tra. So sánh **mọi count**, không chỉ `count > 0`.

| AC | Bằng chứng cần có | Trạng thái tại thời điểm đặc tả |
|---|---|---|
| AC01 | Toàn bộ V01–V12, domain, lifecycle và compatibility `contains == count>0` được kiểm chứng. | NOT VERIFIED |
| AC02 | Oracle direct-scan so mọi I-pair canonical và mọi S-pair, kể cả self-pair; kiểm bound support(Q)<=pairCount bằng oracle pattern độc lập. | NOT VERIFIED |
| AC03 | Test false-negative: count dương dưới ngưỡng reject ở Tier 1; equality pass; pair pass nhưng full pattern fail; không coi count là support pattern. | NOT VERIFIED |
| AC04 | Diagnostic/hot thống nhất outcomes, exact support/endpoints khi có, numeric/zero handling; mọi bất biến statistics vẫn đúng. | NOT VERIFIED |
| AC05 | Table 1 ở 14%: full canonical pattern→exact support map bằng baseline và brute-force, đúng 9 patterns; không chỉ bằng count. | NOT VERIFIED |
| AC06 | Controlled, fixed-seed random và bounded-exhaustive differential: baseline/BitPrune mới/brute-force cùng D, weights, resolved threshold dương; không thiếu/thừa pattern hoặc lệch support. | NOT VERIFIED |
| AC07 | Chạy focused CMAP/evaluator/bound checks rồi toàn bộ `mvn -pl mining-core test`; báo rõ lệnh, seed, phạm vi enumeration và số cases thực chạy. | NOT VERIFIED |
| AC08 | Baseline/Prime-block và ngoài mining không đổi; documentation và mô tả counter phản ánh thay đổi có chủ đích; giới hạn R6/numeric được giữ. | NOT VERIFIED |

Đối chiếu boolean cũ bổ sung: cùng canonical output/support, và tập ứng viên
bị Tier 1 boolean loại là tập con của tập bị Tier 1 count loại, trên cùng tập
candidate được đưa vào evaluator và cấu hình ngưỡng dương. Không so tổng counters
toàn DFS để suy ra quan hệ tập này vì subtree pruning thay đổi tập được thăm.

Nếu có mismatch: ghi pattern/cặp đầu tiên, SID/positions, loại extension, count,
MeanW, configured/resolved threshold, outcome từng tier; thêm regression nhỏ và
dừng benchmark. Không thay baseline để làm test xanh.

## 9. Quyết định chưa chốt và giới hạn

- Các lựa chọn API, biểu diễn và tổ chức test cho triển khai được đề xuất tại
  mục 10; chúng là quyết định engineering, không phải công thức của bài báo.
- Không tự mở rộng sang all-prefix pair checks, adaptive threshold caching,
  thay bitmap bằng pair count, hoặc thay chính sách numeric.
- R6 và exact-real comparator equivalence tiếp tục unresolved. Không hứa cải
  thiện runtime/memory; cần thực nghiệm tương đương sau correctness acceptance.
- Hoàn tất tài liệu không đồng nghĩa thuật toán mới được ACCEPTED hoặc các
  acceptance reports Phase C cũ đã bao phủ thay đổi này.

## 10. Kế hoạch sửa mã và kiểm thử sau đối chiếu nguồn online

Ngày đối chiếu: 2026-11-11. Kế hoạch dưới đây đã được thực thi; xem bằng chứng
tại mục 11. Không coi riêng kế hoạch hoặc source tham chiếu là bằng chứng AC đạt.

### 10.1. Nguồn tham chiếu và khoảng cách với code hiện tại

Đã đọc lại nguồn 2014, mục 3.1–3.3. CMAP thresholded-set và ma trận đếm là
hai biểu diễn của kiểm tra tần suất cặp. Boolean `exists` hiện tại chỉ giữ
thông tin count>0, nên để lọt sang các tier sau những cặp count dương nhưng
không đủ ngưỡng. Đây là lọc yếu hơn, không tự nó chứng minh output sai.

Đã đọc mã từ [gói source SPMF chính thức](https://www.philippe-fournier-viger.com/spmf/spmf.zip),
được liên kết tại [trang download của tác giả](https://www.philippe-fournier-viger.com/spmf/download.php).
Đây là snapshot hiện hành tải khi đối chiếu, không khẳng định là mã đúng phiên
bản dùng cho thực nghiệm năm 2014. Archive chỉ được đọc trong bộ nhớ, không
giải nén hoặc đưa source bên ngoài vào repository.

```text
Archive SHA-256:
48599087a24bd43fc1f32dac05cd2390f3895da437e092a7c0b7e6c7426a9c10
Entry: ca/pfv/spmf/algorithms/sequentialpatterns/spam/AlgoCMSPAM.java
Entry SHA-256:
fbc7227c472d8d8000672b13466f1bf202b7ec688dd3acca046b447b8018f816
```

Trong entry trên: `processTransactionForCMAP` (khoảng dòng 699–743) chống
đếm lặp trong một sequence; `addCooccurrence` (752–759) tăng support cặp;
S/I lookup (842–845 và 924–927) kiểm tra support với minsup trước phép nối.
Các số dòng chỉ áp dụng cho checksum trên. Tham khảo ngữ nghĩa, không sao chép
cấu trúc bitmap, remapping hay tối ưu của SPMF sang baseline.

Khác biệt cần thích nghi: SPMF kiểm tra support nguyên với minsup của frequent
SPM; repository kiểm tra `support * globalMeanW >= effectiveThreshold`.
Chuyển count vào `meetsThreshold`, không sao chép phép so sánh ngưỡng nguyên.
Nguồn 2014 nêu các điều kiện tổng quát trên prefix và cách kiểm tra last item
khi tích hợp với thuật toán cụ thể. Kế hoạch này chỉ nâng count cho last-item
check hiện có; không khẳng định đã áp dụng mọi tối ưu candidate-list của SPMF.

### 10.2. Quyết định triển khai dự kiến

1. Giữ hai lớp `CmapI`, `CmapS`; đổi storage từ `Set<Long>` sang sparse
   `Map<Long, Integer>` chứa count dương, giữ encoding pair key hiện hành.
   Thêm `int supportCount(Item left, Item right)`; query hợp lệ thiếu entry trả 0.
   Giữ `contains` tương thích bằng `supportCount(...) > 0`, dùng chung validation.
   Constructor package-private nhận map và defensive copy; mọi test gọi trực
   tiếp constructor phải được chuyển đổi rõ ràng, không thêm constructor giả
   định rằng mỗi pair trong Set có support=1.
2. `CmapBuilder.buildI/buildS` giữ signatures và universe policy. Mỗi record có
   tập pair riêng; sau khi quét record, tăng map tổng đúng một lần cho mỗi pair.
   I: thu pair trong từng itemset vào tập của record. S: giữ `seenItems` của các
   itemset trước, thu pair vào tập của record rồi mới thêm itemset hiện tại vào
   `seenItems`. Cách này giữ strict t<u và self-pair. Không dùng global Set để
   làm mất count, không tăng trực tiếp theo từng occurrence.
3. Hai đường evaluator lấy `pairSupport` qua `supportCount`; khi θ>0, reject
   nếu `!minimumSupport.meetsThreshold(pairSupport, globalMeanWeight)`.
   Chia sẻ phần quyết định nhỏ nếu cần để tránh lệch hai đường; giữ validation,
   zero bypass, first-failure và exception semantics. Không refactor tier khác.
4. Sửa Javadoc của CMAP/evaluator/outcome/statistics từ existence-only sang
   pair-support bound. Counter names/schema giữ nguyên. Chỉ sửa lời mô tả liên
   quan; không sửa accumulator nếu các bất biến và lifecycle vẫn đáp ứng.

Biểu diễn sparse trên là lựa chọn đơn giản phù hợp code hiện tại, chưa có tuyên
bố nhanh hơn hoặc ít bộ nhớ hơn. Không bổ sung dependency collections mới.

### 10.3. Các đợt thay đổi và cửa kiểm chứng

Mỗi đợt giữ phạm vi nhỏ, nhưng chỉ nghiệm thu toàn bộ sau đợt cuối.

| Đợt | Files dự kiến và hành động | Điều kiện hoàn tất |
|---|---|---|
| A — Storage và builder | Main: `bitprune/cmap/{CmapI,CmapS,CmapBuilder}.java`. Tests: năm lớp `CmapITest`, `CmapSTest`, `CmapBuilderITest`, `CmapBuilderSTest`, `CmapDifferentialTest`. Thêm count assertions, giữ boolean compatibility. | V01–V09 và lifecycle/domain; oracle distinct-SID độc lập khớp mọi count; chưa đổi evaluator. |
| B — Weighted Tier 1 | Main: `bitprune/mining/BitPruneCandidateEvaluator.java` và mô tả trong `BitPruneCandidateEvaluation`, `BitPruneStatistics` khi cần. Tests: `BitPruneCandidateEvaluatorTest`, `BitPruneCandidateEvaluatorDifferentialTest`; thêm `BitPruneCmapCountRoutingTest` trong cùng package để kiểm hai đường và các witness. | V10–V12; cả I/S count dương dưới ngưỡng reject; equality pass; hot/diagnostic parity. |
| C — Statistics và differential | `BitPruneMiningStatisticsIntegrationTest` và những focused integration tests có kỳ vọng routing bị tác động. Tái sử dụng các suite three-oracle/controlled/random/exhaustive hiện có. | Bất biến đúng; witness mỗi tier vẫn đạt tier mục tiêu; full maps/counts đồng nhất baseline và brute-force. |
| D — Review và ghi bằng chứng | Cập nhật tài liệu này với lệnh/kết quả và mô tả thay đổi hiện hành trong documentation map nếu đã triển khai. Không viết lại các acceptance reports lịch sử. | AC01–AC08 có bằng chứng hoặc ghi rõ NOT VERIFIED; không benchmark khi có mismatch. |

Các đường dẫn Java trong bảng tính từ `mining-core/src/main/java/vn/edu/wspm/`
hoặc `mining-core/src/test/java/vn/edu/wspm/` tương ứng. File scope ngoài bảng
chỉ mở rộng khi một caller/test trực tiếp bị ảnh hưởng, với lý do cụ thể.

### 10.4. Regression cần thiết để tránh test xanh sai lý do

- Oracle evaluator hiện có (`directCmapI/S`, `DirectEvaluationResult`,
  `directEvaluate`) đang dùng boolean. Chuyển sang đếm SID độc lập và quyết
  định weighted pair bound; không gọi production builder để sinh expected.
  Chẩn đoán mismatch phải in cả pair count và weighted threshold.
- Có case I: `1:<(ab)>; 2:<(a)>; 3:<(b)>`, ABSOLUTE θ=0.75.
  M=0.5, singleton support=2 đều qua; CI(a,b)=1 cho bound=0.5: reject Tier 1.
  Case S dùng `1:<(a)(b)>` thay record 1, giữ hai record còn lại và cùng ngưỡng.
  Boolean cũ qua cả hai case; đây là bằng chứng tăng khả năng lọc theo count.
- Giữ witness Tier 2: `1:<(a)(b)(c)>; 2:<(a)(b)>; 3:<(b)(c)>`,
  P=`<(a)(b)>`, S-extension c, θ=0.5 ABSOLUTE.
  M=(3/7+2/7)/2; parent support=2; CS(b,c)=2 đủ ngưỡng;
  candidate bitmap cardinality=1 không đủ ngưỡng. Outcome phải BITMAP_REJECTED.
  Không chỉ đổi expected sang CMAP_REJECTED rồi để mất coverage Tier 2.
- Giữ witness Tier 3 V07: các bound qua nhưng verifier reject. Test phải
  chứng minh đã đến exact verification, không chỉ so output rỗng.
- Cập nhật các expected statistics bằng trace independent theo từng fixture,
  không lấy output mới làm expected hoặc xóa assertions để suite pass.
- Thêm assertion parity giữa hai đường evaluator trên các fixture trên; so
  outcome và exact result/endpoints ở nơi cả hai đường có dữ liệu tương ứng.

### 10.5. Thứ tự chạy và nghiệm thu

Lệnh dự kiến từ root, dùng Java 21/Maven như module hiện tại, không `clean`:

```powershell
mvn -pl mining-core "-Dtest=CmapITest,CmapSTest,CmapBuilderITest,CmapBuilderSTest,CmapDifferentialTest" test
mvn -pl mining-core "-Dtest=BitPruneCandidateEvaluatorTest,BitPruneCandidateEvaluatorDifferentialTest,BitPruneCmapCountRoutingTest,BitPruneMiningStatisticsIntegrationTest" test
mvn -pl mining-core "-Dtest=BitPruneTable1ThreeOracleDifferentialTest,BitPruneControlledDifferentialCorrectnessTest,BitPruneFixedSeedRandomizedDifferentialTest,BitPruneBoundedExhaustiveDifferentialTest" test
mvn -pl mining-core test
```

Suite randomized hiện có cấu hình seed `20261104L`, 1,200 trials. Giữ cấu hình
và ghi số case thực chạy, không tuyên bố các suite đã pass trước khi thực thi.
Count differential bổ sung dùng seed cố định ghi trong test, kiểm mọi cặp
canonical I và ordered S trên U, gồm self-pair, record permutation và SID relabel.
Kiểm candidate bound bằng brute-force độc lập trên tiny D; equality và các
giá trị adjacent-double phải có assertions riêng.

Nghiệm thu khi đủ full `canonical pattern -> exact support` map và total count,
Table 1 đúng 9 patterns, mọi routing/bound invariant đạt và toàn module pass.
Không yêu cầu old/new counters bằng nhau; không thay frozen baseline, weight
hoặc threshold để đạt kết quả. Dừng tại correctness disagreement và lưu regression.
Benchmark hiệu năng là task sau, không nằm trong kế hoạch triển khai này.

## 11. Bằng chứng triển khai và kiểm chứng — 2026-11-11

### 11.1. Thay đổi thực tế

- `CmapI/S`: immutable sparse pair counts, `supportCount` và projection
  `contains == supportCount > 0`; giữ validation và defensive copies.
- `CmapBuilder`: tập cặp riêng mỗi record, mỗi cặp tăng tổng đúng một lần/SID.
  I/S phân biệt, S chỉ nối từ các itemset trước.
- Hai evaluator dùng cùng weighted predicate trên pair count ở Tier 1;
  giữ zero bypass, bitmap filter, all-endpoints exact verifier và DFS generation.
- Oracle evaluator đếm từng SID độc lập; kiểm exact child support không vượt
  pair support, kể cả trước khi route reject. Không dùng production builder
  để sinh expected counts. Random count differential so mọi pair count.
- Thêm `CmapSupportCountTest` và `BitPruneCmapCountRoutingTest`. Sửa expected
  routing của các fixture hai item; giữ witness Tier 2/3 bằng pattern dài hơn.
- Giữ counters và bất biến, đổi mô tả Tier 1 thành weighted pair-support bound.
  Không sửa baseline, PRISM, weights, threshold resolver, bitmap hoặc verifier.

Phạm vi mở rộng cần thiết: bốn test consumers có expected Table 1 statistics
boolean cũ (`MainWspmBitPruneConsoleTest`, `MainWspmBitPruneIntegrationTest`,
`BitPruneMeasurementIntegrationTest`, `BitPrunePhaseBoundaryTest`). Chỉ thay
expected routing/counts và mô tả liên quan; giữ assertions output/support.
Không sửa source backend/frontend/database hoặc acceptance reports lịch sử.

### 11.2. Lệnh và kết quả

Môi trường: Windows 11 amd64, Oracle Java 21.0.12.1, Maven 3.9.16.
Các lệnh từ root, không `clean`. Reports nằm trong `mining-core/target/surefire-reports/`
(ignored); test audits có thể ghi thêm evidence dưới ignored `target/`.

| Lệnh | Kết quả |
|---|---|
| `mvn -pl mining-core "-Dtest=CmapITest,CmapSTest,CmapBuilderITest,CmapBuilderSTest,CmapDifferentialTest,CmapSupportCountTest" test` | PASS, 72 tests. Count differential: seed 20261028L, 1,200 trials, 4,399 I + 11,581 S pair comparisons. |
| `mvn -pl mining-core "-Dtest=BitPruneCandidateEvaluatorTest,BitPruneCandidateEvaluatorDifferentialTest,BitPruneCmapCountRoutingTest,BitPruneMiningStatisticsIntegrationTest" test` | PASS, 68 tests sau cập nhật routing. |
| `mvn -pl mining-core "-Dtest=BitPruneTable1ThreeOracleDifferentialTest,BitPruneControlledDifferentialCorrectnessTest,BitPruneFixedSeedRandomizedDifferentialTest,BitPruneBoundedExhaustiveDifferentialTest" test` | PASS, 38 tests; không thiếu/thừa pattern hoặc lệch support. |
| `mvn -pl mining-core "-Dtest=BitPruneCandidateEvaluatorDifferentialTest,BitPrunePhaseBoundaryTest,BitPruneMeasurementIntegrationTest,MainWspmBitPruneConsoleTest,MainWspmBitPruneIntegrationTest" test` | PASS, 86 tests sau sửa consumers Table 1 và oracle bound assertion. |
| `mvn -pl mining-core test` — lần cuối sau mọi thay đổi Java có ý nghĩa | PASS, 1,197 tests, 0 failures/errors/skipped. |
| `git diff --check` | PASS. |

Trong quá trình triển khai, lượt focused/full đầu có lỗi compilation trong test
và expected routing cũ; đã sửa trước lượt full thành công cuối. Không có output
map disagreement giữa baseline/BitPrune/brute-force được phát hiện.

Các catalogue thực chạy trong lượt full cuối:

- Evaluator: 1,500 random trials, seed 20261103L; bounded catalogue 1,836
  comparisons, 1,224 positive-threshold + 612 zero-threshold primitive cases.
- Full-map randomized: seed 20261104L, 1,200 trials (604 absolute, 596 percent),
  3,600 pairwise comparisons; cả ba oracle có tổng 16,283 accepted patterns.
- Full-map exhaustive: 2 items, 3 canonical itemsets, sequences dài 1–2 itemset;
  12 sequences, 156 databases gồm 12 one-sequence và 144 ordered two-sequence
  databases, 312 configurations, 936 pairwise complete-map comparisons.
- Full-map controlled và Table 1 suites đều pass. Zero-threshold primitive
  cases không được diễn giải thành end-to-end acceptance của R6.

### 11.3. Trace độc lập cho các expected statistics thay đổi

Table 1, 14%: N=7, MeanW theo baseline, θ=(14/100)*7. Pair counts trên F1:

```text
I: (1,2)=7; (1,3)=3; (2,3)=3
S: (1,1)=6; (1,2)=7; (1,3)=6;
   (2,1)=4; (2,2)=4; (2,3)=6;
   (3,1)=2; (3,2)=3; (3,3)=3
```

Đếm bằng full-position embedding enumeration độc lập trên fixture Table 1:
34 candidates (7 I, 27 S), 23 CMAP-rejected, 0 bitmap-rejected,
5 exact-rejected, 6 accepted extensions. Năm exact rejections là
`<(1)(1 2)>`, `<(1)(1)(1)>`, `<(1)(1)(2)>`, `<(1)(1)(3)>`, `<(1)(2)(3)>`.
Vì vậy exactVerifications=11 thay vì 34; 9 expanded nodes/patterns và toàn bộ
exact support map không đổi. Thống kê này không phải runtime benchmark.

Mixed fixture mới giữ đủ bốn outcomes: `<(1)(2)(3)>`, `<(1)(2)>`, `<(2)(3)>`,
`<(2)(1)>`, `<(2)(1)>` với SID riêng và θ=0.5. MeanW=(5/11+2/11)/2.
Independent enumeration cho 24 candidates (6 I, 18 S): 18 CMAP, 1 bitmap,
2 exact rejects, 3 accepted extensions; 6 patterns, 6 expanded nodes,
5 exact verifications. Test assert nguyên vector, không suy expected từ actual.

### 11.4. Truy vết acceptance và giới hạn

| AC | Bằng chứng hiện tại |
|---|---|
| AC01 | PASS: count/domain/lifecycle/compatibility tests; numeric/zero/overflow routing tests; random count oracle bổ sung các quan hệ. |
| AC02 | PASS trên catalogue đã chạy: every-pair count differential và independent full-pattern pair-bound assertions trong evaluator differential. |
| AC03 | PASS: positive-count dưới ngưỡng cho cả I/S, equality/adjacent-double, bitmap witness, exact witness; không thay exact support bằng pair count. |
| AC04 | PASS: hot/diagnostic parity trên fixtures I/S/ngưỡng/numeric, full counter vectors và invariants. |
| AC05 | PASS: Table 1 three-oracle complete map, 9 patterns; statistics trace ở trên. |
| AC06 | PASS trên controlled/random/exhaustive catalogue đã mô tả; không phải chứng minh exhaustive cho mọi database. |
| AC07 | PASS: các lệnh ở mục 11.2, full module 1,197 tests. |
| AC08 | PASS trong diff hiện tại: thay đổi giới hạn ở BitPrune production/tests và documentation; baseline/PRISM cùng các giới hạn nghiên cứu được giữ. |

R6 vẫn OPEN. An toàn numeric chỉ tương đối với frozen Java double predicate;
exact-real equivalence vẫn unresolved. Chưa benchmark time/memory. Kết quả này
là bằng chứng implementation/self-review; không tự đánh dấu independent
research review hoặc nghiệm thu Phase C mới.

## 12. Tối ưu validation parent và transaction index — 2026-11-11

Đây là thay đổi engineering sau mục 11, không thay CMAP, quy tắc ngưỡng,
DFS candidate space, exact verifier hay baseline. Phạm vi:

- `BitPruneMiner` kiểm tra quan hệ chứa SID của parent bitmap và exact parent
  verification một lần khi vào node DFS. Hai đối tượng bất biến và được chia sẻ
  bởi các sibling. Đường DFS truyền tiếp đúng hai đối tượng đó đến entry nội bộ
  đã kiểm tra; các kiểm tra cấu trúc/universe/ngưỡng vẫn thực hiện cho từng
  candidate. Đường prepared evaluator thông thường vẫn tự kiểm tra parent.
- `VerticalOccurrenceIndex.build` dùng nhánh xây trực tiếp khi **mọi** sequence
  chỉ có một itemset: mỗi occurrence có vị trí duy nhất 1. CSDL có sequence
  nhiều itemset tiếp tục dùng nhánh tổng quát, bảo toàn toàn bộ vị trí. Hai nhánh
  giữ nguyên validation SID mapping và defensive copies của query.

Focused verification: 149 tests. `mvn -pl mining-core test`: 1.200 tests,
0 failures/errors/skipped. Bổ sung test single-itemset với SID âm/không liên tục,
invalid mapping và defensive array; test mixed temporal positions; test parity
các đường evaluator và từ chối parent SID không thuộc bitmap/universe. Các suite
Table 1, randomized và bounded-exhaustive so full pattern→exact-support map với
baseline/brute-force đều pass trên phạm vi đã chạy.

### 12.1. Đo riêng từng thay đổi

Chi tiết, source snapshot, checksums, class files, 60 logs và runner:
`results/bitprune-validation-index-ablation-20261111-043557/`. CSV gồm
`ablation-runs.csv` và `ablation-means.csv`. Bốn variants được build riêng:
CONTROL (trước cả hai), VALIDATION_ONLY, INDEX_ONLY, BOTH. Bytecode checksum
gate xác nhận khác biệt chỉ nằm trong các lớp thuộc đúng treatment; cùng CMAP.

Ba cấu hình: SIGN 0,3% (sequential), Pumsb 0,65% và T40 0,04% (FIMI
transaction); 5 lần/variant/cấu hình = 60 lượt. Chạy tuần tự, JVM mới cho mỗi
lượt, thứ tự 4 variant xáo theo block/seed 20261111, không warm-up riêng,
Java 21, `-Xms256m -Xmx2g -XX:+UseG1GC`, sampler heap 5 ms, không export.
Mỗi dòng trong bảng là trung bình 5 lượt; SD có trong CSV.

| Dataset / biến thể | Preprocessing (s) | DFS (s) | Complete Algorithm (s) | Sampled algorithm peak heap (MB) |
|---|---:|---:|---:|---:|
| SIGN 0,3% — CONTROL | 0,526 | 1,737 | 2,268 | 162,402 |
| SIGN — VALIDATION_ONLY | 0,497 | 0,533 | 1,037 | 161,642 |
| SIGN — INDEX_ONLY | 0,536 | 1,697 | 2,240 | 161,269 |
| SIGN — BOTH | 0,530 | 0,560 | 1,097 | 161,669 |
| Pumsb 0,65% — CONTROL | 2,188 | 1,341 | 3,533 | 1.436,466 |
| Pumsb — VALIDATION_ONLY | 2,175 | 0,589 | 2,768 | 1.420,359 |
| Pumsb — INDEX_ONLY | 1,473 | 1,252 | 2,728 | 775,886 |
| Pumsb — BOTH | 1,442 | 0,549 | 1,994 | 711,367 |
| T40 0,04% — CONTROL | 3,811 | 2,210 | 6,024 | 1.750,471 |
| T40 — VALIDATION_ONLY | 3,811 | 0,067 | 3,883 | 1.384,442 |
| T40 — INDEX_ONLY | 3,023 | 2,602 | 5,628 | 1.013,730 |
| T40 — BOTH | 2,998 | 0,075 | 3,077 | 920,418 |

Validation giúp giảm DFS trong ba cấu hình quan sát; nhánh index trực tiếp
được kích hoạt cho hai CSDL transaction và giảm preprocessing/peak heap quan sát
trong hai cấu hình đó. SIGN có sequence nhiều itemset nên giữ nhánh index cũ;
không quy khác biệt thời gian nhỏ của INDEX_ONLY trên SIGN cho fast path.
Các thành phần không được giả định cộng tuyến tính.

Kiểm tra độc lập sau chạy: đúng 60 RUN, 12 MEAN; toàn bộ 42 typed metrics
trên từng log khớp CSV; mean time/heap/counters tính lại khớp; CSV checksums
khớp manifest. Trong mỗi dataset, full canonical pattern→support map hash và
các counters đồng nhất ở 20 lượt. Hash là bằng chứng đối chiếu giữa bốn variants,
không phải full-map comparison với baseline trên dataset lớn.

Đây là đo trên một máy có hoạt động nền, cold JVM và chỉ ba cấu hình, không
chứng minh hiệu năng trên mọi dataset hoặc tương đương điều kiện tài liệu cũ.
Peak là observed sampled Java used heap, không phải RSS hay retained heap.
R6 và exact-real comparator equivalence vẫn mở; independent research review
cho các thay đổi này chưa được thực hiện.
