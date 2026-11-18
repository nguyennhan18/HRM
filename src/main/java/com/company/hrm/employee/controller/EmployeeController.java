package com.company.hrm.employee.controller;

import com.company.hrm.common.exception.ApiProblems;
import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeHistoryResponse;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.dto.UpdateEmployeeRequest;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Tạo hồ sơ nhân viên nháp (DRAFT)",
            description = "HR/ADMIN tạo hồ sơ nhân viên ở trạng thái DRAFT; chỉ bắt buộc fullName, các trường khác có thể bổ sung sau trước khi kích hoạt. "
                    + "Sinh mã nhân viên tự động NV + 6 chữ số và ghi AuditLog CREATE.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo hồ sơ DRAFT"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Phòng ban, chức danh hoặc quản lý không tồn tại"),
            @ApiResponse(responseCode = "409", description = "Trùng email công ty, CCCD hoặc vòng lặp quản lý")})
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request, HttpServletRequest httpRequest) {
        return employees.create(request, ApiProblems.traceId(httpRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật thông tin hồ sơ nhân viên",
            description = "HR/ADMIN bổ sung hoặc cập nhật thông tin hồ sơ nhân viên. Nếu không gửi cccd hoặc bankAccountNumber thì giữ nguyên giá trị đã lưu. "
                    + "Không cho phép cập nhật hồ sơ đã TERMINATED hoặc xóa trường bắt buộc của hồ sơ đã kích hoạt.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã cập nhật hồ sơ nhân viên"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên hoặc tham chiếu tổ chức"),
            @ApiResponse(responseCode = "409", description = "Trùng email công ty/CCCD, vòng lặp quản lý hoặc hồ sơ đã thôi việc"),
            @ApiResponse(responseCode = "422", description = "Xóa trường bắt buộc của nhân viên đã kích hoạt")})
    public EmployeeResponse update(@PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request, HttpServletRequest httpRequest) {
        return employees.update(id, request, ApiProblems.traceId(httpRequest));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Kích hoạt hồ sơ nhân viên (DRAFT -> ACTIVE)",
            description = "Kiểm tra đủ 6 trường bắt buộc (fullName, companyEmail, departmentId, positionId, startDate, cccd), "
                    + "chuyển trạng thái từ DRAFT sang ACTIVE, tự động tạo tài khoản User gắn vai trò EMPLOYEE với cờ must_change_password = true, "
                    + "gửi mật khẩu tạm qua Notifier và ghi AuditLog cho cả User (USER_PROVISIONED) và Employee (ACTIVATE). "
                    + "Trả 409 nếu hồ sơ không ở trạng thái DRAFT, trả 422 kèm danh sách trường thiếu nếu chưa đủ thông tin.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Kích hoạt nhân viên thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền ADMIN hoặc HR"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên hoặc phòng ban/chức danh tham chiếu"),
            @ApiResponse(responseCode = "409", description = "Hồ sơ không ở trạng thái DRAFT hoặc email đăng nhập đã tồn tại"),
            @ApiResponse(responseCode = "422", description = "Hồ sơ thiếu trường bắt buộc khi kích hoạt")})
    public EmployeeResponse activate(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return employees.activate(id, ApiProblems.traceId(httpRequest));
    }

    @GetMapping
    @Operation(summary = "Danh sách nhân viên trong phạm vi vai trò",
            description = "Lọc AND: name tìm chứa không dấu/không phân biệt hoa thường; departmentId, positionId đúng UUID; status. "
                    + "Không gồm phòng ban con. Ví dụ: ?name=nguyen&status=ACTIVE&size=50&sort=fullName,asc. "
                    + "Sort: id, code, fullName, status, startDate, createdAt, updatedAt; mặc định code,id ASC; "
                    + "thêm id để ổn định thứ tự. size vượt 200 cắt về 200. Không có dữ liệu hoặc liên kết Employee trả trang rỗng 200. "
                    + "Trường cccdLastFour và bankAccountLastFour trả 4 số cuối (ví dụ 1234) cho người có quyền (ADMIN, HR, hoặc EMPLOYEE xem chính mình), "
                    + "và trả dạng che ******1234 khi MANAGER xem cấp dưới.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách phân trang"),
            @ApiResponse(responseCode = "400", description = "Bộ lọc hoặc trường sort không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Vai trò không được phép")})
    public Page<EmployeeResponse> list(@RequestParam(required = false) String name,
            @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) EmployeeStatus status, @ParameterObject Pageable page) {
        return employees.list(name, departmentId, positionId, status, page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canViewEmployee(#id)")
    @Operation(summary = "Xem chi tiết hồ sơ nhân viên",
            description = "ADMIN/HR xem tất cả; MANAGER xem cấp dưới trực tiếp/gián tiếp; EMPLOYEE xem chính mình. "
                    + "Với người có quyền xem dữ liệu nhạy cảm (ADMIN, HR, hoặc EMPLOYEE xem chính mình), "
                    + "cccdLastFour và bankAccountLastFour trả 4 số cuối theo thiết kế T2.1 (ví dụ 1234); "
                    + "với MANAGER xem cấp dưới, các trường này được che dạng ******1234.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chi tiết hồ sơ nhân viên"),
            @ApiResponse(responseCode = "400", description = "ID không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền xem hồ sơ này"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")})
    public EmployeeResponse get(@PathVariable UUID id) {
        return employees.get(id);
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
