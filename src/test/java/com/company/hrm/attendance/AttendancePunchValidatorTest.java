package com.company.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.attendance.service.AttendancePunchValidator;
import com.company.hrm.common.config.TimeConfig;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AttendancePunchValidatorTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        "2026-06-01T01:00:00Z, 2026-06-01T01:00:00Z, NULL",
        "2026-06-01T00:59:59Z, 2026-06-01T01:00:00Z, NULL",
        "2026-06-01T01:00:00.001Z, 2026-06-01T01:00:00Z, ATTENDANCE_FUTURE_TIME",
        "2026-06-01T02:00:00Z, 2026-06-01T01:00:00Z, ATTENDANCE_FUTURE_TIME",
        "NULL, 2026-06-01T01:00:00Z, HRM_VALIDATION_ERROR"
    })
    void should_validate_punch_time_against_server_clock(
            Instant punchTime,
            Instant serverNow,
            ErrorCode expectedError) {
        if (expectedError == null) {
            assertThatCode(() -> AttendancePunchValidator.validatePunchTime(punchTime, serverNow))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> AttendancePunchValidator.validatePunchTime(punchTime, serverNow))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(expectedError));
        }
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        "2026-06-01T01:00:00Z, 2026-06-01T01:00:00Z, NULL",
        "2026-06-01T01:00:00Z, 2026-06-01T10:00:00Z, NULL",
        "2026-06-01T01:00:00Z, 2026-06-01T00:59:59Z, ATTENDANCE_INVALID_ORDER",
        "NULL, 2026-06-01T10:00:00Z, ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN",
        "2026-06-01T01:00:00Z, NULL, HRM_VALIDATION_ERROR"
    })
    void should_validate_check_out_order(
            Instant checkInAt,
            Instant checkOutAt,
            ErrorCode expectedError) {
        if (expectedError == null) {
            assertThatCode(() -> AttendancePunchValidator.validateCheckOutOrder(checkInAt, checkOutAt))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> AttendancePunchValidator.validateCheckOutOrder(checkInAt, checkOutAt))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(expectedError));
        }
    }

    @ParameterizedTest
    @CsvSource({
        // Day shift 08:00-17:00 VN (shiftEnd = 2026-06-01T10:00:00Z, cutoff = 2026-06-01T16:00:00Z)
        "2026-06-01, 08:00, 17:00, false, 2026-06-01T01:00:00Z, 2026-06-01T10:00:00Z, true",
        "2026-06-01, 08:00, 17:00, false, 2026-06-01T01:00:00Z, 2026-06-01T16:00:00Z, true",
        "2026-06-01, 08:00, 17:00, false, 2026-06-01T01:00:00Z, 2026-06-01T16:00:01Z, false",
        "2026-06-01, 08:00, 17:00, false, 2026-06-01T01:00:00Z, 2026-06-02T01:00:00Z, false",
        // Overnight shift 22:00-06:00 VN (shiftEnd = 2026-06-01T23:00:00Z, cutoff = 2026-06-02T05:00:00Z)
        "2026-06-01, 22:00, 06:00, true, 2026-06-01T15:00:00Z, 2026-06-01T23:02:00Z, true",
        "2026-06-01, 22:00, 06:00, true, 2026-06-01T15:00:00Z, 2026-06-02T05:00:00Z, true",
        "2026-06-01, 22:00, 06:00, true, 2026-06-01T15:00:00Z, 2026-06-02T05:00:01Z, false"
    })
    void should_determine_whether_open_session_is_still_open(
            LocalDate workDate,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            boolean overnight,
            Instant checkInAt,
            Instant now,
            boolean expectedOpen) {
        boolean actual = AttendancePunchValidator.isSessionStillOpen(
                workDate, shiftStart, shiftEnd, overnight, checkInAt, now, TimeConfig.BUSINESS_ZONE);
        assertThat(actual).isEqualTo(expectedOpen);
    }

    @ParameterizedTest
    @MethodSource("workDateCases")
    void should_resolve_check_in_work_date_between_overnight_yesterday_and_today_shift(
            LocalDate today,
            LocalTime localTime,
            Shift todayShift,
            Shift yesterdayShift,
            boolean yesterdayHasRecord,
            LocalDate expectedWorkDate) {
        LocalDate resolved = AttendancePunchValidator.resolveCheckInWorkDate(
                today, localTime, todayShift, yesterdayShift, yesterdayHasRecord);
        assertThat(resolved).isEqualTo(expectedWorkDate);
    }

    static Stream<Arguments> workDateCases() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        LocalDate yesterday = LocalDate.of(2026, 6, 15);
        Shift dayHc = new Shift("HC", "Hành chính", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, false, true);
        Shift morning = new Shift("SANG", "Ca sáng", LocalTime.of(6, 0), LocalTime.of(14, 0), null, null, false, false);
        Shift earlyDawn = new Shift("SOM", "Ca sớm", LocalTime.of(1, 0), LocalTime.of(9, 0), null, null, false, false);
        Shift night = new Shift("NIGHT", "Ca đêm", LocalTime.of(22, 0), LocalTime.of(6, 0), null, null, true, false);

        return Stream.of(
                // 05:00 AM: yesterday NIGHT (22:00-06:00, no record) vs today MORNING (06:00-14:00, early window 04:00-14:00) -> today
                Arguments.of(today, LocalTime.of(5, 0), morning, night, false, today),
                // 04:00 AM exact early boundary for 06:00 shift -> today
                Arguments.of(today, LocalTime.of(4, 0), morning, night, false, today),
                // 03:59 AM before early boundary for 06:00 shift -> yesterday NIGHT
                Arguments.of(today, LocalTime.of(3, 59), morning, night, false, yesterday),
                // 05:00 AM: yesterday NIGHT (no record) vs today HC (08:00-17:00, early window 06:00-17:00) -> yesterday
                Arguments.of(today, LocalTime.of(5, 0), dayHc, night, false, yesterday),
                // 00:30 AM: yesterday NIGHT (no record) vs today HC -> yesterday
                Arguments.of(today, LocalTime.of(0, 30), dayHc, night, false, yesterday),
                // 00:30 AM: yesterday NIGHT already has record -> today
                Arguments.of(today, LocalTime.of(0, 30), dayHc, night, true, today),
                // 05:00 AM: yesterday HC (not overnight) vs today HC -> today
                Arguments.of(today, LocalTime.of(5, 0), dayHc, dayHc, false, today),
                // 22:05 PM: today NIGHT -> today
                Arguments.of(today, LocalTime.of(22, 5), night, night, false, today),
                // 00:30 AM: today earlyDawn (01:00-09:00, start < 2h -> early window 00:00) -> today
                Arguments.of(today, LocalTime.of(0, 30), earlyDawn, night, false, today)
        );
    }
}
