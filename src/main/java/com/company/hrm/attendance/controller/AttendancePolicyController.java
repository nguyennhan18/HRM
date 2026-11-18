package com.company.hrm.attendance.controller;

import com.company.hrm.attendance.dto.AttendancePolicyResponse;
import com.company.hrm.attendance.dto.CreateAttendancePolicyRequest;
import com.company.hrm.attendance.dto.UpdateAttendancePolicyRequest;
import com.company.hrm.attendance.service.AttendancePolicyService;
import com.company.hrm.common.exception.ApiProblems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance-policies")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @ApiResponse(responseCode = "403", description = "Không có quyền"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy chính sách chấm công"),
    @ApiResponse(responseCode = "409", description = "Trùng mã chính sách, đổi mã hoặc xóa chính sách mặc định")
})
public class AttendancePolicyController {

    private final AttendancePolicyService policies;

    public AttendancePolicyController(AttendancePolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách chính sách chấm công",
            description = "Mọi người dùng đã đăng nhập được đọc. Ví dụ: GET /api/v1/attendance-policies?page=0&size=20&sort=code,asc.")
    @ApiResponse(responseCode = "200", description = "Trang chính sách chấm công")
    public Page<AttendancePolicyResponse> list(@ParameterObject Pageable page) {
        return policies.list(page);
    }

    @GetMapping("/default")
    @Operation(
            summary = "Xem chính sách chấm công mặc định",
            description = "Trả về chính sách đang có defaultPolicy=true.")
    @ApiResponse(responseCode = "200", description = "Chính sách chấm công mặc định")
    public AttendancePolicyResponse getDefault() {
        return policies.getDefault();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết chính sách chấm công", description = "Ví dụ: GET /api/v1/attendance-policies/{id}")
    @ApiResponse(responseCode = "200", description = "Chi tiết chính sách chấm công")
    public AttendancePolicyResponse get(@PathVariable UUID id) {
        return policies.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Tạo chính sách chấm công",
            description = "Chỉ ADMIN/HR. Ví dụ: {\"code\":\"FLEX\",\"name\":\"Linh hoạt\",\"lateGraceMinutes\":15,"
                    + "\"earlyLeaveGraceMinutes\":0,\"roundingStepMinutes\":15,\"roundingMode\":\"NEAREST\","
                    + "\"halfDayThresholdMinutes\":240,\"minFullDayMinutes\":480,\"weekdayOvertimeRate\":1.50,"
                    + "\"weekendOvertimeRate\":2.00,\"holidayOvertimeRate\":3.00,\"defaultPolicy\":false}")
    @ApiResponse(responseCode = "201", description = "Đã tạo chính sách chấm công")
    public ResponseEntity<AttendancePolicyResponse> create(
            @Valid @RequestBody CreateAttendancePolicyRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policies.create(request, ApiProblems.traceId(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật chính sách chấm công", description = "Chỉ ADMIN/HR. Mã code bất biến.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật chính sách chấm công")
    public AttendancePolicyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAttendancePolicyRequest request,
            HttpServletRequest http) {
        return policies.update(id, request, ApiProblems.traceId(http));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Xóa chính sách chấm công", description = "Chỉ ADMIN/HR. Chặn xóa 409 nếu đang là mặc định.")
    @ApiResponse(responseCode = "204", description = "Đã xóa chính sách chấm công")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        policies.delete(id, ApiProblems.traceId(http));
        return ResponseEntity.noContent().build();
    }
}
