package com.company.hrm.employee.service;

import com.company.hrm.auth.service.EmployeeRelations;
import com.company.hrm.employee.repository.EmployeeRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeRelationsAdapter implements EmployeeRelations {
    private final EmployeeRepository employees;
    public EmployeeRelationsAdapter(EmployeeRepository employees) { this.employees = employees; }
    public Optional<UUID> employeeIdForUser(UUID userId) { return employees.employeeIdForUser(userId); }
    public Optional<UUID> managerIdOf(UUID employeeId) { return employees.managerIdOf(employeeId); }
}
