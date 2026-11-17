# Tiến độ

| Task | Trạng thái | Ghi chú |
|---|---|---|
| T0.1 | Hoàn thành | Đã khởi tạo Maven/Spring Boot, package và profile; chốt UUIDv7 native PostgreSQL tại ADR-003. `./mvnw clean verify` xanh với Java 21.0.13: 1 test pass (`HrmApplicationContextTest`), 0 fail. Context test hiện chạy không có `DataSource`; Docker/Testcontainers sẽ cần cho các task có migration. |
| T0.2 | Bị chặn | Đã thêm Docker Compose PostgreSQL 16 (volume, healthcheck), `.env.example`, cấu hình profile `dev` và baseline Flyway `V1__init.sql`; README đã có hướng dẫn chạy. `./mvnw clean verify` xanh: 1 test pass, 0 fail. Chưa xác nhận được Docker/app profile `dev` và log Flyway vì môi trường hiện tại không có lệnh `docker`. |
