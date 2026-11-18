package com.company.hrm.auth.dto;
import jakarta.validation.constraints.*;
public final class AuthDtos {
    private AuthDtos(){}
    public record Login(
        @io.swagger.v3.oas.annotations.media.Schema(example="employee@example.com")
        @NotBlank @Email @Size(max=254) String email,
        @io.swagger.v3.oas.annotations.media.Schema(example="example-password", format="password")
        @NotBlank @Size(max=72) String password) {}
    public record Refresh(@NotBlank @Size(max=128) String token) {}
    public record Forgot(@NotBlank @Email @Size(max=254) String email) {}
    public record Reset(@NotBlank @Size(max=128) String token, @NotBlank @Size(min=10,max=72) String password) {}
    public record Change(@NotBlank @Size(max=72) String oldPassword, @NotBlank @Size(min=10,max=72) String password) {}
    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn, boolean mustChangePassword) {
        public Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {
            this(accessToken, refreshToken, tokenType, expiresIn, false);
        }
    }
}

