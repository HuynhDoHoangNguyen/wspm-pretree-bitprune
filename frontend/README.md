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

Known status: production build hiện PASS với ESLint warnings. Test
`src/App.test.tsx` hiện FAIL vì render `App` ngoài Router (`useLocation`) và còn
assert nội dung CRA mẫu “hello world”. Cần sửa trong một frontend test task riêng.

## Phạm vi hiện tại

Backend hiện mới cung cấp health, product và category endpoints. Frontend vẫn
chứa nhiều feature từ hệ thống Electro gốc; chỉ xem một feature là được tích hợp
khi route, API, backend behavior và test tương ứng đều tồn tại.

`src/constants/ApplicationConstants.ts` hiện còn một legacy home URL dùng port
8085, khác backend port 8080 và development proxy. Đây là vấn đề cấu hình đã
biết, cần được xử lý trong task frontend riêng.

## Quy tắc dọn frontend

- Xóa theo feature slice hoàn chỉnh, không xóa file rời chỉ dựa vào tên.
- Kiểm tra route/import graph trước khi xóa page, model, hook hoặc component.
- Chạy build và test sau mỗi đợt dọn.
- Không xóa attribution hoặc tài nguyên kế thừa trước khi kiểm tra
  `LICENSE-ELECTRO`.

Xem [README chung](../README.md) và [architecture map](../ARCHITECTURE.md) để
biết cách chạy toàn hệ thống.
