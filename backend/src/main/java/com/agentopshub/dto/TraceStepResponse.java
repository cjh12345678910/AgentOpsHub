package com.agentopshub.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TraceStepResponse {
    private Long stepId;
    private Integer seq;
    private String stepType;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private Instant startedAt;
    private Instant endedAt;
    private Long latencyMs;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private List<String> citations;
    private List<ToolCallTraceResponse> toolCalls;
    private Integer round;
    private Map<String, Object> modelUsage;
    private Map<String, Object> verifierReport;
    private String diffSummary;
    private String parserStage;
    private String rawResponseSnippet;
    private String usageUnavailableReason;
    private String finalAnswerText;

    public TraceStepResponse() {
    }

    public TraceStepResponse(Long stepId, Integer seq, String stepType, String status, String inputSummary,
                             String outputSummary, Instant startedAt, Instant endedAt, Long latencyMs,
                             String errorCode, String errorMessage, Integer retryCount,
                             List<String> citations, List<ToolCallTraceResponse> toolCalls, Integer round,
                             Map<String, Object> modelUsage, Map<String, Object> verifierReport, String diffSummary,
                             String parserStage, String rawResponseSnippet, String usageUnavailableReason,
                             String finalAnswerText) {
        this.stepId = stepId;
        this.seq = seq;
        this.stepType = stepType;
        this.status = status;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.citations = citations;
        this.toolCalls = toolCalls;
        this.round = round;
        this.modelUsage = modelUsage;
        this.verifierReport = verifierReport;
        this.diffSummary = diffSummary;
        this.parserStage = parserStage;
        this.rawResponseSnippet = rawResponseSnippet;
        this.usageUnavailableReason = usageUnavailableReason;
        this.finalAnswerText = finalAnswerText;
    }

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
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
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
    public List<ToolCallTraceResponse> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallTraceResponse> toolCalls) { this.toolCalls = toolCalls; }
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
