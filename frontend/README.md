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
- Form đăng ký có xác nhận mật khẩu khớp chính xác, chỉ dùng ở UI; không gửi field
  này lên API. Hai form dùng Paper có viền, lỗi dưới từng ô, viền đỏ và focus tới
  field lỗi đầu tiên. Không trim mật khẩu. Không hiển thị nhãn CUSTOMER trên form;
  form Admin không có link đăng ký, Client giữ link Đăng ký.
- Login username/password → JWT → `/me` → hiển thị danh tính thật. Password tối thiểu
  8 codepoint, tối đa 72 byte UTF-8, không trim/cắt. Không gọi API giỏ hàng để login.
  Quy tắc độ dài trên là cho đăng ký; đăng nhập chỉ kiểm tra field rỗng ở UI.
  ADMIN đăng nhập ở cả hai form → `/admin` (trang hướng dẫn); CUSTOMER → `/`.
  Nút Trang quản trị trên header Client chỉ hiện với role ADMIN; Về cửa hàng trên
  header Admin đi về `/` trong cùng tab, giữ cùng shared session.
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
  Các nút logout dùng chung `auth/useSignOut.ts`, kết thúc tại `/` cả khi server
  chưa xác nhận logout (vẫn có thông báo lỗi). Mất phiên ở trang riêng tư Client
  trở về trang chủ thay vì giữ màn hình yêu cầu đăng nhập. Trang công khai không
  bắt đăng nhập; tài khoản/lịch sử đơn vẫn riêng tư. Giỏ hàng/đặt hàng legacy chưa
  được tích hợp thêm trong thay đổi UI này; khách vào `/cart` hiện đi tới đăng nhập.
- Không còn nút Khôi phục phiên trên form/AuthGate. Phiên lỗi có link Đăng nhập lại
  và Về cửa hàng; bootstrap/refresh tự động, cookie và bảo vệ backend giữ nguyên.
  Auth client chỉ giữ field name đã whitelist từ lỗi 400, không hiển thị raw detail.
  Sai credentials 401 đánh dấu nhóm tài khoản/mật khẩu; conflict 409 đánh dấu cả
  tài khoản và email với thông báo chung, không đoán field nào trùng. Lỗi mạng/server
  chỉ báo chung, không đánh dấu input sai.
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

Tooling hiện đặt tại `test/e2e/` theo yêu cầu owner; thư mục cũ `e2e/` đã chuyển
nguyên bộ source/lockfile sang vị trí này. Xem [E2E README](test/e2e/README.md).
AUTH-06 đã được owner chấp nhận; các giới hạn bên dưới là checkpoint của gate Auth.

Kết quả ngày 2026-11-05: **10/10 browser cases PASS** trên Edge 156.0.4314.58;
CRA health proxy PASS; backend regression 138/138 và frontend 96 PASS (2 HTTP
opt-in skipped), TypeScript/build PASS với warnings kế thừa. Owner acceptance
AUTH-06 vẫn chờ review, không tự đóng BLK-01. Chỉ Edge được kiểm chứng; chưa có
production TLS trust/physical outage/100.000-record benchmark hoặc export UI nghiệp vụ.

Tooling E2E nằm riêng ở `test/e2e/` (playwright-core và esbuild có version/lockfile riêng),
không thay React/CRA hoặc dependency ứng dụng. Cần Node 22+, Java 21/keytool, Maven,
Docker với MySQL 8.4 image và Microsoft Edge cài sẵn. Package backend và build frontend
phải khớp source hiện tại trước khi chạy. Không khởi động backend với datasource dev
để chuẩn bị gate; lệnh Maven test toàn context cần datasource test tường minh.

Tại repository root, sau khi đã package backend bằng `mvn -pl backend -am package -DskipTests`:

```powershell
npm --prefix frontend/test/e2e ci
Push-Location frontend
node node_modules/react-scripts/bin/react-scripts.js build
Pop-Location
$env:AUTH06_REGRESSION = '1'
node frontend/test/e2e/run.mjs
Remove-Item Env:AUTH06_REGRESSION
```

