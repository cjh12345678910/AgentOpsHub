package com.agentopshub.dto;

import java.time.Instant;

public class ToolAuditItemResponse {
    private Long id;
    private String taskId;
    private String traceId;
    private String toolName;
    private Boolean success;
    private String errorCode;
    private String policyDecision;
    private String denyReason;
    private String requiredScope;
    private String safetyRule;
    private String targetPath;
    private Long sizeBytes;
    private Long latencyMs;
    private String requestSummary;
    private String responseSummary;
    private Instant createdAt;

    public ToolAuditItemResponse() {
    }

    public ToolAuditItemResponse(Long id, String taskId, String traceId, String toolName, Boolean success,
                                 String errorCode, String policyDecision, String denyReason, String requiredScope,
                                 String safetyRule, String targetPath, Long sizeBytes, Long latencyMs, String requestSummary, String responseSummary,
                                 Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.traceId = traceId;
        this.toolName = toolName;
        this.success = success;
        this.errorCode = errorCode;
        this.policyDecision = policyDecision;
        this.denyReason = denyReason;
        this.requiredScope = requiredScope;
        this.safetyRule = safetyRule;
        this.targetPath = targetPath;
        this.sizeBytes = sizeBytes;
        this.latencyMs = latencyMs;
        this.requestSummary = requestSummary;
        this.responseSummary = responseSummary;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getPolicyDecision() { return policyDecision; }
    public void setPolicyDecision(String policyDecision) { this.policyDecision = policyDecision; }
    public String getDenyReason() { return denyReason; }
    public void setDenyReason(String denyReason) { this.denyReason = denyReason; }
    public String getRequiredScope() { return requiredScope; }
    public void setRequiredScope(String requiredScope) { this.requiredScope = requiredScope; }
    public String getSafetyRule() { return safetyRule; }
    public void setSafetyRule(String safetyRule) { this.safetyRule = safetyRule; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getRequestSummary() { return requestSummary; }
    public void setRequestSummary(String requestSummary) { this.requestSummary = requestSummary; }
    public String getResponseSummary() { return responseSummary; }
    public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
