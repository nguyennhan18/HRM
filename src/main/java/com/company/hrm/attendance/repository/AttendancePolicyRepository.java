package com.company.hrm.attendance.repository;

import com.company.hrm.attendance.entity.AttendancePolicy;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, UUID> {

    boolean existsByCode(String code);

    Optional<AttendancePolicy> findByDefaultPolicyTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AttendancePolicy p where p.id = :id")
    Optional<AttendancePolicy> lockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AttendancePolicy p where p.defaultPolicy = true")
    Optional<AttendancePolicy> lockDefaultPolicy();
}
