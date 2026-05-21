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

    @Test
    void derivesRequiredDraftFormatFromPullRequestTemplate() {
        PullRequestDraftRepositoryGuideContext context = new PullRequestDraftRepositoryGuideContext(
            null,
            """
            ## Summary
            Describe the user-visible change.

            ## Checklist
            - [ ] Tests added or updated
            - [ ] Documentation updated
            """,
            List.of()
        );

        PullRequestDraftRepositoryFormat format = context.draftFormat();

        assertThat(format.source()).isEqualTo(PullRequestDraftRepositoryFormat.Source.TEMPLATE);
        assertThat(format.requiredHeadings()).containsExactly("## Summary", "## Checklist");
        assertThat(format.requiredCheckboxes()).containsExactly(
            "- [ ] Tests added or updated",
            "- [ ] Documentation updated"
        );
        assertThat(format.promptLines())
            .contains("Repository draft format source: PR template")
            .contains("Required PR headings:")
            .contains("- ## Checklist")
            .contains("Required PR checkboxes:")
            .contains("- - [ ] Tests added or updated");
        assertThat(format.isSatisfiedBy("## Summary\n- Done")).isFalse();
        assertThat(format.isSatisfiedBy("""
            ## Summary
            - Done

            ## Checklist
            - [ ] Tests added or updated
            - [ ] Documentation updated
            """)).isTrue();
    }

    @Test
    void infersCommonDraftFormatFromMergedPullRequestSamplesWhenTemplateIsMissing() {
        PullRequestDraftRepositoryGuideContext context = new PullRequestDraftRepositoryGuideContext(
            null,
            null,
            List.of(
                "## What changed\n- Improve docs\n\n## Test plan\n- Run tests",
                "## What changed\n- Fix validation\n\n## Test plan\n- Run focused tests\n\n## Notes\n- Extra",
                "## What changed\n- Add example\n\n## Test plan\n- Run check"
            )
        );

        PullRequestDraftRepositoryFormat format = context.draftFormat();

        assertThat(format.source()).isEqualTo(PullRequestDraftRepositoryFormat.Source.MERGED_PULL_REQUEST_SAMPLES);
        assertThat(format.requiredHeadings()).containsExactly("## What changed", "## Test plan");
        assertThat(format.requiredCheckboxes()).isEmpty();
        assertThat(format.promptLines())
            .contains("Repository draft format source: merged PR samples")
            .contains("- ## What changed")
            .contains("- ## Test plan");
        assertThat(format.isSatisfiedBy("## What changed\n- Done")).isFalse();
        assertThat(format.isSatisfiedBy("## What changed\n- Done\n\n## Test plan\n- ./gradlew check")).isTrue();
    }

    @Test
    void exposesGenericFallbackReasonWhenMergedPullRequestSamplesDoNotShareHeadings() {
        PullRequestDraftRepositoryGuideContext context = new PullRequestDraftRepositoryGuideContext(
            null,
            null,
            List.of(
                "## Summary\n- Improve docs",
                "## Details\n- Fix validation"
            )
        );

        PullRequestDraftRepositoryFormat format = context.draftFormat();

        assertThat(format.source()).isEqualTo(PullRequestDraftRepositoryFormat.Source.GENERIC);
        assertThat(format.fallbackReason()).contains("merged PR samples did not share common headings");
        assertThat(format.promptLines())
            .contains("Repository draft format source: generic fallback")
            .contains("Repository draft format fallback reason: merged PR samples did not share common headings");
        assertThat(format.isSatisfiedBy("any body")).isTrue();
    }
}
