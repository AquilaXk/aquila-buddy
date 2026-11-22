package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftSubmissionTest {

    @Test
    void createsSubmissionBundleFromStoredDraft() {
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
                - ./back/gradlew -p back check
                - npm --prefix front run build
                """,
            "issue-2",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftSubmission submission = PullRequestDraftSubmission.from(draft);

        assertThat(submission.draftId()).isEqualTo("draft-1");
        assertThat(submission.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(submission.githubIssueNumber()).isEqualTo(2);
        assertThat(submission.sourceIssueId()).isEqualTo("issue-2");
        assertThat(submission.items())
            .extracting(PullRequestDraftSubmissionItem::key)
            .containsExactly("title", "body", "branch", "test-plan");
        assertThat(submission.items().get(0).value()).isEqualTo("docs: spring-projects/spring-boot#2 변경 초안");
        assertThat(submission.items().get(2).value()).isEqualTo("base: main\nhead: feat/validation-docs");
        assertThat(submission.items().get(3).value())
            .isEqualTo("- ./back/gradlew -p back check\n- npm --prefix front run build");
    }

    @Test
    void usesFallbackTestPlanWhenBodyHasNoTestSection() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-2",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 변경 초안",
            "## 변경 사항\n- validation 예시를 보강합니다.",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftSubmission submission = PullRequestDraftSubmission.from(draft);

        assertThat(submission.items().get(3).value()).contains("PR 본문에 테스트 계획을 추가해 주세요.");
    }
}
