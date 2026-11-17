# Decisions

| ID | Quyết định | Lý do |
|---|---|---|
| ADR-001 | Modular monolith | Tách ranh giới nghiệp vụ, đơn giản triển khai MVP. |
| ADR-002 | REST API dưới `/api/v1` | Ổn định contract và hỗ trợ versioning. |
| ADR-003 | Khóa chính bảng nghiệp vụ dùng UUIDv7, lưu bằng kiểu `uuid` native của PostgreSQL; mã nghiệp vụ được thêm riêng khi cần. | UUIDv7 hỗ trợ sắp xếp theo thời gian và tránh phụ thuộc vào chuỗi tăng tự động giữa các hệ thống. |
