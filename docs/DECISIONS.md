# Decisions

| ID | Quyết định | Lý do |
|---|---|---|
| ADR-001 | Modular monolith | Tách ranh giới nghiệp vụ, đơn giản triển khai MVP. |
| ADR-002 | REST API dưới `/api/v1` | Ổn định contract và hỗ trợ versioning. |
| ADR-003 | Khóa chính bảng nghiệp vụ dùng UUIDv7, lưu bằng kiểu `uuid` native của PostgreSQL; mã nghiệp vụ được thêm riêng khi cần. | UUIDv7 hỗ trợ sắp xếp theo thời gian và tránh phụ thuộc vào chuỗi tăng tự động giữa các hệ thống. |
| ADR-004 | Dùng Testcontainers 2.0.5 cho integration test. | Tương thích Docker API tối thiểu 1.41 của Docker Desktop 29; bản BOM mặc định dùng API cũ và không khởi chạy được PostgreSQL container. |
| ADR-005 | `audit_logs` là bảng append-only bất biến: chỉ có `created_at`, không có `updated_at` hoặc `version`; entity dùng Hibernate `@Immutable`, mọi cột không thể cập nhật và repository không công khai thao tác xóa. | Đây là ngoại lệ có chủ đích của quy tắc `db-migration` cho bảng nghiệp vụ có thể sửa. Audit log không bao giờ được cập nhật nên các cột này vô nghĩa và gây hiểu nhầm. `created_at` có `DEFAULT CURRENT_TIMESTAMP` ở DB để không phụ thuộc code ứng dụng. |
| ADR-006 | Gọi trực tiếp `AuditService.record(...)` tại service nghiệp vụ; ghi bằng transaction `REQUIRES_NEW`. | Hiện chưa có service nghiệp vụ để aspect tự động bọc, nên gọi trực tiếp là cách đơn giản và rõ ràng nhất. `REQUIRES_NEW` giữ audit kể cả khi transaction nghiệp vụ ngoài rollback; cần gọi sau khi đã có hành động đáng ghi nhận. |
| ADR-007 | Thêm `spring-security-core`, chưa bật security filter chain. | Audit cần đọc `SecurityContext` từ giai đoạn nền tảng; dependency này thuộc stack đã chốt và cung cấp API cần thiết mà không tự bảo vệ endpoint trước Phase 1. |
