package com.agentopshub.dto;

import java.util.List;

public class RagSearchResponse {
    private String query;
    private Integer topK;
    private String retrievalMode;
    private String scoreType;
    private String fallbackReason;
    private Boolean partialCoverage;
    private Integer candidateCount;
    private Boolean rerankApplied;
    private String embeddingProvider;
    private List<Double> scoreDistribution;
    private List<RagSearchItemResponse> items;

    public RagSearchResponse() {
    }

    public RagSearchResponse(String query, Integer topK, List<RagSearchItemResponse> items) {
        this.query = query;
        this.topK = topK;
        this.items = items;
    }

    public RagSearchResponse(String query,
                             Integer topK,
                             String retrievalMode,
                             String scoreType,
                             String fallbackReason,
                             Boolean partialCoverage,
                             Integer candidateCount,
                             Boolean rerankApplied,
                             String embeddingProvider,
                             List<Double> scoreDistribution,
                             List<RagSearchItemResponse> items) {
        this.query = query;
        this.topK = topK;
        this.retrievalMode = retrievalMode;
        this.scoreType = scoreType;
        this.fallbackReason = fallbackReason;
        this.partialCoverage = partialCoverage;
        this.candidateCount = candidateCount;
        this.rerankApplied = rerankApplied;
        this.embeddingProvider = embeddingProvider;
        this.scoreDistribution = scoreDistribution;
        this.items = items;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public Boolean getPartialCoverage() { return partialCoverage; }
    public void setPartialCoverage(Boolean partialCoverage) { this.partialCoverage = partialCoverage; }
    public Integer getCandidateCount() { return candidateCount; }
    public void setCandidateCount(Integer candidateCount) { this.candidateCount = candidateCount; }
    public Boolean getRerankApplied() { return rerankApplied; }
    public void setRerankApplied(Boolean rerankApplied) { this.rerankApplied = rerankApplied; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }
    public List<Double> getScoreDistribution() { return scoreDistribution; }
    public void setScoreDistribution(List<Double> scoreDistribution) { this.scoreDistribution = scoreDistribution; }
    public List<RagSearchItemResponse> getItems() { return items; }
    public void setItems(List<RagSearchItemResponse> items) { this.items = items; }
}
