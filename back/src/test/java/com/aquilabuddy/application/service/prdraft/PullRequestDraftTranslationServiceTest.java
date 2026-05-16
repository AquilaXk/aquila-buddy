package com.aquilabuddy.application.service.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftReadinessItem;
import com.aquilabuddy.domain.prdraft.PullRequestDraftSubmissionItem;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslateResult;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationSubmission;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTimelineItem;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationTimeline;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationActivity;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import com.aquilabuddy.domain.user.UserProfile;
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
            new FakeLoadUserProfilePort("aquila-dev"),
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
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslation> translation = service.createTranslation("missing", "en");

        assertThat(translation).isEmpty();
    }

    @Test
    void translatesStoredDraftForCompatibilityAction() {
        FakeLoadDraftPort loadDraftPort = new FakeLoadDraftPort(storedDraft());
        FakeTranslatePullRequestDraftPort translatePort = new FakeTranslatePullRequestDraftPort();
        FakeSaveTranslationPort savePort = new FakeSaveTranslationPort();
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            loadDraftPort,
            new FakeLoadUserProfilePort("aquila-dev"),
            translatePort,
            savePort,
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslateResult> result = service.translate("draft-1");

        assertThat(result).isPresent();
        assertThat(translatePort.receivedDraft).isEqualTo(storedDraft());
        assertThat(translatePort.receivedTargetLanguage).isEqualTo("en");
        assertThat(savePort.savedDraftId).isEqualTo("draft-1");
        assertThat(savePort.savedTargetLanguage).isEqualTo("en");
        assertThat(result.get().titleEn()).isEqualTo("translated title");
        assertThat(result.get().bodyEn()).isEqualTo("translated body");
        assertThat(result.get().githubUrl())
            .contains("https://github.com/spring-projects/spring-boot/compare/main...aquila-dev:feat/readme-fix")
            .contains("title=translated%20title")
            .contains("body=translated%20body");
    }

    @Test
    void returnsEmptyWhenCompatibilityTranslationDraftDoesNotExist() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(null),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslateResult> result = service.translate("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void listsStoredTranslationsByDraftId() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        List<PullRequestDraftTranslation> translations = service.listTranslations("draft-1");

        assertThat(translations)
            .extracting(PullRequestDraftTranslation::id)
            .containsExactly("translation-1");
    }

    @Test
    void returnsTranslationSubmissionBundleWhenTranslationBelongsToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationSubmission> submission = service.getTranslationSubmission(
            "draft-1",
            "translation-1"
        );

        assertThat(submission).isPresent();
        assertThat(submission.get().draftId()).isEqualTo("draft-1");
        assertThat(submission.get().translationId()).isEqualTo("translation-1");
        assertThat(submission.get().items())
            .extracting(PullRequestDraftSubmissionItem::key)
            .containsExactly("title", "body", "branch", "test-plan");
    }

    @Test
    void returnsEmptyWhenTranslationDoesNotBelongToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationSubmission> submission = service.getTranslationSubmission(
            "other-draft",
            "translation-1"
        );

        assertThat(submission).isEmpty();
    }

    @Test
    void returnsTranslationReadinessWhenTranslationBelongsToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationReadiness> readiness = service.getTranslationReadiness(
            "draft-1",
            "translation-1"
        );

        assertThat(readiness).isPresent();
        assertThat(readiness.get().draftId()).isEqualTo("draft-1");
        assertThat(readiness.get().translationId()).isEqualTo("translation-1");
        assertThat(readiness.get().readyCount()).isEqualTo(5);
        assertThat(readiness.get().items())
            .extracting(PullRequestDraftReadinessItem::key)
            .containsExactly("target-language", "branch", "title", "body", "test-plan");
    }

    @Test
    void returnsEmptyWhenTranslationReadinessDoesNotBelongToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationReadiness> readiness = service.getTranslationReadiness(
            "other-draft",
            "translation-1"
        );

        assertThat(readiness).isEmpty();
    }

    @Test
    void returnsTranslationTimelineWhenTranslationBelongsToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationTimeline> timeline = service.getTranslationTimeline(
            "draft-1",
            "translation-1"
        );

        assertThat(timeline).isPresent();
        assertThat(timeline.get().draftId()).isEqualTo("draft-1");
        assertThat(timeline.get().translationId()).isEqualTo("translation-1");
        assertThat(timeline.get().items())
            .extracting(PullRequestDraftTimelineItem::key)
            .containsExactly("translation-created", "translation-ready", "readiness-reviewed", "submission-prepared");
    }

    @Test
    void returnsEmptyWhenTranslationTimelineDoesNotBelongToDraft() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        Optional<PullRequestDraftTranslationTimeline> timeline = service.getTranslationTimeline(
            "other-draft",
            "translation-1"
        );

        assertThat(timeline).isEmpty();
    }

    @Test
    void listsRecentTranslationActivitiesWithDraftContext() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(storedDraft()),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        List<PullRequestDraftTranslationActivity> activities = service.listRecentTranslationActivities(4);

        assertThat(activities)
            .extracting(PullRequestDraftTranslationActivity::translationId)
            .containsExactly("translation-1");
        assertThat(activities.get(0).draftId()).isEqualTo("draft-1");
        assertThat(activities.get(0).upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(activities.get(0).githubIssueNumber()).isEqualTo(123);
        assertThat(activities.get(0).targetLanguage()).isEqualTo("en");
        assertThat(activities.get(0).title()).isEqualTo("translated title");
    }

    @Test
    void skipsRecentTranslationActivityWhenDraftContextIsMissing() {
        PullRequestDraftTranslationService service = new PullRequestDraftTranslationService(
            new FakeLoadDraftPort(null),
            new FakeLoadUserProfilePort("aquila-dev"),
            new FakeTranslatePullRequestDraftPort(),
            new FakeSaveTranslationPort(),
            new FakeLoadTranslationPort()
        );

        List<PullRequestDraftTranslationActivity> activities = service.listRecentTranslationActivities(4);

        assertThat(activities).isEmpty();
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

    private static class FakeLoadUserProfilePort implements LoadUserProfilePort {

        private final String login;

        private FakeLoadUserProfilePort(String login) {
            this.login = login;
        }

        @Override
        public UserProfile loadCurrentUserProfile() {
            return new UserProfile(
                10001L,
                login,
                "Aquila Developer",
                "aquila@example.com",
                List.of("Java"),
                List.of("spring"),
                true,
                "2026-05-16T00:00:00Z"
            );
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
                "## Summary\n- translated body\n\n## Test Plan\n- Run translated tests.",
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

        @Override
        public List<PullRequestDraftTranslation> loadRecentPullRequestDraftTranslations(int limit) {
            return loadPullRequestDraftTranslations("draft-1")
                .stream()
                .limit(limit)
                .toList();
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
