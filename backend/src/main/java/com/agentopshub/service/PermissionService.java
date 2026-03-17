package com.agentopshub.service;

import com.agentopshub.repository.PermissionRepository;
import com.agentopshub.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public boolean hasPermission(UserPrincipal principal, String requiredScope) {
        if (principal == null || requiredScope == null) {
            return false;
        }

        List<String> userScopes = getUserScopes(principal.getUsername());

        // Check for wildcard permission (admin)
        if (userScopes.contains("*")) {
            return true;
        }

        // Check for exact match
        return userScopes.contains(requiredScope);
    }

    public List<String> getUserScopes(String username) {
        return permissionRepository.findScopesByUsername(username);
    }
}
