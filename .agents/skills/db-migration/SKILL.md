---
name: db-migration
description: Quy tắc viết migration Flyway cho PostgreSQL trong dự án HRM. Dùng khi thêm hoặc sửa bảng, cột, ràng buộc, index, dữ liệu mẫu.
---

# Flyway migration

## Quy tắc

- File: `src/main/resources/db/migration/V{số}__{mo_ta_ngan}.sql`. Số tăng dần, KHÔNG sửa file đã merge; muốn đổi thì tạo migration mới.
- Mỗi migration làm một việc. Có thể chạy trên DB trống và trên DB đã có dữ liệu.
- Luôn đặt tên ràng buộc rõ ràng: `pk_`, `fk_`, `uq_`, `ck_`, `idx_`.
- Cột thời gian: `timestamptz`. Cột ngày: `date`. Tiền: `numeric(18,0)` (VND) hoặc `numeric(18,2)`.
- Bắt buộc có: `created_at`, `updated_at`, `version` (bigint) cho bảng nghiệp vụ; `deleted_at` cho bảng xóa mềm.
- Ràng buộc tính toàn vẹn đặt ở DB (unique, check, not null), không chỉ ở code. Ví dụ: `unique (employee_id, work_date)` cho bản ghi công theo ngày; `check (end_date >= start_date)`.
- Thêm cột NOT NULL vào bảng có dữ liệu: thêm nullable -> backfill -> đặt NOT NULL (có thể tách 2-3 migration).
- Seed data tham chiếu (vai trò, loại phép, ca mặc định) nằm ở migration riêng `V{n}__seed_*.sql`. Dữ liệu demo chỉ nằm ở profile `dev` (`R__demo_data.sql` hoặc Java seeder), KHÔNG chạy ở production.

## Kiểm tra

- Chạy `./mvnw clean verify` (Testcontainers sẽ áp toàn bộ migration lên DB trống).
- `spring.jpa.hibernate.ddl-auto=validate` ở mọi profile ngoài test nhanh.
