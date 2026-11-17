package com.company.hrm.contract.dto;
import com.company.hrm.contract.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record ContractResponse(UUID id, UUID employeeId, ContractType type, LocalDate startDate,
        LocalDate endDate, BigDecimal baseSalary, BigDecimal allowance, ContractStatus status,
        boolean expired, Long version) {
    @Override public String toString() { return "ContractResponse[id=" + id + ", salary=redacted]"; }
}
