package com.company.hrm.organization.service;

import java.util.UUID;

/** Employee supplies this port; Organization does not depend on Employee entities/repositories. */
public interface EmployeeAssignmentGuard {
    /** Acquire the shared employee write lock before any organization row locks. */
    void beforeDepartmentDelete(UUID departmentId);
    void beforePositionDelete(UUID positionId);
}
