package com.company.hrm.attendance.mapper;

import com.company.hrm.attendance.dto.AttendancePolicyResponse;
import com.company.hrm.attendance.entity.AttendancePolicy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttendancePolicyMapper {
    AttendancePolicyResponse toResponse(AttendancePolicy entity);
}
