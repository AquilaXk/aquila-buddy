package com.aquilabuddy.application.service.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 application service가 생성/저장/조회 port를 조합하는지 검증합니다.
 */
class PullRequestDraftServiceTest {

    @Test
    void createsAndStoresGeneratedDraft() {
        FakeSavePullRequestDraftPort savePort = new FakeSavePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            savePort,
            new FakeLoadPullRequestDraftPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
        );

        StoredPullRequestDraft draft = service.create(request);

        assertThat(savePort.savedRequest).isEqualTo(request);
        assertThat(savePort.savedDraft.title()).isEqualTo("generated title");
        assertThat(draft.id()).isEqualTo("stored-1");
        assertThat(draft.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(draft.title()).isEqualTo("generated title");
        assertThat(draft.body()).contains("spring-projects/spring-boot");
    }

    @Test
    void listsRecentStoredDrafts() {
        FakeLoadPullRequestDraftPort loadPort = new FakeLoadPullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeSavePullRequestDraftPort(),
            loadPort
        );

        List<StoredPullRequestDraft> drafts = service.listRecentPullRequestDrafts();

        assertThat(drafts)
            .extracting(StoredPullRequestDraft::id)
            .containsExactly("stored-1");
    }

    @Test
    void getsStoredDraftById() {
        FakeLoadPullRequestDraftPort loadPort = new FakeLoadPullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeSavePullRequestDraftPort(),
            loadPort
        );

        Optional<StoredPullRequestDraft> draft = service.getPullRequestDraft("stored-1");

        assertThat(draft).isPresent();
        assertThat(draft.get().title()).isEqualTo("generated title");
    }

    private static class FakeGeneratePullRequestDraftPort implements GeneratePullRequestDraftPort {

        @Override
        public PullRequestDraft generate(PullRequestDraftRequest request) {
            return new PullRequestDraft("generated title", "body for " + request.upstreamRepo());
        }
    }

    private static class FakeSavePullRequestDraftPort implements SavePullRequestDraftPort {

        private PullRequestDraftRequest savedRequest;
        private PullRequestDraft savedDraft;

        @Override
        public StoredPullRequestDraft savePullRequestDraft(PullRequestDraftRequest request, PullRequestDraft draft) {
            savedRequest = request;
            savedDraft = draft;
            return storedDraft(request, draft);
        }
    }

    private static class FakeLoadPullRequestDraftPort implements LoadPullRequestDraftPort {

        @Override
        public List<StoredPullRequestDraft> loadRecentPullRequestDrafts() {
            return List.of(storedDraft(
                new PullRequestDraftRequest("spring-projects/spring-boot", 2, "main", "feat/validation-docs", "diff"),
                new PullRequestDraft("generated title", "body for spring-projects/spring-boot")
            ));
        }

        @Override
        public Optional<StoredPullRequestDraft> loadPullRequestDraft(String id) {
            return loadRecentPullRequestDrafts()
                .stream()
                .filter(draft -> draft.id().equals(id))
                .findFirst();
        }
    }

    private static StoredPullRequestDraft storedDraft(PullRequestDraftRequest request, PullRequestDraft draft) {
        return new StoredPullRequestDraft(
            "stored-1",
            request.upstreamRepo(),
            request.githubIssueNumber(),
            request.baseBranch(),
            request.headBranch(),
            draft.title(),
            draft.body(),
            Instant.parse("2026-05-15T00:00:00Z")
        );
    }
}
