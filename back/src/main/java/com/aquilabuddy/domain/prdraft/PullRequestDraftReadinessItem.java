package com.aquilabuddy.domain.prdraft;

/**
 * PR 초안을 실제 제출 전에 확인해야 하는 단일 checklist 항목입니다.
 */
public record PullRequestDraftReadinessItem(
    String key,
    String label,
    String description,
    String detail,
    PullRequestDraftReadinessStatus status
) {
}
