# Tạo combo ba sản phẩm cho đủ ba loại

## 1. Hiển thị combo G502 → Bose đang có

Kiểm tra chỉ đọc trên database local ngày 2026-11-10 xác nhận:

- Combo `combo-90adb2bacbf49672f6aaa32b224c8a23`: `APPROVED`.
- Batch `batch-b29983f138154171b9b015d0f4d1f21d`: `DRAFT`.
- Số batch `ACTIVE`: **0**; API `/api/v1/recommendations/combos/latest` trả `[]`.
- RUN `SUCCEEDED`; hai sản phẩm và danh mục đều `ACTIVE`. Có bản ghi writer
  evidence, nhưng sự tồn tại của bản ghi không thay thế kiểm chứng nội dung.

Vì vậy, bước đang thiếu là **công bố batch**, không phải khởi động lại server.

1. Đăng nhập Admin, mở
   `/admin/combos?batchId=batch-b29983f138154171b9b015d0f4d1f21d`.
2. Bấm **Công bố batch cho khách hàng**. Chờ thông báo thành công và bảng chuyển
   sang **Đã công bố**, batch **Đang hoạt động**.
3. Nếu thất bại, đọc mã lỗi/mã tra cứu ở thông báo. Không sửa status bằng SQL.
   `INVALID_RESOURCE_STATE` nghĩa là điều kiện nguồn/trạng thái hiện tại chưa đạt;
   nếu giá hoặc catalog đã thay đổi, cần sinh và duyệt đề xuất mới.
4. Mở `/products/b39d390d-6719-535c-acc6-cd6dbf08f72c`.
   Combo nằm trong khung nhỏ ở cột phải; mobile nằm sau thông tin sản phẩm.
5. Khách không đăng nhập vẫn xem được combo công khai. Đăng nhập `customer_006`
   bằng mật khẩu đã cấp để kiểm tra gợi ý bước tiếp theo. Tài khoản này đã mua
   G502 và chưa mua Bose theo lần kiểm tra trước; cần đối chiếu lại khi dữ liệu đổi.

Task chỉ đọc database development, không bấm công bố thay người dùng, không đổi
mật khẩu hoặc chèn đơn hàng. Việc công bố vẫn là thao tác Admin chủ động.

## 2. Ba trường hợp cần tạo

Gọi A = G502, B = Bose, C = Balo Wenger. Mỗi combo có ba sản phẩm phân biệt:

| Loại | Cấu trúc | Lịch sử minh họa |
|---|---|---|
| Mua cùng đơn | `<(A B C)>` | Một khách, một đơn chứa cả A, B, C |
| Mua theo thứ tự | `<(A)(B)(C)>` | Một khách, ba đơn theo thời gian A → B → C |
| Kết hợp | `<(A B)(C)>` | Một khách mua A+B cùng đơn, sau đó một đơn mua C |

Ba sản phẩm không nhất thiết là ba bước mua. Tăng quantity không làm tăng số
sản phẩm phân biệt. Không gộp đơn khác nhau thành một itemset.

## 3. Chuẩn bị CSV minh họa cho catalog hiện có

Đây là dữ liệu **synthetic để minh họa**, không phải giao dịch bán hàng thật.
Upload CSV chỉ tạo snapshot phục vụ mining, không thêm vào bảng ORDERS và không
tạo tài khoản mang tên customer_code trong file.

Từ PowerShell tại thư mục repository, khi backend đang chạy:

```powershell
$catalog = (Invoke-RestMethod 'http://localhost:8080/api/v1/products').data
$demoMouse = @($catalog | Where-Object { $_.productId -eq 'b39d390d-6719-535c-acc6-cd6dbf08f72c' })
$demoSpeaker = @($catalog | Where-Object { $_.productId -eq 'ff61761c-4237-50e3-a649-ed7e240da45f' })
$demoBag = @($catalog | Where-Object { $_.productId -eq '407ef449-c328-548e-82fb-3e1523965fee' })
if ($demoMouse.Count -ne 1 -or $demoSpeaker.Count -ne 1 -or $demoBag.Count -ne 1) {
  throw 'Catalog không giống bản demo. Chọn lại ba productId thực trước khi tạo file.'
}
$demoProducts = @($demoMouse[0], $demoSpeaker[0], $demoBag[0])
if (@($demoProducts | Where-Object { $_.productStatus -ne 'ACTIVE' }).Count -gt 0) {
  throw 'Ba sản phẩm phải đang ACTIVE.'
}
$demoCsv = Get-Content 'backend/src/test/resources/wdm/csv-v1/combo-three-items.csv' -Raw -Encoding UTF8
$demoCsv = $demoCsv.Replace('native-keyboard', $demoMouse[0].productId)
$demoCsv = $demoCsv.Replace('native-mouse', $demoSpeaker[0].productId)
$demoCsv = $demoCsv.Replace('native-bag', $demoBag[0].productId)
$demoCsv = $demoCsv.Replace('H-K', 'SV3V2-P12').Replace('H-M', 'SV3V2-P04').Replace('H-B', 'SV3V2-P19')
$demoCsv = $demoCsv.Replace('Historical Keyboard', 'Demo G502').Replace('Historical Mouse', 'Demo Bose').Replace('Historical Bag', 'Demo Wenger')
$demoOut = Join-Path ([IO.Path]::GetTempPath()) ('combo-three-items-' + [Guid]::NewGuid().ToString('N') + '.csv')
[IO.File]::WriteAllText($demoOut, $demoCsv, [Text.UTF8Encoding]::new($false))
Write-Output $demoOut
```

