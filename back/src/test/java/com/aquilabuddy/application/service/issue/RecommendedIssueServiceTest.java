package com.aquilabuddy.application.service.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
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
            savePort,
            () -> List.of(searched)
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(searched);
        assertThat(savePort.savedIssues()).containsExactly(searched);
    }

    @Test
    void fallsBackToStoredIssuesWhenSearchReturnsEmpty() {
        RecommendedIssue stored = issue("stored", "vercel/next.js", 1);
        RecommendedIssueService service = new RecommendedIssueService(
            new CountingLoadRecommendedIssuesPort(List.of(stored)),
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
            new RecordingSaveRecommendedIssuesPort(),
            failingSearchPort()
        );

        List<RecommendedIssue> result = service.recommend();

        assertThat(result).containsExactly(stored);
    }

    private static SearchRecommendedIssuesPort failingSearchPort() {
        return () -> {
            throw new IllegalStateException("GitHub unavailable");
        };
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

    private static final class CountingLoadRecommendedIssuesPort implements LoadRecommendedIssuesPort {

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

        int callCount() {
            return callCount;
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
