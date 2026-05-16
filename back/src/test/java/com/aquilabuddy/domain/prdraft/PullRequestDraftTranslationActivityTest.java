package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 번역 활동이 dashboard에 필요한 draft context를 보존하는지 검증합니다.
 */
class PullRequestDraftTranslationActivityTest {

    @Test
    void createsActivityWithDraftContext() {
        StoredPullRequestDraft draft = storedDraft();
        PullRequestDraftTranslation translation = translation("draft-1");

        PullRequestDraftTranslationActivity activity = PullRequestDraftTranslationActivity.from(draft, translation);

        assertThat(activity.draftId()).isEqualTo("draft-1");
        assertThat(activity.translationId()).isEqualTo("translation-1");
        assertThat(activity.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(activity.githubIssueNumber()).isEqualTo(123);
        assertThat(activity.targetLanguage()).isEqualTo("en");
        assertThat(activity.title()).isEqualTo("translated title");
        assertThat(activity.createdAt()).isEqualTo(Instant.parse("2026-05-15T04:00:00Z"));
    }

    @Test
    void rejectsTranslationFromDifferentDraft() {
        StoredPullRequestDraft draft = storedDraft();
        PullRequestDraftTranslation translation = translation("other-draft");

        assertThatThrownBy(() -> PullRequestDraftTranslationActivity.from(draft, translation))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("draftId");
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
            Instant.parse("2026-05-15T01:00:00Z")
        );
    }

    private static PullRequestDraftTranslation translation(String draftId) {
        return new PullRequestDraftTranslation(
            "translation-1",
            draftId,
            "en",
            "translated title",
            "translated body",
            Instant.parse("2026-05-15T04:00:00Z")
        );
    }
}
