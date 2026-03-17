package com.agentopshub.dto;

import java.util.List;

public class ToolPermissionResponse {
    private String role;
    private List<String> scopes;

    public ToolPermissionResponse() {
    }

    public ToolPermissionResponse(String role, List<String> scopes) {
        this.role = role;
        this.scopes = scopes;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }
}
