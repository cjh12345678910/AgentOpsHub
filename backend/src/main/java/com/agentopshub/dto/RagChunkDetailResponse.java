package com.agentopshub.dto;

import java.time.Instant;

public class RagChunkDetailResponse {
    private String chunkId;
    private String docId;
    private String docName;
    private Integer chunkIndex;
    private String content;
    private String metadataJson;
    private Instant createdAt;

    public RagChunkDetailResponse() {
    }

    public RagChunkDetailResponse(String chunkId, String docId, String docName, Integer chunkIndex,
                                  String content, String metadataJson, Instant createdAt) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.docName = docName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
