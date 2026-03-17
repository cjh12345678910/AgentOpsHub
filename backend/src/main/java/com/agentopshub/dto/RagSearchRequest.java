package com.agentopshub.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class RagSearchRequest {
    @NotBlank(message = "query is required")
    private String query;

    private List<String> docIds;

    private Integer topK;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<String> getDocIds() { return docIds; }
    public void setDocIds(List<String> docIds) { this.docIds = docIds; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
}
