package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.AttendancePolicyResponse;
import com.company.hrm.attendance.dto.CreateAttendancePolicyRequest;
import com.company.hrm.attendance.dto.UpdateAttendancePolicyRequest;
import com.company.hrm.attendance.entity.AttendancePolicy;
import com.company.hrm.attendance.entity.RoundingMode;
import com.company.hrm.attendance.mapper.AttendancePolicyMapper;
import com.company.hrm.attendance.repository.AttendancePolicyRepository;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
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
public class AttendancePolicyServiceImpl implements AttendancePolicyService {

    private final AttendancePolicyRepository policies;
    private final AttendancePolicyMapper mapper;
    private final AuditService audit;

    public AttendancePolicyServiceImpl(
            AttendancePolicyRepository policies,
            AttendancePolicyMapper mapper,
            AuditService audit) {
        this.policies = policies;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public Page<AttendancePolicyResponse> list(Pageable page) {
        return policies.findAll(AttendancePages.policies(page)).map(mapper::toResponse);
    }

    @Override
    public AttendancePolicyResponse get(UUID id) {
        return mapper.toResponse(existing(id));
    }

    @Override
    public AttendancePolicyResponse getDefault() {
        return policies.findByDefaultPolicyTrue()
                .map(mapper::toResponse)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.HRM_RESOURCE_NOT_FOUND, "Chưa cấu hình chính sách chấm công mặc định"));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendancePolicyResponse create(CreateAttendancePolicyRequest request, String traceId) {
        validatePolicy(
                request.roundingMode(),
                request.roundingStepMinutes(),
                request.halfDayThresholdMinutes(),
                request.minFullDayMinutes());
        if (policies.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Mã chính sách chấm công đã tồn tại");
        }
        if (Boolean.TRUE.equals(request.defaultPolicy())) {
            clearCurrentDefaultPolicy(null);
        }
        var entity = new AttendancePolicy(
                request.code(),
                request.name(),
                request.lateGraceMinutes(),
                request.earlyLeaveGraceMinutes(),
                request.roundingStepMinutes(),
                request.roundingMode(),
                request.halfDayThresholdMinutes(),
                request.minFullDayMinutes(),
                request.weekdayOvertimeRate(),
                request.weekendOvertimeRate(),
                request.holidayOvertimeRate(),
                request.defaultPolicy());
        policies.saveAndFlush(entity);
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "AttendancePolicy", entity.getId(), null, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendancePolicyResponse update(UUID id, UpdateAttendancePolicyRequest request, String traceId) {
        validatePolicy(
                request.roundingMode(),
                request.roundingStepMinutes(),
                request.halfDayThresholdMinutes(),
                request.minFullDayMinutes());
        var entity = locked(id);
        if (!entity.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không được thay đổi mã chính sách sau khi tạo");
        }
        if (entity.isDefaultPolicy() && Boolean.FALSE.equals(request.defaultPolicy())) {
            throw new BusinessException(
                    ErrorCode.HRM_CONFLICT,
                    "Không thể bỏ cờ mặc định trực tiếp; hãy đặt một chính sách khác làm mặc định");
        }
        var before = mapper.toResponse(entity);
        if (Boolean.TRUE.equals(request.defaultPolicy()) && !entity.isDefaultPolicy()) {
            clearCurrentDefaultPolicy(id);
        }
        entity.update(
                request.name(),
                request.lateGraceMinutes(),
                request.earlyLeaveGraceMinutes(),
                request.roundingStepMinutes(),
                request.roundingMode(),
                request.halfDayThresholdMinutes(),
                request.minFullDayMinutes(),
                request.weekdayOvertimeRate(),
                request.weekendOvertimeRate(),
                request.holidayOvertimeRate(),
                request.defaultPolicy());
        policies.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "AttendancePolicy", id, before, after, traceId);
        return after;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id, String traceId) {
        var entity = locked(id);
        if (entity.isDefaultPolicy()) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không thể xóa chính sách chấm công mặc định");
        }
        var before = mapper.toResponse(entity);
        policies.delete(entity);
        policies.flush();
        audit.record("DELETE", "AttendancePolicy", id, before, null, traceId);
    }

    private void clearCurrentDefaultPolicy(UUID excludeId) {
        policies.lockDefaultPolicy()
                .filter(current -> excludeId == null || !current.getId().equals(excludeId))
                .ifPresent(current -> {
                    current.setDefaultPolicy(false);
                    policies.flush();
                });
    }

    private void validatePolicy(
            RoundingMode roundingMode,
            int roundingStepMinutes,
            int halfDayThresholdMinutes,
            int minFullDayMinutes) {
        if (roundingMode != RoundingMode.NONE && roundingStepMinutes <= 0) {
            throw new BusinessException(
                    ErrorCode.HRM_VALIDATION_ERROR,
                    "Bước làm tròn phải lớn hơn 0 khi kiểu làm tròn khác NONE");
        }
        if (minFullDayMinutes < halfDayThresholdMinutes) {
            throw new BusinessException(
                    ErrorCode.HRM_VALIDATION_ERROR,
                    "Số phút tối thiểu đủ công phải lớn hơn hoặc bằng ngưỡng nửa công");
        }
    }

    private AttendancePolicy existing(UUID id) {
        return policies.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy chính sách chấm công"));
    }

    private AttendancePolicy locked(UUID id) {
        return policies.lockById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy chính sách chấm công"));
    }
}
