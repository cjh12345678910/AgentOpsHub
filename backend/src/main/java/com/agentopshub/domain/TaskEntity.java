package com.agentopshub.domain;

import java.time.Instant;

public class TaskEntity {
    private String id;
    private TaskStatus status;
    private String dispatchStatus;
    private String messageId;
    private Integer retryCount;
    private String dlqReason;
    private String lastErrorCode;
    private String prompt;
    private String docIdsJson;
    private String outputFormat;
    private String resultJson;
    private String resultMd;
    private String errorMessage;
    private Instant createdAt;
    private Instant enqueuedAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getDlqReason() { return dlqReason; }
    public void setDlqReason(String dlqReason) { this.dlqReason = dlqReason; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getDocIdsJson() { return docIdsJson; }
    public void setDocIdsJson(String docIdsJson) { this.docIdsJson = docIdsJson; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getResultMd() { return resultMd; }
    public void setResultMd(String resultMd) { this.resultMd = resultMd; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
