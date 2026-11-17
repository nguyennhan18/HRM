package com.company.hrm.common.web;

import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
class ErrorHandlingTestController {

    @PostMapping("/validation")
    void validate(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new BusinessException(ErrorCode.HRM_CONFLICT, "Email đã tồn tại");
    }

    @GetMapping("/unprocessable")
    void unprocessable() {
        throw new BusinessException(ErrorCode.HRM_UNPROCESSABLE_ENTITY, "Ngày kết thúc phải sau ngày bắt đầu");
    }

    @GetMapping("/contract-overlap")
    void overlap() {
        throw new org.springframework.dao.DataIntegrityViolationException("hidden DB detail",
                new java.sql.SQLException("hidden DB detail", "23P01"));
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("sensitive internal detail");
    }

    record TestRequest(@NotBlank(message = "Tên không được để trống") String name) {
    }
}
