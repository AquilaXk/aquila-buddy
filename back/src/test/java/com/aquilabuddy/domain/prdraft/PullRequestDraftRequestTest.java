package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestDraftRequestTest {

    @Test
    void allowsMissingDiffContentForServerSideResolution() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            " spring-projects/spring-boot ",
            2,
            " main ",
            " feat/validation-docs ",
            null
        );

        assertThat(request.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(request.baseBranch()).isEqualTo("main");
        assertThat(request.headBranch()).isEqualTo("feat/validation-docs");
        assertThat(request.diffContent()).isNull();
        assertThat(request.hasDiffContent()).isFalse();
        assertThat(request.issueContext().hasIssue()).isFalse();
        assertThat(request.analysisContext().hasAnalysis()).isFalse();
    }

    @Test
    void keepsExplicitDiffContentWhenProvided() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            " diff --git a/docs.adoc b/docs.adoc\n+Add validation example "
        );

        assertThat(request.diffContent()).isEqualTo("diff --git a/docs.adoc b/docs.adoc\n+Add validation example");
        assertThat(request.hasDiffContent()).isTrue();
    }

    @Test
    void normalizesOptionalSourceIssueId() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff",
            " issue-demo-1 "
        );

        assertThat(request.sourceIssueId()).isEqualTo("issue-demo-1");
    }

    @Test
    void createsCopyWithResolvedDiffContent() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            null
        );

        PullRequestDraftRequest resolved = request.withDiffContent(" diff --git a/docs.adoc b/docs.adoc\n+Add validation ");

        assertThat(resolved.diffContent()).isEqualTo("diff --git a/docs.adoc b/docs.adoc\n+Add validation");
        assertThat(resolved.hasDiffContent()).isTrue();
        assertThat(resolved.issueContext().hasIssue()).isFalse();
        assertThat(resolved.analysisContext().hasAnalysis()).isFalse();
    }

    @Test
    void createsCopyWithIssueContext() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation"
        );

        PullRequestDraftRequest resolved = request.withIssueContext(new PullRequestDraftIssueContext(
            "Fix validation message",
            "Error message should explain invalid input.",
            "Matches Java validation topic"
        ));

        assertThat(resolved.diffContent()).isEqualTo(request.diffContent());
        assertThat(resolved.repositoryGuideContext()).isEqualTo(request.repositoryGuideContext());
        assertThat(resolved.analysisContext()).isEqualTo(request.analysisContext());
        assertThat(resolved.issueContext().title()).isEqualTo("Fix validation message");
    }

    @Test
    void createsCopyWithAnalysisContext() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation",
            PullRequestDraftRepositoryGuideContext.empty(),
            new PullRequestDraftIssueContext(
                "Fix validation message",
                "Error message should explain invalid input.",
                "Matches Java validation topic"
            )
        );

        PullRequestDraftRequest resolved = request.withAnalysisContext(new PullRequestDraftAnalysisContext(
            "Follow the validation guide.",
            "if invalid then return a helpful message",
            List.of("Update documentation examples.")
        ));

        assertThat(resolved.diffContent()).isEqualTo(request.diffContent());
        assertThat(resolved.repositoryGuideContext()).isEqualTo(request.repositoryGuideContext());
        assertThat(resolved.issueContext()).isEqualTo(request.issueContext());
        assertThat(resolved.analysisContext().guideline()).isEqualTo("Follow the validation guide.");
    }

    @Test
    void rejectsBlankResolvedDiffContent() {
        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            null
        );

        assertThatThrownBy(() -> request.withDiffContent(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("diffContent");
    }

    @Test
    void rejectsOversizedRequestFieldsFailClosed() {
        assertThatThrownBy(() -> new PullRequestDraftRequest(
            "a".repeat(256),
            2,
            "main",
            "feat/validation-docs",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("upstreamRepo");

        assertThatThrownBy(() -> new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "b".repeat(256),
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("headBranch");

        assertThatThrownBy(() -> new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "c".repeat(12_001)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("diffContent");

        assertThatThrownBy(() -> new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            null,
            "d".repeat(101)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceIssueId");
    }

    @Test
    void allowsForkOwnerColonInHeadBranchAndRejectsTraversalOrUriMetaCharacters() {
        PullRequestDraftRequest validForkRequest = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "aquila-dev:feat/validation-docs",
            null
        );
        assertThat(validForkRequest.headBranch()).isEqualTo("aquila-dev:feat/validation-docs");

        for (String badRepo : List.of("a/b/../../evil", "owner/repo?x=1", "owner/{repo}", "owner/..")) {
            assertThatThrownBy(() -> new PullRequestDraftRequest(badRepo, 2, "main", "feat/docs", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upstreamRepo");
        }

        for (String badBase : List.of("main/../evil", "aquila-dev:main", "main?x=1", "main{var}")) {
            assertThatThrownBy(() -> new PullRequestDraftRequest("owner/repo", 2, badBase, "feat/docs", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseBranch");
        }

        for (String badHead : List.of("x:y/../../../../other/repo", "a:b:c", "feat/{var}", "feat/branch.lock")) {
            assertThatThrownBy(() -> new PullRequestDraftRequest("owner/repo", 2, "main", badHead, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headBranch");
        }
    }
}
