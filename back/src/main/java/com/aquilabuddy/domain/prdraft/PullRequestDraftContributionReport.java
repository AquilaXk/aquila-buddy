package com.aquilabuddy.domain.prdraft;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * PR 상세 화면에서 제출 전 상태를 한눈에 확인할 수 있도록 초안, readiness, 번역 요약을 묶습니다.
 */
public record PullRequestDraftContributionReport(
    String draftId,
    String upstreamRepo,
    long githubIssueNumber,
    String baseBranch,
    String headBranch,
    String title,
    Instant createdAt,
    int readinessReadyCount,
    int readinessTotalCount,
    int translationCount,
    PullRequestDraftContributionTranslation latestTranslation
) {

    public PullRequestDraftContributionReport {
        draftId = requireText(draftId, "draftId");
        upstreamRepo = requireText(upstreamRepo, "upstreamRepo");
        baseBranch = requireText(baseBranch, "baseBranch");
        headBranch = requireText(headBranch, "headBranch");
        title = requireText(title, "title");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null.");

        if (githubIssueNumber < 1) {
            throw new IllegalArgumentException("githubIssueNumber must be positive.");
        }
        if (readinessReadyCount < 0 || readinessTotalCount < 0 || readinessReadyCount > readinessTotalCount) {
            throw new IllegalArgumentException("readiness counts must be valid.");
        }
        if (translationCount < 0) {
            throw new IllegalArgumentException("translationCount must not be negative.");
        }
    }

    public static PullRequestDraftContributionReport from(
        StoredPullRequestDraft draft,
        List<PullRequestDraftTranslation> translations
    ) {
        Objects.requireNonNull(draft, "draft must not be null.");
        List<PullRequestDraftTranslation> safeTranslations = List.copyOf(translations);
        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);
        PullRequestDraftContributionTranslation latestTranslation = safeTranslations.stream()
            .max(Comparator.comparing(PullRequestDraftTranslation::createdAt))
            .map(PullRequestDraftContributionTranslation::from)
            .orElse(null);

        return new PullRequestDraftContributionReport(
            draft.id(),
            draft.upstreamRepo(),
            draft.githubIssueNumber(),
            draft.baseBranch(),
            draft.headBranch(),
            draft.title(),
            draft.createdAt(),
            readiness.readyCount(),
            readiness.items().size(),
            safeTranslations.size(),
            latestTranslation
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
