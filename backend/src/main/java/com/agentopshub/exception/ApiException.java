package com.agentopshub.exception;

public class ApiException extends RuntimeException {
    private final String code;
    private final Object details;
    private final int status;

    public ApiException(String code, String message, Object details, int status) {
        super(message);
        this.code = code;
        this.details = details;
        this.status = status;
    }

    public String getCode() { return code; }
    public Object getDetails() { return details; }
    public int getStatus() { return status; }
}
