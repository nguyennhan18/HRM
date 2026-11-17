package com.company.hrm.contract.service;
import java.util.UUID;
/** Implemented by Employee; contract never accesses another module's entity/repository. */
public interface ContractEmployeeGuard {
    void lockAndValidate(UUID employeeId);
}
