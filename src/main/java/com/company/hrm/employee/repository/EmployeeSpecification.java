package com.company.hrm.employee.repository;

import com.company.hrm.employee.entity.*;
import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class EmployeeSpecification {
    private EmployeeSpecification() {}

    public static Specification<Employee> matching(String name, UUID departmentId, UUID positionId,
            EmployeeStatus status, List<UUID> visibleIds) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (visibleIds != null) predicates.add(visibleIds.isEmpty() ? cb.disjunction() : root.get("id").in(visibleIds));
            if (departmentId != null) predicates.add(cb.equal(root.get("departmentId"), departmentId));
            if (positionId != null) predicates.add(cb.equal(root.get("positionId"), positionId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (name != null && !name.isBlank()) {
                String escaped = name.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(
                        cb.function("unaccent", String.class, cb.lower(root.get("fullName"))),
                        cb.function("unaccent", String.class, cb.lower(cb.literal("%" + escaped + "%"))), '!'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
