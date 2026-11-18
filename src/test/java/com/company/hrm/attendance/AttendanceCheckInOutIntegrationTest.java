package com.company.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.hrm.attendance.dto.CreateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.CreateShiftRequest;
import com.company.hrm.attendance.entity.AttendanceSource;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import com.company.hrm.attendance.service.AttendanceService;
import com.company.hrm.attendance.service.EmployeeShiftService;
import com.company.hrm.attendance.service.ShiftService;
import com.company.hrm.auth.dto.AuthDtos.Login;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.auth.service.AuthService;
import com.company.hrm.common.config.TimeConfig;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.organization.dto.CreateDepartmentRequest;
import com.company.hrm.organization.dto.CreatePositionRequest;
import com.company.hrm.organization.service.DepartmentService;
import com.company.hrm.organization.service.PositionService;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.testsupport.EmployeeFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AttendanceCheckInOutIntegrationTest.ClockConfig.class)
class AttendanceCheckInOutIntegrationTest extends AbstractIntegrationTest {

    private static final String CHECK_IN_PATH = "/api/v1/attendance/check-in";
    private static final String CHECK_OUT_PATH = "/api/v1/attendance/check-out";
    private static final UUID SEED_HC_SHIFT_ID = UUID.fromString("019c9e00-0000-7000-8000-000000000301");
    private static final UUID SEED_NIGHT_SHIFT_ID = UUID.fromString("019c9e00-0000-7000-8000-000000000302");
    private static final Instant DEFAULT_INSTANT = Instant.parse("2026-06-15T01:05:00Z"); // 2026-06-15 08:05 +07:00

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testAttendanceClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(DEFAULT_INSTANT);

        @Override
        public ZoneId getZone() {
            return TimeConfig.BUSINESS_ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        void setInstant(Instant instant) {
            current.set(instant);
        }

        void advance(Duration duration) {
            current.updateAndGet(now -> now.plus(duration));
        }
    }

