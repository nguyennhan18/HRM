package com.company.hrm.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void should_generate_uuid_version_7_with_rfc_4122_variant() {
        UUID uuid = UuidV7Generator.next();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }
}
