# Webhook API cho POS

Package này cung cấp API để nhận webhook từ hệ thống POS (Point of Sale).

## Cấu trúc Package

```
webhook/
├── Controller/
│   └── WebhookController.java    # Controller xử lý webhook requests
├── DTO/
│   ├── WebhookPayload.java       # DTO cho dữ liệu webhook nhận được
│   └── WebhookResponse.java      # DTO cho response trả về
└── Service/
    └── WebhookService.java       # Service xử lý logic webhook
```

## API Endpoints

### 1. POST /api/webhook/pos
Nhận webhook từ POS với format JSON.

**Request Body:**
```json
{
  "event_type": "order.created|order.updated|order.cancelled|customer.created|customer.updated|note.created|note.updated",
  "shop_id": 1546758,
  "timestamp": 1672531200000,
  "order_id": "123456",
  "customer_id": "abc123",
  "data": {
    // Dữ liệu bổ sung tùy theo event type
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Webhook processed successfully",
  "eventType": "order.created",
  "timestamp": 1672531200000
}
```

### 2. POST /api/webhook/pos-form
Nhận webhook từ POS với format form-data (nếu POS gửi theo format này).

**Parameters:**
- `event_type`: Loại sự kiện
- `shop_id`: ID cửa hàng
- `data`: Dữ liệu bổ sung (optional)

### 3. GET /api/webhook/test
Endpoint test để kiểm tra webhook API có hoạt động không.

**Query Parameters:**
- `eventType`: Loại sự kiện (optional)
- `shopId`: ID cửa hàng (optional)

## Các Loại Event Hỗ Trợ

1. **order.created** - Đơn hàng mới được tạo
2. **order.updated** - Đơn hàng được cập nhật
3. **order.cancelled** - Đơn hàng bị hủy
4. **customer.created** - Khách hàng mới được tạo
5. **customer.updated** - Thông tin khách hàng được cập nhật
6. **note.created** - Ghi chú mới được tạo
7. **note.updated** - Ghi chú được cập nhật

## Cách Sử Dung

### 1. Cấu hình Webhook trong POS
Trong hệ thống POS, cấu hình webhook URL:
```
http://your-server:8386/api/webhook/pos
```

### 2. Xử lý Logic Webhook
Trong `WebhookService.java`, implement logic xử lý cho từng loại event trong các method:
- `handleOrderCreated()`
- `handleOrderUpdated()`
- `handleOrderCancelled()`
- `handleCustomerCreated()`
- `handleCustomerUpdated()`
- `handleNoteCreated()`
- `handleNoteUpdated()`

### 3. Testing
Sử dụng endpoint `/api/webhook/test` để kiểm tra API hoạt động.

## Logging
Tất cả webhook requests được log với thông tin chi tiết để debug và monitoring.

## Security
Hiện tại API chưa có authentication. Nên thêm authentication mechanism trong production (API key, JWT token, etc.).
