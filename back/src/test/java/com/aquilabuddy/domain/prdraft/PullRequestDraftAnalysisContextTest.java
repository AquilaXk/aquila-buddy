package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestDraftAnalysisContextTest {

    @Test
    void normalizesAnalysisGuideFields() {
        PullRequestDraftAnalysisContext context = new PullRequestDraftAnalysisContext(
            "  Follow the validation guide.  ",
            "  if invalid then return a helpful message  ",
            List.of("  Update documentation examples.  ", " ", "Review affected tests.")
        );

        assertThat(context.guideline()).isEqualTo("Follow the validation guide.");
        assertThat(context.pseudoCode()).isEqualTo("if invalid then return a helpful message");
        assertThat(context.sideEffects()).containsExactly("Update documentation examples.", "Review affected tests.");
        assertThat(context.hasAnalysis()).isTrue();
        assertThat(context.contextSummaryLines()).containsExactly(
            "Guideline: Follow the validation guide.",
            "Pseudo code: if invalid then return a helpful message",
            "Side effect: Update documentation examples.",
            "Side effect: Review affected tests."
        );
    }

    @Test
    void treatsBlankValuesAsEmptyContext() {
        PullRequestDraftAnalysisContext context = new PullRequestDraftAnalysisContext(" ", "\n", null);

        assertThat(context.guideline()).isNull();
        assertThat(context.pseudoCode()).isNull();
        assertThat(context.sideEffects()).isEmpty();
        assertThat(context.hasAnalysis()).isFalse();
        assertThat(context.contextSummaryLines()).isEmpty();
    }

    @Test
    void createsEmptyContext() {
        PullRequestDraftAnalysisContext context = PullRequestDraftAnalysisContext.empty();

        assertThat(context.hasAnalysis()).isFalse();
        assertThat(context.contextSummaryLines()).isEmpty();
    }
}
