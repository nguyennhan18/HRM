package com.company.hrm.employee.service;

import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.employee.dto.*;
import com.company.hrm.employee.entity.Employee;
import com.company.hrm.employee.mapper.EmployeeMapper;
import com.company.hrm.employee.repository.*;
import com.company.hrm.organization.service.DepartmentService;
import com.company.hrm.organization.service.PositionService;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;
    private final SensitiveIdentifierHasher hasher;
    private final EmployeeMapper mapper;
    private final DepartmentService departments;
    private final PositionService positions;
    private final AuditService audit;

    public EmployeeServiceImpl(EmployeeRepository employees, EmployeeWriteLock lock,
            SensitiveIdentifierHasher hasher, EmployeeMapper mapper, DepartmentService departments,
            PositionService positions, AuditService audit) {
        this.employees = employees; this.lock = lock; this.hasher = hasher; this.mapper = mapper;
        this.departments = departments; this.positions = positions; this.audit = audit;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse create(CreateEmployeeRequest request, String traceId) {
        var cccd = hasher.protect("cccd", request.cccd());
        var bank = hasher.protect("bank-account", request.bankAccountNumber());
        lock.acquire();
        if (request.departmentId() != null) departments.get(request.departmentId());
        if (request.positionId() != null) positions.get(request.positionId());
        validateManager(null, request.managerId());
        if ((request.companyEmail() != null && employees.existsByCompanyEmail(request.companyEmail()))
                || (cccd.hash() != null && employees.existsByCccdHash(cccd.hash()))) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Email công ty hoặc CCCD đã được sử dụng");
        }
        var entity = new Employee(nextCode(), request.fullName(), request.dateOfBirth(), request.gender(),
                request.personalEmail(), request.companyEmail(), request.phone(), request.address(),
                cccd.hash(), cccd.lastFour(), bank.hash(), bank.lastFour(), request.departmentId(),
                request.positionId(), request.managerId(), request.startDate());
        employees.saveAndFlush(entity);
        var response = mapper.toResponse(entity);
        audit.record("CREATE", "Employee", entity.getId(), null, response, traceId);
        return response;
    }

    @PreAuthorize("@authz.canViewEmployee(#id)")
    public EmployeeResponse get(UUID id) { return mapper.toResponse(existing(id)); }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse changeManager(UUID id, UUID managerId, String traceId) {
        lock.acquire();
        var employee = existing(id);
        validateManager(id, managerId);
        var before = mapper.toResponse(employee);
        employee.changeManager(managerId);
        employees.flush();
        var after = mapper.toResponse(employee);
        audit.record("UPDATE_MANAGER", "Employee", id, before, after, traceId);
        return after;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public org.springframework.data.domain.Page<EmployeeResponse> list(String name, UUID departmentId,
            UUID positionId, com.company.hrm.employee.entity.EmployeeStatus status,
            org.springframework.data.domain.Pageable page) {
        var checked = EmployeePages.checked(page);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        java.util.List<UUID> visible = null;
        if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_HR")) {
            try {
                visible = employees.visibleIds(UUID.fromString(authentication.getName()),
                        roles.contains("ROLE_MANAGER"), roles.contains("ROLE_EMPLOYEE"));
            } catch (IllegalArgumentException ex) {
                visible = java.util.List.of();
            }
        }
        return employees.findAll(EmployeeSpecification.matching(name, departmentId, positionId, status, visible), checked)
                .map(mapper::toResponse);
    }

    private String nextCode() {
        try {
            long value = lock.nextCode();
            return "NV" + String.format(Locale.ROOT, "%06d", value);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Không thể cấp mã nhân viên từ sequence");
        }
    }

    private void validateManager(UUID employeeId, UUID managerId) {
        var visited = new HashSet<UUID>();
        if (employeeId != null) visited.add(employeeId);
        UUID current = managerId;
        while (current != null) {
            if (!visited.add(current)) {
                throw new BusinessException(ErrorCode.HRM_CONFLICT, "Quan hệ quản lý tạo vòng lặp");
            }
            current = existing(current).getManagerId();
        }
    }

    private Employee existing(UUID id) {
        return employees.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên"));
    }
}
