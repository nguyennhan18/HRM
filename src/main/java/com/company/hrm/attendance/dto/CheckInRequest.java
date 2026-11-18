package com.company.hrm.attendance.dto;

import com.company.hrm.attendance.entity.AttendanceSource;
import io.swagger.v3.oas.annotations.media.Schema;

public record CheckInRequest(
        @Schema(example = "WEB", nullable = true, description = "Nguồn chấm công; mặc định WEB, hỗ trợ mở rộng QR/GPS")
        AttendanceSource source) {

    public AttendanceSource effectiveSource() {
        return source != null ? source : AttendanceSource.WEB;
    }
}
