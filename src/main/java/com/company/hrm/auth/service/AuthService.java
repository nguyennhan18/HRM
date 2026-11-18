package com.company.hrm.auth.service;
import com.company.hrm.auth.dto.AuthDtos.*;
import java.util.UUID;
public interface AuthService {
    Tokens login(Login request, String ip);
    Tokens refresh(String token);
    void logout(UUID user, UUID family);
    void changePassword(UUID user, Change request);
    void forgot(String email);
    void reset(Reset request);
    java.util.Set<String> roles(UUID user);
    boolean active(UUID user, UUID family);
    void deactivateUser(UUID user, String traceId);
}