    @Autowired AttendanceService attendance;
    @Autowired ShiftService shifts;
    @Autowired EmployeeShiftService employeeShifts;
    @Autowired EmployeeService employees;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("hrm.auth.rate-limit", () -> "1000");
    }

    @BeforeEach
    void resetStateBefore() {
        cleanAttendanceTables();
    }

    @AfterEach
    void resetStateAfter() {
        SecurityContextHolder.clearContext();
        cleanAttendanceTables();
    }

    private void cleanAttendanceTables() {
        clock.setInstant(DEFAULT_INSTANT);
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM employee_shifts");
        jdbc.update("DELETE FROM shifts WHERE id NOT IN (?, ?)", SEED_HC_SHIFT_ID, SEED_NIGHT_SHIFT_ID);
        jdbc.update("UPDATE shifts SET is_default = (id = ?) WHERE id IN (?, ?)",
                SEED_HC_SHIFT_ID, SEED_HC_SHIFT_ID, SEED_NIGHT_SHIFT_ID);
    }

    private static void asHr() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr-actor", "unused", AuthorityUtils.createAuthorityList("ROLE_HR")));
    }

    private static void asRole(UUID userId, String roleCode) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        "unused",
                        AuthorityUtils.createAuthorityList("ROLE_" + roleCode)));
    }

    private UUID createEmployeeWithStatus(EmployeeStatus status) {
        asHr();
        var dept = departments.create(new CreateDepartmentRequest(EmployeeFixture.code(), "Phòng Công", null), "trace");
        var pos = positions.create(new CreatePositionRequest(EmployeeFixture.code(), "Chuyên viên"), "trace");
        UUID empId = employees.create(new EmployeeFixture()
                .email(EmployeeFixture.email())
                .cccd(EmployeeFixture.identifier())
                .department(dept.id())
                .position(pos.id())
                .complete(), "trace").id();
        if (status != EmployeeStatus.DRAFT) {
            jdbc.update("UPDATE employees SET status = ? WHERE id = ?", status.name(), empId);
        }
        return empId;
    }

    private record LinkedAccount(UUID employeeId, UUID userId, String email, String password) {}

    private LinkedAccount linkEmployeeUser(EmployeeStatus status, String roleCode) {
        UUID empId = createEmployeeWithStatus(status);
        String userEmail = EmployeeFixture.email();
        String password = "Password#2026";
        UUID userId = users.saveAndFlush(new User(
                userEmail,
                passwordEncoder.encode(password),
                roles.findByCode(roleCode).orElseThrow())).getId();
        jdbc.update("UPDATE employees SET user_id = ? WHERE id = ?", userId, empId);
        return new LinkedAccount(empId, userId, userEmail, password);
    }

    private String loginAccessToken(LinkedAccount account) {
        return auth.login(new Login(account.email(), account.password()), "127.0.0.1").accessToken();
    }

    @Test
    void should_check_in_and_check_out_with_server_clock_and_record_audit_logs() throws Exception {
        var empAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        var mgrAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "MANAGER");

        // 1. EMPLOYEE checks in with explicit source=WEB and checks out with empty body
        String empCheckInBody = mvc.perform(post(CHECK_IN_PATH)
                        .header("Authorization", "Bearer " + loginAccessToken(empAccount))
                        .header("X-Correlation-ID", "ci-emp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(empAccount.employeeId().toString()))
                .andExpect(jsonPath("$.shiftId").value(SEED_HC_SHIFT_ID.toString()))
                .andExpect(jsonPath("$.workDate").value("2026-06-15"))
                .andExpect(jsonPath("$.checkInAt").value("2026-06-15T01:05:00Z"))
                .andExpect(jsonPath("$.checkInSource").value("WEB"))
                .andExpect(jsonPath("$.checkOutAt").doesNotExist())
                .andExpect(jsonPath("$.status").value("MISSING_CHECKOUT"))
                .andReturn().getResponse().getContentAsString();
        UUID empRecordId = UUID.fromString(json.readTree(empCheckInBody).get("id").asText());

        clock.advance(Duration.ofHours(9)); // 2026-06-15 17:05 +07:00
        mvc.perform(post(CHECK_OUT_PATH)
                        .header("Authorization", "Bearer " + loginAccessToken(empAccount))
                        .header("X-Correlation-ID", "co-emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empRecordId.toString()))
                .andExpect(jsonPath("$.workDate").value("2026-06-15"))
                .andExpect(jsonPath("$.checkInAt").value("2026-06-15T01:05:00Z"))
                .andExpect(jsonPath("$.checkOutAt").value("2026-06-15T10:05:00Z"))
                .andExpect(jsonPath("$.checkOutSource").value("WEB"))
                .andExpect(jsonPath("$.status").value("PRESENT"));

        // 2. MANAGER checks in with empty JSON body (defaults to WEB) and checks out
        clock.setInstant(Instant.parse("2026-06-16T01:00:00Z"));
        String mgrCheckInBody = mvc.perform(post(CHECK_IN_PATH)
                        .header("Authorization", "Bearer " + loginAccessToken(mgrAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(mgrAccount.employeeId().toString()))
                .andExpect(jsonPath("$.checkInSource").value("WEB"))
                .andExpect(jsonPath("$.workDate").value("2026-06-16"))
                .andReturn().getResponse().getContentAsString();
        UUID mgrRecordId = UUID.fromString(json.readTree(mgrCheckInBody).get("id").asText());

        clock.advance(Duration.ofHours(8));
        mvc.perform(post(CHECK_OUT_PATH)
                        .header("Authorization", "Bearer " + loginAccessToken(mgrAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mgrRecordId.toString()))
                .andExpect(jsonPath("$.status").value("PRESENT"));

        Integer empAuditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type = 'AttendanceRecord' AND entity_id = ? AND action IN ('CHECK_IN', 'CHECK_OUT')",
                Integer.class, empRecordId);
        assertThat(empAuditCount).isEqualTo(2);
    }

    @Test
    void should_reject_check_in_and_check_out_when_employee_is_not_active() throws Exception {
        var draftAccount = linkEmployeeUser(EmployeeStatus.DRAFT, "EMPLOYEE");
        var onLeaveAccount = linkEmployeeUser(EmployeeStatus.ON_LEAVE_LONG, "EMPLOYEE");
        var terminatedAccount = linkEmployeeUser(EmployeeStatus.TERMINATED, "EMPLOYEE");
        var deletedAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        jdbc.update("UPDATE employees SET deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(DEFAULT_INSTANT), deletedAccount.employeeId());

        UUID unlinkedUserId = users.saveAndFlush(new User(
                EmployeeFixture.email(),
                passwordEncoder.encode("Password#2026"),
                roles.findByCode("EMPLOYEE").orElseThrow())).getId();

        for (UUID actorUserId : List.of(
                draftAccount.userId(),
                onLeaveAccount.userId(),
                terminatedAccount.userId(),
                deletedAccount.userId(),
                unlinkedUserId)) {
            mvc.perform(post(CHECK_IN_PATH).with(user(actorUserId.toString()).roles("EMPLOYEE")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ATTENDANCE_EMPLOYEE_INELIGIBLE"));
            mvc.perform(post(CHECK_OUT_PATH).with(user(actorUserId.toString()).roles("EMPLOYEE")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ATTENDANCE_EMPLOYEE_INELIGIBLE"));
        }

        // Invalid enum value -> 400
        var activeAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        mvc.perform(post(CHECK_IN_PATH)
                        .with(user(activeAccount.userId().toString()).roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));

        // Missing company default shift and no employee shift assignment -> 404
        jdbc.update("UPDATE shifts SET is_default = false WHERE is_default = true");
        mvc.perform(post(CHECK_IN_PATH).with(user(activeAccount.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_reject_duplicate_check_in_and_check_out_without_check_in() throws Exception {
        var account = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        String token = loginAccessToken(account);

        // Check-out without check-in -> 409
        mvc.perform(post(CHECK_OUT_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN"));

        // First check-in succeeds
        mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Duplicate check-in while open -> 409
        mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_DUPLICATE_CHECK_IN"));

        // Check-out succeeds
        clock.advance(Duration.ofHours(8));
        String freshToken = loginAccessToken(account);
        mvc.perform(post(CHECK_OUT_PATH).header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));

        // Duplicate check-out -> 409
        mvc.perform(post(CHECK_OUT_PATH).header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN"));

        // Duplicate check-in on same workDate after check-out -> 409
        mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_DUPLICATE_CHECK_IN"));
    }

    @Test
    void should_reject_future_punch_time_and_check_out_before_check_in() throws Exception {
        var account = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        asRole(account.userId(), "EMPLOYEE");

        Instant futureTime = DEFAULT_INSTANT.plusSeconds(60);
        assertThatThrownBy(() -> attendance.checkInAt(account.employeeId(), futureTime, AttendanceSource.WEB, "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_FUTURE_TIME));

        // Check in at 08:05 +07:00
        mvc.perform(post(CHECK_IN_PATH).with(user(account.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk());

        // Future check-out -> 422 ATTENDANCE_FUTURE_TIME
        asRole(account.userId(), "EMPLOYEE");
        assertThatThrownBy(() -> attendance.checkOutAt(account.employeeId(), futureTime, AttendanceSource.WEB, "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_FUTURE_TIME));

        // Check-out before check-in -> 409 ATTENDANCE_INVALID_ORDER
        Instant earlierTime = DEFAULT_INSTANT.minusSeconds(600);
        assertThatThrownBy(() -> attendance.checkOutAt(account.employeeId(), earlierTime, AttendanceSource.WEB, "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_INVALID_ORDER));

        // Rolling back server clock before check_in_at -> HTTP 409 ATTENDANCE_INVALID_ORDER
        clock.setInstant(DEFAULT_INSTANT.minusSeconds(300));
        mvc.perform(post(CHECK_OUT_PATH).with(user(account.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_INVALID_ORDER"));
    }

    @Test
    void should_handle_overnight_shift_work_date_priority_at_0500_and_stale_missing_checkout() throws Exception {
        // 5a. Check-in before midnight on overnight shift -> check-out after midnight keeps work_date = start day
        var empNight = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        asHr();
        employeeShifts.create(new CreateEmployeeShiftRequest(
                empNight.employeeId(),
                SEED_NIGHT_SHIFT_ID,
                ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 15)), "trace");

        clock.setInstant(Instant.parse("2026-06-15T15:05:00Z")); // 22:05 +07:00 on 2026-06-15
        mvc.perform(post(CHECK_IN_PATH).with(user(empNight.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-15"))
                .andExpect(jsonPath("$.shiftId").value(SEED_NIGHT_SHIFT_ID.toString()));

        clock.setInstant(Instant.parse("2026-06-15T23:02:00Z")); // 06:02 +07:00 on 2026-06-16
        mvc.perform(post(CHECK_OUT_PATH).with(user(empNight.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-15"))
                .andExpect(jsonPath("$.status").value("PRESENT"));

        // 5b. Late check-in after midnight (00:30 +07:00) on overnight shift -> work_date = previous day
        var empLateNight = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        asHr();
        employeeShifts.create(new CreateEmployeeShiftRequest(
                empLateNight.employeeId(),
                SEED_NIGHT_SHIFT_ID,
                ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 17)), "trace");

        clock.setInstant(Instant.parse("2026-06-17T17:30:00Z")); // 00:30 +07:00 on 2026-06-18
        mvc.perform(post(CHECK_IN_PATH).with(user(empLateNight.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-17"))
                .andExpect(jsonPath("$.shiftId").value(SEED_NIGHT_SHIFT_ID.toString()));

        clock.setInstant(Instant.parse("2026-06-17T23:05:00Z")); // 06:05 +07:00 on 2026-06-18
        mvc.perform(post(CHECK_OUT_PATH).with(user(empLateNight.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-17"))
                .andExpect(jsonPath("$.status").value("PRESENT"));

        // 5c. 05:00 AM ambiguity: yesterday NIGHT (22:00-06:00, no record) vs today MORNING (06:00-14:00) -> prioritizes today
        var empMorning = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        asHr();
        var morningShift = shifts.create(new CreateShiftRequest(
                "MORNING_06",
                "Ca sáng sớm",
                LocalTime.of(6, 0),
                LocalTime.of(14, 0),
                null,
                null,
                false,
                false), "trace");
        employeeShifts.create(new CreateEmployeeShiftRequest(
                empMorning.employeeId(),
                SEED_NIGHT_SHIFT_ID,
                ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 19),
                LocalDate.of(2026, 6, 19)), "trace");
        employeeShifts.create(new CreateEmployeeShiftRequest(
                empMorning.employeeId(),
                morningShift.id(),
                ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 20)), "trace");

        clock.setInstant(Instant.parse("2026-06-19T22:00:00Z")); // 05:00 +07:00 on 2026-06-20
        mvc.perform(post(CHECK_IN_PATH).with(user(empMorning.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-20"))
                .andExpect(jsonPath("$.shiftId").value(morningShift.id().toString()));

        // 5d. Stale open record (forgot check-out yesterday) does NOT block today's check-in and cannot be checked out late
        var empStale = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        clock.setInstant(Instant.parse("2026-06-22T01:05:00Z")); // 08:05 +07:00 on 2026-06-22
        mvc.perform(post(CHECK_IN_PATH).with(user(empStale.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-22"))
                .andExpect(jsonPath("$.status").value("MISSING_CHECKOUT"));

        clock.setInstant(Instant.parse("2026-06-23T01:00:00Z")); // 08:00 +07:00 on 2026-06-23 (> 17:00 + 6h)
        mvc.perform(post(CHECK_OUT_PATH).with(user(empStale.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN"));

        mvc.perform(post(CHECK_IN_PATH).with(user(empStale.userId().toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate").value("2026-06-23"))
                .andExpect(jsonPath("$.status").value("MISSING_CHECKOUT"));

        String yesterdayStatus = jdbc.queryForObject(
                "SELECT status FROM attendance_records WHERE employee_id = ? AND work_date = '2026-06-22'",
                String.class, empStale.employeeId());
        assertThat(yesterdayStatus).isEqualTo("MISSING_CHECKOUT");
        assertThat(jdbc.queryForObject(
                "SELECT check_out_at FROM attendance_records WHERE employee_id = ? AND work_date = '2026-06-22'",
                Object.class, empStale.employeeId())).isNull();
    }

    @Test
    void should_allow_only_one_check_in_when_two_requests_arrive_concurrently() throws Exception {
        var account = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        String token = loginAccessToken(account);
        var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> task = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post(CHECK_IN_PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"source\":\"WEB\"}"))
                        .andReturn();
            };
            var firstFuture = executor.submit(task);
            var secondFuture = executor.submit(task);

            MvcResult first = firstFuture.get(15, TimeUnit.SECONDS);
            MvcResult second = secondFuture.get(15, TimeUnit.SECONDS);
            List<MvcResult> results = List.of(first, second);

            assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 409);

            MvcResult rejected = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(json.readTree(rejected.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("ATTENDANCE_DUPLICATE_CHECK_IN");
        }

        Integer recordCount = jdbc.queryForObject(
                "SELECT count(*) FROM attendance_records WHERE employee_id = ?",
                Integer.class, account.employeeId());
        assertThat(recordCount).isEqualTo(1);

        UUID recordId = jdbc.queryForObject(
                "SELECT id FROM attendance_records WHERE employee_id = ?",
                UUID.class, account.employeeId());
        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type = 'AttendanceRecord' AND entity_id = ? AND action = 'CHECK_IN'",
                Integer.class, recordId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void should_allow_only_one_check_out_when_two_requests_arrive_concurrently() throws Exception {
        var account = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        mvc.perform(post(CHECK_IN_PATH)
                        .header("Authorization", "Bearer " + loginAccessToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"WEB\"}"))
                .andExpect(status().isOk());

        clock.advance(Duration.ofHours(9));
        String token = loginAccessToken(account);
        var barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> task = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post(CHECK_OUT_PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"source\":\"WEB\"}"))
                        .andReturn();
            };
            var firstFuture = executor.submit(task);
            var secondFuture = executor.submit(task);

            MvcResult first = firstFuture.get(15, TimeUnit.SECONDS);
            MvcResult second = secondFuture.get(15, TimeUnit.SECONDS);
            List<MvcResult> results = List.of(first, second);

            assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 409);

            MvcResult rejected = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(json.readTree(rejected.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("ATTENDANCE_CHECK_OUT_WITHOUT_CHECK_IN");
        }

        UUID recordId = jdbc.queryForObject(
                "SELECT id FROM attendance_records WHERE employee_id = ?",
                UUID.class, account.employeeId());
        String status = jdbc.queryForObject(
                "SELECT status FROM attendance_records WHERE id = ?",
                String.class, recordId);
        assertThat(status).isEqualTo("PRESENT");

        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type = 'AttendanceRecord' AND entity_id = ? AND action = 'CHECK_OUT'",
                Integer.class, recordId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void should_enforce_rbac_on_http_and_service_proxy() throws Exception {
        // 1. Unauthenticated -> 401
        mvc.perform(post(CHECK_IN_PATH).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post(CHECK_OUT_PATH).with(anonymous())).andExpect(status().isUnauthorized());

        // 2. HR and ADMIN alone -> 403 on HTTP and AccessDeniedException on service proxy
        var hrAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "HR");
        var adminAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "ADMIN");
        for (var account : List.of(hrAccount, adminAccount)) {
            String token = loginAccessToken(account);
            mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));
            mvc.perform(post(CHECK_OUT_PATH).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));
        }

        asRole(hrAccount.userId(), "HR");
        assertThatThrownBy(() -> attendance.checkIn(null, "trace"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> attendance.checkOut(null, "trace"))
                .isInstanceOf(AccessDeniedException.class);

        asRole(adminAccount.userId(), "ADMIN");
        assertThatThrownBy(() -> attendance.checkInAt(adminAccount.employeeId(), DEFAULT_INSTANT, AttendanceSource.WEB, "trace"))
                .isInstanceOf(AccessDeniedException.class);

        // 3. EMPLOYEE with must_change_password = true -> 403 until password is changed
        var mustChangeAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        jdbc.update("UPDATE users SET must_change_password = true WHERE id = ?", mustChangeAccount.userId());
        String blockedToken = loginAccessToken(mustChangeAccount);
        mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + blockedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));

        // 4. EMPLOYEE and MANAGER -> 200 on both HTTP and service proxy
        var empAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "EMPLOYEE");
        asRole(empAccount.userId(), "EMPLOYEE");
        var checkInRes = attendance.checkIn(null, "trace");
        assertThat(checkInRes.employeeId()).isEqualTo(empAccount.employeeId());
        clock.advance(Duration.ofHours(4));
        var checkOutRes = attendance.checkOut(null, "trace");
        assertThat(checkOutRes.id()).isEqualTo(checkInRes.id());

        var mgrAccount = linkEmployeeUser(EmployeeStatus.ACTIVE, "MANAGER");
        mvc.perform(post(CHECK_IN_PATH).header("Authorization", "Bearer " + loginAccessToken(mgrAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(mgrAccount.employeeId().toString()));
    }
}