`AUTH06_REGRESSION=1` chạy thêm full backend + reactor mining-core suite với datasource/keys/storage test;
không đặt biến thì chỉ browser/CRA.
Runner truyền datasource/secret/storage biệt lập qua process environment cho Maven;
không chạy full-context tests bằng cấu hình DB mặc định. Các bước browser/regression
có deadline hữu hạn. File cấu hình local `frontend/.env` không thuộc source E2E.
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
HOST loopback thiếu lanUrlForConfig; preload `test/e2e/cra.cjs` chỉ bổ sung allowedHost
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

WDM-01.6 triển khai trang `/admin/datasets` cho Admin với hai thao tác độc lập:

1. Chọn Từ ngày / Đến hết ngày theo giờ Việt Nam (UTC+07), bấm Export CSV.
   Ngày kết thúc tính trọn ngày; client gửi UTC bounds [fromTime,toTime) đến
   GET `/api/v1/admin/order-exports`. File header-only vẫn tải hợp lệ.
2. Chọn CSV rồi bấm Upload CSV. Client gửi đúng part `file` đến POST
   `/api/v1/admin/dataset-snapshots`, không tự đặt multipart boundary.
   Chỉ receipt HTTP 201 đúng contract mới hiển thị snapshot ID/nguồn/PENDING.

Export không tự upload. Upload không tự validate/convert/chạy mining; chưa có
trang chi tiết hoặc danh sách snapshot trong task này. Chưa tính thống kê thì
sequenceCount vẫn null. Giới hạn byte cuối cùng do backend quyết định; UI xử lý
413 thay vì giả định giới hạn hardcoded luôn khớp cấu hình server.
Request được hủy khi rời trang/phiên kết thúc; không tự replay POST. Khi lỗi
mạng hoặc kết quả upload không chắc chắn, kiểm tra kết quả trước khi gửi lại
để tránh tạo snapshot trùng. Mã request hỗ trợ đối soát lỗi với backend.

Verification ngày 2026-11-05: frontend 157 PASS, 2 live HTTP opt-in skipped;
typecheck và focused lint PASS; production build PASS với warnings kế thừa
ngoài WDM. UI/API tests dùng HTTP mock, không thay thế WDM-01.8 browser/API/DB gate.
Contract và evidence chi tiết: [Dataset CSV](../docs/wdm/dataset-csv-contract.md).
`api/admin/data-mining/datasets.ts` giữ HTTP/file transport; `dataset-response.ts`
giữ validation/projection receipt/detail với các predicate chung. Không ép count
sang Number hoặc nới whitelist response khi refactor.
CSV export có UTF-8 BOM để Excel nhận diện tiếng Việt khi mở file trực tiếp.
Sau khi cập nhật backend, hãy export file mới; file đã tải/snapshot cũ vẫn giữ
nguyên bytes. Upload và tải raw không tự thêm/xóa BOM hoặc đổi encoding.

WDM-01.7 thêm link **Xem chi tiết snapshot** sau upload và route
`/admin/datasets/:snapshotId`. Trang tải metadata thật khi mở URL/reload, hiển thị
nguồn, trạng thái, thời điểm UTC, dung lượng và checksum đã ghi lúc tiếp nhận.
Metadata chưa có hiển thị “Chưa xác định”; sequence_count nội bộ 0 không được
hiển thị như thống kê đã tính. Có thể tải CSV gốc khi artifact sẵn sàng; backend
xác minh size/SHA-256 trước khi trả bytes. File thiếu hoặc lỗi không làm mất
thông tin snapshot. Route/file access vẫn cần ADMIN; không có trang danh sách,
Validate/Convert/Run trong slice này. Gate browser/API/DB toàn luồng giữ ở WDM-01.8.

