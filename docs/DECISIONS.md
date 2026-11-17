# Decisions

T1.2: dùng Spring Security starter và OAuth2 JOSE (Nimbus) để ký/xác minh JWT HS256 bằng secret môi trường tối thiểu 32 byte; không tự cài thuật toán JWT. Thêm spring-security-test để test nền tảng có principal/CSRF hợp lệ sau khi bật security. Access token gắn phiên và kiểm tra trạng thái thu hồi trong DB. Refresh xoay vòng, mỗi token có hạn 7 ngày; logout thu hồi phiên, đổi/đặt lại mật khẩu thu hồi mọi phiên. Notifier stub không ghi token ở bất kỳ profile nào. Rate limit bộ nhớ theo IP kết nối trực tiếp, dành cho một instance (không tin X-Forwarded-For). CORS mặc định không cho origin khác; whitelist cấu hình được.

| ID | Quyết định | Lý do |
|---|---|---|
| ADR-001 | Modular monolith | Tách ranh giới nghiệp vụ, đơn giản triển khai MVP. |
| ADR-002 | REST API dưới `/api/v1` | Ổn định contract và hỗ trợ versioning. |
| ADR-003 | Khóa chính bảng nghiệp vụ dùng UUIDv7, lưu bằng kiểu `uuid` native của PostgreSQL; mã nghiệp vụ được thêm riêng khi cần. | UUIDv7 hỗ trợ sắp xếp theo thời gian và tránh phụ thuộc vào chuỗi tăng tự động giữa các hệ thống. |
| ADR-004 | Dùng Testcontainers 2.0.5 cho integration test. | Tương thích Docker API tối thiểu 1.41 của Docker Desktop 29; bản BOM mặc định dùng API cũ và không khởi chạy được PostgreSQL container. |
| ADR-005 | `audit_logs` là bảng append-only bất biến: chỉ có `created_at`, không có `updated_at` hoặc `version`; entity dùng Hibernate `@Immutable`, mọi cột không thể cập nhật và repository không công khai thao tác xóa. | Đây là ngoại lệ có chủ đích của quy tắc `db-migration` cho bảng nghiệp vụ có thể sửa. Audit log không bao giờ được cập nhật nên các cột này vô nghĩa và gây hiểu nhầm. `created_at` có `DEFAULT CURRENT_TIMESTAMP` ở DB để không phụ thuộc code ứng dụng. |
| ADR-006 | Gọi trực tiếp `AuditService.record(...)` tại service nghiệp vụ; ghi bằng transaction `REQUIRES_NEW`. | Hiện chưa có service nghiệp vụ để aspect tự động bọc, nên gọi trực tiếp là cách đơn giản và rõ ràng nhất. `REQUIRES_NEW` giữ audit kể cả khi transaction nghiệp vụ ngoài rollback; cần gọi sau khi đã có hành động đáng ghi nhận. |
| ADR-007 | Thêm `spring-security-core`, chưa bật security filter chain. | Audit cần đọc `SecurityContext` từ giai đoạn nền tảng; dependency này thuộc stack đã chốt và cung cấp API cần thiết mà không tự bảo vệ endpoint trước Phase 1. |
| ADR-008 | API là REST JSON dưới `/api/v1`, phân trang dùng `page`, `size`, `sort`; frontend tách khỏi backend. | Ổn định API contract, hỗ trợ versioning và cho phép Swagger UI dùng độc lập trong giai đoạn chưa có frontend. |
| ADR-009 | Thời điểm lưu UTC bằng `Instant`/`timestamptz`; ngày công dùng `LocalDate` theo `Asia/Ho_Chi_Minh`; business service nhận `Clock` qua injection. | Loại bỏ mơ hồ múi giờ, giữ tính nhất quán cho dữ liệu chấm công và giúp kiểm thử thời gian xác định được. |
| ADR-010 | Lỗi API dùng `ProblemDetail` RFC 9457 kèm `ErrorCode` tiếng Anh; message dành cho người dùng là tiếng Việt. | Cung cấp contract lỗi máy đọc được đồng thời nhất quán với ngôn ngữ sử dụng của hệ thống. |
| ADR-011 | GitHub Actions chạy Maven Wrapper với Java 21 trên `push` vào `feature/**` và `pull_request`. | Bảo vệ nhánh tính năng và mọi thay đổi được đề xuất bằng cùng lệnh kiểm tra chuẩn của dự án. |

