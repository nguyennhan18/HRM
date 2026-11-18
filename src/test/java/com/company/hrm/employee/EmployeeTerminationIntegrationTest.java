package com.company.hrm.employee;

import com.company.hrm.auth.dto.AuthDtos.Login;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.AuthTokenRepository;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.auth.service.AuthService;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.contract.dto.CreateContractRequest;
import com.company.hrm.contract.entity.ContractStatus;
import com.company.hrm.contract.entity.ContractType;
import com.company.hrm.contract.service.ContractService;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.entity.DocumentType;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.service.EmployeeDocumentService;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.testsupport.EmployeeFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.company.hrm.testsupport.EmployeeFixture.email;
import static com.company.hrm.testsupport.EmployeeFixture.identifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "hr-actor", roles = "HR")
@Import(EmployeeTerminationIntegrationTest.FixedClockConfig.class)
class EmployeeTerminationIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/v1/employees";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-18T10:00:00Z");
    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 17);
    private static final LocalDate TERMINATION_DATE = LocalDate.of(2026, 11, 30);
    private static final BigDecimal SALARY = new BigDecimal("20000000");
    private static final byte[] PDF = "%PDF-1.7\nTermination test document\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final Path DIRECTORY = createTempDirectory();

    static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("hrm-termination-docs-").toRealPath();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedTerminationClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired EmployeeService employees;
    @Autowired EmployeeDocumentService documents;
    @Autowired ContractService contracts;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired AuthTokenRepository tokens;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("hrm.auth.rate-limit", () -> "1000");
        r.add("hrm.documents.directory", DIRECTORY::toString);
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (var paths = Files.walk(DIRECTORY)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static void asHr() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "hr-actor", "unused", AuthorityUtils.createAuthorityList("ROLE_HR")));
    }

    EmployeeResponse createWithState(EmployeeStatus status, String cccd, String bank) {
        asHr();
        var created = employees.create(new EmployeeFixture().email(email()).cccd(cccd).bank(bank).complete(), "create-trace");
        if (status != EmployeeStatus.DRAFT) {
            jdbc.update("update employees set status = ? where id = ?", status.name(), created.id());
        }
        return employees.get(created.id());
    }

    record LinkedAccount(UUID userId, String email, String password, String accessToken, String refreshToken) {}

    LinkedAccount linkAndLogin(UUID employeeId, String role) {
        String userEmail = email();
        String password = "Password#2026";
        UUID userId = users.saveAndFlush(new User(userEmail, passwordEncoder.encode(password),
                roles.findByCode(role).orElseThrow())).getId();
        jdbc.update("update employees set user_id = ? where id = ?", userId, employeeId);
        var issued = auth.login(new Login(userEmail, password), "127.0.0.1");
        return new LinkedAccount(userId, userEmail, password, issued.accessToken(), issued.refreshToken());
    }

    @Test
    void should_terminate_employee_disable_user_revoke_tokens_block_attendance_and_preserve_data() throws Exception {
        String cccd = identifier(), bank = identifier();
        var employee = createWithState(EmployeeStatus.ACTIVE, cccd, bank);
        var doc = documents.upload(employee.id(), DocumentType.CV,
                new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF), "doc-trace");
        var account = linkAndLogin(employee.id(), "EMPLOYEE");

        // Before termination: token works and attendance eligibility passes.
        mvc.perform(get(PATH).header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk());
        asHr();
        employees.validateAttendanceEligibility(employee.id());

        // Terminate via HTTP endpoint.
        mvc.perform(post(PATH + "/" + employee.id() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .header("X-Correlation-ID", "term-trace-1")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", TERMINATION_DATE.toString(),
                                "reason", "  Nghỉ việc theo nguyện vọng cá nhân  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employee.id().toString()))
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.terminationDate").value(TERMINATION_DATE.toString()))
                .andExpect(jsonPath("$.terminationReason").value("Nghỉ việc theo nguyện vọng cá nhân"))
                .andExpect(jsonPath("$.fullName").value(employee.fullName()))
                .andExpect(jsonPath("$.companyEmail").value(employee.companyEmail()))
                .andExpect(jsonPath("$.cccdLastFour").value(cccd.substring(cccd.length() - 4)))
                .andExpect(jsonPath("$.bankAccountLastFour").value(bank.substring(bank.length() - 4)));

        // Old access token is rejected immediately.
        mvc.perform(get(PATH).header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"));

        // Old refresh token is rejected.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", account.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"));

        // Re-login is rejected.
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", account.email(), "password", account.password()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"));

        // User is disabled, all tokens are revoked, User deactivation audit log exists.
        assertThat(users.findById(account.userId()).orElseThrow().isEnabled()).isFalse();
        assertThat(tokens.findByUserId(account.userId())).isNotEmpty().allMatch(t -> t.isRevoked());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'User' and entity_id = ? and action = 'USER_DEACTIVATED'",
                Integer.class, account.userId())).isEqualTo(1);

        // Data remains intact and attendance is marked blocked for Phase 3.
        asHr();
        assertThat(documents.download(employee.id(), doc.id()).content()).isEqualTo(PDF);
        assertThatThrownBy(() -> employees.validateAttendanceEligibility(employee.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE));

        // ON_LEAVE_LONG can also transition to TERMINATED.
        var onLeave = createWithState(EmployeeStatus.ON_LEAVE_LONG, identifier(), identifier());
        var terminatedFromLeave = employees.terminate(onLeave.id(),
                new TerminateEmployeeRequest(TERMINATION_DATE, "Hết thời gian nghỉ không lương"), "leave-term");
        assertThat(terminatedFromLeave.status()).isEqualTo(EmployeeStatus.TERMINATED);
    }

    @Test
    void should_auto_terminate_active_contracts_and_rollback_when_termination_date_precedes_contract_start() throws Exception {
        // Case 1: No contract -> succeeds.
        var noContractEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var terminatedNoContract = employees.terminate(noContractEmp.id(),
                new TerminateEmployeeRequest(TERMINATION_DATE, "Không có hợp đồng"), "no-contract");
        assertThat(terminatedNoContract.status()).isEqualTo(EmployeeStatus.TERMINATED);

        // Case 2: Indefinite ACTIVE contract (endDate == null) -> auto-terminated with endDate = terminationDate.
        var indefiniteEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var draftIndefinite = contracts.create(new CreateContractRequest(
                indefiniteEmp.id(), ContractType.INDEFINITE, START_DATE.plusDays(10), null, SALARY, BigDecimal.ZERO), null);
        var activeIndefinite = contracts.activate(draftIndefinite.id(), null);
        employees.terminate(indefiniteEmp.id(),
                new TerminateEmployeeRequest(TERMINATION_DATE, "Chấm dứt hợp đồng vô thời hạn"), "indef-trace");
        var updatedIndefinite = contracts.get(activeIndefinite.id());
        assertThat(updatedIndefinite.status()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(updatedIndefinite.endDate()).isEqualTo(TERMINATION_DATE);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'Contract' and entity_id = ? and action = 'TERMINATE'",
                Integer.class, activeIndefinite.id())).isEqualTo(1);

        // Case 3: Fixed-term ACTIVE contract with endDate > terminationDate -> shortened to terminationDate;
        // and endDate <= terminationDate -> preserved as contract.endDate.
        var fixedEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var draftFixed = contracts.create(new CreateContractRequest(
                fixedEmp.id(), ContractType.FIXED_TERM, START_DATE, TERMINATION_DATE.plusDays(30), SALARY, BigDecimal.ZERO), null);
        var activeFixed = contracts.activate(draftFixed.id(), null);
        employees.terminate(fixedEmp.id(),
                new TerminateEmployeeRequest(TERMINATION_DATE, "Nghỉ trước hạn hợp đồng"), "fixed-trace");
        var updatedFixed = contracts.get(activeFixed.id());
        assertThat(updatedFixed.status()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(updatedFixed.endDate()).isEqualTo(TERMINATION_DATE);

        var fixedEarlierEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        LocalDate earlierContractEnd = START_DATE.plusDays(5);
        var draftEarlier = contracts.create(new CreateContractRequest(
                fixedEarlierEmp.id(), ContractType.FIXED_TERM, START_DATE, earlierContractEnd, SALARY, BigDecimal.ZERO), null);
        var activeEarlier = contracts.activate(draftEarlier.id(), null);
        employees.terminate(fixedEarlierEmp.id(),
                new TerminateEmployeeRequest(TERMINATION_DATE, "Nghỉ sau ngày hết hạn hợp đồng"), "earlier-trace");
        var updatedEarlier = contracts.get(activeEarlier.id());
        assertThat(updatedEarlier.status()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(updatedEarlier.endDate()).isEqualTo(earlierContractEnd);

        // Case 4: terminationDate < contract.startDate -> 409 Conflict and full rollback (Employee stays ACTIVE, User stays enabled).
        var conflictEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var account = linkAndLogin(conflictEmp.id(), "EMPLOYEE");
        LocalDate contractStart = START_DATE.plusDays(8);
        var draftConflict = contracts.create(new CreateContractRequest(
                conflictEmp.id(), ContractType.INDEFINITE, contractStart, null, SALARY, BigDecimal.ZERO), null);
        var activeConflict = contracts.activate(draftConflict.id(), null);

        mvc.perform(post(PATH + "/" + conflictEmp.id() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", contractStart.minusDays(1).toString(),
                                "reason", "Ngày nghỉ trước ngày hợp đồng"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"));

        asHr();
        var afterRollbackEmp = employees.get(conflictEmp.id());
        assertThat(afterRollbackEmp.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(afterRollbackEmp.terminationDate()).isNull();
        assertThat(afterRollbackEmp.terminationReason()).isNull();
        var afterRollbackContract = contracts.get(activeConflict.id());
        assertThat(afterRollbackContract.status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(afterRollbackContract.endDate()).isNull();
        assertThat(users.findById(account.userId()).orElseThrow().isEnabled()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'Employee' and entity_id = ? and action = 'TERMINATE'",
                Integer.class, conflictEmp.id())).isZero();
    }

    @Test
    void should_reject_invalid_source_status_with_status_name_and_validate_termination_request() throws Exception {
        // DRAFT -> 409 mentioning DRAFT
        var draftEmp = createWithState(EmployeeStatus.DRAFT, identifier(), identifier());
        mvc.perform(post(PATH + "/" + draftEmp.id() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", TERMINATION_DATE.toString(),
                                "reason", "Thử thôi việc hồ sơ nháp"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("DRAFT")));

        // Already TERMINATED -> 409 mentioning TERMINATED
        var activeEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        asHr();
        employees.terminate(activeEmp.id(), new TerminateEmployeeRequest(TERMINATION_DATE, "Nghỉ lần 1"), null);
        mvc.perform(post(PATH + "/" + activeEmp.id() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", TERMINATION_DATE.toString(),
                                "reason", "Nghỉ lần 2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("TERMINATED")));

        // terminationDate < employee.startDate -> 400
        var dateCheckEmp = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        mvc.perform(post(PATH + "/" + dateCheckEmp.id() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", START_DATE.minusDays(1).toString(),
                                "reason", "Trước ngày vào làm"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));

        // Blank reason or missing date -> 400
        for (String invalidBody : List.of(
                "{\"terminationDate\":\"2026-11-30\",\"reason\":\"   \"}",
                "{\"reason\":\"Có lý do nhưng thiếu ngày\"}")) {
            mvc.perform(post(PATH + "/" + dateCheckEmp.id() + "/terminate")
                            .with(user("hr-actor").roles("HR"))
                            .contentType("application/json")
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
        }

        // Non-existent employee -> 404
        mvc.perform(post(PATH + "/" + UUID.randomUUID() + "/terminate")
                        .with(user("hr-actor").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "terminationDate", TERMINATION_DATE.toString(),
                                "reason", "Không tồn tại"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_soft_delete_employee_block_when_subordinates_remain_and_deactivate_linked_user() throws Exception {
        var manager = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var sub1 = employees.create(new EmployeeFixture().manager(manager.id()).build(), null);
        var sub2 = employees.create(new EmployeeFixture().manager(manager.id()).build(), null);
        var account = linkAndLogin(manager.id(), "MANAGER");

        // Blocked with 2 subordinates
        mvc.perform(delete(PATH + "/" + manager.id()).with(user("hr-actor").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("2")));

        // Soft-delete sub1 -> manager still blocked with 1 subordinate
        mvc.perform(delete(PATH + "/" + sub1.id()).with(user("hr-actor").roles("HR")))
                .andExpect(status().isNoContent());
        mvc.perform(delete(PATH + "/" + manager.id()).with(user("hr-actor").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1")));

        // Detach sub2 -> deleting manager succeeds
        asHr();
        employees.changeManager(sub2.id(), null, null);
        mvc.perform(delete(PATH + "/" + manager.id())
                        .with(user("hr-actor").roles("HR"))
                        .header("X-Correlation-ID", "delete-trace"))
                .andExpect(status().isNoContent());

        // Physical row remains with deleted_at set; standard get returns 404; repeating delete returns 404
        assertThat(jdbc.queryForObject("select deleted_at is not null from employees where id = ?",
                Boolean.class, manager.id())).isTrue();
        asHr();
        assertThatThrownBy(() -> employees.get(manager.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_RESOURCE_NOT_FOUND));
        mvc.perform(delete(PATH + "/" + manager.id()).with(user("hr-actor").roles("HR")))
                .andExpect(status().isNotFound());

        // Linked User is disabled, tokens revoked, old access token & refresh token & login rejected, User audit recorded
        assertThat(users.findById(account.userId()).orElseThrow().isEnabled()).isFalse();
        assertThat(tokens.findByUserId(account.userId())).isNotEmpty().allMatch(t -> t.isRevoked());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", account.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", account.email(), "password", account.password()))))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'User' and entity_id = ? and action = 'USER_DEACTIVATED'",
                Integer.class, account.userId())).isEqualTo(1);
    }

    @Test
    void should_return_employee_history_across_lifecycle_including_after_soft_delete_without_sensitive_leaks() throws Exception {
        String cccd = identifier(), bank = identifier();
        var manager = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        var employee = createWithState(EmployeeStatus.ACTIVE, cccd, bank);
        asHr();
        employees.changeManager(employee.id(), manager.id(), "mgr-trace");
        employees.terminate(employee.id(), new TerminateEmployeeRequest(TERMINATION_DATE, "Chuyển công tác"), "term-trace");
        employees.delete(employee.id(), "del-trace");

        // History remains accessible to HR and ADMIN even after soft-delete
        String responseBody = mvc.perform(get(PATH + "/" + employee.id() + "/history")
                        .with(user("hr-actor").roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[1].action").value("TERMINATE"))
                .andExpect(jsonPath("$.content[1].afterJson.status").value("TERMINATED"))
                .andExpect(jsonPath("$.content[1].afterJson.terminationReason").value("Chuyển công tác"))
                .andExpect(jsonPath("$.content[2].action").value("UPDATE_MANAGER"))
                .andExpect(jsonPath("$.content[3].action").value("CREATE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain(cccd, bank, "cccdHash", "bankAccountHash");

        // Pagination, size cap, deterministic tie-breaker on identical timestamps, and sort validation
        jdbc.update("update audit_logs set created_at = '2026-11-18T10:00:00Z'::timestamptz where entity_id = ?", employee.id());
        asHr();
        var page0 = employees.history(employee.id(), PageRequest.of(0, 2));
        var page1 = employees.history(employee.id(), PageRequest.of(1, 2));
        assertThat(page0.getContent().get(0).id()).isGreaterThan(page0.getContent().get(1).id());
        assertThat(page0.getContent().get(1).id()).isGreaterThan(page1.getContent().get(0).id());
        assertThat(page1.getContent().get(0).id()).isGreaterThan(page1.getContent().get(1).id());

        mvc.perform(get(PATH + "/" + employee.id() + "/history")
                        .with(user("admin-actor").roles("ADMIN"))
                        .param("size", "201")
                        .param("sort", "action,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200))
                .andExpect(jsonPath("$.content[0].action").value("CREATE"));

        mvc.perform(get(PATH + "/" + employee.id() + "/history")
                        .with(user("hr-actor").roles("HR"))
                        .param("sort", "invalidField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));

        // Only returns 404 when the employee ID never existed
        mvc.perform(get(PATH + "/" + UUID.randomUUID() + "/history")
                        .with(user("hr-actor").roles("HR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void should_enforce_authentication_and_rbac_on_terminate_delete_and_history(String role) throws Exception {
        var target = createWithState(EmployeeStatus.ACTIVE, identifier(), identifier());
        String terminateBody = json.writeValueAsString(Map.of(
                "terminationDate", TERMINATION_DATE.toString(),
                "reason", "Kiểm thử phân quyền"));

        // Anonymous -> 401
        mvc.perform(post(PATH + "/" + target.id() + "/terminate").with(anonymous())
                        .contentType("application/json").content(terminateBody))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + "/" + target.id()).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/" + target.id() + "/history").with(anonymous()))
                .andExpect(status().isUnauthorized());

        // Non-HR/ADMIN role -> 403 on HTTP
        mvc.perform(post(PATH + "/" + target.id() + "/terminate").with(user("u").roles(role))
                        .contentType("application/json").content(terminateBody))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/" + target.id()).with(user("u").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/" + target.id() + "/history").with(user("u").roles(role)))
                .andExpect(status().isForbidden());

        // Direct service proxy calls -> AccessDeniedException
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "unused", AuthorityUtils.createAuthorityList("ROLE_" + role)));
        try {
            assertThatThrownBy(() -> employees.terminate(target.id(),
                    new TerminateEmployeeRequest(TERMINATION_DATE, "Forbidden"), null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> employees.delete(target.id(), null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> employees.history(target.id(), PageRequest.of(0, 10)))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
