package com.company.hrm.employee;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.*;
import com.company.hrm.employee.dto.EmployeeResponse;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.service.*;
import com.company.hrm.testsupport.*;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static com.company.hrm.testsupport.EmployeeFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "HR")
class EmployeeListIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/v1/employees";
    @Autowired EmployeeService employees;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory emf;

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    EmployeeResponse create(EmployeeFixture fixture) { return employees.create(fixture.build(), null); }
    UUID link(EmployeeResponse employee) {
        UUID id = users.saveAndFlush(new User(email(), "unused-test-hash", roles.findByCode("EMPLOYEE").orElseThrow())).getId();
        jdbc.update("update employees set user_id = ? where id = ?", id, employee.id());
        return id;
    }
    void deleted(UUID id) { jdbc.update("update employees set deleted_at = current_timestamp where id = ?", id); }

    @Test void should_combine_filters_without_including_child_departments_or_deleted_profiles() throws Exception {
        var department = departments.create(new CreateDepartmentRequest(code(), "Phòng", null), null);
        var child = departments.create(new CreateDepartmentRequest(code(), "Phòng con", department.id()), null);
        var position = positions.create(new CreatePositionRequest(code(), "Chức danh"), null);
        var otherPosition = positions.create(new CreatePositionRequest(code(), "Khác"), null);
        String name = "Nguyễn " + UUID.randomUUID();
        var expected = create(new EmployeeFixture().name(name).department(department.id()).position(position.id()));
        create(new EmployeeFixture().name(name).department(child.id()).position(position.id()));
        create(new EmployeeFixture().name(name).department(department.id()).position(otherPosition.id()));
        create(new EmployeeFixture().name("Tên khác").department(department.id()).position(position.id()));
        var active = create(new EmployeeFixture().name(name).department(department.id()).position(position.id()));
        jdbc.update("update employees set status = 'ACTIVE' where id = ?", active.id());
        deleted(create(new EmployeeFixture().name(name).department(department.id()).position(position.id())).id());
        mvc.perform(get(PATH).param("name", name.replace("Nguyễn", "NGUYEN"))
                .param("departmentId", department.id().toString()).param("positionId", position.id().toString()).param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.id().toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"%", "_", "!", "\\"})
    void should_escape_literal_search_characters(String symbol) throws Exception {
        String prefix = UUID.randomUUID().toString();
        var expected = create(new EmployeeFixture().name(prefix + symbol));
        create(new EmployeeFixture().name(prefix + "other"));
        mvc.perform(get(PATH).param("name", prefix + symbol)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.id().toString()));
    }

    @Test void should_normalize_both_sides_including_vietnamese_d() throws Exception {
        String suffix = UUID.randomUUID().toString();
        create(new EmployeeFixture().name("Đặng " + suffix));
        create(new EmployeeFixture().name("dang " + suffix));
        for (String q : List.of("DANG ", "ĐẶNG ")) {
            mvc.perform(get(PATH).with(user("hr").roles("HR")).param("name", q + suffix))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        }
        assertThat(jdbc.queryForObject("select unaccent(lower('Đặng Nguyễn'))", String.class)).isEqualTo("dang nguyen");
    }

    @ParameterizedTest @ValueSource(strings = {"departmentId", "positionId", "status"})
    void should_return_400_for_invalid_filter_types(String field) throws Exception {
        mvc.perform(get(PATH).param(field, "invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
    }

    @ParameterizedTest @ValueSource(strings = {"cccdHash", "department.name", "unknown"})
    void should_reject_sort_fields_outside_whitelist(String field) throws Exception {
        mvc.perform(get(PATH).param("sort", field + ",asc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
    }

    @Test void should_cap_size_and_stabilize_custom_and_default_sort() throws Exception {
        String name = UUID.randomUUID().toString();
        var first = create(new EmployeeFixture().name(name));
        var second = create(new EmployeeFixture().name(name));
        mvc.perform(get(PATH).param("name", name).param("size", "201"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(200))
                .andExpect(jsonPath("$.content[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.content[1].id").value(second.id().toString()));
        var ordered = List.of(first.id().toString(), second.id().toString()).stream().sorted().toList();
        for (int page = 0; page < 2; page++) {
            mvc.perform(get(PATH).with(user("hr").roles("HR")).param("name", name)
                    .param("size", "1").param("page", Integer.toString(page)).param("sort", "fullName,desc"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(ordered.get(page)));
        }
        mvc.perform(get(PATH).with(user("hr").roles("HR")).param("departmentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "HR", "MANAGER", "EMPLOYEE", "MANAGER,EMPLOYEE", "HR,MANAGER"})
    void should_scope_content_and_total_before_pagination(String role) throws Exception {
        String name = UUID.randomUUID().toString();
        var own = create(new EmployeeFixture().name(name));
        var child = create(new EmployeeFixture().name(name).manager(own.id()));
        create(new EmployeeFixture().name(name).manager(child.id()));
        create(new EmployeeFixture().name(name));
        deleted(create(new EmployeeFixture().name(name).manager(own.id())).id());
        UUID id = link(own);
        String[] granted = role.split(",");
        int total = role.contains("HR") || role.equals("ADMIN") ? 4 : role.equals("MANAGER") ? 2 : role.equals("EMPLOYEE") ? 1 : 3;
        EmployeeIntegrationTest.authenticate(id, granted);
        assertThat(employees.list(name, null, null, null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(total);
        mvc.perform(get(PATH).with(user(id.toString()).roles(granted)).param("name", name).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.content[0].id").value((role.equals("MANAGER") ? child : own).id().toString()));
    }

    @Test void should_distinguish_unauthenticated_forbidden_and_unlinked_users() throws Exception {
        mvc.perform(get(PATH).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("invalid-role").roles("OTHER"))).andExpect(status().isForbidden());
        EmployeeIntegrationTest.authenticate(UUID.randomUUID(), "OTHER");
        assertThatThrownBy(() -> employees.list(null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        for (String role : List.of("EMPLOYEE", "MANAGER")) {
            mvc.perform(get(PATH).with(user(UUID.randomUUID().toString()).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test void should_fail_closed_for_cycles_and_deleted_links() throws Exception {
        var own = create(new EmployeeFixture());
        var middle = create(new EmployeeFixture().manager(own.id()));
        var leaf = create(new EmployeeFixture().manager(middle.id()));
        UUID userId = link(own);
        jdbc.update("update employees set manager_id = ? where id = ?", leaf.id(), own.id());
        mvc.perform(get(PATH).with(user(userId.toString()).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("update employees set manager_id = null where id = ?", own.id());
        deleted(middle.id());
        mvc.perform(get(PATH).with(user(userId.toString()).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        deleted(own.id());
        mvc.perform(get(PATH).with(user(userId.toString()).roles("EMPLOYEE", "MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest @ValueSource(strings = {"HR", "MANAGER"})
    void should_use_constant_query_count_for_fifty_rows(String role) throws Exception {
        String name = UUID.randomUUID().toString();
        var manager = create(new EmployeeFixture());
        UUID userId = link(manager);
        UUID parent = manager.id();
        for (int i = 0; i < 51; i++) parent = create(new EmployeeFixture().name(name).manager(parent)).id();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            for (int size : List.of(1, 50)) {
                stats.clear();
                mvc.perform(get(PATH).with(user(userId.toString()).roles(role)).param("name", name)
                        .param("size", Integer.toString(size)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(size))
                        .andExpect(jsonPath("$.totalElements").value(51));
                assertThat(stats.getPrepareStatementCount()).isEqualTo(role.equals("HR") ? 2 : 3);
                assertThat(stats.getEntityFetchCount()).isZero();
            }
        } finally { stats.setStatisticsEnabled(false); }
    }
}
