package com.company.hrm.employee.service;
import com.company.hrm.auth.service.EmployeeRelations;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
@Component("documentAccess")
public class DocumentAccess {
    private final EmployeeRelations relations;
    public DocumentAccess(EmployeeRelations relations) { this.relations=relations; }
    public boolean canRead(UUID employeeId) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated()) return false;
        var roles=auth.getAuthorities().stream().map(a->a.getAuthority()).toList();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_HR")) return true;
        if (!roles.contains("ROLE_EMPLOYEE")) return false;
        try { return relations.employeeIdForUser(UUID.fromString(auth.getName())).filter(employeeId::equals).isPresent(); }
        catch (IllegalArgumentException e) { return false; }
    }
}
