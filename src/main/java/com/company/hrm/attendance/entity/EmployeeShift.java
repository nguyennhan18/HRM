package com.company.hrm.attendance.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "employee_shifts")
@Getter
public class EmployeeShift extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private ShiftAssignmentType assignmentType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected EmployeeShift() {
    }

    public EmployeeShift(UUID employeeId, UUID shiftId, ShiftAssignmentType assignmentType,
            LocalDate startDate, LocalDate endDate) {
        this.employeeId = employeeId;
        this.shiftId = shiftId;
        this.assignmentType = assignmentType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(UUID employeeId, UUID shiftId, ShiftAssignmentType assignmentType,
            LocalDate startDate, LocalDate endDate) {
        this.employeeId = employeeId;
        this.shiftId = shiftId;
        this.assignmentType = assignmentType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof EmployeeShift es && getId() != null && getId().equals(es.getId()));
    }

    @Override
    public int hashCode() {
        return EmployeeShift.class.hashCode();
    }

    @Override
    public String toString() {
        return "EmployeeShift[id=" + getId() + ", employeeId=" + employeeId + ", assignmentType=" + assignmentType + "]";
    }
}
