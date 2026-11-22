package com.aquilabuddy.domain.prdraft;

import java.util.Objects;

/**
 * 저장된 PR 초안의 제출용 제목과 본문을 수정하기 위한 도메인 요청 모델입니다.
 */
public record PullRequestDraftUpdateRequest(
    String title,
    String body
) {

    public PullRequestDraftUpdateRequest {
        title = requireText(title, "title");
        body = requireText(body, "body");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
