package com.aquilabuddy.domain.embedding;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * embedding provider 응답과 pgvector 질의 사이에서 공유하는 벡터 값 객체입니다.
 */
public record EmbeddingVector(
    List<Double> values
) {

    public EmbeddingVector {
        values = values == null ? List.of() : List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty.");
        }
        values.forEach(EmbeddingVector::requireFinite);
        if (magnitude(values) == 0.0) {
            throw new IllegalArgumentException("values must not be a zero vector.");
        }
    }

    public int dimension() {
        return values.size();
    }

    public double cosineSimilarity(EmbeddingVector other) {
        Objects.requireNonNull(other, "other must not be null.");
        if (dimension() != other.dimension()) {
            throw new IllegalArgumentException("Embedding vector dimension must match.");
        }

        double dotProduct = 0.0;
        for (int index = 0; index < values.size(); index++) {
            dotProduct += values.get(index) * other.values().get(index);
        }

        return dotProduct / (magnitude(values) * magnitude(other.values()));
    }

    public String toPgvectorLiteral() {
        return values.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static void requireFinite(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("values must contain only finite numbers.");
        }
    }

    private static double magnitude(List<Double> values) {
        double sum = 0.0;
        for (Double value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