WDM-01.8 technical PASS và OWNER ACCEPTED ngày 2026-11-05: Edge Dataset
7/7 case và Auth regression 10/10, CRA proxy PASS; frontend 183 PASS, 2 opt-in
SKIPPED. Gate Dataset dùng build/route/login thật, MySQL và storage biệt lập,
đối chiếu DB/file/SHA-256; không dùng harness Auth thay giao diện nghiệp vụ.
Gate phát hiện và sửa Admin shell chặn route detail; regression trong App.test.tsx
giờ đi qua lớp Admin thật. Chạy từ repository root sau khi package/build hiện tại:

```powershell
npm --prefix frontend/test/e2e run test:dataset
```

Mã test nằm tại `test/e2e/`; source/fixture nhỏ/lockfile/README có thể version,
output và browser credentials được ignore. Xem [hướng dẫn E2E](test/e2e/README.md).
Gate dùng file limit 4 KiB để test 413; không phải benchmark 100 MiB, migration
upgrade test hoặc chứng nhận production/cross-browser. Không ghi DB development.

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

Dataset hiện có export/upload/detail/raw như mô tả ở trên. Mining Runs và Patterns
vẫn là khung chức năng, chưa có chạy mining hoặc danh sách mẫu/run từ backend.

### WDM-02.6 — Validation trong chi tiết snapshot

`/admin/datasets/:snapshotId` có panel **Kiểm tra dữ liệu** dùng POST/GET validation
đã triển khai ở backend. POST không có body/query, chỉ chạy khi Admin chủ động
nhấn nút; không tự retry khi mất kết nối, lỗi 409/503 hoặc refresh phiên. Khi kết
quả POST chưa xác nhận, dùng **Kiểm tra lại trạng thái** để đọc bằng GET trước
khi cân nhắc thao tác mới. Abort/unmount không hủy công việc trên máy chủ.

Report đã hoàn tất tự tải bằng GET. Response reader kiểm tra snapshot ID, version,
policy, schema/counters và trang; panel đối chiếu SHA/size với metadata. Counter
giữ dạng chuỗi, null hiển thị Chưa xác định; prefix parse không là tổng dataset.
VALID chỉ xác nhận cleaned subset còn dùng được, không khẳng định mọi dòng raw
đúng. Report cũ là bằng chứng trên checksum đã ghi, không re-attest file/catalog.

Diagnostics có stage/code/field/vị trí, phân trang server 20 thông báo; nhóm vị
trí dài hiển thị từng 5 vị trí có nhãn rõ. Không render raw values hoặc manifest.
Metadata được tải lại sau POST thành công hoặc GET phục hồi trạng thái khác
metadata. Lỗi tải metadata không xóa report đã nhận. Request/report cũ bị loại
khi đổi snapshot/binding hoặc identity/session; dùng Auth/WDM client hiện hữu.

Không thêm route, dependency, Auth flow, conversion/mining action hoặc thay đổi
backend contract. Focused tests gồm validation API/reader, panel và snapshot
detail. Gate `npm --prefix frontend/test/e2e run test:dataset` từ repo root dùng
UI build thật, shared Auth, backend và MySQL cô lập, nay gồm cả validation.

Kiểm tra cả navigation và API foundation:

```powershell
node node_modules/react-scripts/bin/react-scripts.js test --watchAll=false --runInBand --testPathPattern='src/(pages|api)/admin/data-mining'
```

### WDM-03.7 — Conversion trong chi tiết snapshot

Panel **Chuyển đổi dữ liệu** đọc GET khi mở/reload trang. Nút POST chỉ bật khi
báo cáo validation VALID/READY/countsComplete khớp ID, raw hash/size và metadata
hiện tại. UI chặn click trùng ngay lập tức; không tự gửi lại POST khi mất phản
hồi. Kết quả được đối soát bằng GET và metadata được cập nhật sau thành công.
RUNNING được đọc tuần tự mỗi 2 giây, tối đa 60 giây rồi cần đọc lại thủ công;
FAILED không có nút retry. Đổi snapshot, rời trang hoặc logout hủy chờ phía client.

