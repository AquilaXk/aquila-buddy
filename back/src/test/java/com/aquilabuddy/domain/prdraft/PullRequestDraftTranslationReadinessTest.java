package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 영문 번역 결과를 제출 전 점검할 readiness checklist로 변환하는지 검증합니다.
 */
class PullRequestDraftTranslationReadinessTest {

    @Test
    void createsReadinessFromStoredDraftAndTranslation() {
        PullRequestDraftTranslationReadiness readiness = PullRequestDraftTranslationReadiness.from(
            storedDraft(),
            readyTranslation()
        );

        assertThat(readiness.draftId()).isEqualTo("draft-1");
        assertThat(readiness.translationId()).isEqualTo("translation-1");
        assertThat(readiness.targetLanguage()).isEqualTo("en");
        assertThat(readiness.sourceIssueId()).isEqualTo("issue-123");
        assertThat(readiness.readyCount()).isEqualTo(5);
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::key)
            .containsExactly("target-language", "branch", "title", "body", "test-plan");
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsOnly(PullRequestDraftReadinessStatus.READY);
        assertThat(readiness.items().get(1).detail())
            .isEqualTo("spring-projects/spring-boot #123 / main -> feat/readme-fix");
    }

    @Test
    void marksActionRequiredWhenTranslationNeedsReview() {
        PullRequestDraftTranslationReadiness readiness = PullRequestDraftTranslationReadiness.from(
            storedDraftWithSameBranch(),
            translationWithoutTestPlan()
        );

        assertThat(readiness.readyCount()).isEqualTo(3);
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::key, PullRequestDraftReadinessItem::status)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("target-language", PullRequestDraftReadinessStatus.READY),
                org.assertj.core.groups.Tuple.tuple("branch", PullRequestDraftReadinessStatus.ACTION_REQUIRED),
                org.assertj.core.groups.Tuple.tuple("title", PullRequestDraftReadinessStatus.READY),
                org.assertj.core.groups.Tuple.tuple("body", PullRequestDraftReadinessStatus.READY),
                org.assertj.core.groups.Tuple.tuple("test-plan", PullRequestDraftReadinessStatus.ACTION_REQUIRED)
            );
    }

    @Test
    void rejectsTranslationFromDifferentDraft() {
        PullRequestDraftTranslation otherDraftTranslation = new PullRequestDraftTranslation(
            "translation-1",
            "other-draft",
            "en",
            "docs: Prepare update",
            "## Summary\n- Prepare the update.\n\n## Test Plan\n- Run translated tests.",
            Instant.parse("2026-05-15T04:00:00Z")
        );

        assertThatThrownBy(() -> PullRequestDraftTranslationReadiness.from(storedDraft(), otherDraftTranslation))
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

    private static StoredPullRequestDraft storedDraftWithSameBranch() {
        return new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "main",
            "[Docs] README 기여 안내 보완",
            "## 변경 사항\n- README 안내를 보완합니다.",
            Instant.parse("2026-05-15T01:00:00Z")
        );
    }

    private static PullRequestDraftTranslation readyTranslation() {
        return new PullRequestDraftTranslation(
            "translation-1",
            "draft-1",
            "en",
            "docs: Prepare update",
            "## Summary\n- Prepare the update.\n\n## Test Plan\n- Run translated tests.",
            Instant.parse("2026-05-15T04:00:00Z")
        );
    }

    private static PullRequestDraftTranslation translationWithoutTestPlan() {
        return new PullRequestDraftTranslation(
            "translation-1",
            "draft-1",
            "en",
            "docs: Prepare update",
            "## Summary\n- Prepare the update.\n\n## Notes\n- Review manually.",
            Instant.parse("2026-05-15T04:00:00Z")
        );
    }
}
