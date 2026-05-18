package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vertex AI embedding 응답을 애플리케이션의 embedding port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class VertexAiEmbeddingClient implements EmbeddingClient {

    private static final String PROVIDER = "vertex-ai";
    private static final String PROMPT_VERSION = "ai-client.embed.v1";

    private final VertexAiEmbeddingGenerator generator;
    private final AiCallObserver observer;
    private final String model;

    public VertexAiEmbeddingClient(VertexAiEmbeddingGenerator generator) {
        this(generator, new NoopAiCallObserver(), "gemini-embedding-001");
    }

    @Autowired
    public VertexAiEmbeddingClient(VertexAiEmbeddingGenerator generator, AiCallObserver observer, VertexAiProperties properties) {
        this(generator, observer, properties.embeddingModelOrDefault());
    }

    public VertexAiEmbeddingClient(VertexAiEmbeddingGenerator generator, AiCallObserver observer, String model) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null.");
        this.observer = Objects.requireNonNull(observer, "observer must not be null.");
        this.model = requireInput(model);
    }

    @Override
    public EmbeddingVector embed(String input) {
        String normalizedInput = requireInput(input);
        long started = System.nanoTime();
        try {
            List<Double> values = generator.embed(normalizedInput);
            if (values == null || values.isEmpty()) {
                throw new IllegalStateException("Vertex AI embedding vector must not be empty.");
            }
            observer.record(AiCallObservation.success(
                PROVIDER,
                AiCallOperation.EMBEDDING,
                model,
                PROMPT_VERSION,
                elapsedSince(started),
                null,
                null,
                Instant.now()
            ));
            return new EmbeddingVector(values);
        } catch (RuntimeException exception) {
            observer.record(AiCallObservation.failure(
                PROVIDER,
                AiCallOperation.EMBEDDING,
                model,
                PROMPT_VERSION,
                elapsedSince(started),
                exception.getClass().getSimpleName(),
                Instant.now()
            ));
            throw exception;
        }
    }

    private static String requireInput(String value) {
        String normalized = Objects.requireNonNull(value, "input must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank.");
        }
        return normalized;
    }

    private static Duration elapsedSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }
}
