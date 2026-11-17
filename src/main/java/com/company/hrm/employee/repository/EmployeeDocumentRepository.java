package com.company.hrm.employee.repository;
import com.company.hrm.employee.entity.EmployeeDocument;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {
    Optional<EmployeeDocument> findByIdAndEmployeeId(UUID id, UUID employeeId);
}
