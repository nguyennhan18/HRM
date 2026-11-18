package com.company.hrm.employee.controller;

import com.company.hrm.common.exception.ApiProblems;
import com.company.hrm.employee.dto.EmployeeHistoryResponse;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class EmployeeController {
    private final EmployeeService employees;
    public EmployeeController(EmployeeService employees) { this.employees = employees; }

    @GetMapping
    @Operation(summary = "Danh sách nhân viên trong phạm vi vai trò",
            description = "Lọc AND: name tìm chứa không dấu/không phân biệt hoa thường; departmentId, positionId đúng UUID; status. "
                    + "Không gồm phòng ban con. Ví dụ: ?name=nguyen&status=ACTIVE&size=50&sort=fullName,asc. "
                    + "Sort: id, code, fullName, status, startDate, createdAt, updatedAt; mặc định code,id ASC; "
                    + "thêm id để ổn định thứ tự. size vượt 200 cắt về 200. Không có dữ liệu hoặc liên kết Employee trả trang rỗng 200.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách phân trang"),
            @ApiResponse(responseCode = "400", description = "Bộ lọc hoặc trường sort không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Vai trò không được phép")})
    public Page<EmployeeResponse> list(@RequestParam(required = false) String name,
            @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) EmployeeStatus status, @ParameterObject Pageable page) {
        return employees.list(name, departmentId, positionId, status, page);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cho nhân viên thôi việc",
            description = "Chuyển trạng thái từ ACTIVE hoặc ON_LEAVE_LONG sang TERMINATED, tự động kết thúc hợp đồng ACTIVE, "
                    + "vô hiệu hóa tài khoản User liên kết, thu hồi toàn bộ refresh token và đánh dấu chặn chấm công.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Thôi việc thành công"),
            @ApiResponse(responseCode = "400", description = "Ngày thôi việc hoặc lý do không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên"),
            @ApiResponse(responseCode = "409", description = "Trạng thái nhân viên hoặc ngày hợp đồng không hợp lệ")})
    public EmployeeResponse terminate(@PathVariable UUID id,
            @Valid @RequestBody TerminateEmployeeRequest request, HttpServletRequest httpRequest) {
        return employees.terminate(id, request, ApiProblems.traceId(httpRequest));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Xóa mềm hồ sơ nhân viên",
            description = "Đánh dấu deleted_at, giữ nguyên dữ liệu lịch sử, vô hiệu hóa User liên kết và thu hồi toàn bộ token. "
                    + "Từ chối 409 nếu nhân viên vẫn còn cấp dưới trực tiếp chưa xóa.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Đã xóa mềm nhân viên"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên"),
            @ApiResponse(responseCode = "409", description = "Nhân viên còn cấp dưới trực tiếp chưa xóa")})
    public void delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        employees.delete(id, ApiProblems.traceId(httpRequest));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Lịch sử thay đổi hồ sơ nhân viên",
            description = "Trả về các bản ghi audit_logs của hồ sơ nhân viên (entity_type = Employee), "
                    + "bao gồm cả hồ sơ đã thôi việc hoặc xóa mềm. Mặc định sắp xếp createdAt,id DESC; "
                    + "whitelist sort: id, createdAt, action, actor; size tối đa 200.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách lịch sử thay đổi phân trang"),
            @ApiResponse(responseCode = "400", description = "Tham số phân trang hoặc trường sort không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")})
    public Page<EmployeeHistoryResponse> history(@PathVariable UUID id, @ParameterObject Pageable page) {
        return employees.history(id, page);
    }
}
