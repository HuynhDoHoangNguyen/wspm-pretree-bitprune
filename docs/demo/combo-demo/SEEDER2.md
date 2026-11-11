# Chạy nhanh bằng seeder2.sql

Dùng cách này nếu muốn chạy backend/frontend bình thường ở **8080/3000**, không
cần runner HTTPS, runtime.private.json hoặc mật khẩu ngẫu nhiên.

## 1. Chạy SQL

MySQL 8.4 phải có schema hiện tại của ứng dụng (Flyway V1–V5). Nếu DB mới hoàn
toàn, khởi động backend theo bước 2 một lần để Flyway tạo bảng, rồi chạy SQL.

Trong MySQL Workbench:

1. Kết nối MySQL local, chọn đúng schema development/test, thường là `wspm_combo`.
2. Mở [seeder2.sql](../../../seeder2.sql) ở root repo.
3. Chạy **toàn bộ file** bằng Execute All, không chỉ chạy một dòng đang chọn.
4. Chờ kết quả `seeder2_status = READY`, `products = 20`.

Seeder hỗ trợ hai trường hợp:

- PRODUCT đang trống: nạp **đúng 20 sản phẩm đã chốt**, danh mục và đường dẫn ảnh.
- PRODUCT đã có đủ 20 ID/SKU đó: giữ nguyên sản phẩm, giá và ảnh đang có.

Không thêm sản phẩm thứ 21. Catalog thiếu/thừa/khác ID sẽ báo lỗi thay vì tự sửa.
SQL chỉ tạo dữ liệu đầu vào synthetic và tài khoản demo; không chèn PATTERN,
COMBO, run hay bằng chứng khai phá. Tồn kho sẵn có không bị ghi đè; kho synthetic
`SYNTHETIC-SEEDER2` chỉ bổ sung sản phẩm chưa có số dư tại kho ACTIVE.
Chạy lại cùng file không tạo trùng, không reset mật khẩu hoặc sửa dữ liệu đã có.
Nếu gặp xung đột, phần INSERT trong transaction sẽ rollback và SQL báo nguyên nhân.

Chỉ chạy trên DB local/test hoặc bản sao đã chuẩn bị. Mật khẩu dùng chung dưới
đây là mật khẩu demo công khai, không dùng cho môi trường production.

## 2. Chạy ứng dụng

Nếu đã có `.env` thì giữ file đó và kiểm tra các giá trị sau. Máy mới có thể copy
`.env.example` thành `.env` một lần, rồi chỉnh thông tin MySQL của máy mình.

```properties
MYSQL_PORT=3307
MYSQL_DATABASE=wspm_combo
MYSQL_USER=wspm_app
MYSQL_PASSWORD=<mat-khau-MySQL-cua-ban>

AUTH_JWT_SECRET=<giu-secret-hien-co-hoac-tao-secret-ben-duoi>
AUTH_JWT_ISSUER=http://localhost:8080
AUTH_JWT_KEY_ID=wspm-local-1
AUTH_COOKIE_SECURE=false
AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://localhost:8080,http://127.0.0.1:8080

WDM_EXECUTION_ENABLED=true
WDM_RESULT_PUBLICATION_ENABLED=true
```

Nếu chưa có JWT secret, tạo một lần rồi dán kết quả vào `AUTH_JWT_SECRET`:

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

PowerShell thứ nhất, tại root repo:

```powershell
# Nếu dùng MySQL của compose và container chưa chạy:
docker compose up -d mysql

# Cài mining-core vào Maven local rồi mở backend:
mvn -B -pl mining-core -am install -DskipTests
mvn -pl backend spring-boot:run
```

Nếu dùng MySQL cài riêng thì bỏ dòng docker compose và chỉnh port/user/password
cho đúng. Chờ backend báo đã chạy ở cổng 8080. DB mới: lúc này chạy bước 1.

PowerShell thứ hai, tại root repo:

```powershell
npm --prefix frontend ci
npm --prefix frontend start
```

Mở **http://localhost:3000/signin**. Giữ hai terminal chạy.

Ảnh sản phẩm không được đóng gói trong SQL. Copy thư mục ảnh của team vào
`backend/uploads/products` và đặt `PRODUCT_IMAGES_ROOT` trong `.env` thành
**đường dẫn tuyệt đối trên máy mình**; bỏ đường dẫn ổ E của máy tác giả nếu đã
copy `.env.example`. Nếu không có ảnh, hệ thống vẫn chạy với placeholder.

