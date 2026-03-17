package com.agentopshub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@Primary
public class RoutingEmbeddingProvider implements EmbeddingProvider {
    private final HashEmbeddingProvider hashProvider;
    private final OpenAiCompatibleEmbeddingProvider openAiProvider;
    private final String provider;
    private final String model;
    private final int dimension;
    private final boolean enabled;
    private final boolean fallbackToLocalHash;

    public RoutingEmbeddingProvider(HashEmbeddingProvider hashProvider,
                                    OpenAiCompatibleEmbeddingProvider openAiProvider,
                                    @Value("${app.rag.embedding.provider:local-hash}") String provider,
                                    @Value("${app.rag.embedding.model:local-hash-v1}") String model,
                                    @Value("${app.rag.embedding.dimension:64}") int dimension,
                                    @Value("${app.rag.embedding.enabled:true}") boolean enabled,
                                    @Value("${app.rag.embedding.fallback-to-local-hash:true}") boolean fallbackToLocalHash) {
        this.hashProvider = hashProvider;
        this.openAiProvider = openAiProvider;
        this.provider = provider == null ? "local-hash" : provider.trim().toLowerCase(Locale.ROOT);
        this.model = model;
        this.dimension = dimension;
        this.enabled = enabled;
        this.fallbackToLocalHash = fallbackToLocalHash;
    }

    @Override
    public String providerName() {
        return provider;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<double[]> embedTexts(List<String> texts) {
        if (!enabled) {
            throw new EmbeddingUnavailableException("embedding_disabled", "Embedding provider is disabled by configuration");
        }

        if (provider.equals("local-hash") || provider.equals("hash")) {
            return hashProvider.embedTexts(texts);
        }

        if (provider.equals("openai") || provider.equals("openai-compatible")) {
            try {
                return openAiProvider.embedTexts(texts);
            } catch (EmbeddingUnavailableException ex) {
                if (!fallbackToLocalHash) {
                    throw ex;
                }
                return hashProvider.embedTexts(texts);
            }
        }

        throw new EmbeddingUnavailableException("embedding_provider_unsupported", "Unsupported embedding provider: " + provider);
    }
}
