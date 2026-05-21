package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vertex AI Gemini 응답을 애플리케이션의 AI completion port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class VertexAiClient implements AiClient {

    private static final String PROVIDER = "vertex-ai";
    private static final String PROMPT_VERSION = "ai-client.complete.v1";

    private final VertexAiTextGenerator generator;
    private final AiCallObserver observer;
    private final String model;

    public VertexAiClient(VertexAiTextGenerator generator) {
        this(generator, new NoopAiCallObserver(), "gemini-2.5-pro");
    }

    @Autowired
    public VertexAiClient(VertexAiTextGenerator generator, ObjectProvider<AiCallObserver> observerProvider, VertexAiProperties properties) {
        this(generator, observerProvider.getIfAvailable(NoopAiCallObserver::new), properties.modelOrDefault());
    }

    public VertexAiClient(VertexAiTextGenerator generator, AiCallObserver observer, String model) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null.");
        this.observer = Objects.requireNonNull(observer, "observer must not be null.");
        this.model = requireText(model, "model");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String prompt = String.join("\n\n",
            "System prompt:",
            requireText(systemPrompt, "systemPrompt"),
            "User prompt:",
            requireText(userPrompt, "userPrompt")
        );
        long started = System.nanoTime();
        try {
            String content = requireText(generator.generate(prompt), "Vertex AI completion content");
            observer.record(AiCallObservation.success(
                PROVIDER,
                AiCallOperation.COMPLETION,
                model,
                PROMPT_VERSION,
                elapsedSince(started),
                null,
                null,
                Instant.now()
            ));
            return content;
        } catch (RuntimeException exception) {
            observer.record(AiCallObservation.failure(
                PROVIDER,
                AiCallOperation.COMPLETION,
                model,
                PROMPT_VERSION,
                elapsedSince(started),
                exception.getClass().getSimpleName(),
                failureReason(exception),
                Instant.now()
            ));
            throw exception;
        }
    }

    private static String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + message.strip();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static Duration elapsedSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }
}
