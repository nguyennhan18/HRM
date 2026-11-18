package com.company.hrm.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.Locale;

public record UpdateShiftRequest(
        @NotBlank(message = "Mã ca không được để trống")
        @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "Mã ca gồm 2–20 ký tự A–Z, 0–9, gạch dưới hoặc gạch ngang")
        @Schema(example = "HC", description = "Phải giữ nguyên mã hiện tại")
        String code,

        @NotBlank(message = "Tên ca không được để trống")
        @Size(max = 200, message = "Tên ca tối đa 200 ký tự")
        @Schema(example = "Ca hành chính ngày")
        String name,

        @NotNull(message = "Giờ bắt đầu ca không được để trống")
        @Schema(example = "08:00:00", type = "string")
        LocalTime startTime,

        @NotNull(message = "Giờ kết thúc ca không được để trống")
        @Schema(example = "17:00:00", type = "string")
        LocalTime endTime,

        @Schema(example = "12:00:00", type = "string", nullable = true)
        LocalTime breakStart,

        @Schema(example = "13:00:00", type = "string", nullable = true)
        LocalTime breakEnd,

        @NotNull(message = "Cờ qua nửa đêm không được để trống")
        @Schema(example = "false")
        Boolean overnight,

        @NotNull(message = "Cờ ca mặc định không được để trống")
        @Schema(example = "false")
        Boolean defaultShift) {

    public UpdateShiftRequest {
        code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.trim();
    }
}
