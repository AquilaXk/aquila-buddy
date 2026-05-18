package com.aquilabuddy.domain.embedding;

import java.util.Objects;

/**
 * embedding backfill 배치가 처리한 대상 수와 skip/failure 사유를 운영 응답으로 전달합니다.
 */
public record EmbeddingBackfillResult(
    String target,
    int requestedLimit,
    int candidates,
    int updated,
    int skippedExisting,
    int skippedBlankText,
    int skippedProviderUnavailable,
    int skippedDimensionMismatch,
    int failed
) {

    public EmbeddingBackfillResult {
        target = Objects.requireNonNull(target, "target must not be null.").trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank.");
        }
        requireNonNegative(requestedLimit, "requestedLimit");
        requireNonNegative(candidates, "candidates");
        requireNonNegative(updated, "updated");
        requireNonNegative(skippedExisting, "skippedExisting");
        requireNonNegative(skippedBlankText, "skippedBlankText");
        requireNonNegative(skippedProviderUnavailable, "skippedProviderUnavailable");
        requireNonNegative(skippedDimensionMismatch, "skippedDimensionMismatch");
        requireNonNegative(failed, "failed");
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
