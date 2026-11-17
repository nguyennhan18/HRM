package com.company.hrm.contract.controller;

import com.company.hrm.contract.dto.*;
import com.company.hrm.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.company.hrm.common.exception.ApiProblems;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/contracts")
@SecurityRequirement(name = "bearerAuth") @PreAuthorize("isAuthenticated()")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Không có quyền"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy hợp đồng/nhân viên"),
        @ApiResponse(responseCode = "409", description = "Chồng lấn, chuyển trạng thái sai hoặc hợp đồng bất biến")})
public class ContractController {
    private final ContractService contracts;
    public ContractController(ContractService contracts) { this.contracts = contracts; }
    @PostMapping
    @Operation(summary = "Tạo hợp đồng DRAFT", description = "Không nhận status. DRAFT được phép trùng ngày; kiểm tra chồng lấn khi activate. "
            + "Ví dụ: {\"employeeId\":\"00000000-0000-4000-8000-000000000001\",\"type\":\"FIXED_TERM\","
            + "\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\",\"baseSalary\":10000000,\"allowance\":0}. Tiền VND/tháng, số nguyên không âm.")
    @ApiResponse(responseCode = "201", description = "Đã tạo bản nháp")
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody CreateContractRequest r, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.create(r, ApiProblems.traceId(http)));
    }
    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin hợp đồng", description = "Body như tạo, không nhận status. DRAFT sửa đầy đủ, được trùng ngày. "
            + "ACTIVE chỉ sửa lương/phụ cấp và rút ngắn endDate; không gia hạn. TERMINATED trả CONTRACT_TERMINATED_IMMUTABLE.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật")
    public ContractResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateContractRequest r, HttpServletRequest http) {
        return contracts.update(id, r, ApiProblems.traceId(http));
    }
    @PostMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt DRAFT", description = "Không có body. Ví dụ POST /api/v1/contracts/{id}/activate. "
            + "Chống chồng lấn cả ACTIVE và TERMINATED, gồm cả hai đầu ngày. Trùng ngày trả CONTRACT_OVERLAP.")
    @ApiResponse(responseCode = "200", description = "Đã kích hoạt")
    public ContractResponse activate(@PathVariable UUID id, HttpServletRequest http) { return contracts.activate(id, ApiProblems.traceId(http)); }
    @PostMapping("/{id}/terminate")
    @Operation(summary = "Chấm dứt ACTIVE", description = "Ví dụ {\"endDate\":\"2026-11-18\"}. "
            + "Ngày thực tế trong khoảng startDate–endDate hiện tại, cập nhật cùng transaction. Sau đó bất biến.")
    @ApiResponse(responseCode = "200", description = "Đã chấm dứt")
    public ContractResponse terminate(@PathVariable UUID id, @Valid @RequestBody TerminateContractRequest r, HttpServletRequest http) {
        return contracts.terminate(id, r, ApiProblems.traceId(http));
    }
    @GetMapping("/{id}")
    @Operation(summary = "Xem hợp đồng và lương", description = "ADMIN/HR hoặc EMPLOYEE sở hữu. MANAGER không xem cấp dưới. "
            + "expired = endDate trước hôm nay tại Asia/Ho_Chi_Minh, độc lập status; ngày kết thúc hôm nay chưa expired. Ví dụ GET /api/v1/contracts/{id}.")
    @ApiResponse(responseCode = "200", description = "Chi tiết hợp đồng")
    public ContractResponse get(@PathVariable UUID id) { return contracts.get(id); }
    @GetMapping
    @Operation(summary = "Danh sách theo nhân viên", description = "Ví dụ ?employeeId={uuid}&page=0&size=20&sort=endDate,asc. "
            + "ADMIN/HR hoặc EMPLOYEE sở hữu; không có dữ liệu trả trang rỗng. size tối đa 200; "
            + "sort: id,startDate,endDate,status,createdAt,updatedAt; mặc định endDate,id ASC.")
    @ApiResponse(responseCode = "200", description = "Trang hợp đồng")
    public Page<ContractResponse> list(@RequestParam UUID employeeId, @ParameterObject Pageable page) {
        return contracts.list(employeeId, page);
    }
    @GetMapping("/expiring")
    @Operation(summary = "Hợp đồng sắp hết hạn", description = "Chỉ ADMIN/HR; ACTIVE kết thúc trong [hôm nay, hôm nay+days], giờ Việt Nam. "
            + "Ví dụ ?days=30&page=0&size=20; days 1–365, mặc định 30. Phân trang/sort giống danh sách theo nhân viên.")
    @ApiResponse(responseCode = "200", description = "Trang hợp đồng sắp hết hạn")
    public Page<ContractResponse> expiring(@RequestParam(defaultValue = "30") int days, @ParameterObject Pageable page) {
        return contracts.expiring(days, page);
    }
}
