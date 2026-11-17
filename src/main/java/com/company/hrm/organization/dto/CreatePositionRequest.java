package com.company.hrm.organization.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import java.util.UUID;

public record CreatePositionRequest(
        @NotBlank(message = "Mã không được để trống")
        @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "Mã gồm 2–20 ký tự A–Z, 0–9, gạch dưới hoặc gạch ngang")
        @Schema(example = "DEV", description = "Mã nhập tay, trim + uppercase; bất biến sau khi tạo") String code,
        @NotBlank(message = "Tên không được để trống") @Size(max = 200, message = "Tên tối đa 200 ký tự") @Schema(example = "Lập trình viên") String name) {
    public CreatePositionRequest {
        code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.trim();
    }
}
