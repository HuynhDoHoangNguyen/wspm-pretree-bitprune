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
AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
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
| Auth POST 403 CSRF_REJECTED | Origin chính xác port 3000; cookie HTTP local không bật Secure; không trộn host |
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
| INVALID_RESOURCE_STATE | 409 | Resource state does not permit this operation. |
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
  RF-05 phân biệt method input validation 400 và return-value validation 500;
  NoResourceFoundException/NoHandlerFoundException đều trả RESOURCE_NOT_FOUND 404.
- web/common: RequestIdFilter trước firewall/guards, UUID mới từ server (không
  tin inbound ID), một request attribute giữ qua dispatch; ApiErrorWriter dùng
  cùng DTO/ID cho lỗi trước MVC. Không handler gọi handler khác.
- Giữ Allow cho 405, no-store cho Auth/WDM, Bearer challenge trên protected 401.
  Chỉ ghi lỗi khi response chưa committed; không nối JSON vào CSV đang gửi.
  Lỗi bất ngờ log một lần tại tầng có context, stack trace được làm sạch.
- web/auth: strict parsing qua AuthJson để tránh log credential; CSRF/Origin,
  cookie và shared authenticator. Không thay bằng bind entity hoặc parsing dễ dãi.
- RF-02: AuthRequestReader kiểm closed object/text fields sau AuthJson;
  AuthInputValidator giữ normalization/validation thuần. DTO Auth giữ dữ liệu,
  annotation email và redacted toString; RegistrationService tiếp tục kiểm tra
  direct caller trước bcrypt/transaction và thực hiện Bean Validation email.
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
frontend/test/e2e/run.mjs tạo container MySQL, key/TLS tạm, chạy app/browser và dọn
tài nguyên nó sở hữu; không ghi fixtures vào DB phát triển. Full-context backend
tests phải có datasource và key test tường minh, không chạy bằng default dev DB.
Từ repo root, sau khi build backend và frontend tương ứng, có thể dùng:

~~~powershell
$env:AUTH06_REGRESSION = '1'
$env:AUTH_E2E_LOG_DIR = '.agent-workspace/api-refactor/browser'
node frontend/test/e2e/run.mjs
~~~

Runner yêu cầu dependency e2e đã cài; xem [frontend README](../../frontend/README.md)
cho lệnh cài/build và điều kiện browser. Chọn thư mục log mới nếu cần giữ một
lượt kiểm chứng khác; không ghi đè evidence đã chọn giữ.

<a id="commit-boundary"></a>
## 5. Phạm vi commit và tích hợp

**Checkpoint lịch sử trước khi owner cho phép tích hợp:** chỉ chuẩn bị commit sau
khi chủ dự án báo kết quả test. Không dùng git add . hoặc stage cả backend/frontend.
Owner sau đó đã yêu cầu review/refactor WDM-01, commit và merge vào
`experiment/combo-recommendation-web`; đồng thời yêu cầu push cả `.env.example`
và `.vscode/launch.json`. Phạm vi tích hợp hiện gồm WDM-01 và follow-up Auth UI
đã triển khai cùng các test/docs liên quan. Bảng tách nhóm dưới đây lưu kế hoạch
cũ trước quyền tích hợp này; không còn là yêu cầu hoãn WDM-01. Staging vẫn dùng
danh sách file cụ thể, loại `frontend/.env`, keys/logs/build và dependencies.

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

## 6. Follow-up Auth UI và điều hướng Admin–Client (2026-11-05)

Owner đã chấp thuận kế hoạch chỉnh UI/điều hướng; đây không phải thay đổi API Auth,
schema, quyền truy cập hoặc lifecycle token/cookie. Sau `/me` thành công, đăng nhập
ở Client hay Admin đều dùng role hiện tại: ADMIN → `/admin/datasets`, CUSTOMER → `/`.
Header Client có Trang quản trị khi có ADMIN; header Admin có Về cửa hàng trong
cùng tab và cùng session, không truyền token trên URL. Shortcut cũ cũng tuân theo
role hiện tại thay vì hiển thị Admin cho mọi người.

Các điểm logout trên hai header, màn hình từ chối Admin và shortcut dùng chung
`useSignOut`. Session owner vẫn xóa truy cập cục bộ/cache và hủy protected requests
trước khi gọi server; UI cuối cùng đi về `/` cả khi server thất bại, kèm thông báo
chưa xác nhận logout. Mất phiên trên `/user` không giữ access wall. Trang Client
công khai vẫn cho khách xem; tài khoản/lịch sử đơn riêng tư không trở thành public.
`/cart` guest hiện đi tới đăng nhập; guest cart/checkout/order API legacy không được
triển khai hay tuyên bố hoạt động trong slice này.

Sign-in/signup dùng Paper có viền và bố cục responsive, validation từng field,
thông báo dưới ô và focus sau khi field được bật lại. Login không áp chính sách
độ dài của mật khẩu mới lên mật khẩu hiện có. Signup giữ chính sách đã chốt
(codepoint/72-byte UTF-8/không trim), thêm xác nhận mật khẩu so sánh nguyên chuỗi;
chỉ bốn field cũ được gửi lên API. Bỏ nhãn CUSTOMER; form Admin không có đăng ký,
Client giữ link Đăng ký. API 400 chỉ giữ field name whitelist, bỏ raw detail text;
401 đánh dấu nhóm credentials và 409 đánh dấu cả username/email bằng thông báo chung,
không suy đoán trường nào sai/trùng. Network/5xx không làm input đỏ.

