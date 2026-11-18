package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.Shift;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    boolean existsByCode(String code);

    Optional<Shift> findByDefaultShiftTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id")
    Optional<Shift> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.defaultShift = true")
    Optional<Shift> lockDefaultShift();
}
