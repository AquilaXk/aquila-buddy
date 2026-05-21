package com.aquilabuddy.domain.issue;

import java.util.List;
import java.util.Objects;

/**
 * 추천 이슈 카드 copy refresh dry-run/live 실행 결과를 운영 응답으로 전달합니다.
 */
public record RecommendedIssueCopyRefreshResult(
    String target,
    int requestedLimit,
    int candidates,
    int updated,
    int skippedCurrent,
    int failed,
    boolean dryRun,
    List<Sample> samples,
    List<String> failureReasons
) {

    public RecommendedIssueCopyRefreshResult {
        target = Objects.requireNonNull(target, "target must not be null.").trim();
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank.");
        }
        requireNonNegative(requestedLimit, "requestedLimit");
        requireNonNegative(candidates, "candidates");
        requireNonNegative(updated, "updated");
        requireNonNegative(skippedCurrent, "skippedCurrent");
        requireNonNegative(failed, "failed");
        samples = samples == null ? List.of() : List.copyOf(samples);
        failureReasons = normalizeStrings(failureReasons);
    }

    public record Sample(
        String issueId,
        String repoFullName,
        long issueNumber,
        String reason
    ) {

        public Sample {
            issueId = normalize(issueId);
            repoFullName = normalize(repoFullName);
            reason = normalize(reason);
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
