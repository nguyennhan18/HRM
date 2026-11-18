package com.company.hrm.attendance.calculator;

import com.company.hrm.attendance.entity.AttendancePolicy;
import com.company.hrm.attendance.entity.AttendanceStatus;
import com.company.hrm.attendance.entity.RoundingMode;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bộ tính giờ làm, đi muộn/về sớm, tăng ca và trạng thái ngày công (lớp thuần, không phụ thuộc Spring/DB).
 */
public final class WorkTimeCalculator {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Clock clock;

    public WorkTimeCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Loại nghỉ phép được duyệt trong ngày công. */
    public enum LeaveCredit {
        NONE,
        HALF_DAY,
        FULL_DAY
    }

    /** Một cặp lượt chấm công vào/ra (UTC). {@code checkOut == null} biểu thị lượt chấm đang mở. */
    public record PunchInterval(Instant checkIn, Instant checkOut) {
    }

    /** Dữ liệu đầu vào đầy đủ cho phép tính một ngày công. */
    public record WorkTimeInput(
            LocalDate workDate,
            Shift shift,
            AttendancePolicy policy,
            List<PunchInterval> punches,
            boolean workingDay,
            boolean holiday,
            BigDecimal holidayMultiplier,
            LeaveCredit approvedLeave,
            boolean periodLocked,
            boolean allowLockedAdjustment) {

        public WorkTimeInput {
            Objects.requireNonNull(workDate, "workDate must not be null");
            Objects.requireNonNull(shift, "shift must not be null");
            Objects.requireNonNull(policy, "policy must not be null");
            // Giữ cả phần tử null (nếu có) để validatePunches trả BusinessException thay vì NullPointerException.
            punches = punches == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(punches));
            approvedLeave = approvedLeave == null ? LeaveCredit.NONE : approvedLeave;
        }

