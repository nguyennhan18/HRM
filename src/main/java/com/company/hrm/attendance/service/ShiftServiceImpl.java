package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateShiftRequest;
import com.company.hrm.attendance.dto.ShiftResponse;
import com.company.hrm.attendance.dto.UpdateShiftRequest;
import com.company.hrm.attendance.entity.Shift;
import com.company.hrm.attendance.mapper.ShiftMapper;
import com.company.hrm.attendance.repository.EmployeeShiftRepository;
import com.company.hrm.attendance.repository.ShiftRepository;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ShiftServiceImpl implements ShiftService {

    private final ShiftRepository shifts;
    private final EmployeeShiftRepository employeeShifts;
    private final ShiftMapper mapper;
    private final AuditService audit;

    public ShiftServiceImpl(
            ShiftRepository shifts,
            EmployeeShiftRepository employeeShifts,
            ShiftMapper mapper,
            AuditService audit) {
        this.shifts = shifts;
        this.employeeShifts = employeeShifts;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public Page<ShiftResponse> list(Pageable page) {
        return shifts.findAll(AttendancePages.shifts(page)).map(mapper::toResponse);
    }

    @Override
    public ShiftResponse get(UUID id) {
        return mapper.toResponse(existing(id));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse create(CreateShiftRequest request, String traceId) {
        validateShiftTiming(
                request.startTime(),
                request.endTime(),
                request.breakStart(),
                request.breakEnd(),
                request.overnight());
        if (shifts.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Mã ca làm đã tồn tại");
        }
        if (Boolean.TRUE.equals(request.defaultShift())) {
            clearCurrentDefaultShift(null);
        }
        var entity = new Shift(
                request.code(),
                request.name(),
                request.startTime(),
                request.endTime(),
                request.breakStart(),
                request.breakEnd(),
                request.overnight(),
                request.defaultShift());
        shifts.saveAndFlush(entity);
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "Shift", entity.getId(), null, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse update(UUID id, UpdateShiftRequest request, String traceId) {
        validateShiftTiming(
                request.startTime(),
                request.endTime(),
                request.breakStart(),
                request.breakEnd(),
                request.overnight());
        var entity = locked(id);
        if (!entity.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không được thay đổi mã ca sau khi tạo");
        }
        if (entity.isDefaultShift() && Boolean.FALSE.equals(request.defaultShift())) {
            throw new BusinessException(
                    ErrorCode.HRM_CONFLICT,
                    "Không thể bỏ cờ mặc định trực tiếp; hãy đặt một ca khác làm mặc định");
        }
        if (timingChanged(entity, request) && employeeShifts.existsByShiftId(id)) {
            throw new BusinessException(
                    ErrorCode.HRM_CONFLICT,
                    "Không thể thay đổi khung giờ của ca làm đang được phân cho nhân viên");
        }
        var before = mapper.toResponse(entity);
        if (Boolean.TRUE.equals(request.defaultShift()) && !entity.isDefaultShift()) {
            clearCurrentDefaultShift(id);
        }
        entity.update(
                request.name(),
                request.startTime(),
                request.endTime(),
                request.breakStart(),
                request.breakEnd(),
                request.overnight(),
                request.defaultShift());
        shifts.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "Shift", id, before, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id, String traceId) {
        var entity = locked(id);
        if (entity.isDefaultShift()) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không thể xóa ca làm mặc định");
        }
        if (employeeShifts.existsByShiftId(id)) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không thể xóa ca làm đang được phân cho nhân viên");
        }
        var before = mapper.toResponse(entity);
        shifts.delete(entity);
        shifts.flush();
        audit.record("DELETE", "Shift", id, before, null, traceId);
    }

    private void clearCurrentDefaultShift(UUID excludeId) {
        shifts.lockDefaultShift()
                .filter(current -> excludeId == null || !current.getId().equals(excludeId))
                .ifPresent(current -> {
                    current.setDefaultShift(false);
                    shifts.flush();
                });
    }

    private boolean timingChanged(Shift entity, UpdateShiftRequest request) {
        return !entity.getStartTime().equals(request.startTime())
                || !entity.getEndTime().equals(request.endTime())
                || !Objects.equals(entity.getBreakStart(), request.breakStart())
                || !Objects.equals(entity.getBreakEnd(), request.breakEnd())
                || entity.isOvernight() != request.overnight();
    }

    private void validateShiftTiming(
            LocalTime startTime,
            LocalTime endTime,
            LocalTime breakStart,
            LocalTime breakEnd,
            boolean overnight) {
        if (!overnight && !endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.HRM_VALIDATION_ERROR,
                    "Ca làm trong ngày phải có giờ kết thúc sau giờ bắt đầu");
        }
        if (overnight && !endTime.isBefore(startTime)) {
            throw new BusinessException(
                    ErrorCode.HRM_VALIDATION_ERROR,
                    "Ca làm qua nửa đêm phải có giờ kết thúc trước giờ bắt đầu vào ngày hôm sau");
        }
        if ((breakStart == null) != (breakEnd == null)) {
            throw new BusinessException(
                    ErrorCode.HRM_VALIDATION_ERROR,
                    "Giờ bắt đầu và kết thúc nghỉ giữa ca phải cùng có hoặc cùng để trống");
        }
        if (breakStart != null && breakEnd != null) {
            boolean validBreak;
            if (!overnight) {
                validBreak = !breakStart.isBefore(startTime)
                        && !breakEnd.isAfter(endTime)
                        && breakEnd.isAfter(breakStart)
                        && !(breakStart.equals(startTime) && breakEnd.equals(endTime));
            } else {
                boolean beforeMidnight = !breakStart.isBefore(startTime) && breakEnd.isAfter(breakStart);
                boolean afterMidnight = !breakEnd.isAfter(endTime) && breakEnd.isAfter(breakStart);
                boolean crossesMidnight = !breakStart.isBefore(startTime)
                        && !breakEnd.isAfter(endTime)
                        && !(breakStart.equals(startTime) && breakEnd.equals(endTime));
                validBreak = beforeMidnight || afterMidnight || crossesMidnight;
            }
            if (!validBreak) {
                throw new BusinessException(
                        ErrorCode.HRM_VALIDATION_ERROR,
                        "Thời gian nghỉ giữa ca phải nằm trong khung giờ của ca làm");
            }
        }
    }

    private Shift existing(UUID id) {
        return shifts.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ca làm"));
    }

    private Shift locked(UUID id) {
        return shifts.lockById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy ca làm"));
    }
}
