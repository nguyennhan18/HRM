package com.company.hrm.auth.entity;
import com.company.hrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="auth_tokens")
public class AuthToken extends BaseEntity {
    private UUID userId;
    private UUID familyId;
    private String tokenHash;
    private String purpose;
    private Instant expiresAt;
    private boolean used;
    private boolean revoked;
    protected AuthToken() {}
    public AuthToken(UUID userId, UUID familyId, String hash, String purpose, Instant expiresAt) {
        this.userId=userId; this.familyId=familyId; this.tokenHash=hash; this.purpose=purpose; this.expiresAt=expiresAt;
    }
    public UUID getUserId(){return userId;}
    public UUID getFamilyId(){return familyId;}
    public Instant getExpiresAt(){return expiresAt;}
    public String getPurpose(){return purpose;}
    public boolean isUsed(){return used;}
    public boolean isRevoked(){return revoked;}
    public void use(){used=true;}
    public void revoke(){revoked=true;}
}

