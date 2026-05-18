package com.aquilabuddy.domain.prdraft;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Dashboard 최근 활동에 표시할 PR 초안 번역 결과와 원본 초안 맥락을 함께 담습니다.
 */
public record PullRequestDraftTranslationActivity(
    String draftId,
    String translationId,
    String upstreamRepo,
    long githubIssueNumber,
    String sourceIssueId,
    String targetLanguage,
    String title,
    Instant createdAt
) {

    public PullRequestDraftTranslationActivity {
        draftId = requireText(draftId, "draftId");
        translationId = requireText(translationId, "translationId");
        upstreamRepo = requireText(upstreamRepo, "upstreamRepo");
        targetLanguage = requireText(targetLanguage, "targetLanguage").toLowerCase(Locale.ROOT);
        title = requireText(title, "title");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null.");

        if (githubIssueNumber < 1) {
            throw new IllegalArgumentException("githubIssueNumber must be positive.");
        }
    }

    public static PullRequestDraftTranslationActivity from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation
    ) {
        Objects.requireNonNull(draft, "draft must not be null.");
        Objects.requireNonNull(translation, "translation must not be null.");

        if (!draft.id().equals(translation.draftId())) {
            throw new IllegalArgumentException("translation draftId must match stored draft id.");
        }

        return new PullRequestDraftTranslationActivity(
            draft.id(),
            translation.id(),
            draft.upstreamRepo(),
            draft.githubIssueNumber(),
            draft.sourceIssueId(),
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
