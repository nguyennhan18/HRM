package com.company.hrm.organization;

import com.company.hrm.common.exception.*;
import com.company.hrm.organization.dto.*;
import com.company.hrm.organization.repository.DepartmentTreeLock;
import com.company.hrm.organization.service.*;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OrganizationIntegrationTest.Configuration.class)
@WithMockUser(username = "hr-test", roles = "HR")
class OrganizationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @MockitoSpyBean DepartmentTreeLock tree;
    static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock organizationClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("hrm.auth.cors-origins", () -> "https://hrm.example.com");
    }
    String code() { return "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT); }
    String body(String code, String name, UUID parent) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", code); data.put("name", name); data.put("parentId", parent);
        return json.writeValueAsString(data);
    }
    JsonNode create(String resource, String code, UUID parent) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/" + resource).contentType("application/json")
                .header("X-Correlation-ID", "organization-test").content(body(code, "Tên", parent)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString());
    }
    UUID id(JsonNode item) { return UUID.fromString(item.get("id").asText()); }
    ResultActions update(String resource, JsonNode item, UUID parent) throws Exception {
        return mvc.perform(put("/api/v1/" + resource + "/" + id(item)).contentType("application/json")
                .content(body(item.get("code").asText(), "Đã sửa", parent)));
    }
    void conflict(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HRM_CONFLICT"));
    }

    @Test void should_move_nodes_and_reject_self_and_deep_cycles_when_editing_tree() throws Exception {
        var root = create("departments", code(), null);
        var child = create("departments", code(), id(root));
        var leaf = create("departments", code(), id(child));
        conflict(update("departments", root, id(root)));
        conflict(update("departments", root, id(leaf)));
        mvc.perform(get("/api/v1/departments").param("parentId", id(root).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id(child).toString()));
        update("departments", child, null).andExpect(status().isOk()).andExpect(jsonPath("$.parentId").isEmpty());
        update("departments", root, id(leaf)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/departments").param("roots", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].parentId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }
    @Test void should_block_parent_delete_and_hide_soft_deleted_nodes_without_reusing_code() throws Exception {
        var root = create("departments", code(), null);
        var child = create("departments", code(), id(root));
        conflict(mvc.perform(delete("/api/v1/departments/" + id(root))));
        mvc.perform(delete("/api/v1/departments/" + id(child))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select deleted_at from departments where id = ?", Instant.class, id(child))).isEqualTo(NOW);
        mvc.perform(get("/api/v1/departments/" + id(child))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/departments/" + id(child))).andExpect(status().isNotFound());
        update("departments", child, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/departments").param("parentId", id(root).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        conflict(mvc.perform(post("/api/v1/departments").contentType("application/json")
                .content(body(child.get("code").asText(), "Mới", null))));
        mvc.perform(post("/api/v1/departments").contentType("application/json")
                .content(body(code(), "Mới", id(child)))).andExpect(status().isNotFound());
        update("departments", root, id(child)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/departments/" + id(root))).andExpect(status().isNoContent());
    }
    @ParameterizedTest @ValueSource(strings = {"departments", "positions"})
    void should_normalize_codes_reject_duplicates_and_prevent_code_changes(String resource) throws Exception {
        String code = code();
        var item = create(resource, " " + code.toLowerCase(Locale.ROOT) + " ", null);
        assertThat(item.get("code").asText()).isEqualTo(code);
        conflict(mvc.perform(post("/api/v1/" + resource).contentType("application/json").content(body(code, "Tên", null))));
        conflict(mvc.perform(put("/api/v1/" + resource + "/" + id(item)).contentType("application/json")
                .content(body(code(), "Tên", null))));
        update(resource, item, null).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        create(resource.equals("departments") ? "positions" : "departments", code, null);
    }
    @ParameterizedTest @CsvSource({"departments,A", "positions,A", "departments,INVALID.CODE", "positions,ABCDEFGHIJKLMNOPQRSTU"})
    void should_validate_code_when_creating_or_updating(String resource, String invalid) throws Exception {
        var item = create(resource, code(), null);
        for (var request : List.of(post("/api/v1/" + resource), put("/api/v1/" + resource + "/" + id(item)))) {
            mvc.perform(request.contentType("application/json").content(body(invalid, "Tên", null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"departments", "positions"})
    void should_validate_names_sort_and_missing_resources(String resource) throws Exception {
        String path = "/api/v1/" + resource;
        mvc.perform(post(path).contentType("application/json").content(body(code(), "  ", null))).andExpect(status().isBadRequest());
        mvc.perform(put(path + "/" + UUID.randomUUID()).contentType("application/json").content(body(code(), " ", null))).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("sort", "deletedAt")).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("sort", "name,desc").param("size", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1));
        mvc.perform(get(path + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(put(path + "/" + UUID.randomUUID()).contentType("application/json").content(body(code(), "Tên", null))).andExpect(status().isNotFound());
        mvc.perform(delete(path + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test void should_reject_missing_parent_and_conflicting_filters() throws Exception {
        var item = create("departments", code(), null);
        mvc.perform(post("/api/v1/departments").contentType("application/json").content(body(code(), "Tên", UUID.randomUUID())))
                .andExpect(status().isNotFound());
        update("departments", item, UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/departments").param("parentId", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/departments").param("parentId", id(item).toString()).param("roots", "true")).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"departments", "positions"})
    void should_audit_create_update_delete_and_delete_positions_physically(String resource) throws Exception {
        var item = create(resource, code(), null);
        update(resource, item, null).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/" + resource + "/" + id(item))).andExpect(status().isNoContent());
        var rows = jdbc.queryForList("select * from audit_logs where entity_id = ? order by created_at", id(item));
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("CREATE", "UPDATE", "DELETE");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("actor")).isEqualTo("hr-test"));
        assertThat(rows.get(0).get("trace_id")).isEqualTo("organization-test");
        assertThat(rows.get(0).get("before_json")).isNull();
        assertThat(json.readTree(rows.get(1).get("before_json").toString()).get("name").asText()).isEqualTo("Tên");
        assertThat(json.readTree(rows.get(1).get("after_json").toString()).get("name").asText()).isEqualTo("Đã sửa");
        if (resource.equals("positions")) {
            assertThat(rows.get(2).get("after_json")).isNull();
            assertThat(jdbc.queryForObject("select count(*) from positions where id = ?", Integer.class, id(item))).isZero();
        } else {
            assertThat(json.readTree(rows.get(2).get("after_json").toString()).get("deletedAt").asText()).isEqualTo(NOW.toString());
        }
    }
    @ParameterizedTest @CsvSource({"departments,ADMIN", "departments,HR", "positions,ADMIN", "positions,HR"})
    void should_allow_all_crud_endpoints_for_writers(String resource, String role) throws Exception {
        String path = "/api/v1/" + resource;
        var result = mvc.perform(post(path).with(user("writer").roles(role)).contentType("application/json").content(body(code(), "Tên", null)))
                .andExpect(status().isCreated()).andReturn();
        var item = json.readTree(result.getResponse().getContentAsString());
        mvc.perform(get(path).with(user("writer").roles(role))).andExpect(status().isOk());
        mvc.perform(get(path + "/" + id(item)).with(user("writer").roles(role))).andExpect(status().isOk());
        mvc.perform(put(path + "/" + id(item)).with(user("writer").roles(role)).contentType("application/json")
                .content(body(item.get("code").asText(), "Sửa", null))).andExpect(status().isOk());
        mvc.perform(delete(path + "/" + id(item)).with(user("writer").roles(role))).andExpect(status().isNoContent());
    }
    @ParameterizedTest @CsvSource({"departments,MANAGER", "departments,EMPLOYEE", "positions,MANAGER", "positions,EMPLOYEE", "departments,NONE", "positions,NONE"})
    void should_allow_authenticated_reads_but_deny_writes_for_non_writers(String resource, String role) throws Exception {
        var item = create(resource, code(), null);
        var actor = user("reader").roles(role.equals("NONE") ? new String[0] : new String[]{role});
        String path = "/api/v1/" + resource;
        mvc.perform(get(path).with(actor)).andExpect(status().isOk());
        mvc.perform(get(path + "/" + id(item)).with(actor)).andExpect(status().isOk());
        for (var request : List.of(post(path), put(path + "/" + id(item)), delete(path + "/" + id(item)))) {
            mvc.perform(request.with(actor).contentType("application/json").content(body(code(), "Tên", null)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HRM_ACCESS_DENIED"));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"departments", "positions"})
    void should_reject_anonymous_on_every_endpoint(String resource) throws Exception {
        String path = "/api/v1/" + resource;
        for (var request : List.of(get(path), get(path + "/" + UUID.randomUUID()), post(path),
                put(path + "/" + UUID.randomUUID()), delete(path + "/" + UUID.randomUUID()))) {
            mvc.perform(request.with(anonymous()).contentType("application/json").content(body(code(), "Tên", null)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"));
        }
    }
    @Test void should_enforce_permissions_on_service_proxy_without_controller() {
        authenticate("EMPLOYEE");
        assertThat(departments.list(null, false, PageRequest.of(0, 10))).isNotNull();
        assertThat(positions.list(PageRequest.of(0, 10))).isNotNull();
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> departments.create(new CreateDepartmentRequest(code(), "Tên", null), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> departments.update(id, new UpdateDepartmentRequest(code(), "Tên", null), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> departments.delete(id, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> positions.create(new CreatePositionRequest(code(), "Tên"), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> positions.update(id, new UpdatePositionRequest(code(), "Tên"), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> positions.delete(id, null)).isInstanceOf(AccessDeniedException.class);
    }
    static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Test @Timeout(30)
    void should_allow_only_one_cross_parent_change_when_transactions_overlap() throws Exception {
        var a = departments.create(new CreateDepartmentRequest(code(), "A", null), null);
        var b = departments.create(new CreateDepartmentRequest(code(), "B", null), null);
        var start = new CyclicBarrier(2);
        DepartmentTreeLock target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(tree);
        doAnswer(call -> { start.await(10, TimeUnit.SECONDS); return call.callRealMethod(); })
                .when(target).lockAndValidate(any(UUID.class), any(UUID.class));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> move(a, b));
            var second = executor.submit(() -> move(b, a));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { reset(target); }
        var afterA = departments.get(a.id()); var afterB = departments.get(b.id());
        assertThat(afterA.parentId() == null || afterB.parentId() == null).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'UPDATE' and entity_id in (?, ?)",
                Integer.class, a.id(), b.id())).isEqualTo(1);
    }
    int move(DepartmentResponse node, DepartmentResponse parent) {
        authenticate("HR");
        try {
            departments.update(node.id(), new UpdateDepartmentRequest(node.code(), node.name(), parent.id()), null);
            return 200;
        } catch (BusinessException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HRM_CONFLICT); return 409;
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void should_preserve_database_constraints_when_bypassing_service() throws Exception {
        var parent = create("departments", code(), null);
        var another = create("departments", code(), null);
        assertThatThrownBy(() -> jdbc.update("update departments set parent_id = id where id = ?", id(parent)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update departments set parent_id = ? where id = ?", UUID.randomUUID(), id(parent)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update departments set code = 'invalid.code' where id = ?", id(parent)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update departments set code = ? where id = ?", parent.get("code").asText(), id(another)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void should_not_audit_or_mutate_rejected_tree_operations() throws Exception {
        var parent = create("departments", code(), null);
        var child = create("departments", code(), id(parent));
        conflict(update("departments", parent, id(child)));
        conflict(mvc.perform(delete("/api/v1/departments/" + id(parent))));
        authenticate("HR");
        assertThat(departments.get(id(parent)).parentId()).isNull();
        assertThat(departments.get(id(child)).parentId()).isEqualTo(id(parent));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ?", Integer.class, id(parent))).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void should_allow_cors_preflight_for_crud_methods(String method) throws Exception {
        mvc.perform(options("/api/v1/departments/" + UUID.randomUUID())
                .header("Origin", "https://hrm.example.com").header("Access-Control-Request-Method", method))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://hrm.example.com"));
    }
}
