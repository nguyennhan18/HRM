# AGENTS.md — Hệ thống quản lý nhân sự (HRM)

## 1. Mục tiêu

Xây dựng hệ thống HRM đầy đủ: tổ chức, hồ sơ nhân sự, hợp đồng, chấm công, giờ làm, nghỉ phép, tăng ca, bảng lương, báo cáo.
Phạm vi và thứ tự công việc nằm trong `docs/PLAN.md`. Đó là nguồn sự thật duy nhất về phạm vi.

## 2. Stack (không tự ý đổi)

- Java 21, Spring Boot 3.x, Maven
- Spring Web, Spring Data JPA (Hibernate), Spring Security + JWT, Jakarta Validation
- PostgreSQL 16, Flyway (mọi thay đổi schema qua migration)
- MapStruct (Entity <-> DTO), Lombok (hạn chế trên Entity), springdoc-openapi
- JUnit 5, Mockito, Testcontainers (PostgreSQL), AssertJ
- Docker + docker-compose cho môi trường dev

## 3. Lệnh chuẩn

- Build + test: `./mvnw clean verify`
- Chạy app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Chạy DB dev: `docker compose up -d postgres`
- Chỉ chạy test một lớp: `./mvnw -Dtest=ClassName test`

Sau mỗi thay đổi code phải chạy `./mvnw clean verify`; chỉ báo hoàn thành khi lệnh này pass.

## 4. Cấu trúc package (modular monolith)

`com.company.hrm.<module>` với module: auth, organization, employee, contract, attendance, leave, overtime, payroll, report, common.
Mỗi module có: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`.

- Module chỉ gọi module khác qua `service` (interface), KHÔNG truy cập `repository` hoặc `entity` của module khác.
- Không có phụ thuộc vòng giữa các module.

## 5. Nguyên tắc BẮT BUỘC

1. Controller chỉ nhận/trả DTO. KHÔNG bao giờ trả Entity ra API.
2. Controller mỏng: validate, gọi service, trả kết quả. Logic nghiệp vụ nằm ở service.
3. Mọi thay đổi schema = một file Flyway mới `V{n}__{mo_ta}.sql`. KHÔNG sửa migration đã có, KHÔNG dùng `ddl-auto=update` ngoài profile test.
4. Thời gian lưu UTC (`Instant`/`timestamptz`); ngày công dùng `LocalDate` theo múi giờ `Asia/Ho_Chi_Minh`. Luôn inject `Clock`, KHÔNG gọi `now()` trực tiếp trong logic nghiệp vụ.
5. Tiền tệ dùng `BigDecimal` (scale 0 cho VND), KHÔNG dùng `double`/`float`.
6. Xóa mềm cho Employee, Contract, Department (`deleted_at`). Không xóa cứng dữ liệu nhân sự.
7. Kỳ công/kỳ lương đã khóa thì KHÔNG cho sửa dữ liệu liên quan; muốn sửa phải có thao tác mở khóa có ghi audit.
8. Mọi thao tác ghi dữ liệu nhạy cảm (lương, hợp đồng, phân quyền, chốt công) phải ghi `AuditLog` (ai, làm gì, trước/sau, khi nào).
9. Phân quyền kiểm ở tầng service/method (`@PreAuthorize`), không chỉ ở controller. Nhân viên chỉ xem được dữ liệu của chính mình trừ khi có quyền.
10. KHÔNG log hoặc trả về: mật khẩu, token, CCCD đầy đủ, số tài khoản đầy đủ, lương của người khác. Che (mask) khi hiển thị.
11. KHÔNG hard-code secret. Dùng biến môi trường; commit `.env.example`, không commit `.env`.
12. Lỗi trả về thống nhất qua `@RestControllerAdvice` theo `ProblemDetail` (RFC 9457) kèm mã lỗi nghiệp vụ.
13. Mọi công thức tính (giờ làm, công, phép, lương) nằm ở lớp thuần (pure), có unit test dạng bảng, KHÔNG nằm trong controller hoặc entity.
14. KHÔNG thêm thư viện mới khi chưa ghi lý do vào `docs/DECISIONS.md`.

## 6. Quy trình cho MỖI task (bắt buộc theo thứ tự)

1. Đọc `docs/PLAN.md`, `docs/PROGRESS.md` và các skill liên quan trong `.agents/skills/`.
2. Nêu ngắn gọn kế hoạch: file sẽ tạo/sửa, migration, test sẽ viết. Task lớn hơn ~300 dòng thay đổi thì chia nhỏ.
3. Viết test trước hoặc cùng lúc với code (ưu tiên test cho nghiệp vụ).
4. Cài đặt, chạy `./mvnw clean verify` cho đến khi xanh.
5. Tự review theo checklist Definition of Done bên dưới.
6. Cập nhật `docs/PROGRESS.md` (task ID, trạng thái, ghi chú, việc còn tồn).
7. Commit theo skill `git-workflow`.

## 7. Definition of Done

- [ ] `./mvnw clean verify` pass, không test bị bỏ qua (`@Disabled`) để lách
- [ ] Có test cho: luồng chính, ít nhất 2 trường hợp biên, 1 trường hợp lỗi/phân quyền
- [ ] Có migration (nếu đổi schema) và chạy được trên DB trống
- [ ] API có mô tả OpenAPI (summary, response code, ví dụ)
- [ ] Không vi phạm mục 5
- [ ] `docs/PROGRESS.md` đã cập nhật

## 8. Khi nào phải DỪNG và hỏi người dùng

- Yêu cầu mâu thuẫn với `docs/PLAN.md` hoặc mục 5
- Quy tắc nghiệp vụ chưa rõ (cách làm tròn, chính sách phép, công thức thuế, bảo hiểm...)
- Cần đổi stack, thêm dịch vụ ngoài, hoặc thay đổi phá vỡ API đã phát hành
- Test thất bại 3 lần liên tiếp với cùng một nguyên nhân chưa hiểu

Khi hỏi: nêu vấn đề, 2-3 phương án, và đề xuất của bạn. KHÔNG tự đoán rồi làm tiếp.

## 9. Điều agent KHÔNG được làm

- Xóa/sửa test để làm pass; tắt bảo mật để chạy cho nhanh
- Làm ngoài phạm vi task hiện tại ("tiện tay" refactor lớn, đổi tên hàng loạt)
- Báo "hoàn thành" khi chưa chạy kiểm tra thực tế
- Bịa số liệu nghiệp vụ (mức bảo hiểm, thuế suất): để cấu hình được và ghi rõ là giá trị mẫu

## 10. Danh mục skill

| Skill | Dùng khi |
|---|---|
| spring-module | Tạo/sửa bất kỳ module nghiệp vụ nào |
| db-migration | Thêm/sửa bảng, cột, index, seed data |
| security-rbac | Đăng nhập, JWT, vai trò, quyền, dữ liệu nhạy cảm |
| attendance-calc | Chấm công, ca làm, giờ làm, tăng ca, phép |
| api-testing | Viết test, Testcontainers, test phân quyền |
| git-workflow | Commit, nhánh, PR |