## T2.1 — Hồ sơ nhân viên (quyết định được người dùng duyệt)

- **ADR-012 — Định danh nhạy cảm:** chỉ lưu HMAC-SHA-256 và 4 số cuối cho CCCD/số tài khoản. HMAC phân tách miền `cccd:` và `bank-account:`; giữ số 0 đầu, trim khoảng trắng ngoài, đầu vào phải là chữ số và đủ 4 ký tự để lấy hậu tố. Không lưu bản rõ/bản mã, không đưa hash vào DTO/audit; DTO chỉ có hậu tố. Khóa riêng `HRM_EMPLOYEE_HMAC_KEY` lấy từ môi trường, tối thiểu 32 byte, không có mặc định; thiếu/rỗng/ngắn thì ứng dụng không khởi động. Dùng khóa ngẫu nhiên có entropy cao, giữ bí mật và sao lưu an toàn; thay khóa sẽ làm mất khả năng đối chiếu hash cũ nếu không có giá trị gốc. Khóa kiểm thử được sinh ngẫu nhiên khi chạy, không phải fallback production. Nếu cần xem đầy đủ sau này, thiết kế AES-GCM và quản lý phiên bản khóa ở task riêng, không thuộc T2.1.
- **ADR-013 — Dữ liệu và mã:** Employee dùng UUIDv7 theo ADR-003, mã `NV` + 6 chữ số do service lấy từ PostgreSQL sequence `employee_code_seq` (1–999999, NO CYCLE). Chấp nhận khoảng trống do rollback, không tái sử dụng; hết sequence báo lỗi. DRAFT chỉ bắt buộc họ tên; các trường hồ sơ khác nullable, giới tính MALE/FEMALE/OTHER hoặc null. Email trim + lowercase (chuỗi rỗng thành null); unique email công ty và CCCD áp dụng cả hồ sơ đã xóa mềm. Số tài khoản có index hash để tra trùng, không unique. `user_id` nullable, unique, FK; T2.1 chưa tạo/liên kết tài khoản qua API.
- **ADR-014 — Quan hệ và khóa:** giữ ma trận cộng quyền T1.3: ADMIN/HR tất cả, MANAGER cấp dưới trực tiếp/gián tiếp, EMPLOYEE chính mình; MANAGER đơn lẻ không xem chính mình. Adapter đọc quan hệ Employee thật và loại các nút đã xóa mềm khỏi ánh xạ user/chuỗi quản lý. Mọi thao tác ghi Employee và kiểm tra xóa tổ chức dùng cùng PostgreSQL transaction advisory lock `(202602, 1)`, lấy trước khi đọc cây/khóa hàng tổ chức, với READ COMMITTED; kiểm lại toàn bộ chuỗi quản lý sau khóa. Khóa tự nhả khi commit/rollback. Đây là lựa chọn đơn giản cho MVP, đánh đổi thông lượng ghi Employee; các task ghi Employee sau này phải giữ giao thức khóa này. Không dùng khóa JVM. Các port service đặt ở module tiêu thụ, adapter ở Employee để tránh phụ thuộc vòng và truy cập entity/repository xuyên module.
- **ADR-015 — Xóa tổ chức và phạm vi:** theo xác nhận người dùng, mở rộng điều kiện “nhân viên active” của task thành `ACTIVE` **hoặc** `ON_LEAVE_LONG`, chỉ tính hồ sơ chưa xóa mềm. DRAFT/TERMINATED không chặn xóa mềm phòng ban. Position bị chặn xóa (409) khi còn bất kỳ hồ sơ tham chiếu, kể cả đã xóa mềm; FK Department/Position đều ON DELETE RESTRICT. Chỉ thêm service nền tảng tạo DRAFT/đọc/đổi quản lý để kiểm thử nghiệp vụ T2.1, không mở REST CRUD, kích hoạt, chuyển trạng thái hay xóa Employee (T2.2/T2.3/T2.6). Controller thử nghiệm chỉ nằm trong test.

## T2.4 — Hợp đồng (quyết định được người dùng duyệt)

