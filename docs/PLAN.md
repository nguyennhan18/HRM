# PLAN.md — Kế hoạch thực hiện hệ thống HRM

## A. Phạm vi

Trong phạm vi (MVP -> đầy đủ): xác thực & phân quyền, tổ chức, **đăng ký nhân sự (onboarding)**, hồ sơ nhân sự, hợp đồng, ca làm, **chấm công/ngày công/giờ làm**, nghỉ phép, tăng ca, bảng công tháng, bảng lương, báo cáo, audit log.
Ngoài phạm vi (làm sau, chỉ khi người dùng yêu cầu): tuyển dụng, KPI, đào tạo, tài sản, nhận diện khuôn mặt, ứng dụng mobile, đa công ty (multi-tenant).

## B. Quyết định đã chốt (ghi thêm vào DECISIONS.md ở Phase 0)

- Modular monolith, REST API `/api/v1`, JSON, phân trang `page,size,sort`.
- Khóa chính `UUID` cho bảng nghiệp vụ (hoặc `bigint` nếu người dùng chọn khác; hỏi ở T0.1).
- Mã nhân viên sinh tự động `NV` + 6 số.
- Ngôn ngữ giao diện/message lỗi: tiếng Việt; mã lỗi (`ErrorCode`) bằng tiếng Anh.
- Frontend tách riêng (ngoài phạm vi kế hoạch này). Backend phải dùng được qua Swagger UI.

## C. Mô hình dữ liệu lõi

`users`, `roles`, `permissions`, `user_roles`, `refresh_tokens`, `departments`, `positions`, `employees` (manager_id, department_id, position_id, status), `employee_documents`, `contracts`, `shifts`, `employee_shifts`, `attendance_policies`, `attendance_records` (unique employee_id + work_date), `attendance_adjustments`, `holidays`, `leave_types`, `leave_balances`, `leave_requests`, `overtime_requests`, `attendance_periods` (OPEN/LOCKED), `payroll_periods`, `payslips`, `payslip_items`, `audit_logs`.

## D. Máy trạng thái

- Employee: `DRAFT -> ACTIVE -> ON_LEAVE_LONG -> TERMINATED` (TERMINATED không được chấm công; dữ liệu giữ nguyên).
- Leave/Overtime request: `DRAFT -> PENDING_MANAGER -> PENDING_HR -> APPROVED | REJECTED | CANCELLED`.
- Attendance period: `OPEN -> LOCKED` (mở khóa chỉ ADMIN, bắt buộc lý do + audit).
- Payroll period: `DRAFT -> CALCULATED -> APPROVED -> PAID`; chỉ tính được khi attendance period tương ứng đã `LOCKED`.

## E. Ma trận quyền (rút gọn)

| Chức năng | ADMIN | HR | MANAGER | EMPLOYEE |
|---|---|---|---|---|
| Quản lý user/vai trò | x | | | |
| Tạo/sửa hồ sơ nhân sự, hợp đồng | x | x | | |
| Xem hồ sơ | tất cả | tất cả | cấp dưới | chính mình |
| Xem lương | x | x | | chính mình |
| Check-in/out | | | x | x |
| Xem/sửa công | x | x | xem cấp dưới | xem chính mình |
| Gửi đơn phép/tăng ca/giải trình | | | x | x |
| Duyệt đơn bước 1 | | | x | |
| Duyệt đơn bước 2, khóa kỳ công | x | x | | |
| Tính/duyệt lương | x | x (tính) | | |
| Xem lịch sử hồ sơ nhân viên | x | x | | |
| Xem audit log toàn hệ thống | x | | | |

## F. Các phase và task

Mỗi task có ID. Làm theo thứ tự; không nhảy phase khi phase trước chưa đạt tiêu chí hoàn thành.

### Phase 0 — Nền tảng

