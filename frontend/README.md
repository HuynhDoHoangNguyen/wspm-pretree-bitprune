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

Known status: production build hiện PASS với ESLint warnings. Test nền frontend
đạt 62 tests; 1 live HTTP test chỉ chạy khi bật cấu hình opt-in bên dưới.
`src/App.test.tsx` dùng MemoryRouter và kiểm tra form đăng nhập Admin thật tại
`/admin` và `/admin/datasets` khi chưa đăng nhập. Auth state được cô lập trong test;
không gửi request hoặc kiểm chứng đăng nhập backend bằng smoke test này.

## Phạm vi hiện tại

Backend hiện mới cung cấp health, product và category endpoints. Frontend vẫn
chứa nhiều feature từ hệ thống Electro gốc; chỉ xem một feature là được tích hợp
khi route, API, backend behavior và test tương ứng đều tồn tại.

`src/constants/ApplicationConstants.ts` còn URL legacy port 8085 cho các feature
Electro. Các chức năng WDM dùng client riêng bên dưới, không phụ thuộc hằng số này.
Việc chuyển các feature cũ không thuộc WDM-00.5.

## API client cho WDM

Import từ `api/admin/data-mining`. API root mặc định là `/api`; CRA proxy chuyển tới
`http://127.0.0.1:8080`. Production cần reverse proxy `/api` hoặc cấu hình biến
`REACT_APP_WDM_API_BASE_URL`, ví dụ `https://backend.example/api`, trước khi build.
Xem `.env.example`; không đặt token/secret vào biến `REACT_APP_*` vì chúng nằm trong bundle.
Nếu dùng backend khác origin, chính sách CORS phải được module Auth/backend cấu hình.

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

Shared Auth tích hợp qua `setWdmAuthAdapter({ getAccessToken, onUnauthorized,
onForbidden })` và tháo bằng `setWdmAuthAdapter(null)`. Client lấy token ở thời điểm
request, không tự đọc localStorage, đăng nhập, refresh hoặc kiểm tra role. Adapter
chịu trách nhiệm cung cấp access token phù hợp; callbacks là nơi Auth/UI dừng polling,
xóa protected cache hoặc hiển thị access denied. Chưa có adapter thì không gửi
Authorization; backend vẫn phải từ chối request Admin chưa xác thực. Client hiện
dùng Bearer với `credentials: 'omit'`; nếu Auth chọn cookie/session, phải cập nhật
carrier và CSRF/CORS theo hợp đồng chung. BLK-01 vẫn mở.

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
bao gồm truy cập trực tiếp và back/forward. Không thay trang chủ hoặc các route Admin cũ.

Menu và page guard WDM kiểm tra mã role `ADMIN` từ Admin store hiện có ở mức UI.
Chưa đăng nhập vẫn qua luồng AdminSignin của shell; user thiếu quyền bị chặn ở page
guard kể cả mở URL trực tiếp. Đây không phải xác thực phiên hay phân quyền backend;
localStorage không phải nguồn quyền đáng tin cậy cho server và BLK-01 vẫn mở.

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
