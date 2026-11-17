package com.company.hrm.organization.mapper;
import com.company.hrm.organization.entity.Department;
import com.company.hrm.organization.dto.DepartmentResponse;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface DepartmentMapper {
    DepartmentResponse toResponse(Department entity);
}
