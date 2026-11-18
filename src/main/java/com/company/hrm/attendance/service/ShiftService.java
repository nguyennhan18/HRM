package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateShiftRequest;
import com.company.hrm.attendance.dto.ShiftResponse;
import com.company.hrm.attendance.dto.UpdateShiftRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShiftService {

    Page<ShiftResponse> list(Pageable page);

    ShiftResponse get(UUID id);

    ShiftResponse create(@Valid CreateShiftRequest request, String traceId);

    ShiftResponse update(UUID id, @Valid UpdateShiftRequest request, String traceId);

    void delete(UUID id, String traceId);
}
