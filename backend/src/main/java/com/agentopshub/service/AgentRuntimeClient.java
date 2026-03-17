package com.agentopshub.service;

import com.agentopshub.dto.AgentRunRequest;
import com.agentopshub.dto.AgentRunResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class AgentRuntimeClient {
    private final String baseUrl;
    private final boolean runtimeEnabled;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper objectMapper;

    public AgentRuntimeClient(@Value("${app.agent.runtime.base-url:http://127.0.0.1:18080}") String baseUrl,
                              @Value("${app.agent.runtime.enabled:false}") boolean runtimeEnabled,
                              @Value("${app.agent.runtime.connect-timeout-ms:5000}") int connectTimeoutMs,
                              @Value("${app.agent.runtime.read-timeout-ms:60000}") int readTimeoutMs,
                              ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.runtimeEnabled = runtimeEnabled;
        this.connectTimeoutMs = Math.max(1000, connectTimeoutMs);
        this.readTimeoutMs = Math.max(1000, readTimeoutMs);
        this.objectMapper = objectMapper;
    }

    public AgentRunResponse run(AgentRunRequest request) {
        if (!runtimeEnabled) {
            throw new AgentRuntimeUnavailableException("Agent runtime dispatch is disabled by configuration");
        }
        try {
            URL url = new URL(baseUrl + "/agent/run");
            // Runtime dispatch is an internal local-service hop and should not be routed via host proxy.
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] payload = objectMapper.writeValueAsBytes(request);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = conn.getOutputStream()) {
                output.write(payload);
            }

            int code = conn.getResponseCode();
            InputStream bodyStream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (bodyStream == null) {
                throw new AgentRuntimeUnavailableException("Agent runtime returned HTTP " + code + " with empty body");
            }

            if (code < 200 || code >= 300) {
                String errorPayload = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
                throw new AgentRuntimeUnavailableException("Agent runtime returned HTTP " + code + ": " + errorPayload);
            }

            return objectMapper.readValue(bodyStream, AgentRunResponse.class);
        } catch (Exception ex) {
            throw new AgentRuntimeUnavailableException("Agent runtime call failed: " + ex.getMessage());
        }
    }
}
