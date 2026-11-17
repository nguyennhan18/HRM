package com.company.hrm.auth;

import com.company.hrm.auth.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void should_fail_closed_without_employee_adapter_or_authentication() {
        var authz = new AuthorizationService(new DefaultListableBeanFactory().getBeanProvider(EmployeeRelations.class));
        UUID id = UUID.randomUUID();
        assertThat(authz.canViewEmployee(id)).isFalse();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThat(authz.canViewEmployee(id)).isFalse();
        for (String role : List.of("MANAGER", "EMPLOYEE")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            assertThat(authz.canViewEmployee(id)).isFalse();
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThat(authz.canViewEmployee(id)).isTrue();
        assertThat(authz.canViewEmployee(null)).isFalse();
    }
}
