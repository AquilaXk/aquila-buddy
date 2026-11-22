package com.aquilabuddy.domain.prdraft;

import java.time.Instant;

/**
 * PR 초안 상세 화면에서 시간순으로 보여줄 단일 workflow 단계입니다.
 */
public record PullRequestDraftTimelineItem(
    String key,
    String label,
    String description,
    String detail,
    PullRequestDraftTimelineStatus status,
    Instant occurredAt
) {
}