SUCCEEDED hiển thị chín thống kê từ backend, size và SHA-256 do máy chủ cung cấp.
Count/size/length giữ dạng chuỗi thập phân, không ép sang Number. Độ dài sequence
là tổng số item; không tính công thức mining trong TypeScript.

**Xem trước SPMF** chỉ chạy khi Admin bấm, với tệp <= 1 MiB. HTTP stream bị hủy
khi vượt giới hạn byte, kể cả Content-Length bị thiếu/sai. Nội dung React text
giữ nguyên dòng và delimiter, tối đa 20 dòng sequence / 4000 ký tự; không cắt
giữa sequence. Sequence đầu quá dài thì hướng dẫn tải file. Tệp lớn vẫn tải
được bằng **Tải SPMF** / **Tải mapping JSON**. Download dùng Auth chung, URL được
dựng từ UUID hợp lệ, kiểm tra MIME, tên file cố định và size; không tự xác minh
SHA-256 phía trình duyệt. Lỗi chỉ có diễn giải an toàn và request ID nếu có.

Không thêm route, thư viện, backend/schema hoặc thay đổi mining-core. Dataset
browser gate có 15 ca gồm conversion UI thật, mất POST response được khôi phục
bằng GET và logout khi response artifact bị giữ lại. WDM-03.8 (parse artifact
qua mining-core và đối chiếu sequence) vẫn là task riêng.

### Cải thiện UI/UX sau WDM-03.8

`/admin` là trang chủ hướng dẫn bảy bước từ CSV đến combo. Đăng nhập Admin và
liên kết Trang quản trị đều dẫn tới trang này; khi form Admin mở từ một URL con,
nó chọn `/admin` trước khi xác thực để việc thay form bằng nội dung đã đăng nhập
không làm mất điều hướng. URL chi tiết vẫn mở trực tiếp được khi đã đăng nhập.
Bốn bước chuẩn bị/kiểm tra/chuyển đổi đã có chức năng; khai phá, xem mẫu và tạo
combo ghi rõ Chưa khả dụng. Không gọi các API legacy của dashboard cũ.

Trang dữ liệu tổ chức xuất/upload theo hai bước, kết quả upload có tên file,
trạng thái dễ đọc, sao chép Snapshot ID và nút Tiếp tục kiểm tra dữ liệu. Nút này
mở `/admin/datasets/:id?tab=validation`. Clipboard không được cấp quyền thì vẫn
có ID đầy đủ để sao chép thủ công; không ghi snapshot/token vào storage.

Chi tiết snapshot có tóm tắt/trạng thái và nút dẫn tới bước cần làm ở phía trên.
Ba tab Dữ liệu gốc, Kiểm tra dữ liệu, Chuyển đổi & thống kê dùng query `tab` chỉ
cho điều hướng UI; query này không chuyển tới API. Nếu không chỉ định tab, chọn
bước theo metadata lần đầu; không tự đổi tab khi kết quả đến trong lúc đọc báo
cáo. Các panel vẫn mounted khi ẩn nên giữ report, preview, request guard, polling
và abort lifecycle. Đổi tab chỉ đổi nội dung nhìn thấy, không POST lại. Tab có
chỉ báo đang xử lý/lỗi, kèm thông báo ngoài tab để lỗi không bị giấu. Tab hỗ trợ
Arrow Left/Right, Home/End, ARIA selection và URL reload. Bước không đủ điều kiện
vẫn xem được nhưng nút xử lý tuân theo validation/conversion gate hiện có.

Metadata/checksum thu gọn, số đếm trình bày thành ô và giữ nguyên chuỗi chính xác.
Bảng thông báo vuông, có viền, header tô màu/cố định và cuộn trong vùng riêng.
Typography/màu chữ phụ được áp dụng trong workspace, không đổi theme toàn bộ cửa
hàng. Giao diện tài khoản Admin và `/user` cùng dùng IdentityPanel đã bố trí lại
theo thông tin đăng nhập, xác minh email và quyền; không bật form sửa tài khoản
hoặc các API hồ sơ legacy chưa được hỗ trợ.