Không còn nút Khôi phục phiên trên UI. AuthGate có Đăng nhập lại và Về cửa hàng;
bootstrap/refresh nền và các primitive restore/checkIdentity vẫn giữ cho session.
Không gọi protected legacy APIs, tạo profile số hoặc nới backend guard để phục vụ UI.

Technical verification PASS, chờ owner review cho follow-up UI:

| Kiểm tra | Kết quả |
|---|---|
| Full frontend `CI=true npm test -- --watchAll=false --runInBand` | 199 PASS, 2 existing live opt-in SKIPPED, 14 passing suites |
| Final focused forms / final navigation | 13/13 PASS / 8/8 PASS qua App và hai header thật; catalog widget mock được phân biệt với browser evidence |
| TypeScript, focused ESLint, node syntax, git diff --check | PASS |
| Normal frontend build | PASS; inherited non-Auth warnings và Browserslist notice vẫn còn |
| Real Auth Edge browser/MySQL | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-sNhVNc/` |
| Real Dataset browser/API/DB regression | 7/7 PASS, exit 0; `.agent-workspace/phase-wdm/WDM-01.8-cnT0Fc/RESULT.json` |
| CRA proxy | NOT VERIFIED lần này vì cổng 8080 đang có dịch vụ; giữ nguyên process của owner |
| Cleanup | Runner dọn tài nguyên sở hữu; docker chỉ còn wspm-mysql; không xóa evidence cũ |

Browser cases bổ sung xác nhận mật khẩu/validation dưới field và form 375 px,
ADMIN login từ Client, nút chuyển hai giao diện, CUSTOMER logout từ /user về /,
và đăng nhập lại sau lỗi phiên thay cho manual recovery. Test credentials chỉ nằm
trong disposable DB. Gate Dataset tiếp tục kiểm BOM/tiếng Việt, raw bytes/SHA và
logout hủy response trễ. Backend API/SQL/migration/dependencies không đổi nên
không chạy lại Maven; không dùng kết quả Maven lịch sử làm test mới của lượt này.

Browser phát hiện Tooltip bọc menu control của Mantine 4 tạo nested button roles;
đã đổi control thành một UnstyledButton có menuButtonLabel. PasswordInput được gắn
aria-invalid/error description rõ ràng, và field lỗi server chỉ được focus sau
khi hết busy. Các assertion role/visibility/mismatch/authorization được giữ; những
run lỗi được giữ riêng. JSDOM navigation queries được giới hạn theo landmark/text
thực tế của Mantine 4 để tránh dò CSS toàn bộ Admin shell; final focused test PASS.

Tạo hai file `auth/useSignOut.ts`, `auth/navigation.test.tsx`; sửa 18 file hiện có
trong slice runtime/tests/E2E/docs/checklist, không xóa source. Backend đang chạy,
frontend .env và các thay đổi có sẵn được giữ nguyên. Không ghi DB dev hoặc
stage/commit/push/merge. Cross-browser, production deployment, audit accessibility
đầy đủ và API commerce legacy vẫn NOT VERIFIED/ngoài phạm vi; WDM-02 chưa bắt đầu.

## 7. RF-01 — Chốt contract trước refactor backend (2026-11-05)

Phạm vi được owner yêu cầu: chốt hành vi hiện tại bằng contract tests trước khi
refactor DTO/Lombok, MapStruct, common API và global exception. Lượt RF-01 chỉ
thêm kiểm thử và tài liệu; không sửa runtime, dependency, schema hoặc frontend.
Các mục evidence ở trên là lịch sử của từng lượt, không phải trạng thái WDM
hiện tại. WDM-02.7 đã được owner chấp nhận trong chat; RF-01 không triển khai
thêm WDM hay converter.

### Contract được bảo vệ

| Nhóm | Bất biến trước/sau refactor | Kiểm thử bổ sung |
|---|---|---|
| Catalog JSON | Đúng field Product/Category, decimal là JSON number, ID là string, null được giữ, danh sách rỗng là `data:[]`; success envelope đúng bốn field | `ResponseJsonContractTest` |
| Auth response | Token có đúng accessToken/tokenType/expiresIn/expiresAt/user; identity có đúng bảy field công khai; roles được copy bất biến, không có password/hash/refresh token; toString che dữ liệu nhạy cảm | `ResponseJsonContractTest`, existing `IdentityMapperTest` |
| Snapshot JSON | Field acquisition/detail và provenance cố định; counts/size là string, unknown statistics là null; timestamp UTC giữ microsecond; không thêm path hoặc arbitrary report JSON | `ResponseJsonContractTest`, existing snapshot service tests |
| Validation JSON | counts/totalElements/totalPages là string; page/size/source positions là number; giữ null/prefix counts, nullable field và DECODER_CURSOR; không serialize manifest | `ValidationResponseContractTest` |
| Validation paging | Thứ tự diagnostics, trang cuối/trang quá cuối, Integer.MAX_VALUE không overflow, không sửa report nguồn, content được copy bất biến | `ValidationResponseContractTest` |
| Product mapping | Giữ đủ năm field, precision BigDecimal, categoryId qua quan hệ, null category/nullable fields/null source; không sửa entity nguồn | `ProductMapperTest` |
| Auth input | Closed JSON object, không coercion number/boolean/null thành text; từ chối duplicate keys/trailing JSON/extra privilege fields; trim/lowercase identity nhưng không trim password; UTF-8 72-byte boundary, NUL/surrogate và minimum length registration | `AuthInputContractTest` |
| Common errors | Cặp code/status, shape sáu field, safe message/details, requestId/header/path không query và no-store; MVC và filter writer trả cùng body; details bất biến | `GlobalExceptionContractTest`, existing `ApiContractTest` |
| Exception translation | Multipart/malformed/binding/type mismatch, input method/constraint validation, missing resource, method/media type, DB availability và unexpected errors giữ phân loại hiện tại | `GlobalExceptionContractTest`, existing `ApiValidationTest` |

JSON expectations là các literal nhỏ trong source test, so sánh sau serialization
thực tế để không nhầm LongNode/IntNode với thay đổi wire contract. Thứ tự property
JSON không phải contract; field names/types/values và thứ tự array là contract.
Fixture chỉ dùng dữ liệu tổng hợp. Snapshot/detail/validation DTO fixtures không
phải bằng chứng rằng metadata tổng hợp đó có thể vượt validation hoặc gate.

AuthInputContractTest kiểm tra reader/DTO hiện có; Bean Validation cho email vẫn
thuộc RegistrationService và được kiểm tra bởi RegistrationIntegrationTest.
Giữ riêng policy: login chấp nhận password ngắn không rỗng để xác thực credential
hiện có, registration đòi tối thiểu tám Unicode code points. Không thay strict
AuthJson reader bằng MVC binding làm lộ credential trong DEBUG log. Quy tắc
closed input/normalization/validation phải được giữ khi di chuyển logic ở RF-02.

Ma trận exception trực tiếp có 16 case; ma trận ApplicationException giữ 14 cặp
code/status trong bảng contract, kể cả INVALID_RESOURCE_STATE 409. Lỗi integrity
không được đổi hàng loạt thành 409, IllegalArgumentException không được đổi hàng
loạt thành 400. Log unexpected failure giữ requestId và stack đã làm sạch, không
ghi original message/cause chứa SQL/credential. Một fixture mock chỉ metadata của
input method validation; đây không phải bằng chứng MVC return-value validation.

Phạm vi kiểm thử giữ rõ ràng: DTO/mapper/handler tests là unit hoặc direct-boundary
tests, không gọi là HTTP/database integration. Các test MVC/Auth/DB có sẵn bổ sung
bằng chứng cho dispatch, authorization trước input/lookup, CSRF/cookie/Bearer,
transaction, raw bytes và publication. ApiErrorWriter không tự quyết định Bearer
challenge; đó vẫn là trách nhiệm filter. Health raw JSON, CSV bytes/headers,
logout 204 và committed response giữ contract riêng đã ghi ở mục 3.

Không thêm mapper hoặc endpoint cho Order/Combo/Run/Pattern/Customer chưa triển
khai. Không ép mọi record dùng Lombok, đổi accessor, đổi DTO schema, hoặc dùng
test để suy ra semantics mining/schema mới. RF-02–05 chưa được triển khai.

### Verification RF-01

Focused command từ repository root:

```powershell
mvn -pl backend -am '-Dtest=ResponseJsonContractTest,ValidationResponseContractTest,AuthInputContractTest,ProductMapperTest,GlobalExceptionContractTest,CategoryMapperTest,IdentityMapperTest,ApiContractTest,ApiValidationTest,WdmExceptionHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Kết quả focused cuối: **67/67 PASS, không fail/error/skip**. Năm test file mới
đóng góp 58 case: ResponseJsonContractTest 4, ValidationResponseContractTest 3,
AuthInputContractTest 18, ProductMapperTest 2, GlobalExceptionContractTest 31.
Chín case còn lại là mapper/common/MVC/committed-response tests có sẵn.
Lượt đầu có hai lỗi fixture: numeric tree node types trước serialization khác
nhau dù JSON giống nhau, và Spring empty validation result không hỗ trợ dựng
method-validation exception. Đã sửa fixture/so sánh JSON, không sửa runtime hoặc
nới kỳ vọng status/code/field. Giữ cả log lỗi đầu và log focused cuối trong
`.agent-workspace/phase-wdm/RF-01-focused.log` và `RF-01-focused-final.log`.

