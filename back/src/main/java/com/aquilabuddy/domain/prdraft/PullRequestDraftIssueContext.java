package com.aquilabuddy.domain.prdraft;

import java.util.List;
import java.util.stream.Stream;

/**
 * PR 초안 작성에 참고할 연결 이슈 context입니다.
 */
public record PullRequestDraftIssueContext(
    String title,
    String summary,
    String reason
) {

    public PullRequestDraftIssueContext {
        title = optionalText(title);
        summary = optionalText(summary);
        reason = optionalText(reason);
    }

    public static PullRequestDraftIssueContext empty() {
        return new PullRequestDraftIssueContext(null, null, null);
    }

    public boolean hasIssue() {
        return title != null || summary != null || reason != null;
    }

    public List<String> contextSummaryLines() {
        return Stream.of(
                title == null ? null : "Issue title: " + title,
                summary == null ? null : "Issue summary: " + summary,
                reason == null ? null : "Recommendation reason: " + reason
            )
            .filter(item -> item != null)
            .toList();
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
