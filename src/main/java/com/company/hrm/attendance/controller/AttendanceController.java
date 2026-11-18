package com.company.hrm.attendance.controller;

import com.company.hrm.attendance.dto.AttendanceRecordResponse;
import com.company.hrm.attendance.dto.CheckInRequest;
import com.company.hrm.attendance.dto.CheckOutRequest;
import com.company.hrm.attendance.service.AttendanceService;
import com.company.hrm.common.exception.ApiProblems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @ApiResponse(responseCode = "403", description = "Không có quyền chấm công"),
    @ApiResponse(responseCode = "404", description = "Chưa có ca làm hiệu lực"),
    @ApiResponse(responseCode = "409", description = "Nhân viên không ở trạng thái ACTIVE, check-in trùng hoặc check-out không hợp lệ"),
    @ApiResponse(responseCode = "422", description = "Thời điểm chấm công không hợp lệ")
})
public class AttendanceController {

    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Operation(
            summary = "Check-in cho chính người đăng nhập",
            description = "Chỉ MANAGER hoặc EMPLOYEE có hồ sơ ở trạng thái ACTIVE. "
                    + "Thời điểm check-in luôn lấy từ Clock phía server (UTC) và gán ngày công theo Asia/Ho_Chi_Minh (hỗ trợ ca đêm qua nửa đêm). "
                    + "Body có thể bỏ trống (mặc định source=WEB). Ví dụ: {\"source\":\"WEB\"}.")
    @ApiResponse(responseCode = "200", description = "Check-in thành công")
    public AttendanceRecordResponse checkIn(
            @Valid @RequestBody(required = false) CheckInRequest request,
            HttpServletRequest http) {
        return attendance.checkIn(request, ApiProblems.traceId(http));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Operation(
            summary = "Check-out cho chính người đăng nhập",
            description = "Chỉ MANAGER hoặc EMPLOYEE có hồ sơ ở trạng thái ACTIVE đang có phiên mở hợp lệ. "
                    + "Thời điểm check-out luôn lấy từ Clock phía server (UTC). "
                    + "Body có thể bỏ trống (mặc định source=WEB). Ví dụ: {\"source\":\"WEB\"}.")
    @ApiResponse(responseCode = "200", description = "Check-out thành công")
    public AttendanceRecordResponse checkOut(
            @Valid @RequestBody(required = false) CheckOutRequest request,
            HttpServletRequest http) {
        return attendance.checkOut(request, ApiProblems.traceId(http));
    }
}
