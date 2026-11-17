package com.company.hrm.contract.service;
import com.company.hrm.contract.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.data.domain.*;
public interface ContractService {
    ContractResponse create(@NotNull @Valid CreateContractRequest request, String traceId);
    ContractResponse update(UUID id, @NotNull @Valid UpdateContractRequest request, String traceId);
    ContractResponse activate(UUID id, String traceId);
    ContractResponse terminate(UUID id, @NotNull @Valid TerminateContractRequest request, String traceId);
    ContractResponse get(UUID id);
    Page<ContractResponse> list(UUID employeeId, Pageable page);
    Page<ContractResponse> expiring(int days, Pageable page);
}
