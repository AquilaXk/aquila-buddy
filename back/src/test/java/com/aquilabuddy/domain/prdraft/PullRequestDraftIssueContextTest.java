package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PullRequestDraftIssueContextTest {

    @Test
    void exposesEmptyContextWhenIssueIsMissing() {
        PullRequestDraftIssueContext context = PullRequestDraftIssueContext.empty();

        assertThat(context.hasIssue()).isFalse();
        assertThat(context.title()).isNull();
        assertThat(context.summary()).isNull();
        assertThat(context.reason()).isNull();
        assertThat(context.contextSummaryLines()).isEmpty();
    }

    @Test
    void normalizesIssueContextAndBuildsSummaryLines() {
        PullRequestDraftIssueContext context = new PullRequestDraftIssueContext(
            "  Fix validation message  ",
            "  Error message should explain invalid input.  ",
            "  Matches Java validation topic  "
        );

        assertThat(context.hasIssue()).isTrue();
        assertThat(context.title()).isEqualTo("Fix validation message");
        assertThat(context.summary()).isEqualTo("Error message should explain invalid input.");
        assertThat(context.reason()).isEqualTo("Matches Java validation topic");
        assertThat(context.contextSummaryLines()).containsExactly(
            "Issue title: Fix validation message",
            "Issue summary: Error message should explain invalid input.",
            "Recommendation reason: Matches Java validation topic"
        );
    }

    @Test
    void treatsBlankFieldsAsMissing() {
        PullRequestDraftIssueContext context = new PullRequestDraftIssueContext(" ", "", null);

        assertThat(context.hasIssue()).isFalse();
        assertThat(context.contextSummaryLines()).isEmpty();
    }
}
