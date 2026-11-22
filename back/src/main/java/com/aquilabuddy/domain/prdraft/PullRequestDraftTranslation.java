package com.aquilabuddy.domain.prdraft;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 저장된 PR 초안에서 파생된 번역 결과를 식별자와 대상 언어와 함께 담습니다.
 */
public record PullRequestDraftTranslation(
    String id,
    String draftId,
    String sourceIssueId,
    String targetLanguage,
    String title,
    String body,
    Instant createdAt
) {

    public PullRequestDraftTranslation(
        String id,
        String draftId,
        String targetLanguage,
        String title,
        String body,
        Instant createdAt
    ) {
        this(id, draftId, null, targetLanguage, title, body, createdAt);
    }

    public PullRequestDraftTranslation {
        id = requireText(id, "id");
        draftId = requireText(draftId, "draftId");
        sourceIssueId = optionalText(sourceIssueId);
        targetLanguage = requireText(targetLanguage, "targetLanguage").toLowerCase(Locale.ROOT);
        title = requireText(title, "title");
        body = requireText(body, "body");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null.");
    }

    public PullRequestDraftTranslation withSourceIssueId(String sourceIssueId) {
        return new PullRequestDraftTranslation(id, draftId, sourceIssueId, targetLanguage, title, body, createdAt);
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
