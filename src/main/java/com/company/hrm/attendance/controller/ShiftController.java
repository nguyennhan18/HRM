package com.company.hrm.attendance.controller;

import com.company.hrm.attendance.dto.CreateShiftRequest;
import com.company.hrm.attendance.dto.ShiftResponse;
import com.company.hrm.attendance.dto.UpdateShiftRequest;
import com.company.hrm.attendance.service.ShiftService;
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
@RequestMapping("/api/v1/shifts")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @ApiResponse(responseCode = "403", description = "Không có quyền"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy ca làm"),
    @ApiResponse(responseCode = "409", description = "Trùng mã ca, đổi mã ca hoặc vi phạm ràng buộc ca mặc định/đang phân ca")
})
public class ShiftController {

    private final ShiftService shifts;

    public ShiftController(ShiftService shifts) {
        this.shifts = shifts;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách ca làm",
            description = "Mọi người dùng đã đăng nhập được đọc. Ví dụ: GET /api/v1/shifts?page=0&size=20&sort=code,asc. "
                    + "Sort whitelist: id,code,name,startTime,endTime,overnight,defaultShift,createdAt,updatedAt; size tối đa 200.")
    @ApiResponse(responseCode = "200", description = "Trang danh sách ca làm")
    public Page<ShiftResponse> list(@ParameterObject Pageable page) {
        return shifts.list(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết ca làm", description = "Ví dụ: GET /api/v1/shifts/{id}")
    @ApiResponse(responseCode = "200", description = "Chi tiết ca làm")
    public ShiftResponse get(@PathVariable UUID id) {
        return shifts.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Tạo ca làm mới",
            description = "Chỉ ADMIN/HR. Nếu overnight=false thì endTime > startTime; nếu overnight=true thì endTime < startTime. "
                    + "Ví dụ: {\"code\":\"NIGHT\",\"name\":\"Ca đêm\",\"startTime\":\"22:00:00\",\"endTime\":\"06:00:00\","
                    + "\"breakStart\":\"02:00:00\",\"breakEnd\":\"03:00:00\",\"overnight\":true,\"defaultShift\":false}")
    @ApiResponse(responseCode = "201", description = "Đã tạo ca làm")
    public ResponseEntity<ShiftResponse> create(
            @Valid @RequestBody CreateShiftRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shifts.create(request, ApiProblems.traceId(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Cập nhật ca làm",
            description = "Chỉ ADMIN/HR. Mã code bất biến. Ca đã được phân cho nhân viên chỉ được đổi tên hoặc cờ mặc định, không đổi khung giờ.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật ca làm")
    public ShiftResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftRequest request,
            HttpServletRequest http) {
        return shifts.update(id, request, ApiProblems.traceId(http));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Xóa ca làm",
            description = "Chỉ ADMIN/HR. Chặn xóa 409 nếu là ca mặc định hoặc đang được phân cho nhân viên.")
    @ApiResponse(responseCode = "204", description = "Đã xóa ca làm")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        shifts.delete(id, ApiProblems.traceId(http));
        return ResponseEntity.noContent().build();
    }
}
