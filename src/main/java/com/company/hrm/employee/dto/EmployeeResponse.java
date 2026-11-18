package com.company.hrm.employee.dto;

import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.entity.Gender;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(UUID id, String code, String fullName, LocalDate dateOfBirth,
        Gender gender, String personalEmail, String companyEmail, String phone, String address,
        String cccdLastFour, String bankAccountLastFour, UUID departmentId, UUID positionId,
        UUID managerId, EmployeeStatus status, LocalDate startDate,
        LocalDate terminationDate, String terminationReason) {}
