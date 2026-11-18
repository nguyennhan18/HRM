package com.company.hrm.contract;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.*;
import com.company.hrm.common.exception.*;
import com.company.hrm.contract.dto.*;
import com.company.hrm.contract.entity.*;
import com.company.hrm.contract.service.ContractService;
import com.company.hrm.employee.service.EmployeeService;
import com.company.hrm.testsupport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @WithMockUser(roles = "HR")
@Import(ContractIntegrationTest.TimeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ContractIntegrationTest extends AbstractIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026,11,19); // UTC is still November 18.
    static final String PATH = "/api/v1/contracts";
    static final BigDecimal SALARY = new BigDecimal("19876543");
    @Autowired ContractService contracts;
    @Autowired EmployeeService employees;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManagerFactory emf;
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock contractClock() { return Clock.fixed(Instant.parse("2026-11-18T18:00:00Z"), ZoneOffset.UTC); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    UUID employee() { return employees.create(new EmployeeFixture().build(), null).id(); }
    CreateContractRequest request(UUID employee, LocalDate start, LocalDate end) {
        return new CreateContractRequest(employee, end == null ? ContractType.INDEFINITE : ContractType.FIXED_TERM,
                start, end, SALARY, BigDecimal.ZERO);
    }
    ContractResponse draft(UUID employee, LocalDate start, LocalDate end) { return contracts.create(request(employee,start,end), null); }
    ContractResponse active(UUID employee, LocalDate start, LocalDate end) { return contracts.activate(draft(employee,start,end).id(), null); }
    UpdateContractRequest update(ContractResponse c, LocalDate end) {
        return new UpdateContractRequest(c.employeeId(), c.type(), c.startDate(), end, c.baseSalary(), c.allowance());
    }
    static void hr() { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("hr", "unused", AuthorityUtils.createAuthorityList("ROLE_HR"))); }
    void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    UUID link(UUID employee) {
        UUID id = users.saveAndFlush(new User(EmployeeFixture.email(), "unused", roles.findByCode("EMPLOYEE").orElseThrow())).getId();
        jdbc.update("update employees set user_id=? where id=?",id,employee); return id;
    }
    @Test void should_allow_overlapping_drafts_but_reject_activation_and_same_day_boundary() {
        UUID employee = employee();
        var first = draft(employee,TODAY,TODAY.plusDays(10));
        var second = draft(employee,TODAY.plusDays(10),TODAY.plusDays(20));
        assertThat(first.status()).isEqualTo(ContractStatus.DRAFT);
        contracts.activate(first.id(),null);
        error(() -> contracts.activate(second.id(),null),ErrorCode.CONTRACT_OVERLAP);
        var moved = contracts.update(second.id(),new UpdateContractRequest(employee,second.type(),TODAY.plusDays(11),
                second.endDate(),SALARY,BigDecimal.ZERO),null);
        assertThat(contracts.activate(moved.id(),null).status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(active(employee(),TODAY,TODAY).status()).isEqualTo(ContractStatus.ACTIVE);
    }
    @Test void should_preserve_terminated_history_and_allow_next_day_after_early_termination() {
        UUID employee=employee(); var first=active(employee,TODAY,null);
        contracts.terminate(first.id(),new TerminateContractRequest(TODAY.plusDays(3)),null);
        var overlap=draft(employee,TODAY.plusDays(3),TODAY.plusDays(8));
        error(() -> contracts.activate(overlap.id(),null),ErrorCode.CONTRACT_OVERLAP);
        assertThat(active(employee,TODAY.plusDays(4),null).endDate()).isNull();
        var later=draft(employee,TODAY.plusYears(20),null);
        error(() -> contracts.activate(later.id(),null),ErrorCode.CONTRACT_OVERLAP);
    }
    @ParameterizedTest @ValueSource(strings={"put","activate","terminate"})
    void should_reject_every_mutation_when_terminated(String operation) throws Exception {
        var c=active(employee(),TODAY,TODAY.plusDays(5));
        contracts.terminate(c.id(),new TerminateContractRequest(TODAY),null);
        var req = operation.equals("put") ? put(PATH+"/"+c.id()).content(json.writeValueAsString(update(c,c.endDate())))
                : post(PATH+"/"+c.id()+"/"+operation).content("{\"endDate\":\"2026-11-19\"}");
        mvc.perform(req.with(user("hr").roles("HR")).contentType("application/json"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_TERMINATED_IMMUTABLE"));
    }
    @Test void should_only_allow_forward_transitions_and_validate_actual_termination_date() {
        var c=draft(employee(),TODAY,TODAY.plusDays(5));
        error(() -> contracts.terminate(c.id(),new TerminateContractRequest(TODAY),null),ErrorCode.CONTRACT_INVALID_TRANSITION);
        contracts.activate(c.id(),null);
        error(() -> contracts.activate(c.id(),null),ErrorCode.CONTRACT_INVALID_TRANSITION);
        for (var date:List.of(TODAY.minusDays(1),TODAY.plusDays(6)))
            error(() -> contracts.terminate(c.id(),new TerminateContractRequest(date),null),ErrorCode.HRM_VALIDATION_ERROR);
        assertThat(contracts.get(c.id()).status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(contracts.get(c.id()).endDate()).isEqualTo(c.endDate());
        assertThat(contracts.terminate(c.id(),new TerminateContractRequest(c.endDate()),null).status()).isEqualTo(ContractStatus.TERMINATED);
    }
    @Test void should_limit_active_edits_to_salary_allowance_and_shorter_end() {
        var c=active(employee(),TODAY,TODAY.plusDays(5));
        UUID other=employee();
        var invalid=List.of(new UpdateContractRequest(other,c.type(),c.startDate(),c.endDate(),SALARY,BigDecimal.ZERO),
                new UpdateContractRequest(c.employeeId(),ContractType.PROBATION,c.startDate(),c.endDate(),SALARY,BigDecimal.ZERO),
                new UpdateContractRequest(c.employeeId(),c.type(),TODAY.minusDays(1),c.endDate(),SALARY,BigDecimal.ZERO),
                update(c,TODAY.plusDays(6)));
        for(var r:invalid) error(() -> contracts.update(c.id(),r,null),ErrorCode.CONTRACT_ACTIVE_RESTRICTED);
        var changed=contracts.update(c.id(),new UpdateContractRequest(c.employeeId(),c.type(),c.startDate(),TODAY,
                SALARY.add(BigDecimal.ONE),BigDecimal.TEN),null);
        assertThat(changed.endDate()).isEqualTo(TODAY);
        assertThat(changed.baseSalary()).isEqualByComparingTo(SALARY.add(BigDecimal.ONE));
        var indefinite=active(employee(),TODAY,null);
        contracts.update(indefinite.id(),update(indefinite,TODAY.plusDays(5)),null);
        error(() -> contracts.update(indefinite.id(),update(indefinite,null),null),ErrorCode.CONTRACT_ACTIVE_RESTRICTED);
    }
    @Test void should_allow_full_draft_edits_and_validate_dates_and_employee_eligibility() {
        var c=draft(employee(),TODAY,null); UUID other=employee();
        var changed=contracts.update(c.id(),new UpdateContractRequest(other,ContractType.PROBATION,TODAY.minusDays(2),TODAY,
                BigDecimal.ZERO,BigDecimal.TEN),null);
        assertThat(changed.employeeId()).isEqualTo(other);
        error(() -> contracts.create(request(other,TODAY,TODAY.minusDays(1)),null),ErrorCode.HRM_VALIDATION_ERROR);
        error(() -> contracts.create(new CreateContractRequest(other,ContractType.FIXED_TERM,TODAY,null,SALARY,BigDecimal.ZERO),null),ErrorCode.HRM_VALIDATION_ERROR);
        error(() -> contracts.create(request(UUID.randomUUID(),TODAY,null),null),ErrorCode.HRM_RESOURCE_NOT_FOUND);
        jdbc.update("update employees set status='TERMINATED' where id=?",other);
        error(() -> contracts.activate(c.id(),null),ErrorCode.CONTRACT_EMPLOYEE_INELIGIBLE);
        error(() -> contracts.update(c.id(),update(changed,TODAY),null),ErrorCode.CONTRACT_EMPLOYEE_INELIGIBLE);
        error(() -> contracts.create(request(other,TODAY,null),null),ErrorCode.CONTRACT_EMPLOYEE_INELIGIBLE);
        assertThat(contracts.get(c.id()).status()).isEqualTo(ContractStatus.DRAFT);
        jdbc.update("update employees set deleted_at=current_timestamp where id=?",other);
        error(() -> contracts.create(request(other,TODAY,null),null),ErrorCode.HRM_RESOURCE_NOT_FOUND);
    }
    @Test void should_filter_expiring_inclusively_using_vietnam_date_and_exclude_ineligible_rows() {
        var today=active(employee(),TODAY.minusDays(1),TODAY);
        var last=active(employee(),TODAY,TODAY.plusDays(30));
        var expired=active(employee(),TODAY.minusDays(2),TODAY.minusDays(1));
        active(employee(),TODAY,TODAY.plusDays(31)); active(employee(),TODAY,null);
        draft(employee(),TODAY,TODAY.plusDays(3));
        var terminated=active(employee(),TODAY,TODAY.plusDays(3));
        contracts.terminate(terminated.id(),new TerminateContractRequest(TODAY),null);
        var deleted=active(employee(),TODAY,TODAY.plusDays(2));
        jdbc.update("update contracts set deleted_at=current_timestamp where id=?",deleted.id());
        var ids=contracts.expiring(30,PageRequest.of(0,200)).map(ContractResponse::id).getContent();
        assertThat(ids).contains(today.id(),last.id()).doesNotContain(expired.id(),terminated.id(),deleted.id());
        assertThat(contracts.get(expired.id()).expired()).isTrue();
        assertThat(contracts.get(today.id()).expired()).isFalse();
        assertThat(contracts.get(expired.id()).status()).isEqualTo(ContractStatus.ACTIVE);
        error(() -> contracts.get(deleted.id()),ErrorCode.HRM_RESOURCE_NOT_FOUND);
    }
    @ParameterizedTest @ValueSource(ints={0,366,-1})
    void should_reject_days_outside_supported_range(int days) throws Exception {
        mvc.perform(get(PATH+"/expiring").param("days",Integer.toString(days)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
    }
    @Test void should_use_constant_query_count_when_listing_fifty_contracts() {
        UUID employee = employee();
        for (int i = 0; i < 51; i++) draft(employee, TODAY, TODAY.plusDays(30));
        var statistics = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (int size : List.of(1, 50)) {
                statistics.clear();
                assertThat(contracts.list(employee, PageRequest.of(0, size)).getContent()).hasSize(size);
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
                assertThat(statistics.getEntityFetchCount()).isZero();
            }
        } finally { statistics.setStatisticsEnabled(false); }
    }
    @Test void should_return_bad_request_for_missing_or_malformed_api_parameters() throws Exception {
        for (String path : List.of(PATH, PATH + "?employeeId=invalid", PATH + "/invalid",
                PATH + "/expiring?days=invalid")) {
            mvc.perform(get(path).with(user("hr").roles("HR"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("HRM_VALIDATION_ERROR"));
        }
        for (String suffix : List.of("/activate", "/terminate")) {
            mvc.perform(post(PATH + "/invalid" + suffix).with(user("hr").roles("HR"))
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(PATH + "/" + UUID.randomUUID()).with(user("hr").roles("HR"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/" + UUID.randomUUID() + "/terminate").with(user("hr").roles("HR"))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
    @Test void should_keep_contract_status_when_employee_is_terminated_later() {
        var c = active(employee(), TODAY, null);
        jdbc.update("update employees set status='TERMINATED' where id=?", c.employeeId());
        assertThat(contracts.get(c.id()).status()).isEqualTo(ContractStatus.ACTIVE);
        error(() -> contracts.terminate(c.id(), new TerminateContractRequest(TODAY), null), ErrorCode.CONTRACT_EMPLOYEE_INELIGIBLE);
    }
    @Test void should_paginate_and_whitelist_sort_for_both_lists() throws Exception {
        UUID employee=employee(); var a=draft(employee,TODAY,TODAY); var b=draft(employee,TODAY,TODAY);
        for(int page=0;page<2;page++) mvc.perform(get(PATH).with(user("hr").roles("HR"))
                .param("employeeId",employee.toString()).param("size","1").param("page",Integer.toString(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value((page==0?a:b).id().toString()));
        for(String path:List.of(PATH+"?employeeId="+employee,PATH+"/expiring")) {
            mvc.perform(get(path).with(user("hr").roles("HR")).param("sort","baseSalary,asc")).andExpect(status().isBadRequest());
            mvc.perform(get(path).with(user("hr").roles("HR")).param("size","201")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(200));
        }
    }
    @ParameterizedTest @ValueSource(strings={"create","put","activate","terminate","get","list","expiring"})
    void should_enforce_authentication_roles_and_missing_resources_for_each_endpoint(String operation) throws Exception {
        UUID employee=employee(); var c=draft(employee,TODAY,TODAY.plusDays(30));
        mvc.perform(endpoint(operation,c).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(endpoint(operation,c).with(user("manager").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(endpoint(operation,c).with(user(UUID.randomUUID().toString()).roles("EMPLOYEE"))).andExpect(status().isForbidden());
        hr();
        if(operation.equals("terminate")) contracts.activate(c.id(),null);
        mvc.perform(endpoint(operation,c).with(user("admin").roles("ADMIN")))
                .andExpect(status().is(operation.equals("create")?201:200));
        if(List.of("put","activate","terminate","get").contains(operation)) {
            var missing=new ContractResponse(UUID.randomUUID(),employee,c.type(),c.startDate(),c.endDate(),SALARY,BigDecimal.ZERO,c.status(),false,0L);
            mvc.perform(endpoint(operation,missing).with(user("hr").roles("HR"))).andExpect(status().isNotFound());
        }
    }
    MockHttpServletRequestBuilder endpoint(String op,ContractResponse c) throws Exception {
        return switch(op) {
            case "create" -> post(PATH).contentType("application/json").content(json.writeValueAsString(request(c.employeeId(),c.startDate(),c.endDate())));
            case "put" -> put(PATH+"/"+c.id()).contentType("application/json").content(json.writeValueAsString(update(c,c.endDate())));
            case "activate" -> post(PATH+"/"+c.id()+"/activate");
            case "terminate" -> post(PATH+"/"+c.id()+"/terminate").contentType("application/json").content(json.writeValueAsString(new TerminateContractRequest(TODAY)));
            case "get" -> get(PATH+"/"+c.id());
            case "list" -> get(PATH).param("employeeId",c.employeeId().toString());
            default -> get(PATH+"/expiring");
        };
    }
    @Test void should_only_expose_salary_to_owner_or_hr_and_check_service_proxy() throws Exception {
        UUID owner=employee(); UUID userId=link(owner); var own=draft(owner,TODAY,null);
        UUID child=employees.create(new EmployeeFixture().manager(owner).build(),null).id();
        var other=draft(child,TODAY,null);
        UUID unrelated=employee(); var unrelatedContract=draft(unrelated,TODAY,null);
        for(String[] role:List.of(new String[]{"EMPLOYEE"},new String[]{"MANAGER","EMPLOYEE"})) {
            mvc.perform(get(PATH+"/"+own.id()).with(user(userId.toString()).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.baseSalary").value(SALARY.intValue()));
            mvc.perform(get(PATH).param("employeeId",owner.toString()).with(user(userId.toString()).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].baseSalary").value(SALARY.intValue()));
            mvc.perform(get(PATH+"/"+unrelatedContract.id()).with(user(userId.toString()).roles(role))).andExpect(status().isForbidden())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SALARY.toString()))));
            mvc.perform(get(PATH).param("employeeId",unrelated.toString()).with(user(userId.toString()).roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH+"/"+other.id()).with(user(userId.toString()).roles("EMPLOYEE"))).andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SALARY.toString()))));
        mvc.perform(get(PATH).param("employeeId",child.toString()).with(user(userId.toString()).roles("EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get(PATH+"/"+other.id()).with(user(userId.toString()).roles("MANAGER","EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.baseSalary").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.allowance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SALARY.toString()))));
        mvc.perform(get(PATH).param("employeeId",child.toString()).with(user(userId.toString()).roles("MANAGER","EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].baseSalary").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].allowance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SALARY.toString()))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId.toString(),"unused",AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));
        assertThat(contracts.get(own.id()).baseSalary()).isEqualByComparingTo(SALARY);
        assertThatThrownBy(() -> contracts.get(other.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> contracts.activate(own.id(),null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("update employees set deleted_at=current_timestamp where id=?",owner);
        assertThatThrownBy(() -> contracts.get(own.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void should_redact_salary_in_audit_validation_and_logs(CapturedOutput output) throws Exception {
        var c=draft(employee(),TODAY,null);
        contracts.update(c.id(),new UpdateContractRequest(c.employeeId(),c.type(),c.startDate(),null,SALARY.add(BigDecimal.ONE),BigDecimal.TEN),null);
        contracts.activate(c.id(),null); contracts.terminate(c.id(),new TerminateContractRequest(TODAY),null);
        var audits=jdbc.queryForList("select actor,action,before_json::text,after_json::text from audit_logs where entity_id=? order by created_at",c.id());
        assertThat(audits).hasSize(4);
        assertThat(audits).allSatisfy(row -> assertThat(row.get("actor")).isEqualTo("user"));
        assertThat(audits.toString()).contains("baseSalary","allowance","changedFields","***")
                .doesNotContain(SALARY.toString(),SALARY.add(BigDecimal.ONE).toString());
        String body=json.writeValueAsString(request(c.employeeId(),TODAY,null));
        for(String invalid:List.of(body.replace(SALARY.toString(),"-19876543"),body.replace(SALARY.toString(),"19876543.25"),
                body.replace(SALARY.toString(),"\"sensitive-invalid-salary\""),body.replace("{","{\"status\":\"ACTIVE\","))) {
            var result=mvc.perform(post(PATH).with(user("hr").roles("HR")).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(SALARY.toString(),"sensitive-invalid-salary");
        }
        hr();
        assertThatThrownBy(() -> contracts.create(new CreateContractRequest(c.employeeId(),c.type(),TODAY,null,new BigDecimal("-19876543"),BigDecimal.ZERO),null))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(output.getAll()).doesNotContain(SALARY.toString(),"sensitive-invalid-salary");
    }
    @Test void should_enforce_database_checks_and_exclusion_independently_of_service() throws Exception {
        var a=active(employee(),TODAY,TODAY.plusDays(5));
        var b=draft(a.employeeId(),TODAY,TODAY.plusDays(2));
        try(Connection connection=connection()) {
            assertSqlState(connection,"update contracts set status='ACTIVE' where id='"+b.id()+"'","23P01");
            assertSqlState(connection,"update contracts set end_date=start_date-1 where id='"+b.id()+"'","23514");
            assertSqlState(connection,"update contracts set end_date=null where id='"+b.id()+"'","23514");
        }
        jdbc.update("update contracts set deleted_at=current_timestamp where id=?",a.id());
        assertThat(contracts.activate(b.id(),null).status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname='btree_gist'",Integer.class)).isEqualTo(1);
    }
    Connection connection() throws SQLException { return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
    void assertSqlState(Connection c,String sql,String state) {
        assertThatThrownBy(() -> {try(var st=c.createStatement()){st.executeUpdate(sql);}})
                .isInstanceOfSatisfying(SQLException.class,e -> assertThat(e.getSQLState()).isEqualTo(state));
    }
    @Test void should_allow_only_one_activation_when_two_real_transactions_overlap() throws Exception {
        UUID employee=employee(); var a=draft(employee,TODAY,null); var b=draft(employee,TODAY,null);
        try(var first=connection();var second=connection();var executor=Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false); second.setAutoCommit(false);
            int pid; try(var st=second.createStatement();var rs=st.executeQuery("select pg_backend_pid()")){rs.next();pid=rs.getInt(1);}
            try(var st=first.createStatement()){st.executeUpdate("update contracts set status='ACTIVE' where id='"+a.id()+"'");}
            var loser=executor.submit(() -> {
                try(var st=second.createStatement()) {st.setQueryTimeout(10);st.executeUpdate("update contracts set status='ACTIVE' where id='"+b.id()+"'");second.commit();return "success";}
                catch(SQLException e) {second.rollback();return e.getSQLState();}
            });
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                        jdbc.queryForObject("select cardinality(pg_blocking_pids(?)) > 0",Boolean.class,pid));
            } finally { first.commit(); }
            assertThat(loser.get(10,TimeUnit.SECONDS)).isEqualTo("23P01");
        }
        assertThat(jdbc.queryForObject("select count(*) from contracts where employee_id=? and status='ACTIVE'",Integer.class,employee)).isEqualTo(1);
        assertThat(contracts.get(b.id()).status()).isEqualTo(ContractStatus.DRAFT);
    }
}
