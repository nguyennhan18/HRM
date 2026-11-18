package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.ShiftAssignmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateEmployeeShiftRequest(
        @NotNull(message = "ID nhân viên không được để trống")
        @Schema(example = "00000000-0000-4000-8000-000000000001")
        UUID employeeId,

        @NotNull(message = "ID ca làm không được để trống")
        @Schema(example = "019c9e00-0000-7000-8000-000000000301")
        UUID shiftId,

        @NotNull(message = "Loại phân ca không được để trống")
        @Schema(example = "DEFAULT")
        ShiftAssignmentType assignmentType,

        @NotNull(message = "Ngày bắt đầu không được để trống")
        @Schema(example = "2026-06-01")
        LocalDate startDate,

        @Schema(example = "2026-06-07", nullable = true)
        LocalDate endDate) {
}
