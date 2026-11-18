package com.company.hrm.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TerminateEmployeeRequest(
        @Schema(description = "Ngày nghỉ việc", example = "2026-11-30")
        @NotNull(message = "Ngày thôi việc không được để trống")
        LocalDate terminationDate,
        @Schema(description = "Lý do thôi việc", example = "Nghỉ việc theo nguyện vọng cá nhân")
        @NotBlank(message = "Lý do thôi việc không được để trống")
        @Size(max = 500, message = "Lý do thôi việc tối đa 500 ký tự")
        String reason) {
    public TerminateEmployeeRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}
