package com.company.hrm.common.audit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(UUID id);
}
