Hướng dẫn sử dụng chức năng Tra cứu khách hàng (/search-info)
===========================================================

Mục tiêu
--------
Tài liệu này hướng dẫn người dùng thao tác với trang Tra cứu khách hàng để:
- Tìm thông tin khách hàng bằng số điện thoại
- Xem lịch sử đơn hàng
- Xem và thêm "Trao đổi" (ghi chú / nhật ký chăm sóc khách hàng)
image.png
Truy cập trang
--------------
- Mở trình duyệt và vào đường dẫn: `https://<your-host>/search-info` hoặc trang demo nếu có (`/demo`).
- Ở thanh tìm kiếm (trên cùng), nhập SĐT của khách rồi nhấn Enter hoặc bấm nút "Tra cứu".

Khi nào dùng chức năng này
-------------------------
- Khi bạn cần xem nhanh:
  - Thông tin cơ bản của khách (tên, số điện thoại, địa chỉ, customer id)
  - Lịch sử mua hàng gần nhất
  - Các trao đổi / note CSKH đã ghi trước đó
- Khi bạn cần thêm một ghi chú/trao đổi mới về khách (ví dụ: cuộc gọi, trạng thái đơn, yêu cầu khách).

Giao diện chính
---------------
- Ô tìm kiếm (header): nhập SĐT hoặc tên, nhấn Tra cứu.
- Bên trái: thẻ khách hàng (avatar, tên, SĐT, trạng thái).
- Giữa: lịch sử mua hàng (các đơn).
- Bên phải: "Nhật ký chăm sóc" và "Trao đổi" — nơi chứa các ghi chú/trao đổi.
- Nút "Thêm mới" ở góc khung "Trao đổi" để mở modal nhập nội dung trao đổi.

Thêm một trao đổi mới — các bước
-------------------------------
1. Bấm nút "Thêm mới" (ở khung Trao đổi).
2. Trong modal, nhập nội dung trao đổi vào ô "Nội dung trao đổi".
   - Chỉ nhập phần nội dung (ví dụ "Khách mua 2 sp, hẹn lấy vào T2").
3. Bấm "Lưu".

Hệ thống sẽ:
- Tự thêm tiền tố "PK: " vào đầu nội dung trước khi lưu (ví dụ: bạn nhập "Khách mua" → lưu thành "PK: Khách mua").
- Lưu trao đổi vào hệ thống (nếu có cấu hình bảng liên kết với khách, trao đổi được lưu vĩnh viễn trên hệ thống Lark; nếu không có, trao đổi vẫn được hiển thị tạm trên giao diện).
- Hiển thị mục trao đổi vừa thêm ngay lập tức ở đầu danh sách trao đổi.

Ghi chú quan trọng cho người dùng
--------------------------------
- Không cần nhập ngày — hệ thống tự dùng thời gian hiện tại khi lưu.
- Nếu bạn không thấy trao đổi mới khi tìm lại SĐT:
  - Chờ vài giây rồi tìm lại (đôi khi cần thời gian để đồng bộ).
  - Nếu vẫn không thấy, báo bộ phận kỹ thuật kèm mô tả: SĐT dùng để tìm, nội dung bạn đã nhập và thời gian thao tác.

Cách đọc mục "Trao đổi"
-----------------------
- Mỗi mục hiển thị:
  - Nội dung trao đổi (bắt đầu bằng "PK: ")
  - Người hoặc nguồn (nếu có)
  - Thời gian (timestamp dạng hiển thị)
- Mục mới nhất luôn ở đầu danh sách.

Một vài tình huống hay gặp
-------------------------
- Muốn lưu ghi chú gấp nhưng không thấy “Thêm mới”: đảm bảo bạn đã đăng nhập và đang ở trang Tra cứu.
- Muốn ghi nhận trao đổi cho 1 khách cụ thể: luôn tìm đúng SĐT trước, rồi bấm Thêm mới (hệ thống sẽ cố gắng liên kết trao đổi với record khách nếu có).
- Muốn xem trao đổi cũ của nhiều CSKH: hệ thống có thể lấy trao đổi từ nhiều bảng; bạn chỉ cần tìm SĐT để xem tổng hợp.

Mẹo sử dụng nhanh
-----------------
- Dùng Enter để tìm nhanh sau khi gõ SĐT.
- Gõ chính xác số điện thoại (bao gồm mã vùng nếu cần) để nhận kết quả tốt nhất.
- Khi ghi chú quan trọng, bắt đầu nội dung bằng cụm từ ngắn gọn để dễ đọc (ví dụ: "Khách xác nhận", "CSKH gọi lại").

Nếu cần trợ giúp
----------------
- Gặp lỗi hoặc cần thay đổi cách lưu/hiển thị trao đổi, liên hệ đội kỹ thuật với:
  - Mô tả lỗi / hành vi (ví dụ: không lưu, lỗi tạo bản ghi)
  - SĐT thử nghiệm và thời gian bạn thao tác

Kết thúc
--------
Trang Tra cứu được thiết kế để thao tác nhanh, nắm bắt thông tin khách và ghi chép trao đổi ngay khi tương tác với khách. Nếu bạn muốn bổ sung tính năng (ví dụ: gắn tag, chọn loại trao đổi), nói rõ yêu cầu để nhóm phát triển mở rộng.


