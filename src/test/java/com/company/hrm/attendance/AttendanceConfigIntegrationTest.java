package com.company.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.hrm.attendance.dto.CreateAttendancePolicyRequest;
import com.company.hrm.attendance.dto.CreateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.CreateHolidayRequest;
import com.company.hrm.attendance.dto.CreateShiftRequest;
import com.company.hrm.attendance.dto.ShiftResponse;
import com.company.hrm.attendance.dto.UpdateAttendancePolicyRequest;
import com.company.hrm.attendance.dto.UpdateEmployeeShiftRequest;
import com.company.hrm.attendance.dto.UpdateHolidayRequest;
import com.company.hrm.attendance.dto.UpdateShiftRequest;
import com.company.hrm.attendance.entity.RoundingMode;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import com.company.hrm.attendance.service.AttendancePolicyService;
import com.company.hrm.attendance.service.EmployeeShiftService;
import com.company.hrm.attendance.service.HolidayService;
import com.company.hrm.attendance.service.ShiftService;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.organization.dto.CreateDepartmentRequest;
import com.company.hrm.organization.dto.CreatePositionRequest;
import com.company.hrm.organization.service.DepartmentService;
import com.company.hrm.organization.service.PositionService;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.testsupport.EmployeeFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "HR")
class AttendanceConfigIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SEED_HC_SHIFT_ID = UUID.fromString("019c9e00-0000-7000-8000-000000000301");
    private static final UUID SEED_NIGHT_SHIFT_ID = UUID.fromString("019c9e00-0000-7000-8000-000000000302");
    private static final UUID SEED_DEFAULT_POLICY_ID = UUID.fromString("019c9e00-0000-7000-8000-000000000311");

    @Autowired ShiftService shifts;
    @Autowired AttendancePolicyService policies;
    @Autowired HolidayService holidays;
    @Autowired EmployeeShiftService employeeShifts;
    @Autowired EmployeeService employees;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
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
    }

    @BeforeEach
    void restoreDefaultSeedState() {
        asHr();
        jdbc.update("DELETE FROM employee_shifts");
        jdbc.update("DELETE FROM holidays");
        jdbc.update("DELETE FROM shifts WHERE id NOT IN (?, ?)", SEED_HC_SHIFT_ID, SEED_NIGHT_SHIFT_ID);
        jdbc.update("UPDATE shifts SET is_default = (id = ?) WHERE id IN (?, ?)",
                SEED_HC_SHIFT_ID, SEED_HC_SHIFT_ID, SEED_NIGHT_SHIFT_ID);
        jdbc.update("DELETE FROM attendance_policies WHERE id <> ?", SEED_DEFAULT_POLICY_ID);
        jdbc.update("UPDATE attendance_policies SET is_default = true WHERE id = ?", SEED_DEFAULT_POLICY_ID);
    }

    private static void asHr() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr", "unused", AuthorityUtils.createAuthorityList("ROLE_HR")));
    }

    private UUID createEmployee(UUID managerId) {
        asHr();
        return employees.create(new EmployeeFixture().manager(managerId).build(), "trace-emp").id();
    }

    private UUID linkUser(UUID employeeId, String... roleCodes) {
        var firstRole = roles.findByCode(roleCodes[0]).orElseThrow();
        var user = users.saveAndFlush(new User(EmployeeFixture.email(), "unused", firstRole));
        for (int i = 1; i < roleCodes.length; i++) {
            var extraRole = roles.findByCode(roleCodes[i]).orElseThrow();
            jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?)", user.getId(), extraRole.getId());
        }
        jdbc.update("UPDATE employees SET user_id = ? WHERE id = ?", user.getId(), employeeId);
        return user.getId();
    }

    @Test
    void should_verify_seeded_shifts_and_support_overnight_shift_crud_and_validation() throws Exception {
        var hc = shifts.get(SEED_HC_SHIFT_ID);
        assertThat(hc.code()).isEqualTo("HC");
        assertThat(hc.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(hc.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(hc.breakStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(hc.breakEnd()).isEqualTo(LocalTime.of(13, 0));
        assertThat(hc.overnight()).isFalse();
        assertThat(hc.defaultShift()).isTrue();

        var night = shifts.get(SEED_NIGHT_SHIFT_ID);
        assertThat(night.code()).isEqualTo("NIGHT");
        assertThat(night.startTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(night.endTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(night.breakStart()).isEqualTo(LocalTime.of(2, 0));
        assertThat(night.breakEnd()).isEqualTo(LocalTime.of(3, 0));
        assertThat(night.overnight()).isTrue();
        assertThat(night.defaultShift()).isFalse();

        // Create an overnight shift whose break crosses midnight (23:30 -> 00:30) and make it default
        var created = shifts.create(new CreateShiftRequest(
                "  night_2 ",
                " Ca đêm 2 ",
                LocalTime.of(21, 0),
                LocalTime.of(5, 0),
                LocalTime.of(23, 30),
                LocalTime.of(0, 30),
                true,
                true), "trace-shift-create");
        assertThat(created.code()).isEqualTo("NIGHT_2");
        assertThat(created.overnight()).isTrue();
        assertThat(created.defaultShift()).isTrue();
        assertThat(shifts.get(SEED_HC_SHIFT_ID).defaultShift()).isFalse();

        // Switch default back to HC via update, then update NIGHT_2 with evening break (22:00 -> 22:30)
        shifts.update(SEED_HC_SHIFT_ID, new UpdateShiftRequest(
                "HC", hc.name(), hc.startTime(), hc.endTime(), hc.breakStart(), hc.breakEnd(), false, true), "trace-hc");
        var updated = shifts.update(created.id(), new UpdateShiftRequest(
                "NIGHT_2",
                "Ca đêm 2 cập nhật",
                LocalTime.of(21, 0),
                LocalTime.of(5, 0),
                LocalTime.of(22, 0),
                LocalTime.of(22, 30),
                true,
                false), "trace-shift-update");
        assertThat(updated.name()).isEqualTo("Ca đêm 2 cập nhật");
        assertThat(updated.defaultShift()).isFalse();

        // Reject invalid overnight/day timing and invalid breaks
        for (CreateShiftRequest invalid : List.of(
                // overnight=false but endTime <= startTime
                new CreateShiftRequest("BAD1", "Bad 1", LocalTime.of(17, 0), LocalTime.of(8, 0), null, null, false, false),
                // overnight=true but endTime >= startTime
                new CreateShiftRequest("BAD2", "Bad 2", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, true, false),
                // only one of breakStart/breakEnd provided
                new CreateShiftRequest("BAD3", "Bad 3", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), null, false, false),
                // break outside overnight shift window (12:00-13:00 not in 22:00-06:00)
                new CreateShiftRequest("BAD4", "Bad 4", LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), true, false),
                // break outside day shift window
                new CreateShiftRequest("BAD5", "Bad 5", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(16, 30), LocalTime.of(17, 30), false, false))) {
            assertThatThrownBy(() -> shifts.create(invalid, "trace"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));
        }

        // Reject changing code, unsetting default directly, or deleting default shift
        assertThatThrownBy(() -> shifts.update(created.id(), new UpdateShiftRequest(
                "CHANGED", "Tên", LocalTime.of(21, 0), LocalTime.of(5, 0), null, null, true, false), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));
        assertThatThrownBy(() -> shifts.update(SEED_HC_SHIFT_ID, new UpdateShiftRequest(
                "HC", hc.name(), hc.startTime(), hc.endTime(), hc.breakStart(), hc.breakEnd(), false, false), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));
        assertThatThrownBy(() -> shifts.delete(SEED_HC_SHIFT_ID, "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));

        // When shift is assigned to an employee, reject changing its hours or deleting it, but allow renaming
        UUID emp = createEmployee(null);
        var assignment = employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, created.id(), ShiftAssignmentType.DAILY, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)), "trace");
        assertThatThrownBy(() -> shifts.update(created.id(), new UpdateShiftRequest(
                "NIGHT_2", "Đổi giờ", LocalTime.of(22, 0), LocalTime.of(6, 0), null, null, true, false), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));
        assertThatThrownBy(() -> shifts.delete(created.id(), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));
        assertThat(shifts.update(created.id(), new UpdateShiftRequest(
                "NIGHT_2", "Đổi tên hợp lệ", LocalTime.of(21, 0), LocalTime.of(5, 0),
                LocalTime.of(22, 0), LocalTime.of(22, 30), true, false), "trace").name())
                .isEqualTo("Đổi tên hợp lệ");

        // After deleting the assignment, deleting the shift succeeds
        employeeShifts.delete(assignment.id(), "trace");
        mvc.perform(delete("/api/v1/shifts/" + created.id()).with(user("hr").roles("HR")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/shifts/" + created.id()).with(user("hr").roles("HR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_manage_attendance_policies_and_holidays_with_validation_and_filtering() throws Exception {
        var defaultPolicy = policies.getDefault();
        assertThat(defaultPolicy.id()).isEqualTo(SEED_DEFAULT_POLICY_ID);
        assertThat(defaultPolicy.code()).isEqualTo("DEFAULT");
        assertThat(defaultPolicy.lateGraceMinutes()).isEqualTo(15);
        assertThat(defaultPolicy.earlyLeaveGraceMinutes()).isZero();
        assertThat(defaultPolicy.roundingStepMinutes()).isEqualTo(15);
        assertThat(defaultPolicy.roundingMode()).isEqualTo(RoundingMode.NEAREST);
        assertThat(defaultPolicy.halfDayThresholdMinutes()).isEqualTo(240);
        assertThat(defaultPolicy.minFullDayMinutes()).isEqualTo(480);

        var createdPolicy = policies.create(new CreateAttendancePolicyRequest(
                "flex",
                "Chính sách linh hoạt",
                10,
                5,
                0,
                RoundingMode.NONE,
                210,
                450,
                new BigDecimal("1.50"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                false), "trace-policy");
        assertThat(createdPolicy.code()).isEqualTo("FLEX");

        // Reject roundingMode != NONE with step 0, or minFullDayMinutes < halfDayThresholdMinutes
        assertThatThrownBy(() -> policies.create(new CreateAttendancePolicyRequest(
                "BAD_P1", "Bad", 0, 0, 0, RoundingMode.UP, 240, 480,
                new BigDecimal("1.50"), new BigDecimal("2.00"), new BigDecimal("3.00"), false), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));
        assertThatThrownBy(() -> policies.create(new CreateAttendancePolicyRequest(
                "BAD_P2", "Bad", 0, 0, 15, RoundingMode.UP, 300, 240,
                new BigDecimal("1.50"), new BigDecimal("2.00"), new BigDecimal("3.00"), false), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));
        assertThatThrownBy(() -> policies.delete(SEED_DEFAULT_POLICY_ID, "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));

        policies.delete(createdPolicy.id(), "trace-policy-del");

        // Holidays CRUD, filtering, and duplicate date prevention
        var h1 = holidays.create(new CreateHolidayRequest(
                LocalDate.of(2026, 4, 30), "Ngày Giải phóng miền Nam", new BigDecimal("3.00")), "trace-h1");
        var h2 = holidays.create(new CreateHolidayRequest(
                LocalDate.of(2026, 5, 1), "Quốc tế Lao động", new BigDecimal("3.00")), "trace-h2");
        var h3 = holidays.create(new CreateHolidayRequest(
                LocalDate.of(2026, 9, 2), "Quốc khánh", new BigDecimal("3.00")), "trace-h3");

        // Duplicate holidayDate on create and update -> 409 Conflict
        assertThatThrownBy(() -> holidays.create(new CreateHolidayRequest(
                LocalDate.of(2026, 9, 2), "Trùng ngày lễ", new BigDecimal("3.00")), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));
        assertThatThrownBy(() -> holidays.update(h2.id(), new UpdateHolidayRequest(
                LocalDate.of(2026, 4, 30), "Đổi sang ngày đã có", new BigDecimal("3.00")), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT));

        var mayAndSep = holidays.list(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30), PageRequest.of(0, 10));
        assertThat(mayAndSep.getContent()).extracting("id").containsExactly(h2.id(), h3.id());

        var updatedH1 = holidays.update(h1.id(), new UpdateHolidayRequest(
                LocalDate.of(2026, 4, 30), "Giải phóng miền Nam 30/4", new BigDecimal("3.50")), "trace-h1-up");
        assertThat(updatedH1.multiplier()).isEqualByComparingTo("3.50");

        holidays.delete(h1.id(), "trace-h1-del");
        assertThat(holidays.list(null, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void should_assign_shifts_enforce_override_priority_and_prevent_overlaps_including_open_ended_default() throws Exception {
        UUID emp = createEmployee(null);
        var swingShift = shifts.create(new CreateShiftRequest(
                "SWING", "Ca chiều", LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.of(18, 0), LocalTime.of(18, 30), false, false), "trace");

        // 1. Before any employee_shifts assignment, effective falls back to company default (HC)
        var fallback = employeeShifts.effective(emp, LocalDate.of(2026, 6, 1));
        assertThat(fallback.companyDefaultFallback()).isTrue();
        assertThat(fallback.assignmentId()).isNull();
        assertThat(fallback.shift().id()).isEqualTo(SEED_HC_SHIFT_ID);

        // 2. Assign open-ended DEFAULT shift (SWING) starting 2026-06-01 (endDate = null)
        var defaultAssign = employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, swingShift.id(), ShiftAssignmentType.DEFAULT, LocalDate.of(2026, 6, 1), null), "trace");

        // Overlapping another DEFAULT (finite or open-ended) on/after 2026-06-01 must fail with SHIFT_ASSIGNMENT_OVERLAP
        for (LocalDate[] range : List.of(
                new LocalDate[]{LocalDate.of(2026, 6, 1), null},
                new LocalDate[]{LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 30)},
                new LocalDate[]{LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 1)})) {
            assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                    emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DEFAULT, range[0], range[1]), "trace"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SHIFT_ASSIGNMENT_OVERLAP));
        }

        // Non-overlapping DEFAULT before 2026-06-01 is allowed
        var earlierDefault = employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DEFAULT,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)), "trace");
        assertThat(earlierDefault.id()).isNotNull();

        // 3. Assign WEEKLY override (NIGHT shift) for 2026-06-08 .. 2026-06-14 (7 days = end - start == 6)
        var weeklyAssign = employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_NIGHT_SHIFT_ID, ShiftAssignmentType.WEEKLY,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14)), "trace");

        // Reject WEEKLY > 7 days (e.g., 2026-06-15 .. 2026-06-22 -> 8 days) or null endDate
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_NIGHT_SHIFT_ID, ShiftAssignmentType.WEEKLY,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 22)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_NIGHT_SHIFT_ID, ShiftAssignmentType.WEEKLY,
                LocalDate.of(2026, 6, 15), null), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));

        // Reject overlapping WEEKLY
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.WEEKLY,
                LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 20)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SHIFT_ASSIGNMENT_OVERLAP));

        // 4. Assign DAILY override (HC shift) on 2026-06-10 (inside the WEEKLY & DEFAULT window)
        var dailyAssign = employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)), "trace");

        // Reject DAILY with mismatched endDate or duplicate DAILY on same date
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_VALIDATION_ERROR));
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_NIGHT_SHIFT_ID, ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SHIFT_ASSIGNMENT_OVERLAP));

        // 5. Verify priority: DAILY (2026-06-10 -> HC) > WEEKLY (2026-06-09 -> NIGHT) > DEFAULT (2026-06-20 -> SWING)
        var onDaily = employeeShifts.effective(emp, LocalDate.of(2026, 6, 10));
        assertThat(onDaily.assignmentId()).isEqualTo(dailyAssign.id());
        assertThat(onDaily.assignmentType()).isEqualTo(ShiftAssignmentType.DAILY);
        assertThat(onDaily.companyDefaultFallback()).isFalse();
        assertThat(onDaily.shift().id()).isEqualTo(SEED_HC_SHIFT_ID);

        var onWeekly = employeeShifts.effective(emp, LocalDate.of(2026, 6, 9));
        assertThat(onWeekly.assignmentId()).isEqualTo(weeklyAssign.id());
        assertThat(onWeekly.assignmentType()).isEqualTo(ShiftAssignmentType.WEEKLY);
        assertThat(onWeekly.shift().id()).isEqualTo(SEED_NIGHT_SHIFT_ID);

        var onDefault = employeeShifts.effective(emp, LocalDate.of(2026, 6, 20));
        assertThat(onDefault.assignmentId()).isEqualTo(defaultAssign.id());
        assertThat(onDefault.assignmentType()).isEqualTo(ShiftAssignmentType.DEFAULT);
        assertThat(onDefault.shift().id()).isEqualTo(swingShift.id());
    }

    @Test
    void should_reject_shift_assignment_for_terminated_or_deleted_employee() throws Exception {
        var dept = departments.create(new CreateDepartmentRequest("DEP_ATT", "Phòng Công", null), "trace");
        var pos = positions.create(new CreatePositionRequest("POS_ATT", "Chuyên viên"), "trace");
        UUID emp = employees.create(new EmployeeFixture()
                .email(EmployeeFixture.email())
                .cccd(EmployeeFixture.identifier())
                .department(dept.id())
                .position(pos.id())
                .complete(), "trace").id();
        employees.activate(emp, "trace");
        employees.terminate(emp, new TerminateEmployeeRequest(LocalDate.of(2026, 11, 20), "Nghỉ việc"), "trace");

        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE));

        employees.delete(emp, "trace");
        assertThatThrownBy(() -> employeeShifts.create(new CreateEmployeeShiftRequest(
                emp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DAILY,
                LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21)), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_RESOURCE_NOT_FOUND));

        // Verify pagination sort whitelist and size cap (200) across all 4 endpoints
        for (String path : List.of(
                "/api/v1/shifts",
                "/api/v1/attendance-policies",
                "/api/v1/holidays",
                "/api/v1/employee-shifts")) {
            mvc.perform(get(path).with(user("hr").roles("HR")).param("sort", "unknownField,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
            mvc.perform(get(path).with(user("hr").roles("HR")).param("size", "250"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(200));
        }

        // Verify 404 when no company default shift exists and employee has no assignment
        UUID activeEmp = createEmployee(null);
        jdbc.update("UPDATE shifts SET is_default = false WHERE is_default = true");
        mvc.perform(get("/api/v1/employee-shifts/effective")
                .param("employeeId", activeEmp.toString())
                .param("date", "2026-06-01")
                .with(user("hr").roles("HR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_block_concurrent_overlapping_shift_assignments_in_postgresql() throws Exception {
        UUID emp = createEmployee(null);
        try (Connection first = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Connection second = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            int secondPid;
            try (var st = second.createStatement(); var rs = st.executeQuery("SELECT pg_backend_pid()")) {
                rs.next();
                secondPid = rs.getInt(1);
            }
            String insertSql = """
                    INSERT INTO employee_shifts(id, employee_id, shift_id, assignment_type, start_date, end_date,
                        created_at, updated_at, created_by, updated_by, version)
                    VALUES ('%s', '%s', '%s', 'WEEKLY', '%s', '%s',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test', 'test', 0)
                    """;
            try (var st = first.createStatement()) {
                st.executeUpdate(insertSql.formatted(
                        UUID.randomUUID(), emp, SEED_HC_SHIFT_ID, "2026-07-01", "2026-07-07"));
            }
            var loser = executor.submit(() -> {
                try (var st = second.createStatement()) {
                    st.setQueryTimeout(10);
                    st.executeUpdate(insertSql.formatted(
                            UUID.randomUUID(), emp, SEED_NIGHT_SHIFT_ID, "2026-07-05", "2026-07-10"));
                    second.commit();
                    return "COMMITTED";
                } catch (SQLException e) {
                    second.rollback();
                    return e.getSQLState() + ":" + e.getMessage();
                }
            });
            try {
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                        jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, secondPid));
            } finally {
                first.commit();
            }
            String outcome = loser.get(10, TimeUnit.SECONDS);
            assertThat(outcome).startsWith("23P01:").contains("ex_employee_shifts_overlap");
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM employee_shifts WHERE employee_id = ?", Integer.class, emp))
                .isEqualTo(1);
    }

    @Test
    void should_enforce_rbac_and_subordinate_scope_across_http_and_service_proxies() throws Exception {
        UUID managerEmp = createEmployee(null);
        UUID subordinateEmp = createEmployee(managerEmp);
        UUID unrelatedEmp = createEmployee(null);

        UUID managerUserId = linkUser(managerEmp, "MANAGER");
        UUID subordinateUserId = linkUser(subordinateEmp, "EMPLOYEE");

        var subShift = employeeShifts.create(new CreateEmployeeShiftRequest(
                subordinateEmp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DEFAULT, LocalDate.of(2026, 6, 1), null), "trace");
        var unrelatedShift = employeeShifts.create(new CreateEmployeeShiftRequest(
                unrelatedEmp, SEED_NIGHT_SHIFT_ID, ShiftAssignmentType.DEFAULT, LocalDate.of(2026, 6, 1), null), "trace");

        // 1. Unauthenticated -> 401 on all 4 endpoints
        for (String path : List.of(
                "/api/v1/shifts",
                "/api/v1/attendance-policies",
                "/api/v1/holidays",
                "/api/v1/employee-shifts")) {
            mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
        }

        // 2. EMPLOYEE and MANAGER can read shifts, policies, holidays, but cannot mutate (403)
        for (String role : List.of("EMPLOYEE", "MANAGER")) {
            mvc.perform(get("/api/v1/shifts").with(user("u").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/attendance-policies").with(user("u").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/attendance-policies/default").with(user("u").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/holidays").with(user("u").roles(role))).andExpect(status().isOk());

            mvc.perform(post("/api/v1/shifts").with(user("u").roles(role))
                    .contentType("application/json")
                    .content(json.writeValueAsString(new CreateShiftRequest(
                            "S1", "Ca 1", LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, false, false))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/holidays").with(user("u").roles(role))
                    .contentType("application/json")
                    .content(json.writeValueAsString(new CreateHolidayRequest(
                            LocalDate.of(2026, 1, 1), "Tết Dương lịch", new BigDecimal("3.00")))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/employee-shifts").with(user("u").roles(role))
                    .contentType("application/json")
                    .content(json.writeValueAsString(new CreateEmployeeShiftRequest(
                            subordinateEmp, SEED_HC_SHIFT_ID, ShiftAssignmentType.DAILY,
                            LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2)))))
                    .andExpect(status().isForbidden());
        }

        // 3. MANAGER can view subordinate's shift assignment & effective shift, but NOT unrelated employee's
        mvc.perform(get("/api/v1/employee-shifts/" + subShift.id())
                .with(user(managerUserId.toString()).roles("MANAGER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/employee-shifts/effective")
                .param("employeeId", subordinateEmp.toString())
                .param("date", "2026-06-15")
                .with(user(managerUserId.toString()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shift.code").value("HC"));
        mvc.perform(get("/api/v1/employee-shifts")
                .with(user(managerUserId.toString()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(subShift.id().toString()));
        mvc.perform(get("/api/v1/employee-shifts/" + unrelatedShift.id())
                .with(user(managerUserId.toString()).roles("MANAGER")))
                .andExpect(status().isForbidden());

        // 4. EMPLOYEE can view own shift assignment & effective shift, but NOT another employee's
        mvc.perform(get("/api/v1/employee-shifts/" + subShift.id())
                .with(user(subordinateUserId.toString()).roles("EMPLOYEE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/employee-shifts/" + unrelatedShift.id())
                .with(user(subordinateUserId.toString()).roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        // 5. Direct service proxy security checks & audit log verification
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subordinateUserId.toString(), "unused", AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));
        assertThat(employeeShifts.get(subShift.id()).id()).isEqualTo(subShift.id());
        assertThatThrownBy(() -> employeeShifts.get(unrelatedShift.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> employeeShifts.delete(subShift.id(), "trace"))
                .isInstanceOf(AccessDeniedException.class);

        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type IN ('Shift', 'AttendancePolicy', 'Holiday', 'EmployeeShift')",
                Integer.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(2);
    }
}
