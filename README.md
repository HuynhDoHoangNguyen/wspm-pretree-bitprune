# WSPM_PreTree & WSPM_BitPrune

Repository cho khóa luận tốt nghiệp: **“Nghiên cứu cải tiến thuật toán WSPM_PreTree và xây dựng hệ thống gợi ý combo sản phẩm.”** 

Dự án gồm:
- **`mining-core`**: Lõi thuật toán khai phá mẫu tuần tự có trọng số (WSPM_PreTree baseline và WSPM_BitPrune cải tiến).
- **`backend`**: REST API viết bằng Spring Boot 4.x / Java 21, quản lý dữ liệu sản phẩm, đơn hàng, kích hoạt thuật toán và gợi ý combo.
- **`frontend`**: Giao diện ứng dụng người dùng và quản trị viên viết bằng React 17, TypeScript và Mantine UI.

---

## 🚀 Hướng dẫn khởi chạy hệ thống (Dành cho thành viên mới)

> [!TIP]
> Bạn chỉ cần làm theo **lần lượt 4 bước bên dưới** là có thể chạy được toàn bộ hệ thống từ Database, Migration, Backend tới Frontend.

---

### Yêu cầu phần mềm cài đặt sẵn (Prerequisites)

Trước khi bắt đầu, hãy đảm bảo máy tính đã cài đặt các công cụ sau:

1. **Git**: Để tải và đồng bộ mã nguồn.
2. **Java JDK 21**: Kiểm tra bằng lệnh:
   ```powershell
   java -version
   ```
3. **Apache Maven 3.9+**: Kiểm tra bằng lệnh:
   ```powershell
   mvn -version
   ```
4. **Docker Desktop**: Cần mở ứng dụng Docker Desktop lên trước khi chạy lệnh. Kiểm tra bằng lệnh:
   ```powershell
   docker -v
   ```
5. **Node.js (v18 - v22) & npm**: Kiểm tra bằng lệnh:
   ```powershell
   node -v
   npm -v
   ```

---

### BƯỚC 1: Chuẩn bị file cấu hình môi trường (`.env`)

Mở terminal (PowerShell trên Windows hoặc Bash trên Linux/macOS) tại **thư mục gốc của dự án** (`wspm-pretree-bitprune`), chạy lệnh:

- **Trên Windows (PowerShell):**
  ```powershell
  Copy-Item .env.example .env
  ```
- **Trên Linux / macOS:**
  ```bash
  cp .env.example .env
  ```

> [!NOTE]
> File `.env` chứa các thông số mặc định kết nối MySQL cục bộ:
> - Cổng: `3307`
> - Tên Database: `wspm_combo`
> - Tài khoản: `wspm_app` / Mật khẩu: `123456`
> 
> Với môi trường dev cá nhân, bạn **không cần thay đổi gì thêm** trong file `.env`.

---

### BƯỚC 2: Khởi động Cơ sở dữ liệu MySQL (Docker Compose)

Đảm bảo phần mềm **Docker Desktop** đang chạy (icon Docker chuyển sang màu xanh), sau đó gõ lệnh tại thư mục gốc:

```powershell
# 1. Bật container MySQL chạy nền
docker compose up -d mysql

# 2. Kiểm tra container đã hoạt động chưa
docker compose ps
```
Nếu thấy cột `STATUS` hiển thị `Up` (ví dụ `Up 10 seconds`), MySQL đã sẵn sàng tại cổng `3307`.

* **Khi muốn tạm dừng Database:**
  ```powershell
  docker compose down
  ```
  *(Dữ liệu database được lưu an toàn trong Docker volume `wspm_mysql_data`, không bị mất khi tắt).*

---

### BƯỚC 3: Build và Chạy Backend (Tự động chạy Migration)

> [!IMPORTANT]
> **Bạn KHÔNG CẦN phải mở MySQL Workbench để chạy script tạo bảng thủ công!**
> 
> Hệ thống tích hợp sẵn **Flyway Migration** và quét `classpath:db/migration` khi
> Backend khởi động. Nhánh hiện tại chứa `V1__initial_schema.sql` và
> `V2__complete_laptop_combo_schema.sql`; V2 chuyển schema 12 bảng của V1 sang
> schema đích 45 bảng. Kiểm tra read-only database local ngày 2026-11-03 xác nhận
> V1 và V2 đều có `success = 1` trong `flyway_schema_history`; checksum V2
> `727165286` khớp file runtime, đủ đúng 45 bảng và không còn bảng
> `LEGACY_V1_*`. Trạng thái database khác vẫn phải kiểm tra riêng; không chạy lại
> hoặc sửa file V2 đã áp dụng.

Tại thư mục gốc của dự án, chạy 2 lệnh sau:

#### 1. Biên dịch và đóng gói Backend:
```powershell
mvn -pl backend -am package -DskipTests
```
*(Lệnh này sẽ tự động biên dịch thuật toán `mining-core` và đóng gói ứng dụng `backend`).*

#### 2. Khởi chạy Backend:
```powershell
java -jar backend/target/backend-1.0-SNAPSHOT.jar
```
Khi thấy log xuất hiện dòng chữ:
```text
Started BackendApplication in X.XXX seconds
```
Backend đã khởi động thành công tại cổng `8080`.

