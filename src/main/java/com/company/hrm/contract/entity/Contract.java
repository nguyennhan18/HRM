package com.company.hrm.contract.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.Getter;

@Entity @Table(name = "contracts") @Getter
public class Contract extends BaseEntity {
    @Column(nullable = false) private UUID employeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContractType type;
    @Column(nullable = false) private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false, precision = 18, scale = 0) private BigDecimal baseSalary;
    @Column(nullable = false, precision = 18, scale = 0) private BigDecimal allowance;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;
    private Instant deletedAt;
    public Contract() {}
    public void revise(UUID employeeId, ContractType type, LocalDate startDate, LocalDate endDate,
                       BigDecimal baseSalary, BigDecimal allowance) {
        this.employeeId = employeeId; this.type = type; this.startDate = startDate;
        this.endDate = endDate; this.baseSalary = baseSalary; this.allowance = allowance;
    }
    public void activate() { status = ContractStatus.ACTIVE; }
    public void terminate(LocalDate date) { endDate = date; status = ContractStatus.TERMINATED; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof Contract c && getId() != null && getId().equals(c.getId());
    }
    @Override public int hashCode() { return Contract.class.hashCode(); }
    @Override public String toString() { return "Contract[id=" + getId() + ", status=" + status + "]"; }
}