File scope RF-01: tạo đúng năm test file nêu trên trong các package dto, auth,
mappers và exception; sửa tài liệu này, không sửa test/runtime có sẵn. Không
thêm golden report/helper/dependency, không xóa source, stage/commit/push hoặc
đọc/sửa frontend/.env. Các dirty/staged changes từ WDM được giữ nguyên.
Build output/surefire reports được tái sinh trong các thư mục ignored; không
chạy mvn clean hoặc dọn artifact có từ trước task. RF-01 không chạy browser,
frontend test/build hay capacity benchmark; các hạng mục đó NOT VERIFIED ở lượt này.

Hồi quy rộng hơn từ repository root:

```powershell
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Kết quả: **backend 482/482 PASS, mining-core 1182/1182 PASS**, không fail/error/skip,
Maven BUILD SUCCESS. Bao gồm các integration suite AuthSession (18), Registration
(33), WdmAuthorization (35), export repository, product lookup, validation và
conversion gate trên MySQL Testcontainers riêng; không truy cập datasource dev.
Hai test BackendApplicationTests và OrderExportAvailabilityTest dùng cấu hình
datasource mặc định bị loại rõ khỏi lệnh; không báo chúng là PASS hoặc SKIPPED.
Evidence `.agent-workspace/phase-wdm/RF-01-regression.log` và Surefire XML ignored.

PowerShell wrapper của lượt regression báo NativeCommandError khi redirect
cảnh báo Mockito self-attach, dù Maven kết thúc BUILD SUCCESS và XML không có
failure/error. Đã kiểm lại focused 67/67 bằng wrapper trả tường minh
`exit $LASTEXITCODE`: Maven exit 0, shell exit 0, log
`.agent-workspace/phase-wdm/RF-01-focused-exit-check.log` có
`RF01_MAVEN_EXIT_CODE=0`. Không coi warning wrapper là test failure hay sửa runtime
để che warning. Resource của các test được dọn bởi JUnit/Testcontainers; sau chạy
Docker chỉ còn container `wspm-mysql` có sẵn của owner. Các log RF-01 được giữ làm
acceptance evidence, không có source/disposable helper cần xóa.

**RF-01: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).** Owner xác
nhận RF-01 đã xong và yêu cầu tiếp tục RF-02. Evidence ở trên là kết quả lượt RF-01.

## 8. RF-02 — Làm gọn DTO Auth và dùng Lombok phù hợp (2026-11-06)

DTO LoginRequest và RegistrationRequest vẫn là record, giữ nguyên field/accessor,
annotation email và redacted toString. Đã chuyển static factory/validation ra khỏi
DTO: AuthRequestReader trong web/auth kiểm closed JSON object và text fields;
AuthInputValidator trong services/auth giữ normalization và input policy thuần.
Hai lớp không giữ trạng thái hoặc làm I/O, dùng static methods có tên theo từng
operation; không thêm Spring bean hay mapper/registry không cần thiết.

Luồng login/register vẫn đọc body bằng AuthJson trước projection, giữ từ chối
duplicate keys/trailing tokens, unknown fields, coercion và credential DEBUG-log
protection. Username/email dùng trim + Locale.ROOT như cũ, password không trim.
RegistrationService vẫn gọi validator cho cả direct caller và Bean Validation
email trước bcrypt/transaction. Không sửa transaction template, khóa, bcrypt,
session/cookie/CSRF, status/code/messages hoặc response shape. Các caller Java
nội bộ đã đổi từ DTO.from()/validated() sang reader/validator; HTTP contract không đổi.

Áp dụng @RequiredArgsConstructor tại SnapshotReadService,
SnapshotValidationController và ConversionAdmissionController: constructor cũ
chỉ gán dependency, signature giữ nguyên. Giữ constructor tường minh tại các lớp
cần TransactionTemplate, semaphore, dummy password hash hoặc xử lý cấu hình.
Không thêm Lombok đại trà vào record/entity hoặc thay đổi equality/toString.

AuthInputContractTest của RF-01 chỉ đổi imports/điểm gọi; giữ nguyên 18 case và
assertions. RegistrationInputValidationTest thêm năm ca gọi service bằng DTO
tạo trực tiếp, gồm username/password/null/displayName không hợp lệ và email
sai Bean Validation, xác nhận không hash hoặc bắt đầu transaction/persistence.
Focused RF-01 + ba lớp dùng Lombok: 83/83 PASS; focused direct-service: 5/5 PASS,
Maven/shell exit 0, không fail/error/skip. Logs ignored tại
`.agent-workspace/phase-wdm/RF-02-focused.log` và `RF-02-service-input.log`.

Phạm vi file: tạo AuthRequestReader, AuthInputValidator và
RegistrationInputValidationTest; sửa hai DTO Auth, hai Auth controller,
RegistrationService, hai Admin controller, SnapshotReadService,
AuthInputContractTest, ARCHITECTURE và tài liệu này. Không sửa mapper Dataset,
projection/paging, web common hoặc GlobalExceptionHandler; RF-03–05 chưa thực hiện.
Không schema/dependency/env/mining/frontend-runtime changes, không xóa source,
stage/commit/push; giữ RF-01 staged files và staged frontend/.env có sẵn. Working
tree AuthInputContractTest được cập nhật, staged phiên bản RF-01 không bị ghi đè.

### Verification RF-02

Hồi quy từ repository root:

```powershell
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

