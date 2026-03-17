package com.agentopshub.dto;

import java.time.Instant;

public class TaskStatusResponse {
    private String taskId;
    private String status;
    private String prompt;
    private String outputFormat;
    private String dispatchStatus;
    private Integer retryCount;
    private String phase;
    private String phaseStatus;
    private Integer currentRound;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean traceAvailable;

    public TaskStatusResponse() {
    }

    public TaskStatusResponse(String taskId, String status, String prompt, String outputFormat, String dispatchStatus,
                              Integer retryCount, String phase, String phaseStatus, Integer currentRound,
                              Instant createdAt, Instant updatedAt, boolean traceAvailable) {
        this.taskId = taskId;
        this.status = status;
        this.prompt = prompt;
        this.outputFormat = outputFormat;
        this.dispatchStatus = dispatchStatus;
        this.retryCount = retryCount;
        this.phase = phase;
        this.phaseStatus = phaseStatus;
        this.currentRound = currentRound;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.traceAvailable = traceAvailable;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getPhaseStatus() { return phaseStatus; }
    public void setPhaseStatus(String phaseStatus) { this.phaseStatus = phaseStatus; }
    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isTraceAvailable() { return traceAvailable; }
    public void setTraceAvailable(boolean traceAvailable) { this.traceAvailable = traceAvailable; }
}
