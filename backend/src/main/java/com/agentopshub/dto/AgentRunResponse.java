package com.agentopshub.dto;

import java.util.List;
import java.util.Map;

public class AgentRunResponse {
    private String status;
    private String phase;
    private String phaseStatus;
    private String resultMd;
    private String resultJson;
    private List<String> citations;
    private List<AgentStepResult> steps;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> verifierReport;
    private List<Map<String, Object>> repairRounds;
    private String finalDecision;
    private List<Map<String, Object>> modelUsage;
    private Integer currentRound;
    private String parserStage;
    private String rawResponseSnippet;
    private String usageUnavailableReason;
    private Map<String, Object> finalAnswer;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getPhaseStatus() { return phaseStatus; }
    public void setPhaseStatus(String phaseStatus) { this.phaseStatus = phaseStatus; }
    public String getResultMd() { return resultMd; }
    public void setResultMd(String resultMd) { this.resultMd = resultMd; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
    public List<AgentStepResult> getSteps() { return steps; }
    public void setSteps(List<AgentStepResult> steps) { this.steps = steps; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, Object> getVerifierReport() { return verifierReport; }
    public void setVerifierReport(Map<String, Object> verifierReport) { this.verifierReport = verifierReport; }
    public List<Map<String, Object>> getRepairRounds() { return repairRounds; }
    public void setRepairRounds(List<Map<String, Object>> repairRounds) { this.repairRounds = repairRounds; }
    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }
    public List<Map<String, Object>> getModelUsage() { return modelUsage; }
    public void setModelUsage(List<Map<String, Object>> modelUsage) { this.modelUsage = modelUsage; }
    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }
    public String getParserStage() { return parserStage; }
    public void setParserStage(String parserStage) { this.parserStage = parserStage; }
    public String getRawResponseSnippet() { return rawResponseSnippet; }
    public void setRawResponseSnippet(String rawResponseSnippet) { this.rawResponseSnippet = rawResponseSnippet; }
    public String getUsageUnavailableReason() { return usageUnavailableReason; }
    public void setUsageUnavailableReason(String usageUnavailableReason) { this.usageUnavailableReason = usageUnavailableReason; }
    public Map<String, Object> getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(Map<String, Object> finalAnswer) { this.finalAnswer = finalAnswer; }
}
