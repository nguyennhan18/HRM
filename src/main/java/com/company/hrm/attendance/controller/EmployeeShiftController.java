package com.company.hrm.attendance.controller;

import com.company.hrm.attendance.dto.CreateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.EffectiveEmployeeShiftResponse;
import com.company.hrm.attendance.dto.EmployeeShiftResponse;
import com.company.hrm.attendance.dto.UpdateEmployeeShiftRequest;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import com.company.hrm.attendance.service.EmployeeShiftService;
import com.company.hrm.common.exception.ApiProblems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employee-shifts")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @ApiResponse(responseCode = "403", description = "Không có quyền"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy phân ca, nhân viên hoặc ca làm"),
    @ApiResponse(responseCode = "409", description = "Phân ca trùng khoảng ngày (SHIFT_ASSIGNMENT_OVERLAP) hoặc nhân viên đã thôi việc")
})
public class EmployeeShiftController {

    private final EmployeeShiftService employeeShifts;

    public EmployeeShiftController(EmployeeShiftService employeeShifts) {
        this.employeeShifts = employeeShifts;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách phân ca nhân viên",
            description = "ADMIN/HR xem tất cả; MANAGER xem cấp dưới trực tiếp/gián tiếp; EMPLOYEE xem chính mình. "
                    + "Ví dụ: GET /api/v1/employee-shifts?employeeId={uuid}&assignmentType=WEEKLY&fromDate=2026-06-01&toDate=2026-06-30")
    @ApiResponse(responseCode = "200", description = "Trang danh sách phân ca")
    public Page<EmployeeShiftResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID shiftId,
            @RequestParam(required = false) ShiftAssignmentType assignmentType,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @ParameterObject Pageable page) {
        return employeeShifts.list(employeeId, shiftId, assignmentType, fromDate, toDate, page);
    }

    @GetMapping("/effective")
    @Operation(
            summary = "Tra cứu ca hiệu lực của nhân viên theo ngày",
            description = "Thứ tự ưu tiên ghi đè: DAILY > WEEKLY > DEFAULT của nhân viên > Ca mặc định toàn công ty (shifts.is_default=true). "
                    + "Ví dụ: GET /api/v1/employee-shifts/effective?employeeId={uuid}&date=2026-06-15")
    @ApiResponse(responseCode = "200", description = "Ca làm có hiệu lực trong ngày")
    public EffectiveEmployeeShiftResponse effective(
            @RequestParam UUID employeeId,
            @RequestParam LocalDate date) {
        return employeeShifts.effective(employeeId, date);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phân ca nhân viên", description = "Ví dụ: GET /api/v1/employee-shifts/{id}")
    @ApiResponse(responseCode = "200", description = "Chi tiết phân ca")
    public EmployeeShiftResponse get(@PathVariable UUID id) {
        return employeeShifts.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Phân ca cho nhân viên",
            description = "Chỉ ADMIN/HR. Chặn trùng khoảng ngày trong cùng loại phân ca (DEFAULT/WEEKLY/DAILY) bằng SHIFT_ASSIGNMENT_OVERLAP; "
                    + "cho phép WEEKLY/DAILY ghi đè lên DEFAULT. WEEKLY tối đa 7 ngày; DAILY bắt buộc endDate=startDate.")
    @ApiResponse(responseCode = "201", description = "Đã tạo phân ca")
    public ResponseEntity<EmployeeShiftResponse> create(
            @Valid @RequestBody CreateEmployeeShiftRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeShifts.create(request, ApiProblems.traceId(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật phân ca nhân viên", description = "Chỉ ADMIN/HR.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật phân ca")
    public EmployeeShiftResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeShiftRequest request,
            HttpServletRequest http) {
        return employeeShifts.update(id, request, ApiProblems.traceId(http));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Xóa phân ca nhân viên", description = "Chỉ ADMIN/HR.")
    @ApiResponse(responseCode = "204", description = "Đã xóa phân ca")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        employeeShifts.delete(id, ApiProblems.traceId(http));
        return ResponseEntity.noContent().build();
    }
}
