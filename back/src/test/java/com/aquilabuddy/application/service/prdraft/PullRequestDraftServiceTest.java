package com.aquilabuddy.application.service.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.prdraft.DeletePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftDiffPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftAnalysisContextPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftHistoryPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftRepositoryGuideContextPort;
import com.aquilabuddy.application.port.out.prdraft.LoadPullRequestDraftTranslationPort;
import com.aquilabuddy.application.port.out.prdraft.SavePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.UpdatePullRequestDraftPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftContributionReport;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryPage;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryQuery;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
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
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            savePort,
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
        );

        StoredPullRequestDraft draft = service.create(request);

        assertThat(savePort.savedRequest.upstreamRepo()).isEqualTo(request.upstreamRepo());
        assertThat(savePort.savedRequest.repositoryGuideContext().hasAnyGuide()).isTrue();
        assertThat(savePort.savedDraft.title()).isEqualTo("generated title");
        assertThat(draft.id()).isEqualTo("stored-1");
        assertThat(draft.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(draft.title()).isEqualTo("generated title");
        assertThat(draft.body()).contains("spring-projects/spring-boot");
    }

    @Test
    void resolvesDiffBeforeGeneratingAndSavingWhenRequestHasNoDiffContent() {
        FakeLoadPullRequestDraftDiffPort diffPort = new FakeLoadPullRequestDraftDiffPort();
        FakeGeneratePullRequestDraftPort generatePort = new FakeGeneratePullRequestDraftPort();
        FakeSavePullRequestDraftPort savePort = new FakeSavePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            generatePort,
            diffPort,
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            savePort,
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            null
        );

        StoredPullRequestDraft draft = service.create(request);

        assertThat(diffPort.receivedRequest).isEqualTo(request);
        assertThat(generatePort.generatedRequest.diffContent())
            .isEqualTo("diff --git a/docs.adoc b/docs.adoc\n+Loaded from GitHub compare");
        assertThat(savePort.savedRequest).isEqualTo(generatePort.generatedRequest);
        assertThat(draft.body()).contains("spring-projects/spring-boot");
    }

    @Test
    void keepsExplicitDiffContentWithoutResolvingFromGitHub() {
        FakeLoadPullRequestDraftDiffPort diffPort = new FakeLoadPullRequestDraftDiffPort();
        FakeGeneratePullRequestDraftPort generatePort = new FakeGeneratePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            generatePort,
            diffPort,
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Provided by client"
        );

        service.create(request);

        assertThat(diffPort.receivedRequest).isNull();
        assertThat(generatePort.generatedRequest.diffContent()).isEqualTo(request.diffContent());
        assertThat(generatePort.generatedRequest.repositoryGuideContext().hasAnyGuide()).isTrue();
    }

    @Test
    void resolvesRepositoryGuideContextBeforeGeneratingAndSaving() {
        FakeGeneratePullRequestDraftPort generatePort = new FakeGeneratePullRequestDraftPort();
        FakeLoadPullRequestDraftRepositoryGuideContextPort guideContextPort =
            new FakeLoadPullRequestDraftRepositoryGuideContextPort();
        FakeSavePullRequestDraftPort savePort = new FakeSavePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            generatePort,
            new FakeLoadPullRequestDraftDiffPort(),
            guideContextPort,
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            savePort,
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Provided by client"
        );

        service.create(request);

        assertThat(guideContextPort.receivedRequest).isEqualTo(request);
        assertThat(generatePort.generatedRequest.repositoryGuideContext().contributing())
            .isEqualTo("Please run tests before opening a PR.");
        assertThat(savePort.savedRequest).isEqualTo(generatePort.generatedRequest);
    }

    @Test
    void resolvesStoredIssueContextBeforeGeneratingAndSaving() {
        FakeFindRecommendedIssuePort issuePort = new FakeFindRecommendedIssuePort();
        FakeGeneratePullRequestDraftPort generatePort = new FakeGeneratePullRequestDraftPort();
        FakeSavePullRequestDraftPort savePort = new FakeSavePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            generatePort,
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            issuePort,
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            savePort,
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Provided by client"
        );

        service.create(request);

        assertThat(issuePort.receivedRepoFullName).isEqualTo("spring-projects/spring-boot");
        assertThat(issuePort.receivedIssueNumber).isEqualTo(2);
        assertThat(generatePort.generatedRequest.sourceIssueId()).isEqualTo("issue-2");
        assertThat(generatePort.generatedRequest.issueContext().title()).isEqualTo("Fix validation message");
        assertThat(generatePort.generatedRequest.issueContext().summary())
            .isEqualTo("Error message should explain invalid input.");
        assertThat(generatePort.generatedRequest.issueContext().reason()).isEqualTo("Matches Java validation topic");
        assertThat(savePort.savedRequest).isEqualTo(generatePort.generatedRequest);
    }

    @Test
    void resolvesAnalysisContextBeforeGeneratingAndSaving() {
        FakeLoadPullRequestDraftAnalysisContextPort analysisContextPort =
            new FakeLoadPullRequestDraftAnalysisContextPort();
        FakeGeneratePullRequestDraftPort generatePort = new FakeGeneratePullRequestDraftPort();
        FakeSavePullRequestDraftPort savePort = new FakeSavePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            generatePort,
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            analysisContextPort,
            savePort,
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftRequest request = new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Provided by client"
        );

        service.create(request);

        assertThat(analysisContextPort.receivedRequest.issueContext().title()).isEqualTo("Fix validation message");
        assertThat(generatePort.generatedRequest.analysisContext().guideline())
            .isEqualTo("Follow the validation guide.");
        assertThat(generatePort.generatedRequest.analysisContext().pseudoCode())
            .isEqualTo("if invalid then return a helpful message");
        assertThat(savePort.savedRequest).isEqualTo(generatePort.generatedRequest);
    }

    @Test
    void listsRecentStoredDrafts() {
        FakeLoadPullRequestDraftPort loadPort = new FakeLoadPullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            loadPort,
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
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
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            loadPort,
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        Optional<StoredPullRequestDraft> draft = service.getPullRequestDraft("stored-1");

        assertThat(draft).isPresent();
        assertThat(draft.get().title()).isEqualTo("generated title");
    }

    @Test
    void getsContributionReportWithTranslationSummary() {
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        Optional<PullRequestDraftContributionReport> report = service.getContributionReport("stored-1");

        assertThat(report).isPresent();
        assertThat(report.get().draftId()).isEqualTo("stored-1");
        assertThat(report.get().readinessReadyCount()).isEqualTo(2);
        assertThat(report.get().readinessTotalCount()).isEqualTo(4);
        assertThat(report.get().translationCount()).isEqualTo(1);
        assertThat(report.get().latestTranslation().translationId()).isEqualTo("translation-1");
    }

    @Test
    void updatesStoredDraftTitleAndBody() {
        FakeUpdatePullRequestDraftPort updatePort = new FakeUpdatePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            updatePort,
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftUpdateRequest request = new PullRequestDraftUpdateRequest(
            "docs: update contributor guide",
            "## Summary\n- Update contributor guide"
        );

        Optional<StoredPullRequestDraft> updated = service.updatePullRequestDraft("stored-1", request);

        assertThat(updatePort.updatedId).isEqualTo("stored-1");
        assertThat(updatePort.updatedRequest).isEqualTo(request);
        assertThat(updated).isPresent();
        assertThat(updated.get().title()).isEqualTo("docs: update contributor guide");
        assertThat(updated.get().body()).contains("Update contributor guide");
    }

    @Test
    void deletesStoredDraft() {
        FakeDeletePullRequestDraftPort deletePort = new FakeDeletePullRequestDraftPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            new FakeLoadPullRequestDraftPort(),
            new FakeLoadPullRequestDraftHistoryPort(),
            new FakeUpdatePullRequestDraftPort(),
            deletePort,
            new FakeLoadPullRequestDraftTranslationPort()
        );

        boolean deleted = service.deletePullRequestDraft("stored-1");

        assertThat(deleted).isTrue();
        assertThat(deletePort.deletedId).isEqualTo("stored-1");
    }

    @Test
    void listsDraftHistoryPage() {
        FakeLoadPullRequestDraftHistoryPort historyPort = new FakeLoadPullRequestDraftHistoryPort();
        PullRequestDraftService service = new PullRequestDraftService(
            new FakeGeneratePullRequestDraftPort(),
            new FakeLoadPullRequestDraftDiffPort(),
            new FakeLoadPullRequestDraftRepositoryGuideContextPort(),
            new FakeFindRecommendedIssuePort(),
            new FakeLoadPullRequestDraftAnalysisContextPort(),
            new FakeSavePullRequestDraftPort(),
            new FakeLoadPullRequestDraftPort(),
            historyPort,
            new FakeUpdatePullRequestDraftPort(),
            new FakeDeletePullRequestDraftPort(),
            new FakeLoadPullRequestDraftTranslationPort()
        );

        PullRequestDraftHistoryPage page = service.listPullRequestDraftHistory(
            new PullRequestDraftHistoryQuery(1, 2)
        );

        assertThat(historyPort.receivedQuery).isEqualTo(new PullRequestDraftHistoryQuery(1, 2));
        assertThat(page.content())
            .extracting(StoredPullRequestDraft::id)
            .containsExactly("stored-1");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
    }

    private static class FakeGeneratePullRequestDraftPort implements GeneratePullRequestDraftPort {

        private PullRequestDraftRequest generatedRequest;

        @Override
        public PullRequestDraft generate(PullRequestDraftRequest request) {
            generatedRequest = request;
            return new PullRequestDraft("generated title", "body for " + request.upstreamRepo());
        }
    }

    private static class FakeLoadPullRequestDraftRepositoryGuideContextPort
        implements LoadPullRequestDraftRepositoryGuideContextPort {

        private PullRequestDraftRequest receivedRequest;

        @Override
        public PullRequestDraftRepositoryGuideContext loadRepositoryGuideContext(PullRequestDraftRequest request) {
            receivedRequest = request;
            return new PullRequestDraftRepositoryGuideContext(
                "Please run tests before opening a PR.",
                "## Summary\n- Describe changes",
                List.of()
            );
        }
    }

    private static class FakeFindRecommendedIssuePort implements FindRecommendedIssuePort {

        private final RecommendedIssue issue;
        private String receivedRepoFullName;
        private long receivedIssueNumber;

        private FakeFindRecommendedIssuePort() {
            this(recommendedIssue());
        }

        private FakeFindRecommendedIssuePort(RecommendedIssue issue) {
            this.issue = issue;
        }

        @Override
        public Optional<RecommendedIssue> findById(String id) {
            return Optional.ofNullable(issue)
                .filter(candidate -> candidate.id().equals(id));
        }

        @Override
        public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
            receivedRepoFullName = repoFullName;
            receivedIssueNumber = issueNumber;
            return Optional.ofNullable(issue);
        }
    }

    private static class FakeLoadPullRequestDraftAnalysisContextPort implements LoadPullRequestDraftAnalysisContextPort {

        private PullRequestDraftRequest receivedRequest;

        @Override
        public PullRequestDraftAnalysisContext loadAnalysisContext(PullRequestDraftRequest request) {
            receivedRequest = request;
            return new PullRequestDraftAnalysisContext(
                "Follow the validation guide.",
                "if invalid then return a helpful message",
                List.of("Update documentation examples.")
            );
        }
    }

    private static class FakeLoadPullRequestDraftDiffPort implements LoadPullRequestDraftDiffPort {

        private PullRequestDraftRequest receivedRequest;

        @Override
        public String loadDiff(PullRequestDraftRequest request) {
            receivedRequest = request;
            return "diff --git a/docs.adoc b/docs.adoc\n+Loaded from GitHub compare";
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

    private static class FakeLoadPullRequestDraftHistoryPort implements LoadPullRequestDraftHistoryPort {

        private PullRequestDraftHistoryQuery receivedQuery;

        @Override
        public PullRequestDraftHistoryPage loadPullRequestDraftHistory(PullRequestDraftHistoryQuery query) {
            receivedQuery = query;
            return new PullRequestDraftHistoryPage(
                List.of(storedDraft(
                    new PullRequestDraftRequest("spring-projects/spring-boot", 2, "main", "feat/validation-docs", "diff"),
                    new PullRequestDraft("generated title", "body for spring-projects/spring-boot")
                )),
                query.page(),
                query.size(),
                3,
                2,
                false,
                true
            );
        }
    }

    private static class FakeUpdatePullRequestDraftPort implements UpdatePullRequestDraftPort {

        private String updatedId;
        private PullRequestDraftUpdateRequest updatedRequest;

        @Override
        public Optional<StoredPullRequestDraft> updatePullRequestDraft(
            String id,
            PullRequestDraftUpdateRequest request
        ) {
            updatedId = id;
            updatedRequest = request;
            return Optional.of(storedDraft(
                new PullRequestDraftRequest("spring-projects/spring-boot", 2, "main", "feat/validation-docs", "diff"),
                new PullRequestDraft(request.title(), request.body())
            ));
        }
    }

    private static class FakeDeletePullRequestDraftPort implements DeletePullRequestDraftPort {

        private String deletedId;

        @Override
        public boolean deletePullRequestDraft(String id) {
            deletedId = id;
            return "stored-1".equals(id);
        }
    }

    private static class FakeLoadPullRequestDraftTranslationPort implements LoadPullRequestDraftTranslationPort {

        @Override
        public List<PullRequestDraftTranslation> loadPullRequestDraftTranslations(String draftId) {
            return List.of(new PullRequestDraftTranslation(
                "translation-1",
                draftId,
                "en",
                "translated title",
                "translated body",
                Instant.parse("2026-05-15T02:00:00Z")
            ));
        }

        @Override
        public Optional<PullRequestDraftTranslation> loadPullRequestDraftTranslation(String id) {
            return loadPullRequestDraftTranslations("stored-1")
                .stream()
                .filter(translation -> translation.id().equals(id))
                .findFirst();
        }

        @Override
        public List<PullRequestDraftTranslation> loadRecentPullRequestDraftTranslations(int limit) {
            return loadPullRequestDraftTranslations("stored-1")
                .stream()
                .limit(limit)
                .toList();
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

    private static RecommendedIssue recommendedIssue() {
        return new RecommendedIssue(
            "issue-2",
            "spring-projects/spring-boot",
            2,
            "Fix validation message",
            "Error message should explain invalid input.",
            0.91,
            List.of("good first issue"),
            "OPEN",
            "Matches Java validation topic"
        );
    }
}
