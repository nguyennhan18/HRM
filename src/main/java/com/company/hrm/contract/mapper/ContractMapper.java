package com.company.hrm.contract.mapper;
import com.company.hrm.contract.entity.Contract;
import com.company.hrm.contract.dto.ContractResponse;
import org.mapstruct.*;
@Mapper(componentModel = "spring")
public interface ContractMapper {
    @Mapping(target = "expired", source = "expired")
    ContractResponse toResponse(Contract contract, boolean expired);
}
