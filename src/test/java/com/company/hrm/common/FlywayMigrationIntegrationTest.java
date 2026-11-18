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

        assertThat(flyway.info().applied()).hasSize(15);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
    }

    @Test
    void should_upgrade_v14_to_v15_and_enforce_attendance_record_constraints() throws Exception {
        String schema = "attendance_records_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("14").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into employees(id,code,full_name,status,start_date,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000008','NV000001','Nhân viên chấm công','ACTIVE','2026-06-01',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("15").load().migrate();
            try (var rows = statement.executeQuery("select count(*) from employees where code='NV000001'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            statement.executeUpdate("""
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000021','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-01',
                        '2026-06-01T01:00:00Z','WEB',null,null,'MISSING_CHECKOUT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            for (String invalidSql : java.util.List.of(
                    // Duplicate (employee_id, work_date) -> 23505
                    """
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000022','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-01',
                        '2026-06-01T01:05:00Z','WEB',null,null,'MISSING_CHECKOUT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // Invalid check_in_source -> 23514
                    """
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000023','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-02',
                        '2026-06-02T01:00:00Z','SMS',null,null,'MISSING_CHECKOUT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // check_out without check_in -> 23514
                    """
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000024','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-03',
                        null,null,'2026-06-03T10:00:00Z','WEB','PRESENT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // check_out < check_in -> 23514
                    """
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000025','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-04',
                        '2026-06-04T09:00:00Z','WEB','2026-06-04T08:00:00Z','WEB','PRESENT',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // Invalid status -> 23514
                    """
                    insert into attendance_records(id,employee_id,shift_id,work_date,check_in_at,check_in_source,
                        check_out_at,check_out_source,status,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000026','00000000-0000-4000-8000-000000000008',
                        '019c9e00-0000-7000-8000-000000000301','2026-06-05',
                        '2026-06-05T01:00:00Z','WEB',null,null,'UNKNOWN',
                        current_timestamp,current_timestamp,'test','test',0)
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(invalidSql))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test
    void should_upgrade_v12_with_existing_employee_to_v14_and_verify_seeds_and_constraints() throws Exception {
        String schema = "attendance_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("12").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into employees(id,code,full_name,status,start_date,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000007','NV000001','Nhân viên nâng cấp','ACTIVE','2026-06-01',
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("14").load().migrate();
            try (var rows = statement.executeQuery("select count(*) from employees where code='NV000001'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("select code, overnight, is_default from shifts order by code")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("HC");
                assertThat(rows.getBoolean(2)).isFalse();
                assertThat(rows.getBoolean(3)).isTrue();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("NIGHT");
                assertThat(rows.getBoolean(2)).isTrue();
                assertThat(rows.getBoolean(3)).isFalse();
            }
            try (var rows = statement.executeQuery("""
                    select code, late_grace_minutes, early_leave_grace_minutes, rounding_step_minutes,
                           rounding_mode, half_day_threshold_minutes, min_full_day_minutes, is_default
                    from attendance_policies
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("DEFAULT");
                assertThat(rows.getInt(2)).isEqualTo(15);
                assertThat(rows.getInt(3)).isEqualTo(0);
                assertThat(rows.getInt(4)).isEqualTo(15);
                assertThat(rows.getString(5)).isEqualTo("NEAREST");
                assertThat(rows.getInt(6)).isEqualTo(240);
                assertThat(rows.getInt(7)).isEqualTo(480);
                assertThat(rows.getBoolean(8)).isTrue();
            }
            statement.executeUpdate("""
                    insert into employee_shifts(id,employee_id,shift_id,assignment_type,start_date,end_date,
                        created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000007',
                        '019c9e00-0000-7000-8000-000000000301','DEFAULT','2026-06-01',null,
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            for (String invalidSql : java.util.List.of(
                    // Overlapping open-ended DEFAULT assignment -> 23P01
                    """
                    insert into employee_shifts(id,employee_id,shift_id,assignment_type,start_date,end_date,
                        created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000007',
                        '019c9e00-0000-7000-8000-000000000302','DEFAULT','2026-06-15','2026-06-30',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // WEEKLY exceeding 7 days (end_date - start_date = 7 > 6) -> 23514
                    """
                    insert into employee_shifts(id,employee_id,shift_id,assignment_type,start_date,end_date,
                        created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000012','00000000-0000-4000-8000-000000000007',
                        '019c9e00-0000-7000-8000-000000000302','WEEKLY','2026-06-01','2026-06-08',
                        current_timestamp,current_timestamp,'test','test',0)
                    """,
                    // DAILY with start_date <> end_date -> 23514
                    """
                    insert into employee_shifts(id,employee_id,shift_id,assignment_type,start_date,end_date,
                        created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000013','00000000-0000-4000-8000-000000000007',
                        '019c9e00-0000-7000-8000-000000000302','DAILY','2026-06-01','2026-06-02',
                        current_timestamp,current_timestamp,'test','test',0)
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(invalidSql))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test
    void should_upgrade_v11_preserve_users_and_backfill_must_change_password_default() throws Exception {
        String schema = "onboarding_upgrade";
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        configuration.target("11").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.executeUpdate("""
                    insert into users(id,email,password_hash,enabled,failed_attempts,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000005','legacy@example.com','$2a$10$hash',true,0,
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            configuration.target("12").load().migrate();
            try (var rows = statement.executeQuery("select must_change_password from users where email='legacy@example.com'")) {
                rows.next();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    statement.executeUpdate("update users set must_change_password = null where email='legacy@example.com'"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("""
                    insert into users(id,email,password_hash,enabled,failed_attempts,created_at,updated_at,created_by,updated_by,version)
                    values ('00000000-0000-4000-8000-000000000006','default@example.com','$2a$10$hash',true,0,
                        current_timestamp,current_timestamp,'test','test',0)
                    """);
            try (var rows = statement.executeQuery("select must_change_password from users where email='default@example.com'")) {
                rows.next();
                assertThat(rows.getBoolean(1)).isFalse();
            }
        }
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
