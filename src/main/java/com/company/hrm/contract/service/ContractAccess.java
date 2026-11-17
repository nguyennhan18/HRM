package com.company.hrm.contract.service;
import com.company.hrm.auth.service.EmployeeRelations;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("contractAccess")
public class ContractAccess {
    private final ObjectProvider<EmployeeRelations> relations;
    public ContractAccess(ObjectProvider<EmployeeRelations> relations) { this.relations = relations; }
    public boolean canRead(UUID employeeId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_HR")) return true;
        if (!roles.contains("ROLE_EMPLOYEE")) return false;
        var query = relations.getIfAvailable();
        if (query == null) return false;
        try { return query.employeeIdForUser(UUID.fromString(auth.getName())).filter(employeeId::equals).isPresent(); }
        catch (IllegalArgumentException e) { return false; }
    }
}
