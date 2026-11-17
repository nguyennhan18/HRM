package com.company.hrm.auth.service;
import com.company.hrm.auth.config.AuthProperties;
import java.time.Clock;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;
    private final AuthProperties properties;
    public JwtService(AuthProperties properties, Clock clock) {
        this.properties=properties; this.clock=clock;
        byte[] secret=properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(secret.length<32) throw new IllegalStateException("HRM_JWT_SECRET must contain at least 32 bytes");
        var key=new SecretKeySpec(secret,"HmacSHA256");
        encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp=new JwtTimestampValidator(java.time.Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            timestamp,new JwtIssuerValidator("hrm")));
    }
    public String issue(UUID user, UUID family) {
        var claims=JwtClaimsSet.builder().issuer("hrm").subject(user.toString())
            .issuedAt(clock.instant()).expiresAt(clock.instant().plus(properties.accessTtl()))
            .claim("sid",family.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    public Jwt decode(String token){ return decoder.decode(token); }
}

