package com.agentopshub.dto;

public class TaskDispatchMessage {
    private String taskId;
    private String messageId;
    private Integer retryCount;
    private Long createdAtEpochMs;

    public TaskDispatchMessage() {
    }

    public TaskDispatchMessage(String taskId, String messageId, Integer retryCount, Long createdAtEpochMs) {
        this.taskId = taskId;
        this.messageId = messageId;
        this.retryCount = retryCount;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public void setCreatedAtEpochMs(Long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }
}