## 3. Đăng nhập

**Mật khẩu chung: `DemoCombo@2026`**

| Tài khoản | Quyền / lịch sử mẫu | Dùng để xem |
|---|---|---|
| `demo2.admin` | ADMIN + CUSTOMER | Upload, mining, sinh combo, review, công bố |
| `demo2.sequence` | CUSTOMER; đã mua G502 | Gợi ý tuần tự |
| `demo2.mixed` | CUSTOMER; một đơn có G502 + Bose + JBL + bàn phím Lenovo | Gợi ý hỗn hợp |
| `demo2.cold` | CUSTOMER; chưa có đơn | Gợi ý cá nhân rỗng |
| `demo2.wrong` | CUSTOMER; chỉ mua Balo Wenger | Lịch sử không khớp bước đầu |

Tài khoản `demo2.*` tách biệt với tài khoản `demo.*` của runner cũ. Không cần đăng
ký qua form, không cần lấy mật khẩu trong file private. Sau khi đăng nhập Admin
sẽ vào khu vực Admin; khách hàng vào phần mua sắm.

## 4. Làm một lần để có combo trên giao diện

**Chạy SQL xong chưa tự có combo.** Admin làm theo luồng sau; không đổi status
bằng SQL:

1. Vào **Dữ liệu khai phá**, upload [data/orders.csv](data/orders.csv).
2. Chạy validation rồi conversion. Kiểm tra **41 sequences, 7 items, 62 itemsets,
   88 occurrences**. CSV là fixture mining portable; SQL có thêm lịch sử của ba
   tài khoản để kiểm tra gợi ý cá nhân, nên tổng SQL là 65 đơn/94 dòng sản phẩm.
3. Tạo run: `WSPM_BITPRUNE` (hoặc `WSPM_PRETREE`), mode **ABSOLUTE**, minsup
   **0.001**. Chờ `SUCCEEDED` và kết quả được công bố đầy đủ.
4. Mở **Kiểm tra đầu vào Combo** từ chi tiết run. Chọn danh mục **Chuột**, quantity
   1, số sản phẩm tối thiểu **2**, tối đa **3**; để sản phẩm neo trống.
5. Nhập số combo nháp muốn lưu **20**, xem trước **20**, Top-K khách **20**.
   Xem trước rồi lưu DRAFT.
6. Mở batch, duyệt các combo cần hiển thị, sau đó **Công bố batch cho khách hàng**.
   Công bố batch mới sẽ lưu trữ batch ACTIVE cũ trong DB đang thao tác.
7. Mở **http://localhost:3000/products**: kiểm tra Top Combo mới nhất có 5 itemset.
   Đăng nhập `demo2.sequence` hoặc `demo2.mixed`, mở trang G502 bên dưới rồi bấm
   **Xem thêm gợi ý**:

```text
http://localhost:3000/products/b39d390d-6719-535c-acc6-cd6dbf08f72c
```

Checkbox ban đầu chưa tick, số lượng min 1/max tồn khả dụng; nút giỏ hàng disabled.
P05 được đặt tồn khả dụng 0 trong fixture để có trường hợp hết hàng. Nếu DB đã có
số dư tại kho ACTIVE, seeder giữ số dư đó và giới hạn số lượng theo dữ liệu đang có.

## Khi SQL báo lỗi

- Lỗi thiếu bảng/Flyway: chạy backend để áp dụng migration hiện tại trước.
- Lỗi catalog: DB phải trống sản phẩm hoặc khớp đúng 20 ID/SKU đã chốt; không chạy
  lệnh xóa/reset catalog để ép seeder qua.
- Lỗi trùng tài khoản/mật khẩu hoặc dữ liệu đã đổi: dùng DB test riêng; seeder
  chủ động không ghi đè tài khoản hay đơn hàng đó.
- Nếu lần lỗi khiến helper còn lại, và nó đúng là routine do file này vừa tạo,
  bỏ helper rồi chạy lại sau khi xử lý nguyên nhân:

```sql
DROP PROCEDURE wspm_seeder2_20261111;
```

Routine chỉ là helper seed, không phải bảng dữ liệu. Sau lượt chạy thành công,
file SQL tự bỏ helper. Không cần chạy lại SQL mỗi khi khởi động backend/frontend.
