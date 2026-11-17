---
name: api-testing
description: Cách viết unit test, integration test với Testcontainers và test phân quyền cho API Spring Boot trong dự án HRM. Dùng mỗi khi viết hoặc sửa test.
---

# Kiểm thử

## Phân tầng

- Unit test (nhanh, không Spring): calculator, mapper, validator, service với Mockito.
- Slice test: `@WebMvcTest` cho controller + validation + định dạng lỗi; `@DataJpaTest` cho query phức tạp.
- Integration test: `@SpringBootTest` + Testcontainers PostgreSQL, áp toàn bộ Flyway migration. KHÔNG dùng H2 thay Postgres.

## Quy ước

- Tên test: `should_<hanh_vi>_when_<dieu_kien>`.
- Dùng builder/fixture trong `src/test/.../testsupport`, không copy-paste dữ liệu.
- Thời gian: inject `Clock.fixed(...)`, không `Thread.sleep`.
- Mỗi endpoint: test thành công, test validation (400), test phân quyền (401/403), test không tìm thấy (404).
- Test nghiệp vụ tính toán: dạng bảng nhiều dòng (`@CsvSource`/`@MethodSource`).
- KHÔNG dùng `@Disabled` hoặc nới lỏng assert để test pass. Nếu test sai thì sửa và nêu lý do trong commit.
- Mục tiêu độ phủ dòng cho package `service` và `calculator` >= 80%.
