package com.aquilabuddy.domain.prdraft;

/**
 * 자동 PR 초안 생성 흐름에서 사용할 제목과 본문 값을 담는 도메인 모델입니다.
 */
public record PullRequestDraft(
    String title,
    String body
) {
}
