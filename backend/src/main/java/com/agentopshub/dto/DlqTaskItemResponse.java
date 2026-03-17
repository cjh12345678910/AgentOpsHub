package com.agentopshub.dto;

import java.time.Instant;

public class DlqTaskItemResponse {
    private String taskId;
    private String status;
    private String dispatchStatus;
    private Integer retryCount;
    private String dlqReason;
    private String lastErrorCode;
    private Instant updatedAt;

    public DlqTaskItemResponse() {
    }

    public DlqTaskItemResponse(String taskId, String status, String dispatchStatus, Integer retryCount,
                               String dlqReason, String lastErrorCode, Instant updatedAt) {
        this.taskId = taskId;
        this.status = status;
        this.dispatchStatus = dispatchStatus;
        this.retryCount = retryCount;
        this.dlqReason = dlqReason;
        this.lastErrorCode = lastErrorCode;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getDlqReason() { return dlqReason; }
    public void setDlqReason(String dlqReason) { this.dlqReason = dlqReason; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