**Backend 487/487 PASS, mining-core 1182/1182 PASS**, không fail/error/skip, Maven
và shell exit 0. Bao gồm actual HTTP/AuthSession, Registration, shared WDM Auth,
export repository, product lookup, publication và conversion gate trên MySQL
Testcontainers cô lập. Hai test cấu hình datasource mặc định vẫn được loại rõ;
không chạy backend hoặc ghi DB dev để chuẩn bị test. Log:
`.agent-workspace/phase-wdm/RF-02-regression.log` có `RF02_MAVEN_EXIT_CODE=0`.

Normal frontend build PASS, exit 0; giữ cảnh báo hook/non-null assertion và
Browserslist có sẵn, không chỉnh frontend source/dependency để dọn warnings.
Build mới dùng cho browser verification, không thay thế frontend unit regression.
Đóng gói backend hiện tại với skipTests PASS, exit 0, chỉ là preparation sau
Maven regression. Logs `RF-02-frontend-build.log` và `RF-02-package.log` ở cùng
thư mục ignored. Không dùng artifact jar cũ để kiểm chứng source RF-02.

Real Edge 156.0.4314.58 / HTTPS / MySQL cô lập, source backend/UI build hiện tại:

| Gate | Result / evidence |
|---|---|
| Dataset + Validation + Gate | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/WDM-01.8-xp3yNC/RESULT.json` |
| Auth UI/session/cookie/concurrency | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-4kCR02/` và `RF-02-auth-browser.log` |
| CRA development /api proxy | PASS qua bridge/backend/container của runner; không dùng DB dev |
| Diff whitespace | PASS |

