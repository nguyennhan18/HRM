package com.company.hrm.employee.dto;

import com.company.hrm.employee.entity.Gender;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 200) String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        @Email @Size(max = 254) String personalEmail,
        @Email @Size(max = 254) String companyEmail,
        @Size(max = 30) String phone,
        @Size(max = 1000) String address,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String cccd,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String bankAccountNumber,
        UUID departmentId,
        UUID positionId,
        UUID managerId,
        LocalDate startDate) {
    public UpdateEmployeeRequest {
        fullName = fullName == null ? null : fullName.trim();
        companyEmail = normalizeEmail(companyEmail);
        personalEmail = normalizeEmail(personalEmail);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // Validation exceptions may include the request's string representation.
    @Override
    public String toString() {
        return "UpdateEmployeeRequest[redacted]";
    }
}
