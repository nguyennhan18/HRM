package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.CreateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.EffectiveEmployeeShiftResponse;
import com.company.hrm.attendance.dto.EmployeeShiftResponse;
import com.company.hrm.attendance.dto.UpdateEmployeeShiftRequest;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmployeeShiftService {

    Page<EmployeeShiftResponse> list(
            UUID employeeId,
            UUID shiftId,
            ShiftAssignmentType assignmentType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable page);

    EmployeeShiftResponse get(UUID id);

    EffectiveEmployeeShiftResponse effective(UUID employeeId, LocalDate workDate);

    EmployeeShiftResponse create(@Valid CreateEmployeeShiftRequest request, String traceId);

    EmployeeShiftResponse update(UUID id, @Valid UpdateEmployeeShiftRequest request, String traceId);

    void delete(UUID id, String traceId);
}
