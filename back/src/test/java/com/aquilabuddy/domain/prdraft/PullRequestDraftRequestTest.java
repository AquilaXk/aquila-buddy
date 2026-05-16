package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
