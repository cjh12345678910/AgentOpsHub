package com.agentopshub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ToolPermissionUpdateRequest {
    @NotBlank(message = "role is required")
    private String role;

    @NotEmpty(message = "scopes is required")
    private List<String> scopes;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }
}
