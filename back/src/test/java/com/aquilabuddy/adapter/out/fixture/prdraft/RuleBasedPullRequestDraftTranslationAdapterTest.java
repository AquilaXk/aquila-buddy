package com.aquilabuddy.adapter.out.fixture.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 규칙 기반 PR 초안 번역 adapter가 저장 초안 맥락으로 영문 PR 초안을 생성하는지 검증합니다.
 */
class RuleBasedPullRequestDraftTranslationAdapterTest {

    private final RuleBasedPullRequestDraftTranslationAdapter adapter =
        new RuleBasedPullRequestDraftTranslationAdapter();

    @Test
    void translatesStoredDraftToEnglishDraft() {
        PullRequestDraft translated = adapter.translate(storedDraft(), "en");

        assertThat(translated.title()).isEqualTo("docs: Prepare spring-projects/spring-boot#123 update");
        assertThat(translated.body()).contains("## Summary");
        assertThat(translated.body()).contains("spring-projects/spring-boot #123");
        assertThat(translated.body()).contains("main -> feat/readme-fix");
        assertThat(translated.body()).contains("Source draft title: [Docs] README 기여 안내 보완");
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> adapter.translate(storedDraft(), "ko"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targetLanguage");
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
}
