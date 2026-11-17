package com.company.hrm.auth.config;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("hrm.auth")
public record AuthProperties(String secret, Duration accessTtl, Duration refreshTtl, Duration resetTtl,
                             int lockThreshold, Duration lockDuration, int rateLimit, Duration rateWindow,
                             List<String> corsOrigins) {
    public AuthProperties {
        if (accessTtl == null || refreshTtl == null || resetTtl == null || lockDuration == null
                || rateWindow == null || accessTtl.isNegative() || accessTtl.isZero()
                || refreshTtl.isNegative() || refreshTtl.isZero() || resetTtl.isNegative() || resetTtl.isZero()
                || lockDuration.isNegative() || lockDuration.isZero() || rateWindow.isNegative()
                || rateWindow.isZero() || lockThreshold < 1 || rateLimit < 1) {
            throw new IllegalArgumentException("Invalid authentication policy configuration");
        }
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }
}

