# Frontend

React/TypeScript frontend cho hệ thống minh họa gợi ý combo sản phẩm. Source
được kế thừa từ Electro và đang được thu hẹp để khớp với backend cũng như phạm
vi khóa luận; không phải mọi page/model hiện có đều đã được backend hỗ trợ.

## Công nghệ

- React 17 và TypeScript 4.6.
- Create React App / `react-scripts` 5.
- Mantine 4.
- React Router 6.
- React Query 3, Zustand và React Tracked.
- Testing Library.

## Cài đặt và chạy

```powershell
npm ci
npm start
```

Development server mặc định: `http://localhost:3000`.

`package.json` proxy các request development tới backend tại
`http://127.0.0.1:8080`. Backend cần được chạy riêng từ repository root.

## Build và test

```powershell
npm run build
npm test -- --watchAll=false
```

Output production được tạo tại `build/` và không được commit.

AUTH-05: suite frontend có 96 test PASS, 2 HTTP test opt-in skipped ở lượt thường.
HTTP Auth thật với backend/MySQL test biệt lập chạy riêng; không thay thế browser
E2E. Khi thiếu npm Windows launcher, gọi trực tiếp script trong node_modules như
các lệnh ở phần WDM bên dưới; typecheck bằng `node node_modules/typescript/bin/tsc --noEmit`.

## Shared Auth

