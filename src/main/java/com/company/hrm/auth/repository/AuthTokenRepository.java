package com.company.hrm.auth.repository;
import com.company.hrm.auth.entity.AuthToken;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuthTokenRepository extends JpaRepository<AuthToken,UUID> {
    Optional<AuthToken> findByTokenHash(String hash);
    List<AuthToken> findByFamilyId(UUID familyId);
    List<AuthToken> findByUserId(UUID userId);
}

