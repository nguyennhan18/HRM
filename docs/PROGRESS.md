# Tiến độ

| Task | Trạng thái | Ghi chú |
|---|---|---|
| T0.1 | Hoàn thành | Đã khởi tạo Maven/Spring Boot, package và profile; chốt UUIDv7 native PostgreSQL tại ADR-003. `./mvnw clean verify` xanh với Java 21.0.13: 1 test pass (`HrmApplicationContextTest`), 0 fail. Context test hiện chạy không có `DataSource`; Docker/Testcontainers sẽ cần cho các task có migration. |
