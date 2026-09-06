package com.jiu.careagent.auth;

import com.jiu.careagent.audit.AuditService;
import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.common.RequestIds;
import com.jiu.careagent.domain.AppUser;
import com.jiu.careagent.repository.AppUserRepository;
import com.jiu.careagent.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter limiter;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, LoginRateLimiter limiter,
                          JwtService jwtService, AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String ip = clientIp(request);
        long retryAfter = limiter.retryAfterSeconds(ip, body.username());
        if (retryAfter > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "登录失败次数过多，请在 " + retryAfter + " 秒后重试");
        }

        Optional<AppUser> candidate = users.findByUsername(body.username().trim());
        AppUser user = candidate.orElse(null);
        boolean valid = user != null && user.isEnabled() && passwordEncoder.matches(body.password(), user.getPasswordHash());
        if (!valid) {
            limiter.recordFailure(ip, body.username());
            auditService.record("LOGIN_FAILURE", user == null ? null : user.getId(), RequestIds.current(request), "INVALID_CREDENTIALS", null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }

        limiter.clear(ip, body.username());
        JwtService.IssuedToken token = jwtService.issue(user);
        auditService.record("LOGIN_SUCCESS", user.getId(), RequestIds.current(request), "SUCCESS", null);
        return new LoginResponse(token.value(), token.expiresAt(), user.getRole());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名过长") String username,
            @NotBlank(message = "密码不能为空") @Size(max = 200, message = "密码过长") String password) {
    }

    public record LoginResponse(String accessToken, Instant expiresAt, String role) {
    }
}