        /** Thứ Hai–Thứ Sáu là ngày làm việc chuẩn; Thứ Bảy–Chủ Nhật là ngày nghỉ cuối tuần. */
        public static boolean isStandardWorkingDay(LocalDate workDate) {
            Objects.requireNonNull(workDate, "workDate must not be null");
            DayOfWeek dayOfWeek = workDate.getDayOfWeek();
            return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        }
    }

    /** Kết quả tính giờ làm và trạng thái ngày công. */
    public record WorkTimeResult(
            int workMinutes,
            int lateMinutes,
            int earlyLeaveMinutes,
            int overtimeMinutes,
            BigDecimal overtimeMultiplier,
            AttendanceStatus status) {
    }

    private record ShiftWindow(
            Instant shiftStart,
            Instant shiftEnd,
            Instant breakStart,
            Instant breakEnd) {
    }

    private record AttendanceBounds(
            int lateMinutes,
            int earlyLeaveMinutes,
            Instant effectiveFirstIn,
            Instant effectiveLastOut) {
    }

    private record RawDurations(Duration workDuration, Duration overtimeDuration) {
    }

    /** Tính toán giờ công, vi phạm giờ giấc, tăng ca và trạng thái ngày công từ cấu hình và lượt chấm. */
    public WorkTimeResult calculate(WorkTimeInput input) {
        Objects.requireNonNull(input, "input must not be null");
        validatePunches(input);
        if (input.periodLocked() && !input.allowLockedAdjustment()) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_PERIOD_LOCKED,
                    "Kỳ công đã khóa, không thể tính hoặc cập nhật dữ liệu chấm công");
        }

        ShiftWindow window = resolveShiftWindow(input.workDate(), input.shift());
        BigDecimal overtimeMultiplier = resolveOvertimeMultiplier(input);
        List<PunchInterval> punches = input.punches();
        AttendanceBounds bounds = evaluateBounds(input, window);

        boolean hasOpenLastPunch = !punches.isEmpty() && punches.getLast().checkOut() == null;
        if (hasOpenLastPunch) {
            return handleOpenLastPunch(input, window, bounds, overtimeMultiplier);
        }

        RawDurations raw = accumulateDurations(input, window, punches, bounds, punches.size(), true);
        int workMinutes = roundMinutes(
                raw.workDuration().toMinutes(),
                input.policy().getRoundingStepMinutes(),
                input.policy().getRoundingMode());
        int overtimeMinutes = roundMinutes(
                raw.overtimeDuration().toMinutes(),
                input.policy().getRoundingStepMinutes(),
                input.policy().getRoundingMode());
        AttendanceStatus status = resolveStatus(input, workMinutes, bounds);

        return new WorkTimeResult(
                workMinutes,
                bounds.lateMinutes(),
                bounds.earlyLeaveMinutes(),
                overtimeMinutes,
                overtimeMultiplier,
                status);
    }

    private void validatePunches(WorkTimeInput input) {
        Instant now = clock.instant();
        List<PunchInterval> punches = input.punches();
        for (int i = 0; i < punches.size(); i++) {
            PunchInterval punch = punches.get(i);
            if (punch == null || punch.checkIn() == null) {
                throw new BusinessException(
                        ErrorCode.ATTENDANCE_INVALID_PUNCH,
                        "Lượt chấm công phải có thời điểm check-in");
            }
            if (punch.checkIn().isAfter(now)) {
                throw new BusinessException(
                        ErrorCode.ATTENDANCE_INVALID_PUNCH,
                        "Thời điểm check-in không được ở tương lai");
            }
            if (punch.checkOut() != null) {
                if (punch.checkOut().isAfter(now)) {
                    throw new BusinessException(
                            ErrorCode.ATTENDANCE_INVALID_PUNCH,
                            "Thời điểm check-out không được ở tương lai");
                }
                if (punch.checkOut().isBefore(punch.checkIn())) {
                    throw new BusinessException(
                            ErrorCode.ATTENDANCE_INVALID_PUNCH,
                            "Thời điểm check-out không được trước thời điểm check-in");
                }
            }
            if (i > 0) {
                PunchInterval prev = punches.get(i - 1);
                if (prev.checkOut() == null) {
                    throw new BusinessException(
                            ErrorCode.ATTENDANCE_INVALID_PUNCH,
                            "Lượt chấm chưa check-out phải nằm ở cuối danh sách");
                }
                if (punch.checkIn().equals(prev.checkIn())) {
                    throw new BusinessException(
                            ErrorCode.ATTENDANCE_INVALID_PUNCH,
                            "Không được trùng thời điểm check-in giữa các lượt chấm");
                }
                if (punch.checkIn().isBefore(prev.checkIn()) || punch.checkIn().isBefore(prev.checkOut())) {
                    throw new BusinessException(
                            ErrorCode.ATTENDANCE_INVALID_PUNCH,
                            "Các khoảng chấm công không được ngược thứ tự hoặc chồng lấn");
                }
            }
        }

        if (!input.workingDay()
                && !input.holiday()
                && input.approvedLeave() == LeaveCredit.NONE
                && punches.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_INVALID_PUNCH,
                    "Không tính ngày công cho ngày nghỉ cuối tuần không có chấm công, ngày lễ hoặc nghỉ phép");
        }
    }

    private ShiftWindow resolveShiftWindow(LocalDate workDate, Shift shift) {
        Instant shiftStart = workDate.atTime(shift.getStartTime()).atZone(BUSINESS_ZONE).toInstant();
        LocalDate shiftEndDate = shift.isOvernight() ? workDate.plusDays(1) : workDate;
        Instant shiftEnd = shiftEndDate.atTime(shift.getEndTime()).atZone(BUSINESS_ZONE).toInstant();

        Instant breakStart = null;
        Instant breakEnd = null;
        if (shift.getBreakStart() != null && shift.getBreakEnd() != null) {
            if (!shift.isOvernight()) {
                breakStart = workDate.atTime(shift.getBreakStart()).atZone(BUSINESS_ZONE).toInstant();
                breakEnd = workDate.atTime(shift.getBreakEnd()).atZone(BUSINESS_ZONE).toInstant();
            } else {
                LocalDate breakStartDate = !shift.getBreakStart().isBefore(shift.getStartTime())
                        ? workDate
                        : workDate.plusDays(1);
                LocalDate breakEndDate = (breakStartDate.equals(workDate)
                        && shift.getBreakEnd().isAfter(shift.getBreakStart()))
                        ? workDate
                        : workDate.plusDays(1);
                breakStart = breakStartDate.atTime(shift.getBreakStart()).atZone(BUSINESS_ZONE).toInstant();
                breakEnd = breakEndDate.atTime(shift.getBreakEnd()).atZone(BUSINESS_ZONE).toInstant();
            }
        }
        return new ShiftWindow(shiftStart, shiftEnd, breakStart, breakEnd);
    }

    private BigDecimal resolveOvertimeMultiplier(WorkTimeInput input) {
        if (input.holiday()) {
            return input.holidayMultiplier() != null
                    ? input.holidayMultiplier()
                    : input.policy().getHolidayOvertimeRate();
        }
        if (!input.workingDay()) {
            return input.policy().getWeekendOvertimeRate();
        }
        return input.policy().getWeekdayOvertimeRate();
    }

    private AttendanceBounds evaluateBounds(WorkTimeInput input, ShiftWindow window) {
        List<PunchInterval> punches = input.punches();
        if (punches.isEmpty()) {
            return new AttendanceBounds(0, 0, null, null);
        }

        Instant firstCheckIn = punches.getFirst().checkIn();
        Instant lastCheckOut = punches.getLast().checkOut();
        boolean trackLateAndEarly = input.workingDay()
                && !input.holiday()
                && input.approvedLeave() != LeaveCredit.FULL_DAY;
        if (!trackLateAndEarly) {
            return new AttendanceBounds(0, 0, firstCheckIn, lastCheckOut);
        }

        Instant expectedStart = window.shiftStart();
        Instant expectedEnd = window.shiftEnd();
        if (input.approvedLeave() == LeaveCredit.HALF_DAY) {
            Instant firstHalfEnd;
            Instant secondHalfStart;
            if (window.breakStart() != null && window.breakEnd() != null) {
                firstHalfEnd = window.breakStart();
                secondHalfStart = window.breakEnd();
            } else {
                Instant midpoint = window.shiftStart()
                        .plus(Duration.between(window.shiftStart(), window.shiftEnd()).dividedBy(2));
                firstHalfEnd = midpoint;
                secondHalfStart = midpoint;
            }
            if (!firstCheckIn.isBefore(firstHalfEnd)) {
                expectedStart = secondHalfStart;
                expectedEnd = window.shiftEnd();
            } else {
                expectedStart = window.shiftStart();
                expectedEnd = firstHalfEnd;
            }
        }

        int lateMinutes = 0;
        Instant effectiveFirstIn = firstCheckIn;
        long rawLate = Duration.between(expectedStart, firstCheckIn).toMinutes();
        if (rawLate > input.policy().getLateGraceMinutes()) {
            lateMinutes = Math.toIntExact(rawLate);
        } else if (rawLate > 0) {
            // Ân hạn kiểu "vách đá": kiểm tra ân hạn trước, trong ngưỡng kéo về mốc đầu ca chuẩn.
            effectiveFirstIn = expectedStart;
        }

        int earlyLeaveMinutes = 0;
        Instant effectiveLastOut = lastCheckOut;
        if (lastCheckOut != null) {
            long rawEarly = Duration.between(lastCheckOut, expectedEnd).toMinutes();
            if (rawEarly > input.policy().getEarlyLeaveGraceMinutes()) {
                earlyLeaveMinutes = Math.toIntExact(rawEarly);
            } else if (rawEarly > 0) {
                effectiveLastOut = expectedEnd;
            }
        }

        return new AttendanceBounds(lateMinutes, earlyLeaveMinutes, effectiveFirstIn, effectiveLastOut);
    }

    private WorkTimeResult handleOpenLastPunch(
            WorkTimeInput input,
            ShiftWindow window,
            AttendanceBounds bounds,
            BigDecimal overtimeMultiplier) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        boolean shiftEndedOrPastDay = input.workDate().isBefore(today)
                || clock.instant().isAfter(window.shiftEnd());
        if (shiftEndedOrPastDay) {
            return new WorkTimeResult(
                    0,
                    bounds.lateMinutes(),
                    0,
                    0,
                    overtimeMultiplier,
                    AttendanceStatus.MISSING_CHECKOUT);
        }

        int closedCount = input.punches().size() - 1;
        RawDurations raw = accumulateDurations(input, window, input.punches(), bounds, closedCount, false);
        int workMinutes = roundMinutes(
                raw.workDuration().toMinutes(),
                input.policy().getRoundingStepMinutes(),
                input.policy().getRoundingMode());
        int overtimeMinutes = roundMinutes(
                raw.overtimeDuration().toMinutes(),
                input.policy().getRoundingStepMinutes(),
                input.policy().getRoundingMode());

        AttendanceStatus provisionalStatus;
        if (input.approvedLeave() == LeaveCredit.FULL_DAY) {
            provisionalStatus = AttendanceStatus.ON_LEAVE;
        } else if (input.holiday()) {
            provisionalStatus = AttendanceStatus.HOLIDAY;
        } else if (bounds.lateMinutes() > 0) {
            provisionalStatus = AttendanceStatus.LATE;
        } else {
            provisionalStatus = AttendanceStatus.PRESENT;
        }

        return new WorkTimeResult(
                workMinutes,
                bounds.lateMinutes(),
                0,
                overtimeMinutes,
                overtimeMultiplier,
                provisionalStatus);
    }

    private RawDurations accumulateDurations(
            WorkTimeInput input,
            ShiftWindow window,
            List<PunchInterval> punches,
            AttendanceBounds bounds,
            int count,
            boolean applyEffectiveLastOut) {
        Duration workDuration = Duration.ZERO;
        Duration overtimeDuration = Duration.ZERO;
        boolean allWorkedAsOvertime = !input.workingDay()
                || input.holiday()
                || input.approvedLeave() == LeaveCredit.FULL_DAY;

        for (int i = 0; i < count; i++) {
            PunchInterval punch = punches.get(i);
            if (allWorkedAsOvertime) {
                Duration worked = workedDuration(
                        punch.checkIn(),
                        punch.checkOut(),
                        window.breakStart(),
                        window.breakEnd());
                workDuration = workDuration.plus(worked);
                overtimeDuration = overtimeDuration.plus(worked);
            } else {
                Instant effStart = (i == 0) ? bounds.effectiveFirstIn() : punch.checkIn();
                Instant effEnd = (applyEffectiveLastOut && i == count - 1)
                        ? bounds.effectiveLastOut()
                        : punch.checkOut();

                Instant inShiftStart = later(effStart, window.shiftStart());
                Instant inShiftEnd = earlier(effEnd, window.shiftEnd());
                workDuration = workDuration.plus(
                        workedDuration(inShiftStart, inShiftEnd, window.breakStart(), window.breakEnd()));

                Duration preShift = positiveDuration(punch.checkIn(), earlier(punch.checkOut(), window.shiftStart()));
                Duration postShift = positiveDuration(later(punch.checkIn(), window.shiftEnd()), punch.checkOut());
                overtimeDuration = overtimeDuration.plus(preShift).plus(postShift);
            }
        }
        return new RawDurations(workDuration, overtimeDuration);
    }

    private AttendanceStatus resolveStatus(WorkTimeInput input, int workMinutes, AttendanceBounds bounds) {
        if (input.approvedLeave() == LeaveCredit.FULL_DAY) {
            return AttendanceStatus.ON_LEAVE;
        }
        if (input.holiday()) {
            return AttendanceStatus.HOLIDAY;
        }
        if (input.punches().isEmpty()) {
            return input.approvedLeave() == LeaveCredit.HALF_DAY
                    ? AttendanceStatus.HALF_DAY
                    : AttendanceStatus.ABSENT;
        }
        if (!input.workingDay()) {
            return workMinutes > 0 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
        }

        AttendancePolicy policy = input.policy();
        int leaveCreditMinutes = input.approvedLeave() == LeaveCredit.HALF_DAY
                ? (policy.getMinFullDayMinutes() - policy.getHalfDayThresholdMinutes())
                : 0;
        int effectiveDayMinutes = workMinutes + leaveCreditMinutes;

        if (effectiveDayMinutes < policy.getHalfDayThresholdMinutes()) {
            return AttendanceStatus.ABSENT;
        }
        if (effectiveDayMinutes < policy.getMinFullDayMinutes()) {
            return AttendanceStatus.HALF_DAY;
        }
        if (bounds.lateMinutes() > 0) {
            return AttendanceStatus.LATE;
        }
        if (bounds.earlyLeaveMinutes() > 0) {
            return AttendanceStatus.EARLY_LEAVE;
        }
        return AttendanceStatus.PRESENT;
    }

    private static Duration workedDuration(Instant start, Instant end, Instant breakStart, Instant breakEnd) {
        if (!end.isAfter(start)) {
            return Duration.ZERO;
        }
        Duration total = Duration.between(start, end);
        if (breakStart != null && breakEnd != null) {
            total = total.minus(overlap(start, end, breakStart, breakEnd));
        }
        return total;
    }

    private static Duration overlap(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        Instant start = later(aStart, bStart);
        Instant end = earlier(aEnd, bEnd);
        return positiveDuration(start, end);
    }

    private static Duration positiveDuration(Instant start, Instant end) {
        return end.isAfter(start) ? Duration.between(start, end) : Duration.ZERO;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static int roundMinutes(long rawMinutes, int stepMinutes, RoundingMode roundingMode) {
        if (rawMinutes <= 0) {
            return 0;
        }
        if (roundingMode == null || roundingMode == RoundingMode.NONE || stepMinutes <= 1) {
            return Math.toIntExact(rawMinutes);
        }
        long quotient = rawMinutes / stepMinutes;
        long remainder = rawMinutes % stepMinutes;
        return switch (roundingMode) {
            case NONE -> Math.toIntExact(rawMinutes);
            case DOWN -> Math.toIntExact(quotient * stepMinutes);
            case UP -> Math.toIntExact(remainder == 0 ? rawMinutes : (quotient + 1) * stepMinutes);
            case NEAREST -> Math.toIntExact((remainder * 2 >= stepMinutes ? quotient + 1 : quotient) * stepMinutes);
        };
    }
}
