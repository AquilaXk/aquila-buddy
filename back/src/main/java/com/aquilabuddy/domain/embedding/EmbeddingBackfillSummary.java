package com.aquilabuddy.domain.embedding;

import java.util.Objects;

/**
 * profile과 issue embedding backfill 결과를 한 응답으로 묶는 요약 모델입니다.
 */
public record EmbeddingBackfillSummary(
    EmbeddingBackfillResult profiles,
    EmbeddingBackfillResult issues
) {

    public EmbeddingBackfillSummary {
        Objects.requireNonNull(profiles, "profiles must not be null.");
        Objects.requireNonNull(issues, "issues must not be null.");
    }
}
