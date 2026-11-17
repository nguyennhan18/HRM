package com.company.hrm.employee.service;

import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SensitiveIdentifierHasher {
    private final SecretKeySpec key;

    public SensitiveIdentifierHasher(Environment environment) {
        String secret = environment.getRequiredProperty("hrm.employee.hmac-key");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || bytes.length < 32) {
            throw new IllegalStateException("Khóa HMAC nhân viên phải có ít nhất 32 byte");
        }
        key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public ProtectedValue protect(String domain, String raw) {
        if (raw == null) return new ProtectedValue(null, null);
        String value = raw.trim();
        // Do not put raw values into Bean Validation exceptions, messages or causes.
        if (!value.matches("[0-9]{4,}")) {
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR,
                    "CCCD và tài khoản phải là chuỗi chữ số có ít nhất 4 ký tự");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((domain + ":" + value).getBytes(StandardCharsets.UTF_8));
            return new ProtectedValue(HexFormat.of().formatHex(digest), value.substring(value.length() - 4));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể bảo vệ định danh nhân viên");
        }
    }

    public record ProtectedValue(String hash, String lastFour) {
        @Override public String toString() { return "ProtectedValue[redacted]"; }
    }
}