Commands: `npm --prefix frontend/test/e2e run test:dataset` và
`npm --prefix frontend/test/e2e test`. Auth runner đặt AUTH06_REGRESSION=0 trong
process test để không lặp Maven regression đã PASS. Hai browser runner dùng
container/process/storage/temp riêng. Logs wrapper ở
`.agent-workspace/phase-wdm/RF-02-dataset-browser.log` và `RF-02-auth-browser.log`
đều kết thúc `RF02_BROWSER_EXIT_CODE=0`. Không thay đổi browser case/source để
phù hợp refactor. Các ca giữ nguyên đã kiểm đăng ký/login, form lỗi, role guard,
CSRF/cookie/refresh/logout và Dataset validation/conversion admission sau RF-02.

JUnit/Testcontainers/browser runner đã dọn tài nguyên sở hữu; Docker cuối lượt
chỉ còn `wspm-mysql` có sẵn. Không xóa artifact/evidence cũ; log/build/surefire output
ignored được giữ hoặc tái sinh đúng mục đích. Không có source/disposable helper
chờ dọn. Full frontend unit suite, production capacity/cross-browser không chạy
lại trong RF-02; không suy ra chúng PASS từ normal build hoặc Edge gate.

**RF-02: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Tạo ba file, sửa 11 file trong scope kể trên. Giữ nguyên HTTP contract và toàn bộ
assertions RF-01. Owner chấp nhận trong chat và yêu cầu triển khai riêng RF-03.

## 9. RF-03 — Tách mapping Dataset (2026-11-06)

SnapshotResponseMapper và ValidationResponseMapper dùng BackendMapperConfig hiện
có: Spring component, constructor injection, unmappedTargetPolicy=ERROR. Không
đổi dependency hoặc tạo mapper cho use case chưa triển khai.

- Acquisition mapping nhận snapshot và checksum/size từ receipt đã lưu. Giữ các
  acquisition defaults CSV/PENDING/NOT_STARTED/NOT_COMPUTED và null count/period;
  không suy ra thống kê hoặc lấy placeholder count của entity đưa ra API.
- Detail mapping chuẩn bị Metadata thuần với cùng whitelist/hash/size/name/count
  visibility như trước. SnapshotReadService vẫn lookup và gọi storage availability
  bằng hash/size đã lọc, rồi truyền kết quả sang mapper. Raw download/integrity,
  repository và acquisition transaction/commit/cleanup không chuyển vào mapper.
- ValidationResponse sở hữu Counts, Diagnostic, Position và DiagnosticPage riêng;
  không import ValidationReport hoặc service/mining types. List được copy bất biến.
  JSON field names/types/nulls/array order không đổi, manifest vẫn private.
- ValidationReportPresenter giữ logic slicing/bounds/page totals trước đây nằm
  trong DTO. Presenter không đọc file/DB, không chạy validation/cleaning lại.
  Controller vẫn giữ query/body checks, default page 0/size 20, limit 100 và HTTP
  error/envelope behavior. Không gom web helper hoặc sửa exception handler ở lượt này.

RF-01 JSON literal expectations được giữ nguyên. Test đổi điểm gọi từ
ValidationResponse.page sang presenter; so sánh diagnostic Java object được đổi
sang DTO literal hoặc JSON equality vì public/internal classes đã tách, không
nới field/value assertions. Các manual constructor fixture được thêm mapper hoặc
presenter thật do MapStruct sinh; không mock mapping để bỏ qua public projection.

Focused run cuối 43/43 PASS, Maven/shell exit 0, không fail/error/skip. Bao gồm
33 case sẵn có và 10 case mới: SnapshotResponseMapperTest (4),
ValidationResponseMapperTest (2), ValidationReportPresenterTest (4).
Các ca mới kiểm receipt binding, null placeholder, safe/unknown metadata,
large string counts, nullable diagnostic fields, deep immutable DTO lists,
empty diagnostics và invalid bounds. Log `.agent-workspace/phase-wdm/RF-03-focused-final.log`.

File scope: tạo ba runtime file (hai mapper, một presenter) và ba test file nêu
trên; sửa DatasetSnapshotService, SnapshotReadService, SnapshotValidationController,
ValidationResponse, năm test liên quan (acquisition/read/controller/JSON/report
engine), ARCHITECTURE và tài liệu này. Không xóa source hoặc thay đổi persisted
report, cleaning, validation gate, Auth, schema, frontend runtime, dependency hay
mining semantics. Giữ thay đổi RF-01/RF-02 có sẵn và index; staged
ValidationResponseContractTest của RF-01 không bị ghi đè, chỉ cập nhật working tree.
Không stage/commit/push; RF-04–05 chưa thực hiện.

### Verification RF-03

