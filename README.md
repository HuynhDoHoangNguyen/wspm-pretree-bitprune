# WSPM_PreTree & WSPM_BitPrune

**Research on Improving the WSPM_PreTree Algorithm and Developing a Product Combo Recommendation System**

Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây dựng hệ thống gợi ý combo sản phẩm.

## 1. Project Overview

Dự án tập trung nghiên cứu lĩnh vực **Weighted Sequential Pattern Mining (WSPM)** — khai phá mẫu tuần tự có trọng số từ cơ sở dữ liệu chuỗi.

Mục tiêu của nghiên cứu là tìm hiểu, tái hiện và cải tiến thuật toán **WSPM_PreTree** thông qua phương pháp **WSPM_BitPrune**, kết hợp các cấu trúc dữ liệu và cơ chế cắt tỉa nhằm tối ưu hóa quá trình khai phá mẫu tuần tự có trọng số.

Nghiên cứu hướng đến việc nâng cao hiệu quả khai phá trên các tập dữ liệu có quy mô và đặc điểm khác nhau, đồng thời ứng dụng các mẫu tuần tự được phát hiện vào bài toán **gợi ý combo sản phẩm**, hỗ trợ phân tích hành vi mua sắm và đề xuất các nhóm sản phẩm có mối liên hệ trong lịch sử giao dịch.

## 2. Research Objectives

Các mục tiêu chính của dự án:

1. **Nghiên cứu WSPM_PreTree:** Phân tích cơ sở lý thuyết, cách biểu diễn dữ liệu, phương pháp tính trọng số, cấu trúc Prefix Tree, Prime-block Encoding và cơ chế khai phá mẫu tuần tự.
2. **Tái hiện thuật toán gốc:** Xây dựng phiên bản WSPM_PreTree bằng Java, kiểm chứng kết quả với các ví dụ và dữ liệu thực nghiệm trong công trình nghiên cứu.
3. **Nghiên cứu WSPM_BitPrune:** Xây dựng phương pháp cải tiến sử dụng Bitmap, CMAP, Vertical ID-list và Multi-tier Lazy Pruning.
4. **Kiểm chứng tính đúng đắn:** Đối chiếu toàn bộ tập mẫu và support của từng mẫu giữa hai thuật toán trên cùng dữ liệu và ngưỡng khai phá.
5. **Đánh giá hiệu năng:** Thực nghiệm về thời gian thực thi, bộ nhớ, số lượng ứng viên, khả năng cắt tỉa và khả năng mở rộng.
6. **Ứng dụng gợi ý combo:** Khai thác các mẫu tuần tự có trọng số để hỗ trợ đề xuất các nhóm sản phẩm phù hợp dựa trên lịch sử mua sắm.

## 3. Algorithms

### 3.1. WSPM_PreTree — Baseline Algorithm

WSPM_PreTree là thuật toán khai phá mẫu tuần tự có trọng số dựa trên cấu trúc cây tiền tố (Prefix Tree) và phương pháp mã hóa PRISM.

Các thành phần nghiên cứu bao gồm:

- Sequence Database Representation.
- Item Weight Calculation.
- MaxW, MinW and MeanW.
- Frequent Pattern Initialization (F1).
- Prefix Tree Construction.
- I-Extension and S-Extension.
- Depth-First Search (DFS).
- PRISM Prime-block Encoding.
- Sequential Pattern Support.
- Weighted Sequential Pattern Evaluation.
- Pattern Pruning.

WSPM_PreTree được sử dụng làm thuật toán đối chứng để kiểm tra tính đúng đắn và đánh giá phương pháp cải tiến.

### 3.2. WSPM_BitPrune — Proposed Improvement

WSPM_BitPrune là hướng cải tiến được nghiên cứu nhằm tối ưu quá trình sinh ứng viên, kiểm tra điều kiện cắt tỉa và xác định support của mẫu tuần tự.

Các thành phần thiết kế chính:

**Bitmap Representation**

Sử dụng cấu trúc bitmap để biểu diễn thông tin xuất hiện của item hoặc pattern, hỗ trợ các phép toán bit nhằm kiểm tra điều kiện cần trước khi tính support chính xác.

**CMAP — Co-occurrence Map**

Lưu trữ thông tin đồng xuất hiện của các item, hỗ trợ loại bỏ sớm những phép mở rộng không thỏa mãn điều kiện cần về support.

**Vertical ID-list**

Tổ chức thông tin vị trí xuất hiện của item trong các sequence nhằm hỗ trợ phép nối tuần tự và xác định support chính xác.

**Multi-tier Lazy Pruning**

Tổ chức quá trình cắt tỉa theo nhiều lớp:

1. CMAP-based Pruning.
2. Bitmap Upper-Bound Pruning.
3. Exact Support Verification.

Các lớp cắt tỉa được thiết kế nhằm hạn chế các phép nối tốn kém trong khi bảo toàn các mẫu hợp lệ theo định nghĩa khai phá.

## 4. Technology Stack

| Technology | Purpose |
|---|---|
| Java JDK 21 | Algorithm implementation |
| Apache Maven | Build and dependency management |
| JUnit 5 | Unit testing and correctness verification |
| Visual Studio Code | Development environment |
| Git & GitHub | Version control and source management |
| Java Core | Algorithm execution and data processing |

## 5. System Architecture

Hệ thống nghiên cứu thuật toán được tổ chức theo các thành phần chức năng:

