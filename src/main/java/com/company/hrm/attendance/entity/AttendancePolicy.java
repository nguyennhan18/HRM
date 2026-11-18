package com.company.hrm.attendance.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

@Entity
@Table(name = "attendance_policies")
@Getter
public class AttendancePolicy extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes;

    @Column(name = "early_leave_grace_minutes", nullable = false)
    private int earlyLeaveGraceMinutes;

    @Column(name = "rounding_step_minutes", nullable = false)
    private int roundingStepMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false, length = 20)
    private RoundingMode roundingMode;

    @Column(name = "half_day_threshold_minutes", nullable = false)
    private int halfDayThresholdMinutes;

    @Column(name = "min_full_day_minutes", nullable = false)
    private int minFullDayMinutes;

    @Column(name = "weekday_overtime_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal weekdayOvertimeRate;

    @Column(name = "weekend_overtime_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal weekendOvertimeRate;

    @Column(name = "holiday_overtime_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal holidayOvertimeRate;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPolicy;

    protected AttendancePolicy() {
    }

    public AttendancePolicy(String code, String name, int lateGraceMinutes, int earlyLeaveGraceMinutes,
            int roundingStepMinutes, RoundingMode roundingMode, int halfDayThresholdMinutes,
            int minFullDayMinutes, BigDecimal weekdayOvertimeRate, BigDecimal weekendOvertimeRate,
            BigDecimal holidayOvertimeRate, boolean defaultPolicy) {
        this.code = code;
        this.name = name;
        this.lateGraceMinutes = lateGraceMinutes;
        this.earlyLeaveGraceMinutes = earlyLeaveGraceMinutes;
        this.roundingStepMinutes = roundingStepMinutes;
        this.roundingMode = roundingMode;
        this.halfDayThresholdMinutes = halfDayThresholdMinutes;
        this.minFullDayMinutes = minFullDayMinutes;
        this.weekdayOvertimeRate = weekdayOvertimeRate;
        this.weekendOvertimeRate = weekendOvertimeRate;
        this.holidayOvertimeRate = holidayOvertimeRate;
        this.defaultPolicy = defaultPolicy;
    }

    public void update(String name, int lateGraceMinutes, int earlyLeaveGraceMinutes,
            int roundingStepMinutes, RoundingMode roundingMode, int halfDayThresholdMinutes,
            int minFullDayMinutes, BigDecimal weekdayOvertimeRate, BigDecimal weekendOvertimeRate,
            BigDecimal holidayOvertimeRate, boolean defaultPolicy) {
        this.name = name;
        this.lateGraceMinutes = lateGraceMinutes;
        this.earlyLeaveGraceMinutes = earlyLeaveGraceMinutes;
        this.roundingStepMinutes = roundingStepMinutes;
        this.roundingMode = roundingMode;
        this.halfDayThresholdMinutes = halfDayThresholdMinutes;
        this.minFullDayMinutes = minFullDayMinutes;
        this.weekdayOvertimeRate = weekdayOvertimeRate;
        this.weekendOvertimeRate = weekendOvertimeRate;
        this.holidayOvertimeRate = holidayOvertimeRate;
        this.defaultPolicy = defaultPolicy;
    }

    public void setDefaultPolicy(boolean defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof AttendancePolicy p && getId() != null && getId().equals(p.getId()));
    }

    @Override
    public int hashCode() {
        return AttendancePolicy.class.hashCode();
    }

    @Override
    public String toString() {
        return "AttendancePolicy[id=" + getId() + ", code=" + code + "]";
    }
}
