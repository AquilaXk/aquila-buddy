package com.aquilabuddy.domain.issue;

import java.time.Instant;

/**
 * 사용자별 추천 이슈 북마크 및 분석 상태 도메인 모델입니다.
 */
public record UserIssueState(
    String issueId,
    boolean bookmarked,
    Instant bookmarkedAt,
    boolean analyzed,
    Instant analyzedAt
) {
}
