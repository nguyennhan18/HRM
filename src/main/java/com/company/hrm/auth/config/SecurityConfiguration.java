package com.company.hrm.auth.config;
import com.company.hrm.auth.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;
@Configuration
@EnableMethodSecurity
@io.swagger.v3.oas.annotations.security.SecurityScheme(name="bearerAuth",
    type=io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme="bearer", bearerFormat="JWT")
public class SecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordLogin() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Unavailable"); };
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth, JwtService jwt,
            ObjectMapper mapper, AuthProperties props, Clock clock, Environment env) throws Exception {
        http.csrf(c->c.disable()).cors(c->c.configurationSource(request-> {
            var cors=new CorsConfiguration();
            cors.setAllowedOrigins(props.corsOrigins());
            cors.setAllowedMethods(List.of("POST","GET","PUT","DELETE","OPTIONS"));
            cors.setAllowedHeaders(List.of("Authorization","Content-Type"));
            return cors;
        })).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .requestCache(c->c.disable())
          .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->problem(mapper,q,r,clock,401,"HRM_AUTHENTICATION_FAILED"))
            .accessDeniedHandler((q,r,x)->problem(mapper,q,r,clock,403,"HRM_ACCESS_DENIED")));
        http.authorizeHttpRequests(a-> {
            if(env.matchesProfiles("dev")) a.requestMatchers("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**","/actuator/health","/actuator/health/**").permitAll();
            a.requestMatchers("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/forgot-password","/api/v1/auth/reset-password").permitAll();
            a.anyRequest().authenticated();
        });
        http.addFilterBefore(new OncePerRequestFilter() {
            private final Map<String, Window> windows=new HashMap<>();
            @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)
                    throws ServletException,IOException {
                if(q.getRequestURI().startsWith("/api/v1/auth/")) {
                    long retry=limit(q.getRemoteAddr());
                    if(retry>0) {r.setHeader("Retry-After",Long.toString(retry)); problem(mapper,q,r,clock,429,"HRM_RATE_LIMITED");return;}
                }
                String header=q.getHeader("Authorization");
                if(header!=null && header.startsWith("Bearer ")) {
                    try {
                        var token=jwt.decode(header.substring(7));
                        var user=UUID.fromString(token.getSubject());
                        var family=UUID.fromString(token.getClaimAsString("sid"));
                        if(!auth.active(user,family)) {problem(mapper,q,r,clock,401,"HRM_AUTHENTICATION_FAILED");return;}
                        if(auth.mustChangePassword(user) && !isAllowedDuringPasswordChange(q.getRequestURI())) {
                            problem(mapper,q,r,clock,403,"HRM_ACCESS_DENIED");return;
                        }
                        var authentication=new UsernamePasswordAuthenticationToken(user.toString(),null,auth.roles(user).stream()
                            .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList());
                        authentication.setDetails(family);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch(org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException ex) {
                        problem(mapper,q,r,clock,401,"HRM_AUTHENTICATION_FAILED");return;
                    }
                }
                chain.doFilter(q,r);
            }
            private boolean isAllowedDuringPasswordChange(String uri) {
                return uri.equals("/api/v1/auth/change-password")
                        || uri.equals("/api/v1/auth/logout")
                        || uri.equals("/api/v1/auth/refresh")
                        || uri.equals("/api/v1/auth/login")
                        || uri.equals("/api/v1/auth/forgot-password")
                        || uri.equals("/api/v1/auth/reset-password");
            }
            private synchronized long limit(String ip) {
                var now=clock.instant();
                windows.entrySet().removeIf(e->!e.getValue().end.isAfter(now));
                Window w=windows.computeIfAbsent(ip,k->new Window(now.plus(props.rateWindow())));
                if(++w.count>props.rateLimit()) return Math.max(1,(java.time.Duration.between(now,w.end).toMillis()+999)/1000);
                return 0;
            }
        },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static final class Window {
        final java.time.Instant end; int count;
        Window(java.time.Instant end){this.end=end;}
    }
    private static void problem(ObjectMapper mapper,HttpServletRequest request,HttpServletResponse response,Clock clock,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/problem+json");
        var p=com.company.hrm.common.exception.ApiProblems.create(
            com.company.hrm.common.exception.ErrorCode.valueOf(code),
            status==429?"Quá nhiều yêu cầu":status==403?"Không có quyền truy cập":"Thông tin xác thực không hợp lệ",
            request,clock,com.company.hrm.common.exception.ApiProblems.traceId(request));
        mapper.writeValue(response.getOutputStream(),p);
    }
}

