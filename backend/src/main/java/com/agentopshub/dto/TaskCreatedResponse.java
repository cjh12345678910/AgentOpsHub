package com.agentopshub.dto;

public class TaskCreatedResponse {
    private String taskId;
    private String status;

    public TaskCreatedResponse(String taskId, String status) {
        this.taskId = taskId;
        this.status = status;
    }

    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
}
