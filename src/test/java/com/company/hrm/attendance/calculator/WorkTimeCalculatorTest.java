package com.company.hrm.attendance.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.hrm.attendance.calculator.WorkTimeCalculator.LeaveCredit;
import com.company.hrm.attendance.calculator.WorkTimeCalculator.PunchInterval;
import com.company.hrm.attendance.calculator.WorkTimeCalculator.WorkTimeInput;
import com.company.hrm.attendance.calculator.WorkTimeCalculator.WorkTimeResult;
import com.company.hrm.attendance.entity.AttendancePolicy;
import com.company.hrm.attendance.entity.AttendanceStatus;
import com.company.hrm.attendance.entity.RoundingMode;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class WorkTimeCalculatorTest {

    private static final ZoneId VN_ZONE = WorkTimeCalculator.BUSINESS_ZONE;
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 6, 19);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 6, 20);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 21);
    private static final Instant FUTURE_CLOCK = Instant.parse("2026-06-30T00:00:00Z");

    private static Shift dayShift() {
        return new Shift(
                "HC",
                "Ca hành chính",
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                false,
                true);
    }

    private static Shift dayShiftWithoutBreak() {
        return new Shift(
                "HC_NOBRK",
                "Ca hành chính không nghỉ",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                null,
                null,
                false,
                false);
    }

    private static Shift nightShift() {
        return new Shift(
                "NIGHT",
                "Ca đêm",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                LocalTime.of(2, 0),
                LocalTime.of(3, 0),
                true,
                false);
    }

    private static Shift nightShiftEarlyBreak() {
        return new Shift(
                "NIGHT_E",
                "Ca đêm nghỉ trước nửa đêm",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                LocalTime.of(23, 0),
                LocalTime.of(23, 30),
                true,
                false);
    }

    private static Shift nightShiftCrossMidnightBreak() {
        return new Shift(
                "NIGHT_X",
                "Ca đêm nghỉ vắt nửa đêm",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                LocalTime.of(23, 30),
                LocalTime.of(0, 30),
                true,
                false);
    }

    private static Shift nightShiftNoBreak() {
        return new Shift(
                "NIGHT_NB",
                "Ca đêm không nghỉ",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                null,
                null,
                true,
                false);
    }

    private static AttendancePolicy defaultPolicy() {
        return policy(15, 0, 15, RoundingMode.NEAREST, 240, 480);
    }

    private static AttendancePolicy policy(
            int lateGrace,
            int earlyGrace,
            int roundingStep,
            RoundingMode roundingMode,
            int halfDayThreshold,
            int minFullDay) {
        return new AttendancePolicy(
                "DEFAULT",
                "Chính sách mẫu",
                lateGrace,
                earlyGrace,
                roundingStep,
                roundingMode,
                halfDayThreshold,
                minFullDay,
                new BigDecimal("1.50"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                true);
    }

    private static Instant vn(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(VN_ZONE).toInstant();
    }

    private static PunchInterval p(LocalDate inDate, int inHour, int inMin, LocalDate outDate, int outHour, int outMin) {
        return new PunchInterval(vn(inDate, inHour, inMin), vn(outDate, outHour, outMin));
    }

    private static PunchInterval openPunch(LocalDate inDate, int inHour, int inMin) {
        return new PunchInterval(vn(inDate, inHour, inMin), null);
    }

    private static WorkTimeInput input(
            LocalDate workDate,
            Shift shift,
            AttendancePolicy policy,
            List<PunchInterval> punches,
            boolean workingDay,
            boolean holiday,
            BigDecimal holidayMultiplier,
            LeaveCredit leave,
            boolean periodLocked,
            boolean allowLockedAdjustment) {
        return new WorkTimeInput(
                workDate,
                shift,
                policy,
                punches,
                workingDay,
                holiday,
                holidayMultiplier,
                leave,
                periodLocked,
                allowLockedAdjustment);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("standardDayAndGraceCases")
    void should_calculate_day_shift_grace_boundaries_and_multiple_punches(
            String caseName,
            AttendancePolicy policy,
            List<PunchInterval> punches,
            int expectedWorkMinutes,
            int expectedLateMinutes,
            int expectedEarlyMinutes,
            int expectedOvertimeMinutes,
            AttendanceStatus expectedStatus) {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));

        WorkTimeResult result = calculator.calculate(input(
                MONDAY, dayShift(), policy, punches, true, false, null, LeaveCredit.NONE, false, false));

        assertThat(result.workMinutes()).isEqualTo(expectedWorkMinutes);
        assertThat(result.lateMinutes()).isEqualTo(expectedLateMinutes);
        assertThat(result.earlyLeaveMinutes()).isEqualTo(expectedEarlyMinutes);
        assertThat(result.overtimeMinutes()).isEqualTo(expectedOvertimeMinutes);
        assertThat(result.overtimeMultiplier()).isEqualByComparingTo("1.50");
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    static Stream<Arguments> standardDayAndGraceCases() {
        AttendancePolicy def = defaultPolicy();
        AttendancePolicy earlyGrace10 = policy(15, 10, 15, RoundingMode.NEAREST, 240, 480);
        AttendancePolicy noRoundLowerFull = policy(15, 0, 0, RoundingMode.NONE, 240, 460);

        return Stream.of(
                Arguments.of(
                        "1. Ca ngày bình thường đúng giờ 08:00-17:00",
                        def,
                        List.of(p(MONDAY, 8, 0, MONDAY, 17, 0)),
                        480, 0, 0, 0, AttendanceStatus.PRESENT),
                Arguments.of(
                        "2a. Biên đi muộn đúng bằng ngưỡng ân hạn 15 phút (08:15-17:00) -> kéo về 08:00",
                        def,
                        List.of(p(MONDAY, 8, 15, MONDAY, 17, 0)),
                        480, 0, 0, 0, AttendanceStatus.PRESENT),
                Arguments.of(
                        "2b. Vượt ngưỡng ân hạn +1 phút (08:16-17:00) -> muộn kiểu vách đá 16 phút, rơi vào HALF_DAY vẫn giữ lateMinutes",
                        def,
                        List.of(p(MONDAY, 8, 16, MONDAY, 17, 0)),
                        465, 16, 0, 0, AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "2c. Vượt ngưỡng ân hạn 16 phút với minFullDay=460 -> đạt đủ công nên trả LATE",
                        noRoundLowerFull,
                        List.of(p(MONDAY, 8, 16, MONDAY, 17, 0)),
                        464, 16, 0, 0, AttendanceStatus.LATE),
                Arguments.of(
                        "2d. Biên về sớm đúng bằng ngưỡng ân hạn 10 phút (08:00-16:50) -> kéo về 17:00",
                        earlyGrace10,
                        List.of(p(MONDAY, 8, 0, MONDAY, 16, 50)),
                        480, 0, 0, 0, AttendanceStatus.PRESENT),
                Arguments.of(
                        "2e. Vượt ngưỡng ân hạn về sớm +1 phút (08:00-16:49, grace=10) -> early=11, vẫn giữ HALF_DAY khi công 465",
                        earlyGrace10,
                        List.of(p(MONDAY, 8, 0, MONDAY, 16, 49)),
                        465, 0, 11, 0, AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "2f. Về sớm 7 phút (grace=0) được làm tròn NEAREST thành 480 nhưng vẫn trả EARLY_LEAVE theo ADR-034",
                        def,
                        List.of(p(MONDAY, 8, 0, MONDAY, 16, 53)),
                        480, 0, 7, 0, AttendanceStatus.EARLY_LEAVE),
                Arguments.of(
                        "2g. Vừa đi muộn 16p vừa về sớm 5p với minFullDay=450 -> ưu tiên LATE và trả đủ cả hai số phút",
                        policy(15, 0, 15, RoundingMode.NEAREST, 240, 450),
                        List.of(p(MONDAY, 8, 16, MONDAY, 16, 55)),
                        465, 16, 5, 0, AttendanceStatus.LATE),
                Arguments.of(
                        "2h. Làm dưới ngưỡng nửa công (08:00-11:30 = 210 < 240) -> ABSENT nhưng vẫn ghi nhận earlyLeave",
                        def,
                        List.of(p(MONDAY, 8, 0, MONDAY, 11, 30)),
                        210, 0, 330, 0, AttendanceStatus.ABSENT),
                Arguments.of(
                        "2i. Ngày làm việc không có lượt chấm nào -> ABSENT",
                        def,
                        List.of(),
                        0, 0, 0, 0, AttendanceStatus.ABSENT),
                Arguments.of(
                        "5a. Nhiều lần check-in/out ngắt quãng (08:00-10:00 & 13:00-17:00) -> tổng 360p (HALF_DAY)",
                        def,
                        List.of(
                                p(MONDAY, 8, 0, MONDAY, 10, 0),
                                p(MONDAY, 13, 0, MONDAY, 17, 0)),
                        360, 0, 0, 0, AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "5b. Nhiều lần chấm có khoảng nối tiếp và làm ngoài ca trước/sau giờ (07:30-12:00, 12:00-17:30, 18:00-19:00)",
                        def,
                        List.of(
                                p(MONDAY, 7, 30, MONDAY, 12, 0),
                                p(MONDAY, 12, 0, MONDAY, 17, 30),
                                p(MONDAY, 18, 0, MONDAY, 19, 0)),
                        480, 0, 0, 120, AttendanceStatus.PRESENT),
                Arguments.of(
                        "5c. Khoảng chấm 0 phút hợp lệ (08:00-08:00) trên ngày làm việc -> 0 phút, ABSENT",
                        def,
                        List.of(p(MONDAY, 8, 0, MONDAY, 8, 0)),
                        0, 0, 540, 0, AttendanceStatus.ABSENT)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("overnightShiftCases")
    void should_calculate_overnight_shift_across_midnight_and_determine_type_by_work_date(
            String caseName,
            LocalDate workDate,
            Shift shift,
            boolean workingDay,
            boolean holiday,
            List<PunchInterval> punches,
            int expectedWorkMinutes,
            int expectedLateMinutes,
            int expectedOvertimeMinutes,
            BigDecimal expectedMultiplier,
            AttendanceStatus expectedStatus) {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));

        WorkTimeResult result = calculator.calculate(input(
                workDate, shift, defaultPolicy(), punches, workingDay, holiday, null, LeaveCredit.NONE, false, false));

        assertThat(result.workMinutes()).isEqualTo(expectedWorkMinutes);
        assertThat(result.lateMinutes()).isEqualTo(expectedLateMinutes);
        assertThat(result.overtimeMinutes()).isEqualTo(expectedOvertimeMinutes);
        assertThat(result.overtimeMultiplier()).isEqualByComparingTo(expectedMultiplier);
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    static Stream<Arguments> overnightShiftCases() {
        LocalDate nextDayFromFriday = FRIDAY.plusDays(1); // Thứ Bảy
        LocalDate nextDayFromSaturday = SATURDAY.plusDays(1); // Chủ Nhật
        LocalDate nextDayFromMonday = MONDAY.plusDays(1);

        return Stream.of(
                Arguments.of(
                        "3a. Ca đêm Thứ Sáu 22:00 sang Thứ Bảy 06:00 đúng giờ (nghỉ 02:00-03:00) -> vẫn tính ngày thường theo workDate",
                        FRIDAY,
                        nightShift(),
                        true,
                        false,
                        List.of(p(FRIDAY, 22, 0, nextDayFromFriday, 6, 0)),
                        420, 0, 0, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "3b. Ca đêm làm thêm sau 06:00 sáng hôm sau với minFullDay=420 -> PRESENT và OT",
                        MONDAY,
                        nightShift(),
                        true,
                        false,
                        List.of(p(MONDAY, 22, 10, nextDayFromMonday, 7, 0)),
                        420, 0, 60, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "3c. Ca đêm nghỉ trước nửa đêm (23:00-23:30) đi muộn 20 phút",
                        MONDAY,
                        nightShiftEarlyBreak(),
                        true,
                        false,
                        List.of(p(MONDAY, 22, 20, nextDayFromMonday, 6, 0)),
                        435, 20, 0, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "3d. Ca đêm nghỉ vắt nửa đêm (23:30-00:30) đúng giờ -> trừ đúng 60p nghỉ",
                        MONDAY,
                        nightShiftCrossMidnightBreak(),
                        true,
                        false,
                        List.of(p(MONDAY, 22, 0, nextDayFromMonday, 6, 0)),
                        420, 0, 0, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "3e. Ca đêm không nghỉ giữa ca (22:00-06:00) -> 480 phút PRESENT",
                        MONDAY,
                        nightShiftNoBreak(),
                        true,
                        false,
                        List.of(p(MONDAY, 22, 0, nextDayFromMonday, 6, 0)),
                        480, 0, 0, new BigDecimal("1.50"), AttendanceStatus.PRESENT),
                Arguments.of(
                        "3f. Ca đêm bắt đầu Thứ Bảy 22:00 sang Chủ Nhật -> cuối tuần theo workDate Thứ Bảy, toàn bộ là OT x2.00",
                        SATURDAY,
                        nightShift(),
                        false,
                        false,
                        List.of(p(SATURDAY, 22, 0, nextDayFromSaturday, 6, 0)),
                        420, 0, 420, new BigDecimal("2.00"), AttendanceStatus.PRESENT)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("openPunchCases")
    void should_handle_open_last_punch_between_today_provisional_and_missing_checkout(
            String caseName,
            Instant clockNow,
            LocalDate workDate,
            Shift shift,
            boolean workingDay,
            boolean holiday,
            LeaveCredit leave,
            List<PunchInterval> punches,
            int expectedWorkMinutes,
            int expectedLateMinutes,
            int expectedOvertimeMinutes,
            AttendanceStatus expectedStatus) {
        var calculator = new WorkTimeCalculator(Clock.fixed(clockNow, VN_ZONE));

        WorkTimeResult result = calculator.calculate(input(
                workDate, shift, defaultPolicy(), punches, workingDay, holiday, null, leave, false, false));

        assertThat(result.workMinutes()).isEqualTo(expectedWorkMinutes);
        assertThat(result.lateMinutes()).isEqualTo(expectedLateMinutes);
        assertThat(result.earlyLeaveMinutes()).isZero();
        assertThat(result.overtimeMinutes()).isEqualTo(expectedOvertimeMinutes);
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    static Stream<Arguments> openPunchCases() {
        return Stream.of(
                Arguments.of(
                        "4a. Ngày đã qua còn lượt mở -> MISSING_CHECKOUT, không tự suy diễn giờ, giữ lateMinutes=20",
                        vn(MONDAY.plusDays(1), 8, 0),
                        MONDAY,
                        dayShift(),
                        true,
                        false,
                        LeaveCredit.NONE,
                        List.of(openPunch(MONDAY, 8, 20)),
                        0, 20, 0, AttendanceStatus.MISSING_CHECKOUT),
                Arguments.of(
                        "4b. Cùng ngày nhưng đã quá giờ kết thúc ca (17:01 > 17:00) -> MISSING_CHECKOUT",
                        vn(MONDAY, 17, 1),
                        MONDAY,
                        dayShift(),
                        true,
                        false,
                        LeaveCredit.NONE,
                        List.of(p(MONDAY, 8, 0, MONDAY, 12, 0), openPunch(MONDAY, 13, 0)),
                        0, 0, 0, AttendanceStatus.MISSING_CHECKOUT),
                Arguments.of(
                        "4c. Trong ca hôm nay (đúng mốc 17:00) còn lượt mở đầu tiên đi đúng giờ -> trạng thái tạm PRESENT",
                        vn(MONDAY, 17, 0),
                        MONDAY,
                        dayShift(),
                        true,
                        false,
                        LeaveCredit.NONE,
                        List.of(openPunch(MONDAY, 8, 5)),
                        0, 0, 0, AttendanceStatus.PRESENT),
                Arguments.of(
                        "4d. Trong ca hôm nay (14:00) có lượt trước đã đóng 08:00-12:00 và lượt mở 13:00 -> tính tạm 240p",
                        vn(MONDAY, 14, 0),
                        MONDAY,
                        dayShift(),
                        true,
                        false,
                        LeaveCredit.NONE,
                        List.of(p(MONDAY, 8, 20, MONDAY, 12, 0), openPunch(MONDAY, 13, 0)),
                        225, 20, 0, AttendanceStatus.LATE),
                Arguments.of(
                        "4e. Trong ca hôm nay vào ngày lễ còn lượt mở -> trạng thái tạm HOLIDAY",
                        vn(MONDAY, 14, 0),
                        MONDAY,
                        dayShift(),
                        true,
                        true,
                        LeaveCredit.NONE,
                        List.of(p(MONDAY, 8, 0, MONDAY, 10, 0), openPunch(MONDAY, 13, 0)),
                        120, 0, 120, AttendanceStatus.HOLIDAY),
                Arguments.of(
                        "4f. Trong ca hôm nay ngày nghỉ phép cả ngày còn lượt mở -> trạng thái tạm ON_LEAVE",
                        vn(MONDAY, 14, 0),
                        MONDAY,
                        dayShift(),
                        true,
                        false,
                        LeaveCredit.FULL_DAY,
                        List.of(openPunch(MONDAY, 9, 0)),
                        0, 0, 0, AttendanceStatus.ON_LEAVE)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("weekendHolidayAndLeaveCases")
    void should_calculate_weekend_holiday_and_approved_leave_correctly(
            String caseName,
            LocalDate workDate,
            Shift shift,
            boolean workingDay,
            boolean holiday,
            BigDecimal holidayMultiplier,
            LeaveCredit leave,
            List<PunchInterval> punches,
            int expectedWorkMinutes,
            int expectedLateMinutes,
            int expectedEarlyMinutes,
            int expectedOvertimeMinutes,
            BigDecimal expectedMultiplier,
            AttendanceStatus expectedStatus) {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));

        WorkTimeResult result = calculator.calculate(input(
                workDate, shift, defaultPolicy(), punches, workingDay, holiday, holidayMultiplier, leave, false, false));

        assertThat(result.workMinutes()).isEqualTo(expectedWorkMinutes);
        assertThat(result.lateMinutes()).isEqualTo(expectedLateMinutes);
        assertThat(result.earlyLeaveMinutes()).isEqualTo(expectedEarlyMinutes);
        assertThat(result.overtimeMinutes()).isEqualTo(expectedOvertimeMinutes);
        assertThat(result.overtimeMultiplier()).isEqualByComparingTo(expectedMultiplier);
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    static Stream<Arguments> weekendHolidayAndLeaveCases() {
        return Stream.of(
                Arguments.of(
                        "6a. Ngày lễ không đi làm -> HOLIDAY, 0 phút, dùng hệ số holiday riêng 4.00",
                        MONDAY, dayShift(), true, true, new BigDecimal("4.00"), LeaveCredit.NONE,
                        List.of(),
                        0, 0, 0, 0, new BigDecimal("4.00"), AttendanceStatus.HOLIDAY),
                Arguments.of(
                        "6b. Ngày lễ có đi làm 08:00-17:00 -> giữ HOLIDAY, toàn bộ 480p vào cả work và OT x3.00",
                        MONDAY, dayShift(), true, true, null, LeaveCredit.NONE,
                        List.of(p(MONDAY, 8, 25, MONDAY, 17, 0)),
                        450, 0, 0, 450, new BigDecimal("3.00"), AttendanceStatus.HOLIDAY),
                Arguments.of(
                        "6c. Ngày lễ rơi vào cuối tuần vẫn ưu tiên HOLIDAY",
                        SUNDAY, dayShift(), false, true, null, LeaveCredit.NONE,
                        List.of(),
                        0, 0, 0, 0, new BigDecimal("3.00"), AttendanceStatus.HOLIDAY),
                Arguments.of(
                        "6d. Cuối tuần (Chủ Nhật) đi làm 09:00-12:00 -> PRESENT, 180p vào cả work và OT x2.00",
                        SUNDAY, dayShift(), false, false, null, LeaveCredit.NONE,
                        List.of(p(SUNDAY, 9, 0, SUNDAY, 12, 0)),
                        180, 0, 0, 180, new BigDecimal("2.00"), AttendanceStatus.PRESENT),
                Arguments.of(
                        "6e. Cuối tuần chấm 0 phút (09:00-09:00) -> ABSENT",
                        SUNDAY, dayShift(), false, false, null, LeaveCredit.NONE,
                        List.of(p(SUNDAY, 9, 0, SUNDAY, 9, 0)),
                        0, 0, 0, 0, new BigDecimal("2.00"), AttendanceStatus.ABSENT),
                Arguments.of(
                        "7a. Nghỉ phép cả ngày không chấm công -> ON_LEAVE, 0 phút",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.FULL_DAY,
                        List.of(),
                        0, 0, 0, 0, new BigDecimal("1.50"), AttendanceStatus.ON_LEAVE),
                Arguments.of(
                        "7b. Nghỉ phép cả ngày có đi làm 09:00-12:00 -> giữ ON_LEAVE, 180p vào cả work và OT",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.FULL_DAY,
                        List.of(p(MONDAY, 9, 0, MONDAY, 12, 0)),
                        180, 0, 0, 180, new BigDecimal("1.50"), AttendanceStatus.ON_LEAVE),
                Arguments.of(
                        "7c. Nghỉ phép nửa ngày không đi làm -> HALF_DAY",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.HALF_DAY,
                        List.of(),
                        0, 0, 0, 0, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY),
                Arguments.of(
                        "7d. Nghỉ phép nửa ngày (sáng nghỉ, chiều làm 13:00-17:00) -> cộng bù 240p đạt 480 -> PRESENT",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.HALF_DAY,
                        List.of(p(MONDAY, 13, 0, MONDAY, 17, 0)),
                        240, 0, 0, 0, new BigDecimal("1.50"), AttendanceStatus.PRESENT),
                Arguments.of(
                        "7e. Nghỉ phép nửa ngày (làm sáng 08:00-12:00, chiều nghỉ) -> cộng bù 240p đạt 480 -> PRESENT",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.HALF_DAY,
                        List.of(p(MONDAY, 8, 0, MONDAY, 12, 0)),
                        240, 0, 0, 0, new BigDecimal("1.50"), AttendanceStatus.PRESENT),
                Arguments.of(
                        "7f. Nghỉ phép nửa ngày trên ca không giờ nghỉ (08:00-16:00, làm 12:00-16:00) -> PRESENT",
                        MONDAY, dayShiftWithoutBreak(), true, false, null, LeaveCredit.HALF_DAY,
                        List.of(p(MONDAY, 12, 0, MONDAY, 16, 0)),
                        240, 0, 0, 0, new BigDecimal("1.50"), AttendanceStatus.PRESENT),
                Arguments.of(
                        "7g. Nghỉ phép nửa ngày (làm chiều 13:20-17:00) -> late=20, effectiveDay=465 < 480 -> HALF_DAY",
                        MONDAY, dayShift(), true, false, null, LeaveCredit.HALF_DAY,
                        List.of(p(MONDAY, 13, 20, MONDAY, 17, 0)),
                        225, 20, 0, 0, new BigDecimal("1.50"), AttendanceStatus.HALF_DAY)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidPunchAndLockCases")
    void should_reject_invalid_punches_and_locked_period_with_defined_precedence(
            String caseName,
            Instant clockNow,
            boolean workingDay,
            List<PunchInterval> punches,
            boolean periodLocked,
            boolean allowLockedAdjustment,
            ErrorCode expectedErrorCode) {
        var calculator = new WorkTimeCalculator(Clock.fixed(clockNow, VN_ZONE));

        assertThatThrownBy(() -> calculator.calculate(input(
                MONDAY,
                dayShift(),
                defaultPolicy(),
                punches,
                workingDay,
                false,
                null,
                LeaveCredit.NONE,
                periodLocked,
                allowLockedAdjustment)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expectedErrorCode));
    }

    static Stream<Arguments> invalidPunchAndLockCases() {
        Instant now = vn(MONDAY, 18, 0);
        return Stream.of(
                Arguments.of(
                        "8a. Phần tử punch null -> ATTENDANCE_INVALID_PUNCH",
                        now, true, Arrays.asList((PunchInterval) null), false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8b. checkIn null -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(new PunchInterval(null, vn(MONDAY, 17, 0))),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8c. checkIn ở tương lai so với Clock -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 18, 1, MONDAY, 18, 5)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8d. checkOut ở tương lai so với Clock -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 8, 0, MONDAY, 18, 1)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8e. checkOut trước checkIn -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 10, 0, MONDAY, 9, 59)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8f. Lượt chấm mở không nằm ở cuối -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(openPunch(MONDAY, 8, 0), p(MONDAY, 13, 0, MONDAY, 17, 0)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8g. Hai lượt chấm trùng thời điểm checkIn -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 8, 0, MONDAY, 8, 0), p(MONDAY, 8, 0, MONDAY, 12, 0)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8h. Hai lượt chấm ngược thứ tự thời gian -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 13, 0, MONDAY, 17, 0), p(MONDAY, 8, 0, MONDAY, 12, 0)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8i. Hai lượt chấm chồng lấn nhau -> ATTENDANCE_INVALID_PUNCH",
                        now, true, List.of(p(MONDAY, 8, 0, MONDAY, 12, 30), p(MONDAY, 12, 0, MONDAY, 17, 0)),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "8j. Ngày nghỉ cuối tuần không có chấm công, lễ hay phép -> ATTENDANCE_INVALID_PUNCH",
                        now, false, List.of(),
                        false, false, ErrorCode.ATTENDANCE_INVALID_PUNCH),
                Arguments.of(
                        "9a. Kỳ công đã khóa và không cho phép điều chỉnh -> ATTENDANCE_PERIOD_LOCKED (409)",
                        now, true, List.of(p(MONDAY, 8, 0, MONDAY, 17, 0)),
                        true, false, ErrorCode.ATTENDANCE_PERIOD_LOCKED),
                Arguments.of(
                        "9b. Thứ tự lỗi: punch sai (400) ưu tiên trước kỳ công khóa (409)",
                        now, true, List.of(p(MONDAY, 10, 0, MONDAY, 9, 0)),
                        true, false, ErrorCode.ATTENDANCE_INVALID_PUNCH)
        );
    }

    @Test
    void should_allow_calculation_on_locked_period_when_adjustment_flag_is_enabled() {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));

        WorkTimeResult result = calculator.calculate(input(
                MONDAY,
                dayShift(),
                defaultPolicy(),
                List.of(p(MONDAY, 8, 0, MONDAY, 17, 0)),
                true,
                false,
                null,
                LeaveCredit.NONE,
                true,
                true));

        assertThat(result.workMinutes()).isEqualTo(480);
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @ParameterizedTest
    @CsvSource({
        "NONE, 15, 7, 7",
        "NONE, 15, 8, 8",
        "DOWN, 15, 14, 0",
        "DOWN, 15, 15, 15",
        "DOWN, 15, 29, 15",
        "UP, 15, 0, 0",
        "UP, 15, 1, 15",
        "UP, 15, 15, 15",
        "UP, 15, 16, 30",
        "NEAREST, 15, 7, 0",
        "NEAREST, 15, 8, 15",
        "NEAREST, 15, 22, 15",
        "NEAREST, 15, 23, 30",
        "NEAREST, 10, 4, 0",
        "NEAREST, 10, 5, 10",
        "NEAREST, 10, 15, 20",
        "NEAREST, 1, 7, 7"
    })
    void should_round_work_and_overtime_minutes_independently_by_policy_mode(
            RoundingMode mode,
            int stepMinutes,
            int extraOvertimeMinutes,
            int expectedOvertimeMinutes) {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));
        AttendancePolicy customPolicy = policy(15, 0, stepMinutes, mode, 240, 480);
        Instant checkOut = vn(MONDAY, 17, 0).plusSeconds(extraOvertimeMinutes * 60L);

        WorkTimeResult result = calculator.calculate(input(
                MONDAY,
                dayShift(),
                customPolicy,
                List.of(new PunchInterval(vn(MONDAY, 8, 0), checkOut)),
                true,
                false,
                null,
                LeaveCredit.NONE,
                false,
                false));

        assertThat(result.workMinutes()).isEqualTo(480);
        assertThat(result.overtimeMinutes()).isEqualTo(expectedOvertimeMinutes);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-06-15, true",
        "2026-06-16, true",
        "2026-06-17, true",
        "2026-06-18, true",
        "2026-06-19, true",
        "2026-06-20, false",
        "2026-06-21, false"
    })
    void should_classify_standard_working_days_monday_to_sunday(LocalDate date, boolean expectedWorkingDay) {
        assertThat(WorkTimeInput.isStandardWorkingDay(date)).isEqualTo(expectedWorkingDay);
    }

    @Test
    void should_default_null_optional_inputs_and_reject_missing_required_arguments() {
        var calculator = new WorkTimeCalculator(Clock.fixed(FUTURE_CLOCK, VN_ZONE));
        var inputWithNullOptionals = new WorkTimeInput(
                MONDAY,
                dayShift(),
                defaultPolicy(),
                null,
                true,
                false,
                null,
                null,
                false,
                false);

        WorkTimeResult result = calculator.calculate(inputWithNullOptionals);
        assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.workMinutes()).isZero();

        assertThatThrownBy(() -> new WorkTimeCalculator(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkTimeInput.isStandardWorkingDay(null)).isInstanceOf(NullPointerException.class);
    }
}
