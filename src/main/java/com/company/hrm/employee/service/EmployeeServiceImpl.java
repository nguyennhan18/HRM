package com.company.hrm.employee.service;

import com.company.hrm.auth.service.AuthService;
import com.company.hrm.auth.service.AuthorizationService;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.common.security.SensitiveFieldMasker;
import com.company.hrm.contract.service.ContractService;
import com.company.hrm.employee.dto.*;
import com.company.hrm.employee.entity.Employee;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.mapper.EmployeeMapper;
import com.company.hrm.employee.repository.*;
import com.company.hrm.organization.service.DepartmentService;
import com.company.hrm.organization.service.PositionService;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
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
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;
    private final SensitiveIdentifierHasher hasher;
    private final EmployeeMapper mapper;
    private final DepartmentService departments;
    private final PositionService positions;
    private final ContractService contracts;
    private final AuthService auth;
    private final AuthorizationService authz;
    private final SensitiveFieldMasker masker;
    private final AuditService audit;
    private final Clock clock;

    public EmployeeServiceImpl(EmployeeRepository employees, EmployeeWriteLock lock,
            SensitiveIdentifierHasher hasher, EmployeeMapper mapper, DepartmentService departments,
            PositionService positions, ContractService contracts, AuthService auth,
            AuthorizationService authz, SensitiveFieldMasker masker,
            AuditService audit, Clock clock) {
        this.employees = employees; this.lock = lock; this.hasher = hasher; this.mapper = mapper;
        this.departments = departments; this.positions = positions; this.contracts = contracts;
        this.auth = auth; this.authz = authz; this.masker = masker; this.audit = audit; this.clock = clock;
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
        return mask(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request, String traceId) {
        var cccd = hasher.protect("cccd", request.cccd());
        var bank = hasher.protect("bank-account", request.bankAccountNumber());
        lock.acquire();
        var employee = existing(id);
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT,
                    "Không thể cập nhật nhân viên đang ở trạng thái " + employee.getStatus());
        }
        if (employee.getStatus() != EmployeeStatus.DRAFT) {
            if (request.companyEmail() == null || request.departmentId() == null
                    || request.positionId() == null || request.startDate() == null) {
                throw new BusinessException(ErrorCode.HRM_UNPROCESSABLE_ENTITY,
                        "Không thể xóa trường bắt buộc của nhân viên đã kích hoạt");
            }
        }
        if (request.departmentId() != null) departments.get(request.departmentId());
        if (request.positionId() != null) positions.get(request.positionId());
        validateManager(id, request.managerId());
        if ((request.companyEmail() != null && employees.existsByCompanyEmailAndIdNot(request.companyEmail(), id))
                || (cccd.hash() != null && employees.existsByCccdHashAndIdNot(cccd.hash(), id))) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Email công ty hoặc CCCD đã được sử dụng");
        }
        var before = mapper.toResponse(employee);
        employee.updateProfile(request.fullName(), request.dateOfBirth(), request.gender(),
                request.personalEmail(), request.companyEmail(), request.phone(), request.address(),
                cccd.hash(), cccd.lastFour(), bank.hash(), bank.lastFour(), request.departmentId(),
                request.positionId(), request.managerId(), request.startDate());
        employees.flush();
        var after = mapper.toResponse(employee);
        audit.record("UPDATE", "Employee", id, before, after, traceId);
        return mask(after);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse activate(UUID id, String traceId) {
        lock.acquire();
        var employee = existing(id);
        if (employee.getStatus() != EmployeeStatus.DRAFT) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT,
                    "Không thể kích hoạt nhân viên đang ở trạng thái " + employee.getStatus());
        }
        var missing = new java.util.ArrayList<String>();
        if (employee.getFullName() == null || employee.getFullName().isBlank()) missing.add("fullName");
        if (employee.getCompanyEmail() == null || employee.getCompanyEmail().isBlank()) missing.add("companyEmail");
        if (employee.getDepartmentId() == null) missing.add("departmentId");
        if (employee.getPositionId() == null) missing.add("positionId");
        if (employee.getStartDate() == null) missing.add("startDate");
        if (employee.getCccdHash() == null || employee.getCccdLastFour() == null) missing.add("cccd");
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.HRM_UNPROCESSABLE_ENTITY,
                    "Hồ sơ thiếu trường bắt buộc khi kích hoạt: " + String.join(", ", missing));
        }
        departments.get(employee.getDepartmentId());
        positions.get(employee.getPositionId());
        validateManager(employee.getId(), employee.getManagerId());
        var before = mapper.toResponse(employee);
        UUID userId = auth.provisionEmployeeUser(employee.getCompanyEmail(), traceId);
        employee.activate(userId);
        employees.flush();
        var after = mapper.toResponse(employee);
        audit.record("ACTIVATE", "Employee", id, before, after, traceId);
        return mask(after);
    }

    @PreAuthorize("@authz.canViewEmployee(#id)")
    public EmployeeResponse get(UUID id) { return mask(mapper.toResponse(existing(id))); }

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
        return mask(after);
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
        var canViewSensitive = authz.sensitiveDataFilter();
        return employees.findAll(EmployeeSpecification.matching(name, departmentId, positionId, status, visible), checked)
                .map(e -> mapper.toResponse(e).masked(masker, canViewSensitive.test(e.getId())));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse terminate(UUID id, TerminateEmployeeRequest request, String traceId) {
        lock.acquire();
        var employee = existing(id);
        if (employee.getStatus() != EmployeeStatus.ACTIVE && employee.getStatus() != EmployeeStatus.ON_LEAVE_LONG) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT,
                    "Không thể thôi việc nhân viên đang ở trạng thái " + employee.getStatus());
        }
        if (employee.getStartDate() != null && request.terminationDate().isBefore(employee.getStartDate())) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR,
                    "Ngày thôi việc không được trước ngày bắt đầu làm việc");
        }
        var before = mapper.toResponse(employee);
        employee.terminate(request.terminationDate(), request.reason());
        employees.flush();
        contracts.terminateActiveForEmployee(id, request.terminationDate(), traceId);
        if (employee.getUserId() != null) {
            auth.deactivateUser(employee.getUserId(), traceId);
        }
        var after = mapper.toResponse(employee);
        audit.record("TERMINATE", "Employee", id, before, after, traceId);
        return mask(after);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id, String traceId) {
        lock.acquire();
        var employee = existing(id);
        long subordinates = employees.countByManagerIdAndDeletedAtIsNull(id);
        if (subordinates > 0) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT,
                    "Không thể xóa nhân viên đang có " + subordinates + " cấp dưới trực tiếp chưa xóa");
        }
        var before = mapper.toResponse(employee);
        employee.markDeleted(clock.instant());
        employees.flush();
        if (employee.getUserId() != null) {
            auth.deactivateUser(employee.getUserId(), traceId);
        }
        audit.record("DELETE", "Employee", id, before,
                Map.of("id", id, "status", employee.getStatus(), "deletedAt", employee.getDeletedAt()), traceId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public Page<EmployeeHistoryResponse> history(UUID id, Pageable page) {
        var checked = EmployeePages.checkedHistory(page);
        if (!employees.existsById(id)) {
            throw new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên");
        }
        return audit.findEntityHistory("Employee", id, checked).map(mapper::toHistoryResponse);
    }

    @PreAuthorize("isAuthenticated()")
    public void validateAttendanceEligibility(UUID id) {
        var employee = existing(id);
        if (!employee.canCheckAttendance()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE,
                    "Nhân viên đã thôi việc không được chấm công");
        }
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

    private EmployeeResponse mask(EmployeeResponse response) {
        return response.masked(masker, authz.canViewSensitiveData(response.id()));
    }

    private Employee existing(UUID id) {
        return employees.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên"));
    }
}
