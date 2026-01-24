README — Hướng dẫn sử dụng trang /search-info
=============================================

Mục đích
--------
File này mô tả chi tiết về trang `/search-info` trong ứng dụng MERAGroup backend: mục đích, các chức năng chính, luồng dữ liệu front-end ↔ back-end ↔ Lark Bitable, cách sử dụng UI và cách debug khi cần.

Mô tả ngắn
-----------
- Trang: `/search-info` (template `searchInfo.html` / `demo.html`) — giao diện để tra cứu thông tin khách hàng theo số điện thoại, hiển thị:
  - Thông tin khách hàng (tên, SĐT, địa chỉ, customer id)
  - Lịch sử mua hàng
  - Nhật ký / Trao đổi (exchanges) — load từ Lark Bitable hoặc từ endpoint aggregate
  - Cho phép thêm "Trao đổi" mới (ghi lên Lark nếu có cấu hình bảng tương ứng)

Các thành phần kỹ thuật chính
----------------------------
- Frontend:
  - `src/main/resources/templates/demo.html` (UI)
  - `src/main/resources/static/js/search-info.js` (logic tìm kiếm, render, thêm trao đổi)
  - Khi người dùng bấm tìm: frontend gọi `GET /api/search-info?phone=<phone>` để lấy customer + orders + notes.
  - Trao đổi (exchanges) được load theo thứ tự ưu tiên:
    1. Nếu có `baseId` & `tableId` (truyền qua query `?baseId=...&tableId=...`) hoặc biến global, frontend POST tới `/api/lark/search-by-table?baseId=...&tableId=...&phone=...` để lấy trao đổi từ bảng Lark cụ thể.
    2. Nếu không có, fallback sang aggregate endpoint `/api/exchanges?phone=...` (server quét nhiều bảng CSKH để lấy trao đổi).
- Backend:
  - `src/main/java/org/report/backend/searchCustomer/Controller/SearchInfoController.java`
    - `GET /api/search-info?phone=...` — trả thông tin customer, orders, notes từ POS.
  - `src/main/java/org/report/backend/controller/authenController.java`
    - `GET /api/exchanges?phone=...` — aggregate: quét tất cả bảng "Trao Đổi" của các CSKH đã cấu hình trong session.
    - `POST /api/lark/search-by-table` — search records trong 1 table cụ thể (proxy tới Lark records/search).
    - `POST /api/lark/create-record` — tạo record mới trong 1 table Bitable (proxy tới Lark records POST).
  - Token Lark được quản lý bởi `LarkTokenService` (refresh khi cần).

Dữ liệu trao đổi (fields cần lưu)
---------------------------------
Khi lấy hoặc tạo trao đổi, hệ thống xử lý và lưu lại (hoặc truyền qua) các trường chính:
- `Nội dung` (Nội dung trao đổi) — rich text trên Lark, trên frontend ta trích text thuần.
- `Ngày` — giữ nguyên giá trị gốc (có thể là timestamp ms hoặc chuỗi). Khi tạo mới ta dùng timestamp hiện tại (ms).
- `PhoneNumber` — số điện thoại; dùng để tìm record.
- `Khách Hàng` — field liên kết (link_record_ids) — dùng để liên kết record trao đổi với record Khách Hàng trong Bitable.

Thao tác chính — Hướng dẫn sử dụng (user)
-----------------------------------------
1. Tra cứu khách:
   - Mở trang `/search-info` hoặc `demo.html`.
   - Nhập SĐT vào ô tìm kiếm ở header (hỗ trợ Enter hoặc bấm Tra cứu).
   - Trang sẽ hiển thị thông tin khách, lịch sử đơn và phần "Trao đổi".

2. Xem trao đổi:
   - Trao đổi hiển thị trong khung "Trao đổi". Nếu site được mở với `?baseId=...&tableId=...` (ví dụ: link chia sẻ), frontend sẽ query bảng đó trực tiếp; nếu không, server sẽ aggregate từ cấu hình CSKH.