## WDM-04.5 — Cấu hình khai phá

Trang `/admin/mining-runs` dành cho Admin có form snapshot ID, thuật toán,
mode và giá trị minsup. Từ tab chuyển đổi của snapshot `SUCCEEDED/COMPUTED`,
chọn **Cấu hình khai phá** để mở `/admin/mining-runs?snapshotId=<UUID>`.
ID được điền sẵn nhưng không tự gửi request. Chưa có API danh sách snapshot;
form không sử dụng danh sách hoặc dữ liệu mẫu.

- **Xem trước dữ liệu** gọi POST `/api/v1/admin/mining-runs/preview`.
- **Kiểm tra cấu hình** gọi POST `/api/v1/admin/mining-runs/configuration-validation`.
- Hai thao tác dùng cùng request bốn trường và Auth client hiện có. Không có nút
  tạo/chạy mining run trong task này.
- `PERCENT: 14` là 14%; ABSOLUTE là ngưỡng hỗ trợ có trọng số, cho phép số thập
  phân. Form giữ chuỗi khi nhập, hỗ trợ ký pháp khoa học và không tính threshold.
- Thống kê, relative fraction và effective threshold lấy từ backend; count giữ
  nguyên chuỗi số. Preview không cấp quyền thực thi; SUPPORTED chỉ có nghĩa cấu
  hình qua kiểm tra tại thời điểm đọc. Lỗi 422 zero được giải thích riêng.
- Sửa cấu hình/đổi phiên hoặc quyền xóa kết quả cũ, hủy request đang chạy và bỏ
  response đến muộn. Không tự replay POST, tự submit khi mở trang hoặc lưu marker
  SUPPORTED vào localStorage.

API types/decoders nằm trong `src/api/admin/data-mining/mining*.ts`; form/hook/
result thuộc `src/pages/admin/data-mining/`. Hợp đồng và evidence:
`../docs/wdm/mining-request-contract.md`, mục WDM-04.5. Real browser/API/DB gate
cho cấu hình thuộc WDM-04.6; module tests ở đây dùng mocked HTTP.

## WDM-05.8 — Admin Execution UI

- `/admin/mining-runs`: giữ preview/validation; nút **Tạo lần chạy** chỉ bật với
  cấu hình hiện tại đã SUPPORTED. POST đúng bốn trường, một flight; không tự replay.
- `202` hợp lệ mở `/admin/mining-runs/:runId`. Có thể mở run đã biết bằng ID/link;
  direct navigation/refresh chỉ GET, không có danh sách lịch sử giả.
- Detail hiển thị config, trạng thái, UTC nguyên chuỗi (giữ microseconds), lỗi an
  toàn và pattern count cơ bản. BIGINT giữ dạng chuỗi, null khác zero. Metrics
  chi tiết thuộc WDM-05.9; SUCCEEDED chưa xác nhận durable pattern storage.
- Polling serial sau 2 giây, budget 2 phút, timeout GET 15 giây/POST 45 giây.
  Hidden/unmount/Auth change hủy reads/timers; terminal dừng polling. **Đọc lại /
  Tiếp tục theo dõi** chỉ gửi GET. Dừng theo dõi không hủy mining.
- Mất POST/503/response không hợp lệ có thể sau commit: giữ submitted config và
  requestId, hiển thị uncertainty, không tự gửi lại hoặc đoán runId. Tạo yêu cầu
  mới là thao tác rõ ràng và phải kiểm tra lại cấu hình. Không lưu token/result/
  SUPPORTED vào localStorage; không thêm cancel/retry/ack API.

API/decoders: `src/api/admin/data-mining/mining-runs.ts`,
`mining-run-response.ts`. Hooks/pages: `src/pages/admin/data-mining/useMiningRun*`,
`MiningRunDetailPage`, `MiningRunStatusPanel`, `MiningRunCreationFeedback`.
Browser UI smoke: `node frontend/test/e2e/execution-ui-smoke.mjs` từ repo root;
dùng actual components/client/guard với fixture identity và mocked HTTP, không
khởi động backend hoặc sửa owner DB. Xem `test/e2e/README.md`.

