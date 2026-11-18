package com.company.hrm.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateHolidayRequest(
        @NotNull(message = "Ngày lễ không được để trống")
        @Schema(example = "2026-09-02")
        LocalDate holidayDate,

        @NotBlank(message = "Tên ngày lễ không được để trống")
        @Size(max = 200, message = "Tên ngày lễ tối đa 200 ký tự")
        @Schema(example = "Quốc khánh 2/9")
        String name,

        @NotNull(message = "Hệ số ngày lễ không được để trống")
        @DecimalMin(value = "1.00", message = "Hệ số ngày lễ phải từ 1.00 đến 10.00")
        @DecimalMax(value = "10.00", message = "Hệ số ngày lễ phải từ 1.00 đến 10.00")
        @Digits(integer = 3, fraction = 2, message = "Hệ số ngày lễ tối đa 2 chữ số thập phân")
        @Schema(example = "3.00")
        BigDecimal multiplier) {

    public CreateHolidayRequest {
        name = name == null ? null : name.trim();
    }
}
