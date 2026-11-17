package com.company.hrm.organization.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class Position extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    protected Position() {}
    public Position(String code, String name) {
        this.code = code; this.name = name;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Position entity && getId() != null && getId().equals(entity.getId());
    }
    @Override
    public int hashCode() { return Position.class.hashCode(); }
}