```powershell
mvn -pl backend -am '-Dtest=SnapshotResponseMapperTest,ValidationResponseMapperTest,ValidationReportPresenterTest,DatasetSnapshotServiceTest,SnapshotReadServiceTest,SnapshotValidationControllerTest,ValidationResponseContractTest,ResponseJsonContractTest,ValidationReportEngineTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Selected regression: **backend 497/497 PASS, mining-core 1182/1182 PASS**, không
fail/error/skip, Maven và shell exit 0. Actual application wiring bao gồm các
mapper/presenter bean mới; Auth/session/registration/shared authorization,
export/acquisition/read, validation publication và conversion gate đều được chạy
trong các suite tương ứng. MySQL integration dùng Testcontainers/schema/storage
cô lập. Hai test datasource mặc định vẫn bị loại rõ khỏi lệnh, không dùng DB dev.
Evidence `.agent-workspace/phase-wdm/RF-03-regression.log` kết thúc
`RF03_MAVEN_EXIT_CODE=0`; generated MapStruct code chỉ nằm trong target ignored.

Backend package hiện tại với skipTests PASS, exit 0; chỉ là chuẩn bị browser sau
test regression, không phải test evidence riêng. Log RF-03-package.log ở cùng
thư mục ignored. Frontend source không đổi nên dùng lại UI build đã PASS ở RF-02;
không chạy lại frontend build/unit suite hoặc browser Auth riêng trong RF-03.

Real Edge 156.0.4314.58 / HTTPS / shared Auth / isolated MySQL Dataset gate:
**12/12 PASS, exit 0**, không sửa case hoặc fabricate response để phù hợp mapper.
Evidence `.agent-workspace/phase-wdm/WDM-01.8-YWJvn5/RESULT.json` và
`RF-03-dataset-browser.log`, có `RF03_BROWSER_EXIT_CODE=0`. Các ca kiểm actual
export/upload/detail/raw, VALID report/reload, dirty-input recovery sau mất POST
response, INVALID UTF-8/empty/blocked/null/cursor và conversion admission gate.
Frontend decoder nhận cùng contract JSON; raw bytes/private persisted report và
trạng thái conversion không đổi do projection.

Runner/JUnit/Testcontainers dọn tài nguyên sở hữu; Docker cuối lượt chỉ còn
`wspm-mysql` có sẵn. Không xóa source/old evidence/owner resources; giữ logs,
generated target/build và acceptance evidence ignored, không có helper/disposable
source chờ dọn. Diff whitespace PASS. Không suy ra production capacity hoặc
cross-browser từ Edge gate; successful SPMF conversion vẫn ngoài phạm vi.

**RF-03: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Tạo sáu file, sửa 11 file trong scope kể trên. Không tự triển khai RF-04–05,
không stage/commit/push; giữ index và thay đổi RF-01/RF-02 có sẵn. Owner chấp nhận
RF-03 trong chat và yêu cầu triển khai riêng RF-04.

## 10. RF-04 — Gom kiểm tra web dùng chung (2026-11-06)

Thêm web/common/RequestChecks là utility nhỏ, không Spring bean/filter/framework
mới. Bốn thao tác có tên cụ thể: requireEmptyBody, requireNoParametersOrBody,
requireAllowedQueryNames và requireSingleQueryParameters. Helper chỉ kiểm request
shape và tạo ApplicationException theo lỗi an toàn do endpoint truyền vào; không
quyết định Auth, log payload, gọi handler khác hoặc tự bọc response.

| Caller | Policy được giữ nguyên |
|---|---|
| AuthJson.empty cho refresh/logout | Chỉ body phải rỗng, không thêm query policy. Field `body`, code INVALID_VALUE, message `This operation requires an empty body.` |
| Validation POST | Parameters hoặc body bị từ chối với field `request`, INVALID_VALUE và message riêng `Validation POST accepts no body or query parameters.` |
| Conversion POST | Cùng cơ chế, giữ message riêng `Conversion admission accepts no body or query parameters.`; eligible vẫn 503 converter unavailable |
| Validation GET | Chỉ page/size, mỗi key đúng một value; unknown/repeated giữ field `query`, INVALID_VALUE, `Unknown or repeated query parameter.` |
| Order export | Chỉ fromTime/toTime; unknown key giữ field `query`, UNKNOWN_FIELD, `Query contains an unsupported filter.` |

Parameters được kiểm trước body ở hai POST, giữ short-circuit: có parameters thì
không đụng getInputStream. Empty body là EOF thực, whitespace/NUL cũng là content;
chỉ đọc một byte, không tin Content-Length hoặc đọc toàn bộ payload. IOException
được propagate như trước, không dịch mọi I/O failure thành client 400. Query errors
không echo untrusted key/value. getParameterMap vẫn là cơ chế servlet ban đầu,
có thể kích hoạt parsing parameters theo container; helper không được gọi trước
authorization hoặc áp dụng nhầm cho multipart body.

AuthJson.read strict JSON parsing/credential redaction giữ nguyên. Các numeric
page/size rules, default 0/20, max 100, timestamp/period/duplicate export filters
và upload multipart/file checks vẫn ở endpoint/service chuyên trách. Không thêm
BaseController, automatic response advice, pagination framework hoặc UUID policy.
Auth/CSRF/WDM filters, GlobalExceptionHandler và response writer không đổi.

Focused 92/92 PASS, không fail/error/skip, Maven/shell exit 0. RequestChecksTest có
14 case mới cho byte/body/query policy, parameter-first no-read, một-byte bound,
IOException và không echo private key. Thêm một case MVC validation giữ exact
details trước service; tăng assertions ở conversion/export tests cho field/code/
message hiện có. Không nới assertion hoặc fabricate business response để pass.
Log `.agent-workspace/phase-wdm/RF-04-focused.log` có `RF04_MAVEN_EXIT_CODE=0`.

File scope: tạo RequestChecks và RequestChecksTest; sửa AuthJson, ba Admin
controller (validation/conversion/export), ba controller test tương ứng,
ARCHITECTURE và tài liệu này. Không đổi DTO/mapping/presenter của RF-01–03,
schema/dependencies/env/frontend runtime/mining hoặc tạo conversion thực tế.
Giữ dirty/staged changes trước task, kể cả staged frontend/.env; không xóa source,
stage/commit/push. RF-05 chưa thực hiện.

### Verification RF-04

```powershell
mvn -pl backend -am '-Dtest=RequestChecksTest,AuthInputContractTest,OrderExportControllerTest,SnapshotValidationControllerTest,ConversionAdmissionControllerTest,ApiContractTest,ApiValidationTest,GlobalExceptionContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Selected regression: **backend 512/512 PASS, mining-core 1182/1182 PASS**, không
fail/error/skip, Maven/shell exit 0. Bao gồm actual HTTP Auth/session/registration,
shared WDM authorization, export/acquisition/validation/gate, cùng mapper/DTO
contract tests của RF-01–03. MySQL Testcontainers/schema/storage được cô lập;
hai test datasource mặc định vẫn được loại rõ khỏi lệnh, không dùng DB dev.
Evidence `.agent-workspace/phase-wdm/RF-04-regression.log` có `RF04_MAVEN_EXIT_CODE=0`.

