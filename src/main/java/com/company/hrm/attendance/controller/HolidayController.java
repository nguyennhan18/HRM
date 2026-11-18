package com.company.hrm.attendance.controller;

import com.company.hrm.attendance.dto.CreateHolidayRequest;
import com.company.hrm.attendance.dto.HolidayResponse;
import com.company.hrm.attendance.dto.UpdateHolidayRequest;
import com.company.hrm.attendance.service.HolidayService;
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
@RequestMapping("/api/v1/holidays")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @ApiResponse(responseCode = "403", description = "Không có quyền"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy ngày lễ"),
    @ApiResponse(responseCode = "409", description = "Trùng ngày lễ")
})
public class HolidayController {

    private final HolidayService holidays;

    public HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    @GetMapping
    @Operation(
            summary = "Danh sách ngày lễ",
            description = "Mọi người dùng đã đăng nhập được đọc. Có thể lọc theo fromDate, toDate. "
                    + "Ví dụ: GET /api/v1/holidays?fromDate=2026-01-01&toDate=2026-12-31&page=0&size=20&sort=holidayDate,asc.")
    @ApiResponse(responseCode = "200", description = "Trang danh sách ngày lễ")
    public Page<HolidayResponse> list(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @ParameterObject Pageable page) {
        return holidays.list(fromDate, toDate, page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết ngày lễ", description = "Ví dụ: GET /api/v1/holidays/{id}")
    @ApiResponse(responseCode = "200", description = "Chi tiết ngày lễ")
    public HolidayResponse get(@PathVariable UUID id) {
        return holidays.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(
            summary = "Thêm ngày lễ",
            description = "Chỉ ADMIN/HR. Ví dụ: {\"holidayDate\":\"2026-09-02\",\"name\":\"Quốc khánh 2/9\",\"multiplier\":3.00}")
    @ApiResponse(responseCode = "201", description = "Đã tạo ngày lễ")
    public ResponseEntity<HolidayResponse> create(
            @Valid @RequestBody CreateHolidayRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidays.create(request, ApiProblems.traceId(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật ngày lễ", description = "Chỉ ADMIN/HR. Chặn 409 nếu trùng ngày lễ khác.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật ngày lễ")
    public HolidayResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHolidayRequest request,
            HttpServletRequest http) {
        return holidays.update(id, request, ApiProblems.traceId(http));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Xóa ngày lễ", description = "Chỉ ADMIN/HR.")
    @ApiResponse(responseCode = "204", description = "Đã xóa ngày lễ")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        holidays.delete(id, ApiProblems.traceId(http));
        return ResponseEntity.noContent().build();
    }
}
