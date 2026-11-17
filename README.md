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
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Nếu cổng `5432` đã được một PostgreSQL cục bộ sử dụng (ví dụ `postgresql@16` của Homebrew), hãy đổi `DB_PORT` trong `.env` sang một cổng trống, chẳng hạn `5433`. Giữ `DB_PORT=5432` trong `.env.example` làm giá trị mặc định cho môi trường khác.

Khi khởi động lần đầu, Flyway tạo bảng `flyway_schema_history` và áp dụng migration baseline `V1__init.sql`. Dừng container nhưng giữ dữ liệu cục bộ bằng:

```bash
docker compose down
```

Để xoá cả dữ liệu PostgreSQL cục bộ, dùng `docker compose down -v`.
