package com.company.hrm.employee.mapper;

import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EmployeeMapper {
    EmployeeResponse toResponse(Employee employee);
}
