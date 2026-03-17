package com.agentopshub.dto;

public class RagSearchItemResponse {
    private String chunkId;
    private String docId;
    private String docName;
    private Integer chunkIndex;
    private Double score;
    private String scoreType;
    private Integer rank;
    private String snippet;

    public RagSearchItemResponse() {
    }

    public RagSearchItemResponse(String chunkId, String docId, String docName, Integer chunkIndex, Double score, String snippet) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.docName = docName;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.snippet = snippet;
    }

    public RagSearchItemResponse(String chunkId, String docId, String docName, Integer chunkIndex, Double score, String scoreType, Integer rank, String snippet) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.docName = docName;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.scoreType = scoreType;
        this.rank = rank;
        this.snippet = snippet;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
