package com.agentopshub.dto;

import java.time.Instant;

public class ErrorResponse {
    private String code;
    private String message;
    private Object details;
    private String timestamp;

    public ErrorResponse(String code, String message, Object details) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = Instant.now().toString();
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Object getDetails() { return details; }
    public String getTimestamp() { return timestamp; }
}
