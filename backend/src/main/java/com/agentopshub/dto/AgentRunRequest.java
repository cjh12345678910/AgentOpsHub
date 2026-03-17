package com.agentopshub.dto;

import java.util.List;

public class AgentRunRequest {
    private String taskId;
    private String prompt;
    private List<String> docIds;
    private String outputFormat;
    private String token;
    private String role;

    public AgentRunRequest() {
    }

    public AgentRunRequest(String taskId, String prompt, List<String> docIds, String outputFormat) {
        this.taskId = taskId;
        this.prompt = prompt;
        this.docIds = docIds;
        this.outputFormat = outputFormat;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getDocIds() { return docIds; }
    public void setDocIds(List<String> docIds) { this.docIds = docIds; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
