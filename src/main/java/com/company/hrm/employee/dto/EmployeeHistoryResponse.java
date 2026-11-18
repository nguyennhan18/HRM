package com.company.hrm.employee.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record EmployeeHistoryResponse(
        @Schema(description = "ID bản ghi lịch sử") UUID id,
        @Schema(description = "Hành động thay đổi", example = "TERMINATE") String action,
        @Schema(description = "Người thực hiện", example = "019c9e00-0000-7000-8000-000000000002") String actor,
        @Schema(description = "Dữ liệu trước thay đổi") JsonNode beforeJson,
        @Schema(description = "Dữ liệu sau thay đổi") JsonNode afterJson,
        @Schema(description = "Thời điểm thực hiện (UTC)") Instant createdAt,
        @Schema(description = "Mã truy vết request") String traceId) {
    @Override
    public String toString() {
        return "EmployeeHistoryResponse[id=" + id + ", action=" + action + "]";
    }
}
