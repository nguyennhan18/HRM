package com.company.hrm.auth.service;

import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("authz")
public class AuthorizationService {
    private final ObjectProvider<EmployeeRelations> relations;

    public AuthorizationService(ObjectProvider<EmployeeRelations> relations) {
        this.relations = relations;
    }

    public boolean canViewEmployee(UUID id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (id == null || authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return false;
        var roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_HR")) return true;
        if (!roles.contains("ROLE_MANAGER") && !roles.contains("ROLE_EMPLOYEE")) return false;
        var query = relations.getIfAvailable();
        // Fail closed if the Employee adapter is unavailable.
        if (query == null) return false;
        UUID userId;
        try { userId = UUID.fromString(authentication.getName()); }
        catch (IllegalArgumentException ex) { return false; }
        var own = query.employeeIdForUser(userId).orElse(null);
        if (own == null) return false;
        if (own.equals(id)) return roles.contains("ROLE_EMPLOYEE");
        if (!roles.contains("ROLE_MANAGER")) return false;
        var visited = new HashSet<UUID>();
        UUID current = id;
        boolean subordinate = false;
        while (current != null) {
            // Reject a malformed cyclic hierarchy, even if it contains this manager.
            if (!visited.add(current)) return false;
            current = query.managerIdOf(current).orElse(null);
            if (own.equals(current)) subordinate = true;
        }
        return subordinate;
    }

    public boolean canViewSensitiveData(UUID employeeId) {
        return employeeId != null && sensitiveDataFilter().test(employeeId);
    }

    public Predicate<UUID> sensitiveDataFilter() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return id -> false;
        }
        var roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_HR")) {
            return Objects::nonNull;
        }
        if (!roles.contains("ROLE_EMPLOYEE")) {
            return id -> false;
        }
        var query = relations.getIfAvailable();
        if (query == null) {
            return id -> false;
        }
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return id -> false;
        }
        var own = query.employeeIdForUser(userId).orElse(null);
        if (own == null) {
            return id -> false;
        }
        return own::equals;
    }
}
