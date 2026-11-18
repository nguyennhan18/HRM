package com.company.hrm.attendance.service;

import com.company.hrm.attendance.dto.AttendanceRecordResponse;
import com.company.hrm.attendance.dto.CheckInRequest;
import com.company.hrm.attendance.dto.CheckOutRequest;
import com.company.hrm.attendance.entity.AttendanceSource;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

public interface AttendanceService {

    AttendanceRecordResponse checkIn(@Valid CheckInRequest request, String traceId);

    AttendanceRecordResponse checkOut(@Valid CheckOutRequest request, String traceId);

    AttendanceRecordResponse checkInAt(UUID employeeId, Instant punchTime, AttendanceSource source, String traceId);

    AttendanceRecordResponse checkOutAt(UUID employeeId, Instant punchTime, AttendanceSource source, String traceId);
}
