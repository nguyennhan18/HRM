package com.company.hrm.attendance.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        String code,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        LocalTime breakStart,
        LocalTime breakEnd,
        boolean overnight,
        boolean defaultShift,
        Instant createdAt,
        Instant updatedAt) {
}
