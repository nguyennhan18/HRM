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
        assertThat(authz.canViewSensitiveData(id)).isTrue();
        assertThat(authz.canViewSensitiveData(null)).isFalse();
    }

    @Test void should_evaluate_sensitive_data_access_with_role_union_and_fail_closed() {
        UUID userId = UUID.randomUUID(), ownId = UUID.randomUUID(), subId = UUID.randomUUID();
        var withoutAdapter = new AuthorizationService(new DefaultListableBeanFactory().getBeanProvider(EmployeeRelations.class));
        assertThat(withoutAdapter.canViewSensitiveData(ownId)).isFalse();

        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("employeeRelations", new EmployeeRelations() {
            @Override public Optional<UUID> employeeIdForUser(UUID id) {
                return userId.equals(id) ? Optional.of(ownId) : Optional.empty();
            }
            @Override public Optional<UUID> managerIdOf(UUID id) {
                return subId.equals(id) ? Optional.of(ownId) : Optional.empty();
            }
        });
        var authz = new AuthorizationService(factory.getBeanProvider(EmployeeRelations.class));

        // Anonymous or non-UUID principal -> denied
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThat(authz.canViewSensitiveData(ownId)).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "not-a-uuid", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
        assertThat(authz.canViewSensitiveData(ownId)).isFalse();

        // Unlinked user -> denied
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
        assertThat(authz.canViewSensitiveData(ownId)).isFalse();

        // Pure MANAGER -> denied for both own and subordinate
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThat(authz.canViewSensitiveData(ownId)).isFalse();
        assertThat(authz.canViewSensitiveData(subId)).isFalse();

        // MANAGER + EMPLOYEE -> allowed for own, denied for subordinate
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(
                        new SimpleGrantedAuthority("ROLE_MANAGER"),
                        new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
        assertThat(authz.canViewSensitiveData(ownId)).isTrue();
        assertThat(authz.canViewSensitiveData(subId)).isFalse();

        // MANAGER + HR and MANAGER + ADMIN -> highest privilege wins
        for (String highRole : List.of("ROLE_HR", "ROLE_ADMIN")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    userId.toString(), null, List.of(
                            new SimpleGrantedAuthority("ROLE_MANAGER"),
                            new SimpleGrantedAuthority(highRole))));
            assertThat(authz.canViewSensitiveData(ownId)).isTrue();
            assertThat(authz.canViewSensitiveData(subId)).isTrue();
        }
    }
}