```text
Sequence Database
        |
        v
  Data Loading
        |
        v
  Preprocessing
        |
        +------------------------+
        |                        |
        v                        v
  WSPM_PreTree              WSPM_BitPrune
  - Prefix Tree             - Bitmap
  - PRISM Encoding          - CMAP
  - I/S Extension           - Vertical ID-list
  - DFS Mining              - Lazy Pruning
        |                        |
        v                        v
  Weighted Sequential Pattern Results
        |
        v
  Correctness Verification
        |
        v
  Experimental Evaluation
        |
        v
  Product Combo Recommendation
```

Hai thuật toán sử dụng cùng mô hình dữ liệu và các quy tắc đánh giá mẫu để phục vụ việc so sánh kết quả và hiệu năng.

## 6. Project Structure

```text
wspm-pretree-bitprune/
├── data/                 # Research datasets
├── docs/                 # Research documentation
├── results/              # Experimental results
├── src/
│   ├── main/
│   │   ├── java/vn/edu/wspm/
│   │   │   ├── config/     # Experiment configuration
│   │   │   ├── data/       # Sequence database processing
│   │   │   ├── model/      # Pattern and sequence models
│   │   │   ├── baseline/   # WSPM_PreTree
│   │   │   ├── improved/   # WSPM_BitPrune
│   │   │   ├── metrics/    # Performance measurement
│   │   │   └── output/     # Result export
│   │   └── resources/
│   └── test/
│       └── java/          # Unit and correctness tests
├── .gitignore
├── LICENSE
├── pom.xml
└── README.md
```

## 7. Dataset

Dự án sử dụng cơ sở dữ liệu chuỗi (Sequence Database) phục vụ nghiên cứu và đánh giá thuật toán.

Các bộ dữ liệu thực nghiệm dự kiến bao gồm:

- Snake
- Sign
- Pumsb
- T40I10D100k
- Synthetic Sequence Datasets
- Sample Dataset from the WSPM_PreTree paper (2023)

Nguồn, phiên bản, đặc điểm và định dạng dữ liệu được kiểm tra trước khi sử dụng. Đặc biệt, dữ liệu giao dịch thông thường không được mặc nhiên xem là dữ liệu chuỗi có thứ tự thời gian.

Định dạng đầu vào cho dữ liệu chuỗi được chuẩn hóa theo quy ước SPMF:

- `-1`: Kết thúc một itemset.
- `-2`: Kết thúc một sequence.

## 8. Experimental Evaluation

Quá trình thực nghiệm được xây dựng dựa trên cùng điều kiện đầu vào và môi trường thực thi nhằm đảm bảo tính công bằng và khả năng tái lập.

### 8.1. Correctness Verification

- Kiểm thử dữ liệu mẫu từ bài báo gốc.
- Kiểm tra phép tính trọng số và support.
- Đối chiếu kết quả với phương pháp brute-force trên dữ liệu nhỏ.
- So sánh toàn bộ tập pattern và support giữa WSPM_PreTree và WSPM_BitPrune.
- Kiểm tra tính an toàn của các điều kiện cắt tỉa.

### 8.2. Performance Metrics

| Metric | Description |
|---|---|
| Dataset I/O Time | Thời gian đọc dữ liệu |
| Preprocessing Time | Thời gian tiền xử lý |
| Mining Time | Thời gian khai phá mẫu |
| Output I/O Time | Thời gian xuất kết quả |
| Total Execution Time | Tổng thời gian thực thi |
| Memory Usage | Dung lượng bộ nhớ sử dụng |
| Generated Candidates | Số lượng ứng viên được sinh |
| Pruned Candidates | Số lượng ứng viên bị cắt tỉa |
| Weighted Sequential Patterns | Số lượng mẫu tuần tự có trọng số tìm được |

Các kết quả thực nghiệm được phân tích dựa trên dữ liệu đo thực tế, không mặc định thuật toán cải tiến có hiệu năng tốt hơn trong mọi trường hợp.

## 9. Product Combo Recommendation

Các mẫu tuần tự có trọng số được sử dụng làm cơ sở nghiên cứu xây dựng hệ thống gợi ý combo sản phẩm.

Hướng ứng dụng tập trung vào:

- Phân tích lịch sử mua sắm của khách hàng.
- Phát hiện các nhóm sản phẩm thường xuất hiện theo những chuỗi hành vi mua sắm.
- Xây dựng các ứng viên combo dựa trên những mẫu tuần tự có trọng số.
- Thiết kế cơ chế đánh giá và xếp hạng combo phù hợp.
- Hỗ trợ đề xuất sản phẩm cho hoạt động thương mại điện tử.

Việc chuyển đổi từ mẫu tuần tự sang combo sản phẩm cần có quy tắc đề xuất và đánh giá riêng để bảo đảm tính phù hợp với bài toán ứng dụng.

## 10. Research References

**WSPM_PreTree (2023)**

Pham et al., *Mining Weighted Sequential Patterns Based on Prefix-Tree and Prism Encoding*, Vietnam Journal of Computer Science, 2023.

**WSPM_BitPrune (2026)**

*Tối ưu hóa thuật toán khai phá mẫu tuần tự có trọng số bằng cấu trúc Bitmap và cơ chế cắt tỉa trễ đa tầng* — Bản thảo nghiên cứu.

Các công trình liên quan đến PRISM, Sequential Pattern Mining và cấu trúc Bitmap sẽ được bổ sung trong tài liệu nghiên cứu.

## 11. License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

Third-party research publications, datasets, and external code remain subject to their respective licenses and usage conditions.
