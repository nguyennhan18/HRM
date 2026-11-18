package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.AttendanceRecordResponse;
import com.company.hrm.attendance.dto.CheckInRequest;
import com.company.hrm.attendance.dto.CheckOutRequest;
import com.company.hrm.attendance.entity.AttendanceRecord;
import com.company.hrm.attendance.entity.AttendanceSource;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import com.company.hrm.attendance.mapper.AttendanceRecordMapper;
import com.company.hrm.attendance.repository.AttendanceRecordRepository;
import com.company.hrm.attendance.repository.EmployeeShiftRepository;
import com.company.hrm.attendance.repository.ShiftRepository;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.config.TimeConfig;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRecordRepository attendanceRecords;
    private final EmployeeShiftRepository employeeShifts;
    private final ShiftRepository shifts;
    private final AttendanceEmployeeGuard employees;
    private final AttendanceRecordMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public AttendanceServiceImpl(
            AttendanceRecordRepository attendanceRecords,
            EmployeeShiftRepository employeeShifts,
            ShiftRepository shifts,
            AttendanceEmployeeGuard employees,
            AttendanceRecordMapper mapper,
            AuditService audit,
            Clock clock) {
        this.attendanceRecords = attendanceRecords;
        this.employeeShifts = employeeShifts;
        this.shifts = shifts;
        this.employees = employees;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceRecordResponse checkIn(CheckInRequest request, String traceId) {
        UUID employeeId = employees.requireActiveEmployeeForUser(currentUserId());
        Instant serverNow = Instant.now(clock);
        AttendanceSource source = request != null ? request.effectiveSource() : AttendanceSource.WEB;
        return doCheckIn(employeeId, serverNow, serverNow, source, traceId);
    }

    @Override
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceRecordResponse checkOut(CheckOutRequest request, String traceId) {
        UUID employeeId = employees.requireActiveEmployeeForUser(currentUserId());
        Instant serverNow = Instant.now(clock);
        AttendanceSource source = request != null ? request.effectiveSource() : AttendanceSource.WEB;
        return doCheckOut(employeeId, serverNow, serverNow, source, traceId);
    }

    @Override
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceRecordResponse checkInAt(
            UUID employeeId,
            Instant punchTime,
            AttendanceSource source,
            String traceId) {
        employees.requireActiveEmployee(employeeId);
        Instant serverNow = Instant.now(clock);
        AttendanceSource effectiveSource = source != null ? source : AttendanceSource.WEB;
        return doCheckIn(employeeId, punchTime, serverNow, effectiveSource, traceId);
    }

    @Override
    @PreAuthorize("hasAnyRole('MANAGER', 'EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceRecordResponse checkOutAt(
            UUID employeeId,
            Instant punchTime,
            AttendanceSource source,
            String traceId) {
        employees.requireActiveEmployee(employeeId);
        Instant serverNow = Instant.now(clock);
        AttendanceSource effectiveSource = source != null ? source : AttendanceSource.WEB;
        return doCheckOut(employeeId, punchTime, serverNow, effectiveSource, traceId);
    }

    private AttendanceRecordResponse doCheckIn(
            UUID employeeId,
            Instant punchTime,
            Instant serverNow,
            AttendanceSource source,
            String traceId) {
        AttendancePunchValidator.validatePunchTime(punchTime, serverNow);

        var zdt = punchTime.atZone(TimeConfig.BUSINESS_ZONE);
        LocalDate today = zdt.toLocalDate();
        LocalTime localTime = zdt.toLocalTime();
        LocalDate yesterday = today.minusDays(1);

        var openRecords = attendanceRecords.findLockedOpenRecordsByEmployeeIdAndWorkDateIn(
                employeeId, List.of(today, yesterday));
        for (var open : openRecords) {
            Shift openShift = existingShift(open.getShiftId());
            boolean stillOpen = AttendancePunchValidator.isSessionStillOpen(
                    open.getWorkDate(),
                    openShift.getStartTime(),
                    openShift.getEndTime(),
                    openShift.isOvernight(),
                    open.getCheckInAt(),
                    punchTime,
                    TimeConfig.BUSINESS_ZONE);
            if (!stillOpen) {
                continue;
            }
            if (open.getWorkDate().equals(yesterday)
                    && openShift.isOvernight()
                    && localTime.isAfter(openShift.getEndTime())) {
                continue;
            }
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_DUPLICATE_CHECK_IN,
                    "Nhân viên đang có phiên chấm công chưa check-out");
        }

        Shift todayShift = findEffectiveShift(employeeId, today).orElse(null);
        Shift yesterdayShift = findEffectiveShift(employeeId, yesterday).orElse(null);
        boolean yesterdayHasRecord = attendanceRecords.findByEmployeeIdAndWorkDate(employeeId, yesterday).isPresent();
        LocalDate targetWorkDate = AttendancePunchValidator.resolveCheckInWorkDate(
                today, localTime, todayShift, yesterdayShift, yesterdayHasRecord);
        Shift targetShift = targetWorkDate.equals(yesterday) ? yesterdayShift : todayShift;
        if (targetShift == null) {
            throw new BusinessException(
                    ErrorCode.HRM_RESOURCE_NOT_FOUND,
                    "Chưa có phân ca hoặc ca mặc định toàn công ty cho ngày này");
        }

        if (attendanceRecords.findByEmployeeIdAndWorkDate(employeeId, targetWorkDate).isPresent()) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_DUPLICATE_CHECK_IN,
                    "Nhân viên đã check-in trong ngày công này");
        }

        var record = AttendanceRecord.checkIn(
                employeeId,
                targetShift.getId(),
                targetWorkDate,
                punchTime,
                source);
        try {
            attendanceRecords.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateAttendanceViolation(ex)) {
                throw new BusinessException(
                        ErrorCode.ATTENDANCE_DUPLICATE_CHECK_IN,
                        "Nhân viên đã check-in trong ngày công này");
            }
            throw ex;
        }

        var after = mapper.toResponse(record);
        audit.record("CHECK_IN", "AttendanceRecord", record.getId(), null, after, traceId);
        return after;
    }

    private AttendanceRecordResponse doCheckOut(
            UUID employeeId,
            Instant punchTime,
            Instant serverNow,
            AttendanceSource source,
            String traceId) {
        AttendancePunchValidator.validatePunchTime(punchTime, serverNow);

        var zdt = punchTime.atZone(TimeConfig.BUSINESS_ZONE);
        LocalDate today = zdt.toLocalDate();
        LocalDate yesterday = today.minusDays(1);

        var openRecords = attendanceRecords.findLockedOpenRecordsByEmployeeIdAndWorkDateIn(
                employeeId, List.of(today, yesterday));
        AttendanceRecord target = null;
        for (var open : openRecords) {
            Shift openShift = existingShift(open.getShiftId());
            if (AttendancePunchValidator.isSessionStillOpen(
                    open.getWorkDate(),
                    openShift.getStartTime(),
                    openShift.getEndTime(),
                    openShift.isOvernight(),
                    open.getCheckInAt(),
                    punchTime,
                    TimeConfig.BUSINESS_ZONE)) {
                target = open;
                break;
            }
        }
        if (target == null) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN,
                    "Nhân viên chưa check-in hoặc phiên chấm công đã kết thúc");
        }

        var before = mapper.toResponse(target);
        target.recordCheckOut(punchTime, source);
        attendanceRecords.flush();
        var after = mapper.toResponse(target);
        audit.record("CHECK_OUT", "AttendanceRecord", target.getId(), before, after, traceId);
        return after;
    }

    private UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Chưa xác thực người dùng");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE,
                    "Chỉ nhân viên đang ở trạng thái ACTIVE mới được chấm công");
        }
    }

    private Optional<Shift> findEffectiveShift(UUID employeeId, LocalDate workDate) {
        var chosen = employeeShifts.findCoveringDate(employeeId, workDate).stream()
                .max(Comparator.comparingInt(es -> priority(es.getAssignmentType())));
        if (chosen.isPresent()) {
            return Optional.of(existingShift(chosen.get().getShiftId()));
        }
        return shifts.findByDefaultShiftTrue();
    }

    private int priority(ShiftAssignmentType type) {
        return switch (type) {
            case DAILY -> 3;
            case WEEKLY -> 2;
            case DEFAULT -> 1;
        };
    }

    private Shift existingShift(UUID shiftId) {
        return shifts.findById(shiftId).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ca làm"));
    }

    private boolean isDuplicateAttendanceViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains("uq_attendance_records_employee_date")) {
                return true;
            }
        }
        return false;
    }
}
