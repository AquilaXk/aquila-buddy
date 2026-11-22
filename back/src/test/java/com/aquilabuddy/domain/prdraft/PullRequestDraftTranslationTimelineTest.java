package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftTranslationTimelineTest {

    @Test
    void createsTimelineFromReadyTranslation() {
        PullRequestDraftTranslationTimeline timeline = PullRequestDraftTranslationTimeline.from(
            storedDraft(),
            readyTranslation()
        );

        assertThat(timeline.draftId()).isEqualTo("draft-1");
        assertThat(timeline.translationId()).isEqualTo("translation-1");
        assertThat(timeline.sourceIssueId()).isEqualTo("issue-123");
        assertThat(timeline.items())
            .extracting(PullRequestDraftTimelineItem::key)
            .containsExactly("translation-created", "translation-ready", "readiness-reviewed", "submission-prepared");
        assertThat(timeline.items())
            .extracting(PullRequestDraftTimelineItem::status)
            .containsExactly(
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.READY
            );
        assertThat(timeline.items().get(0).detail()).contains("en");
        assertThat(timeline.items().get(2).detail()).isEqualTo("5/5 translation readiness items ready");
        assertThat(timeline.items().get(3).occurredAt()).isEqualTo(Instant.parse("2026-05-15T04:00:00Z"));
    }

    @Test
    void marksReadinessAndSubmissionAttentionWhenTranslationNeedsReview() {
        PullRequestDraftTranslationTimeline timeline = PullRequestDraftTranslationTimeline.from(
            storedDraftWithSameBranch(),
            translationWithoutTestPlan()
        );

        assertThat(timeline.items().get(2).status()).isEqualTo(PullRequestDraftTimelineStatus.NEEDS_ATTENTION);
        assertThat(timeline.items().get(2).detail()).isEqualTo("3/5 translation readiness items ready");
        assertThat(timeline.items().get(3).status()).isEqualTo(PullRequestDraftTimelineStatus.NEEDS_ATTENTION);
        assertThat(timeline.items().get(3).detail()).contains("translation readiness 항목을 먼저 확인해 주세요.");
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

        assertThatThrownBy(() -> PullRequestDraftTranslationTimeline.from(storedDraft(), otherDraftTranslation))
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
