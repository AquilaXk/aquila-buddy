package com.aquilabuddy.domain.prdraft;

import java.time.Instant;
import java.util.Objects;

/**
 * 생성된 PR 초안을 이후 화면/API에서 다시 조회할 수 있도록 저장 식별자와 요청 맥락을 함께 담습니다.
 */
public record StoredPullRequestDraft(
    String id,
    String upstreamRepo,
    long githubIssueNumber,
    String baseBranch,
    String headBranch,
    String title,
    String body,
    Instant createdAt
) {

    public StoredPullRequestDraft {
        id = requireText(id, "id");
        upstreamRepo = requireText(upstreamRepo, "upstreamRepo");
        baseBranch = requireText(baseBranch, "baseBranch");
        headBranch = requireText(headBranch, "headBranch");
        title = requireText(title, "title");
        body = requireText(body, "body");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null.");

        if (githubIssueNumber < 1) {
            throw new IllegalArgumentException("githubIssueNumber must be positive.");
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
