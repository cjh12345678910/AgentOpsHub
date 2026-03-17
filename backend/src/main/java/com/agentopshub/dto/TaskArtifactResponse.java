package com.agentopshub.dto;

import java.time.Instant;

public class TaskArtifactResponse {
    private String name;
    private String relativePath;
    private String downloadUrl;
    private Long sizeBytes;
    private Instant createdAt;
    private String sourceTool;

    public TaskArtifactResponse() {
    }

    public TaskArtifactResponse(String name, String relativePath, String downloadUrl, Long sizeBytes, Instant createdAt, String sourceTool) {
        this.name = name;
        this.relativePath = relativePath;
        this.downloadUrl = downloadUrl;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.sourceTool = sourceTool;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getSourceTool() { return sourceTool; }
    public void setSourceTool(String sourceTool) { this.sourceTool = sourceTool; }
}
