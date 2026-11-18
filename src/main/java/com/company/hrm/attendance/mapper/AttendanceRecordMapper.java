package com.company.hrm.attendance.mapper;

import com.company.hrm.attendance.dto.AttendanceRecordResponse;
import com.company.hrm.attendance.entity.AttendanceRecord;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttendanceRecordMapper {
    AttendanceRecordResponse toResponse(AttendanceRecord entity);
}
