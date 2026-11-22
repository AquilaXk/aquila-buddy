package com.aquilabuddy.adapter.out.persistence.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * PR 초안 번역 persistence adapter가 번역 결과를 초안 ID별로 저장하고 조회하는지 검증합니다.
 */
@DataJpaTest
@Import(PullRequestDraftTranslationPersistenceAdapter.class)
class PullRequestDraftTranslationPersistenceAdapterTest {

    @Autowired
    private PullRequestDraftTranslationPersistenceAdapter adapter;

    @Autowired
    private PullRequestDraftTranslationJpaRepository translationRepository;

    @Autowired
    private PullRequestDraftJpaRepository draftRepository;

    @BeforeEach
    void setUp() {
        translationRepository.deleteAll();
        draftRepository.deleteAll();
        draftRepository.save(new PullRequestDraftJpaEntity(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "[Docs] README 기여 안내 보완",
            "close #123",
            Instant.parse("2026-05-15T01:00:00Z")
        ));
    }

    @Test
    void savesTranslatedDraftWithDraftContext() {
        PullRequestDraft translatedDraft = new PullRequestDraft(
            "docs: Update README contribution guide",
            "## Summary\n- Update the README contribution guide."
        );

        PullRequestDraftTranslation translation = adapter.savePullRequestDraftTranslation(
            "draft-1",
            "en",
            translatedDraft
        );

        assertThat(translation.id()).isNotBlank();
        assertThat(translation.draftId()).isEqualTo("draft-1");
        assertThat(translation.targetLanguage()).isEqualTo("en");
        assertThat(translation.title()).isEqualTo("docs: Update README contribution guide");
        assertThat(translation.body()).contains("## Summary");
        assertThat(translation.createdAt()).isNotNull();

        assertThat(adapter.loadPullRequestDraftTranslation(translation.id()))
            .get()
            .usingRecursiveComparison()
            .isEqualTo(translation);
    }

    @Test
    void loadsTranslationsForDraftNewestFirst() {
        translationRepository.save(new PullRequestDraftTranslationJpaEntity(
            "older",
            "draft-1",
            "en",
            "older title",
            "older body",
            Instant.parse("2026-05-15T02:00:00Z")
        ));
        translationRepository.save(new PullRequestDraftTranslationJpaEntity(
            "newer",
            "draft-1",
            "en",
            "newer title",
            "newer body",
            Instant.parse("2026-05-15T03:00:00Z")
        ));

        List<PullRequestDraftTranslation> translations = adapter.loadPullRequestDraftTranslations("draft-1");

        assertThat(translations)
            .extracting(PullRequestDraftTranslation::id)
            .containsExactly("newer", "older");
    }

    @Test
    void loadsRecentTranslationsNewestFirstWithLimit() {
        translationRepository.save(new PullRequestDraftTranslationJpaEntity(
            "older",
            "draft-1",
            "en",
            "older title",
            "older body",
            Instant.parse("2026-05-15T02:00:00Z")
        ));
        translationRepository.save(new PullRequestDraftTranslationJpaEntity(
            "newer",
            "draft-1",
            "en",
            "newer title",
            "newer body",
            Instant.parse("2026-05-15T03:00:00Z")
        ));

        List<PullRequestDraftTranslation> translations = adapter.loadRecentPullRequestDraftTranslations(1);

        assertThat(translations)
            .extracting(PullRequestDraftTranslation::id)
            .containsExactly("newer");
    }
}
