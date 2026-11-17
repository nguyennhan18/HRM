package com.company.hrm.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.ProblemDetail;

public final class ApiProblems {
    private ApiProblems() {}

    public static ProblemDetail create(ErrorCode code, String detail, HttpServletRequest request,
            Clock clock, String traceId) {
        var problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.status().getReasonPhrase());
        problem.setType(URI.create("https://hrm.company.com/problems/" + code.name().toLowerCase(java.util.Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", clock.instant().toString());
        problem.setProperty("traceId", traceId);
        return problem;
    }

    public static String traceId(HttpServletRequest request) {
        var value = request.getHeader("X-Correlation-ID");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
