# WSPM_PreTree & WSPM_BitPrune

Repository cho khóa luận **“Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây
dựng hệ thống gợi ý combo sản phẩm.”** Dự án gồm lõi khai phá mẫu tuần tự có
trọng số, REST API và frontend phục vụ giai đoạn tích hợp hệ thống.

## Trạng thái hiện tại

- WSPM_PreTree baseline: Phase B đã hoàn thành và được nghiệm thu.
- WSPM_BitPrune: Phase C đã hoàn thành và được nghiệm thu trên phạm vi ngưỡng
  hiệu dụng dương đã được tài liệu hóa.
- Backend, MySQL và frontend: đang trong giai đoạn bootstrap/tích hợp.
- Vấn đề nghiên cứu còn mở: ngữ nghĩa ngưỡng support hiệu dụng bằng 0 (R6) và
  tính tương đương exact-real của comparator dùng `double`.

Xem [ARCHITECTURE.md](ARCHITECTURE.md) để biết dependency và cấu trúc module;
xem [docs/README.md](docs/README.md) để chọn đúng tài liệu nghiên cứu cần đọc.

## Công nghệ

| Khu vực | Công nghệ chính |
|---|---|
| Build chung | Java 21, Maven multi-module |
| Mining | Java Core, JUnit Jupiter 5.12.2 |
| Backend | Spring Boot 4.1.2, Spring Web MVC, Data JPA, JDBC, Actuator |
| Database | MySQL 8.4, Docker Compose |
| Frontend | React 17, TypeScript 4.6, Create React App 5 |
| UI/state/data | Mantine 4, React Query 3, Zustand, React Router 6 |
| Kiểm chứng | JUnit 5, paper oracle, brute-force và differential tests |

## Yêu cầu môi trường

- JDK 21.
- Maven 3.9+.
- Docker và Docker Compose để chạy MySQL.
- Node.js và npm tương thích với `frontend/package-lock.json`.

Các phiên bản đã dùng khi cập nhật tài liệu này: Java 21, Maven 3.9.16,
Node.js 22.23.4, npm 10.9.9 và Docker 29.7.2. Đây là môi trường đã quan sát,
không phải tuyên bố rằng mọi phiên bản thành phần đều bắt buộc đúng tuyệt đối.

## Chuẩn bị cấu hình local

Từ thư mục root:

```powershell
Copy-Item .env.example .env
```

Cập nhật mật khẩu local trong `.env`. Không commit `.env`.

## Build và test

Chạy toàn bộ Maven reactor:

```powershell
mvn clean test
```

Chỉ kiểm thử mining-core:

```powershell
mvn -pl mining-core test
```

Chỉ kiểm thử backend và tự build dependency mining-core:

```powershell
mvn -pl backend -am test
```

Đóng gói toàn bộ Java modules:

```powershell
mvn clean package
```

## Chạy thuật toán mining

Hai runner dùng cấu hình bất biến trong source, không yêu cầu nhập tham số bằng
terminal. Trước khi chạy dataset khác, chỉnh các hằng cấu hình trong runner và
ghi nhận thay đổi để thí nghiệm có thể tái lập.

### WSPM_PreTree

```powershell
Set-Location mining-core
mvn test-compile
java -cp target/classes vn.edu.wspm.experiments.MainWspmPreTree
```

### WSPM_BitPrune

```powershell
Set-Location mining-core
mvn test-compile
java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune
```

Các runner mặc định dùng fixture Table 1 tại
`mining-core/src/test/resources/datasets/wspm/table1.spmf` và đường dẫn được
resolve tương đối từ thư mục `mining-core`.

## Chạy MySQL

Sau khi tạo `.env`:

```powershell
docker compose up -d mysql
docker compose ps
```

Dừng container:

```powershell
docker compose down
```

Dữ liệu MySQL nằm trong named volume `wspm_mysql_data`; `docker compose down`
không xóa volume. Không dùng tùy chọn `-v` nếu chưa chủ động muốn xóa dữ liệu.

> Lưu ý: Compose chỉ khởi tạo MySQL/database. Backend hiện dùng
> `spring.jpa.hibernate.ddl-auto=validate` và không tự tạo bảng. File
> `docs/database/schema_v3_draft_review.sql` vẫn là bản draft để review, không
> phải migration đã được phê duyệt.

## Chạy backend

Khi MySQL đã chạy và schema phù hợp đã được áp dụng:

```powershell
mvn -pl backend -am package -DskipTests
java -jar backend/target/backend-1.0-SNAPSHOT.jar
```

Backend mặc định chạy tại `http://localhost:8080`. Các endpoint hiện có gồm:

```text
GET /api/health
GET /actuator/health
GET /api/v1/categories
GET /api/v1/products
```

## Chạy frontend

```powershell
Set-Location frontend
npm ci
npm start
```

Frontend development server mặc định ở `http://localhost:3000` và proxy API
trong `package.json` tới `http://127.0.0.1:8080`.

Build production:

```powershell
npm run build
```

Chạy test một lần, không bật watch mode:

```powershell
npm test -- --watchAll=false
```

Trạng thái hiện tại: production build thành công nhưng có ESLint warnings; test
`src/App.test.tsx` kế thừa đang fail vì render `App` ngoài Router và còn kiểm tra
nội dung mẫu “hello world”. Đây là known frontend verification gap, không được
báo cáo test frontend là xanh trước khi có task sửa test riêng.

Frontend được kế thừa từ Electro và đang được thu hẹp theo phạm vi khóa luận;
xem `frontend/LICENSE-ELECTRO` trước khi loại bỏ attribution hoặc tài nguyên gốc.

## Dữ liệu nghiên cứu

- SPMF sequence format: `-1` kết thúc itemset, `-2` kết thúc sequence.
- Dataset lớn trong `mining-core/src/main/resources/datasets/` được ignore và
  phải được cung cấp local theo đúng nguồn/provenance.
- Fixture nhỏ phục vụ correctness tests được lưu trong Git.
- Không biến transaction database thành sequence database bằng cách tự tạo thứ
  tự nếu chưa có tài liệu hóa và phê duyệt nghiên cứu.

## Nguyên tắc correctness

Kết quả BitPrune chỉ được xem là đúng khi đồng nhất với baseline trên:

```text
pattern identity + exact support + total pattern count
```

Không dùng “cùng số lượng pattern” làm bằng chứng duy nhất. Không công bố nhanh
hơn, ít bộ nhớ hơn hoặc mở rộng tốt hơn nếu chưa có benchmark tái lập và công
bằng.

## Tài liệu

- [Architecture map](ARCHITECTURE.md)
- [Documentation map](docs/README.md)
- [Agent/research rules](AGENTS.md)
- [WSPM_PreTree contract](docs/research/wspm-pretree-spec.md)
- [PRISM Prime-block contract](docs/research/prism-prime-block.md)
- [BitPrune semantic contract](docs/research/wspm-bitprune-c01-1-semantic-contract.md)
- [BitPrune pruning-safety contract](docs/research/wspm-bitprune-c01-2-pruning-safety.md)
- [Phase C final acceptance](docs/research/wspm-bitprune-c16-final-acceptance-report.md)

## License

Mã nguồn của repository sử dụng giấy phép MIT, xem [LICENSE](LICENSE). Các bài
báo, dataset, mã tham khảo và frontend kế thừa có thể có giấy phép riêng.