- **ADR-016 — Vòng đời và ngày:** loại PROBATION/FIXED_TERM/INDEFINITE; chỉ tạo DRAFT, request tạo/sửa không nhận status (trường không hỗ trợ trả 400). DRAFT sửa đầy đủ, cho phép trùng hiệu lực. Chuyển trạng thái chỉ qua `POST /api/v1/contracts/{id}/activate` và `/terminate`: DRAFT → ACTIVE → TERMINATED, không quay ngược. ACTIVE chỉ sửa lương/phụ cấp hoặc rút ngắn end_date, không đổi employee/type/start_date; null là vô hạn nên có thể rút ngắn thành ngày hữu hạn. TERMINATED bất biến với mã CONTRACT_TERMINATED_IMMUTABLE. Ngày chấm dứt bắt buộc trong [start_date, end_date hiện tại], không có cận trên khi end_date null; ghi ngày và trạng thái cùng transaction. Không mở gia hạn, đính chính lịch sử hoặc API xóa.
- **ADR-017 — Hiệu lực và ràng buộc:** hai đầu ngày đều được tính; ngày kết thúc ≥ ngày bắt đầu; chỉ INDEFINITE được có end_date null (INDEFINITE vẫn được có end_date hữu hạn khi rút ngắn/chấm dứt). PostgreSQL `btree_gist` + exclusion constraint `employee_id WITH =, daterange(start_date,end_date,'[]') WITH &&`, partial `deleted_at IS NULL AND status <> 'DRAFT'`, bảo vệ cả ACTIVE và lịch sử TERMINATED. Service kiểm trước khi kích hoạt, DB bảo vệ ghi trực tiếp/đồng thời; SQLState 23P01 trả CONTRACT_OVERLAP/409. Dùng optimistic version để từ chối ghi đè thay đổi đồng thời; kiểm Employee qua port service, lấy cùng advisory lock Employee theo ADR-014 để tránh đua với thay đổi trạng thái Employee.
- **ADR-018 — Quyền và dữ liệu lương:** ADMIN/HR đọc/ghi; EMPLOYEE chỉ đọc hợp đồng của mình qua liên kết user_id. MANAGER không được đọc hợp đồng/lương cấp dưới; MANAGER + EMPLOYEE chỉ đọc của mình, cộng quyền ADMIN/HR như trước. Tiền VND theo tháng, BigDecimal/numeric(18,0), không âm và không có phần lẻ; phụ cấp là tổng một khoản. Audit CREATE/UPDATE/ACTIVATE/TERMINATE dùng AuditService hiện có (REQUIRES_NEW theo ADR-006), snapshot trước/sau che baseSalary/allowance bằng `***`; cập nhật ghi tên trường thay đổi, tuyệt đối không ghi số tiền thật. Không log DTO chứa lương; lỗi parse/validation không trả giá trị đầu vào. Truy vết số tiền thật để yêu cầu riêng.
- **ADR-019 — Employee và hết hạn:** tạo/sửa/chuyển trạng thái chỉ khi Employee chưa xóa và thuộc DRAFT/ACTIVE/ON_LEAVE_LONG; Employee bị TERMINATED sau đó không tự đổi Contract. DTO `expired` nghĩa là end_date trước ngày hiện tại tại Asia/Ho_Chi_Minh, độc lập trạng thái lưu; tính bằng Clock inject, không job. `GET /api/v1/contracts/expiring` chỉ ADMIN/HR, ACTIVE chưa xóa có end_date trong [hôm nay,hôm nay+days], gồm hai mốc, days 1–365/mặc định 30. Hai endpoint danh sách đều phân trang, size cap 200, sort whitelist không cho sắp theo lương, thêm id để ổn định.

### Chuẩn bị hạ tầng cho V9

Production phải chuẩn bị extension trước migration nếu tài khoản migration không đủ quyền. DBA chạy trên đúng database:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
```

Kiểm tra bằng chính tài khoản migration (không đưa mật khẩu vào log):

```sql
SELECT current_user, current_database(),
       has_database_privilege(current_user, current_database(), 'CREATE') AS can_create;
SELECT name, version, installed, trusted
FROM pg_available_extension_versions WHERE name = 'btree_gist';
SELECT e.extname, n.nspname FROM pg_extension e
JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'btree_gist';
```

V9 dùng `CREATE EXTENSION IF NOT EXISTS`; extension đã được DBA cài sẽ không cần tạo lại. Migration role cần quyền tạo bảng/index/FK trong schema ứng dụng và truy cập schema extension. Cấu hình production lấy DB_URL/DB_USERNAME/DB_PASSWORD từ môi trường; chưa có kết nối production trong phiên T2.4 để xác minh. DB Docker Compose dev không chạy khi kiểm tra; không coi kiểm chứng container test là kiểm chứng production. Không thêm dependency Maven.
