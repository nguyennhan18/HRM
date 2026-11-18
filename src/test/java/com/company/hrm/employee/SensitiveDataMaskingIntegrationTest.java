package com.company.hrm.employee;

import static com.company.hrm.testsupport.EmployeeFixture.email;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.common.audit.AuditLogRepository;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.contract.dto.ContractResponse;
import com.company.hrm.contract.dto.CreateContractRequest;
import com.company.hrm.contract.entity.ContractType;
import com.company.hrm.contract.service.ContractService;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.testsupport.EmployeeFixture;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataMaskingIntegrationTest extends AbstractIntegrationTest {

    private static final String EMP_PATH = "/api/v1/employees";
    private static final String CONTRACT_PATH = "/api/v1/contracts";

    private static final String SELF_CCCD = "001099101111";
    private static final String SELF_BANK = "970400102222";
    private static final BigDecimal SELF_SALARY = new BigDecimal("18543210");
    private static final BigDecimal SELF_ALLOWANCE = new BigDecimal("1234560");

    private static final String DIRECT_CCCD = "001099203333";
    private static final String DIRECT_BANK = "970400204444";
    private static final BigDecimal DIRECT_SALARY = new BigDecimal("27654320");
    private static final BigDecimal DIRECT_ALLOWANCE = new BigDecimal("2345670");

    private static final String INDIRECT_CCCD = "001099305555";
    private static final String INDIRECT_BANK = "970400306666";
    private static final BigDecimal INDIRECT_SALARY = new BigDecimal("36543210");
    private static final BigDecimal INDIRECT_ALLOWANCE = new BigDecimal("3456780");

    private static final String OTHER_CCCD = "001099407777";
    private static final String OTHER_BANK = "970400408888";
    private static final BigDecimal OTHER_SALARY = new BigDecimal("45432100");
    private static final BigDecimal OTHER_ALLOWANCE = new BigDecimal("4567890");

    @Autowired EmployeeService employees;
    @Autowired ContractService contracts;
    @Autowired AuditService auditService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private String batchTag;
    private UUID userId;
    private EmployeeResponse selfEmp;
    private EmployeeResponse directSubEmp;
    private EmployeeResponse indirectSubEmp;
    private EmployeeResponse otherEmp;
    private ContractResponse selfContract;
    private ContractResponse directSubContract;
    private ContractResponse indirectSubContract;
    private ContractResponse otherContract;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void setUpHierarchy() {
        authenticate(UUID.randomUUID(), "HR");
        batchTag = "T27-" + UUID.randomUUID();
        String uniqueSuffix = String.format("%04d", Math.abs(batchTag.hashCode() % 10000));

        selfEmp = employees.create(new EmployeeFixture()
                .name("Self " + batchTag)
                .email(email())
                .cccd(SELF_CCCD.substring(0, 8) + uniqueSuffix + "1111")
                .bank(SELF_BANK.substring(0, 8) + uniqueSuffix + "2222")
                .complete(), "setup-self");
        directSubEmp = employees.create(new EmployeeFixture()
                .name("FirstSub " + batchTag)
                .email(email())
                .cccd(DIRECT_CCCD.substring(0, 8) + uniqueSuffix + "3333")
                .bank(DIRECT_BANK.substring(0, 8) + uniqueSuffix + "4444")
                .manager(selfEmp.id())
                .complete(), "setup-direct");
        indirectSubEmp = employees.create(new EmployeeFixture()
                .name("SecondSub " + batchTag)
                .email(email())
                .cccd(INDIRECT_CCCD.substring(0, 8) + uniqueSuffix + "5555")
                .bank(INDIRECT_BANK.substring(0, 8) + uniqueSuffix + "6666")
                .manager(directSubEmp.id())
                .complete(), "setup-indirect");
        otherEmp = employees.create(new EmployeeFixture()
                .name("Other " + batchTag)
                .email(email())
                .cccd(OTHER_CCCD.substring(0, 8) + uniqueSuffix + "7777")
                .bank(OTHER_BANK.substring(0, 8) + uniqueSuffix + "8888")
                .complete(), "setup-other");

        selfContract = createContract(selfEmp.id(), SELF_SALARY, SELF_ALLOWANCE);
        directSubContract = createContract(directSubEmp.id(), DIRECT_SALARY, DIRECT_ALLOWANCE);
        indirectSubContract = createContract(indirectSubEmp.id(), INDIRECT_SALARY, INDIRECT_ALLOWANCE);
        otherContract = createContract(otherEmp.id(), OTHER_SALARY, OTHER_ALLOWANCE);

        userId = users.saveAndFlush(new User(email(), "unused-hash",
                roles.findByCode("EMPLOYEE").orElseThrow())).getId();
        jdbc.update("update employees set user_id = ? where id = ?", userId, selfEmp.id());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ContractResponse createContract(UUID employeeId, BigDecimal salary, BigDecimal allowance) {
        return contracts.create(new CreateContractRequest(
                employeeId, ContractType.INDEFINITE, LocalDate.of(2026, 1, 1), null, salary, allowance), "setup-contract");
    }

    private static void authenticate(UUID principalId, String... grantedRoles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principalId.toString(),
                        null,
                        Arrays.stream(grantedRoles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                // ADMIN: full unmasked access to all profiles and contracts
                "ADMIN,            SELF,         200, 1111,       2222,       18543210, 1234560",
                "ADMIN,            DIRECT_SUB,   200, 3333,       4444,       27654320, 2345670",
                "ADMIN,            INDIRECT_SUB, 200, 5555,       6666,       36543210, 3456780",
                "ADMIN,            OTHER,        200, 7777,       8888,       45432100, 4567890",
                // HR: full unmasked access to all profiles and contracts
                "HR,               SELF,         200, 1111,       2222,       18543210, 1234560",
                "HR,               DIRECT_SUB,   200, 3333,       4444,       27654320, 2345670",
                "HR,               INDIRECT_SUB, 200, 5555,       6666,       36543210, 3456780",
                "HR,               OTHER,        200, 7777,       8888,       45432100, 4567890",
                // MANAGER (single role): cannot view self/other (403); can view direct/indirect subordinates with masked fields
                "MANAGER,          SELF,         403, NULL,       NULL,       NULL,     NULL",
                "MANAGER,          DIRECT_SUB,   200, ******3333, ******4444, NULL,     NULL",
                "MANAGER,          INDIRECT_SUB, 200, ******5555, ******6666, NULL,     NULL",
                "MANAGER,          OTHER,        403, NULL,       NULL,       NULL,     NULL",
                // EMPLOYEE (single role): can view self with unmasked fields; cannot view subordinates or others (403)
                "EMPLOYEE,         SELF,         200, 1111,       2222,       18543210, 1234560",
                "EMPLOYEE,         DIRECT_SUB,   403, NULL,       NULL,       NULL,     NULL",
                "EMPLOYEE,         INDIRECT_SUB, 403, NULL,       NULL,       NULL,     NULL",
                "EMPLOYEE,         OTHER,        403, NULL,       NULL,       NULL,     NULL",
                // MANAGER + EMPLOYEE: unmasked on self, masked on subordinates, 403 on other
                "MANAGER|EMPLOYEE, SELF,         200, 1111,       2222,       18543210, 1234560",
                "MANAGER|EMPLOYEE, DIRECT_SUB,   200, ******3333, ******4444, NULL,     NULL",
                "MANAGER|EMPLOYEE, INDIRECT_SUB, 200, ******5555, ******6666, NULL,     NULL",
                "MANAGER|EMPLOYEE, OTHER,        403, NULL,       NULL,       NULL,     NULL",
                // MANAGER + HR: highest privilege wins -> unmasked on all
                "MANAGER|HR,       SELF,         200, 1111,       2222,       18543210, 1234560",
                "MANAGER|HR,       DIRECT_SUB,   200, 3333,       4444,       27654320, 2345670",
                "MANAGER|HR,       INDIRECT_SUB, 200, 5555,       6666,       36543210, 3456780",
                "MANAGER|HR,       OTHER,        200, 7777,       8888,       45432100, 4567890",
                // MANAGER + ADMIN: highest privilege wins -> unmasked on all
                "MANAGER|ADMIN,    SELF,         200, 1111,       2222,       18543210, 1234560",
                "MANAGER|ADMIN,    DIRECT_SUB,   200, 3333,       4444,       27654320, 2345670",
                "MANAGER|ADMIN,    INDIRECT_SUB, 200, 5555,       6666,       36543210, 3456780",
                "MANAGER|ADMIN,    OTHER,        200, 7777,       8888,       45432100, 4567890"
            })
    void should_enforce_masking_matrix_for_every_role_and_sensitive_field_across_http_and_service(
            String roleSpec,
            String targetType,
            int expectedStatus,
            String expectedCccd,
            String expectedBank,
            BigDecimal expectedSalary,
            BigDecimal expectedAllowance,
            CapturedOutput output) throws Exception {

        String[] grantedRoles = roleSpec.split("\\|");
        EmployeeResponse targetEmp = switch (targetType) {
            case "SELF" -> selfEmp;
            case "DIRECT_SUB" -> directSubEmp;
            case "INDIRECT_SUB" -> indirectSubEmp;
            default -> otherEmp;
        };
        ContractResponse targetContract = switch (targetType) {
            case "SELF" -> selfContract;
            case "DIRECT_SUB" -> directSubContract;
            case "INDIRECT_SUB" -> indirectSubContract;
            default -> otherContract;
        };
        BigDecimal rawSalary = switch (targetType) {
            case "SELF" -> SELF_SALARY;
            case "DIRECT_SUB" -> DIRECT_SALARY;
            case "INDIRECT_SUB" -> INDIRECT_SALARY;
            default -> OTHER_SALARY;
        };
        BigDecimal rawAllowance = switch (targetType) {
            case "SELF" -> SELF_ALLOWANCE;
            case "DIRECT_SUB" -> DIRECT_ALLOWANCE;
            case "INDIRECT_SUB" -> INDIRECT_ALLOWANCE;
            default -> OTHER_ALLOWANCE;
        };

        // 1. HTTP GET /api/v1/employees/{id}
        var empDetailResult = mvc.perform(get(EMP_PATH + "/" + targetEmp.id())
                        .with(user(userId.toString()).roles(grantedRoles)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        assertThat(empDetailResult).doesNotContain("cccdHash", "bankAccountHash");

        if (expectedStatus == 200) {
            mvc.perform(get(EMP_PATH + "/" + targetEmp.id())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetEmp.id().toString()))
                    .andExpect(jsonPath("$.cccdLastFour").value(expectedCccd))
                    .andExpect(jsonPath("$.bankAccountLastFour").value(expectedBank));

            // 2. Direct Service call: EmployeeService.get(id)
            authenticate(userId, grantedRoles);
            EmployeeResponse serviceEmp = employees.get(targetEmp.id());
            assertThat(serviceEmp.cccdLastFour()).isEqualTo(expectedCccd);
            assertThat(serviceEmp.bankAccountLastFour()).isEqualTo(expectedBank);

            // 3. HTTP GET /api/v1/employees (list filtered by target name)
            mvc.perform(get(EMP_PATH)
                            .param("name", targetEmp.fullName())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(targetEmp.id().toString()))
                    .andExpect(jsonPath("$.content[0].cccdLastFour").value(expectedCccd))
                    .andExpect(jsonPath("$.content[0].bankAccountLastFour").value(expectedBank));

            // 4. Direct Service call: EmployeeService.list(...)
            authenticate(userId, grantedRoles);
            var listedEmp = employees.list(targetEmp.fullName(), null, null, null, PageRequest.of(0, 10))
                    .getContent().getFirst();
            assertThat(listedEmp.cccdLastFour()).isEqualTo(expectedCccd);
            assertThat(listedEmp.bankAccountLastFour()).isEqualTo(expectedBank);

            // 5. HTTP GET /api/v1/contracts/{id} & GET /api/v1/contracts?employeeId={id}
            var contractDetailAction = mvc.perform(get(CONTRACT_PATH + "/" + targetContract.id())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetContract.id().toString()));
            var contractListAction = mvc.perform(get(CONTRACT_PATH)
                            .param("employeeId", targetEmp.id().toString())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            if (expectedSalary != null) {
                contractDetailAction
                        .andExpect(jsonPath("$.baseSalary").value(expectedSalary.intValue()))
                        .andExpect(jsonPath("$.allowance").value(expectedAllowance.intValue()));
                contractListAction
                        .andExpect(jsonPath("$.content[0].baseSalary").value(expectedSalary.intValue()))
                        .andExpect(jsonPath("$.content[0].allowance").value(expectedAllowance.intValue()));
            } else {
                String contractDetailBody = contractDetailAction
                        .andExpect(jsonPath("$.baseSalary").value(nullValue()))
                        .andExpect(jsonPath("$.allowance").value(nullValue()))
                        .andReturn().getResponse().getContentAsString();
                String contractListBody = contractListAction
                        .andExpect(jsonPath("$.content[0].baseSalary").value(nullValue()))
                        .andExpect(jsonPath("$.content[0].allowance").value(nullValue()))
                        .andReturn().getResponse().getContentAsString();
                assertThat(contractDetailBody).doesNotContain(rawSalary.toString(), rawAllowance.toString());
                assertThat(contractListBody).doesNotContain(rawSalary.toString(), rawAllowance.toString());
            }

            // 6. Direct Service call: ContractService.get(id) & ContractService.list(employeeId, ...)
            authenticate(userId, grantedRoles);
            ContractResponse serviceContract = contracts.get(targetContract.id());
            ContractResponse listedContract = contracts.list(targetEmp.id(), PageRequest.of(0, 10))
                    .getContent().getFirst();
            assertThat(serviceContract.baseSalary()).isEqualTo(expectedSalary);
            assertThat(serviceContract.allowance()).isEqualTo(expectedAllowance);
            assertThat(listedContract.baseSalary()).isEqualTo(expectedSalary);
            assertThat(listedContract.allowance()).isEqualTo(expectedAllowance);
        } else {
            // 403 Forbidden on Employee detail, Contract detail, and Contract list; filtered out of Employee list
            authenticate(userId, grantedRoles);
            assertThatThrownBy(() -> employees.get(targetEmp.id()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(employees.list(targetEmp.fullName(), null, null, null, PageRequest.of(0, 10))
                    .getTotalElements()).isZero();

            String forbiddenContractDetail = mvc.perform(get(CONTRACT_PATH + "/" + targetContract.id())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            String forbiddenContractList = mvc.perform(get(CONTRACT_PATH)
                            .param("employeeId", targetEmp.id().toString())
                            .with(user(userId.toString()).roles(grantedRoles)))
                    .andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            assertThat(forbiddenContractDetail).doesNotContain(rawSalary.toString(), rawAllowance.toString());
            assertThat(forbiddenContractList).doesNotContain(rawSalary.toString(), rawAllowance.toString());

            authenticate(userId, grantedRoles);
            assertThatThrownBy(() -> contracts.get(targetContract.id()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> contracts.list(targetEmp.id(), PageRequest.of(0, 10)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        // Verify logs never contain raw sensitive identifiers or salaries
        assertThat(output.getAll()).doesNotContain(
                SELF_CCCD, SELF_BANK, DIRECT_CCCD, DIRECT_BANK,
                INDIRECT_CCCD, INDIRECT_BANK, OTHER_CCCD, OTHER_BANK,
                SELF_SALARY.toString(), DIRECT_SALARY.toString(),
                INDIRECT_SALARY.toString(), OTHER_SALARY.toString());
    }

    @Test
    void should_preserve_null_for_missing_optional_identifiers_and_validate_employee_detail_endpoint(
            CapturedOutput output) throws Exception {
        authenticate(UUID.randomUUID(), "HR");
        var draftWithoutIdentifiers = employees.create(
                new EmployeeFixture().name("NoIdentifiers " + batchTag).manager(selfEmp.id()).build(),
                "draft-no-id");

        // HR and MANAGER both receive null (never "******null") when CCCD and bank account are absent
        for (String role : List.of("HR", "MANAGER")) {
            mvc.perform(get(EMP_PATH + "/" + draftWithoutIdentifiers.id())
                            .with(user(userId.toString()).roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cccdLastFour").value(nullValue()))
                    .andExpect(jsonPath("$.bankAccountLastFour").value(nullValue()));
        }

        // Authentication & error boundaries for GET /api/v1/employees/{id}
        mvc.perform(get(EMP_PATH + "/" + selfEmp.id()).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"));

        mvc.perform(get(EMP_PATH + "/not-a-uuid").with(user(userId.toString()).roles("HR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));

        UUID missingId = UUID.randomUUID();
        mvc.perform(get(EMP_PATH + "/" + missingId).with(user(userId.toString()).roles("HR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
        mvc.perform(get(EMP_PATH + "/" + missingId).with(user(userId.toString()).roles("EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));

        // Soft-deleted employee returns 404 for HR/ADMIN and 403 for MANAGER
        authenticate(UUID.randomUUID(), "HR");
        employees.delete(draftWithoutIdentifiers.id(), "soft-delete-draft");
        mvc.perform(get(EMP_PATH + "/" + draftWithoutIdentifiers.id()).with(user(userId.toString()).roles("HR")))
                .andExpect(status().isNotFound());
        mvc.perform(get(EMP_PATH + "/" + draftWithoutIdentifiers.id()).with(user(userId.toString()).roles("MANAGER")))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> {
            authenticate(UUID.randomUUID(), "HR");
            employees.get(draftWithoutIdentifiers.id());
        }).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_RESOURCE_NOT_FOUND));

        assertThat(output.getAll()).doesNotContain("******null");
    }

    @Test
    void should_mask_salary_and_hashes_in_audit_payload_masker() {
        UUID entityId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "cccdHash", "a".repeat(64),
                "bankAccountHash", "b".repeat(64),
                "baseSalary", new BigDecimal("50000000"),
                "allowance", new BigDecimal("5000000"),
                "nested", List.of(Map.of("salary", "99999999", "cccd", "001234567890")));

        var recorded = auditService.record("MASK_TEST", "Contract", entityId, payload, payload, "mask-trace");
        var persisted = auditLogRepository.findById(recorded.getId()).orElseThrow();
        String jsonText = persisted.getAfterJson().toString();

        assertThat(persisted.getAfterJson().get("cccdHash").asText()).isEqualTo("***");
        assertThat(persisted.getAfterJson().get("bankAccountHash").asText()).isEqualTo("***");
        assertThat(persisted.getAfterJson().get("baseSalary").asText()).isEqualTo("***");
        assertThat(persisted.getAfterJson().get("allowance").asText()).isEqualTo("***");
        assertThat(persisted.getAfterJson().path("nested").get(0).path("salary").asText()).isEqualTo("***");
        assertThat(persisted.getAfterJson().path("nested").get(0).path("cccd").asText()).isEqualTo("***");
        assertThat(jsonText).doesNotContain("a".repeat(64), "b".repeat(64), "50000000", "5000000", "99999999", "001234567890");
    }
}
