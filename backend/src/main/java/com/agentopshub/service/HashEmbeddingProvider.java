package com.agentopshub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class HashEmbeddingProvider implements EmbeddingProvider {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5]+");

    private final String providerName;
    private final String modelName;
    private final int dimension;
    private final boolean enabled;

    public HashEmbeddingProvider(@Value("${app.rag.embedding.provider:local-hash}") String providerName,
                                 @Value("${app.rag.embedding.model:local-hash-v1}") String modelName,
                                 @Value("${app.rag.embedding.dimension:64}") int dimension,
                                 @Value("${app.rag.embedding.enabled:true}") boolean enabled) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.dimension = dimension;
        this.enabled = enabled;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<double[]> embedTexts(List<String> texts) {
        if (!enabled) {
            throw new EmbeddingUnavailableException("Embedding provider is disabled by configuration");
        }
        List<double[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedSingle(text));
        }
        return vectors;
    }

    private double[] embedSingle(String text) {
        double[] vector = new double[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String[] terms = TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT));
        int termCount = 0;
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            termCount++;
            int base = term.hashCode();
            for (int i = 0; i < dimension; i++) {
                // 使用 deterministic hash 投影，保证相同输入在不同环境得到一致向量。
                int mixed = base ^ (i * 0x9e3779b9);
                double value = ((mixed & 0x7fffffff) / (double) Integer.MAX_VALUE) * 2D - 1D;
                vector[i] += value;
            }
        }

        if (termCount == 0) {
            return vector;
        }

        // 统一做 L2 normalization，便于后续 cosine similarity 排序稳定。
        double norm = 0D;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm <= 0D) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
        return vector;
    }
}
