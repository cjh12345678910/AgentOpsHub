package com.agentopshub.service;

import java.util.List;

public interface EmbeddingProvider {
    String providerName();
    String modelName();
    int dimension();
    List<double[]> embedTexts(List<String> texts);
}
