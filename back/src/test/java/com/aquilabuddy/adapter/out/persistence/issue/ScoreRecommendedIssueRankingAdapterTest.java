package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreRecommendedIssueRankingAdapterTest {

    @Test
    void preservesInputOrderWhenUnauthenticated() {
        RecommendedIssue first = new RecommendedIssue(
            "demo-1",
            "example/one",
            1L,
            "First issue",
            "Summary one",
            0.91,
            List.of("good first issue"),
            "OPEN",
            "Reason one"
        );
        RecommendedIssue second = new RecommendedIssue(
            "demo-2",
            "example/two",
            2L,
            "Second issue",
            "Summary two",
            0.40,
            List.of("bug"),
            "OPEN",
            "Reason two"
        );
        LoadRecommendedIssuesPort storedPort = () -> List.of(
            new RecommendedIssue("stored-3", "example/three", 3L, "Stored", "Stored", 0.99, List.of(), "OPEN", "")
        );
        LoadUserProfilePort nullProfilePort = () -> null;
        ScoreRecommendedIssueRankingAdapter adapter = new ScoreRecommendedIssueRankingAdapter(storedPort, nullProfilePort);

        List<RecommendedIssue> result = adapter.rankRecommendedIssues(List.of(first, second));

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void mergesStoredCatalogAndRanksWithBm25AndCommunityWhenProfileAvailable() {
        UserProfile javaProfile = new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Dev",
            "dev@example.com",
            List.of("Java"),
            List.of("spring", "oauth"),
            true,
            null
        );
        RecommendedIssue liveUnrelated = new RecommendedIssue(
            "live-1",
            "sample/docs-site",
            10L,
            "Fix footer alignment",
            "Update footer CSS spacing",
            0.2,
            List.of("documentation"),
            "OPEN",
            "[스타수: 10, 최근 커밋: 2025-11-01]"
        );
        RecommendedIssue storedStrongMatch = new RecommendedIssue(
            "stored-2",
            "spring-projects/spring-security",
            20L,
            "Validate Spring OAuth PKCE state in Java filter",
            "Ensure Java OAuth PKCE token exchange validates code verifier",
            0.85,
            List.of("good first issue", "java"),
            "OPEN",
            "미할당 [스타수: 8.5k, 최근 커밋: 2026-11-18]"
        );

        ScoreRecommendedIssueRankingAdapter adapter = new ScoreRecommendedIssueRankingAdapter(
            () -> List.of(storedStrongMatch),
            () -> javaProfile
        );

        List<RecommendedIssue> ranked = adapter.rankRecommendedIssues(List.of(liveUnrelated));

        assertThat(ranked).hasSize(2);
        assertThat(ranked.getFirst().id()).isEqualTo("stored-2");
        assertThat(ranked.getFirst().reason()).contains("[주 사용 언어 일치: Java]");
    }

    @Test
    void continuesRankingLiveCandidatesIfStoredCatalogThrows() {
        UserProfile profile = new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Dev",
            "dev@example.com",
            List.of("Java"),
            List.of("security"),
            true,
            null
        );
        RecommendedIssue liveMatch = new RecommendedIssue(
            "live-security",
            "spring-projects/spring-boot",
            31L,
            "Improve Java security header validation",
            "Security filter hardening",
            0.8,
            List.of("java"),
            "OPEN",
            "미할당"
        );
        LoadRecommendedIssuesPort failingStoredPort = () -> {
            throw new IllegalStateException("DB temporarily busy");
        };

        ScoreRecommendedIssueRankingAdapter adapter = new ScoreRecommendedIssueRankingAdapter(
            failingStoredPort,
            () -> profile
        );

        List<RecommendedIssue> ranked = adapter.rankRecommendedIssues(List.of(liveMatch));

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().id()).isEqualTo("live-security");
    }
}
