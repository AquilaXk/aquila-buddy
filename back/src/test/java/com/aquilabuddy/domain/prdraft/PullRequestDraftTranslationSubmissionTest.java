package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 영문 번역 결과를 실제 PR 제출 화면에 옮길 복사 항목으로 변환하는지 검증합니다.
 */
class PullRequestDraftTranslationSubmissionTest {

    @Test
    void createsSubmissionBundleFromStoredDraftAndTranslation() {
        PullRequestDraftTranslationSubmission submission = PullRequestDraftTranslationSubmission.from(
            storedDraft(),
            translation()
        );

        assertThat(submission.draftId()).isEqualTo("draft-1");
        assertThat(submission.translationId()).isEqualTo("translation-1");
        assertThat(submission.targetLanguage()).isEqualTo("en");
        assertThat(submission.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(submission.githubIssueNumber()).isEqualTo(123);
        assertThat(submission.sourceIssueId()).isEqualTo("issue-123");
        assertThat(submission.baseBranch()).isEqualTo("main");
        assertThat(submission.headBranch()).isEqualTo("feat/readme-fix");
        assertThat(submission.items())
            .extracting(PullRequestDraftSubmissionItem::key)
            .containsExactly("title", "body", "branch", "test-plan");
        assertThat(submission.items().get(0).value()).isEqualTo("docs: Prepare update");
        assertThat(submission.items().get(2).value()).isEqualTo("base: main\nhead: feat/readme-fix");
        assertThat(submission.items().get(3).value()).isEqualTo("- Run translated tests.");
    }

    @Test
    void rejectsTranslationFromDifferentDraft() {
        PullRequestDraftTranslation otherDraftTranslation = new PullRequestDraftTranslation(
            "translation-1",
            "other-draft",
            "en",
            "docs: Prepare update",
            "## Test Plan\n- Run translated tests.",
            Instant.parse("2026-05-15T04:00:00Z")
        );

        assertThatThrownBy(() -> PullRequestDraftTranslationSubmission.from(storedDraft(), otherDraftTranslation))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("translation must belong to draft");
    }

    private static StoredPullRequestDraft storedDraft() {
        return new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "[Docs] README 기여 안내 보완",
            "## 변경 사항\n- README 안내를 보완합니다.",
            "issue-123",
            Instant.parse("2026-05-15T01:00:00Z")
        );
    }

    private static PullRequestDraftTranslation translation() {
        return new PullRequestDraftTranslation(
            "translation-1",
            "draft-1",
            "en",
            "docs: Prepare update",
            "## Summary\n- Prepare the update.\n\n## Test Plan\n- Run translated tests.",
            Instant.parse("2026-05-15T04:00:00Z")
        );
    }
}