- T0.1 Khởi tạo Spring Boot 3 (Java 21, Maven wrapper), cấu trúc package, profile `dev/test/prod`. Hỏi người dùng: UUID hay bigint, package gốc.
- T0.2 `docker-compose.yml` (PostgreSQL 16), `.env.example`, Flyway `V1__init.sql`.
- T0.3 `common`: `BaseEntity`, `BusinessException` + `ErrorCode`, `@RestControllerAdvice` (ProblemDetail), cấu hình `Clock`, springdoc, cấu hình Jackson (ISO-8601).
- T0.4 Audit: `AuditLog` entity + `AuditService` + annotation/aspect ghi audit.
- T0.5 Viết `docs/ARCHITECTURE.md`, `DECISIONS.md`, `PROGRESS.md`; cấu hình CI chạy `./mvnw clean verify`.
- **Tiêu chí hoàn thành**: app chạy, Swagger UI mở được, 1 integration test Testcontainers xanh.

### Phase 1 — Xác thực & tổ chức

- T1.1 Entity User/Role/Permission + seed 4 vai trò và quyền theo ma trận E.
- T1.2 Login, JWT access + refresh (xoay vòng, thu hồi), logout, đổi mật khẩu, quên mật khẩu (token gửi mail, có thể stub).
- T1.3 `@PreAuthorize` + `authz` bean kiểm phạm vi dữ liệu (cấp dưới, chính mình).
- T1.4 CRUD Department (cây phòng ban), Position.
- **Tiêu chí**: test 401/403/200 cho mọi endpoint; không đăng nhập được bằng token đã thu hồi.

### Phase 2 — Đăng ký nhân sự & hồ sơ

- T2.1 Entity Employee + migration; sinh mã `NV000001`; unique cho email công ty, CCCD (hash/che).
- T2.2 **Luồng đăng ký nhân sự**: HR tạo hồ sơ `DRAFT` -> bổ sung thông tin -> kích hoạt `ACTIVE` (tự tạo User, gán vai trò EMPLOYEE, gửi mật khẩu tạm/lời mời).
- T2.3 CRUD + tìm kiếm/lọc/phân trang hồ sơ (tên, phòng ban, trạng thái, chức danh).
- T2.4 Hợp đồng lao động: loại, ngày hiệu lực, lương cơ bản, phụ cấp; không chồng lấn hiệu lực; cảnh báo sắp hết hạn.
- T2.5 Tài liệu đính kèm (upload, giới hạn loại/dung lượng file, lưu local/S3 qua interface `FileStorage`).
- T2.6 Thôi việc (`TERMINATED`), xóa mềm, khóa tài khoản; lịch sử thay đổi hồ sơ qua audit.
- T2.7 Mask dữ liệu nhạy cảm theo vai trò.
- **Tiêu chí**: HR đăng ký -> kích hoạt -> nhân viên đăng nhập được; nhân viên không đọc được hồ sơ người khác.

### Phase 3 — Ca làm, chấm công, giờ làm

- T3.1 Shift, AttendancePolicy, Holiday (CRUD, cấu hình được). Phân ca `EmployeeShift` theo ngày/tuần/mặc định.
- T3.2 Check-in/check-out qua API (nguồn: WEB; thiết kế `source` mở rộng QR/GPS), chống trùng/chống sai thứ tự.
- T3.3 `WorkTimeCalculator` (lớp thuần) theo skill `attendance-calc`, test bảng đủ 9 trường hợp bắt buộc.
- T3.4 Job `@Scheduled` tổng hợp ngày công mỗi đêm và đánh dấu `ABSENT`/`MISSING_CHECKOUT` (idempotent, chạy lại không tạo trùng).
- T3.5 Điều chỉnh công/giải trình quên check-out (`attendance_adjustments`) có duyệt và audit.
- T3.6 API xem bảng công tháng của một nhân viên/phòng ban; xuất Excel (Apache POI).
- **Tiêu chí**: bảng công tháng khớp với dữ liệu mẫu tính tay trong test; chạy job 2 lần cho cùng một ngày cho kết quả như nhau.

