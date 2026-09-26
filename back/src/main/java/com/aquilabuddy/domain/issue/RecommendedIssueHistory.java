package com.aquilabuddy.domain.issue;

import java.util.List;

/**
 * 추천 이력 화면과 API 응답에서 사용하는 이슈 추천 이력 도메인 모델입니다.
 */
public record RecommendedIssueHistory(
    String id,
    String repoFullName,
    long issueNumber,
    String title,
    String summary,
    double score,
    List<String> labels,
    String status,
    boolean isAnalyzed,
    String analysisResultId,
    String reason
) {

    public RecommendedIssueHistory(
        String id,
        String repoFullName,
        long issueNumber,
        String title,
        String summary,
        double score,
        List<String> labels,
        String status,
        boolean isAnalyzed,
        String analysisResultId
    ) {
        this(id, repoFullName, issueNumber, title, summary, score, labels, status, isAnalyzed, analysisResultId, "");
    }

    /**
     * 분석 결과 persistence가 연결되기 전까지 저장 추천 이슈를 미분석 이력으로 노출합니다.
     */
    public static RecommendedIssueHistory notAnalyzed(RecommendedIssue issue) {
        return new RecommendedIssueHistory(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            issue.summary(),
            issue.score(),
            issue.labels(),
            issue.status(),
            false,
            null,
            issue.reason()
        );
    }
}

