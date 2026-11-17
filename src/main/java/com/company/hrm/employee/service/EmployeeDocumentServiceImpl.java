package com.company.hrm.employee.service;

import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import com.company.hrm.common.storage.FileStorage;
import com.company.hrm.employee.dto.*;
import com.company.hrm.employee.entity.*;
import com.company.hrm.employee.mapper.EmployeeDocumentMapper;
import com.company.hrm.employee.repository.*;
import java.io.IOException;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class EmployeeDocumentServiceImpl implements EmployeeDocumentService {
    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;
    private final EmployeeDocumentRepository documents;
    private final EmployeeDocumentMapper mapper;
    private final FileStorage storage;
    private final AuditService audit;
    private final long maxSize;
    public EmployeeDocumentServiceImpl(EmployeeRepository employees, EmployeeWriteLock lock,
            EmployeeDocumentRepository documents, EmployeeDocumentMapper mapper, FileStorage storage,
            AuditService audit, @Value("${spring.servlet.multipart.max-file-size}") String maxSize) {
        this.employees=employees; this.lock=lock; this.documents=documents; this.mapper=mapper;
        this.storage=storage; this.audit=audit; this.maxSize=DataSize.parse(maxSize).toBytes();
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public DocumentResponse upload(UUID employeeId, DocumentType type, MultipartFile file, String traceId) {
        lock.acquire();
        var employee=employees.findByIdAndDeletedAtIsNull(employeeId).orElseThrow(this::missing);
        if (employee.getStatus()==EmployeeStatus.TERMINATED)
            throw new BusinessException(ErrorCode.DOCUMENT_EMPLOYEE_INELIGIBLE,"Không thể tải lên cho nhân viên đã thôi việc");
        if (type==null || file==null || file.isEmpty())
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_FILE,"Tài liệu không được rỗng");
        if (file.getSize()>maxSize) throw oversized();
        byte[] bytes;
        try (var input=file.getInputStream()) { bytes=input.readNBytes((int)maxSize+1); }
        catch (IOException e) { throw new BusinessException(ErrorCode.DOCUMENT_INVALID_FILE,"Không thể đọc tài liệu"); }
        if (bytes.length>maxSize) throw oversized();
        String mime=DocumentValidator.validate(file.getOriginalFilename(),bytes);
        String name=storage.store(bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status!=STATUS_COMMITTED) storage.delete(name);
            }
        });
        var document=documents.saveAndFlush(new EmployeeDocument(employeeId,type,file.getOriginalFilename(),
                name,bytes.length,mime,checksum(bytes)));
        audit.record("UPLOAD","EmployeeDocument",document.getId(),null,
                Map.of("employeeId",employeeId,"type",type,"size",bytes.length),traceId);
        return mapper.toResponse(document);
    }
    @Override @PreAuthorize("@documentAccess.canRead(#employeeId)")
    public DocumentDownload download(UUID employeeId, UUID documentId) {
        employees.findByIdAndDeletedAtIsNull(employeeId).orElseThrow(this::missing);
        var document=documents.findByIdAndEmployeeId(documentId,employeeId).orElseThrow(this::missing);
        byte[] bytes=storage.read(document.getStoredName());
        if (bytes.length!=document.getSize() || !checksum(bytes).equals(document.getChecksum()))
            throw new BusinessException(ErrorCode.HRM_INTERNAL_ERROR,"Tài liệu không toàn vẹn");
        return new DocumentDownload(document.getOriginalName(),document.getContentType(),bytes);
    }
    private static String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private BusinessException missing() { return new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND,"Không tìm thấy tài liệu hoặc hồ sơ"); }
    private BusinessException oversized() { return new BusinessException(ErrorCode.DOCUMENT_TOO_LARGE,"Tài liệu vượt dung lượng cho phép"); }
}
