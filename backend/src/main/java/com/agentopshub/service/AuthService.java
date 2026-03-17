package com.agentopshub.service;

import com.agentopshub.domain.RoleEntity;
import com.agentopshub.domain.UserEntity;
import com.agentopshub.dto.LoginRequest;
import com.agentopshub.dto.LoginResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.UserRepository;
import com.agentopshub.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService authAuditService;
    private final long jwtExpirationMs;

    public AuthService(UserRepository userRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuthAuditService authAuditService,
                       @Value("${app.security.jwt.expiration-ms}") long jwtExpirationMs) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.authAuditService = authAuditService;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public LoginResponse login(LoginRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();

        try {
            UserEntity user = userRepository.findByUsername(request.getUsername());
            if (user == null) {
                authAuditService.logLoginFailed(request.getUsername(), "User not found", ipAddress, userAgent);
                throw new ApiException("UNAUTHORIZED", "Invalid username or password", null, HttpStatus.UNAUTHORIZED.value());
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                authAuditService.logLoginFailed(request.getUsername(), "Invalid password", ipAddress, userAgent);
                throw new ApiException("UNAUTHORIZED", "Invalid username or password", null, HttpStatus.UNAUTHORIZED.value());
            }

            if (!"active".equals(user.getStatus())) {
                authAuditService.logLoginFailed(request.getUsername(), "Account disabled", ipAddress, userAgent);
                throw new ApiException("FORBIDDEN", "User account is disabled", null, HttpStatus.FORBIDDEN.value());
            }

            List<RoleEntity> roles = userRepository.findRolesByUserId(user.getId());
            List<String> roleNames = roles.stream()
                    .map(RoleEntity::getName)
                    .collect(Collectors.toList());

            String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roleNames);

            authAuditService.logLoginSuccess(user.getId(), user.getUsername(), ipAddress, userAgent);

            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                    user.getId(),
                    user.getUsername(),
                    roleNames
            );

            return new LoginResponse(token, jwtExpirationMs, userInfo);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            authAuditService.logLoginFailed(request.getUsername(), "System error: " + e.getMessage(), ipAddress, userAgent);
            throw e;
        }
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
