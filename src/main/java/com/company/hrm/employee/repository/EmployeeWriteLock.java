package com.company.hrm.employee.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All employee writers and organization deletion guards share this transaction lock. */
@Component
public class EmployeeWriteLock {
    private final JdbcTemplate jdbc;
    public EmployeeWriteLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // Separate two-integer namespace; released by PostgreSQL on commit or rollback.
        jdbc.execute("select pg_advisory_xact_lock(202602, 1)");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long nextCode() {
        return jdbc.queryForObject("select nextval('employee_code_seq')", Long.class);
    }
}
