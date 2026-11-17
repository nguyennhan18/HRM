package com.company.hrm.auth.service;

import com.company.hrm.auth.config.BootstrapAdminProperties;
import com.company.hrm.auth.entity.Role;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminProvisioner.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminProvisioner(BootstrapAdminProperties properties, UserRepository userRepository,
                                    RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        provisionIfConfigured();
    }

    @Transactional
    public void provisionIfConfigured() {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            if (!isBlank(properties.email()) || !isBlank(properties.password())) {
                log.warn("Bootstrap admin was skipped because both HRM_ADMIN_EMAIL and HRM_ADMIN_PASSWORD are required");
            }
            return;
        }
        if (userRepository.existsByRoles_Code(ADMIN_ROLE)) {
            return;
        }
        if (userRepository.findByEmail(properties.email()).isPresent()) {
            log.warn("Bootstrap admin was skipped because the configured email is already in use");
            return;
        }

        Role adminRole = roleRepository.findByCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Seeded ADMIN role is missing"));
        userRepository.save(new User(properties.email(), passwordEncoder.encode(properties.password()), adminRole));
        log.info("Bootstrap admin account was created");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
