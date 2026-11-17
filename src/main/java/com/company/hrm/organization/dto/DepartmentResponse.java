package com.company.hrm.organization.dto;
import java.time.Instant;
import java.util.UUID;
public record DepartmentResponse(UUID id, String code, String name, UUID parentId, Instant deletedAt,
        Instant createdAt, Instant updatedAt, Long version) {}
