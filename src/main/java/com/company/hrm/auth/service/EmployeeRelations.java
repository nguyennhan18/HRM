package com.company.hrm.auth.service;

import java.util.Optional;
import java.util.UUID;

/** Read-only port to be implemented by Employee at T2.1; IDs are not interchangeable. */
public interface EmployeeRelations {
    Optional<UUID> employeeIdForUser(UUID userId);
    Optional<UUID> managerIdOf(UUID employeeId);
}
