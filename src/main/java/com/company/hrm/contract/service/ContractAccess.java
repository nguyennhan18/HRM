package com.company.hrm.contract.service;

import com.company.hrm.auth.service.AuthorizationService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("contractAccess")
public class ContractAccess {
    private final AuthorizationService authz;

    public ContractAccess(AuthorizationService authz) {
        this.authz = authz;
    }

    public boolean canRead(UUID employeeId) {
        return authz.canViewEmployee(employeeId);
    }

    public boolean canViewSalary(UUID employeeId) {
        return authz.canViewSensitiveData(employeeId);
    }
}
