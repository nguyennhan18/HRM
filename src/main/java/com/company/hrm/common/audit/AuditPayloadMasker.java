package com.company.hrm.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class AuditPayloadMasker {

    private static final String MASKED_VALUE = "***";
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password", "passwordhash", "temporarypassword", "token", "accesstoken", "refreshtoken", "cccd",
            "cccdhash", "citizenidentificationnumber", "accountnumber", "bankaccountnumber",
            "bankaccounthash", "basesalary", "allowance", "salary");

    JsonNode mask(JsonNode payload) {
        if (payload == null) {
            return null;
        }

        JsonNode maskedPayload = payload.deepCopy();
        maskRecursively(maskedPayload);
        return maskedPayload;
    }

    private void maskRecursively(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (isSensitiveField(entry.getKey())) {
                    objectNode.put(entry.getKey(), MASKED_VALUE);
                } else {
                    maskRecursively(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskRecursively);
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalizedFieldName = fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.contains(normalizedFieldName);
    }
}
