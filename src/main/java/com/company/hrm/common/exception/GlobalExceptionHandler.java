package com.company.hrm.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

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

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception, HttpServletRequest request) {
        return problem(exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
        return problem(ErrorCode.HRM_CONFLICT, "Dữ liệu đã được thay đổi hoặc đang xung đột", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Lỗi không mong đợi, traceId={}", traceId, exception);
        return problem(ErrorCode.HRM_INTERNAL_ERROR, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", request, traceId);
    }

    private Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage());
    }

    private ProblemDetail problem(ErrorCode errorCode, String detail, HttpServletRequest request) {
        return problem(errorCode, detail, request, traceId(request));
    }

    private ProblemDetail problem(ErrorCode errorCode, String detail, HttpServletRequest request, String traceId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setTitle(errorCode.status().getReasonPhrase());
        problem.setType(URI.create("https://hrm.company.com/problems/" + errorCode.name().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("timestamp", Instant.now(clock).toString());
        problem.setProperty("traceId", traceId);
        return problem;
    }

    private String traceId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
