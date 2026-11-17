package com.company.hrm.organization.service;
import com.company.hrm.common.exception.*;
import java.util.Set;
import org.springframework.data.domain.*;
final class OrganizationPages {
    private OrganizationPages() {}
    static Pageable checked(Pageable page) {
        var allowed = Set.of("id", "code", "name", "createdAt", "updatedAt");
        if (page.getSort().stream().anyMatch(order -> !allowed.contains(order.getProperty()))) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Trường sắp xếp không hợp lệ");
        }
        return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 200),
                page.getSort().isSorted() ? page.getSort().and(Sort.by("id")) : Sort.by("code", "id"));
    }
}
