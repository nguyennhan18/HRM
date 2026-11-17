package com.company.hrm.common.audit;

import com.company.hrm.common.entity.UuidV7Generator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;
    private final AuditPayloadMasker auditPayloadMasker;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, AuditPayloadMasker auditPayloadMasker,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditPayloadMasker = auditPayloadMasker;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String action, String entityType, UUID entityId, Object before, Object after,
                           String traceId) {
        AuditLog auditLog = new AuditLog(
                UuidV7Generator.next(),
                currentActor(),
                action,
                entityType,
                entityId,
                maskedJson(before),
                maskedJson(after),
                traceId);
        return auditLogRepository.save(auditLog);
    }

    private JsonNode maskedJson(Object payload) {
        return payload == null ? null : auditPayloadMasker.mask(objectMapper.valueToTree(payload));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }
}