Backend package hiện tại với skipTests PASS, exit 0; chỉ chuẩn bị jar mới cho
browser sau regression, không phải test evidence riêng. Log RF-04-package.log
ở cùng thư mục ignored. Frontend source không đổi nên dùng lại build đã PASS ở
RF-02; không chạy lại frontend build/unit suite trong RF-04.

Real Edge 156.0.4314.58 / HTTPS / MySQL cô lập, jar RF-04 và UI build hiện tại:

| Gate | Result / evidence |
|---|---|
| Dataset/Validation/Gate | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/WDM-01.8-54PA5R/RESULT.json` |
| Auth UI/session/cookie/concurrency | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-kaYgJx/` |
| CRA /api proxy | PASS qua bridge/backend riêng của runner |
| Diff whitespace | PASS |

Commands: `npm --prefix frontend/test/e2e run test:dataset` và
`npm --prefix frontend/test/e2e test`. Đặt AUTH06_REGRESSION=0 trong process Auth
để không lặp Maven đã PASS. Browser case/source giữ nguyên; không mock business
response để che thay đổi helper. Evidence wrapper RF-04-dataset-browser.log và
RF-04-auth-browser.log ở thư mục phase-wdm đều có `RF04_BROWSER_EXIT_CODE=0`.

JUnit/Testcontainers/browser runners đã dọn resource sở hữu; Docker cuối lượt
chỉ còn `wspm-mysql` có sẵn. Không ghi DB dev hoặc xóa source/owner resources/old
evidence. Logs/RESULT.json được giữ làm acceptance evidence ignored, không có
disposable helper cần xóa. Không suy ra production capacity/cross-browser từ
Edge gate, không triển khai conversion/SPMF.

**RF-04: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Tạo hai file, sửa chín file trong scope kể trên; giữ index và RF-01–03 changes,
không stage/commit/push. Owner chấp nhận RF-04 và yêu cầu triển khai riêng RF-05.

## 11. RF-05 — Global exception và hồi quy (2026-11-06)

GlobalExceptionHandler giữ public MVC entry point, @RestControllerAdvice và
@ExceptionHandler(Exception.class). Chuỗi instanceof dài chuyển thành Java 21
type switch với dispatch rõ ràng; validation field projection, generic request
validation, method-validation classification, Allow header và unexpected logging
được tách thành helper có tên trong cùng lớp. Không thêm registry/Spring bean,
ResponseEntityExceptionHandler/ProblemDetail hoặc dispatcher gọi handler khác.

Giữ nguyên:

- Error envelope sáu field, toàn bộ 14 cặp ErrorCode/status hiện có, safe messages,
  details array, requestId/header correlation, no-store và path không query.
- Field validation không trả rejected values/validator messages; details generic
  được deduplicate/sort như trước. Không map mọi IllegalArgumentException thành
  400 hoặc mọi integrity/unique error thành 409.
- Chỉ DB resource/transaction availability types đã hỗ trợ được map 503; các
  unexpected types mặc định 500. Log giữ safe class name/stack và requestId,
  không original message/cause/SQL/credential, không log lại ApplicationException.
- Non-API errors rethrow trước khi xử lý committed response; API response đã
  committed chỉ warn và không append JSON. Allow giữ danh sách framework cung
  cấp cho 405, không tự tạo header khi framework không khai báo alternatives.
- Filter errors vẫn qua ApiErrorWriter; Auth/CSRF/default-deny/ADMIN trước MVC
  validation/lookup không đổi. Không mở endpoint để làm test missing handler.

Hai chỉnh sửa phân loại có chủ đích, không gọi là chỉ thay đổi hình thức:

