package com.company.hrm.employee;

import com.company.hrm.employee.service.SensitiveIdentifierHasher;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class EmployeeHmacConfigurationTest {
    private AnnotationConfigApplicationContext context(String secret) throws IOException {
        // Isolate from developer secrets and the test-only random-key override.
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "isolatedEnvironment", secret == null ? Map.of() : Map.of("HRM_EMPLOYEE_HMAC_KEY", secret)));
        for (var source : new YamlPropertySourceLoader().load("productionConfig",
                new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.register(SensitiveIdentifierHasher.class);
        return context;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"                                    ", "short"})
    void should_refuse_startup_when_environment_key_is_missing_or_invalid(String secret) throws IOException {
        try (var context = context(secret)) {
            assertThatThrownBy(context::refresh)
                    .isInstanceOf(BeanCreationException.class)
                    .hasStackTraceContaining(secret == null
                            ? "HRM_EMPLOYEE_HMAC_KEY" : "Khóa HMAC nhân viên phải có ít nhất 32 byte");
            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    void should_use_environment_key_when_production_configuration_starts() throws IOException {
        String secret = SensitiveIdentifierHasherTest.randomKey();
        try (var context = context(secret)) {
            context.refresh();
            var expected = new SensitiveIdentifierHasher(new MockEnvironment()
                    .withProperty("hrm.employee.hmac-key", secret)).protect("cccd", "001234567890");
            assertThat(context.getBean(SensitiveIdentifierHasher.class).protect("cccd", "001234567890"))
                    .isEqualTo(expected);
        }
    }
}
