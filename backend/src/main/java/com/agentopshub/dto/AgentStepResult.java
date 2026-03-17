package com.agentopshub.dto;

import java.util.List;
import java.util.Map;

public class AgentStepResult {
    private Integer seq;
    private String stepType;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private List<String> citations;
    private List<AgentToolCallResult> toolCalls;
    private Integer round;
    private Map<String, Object> modelUsage;
    private Map<String, Object> verifierReport;
    private String diffSummary;
    private String parserStage;
    private String rawResponseSnippet;
    private String usageUnavailableReason;
    private String finalAnswerText;

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public void setOutputSummary(String outputSummary) { this.outputSummary = outputSummary; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
    public List<AgentToolCallResult> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<AgentToolCallResult> toolCalls) { this.toolCalls = toolCalls; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public Map<String, Object> getModelUsage() { return modelUsage; }
    public void setModelUsage(Map<String, Object> modelUsage) { this.modelUsage = modelUsage; }
    public Map<String, Object> getVerifierReport() { return verifierReport; }
    public void setVerifierReport(Map<String, Object> verifierReport) { this.verifierReport = verifierReport; }
    public String getDiffSummary() { return diffSummary; }
    public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }
    public String getParserStage() { return parserStage; }
    public void setParserStage(String parserStage) { this.parserStage = parserStage; }
    public String getRawResponseSnippet() { return rawResponseSnippet; }
    public void setRawResponseSnippet(String rawResponseSnippet) { this.rawResponseSnippet = rawResponseSnippet; }
    public String getUsageUnavailableReason() { return usageUnavailableReason; }
    public void setUsageUnavailableReason(String usageUnavailableReason) { this.usageUnavailableReason = usageUnavailableReason; }
    public String getFinalAnswerText() { return finalAnswerText; }
    public void setFinalAnswerText(String finalAnswerText) { this.finalAnswerText = finalAnswerText; }
}
