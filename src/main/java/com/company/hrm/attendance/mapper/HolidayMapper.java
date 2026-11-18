package com.company.hrm.attendance.mapper;

import com.company.hrm.attendance.dto.HolidayResponse;
import com.company.hrm.attendance.entity.Holiday;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HolidayMapper {
    HolidayResponse toResponse(Holiday entity);
}
