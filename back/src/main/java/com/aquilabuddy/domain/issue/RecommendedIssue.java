package com.aquilabuddy.domain.issue;

public record RecommendedIssue(
    String repository,
    int number,
    String title,
    String reason
) {
}