#### 3. Kiểm tra Backend:
Mở trình duyệt web và truy cập các liên kết sau:
- Kiểm tra sức khỏe hệ thống: [http://localhost:8080/api/health](http://localhost:8080/api/health) (kết quả trả về `{"status":"UP", ...}`)
- Danh sách ngành hàng: [http://localhost:8080/api/v1/categories](http://localhost:8080/api/v1/categories)
- Danh sách sản phẩm: [http://localhost:8080/api/v1/products](http://localhost:8080/api/v1/products)

---

### BƯỚC 4: Khởi chạy Giao diện Frontend

Mở **một cửa sổ Terminal mới** (để cửa sổ Backend ở Bước 3 tiếp tục chạy), sau đó thực hiện:

#### 1. Di chuyển vào thư mục frontend:
```powershell
Set-Location frontend
# hoặc: cd frontend
```

#### 2. Cài đặt các thư viện phụ thuộc:
```powershell
npm ci
```
*(Nếu gặp lỗi xung đột phiên bản thư viện peer dependencies, dùng lệnh: `npm install --legacy-peer-deps`)*

#### 3. Chạy giao diện ở môi trường phát triển:
```powershell
npm start
```
Trình duyệt sẽ tự động mở trang web tại địa chỉ: **[http://localhost:3000](http://localhost:3000)**.
Mọi request gọi API từ frontend sẽ tự động được chuyển tiếp (proxy) sang Backend tại cổng `8080`.

---

## 🛠️ Các câu lệnh hữu ích khác

### Chạy kiểm thử tự động (Unit / Integration Tests)
```powershell
# Chạy toàn bộ test cả hệ thống
mvn test

# Chỉ chạy test thuật toán khai phá mẫu (mining-core)
mvn -pl mining-core test

# Chỉ chạy test backend (controller, service, repository, entity validation)
mvn -pl backend -am test
```

### Chạy trực tiếp thuật toán Mining mẫu trong terminal
Hai runner dưới đây sử dụng tập dữ liệu chuẩn Table 1 từ bài báo gốc (`mining-core/src/test/resources/datasets/wspm/table1.spmf`):

- **Chạy WSPM_PreTree (Thuật toán gốc):**
  ```powershell
  Set-Location mining-core
  mvn test-compile
  java -cp target/classes vn.edu.wspm.experiments.MainWspmPreTree
  ```

- **Chạy WSPM_BitPrune (Thuật toán cải tiến):**
  ```powershell
  Set-Location mining-core
  mvn test-compile
  java -cp target/classes vn.edu.wspm.bitprune.experiments.MainWspmBitPrune
  ```

---

## 📊 Kiến trúc và Công nghệ

| Phân hệ | Công nghệ sử dụng | Vai trò |
|---|---|---|
| **Build System** | Java 21, Maven Multi-module | Quản lý vòng đời build đồng bộ cho cả dự án |
| **Mining Core** | Java Core, JUnit Jupiter 5 | Hiện thực WSPM_PreTree và WSPM_BitPrune tối ưu |
| **Backend** | Spring Boot 4.1.2, Spring Data JPA, Web MVC | Xử lý nghiệp vụ, quản lý Entity, ánh xạ DTO, REST API |
| **Database & Migration**| MySQL 8.4 (Docker), Flyway | Lưu trữ dữ liệu quan hệ, tự động migrate schema 12 bảng |
| **Frontend** | React 17, TypeScript 4.6, Create React App | Xây dựng giao diện Single-Page Application (SPA) |
| **UI Components** | Mantine UI 4, Tabler Icons, React Query 3, Zustand | Hệ thống component UI, quản lý state và gọi API |

---

## 🔬 Nghiên cứu và Độ tin cậy thuật toán (Correctness)

- Dữ liệu tuần tự định dạng SPMF: `-1` kết thúc một itemset, `-2` kết thúc một chuỗi sequence.
- Thuật toán cải tiến **WSPM_BitPrune** bắt buộc phải thỏa mãn tính đúng đắn tuyệt đối so với **WSPM_PreTree** trên cùng ngưỡng:
  ```text
  pattern identity + exact support + total pattern count
  ```
- Không chấp nhận việc chỉ so sánh số lượng pattern; bắt buộc kiểm tra đồng nhất toàn bộ tập mẫu và độ hỗ trợ chính xác `pattern -> exact support`.

---

## 📚 Tài liệu kỹ thuật chi tiết

- [Bản đồ cấu trúc hệ thống (ARCHITECTURE.md)](ARCHITECTURE.md)
- [Bản đồ tài liệu nghiên cứu (docs/README.md)](docs/README.md)
- [Quy chuẩn thực thi và nguyên tắc nghiên cứu (AGENTS.md)](AGENTS.md)
- [Đặc tả thuật toán WSPM_PreTree](docs/research/wspm-pretree-spec.md)
- [Đặc tả mã hóa Prime-block PRISM](docs/research/prism-prime-block.md)
- [Hợp đồng ngữ nghĩa WSPM_BitPrune](docs/research/wspm-bitprune-c01-1-semantic-contract.md)
- [Báo cáo nghiệm thu Phase C WSPM_BitPrune](docs/research/wspm-bitprune-c16-final-acceptance-report.md)

---

## 📄 License
Mã nguồn của repository sử dụng giấy phép MIT, xem [LICENSE](LICENSE). Các bài báo, dataset tham khảo và mã nguồn frontend kế thừa tuân thủ giấy phép gốc tương ứng.
