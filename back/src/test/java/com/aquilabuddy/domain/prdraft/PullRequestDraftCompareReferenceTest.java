package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PullRequestDraftCompareReferenceTest {

    @Test
    void buildsComparePathWithForkOwnerWhenHeadBranchHasNoOwner() {
        PullRequestDraftCompareReference reference = new PullRequestDraftCompareReference(
            " main ",
            " feat/readme-fix ",
            " aquila-dev "
        );

        assertThat(reference.baseBranch()).isEqualTo("main");
        assertThat(reference.headBranch()).isEqualTo("feat/readme-fix");
        assertThat(reference.forkOwner()).isEqualTo("aquila-dev");
        assertThat(reference.headReference()).isEqualTo("aquila-dev:feat/readme-fix");
        assertThat(reference.comparePath()).isEqualTo("main...aquila-dev:feat/readme-fix");
    }

    @Test
    void keepsOwnerQualifiedHeadBranchWithoutAddingForkOwnerTwice() {
        PullRequestDraftCompareReference reference = new PullRequestDraftCompareReference(
            "main",
            "external-dev:feat/readme-fix",
            "aquila-dev"
        );

        assertThat(reference.headReference()).isEqualTo("external-dev:feat/readme-fix");
        assertThat(reference.comparePath()).isEqualTo("main...external-dev:feat/readme-fix");
    }

    @Test
    void fallsBackToHeadBranchWhenForkOwnerIsBlank() {
        PullRequestDraftCompareReference reference = new PullRequestDraftCompareReference(
            "main",
            "feat/readme-fix",
            " "
        );

        assertThat(reference.forkOwner()).isNull();
        assertThat(reference.headReference()).isEqualTo("feat/readme-fix");
        assertThat(reference.comparePath()).isEqualTo("main...feat/readme-fix");
    }

    @Test
    void rejectsBlankBranches() {
        assertThatThrownBy(() -> new PullRequestDraftCompareReference("", "feat/readme-fix", "aquila-dev"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("baseBranch");

        assertThatThrownBy(() -> new PullRequestDraftCompareReference("main", "", "aquila-dev"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("headBranch");
    }
}
