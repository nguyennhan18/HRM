# HRM

## Môi trường phát triển

Yêu cầu Docker, Docker Compose và Java 21.

Tạo file cấu hình cục bộ từ mẫu:

```bash
cp .env.example .env
```

Khởi động PostgreSQL 16:

```bash
docker compose up -d postgres
```

Chạy ứng dụng với profile `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Khi khởi động lần đầu, Flyway tạo bảng `flyway_schema_history` và áp dụng migration baseline `V1__init.sql`. Dừng container nhưng giữ dữ liệu cục bộ bằng:

```bash
docker compose down
```

Để xoá cả dữ liệu PostgreSQL cục bộ, dùng `docker compose down -v`.
