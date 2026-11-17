package com.company.hrm.employee.service;
import com.company.hrm.employee.dto.*;
import com.company.hrm.employee.entity.DocumentType;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
public interface EmployeeDocumentService {
    DocumentResponse upload(UUID employeeId, DocumentType type, MultipartFile file, String traceId);
    DocumentDownload download(UUID employeeId, UUID documentId);
}
