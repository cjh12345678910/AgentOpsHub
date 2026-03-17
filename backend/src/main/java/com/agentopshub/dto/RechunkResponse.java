package com.agentopshub.dto;

public class RechunkResponse {
    private Integer limit;
    private Integer processedDocs;
    private Integer rebuiltChunks;
    private Integer failedDocs;

    public RechunkResponse() {
    }

    public RechunkResponse(Integer limit, Integer processedDocs, Integer rebuiltChunks, Integer failedDocs) {
        this.limit = limit;
        this.processedDocs = processedDocs;
        this.rebuiltChunks = rebuiltChunks;
        this.failedDocs = failedDocs;
    }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public Integer getProcessedDocs() { return processedDocs; }
    public void setProcessedDocs(Integer processedDocs) { this.processedDocs = processedDocs; }
    public Integer getRebuiltChunks() { return rebuiltChunks; }
    public void setRebuiltChunks(Integer rebuiltChunks) { this.rebuiltChunks = rebuiltChunks; }
    public Integer getFailedDocs() { return failedDocs; }
    public void setFailedDocs(Integer failedDocs) { this.failedDocs = failedDocs; }
}
