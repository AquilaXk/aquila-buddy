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
    String reason,
    String copyOutputVersion
) {

    public static final String CURRENT_COPY_OUTPUT_VERSION = "recommended-issue-card-copy-v2026-05-21";

    public RecommendedIssue(
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
        this(
            id,
            repoFullName,
            issueNumber,
            title,
            summary,
            score,
            labels,
            status,
            reason,
            CURRENT_COPY_OUTPUT_VERSION
        );
    }

    public RecommendedIssue {
        labels = labels == null ? List.of() : List.copyOf(labels);
        copyOutputVersion = normalizeCopyOutputVersion(copyOutputVersion);
    }

    public RecommendedIssue withCurrentCopyOutputVersion() {
        return new RecommendedIssue(
            id,
            repoFullName,
            issueNumber,
            title,
            summary,
            score,
            labels,
            status,
            reason,
            CURRENT_COPY_OUTPUT_VERSION
        );
    }

    private static String normalizeCopyOutputVersion(String value) {
        if (value == null || value.trim().isBlank()) {
            return "legacy";
        }
        return value.trim();
    }
}
