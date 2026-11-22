package com.aquilabuddy.domain.issue;

import com.aquilabuddy.domain.user.UserProfile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridIssueRrfScorerTest {

    private static final LocalDate REFERENCE_TODAY = LocalDate.of(2026, 6, 15);

    @Test
    void returnsEmptyListWhenCandidatesEmpty() {
        UserProfile profile = new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Dev",
            "dev@example.com",
            List.of("Java"),
            List.of("spring-boot"),
            true,
            null
        );

        assertThat(HybridIssueRrfScorer.rank(List.of(), List.of(), profile, REFERENCE_TODAY)).isEmpty();
        assertThat(HybridIssueRrfScorer.rank(null, List.of(), profile, REFERENCE_TODAY)).isEmpty();
    }

    @Test
    void combinesVectorBm25FreshnessAndCommunitySignalsWithRrf() {
        UserProfile profile = new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Dev",
            "dev@example.com",
            List.of("Java"),
            List.of("spring", "validation"),
            true,
            null
        );

        RecommendedIssue topFit = new RecommendedIssue(
            "issue-top",
            "spring-projects/spring-boot",
            101L,
            "Fix Spring validation error mapper in Java parser",
            "Improve spring validation binding message handling.",
            0.70,
            List.of("good first issue", "java"),
            "OPEN",
            "미할당 이슈 후보입니다. [스타수: 12.5k, 최근 커밋: 2026-06-14]"
        );
        RecommendedIssue staleMismatch = new RecommendedIssue(
            "issue-stale",
            "sample/css-theme",
            102L,
            "Adjust button padding CSS",
            "Minor CSS spacing tweak.",
            0.75,
            List.of("documentation"),
            "OPEN",
            "[스타수: 12, 최근 커밋: 2025-10-01]"
        );

        RecommendedIssue vectorTop = new RecommendedIssue(
            topFit.id(),
            topFit.repoFullName(),
            topFit.issueNumber(),
            topFit.title(),
            topFit.summary(),
            0.94,
            topFit.labels(),
            topFit.status(),
            topFit.reason()
        );
        RecommendedIssue vectorOther = new RecommendedIssue(
            staleMismatch.id(),
            staleMismatch.repoFullName(),
            staleMismatch.issueNumber(),
            staleMismatch.title(),
            staleMismatch.summary(),
            0.22,
            staleMismatch.labels(),
            staleMismatch.status(),
            staleMismatch.reason()
        );

        List<RecommendedIssue> ranked = HybridIssueRrfScorer.rank(
            List.of(staleMismatch, topFit),
            List.of(vectorTop, vectorOther),
            profile,
            REFERENCE_TODAY
        );

        assertThat(ranked).hasSize(2);
        assertThat(ranked.getFirst().id()).isEqualTo("issue-top");
        assertThat(ranked.getFirst().score()).isGreaterThan(ranked.get(1).score());
        assertThat(ranked.getFirst().score()).isBetween(0.0, 1.0);
        assertThat(ranked.getFirst().reason()).contains("[주 사용 언어 일치: Java]");
        assertThat(ranked.get(1).reason()).doesNotContain("주 사용 언어 일치");
    }

    @Test
    void doesNotDuplicateLanguageBadgeWhenAlreadyPresentAndParsesFreshnessSignals() {
        UserProfile profile = new UserProfile(
            10001L,
            "aquila-dev",
            "Aquila Dev",
            "dev@example.com",
            List.of("TypeScript"),
            List.of("nextjs"),
            true,
            null
        );

        RecommendedIssue freshUnassigned = new RecommendedIssue(
            "issue-fresh",
            "vercel/next.js",
            201L,
            "Refine TypeScript router types",
            "Improve Next.js TypeScript inference.",
            0.5,
            List.of("help wanted", "typescript"),
            "OPEN",
            "미할당 [스타수: 4.2k, 최근 커밋: 2026-06-12] [주 사용 언어 일치: TypeScript]"
        );
        RecommendedIssue olderLowCommunity = new RecommendedIssue(
            "issue-older",
            "vercel/next.js",
            202L,
            "Refine TypeScript router fallback",
            "Improve Next.js TypeScript inference.",
            0.5,
            List.of("typescript"),
            "OPEN",
            "[스타수: 50, 최근 커밋: 2026-04-01]"
        );

        List<RecommendedIssue> ranked = HybridIssueRrfScorer.rank(
            List.of(olderLowCommunity, freshUnassigned),
            List.of(),
            profile,
            REFERENCE_TODAY
        );

        assertThat(ranked.getFirst().id()).isEqualTo("issue-fresh");
        int firstBadgeIndex = ranked.getFirst().reason().indexOf("주 사용 언어 일치");
        int secondBadgeIndex = ranked.getFirst().reason().indexOf("주 사용 언어 일치", firstBadgeIndex + 1);
        assertThat(secondBadgeIndex).isEqualTo(-1);

        assertThat(HybridIssueRrfScorer.freshnessSignal(freshUnassigned, REFERENCE_TODAY)).isEqualTo(1.0);
        assertThat(HybridIssueRrfScorer.freshnessSignal(olderLowCommunity, REFERENCE_TODAY)).isEqualTo(0.5);
        assertThat(HybridIssueRrfScorer.parseStarCount(freshUnassigned.reason())).isEqualTo(4200);
        assertThat(HybridIssueRrfScorer.parseStarCount(olderLowCommunity.reason())).isEqualTo(50);
    }
}
