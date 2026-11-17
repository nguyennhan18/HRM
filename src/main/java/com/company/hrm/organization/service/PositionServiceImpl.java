package com.company.hrm.organization.service;

import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.entity.Position;
import com.company.hrm.organization.mapper.PositionMapper;
import com.company.hrm.organization.repository.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class PositionServiceImpl implements PositionService {
    private final PositionRepository repository;
    private final PositionMapper mapper;
    private final AuditService audit;
    public PositionServiceImpl(PositionRepository repository, PositionMapper mapper, AuditService audit) {
        this.repository = repository; this.mapper = mapper; this.audit = audit;
    }
    public Page<PositionResponse> list(Pageable page) {
        return repository.findAll(OrganizationPages.checked(page)).map(mapper::toResponse);
    }
    public PositionResponse get(UUID id) { return mapper.toResponse(active(id)); }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public PositionResponse create(CreatePositionRequest request, String traceId) {
        var entity = repository.saveAndFlush(new Position(request.code(), request.name()));
        var after = mapper.toResponse(entity);
        audit.record("CREATE", "Position", entity.getId(), null, after, traceId);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public PositionResponse update(UUID id, UpdatePositionRequest request, String traceId) {
        var entity = locked(id);
        if (!entity.getCode().equals(request.code())) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không được thay đổi mã sau khi tạo");
        }
        var before = mapper.toResponse(entity);
        entity.setName(request.name());
        repository.flush();
        var after = mapper.toResponse(entity);
        audit.record("UPDATE", "Position", id, before, after, traceId);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void delete(UUID id, String traceId) {
        var entity = locked(id);
        var before = mapper.toResponse(entity);
        repository.delete(entity);
        repository.flush();
        audit.record("DELETE", "Position", id, before, null, traceId);
    }
    private Position active(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy chức danh"));
    }
    private Position locked(UUID id) {
        return repository.lockById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy chức danh"));
    }
}
