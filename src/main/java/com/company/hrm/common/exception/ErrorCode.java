package com.company.hrm.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    HRM_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED),
    HRM_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    HRM_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    HRM_VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    HRM_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONTRACT_OVERLAP(HttpStatus.CONFLICT),
    CONTRACT_TERMINATED_IMMUTABLE(HttpStatus.CONFLICT),
    CONTRACT_INVALID_TRANSITION(HttpStatus.CONFLICT),
    CONTRACT_ACTIVE_RESTRICTED(HttpStatus.CONFLICT),
    CONTRACT_EMPLOYEE_INELIGIBLE(HttpStatus.CONFLICT),
    DOCUMENT_INVALID_FILE(HttpStatus.BAD_REQUEST),
    DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    DOCUMENT_EMPLOYEE_INELIGIBLE(HttpStatus.CONFLICT),
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
