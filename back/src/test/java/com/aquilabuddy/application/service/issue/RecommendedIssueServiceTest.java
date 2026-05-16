package com.aquilabuddy.application.service.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecommendedIssueServiceTest {

    @Test
    void returnsSearchedIssuesBeforeStoredFallback() {
        RecommendedIssue searched = issue("github-search", "spring-projects/spring-boot", 101);
        CountingLoadRecommendedIssuesPort fallbackPort = new CountingLoadRecommendedIssuesPort(
            List.of(issue("stored", "vercel/next.js", 1))
        );
        RecommendedIssueService service = new RecommendedIssueService(
            fallbackPort,
            fallbackPort,
            new InMemoryFindRecommendedIssuePort(List.of()),
            new RecordingSaveRecommendedIssuesPort(),
            () -> List.of(searched)
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(searched);
        assertThat(fallbackPort.callCount()).isZero();
    }

    @Test
    void savesSearchedIssuesBeforeReturningThem() {
        RecommendedIssue searched = issue("github-search", "spring-projects/spring-boot", 101);
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            savePort,
            () -> List.of(searched)
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(searched);
        assertThat(savePort.savedIssues()).containsExactly(searched);
    }

    @Test
    void crawlsIssuesBySearchQueryAndSavesResult() {
        RecommendedIssue searched = issue("github-search", "spring-projects/spring-boot", 101);
        RecordingSearchRecommendedIssuesPort searchPort = new RecordingSearchRecommendedIssuesPort(List.of(searched));
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            savePort,
            searchPort
        );

        List<RecommendedIssue> result = service.crawlSearchIssues("language:java label:\"good first issue\"");

        assertThat(result).containsExactly(searched);
        assertThat(searchPort.receivedQuery()).isEqualTo("language:java label:\"good first issue\"");
        assertThat(savePort.savedIssues()).containsExactly(searched);
    }

    @Test
    void fallsBackToStoredIssuesWhenSearchReturnsEmpty() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            List::of
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(stored);
    }

    @Test
    void fallsBackToStoredIssuesWhenSearchFails() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            failingSearchPort()
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(stored);
    }

    @Test
    void returnsStoredIssuesAsRecommendationHistory() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            List::of
        );

        List<RecommendedIssueHistory> result = service.history();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("stored");
        assertThat(result.getFirst().repoFullName()).isEqualTo("vercel/next.js");
        assertThat(result.getFirst().issueNumber()).isEqualTo(1);
        assertThat(result.getFirst().labels()).containsExactly("good first issue");
        assertThat(result.getFirst().isAnalyzed()).isFalse();
        assertThat(result.getFirst().analysisResultId()).isNull();
    }

    @Test
    void listsCollectedIssuesWithoutCallingSearch() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        CountingLoadRecommendedIssuesPort loadPort = new CountingLoadRecommendedIssuesPort(List.of(stored));
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            loadPort,
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            failingSearchPort()
        );

        List<RecommendedIssue> result = service.listCollectedIssues();

        assertThat(result).containsExactly(stored);
        assertThat(loadPort.callCount()).isEqualTo(1);
    }

    @Test
    void looksUpStoredIssueByRepositoryAndIssueNumber() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            List::of
        );

        Optional<RecommendedIssue> result = service.lookup("vercel/next.js", 1);

        assertThat(result).contains(stored);
    }

    private static SearchRecommendedIssuesPort failingSearchPort() {
        return () -> {
            throw new IllegalStateException("GitHub unavailable");
        };
    }

    private static final class RecordingSearchRecommendedIssuesPort implements SearchRecommendedIssuesPort {

        private final List<RecommendedIssue> issues;
        private String receivedQuery;

        private RecordingSearchRecommendedIssuesPort(List<RecommendedIssue> issues) {
            this.issues = issues;
        }

        @Override
        public List<RecommendedIssue> searchRecommendedIssues() {
            return issues;
        }

        @Override
        public List<RecommendedIssue> searchRecommendedIssues(String query) {
            receivedQuery = query;
            return issues;
        }

        String receivedQuery() {
            return receivedQuery;
        }
    }

    private static RecommendedIssue issue(String id, String repoFullName, long issueNumber) {
        return new RecommendedIssue(
            id,
            repoFullName,
            issueNumber,
            "Improve contributor guide",
            "Good first issue 후보입니다.",
            0.9,
            List.of("good first issue"),
            "OPEN",
            "GitHub Search 후보입니다."
        );
    }

    private static final class CountingLoadRecommendedIssuesPort implements
        LoadRecommendedIssuesPort,
        LoadCollectedIssuesPort {

        private final List<RecommendedIssue> issues;
        private int callCount;

        private CountingLoadRecommendedIssuesPort(List<RecommendedIssue> issues) {
            this.issues = issues;
        }

        @Override
        public List<RecommendedIssue> loadRecommendedIssues() {
            callCount++;
            return issues;
        }

        @Override
        public List<RecommendedIssue> loadCollectedIssues() {
            callCount++;
            return issues;
        }

        int callCount() {
            return callCount;
        }
    }

    private static final class InMemoryFindRecommendedIssuePort implements FindRecommendedIssuePort {

        private final List<RecommendedIssue> issues;

        private InMemoryFindRecommendedIssuePort(List<RecommendedIssue> issues) {
            this.issues = issues;
        }

        @Override
        public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
            return issues.stream()
                .filter(issue -> issue.repoFullName().equals(repoFullName) && issue.issueNumber() == issueNumber)
                .findFirst();
        }
    }

    private static final class RecordingSaveRecommendedIssuesPort implements SaveRecommendedIssuesPort {

        private List<RecommendedIssue> savedIssues = List.of();

        @Override
        public void saveRecommendedIssues(List<RecommendedIssue> issues) {
            savedIssues = List.copyOf(issues);
        }

        List<RecommendedIssue> savedIssues() {
            return savedIssues;
        }
    }
}
