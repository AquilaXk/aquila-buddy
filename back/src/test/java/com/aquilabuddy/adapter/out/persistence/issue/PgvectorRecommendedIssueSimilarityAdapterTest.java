package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

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
