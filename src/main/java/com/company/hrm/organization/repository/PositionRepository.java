package com.company.hrm.organization.repository;
import com.company.hrm.organization.entity.Position;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface PositionRepository extends JpaRepository<Position, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.id = :id")
    Optional<Position> lockById(UUID id);
}
