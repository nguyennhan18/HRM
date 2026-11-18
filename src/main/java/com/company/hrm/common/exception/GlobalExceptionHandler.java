package com.company.hrm.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        ProblemDetail problem = problem(ErrorCode.HRM_VALIDATION_ERROR, "Dữ liệu yêu cầu không hợp lệ", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ProblemDetail handleParameterType(HttpServletRequest request) {
        return problem(ErrorCode.HRM_VALIDATION_ERROR, "Tham số yêu cầu không hợp lệ", request);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail handleMethodValidation(HttpServletRequest request) {
        return problem(ErrorCode.HRM_VALIDATION_ERROR, "Dữ liệu yêu cầu không hợp lệ", request);
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception, HttpServletRequest request) {
        return problem(exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpServletRequest request) {
        return problem(ErrorCode.HRM_VALIDATION_ERROR, "Dữ liệu yêu cầu không hợp lệ", request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ProblemDetail handleAccessDenied(HttpServletRequest request) {
        return problem(ErrorCode.HRM_ACCESS_DENIED, "Không có quyền truy cập", request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                if (sql.getMessage() != null && sql.getMessage().contains("ex_employee_shifts_overlap")) {
                    return problem(ErrorCode.SHIFT_ASSIGNMENT_OVERLAP, "Phân ca nhân viên bị trùng khoảng thời gian", request);
                }
                return problem(ErrorCode.CONTRACT_OVERLAP, "Hiệu lực hợp đồng chồng lấn", request);
            }
        }
        return problem(ErrorCode.HRM_CONFLICT, "Dữ liệu đã được thay đổi hoặc đang xung đột", request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadSize(HttpServletRequest request) {
        return problem(ErrorCode.DOCUMENT_TOO_LARGE, "Tài liệu vượt dung lượng cho phép", request);
    }

    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ProblemDetail handleMultipart(HttpServletRequest request) {
        return problem(ErrorCode.DOCUMENT_INVALID_FILE, "Yêu cầu tải lên không hợp lệ", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Lỗi không mong đợi, traceId={}, exceptionType={}", traceId, exception.getClass().getName());
        return problem(ErrorCode.HRM_INTERNAL_ERROR, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", request, traceId);
    }

    private Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage());
    }

    private ProblemDetail problem(ErrorCode errorCode, String detail, HttpServletRequest request) {
        return problem(errorCode, detail, request, traceId(request));
    }

    private ProblemDetail problem(ErrorCode errorCode, String detail, HttpServletRequest request, String traceId) {
        return ApiProblems.create(errorCode, detail, request, clock, traceId);
    }

    private String traceId(HttpServletRequest request) {
        return ApiProblems.traceId(request);
    }
}
