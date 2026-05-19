package com.aquilabuddy.domain.analysis;

import java.time.Instant;
import java.util.Objects;

/**
 * 저장된 이슈 가이드와 생성 시각을 함께 전달하는 cache snapshot입니다.
 */
public record CachedIssueGuide(
    IssueGuide guide,
    Instant generatedAt
) {

    public CachedIssueGuide {
        guide = Objects.requireNonNull(guide, "guide must not be null");
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
    }
}
