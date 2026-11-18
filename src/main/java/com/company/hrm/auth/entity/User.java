package com.company.hrm.auth.entity;

import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    private int failedAttempts;
    private java.time.Instant lockedUntil;

    public boolean isLocked(java.time.Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    public int fail(java.time.Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) clearFailures();
        return ++failedAttempts;
    }
    public void lockUntil(java.time.Instant until) { lockedUntil = until; }
    public void clearFailures() { failedAttempts = 0; lockedUntil = null; }
    public void changePassword(String hash) { passwordHash = hash; clearFailures(); }
    public void disable() { enabled = false; clearFailures(); }

    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
    }

    public User(String email, String passwordHash, Role role) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.roles.add(Objects.requireNonNull(role, "role must not be null"));
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
