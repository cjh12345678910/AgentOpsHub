package com.agentopshub.dto;

import java.util.List;
import java.util.Map;

public class TaskResultResponse {
    private String taskId;
    private String status;
    private String resultMd;
    private String resultJson;
    private List<String> citations;
    private Map<String, Object> verifierReport;
    private List<Map<String, Object>> repairRounds;
    private String finalDecision;
    private List<Map<String, Object>> modelUsage;
    private String parserStage;
    private String rawResponseSnippet;
    private String usageUnavailableReason;
    private Map<String, Object> finalAnswer;
    private List<TaskArtifactResponse> artifacts;

    public TaskResultResponse(String taskId, String status, String resultMd, String resultJson, List<String> citations,
                              Map<String, Object> verifierReport, List<Map<String, Object>> repairRounds,
                              String finalDecision, List<Map<String, Object>> modelUsage,
                              String parserStage, String rawResponseSnippet, String usageUnavailableReason,
                              Map<String, Object> finalAnswer,
                              List<TaskArtifactResponse> artifacts) {
        this.taskId = taskId;
        this.status = status;
        this.resultMd = resultMd;
        this.resultJson = resultJson;
        this.citations = citations;
        this.verifierReport = verifierReport;
        this.repairRounds = repairRounds;
        this.finalDecision = finalDecision;
        this.modelUsage = modelUsage;
        this.parserStage = parserStage;
        this.rawResponseSnippet = rawResponseSnippet;
        this.usageUnavailableReason = usageUnavailableReason;
        this.finalAnswer = finalAnswer;
        this.artifacts = artifacts;
    }

    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getResultMd() { return resultMd; }
    public String getResultJson() { return resultJson; }
    public List<String> getCitations() { return citations; }
    public Map<String, Object> getVerifierReport() { return verifierReport; }
    public List<Map<String, Object>> getRepairRounds() { return repairRounds; }
    public String getFinalDecision() { return finalDecision; }
    public List<Map<String, Object>> getModelUsage() { return modelUsage; }
    public String getParserStage() { return parserStage; }
    public String getRawResponseSnippet() { return rawResponseSnippet; }
    public String getUsageUnavailableReason() { return usageUnavailableReason; }
    public Map<String, Object> getFinalAnswer() { return finalAnswer; }
    public List<TaskArtifactResponse> getArtifacts() { return artifacts; }
}
