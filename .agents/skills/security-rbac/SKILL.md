---
name: security-rbac
description: Xác thực JWT, phân quyền theo vai trò và theo dữ liệu, bảo vệ thông tin nhạy cảm trong HRM. Dùng khi đụng tới đăng nhập, vai trò, quyền, hoặc dữ liệu lương/CCCD/ngân hàng.
---

# Bảo mật & phân quyền

## Vai trò

ADMIN, HR, MANAGER, EMPLOYEE. Quyền chi tiết dạng `resource:action` (ví dụ `employee:read`, `payroll:approve`, `attendance:lock`). Ma trận quyền nằm trong `docs/PLAN.md`.

## Quy tắc

- Mật khẩu: BCrypt (hoặc Argon2), tối thiểu 10 ký tự, khóa tạm sau nhiều lần sai.
- JWT: access token ngắn (15 phút), refresh token lưu băm (hash) trong DB, xoay vòng và thu hồi được. Secret từ biến môi trường.
- Kiểm quyền bằng `@PreAuthorize` ở service. Với dữ liệu theo sở hữu dùng biểu thức như `@authz.canViewEmployee(#id)` (MANAGER chỉ xem cấp dưới trực tiếp/gián tiếp; EMPLOYEE chỉ xem chính mình).
- Trường nhạy cảm (lương, CCCD, số tài khoản): chỉ trả khi có quyền; còn lại trả dạng che (`******1234`). Cân nhắc mã hóa cột ở mức ứng dụng.
- CORS cấu hình whitelist; bật rate limit cho `/auth/**`.
- Response lỗi đăng nhập không tiết lộ tài khoản có tồn tại hay không.
- Không log token, mật khẩu, payload chứa dữ liệu nhạy cảm.

## Test bắt buộc cho mỗi endpoint có quyền

- Không đăng nhập -> 401
- Đăng nhập sai vai trò -> 403
- Đúng vai trò nhưng sai phạm vi dữ liệu (xem người khác) -> 403/404
- Đúng quyền -> 200
