package com.company.hrm.employee.service;
import com.company.hrm.contract.service.ContractEmployeeGuard;
import com.company.hrm.employee.repository.*;
import com.company.hrm.employee.entity.EmployeeStatus;
import com.company.hrm.common.exception.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class ContractEmployeeGuardImpl implements ContractEmployeeGuard {
    private final EmployeeRepository employees;
    private final EmployeeWriteLock lock;
    public ContractEmployeeGuardImpl(EmployeeRepository employees, EmployeeWriteLock lock) {
        this.employees = employees; this.lock = lock;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndValidate(UUID id) {
        lock.acquire();
        var employee = employees.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
                new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên"));
        if (employee.getStatus() == EmployeeStatus.TERMINATED)
            throw new BusinessException(ErrorCode.CONTRACT_EMPLOYEE_INELIGIBLE, "Nhân viên đã thôi việc");
    }
}
