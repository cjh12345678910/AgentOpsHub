package com.agentopshub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingProviderRoutingTest {

    @Test
    void openAiProviderFailsWithoutApiKey() {
        OpenAiCompatibleEmbeddingProvider provider = new OpenAiCompatibleEmbeddingProvider(
            new ObjectMapper(),
            "https://api.openai.com/v1",
            "text-embedding-3-small",
            "",
            1000,
            0
        );

        EmbeddingUnavailableException ex = assertThrows(EmbeddingUnavailableException.class,
            () -> provider.embedTexts(List.of("hello")));
        assertEquals("embedding_auth_missing", ex.getCode());
    }

    @Test
    void routingProviderFallsBackToHash() {
        HashEmbeddingProvider hashProvider = new HashEmbeddingProvider("local-hash", "local-hash-v1", 8, true);
        OpenAiCompatibleEmbeddingProvider openAiProvider = new OpenAiCompatibleEmbeddingProvider(
            new ObjectMapper(),
            "https://api.openai.com/v1",
            "text-embedding-3-small",
            "",
            1000,
            0
        );
        RoutingEmbeddingProvider provider = new RoutingEmbeddingProvider(
            hashProvider,
            openAiProvider,
            "openai-compatible",
            "text-embedding-3-small",
            8,
            true,
            true
        );

        List<double[]> vectors = provider.embedTexts(List.of("fallback"));
        assertEquals(1, vectors.size());
        assertEquals(8, vectors.get(0).length);
    }

    @Test
    void routingProviderThrowsWhenFallbackDisabled() {
        HashEmbeddingProvider hashProvider = new HashEmbeddingProvider("local-hash", "local-hash-v1", 8, true);
        OpenAiCompatibleEmbeddingProvider openAiProvider = new OpenAiCompatibleEmbeddingProvider(
            new ObjectMapper(),
            "https://api.openai.com/v1",
            "text-embedding-3-small",
            "",
            1000,
            0
        );
        RoutingEmbeddingProvider provider = new RoutingEmbeddingProvider(
            hashProvider,
            openAiProvider,
            "openai-compatible",
            "text-embedding-3-small",
            8,
            true,
            false
        );

        EmbeddingUnavailableException ex = assertThrows(EmbeddingUnavailableException.class,
            () -> provider.embedTexts(List.of("no-fallback")));
        assertEquals("embedding_auth_missing", ex.getCode());
    }
}
