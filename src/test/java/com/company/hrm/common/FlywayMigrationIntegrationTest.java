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

        assertThat(flyway.info().applied()).hasSize(11);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
    }

    @Test
    void should_upgrade_v10_preserve_employee_and_enforce_termination_constraints() throws Exception {
        String schema = "termination_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("10").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into employees(id,code,full_name,status,start_date,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000004','NV000001','Nhân viên mẫu','ACTIVE','2026-06-01',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("11").load().migrate();
            try (var rows = statement.executeQuery("select count(*) from employees where code='NV000001'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
            for (String invalidSql : java.util.List.of(
                    "update employees set termination_date='2026-06-15', termination_reason='Nghỉ' where code='NV000001'",
                    "update employees set status='TERMINATED', termination_date='2026-06-15', termination_reason='   ' where code='NV000001'",
                    "update employees set status='TERMINATED', termination_date='2026-05-31', termination_reason='Nghỉ' where code='NV000001'",
                    "update employees set status='TERMINATED', termination_date='2026-06-15' where code='NV000001'")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(invalidSql))
                        .isInstanceOf(java.sql.SQLException.class);
            }
            statement.executeUpdate("""
                    update employees set status='TERMINATED', termination_date='2026-06-15', termination_reason='Nghỉ việc'
                    where code='NV000001'
                    """);
            try (var rows = statement.executeQuery("select status, termination_reason from employees where code='NV000001'")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("TERMINATED");
                assertThat(rows.getString(2)).isEqualTo("Nghỉ việc");
            }
        }
    }

    @Test
    void should_upgrade_v9_preserve_employee_and_enforce_document_constraints() throws Exception {
        String schema = "document_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("9").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into employees(id,code,full_name,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000003','NV000001','Nhân viên mẫu','DRAFT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("10").load().migrate();
            try (var rows = statement.executeQuery("select count(*) from employees where code='NV000001'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
            String insert = """
                    insert into employee_documents(id,employee_id,type,original_name,stored_name,size,content_type,
                        checksum,created_at,updated_at,created_by,updated_by,version)
                    values (gen_random_uuid(),'00000000-0000-4000-8000-000000000003','CV','cv.pdf',
                        gen_random_uuid()::text,%s,'application/pdf',repeat('a',64),current_timestamp,current_timestamp,'test','test',0)
                    """;
            for (long size : new long[]{0, -1, 10485761}) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(insert.formatted(size)))
                        .isInstanceOf(java.sql.SQLException.class);
            }
            statement.executeUpdate(insert.formatted(10485760));
            try (var rows = statement.executeQuery("select count(*) from employee_documents")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void should_upgrade_v8_with_existing_employee_and_preserve_data() throws Exception {
        String schema = "contract_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("8").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into employees(id,code,full_name,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000002','NV000001','Nhân viên mẫu','DRAFT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("9").load().migrate();
            try (var rows = statement.executeQuery("select count(*) from employees where code='NV000001'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("select count(*) from contracts")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
            try (var rows = statement.executeQuery("select has_database_privilege(current_user,current_database(),'CREATE')")) {
                rows.next(); assertThat(rows.getBoolean(1)).isTrue();
            }
        }
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
