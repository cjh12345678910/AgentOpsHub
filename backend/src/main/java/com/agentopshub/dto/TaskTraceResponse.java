package com.agentopshub.dto;

import java.util.List;

public class TaskTraceResponse {
    private String taskId;
    private String status;
    private String traceId;
    private boolean incomplete;
    private List<TraceStepResponse> steps;

    public TaskTraceResponse() {
    }

    public TaskTraceResponse(String taskId, String status, String traceId, boolean incomplete, List<TraceStepResponse> steps) {
        this.taskId = taskId;
        this.status = status;
        this.traceId = traceId;
        this.incomplete = incomplete;
        this.steps = steps;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public boolean isIncomplete() { return incomplete; }
    public void setIncomplete(boolean incomplete) { this.incomplete = incomplete; }
    public List<TraceStepResponse> getSteps() { return steps; }
    public void setSteps(List<TraceStepResponse> steps) { this.steps = steps; }
}
