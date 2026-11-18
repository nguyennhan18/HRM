package com.company.hrm.attendance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
        UUID id,
        LocalDate holidayDate,
        String name,
        BigDecimal multiplier,
        Instant createdAt,
        Instant updatedAt) {
}
