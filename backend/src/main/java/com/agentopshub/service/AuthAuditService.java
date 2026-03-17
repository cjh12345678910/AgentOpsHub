package com.agentopshub.service;

import com.agentopshub.domain.AuthAuditLogEntity;
import com.agentopshub.repository.AuthAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthAuditService {

    private final AuthAuditLogRepository authAuditLogRepository;

    public AuthAuditService(AuthAuditLogRepository authAuditLogRepository) {
        this.authAuditLogRepository = authAuditLogRepository;
    }

    public void logLoginSuccess(Long userId, String username, String ipAddress, String userAgent) {
        AuthAuditLogEntity log = new AuthAuditLogEntity();
        log.setUserId(userId);
        log.setUsername(username);
        log.setEventType("LOGIN_SUCCESS");
        log.setDecision("ALLOW");
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedAt(Instant.now());
        authAuditLogRepository.insert(log);
    }

    public void logLoginFailed(String username, String reason, String ipAddress, String userAgent) {
        AuthAuditLogEntity log = new AuthAuditLogEntity();
        log.setUsername(username);
        log.setEventType("LOGIN_FAILED");
        log.setDecision("DENY");
        log.setReason(reason);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedAt(Instant.now());
        authAuditLogRepository.insert(log);
    }

    public void logAccessDenied(Long userId, String username, String resource, String action, String reason) {
        AuthAuditLogEntity log = new AuthAuditLogEntity();
        log.setUserId(userId);
        log.setUsername(username);
        log.setEventType("ACCESS_DENIED");
        log.setResource(resource);
        log.setAction(action);
        log.setDecision("DENY");
        log.setReason(reason);
        log.setCreatedAt(Instant.now());
        authAuditLogRepository.insert(log);
    }

    public void logPermissionCheck(Long userId, String username, String resource, String action, String decision) {
        AuthAuditLogEntity log = new AuthAuditLogEntity();
        log.setUserId(userId);
        log.setUsername(username);
        log.setEventType("PERMISSION_CHECK");
        log.setResource(resource);
        log.setAction(action);
        log.setDecision(decision);
        log.setCreatedAt(Instant.now());
        authAuditLogRepository.insert(log);
    }
}
