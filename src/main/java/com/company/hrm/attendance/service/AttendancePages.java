package com.company.hrm.attendance.service;

import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AttendancePages {

    private static final Set<String> SHIFT_FIELDS = Set.of(
            "id", "code", "name", "startTime", "endTime", "overnight", "defaultShift", "createdAt", "updatedAt");

    private static final Set<String> POLICY_FIELDS = Set.of(
            "id", "code", "name", "lateGraceMinutes", "earlyLeaveGraceMinutes",
            "roundingStepMinutes", "roundingMode", "halfDayThresholdMinutes",
            "minFullDayMinutes", "defaultPolicy", "createdAt", "updatedAt");

    private static final Set<String> HOLIDAY_FIELDS = Set.of(
            "id", "holidayDate", "name", "multiplier", "createdAt", "updatedAt");

    private static final Set<String> EMPLOYEE_SHIFT_FIELDS = Set.of(
            "id", "employeeId", "shiftId", "assignmentType", "startDate", "endDate", "createdAt", "updatedAt");

    private AttendancePages() {
    }

    static Pageable shifts(Pageable page) {
        return checked(page, SHIFT_FIELDS, Sort.by("code", "id"));
    }

    static Pageable policies(Pageable page) {
        return checked(page, POLICY_FIELDS, Sort.by("code", "id"));
    }

    static Pageable holidays(Pageable page) {
        return checked(page, HOLIDAY_FIELDS, Sort.by("holidayDate", "id"));
    }

    static Pageable employeeShifts(Pageable page) {
        return checked(page, EMPLOYEE_SHIFT_FIELDS, Sort.by("startDate", "id"));
    }

    private static Pageable checked(Pageable page, Set<String> allowed, Sort defaultSort) {
        if (page.getSort().stream().anyMatch(order -> !allowed.contains(order.getProperty()))) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Trường sắp xếp không hợp lệ");
        }
        Sort sort = page.getSort().isSorted() ? page.getSort() : defaultSort;
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 200), sort);
    }
}
