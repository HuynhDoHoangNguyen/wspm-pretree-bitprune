# Shared Auth — Setup, contract và kiểm chứng

Tài liệu dành cho chạy local, tích hợp Auth và đối soát kết quả đã ghi nhận.
Auth dùng chung cho Client/Admin/WDM; không có luồng đăng nhập WDM riêng.

- [Chạy local và test tay](#manual-test)
- [SQL vận hành](#operational-scripts)
- [API và bảo mật](#api-contract)
- [Trạng thái kiểm chứng](#verification-evidence)
- [Phạm vi tích hợp](#commit-boundary)

<a id="manual-test"></a>
## 1. Chạy local và test tay

### Chuẩn bị backend

Cần JDK 21, Maven 3.9+, Node/npm và Docker Desktop. Chạy từ repo root:

```powershell
if (!(Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
docker compose up -d mysql
docker compose ps
```

Mặc định MySQL `127.0.0.1:3307`, database `wspm_combo`; dùng giá trị MYSQL_*
thực tế trong `.env` nếu đã đổi. Volume giữ account qua các lần restart.

Backend đọc `.env` ở repo root. Với máy mới, tạo secret **một lần**, dán vào
AUTH_JWT_SECRET; giữ secret hiện có nếu đã cấu hình:

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

```properties
AUTH_JWT_SECRET=<Base64 của ít nhất 32 byte ngẫu nhiên>
AUTH_JWT_ISSUER=http://localhost:8080
AUTH_JWT_KEY_ID=wspm-local-1
AUTH_COOKIE_SECURE=false
AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://localhost:8080,http://127.0.0.1:8080
```

Auth dùng HS256, không cần PEM/OpenSSL. Secret thiếu/sai/ngắn làm startup fail;
không có fallback hoặc tự sinh secret. Không commit/chia sẻ `.env`.
Đổi secret vô hiệu access JWT cũ; refresh session còn hợp lệ có thể cấp token mới.
CLI/environment tường minh có thể override `.env`; đổi cấu hình cần restart.
HTTPS dùng Secure=true và allowlist đúng HTTPS origin.

```powershell
mvn -pl backend -am package -DskipTests
java -jar backend/target/backend-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Chờ Started BackendApplication; `GET http://localhost:8080/api/health` trả
200, `{"status":"UP","service":"wspm-backend"}`. Startup chạy Flyway rồi Hibernate
validate; không tự tạo secret/role/account. Không sửa migration V2 đã áp dụng.
Package với skipTests chỉ là build, không chứng minh tests PASS.

### Chuẩn bị role và frontend

Trong Workbench, chọn đúng DB và kiểm tra:

```sql
USE wspm_combo;
SELECT role_id, code, name FROM ROLE
WHERE LOWER(code) IN ('customer', 'admin');
```

Đăng ký cần reference role đúng chữ hoa CUSTOMER; ADMIN chỉ cần cho test quản trị.
Nếu chỉ có role khác casing, dừng kiểm tra, không tự đổi code.
Nếu CUSTOMER thiếu, sau khi review đúng DB có thể chạy:

```sql
INSERT INTO ROLE(role_id, code, name, created_at)
SELECT UUID(), 'CUSTOMER', 'Customer', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
  SELECT 1 FROM ROLE WHERE CAST(code AS BINARY) = CAST('CUSTOMER' AS BINARY)
);
```

Nếu cần ADMIN nhưng thiếu, dùng cùng mẫu trên với code ADMIN/name Admin.
Không seed account/password hoặc chèn password_hash bằng tay.

Terminal frontend, từ repo root:

```powershell
Set-Location frontend
npm ci
$env:REACT_APP_WDM_API_BASE_URL = '/api'
npm start
```

Nếu gặp peer-dependency conflict kế thừa, dùng `npm ci --legacy-peer-deps`.
Mở `http://localhost:3000`, backend ở 8080; dùng proxy /api, không API root khác origin.
Không trộn localhost và 127.0.0.1 trong cùng phiên vì cookie theo host.

### Tài khoản CUSTOMER và ADMIN

1. Tự đăng ký tại /signup bằng username/email mới; thành công 201, CUSTOMER/ACTIVE,
   emailVerified=false, không tự login. Chưa có xác minh email.
2. Đăng nhập /signin bằng **username**, không phải email. Username/email được
   trim/lowercase; password không trim. Registration yêu cầu >=8 Unicode code points,
   <=72 byte UTF-8, không NUL; bcrypt cost 12. Login không áp minimum-length của
   mật khẩu mới lên credential hiện có.
3. Account lưu ở USER_ACCOUNT, liên kết CUSTOMER/USER_ROLE; login tạo USER_SESSION.
   Account test e2e/ui/wdm thuộc DB tạm, không phải account local mặc định.
4. Muốn test ADMIN: đăng ký account thứ hai (vẫn CUSTOMER), rồi operator cấp ADMIN
   riêng cho đúng account ACTIVE. Không có public API tự cấp quyền.

Ví dụ bootstrap ADMIN local, trong Workbench sau khi xác minh đúng DB/account/role:

```sql
SET @target_username = 'manual.admin';
SELECT user_id, username, status FROM USER_ACCOUNT WHERE username = @target_username;
SELECT role_id, code FROM ROLE WHERE CAST(code AS BINARY) = CAST('ADMIN' AS BINARY);
SET @target_user_id = (SELECT user_id FROM USER_ACCOUNT WHERE username = @target_username);
SET @assigned_by = NULL; -- Chỉ bootstrap local; nếu có operator, dùng user_id của operator.
START TRANSACTION;
INSERT INTO USER_ROLE(user_id, role_id, assigned_at, assigned_by)
SELECT u.user_id, r.role_id, UTC_TIMESTAMP(6), @assigned_by
FROM USER_ACCOUNT u CROSS JOIN ROLE r
WHERE u.user_id = @target_user_id
  AND CAST(u.status AS BINARY) = CAST('ACTIVE' AS BINARY)
  AND CAST(r.code AS BINARY) = CAST('ADMIN' AS BINARY);
SELECT ROW_COUNT() AS inserted_rows;
```

Chỉ COMMIT khi inserted_rows=1; nếu 0 dòng hoặc duplicate/FK error thì ROLLBACK,
kiểm tra lại, không cấp lặp. Đăng xuất/đăng nhập lại; không cần restart backend.
Backend đọc quyền hiện tại từ DB.

### Checklist test tay

| Kiểm tra | Kỳ vọng |
|---|---|
| Register hợp lệ / trùng identity / input sai | 201, không auto-login / 409 IDENTITY_CONFLICT / 400 VALIDATION_FAILED |
| Login sai / đúng | 401 generic / csrf → login → me đều 200 |
| F5 /user | Khôi phục nếu refresh cookie/session còn hợp lệ |
| Anonymous / CUSTOMER vào Admin API | 401 / 403 |
| ADMIN login từ Client hoặc Admin | Vào /admin; /admin/account hiển thị identity/roles |
| CUSTOMER login | Về /; /user xem account |
| Chuyển Client ↔ Admin | Cùng tab/session; không token trên URL |
| Hai tab cùng origin | Đồng bộ sự kiện phiên/logout; giới hạn Web Locks xem mục bảo mật |
| Logout từ header | 204, về /, F5 không khôi phục session đã logout |
| Logout server lỗi | Xóa access/cache local ngay, báo chưa xác nhận server |
| Restart backend/frontend | Account còn trong MySQL; restore phụ thuộc session/cookie/secret |

DevTools: Network lọc /api/v1/auth; Cookies kiểm HttpOnly, SameSite=Lax,
Path=/api/v1/auth; HTTP local Secure=false, HTTPS Secure=true.
Access token chỉ ở memory. Bootstrap anonymous có thể refresh 401 bình thường.
Auth test không yêu cầu dataset/mining; commerce legacy chưa nối không phải lỗi Auth.
Gửi kết quả PASS/FAIL và requestId, không gửi token/cookie/secret.

| Lỗi | Kiểm tra |
|---|---|
| Startup fail | JDK/MySQL, secret Base64 >=32 byte, issuer/kid và shell override |
| Register 503 | CUSTOMER đúng casing và storage availability |
| Login 401 | Username/password, host và account ACTIVE |
| CSRF_REJECTED 403 | Origin/port allowlist, cookie Secure theo HTTP/HTTPS, không trộn host |
| API khác origin | API root=/api; restart frontend sau đổi env |
| Quyền UI chưa cập nhật | Login lại hoặc Kiểm tra lại quyền; CUSTOMER bị chặn Admin là đúng |
| Legacy gọi 8085 | Ngoài slice Auth; kiểm qua /signup, /signin, /user, /admin |

<a id="operational-scripts"></a>
## 2. SQL vận hành

[provision-customer-role.sql](../../backend/scripts/auth/provision-customer-role.sql)
và [grant-admin-role.sql](../../backend/scripts/auth/grant-admin-role.sql) là mẫu
PreparedStatement có placeholder `?`, không dán nguyên vào Workbench.
Chúng không thuộc Flyway/startup, không seed account/password.
Script grant yêu cầu account ACTIVE và ADMIN role đã tồn tại, transaction caller-owned,
đúng một affected row trước commit; ghi nhận operator/DB/target khi vận hành.

<a id="api-contract"></a>
## 3. API và bảo mật

### Endpoints và response

Prefix `/api/v1/auth`; POST dùng CSRF/Origin policy.

| Endpoint | Request | Thành công |
|---|---|---|
| GET /csrf | Không cần login | 200 {headerName,token} + CSRF cookie |
| POST /register | username,email,password,displayName | 201 IdentityResponse |
| POST /login | username,password | 200 TokenResponse + refresh cookie |
| POST /refresh | Body rỗng + refresh/CSRF cookies | 200 TokenResponse + rotated cookie |
| GET /me | Bearer access JWT | 200 IdentityResponse từ DB |
| POST /logout | Body rỗng + refresh/CSRF cookies | 204, không body |

- Success JSON: `{code:"SUCCESS",message:"Request completed successfully.",data,requestId}`.
  Server-generated UUID requestId khớp X-Request-Id; frontend validate envelope/DTO,
  unwrap data một lần; không fallback business JSON cũ. Empty list là data=[].
- IdentityResponse: userId,username,email,displayName,status,roles,emailVerified.
  TokenResponse: accessToken,tokenType="Bearer",expiresIn=900,expiresAt,user.
  IDs opaque strings; timestamps UTC ISO-8601; không trả hash/password/refresh token.
- Strict JSON closed object: reject unknown privilege fields, duplicate keys,
  trailing JSON, coercion và sai type. Service direct callers vẫn phải validate.
- Ngoại lệ: /api/health raw JSON; logout 204 và HEAD không body; CSV giữ bytes,
  content type/attachment/length, dùng Blob branch; không tự mở/bọc Actuator.
  Catalog giữ DTO/pagination khi bọc ApiResponse; WDM string BIGINT/count giữ string.
- Error: `{status,code,message,path,requestId,details}`; status khớp HTTP,
  path không query, details luôn array {field,code,message}. Không raw exception,
  SQL, rejected values, secret/PII. Unknown keys báo body/query an toàn;
  generic details deduplicate/sort theo field/code/message.

| HTTP | ErrorCode |
|---|---|
| 400 | VALIDATION_FAILED, MALFORMED_REQUEST |
| 401 | AUTHENTICATION_REQUIRED |
| 403 | ACCESS_DENIED, CSRF_REJECTED |
| 404 | RESOURCE_NOT_FOUND |
| 405 / 406 | METHOD_NOT_ALLOWED / NOT_ACCEPTABLE |
| 409 | IDENTITY_CONFLICT, INVALID_RESOURCE_STATE |
| 413 / 415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE |
| 500 / 503 | INTERNAL_ERROR / SERVICE_UNAVAILABLE |

Detail codes gồm INVALID_VALUE, UNKNOWN_FIELD, INVALID_TIMESTAMP, INVALID_PERIOD.
Safe default messages do ErrorCode quản lý; không map mọi integrity error thành 409
hoặc mọi IllegalArgumentException thành 400. Storage availability được nhận diện mới là 503.

### Bất biến session, guard và kiến trúc

- JWT 15 phút, HS256/at+jwt, current kid/issuer, audience wspm-api,
  sub/sid/jti/iat/nbf/exp, zero clock skew. Không token-supplied key URL/discovery,
  RS256 hoặc secret fallback. Mỗi protected request kiểm account/session/roles từ DB.
- Refresh random 256 bit, DB chỉ lưu SHA-256, rotate mỗi lần; absolute expiry 7 ngày
  từ login. Old token bị từ chối; không replay-family revocation hoặc grace retry.
  Giữ account-before-session lock và transaction/commit boundary.
- Logout cookie thiếu/cũ vẫn 204, không revoke session khác. Server chỉ clear cookie
  sau storage success; client xóa credentials/cache và abort protected requests ngay.
  Request đã được phép trước commit không bị thu hồi ngược.
- ApiSecurityFilter firewall/default-deny; public GET/HEAD health/products/categories;
  Auth chỉ sáu path/method, /me auth trước method disclosure. ADMIN guard trước
  lookup/input validation; authorizer provider phải đúng một bean, không che bằng @Primary.
  Không CORS allow-all. Unknown API deny 401/403; lỗi Tomcat/firewall hạ tầng có thể ngoài envelope.
- RequestIdFilter trước guards; ApiErrorWriter cho filter và GlobalExceptionHandler
  cho MVC dùng chung DTO/ID. Giữ Allow 405, no-store, protected 401 Bearer challenge.
  Không append JSON vào committed response; unexpected error log một lần, sanitized stack.
- Frontend common API không phụ thuộc Auth/WDM state; giữ abort/Blob/no automatic
  mutation replay. Identity từ /me, không tin localStorage hoặc decoded JWT.
  Web Locks/queue serialize Auth operations; BroadcastChannel chỉ phát sự kiện, không token.
  Không Web Locks chỉ có queue cục bộ, không hứa exactly-once giữa tab.
- Forms giữ password nguyên chuỗi; confirm password chỉ phía UI, register vẫn gửi bốn field.
  API 400 dùng field whitelist; 401/409 báo chung, không đoán identity trùng.
  Network/5xx không đánh dấu input sai. /cart guest đi login; commerce legacy ngoài phạm vi.
- Lombok chỉ giảm boilerplate; không @Data/entity toString lộ dữ liệu.
  MapStruct dùng Spring constructor injection, unmappedTargetPolicy=ERROR;
  IdentityMapper nhận resolved roles, không query/cấp quyền.

### Refactor RF-01–RF-05 đã nghiệm thu

| Task | Kết quả và ranh giới |
|---|---|
| RF-01 | Chốt literal JSON/input/mapping/error contracts trước refactor; array order/field types/nulls được bảo vệ, JSON property order không phải contract |
| RF-02 | AuthRequestReader/AuthInputValidator tách parsing/normalization khỏi DTO; RegistrationService validate direct callers trước bcrypt/transaction |
| RF-03 | SnapshotResponseMapper/ValidationResponseMapper và presenter thuần; giữ receipt/hash whitelist, null unknown stats, immutable DTO lists/paging; không chuyển storage/DB/cleaning vào mapper |
| RF-04 | RequestChecks gom empty-body/query checks; giữ từng caller policy/message, parameters-first, đọc tối đa một byte, propagate IOException; không áp trước authorization hoặc nhầm multipart |
| RF-05 | GlobalExceptionHandler Java 21 type switch và helpers; input method validation 400, return-value validation 500; missing MVC handler/resource 404 |

RF-03/04 giữ validation page mặc định 0/size 20/max 100 và query/body policies riêng;
không áp quy tắc paging đó cho mọi endpoint. RF-05 giữ non-API rethrow, committed-response
no-append, Allow framework và safe logs; generic ConstraintViolationException không
đoán nguồn từ message/cause. Không thêm registry/BaseController/response-advice framework.

<a id="verification-evidence"></a>
## 4. Trạng thái kiểm chứng

**Số liệu lịch sử, không phải test chạy lại khi rút gọn tài liệu.**
AUTH-01–06 owner accepted, BLK-01 closed (2026-11-05); RF-01–05 owner accepted
(2026-11-06). Follow-up Auth UI technical PASS, owner review chưa được ghi nhận tại đây.
Không dùng kết quả cũ để chứng minh source hiện tại hoặc rollout của owner đã PASS.

| Mốc | Kết quả đã ghi nhận |
|---|---|
| Auth/API refactor đầu, trước thay đổi HS256 | Backend cuối 144 PASS; core 1182 PASS; frontend 107 PASS + 2 opt-in skip; types/build PASS; HTTPS Edge gate 10/10 PASS |
| Auth UI follow-up | Frontend 199 PASS + 2 existing skips; forms 13/13, navigation 8/8; types/lint/build PASS; Auth browser 12/12, Dataset browser 7/7 PASS; CRA proxy NOT VERIFIED lượt này |
| RF-01 | Focused 67 PASS; backend regression 482/core 1182 PASS |
| RF-02 | Focused 83 + direct-service 5 PASS; backend 487/core 1182 PASS; Auth 12/12 + Dataset 12/12 + proxy PASS |
| RF-03 | Focused 43 PASS; backend 497/core 1182 PASS; Dataset 12/12 PASS; không chạy lại Auth/frontend suite |
| RF-04 | Focused 92 PASS; backend 512/core 1182 PASS; Auth 12/12 + Dataset 12/12 + proxy PASS |
| RF-05 | Focused 59 PASS; backend 519/core 1182 PASS; Auth 12/12 + Dataset 12/12 + proxy PASS |

RF regressions nêu trên loại rõ BackendApplicationTests và OrderExportAvailabilityTest,
dùng MySQL Testcontainers/storage cô lập; không gọi là full default-environment suite.
Unit/DTO/mock assertions không phải browser/DB evidence. Build -DskipTests không phải test.
Browser dùng actual app/Edge/shared Auth/HTTPS/MySQL cô lập, test helpers ngoài production;
fault-injection 503 không chứng minh physical outage.

Evidence lịch sử nằm ở `.agent-workspace/api-refactor/`, `.agent-workspace/phase-wdm/`:
RF-01..05 focused/regression logs; RF-05 cuối có `RF05_MAVEN_EXIT_CODE=0`,
Auth `AUTH-06-zJg7dI/`, Dataset `WDM-01.8-C4fCwN/RESULT.json`.
Workspace đã được owner duyệt dọn: cần kiểm tra file còn tồn tại trước khi đối soát;
nếu evidence mất thì trạng thái kiểm lại là NOT VERIFIED, không tái tạo log từ bảng này.

Để chạy lại, chuẩn bị datasource/key test biệt lập và xem
[frontend README](../../frontend/README.md) cùng [E2E README](../../frontend/test/e2e/README.md).
Không chạy full-context tests bằng DB dev. Các lệnh tham khảo từ repo root:

```powershell
mvn -pl backend -am '-Dtest=*,!BackendApplicationTests,!OrderExportAvailabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
$env:AUTH06_REGRESSION = '1'
$env:AUTH_E2E_LOG_DIR = '.agent-workspace/api-refactor/browser-new'
node frontend/test/e2e/run.mjs
```

Cần cài dependencies/build tương ứng trước browser gate; dùng thư mục evidence mới.
Runner tạo/dọn container và TLS/test secret riêng, không ghi fixture vào DB dev.

Chưa chứng minh: owner manual test/production TLS/deployment, cross-browser,
100000-record capacity, full accessibility audit, exactly-once khi thiếu Web Locks,
CI=true build sau refactor (warnings ESLint kế thừa từng làm fail).
Normal build hoặc CI=true test PASS không đồng nghĩa CI=true build PASS.
Không dùng gate validation/conversion-admission lịch sử để chứng minh successful SPMF conversion.

<a id="commit-boundary"></a>
## 5. Phạm vi tích hợp

Owner từng cho phép tích hợp Auth/API refactor + WDM-01 + follow-up UI vào
experiment/combo-recommendation-web; kế hoạch cũ “hoãn WDM-01” không còn áp dụng.
Đây là ghi nhận lịch sử, không phải lệnh commit/push mới.

Stage theo file/hunk cụ thể; file trộn và WDM authorization/export E2E có dependencies
phải review cùng nhau. Working tree xanh không chứng minh commit tách riêng build được;
isolated-commit verification vẫn NOT VERIFIED nếu chưa chạy trên đúng staged snapshot.
Không commit secrets/local env, keys/logs/build/dependencies. Không đổi mining-core,
V1/V2 hoặc semantics threshold vì dọn Auth; R6/exact-real equivalence vẫn mở.
