package com.company.hrm.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hrm.admin")
public record BootstrapAdminProperties(String email, String password) {
}