### Phase 4 — Nghỉ phép & tăng ca

- T4.1 LeaveType, LeaveBalance, `LeaveBalanceCalculator` (cộng dồn, thâm niên, chuyển phép).
- T4.2 Đơn nghỉ phép: gửi, hủy, duyệt 2 cấp; kiểm tra đủ phép, chống trùng/chồng lấn ngày; không tính cuối tuần/lễ.
- T4.3 Đơn tăng ca + duyệt; giới hạn giờ tăng ca/tháng theo policy.
- T4.4 Đồng bộ: đơn được duyệt -> cập nhật trạng thái ngày công (`ON_LEAVE`, giờ tăng ca).
- T4.5 Thông báo (in-app + email qua interface `Notifier`).
- **Tiêu chí**: hủy đơn đã duyệt hoàn lại phép; kỳ đã khóa không nhận thay đổi.

### Phase 5 — Chốt công & bảng lương

- T5.1 `attendance_periods`: chốt/khóa kỳ, kiểm tra điều kiện (không còn đơn PENDING, không còn `MISSING_CHECKOUT`), mở khóa có audit.
- T5.2 Cấu hình thành phần lương (khoản cộng/trừ) và tham số bảo hiểm/thuế dưới dạng cấu hình có hiệu lực theo thời gian. **Giá trị mẫu, phải xác nhận với người dùng/kế toán trước khi dùng thật.**
- T5.3 `PayrollCalculator` (lớp thuần): lương theo công, tăng ca, phụ cấp, thưởng/phạt, bảo hiểm, thuế TNCN; `BigDecimal`; test bảng.
- T5.4 Payroll period, tính lương hàng loạt, duyệt, đánh dấu `PAID`; phiếu lương bất biến sau khi duyệt.
- T5.5 Xuất phiếu lương PDF (OpenPDF) và bảng lương Excel; nhân viên chỉ tải phiếu của mình.
- **Tiêu chí**: tính lương lại cùng dữ liệu cho cùng kết quả; không tính được khi kỳ công chưa khóa.

### Phase 6 — Báo cáo & dashboard

- T6.1 Báo cáo: biến động nhân sự, tỷ lệ đi muộn/vắng, tổng giờ làm/tăng ca, chi phí lương theo phòng ban.
- T6.2 API dashboard theo vai trò (HR tổng quan, Manager nhóm, Employee cá nhân).
- T6.3 Tối ưu truy vấn/index dựa trên `EXPLAIN` với dữ liệu mẫu lớn (vài nghìn nhân viên x 12 tháng).

### Phase 7 — Hoàn thiện

- T7.1 Rà soát bảo mật (OWASP Top 10, rate limit, header, dữ liệu nhạy cảm trong log).
- T7.2 Dockerfile, docker-compose đầy đủ (app + db), profile prod, health check (`/actuator/health`).
- T7.3 Backup/restore DB, hướng dẫn vận hành.
- T7.4 README, tài liệu API, bộ dữ liệu demo cho profile dev.

## G. Yêu cầu phi chức năng

- API danh sách trả dưới 500 ms với 5.000 nhân viên (trên máy dev thông thường).
- Mọi thao tác ghi quan trọng được audit; log có `traceId`.
- Không N+1 trên các API danh sách (kiểm bằng test đếm query hoặc log SQL).

## H. Câu hỏi mở (hỏi người dùng, không tự đoán)

1. Giờ làm chuẩn, số ca, có ca đêm không? Phút ân hạn đi muộn? Quy tắc làm tròn?
2. Chính sách phép năm (12 ngày? thâm niên? chuyển phép?), các loại nghỉ khác.
3. Công thức lương: lương theo tháng hay theo giờ? Hệ số tăng ca? Mức bảo hiểm, giảm trừ, biểu thuế?
4. Một hay nhiều chi nhánh/công ty? Có cần đa ngôn ngữ?
5. Lưu file ở đâu (local, S3, MinIO)? Gửi mail qua dịch vụ nào?
