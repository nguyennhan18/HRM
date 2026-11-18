package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.ShiftAssignmentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeShiftResponse(
        UUID id,
        UUID employeeId,
        UUID shiftId,
        ShiftAssignmentType assignmentType,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        Instant updatedAt) {
}
