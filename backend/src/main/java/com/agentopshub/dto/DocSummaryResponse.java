package com.agentopshub.dto;

import java.time.Instant;

public class DocSummaryResponse {
    private String docId;
    private String name;
    private String contentType;
    private String status;
    private Long fileSize;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public DocSummaryResponse() {
    }

    public DocSummaryResponse(String docId, String name, String contentType, String status, Long fileSize,
                              String errorMessage, Instant createdAt, Instant updatedAt) {
        this.docId = docId;
        this.name = name;
        this.contentType = contentType;
        this.status = status;
        this.fileSize = fileSize;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
