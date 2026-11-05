# Auth: chạy local, test tay và contract API

Đây là tài liệu chính của Auth và phần refactor API dùng chung. Nội dung setup,
contract và kết quả kiểm chứng trước đây được tập trung tại đây. Ba tài liệu
api-refactor cũ đã được xoá sau khi chủ dự án xác nhận; evidence và logs lịch sử
được giữ theo phần kiểm chứng bên dưới.

- [Chạy và test tay](#manual-test)
- [Vai trò của backend/scripts](#operational-scripts)
- [Contract API và kiến trúc](#api-contract)
- [Bằng chứng kiểm chứng](#verification-evidence)
- [Phạm vi commit sau khi test tay](#commit-boundary)

<a id="manual-test"></a>
## 1. Chạy và test tay trên Windows

**Không cần tạo sẵn tài khoản/mật khẩu trong SQL.** Cần có role tham chiếu
CUSTOMER trước khi đăng ký. Bạn tự đăng ký trên web; account, hash mật khẩu,
customer và liên kết CUSTOMER được backend lưu vào MySQL. ADMIN được cấp riêng
cho một account đã đăng ký, không đăng ký bằng cách gửi role từ trình duyệt.

Các lệnh PowerShell bên dưới chạy tại thư mục gốc repo, trừ bước frontend.
Đây là hướng dẫn để bạn thực hiện; việc cập nhật tài liệu không tạo account,
cấp role, ghi database hoặc thay key đang dùng.

### Bước 1 — Chuẩn bị công cụ và MySQL

Cần Java/JDK 21, Maven 3.9+, Node/npm và Docker Desktop.
Kiểm tra `java -version`, `mvn -version`, `node --version` và `docker version`.

Nếu chưa có `.env`, sao chép `.env.example`; nếu đã có thì giữ cấu hình hiện tại:

~~~powershell
if (!(Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
docker compose up -d mysql
docker compose ps
~~~

Mặc định MySQL ở `127.0.0.1:3307`, database `wspm_combo`. Nếu đã đổi `.env`,
dùng đúng giá trị MYSQL_PORT/MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD thực tế.
Docker giữ dữ liệu trong volume: khởi động lại không cần đăng ký lại account.

### Bước 2 — Một secret trong .env, không cần file key

Auth hiện dùng HS256 với **AUTH_JWT_SECRET** trong .env. Không dùng đường dẫn,
private.pem, public.pem, OpenSSL hoặc jshell. Máy local đã được cấu hình secret
ngẫu nhiên; giữ nguyên để chạy, không cần tạo lại.

Với bản clone/máy mới: copy .env.example thành .env nếu chưa có, chạy lệnh dưới
một lần rồi dán kết quả vào AUTH_JWT_SECRET trong .env:

~~~powershell
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
~~~

Secret là Base64 của ít nhất 32 byte ngẫu nhiên; thiếu/sai/ngắn sẽ làm startup
thất bại, không có secret mặc định hoặc tự sinh lúc startup. Không commit/chia sẻ
.env hoặc secret. Giữ cùng secret qua các lần restart; đổi secret làm JWT cũ
không hợp lệ. Sau chuyển từ RSA sang HS256, nên đăng xuất/đăng nhập để test lại;
refresh cookie/session còn hợp lệ vẫn có thể cấp access token mới.

Backend dùng cơ chế HMAC của [Spring Security NimbusJwtDecoder](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html)
và chỉ chấp nhận HS256; việc đổi cách ký không đổi kiểm tra account/session trong DB.

### Bước 3 — Cấu hình qua .env và chạy backend

Backend đọc file **.env tại repo root**, giống cấu hình MySQL. Cấu hình local
đã có sẵn; trên máy mới điền AUTH_JWT_SECRET theo bước 2 và giữ các dòng MYSQL_*
đang dùng. Không dùng nguyên placeholder trong ví dụ dưới đây:

~~~properties
AUTH_JWT_SECRET=<secret Base64 sinh ở bước 2>
AUTH_JWT_ISSUER=http://localhost:8080
AUTH_JWT_KEY_ID=wspm-local-1
AUTH_COOKIE_SECURE=false
AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://localhost:8080,http://127.0.0.1:8080
~~~

.env.example không chứa secret dùng được. Các biến AUTH_* ánh xạ sang auth.jwt.*,
auth.cookie-secure và auth.allowed-origins trong YAML. Không cần gõ $env:AUTH_*
mỗi lần chạy. Bỏ override cũ hoặc dùng terminal mới khi test .env; CLI/biến môi
trường tường minh vẫn có thể override file theo thứ tự ưu tiên chuẩn của Spring.

Mở terminal ở repo root và chạy:

~~~powershell
mvn -pl backend -am package -DskipTests
java -jar backend/target/backend-1.0-SNAPSHOT.jar --spring.profiles.active=local
~~~

Profile local dành cho HTTP trên máy cá nhân; giá trị AUTH_* trong .env vẫn được
ưu tiên hơn các fallback của profile. Môi trường HTTPS đặt AUTH_COOKIE_SECURE=true
và AUTH_ALLOWED_ORIGINS đúng origin HTTPS trong .env. Sửa .env cần restart backend.
Startup không tự tạo secret, role hoặc account; secret thiếu/sai vẫn làm startup thất bại.

Chờ `Started BackendApplication`. Kiểm tra
[health](http://localhost:8080/api/health): HTTP 200,
`{"status":"UP","service":"wspm-backend"}`.
Backend áp dụng Flyway khi startup rồi Hibernate validate. Không sửa/chạy lại
file migration V2 đã áp dụng; không tự tạo 45 bảng bằng tay.
Lệnh package ở trên bỏ qua tests, không phải bằng chứng kiểm thử.

### Bước 4 — Kiểm tra role tham chiếu trong MySQL

Mở Workbench vào đúng MySQL local, dùng tài khoản có quyền phù hợp và chọn
database đang cấu hình. Với cấu hình mặc định:

~~~sql
USE wspm_combo;
SELECT DATABASE();
SELECT role_id, code, name
FROM ROLE
WHERE LOWER(code) IN ('customer', 'admin');
~~~

Nếu đã có code **đúng chữ hoa** CUSTOMER/ADMIN thì giữ nguyên. Nếu chỉ có
`customer`/`admin` khác casing, dừng để kiểm tra, không tự đổi code.
Nếu thiếu role, chạy riêng câu tương ứng; đây chỉ là reference data, không tạo user:

~~~sql
INSERT INTO ROLE(role_id, code, name, created_at)
SELECT UUID(), 'CUSTOMER', 'Customer', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ROLE WHERE CAST(code AS BINARY) = CAST('CUSTOMER' AS BINARY)
);

INSERT INTO ROLE(role_id, code, name, created_at)
SELECT UUID(), 'ADMIN', 'Admin', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ROLE WHERE CAST(code AS BINARY) = CAST('ADMIN' AS BINARY)
);
~~~

Chỉ CUSTOMER là bắt buộc để đăng ký. ADMIN cần khi test phân quyền.
Không chạy SQL chèn mật khẩu/plaintext/password_hash hoặc seed account mặc định.

### Bước 5 — Chạy frontend

Mở terminal thứ hai, từ repo root:

~~~powershell
Set-Location frontend
npm ci
$env:REACT_APP_WDM_API_BASE_URL = '/api'
npm start
~~~

Nếu npm ci báo xung đột peer dependency của frontend kế thừa, thử
`npm ci --legacy-peer-deps`; không đổi version framework trong task Auth.
Giữ frontend ở **http://localhost:3000**, backend ở 8080. Trình duyệt dùng frontend
origin và proxy `/api`; không đặt WDM API root thành `http://localhost:8080/api`.
Không trộn localhost với 127.0.0.1 trong cùng phiên vì cookie thuộc host khác nhau.

### Bước 6 — Đăng ký và đăng nhập CUSTOMER

Mở [đăng ký](http://localhost:3000/signup). Tự tạo account, ví dụ:

| Trường | Giá trị minh hoạ |
|---|---|
| Tên tài khoản | `manual.customer` |
| Email | `manual.customer@example.test` |
| Tên hiển thị | Khách test tay |
| Mật khẩu | `ManualTest123!` — chỉ là ví dụ local, bạn có thể chọn khác |

Nếu tên/email đã tồn tại, dùng tên/email mới hoặc đăng nhập account cũ; không xoá
account để test lại. Username được trim/lowercase; đăng nhập bằng username,
không phải email. Mật khẩu không tự trim, ít nhất 8 Unicode code points,
tối đa 72 byte UTF-8, không NUL; lưu bằng bcrypt cost 12.

Kết quả: POST register trả 201, giao diện báo tạo tài khoản và yêu cầu đăng nhập,
**không tự login**; emailVerified=false là đúng hiện trạng, chưa có xác minh email.
Mở [đăng nhập](http://localhost:3000/signin), nhập username/password vừa tạo.
Kết quả: tới [tài khoản](http://localhost:3000/user), thấy identity và badge CUSTOMER.
F5 vẫn khôi phục phiên nếu refresh cookie còn hợp lệ.

Có thể kiểm tra dữ liệu đã lưu bằng truy vấn không lấy hash/token:

~~~sql
SELECT u.user_id, u.username, u.email, u.status,
       GROUP_CONCAT(r.code ORDER BY r.code) AS roles
FROM USER_ACCOUNT u
LEFT JOIN USER_ROLE ur ON ur.user_id = u.user_id
LEFT JOIN ROLE r ON r.role_id = ur.role_id
WHERE u.username IN ('manual.customer', 'manual.admin')
GROUP BY u.user_id, u.username, u.email, u.status;
~~~

Account ở USER_ACCOUNT; CUSTOMER liên kết user; USER_ROLE chứa quyền.
USER_SESSION được tạo khi login, chỉ lưu hash refresh token.
Các account `e2e.*`, `ui.*`, `wdm.*`, `session.customer` trong test tự động
thuộc database/container tạm đã dọn, không phải account mặc định để đăng nhập local.

### Bước 7 — Tạo account ADMIN để test phân quyền

Đăng xuất CUSTOMER trong menu tài khoản trên header. Đăng ký account thứ hai,
ví dụ `manual.admin` / `manual.admin@example.test`, tự chọn mật khẩu.
Account này ban đầu **vẫn là CUSTOMER**. Không có API public tự cấp ADMIN.

Trong Workbench, xác minh đúng database và đúng account của bạn:

~~~sql
SET @target_username = 'manual.admin';
SELECT user_id, username, status
FROM USER_ACCOUNT WHERE username = @target_username;
SELECT role_id, code FROM ROLE WHERE CAST(code AS BINARY) = CAST('ADMIN' AS BINARY);
~~~

Nếu account ACTIVE và role ADMIN tồn tại, với bootstrap local do bạn quản lý,
có thể dùng assigned_by=NULL. Khi có account operator thực tế, dùng user_id của
operator thay NULL. Thực hiện từng khối, không commit trước khi kiểm tra số dòng:

~~~sql
SET @target_user_id = (
    SELECT user_id FROM USER_ACCOUNT WHERE username = @target_username
);
SET @assigned_by = NULL;
START TRANSACTION;
INSERT INTO USER_ROLE(user_id, role_id, assigned_at, assigned_by)
SELECT u.user_id, r.role_id, UTC_TIMESTAMP(6), @assigned_by
FROM USER_ACCOUNT u CROSS JOIN ROLE r
WHERE u.user_id = @target_user_id
  AND CAST(u.status AS BINARY) = CAST('ACTIVE' AS BINARY)
  AND CAST(r.code AS BINARY) = CAST('ADMIN' AS BINARY);
SELECT ROW_COUNT() AS inserted_rows;
~~~

Chỉ khi inserted_rows=1 mới chạy `COMMIT;`. Nếu 0 dòng hoặc lỗi duplicate/FK,
chạy `ROLLBACK;` và kiểm tra lại; nếu ADMIN đã cấp từ trước thì không cấp lần nữa.
Sau commit, đăng nhập account này tại [Admin](http://localhost:3000/admin).
Kết quả: tới `/admin/datasets`; `/admin/account` hiển thị identity có ADMIN.
Không cần restart backend. Quyền hiện tại được đọc từ database khi kiểm tra phiên.

### Bước 8 — Checklist test tay

Mở DevTools → Network, lọc `/api/v1/auth` để xem status/envelope;
Application → Cookies để xem thuộc tính cookie. Không chia sẻ token/cookie thật.

| Thao tác | Kết quả mong đợi |
|---|---|
| Đăng ký hợp lệ | register 201; CUSTOMER, ACTIVE; chưa tự login |
| Đăng ký trùng username hoặc email | 409 IDENTITY_CONFLICT, không tạo thêm account |
| Mật khẩu quá ngắn | UI chặn; backend trực tiếp trả 400 VALIDATION_FAILED |
| Đăng nhập sai mật khẩu | 401 AUTHENTICATION_REQUIRED, thông điệp chung |
| Đăng nhập đúng | csrf 200 → login 200 → me 200; identity đúng |
| F5 tại /user | Cookie refresh giúp khôi phục, sau đó me xác minh identity |
| CUSTOMER vào /admin | Báo thiếu quyền ADMIN; backend Admin API từ chối 403 |
| Ẩn danh vào /admin | Yêu cầu đăng nhập; backend protected API trả 401 |
| ADMIN vào /admin/account | Hiển thị identity và quyền từ DB |
| Hai tab cùng origin | Phiên đồng bộ; logout ở một tab làm tab kia mất phiên |
| Đăng xuất từ header | logout 204, mất truy cập /user; F5 không đăng nhập lại |
| Đóng/mở frontend hoặc backend | Account vẫn ở MySQL; khôi phục phụ thuộc cookie/session/secret còn hợp lệ |

Trên HTTP local, refresh cookie có HttpOnly, SameSite=Lax, Path=/api/v1/auth,
**không Secure** theo cấu hình local; HTTPS mới kiểm tra Secure=true.
Access token ở bộ nhớ, không ở localStorage/sessionStorage. Client lấy CSRF mới
trước các Auth POST; bootstrap chưa login có thể gặp refresh 401, đây là trạng thái
ẩn danh bình thường. Logout không xoá account khỏi database.

Bước test Auth không yêu cầu upload dataset, chạy mining hoặc có export UI.
Các màn legacy chưa nối API sẽ báo chưa hỗ trợ; đó không phải bằng chứng login lỗi.
Sau khi test, gửi kết quả các dòng PASS/FAIL và lỗi có requestId nếu có.

### Lỗi thường gặp

| Hiện tượng | Kiểm tra |
|---|---|
| Backend không startup | Đúng JDK, MySQL; các AUTH_* trong .env không trống; AUTH_JWT_SECRET là Base64 ít nhất 32 byte, issuer/kid đúng; không có override cũ trong shell |
| Register 503 | Role CUSTOMER có tồn tại đúng casing không; storage có sẵn không |
| Login 401 | Dùng username; đúng mật khẩu/host; account ACTIVE |
| Auth POST 403 CSRF_REJECTED | Origin hợp lệ port 3000 hoặc qua CRA proxy (port 8080); cookie HTTP local không bật Secure; không trộn host |
| UI báo API khác origin | WDM API root=/api; restart frontend sau khi đổi env |
| CUSTOMER không vào Admin | Đúng chính sách; cấp ADMIN riêng cho account mục tiêu |
| Role đã cấp nhưng UI chưa thấy | Đăng xuất/đăng nhập lại hoặc dùng nút Kiểm tra lại quyền ở màn từ chối |
| Frontend legacy gọi port 8085 | Chức năng cũ chưa nối; test Auth qua /signup, /signin, /user, /admin |

<a id="operational-scripts"></a>
## 2. Vì sao có backend/scripts?

Hai file `backend/scripts/auth/provision-customer-role.sql` và
`backend/scripts/auth/grant-admin-role.sql` là **mẫu SQL vận hành** có placeholder
`?` để caller bind qua PreparedStatement. Chúng không nằm trong Flyway,
không được startup tự chạy, không seed account/password.

- provision-customer-role: tạo reference CUSTOMER khi thiếu, sau khi review DB.
- grant-admin-role: cấp ADMIN cho account ACTIVE đã tồn tại, trong transaction;
  yêu cầu đúng một dòng, không tự tạo role ADMIN hoặc account.

Đặt trong backend/scripts thể hiện chúng là công cụ phục vụ backend; docs chứa
hướng dẫn và phiên bản dùng biến SQL cho Workbench ở trên. Không dán nguyên file
có `?` vào Workbench. Có thể chuyển chúng sang docs nếu chủ dự án chọn duy trì
như tài liệu tham khảo, nhưng phải cập nhật caller/liên kết và duyệt việc chuyển;
lượt gom tài liệu này **giữ nguyên hai script**, tránh vừa có hai bản vừa lệch SQL.

<a id="api-contract"></a>
## 3. API contract và kiến trúc

### Response và endpoint

Business JSON thành công: `{code:"SUCCESS",message:"Request completed successfully.",data,requestId}`.
requestId do server tạo và khớp header X-Request-Id; frontend unwrap data đúng
một lần, kiểm tra envelope rồi kiểm tra DTO. Không fallback JSON business cũ.
HTTP status thể hiện kết quả, không trả lỗi bằng 200. Danh sách rỗng là data=[].

| Endpoint dưới /api/v1/auth | Request | Success data/status |
|---|---|---|
| GET /csrf | Không credential | 200 {headerName,token} và CSRF cookie |
| POST /register | username,email,password,displayName + CSRF | 201 IdentityResponse |
| POST /login | username,password + CSRF | 200 TokenResponse, set refresh cookie |
| POST /refresh | Body rỗng + cookie refresh/CSRF | 200 TokenResponse, rotate cookie |
| GET /me | Bearer access JWT | 200 IdentityResponse từ DB |
| POST /logout | Body rỗng + refresh/CSRF cookies | 204, không body |

IdentityResponse: userId, username, email, displayName, status, roles, emailVerified.
TokenResponse: accessToken, tokenType="Bearer", expiresIn=900, expiresAt, user.
IDs là string opaque; timestamp UTC ISO-8601. Không trả password/hash/refresh token.
Unknown fields, kể cả role/roles/status/userId/passwordHash, bị từ chối.
Category/product JSON cũng dùng ApiResponse; không thay DTO/phân trang khi bọc.
WDM BIGINT/count đã quy định string vẫn giữ string.

Ngoại lệ: GET /api/health giữ raw {status,service}; logout 204 và HEAD không body;
CSV export giữ bytes/content type/attachment/length, không bọc JSON.
Chỉ GET /health của WDM client được chọn raw-health; download đi nhánh Blob.
Actuator không tự được mở quyền hoặc bọc response bởi refactor.

Lỗi giữ shape `{status,code,message,path,requestId,details}`; status khớp HTTP,
path không chứa query, details luôn array {field,code,message}. Không đưa raw
exception, SQL, rejected values, PII hoặc secret vào response/log. Unknown key
dùng field body/query an toàn. Validation generic sắp theo field/code/message.

| ErrorCode | HTTP | Safe default message |
|---|---|---|
| VALIDATION_FAILED | 400 | Request validation failed. |
| MALFORMED_REQUEST | 400 | Request body or transport is invalid. |
| AUTHENTICATION_REQUIRED | 401 | Authentication is required. |
| ACCESS_DENIED | 403 | Access is denied. |
| CSRF_REJECTED | 403 | Request origin or CSRF token was rejected. |
| RESOURCE_NOT_FOUND | 404 | Resource was not found. |
| METHOD_NOT_ALLOWED | 405 | Request method is not supported. |
| NOT_ACCEPTABLE | 406 | Requested response media type is not supported. |
| IDENTITY_CONFLICT | 409 | Account identity is already in use. |
| PAYLOAD_TOO_LARGE | 413 | Export exceeds configured limits; select a smaller period. |
| UNSUPPORTED_MEDIA_TYPE | 415 | Request media type is not supported. |
| INTERNAL_ERROR | 500 | Request could not be completed. |
| SERVICE_UNAVAILABLE | 503 | Service is temporarily unavailable. |

Giữ code/status hiện hữu; thông điệp nghiệp vụ có thể override bằng text an toàn.
Detail codes gồm INVALID_VALUE, UNKNOWN_FIELD, INVALID_TIMESTAMP, INVALID_PERIOD.
Không map mọi IllegalArgumentException thành 400 hoặc mọi lỗi unique thành 409;
registration chỉ nhận diện đúng constraint identity; lỗi storage phù hợp là 503.

### Thành phần và bất biến

- common/api: ApiResponse, ApiErrorResponse, FieldErrorDetail bất biến.
- exception: ErrorCode quyết định cặp status/code; ApplicationException mang
  lỗi nghiệp vụ an toàn; GlobalExceptionHandler xử lý MVC API, không áp vào legacy.
- web/common: RequestIdFilter trước firewall/guards, UUID mới từ server (không
  tin inbound ID), một request attribute giữ qua dispatch; ApiErrorWriter dùng
  cùng DTO/ID cho lỗi trước MVC. Không handler gọi handler khác.
- Giữ Allow cho 405, no-store cho Auth/WDM, Bearer challenge trên protected 401.
  Chỉ ghi lỗi khi response chưa committed; không nối JSON vào CSV đang gửi.
  Lỗi bất ngờ log một lần tại tầng có context, stack trace được làm sạch.
- web/auth: strict parsing qua AuthJson để tránh log credential; CSRF/Origin,
  cookie và shared authenticator. Không thay bằng bind entity hoặc parsing dễ dãi.
- JWT access 15 phút, HS256/at+jwt, Base64 secret >=32 byte, kid hiện tại, issuer,
  audience wspm-api, sub/sid/jti/iat/nbf/exp và zero clock skew. Không key URL/discovery
  từ token, không fallback secret; không chấp nhận RS256 hoặc thuật toán khác. Mỗi protected request kiểm tra DB account/session/roles.
- Refresh ngẫu nhiên 256 bit; chỉ lưu SHA-256; rotate mỗi lần; expiry tuyệt đối
  bảy ngày từ login. Token cũ bị từ chối, không có replay-family revocation/grace retry.
  Giữ account-before-session lock và ranh giới TransactionTemplate/commit.
- Logout cookie thiếu/cũ vẫn 204; không revoke session khác. Chỉ clear cookie sau
  storage thành công; logout phía client vẫn xoá credentials/cache ngay và báo nếu
  chưa xác nhận được server. Request đã được cho phép trước commit không bị thu hồi ngược.
- ApiSecurityFilter giữ firewall/default-deny; GET/HEAD health/products/categories
  công khai; Auth chỉ sáu path/method; /me xác thực trước disclosure lỗi method.
  /api/v1/admin/** qua ADMIN guard trước lookup/query validation; provider phải đúng
  một bean (không chọn @Primary để che nhiều provider). Không CORS allow-all.
  Unknown API deny 401/403; path mơ hồ bị firewall/Tomcat từ chối, lỗi hạ tầng có
  thể không mang envelope ứng dụng. Không tự mở endpoint mới ngoài policy.
- Lombok chỉ giảm accessor/injection/logging; không @Data trên entity hoặc sinh
  toString lộ dữ liệu. MapStruct dùng config Spring constructor injection và
  unmappedTargetPolicy=ERROR; IdentityMapper nhận roles đã resolve, không query/cấp quyền.
- frontend/src/api/common không phụ thuộc Auth/WDM state. AuthSession/UI nhận
  DTO cũ; WDM error types là alias dùng chung, Blob/abort/no-replay giữ nguyên.
  Access chỉ ở memory; Web Locks/queue serialize auth operations, BroadcastChannel
  chỉ truyền sự kiện không token. Không có Web Locks chỉ bảo đảm queue cục bộ.
  Identity xác minh qua /me, không tin localStorage/decoded JWT; cache/cart/request
  được dọn khi mất phiên. Legacy port 8085 không được tự chuyển sang API mới.

<a id="verification-evidence"></a>
## 4. Bằng chứng kiểm chứng và giới hạn

Các số liệu dưới đây là **lịch sử đã chạy trước thay đổi HS256**, không phải tests chạy lại trong
lượt gom tài liệu. Checklist local ghi AUTH-01–06 owner accepted, BLK-01 CLOSED
ngày 2026-11-05. Test tay trên môi trường của chủ dự án sau refactor còn chờ.

| Mốc | Bằng chứng |
|---|---|
| Baseline trước refactor | Backend 138 PASS; mining-core 1.182 PASS; frontend 96 PASS + 2 HTTP opt-in skip; types PASS |
| Refactor: focused unit/web | 45 PASS |
| Refactor: full reactor ban đầu | Backend 142 PASS; mining-core 1.182 PASS; không failure/error/skip |
| Refactor: backend cuối, gồm extraction/validation | 144 PASS, không failure/error/skip; MySQL/key biệt lập |
| Refactor: frontend cuối | 107 PASS + 2 HTTP opt-in skip; 9 suites PASS; types PASS |
| Build | Maven package PASS (-DskipTests là build); frontend CI=false PASS với warnings cũ |
| HTTPS browser/HTTP | Edge 156.0.4314.58 10/10 PASS; cookie/session/multi-tab/CSV; HTTP envelope/HEAD/raw health/406 PASS; CRA proxy PASS |

AUTH-05 có kiểm tra HTTP thật qua Node proxy trước browser gate; không gọi đó là
browser E2E. Browser gate dùng app thật và harness chỉ dành cho test để kiểm tra
race/download, không đưa hook/route test vào bundle ứng dụng. CSV download có 13
cột và dữ liệu ORDER_ITEM lịch sử; không đồng nghĩa business export UI đã hoàn tất.
Fault-injected 503/transport không phải outage vật lý.
Production TLS/deployment, browser khác, exactly-once giữa tab không Web Locks,
100.000-record capacity và CI=true build sau refactor: NOT VERIFIED.
Baseline CI=true thất bại vì warning ESLint cũ thành error; không đổi policy để che.

Raw evidence cũ giữ trong .agent-workspace/phase-wdm và
.agent-workspace/api-refactor (ignored). Các file cuối gồm backend-refactor.log,
frontend-final.log, typecheck-final.log, build-final.log, package-refactor.log,
browser-gate.log, public-contract.log và browser/AUTH-06_BACKEND.log.
Baseline lưu status/HEAD/hash và runner run-baseline.mjs trong api-refactor.
Không xoá bằng chứng chỉ vì tài liệu đã gom.

Kiểm tra lại automated gate cần đúng môi trường biệt lập. Runner hiện tại
frontend/e2e/run.mjs tạo container MySQL, key/TLS tạm, chạy app/browser và dọn
tài nguyên nó sở hữu; không ghi fixtures vào DB phát triển. Full-context backend
tests phải có datasource và key test tường minh, không chạy bằng default dev DB.
Từ repo root, sau khi build backend và frontend tương ứng, có thể dùng:

~~~powershell
$env:AUTH06_REGRESSION = '1'
$env:AUTH_E2E_LOG_DIR = '.agent-workspace/api-refactor/browser'
node frontend/e2e/run.mjs
~~~

Runner yêu cầu dependency e2e đã cài; xem [frontend README](../../frontend/README.md)
cho lệnh cài/build và điều kiện browser. Chọn thư mục log mới nếu cần giữ một
lượt kiểm chứng khác; không ghi đè evidence đã chọn giữ.

<a id="commit-boundary"></a>
## 5. Phạm vi commit sau khi chủ dự án test tay

**Chưa stage/commit trong lượt tài liệu này.** Chỉ chuẩn bị commit sau khi chủ
dự án báo kết quả test. Không dùng git add . hoặc stage cả backend/frontend.

| Nhóm | Cách xử lý dự kiến khi chuẩn bị commit |
|---|---|
| Shared Auth | controllers/dto/services/repositories/auth, config/auth, security/jwt, guards/cookies, entities Auth cần thiết, SQL provisioning |
| API refactor | common/api, exception, web/common, MapStruct/Lombok, product/category/Auth responses và tests |
| Frontend Auth | auth/session/UI, guards/header/store compatibility, common parser và consumer/tests bị ảnh hưởng |
| WDM-00/Auth-04 seam | Chỉ phần provider/authenticator/guard bắt buộc để Auth/refactor build độc lập; review dependency thực tế |
| WDM-01 đang làm | Chưa stage export repository/service/CSV/controller, fixtures và tests nghiệp vụ export |
| File trộn | pom.xml, .gitignore, README/ARCHITECTURE và WDM client/types/tests/e2e: tách hunk, không commit nguyên file mặc định |

Đặc biệt WdmAuthorizationIntegrationTest và browser export cases gọi nghiệp vụ
WDM-01; không đưa nguyên bộ vào commit Auth nếu thiếu các implementation đó.
Working tree build xanh không chứng minh riêng commit Auth/refactor build được.
Khi chuẩn bị commit cần kiểm tra nội dung stage và build/test từ đúng snapshot
sẽ commit; kết quả tách commit hiện là NOT VERIFIED. Nếu một đoạn không tách độc
lập được, báo dependency thay vì đưa lén WDM-01 vào commit.

Không thay mining-core, V1/V2 hoặc semantics threshold vì dọn/tách Auth.
R6 và exact-real comparator equivalence vẫn mở như trước.
