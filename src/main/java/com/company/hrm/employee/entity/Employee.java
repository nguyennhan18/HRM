package com.company.hrm.employee.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "employees")
@Getter
public class Employee extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 8)
    private String code;
    @Column(nullable = false, length = 200)
    private String fullName;
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Gender gender;
    @Column(length = 254) private String personalEmail;
    @Column(length = 254) private String companyEmail;
    @Column(length = 30) private String phone;
    @Column(length = 1000) private String address;
    @Column(length = 64) private String cccdHash;
    @Column(length = 4) private String cccdLastFour;
    @Column(length = 64) private String bankAccountHash;
    @Column(length = 4) private String bankAccountLastFour;
    // Cross-module references are IDs, never other modules' entities.
    private UUID departmentId;
    private UUID positionId;
    private UUID managerId;
    private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.DRAFT;
    private LocalDate startDate;
    private Instant deletedAt;

    protected Employee() {}

    public Employee(String code, String fullName, LocalDate dateOfBirth, Gender gender,
                    String personalEmail, String companyEmail, String phone, String address,
                    String cccdHash, String cccdLastFour, String bankAccountHash, String bankAccountLastFour,
                    UUID departmentId, UUID positionId, UUID managerId, LocalDate startDate) {
        this.code = code; this.fullName = fullName; this.dateOfBirth = dateOfBirth; this.gender = gender;
        this.personalEmail = personalEmail; this.companyEmail = companyEmail;
        this.phone = phone; this.address = address;
        this.cccdHash = cccdHash; this.cccdLastFour = cccdLastFour;
        this.bankAccountHash = bankAccountHash; this.bankAccountLastFour = bankAccountLastFour;
        this.departmentId = departmentId; this.positionId = positionId;
        this.managerId = managerId; this.startDate = startDate;
    }

    public void changeManager(UUID managerId) { this.managerId = managerId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Employee employee && getId() != null && getId().equals(employee.getId());
    }
    @Override public int hashCode() { return Employee.class.hashCode(); }
}
