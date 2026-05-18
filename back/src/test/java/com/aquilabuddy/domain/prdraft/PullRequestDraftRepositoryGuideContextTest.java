package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestDraftRepositoryGuideContextTest {

    @Test
    void exposesEmptyContextWhenRepositoryHasNoGuides() {
        PullRequestDraftRepositoryGuideContext context = PullRequestDraftRepositoryGuideContext.empty();

        assertThat(context.hasAnyGuide()).isFalse();
        assertThat(context.contributing()).isNull();
        assertThat(context.pullRequestTemplate()).isNull();
        assertThat(context.mergedPullRequestSamples()).isEmpty();
        assertThat(context.guideSummaryLines()).isEmpty();
    }

    @Test
    void normalizesGuidesAndLimitsMergedPullRequestSamples() {
        PullRequestDraftRepositoryGuideContext context = new PullRequestDraftRepositoryGuideContext(
            "  Please run tests before opening a PR.  ",
            "  ## Summary\n- Describe changes  ",
            List.of(
                "  ## Summary\n- Improve docs  ",
                "",
                "  ## Summary\n- Fix validation  ",
                "  ## Summary\n- Add examples  ",
                "  ## Summary\n- Refactor API  "
            )
        );

        assertThat(context.hasAnyGuide()).isTrue();
        assertThat(context.contributing()).isEqualTo("Please run tests before opening a PR.");
        assertThat(context.pullRequestTemplate()).isEqualTo("## Summary\n- Describe changes");
        assertThat(context.mergedPullRequestSamples()).containsExactly(
            "## Summary\n- Improve docs",
            "## Summary\n- Fix validation",
            "## Summary\n- Add examples"
        );
        assertThat(context.guideSummaryLines()).containsExactly(
            "CONTRIBUTING: Please run tests before opening a PR.",
            "PR template: ## Summary",
            "Merged PR samples: 3"
        );
    }

    @Test
    void exposesRepositoryGuidePromptLinesWithFullContext() {
        PullRequestDraftRepositoryGuideContext context = new PullRequestDraftRepositoryGuideContext(
            "Please run tests before opening a PR.",
            "## Summary\n- Describe changes\n## Tests\n- List verification",
            List.of(
                "## Summary\n- Improve docs",
                "## Summary\n- Fix validation"
            )
        );

        assertThat(context.guidePromptLines()).containsExactly(
            "CONTRIBUTING:",
            "Please run tests before opening a PR.",
            "PR template:",
            "## Summary\n- Describe changes\n## Tests\n- List verification",
            "Merged PR sample #1:",
            "## Summary\n- Improve docs",
            "Merged PR sample #2:",
            "## Summary\n- Fix validation"
        );
    }
}
