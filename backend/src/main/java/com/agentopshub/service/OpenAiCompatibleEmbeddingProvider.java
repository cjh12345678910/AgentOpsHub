package com.agentopshub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleEmbeddingProvider {
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutMs;
    private final int retryMax;

    public OpenAiCompatibleEmbeddingProvider(ObjectMapper objectMapper,
                                             @Value("${app.rag.embedding.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                             @Value("${app.rag.embedding.model:text-embedding-3-small}") String model,
                                             @Value("${app.rag.embedding.openai.api-key:}") String apiKey,
                                             @Value("${app.rag.embedding.openai.timeout-ms:10000}") int timeoutMs,
                                             @Value("${app.rag.embedding.openai.retry-max:1}") int retryMax) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.retryMax = Math.max(0, retryMax);
    }

    public List<double[]> embedTexts(List<String> texts) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingUnavailableException("embedding_auth_missing", "OpenAI-compatible embedding api key is missing");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                return requestEmbeddings(texts);
            } catch (EmbeddingUnavailableException ex) {
                lastError = ex;
                if ("embedding_auth_failed".equals(ex.getCode()) || "embedding_bad_response".equals(ex.getCode())) {
                    break;
                }
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }

        if (lastError instanceof EmbeddingUnavailableException emb) {
            throw emb;
        }
        throw new EmbeddingUnavailableException("embedding_unavailable", lastError == null ? "Unknown embedding failure" : lastError.getMessage());
    }

    private List<double[]> requestEmbeddings(List<String> texts) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/embeddings");
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                "model", model,
                "input", texts
            ));
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = conn.getOutputStream()) {
                output.write(payload);
            }

            int code = conn.getResponseCode();
            InputStream bodyStream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = bodyStream == null ? "" : new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);

            if (code == 401 || code == 403) {
                throw new EmbeddingUnavailableException("embedding_auth_failed", "Embedding provider auth failed: HTTP " + code);
            }
            if (code < 200 || code >= 300) {
                throw new EmbeddingUnavailableException("embedding_http_error", "Embedding provider HTTP " + code + ": " + body);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new EmbeddingUnavailableException("embedding_bad_response", "Embedding provider response missing data array");
            }

            List<double[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embNode = item.get("embedding");
                if (embNode == null || !embNode.isArray()) {
                    throw new EmbeddingUnavailableException("embedding_bad_response", "Embedding item missing embedding array");
                }
                double[] vector = new double[embNode.size()];
                for (int i = 0; i < embNode.size(); i++) {
                    vector[i] = embNode.get(i).asDouble();
                }
                vectors.add(vector);
            }

            if (vectors.size() != texts.size()) {
                throw new EmbeddingUnavailableException("embedding_bad_response",
                    "Embedding count mismatch: expected " + texts.size() + " but got " + vectors.size());
            }
            return vectors;
        } catch (EmbeddingUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingUnavailableException("embedding_network_error", "Embedding provider call failed: " + ex.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
