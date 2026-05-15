package com.aquilabuddy.domain.issue;

import java.util.List;

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
