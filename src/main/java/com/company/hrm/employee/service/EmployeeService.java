package com.company.hrm.employee.service;

import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Employee foundation and scoped listing; onboarding remains separate. */
public interface EmployeeService {
    org.springframework.data.domain.Page<EmployeeResponse> list(String name, UUID departmentId, UUID positionId,
            com.company.hrm.employee.entity.EmployeeStatus status, org.springframework.data.domain.Pageable page);
    EmployeeResponse create(@NotNull @Valid CreateEmployeeRequest request, String traceId);
    EmployeeResponse get(UUID id);
    EmployeeResponse changeManager(UUID id, UUID managerId, String traceId);
}
