package com.company.hrm.auth.service;

import com.company.hrm.auth.config.AuthProperties;
import com.company.hrm.auth.dto.AuthDtos.*;
import com.company.hrm.auth.entity.*;
import com.company.hrm.auth.repository.*;
import com.company.hrm.common.audit.AuditService;
import com.company.hrm.common.exception.*;
import java.time.Clock;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor=BusinessException.class)
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final AuthProperties config;
    private final Clock clock;
    private final JwtService jwt;
    private final Notifier notifier;
    private final AuditService audit;
    private final String dummy;
    private final SecureRandom random = new SecureRandom();
    public AuthServiceImpl(UserRepository users, AuthTokenRepository tokens, PasswordEncoder passwords,
            AuthProperties config, Clock clock, JwtService jwt, Notifier notifier, AuditService audit) {
        this.users=users; this.tokens=tokens; this.passwords=passwords; this.config=config;
        this.clock=clock; this.jwt=jwt; this.notifier=notifier; this.audit=audit;
        dummy=passwords.encode(UUID.randomUUID().toString());
    }
    public static BusinessException denied() {
        return new BusinessException(ErrorCode.HRM_AUTHENTICATION_FAILED,"Thông tin xác thực không hợp lệ");
    }
    @Override public Tokens login(Login request, String ip) {
        var user=users.lockByEmail(request.email()).orElse(null);
        boolean matches=passwords.matches(request.password(),user==null?dummy:user.getPasswordHash());
        if(user==null) throw denied();
        if(!user.isEnabled() || user.isLocked(clock.instant())) throw denied();
        if(!matches) {
            if(user.fail(clock.instant())>=config.lockThreshold()) {
                var until=clock.instant().plus(config.lockDuration());
                user.lockUntil(until);
                audit.record("ACCOUNT_LOCKED","User",user.getId(),null,
                    Map.of("ip",ip,"lockedAt",clock.instant(),"lockedUntil",until),null);
            }
            throw denied();
        }
        user.clearFailures();
        return issue(user.getId(),UUID.randomUUID(),clock.instant().plus(config.refreshTtl()));
    }
    private Tokens issue(UUID user, UUID family, java.time.Instant expiry) {
        String raw=randomToken();
        tokens.save(new AuthToken(user,family,hash(raw),"REFRESH",expiry));
        return new Tokens(jwt.issue(user,family),raw,"Bearer",config.accessTtl().toSeconds());
    }
    @Override public Tokens refresh(String raw) {
        AuthToken initial=tokens.findByTokenHash(hash(raw)).orElseThrow(AuthServiceImpl::denied);
        User user=users.lockById(initial.getUserId()).orElseThrow(AuthServiceImpl::denied);
        // Refresh managed state after waiting for a concurrent request's user lock.
        entityManager.refresh(initial);
        if(!initial.getPurpose().equals("REFRESH")) throw denied();
        if(initial.isUsed()) {
            tokens.findByFamilyId(initial.getFamilyId()).forEach(AuthToken::revoke);
            throw denied();
        }
        if(initial.isRevoked() || !initial.getExpiresAt().isAfter(clock.instant()) || !user.isEnabled())
            throw denied();
        initial.use();
        return issue(user.getId(),initial.getFamilyId(),clock.instant().plus(config.refreshTtl()));
    }
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override @PreAuthorize("isAuthenticated()")
    public void logout(UUID user, UUID family) {
        users.lockById(user).orElseThrow(AuthServiceImpl::denied);
        tokens.findByFamilyId(family).stream().filter(t->t.getUserId().equals(user)).forEach(AuthToken::revoke);
    }
    @Override @PreAuthorize("isAuthenticated()")
    public void changePassword(UUID id, Change request) {
        User user=users.lockById(id).orElseThrow(AuthServiceImpl::denied);
        if(!passwords.matches(request.oldPassword(),user.getPasswordHash())) throw denied();
        user.changePassword(encodePassword(request.password()));
        tokens.findByUserId(id).forEach(AuthToken::revoke);
        audit.record("PASSWORD_CHANGED","User",id,null,null,null);
    }
    @Override public void forgot(String email) {
        users.lockByEmail(email).filter(User::isEnabled).ifPresent(user-> {
            tokens.findByUserId(user.getId()).stream().filter(t->t.getPurpose().equals("RESET")).forEach(AuthToken::revoke);
            String raw=randomToken();
            tokens.save(new AuthToken(user.getId(),UUID.randomUUID(),hash(raw),"RESET",
                clock.instant().plus(config.resetTtl())));
            notifier.passwordReset(email,raw);
        });
    }
    @Override public void reset(Reset request) {
        AuthToken token=tokens.findByTokenHash(hash(request.token())).orElseThrow(AuthServiceImpl::denied);
        User user=users.lockById(token.getUserId()).orElseThrow(AuthServiceImpl::denied);
        entityManager.refresh(token);
        if(!token.getPurpose().equals("RESET") || token.isUsed() || token.isRevoked()
                || !token.getExpiresAt().isAfter(clock.instant()) || !user.isEnabled()) throw denied();
        String encoded = encodePassword(request.password());
        token.use();
        user.changePassword(encoded);
        tokens.findByUserId(user.getId()).forEach(AuthToken::revoke);
        audit.record("PASSWORD_RESET","User",user.getId(),null,null,null);
    }
    @Override @Transactional(readOnly=true)
    public boolean active(UUID user, UUID family) {
        return users.findById(user).filter(User::isEnabled).isPresent()
            && tokens.findByFamilyId(family).stream().anyMatch(t->t.getUserId().equals(user)
                && t.getPurpose().equals("REFRESH") && !t.isRevoked() && !t.isUsed()
                && t.getExpiresAt().isAfter(clock.instant()));
    }
    @Override @Transactional(readOnly=true)
    public Set<String> roles(UUID user) {
        return users.findRoleCodesById(user);
    }
    private String randomToken() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String encodePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Mật khẩu vượt quá 72 byte UTF-8");
        }
        return passwords.encode(password);
    }
    static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