| Trường hợp | Trước RF-05 | Sau RF-05 |
|---|---|---|
| HandlerMethodValidationException với isForReturnValue=true | 400 VALIDATION_FAILED | 500 INTERNAL_ERROR, generic body và safe server log |
| NoHandlerFoundException khi đã tới MVC | Unexpected 500 | 404 RESOURCE_NOT_FOUND như NoResourceFoundException |

Input method validation vẫn 400. Source Spring 7.0.10 đúng dependency đang dùng,
`spring-web-7.0.10-sources.jar`, HandlerMethodValidationException.initHttpStatus
xác nhận 400 cho input và 500 cho return value. Production controllers hiện
không khai báo method return constraints; không thay đổi business validation
hoặc response schema của các route đã nghiệm thu. Generic ConstraintViolationException
giữ phân loại hiện có; không đoán nguồn service/HTTP từ message hoặc cause.

Focused 59/59 PASS, Maven/shell exit 0, không fail/error/skip. Global exception
matrix thêm NoHandlerFoundException 404, serialization failure 500 và IOException
500; giữ toàn bộ 31 case RF-01, thêm hai case null Allow và dedup/sort/private
binding details. ApiValidationTest thêm hai ca dispatch MVC thật cho input 400 và
return-value 500 trước khi ghi body; endpoints đó chỉ ở test source, không nằm
trong app. Tổng cộng bảy case mới. Log
`.agent-workspace/phase-wdm/RF-05-focused.log` có `RF05_MAVEN_EXIT_CODE=0`.

File scope: sửa GlobalExceptionHandler, GlobalExceptionContractTest,
ApiValidationTest, ARCHITECTURE và tài liệu này. Không tạo/xóa source file,
không đổi ErrorCode, filters/writer, services/DTO/mapper, schema/dependencies/env,
frontend runtime hoặc mining. Giữ staged RF-01 và changes RF-02–04; chỉ cập nhật
working tree của GlobalExceptionContractTest, không ghi đè index. Không stage/
commit/push hoặc tự chuyển sang WDM mới.

### Verification RF-05

```powershell
mvn -pl backend -am '-Dtest=GlobalExceptionContractTest,ApiValidationTest,ApiContractTest,WdmExceptionHandlerTest,RequestChecksTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Selected regression: **backend 519/519 PASS, mining-core 1182/1182 PASS**, không
fail/error/skip, Maven/shell exit 0. Các suite actual HTTP Auth/session,
registration, shared authorization, export/upload/read, publication và conversion
gate tiếp tục PASS qua handler mới. MySQL/storage/schema dùng Testcontainers
cô lập; hai test datasource mặc định vẫn được loại rõ, không dùng DB dev.
`.agent-workspace/phase-wdm/RF-05-regression.log` có `RF05_MAVEN_EXIT_CODE=0`.

Backend package hiện tại với skipTests PASS, exit 0, chỉ chuẩn bị browser sau
Maven regression, không phải test evidence riêng. Log RF-05-package.log ở cùng
thư mục ignored. Frontend runtime không đổi nên dùng build đã PASS ở RF-02;
không chạy lại frontend build/unit suite để suy ra kết quả API runtime.

Real Edge 156.0.4314.58 / HTTPS / shared Auth / isolated MySQL, jar RF-05:

| Gate | Result / evidence |
|---|---|
| Dataset/Validation/Gate | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/WDM-01.8-C4fCwN/RESULT.json` |
| Auth UI/session/cookie/concurrency | 12/12 PASS, exit 0; `.agent-workspace/phase-wdm/AUTH-06-zJg7dI/` |
| CRA /api proxy | PASS qua runner-owned bridge/backend/container |
| Diff whitespace | PASS |

Commands: `npm --prefix frontend/test/e2e run test:dataset` và
`npm --prefix frontend/test/e2e test`. Auth process đặt AUTH06_REGRESSION=0 để
không lặp Maven đã PASS. Không thay browser case để phù hợp handler mới.
RF-05-dataset-browser.log và RF-05-auth-browser.log trong thư mục phase-wdm
đều có `RF05_BROWSER_EXIT_CODE=0`. Các ca real API errors/401/403, cookie/CSRF,
refresh/logout/races và export/upload/read/validation/gate tiếp tục hoạt động
với error envelope đã chốt ở RF-01. Hai classification corrections được chứng
minh bởi direct matrix/MVC tests, không suy ra từ browser case không gọi chúng.

JUnit/Testcontainers/browser runners dọn resource của lượt test; Docker cuối lượt
chỉ còn `wspm-mysql` có sẵn. Không ghi DB dev, xóa source/owner resources hoặc
old evidence. Logs/RESULT.json/generated outputs được giữ ignored đúng mục đích;
không có disposable helper cần dọn. Không suy ra cross-browser/production capacity
hoặc successful SPMF conversion từ các gate này.

**RF-05: IMPLEMENTED / TECHNICAL PASS / OWNER ACCEPTED (2026-11-06).**
Sửa năm file, không tạo/xóa source. Owner đã chấp nhận RF-05 và chốt toàn bộ
RF-01–RF-05 trong chat. Bộ refactor hoàn tất kỹ thuật và được nghiệm thu theo
phạm vi/bằng chứng đã ghi; các giới hạn verification vẫn giữ nguyên. Không tự
triển khai task mới hoặc stage/commit/push; index và thay đổi trước task được giữ nguyên.
