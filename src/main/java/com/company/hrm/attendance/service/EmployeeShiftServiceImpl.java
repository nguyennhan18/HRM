package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.EffectiveEmployeeShiftResponse;
import com.company.hrm.attendance.dto.EmployeeShiftResponse;
import com.company.hrm.attendance.dto.UpdateEmployeeShiftRequest;
import com.company.hrm.attendance.entity.EmployeeShift;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import com.company.hrm.attendance.mapper.EmployeeShiftMapper;
import com.company.hrm.attendance.mapper.ShiftMapper;
import com.company.hrm.attendance.repository.EmployeeShiftRepository;
import com.company.hrm.attendance.repository.EmployeeShiftSpecification;
import com.company.hrm.attendance.repository.ShiftRepository;
import com.company.hrm.auth.service.AuthorizationService;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class EmployeeShiftServiceImpl implements EmployeeShiftService {

    private static final UUID NO_EXCLUDED_ID = new UUID(0L, 0L);

    private final EmployeeShiftRepository employeeShifts;
    private final ShiftRepository shifts;
    private final AttendanceEmployeeGuard employees;
    private final AuthorizationService authz;
    private final EmployeeShiftMapper mapper;
    private final ShiftMapper shiftMapper;
    private final AuditService audit;

    public EmployeeShiftServiceImpl(
            EmployeeShiftRepository employeeShifts,
            ShiftRepository shifts,
            AttendanceEmployeeGuard employees,
            AuthorizationService authz,
            EmployeeShiftMapper mapper,
            ShiftMapper shiftMapper,
            AuditService audit) {
        this.employeeShifts = employeeShifts;
        this.shifts = shifts;
        this.employees = employees;
        this.authz = authz;
        this.mapper = mapper;
        this.shiftMapper = shiftMapper;
        this.audit = audit;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<EmployeeShiftResponse> list(
            UUID employeeId,
            UUID shiftId,
            ShiftAssignmentType assignmentType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable page) {
        var checked = AttendancePages.employeeShifts(page);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Ngày kết thúc lọc không được trước ngày bắt đầu");
        }
        if (employeeId != null && !authz.canViewEmployee(employeeId)) {
            throw new AccessDeniedException("Không có quyền xem phân ca của nhân viên này");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        List<UUID> visible = null;
        if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_HR")) {
            try {
                visible = employees.visibleEmployeeIds(
                        UUID.fromString(authentication.getName()),
                        roles.contains("ROLE_MANAGER"),
                        roles.contains("ROLE_EMPLOYEE"));
            } catch (IllegalArgumentException ex) {
                visible = List.of();
            }
        }
        return employeeShifts.findAll(
                EmployeeShiftSpecification.matching(employeeId, shiftId, assignmentType, fromDate, toDate, visible),
                checked)
                .map(mapper::toResponse);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public EmployeeShiftResponse get(UUID id) {
        var entity = existing(id);
        if (!authz.canViewEmployee(entity.getEmployeeId())) {
            throw new AccessDeniedException("Không có quyền xem phân ca của nhân viên này");
        }
        return mapper.toResponse(entity);
    }

    @Override
    @PreAuthorize("@authz.canViewEmployee(#employeeId)")
    public EffectiveEmployeeShiftResponse effective(UUID employeeId, LocalDate workDate) {
        if (workDate == null) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Ngày làm việc không được để trống");
        }
        employees.requireExisting(employeeId);
        var candidates = employeeShifts.findCoveringDate(employeeId, workDate);
        var chosen = candidates.stream()
                .max(Comparator.comparingInt(es -> priority(es.getAssignmentType())));
        if (chosen.isPresent()) {
            var assignment = chosen.get();
            var shift = existingShift(assignment.getShiftId());
            return new EffectiveEmployeeShiftResponse(
                    employeeId,
                    workDate,
                    assignment.getId(),
                    assignment.getAssignmentType(),
                    false,
                    shiftMapper.toResponse(shift));
        }
        var companyDefault = shifts.findByDefaultShiftTrue().orElseThrow(() ->
                new BusinessException(
                        ErrorCode.HRM_RESOURCE_NOT_FOUND,
                        "Chưa có phân ca hoặc ca mặc định toàn công ty cho ngày này"));
        return new EffectiveEmployeeShiftResponse(
                employeeId,
                workDate,
                null,
                null,
                true,
                shiftMapper.toResponse(companyDefault));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeShiftResponse create(CreateEmployeeShiftRequest request, String traceId) {
        validateAssignmentDates(request.assignmentType(), request.startDate(), request.endDate());
        employees.lockAndValidate(request.employeeId());
        existingShift(request.shiftId());
        checkOverlap(request.employeeId(), request.assignmentType(), NO_EXCLUDED_ID, request.startDate(), request.endDate());
        var entity = new EmployeeShift(
                request.employeeId(),
                request.shiftId(),
                request.assignmentType(),
                request.startDate(),
                request.endDate());
        employeeShifts.saveAndFlush(entity);
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "EmployeeShift", entity.getId(), null, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeShiftResponse update(UUID id, UpdateEmployeeShiftRequest request, String traceId) {
        validateAssignmentDates(request.assignmentType(), request.startDate(), request.endDate());
        var entity = existing(id);
        employees.lockAndValidate(entity.getEmployeeId());
        if (!entity.getEmployeeId().equals(request.employeeId())) {
            employees.lockAndValidate(request.employeeId());
        }
        existingShift(request.shiftId());
        checkOverlap(request.employeeId(), request.assignmentType(), id, request.startDate(), request.endDate());
        var before = mapper.toResponse(entity);
        entity.update(
                request.employeeId(),
                request.shiftId(),
                request.assignmentType(),
                request.startDate(),
                request.endDate());
        employeeShifts.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "EmployeeShift", id, before, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id, String traceId) {
        var entity = existing(id);
        employees.lockAndValidate(entity.getEmployeeId());
        var before = mapper.toResponse(entity);
        employeeShifts.delete(entity);
        employeeShifts.flush();
        audit.record("DELETE", "EmployeeShift", id, before, null, traceId);
    }

    private void checkOverlap(
            UUID employeeId,
            ShiftAssignmentType assignmentType,
            UUID excludeId,
            LocalDate startDate,
            LocalDate endDate) {
        if (employeeShifts.overlaps(employeeId, assignmentType.name(), excludeId, startDate, endDate)) {
            throw new BusinessException(ErrorCode.SHIFT_ASSIGNMENT_OVERLAP, "Phân ca nhân viên bị trùng khoảng thời gian");
        }
    }

    private void validateAssignmentDates(
            ShiftAssignmentType assignmentType,
            LocalDate startDate,
            LocalDate endDate) {
        switch (assignmentType) {
            case DEFAULT -> {
                if (endDate != null && endDate.isBefore(startDate)) {
                    throw new BusinessException(
                            ErrorCode.HRM_VALIDATION_ERROR,
                            "Ngày kết thúc phân ca mặc định không được trước ngày bắt đầu");
                }
            }
            case WEEKLY -> {
                if (endDate == null || endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) > 6) {
                    throw new BusinessException(
                            ErrorCode.HRM_VALIDATION_ERROR,
                            "Phân ca theo tuần bắt buộc có ngày kết thúc trong phạm vi tối đa 7 ngày kể từ ngày bắt đầu");
                }
            }
            case DAILY -> {
                if (endDate == null || !endDate.equals(startDate)) {
                    throw new BusinessException(
                            ErrorCode.HRM_VALIDATION_ERROR,
                            "Phân ca theo ngày bắt buộc có ngày kết thúc trùng ngày bắt đầu");
                }
            }
        }
    }

    private int priority(ShiftAssignmentType type) {
        return switch (type) {
            case DAILY -> 3;
            case WEEKLY -> 2;
            case DEFAULT -> 1;
        };
    }

    private EmployeeShift existing(UUID id) {
        return employeeShifts.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy phân ca nhân viên"));
    }

    private Shift existingShift(UUID shiftId) {
        return shifts.findById(shiftId).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ca làm"));
    }
}
