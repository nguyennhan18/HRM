package com.company.hrm.common.security;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Unified pure masking component for sensitive HR fields (CCCD, bank account, salary, allowance).
 */
@Component
public class SensitiveFieldMasker {

    public static final String MASK_PREFIX = "******";

    /**
     * Masks a 4-digit identifier suffix (CCCD or bank account).
     * Authorized callers receive the 4-digit suffix as designed in T2.1 (e.g. "1234");
     * unauthorized callers receive the masked form ("******1234"). Null values remain null.
     */
    public String maskLastFour(String lastFour, boolean canViewSensitive) {
        if (lastFour == null) {
            return null;
        }
        String normalized = lastFour.startsWith(MASK_PREFIX)
                ? lastFour.substring(MASK_PREFIX.length())
                : lastFour;
        return canViewSensitive ? normalized : MASK_PREFIX + normalized;
    }

    /**
     * Masks a monetary amount (baseSalary or allowance).
     * Authorized callers receive the BigDecimal amount; unauthorized callers receive null.
     */
    public BigDecimal maskAmount(BigDecimal amount, boolean canViewSensitive) {
        if (amount == null || !canViewSensitive) {
            return null;
        }
        return amount;
    }
}
