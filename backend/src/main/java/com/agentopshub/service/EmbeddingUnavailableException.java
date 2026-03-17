package com.agentopshub.service;

public class EmbeddingUnavailableException extends RuntimeException {
    private final String code;

    public EmbeddingUnavailableException(String message) {
        this("embedding_unavailable", message);
    }

    public EmbeddingUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
