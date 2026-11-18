package com.company.hrm.attendance.entity;

/** Trạng thái ngày công chuẩn theo quy tắc nghiệp vụ chấm công. */
public enum AttendanceStatus {
    PRESENT,
    HALF_DAY,
    LATE,
    EARLY_LEAVE,
    ON_LEAVE,
    HOLIDAY,
    ABSENT,
    MISSING_CHECKOUT
}
