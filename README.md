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

Auth dùng HS256 với một `AUTH_JWT_SECRET` (Base64 của ít nhất 32 byte ngẫu nhiên)
trong `.env`, cùng issuer và key ID; cookie/Origin dùng `AUTH_COOKIE_SECURE` và
`AUTH_ALLOWED_ORIGINS` trong cùng file.
Thiếu/sai cấu hình sẽ làm backend startup thất bại. Xem
[Shared Auth setup](docs/auth/README.md) trước khi chạy. Profile `local` chỉ cấu
hình HTTP cookie/Origin, không tạo secret, role hoặc tài khoản mặc định. Không cần file key.

WDM snapshot upload lưu file gốc trong `${user.home}/.wspm/snapshots` trên máy
chạy backend. Có thể đặt `WDM_SNAPSHOT_STORAGE_ROOT=E:/wspm-data/snapshots` trong
`.env`; dùng đường dẫn tuyệt đối nằm ngoài cây làm việc của ứng dụng. Tài khoản
chạy backend cần quyền đọc/ghi riêng tại đó. Thư mục được tạo khi upload đầu tiên,
không được public qua web. Giữ storage và metadata MySQL cùng nhau khi backup;
đổi cấu hình không tự di chuyển artifact đã lưu. Chi tiết response, giới hạn
upload và xử lý lỗi: [CSV/snapshot contract](docs/wdm/dataset-csv-contract.md).

WDM-03.5 bổ sung service nội bộ lưu `sequences.spmf` và `mapping.json` dưới
`<snapshotId>/conversions/<attemptId>/` trong cùng private storage root. Mỗi file
bị giới hạn bởi `WDM_CONVERSION_MAX_ARTIFACT_BYTES` (mặc định 67.108.864 bytes).
Backup phải giữ nguyên artifact cùng metadata/hash MySQL; không sửa/xóa file đã
công bố. Claim `RUNNING` bị bỏ dở hoặc commit chưa xác định cần reconciliation,
không tự chiếm lại/xóa thư mục. Endpoint conversion chưa nối service này; API
tạo/xem/tải kết quả thuộc WDM-03.6.

WDM-03.6 đã nối API Admin: `POST /api/v1/admin/dataset-snapshots/{id}/conversion`
chạy đồng bộ và trả 200 khi đã xác minh kết quả; `GET` cùng URL đọc trạng thái.
Tải artifact qua `GET .../{id}/artifacts/sequence` hoặc `.../artifacts/mapping`,
cùng shared ADMIN Bearer Auth. Không truyền body/query, token trong URL, `force`
hoặc retry FAILED/RUNNING. GET/download chỉ đọc và xác minh integrity; file thiếu/
bị sửa không tự sửa hoặc chuyển đổi lại. Giao diện conversion thuộc WDM-03.7.

WDM-05.3 bổ sung `POST /api/v1/admin/mining-runs` với đúng bốn trường cấu hình
đã chốt. Backend xác minh publication, chuẩn bị input và weights bằng mining-core,
và mặc định **trả 503, không tạo run** vì `WDM_EXECUTION_ENABLED=false`.
WDM-05.4 thêm provider thật; có thể opt-in bằng `WDM_EXECUTION_ENABLED=true`.
Mặc định `WDM_EXECUTION_PARALLELISM=1`, `WDM_EXECUTION_QUEUE_CAPACITY=1`,
`WDM_EXECUTION_SHUTDOWN_GRACE_MILLIS=30000`. HTTP 202 chỉ sau durable commit và
admission acknowledgement, không chờ mining; không tự retry POST.

Executor giữ complete result trong bộ nhớ chờ bàn giao. WDM-06.11 bổ sung consumer
publication opt-in bằng `WDM_RESULT_PUBLICATION_ENABLED=true` cùng execution đã bật.
Consumer lưu/kiểm chứng đầy đủ kết quả, commit manifest rồi mới acknowledge để trả slot.
Reservation, queued/running job và outcome chưa acknowledge cùng dùng tối đa hai
slot theo cấu hình mặc định. Hết slot trả 503 kể cả worker đã rảnh; chưa có consumer
production hoạt động khi publication tắt (mặc định). Không có TTL/discard để nhận thêm job. WDM-05.5
cập nhật DB bằng CAS QUEUED → RUNNING → SUCCEEDED/FAILED, có ownership/attempt
fencing, heartbeat và recovery run hết lease. Mặc định lease 60 giây, heartbeat
10 giây, recovery tối đa 100 run/lượt; lease không phải timeout mining.
WDM-05.6 ghi counts, timing và sampled JVM heap cùng lần chuyển SUCCEEDED.
Total đo worker refresh+engine, không gồm queue/claim/finalization DB; heap là
sample trong thuật toán. Output I/O để null khi chưa có bước ghi patterns.
SUCCEEDED chưa có nghĩa patterns đã được lưu xuống DB. Chỉ publication COMMITTED
khớp manifest được hỗ trợ mới đủ điều kiện đọc published qua restart. Outcome chưa
publication vẫn chỉ nằm trong bộ nhớ; không tự phục hồi mining sau khi mất tiến trình.
Chi tiết/evidence: [Run creation and execution contract](docs/wdm/mining-request-contract.md).

