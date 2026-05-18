package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import java.util.List;
import java.util.Objects;

/**
 * Vertex AI embedding 응답을 애플리케이션의 embedding port로 연결합니다.
 */
public class VertexAiEmbeddingClient implements EmbeddingClient {

    private final VertexAiEmbeddingGenerator generator;

    public VertexAiEmbeddingClient(VertexAiEmbeddingGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null.");
    }

    @Override
    public EmbeddingVector embed(String input) {
        String normalizedInput = requireInput(input);
        List<Double> values = generator.embed(normalizedInput);
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Vertex AI embedding vector must not be empty.");
        }
        return new EmbeddingVector(values);
    }

    private static String requireInput(String value) {
        String normalized = Objects.requireNonNull(value, "input must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank.");
        }
        return normalized;
    }
}
