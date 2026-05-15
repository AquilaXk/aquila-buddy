package com.aquilabuddy.domain.issue;

import java.util.List;

/**
 * 추천 목록 화면과 API 응답에서 사용하는 이슈 추천 도메인 모델입니다.
 */
public record RecommendedIssue(
    String id,
    String repoFullName,
    long issueNumber,
    String title,
    String summary,
    double score,
    List<String> labels,
    String status,
    String reason
) {
}
