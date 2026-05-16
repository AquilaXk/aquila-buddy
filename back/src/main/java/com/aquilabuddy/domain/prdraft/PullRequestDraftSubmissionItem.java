package com.aquilabuddy.domain.prdraft;

/**
 * 실제 PR 제출 직전에 확인하거나 복사할 수 있는 단일 항목입니다.
 */
public record PullRequestDraftSubmissionItem(
    String key,
    String label,
    String description,
    String value
) {
}
