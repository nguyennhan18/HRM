package com.company.hrm.auth.controller;
import com.company.hrm.auth.dto.AuthDtos.*;
import com.company.hrm.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service){this.service=service;}
    @PostMapping("/login")
    @Operation(summary="Đăng nhập",responses={@ApiResponse(responseCode="200",description="Cặp token"),@ApiResponse(responseCode="401",description="Thông tin xác thực không hợp lệ"),@ApiResponse(responseCode="429",description="Quá nhiều yêu cầu")})
    public ResponseEntity<Tokens> login(@Valid @RequestBody Login request,HttpServletRequest servlet) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(request,servlet.getRemoteAddr()));
    }
    @PostMapping("/refresh")
    @Operation(summary="Xoay vòng refresh token",responses={@ApiResponse(responseCode="200"),@ApiResponse(responseCode="401")})
    public ResponseEntity<Tokens> refresh(@Valid @RequestBody Refresh request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.refresh(request.token()));
    }
    @PostMapping("/logout")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @Operation(summary="Thu hồi phiên hiện tại",responses={@ApiResponse(responseCode="204"),@ApiResponse(responseCode="401")})
    public ResponseEntity<Void> logout(Authentication auth) {
        service.logout(UUID.fromString(auth.getName()),(UUID)auth.getDetails());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/change-password")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @Operation(summary="Đổi mật khẩu và thu hồi các phiên",responses={@ApiResponse(responseCode="204"),@ApiResponse(responseCode="400"),@ApiResponse(responseCode="401")})
    public ResponseEntity<Void> change(@Valid @RequestBody Change request,Authentication auth) {
        service.changePassword(UUID.fromString(auth.getName()),request);return ResponseEntity.noContent().build();
    }
    @PostMapping("/forgot-password")
    @Operation(summary="Yêu cầu đặt lại mật khẩu",responses={@ApiResponse(responseCode="204",description="Luôn trả cùng kết quả cho email hợp lệ"),@ApiResponse(responseCode="400")})
    public ResponseEntity<Void> forgot(@Valid @RequestBody Forgot request) {
        service.forgot(request.email());return ResponseEntity.noContent().build();
    }
    @PostMapping("/reset-password")
    @Operation(summary="Đặt lại mật khẩu bằng token một lần",responses={@ApiResponse(responseCode="204"),@ApiResponse(responseCode="400"),@ApiResponse(responseCode="401")})
    public ResponseEntity<Void> reset(@Valid @RequestBody Reset request) {
        service.reset(request);return ResponseEntity.noContent().build();
    }
}

