package com.company.hrm.organization.repository;
import com.company.hrm.organization.entity.Department;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Optional<Department> findByIdAndDeletedAtIsNull(UUID id);
    Page<Department> findByDeletedAtIsNull(Pageable pageable);
    Page<Department> findByParentIdAndDeletedAtIsNull(UUID parentId, Pageable pageable);
    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);
}
