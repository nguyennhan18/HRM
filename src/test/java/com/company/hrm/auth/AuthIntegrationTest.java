package com.company.hrm.auth;
import com.company.hrm.testsupport.AbstractIntegrationTest;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.*;
import com.company.hrm.auth.service.Notifier;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.TimeConfiguration.class)
class AuthIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuthTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockBean Notifier notifier;
    String email;
    static final String PASSWORD="correct-password";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2030-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
        void advance(Duration d){value=value.plus(d);}
    }
    @BeforeEach void setup() {
        clock.advance(Duration.ofDays(10));
        email=UUID.randomUUID()+"@example.com";
        users.saveAndFlush(new User(email,encoder.encode(PASSWORD),roles.findByCode("EMPLOYEE").orElseThrow()));
    }
    ResultActions postJson(String path,Object body,String bearer) throws Exception {
        var request=post("/api/v1/auth/"+path).contentType("application/json").content(json.writeValueAsString(body));
        if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        return mvc.perform(request);
    }
    JsonNode login() throws Exception {
        return json.readTree(postJson("login",Map.of("email",email,"password",PASSWORD),null)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String access(JsonNode node){return node.get("accessToken").asText();}
    String refresh(JsonNode node){return node.get("refreshToken").asText();}
    @Test void should_hide_account_and_lock_state_and_unlock_after_fifteen_minutes() throws Exception {
        for(int i=0;i<5;i++) assertFailure(email,"incorrect");
        assertFailure(email,PASSWORD);
        assertFailure("missing@example.com",PASSWORD);
        clock.advance(Duration.ofMinutes(15));
        login();
    }
    void assertFailure(String name,String password) throws Exception {
        postJson("login",Map.of("email",name,"password",password),null)
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("HRM_AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.detail").value("Thông tin xác thực không hợp lệ"))
            .andExpect(header().doesNotExist("Retry-After")).andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }
    @Test void should_revoke_whole_family_when_used_refresh_is_replayed() throws Exception {
        var first=login();
        var second=json.readTree(postJson("refresh",Map.of("token",refresh(first)),null)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(refresh(second)).isNotEqualTo(refresh(first));
        postJson("refresh",Map.of("token",refresh(first)),null).andExpect(status().isUnauthorized());
        postJson("refresh",Map.of("token",refresh(second)),null).andExpect(status().isUnauthorized());
        postJson("logout",Map.of(),access(second)).andExpect(status().isUnauthorized());
    }
    @Test void should_reject_access_after_expiration() throws Exception {
        var pair=login();clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        postJson("logout",Map.of(),access(pair)).andExpect(status().isUnauthorized());
    }
    @Test void should_reject_revoked_tokens_after_logout() throws Exception {
        var pair=login();
        postJson("logout",Map.of(),access(pair)).andExpect(status().isNoContent());
        postJson("logout",Map.of(),access(pair)).andExpect(status().isUnauthorized());
        postJson("refresh",Map.of("token",refresh(pair)),null).andExpect(status().isUnauthorized());
    }
    @Test void should_require_token_for_protected_endpoints() throws Exception {
        postJson("logout",Map.of(),null).andExpect(status().isUnauthorized());
        postJson("change-password",Map.of(),null).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }
    @Test void should_rate_limit_per_ip_with_retry_after() throws Exception {
        for(int i=0;i<10;i++) postJson("forgot-password",Map.of("email","missing@example.com"),null).andExpect(status().isNoContent());
        postJson("forgot-password",Map.of("email",email),null).andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After","60"));
        clock.advance(Duration.ofMinutes(1));
        postJson("forgot-password",Map.of("email",email),null).andExpect(status().isNoContent());
    }
    @Test void should_reset_once_and_revoke_all_sessions() throws Exception {
        var pair=login();
        postJson("forgot-password",Map.of("email",email),null).andExpect(status().isNoContent());
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).passwordReset(eq(email),capture.capture());
        String raw=capture.getValue();
        assertThat(tokens.findByTokenHash(raw)).isEmpty();
        postJson("reset-password",Map.of("token",raw,"password","replacement-password"),null).andExpect(status().isNoContent());
        postJson("reset-password",Map.of("token",raw,"password","another-password"),null).andExpect(status().isUnauthorized());
        postJson("refresh",Map.of("token",refresh(pair)),null).andExpect(status().isUnauthorized());
        postJson("logout",Map.of(),access(pair)).andExpect(status().isUnauthorized());
        postJson("login",Map.of("email",email,"password","replacement-password"),null).andExpect(status().isOk());
    }
    @Test void should_expire_reset_token_after_thirty_minutes() throws Exception {
        postJson("forgot-password",Map.of("email",email),null).andExpect(status().isNoContent());
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).passwordReset(eq(email),capture.capture());
        clock.advance(Duration.ofMinutes(30));
        postJson("reset-password",Map.of("token",capture.getValue(),"password","replacement-password"),null).andExpect(status().isUnauthorized());
    }
    @Test void should_change_password_and_record_current_auditor() throws Exception {
        var pair=login();
        postJson("change-password",Map.of("oldPassword",PASSWORD,"password","replacement-password"),access(pair))
            .andExpect(status().isNoContent());
        User user=users.findByEmail(email).orElseThrow();
        assertThat(user.getUpdatedBy()).isEqualTo(user.getId().toString());
        postJson("refresh",Map.of("token",refresh(pair)),null).andExpect(status().isUnauthorized());
        assertFailure(email,PASSWORD);
    }
    @Test void should_validate_inputs() throws Exception {
        for(String path:List.of("login","refresh","forgot-password","reset-password"))
            postJson(path,Map.of(),null).andExpect(status().isBadRequest());
    }
    @Test void should_expire_refresh_after_seven_days() throws Exception {
        var pair=login();clock.advance(Duration.ofDays(7));
        postJson("refresh",Map.of("token",refresh(pair)),null).andExpect(status().isUnauthorized());
    }
    @Test void should_reset_failure_counter_after_successful_login() throws Exception {
        for(int i=0;i<4;i++) assertFailure(email,"incorrect");
        login();
        clock.advance(Duration.ofMinutes(1));
        for(int i=0;i<4;i++) assertFailure(email,"incorrect");
        login();
    }
    @Test void should_revoke_family_when_refresh_requests_race() throws Exception {
        var pair=login();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> call=()-> {
                start.await();
                return postJson("refresh",Map.of("token",refresh(pair)),null)
                    .andReturn().getResponse().getStatus();
            };
            var first=executor.submit(call);
            var second=executor.submit(call);
            start.countDown();
            assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),
                second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        postJson("logout",Map.of(),access(pair)).andExpect(status().isUnauthorized());
    }
    @Test void should_preserve_other_session_when_one_family_is_replayed() throws Exception {
        var first=login();
        var other=login();
        postJson("refresh",Map.of("token",refresh(first)),null).andExpect(status().isOk());
        postJson("refresh",Map.of("token",refresh(first)),null).andExpect(status().isUnauthorized());
        postJson("logout",Map.of(),access(other)).andExpect(status().isNoContent());
    }
}
