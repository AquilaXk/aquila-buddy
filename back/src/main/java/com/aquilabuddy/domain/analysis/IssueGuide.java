package com.aquilabuddy.domain.analysis;

import java.util.List;

public record IssueGuide(
    String issueKey,
    String guideline,
    String pseudoCode,
    List<String> sideEffects
) {
}
