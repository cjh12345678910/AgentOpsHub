package com.agentopshub.service;

import com.agentopshub.domain.PermissionEntity;
import com.agentopshub.domain.RoleEntity;
import com.agentopshub.domain.ToolCallLogEntity;
import com.agentopshub.dto.ToolAuditItemResponse;
import com.agentopshub.dto.ToolCatalogItemResponse;
import com.agentopshub.dto.ToolPermissionResponse;
import com.agentopshub.dto.ToolPermissionUpdateRequest;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.PermissionRepository;
import com.agentopshub.repository.RolePermissionRepository;
import com.agentopshub.repository.RoleRepository;
import com.agentopshub.repository.ToolCallLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ToolGovernanceService {
    private final ToolCallLogRepository toolCallLogRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public ToolGovernanceService(ToolCallLogRepository toolCallLogRepository,
                                 RoleRepository roleRepository,
                                 PermissionRepository permissionRepository,
                                 RolePermissionRepository rolePermissionRepository) {
        this.toolCallLogRepository = toolCallLogRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<ToolCatalogItemResponse> listToolCatalog(String statusFilter) {
        List<ToolCatalogItemResponse> all = new ArrayList<>();
        all.add(new ToolCatalogItemResponse(
            "rag_search",
            "internal",
            true,
            "rag:search",
            "2500ms",
            "0",
            Map.of("source", "backend:/api/rag/search")
        ));
        all.add(new ToolCatalogItemResponse(
            "chunk_fetch",
            "internal",
            true,
            "rag:chunk:read",
            "2500ms",
            "0",
            Map.of("source", "backend:/api/rag/chunk/{chunkId}")
        ));
        all.add(new ToolCatalogItemResponse(
            "http_get",
            "external",
            true,
            "tool:http_get",
            "3000ms",
            "0",
            Map.of("policy", "allowlist + ssrf guard")
        ));
        all.add(new ToolCatalogItemResponse(
            "sql_select",
            "external",
            true,
            "sql:select:readonly",
            "2000ms",
            "0",
            Map.of("policy", "readonly + table allowlist + row limit")
        ));
        all.add(new ToolCatalogItemResponse(
            "file_write",
            "external",
            true,
            "tool:file_write",
            "2000ms",
            "0",
            Map.of("policy", "baseDir sandbox + extension allowlist + size limit")
        ));

        if (statusFilter == null || statusFilter.isBlank()) {
            return all;
        }
        boolean targetEnabled = "enabled".equalsIgnoreCase(statusFilter.trim());
        return all.stream().filter(item -> targetEnabled == Boolean.TRUE.equals(item.getEnabled())).toList();
    }

    public List<ToolPermissionResponse> listPermissions() {
        List<RoleEntity> roles = roleRepository.findAll();
        return roles.stream()
            .map(role -> {
                List<String> scopes = permissionRepository.findScopesByRoleId(role.getId());
                return new ToolPermissionResponse(role.getName(), scopes);
            })
            .sorted((a, b) -> a.getRole().compareTo(b.getRole()))
            .toList();
    }

    public ToolPermissionResponse getPermissionByRole(String role) {
        String normalizedRole = normalizeRole(role);
        RoleEntity roleEntity = roleRepository.findByName(normalizedRole);
        if (roleEntity == null) {
            throw new ApiException(
                "PERMISSION_NOT_FOUND",
                "Role permission mapping not found",
                Map.of("role", normalizedRole),
                HttpStatus.NOT_FOUND.value()
            );
        }
        List<String> scopes = permissionRepository.findScopesByRoleId(roleEntity.getId());
        return new ToolPermissionResponse(normalizedRole, scopes);
    }

    @Transactional
    public ToolPermissionResponse updatePermissions(ToolPermissionUpdateRequest request) {
        String role = normalizeRole(request.getRole());
        List<String> scopes = normalizeScopes(request.getScopes());
        if (scopes.isEmpty()) {
            throw new ApiException(
                "INVALID_POLICY_UPDATE",
                "At least one scope is required",
                Map.of("role", role),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        RoleEntity roleEntity = roleRepository.findByName(role);
        if (roleEntity == null) {
            roleEntity = new RoleEntity();
            roleEntity.setName(role);
            roleEntity.setDescription("Auto-created role");
            roleEntity.setCreatedAt(Instant.now());
            roleEntity.setUpdatedAt(Instant.now());
            roleRepository.insert(roleEntity);
            roleEntity = roleRepository.findByName(role);
        }

        rolePermissionRepository.deleteByRoleId(roleEntity.getId());

        for (String scope : scopes) {
            PermissionEntity permission = permissionRepository.findByScope(scope);
            if (permission == null) {
                permission = new PermissionEntity();
                permission.setScope(scope);
                String[] parts = scope.split(":", 2);
                permission.setResource(parts.length > 0 ? parts[0] : scope);
                permission.setAction(parts.length > 1 ? parts[1] : "*");
                permission.setDescription("Auto-created permission");
                permission.setCreatedAt(Instant.now());
                permission.setUpdatedAt(Instant.now());
                permissionRepository.insert(permission);
                permission = permissionRepository.findByScope(scope);
            }
            if (permission != null) {
                rolePermissionRepository.insert(roleEntity.getId(), permission.getId());
            }
        }

        return new ToolPermissionResponse(role, scopes);
    }

    public ToolPermissionResponse putPermissions(String role, List<String> scopes) {
        ToolPermissionUpdateRequest request = new ToolPermissionUpdateRequest();
        request.setRole(role);
        request.setScopes(scopes);
        return updatePermissions(request);
    }

    @Transactional
    public void deletePermission(String role) {
        String normalizedRole = normalizeRole(role);
        RoleEntity roleEntity = roleRepository.findByName(normalizedRole);
        if (roleEntity == null) {
            throw new ApiException(
                "PERMISSION_NOT_FOUND",
                "Role permission mapping not found",
                Map.of("role", normalizedRole),
                HttpStatus.NOT_FOUND.value()
            );
        }
        rolePermissionRepository.deleteByRoleId(roleEntity.getId());
    }

    public List<ToolAuditItemResponse> listAudit(String policyDecision, Instant fromTs, Instant toTs, Integer limit) {
        String normalizedDecision = normalizePolicyDecision(policyDecision);
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(500, limit));
        return toolCallLogRepository.findByPolicyAndTimeRange(normalizedDecision, fromTs, toTs, safeLimit).stream()
            .map(this::toAuditItem)
            .toList();
    }

    private ToolAuditItemResponse toAuditItem(ToolCallLogEntity item) {
        return new ToolAuditItemResponse(
            item.getId(),
            item.getTaskId(),
            item.getTraceId(),
            item.getToolName(),
            item.getSuccess(),
            item.getErrorCode(),
            item.getPolicyDecision(),
            item.getDenyReason(),
            item.getRequiredScope(),
            item.getSafetyRule(),
            item.getTargetPath(),
            item.getSizeBytes(),
            item.getLatencyMs(),
            item.getRequestSummary(),
            item.getResponseSummary(),
            item.getCreatedAt()
        );
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ApiException(
                "INVALID_POLICY_UPDATE",
                "role is required",
                Map.of(),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null) {
            return List.of();
        }
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            unique.put(scope.trim(), Boolean.TRUE);
        }
        return List.copyOf(unique.keySet());
    }

    private String normalizePolicyDecision(String policyDecision) {
        if (policyDecision == null || policyDecision.isBlank()) {
            return null;
        }
        String normalized = policyDecision.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("allow") && !normalized.equals("deny")) {
            throw new ApiException(
                "INVALID_AUDIT_FILTER",
                "policyDecision must be allow or deny",
                Map.of("policyDecision", policyDecision),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        return normalized;
    }
}
