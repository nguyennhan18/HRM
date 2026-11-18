package com.company.hrm.employee.service;

import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeHistoryResponse;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.dto.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Employee foundation, onboarding, scoped listing, termination, soft deletion and audit history. */
public interface EmployeeService {
    org.springframework.data.domain.Page<EmployeeResponse> list(String name, UUID departmentId, UUID positionId,
            com.company.hrm.employee.entity.EmployeeStatus status, org.springframework.data.domain.Pageable page);
    EmployeeResponse create(@NotNull @Valid CreateEmployeeRequest request, String traceId);
    EmployeeResponse update(@NotNull UUID id, @NotNull @Valid UpdateEmployeeRequest request, String traceId);
    EmployeeResponse activate(@NotNull UUID id, String traceId);
    EmployeeResponse get(UUID id);
    EmployeeResponse changeManager(UUID id, UUID managerId, String traceId);
    EmployeeResponse terminate(@NotNull UUID id, @NotNull @Valid TerminateEmployeeRequest request, String traceId);
    void delete(@NotNull UUID id, String traceId);
    org.springframework.data.domain.Page<EmployeeHistoryResponse> history(
            @NotNull UUID id, org.springframework.data.domain.Pageable page);
    void validateAttendanceEligibility(@NotNull UUID id);
}
