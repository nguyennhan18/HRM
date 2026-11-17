package com.company.hrm.employee;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.auth.service.AuthorizationService;
import com.company.hrm.auth.service.EmployeeRelations;
import com.company.hrm.common.exception.*;
import com.company.hrm.employee.dto.*;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.repository.EmployeeWriteLock;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.service.*;
import com.company.hrm.testsupport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import static com.company.hrm.testsupport.EmployeeFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmployeeIntegrationTest.SampleController.class)
@WithMockUser(username = "employee-test-hr", roles = "HR")
@ExtendWith(OutputCaptureExtension.class)
class EmployeeIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/v1/employee-foundation-test";
    @Autowired EmployeeService employees;
    @Autowired EmployeeRelations relations;
    @Autowired AuthorizationService authz;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean EmployeeWriteLock writeLock;

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    // Exercise HTTP error/response boundaries without adding the T2.3 production API.
    @RestController static class SampleController {
        private final EmployeeService employees;
        SampleController(EmployeeService employees) { this.employees = employees; }
        @PostMapping(PATH) EmployeeResponse create(@jakarta.validation.Valid @RequestBody CreateEmployeeRequest request) {
            return employees.create(request, "employee-test");
        }
        @GetMapping(PATH + "/{id}") EmployeeResponse get(@PathVariable UUID id) { return employees.get(id); }
    }

    EmployeeResponse create(EmployeeFixture fixture) { return employees.create(fixture.build(), "employee-test"); }
    UUID link(EmployeeResponse employee, String role) {
        UUID userId = users.saveAndFlush(new User(email(), "unused-test-hash",
                roles.findByCode(role).orElseThrow())).getId();
        jdbc.update("update employees set user_id = ? where id = ?", userId, employee.id());
        return userId;
    }
    static void authenticate(UUID userId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId.toString(), null,
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }
    static void hr() { authenticate(UUID.randomUUID(), "HR"); }
    void softDelete(UUID id) { jdbc.update("update employees set deleted_at = current_timestamp where id = ?", id); }

    @Test void should_persist_complete_profile_and_keep_only_hashes_and_suffixes(CapturedOutput output) throws Exception {
        String cccd = identifier(), bank = identifier();
        var department = departments.create(new CreateDepartmentRequest(code(), "Phòng thử nghiệm", null), null);
        var position = positions.create(new CreatePositionRequest(code(), "Chức danh thử nghiệm"), null);
        var manager = create(new EmployeeFixture());
        var request = new EmployeeFixture().email(" Staff@Example.Test ").cccd(cccd).bank(bank)
                .department(department.id()).position(position.id()).manager(manager.id()).complete();
        var employee = employees.create(request, "employee-test");
        var saved = employees.get(employee.id());
        assertThat(saved).isEqualTo(employee);
        assertThat(saved.companyEmail()).isEqualTo("staff@example.test");
        assertThat(saved.dateOfBirth()).isEqualTo(request.dateOfBirth());
        assertThat(saved.gender()).isEqualTo(request.gender());
        assertThat(saved.personalEmail()).isEqualTo(request.personalEmail());
        assertThat(saved.phone()).isEqualTo(request.phone());
        assertThat(saved.address()).isEqualTo(request.address());
        assertThat(saved.startDate()).isEqualTo(request.startDate());
        assertThat(saved.status()).isEqualTo(EmployeeStatus.DRAFT);
        assertThat(saved.cccdLastFour()).isEqualTo(cccd.substring(cccd.length() - 4));
        assertThat(saved.bankAccountLastFour()).isEqualTo(bank.substring(bank.length() - 4));
        var row = jdbc.queryForMap("select * from employees where id = ?", employee.id());
        assertThat(row.get("cccd_hash").toString()).matches("[0-9a-f]{64}");
        assertThat(row.get("bank_account_hash").toString()).matches("[0-9a-f]{64}");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("created_by")).isNotNull();
        assertThat(row.get("version")).isEqualTo(0L);
        assertThat(row.toString()).doesNotContain(cccd, bank);
        assertThat(json.writeValueAsString(request)).doesNotContain(cccd, bank);
        assertThat(request.toString()).doesNotContain(cccd, bank);
        String response = mvc.perform(get(PATH + "/" + employee.id())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(cccd, bank, "cccdHash", "bankAccountHash");
        String audit = jdbc.queryForObject("select after_json::text from audit_logs where entity_id = ?",
                String.class, employee.id());
        assertThat(audit).doesNotContain(cccd, bank, row.get("cccd_hash").toString());
        assertThat(output.getAll()).doesNotContain(cccd, bank);
    }

    @Test void should_accept_draft_with_only_name_and_null_optional_fields() {
        var employee = create(new EmployeeFixture());
        assertThat(employee.status()).isEqualTo(EmployeeStatus.DRAFT);
        assertThat(employee.gender()).isNull();
        assertThat(employee.companyEmail()).isNull();
        assertThat(employee.departmentId()).isNull();
        assertThat(employee.cccdLastFour()).isNull();
        assertThat(employee.bankAccountLastFour()).isNull();
        assertThat(create(new EmployeeFixture()).id()).isNotEqualTo(employee.id());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void should_reject_duplicate_email_and_cccd_even_after_soft_delete(boolean deleted, CapturedOutput output) throws Exception {
        String address = email(), cccd = identifier(), bank = identifier();
        var first = create(new EmployeeFixture().email(address).cccd(cccd).bank(bank));
        if (deleted) softDelete(first.id());
        for (var fixture : List.of(new EmployeeFixture().email(" " + address.toUpperCase(Locale.ROOT) + " "),
                new EmployeeFixture().cccd(" " + cccd + " ").bank(bank))) {
            Throwable error = catchThrowable(() -> create(fixture));
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT);
            assertSafe(error, cccd, bank);
        }
        var duplicateBank = create(new EmployeeFixture().bank(bank));
        assertThat(jdbc.queryForObject("select bank_account_hash from employees where id = ?", String.class, duplicateBank.id()))
                .isEqualTo(jdbc.queryForObject("select bank_account_hash from employees where id = ?", String.class, first.id()));
        String response = mvc.perform(post(PATH).contentType("application/json")
                .content(json.writeValueAsString(Map.of("fullName", "Tên", "cccd", cccd, "bankAccountNumber", bank))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(cccd, bank);
        assertThat(output.getAll()).doesNotContain(cccd, bank);
    }

    @ParameterizedTest @ValueSource(strings = {"invalid", "blank-name"})
    void should_not_expose_sensitive_values_in_validation_exceptions_responses_or_logs(String scenario, CapturedOutput output) throws Exception {
        String cccd = identifier(), bank = identifier();
        var fixture = new EmployeeFixture().cccd(scenario.equals("invalid") ? cccd + "x" : cccd).bank(bank)
                .name(scenario.equals("blank-name") ? " " : "Tên");
        Throwable error = catchThrowable(() -> create(fixture));
        assertThat(error).isNotNull();
        assertSafe(error, cccd, bank);
        String response = mvc.perform(post(PATH).contentType("application/json")
                .content(json.writeValueAsString(Map.of("fullName", fixture.build().fullName(),
                        "cccd", fixture.build().cccd(), "bankAccountNumber", bank))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(cccd, bank);
        assertThat(output.getAll()).doesNotContain(cccd, bank);
    }

    static void assertSafe(Throwable error, String... forbidden) {
        var writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        assertThat(writer.toString()).doesNotContain(forbidden);
    }

    @Test void should_allocate_sequential_codes_and_never_reuse_after_rollback() {
        var first = create(new EmployeeFixture());
        var second = create(new EmployeeFixture());
        assertThat(first.code()).matches("NV[0-9]{6}");
        int number = Integer.parseInt(first.code().substring(2));
        assertThat(second.code()).isEqualTo("NV%06d".formatted(number + 1));
        var tx = new TransactionTemplate(transactionManager);
        Long consumed = tx.execute(status -> {
            long value = writeLock.nextCode();
            status.setRollbackOnly();
            return value;
        });
        assertThat(create(new EmployeeFixture()).code()).isEqualTo("NV%06d".formatted(consumed + 1));
    }

    @Test void should_stop_at_sequence_limit_without_wrapping_or_reusing_codes() {
        long previous = jdbc.queryForObject("select last_value from employee_code_seq", Long.class);
        try {
            jdbc.queryForObject("select setval('employee_code_seq', 999998, true)", Long.class);
            assertThat(create(new EmployeeFixture()).code()).isEqualTo("NV999999");
            assertThatThrownBy(() -> create(new EmployeeFixture())).isInstanceOf(BusinessException.class);
        } finally {
            jdbc.queryForObject("select setval('employee_code_seq', ?, true)", Long.class, previous);
        }
    }

    @Test void should_reject_self_deep_cycles_and_missing_or_deleted_managers() {
        var root = create(new EmployeeFixture());
        var child = create(new EmployeeFixture().manager(root.id()));
        var leaf = create(new EmployeeFixture().manager(child.id()));
        for (UUID manager : List.of(root.id(), child.id(), leaf.id())) {
            assertThatThrownBy(() -> employees.changeManager(root.id(), manager, null))
                    .isInstanceOf(BusinessException.class).hasMessage("Quan hệ quản lý tạo vòng lặp");
        }
        assertThatThrownBy(() -> create(new EmployeeFixture().manager(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
        softDelete(leaf.id());
        assertThatThrownBy(() -> employees.changeManager(root.id(), leaf.id(), null))
                .isInstanceOf(BusinessException.class).hasMessage("Không tìm thấy nhân viên");
        assertThat(employees.changeManager(child.id(), null, null).managerId()).isNull();
        assertThat(employees.changeManager(root.id(), child.id(), null).managerId()).isEqualTo(child.id());
    }

    @Test @Timeout(30)
    void should_allow_only_one_cross_manager_change_with_overlapping_postgres_transactions() throws Exception {
        var a = create(new EmployeeFixture());
        var b = create(new EmployeeFixture());
        var start = new CyclicBarrier(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Set<Integer> connections = ConcurrentHashMap.newKeySet();
        EmployeeWriteLock target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writeLock);
        doAnswer(call -> {
            connections.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            start.await(10, TimeUnit.SECONDS);
            Object result = call.callRealMethod();
            locked.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Lock not released by test");
            return result;
        }).when(target).acquire();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> move(a.id(), b.id()));
            var second = executor.submit(() -> move(b.id(), a.id()));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(connections).hasSize(2);
                assertThat(jdbc.queryForObject("select pg_try_advisory_xact_lock(202602, 1)", Boolean.class)).isFalse();
            } finally { release.countDown(); }
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { reset(target); }
        assertThat(employees.get(a.id()).managerId() == null || employees.get(b.id()).managerId() == null).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'UPDATE_MANAGER' and entity_id in (?, ?)",
                Integer.class, a.id(), b.id())).isEqualTo(1);
    }
    int move(UUID employee, UUID manager) {
        hr();
        try { employees.changeManager(employee, manager, null); return 200; }
        catch (BusinessException error) {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT);
            return 409;
        } finally { SecurityContextHolder.clearContext(); }
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,200,200,200", "HR,200,200,200", "MANAGER,403,200,403", "EMPLOYEE,200,403,403"})
    void should_enforce_real_employee_relationships_on_service_and_http(String role, int ownStatus, int childStatus, int otherStatus) throws Exception {
        var own = create(new EmployeeFixture());
        var child = create(new EmployeeFixture().manager(own.id()));
        var grandchild = create(new EmployeeFixture().manager(child.id()));
        var other = create(new EmployeeFixture());
        UUID userId = link(own, role);
        assertThat(userId).isNotEqualTo(own.id());
        authenticate(userId, role);
        var ids = List.of(own.id(), child.id(), grandchild.id(), other.id());
        var statuses = List.of(ownStatus, childStatus, childStatus, otherStatus);
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            // MockMvc clears the thread's SecurityContext after each request.
            authenticate(userId, role);
            if (statuses.get(i) == 200) assertThat(employees.get(id).id()).isEqualTo(id);
            else assertThatThrownBy(() -> employees.get(id)).isInstanceOf(AccessDeniedException.class);
            mvc.perform(get(PATH + "/" + id).with(user(userId.toString()).roles(role)))
                    .andExpect(status().is(statuses.get(i)));
        }
        mvc.perform(get(PATH + "/" + own.id()).with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test void should_combine_roles_and_deny_missing_links_deleted_nodes_and_cycles() {
        var own = create(new EmployeeFixture());
        var middle = create(new EmployeeFixture().manager(own.id()));
        var child = create(new EmployeeFixture().manager(middle.id()));
        UUID userId = link(own, "MANAGER");
        authenticate(userId, "MANAGER", "EMPLOYEE");
        assertThat(employees.get(own.id()).id()).isEqualTo(own.id());
        assertThat(employees.get(child.id()).id()).isEqualTo(child.id());
        jdbc.update("update employees set manager_id = ? where id = ?", child.id(), own.id());
        assertThat(authz.canViewEmployee(child.id())).isFalse();
        jdbc.update("update employees set manager_id = null where id = ?", own.id());
        softDelete(middle.id());
        assertThat(relations.managerIdOf(child.id())).isEmpty();
        assertThat(authz.canViewEmployee(child.id())).isFalse();
        assertThat(authz.canViewEmployee(middle.id())).isFalse();
        softDelete(own.id());
        assertThat(relations.employeeIdForUser(userId)).isEmpty();
        assertThat(authz.canViewEmployee(own.id())).isFalse();
        authenticate(UUID.randomUUID(), "EMPLOYEE");
        assertThat(authz.canViewEmployee(child.id())).isFalse();
        hr();
        assertThatThrownBy(() -> employees.get(own.id())).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "EMPLOYEE"})
    void should_deny_employee_writes_for_non_writers(String role, CapturedOutput output) throws Exception {
        String cccd = identifier(), bank = identifier();
        var employee = create(new EmployeeFixture());
        authenticate(UUID.randomUUID(), role);
        Throwable error = catchThrowable(() -> create(new EmployeeFixture().cccd(cccd).bank(bank)));
        assertThat(error).isInstanceOf(AccessDeniedException.class);
        assertSafe(error, cccd, bank);
        assertThatThrownBy(() -> employees.changeManager(employee.id(), null, null)).isInstanceOf(AccessDeniedException.class);
        String body = json.writeValueAsString(Map.of("fullName", "Tên", "cccd", cccd, "bankAccountNumber", bank));
        String response = mvc.perform(post(PATH).contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(cccd, bank);
        mvc.perform(post(PATH).with(anonymous()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        assertThat(output.getAll()).doesNotContain(cccd, bank);
    }

    @ParameterizedTest @CsvSource({"ACTIVE,false,409", "ON_LEAVE_LONG,false,409", "DRAFT,false,204",
            "TERMINATED,false,204", "ACTIVE,true,204", "ON_LEAVE_LONG,true,204"})
    void should_block_department_deletion_only_for_current_non_deleted_staff(String status, boolean deleted, int expected) throws Exception {
        var department = departments.create(new CreateDepartmentRequest(code(), "Phòng", null), null);
        var employee = create(new EmployeeFixture().department(department.id()));
        jdbc.update("update employees set status = ? where id = ?", status, employee.id());
        if (deleted) softDelete(employee.id());
        mvc.perform(delete("/api/v1/departments/" + department.id())).andExpect(status().is(expected));
        assertThat(jdbc.queryForObject("select deleted_at is null from departments where id = ?", Boolean.class, department.id()))
                .isEqualTo(expected == 409);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void should_block_position_deletion_for_any_referencing_profile(boolean deleted) throws Exception {
        var position = positions.create(new CreatePositionRequest(code(), "Chức danh"), null);
        var employee = create(new EmployeeFixture().position(position.id()));
        if (deleted) softDelete(employee.id());
        mvc.perform(delete("/api/v1/positions/" + position.id())).andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.update("delete from positions where id = ?", position.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test @Timeout(30)
    void should_allocate_distinct_adjacent_codes_for_concurrent_creates() throws Exception {
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                hr();
                try {
                    start.await(10, TimeUnit.SECONDS);
                    return Integer.parseInt(create(new EmployeeFixture()).code().substring(2));
                } finally { SecurityContextHolder.clearContext(); }
            };
            var a = executor.submit(task);
            var b = executor.submit(task);
            assertThat(Math.abs(a.get(20, TimeUnit.SECONDS) - b.get(20, TimeUnit.SECONDS))).isEqualTo(1);
        }
    }

    @Test void should_reject_missing_and_deleted_organization_references() {
        assertThatThrownBy(() -> create(new EmployeeFixture().department(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> create(new EmployeeFixture().position(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
        var department = departments.create(new CreateDepartmentRequest(code(), "Phòng", null), null);
        departments.delete(department.id(), null);
        assertThatThrownBy(() -> create(new EmployeeFixture().department(department.id())))
                .isInstanceOf(BusinessException.class);
    }

    @Test void should_enforce_unique_checks_and_foreign_keys_in_postgres() {
        var first = create(new EmployeeFixture().email(email()).cccd(identifier()));
        var second = create(new EmployeeFixture());
        UUID userId = link(first, "EMPLOYEE");
        for (String column : List.of("company_email", "cccd_hash", "code", "user_id")) {
            assertThatThrownBy(() -> jdbc.update("update employees set " + column
                    + " = (select " + column + " from employees where id = ?) where id = ?", first.id(), second.id()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        for (String column : List.of("department_id", "position_id", "manager_id", "user_id")) {
            assertThatThrownBy(() -> jdbc.update("update employees set " + column + " = ? where id = ?", UUID.randomUUID(), second.id()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("update employees set manager_id = id where id = ?", second.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update employees set cccd_last_four = '1234' where id = ?", second.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(relations.employeeIdForUser(userId)).contains(first.id());
    }
}
