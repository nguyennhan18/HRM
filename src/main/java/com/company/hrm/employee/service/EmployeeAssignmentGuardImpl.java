package com.company.hrm.employee.service;

import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.employee.repository.EmployeeRepository;
import com.company.hrm.employee.repository.EmployeeWriteLock;
import com.company.hrm.organization.service.EmployeeAssignmentGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EmployeeAssignmentGuardImpl implements EmployeeAssignmentGuard {
    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;
    public EmployeeAssignmentGuardImpl(EmployeeRepository employees, EmployeeWriteLock lock) {
        this.employees = employees; this.lock = lock;
    }
    public void beforeDepartmentDelete(UUID departmentId) {
        lock.acquire();
        if (employees.hasCurrentStaff(departmentId)) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Phòng ban còn nhân viên đang làm việc hoặc nghỉ dài hạn");
        }
    }
    public void beforePositionDelete(UUID positionId) {
        lock.acquire();
        if (employees.existsByPositionId(positionId)) {
            throw new BusinessException(ErrorCode.HRM_CONFLICT, "Chức danh còn hồ sơ nhân viên tham chiếu");
        }
    }
}
