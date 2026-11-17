package com.company.hrm.employee.repository;

import com.company.hrm.employee.entity.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Employee> {
    // One query for the complete scope. Deleted nodes break chains; cycles fail closed.
    @Query(value = """
            with recursive own as (
                select id, manager_id from employees where user_id = :userId and deleted_at is null
            ), ancestors as (
                select id, manager_id, array[id] as path, false as cycle from own
                union all
                select e.id, e.manager_id, a.path || e.id, e.id = any(a.path)
                from employees e join ancestors a on e.id = a.manager_id
                where e.deleted_at is null and not a.cycle
            ), descendants(id) as (
                select id from own
                union
                select e.id from employees e join descendants d on e.manager_id = d.id
                where e.deleted_at is null and :manager
                  and not exists(select 1 from ancestors where cycle)
            )
            select id from descendants
            where (:self and id in (select id from own))
               or (:manager and id not in (select id from own))
            """, nativeQuery = true)
    java.util.List<UUID> visibleIds(UUID userId, boolean manager, boolean self);

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
