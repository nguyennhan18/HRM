package com.company.hrm.organization.service;

import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.entity.Department;
import com.company.hrm.organization.mapper.DepartmentMapper;
import com.company.hrm.organization.repository.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class DepartmentServiceImpl implements DepartmentService {
    private final DepartmentRepository repository;
    private final DepartmentMapper mapper;
    private final AuditService audit;
    private final EmployeeAssignmentGuard employeeGuard;
    private final DepartmentTreeLock tree;
    private final Clock clock;
    public DepartmentServiceImpl(DepartmentRepository repository, DepartmentMapper mapper, AuditService audit, DepartmentTreeLock tree, Clock clock, EmployeeAssignmentGuard employeeGuard) {
        this.repository = repository; this.mapper = mapper; this.audit = audit; this.employeeGuard = employeeGuard;
        this.tree = tree; this.clock = clock;
    }
    public Page<DepartmentResponse> list(UUID parentId, boolean roots, Pageable page) {
        var checked = OrganizationPages.checked(page);
        if (roots && parentId != null) throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Không thể lọc cha và gốc cùng lúc");
        if (parentId != null) active(parentId);
        return (roots || parentId != null ? repository.findByParentIdAndDeletedAtIsNull(parentId, checked)
                : repository.findByDeletedAtIsNull(checked)).map(mapper::toResponse);
    }
    public DepartmentResponse get(UUID id) { return mapper.toResponse(active(id)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public DepartmentResponse create(CreateDepartmentRequest request, String traceId) {
        tree.lockAndValidate(null, request.parentId());
        if (request.parentId() != null) active(request.parentId());
        var entity = repository.saveAndFlush(new Department(request.code(), request.name(), request.parentId()));
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "Department", entity.getId(), null, after, traceId);
        return after;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public DepartmentResponse update(UUID id, UpdateDepartmentRequest request, String traceId) {
        tree.lockAndValidate(id, request.parentId());
        var entity = active(id);
        if (!entity.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không được thay đổi mã sau khi tạo");
        }
        if (request.parentId() != null) active(request.parentId());
        var before = mapper.toResponse(entity);
        entity.setName(request.name());
        entity.setParentId(request.parentId());
        repository.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "Department", id, before, after, traceId);
        return after;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void delete(UUID id, String traceId) {
        employeeGuard.beforeDepartmentDelete(id);
        tree.lockAndValidate(id, null);
        var entity = active(id);
        if (repository.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Phòng ban còn phòng ban con chưa xóa");
        }
        var before = mapper.toResponse(entity);
        entity.delete(clock.instant());
        repository.flush();
        audit.record("DELETE", "Department", id, before, mapper.toResponse(entity), traceId);
    }
    private Department active(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy phòng ban"));
    }
}
