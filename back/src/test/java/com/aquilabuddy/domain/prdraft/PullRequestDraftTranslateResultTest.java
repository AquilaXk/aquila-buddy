package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftTranslateResultTest {

    @Test
    void createsTranslateResultWithPrefilledGithubUrl() {
        PullRequestDraftTranslateResult result = PullRequestDraftTranslateResult.from(storedDraft(), translation());

        assertThat(result.titleEn()).isEqualTo("docs: Prepare contributor guide");
        assertThat(result.bodyEn()).isEqualTo("## Summary\n- Prepare contributor guide.");
        assertThat(result.githubUrl())
            .isEqualTo(
                "https://github.com/spring-projects/spring-boot/compare/main...feat/readme-fix"
                    + "?quick_pull=1&title=docs%3A%20Prepare%20contributor%20guide"
                    + "&body=%23%23%20Summary%0A-%20Prepare%20contributor%20guide."
            );
    }

    @Test
    void rejectsTranslationFromDifferentDraft() {
        PullRequestDraftTranslation otherDraftTranslation = new PullRequestDraftTranslation(
            "translation-1",
            "other-draft",
            "en",
            "docs: Prepare contributor guide",
            "## Summary\n- Prepare contributor guide.",
            Instant.parse("2026-05-16T03:00:00Z")
        );

        assertThatThrownBy(() -> PullRequestDraftTranslateResult.from(storedDraft(), otherDraftTranslation))
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
            Instant.parse("2026-05-16T01:00:00Z")
        );
    }

    private static PullRequestDraftTranslation translation() {
        return new PullRequestDraftTranslation(
            "translation-1",
            "draft-1",
            "en",
            "docs: Prepare contributor guide",
            "## Summary\n- Prepare contributor guide.",
            Instant.parse("2026-05-16T03:00:00Z")
        );
    }
}
