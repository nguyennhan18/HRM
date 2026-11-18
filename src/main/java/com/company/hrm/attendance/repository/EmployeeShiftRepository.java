package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.EmployeeShift;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeShiftRepository
        extends JpaRepository<EmployeeShift, UUID>, JpaSpecificationExecutor<EmployeeShift> {

    boolean existsByShiftId(UUID shiftId);

    @Query(value = """
            select exists(select 1 from employee_shifts where employee_id = :employeeId
                and assignment_type = :assignmentType
                and id <> :excludeId
                and daterange(start_date, end_date, '[]') && daterange(cast(:startDate as date), cast(:endDate as date), '[]'))
            """, nativeQuery = true)
    boolean overlaps(UUID employeeId, String assignmentType, UUID excludeId, LocalDate startDate, LocalDate endDate);

    @Query("""
            select es from EmployeeShift es
            where es.employeeId = :employeeId
              and es.startDate <= :workDate
              and (es.endDate is null or es.endDate >= :workDate)
            """)
    List<EmployeeShift> findCoveringDate(UUID employeeId, LocalDate workDate);
}
