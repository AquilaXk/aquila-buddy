package com.aquilabuddy.domain.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * AI provider 호출 결과를 운영 추적용으로 남기는 불변 이벤트입니다.
 */
public record AiCallObservation(
    String provider,
    AiCallOperation operation,
    String model,
    String promptVersion,
    long latencyMillis,
    boolean success,
    String errorType,
    Integer inputTokens,
    Integer outputTokens,
    Instant observedAt
) {

    public AiCallObservation {
        provider = requireText(provider, "provider");
        operation = Objects.requireNonNull(operation, "operation must not be null.");
        model = requireText(model, "model");
        promptVersion = requireText(promptVersion, "promptVersion");
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis must not be negative.");
        }
        if (!success) {
            errorType = requireText(errorType, "errorType");
        } else if (errorType != null && errorType.isBlank()) {
            errorType = null;
        }
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null.");
    }

    public static AiCallObservation success(
        String provider,
        AiCallOperation operation,
        String model,
        String promptVersion,
        Duration latency,
        Integer inputTokens,
        Integer outputTokens,
        Instant observedAt
    ) {
        return new AiCallObservation(
            provider,
            operation,
            model,
            promptVersion,
            latencyMillis(latency),
            true,
            null,
            inputTokens,
            outputTokens,
            observedAt
        );
    }

    public static AiCallObservation failure(
        String provider,
        AiCallOperation operation,
        String model,
        String promptVersion,
        Duration latency,
        String errorType,
        Instant observedAt
    ) {
        return new AiCallObservation(
            provider,
            operation,
            model,
            promptVersion,
            latencyMillis(latency),
            false,
            errorType,
            null,
            null,
            observedAt
        );
    }

    private static long latencyMillis(Duration latency) {
        return Objects.requireNonNull(latency, "latency must not be null.").toMillis();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
