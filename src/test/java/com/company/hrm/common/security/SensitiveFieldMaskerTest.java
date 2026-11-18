package com.company.hrm.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.hrm.contract.dto.ContractResponse;
import com.company.hrm.contract.dto.CreateContractRequest;
import com.company.hrm.contract.dto.UpdateContractRequest;
import com.company.hrm.contract.entity.Contract;
import com.company.hrm.contract.entity.ContractStatus;
import com.company.hrm.contract.entity.ContractType;
import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeHistoryResponse;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.entity.Employee;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.entity.Gender;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SensitiveFieldMaskerTest {

    private final SensitiveFieldMasker masker = new SensitiveFieldMasker();

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                "1234, true, 1234",
                "1234, false, ******1234",
                "0042, true, 0042",
                "0042, false, ******0042",
                "******1234, true, 1234",
                "******1234, false, ******1234",
                "NULL, true, NULL",
                "NULL, false, NULL"
            })
    void should_mask_or_preserve_last_four_according_to_permission(
            String input, boolean canViewSensitive, String expected) {
        assertThat(masker.maskLastFour(input, canViewSensitive)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                "25000000, true, 25000000",
                "25000000, false, NULL",
                "0, true, 0",
                "0, false, NULL",
                "NULL, true, NULL",
                "NULL, false, NULL"
            })
    void should_mask_or_preserve_monetary_amount_according_to_permission(
            BigDecimal input, boolean canViewSensitive, BigDecimal expected) {
        assertThat(masker.maskAmount(input, canViewSensitive)).isEqualTo(expected);
    }

    @Test
    void should_mask_employee_and_contract_responses_and_redact_to_string() {
        UUID empId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        var employeeResponse = new EmployeeResponse(
                empId, "NV000001", "Nguyễn Văn A", LocalDate.of(1995, 5, 10),
                Gender.MALE, "personal@example.com", "staff@example.com", "0901234567", "Hà Nội",
                "1234", "5678", UUID.randomUUID(), UUID.randomUUID(), null,
                EmployeeStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null);

        var unmaskedEmp = employeeResponse.masked(masker, true);
        assertThat(unmaskedEmp.cccdLastFour()).isEqualTo("1234");
        assertThat(unmaskedEmp.bankAccountLastFour()).isEqualTo("5678");

        var maskedEmp = employeeResponse.masked(masker, false);
        assertThat(maskedEmp.cccdLastFour()).isEqualTo("******1234");
        assertThat(maskedEmp.bankAccountLastFour()).isEqualTo("******5678");

        var contractResponse = new ContractResponse(
                contractId, empId, ContractType.FIXED_TERM,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("25000000"), new BigDecimal("2000000"),
                ContractStatus.ACTIVE, false, 0L);

        var unmaskedContract = contractResponse.masked(masker, true);
        assertThat(unmaskedContract.baseSalary()).isEqualByComparingTo("25000000");
        assertThat(unmaskedContract.allowance()).isEqualByComparingTo("2000000");

        var maskedContract = contractResponse.masked(masker, false);
        assertThat(maskedContract.baseSalary()).isNull();
        assertThat(maskedContract.allowance()).isNull();

        var employeeEntity = new Employee(
                "NV000001", "Nguyễn Văn A", null, null, null, "staff@example.com", null, null,
                "a".repeat(64), "1234", "b".repeat(64), "5678", null, null, null, null);
        var contractEntity = new Contract();
        contractEntity.revise(empId, ContractType.INDEFINITE, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("25000000"), new BigDecimal("2000000"));
        var createEmpReq = new CreateEmployeeRequest(
                "Nguyễn Văn A", null, null, null, null, null, null,
                "001099001234", "970400005678", null, null, null, null);
        var createContractReq = new CreateContractRequest(
                empId, ContractType.INDEFINITE, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("25000000"), new BigDecimal("2000000"));
        var updateContractReq = new UpdateContractRequest(
                empId, ContractType.INDEFINITE, LocalDate.of(2026, 1, 1), null,
                new BigDecimal("25000000"), new BigDecimal("2000000"));
        var historyResp = new EmployeeHistoryResponse(
                UUID.randomUUID(), "CREATE", "actor", null, null, Instant.now(), "trace");

        assertThat(employeeResponse.toString()).doesNotContain("1234", "5678", "personal@example.com", "0901234567");
        assertThat(maskedEmp.toString()).doesNotContain("1234", "5678");
        assertThat(contractResponse.toString()).doesNotContain("25000000", "2000000");
        assertThat(employeeEntity.toString()).doesNotContain("1234", "5678", "a".repeat(64), "b".repeat(64));
        assertThat(contractEntity.toString()).doesNotContain("25000000", "2000000");
        assertThat(createEmpReq.toString()).doesNotContain("001099001234", "970400005678");
        assertThat(createContractReq.toString()).doesNotContain("25000000", "2000000");
        assertThat(updateContractReq.toString()).doesNotContain("25000000", "2000000");
        assertThat(historyResp.toString()).doesNotContain("actor", "trace");
    }
}
