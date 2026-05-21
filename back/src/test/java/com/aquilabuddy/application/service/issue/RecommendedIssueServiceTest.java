package com.aquilabuddy.application.service.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
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
    void ranksSearchedIssuesBeforeReturningThem() {
        RecommendedIssue low = issue("low", "spring-projects/spring-boot", 101);
        RecommendedIssue high = issue("high", "vercel/next.js", 2);
        RecordingRankRecommendedIssuesPort rankPort = new RecordingRankRecommendedIssuesPort(List.of(high, low));
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            new RecordingSaveRecommendedIssuesPort(),
            () -> List.of(low, high),
            rankPort
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(high, low);
        assertThat(rankPort.receivedIssues()).containsExactly(low, high);
    }

    @Test
    void summarizesSearchedIssuesBeforeSavingRankingAndReturning() {
        RecommendedIssue searched = issue(
            "github-search",
            "spring-projects/spring-boot",
            101,
            "Problem Snapshot validates by checking isEmpty(), so whitespace input passes and fails later."
        );
        RecommendedIssue summarized = withSummary(
            searched,
            "템플릿 입력값이 공백만 있어도 검증을 통과해 나중에 Thymeleaf 처리 단계에서 실패하는 문제입니다."
        );
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecordingRankRecommendedIssuesPort rankPort = new RecordingRankRecommendedIssuesPort(List.of(summarized));
        RecordingSummarizeRecommendedIssuePort summaryPort = new RecordingSummarizeRecommendedIssuePort(List.of(summarized));
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            savePort,
            () -> List.of(searched),
            rankPort,
            summaryPort
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(summarized);
        assertThat(summaryPort.receivedIssues()).containsExactly(searched);
        assertThat(savePort.savedIssues()).containsExactly(summarized);
        assertThat(rankPort.receivedIssues()).containsExactly(summarized);
    }

    @Test
    void keepsSearchedIssuesWhenSummaryGenerationFails() {
        RecommendedIssue searched = issue("github-search", "spring-projects/spring-boot", 101, "English source summary.");
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            savePort,
            () -> List.of(searched),
            List::copyOf,
            issues -> {
                throw new IllegalStateException("AI unavailable");
            }
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(searched);
        assertThat(savePort.savedIssues()).containsExactly(searched);
    }

    @Test
    void crawlsIssuesBySearchQueryAndSavesResult() {
        RecommendedIssue searched = issue("github-search", "spring-projects/spring-boot", 101);
        RecommendedIssue summarized = withSummary(searched, "Java 문서 개선을 처음 기여자가 맡을 수 있는 이슈입니다.");
        RecordingSearchRecommendedIssuesPort searchPort = new RecordingSearchRecommendedIssuesPort(List.of(searched));
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecordingSummarizeRecommendedIssuePort summaryPort = new RecordingSummarizeRecommendedIssuePort(List.of(summarized));
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of()),
            new CountingLoadRecommendedIssuesPort(List.of()),
            new InMemoryFindRecommendedIssuePort(List.of()),
            savePort,
            searchPort,
            List::copyOf,
            summaryPort
        );

        List<RecommendedIssue> result = service.crawlSearchIssues("language:java label:\"good first issue\"");

        assertThat(result).containsExactly(summarized);
        assertThat(searchPort.receivedQuery()).isEqualTo("language:java label:\"good first issue\"");
        assertThat(summaryPort.receivedIssues()).containsExactly(searched);
        assertThat(savePort.savedIssues()).containsExactly(summarized);
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
    void ranksStoredFallbackIssuesBeforeReturningThem() {
        RecommendedIssue low = issue("low", "spring-projects/spring-boot", 101);
        RecommendedIssue high = issue("high", "vercel/next.js", 2);
        RecordingRankRecommendedIssuesPort rankPort = new RecordingRankRecommendedIssuesPort(List.of(high, low));
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(low, high)),
            new CountingLoadRecommendedIssuesPort(List.of(low, high)),
            new InMemoryFindRecommendedIssuePort(List.of(low, high)),
            new RecordingSaveRecommendedIssuesPort(),
            List::of,
            rankPort
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(high, low);
        assertThat(rankPort.receivedIssues()).containsExactly(low, high);
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

    @Test
    void getsStoredIssueByInternalId() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
            new InMemoryFindRecommendedIssuePort(List.of(stored)),
            new RecordingSaveRecommendedIssuesPort(),
            List::of
        );

        Optional<RecommendedIssue> result = service.getRecommendedIssue("stored");

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

    private static final class RecordingRankRecommendedIssuesPort implements RankRecommendedIssuesPort {

        private final List<RecommendedIssue> rankedIssues;
        private List<RecommendedIssue> receivedIssues = List.of();

        private RecordingRankRecommendedIssuesPort(List<RecommendedIssue> rankedIssues) {
            this.rankedIssues = rankedIssues;
        }

        @Override
        public List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues) {
            receivedIssues = List.copyOf(issues);
            return rankedIssues;
        }

        List<RecommendedIssue> receivedIssues() {
            return receivedIssues;
        }
    }

    private static RecommendedIssue issue(String id, String repoFullName, long issueNumber) {
        return issue(id, repoFullName, issueNumber, "Good first issue 후보입니다.");
    }

    private static RecommendedIssue issue(String id, String repoFullName, long issueNumber, String summary) {
        return new RecommendedIssue(
            id,
            repoFullName,
            issueNumber,
            "Improve contributor guide",
            summary,
            0.9,
            List.of("good first issue"),
            "OPEN",
            "GitHub Search 후보입니다."
        );
    }

    private static RecommendedIssue withSummary(RecommendedIssue issue, String summary) {
        return new RecommendedIssue(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            summary,
            issue.score(),
            issue.labels(),
            issue.status(),
            issue.reason()
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
        public Optional<RecommendedIssue> findById(String id) {
            return issues.stream()
                .filter(issue -> issue.id().equals(id))
                .findFirst();
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

    private static final class RecordingSummarizeRecommendedIssuePort implements SummarizeRecommendedIssuePort {

        private final List<RecommendedIssue> summarizedIssues;
        private List<RecommendedIssue> receivedIssues = List.of();

        private RecordingSummarizeRecommendedIssuePort(List<RecommendedIssue> summarizedIssues) {
            this.summarizedIssues = summarizedIssues;
        }

        @Override
        public List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues) {
            receivedIssues = List.copyOf(issues);
            return summarizedIssues;
        }

        List<RecommendedIssue> receivedIssues() {
            return receivedIssues;
        }
    }
}
