package com.company.hrm.employee.controller;

import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
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
}
