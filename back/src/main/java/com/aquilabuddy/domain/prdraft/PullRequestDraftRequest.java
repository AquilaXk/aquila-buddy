package com.aquilabuddy.domain.prdraft;

import java.util.Objects;

/**
 * PR 초안 생성에 필요한 저장소, 이슈, 브랜치, diff 정보를 담는 도메인 요청 모델입니다.
 */
public record PullRequestDraftRequest(
    String upstreamRepo,
    long githubIssueNumber,
    String baseBranch,
    String headBranch,
    String diffContent
) {

    /**
     * 화면/adapter에서 넘어온 문자열 입력을 domain 경계에서 한 번 더 정규화합니다.
     */
    public PullRequestDraftRequest {
        upstreamRepo = requireText(upstreamRepo, "upstreamRepo");
        baseBranch = requireText(baseBranch, "baseBranch");
        headBranch = requireText(headBranch, "headBranch");
        diffContent = requireText(diffContent, "diffContent");

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
