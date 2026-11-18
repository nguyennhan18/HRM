package com.company.hrm.contract.dto;

import com.company.hrm.common.security.SensitiveFieldMasker;
import com.company.hrm.contract.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID employeeId,
        ContractType type,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(description = "Lương cơ bản (VND/tháng). Trong DB trường này bắt buộc không null; khi response trả null nghĩa là người gọi không có quyền xem lương (ví dụ MANAGER xem hợp đồng cấp dưới).", nullable = true)
        BigDecimal baseSalary,
        @Schema(description = "Phụ cấp (VND/tháng). Trong DB trường này bắt buộc không null; khi response trả null nghĩa là người gọi không có quyền xem lương (ví dụ MANAGER xem hợp đồng cấp dưới).", nullable = true)
        BigDecimal allowance,
        ContractStatus status,
        boolean expired,
        Long version) {

    public ContractResponse masked(SensitiveFieldMasker masker, boolean canViewSalary) {
        if (canViewSalary) {
            return new ContractResponse(
                    id, employeeId, type, startDate, endDate,
                    masker.maskAmount(baseSalary, true),
                    masker.maskAmount(allowance, true),
                    status, expired, version);
        }
        return new ContractResponse(
                id, employeeId, type, startDate, endDate,
                masker.maskAmount(baseSalary, false),
                masker.maskAmount(allowance, false),
                status, expired, version);
    }

    @Override
    public String toString() {
        return "ContractResponse[id=" + id + ", salary=redacted]";
    }
}
