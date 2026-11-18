package com.company.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.hrm.attendance.entity.AttendanceRecord;
import com.company.hrm.attendance.entity.AttendanceSource;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.attendance.mapper.AttendanceRecordMapper;
import com.company.hrm.attendance.repository.AttendanceRecordRepository;
import com.company.hrm.attendance.repository.EmployeeShiftRepository;
import com.company.hrm.attendance.repository.ShiftRepository;
import com.company.hrm.attendance.service.AttendanceEmployeeGuard;
import com.company.hrm.attendance.service.AttendanceServiceImpl;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.config.TimeConfig;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class AttendanceServiceUnitTest {

    @Test
    void should_map_unique_constraint_violation_on_check_in_without_writing_audit_log() {
        var attendanceRecords = mock(AttendanceRecordRepository.class);
        var employeeShifts = mock(EmployeeShiftRepository.class);
        var shifts = mock(ShiftRepository.class);
        var guard = mock(AttendanceEmployeeGuard.class);
        var mapper = Mappers.getMapper(AttendanceRecordMapper.class);
        var audit = mock(AuditService.class);
        Instant now = Instant.parse("2026-06-15T01:05:00Z");
        Clock clock = Clock.fixed(now, TimeConfig.BUSINESS_ZONE);

        var service = new AttendanceServiceImpl(
                attendanceRecords, employeeShifts, shifts, guard, mapper, audit, clock);

        UUID empId = UUID.randomUUID();
        Shift shift = new Shift("HC", "Hành chính", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, false, true);
        ReflectionTestUtils.setField(shift, "id", UUID.randomUUID());
        when(attendanceRecords.findLockedOpenRecordsByEmployeeIdAndWorkDateIn(any(), any())).thenReturn(List.of());
        when(employeeShifts.findCoveringDate(any(), any())).thenReturn(List.of());
        when(shifts.findByDefaultShiftTrue()).thenReturn(Optional.of(shift));
        when(attendanceRecords.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        when(attendanceRecords.saveAndFlush(any(AttendanceRecord.class))).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint \"uq_attendance_records_employee_date\"",
                                "23505")));

        assertThatThrownBy(() -> service.checkInAt(empId, now, AttendanceSource.WEB, "trace-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_DUPLICATE_CHECK_IN));

        verify(guard).requireActiveEmployee(empId);
        verifyNoInteractions(audit);
    }
}
