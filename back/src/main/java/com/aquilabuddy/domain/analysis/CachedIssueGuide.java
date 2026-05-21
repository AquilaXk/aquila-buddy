package com.aquilabuddy.domain.analysis;

import java.time.Instant;
import java.util.Objects;

/**
 * 저장된 이슈 가이드와 생성 시각을 함께 전달하는 cache snapshot입니다.
 */
public record CachedIssueGuide(
    IssueGuide guide,
    Instant generatedAt,
    String outputVersion
) {

    public CachedIssueGuide(IssueGuide guide, Instant generatedAt) {
        this(guide, generatedAt, IssueGuideOutputContract.CURRENT_VERSION);
    }

    public CachedIssueGuide {
        guide = Objects.requireNonNull(guide, "guide must not be null");
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
        outputVersion = outputVersion == null || outputVersion.isBlank()
            ? IssueGuideOutputContract.LEGACY_VERSION
            : outputVersion.trim();
    }
}
