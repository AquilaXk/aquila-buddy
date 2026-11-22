package com.aquilabuddy.domain.prdraft;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Contribution report에서 최신 번역 결과를 compact하게 표시하기 위한 요약 모델입니다.
 */
public record PullRequestDraftContributionTranslation(
    String translationId,
    String targetLanguage,
    String title,
    Instant createdAt
) {

    public PullRequestDraftContributionTranslation {
        translationId = requireText(translationId, "translationId");
        targetLanguage = requireText(targetLanguage, "targetLanguage").toLowerCase(Locale.ROOT);
        title = requireText(title, "title");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null.");
    }

    public static PullRequestDraftContributionTranslation from(PullRequestDraftTranslation translation) {
        Objects.requireNonNull(translation, "translation must not be null.");

        return new PullRequestDraftContributionTranslation(
            translation.id(),
            translation.targetLanguage(),
            translation.title(),
            translation.createdAt()
        );
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
