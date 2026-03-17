package com.agentopshub.dto;

import java.util.Map;

public class ToolCatalogItemResponse {
    private String name;
    private String type;
    private Boolean enabled;
    private String requiredScope;
    private String timeoutPolicy;
    private String retryPolicy;
    private Map<String, Object> safetyPolicy;

    public ToolCatalogItemResponse() {
    }

    public ToolCatalogItemResponse(String name, String type, Boolean enabled, String requiredScope,
                                   String timeoutPolicy, String retryPolicy, Map<String, Object> safetyPolicy) {
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.requiredScope = requiredScope;
        this.timeoutPolicy = timeoutPolicy;
        this.retryPolicy = retryPolicy;
        this.safetyPolicy = safetyPolicy;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRequiredScope() { return requiredScope; }
    public void setRequiredScope(String requiredScope) { this.requiredScope = requiredScope; }
    public String getTimeoutPolicy() { return timeoutPolicy; }
    public void setTimeoutPolicy(String timeoutPolicy) { this.timeoutPolicy = timeoutPolicy; }
    public String getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(String retryPolicy) { this.retryPolicy = retryPolicy; }
    public Map<String, Object> getSafetyPolicy() { return safetyPolicy; }
    public void setSafetyPolicy(Map<String, Object> safetyPolicy) { this.safetyPolicy = safetyPolicy; }
}
