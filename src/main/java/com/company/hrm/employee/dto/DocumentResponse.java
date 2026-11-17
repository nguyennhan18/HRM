package com.company.hrm.employee.dto;
import com.company.hrm.employee.entity.DocumentType;
import java.util.UUID;
public record DocumentResponse(UUID id, UUID employeeId, DocumentType type, String originalName,
                               long size, String contentType, String checksum) { }
