package com.company.hrm.organization.mapper;
import com.company.hrm.organization.entity.Position;
import com.company.hrm.organization.dto.PositionResponse;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface PositionMapper {
    PositionResponse toResponse(Position entity);
}
