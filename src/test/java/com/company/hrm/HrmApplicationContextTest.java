package com.company.hrm;

import com.company.hrm.common.audit.AuditLogRepository;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class HrmApplicationContextTest {
    @MockBean com.company.hrm.attendance.repository.ShiftRepository shifts;
    @MockBean com.company.hrm.attendance.repository.AttendancePolicyRepository attendancePolicies;
    @MockBean com.company.hrm.attendance.repository.HolidayRepository holidays;
    @MockBean com.company.hrm.attendance.repository.EmployeeShiftRepository employeeShifts;
    @MockBean com.company.hrm.employee.repository.EmployeeDocumentRepository documents;
    @MockBean com.company.hrm.contract.repository.ContractRepository contracts;
    @MockBean com.company.hrm.employee.repository.EmployeeRepository employees;
    @MockBean com.company.hrm.employee.repository.EmployeeWriteLock employeeLock;
    @MockBean com.company.hrm.organization.repository.DepartmentRepository departments;
    @MockBean com.company.hrm.organization.repository.PositionRepository positions;
    @MockBean com.company.hrm.organization.repository.DepartmentTreeLock treeLock;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;
    @MockBean
    private com.company.hrm.auth.service.AuthService authService;
    @MockBean
    private com.company.hrm.auth.repository.AuthTokenRepository authTokenRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot application context starts without an external database.
    }
}