Auth và WDM business JSON dùng contract chung tại `src/api/common/response.ts`:
`{code,message,data,requestId}`. Client kiểm tra envelope, đối chiếu request ID với
`X-Request-Id`, rồi trả `data` cho session/UI; response lỗi dùng chung
`{status,code,message,path,requestId,details}`. Không có fallback cho JSON business
kiểu cũ. `getBackendHealth` dùng chế độ `raw-health` dành riêng GET `/health`;
logout 204 không đọc JSON và download CSV vẫn trả Blob. Backend và frontend phải
được cập nhật cùng nhau. Xem [API contract](../docs/auth/README.md#api-contract).

Source `src/auth/`: `api.ts` định nghĩa DTO/client; `session.ts` quản lý duy nhất
Auth trong bộ nhớ; `AuthBoundary.tsx` bootstrap, nối WDM, xóa cache/cart và đồng bộ
sự kiện tab; `SignInForm`, `AuthGate`, `IdentityPanel` phục vụ giao diện.

- Đăng ký username/email/password/displayName qua `/api/v1/auth/register`, CUSTOMER
  ACTIVE ngay, email chưa xác minh; thành công chuyển sang đăng nhập, không tự login.
- Login username/password → JWT → `/me` → hiển thị danh tính thật. Password tối thiểu
  8 codepoint, tối đa 72 byte UTF-8, không trim/cắt. Không gọi API giỏ hàng để login.
- Khởi động/reload → refresh cookie → `/me`. JWT chỉ ở memory, refresh là HttpOnly
  cookie. Auth POST luôn lấy `/csrf` trước. Không lưu token/identity vào storage
  hoặc URL; khóa localStorage Auth kế thừa được xóa mà không đọc.
- Refresh single-flight trong tab, queue serialize refresh/login/logout/register;
  Web Locks serialize giữa tab khi trình duyệt hỗ trợ. BroadcastChannel chỉ gửi
  sự kiện không chứa token. Không có Web Locks thì chỉ bảo đảm queue trong tab;
  phối hợp thực tế nhiều tab vẫn cần browser gate AUTH-06.
- Logout xóa memory, cache/cart và hủy protected requests ngay, rồi gọi server sau
  refresh đang chạy. Nếu server lỗi, UI nói rõ chưa xác nhận logout trên server.
  401 kết thúc truy cập; 403 dừng protected UI/request và cho kiểm tra lại quyền.
  Không tự phát lại mutation/download; không refresh lặp khi bị từ chối.
- `/user` và `/admin/account` hiển thị đúng identity DTO string ID/role codes.
  Trang Admin yêu cầu ADMIN từ `/me`; backend quyết định quyền trên mỗi request.
  Các trang protected Electro cần profile/CRUD chưa có được chặn bằng thông báo;
  compatibility stores không tạo rich profile hoặc giữ Auth thứ hai.

Deployment cùng origin: CRA proxy `/api` → backend 8080 ở development; production
reverse proxy `/api`. Auth từ chối WDM API khác origin, kể cả cấu hình legacy 8085.
Backend cần AUTH_JWT_SECRET/issuer/kid trong .env, origin khớp và role reference đã review riêng; xem
[Shared Auth backend](../docs/auth/README.md). HTTPS dùng refresh cookie Secure;
HTTP local chỉ dùng Secure=false tường minh. Không tạo ADMIN mặc định.

HTTP opt-in `src/auth/auth.live.test.ts` dùng frontend modules thật, proxy Node
cùng origin và Spring/MySQL biệt lập. Cấp rõ `AUTH05_LIVE_BACKEND`,
`AUTH05_PROXY_PORT`, `AUTH05_TEST_CONTAINER`, `AUTH05_TEST_CONTAINER_ID`,
`AUTH05_TEST_DB_PASSWORD`; test kiểm tra ID/label container trước khi ghi fixture.
DB tên `auth05_frontend_test`, label `codex-task=AUTH-05`, role reference CUSTOMER và
ADMIN phải chuẩn bị riêng trong DB test. Chỉ chạy fixture sạch, không dùng dev DB.
Các biến này chỉ dành cho Jest Node, không vào bundle. Chạy bằng direct Jest script
với `--watchAll=false --runInBand --runTestsByPath src/auth/auth.live.test.ts`.
Test tạo `ui.customer` và `ui.admin`, mật khẩu `Ui123456`; ADMIN được cấp riêng trong
fixture. Hai tài khoản chỉ có trong DB test đã hủy sau kiểm chứng, không tồn tại ở
development. Test chứng minh register, login/me, concurrent refresh, CUSTOMER 403,
ADMIN CSV Blob 13 cột và JWT bị từ chối sau logout. Cookie jar Node mô phỏng transport;
HttpOnly/Secure thực tế, browser download, CRA proxy và đa tab chưa kiểm chứng ở
checkpoint AUTH-05; AUTH-06 bên dưới bổ sung bằng chứng browser riêng.

## Chạy Auth browser gate (AUTH-06)

Kết quả ngày 2026-11-05: **10/10 browser cases PASS** trên Edge 156.0.4314.58;
CRA health proxy PASS; backend regression 138/138 và frontend 96 PASS (2 HTTP
opt-in skipped), TypeScript/build PASS với warnings kế thừa. Owner acceptance
AUTH-06 vẫn chờ review, không tự đóng BLK-01. Chỉ Edge được kiểm chứng; chưa có
production TLS trust/physical outage/100.000-record benchmark hoặc export UI nghiệp vụ.

Tooling E2E nằm riêng ở `e2e/` (playwright-core và esbuild có version/lockfile riêng),
không thay React/CRA hoặc dependency ứng dụng. Cần Node 22+, Java 21/keytool, Maven,
Docker với MySQL 8.4 image và Microsoft Edge cài sẵn. Package backend và build frontend
phải khớp source hiện tại trước khi chạy. Không khởi động backend với datasource dev
để chuẩn bị gate; lệnh Maven test toàn context cần datasource test tường minh.

Tại repository root, sau khi đã package backend bằng `mvn -pl backend -am package -DskipTests`:

```powershell
npm --prefix frontend/e2e ci
Push-Location frontend
node node_modules/react-scripts/bin/react-scripts.js build
Pop-Location
$env:AUTH06_REGRESSION = '1'
node frontend/e2e/run.mjs
Remove-Item Env:AUTH06_REGRESSION
```

`AUTH06_REGRESSION=1` chạy thêm full backend suite với datasource/keys test;
không đặt biến thì chỉ browser/CRA.
Runner dùng cổng ngẫu nhiên, tạo container `wspm-auth06-*`, label `codex-task=AUTH-06`,
DB `auth06_browser_test`, đúng CREATE/ALTER từ V2 và Hibernate validate; không chạy
Flyway/seed trên dev DB. Trước ghi/cleanup đều kiểm tra container ID/label. Key/cert
tạm nằm trong system temp, HTTPS self-signed chỉ được context browser test bỏ qua
lỗi tin cậy chứng chỉ; không cài certificate vào trust store hoặc đổi cài đặt Edge.

Browser dùng app build thật cho signup/login/Admin shell. `/__auth06` chỉ có trên
server test, bundle trực tiếp shared AuthBoundary/session/WDM client cho race và
download; không thêm route/hook test vào sản phẩm. Việc tải CSV chứng minh browser
download qua client thật, chưa cung cấp nút export nghiệp vụ tại trang datasets.
Test lưu token/cookie trong bộ nhớ transport, không ghi traces/credential ra file.

CRA proxy kiểm tra qua bridge test bind `127.0.0.1:8080` đến backend biệt lập. Nếu
cổng 8080 đã có process, runner giữ nguyên và báo NOT VERIFIED phần này. CRA 5 với
HOST loopback thiếu lanUrlForConfig; preload `e2e/cra.cjs` chỉ bổ sung allowedHost
`127.0.0.1`, không disable host checking, không sửa CRA dependency hoặc config sản phẩm.
CRA check này là health round-trip; Auth browser gate dùng HTTPS reverse proxy riêng.

Tài khoản browser fixtures: `e2e.customer`, `e2e.admin`, `e2e.signup`, mật khẩu
`Test1234`; ADMIN cấp riêng trong DB test. Chỉ tồn tại trong container bị dọn sau test,
không phải tài khoản development. Runner dọn browser, process tree do nó tạo,
container và thư mục temp sau cùng. Nếu bị kill cưỡng bức giữa chừng, kiểm tra đúng
ID/label/process/temp directory trước cleanup; không dọn tài nguyên development.

## Phạm vi hiện tại

Backend cung cấp health/product/category reads, shared Auth và Admin CSV export. Frontend vẫn
chứa nhiều feature từ hệ thống Electro gốc; chỉ xem một feature là được tích hợp
khi route, API, backend behavior và test tương ứng đều tồn tại.

`src/constants/ApplicationConstants.ts` còn URL legacy port 8085 cho các feature
Electro. Các chức năng WDM dùng client riêng bên dưới, không phụ thuộc hằng số này.
Việc chuyển các feature cũ không thuộc WDM-00.5.

## API client cho WDM

Import từ `api/admin/data-mining`. API root mặc định là `/api`; CRA proxy chuyển tới
`http://127.0.0.1:8080`. Production cần reverse proxy `/api`; nếu đổi
`REACT_APP_WDM_API_BASE_URL` thì API vẫn phải cùng origin.
Xem `.env.example`; không đặt token/secret vào biến `REACT_APP_*` vì chúng nằm trong bundle.
Không dùng backend khác origin với contract Auth hiện tại.

```typescript
import { getBackendHealth, wdmApi } from 'api/admin/data-mining';
import type { WdmPage } from 'api/admin/data-mining';

// Endpoint hiện có; không gửi Admin token.
const health = await getBackendHealth();

// Ví dụ cho feature WDM tương lai; endpoint này chưa được triển khai ở backend.
const runs = await wdmApi.get<WdmPage<{ runId: string }>>('/v1/admin/mining-runs', {
  query: { page: 0, size: 20, sort: ['createdAt,desc', 'runId,asc'] },
});
```

Client có `get`, `post` (JSON hoặc FormData), `download` trả Blob và metadata,
query lặp cho sort, và AbortSignal. Truyền path bắt đầu `/` tính từ API root;
không truyền URL đầy đủ hoặc nối query vào path. ID và BIGINT count giữ nguyên chuỗi;
response type không thay thế validation nghiệp vụ/schema ở feature tương ứng.

`WdmApiError` phân biệt HTTP, lỗi mạng, JSON response không hợp lệ và hủy request.
Lỗi HTTP có code, status, requestId, details; response lỗi HTML/plain text được
chuẩn hóa mà không đưa raw body vào thông báo. Không tự retry hoặc replay POST.

Shared Auth đã nối `setWdmAuthAdapter({ getAccessToken, getSessionSignal,
onUnauthorized, onForbidden })`. Token được lấy ở thời điểm request; signal kết hợp
hủy của caller và phiên Auth. Callback 401/403 giữ nguyên lỗi HTTP, hủy request khác
và xóa protected cache. WDM gửi Bearer với credentials omit; refresh cookie chỉ dùng
trong Auth client. Không tự retry/replay POST hoặc tải CSV. BLK-01 còn mở cho browser
gate và các tiêu chí chưa kiểm chứng.

Chạy test tập trung (Node 18+ có native Fetch; đã kiểm tra bằng Node 22):

```powershell
node node_modules/react-scripts/bin/react-scripts.js test --watchAll=false --runInBand --testPathPattern=src/api/admin/data-mining
node node_modules/react-scripts/bin/react-scripts.js build
```

Các lệnh gọi trực tiếp script có cùng chức năng với npm scripts và dùng được khi
node_modules trên máy chưa có npm `.bin` launcher cho Windows. Không cần nâng dependency.

Test HTTP thật là opt-in, không dùng fetch mock. Chạy backend và CRA dev server,
rồi trong terminal khác tại `frontend/`:

```powershell
$env:WDM_LIVE_API_BASE_URL = 'http://127.0.0.1:3000/api'
node node_modules/react-scripts/bin/react-scripts.js test --watchAll=false --runInBand --runTestsByPath src/api/admin/data-mining/health.live.test.ts
Remove-Item Env:WDM_LIVE_API_BASE_URL
```

Nếu chưa có backend/DB, không khởi động ứng dụng có Flyway chỉ để vượt smoke test.
Có thể kiểm tra HealthController thật trong Spring context cô lập không có DB,
nhưng phải ghi rõ đó là phép thử HTTP controller, không phải backend/database/Auth E2E.

## Điều hướng Admin Data & Mining

| URL giao diện | Trang |
|---|---|
| `/admin/datasets` | Dữ liệu khai phá |
| `/admin/mining-runs` | Lần chạy khai phá |
| `/admin/patterns` | Mẫu khai phá |

Source ở `src/pages/admin/data-mining/`; cấu hình route dùng chung với nhóm menu
“Dữ liệu & Khai phá” trong Admin shell hiện có. Liên kết đang chọn bám theo pathname,
bao gồm truy cập trực tiếp và back/forward. Trang chủ giữ nguyên; route Admin cũ
chưa được backend hỗ trợ hiển thị thông báo.

Menu và page guard WDM dùng shared identity đã xác minh qua `/me`, mã role ADMIN.
Bootstrap chờ xác minh; không tin storage hoặc decoded-only JWT. UI guard phục vụ
điều hướng; backend kiểm tra account/session/current role trước khi truy vấn.

Ba trang hiện là khung chức năng, thông báo rõ đang được chuẩn bị. Chưa có thao tác
upload/chạy mining, danh sách mẫu/run, số liệu mẫu hoặc request tới API chưa tồn tại.
Các task feature sau sẽ dùng client `api/admin/data-mining` để nối dữ liệu thực.

Kiểm tra cả navigation và API foundation:

```powershell
node node_modules/react-scripts/bin/react-scripts.js test --watchAll=false --runInBand --testPathPattern='src/(pages|api)/admin/data-mining'
```

## Quy tắc dọn frontend

- Xóa theo feature slice hoàn chỉnh, không xóa file rời chỉ dựa vào tên.
- Kiểm tra route/import graph trước khi xóa page, model, hook hoặc component.
- Chạy build và test sau mỗi đợt dọn.
- Không xóa attribution hoặc tài nguyên kế thừa trước khi kiểm tra
  `LICENSE-ELECTRO`.

Xem [README chung](../README.md) và [architecture map](../ARCHITECTURE.md) để
biết cách chạy toàn hệ thống.
