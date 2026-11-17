package com.company.hrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.hrm.testsupport.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;

class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_apply_flyway_migrations_when_database_is_empty() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        assertThat(flyway.info().applied()).hasSize(7);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
    }

    @Test
    void should_upgrade_existing_v6_data_and_start_employee_sequence_at_one() throws Exception {
        String schema = "employee_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("6").load().migrate();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into departments(id, code, name, created_at, updated_at, created_by, updated_by, version)
                    values ('00000000-0000-4000-8000-000000000001', 'EXISTING', 'Phòng cũ',
                        current_timestamp, current_timestamp, 'test', 'test', 0)
                    """);
            configuration.target("7").load().migrate();
            try (ResultSet result = statement.executeQuery("select count(*) from departments where code = 'EXISTING'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (ResultSet result = statement.executeQuery("select nextval('employee_code_seq')")) {
                result.next();
                assertThat(result.getLong(1)).isEqualTo(1);
            }
            try (ResultSet result = statement.executeQuery("select count(*) from employees")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    @Test
    void should_seed_roles_and_permissions_from_authorization_matrix() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet roles = statement.executeQuery("SELECT count(*) FROM roles")) {
            roles.next();
            assertThat(roles.getInt(1)).isEqualTo(4);
        }

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet permissions = statement.executeQuery("SELECT count(*) FROM permissions")) {
            permissions.next();
            assertThat(permissions.getInt(1)).isEqualTo(23);
        }

        Map<String, Set<String>> assignedPermissions = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet assignments = statement.executeQuery("""
                     SELECT r.code, p.code
                     FROM role_permissions rp
                     JOIN roles r ON r.id = rp.role_id
                     JOIN permissions p ON p.id = rp.permission_id
                     ORDER BY r.code, p.code
                     """)) {
            while (assignments.next()) {
                assignedPermissions.computeIfAbsent(assignments.getString(1), ignored -> new LinkedHashSet<>())
                        .add(assignments.getString(2));
            }
        }

        assertThat(assignedPermissions).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ADMIN", Set.of(
                        "user:manage", "role:manage", "employee:read", "employee:write", "contract:read",
                        "contract:write", "payroll:read", "attendance:check", "attendance:read", "attendance:write",
                        "leave:request", "overtime:request", "attendance_adjustment:request", "leave:approve_level1",
                        "overtime:approve_level1", "attendance_adjustment:approve_level1", "leave:approve_level2",
                        "overtime:approve_level2", "attendance_adjustment:approve_level2", "attendance:lock",
                        "payroll:calculate", "payroll:approve", "audit:read"),
                "HR", Set.of(
                        "employee:read", "employee:write", "contract:read", "contract:write", "payroll:read",
                        "attendance:read", "attendance:write", "leave:approve_level2", "overtime:approve_level2",
                        "attendance_adjustment:approve_level2", "attendance:lock", "payroll:calculate"),
                "MANAGER", Set.of(
                        "employee:read", "attendance:check", "attendance:read", "leave:request", "overtime:request",
                        "attendance_adjustment:request", "leave:approve_level1", "overtime:approve_level1",
                        "attendance_adjustment:approve_level1"),
                "EMPLOYEE", Set.of(
                        "employee:read", "payroll:read", "attendance:check", "attendance:read", "leave:request",
                        "overtime:request", "attendance_adjustment:request")));
    }
}
