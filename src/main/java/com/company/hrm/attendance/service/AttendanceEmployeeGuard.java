package com.company.hrm.attendance.service;

import java.util.List;
import java.util.UUID;

/** Implemented by Employee module; attendance never accesses another module's entity/repository. */
public interface AttendanceEmployeeGuard {

    void lockAndValidate(UUID employeeId);

    void requireExisting(UUID employeeId);

    List<UUID> visibleEmployeeIds(UUID userId, boolean manager, boolean employee);
}
