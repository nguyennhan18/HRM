package com.company.hrm.employee;

import static com.company.hrm.testsupport.EmployeeFixture.code;
import static com.company.hrm.testsupport.EmployeeFixture.email;
import static com.company.hrm.testsupport.EmployeeFixture.identifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import com.company.hrm.auth.service.AuthService;
import com.company.hrm.auth.service.Notifier;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.employee.dto.CreateEmployeeRequest;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.dto.TerminateEmployeeRequest;
import com.company.hrm.employee.dto.UpdateEmployeeRequest;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.employee.entity.Gender;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.organization.dto.CreateDepartmentRequest;
import com.company.hrm.organization.dto.CreatePositionRequest;
import com.company.hrm.organization.service.DepartmentService;
import com.company.hrm.organization.service.PositionService;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.testsupport.EmployeeFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "hr-onboarding", roles = "HR")
@ExtendWith(OutputCaptureExtension.class)
class EmployeeOnboardingIntegrationTest extends AbstractIntegrationTest {

    private static final String EMP_PATH = "/api/v1/employees";
    private static final String AUTH_PATH = "/api/v1/auth";
    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 18);

    @Autowired EmployeeService employees;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean Notifier notifier;

    private UUID departmentId;
    private UUID positionId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("hrm.auth.rate-limit", () -> "1000");
    }

    @BeforeEach
    void setUpOrganization() {
        asHr();
        clearInvocations(notifier);
        departmentId = departments.create(new CreateDepartmentRequest(code(), "Phòng Kỹ thuật", null), null).id();
        positionId = positions.create(new CreatePositionRequest(code(), "Kỹ sư phần mềm"), null).id();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    static void asHr() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "hr-onboarding", "unused", AuthorityUtils.createAuthorityList("ROLE_HR")));
    }

    @Test
    void should_complete_onboarding_flow_from_draft_to_password_change_and_self_profile_access(
            CapturedOutput output) throws Exception {
        String staffEmail = email();
        String cccd = identifier();
        String bank = identifier();

        // 1. HR creates DRAFT profile with only fullName
        String createBody = json.writeValueAsString(Map.of("fullName", "  Nguyễn Văn Onboarding  "));
        String createdJson = mvc.perform(post(EMP_PATH)
                        .with(user("hr-onboarding").roles("HR"))
                        .header("X-Correlation-ID", "onboard-create-trace")
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("^NV[0-9]{6}$")))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Onboarding"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.companyEmail").isEmpty())
                .andExpect(jsonPath("$.departmentId").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID employeeId = UUID.fromString(json.readTree(createdJson).get("id").asText());

        // 2. HR updates DRAFT profile to add mandatory and optional fields
        Map<String, Object> updatePayload = Map.of(
                "fullName", "Nguyễn Văn Onboarding",
                "dateOfBirth", "1998-05-20",
                "gender", "MALE",
                "personalEmail", "personal." + staffEmail,
                "companyEmail", "  " + staffEmail.toUpperCase() + "  ",
                "phone", "0901234567",
                "address", "123 Lê Lợi, Quận 1",
                "cccd", cccd,
                "bankAccountNumber", bank,
                "departmentId", departmentId.toString());
        mvc.perform(put(EMP_PATH + "/" + employeeId)
                        .with(user("hr-onboarding").roles("HR"))
                        .header("X-Correlation-ID", "onboard-update-1")
                        .contentType("application/json")
                        .content(json.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.companyEmail").value(staffEmail))
                .andExpect(jsonPath("$.cccdLastFour").value(cccd.substring(cccd.length() - 4)))
                .andExpect(jsonPath("$.bankAccountLastFour").value(bank.substring(bank.length() - 4)));

        // Second update adds positionId and startDate without resending cccd/bankAccountNumber -> preserves existing hashes/lastFour
        Map<String, Object> secondUpdatePayload = Map.of(
                "fullName", "Nguyễn Văn Chính Thức",
                "companyEmail", staffEmail,
                "departmentId", departmentId.toString(),
                "positionId", positionId.toString(),
                "startDate", START_DATE.toString());
        mvc.perform(put(EMP_PATH + "/" + employeeId)
                        .with(user("hr-onboarding").roles("HR"))
                        .header("X-Correlation-ID", "onboard-update-2")
                        .contentType("application/json")
                        .content(json.writeValueAsString(secondUpdatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn Chính Thức"))
                .andExpect(jsonPath("$.positionId").value(positionId.toString()))
                .andExpect(jsonPath("$.startDate").value(START_DATE.toString()))
                .andExpect(jsonPath("$.cccdLastFour").value(cccd.substring(cccd.length() - 4)))
                .andExpect(jsonPath("$.bankAccountLastFour").value(bank.substring(bank.length() - 4)));

        // 3. HR activates the employee: POST /api/v1/employees/{id}/activate
        mvc.perform(post(EMP_PATH + "/" + employeeId + "/activate")
                        .with(user("hr-onboarding").roles("HR"))
                        .header("X-Correlation-ID", "onboard-activate-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.companyEmail").value(staffEmail))
                .andExpect(jsonPath("$.cccdLastFour").value(cccd.substring(cccd.length() - 4)));

        // Capture temporary password sent via Notifier
        ArgumentCaptor<String> tempPasswordCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).onboardingInvite(eq(staffEmail), tempPasswordCaptor.capture());
        String temporaryPassword = tempPasswordCaptor.getValue();
        assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(10);

        // Verify User entity and Employee.user_id link in DB
        User createdUser = users.findByEmail(staffEmail).orElseThrow();
        assertThat(createdUser.isEnabled()).isTrue();
        assertThat(createdUser.isMustChangePassword()).isTrue();
        assertThat(users.findRoleCodesById(createdUser.getId())).containsExactly("EMPLOYEE");
        UUID linkedUserId = jdbc.queryForObject("select user_id from employees where id = ?", UUID.class, employeeId);
        assertThat(linkedUserId).isEqualTo(createdUser.getId());

        // Verify AuditLogs for CREATE, UPDATE, ACTIVATE on Employee and USER_PROVISIONED on User
        assertThat(jdbc.queryForList(
                "select action from audit_logs where entity_type = 'Employee' and entity_id = ? order by created_at asc, id asc",
                String.class, employeeId))
                .containsExactly("CREATE", "UPDATE", "UPDATE", "ACTIVATE");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'User' and entity_id = ? and action = 'USER_PROVISIONED'",
                Integer.class, createdUser.getId())).isEqualTo(1);
        String userAuditAfter = jdbc.queryForObject(
                "select after_json::text from audit_logs where entity_type = 'User' and entity_id = ? and action = 'USER_PROVISIONED'",
                String.class, createdUser.getId());
        assertThat(json.readTree(userAuditAfter).get("mustChangePassword").asBoolean()).isTrue();
        assertThat(userAuditAfter).doesNotContain(temporaryPassword);

        // 4. Employee logs in with temporary password -> receives mustChangePassword = true
        String loginResponse = mvc.perform(post(AUTH_PATH + "/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", staffEmail, "password", temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode initialTokens = json.readTree(loginResponse);
        String tempAccessToken = initialTokens.get("accessToken").asText();
        String tempRefreshToken = initialTokens.get("refreshToken").asText();

        // 5. Option 2B enforcement: business APIs return 403 Forbidden while must_change_password is true
        mvc.perform(get(EMP_PATH + "/" + employeeId)
                        .header("Authorization", "Bearer " + tempAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));
        mvc.perform(get(EMP_PATH)
                        .header("Authorization", "Bearer " + tempAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));
        mvc.perform(get("/api/v1/departments")
                        .header("Authorization", "Bearer " + tempAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));

        // Refresh is still permitted while must_change_password is true and preserves mustChangePassword = true
        String refreshedResponse = mvc.perform(post(AUTH_PATH + "/refresh")
                        .header("Authorization", "Bearer " + tempAccessToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", tempRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String refreshedAccessToken = json.readTree(refreshedResponse).get("accessToken").asText();

        // 6. Employee changes password via /api/v1/auth/change-password -> clears must_change_password and revokes sessions
        String permanentPassword = "Permanent#Password2026";
        mvc.perform(post(AUTH_PATH + "/change-password")
                        .header("Authorization", "Bearer " + refreshedAccessToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "oldPassword", temporaryPassword,
                                "password", permanentPassword))))
                .andExpect(status().isNoContent());

        assertThat(users.findById(createdUser.getId()).orElseThrow().isMustChangePassword()).isFalse();

        // Old token is immediately revoked after password change
        mvc.perform(get(EMP_PATH + "/" + employeeId)
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isUnauthorized());

        // 7. Employee logs in with new password -> mustChangePassword = false -> can access own profile, denied on others
        String secondLoginResponse = mvc.perform(post(AUTH_PATH + "/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", staffEmail, "password", permanentPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String activeAccessToken = json.readTree(secondLoginResponse).get("accessToken").asText();

        mvc.perform(get(EMP_PATH + "/" + employeeId)
                        .header("Authorization", "Bearer " + activeAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cccdLastFour").value(cccd.substring(cccd.length() - 4)));

        asHr();
        EmployeeResponse otherEmployee = employees.create(new EmployeeFixture().build(), null);
        mvc.perform(get(EMP_PATH + "/" + otherEmployee.id())
                        .header("Authorization", "Bearer " + activeAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));

        assertThat(output.getAll()).doesNotContain(temporaryPassword, permanentPassword, cccd, bank);
    }

    @Test
    void should_reject_activation_with_422_and_list_all_missing_fields_when_draft_has_only_name() throws Exception {
        asHr();
        EmployeeResponse draftOnlyName = employees.create(
                new CreateEmployeeRequest("Chỉ Có Họ Tên", null, null, null, null, null, null, null, null, null, null, null, null),
                "draft-minimal");

        mvc.perform(post(EMP_PATH + "/" + draftOnlyName.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HRM_UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.detail").value(containsString("companyEmail")))
                .andExpect(jsonPath("$.detail").value(containsString("departmentId")))
                .andExpect(jsonPath("$.detail").value(containsString("positionId")))
                .andExpect(jsonPath("$.detail").value(containsString("startDate")))
                .andExpect(jsonPath("$.detail").value(containsString("cccd")));

        verify(notifier, never()).onboardingInvite(anyString(), anyString());
        assertThat(jdbc.queryForObject("select status from employees where id = ?", String.class, draftOnlyName.id()))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select user_id from employees where id = ?", UUID.class, draftOnlyName.id()))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyEmail", "departmentId", "positionId", "startDate", "cccd"})
    void should_reject_activation_when_any_single_mandatory_field_is_missing_and_allow_missing_optional_fields(
            String missingField) throws Exception {
        asHr();
        String staffEmail = "companyEmail".equals(missingField) ? null : email();
        UUID dept = "departmentId".equals(missingField) ? null : departmentId;
        UUID pos = "positionId".equals(missingField) ? null : positionId;
        LocalDate start = "startDate".equals(missingField) ? null : START_DATE;
        String cccd = "cccd".equals(missingField) ? null : identifier();

        // Optional fields (dateOfBirth, gender, personalEmail, phone, address, bankAccountNumber, managerId) are all null
        EmployeeResponse draft = employees.create(new CreateEmployeeRequest(
                "Nhân viên kiểm tra trường", null, null, null, staffEmail, null, null, cccd, null, dept, pos, null, start),
                "single-missing-test");

        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HRM_UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.detail").value(containsString(missingField)));

        verify(notifier, never()).onboardingInvite(anyString(), anyString());

        // Filling the missing mandatory field while keeping all optional fields null allows activation to succeed
        asHr();
        employees.update(draft.id(), new UpdateEmployeeRequest(
                "Nhân viên kiểm tra trường", null, null, null,
                staffEmail != null ? staffEmail : email(),
                null, null,
                cccd != null ? null : identifier(),
                null,
                dept != null ? dept : departmentId,
                pos != null ? pos : positionId,
                null,
                start != null ? start : START_DATE), "fill-missing");

        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void should_reject_repeated_or_non_draft_activation_with_409_without_creating_duplicate_user_or_notifying()
            throws Exception {
        asHr();
        String staffEmail = email();
        EmployeeResponse draft = employees.create(new CreateEmployeeRequest(
                "Nhân viên kích hoạt lặp", LocalDate.of(1995, 1, 1), Gender.FEMALE, null, staffEmail,
                null, null, identifier(), null, departmentId, positionId, null, START_DATE), "create-repeat");

        // First activation succeeds
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        clearInvocations(notifier);
        long userCountAfterFirst = users.count();

        // Second activation returns 409 Conflict with current status ACTIVE, no extra User or Notifier call
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(containsString("ACTIVE")));

        assertThat(users.count()).isEqualTo(userCountAfterFirst);
        verify(notifier, never()).onboardingInvite(anyString(), anyString());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'Employee' and entity_id = ? and action = 'ACTIVATE'",
                Integer.class, draft.id())).isEqualTo(1);

        // ON_LEAVE_LONG -> 409 mentioning ON_LEAVE_LONG
        jdbc.update("update employees set status = 'ON_LEAVE_LONG' where id = ?", draft.id());
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(containsString("ON_LEAVE_LONG")));

        // TERMINATED -> 409 mentioning TERMINATED
        asHr();
        employees.terminate(draft.id(), new TerminateEmployeeRequest(START_DATE.plusDays(5), "Nghỉ việc"), "term");
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(containsString("TERMINATED")));

        // Soft-deleted or non-existent -> 404
        asHr();
        employees.delete(draft.id(), "del");
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
        mvc.perform(post(EMP_PATH + "/" + UUID.randomUUID() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_reject_activation_and_rollback_when_company_email_already_exists_in_users_table() throws Exception {
        String existingEmail = email();
        users.saveAndFlush(new User(existingEmail, "$2a$10$existinghashvalue00000000000000000000000000000000",
                roles.findByCode("HR").orElseThrow()));

        asHr();
        EmployeeResponse draft = employees.create(new CreateEmployeeRequest(
                "Trùng Email User", null, null, null, existingEmail, null, null,
                identifier(), null, departmentId, positionId, null, START_DATE), "conflict-user-email");

        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate")
                        .with(user("hr-onboarding").roles("HR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"));

        verify(notifier, never()).onboardingInvite(anyString(), anyString());
        asHr();
        EmployeeResponse afterFailure = employees.get(draft.id());
        assertThat(afterFailure.status()).isEqualTo(EmployeeStatus.DRAFT);
        assertThat(jdbc.queryForObject("select user_id from employees where id = ?", UUID.class, draft.id())).isNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'Employee' and entity_id = ? and action = 'ACTIVATE'",
                Integer.class, draft.id())).isZero();
    }

    @Test
    void should_validate_update_conflicts_and_protect_sensitive_values(CapturedOutput output) throws Exception {
        asHr();
        String existingEmail = email();
        String existingCccd = identifier();
        employees.create(new CreateEmployeeRequest(
                "Nhân viên 1", null, null, null, existingEmail, null, null,
                existingCccd, null, departmentId, positionId, null, START_DATE), "emp-1");

        String secondEmail = email();
        String secondCccd = identifier();
        EmployeeResponse second = employees.create(new CreateEmployeeRequest(
                "Nhân viên 2", null, null, null, secondEmail, null, null,
                secondCccd, null, departmentId, positionId, null, START_DATE), "emp-2");

        // Duplicate companyEmail on update -> 409
        mvc.perform(put(EMP_PATH + "/" + second.id())
                        .with(user("hr-onboarding").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Nhân viên 2",
                                "companyEmail", existingEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"));

        // Duplicate CCCD on update -> 409 without leaking CCCD
        String dupCccdResponse = mvc.perform(put(EMP_PATH + "/" + second.id())
                        .with(user("hr-onboarding").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Nhân viên 2",
                                "cccd", existingCccd))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(dupCccdResponse).doesNotContain(existingCccd);

        // Activate second employee, then attempt to clear mandatory field on ACTIVE profile -> 422
        asHr();
        employees.activate(second.id(), "act-2");
        mvc.perform(put(EMP_PATH + "/" + second.id())
                        .with(user("hr-onboarding").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("fullName", "Nhân viên 2"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("HRM_UNPROCESSABLE_ENTITY"));

        // Terminate second employee, then attempt to update TERMINATED profile -> 409
        asHr();
        employees.terminate(second.id(), new TerminateEmployeeRequest(START_DATE.plusDays(1), "Thôi việc"), "term-2");
        mvc.perform(put(EMP_PATH + "/" + second.id())
                        .with(user("hr-onboarding").roles("HR"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Nhân viên 2",
                                "companyEmail", secondEmail,
                                "departmentId", departmentId.toString(),
                                "positionId", positionId.toString(),
                                "startDate", START_DATE.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONFLICT"));

        assertThat(output.getAll()).doesNotContain(existingCccd, secondCccd);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void should_enforce_authentication_and_rbac_on_create_update_and_activate(String forbiddenRole) throws Exception {
        asHr();
        EmployeeResponse draft = employees.create(new CreateEmployeeRequest(
                "Nhân viên RBAC", null, null, null, email(), null, null,
                identifier(), null, departmentId, positionId, null, START_DATE), "rbac-draft");

        String body = json.writeValueAsString(Map.of("fullName", "Nhân viên RBAC"));

        // 1. Anonymous -> 401
        mvc.perform(post(EMP_PATH).with(anonymous()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(EMP_PATH + "/" + draft.id()).with(anonymous()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate").with(anonymous()))
                .andExpect(status().isUnauthorized());

        // 2. Non-HR/ADMIN role -> 403 on HTTP
        mvc.perform(post(EMP_PATH).with(user("u").roles(forbiddenRole)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(EMP_PATH + "/" + draft.id()).with(user("u").roles(forbiddenRole))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate").with(user("u").roles(forbiddenRole)))
                .andExpect(status().isForbidden());

        // 3. Direct service proxy calls -> AccessDeniedException
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(), "unused", AuthorityUtils.createAuthorityList("ROLE_" + forbiddenRole)));
        assertThatThrownBy(() -> employees.create(
                new CreateEmployeeRequest("Forbidden", null, null, null, null, null, null, null, null, null, null, null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> employees.update(draft.id(),
                new UpdateEmployeeRequest("Forbidden", null, null, null, null, null, null, null, null, null, null, null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> employees.activate(draft.id(), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.provisionEmployeeUser(email(), null))
                .isInstanceOf(AccessDeniedException.class);

        // 4. ADMIN role is also permitted
        mvc.perform(post(EMP_PATH + "/" + draft.id() + "/activate").with(user("admin-user").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
