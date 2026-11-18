package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.AttendancePolicyResponse;
import com.company.hrm.attendance.dto.CreateAttendancePolicyRequest;
import com.company.hrm.attendance.dto.UpdateAttendancePolicyRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttendancePolicyService {

    Page<AttendancePolicyResponse> list(Pageable page);

    AttendancePolicyResponse get(UUID id);

    AttendancePolicyResponse getDefault();

    AttendancePolicyResponse create(@Valid CreateAttendancePolicyRequest request, String traceId);

    AttendancePolicyResponse update(UUID id, @Valid UpdateAttendancePolicyRequest request, String traceId);

    void delete(UUID id, String traceId);
}
