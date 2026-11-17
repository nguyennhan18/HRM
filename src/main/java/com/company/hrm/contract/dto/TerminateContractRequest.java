package com.company.hrm.contract.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public record TerminateContractRequest(@NotNull(message = "Trường này là bắt buộc") LocalDate endDate) {}
