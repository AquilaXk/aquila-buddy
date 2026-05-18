package com.aquilabuddy.domain.embedding;

import java.util.List;
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
    int failed,
    List<String> failureReasons
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
        failureReasons = normalizeFailureReasons(failureReasons);
    }

    public EmbeddingBackfillResult(
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
        this(
            target,
            requestedLimit,
            candidates,
            updated,
            skippedExisting,
            skippedBlankText,
            skippedProviderUnavailable,
            skippedDimensionMismatch,
            failed,
            List.of()
        );
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }

    private static List<String> normalizeFailureReasons(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }
}
