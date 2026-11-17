package com.company.hrm.employee.controller;

import com.company.hrm.employee.dto.DocumentResponse;
import com.company.hrm.employee.entity.DocumentType;
import com.company.hrm.employee.service.EmployeeDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/documents")
@ApiResponses({@ApiResponse(responseCode="400",description="Dữ liệu/file không hợp lệ"),
    @ApiResponse(responseCode="401",description="Chưa xác thực"),@ApiResponse(responseCode="403",description="Không có quyền"),
    @ApiResponse(responseCode="404",description="Không tìm thấy tài liệu/hồ sơ")})
public class EmployeeDocumentController {
    private final EmployeeDocumentService service;
    public EmployeeDocumentController(EmployeeDocumentService service) { this.service=service; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @Operation(summary="Tải lên tài liệu nhân viên",description="PDF/JPEG/PNG tối đa 10 MiB; không nhận CCCD/tài khoản đầy đủ. Ví dụ: type=CV, file=cv.pdf.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Đã lưu tài liệu"),
        @ApiResponse(responseCode="409",description="Nhân viên đã thôi việc"),@ApiResponse(responseCode="413",description="File quá lớn")})
    public ResponseEntity<DocumentResponse> upload(@PathVariable UUID employeeId,
            @Parameter(example="CV") @RequestParam DocumentType type, @RequestPart MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(employeeId,type,file,
                com.company.hrm.common.exception.ApiProblems.traceId(request)));
    }
    @GetMapping("/{documentId}/download")
    @PreAuthorize("@documentAccess.canRead(#employeeId)")
    @Operation(summary="Tải tài liệu gốc theo quyền sở hữu",description="ADMIN/HR tải tất cả; EMPLOYEE tải của mình. Ví dụ documentId là UUID trả về khi upload.")
    @ApiResponse(responseCode="200",description="Nội dung file đính kèm")
    public ResponseEntity<byte[]> download(@PathVariable UUID employeeId,@PathVariable UUID documentId) {
        var result=service.download(employeeId,documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(result.originalName(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff")
                .cacheControl(CacheControl.noStore()).body(result.content());
    }
}
