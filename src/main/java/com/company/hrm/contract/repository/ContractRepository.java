package com.company.hrm.contract.repository;
import com.company.hrm.contract.entity.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByIdAndDeletedAtIsNull(UUID id);
    List<Contract> findByEmployeeIdAndStatusAndDeletedAtIsNull(UUID employeeId, ContractStatus status);
    Page<Contract> findByEmployeeIdAndDeletedAtIsNull(UUID employeeId, Pageable page);
    Page<Contract> findByStatusAndDeletedAtIsNullAndEndDateBetween(
            ContractStatus status, LocalDate from, LocalDate to, Pageable page);
    @Query(value = """
            select exists(select 1 from contracts where employee_id = :employeeId and id <> :id
                and deleted_at is null and status <> 'DRAFT'
                and daterange(start_date, end_date, '[]') && daterange(cast(:start as date), cast(:end as date), '[]'))
            """, nativeQuery = true)
    boolean overlaps(UUID employeeId, UUID id, LocalDate start, LocalDate end);
}
