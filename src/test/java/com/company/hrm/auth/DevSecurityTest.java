package com.company.hrm.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
    "spring.profiles.active=dev",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
@AutoConfigureMockMvc
class DevSecurityTest {
    @MockBean com.company.hrm.employee.repository.EmployeeRepository employees;
    @MockBean com.company.hrm.employee.repository.EmployeeWriteLock employeeLock;
    @MockBean com.company.hrm.organization.repository.DepartmentRepository departments;
    @MockBean com.company.hrm.organization.repository.PositionRepository positions;
    @MockBean com.company.hrm.organization.repository.DepartmentTreeLock treeLock;

    @Autowired MockMvc mvc;
    @MockBean com.company.hrm.auth.service.AuthService auth;
    @MockBean com.company.hrm.auth.repository.UserRepository users;
    @MockBean com.company.hrm.auth.repository.RoleRepository roles;
    @MockBean com.company.hrm.auth.repository.AuthTokenRepository tokens;
    @MockBean com.company.hrm.common.audit.AuditLogRepository audits;
    @MockBean org.springframework.data.jpa.mapping.JpaMetamodelMappingContext mapping;
    @Test void should_allow_health_and_swagger_without_login_in_dev() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