3. Thêm trao đổi:
   - Bấm "Thêm mới" → modal "Thêm trao đổi mới" xuất hiện.
   - Nhập nội dung (chỉ phần text).
   - Khi lưu:
     - Frontend tự thêm tiền tố `PK: ` vào nội dung trước khi gửi.
     - Nếu frontend có context `baseId` + `tableId` + `linkRecordIds` (được lưu trong `window._lastExchangeContext` khi fetch), frontend sẽ gọi backend:
         POST `/api/lark/create-record?baseId=<BaseID>&tableId=<TableID>`
         Body JSON: { "content": "PK: ...", "ngay": <timestamp_ms>, "linkRecordIds": ["recv..."] }
     - Backend gọi Lark API tạo record trong bảng tương ứng (dùng access token server-side).
     - Sau khi tạo thành công, frontend sẽ prepend item vừa tạo vào danh sách hiển thị; khi search lại SĐT thì server sẽ trả record mới (không bị mất).

Curl ví dụ
----------
1) Search trong 1 table:
```bash
curl -i -X POST 'https://open.larksuite.com/open-apis/bitable/v1/apps/<BASE_ID>/tables/<TABLE_ID>/records/search?page_size=500' \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer <ACCESS_TOKEN>' \
-d '{
  "automatic_fields": false,
  "field_names": ["Khách Hàng","Nội dung","PhoneNumber","Ngày"],
  "filter": { "conditions":[{"field_name":"PhoneNumber","operator":"is","value":["<SDT>"] }],"conjunction":"and" },
  "view_id":"vewNXdsB3K"
}'
```

2) Tạo record mới:
```bash
curl -i -X POST 'https://open.larksuite.com/open-apis/bitable/v1/apps/<BASE_ID>/tables/<TABLE_ID>/records?user_id_type=union_id' \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer <ACCESS_TOKEN>' \
-d '{
  "fields": {
    "Nội dung": "PK: Khách mua ...",
    "Ngày": 1700000000000,
    "Khách Hàng": ["recv962tQ21N70"]
  }
}'
```

Lưu ý triển khai & debugging
-----------------------------
- Không gọi Lark API trực tiếp từ trình duyệt (token bí mật) — frontend luôn gọi endpoint backend (`/api/lark/search-by-table`, `/api/lark/create-record`) để server proxy.
- Nếu sau khi lưu bạn không thấy record khi search lại:
  - Kiểm tra browser console network: có request tới `/api/lark/create-record?...` không; response có `code:0` không.
  - Kiểm tra `window._lastExchangeContext` có chứa `baseId`/`tableId`/`linkRecordIds` không (frontend cần context để biết tạo vào bảng nào).
  - Kiểm tra log server (authenController) để xem lỗi gọi Lark (token, rate limit, permission).
- Nếu muốn lưu bản sao ở server (DB) thay vì chỉ dựa trên Lark, nên mở rộng backend để persist (migration + service).

Gợi ý cải tiến tiếp theo
------------------------
- Tự động inject `baseId/tableId` mặc định từ session `SESSION_USER_CONFIGS` vào trang khi render để người dùng không cần truyền query param.
- Sau khi tạo record thành công, tự động refresh lại danh sách bằng cách gọi lại endpoint `/api/lark/search-by-table` hoặc `/api/exchanges`.
- Thêm xác nhận lỗi/notify cho người dùng (toast) khi tạo thất bại.

Vị trí file liên quan
---------------------
- Frontend: `src/main/resources/static/js/search-info.js`
- Template: `src/main/resources/templates/demo.html` (và `searchInfo.html`)
- Backend: `src/main/java/org/report/backend/controller/authenController.java`
- Search aggregate: `GET /api/exchanges`
- Search customer: `GET /api/search-info?phone=...` (SearchInfoController)

Hỗ trợ thêm
-----------
Nếu bạn muốn tôi:
- chèn tự động `baseId/tableId` mặc định từ `SESSION_USER_CONFIGS` vào trang, hoặc
- làm refresh tự động sau khi tạo xong (thay vì chỉ prepend),
hãy nói lựa chọn — tôi sẽ cập nhật code và README tương ứng.


