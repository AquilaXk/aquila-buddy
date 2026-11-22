package com.aquilabuddy.domain.prdraft;

import java.util.Objects;

/**
 * PR 작성 URL의 compare path를 만들기 위한 base/head/fork owner 값 객체입니다.
 */
public record PullRequestDraftCompareReference(
    String baseBranch,
    String headBranch,
    String forkOwner
) {

    public PullRequestDraftCompareReference {
        baseBranch = requireText(baseBranch, "baseBranch");
        headBranch = requireText(headBranch, "headBranch");
        forkOwner = optionalText(forkOwner);
    }

    public String headReference() {
        if (headBranch.contains(":") || forkOwner == null) {
            return headBranch;
        }

        return forkOwner + ":" + headBranch;
    }

    public String comparePath() {
        return baseBranch + "..." + headReference();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }

        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
