package com.company.hrm.employee;

import com.company.hrm.employee.service.SensitiveIdentifierHasher;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class SensitiveIdentifierHasherTest {
    static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    SensitiveIdentifierHasher hasher(String key) {
        return new SensitiveIdentifierHasher(new MockEnvironment().withProperty("hrm.employee.hmac-key", key));
    }
    @Test void should_fail_closed_when_key_is_missing_blank_or_short() {
        assertThatThrownBy(() -> new SensitiveIdentifierHasher(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        for (String key : new String[]{"", " ".repeat(64), "short"}) {
            assertThatThrownBy(() -> hasher(key)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void should_hash_deterministically_and_separate_domains_and_keys() throws Exception {
        String key = randomKey();
        var hasher = hasher(key);
        String value = "001234567890";
        var result = hasher.protect("cccd", value);
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(result.hash()).isEqualTo(HexFormat.of().formatHex(
                mac.doFinal(("cccd:" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(result.lastFour()).isEqualTo("7890");
        assertThat(hasher.protect("cccd", " " + value + " ")).isEqualTo(result);
        assertThat(hasher.protect("bank-account", value).hash()).isNotEqualTo(result.hash());
        assertThat(hasher(randomKey()).protect("cccd", value).hash()).isNotEqualTo(result.hash());
        assertThat(result.toString()).doesNotContain(value, result.hash());
        assertThat(hasher.protect("cccd", null).hash()).isNull();
    }
}
