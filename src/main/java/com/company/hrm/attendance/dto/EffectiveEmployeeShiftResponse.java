package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.ShiftAssignmentType;
import java.time.LocalDate;
import java.util.UUID;

public record EffectiveEmployeeShiftResponse(
        UUID employeeId,
        LocalDate workDate,
        UUID assignmentId,
        ShiftAssignmentType assignmentType,
        boolean companyDefaultFallback,
        ShiftResponse shift) {
}
