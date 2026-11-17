package com.company.hrm.employee.service;

import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** T2.1 foundation only; onboarding and REST CRUD belong to T2.2/T2.3. */
public interface EmployeeService {
    EmployeeResponse create(@NotNull @Valid CreateEmployeeRequest request, String traceId);
    EmployeeResponse get(UUID id);
    EmployeeResponse changeManager(UUID id, UUID managerId, String traceId);
}
