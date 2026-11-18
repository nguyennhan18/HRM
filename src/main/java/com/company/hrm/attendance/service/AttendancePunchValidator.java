package com.company.hrm.attendance.service;

import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Lớp thuần kiểm tra quy tắc thời điểm chấm công, thứ tự và xác định ngày công. */
public final class AttendancePunchValidator {

    public static final Duration MAX_CHECKOUT_AFTER_SHIFT_END = Duration.ofHours(6);
    public static final long EARLY_CHECK_IN_HOURS = 2;

    private AttendancePunchValidator() {
    }

    public static void validatePunchTime(Instant punchTime, Instant serverNow) {
        if (punchTime == null || serverNow == null) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Thời điểm chấm công không được để trống");
        }
        if (punchTime.isAfter(serverNow)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FUTURE_TIME, "Thời điểm chấm công không được ở tương lai");
        }
    }

    public static void validateCheckOutOrder(Instant checkInAt, Instant checkOutAt) {
        if (checkInAt == null) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN,
                    "Nhân viên chưa check-in hoặc đã check-out");
        }
        if (checkOutAt == null) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Thời điểm check-out không được để trống");
        }
        if (checkOutAt.isBefore(checkInAt)) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_INVALID_ORDER,
                    "Thời điểm check-out không được trước thời điểm check-in");
        }
    }

    /**
     * Phiên mở chưa check-out chỉ còn hiệu lực trong khoảng kết thúc ca + ngưỡng chờ.
     * Quá ngưỡng này, bản ghi giữ nguyên trạng thái MISSING_CHECKOUT (giải trình ở T3.5) và không chặn check-in mới.
     */
    public static boolean isSessionStillOpen(
            LocalDate recordWorkDate,
            LocalTime shiftStartTime,
            LocalTime shiftEndTime,
            boolean overnight,
            Instant checkInAt,
            Instant now,
            ZoneId zone) {
        LocalDate endDate = overnight ? recordWorkDate.plusDays(1) : recordWorkDate;
        Instant shiftEnd = ZonedDateTime.of(endDate, shiftEndTime, zone).toInstant();
        Instant cutoff = shiftEnd.plus(MAX_CHECKOUT_AFTER_SHIFT_END);
        return !now.isAfter(cutoff);
    }

    /**
     * Xác định ngày công khi check-in.
     * Nếu cùng thỏa cửa sổ check-in sớm của ca ngày hôm nay (2 giờ trước giờ vào ca) và phần sau nửa đêm của ca đêm hôm qua,
     * ưu tiên gán cho ca hôm nay.
     */
    public static LocalDate resolveCheckInWorkDate(
            LocalDate today,
            LocalTime currentLocalTime,
            Shift todayShift,
            Shift yesterdayShift,
            boolean yesterdayHasRecord) {
        LocalDate yesterday = today.minusDays(1);
        if (todayShift != null && !todayShift.isOvernight()) {
            LocalTime earliestTodayCheckIn = todayShift.getStartTime().getHour() >= EARLY_CHECK_IN_HOURS
                    ? todayShift.getStartTime().minusHours(EARLY_CHECK_IN_HOURS)
                    : LocalTime.MIN;
            if (!currentLocalTime.isBefore(earliestTodayCheckIn)
                    && !currentLocalTime.isAfter(todayShift.getEndTime())) {
                return today;
            }
        }
        if (yesterdayShift != null
                && yesterdayShift.isOvernight()
                && !currentLocalTime.isAfter(yesterdayShift.getEndTime())
                && !yesterdayHasRecord) {
            return yesterday;
        }
        return today;
    }
}
