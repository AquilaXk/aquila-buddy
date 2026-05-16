package com.aquilabuddy.domain.prdraft;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 단일 번역 action API에서 기존 클라이언트가 기대하는 PR 초안 영문 번역 응답입니다.
 */
public record PullRequestDraftTranslateResult(
    String titleEn,
    String bodyEn,
    String githubUrl
) {

    public PullRequestDraftTranslateResult {
        titleEn = requireText(titleEn, "titleEn");
        bodyEn = requireText(bodyEn, "bodyEn");
        githubUrl = requireText(githubUrl, "githubUrl");
    }

    public static PullRequestDraftTranslateResult from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation
    ) {
        return from(draft, translation, new PullRequestDraftCompareReference(
            draft.baseBranch(),
            draft.headBranch(),
            null
        ));
    }

    public static PullRequestDraftTranslateResult from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation,
        PullRequestDraftCompareReference compareReference
    ) {
        Objects.requireNonNull(draft, "draft must not be null.");
        Objects.requireNonNull(translation, "translation must not be null.");
        Objects.requireNonNull(compareReference, "compareReference must not be null.");
        if (!draft.id().equals(translation.draftId())) {
            throw new IllegalArgumentException("translation must belong to draft.");
        }

        return new PullRequestDraftTranslateResult(
            translation.title(),
            translation.body(),
            buildGithubUrl(draft, translation, compareReference)
        );
    }

    private static String buildGithubUrl(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation,
        PullRequestDraftCompareReference compareReference
    ) {
        return "https://github.com/"
            + draft.upstreamRepo()
            + "/compare/"
            + compareReference.comparePath()
            + "?quick_pull=1&title="
            + encodeQueryValue(translation.title())
            + "&body="
            + encodeQueryValue(translation.body());
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
