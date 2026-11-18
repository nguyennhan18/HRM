package com.company.hrm.attendance.mapper;

import com.company.hrm.attendance.dto.ShiftResponse;
import com.company.hrm.attendance.entity.Shift;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShiftMapper {
    ShiftResponse toResponse(Shift entity);
}
