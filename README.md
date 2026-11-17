# HRM

### Xác thực (T1.2)

Đặt `HRM_JWT_SECRET` bằng secret ngẫu nhiên riêng tối thiểu 32 byte trước khi chạy.
API POST dưới `/api/v1/auth`: `login` (email/password), `refresh` (token),
`logout` (Bearer), `change-password` (Bearer + oldPassword/password),
`forgot-password` (email), `reset-password` (token/password).
Notifier hiện là stub không gửi email và không log token. Access token 15 phút;
refresh xoay vòng, mỗi token hạn 7 ngày. Logout thu hồi phiên, đổi/reset mật khẩu
thu hồi mọi phiên. Các policy nằm trong `hrm.auth` ở application.yml.
Rate limit 10/phút theo IP kết nối trực tiếp, bộ nhớ của một instance; proxy và
nhiều instance cần cấu hình triển khai riêng. Swagger và health mở công khai chỉ ở dev.

Backend quản lý nhân sự, xây dựng bằng Java 21, Spring Boot và PostgreSQL 16.
Kiến trúc và các quy ước kỹ thuật được mô tả tại
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Yêu cầu

- Java 21
- Docker và Docker Compose (để chạy PostgreSQL cho profile `dev` và
  Testcontainers khi chạy integration test)

## Chạy môi trường phát triển

Tạo file cấu hình cục bộ từ mẫu. `.env` bị Git bỏ qua và không được commit.

```bash
cp .env.example .env
docker compose up -d postgres
```

Nạp biến môi trường rồi chạy ứng dụng với profile `dev`:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI có tại `http://localhost:8080/swagger-ui.html`. Lần khởi động đầu,
Flyway tạo `flyway_schema_history` và áp dụng mọi migration.

Nếu cổng `5432` đang được dùng, đặt `DB_PORT` trong `.env` thành cổng trống,
ví dụ `5433`. Dừng database nhưng giữ dữ liệu bằng `docker compose down`; xóa
dữ liệu phát triển bằng `docker compose down -v`.

## Kiểm thử

Chạy toàn bộ build và kiểm thử bằng lệnh chuẩn:

```bash
./mvnw clean verify
```

Chỉ chạy một lớp test:

```bash
./mvnw -Dtest=ClassName test
```

## Biến môi trường

Các giá trị dưới đây chỉ là mẫu phát triển cục bộ, không phải secret dùng cho
môi trường thật. Sao chép `.env.example` thành `.env` và thay bằng giá trị phù
hợp cho máy của bạn.

| Biến | Dùng cho | Giá trị mẫu |
|---|---|---|
| `DB_HOST` | Host PostgreSQL dev | `localhost` |
| `DB_PORT` | Cổng PostgreSQL dev | `5432` |
| `DB_NAME` | Tên database dev | `hrm` |
| `DB_USERNAME` | Tài khoản database dev | `hrm` |
| `DB_PASSWORD` | Mật khẩu database dev | `hrm_dev_password` |
| `DB_URL` | JDBC URL profile `prod` | `jdbc:postgresql://db.example:5432/hrm` |

Profile `prod` cần `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`; cung cấp chúng qua
secret manager hoặc biến môi trường của nền tảng triển khai, không hard-code
trong repository.
