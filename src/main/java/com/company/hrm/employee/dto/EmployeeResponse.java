package com.company.hrm.employee.dto;

import com.company.hrm.common.security.SensitiveFieldMasker;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String code,
        String fullName,
        LocalDate dateOfBirth,
        Gender gender,
        String personalEmail,
        String companyEmail,
        String phone,
        String address,
        @Schema(description = "4 số cuối CCCD (ví dụ 1234) khi có quyền xem dữ liệu nhạy cảm; trả ******1234 khi không có quyền (ví dụ MANAGER xem cấp dưới)", nullable = true)
        String cccdLastFour,
        @Schema(description = "4 số cuối số tài khoản ngân hàng (ví dụ 5678) khi có quyền xem dữ liệu nhạy cảm; trả ******5678 khi không có quyền (ví dụ MANAGER xem cấp dưới)", nullable = true)
        String bankAccountLastFour,
        UUID departmentId,
        UUID positionId,
        UUID managerId,
        EmployeeStatus status,
        LocalDate startDate,
        LocalDate terminationDate,
        String terminationReason) {

    public EmployeeResponse masked(SensitiveFieldMasker masker, boolean canViewSensitive) {
        if (canViewSensitive) {
            return new EmployeeResponse(
                    id, code, fullName, dateOfBirth, gender, personalEmail, companyEmail,
                    phone, address,
                    masker.maskLastFour(cccdLastFour, true),
                    masker.maskLastFour(bankAccountLastFour, true),
                    departmentId, positionId, managerId, status, startDate,
                    terminationDate, terminationReason);
        }
        return new EmployeeResponse(
                id, code, fullName, dateOfBirth, gender, personalEmail, companyEmail,
                phone, address,
                masker.maskLastFour(cccdLastFour, false),
                masker.maskLastFour(bankAccountLastFour, false),
                departmentId, positionId, managerId, status, startDate,
                terminationDate, terminationReason);
    }

    @Override
    public String toString() {
        return "EmployeeResponse[id=" + id + ", code=" + code + ", sensitive=redacted]";
    }
}
