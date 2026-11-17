---
name: spring-module
description: Quy trình tạo hoặc sửa một module nghiệp vụ Spring Boot (entity, repository, service, controller, DTO, mapper) đúng kiến trúc của dự án HRM. Dùng cho mọi tác vụ thêm tính năng backend.
---

# Tạo module nghiệp vụ

## Thứ tự thực hiện

1. Migration (xem skill `db-migration`).
2. `entity`: `@Entity`, khóa chính `Long` hoặc `UUID` (theo `docs/DECISIONS.md`), kế thừa `BaseEntity` (id, createdAt, updatedAt, createdBy, updatedBy, version cho optimistic lock). Quan hệ `LAZY` mặc định.
3. `repository`: `JpaRepository`; truy vấn phức tạp dùng `@Query` hoặc Specification. Tránh N+1 (dùng `@EntityGraph` hoặc fetch join có chủ đích).
4. `dto`: dùng Java `record`. Tách `CreateXRequest`, `UpdateXRequest`, `XResponse`. Gắn Jakarta Validation lên request.
5. `mapper`: interface MapStruct, `componentModel = "spring"`.
6. `service`: interface + impl; `@Transactional` ở impl (`readOnly = true` cho truy vấn). Ném `BusinessException(ErrorCode, ...)` khi vi phạm nghiệp vụ.
7. `controller`: `@RestController`, đường dẫn `/api/v1/<module>`, `@PreAuthorize`, trả `ResponseEntity`/DTO, phân trang bằng `Pageable`.
8. Test (xem skill `api-testing`).

## Quy ước

- Tên bảng `snake_case` số nhiều (`employees`), cột `snake_case`.
- Mã nghiệp vụ (mã nhân viên `NV000123`) sinh ở service, có unique index.
- Không dùng `@Data` trên Entity; dùng `@Getter`/`@Setter` chọn lọc, tự cài `equals/hashCode` theo id.
- Danh sách trả về luôn có phân trang và sắp xếp có whitelist trường cho phép.
- Không để logic trong mapper.

## Checklist trước khi xong

- [ ] Không trả Entity ra ngoài
- [ ] Không gọi repository của module khác
- [ ] Có kiểm tra quyền và test quyền
- [ ] Có ghi audit nếu là dữ liệu nhạy cảm
