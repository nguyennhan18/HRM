package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.RoundingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttendancePolicyResponse(
        UUID id,
        String code,
        String name,
        int lateGraceMinutes,
        int earlyLeaveGraceMinutes,
        int roundingStepMinutes,
        RoundingMode roundingMode,
        int halfDayThresholdMinutes,
        int minFullDayMinutes,
        BigDecimal weekdayOvertimeRate,
        BigDecimal weekendOvertimeRate,
        BigDecimal holidayOvertimeRate,
        boolean defaultPolicy,
        Instant createdAt,
        Instant updatedAt) {
}
