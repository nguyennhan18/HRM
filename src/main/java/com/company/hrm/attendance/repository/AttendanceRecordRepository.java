package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceRecord r where r.employeeId = :employeeId and r.workDate = :workDate")
    Optional<AttendanceRecord> findLockedByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from AttendanceRecord r
            where r.employeeId = :employeeId
              and r.workDate in :workDates
              and r.checkInAt is not null
              and r.checkOutAt is null
            order by r.workDate desc
            """)
    List<AttendanceRecord> findLockedOpenRecordsByEmployeeIdAndWorkDateIn(
            UUID employeeId,
            Collection<LocalDate> workDates);
}
