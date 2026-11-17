package com.company.hrm.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class AuditingConfig {
    @Bean
    org.springframework.data.auditing.DateTimeProvider auditDateTimeProvider(java.time.Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return Optional.of(auth != null && auth.isAuthenticated()
                    && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                    ? auth.getName() : "system");
        };
    }
}
