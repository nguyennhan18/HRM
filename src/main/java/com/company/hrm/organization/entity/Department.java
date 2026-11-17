package com.company.hrm.organization.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "parent_id")
    private UUID parentId;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    protected Department() {}
    public Department(String code, String name, UUID parentId) {
        this.code = code; this.name = name; this.parentId = parentId;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public Instant getDeletedAt() { return deletedAt; }
    public void delete(Instant at) { deletedAt = at; }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Department entity && getId() != null && getId().equals(entity.getId());
    }
    @Override
    public int hashCode() { return Department.class.hashCode(); }
}
