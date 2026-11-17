package com.company.hrm.auth.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    protected Permission() {
    }

    public Permission(String code) {
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public String getCode() {
        return code;
    }
}
