package com.company.hrm.auth;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.*;
import com.company.hrm.auth.service.EmployeeRelations;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({AuthorizationIntegrationTest.Configuration.class, AuthorizationIntegrationTest.SampleController.class})
class AuthorizationIntegrationTest extends AbstractIntegrationTest {
    static final String PATH = "/api/v1/rbac-sample/";
    static final UUID OWN = UUID.randomUUID(), CHILD = UUID.randomUUID(), GRANDCHILD = UUID.randomUUID(),
            DEEP = UUID.randomUUID(), OTHER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeRelations relations;
    @Autowired SampleService service;
    UUID userId;

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("hrm.auth.rate-limit", () -> "1000");
    }
    @TestConfiguration static class Configuration {
        @Bean FakeRelations employeeRelations() { return new FakeRelations(); }
        @Bean SampleService sampleService() { return new SampleService(); }
    }
    static class FakeRelations implements EmployeeRelations {
        final Map<UUID, UUID> employees = new HashMap<>(), managers = new HashMap<>();
        public Optional<UUID> employeeIdForUser(UUID id) { return Optional.ofNullable(employees.get(id)); }
        public Optional<UUID> managerIdOf(UUID id) { return Optional.ofNullable(managers.get(id)); }
    }
    public record Sample(UUID id) {}
    public static class SampleService {
        @PreAuthorize("@authz.canViewEmployee(#id)")
        public Sample view(UUID id) { return new Sample(id); }
    }
    @RestController static class SampleController {
        private final SampleService service;
        SampleController(SampleService service) { this.service = service; }
        @GetMapping(PATH + "{id}")
        public Sample view(@PathVariable UUID id) { return service.view(id); }
    }
    @BeforeEach void setup() {
        relations.employees.clear(); relations.managers.clear();
        relations.managers.put(CHILD, OWN);
        relations.managers.put(GRANDCHILD, CHILD);
        relations.managers.put(DEEP, GRANDCHILD);
    }
    String login(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        userId = users.saveAndFlush(new User(email, encoder.encode("correct-password"),
                roles.findByCode(role).orElseThrow())).getId();
        relations.employees.put(userId, OWN);
        var response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", "correct-password"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("accessToken").asText();
    }
    ResultActions view(UUID id, String token) throws Exception {
        var request = get(PATH + id).header("X-Correlation-ID", "rbac-test");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }
    void problem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value("https://hrm.company.com/problems/" + code.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.title").value(status == 401 ? "Unauthorized" : "Forbidden"))
                .andExpect(jsonPath("$.detail").value(status == 401 ? "Thông tin xác thực không hợp lệ" : "Không có quyền truy cập"))
                .andExpect(jsonPath("$.instance").exists()).andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.traceId").value("rbac-test"));
    }
    @ParameterizedTest
    @CsvSource({"ADMIN,200,200,200", "HR,200,200,200", "MANAGER,403,200,403", "EMPLOYEE,200,403,403"})
    void should_enforce_role_and_scope_with_real_jwt(String role, int self, int child, int other) throws Exception {
        String token = login(role);
        problem(view(OWN, null), 401, "HRM_AUTHENTICATION_FAILED");
        UUID[] ids = {OWN, CHILD, GRANDCHILD, DEEP, OTHER};
        int[] expected = {self, child, child, child, other};
        for (int i = 0; i < ids.length; i++) {
            var result = view(ids[i], token);
            if (expected[i] == 403) problem(result, 403, "HRM_ACCESS_DENIED");
            else result.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ids[i].toString()));
        }
    }
    @Test void should_reject_invalid_and_revoked_tokens_with_same_problem_format() throws Exception {
        problem(view(OWN, "invalid"), 401, "HRM_AUTHENTICATION_FAILED");
        String token = login("ADMIN");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        problem(view(OWN, token), 401, "HRM_AUTHENTICATION_FAILED");
    }
    @ParameterizedTest @CsvSource({"ADMIN", "HR", "MANAGER", "EMPLOYEE"})
    void should_revoke_role_immediately_for_existing_token(String role) throws Exception {
        String token = login(role);
        UUID target = role.equals("EMPLOYEE") ? OWN : CHILD;
        view(target, token).andExpect(status().isOk());
        jdbc.update("delete from user_roles where user_id = ?", userId);
        problem(view(target, token), 403, "HRM_ACCESS_DENIED");
    }
    @Test void should_combine_manager_and_employee_roles() throws Exception {
        String token = login("MANAGER");
        jdbc.update("insert into user_roles(user_id, role_id) values (?, ?)", userId,
                roles.findByCode("EMPLOYEE").orElseThrow().getId());
        view(OWN, token).andExpect(status().isOk());
        view(DEEP, token).andExpect(status().isOk());
        problem(view(OTHER, token), 403, "HRM_ACCESS_DENIED");
    }
    @Test void should_deny_when_user_has_no_employee_link() throws Exception {
        String token = login("EMPLOYEE");
        relations.employees.clear();
        problem(view(OWN, token), 403, "HRM_ACCESS_DENIED");
    }
    @Test @Timeout(10)
    void should_terminate_and_deny_cyclic_hierarchies() throws Exception {
        String token = login("MANAGER");
        relations.managers.put(CHILD, GRANDCHILD);
        problem(view(DEEP, token), 403, "HRM_ACCESS_DENIED");
        relations.managers.put(CHILD, CHILD);
        problem(view(CHILD, token), 403, "HRM_ACCESS_DENIED");
        relations.managers.put(CHILD, OWN);
        relations.managers.put(OWN, DEEP);
        problem(view(DEEP, token), 403, "HRM_ACCESS_DENIED");
    }
    @Test void should_enforce_security_on_direct_service_calls() {
        try {
            UUID principal = UUID.randomUUID();
            relations.employees.put(principal, OWN);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
            assertThat(service.view(OWN).id()).isEqualTo(OWN);
            assertThatThrownBy(() -> service.view(OTHER)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.view(null)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
