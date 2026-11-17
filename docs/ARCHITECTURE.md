# Kiến trúc HRM

## Tổng quan

HRM là modular monolith Spring Boot, cung cấp REST API JSON dưới tiền tố
`/api/v1`. Mỗi module nghiệp vụ độc lập theo ranh giới package; frontend là hệ
thống tách riêng và không thuộc repository này.

```text
Client / Swagger UI
        |
        v
Controller (DTO, validation)
        |
        v
Service interface + implementation ----> module khác qua service interface
        |                                           |
        v                                           v
Repository (JPA) ------------------------------> PostgreSQL 16
        |
        +--> AuditService (thao tác nhạy cảm, transaction riêng)
```

## Module và phụ thuộc

```text
com.company.hrm
├── auth          Xác thực, người dùng, vai trò và quyền
├── organization  Phòng ban và chức danh
├── employee      Hồ sơ và đăng ký nhân sự
├── contract      Hợp đồng lao động
├── attendance    Ca làm, check-in/out, ngày công
├── leave         Nghỉ phép
├── overtime      Tăng ca
├── payroll       Kỳ lương, phiếu lương, tính lương
├── report        Báo cáo và dashboard
└── common        Thành phần dùng chung: lỗi, audit, thời gian, cấu hình
```

Mỗi module gồm `controller`, `service`, `repository`, `entity`, `dto` và
`mapper`. Một module chỉ được dùng module khác qua `service` interface; không
được truy cập trực tiếp `repository` hoặc `entity` của module khác. Không tạo
phụ thuộc vòng.

## Luồng request

1. Client gọi API `/api/v1/...` với JSON.
2. Controller nhận/trả DTO, thực hiện validation Jakarta Validation và ủy quyền
   cho service; controller không chứa logic nghiệp vụ và không trả JPA entity.
3. Service kiểm tra quyền ở method (`@PreAuthorize`), áp dụng quy tắc nghiệp
   vụ và gọi repository của chính module.
4. Repository đọc/ghi PostgreSQL qua JPA. Thay đổi schema chỉ đi qua Flyway
   migration `V{n}__{mo_ta}.sql`.
5. Các thao tác ghi nhạy cảm gọi `AuditService`, tạo bản ghi append-only với
   actor, hành động, dữ liệu trước/sau đã che và thời điểm.
6. Lỗi nghiệp vụ đi qua `@RestControllerAdvice`, trả `ProblemDetail` RFC 9457
   bằng tiếng Việt cùng mã lỗi tiếng Anh.

## Quy ước đặt tên

- Package module: chữ thường, số ít khi là khái niệm (`employee`, `payroll`).
- Class Java: PascalCase; interface service có hậu tố `Service`, implementation
  có hậu tố `ServiceImpl`; DTO dùng hậu tố `Request`, `Response` hoặc `Dto`.
- Endpoint: danh từ số nhiều, kebab-case khi cần, dưới `/api/v1`; dùng HTTP
  method theo REST.
- Database: `snake_case`; bảng ở số nhiều; khóa ngoại có hậu tố `_id`; thời
  điểm audit dùng `_at`.
- Flyway: không sửa migration đã áp dụng; thêm migration tăng dần theo mẫu
  `V{n}__{mo_ta}.sql`.
- Mã nhân viên được sinh tự động theo mẫu `NV` + 6 chữ số.

## Lỗi, audit và dữ liệu nhạy cảm

- `BusinessException` và `ErrorCode` là contract lỗi nghiệp vụ. Global handler
  chuyển chúng thành `ProblemDetail` với HTTP status phù hợp.
- Không trả hoặc ghi log mật khẩu, token, CCCD đầy đủ, số tài khoản đầy đủ hay
  lương của người khác. Dữ liệu nhạy cảm phải được mask trước khi hiển thị hay
  ghi audit.
- `audit_logs` là append-only. `AuditService.record(...)` chạy trong transaction
  `REQUIRES_NEW`, do đó audit vẫn tồn tại nếu transaction nghiệp vụ ngoài bị
  rollback.
- Các thay đổi lương, hợp đồng, phân quyền và chốt/mở khóa công bắt buộc audit.

## Thời gian và tiền tệ

- Timestamp lưu ở UTC bằng `Instant` và cột PostgreSQL `timestamptz`.
- Ngày công dùng `LocalDate` theo múi giờ `Asia/Ho_Chi_Minh`.
- Logic nghiệp vụ nhận `Clock` qua injection; không gọi `now()` trực tiếp.
- Giá trị VND dùng `BigDecimal`, scale 0; không dùng `double` hoặc `float`.

## Chất lượng và vận hành

- Unit test dùng JUnit 5, Mockito và AssertJ; integration test dùng
  Testcontainers PostgreSQL.
- CI GitHub Actions chạy `./mvnw clean verify` trên Java 21 cho mọi pull request
  và mọi push vào nhánh `feature/**`.
- Profile `dev` dùng PostgreSQL cục bộ và Flyway; profile `test` do
  Testcontainers cấp database; profile `prod` lấy chuỗi kết nối từ biến môi
  trường.