WDM-05.7 thêm `GET /api/v1/admin/mining-runs/{runId}` (shared ADMIN Bearer),
không query/body. GET đọc status/config/timestamps/metrics trong DB, hoạt động
cả khi execution tắt; counts/timing/bytes BIGINT trả chuỗi thập phân, chưa đo là
null. GET không chạy engine hoặc acknowledge kết quả. SUCCEEDED chưa xác nhận
patterns đã được lưu bền vững.

WDM-06.7 bổ sung các GET Admin dưới `/api/v1/admin/mining-runs/{runId}`:
`/results`, `/patterns`, `/patterns/{patternId}` và `/patterns/export` (JSON).
`?view=staging` phục vụ kiểm tra dữ liệu chưa công bố; không đủ điều kiện bàn giao
Combo. WDM-06.11 mở `view=published` (mặc định) sau khi kiểm chứng manifest V1.
Export staging giữ V1; export published V2 có provenance, patterns và toàn bộ
run-item statistics. UI run detail/Explorer cho tải JSON qua shared Auth. Metadata
không cần query; page nhận `page`/`size` (1/20 mặc định, size tối đa 100).
`WDM_RESULT_QUERY_MAX_EXPORT_BYTES` giới hạn byte export, mặc định 16 MiB,
phạm vi 256..67108864. Chi tiết giới hạn và evidence nằm trong request contract.

`PublishedMiningResultProvider` cung cấp đầu vào Java bất biến cho bước sinh combo,
giữ exact support và thứ tự/item lặp. Chưa sinh hoặc ghi combo bán hàng. Run có 0
pattern được công bố/export nhưng không eligible cho combo. Publication lỗi giữ
Outcome; lỗi tạm thời thử tối đa 5 lần với backoff 4/8/16/32 giây, lỗi nội dung
dừng tự động xử lý. Không có endpoint sửa dữ liệu hay retry mining tự động.

WDM-06.8 hoàn thiện tab **Lịch sử** tại `/admin/mining-runs?tab=history`:
tìm Run ID, lọc trạng thái/thuật toán/Snapshot ID, phân trang và quay lại đúng bộ
lọc từ chi tiết. Trang chi tiết hiển thị riêng trạng thái lưu/công bố và provenance
lịch sử qua `/results`. Run hoàn tất hoặc có pattern count không đồng nghĩa đã
công bố đầy đủ; UI không mở bàn giao Combo từ metadata chưa xác minh.

> [!IMPORTANT]
> **Bạn KHÔNG CẦN phải mở MySQL Workbench để chạy script tạo bảng thủ công!**
> 
> Hệ thống tích hợp sẵn **Flyway Migration** và quét `classpath:db/migration` khi
> Backend khởi động. Nhánh hiện tại chứa `V1__initial_schema.sql` và
> `V2__complete_laptop_combo_schema.sql` và `V3__mining_run_execution_ownership.sql`.
> WDM-06.2 bổ sung `V4__mining_run_metadata_provenance.sql`: hai bảng
> `RUN_PROVENANCE` và `RUN_RESULT_PUBLICATION`. Luồng tạo/chạy run mới cần V4;
> Hibernate validate riêng không xác minh hai bảng native-SQL này. V4 được kiểm
> thử trên MySQL riêng, chưa áp dụng vào DB owner trong task này. Flyway sẽ tự
> xét V4 khi chạy bản backend mới; cần triển khai migration có chủ đích trước
> khi dùng datasource owner. Pattern persistence vẫn thuộc các task tiếp theo.
> V3 bổ sung ba cột ownership/lease nullable cho MINING_RUN; đã kiểm thử upgrade
> trên MySQL riêng, chưa áp dụng vào database của owner trong task WDM-05.5.
> Trong WDM-05.7 (2026-11-07), đã xác minh database local `127.0.0.1:3307/wspm_combo`
> có V3 SUCCESS, checksum `1697070581`. Flyway validate/migrate(target=3)/validate
> PASS; migrate thực hiện 0 migration vì V3 đã tồn tại trước lượt kiểm tra này.
> Backend cần schema V3 để Hibernate validate. V2 chuyển schema 12 bảng của V1 sang
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
java -jar backend/target/backend-1.0-SNAPSHOT.jar --spring.profiles.active=local
```
Khi thấy log xuất hiện dòng chữ:
```text
Started BackendApplication in X.XXX seconds
```
Backend đã khởi động thành công tại cổng `8080`. Lệnh trên dành cho HTTP local;
cấu hình JWT được đọc từ `.env` ở repo root theo
[hướng dẫn Auth và test tay](docs/auth/README.md#manual-test).

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
