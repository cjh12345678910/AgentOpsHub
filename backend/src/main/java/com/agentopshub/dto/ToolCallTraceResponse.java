package com.agentopshub.dto;

import java.time.Instant;
import java.util.List;

public class ToolCallTraceResponse {
    private Long id;
    private Long stepId;
    private Integer callOrder;
    private String toolName;
    private String requestSummary;
    private String responseSummary;
    private Boolean success;
    private String errorCode;
    private String errorMessage;
    private Long latencyMs;
    private String traceId;
    private Instant createdAt;
    private List<String> citations;
    private String policyDecision;
    private String denyReason;
    private String requiredScope;
    private String safetyRule;
    private String targetPath;
    private Long sizeBytes;

    public ToolCallTraceResponse() {
    }

    public ToolCallTraceResponse(Long id, Long stepId, Integer callOrder, String toolName, String requestSummary,
                                 String responseSummary, Boolean success, String errorCode, String errorMessage,
                                 Long latencyMs, String traceId, Instant createdAt, List<String> citations,
                                 String policyDecision, String denyReason, String requiredScope, String safetyRule,
                                 String targetPath, Long sizeBytes) {
        this.id = id;
        this.stepId = stepId;
        this.callOrder = callOrder;
        this.toolName = toolName;
        this.requestSummary = requestSummary;
        this.responseSummary = responseSummary;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.citations = citations;
        this.policyDecision = policyDecision;
        this.denyReason = denyReason;
        this.requiredScope = requiredScope;
        this.safetyRule = safetyRule;
        this.targetPath = targetPath;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public Integer getCallOrder() { return callOrder; }
    public void setCallOrder(Integer callOrder) { this.callOrder = callOrder; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getRequestSummary() { return requestSummary; }
    public void setRequestSummary(String requestSummary) { this.requestSummary = requestSummary; }
    public String getResponseSummary() { return responseSummary; }
    public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
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
}
