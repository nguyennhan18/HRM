package com.company.hrm.employee.service;

import com.company.hrm.attendance.service.AttendanceEmployeeGuard;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import com.company.hrm.employee.entity.Employee;
import com.company.hrm.employee.repository.EmployeeRepository;
import com.company.hrm.employee.repository.EmployeeWriteLock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceEmployeeGuardImpl implements AttendanceEmployeeGuard {

    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;

    public AttendanceEmployeeGuardImpl(EmployeeRepository employees, EmployeeWriteLock lock) {
        this.employees = employees;
        this.lock = lock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndValidate(UUID employeeId) {
        lock.acquire();
        var employee = employees.findByIdAndDeletedAtIsNull(employeeId).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên"));
        if (!employee.canCheckAttendance()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE,
                    "Nhân viên đã thôi việc không được phân ca hoặc chấm công");
        }
    }

    @Override
    public UUID requireActiveEmployeeForUser(UUID userId) {
        if (userId == null) {
            throw ineligible();
        }
        var employee = employees.findByUserIdAndDeletedAtIsNull(userId).orElseThrow(this::ineligible);
        validateActive(employee);
        return employee.getId();
    }

    @Override
    public void requireActiveEmployee(UUID employeeId) {
        if (employeeId == null) {
            throw ineligible();
        }
        var employee = employees.findByIdAndDeletedAtIsNull(employeeId).orElseThrow(this::ineligible);
        validateActive(employee);
    }

    private void validateActive(Employee employee) {
        if (!employee.isActiveForAttendance()) {
            throw ineligible();
        }
    }

    private BusinessException ineligible() {
        return new BusinessException(ErrorCode.ATTENDANCE_EMPLOYEE_INELIGIBLE,
                "Chỉ nhân viên đang ở trạng thái ACTIVE mới được chấm công");
    }

    @Override
    public void requireExisting(UUID employeeId) {
        if (employees.findByIdAndDeletedAtIsNull(employeeId).isEmpty()) {
            throw new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên");
        }
    }

    @Override
    public List<UUID> visibleEmployeeIds(UUID userId, boolean manager, boolean employee) {
        return employees.visibleIds(userId, manager, employee);
    }
}
