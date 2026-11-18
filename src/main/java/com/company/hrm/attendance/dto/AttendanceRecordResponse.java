package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.AttendanceSource;
import com.company.hrm.attendance.entity.AttendanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceRecordResponse(
        UUID id,
        UUID employeeId,
        UUID shiftId,
        LocalDate workDate,
        Instant checkInAt,
        AttendanceSource checkInSource,
        Instant checkOutAt,
        AttendanceSource checkOutSource,
        AttendanceStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
