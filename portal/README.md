# TSB Operations Portal

Portal HTML/CSS/JS thuần để quản lý local config và entitlement của `common`.

## Chạy local

```bash
cd portal
python3 -m http.server 8090
```

Mở http://localhost:8090. Mặc định portal gọi qua BFF tại `http://localhost:8086/bff/api/common` với admin key `local-admin-key`.

Các request/response được hiển thị ở tab **API Log**. Portal không lưu admin key lên server; giá trị chỉ nằm trong form của trình duyệt.
