package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.RoundingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;

public record CreateAttendancePolicyRequest(
        @NotBlank(message = "Mã chính sách không được để trống")
        @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "Mã gồm 2–20 ký tự A–Z, 0–9, gạch dưới hoặc gạch ngang")
        @Schema(example = "DEFAULT")
        String code,

        @NotBlank(message = "Tên chính sách không được để trống")
        @Size(max = 200, message = "Tên chính sách tối đa 200 ký tự")
        @Schema(example = "Chính sách chấm công mặc định")
        String name,

        @NotNull(message = "Số phút ân hạn đi muộn không được để trống")
        @Min(value = 0, message = "Số phút ân hạn đi muộn phải từ 0 đến 720")
        @Max(value = 720, message = "Số phút ân hạn đi muộn phải từ 0 đến 720")
        @Schema(example = "15")
        Integer lateGraceMinutes,

        @NotNull(message = "Số phút ân hạn về sớm không được để trống")
        @Min(value = 0, message = "Số phút ân hạn về sớm phải từ 0 đến 720")
        @Max(value = 720, message = "Số phút ân hạn về sớm phải từ 0 đến 720")
        @Schema(example = "0")
        Integer earlyLeaveGraceMinutes,

        @NotNull(message = "Bước làm tròn không được để trống")
        @Min(value = 0, message = "Bước làm tròn phải từ 0 đến 240 phút")
        @Max(value = 240, message = "Bước làm tròn phải từ 0 đến 240 phút")
        @Schema(example = "15")
        Integer roundingStepMinutes,

        @NotNull(message = "Kiểu làm tròn không được để trống")
        @Schema(example = "NEAREST")
        RoundingMode roundingMode,

        @NotNull(message = "Ngưỡng nửa công không được để trống")
        @Min(value = 1, message = "Ngưỡng nửa công phải lớn hơn 0")
        @Max(value = 1440, message = "Ngưỡng nửa công tối đa 1440 phút")
        @Schema(example = "240")
        Integer halfDayThresholdMinutes,

        @NotNull(message = "Số phút tối thiểu đủ công không được để trống")
        @Min(value = 1, message = "Số phút tối thiểu đủ công phải từ 1 đến 1440")
        @Max(value = 1440, message = "Số phút tối thiểu đủ công phải từ 1 đến 1440")
        @Schema(example = "480")
        Integer minFullDayMinutes,

        @NotNull(message = "Hệ số tăng ca ngày thường không được để trống")
        @DecimalMin(value = "1.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @DecimalMax(value = "10.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @Digits(integer = 3, fraction = 2, message = "Hệ số tăng ca tối đa 2 chữ số thập phân")
        @Schema(example = "1.50")
        BigDecimal weekdayOvertimeRate,

        @NotNull(message = "Hệ số tăng ca cuối tuần không được để trống")
        @DecimalMin(value = "1.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @DecimalMax(value = "10.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @Digits(integer = 3, fraction = 2, message = "Hệ số tăng ca tối đa 2 chữ số thập phân")
        @Schema(example = "2.00")
        BigDecimal weekendOvertimeRate,

        @NotNull(message = "Hệ số tăng ca ngày lễ không được để trống")
        @DecimalMin(value = "1.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @DecimalMax(value = "10.00", message = "Hệ số tăng ca phải từ 1.00 đến 10.00")
        @Digits(integer = 3, fraction = 2, message = "Hệ số tăng ca tối đa 2 chữ số thập phân")
        @Schema(example = "3.00")
        BigDecimal holidayOvertimeRate,

        @NotNull(message = "Cờ chính sách mặc định không được để trống")
        @Schema(example = "true")
        Boolean defaultPolicy) {

    public CreateAttendancePolicyRequest {
        code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.trim();
    }
}
