package com.company.hrm.attendance.mapper;

import com.company.hrm.attendance.dto.EmployeeShiftResponse;
import com.company.hrm.attendance.entity.EmployeeShift;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmployeeShiftMapper {
    EmployeeShiftResponse toResponse(EmployeeShift entity);
}
