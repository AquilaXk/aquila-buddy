package com.aquilabuddy.adapter.out.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PullRequestDraftAnalysisContextPersistenceAdapterTest {

    @Test
    void loadsAnalysisContextFromStoredIssueGuide() {
        FakeFindRecommendedIssuePort issuePort = new FakeFindRecommendedIssuePort(recommendedIssue());
        FakeLoadIssueGuidePort guidePort = new FakeLoadIssueGuidePort(issueGuide());
        PullRequestDraftAnalysisContextPersistenceAdapter adapter =
            new PullRequestDraftAnalysisContextPersistenceAdapter(issuePort, guidePort);

        PullRequestDraftAnalysisContext context = adapter.loadAnalysisContext(request());

        assertThat(issuePort.receivedRepoFullName).isEqualTo("spring-projects/spring-boot");
        assertThat(issuePort.receivedIssueNumber).isEqualTo(2);
        assertThat(guidePort.receivedIssueId).isEqualTo("issue-2");
        assertThat(context.guideline()).isEqualTo("Follow the validation guide.");
        assertThat(context.pseudoCode()).isEqualTo("if invalid then return a helpful message");
        assertThat(context.sideEffects()).containsExactly("Update documentation examples.");
    }

    @Test
    void returnsEmptyContextWhenRecommendedIssueIsMissing() {
        FakeLoadIssueGuidePort guidePort = new FakeLoadIssueGuidePort(issueGuide());
        PullRequestDraftAnalysisContextPersistenceAdapter adapter =
            new PullRequestDraftAnalysisContextPersistenceAdapter(new FakeFindRecommendedIssuePort(null), guidePort);

        PullRequestDraftAnalysisContext context = adapter.loadAnalysisContext(request());

        assertThat(context.hasAnalysis()).isFalse();
        assertThat(guidePort.receivedIssueId).isNull();
    }

    @Test
    void returnsEmptyContextWhenGuideIsMissing() {
        PullRequestDraftAnalysisContextPersistenceAdapter adapter =
            new PullRequestDraftAnalysisContextPersistenceAdapter(
                new FakeFindRecommendedIssuePort(recommendedIssue()),
                new FakeLoadIssueGuidePort(null)
            );

        PullRequestDraftAnalysisContext context = adapter.loadAnalysisContext(request());

        assertThat(context.hasAnalysis()).isFalse();
    }

    private static PullRequestDraftRequest request() {
        return new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation"
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

    private static IssueGuide issueGuide() {
        return new IssueGuide(
            "issue-2",
            "Follow the validation guide.",
            "if invalid then return a helpful message",
            List.of("Update documentation examples.")
        );
    }

    private static class FakeFindRecommendedIssuePort implements FindRecommendedIssuePort {

        private final RecommendedIssue issue;
        private String receivedRepoFullName;
        private long receivedIssueNumber;

        private FakeFindRecommendedIssuePort(RecommendedIssue issue) {
            this.issue = issue;
        }

        @Override
        public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
            receivedRepoFullName = repoFullName;
            receivedIssueNumber = issueNumber;
            return Optional.ofNullable(issue);
        }
    }

    private static class FakeLoadIssueGuidePort implements LoadIssueGuidePort {

        private final IssueGuide guide;
        private String receivedIssueId;

        private FakeLoadIssueGuidePort(IssueGuide guide) {
            this.guide = guide;
        }

        @Override
        public IssueGuide loadIssueGuide(String issueId) {
            receivedIssueId = issueId;
            if (guide == null) {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            }
            return guide;
        }
    }
}
