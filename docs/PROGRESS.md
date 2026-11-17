# Tiến độ

| Task | Trạng thái | Ghi chú |
|---|---|---|
| T0.1 | Hoàn thành | Đã khởi tạo Maven/Spring Boot, package và profile; chốt UUIDv7 native PostgreSQL tại ADR-003. `./mvnw clean verify` xanh với Java 21.0.13: 1 test pass (`HrmApplicationContextTest`), 0 fail. Context test hiện chạy không có `DataSource`; Docker/Testcontainers sẽ cần cho các task có migration. |
| T0.2 | Hoàn thành | Đã thêm Docker Compose PostgreSQL 16 (volume, healthcheck), `.env.example`, cấu hình profile `dev` và baseline Flyway `V1__init.sql`; README đã có hướng dẫn chạy. Docker 29.9.3 / Compose v5.7.0 khởi động Postgres 16 healthy trên cổng máy 5433 (5432 bị `postgresql@16` của Homebrew chiếm). Profile `dev` đã tạo `flyway_schema_history`, áp dụng V1 và khởi động sạch trong 2.124 giây. `./mvnw clean verify` xanh: 1 test pass, 0 fail, 0 skipped. Chưa có entity nên `ddl-auto=validate` chưa kiểm tra schema thực chất. |
