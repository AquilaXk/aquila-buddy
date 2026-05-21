package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftReadinessTest {

    @Test
    void createsReadyChecklistFromCompleteDraft() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 변경 초안",
            """
                ## 변경 사항
                - validation 예시를 보강합니다.

                ## 작업 메모
                - diff 기반으로 초안을 구성했습니다.

                ## 테스트
                - 관련 테스트를 실행해 주세요.
                """,
            "issue-2",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.draftId()).isEqualTo("draft-1");
        assertThat(readiness.sourceIssueId()).isEqualTo("issue-2");
        assertThat(readiness.readyCount()).isEqualTo(4);
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::key)
            .containsExactly("metadata", "title", "body", "test-plan");
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsOnly(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void recognizesEnglishTestsHeadingAsTestPlan() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-english-tests",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            """
                ## Summary
                - Improve validation docs.

                ## Tests
                - ./gradlew check
                """,
            "issue-2",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.readyCount()).isEqualTo(4);
        assertThat(readiness.items().get(3).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void marksChecklistItemsActionRequiredWhenDraftNeedsReview() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-2",
            "spring-projects/spring-boot",
            2,
            "main",
            "main",
            "x".repeat(121),
            "본문만 있는 초안입니다.",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.readyCount()).isZero();
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsExactly(
                PullRequestDraftReadinessStatus.ACTION_REQUIRED,
                PullRequestDraftReadinessStatus.ACTION_REQUIRED,
                PullRequestDraftReadinessStatus.ACTION_REQUIRED,
                PullRequestDraftReadinessStatus.ACTION_REQUIRED
            );
        assertThat(readiness.items().get(0).detail()).contains("base/head branch");
        assertThat(readiness.items().get(1).detail()).contains("120자");
        assertThat(readiness.items().get(2).detail()).contains("section");
        assertThat(readiness.items().get(3).detail()).contains("테스트");
    }
}
