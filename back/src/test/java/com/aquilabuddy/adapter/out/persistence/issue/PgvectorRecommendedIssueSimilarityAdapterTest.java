package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class PgvectorRecommendedIssueSimilarityAdapterTest {

    @Test
    void keepsScoreFallbackCandidatesAfterRankedIssues() {
        RecommendedIssue scoreFirstWithoutEmbedding = issue("score-first", 0.91);
        RecommendedIssue rankedByVector = issue("ranked", 0.80);
        RecommendedIssue scoreLastWithoutEmbedding = issue("score-last", 0.70);

        List<RecommendedIssue> result = PgvectorRecommendedIssueSimilarityAdapter.mergeRankedWithScoreFallback(
            List.of(scoreFirstWithoutEmbedding, rankedByVector, scoreLastWithoutEmbedding),
            List.of(rankedByVector)
        );

        assertThat(result)
            .extracting(RecommendedIssue::id)
            .containsExactly("ranked", "score-first", "score-last");
    }

    @Test
    void keepsFallbackCandidatesWhenRankLimitReturnsOnlyPartOfCandidates() {
        RecommendedIssue ranked = issue("ranked", 0.95);
        RecommendedIssue fallbackOne = issue("fallback-one", 0.90);
        RecommendedIssue fallbackTwo = issue("fallback-two", 0.80);

        List<RecommendedIssue> result = PgvectorRecommendedIssueSimilarityAdapter.mergeRankedWithScoreFallback(
            List.of(ranked, fallbackOne, fallbackTwo),
            List.of(ranked)
        );

        assertThat(result)
            .extracting(RecommendedIssue::id)
            .containsExactly("ranked", "fallback-one", "fallback-two");
    }

    @Test
    void fallsBackToScoreCandidatesWhenEmbeddingProviderFails() {
        RecommendedIssue scoreFirst = issue("score-first", 0.91);
        RecommendedIssue scoreSecond = issue("score-second", 0.80);
        PgvectorRecommendedIssueSimilarityAdapter adapter = new PgvectorRecommendedIssueSimilarityAdapter(
            mock(JdbcClient.class),
            input -> {
                throw new IllegalStateException("embedding provider unavailable");
            },
            () -> new UserProfile(
                10001L,
                "aquila-dev",
                "Aquila Developer",
                "aquila@example.com",
                List.of("Java"),
                List.of("spring"),
                true,
                "2026-05-18T00:00:00Z"
            ),
            1
        );

        List<RecommendedIssue> result = adapter.rankRecommendedIssues(List.of(scoreFirst, scoreSecond));

        assertThat(result)
            .extracting(RecommendedIssue::id)
            .containsExactly("score-first", "score-second");
    }

    @Test
    void appliesHybridRankingPromotingLanguageMatchOverHigherCosineDistance() {
        RecommendedIssue javaIssue = new RecommendedIssue(
            "java-issue",
            "spring-projects/spring-boot",
            101,
            "Fix Java issue",
            "Java issue summary",
            0.70,
            List.of("good first issue", "java"),
            "OPEN",
            "GitHub 수집 후보"
        );
        RecommendedIssue pythonIssue = new RecommendedIssue(
            "python-issue",
            "psf/requests",
            202,
            "Fix Python issue",
            "Python issue summary",
            0.80,
            List.of("good first issue", "python"),
            "OPEN",
            "GitHub 수집 후보"
        );

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(pythonIssue, javaIssue),
            List.of("Java")
        );

        // 코사인 점수는 python(0.80)이 java(0.70)보다 높았으나,
        // 사용자 주 언어(Java) 일치 가중치(0.25) 결합으로 javaIssue가 1위로 최적화되어야 합니다.
        assertThat(ranked)
            .extracting(RecommendedIssue::id)
            .containsExactly("java-issue", "python-issue");
        assertThat(ranked.getFirst().reason()).contains("주 사용 언어 일치: Java");
        assertThat(ranked.getFirst().score()).isEqualTo(0.775); // 0.70 * 0.75 + 0.25 = 0.775
        assertThat(ranked.get(1).score()).isEqualTo(0.6); // 0.80 * 0.75 = 0.600
    }

    @Test
    void preservesCosineRankingWhenBothOrNeitherMatchLanguage() {
        RecommendedIssue near = issue("near-issue", 0.90);
        RecommendedIssue far = issue("far-issue", 0.50);

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(far, near),
            List.of("Rust")
        );

        assertThat(ranked)
            .extracting(RecommendedIssue::id)
            .containsExactly("near-issue", "far-issue");
    }

    @Test
    void handlesNullFieldsGracefullyWithoutThrowingNullPointerException() {
        RecommendedIssue issueWithNulls = new RecommendedIssue(
            "null-issue",
            null,
            1,
            null,
            null,
            0.5,
            null,
            "OPEN",
            null
        );

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(issueWithNulls),
            List.of("Java")
        );

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().id()).isEqualTo("null-issue");
    }

    @Test
    void doesNotFalseMatchLanguageGoOnGoodFirstIssueOrOngoingTitles() {
        RecommendedIssue javaIssueWithGoodFirstIssue = new RecommendedIssue(
            "java-spring",
            "spring-projects/spring-boot",
            10,
            "Ongoing refactor for algorithm module",
            "Refactor ongoing algorithm classes",
            0.80,
            List.of("good first issue", "help wanted"),
            "OPEN",
            "GitHub 수집 후보"
        );
        RecommendedIssue realGoIssue = new RecommendedIssue(
            "go-runtime",
            "golang/go",
            20,
            "Fix runtime memory leak",
            "Memory leak in Go runtime scheduler",
            0.60,
            List.of("help wanted", "go"),
            "OPEN",
            "GitHub 수집 후보"
        );

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(javaIssueWithGoodFirstIssue, realGoIssue),
            List.of("Go")
        );

        // realGoIssue는 점수 0.60 * 0.75 + 0.25 = 0.70으로 승격되어 1위가 되어야 합니다.
        // javaIssue는 'good'이나 'ongoing', 'algorithm'에 'go'가 포함되어 있지만 단어 단위 검사이므로 Go로 오매칭되지 않아야 합니다 (0.80 * 0.75 = 0.60).
        assertThat(ranked)
            .extracting(RecommendedIssue::id)
            .containsExactly("go-runtime", "java-spring");
        assertThat(ranked.getFirst().reason()).contains("주 사용 언어 일치: Go");
        assertThat(ranked.get(1).reason()).doesNotContain("주 사용 언어 일치");
    }

    @Test
    void doesNotFalseMatchLanguageJavaOnJavaScript() {
        RecommendedIssue jsIssue = new RecommendedIssue(
            "react-issue",
            "facebook/react",
            10,
            "Fix JavaScript bundler issue",
            "JavaScript client issue",
            0.80,
            List.of("good first issue", "javascript"),
            "OPEN",
            "GitHub 수집 후보"
        );

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(jsIssue),
            List.of("Java")
        );

        assertThat(ranked.getFirst().reason()).doesNotContain("주 사용 언어 일치");
        assertThat(ranked.getFirst().score()).isEqualTo(0.60); // 0.80 * 0.75 = 0.60
    }

    @Test
    void matchesCPlusPlusLanguageCorrectly() {
        RecommendedIssue cppIssue = new RecommendedIssue(
            "protobuf-issue",
            "protocolbuffers/protobuf",
            50,
            "Fix C++ build issue",
            "Protobuf C++ build failure",
            0.70,
            List.of("good first issue", "c++"),
            "OPEN",
            "GitHub 수집 후보"
        );

        List<RecommendedIssue> ranked = PgvectorRecommendedIssueSimilarityAdapter.applyHybridRanking(
            List.of(cppIssue),
            List.of("C++")
        );

        assertThat(ranked.getFirst().reason()).contains("주 사용 언어 일치: C++");
        assertThat(ranked.getFirst().score()).isEqualTo(0.775); // 0.70 * 0.75 + 0.25 = 0.775
    }

    private static RecommendedIssue issue(String id, double score) {
        return new RecommendedIssue(
            id,
            "spring-projects/spring-boot",
            100,
            "Refine validation error example",
            "Java/Spring validation 예시 개선 이슈입니다.",
            score,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        );
    }
}
