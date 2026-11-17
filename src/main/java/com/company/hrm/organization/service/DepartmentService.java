package com.company.hrm.organization.service;
import com.company.hrm.organization.dto.*;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
public interface DepartmentService {
    Page<DepartmentResponse> list(UUID parentId, boolean roots, Pageable page);
    DepartmentResponse get(UUID id);
    DepartmentResponse create(@Valid CreateDepartmentRequest request, String traceId);
    DepartmentResponse update(UUID id, @Valid UpdateDepartmentRequest request, String traceId);
    void delete(UUID id, String traceId);
}
