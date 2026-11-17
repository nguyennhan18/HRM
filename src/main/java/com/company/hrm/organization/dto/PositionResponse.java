package com.company.hrm.organization.dto;
import java.time.Instant;
import java.util.UUID;
public record PositionResponse(UUID id, String code, String name,
        Instant createdAt, Instant updatedAt, Long version) {}
