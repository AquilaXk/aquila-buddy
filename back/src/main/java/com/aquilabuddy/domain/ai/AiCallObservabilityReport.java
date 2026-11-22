package com.aquilabuddy.domain.ai;

import java.util.List;

/**
 * 최근 AI provider 호출 이벤트와 운영 지표 요약입니다.
 */
public record AiCallObservabilityReport(
    int requestedLimit,
    int total,
    int successCount,
    int failureCount,
    long averageLatencyMillis,
    int totalInputTokens,
    int totalOutputTokens,
    List<AiCallObservation> recentCalls
) {

    public AiCallObservabilityReport {
        requireNonNegative(requestedLimit, "requestedLimit");
        requireNonNegative(total, "total");
        requireNonNegative(successCount, "successCount");
        requireNonNegative(failureCount, "failureCount");
        requireNonNegative(averageLatencyMillis, "averageLatencyMillis");
        requireNonNegative(totalInputTokens, "totalInputTokens");
        requireNonNegative(totalOutputTokens, "totalOutputTokens");
        recentCalls = recentCalls == null ? List.of() : List.copyOf(recentCalls);
    }

    private static void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
