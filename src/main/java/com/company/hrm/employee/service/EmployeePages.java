package com.company.hrm.employee.service;

import com.company.hrm.common.exception.*;
import java.util.Set;
import org.springframework.data.domain.*;

final class EmployeePages {
    private static final Set<String> SORT_FIELDS = Set.of("id", "code", "fullName", "status", "startDate", "createdAt", "updatedAt");
    private EmployeePages() {}
    static Pageable checked(Pageable page) {
        if (page.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Trường sắp xếp không hợp lệ");
        }
        Sort sort = page.getSort().isSorted() ? page.getSort() : Sort.by("code");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 200), sort);
    }
}
