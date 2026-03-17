package com.agentopshub.dto;

public class EmbeddingBackfillResponse {
    private Integer requested;
    private Integer processed;
    private Integer successCount;
    private Integer failureCount;
    private String provider;
    private String model;

    public EmbeddingBackfillResponse() {
    }

    public EmbeddingBackfillResponse(Integer requested, Integer processed, Integer successCount, Integer failureCount, String provider, String model) {
        this.requested = requested;
        this.processed = processed;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.provider = provider;
        this.model = model;
    }

    public Integer getRequested() { return requested; }
    public void setRequested(Integer requested) { this.requested = requested; }
    public Integer getProcessed() { return processed; }
    public void setProcessed(Integer processed) { this.processed = processed; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