## WDM-05.9 — Metrics UI

Trang chi tiết run dùng cùng snapshot status để hiển thị hai counts, sáu timing
và sampled heap. Counts/timings/bytes giữ chuỗi thập phân; dấu chấm chỉ phân nhóm
chữ số, không chuyển qua Number. Timing dùng ms, heap dùng bytes; chưa có số đo
khác số đo 0. Với contract WDM-05.6, 0 ms có thể do phép đo dưới 1 ms được chia
nguyên từ nanos.

Scope hiện tại chỉ được giải thích khi measurement descriptor xác nhận WDM-05.6.
Legacy giữ giá trị đã lưu và thông báo scope chưa được xác minh. Null output I/O
trong contract hiện tại nghĩa chưa có output stage; null lịch sử chỉ là thiếu số đo.
PreTree dùng candidateCount, BitPrune dùng generatedExtensions; định nghĩa bộ đếm
native được giữ nguyên. Heap là sampling used heap dùng chung của JVM trong thuật
toán; các scope thời gian có phần lồng nhau và không được cộng để suy ra tổng.

Panel không thêm GET/polling, đổi trạng thái hoặc tính thời gian từ timestamps.
GET lỗi giữ metrics cùng status cũ dưới banner tạm dừng. Browser smoke mở rộng:
`node frontend/test/e2e/execution-ui-smoke.mjs --metrics` từ root (actual UI với
identity/HTTP fixtures). Engine/browser/API/DB gate vẫn thuộc WDM-05.10.

## Quy tắc dọn frontend

The mining workspace follow-up adds the snapshot library at `/admin/datasets`
and create/history tabs at `/admin/mining-runs?tab=history`. Both lists are
server-backed, newest-first, paged and URL-filtered. Export/upload remain in
the "Tạo dữ liệu mới" tab (`?tab=create`); the default "Danh sách dữ liệu" tab
shows the library. Tab selection and list filters remain in the URL, and form
inputs are retained when switching tabs. Configuration output uses summary
cards and distinct backend threshold fields; run detail retains exact metrics.

The capacity card describes slots on the current backend instance, not a DB
save quota. Completed retained results can still occupy slots. Backend file
locations show the current snapshot publication metadata; they do not prove
file integrity or historical run publication identity. The existing verification
boundaries remain authoritative. Read contracts and limitations are documented
in `docs/wdm/mining-request-contract.md` (workspace follow-up section).

WDM-05.10 bổ sung gate thật bằng
`node frontend/test/e2e/engine-run.mjs`: production build, shared Auth, HTTPS,
backend đã package, MySQL riêng và Flyway V1-V3. Mỗi kết quả gốc được đối chiếu
đầy đủ pattern/support với hai engine và oracle; metrics được kiểm tra qua
capture/DB/API/UI. Các ca sửa artifact đang chờ, mất phản hồi 202, hết capacity
và zero/underflow không dùng response thành công giả. Lệnh tái lập và giới hạn
nằm tại [E2E README](test/e2e/README.md). Gate cũng phát hiện và sửa allowlist
`Admin` shell cho trang chi tiết run; `App.test.tsx` bảo vệ route này. Backend,
mining-core production, schema và dependency versions không đổi.

- Xóa theo feature slice hoàn chỉnh, không xóa file rời chỉ dựa vào tên.
- Kiểm tra route/import graph trước khi xóa page, model, hook hoặc component.
- Chạy build và test sau mỗi đợt dọn.
- Không xóa attribution hoặc tài nguyên kế thừa trước khi kiểm tra
  `LICENSE-ELECTRO`.

Xem [README chung](../README.md) và [architecture map](../ARCHITECTURE.md) để
biết cách chạy toàn hệ thống.
