package com.aquilabuddy.application.service.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 번역 application service가 원본 초안 조회, 번역, 저장 port를 조합하는지 검증합니다.
 */
class PullRequestDraftTranslationServiceTest {

    @Test
    void createsEnglishTranslationForStoredDraft() {
        FakeLoadDraftPort loadDraftPort = new FakeLoadDraftPort(storedDraft());
        FakeTranslatePullRequestDraftPort translatePort = new FakeTranslatePullRequestDraftPort();
        FakeSaveTranslationPort savePort = new FakeSaveTranslationPort();
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            loadDraftPort,
            translatePort,
            savePort,
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslation> translation = service.createTranslation("draft-1", "EN");

        assertThat(translation).isPresent();
        assertThat(translatePort.receivedDraft).isEqualTo(storedDraft());
        assertThat(translatePort.receivedTargetLanguage).isEqualTo("en");
        assertThat(savePort.savedDraftId).isEqualTo("draft-1");
        assertThat(savePort.savedTargetLanguage).isEqualTo("en");
        assertThat(translation.get().title()).isEqualTo("translated title");
    }

    @Test
    void returnsEmptyWhenSourceDraftDoesNotExist() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(null),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslation> translation = service.createTranslation("missing", "en");

        assertThat(translation).isEmpty();
    }

    @Test
    void listsStoredTranslationsByDraftId() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        List<PullRequestDraftTranslation> translations = service.listTranslations("draft-1");

        assertThat(translations)
            .extracting(PullRequestDraftTranslation::id)
            .containsExactly("translation-1");
    }

    private static class FakeLoadDraftPort implements LoadPullRequestDraftPort {

        private final StoredPullRequestDraft draft;

        private FakeLoadDraftPort(StoredPullRequestDraft draft) {
            this.draft = draft;
        }

        @Override
        public List<StoredPullRequestDraft> loadRecentPullRequestDrafts() {
            return draft == null ? List.of() : List.of(draft);
        }

        @Override
        public Optional<StoredPullRequestDraft> loadPullRequestDraft(String id) {
            return draft == null || !draft.id().equals(id) ? Optional.empty() : Optional.of(draft);
        }
    }

    private static class FakeTranslatePullRequestDraftPort implements TranslatePullRequestDraftPort {

        private StoredPullRequestDraft receivedDraft;
        private String receivedTargetLanguage;

        @Override
        public PullRequestDraft translate(StoredPullRequestDraft draft, String targetLanguage) {
            receivedDraft = draft;
            receivedTargetLanguage = targetLanguage;
            return new PullRequestDraft("translated title", "translated body");
        }
    }

    private static class FakeSaveTranslationPort implements SavePullRequestDraftTranslationPort {

        private String savedDraftId;
        private String savedTargetLanguage;

        @Override
        public PullRequestDraftTranslation savePullRequestDraftTranslation(
            String draftId,
            String targetLanguage,
            PullRequestDraft translatedDraft
        ) {
            savedDraftId = draftId;
            savedTargetLanguage = targetLanguage;
            return new PullRequestDraftTranslation(
                "translation-1",
                draftId,
                targetLanguage,
                translatedDraft.title(),
                translatedDraft.body(),
                Instant.parse("2026-05-15T04:00:00Z")
            );
        }
    }

    private static class FakeLoadTranslationPort implements LoadPullRequestDraftTranslationPort {

        @Override
        public List<PullRequestDraftTranslation> loadPullRequestDraftTranslations(String draftId) {
            return List.of(new PullRequestDraftTranslation(
                "translation-1",
                draftId,
                "en",
                "translated title",
                "translated body",
                Instant.parse("2026-05-15T04:00:00Z")
            ));
        }

        @Override
        public Optional<PullRequestDraftTranslation> loadPullRequestDraftTranslation(String id) {
            return loadPullRequestDraftTranslations("draft-1")
                .stream()
                .filter(translation -> translation.id().equals(id))
                .findFirst();
        }
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
