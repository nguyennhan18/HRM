package com.company.hrm.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    HRM_VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    HRM_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    HRM_CONFLICT(HttpStatus.CONFLICT),
    HRM_UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY),
    HRM_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
