package com.company.hrm.organization.controller;

import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.service.PositionService;
import com.company.hrm.common.exception.ApiProblems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/positions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
    @ApiResponse(responseCode = "403", description = "Không có quyền ghi"),
    @ApiResponse(responseCode = "404", description = "Không tìm thấy"),
    @ApiResponse(responseCode = "409", description = "Trùng mã, đổi mã hoặc xung đột dữ liệu/cây")})
public class PositionController {
    private final PositionService service;
    public PositionController(PositionService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "Danh sách chức danh")
    @ApiResponse(responseCode = "200", description = "Danh sách phân trang")
    public Page<PositionResponse> list(Pageable page) {
        return service.list(page);
    }
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết chức danh")
    @ApiResponse(responseCode = "200", description = "Chi tiết")
    public PositionResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Tạo chức danh")
    @ApiResponse(responseCode = "201", description = "Đã tạo")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody CreatePositionRequest request, HttpServletRequest http) {
        var result = service.create(request, ApiProblems.traceId(http));
        return ResponseEntity.created(URI.create("/api/v1/positions/" + result.id())).body(result);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Cập nhật chức danh; mã bất biến")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật")
    public PositionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePositionRequest request, HttpServletRequest http) {
        return service.update(id, request, ApiProblems.traceId(http));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Xóa cứng chức danh dùng chung")
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        service.delete(id, ApiProblems.traceId(http));
        return ResponseEntity.noContent().build();
    }
}
