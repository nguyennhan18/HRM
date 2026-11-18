package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.EmployeeShift;
import com.company.hrm.attendance.entity.ShiftAssignmentType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class EmployeeShiftSpecification {

    private EmployeeShiftSpecification() {
    }

    public static Specification<EmployeeShift> matching(
            UUID employeeId,
            UUID shiftId,
            ShiftAssignmentType assignmentType,
            LocalDate fromDate,
            LocalDate toDate,
            List<UUID> visibleEmployeeIds) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (visibleEmployeeIds != null) {
                if (visibleEmployeeIds.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("employeeId").in(visibleEmployeeIds));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (shiftId != null) {
                predicates.add(cb.equal(root.get("shiftId"), shiftId));
            }
            if (assignmentType != null) {
                predicates.add(cb.equal(root.get("assignmentType"), assignmentType));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), toDate));
            }
            if (fromDate != null) {
                predicates.add(cb.or(
                        cb.isNull(root.get("endDate")),
                        cb.greaterThanOrEqualTo(root.get("endDate"), fromDate)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
