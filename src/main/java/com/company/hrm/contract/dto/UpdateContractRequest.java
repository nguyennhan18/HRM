package com.company.hrm.contract.dto;

import com.company.hrm.contract.entity.ContractType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateContractRequest(
        @NotNull(message = "Trường này là bắt buộc") UUID employeeId, @NotNull(message = "Trường này là bắt buộc") ContractType type, @NotNull(message = "Trường này là bắt buộc") LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "Trường này là bắt buộc") @DecimalMin(value = "0", message = "Số tiền không được âm") @Digits(integer = 18, fraction = 0, message = "Số tiền phải là số nguyên tối đa 18 chữ số") BigDecimal baseSalary,
        @NotNull(message = "Trường này là bắt buộc") @DecimalMin(value = "0", message = "Số tiền không được âm") @Digits(integer = 18, fraction = 0, message = "Số tiền phải là số nguyên tối đa 18 chữ số") BigDecimal allowance) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("Trường yêu cầu không được hỗ trợ");
    }
    @Override public String toString() { return "UpdateContractRequest[redacted]"; }
}
