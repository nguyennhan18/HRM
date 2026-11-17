package com.company.hrm.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.hrm.HrmApplication;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = HrmApplication.class)
class AuditServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuditWithActorFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr.manager", "not-used", List.of()));
        UUID entityId = UUID.randomUUID();

        AuditLog recorded = auditService.record(
                "EMPLOYEE_UPDATED", "employee", entityId,
                Map.of("status", "DRAFT"), Map.of("status", "ACTIVE"), "trace-123");

        AuditLog persisted = auditLogRepository.findById(recorded.getId()).orElseThrow();
        assertThat(persisted.getActor()).isEqualTo("hr.manager");
        assertThat(persisted.getAction()).isEqualTo("EMPLOYEE_UPDATED");
        assertThat(persisted.getEntityType()).isEqualTo("employee");
        assertThat(persisted.getEntityId()).isEqualTo(entityId);
        assertThat(persisted.getBeforeJson().get("status").asText()).isEqualTo("DRAFT");
        assertThat(persisted.getAfterJson().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getTraceId()).isEqualTo("trace-123");
    }

    @Test
    void masksSensitiveFieldsRegardlessOfCaseInNestedObjectsAndArrays() {
        Map<String, Object> before = Map.of(
                "Password", "secret",
                "profile", Map.of("CCCD", "001234567890", "account_number", "123456789"),
                "sessions", List.of(Map.of("refreshToken", "refresh-secret")));

        AuditLog recorded = auditService.record("EMPLOYEE_UPDATED", "employee", UUID.randomUUID(), before, null, null);

        AuditLog persisted = auditLogRepository.findById(recorded.getId()).orElseThrow();
        assertThat(persisted.getBeforeJson().get("Password").asText()).isEqualTo("***");
        assertThat(persisted.getBeforeJson().path("profile").path("CCCD").asText()).isEqualTo("***");
        assertThat(persisted.getBeforeJson().path("profile").path("account_number").asText()).isEqualTo("***");
        assertThat(persisted.getBeforeJson().path("sessions").get(0).path("refreshToken").asText()).isEqualTo("***");
    }

    @Test
    void defaultsActorToSystemWhenSecurityContextIsAbsent() {
        AuditLog recorded = auditService.record("SCHEDULED_JOB", "attendance", null, null, null, null);

        AuditLog persisted = auditLogRepository.findById(recorded.getId()).orElseThrow();
        assertThat(persisted.getActor()).isEqualTo("system");
    }
}
