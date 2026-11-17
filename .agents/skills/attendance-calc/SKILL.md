---
name: attendance-calc
description: Quy tắc nghiệp vụ và cách cài đặt chấm công, tính giờ làm, đi muộn/về sớm, tăng ca, ngày công và số dư phép trong HRM. Dùng khi đụng tới module attendance, leave, overtime.
---

# Chấm công và tính giờ làm

## Nguyên tắc cài đặt

- Toàn bộ phép tính nằm ở lớp thuần, ví dụ `WorkTimeCalculator`, `LeaveBalanceCalculator`; không phụ thuộc Spring/DB; nhận `Clock` và dữ liệu đầu vào, trả kết quả. Dễ test bằng bảng dữ liệu (`@ParameterizedTest`).
- Lưu `check_in`/`check_out` dạng `timestamptz` (UTC). Ngày công (`work_date`) xác định theo ca và múi giờ `Asia/Ho_Chi_Minh`.
- Mọi tham số chính sách là cấu hình trong DB (bảng `shift`, `attendance_policy`), KHÔNG hard-code: giờ vào/ra, nghỉ trưa, phút ân hạn đi muộn, bước làm tròn, ngưỡng nửa công, hệ số tăng ca.

## Các trường hợp bắt buộc xử lý và có test

1. Ca ngày bình thường, đúng giờ.
2. Đi muộn trong/ngoài thời gian ân hạn; về sớm.
3. Ca đêm qua nửa đêm (ngày công thuộc ngày bắt đầu ca).
4. Quên check-out: trạng thái `MISSING_CHECKOUT`, không tự suy diễn giờ; cho phép gửi đơn giải trình, HR/Manager duyệt mới tính.
5. Nhiều lần check-in/out trong ngày: lấy tổng các khoảng hợp lệ hoặc lần đầu/lần cuối theo policy.
6. Làm việc vào ngày lễ/cuối tuần: tính theo hệ số tăng ca cấu hình.
7. Ngày nghỉ phép được duyệt (cả ngày/nửa ngày) kết hợp với giờ làm thực tế.
8. Check-in trùng, check-in tương lai, check-out trước check-in: từ chối bằng lỗi nghiệp vụ rõ ràng.
9. Kỳ công đã khóa: chặn sửa, chỉ cho điều chỉnh qua phiếu điều chỉnh có audit.

## Trạng thái ngày công

`PRESENT`, `HALF_DAY`, `LATE`, `EARLY_LEAVE`, `ON_LEAVE`, `HOLIDAY`, `ABSENT`, `MISSING_CHECKOUT`.

## Số dư phép

- Cộng dồn theo tháng hoặc theo năm (cấu hình), cộng thêm theo thâm niên, quy tắc chuyển phép sang năm sau có hạn.
- Trừ phép khi đơn được duyệt cuối; hoàn phép khi đơn bị hủy sau duyệt.
- Khi chưa rõ chính sách, DỪNG và hỏi người dùng (xem AGENTS.md mục 8).
