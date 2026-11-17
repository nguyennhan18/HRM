package com.company.hrm.employee.mapper;
import com.company.hrm.employee.dto.DocumentResponse;
import com.company.hrm.employee.entity.EmployeeDocument;
import org.mapstruct.Mapper;
@Mapper(componentModel="spring")
public interface EmployeeDocumentMapper { DocumentResponse toResponse(EmployeeDocument document); }
