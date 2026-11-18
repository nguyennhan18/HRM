package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.Holiday;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    boolean existsByHolidayDate(LocalDate holidayDate);

    boolean existsByHolidayDateAndIdNot(LocalDate holidayDate, UUID id);

    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);

    Page<Holiday> findByHolidayDateBetween(LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<Holiday> findByHolidayDateGreaterThanEqual(LocalDate fromDate, Pageable pageable);

    Page<Holiday> findByHolidayDateLessThanEqual(LocalDate toDate, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holiday h where h.id = :id")
    Optional<Holiday> lockById(UUID id);
}
