package com.company.hrm.employee.repository;

import com.company.hrm.employee.entity.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByCompanyEmail(String companyEmail);
    boolean existsByCccdHash(String cccdHash);
    boolean existsByPositionId(UUID positionId);

    @Query("select e.id from Employee e where e.userId = :userId and e.deletedAt is null")
    Optional<UUID> employeeIdForUser(UUID userId);

    @Query("""
            select m.id from Employee e, Employee m
            where e.id = :employeeId and e.managerId = m.id
              and e.deletedAt is null and m.deletedAt is null
            """)
    Optional<UUID> managerIdOf(UUID employeeId);

    @Query(value = """
            select exists(select 1 from employees where department_id = :departmentId
                and deleted_at is null and status in ('ACTIVE', 'ON_LEAVE_LONG'))
            """, nativeQuery = true)
    boolean hasCurrentStaff(UUID departmentId);
}
