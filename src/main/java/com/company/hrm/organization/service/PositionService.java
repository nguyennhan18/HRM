package com.company.hrm.organization.service;
import com.company.hrm.organization.dto.*;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
public interface PositionService {
    Page<PositionResponse> list(Pageable page);
    PositionResponse get(UUID id);
    PositionResponse create(@Valid CreatePositionRequest request, String traceId);
    PositionResponse update(UUID id, @Valid UpdatePositionRequest request, String traceId);
    void delete(UUID id, String traceId);
}
