package com.company.hrm.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb", updatable = false)
    private JsonNode beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb", updatable = false)
    private JsonNode afterJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "trace_id", updatable = false, length = 100)
    private String traceId;

    protected AuditLog() {
    }

    AuditLog(UUID id, String actor, String action, String entityType, UUID entityId, JsonNode beforeJson,
             JsonNode afterJson, String traceId) {
        this.id = id;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.traceId = traceId;
    }

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public JsonNode getBeforeJson() {
        return beforeJson == null ? null : beforeJson.deepCopy();
    }

    public JsonNode getAfterJson() {
        return afterJson == null ? null : afterJson.deepCopy();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getTraceId() {
        return traceId;
    }
}
