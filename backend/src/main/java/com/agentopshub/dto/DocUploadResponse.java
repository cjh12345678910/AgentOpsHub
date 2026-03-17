package com.agentopshub.dto;

import java.time.Instant;

public class DocUploadResponse {
    private String docId;
    private String status;
    private String name;
    private Long fileSize;
    private Integer chunkCount;
    private Instant createdAt;
    private Instant updatedAt;

    public DocUploadResponse() {
    }

    public DocUploadResponse(String docId, String status, String name, Long fileSize, Integer chunkCount, Instant createdAt, Instant updatedAt) {
        this.docId = docId;
        this.status = status;
        this.name = name;
        this.fileSize = fileSize;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