File có 9 dòng sản phẩm, 3 customer_code synthetic và 6 đơn. Các tên/SKU trong
CSV là snapshot lịch sử demo; chúng không cập nhật catalog. Không dùng lệnh seed
lại database. Nếu catalog khác, dùng productId/SKU của chính catalog đó.

## 4. Upload → mining → sinh combo

1. Admin → **Dữ liệu khai phá** → **Tạo dữ liệu mới** → upload file vừa tạo.
2. Chạy validation, sau đó conversion. Kiểm tra **3 sequences**, **3 distinct
   items**, **6 itemsets**, **9 occurrences**. Tổng occurrence không phải quantity.
3. Tạo RUN từ snapshot này. Chọn `WSPM_PRETREE`, mode `ABSOLUTE`, giá trị `0.1`
   (giao diện trình duyệt có thể dùng dấu thập phân theo locale). Đây là ngưỡng
   demo dương nhỏ, không phải 10%, không phải số nguyên tối thiểu một đơn.
4. Chờ RUN `SUCCEEDED` và kết quả được công bố đầy đủ. Có thể chạy lại
   `WSPM_BITPRUNE` cùng snapshot/cấu hình để xem kết quả tương ứng; không cần cho
   bước hiển thị client này.
5. Mở phần Combo từ chi tiết RUN → **Xem đề xuất và lưu Combo**.
6. Chọn ba danh mục bắt buộc **Chuột**, **Loa**, **Balo**. Quantity mỗi danh mục = 1.
7. **Số sản phẩm tối thiểu = 3**, **tối đa = 3**. Số đề xuất xem trước = 20.
   Để sản phẩm neo trống nếu giao diện cho phép; neo chỉ lọc phần xem trước.
8. Bấm **Xem trước Combo**. Kiểm tra có cả ba nhãn loại và cấu trúc từng bước.
   Nếu chưa có, kiểm tra dữ liệu đầu vào, ngưỡng, danh mục và các lý do loại ứng
   viên. Không chỉnh support hoặc tạo PATTERN/COMBO bằng SQL.
9. Lưu các combo DRAFT, mở batch, duyệt từng combo cần hiển thị.
10. Duyệt xong mới bấm **Công bố batch cho khách hàng**. Batch trước đó sẽ được
    lưu trữ; combo G502 → Bose hai sản phẩm của batch cũ không còn xuất hiện khi
    batch demo mới được kích hoạt. Đó là quy tắc một batch ACTIVE đã thống nhất.

Các ID item số trong canonical pattern phụ thuộc mapping của snapshot. Đối chiếu
tên/productId và ranh giới bước, không giả định A luôn có item ID bằng 1.

## 5. Kiểm tra client và tài khoản

- Không đăng nhập: mở chi tiết G502; xem các combo công khai, ảnh, giá và tick
  chọn. Tối đa hai card trong cột phải; mở link chi tiết để xem toàn bộ combo.
  Để kiểm tra đủ ba loại khi trang chỉ ưu tiên hai card, lấy mã từng combo từ
  bảng Admin rồi mở `/combos/{comboId}`. Không phải cả ba loại luôn đồng thời
  nằm trong hai vị trí gợi ý đầu tiên.
- Mở `/products`: dưới bộ lọc là tối đa hai card mới nhất. Mỗi card chứa các dòng
  ảnh/tên/giá của sản phẩm thành phần. Đây là mới công bố, không phải doanh số hot.
- `customer_006`: lịch sử hiện có có G502, chưa có Bose nên có thể khớp bước A
  của mẫu A → B → C và gợi ý B. Tài khoản này cũng đã mua Wenger: chính sách không
  gợi ý lại sản phẩm đã mua sẽ ảnh hưởng các bước sau, không đảm bảo gợi ý C.
- Mẫu hỗn hợp `<(A B)(C)>` yêu cầu một đơn COMPLETED thực của tài khoản chứa A+B.
  Upload CSV synthetic không tạo lịch sử cho tài khoản đăng nhập. Nếu tài khoản
  chưa đáp ứng, chỉ có gợi ý theo sản phẩm, không có gợi ý cá nhân của mẫu đó.
- Với mua cùng đơn, không suy diễn rằng người đã mua A trong quá khứ đang có A
  trong giỏ hiện tại. Card hiển thị tham khảo đầy đủ thành phần combo.

Tick chọn chỉ tính tổng giá hiện tại của các sản phẩm được chọn; chưa tạo đơn,
chưa áp dụng ưu đãi của combo đầy đủ. Không có combo hợp lệ thì ẩn cả khu vực.

## 6. Bằng chứng và giới hạn

Fixture gốc `combo-three-items.csv` dùng catalog synthetic `native-*` của E2E.
Kết quả chạy thật và trạng thái nghiệm thu được ghi trong
[client-combo-contract.md](client-combo-contract.md). File chuyển đổi theo catalog
development ở bước 3 cần bạn upload/chạy và duyệt/công bố chủ động; không được
coi là đã nghiệm thu trên database của bạn chỉ vì fixture E2E đạt.
