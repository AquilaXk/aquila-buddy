package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillResult;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 추천 이슈 persistence adapter가 JPA entity를 도메인 모델로 변환하고 정렬하는지 검증합니다.
 */
@DataJpaTest
@Import(RecommendedIssuePersistenceAdapter.class)
@TestPropertySource(properties = "app.embedding.expected-dimensions=3")
class RecommendedIssuePersistenceAdapterTest {

    private static final List<Double> DEFAULT_EMBEDDING_VALUES = List.of(0.1, 0.2, 0.3);
    private static List<Double> embeddingValues = DEFAULT_EMBEDDING_VALUES;
    private static boolean embeddingFails;

    @Autowired
    private RecommendedIssuePersistenceAdapter adapter;

    @Autowired
    private RecommendedIssueJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        embeddingValues = DEFAULT_EMBEDDING_VALUES;
        embeddingFails = false;
    }

    @Test
    void loadsRecommendedIssuesOrderedByScoreDescending() {
        repository.save(new RecommendedIssueJpaEntity(
            "low-score",
            "spring-projects/spring-boot",
            2,
            "Refine validation error example",
            "Java/Spring 관심사와 맞는 validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        ));
        repository.save(new RecommendedIssueJpaEntity(
            "high-score",
            "vercel/next.js",
            1,
            "Improve contributor-facing documentation",
            "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
            0.91,
            List.of("good first issue", "documentation"),
            "OPEN",
            "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
        ));

        List<RecommendedIssue> issues = adapter.loadRecommendedIssues();

        assertThat(issues)
            .extracting(RecommendedIssue::id)
            .containsExactly("high-score", "low-score");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("vercel/next.js");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(1);
        assertThat(issues.getFirst().labels()).containsExactly("good first issue", "documentation");
        assertThat(issues.getFirst().score()).isEqualTo(0.91);
        assertThat(issues.getFirst().status()).isEqualTo("OPEN");
    }

    @Test
    void loadsCollectedIssuesForCatalog() {
        repository.save(new RecommendedIssueJpaEntity(
            "catalog-low-score",
            "spring-projects/spring-boot",
            2,
            "Refine validation error example",
            "Java/Spring 관심사와 맞는 validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        ));
        repository.save(new RecommendedIssueJpaEntity(
            "catalog-high-score",
            "vercel/next.js",
            1,
            "Improve contributor-facing documentation",
            "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
            0.91,
            List.of("good first issue", "documentation"),
            "OPEN",
            "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
        ));

        List<RecommendedIssue> issues = adapter.loadCollectedIssues();

        assertThat(issues)
            .extracting(RecommendedIssue::id)
            .containsExactly("catalog-high-score", "catalog-low-score");
    }

    @Test
    void savesRecommendedIssuesForLaterFallback() {
        RecommendedIssue searched = new RecommendedIssue(
            "github-search",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        );

        adapter.saveRecommendedIssues(List.of(searched));

        List<RecommendedIssue> issues = adapter.loadRecommendedIssues();
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().id()).isEqualTo("github-search");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(101);
        assertThat(issues.getFirst().labels()).containsExactly("good first issue", "documentation");
        assertThat(issues.getFirst().reason()).isEqualTo("GitHub Search 후보입니다.");
        assertThat(issues.getFirst().copyOutputVersion())
            .isEqualTo(RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION);
    }

    @Test
    void savesIssueEmbeddingWhenEmbeddingClientIsAvailable() {
        RecommendedIssue searched = new RecommendedIssue(
            "embedded-issue",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        );

        adapter.saveRecommendedIssues(List.of(searched));
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT issue_embedding FROM recommended_issues WHERE issue_id = ?",
            String.class,
            "embedded-issue"
        );
        assertThat(storedEmbedding).isEqualTo("[0.1,0.2,0.3]");
    }

    @Test
    void skipsIssueEmbeddingWhenProviderDimensionDoesNotMatchExpectedDimension() {
        embeddingValues = List.of(0.1, 0.2);
        RecommendedIssue searched = new RecommendedIssue(
            "dimension-mismatch-issue",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        );

        adapter.saveRecommendedIssues(List.of(searched));
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT issue_embedding FROM recommended_issues WHERE issue_id = ?",
            String.class,
            "dimension-mismatch-issue"
        );
        assertThat(storedEmbedding).isNull();
    }

    @Test
    void backfillsMissingIssueEmbeddingsWithLimitAndResultCounts() {
        repository.save(new RecommendedIssueJpaEntity(
            "missing-issue",
            "spring-projects/spring-boot",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        ));
        repository.save(new RecommendedIssueJpaEntity(
            "embedded-issue",
            "vercel/next.js",
            102,
            "Improve frontend docs",
            "프론트엔드 기여 문서 이슈입니다.",
            0.9,
            List.of("documentation"),
            "OPEN",
            "이미 embedding이 있는 후보입니다."
        ));
        repository.flush();
        jdbcTemplate.update(
            "UPDATE recommended_issues SET issue_embedding = ? WHERE issue_id = ?",
            "[0.9,0.8,0.7]",
            "embedded-issue"
        );

        EmbeddingBackfillResult result = adapter.backfillMissingIssueEmbeddings(10);
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT issue_embedding FROM recommended_issues WHERE issue_id = ?",
            String.class,
            "missing-issue"
        );
        String existingEmbedding = jdbcTemplate.queryForObject(
            "SELECT issue_embedding FROM recommended_issues WHERE issue_id = ?",
            String.class,
            "embedded-issue"
        );
        assertThat(storedEmbedding).isEqualTo("[0.1,0.2,0.3]");
        assertThat(existingEmbedding).isEqualTo("[0.9,0.8,0.7]");
        assertThat(result).isEqualTo(new EmbeddingBackfillResult(
            "issues",
            10,
            1,
            1,
            1,
            0,
            0,
            0,
            0
        ));
    }

    @Test
    void reportsIssueBackfillProviderFailureWithoutStoppingBatch() {
        embeddingFails = true;
        repository.save(new RecommendedIssueJpaEntity(
            "provider-failure-issue",
            "spring-projects/spring-boot",
            103,
            "Improve validation example",
            "검증 예시 개선 이슈입니다.",
            1.0,
            List.of("good first issue"),
            "OPEN",
            "provider 실패 fallback 대상입니다."
        ));

        EmbeddingBackfillResult result = adapter.backfillMissingIssueEmbeddings(10);
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT issue_embedding FROM recommended_issues WHERE issue_id = ?",
            String.class,
            "provider-failure-issue"
        );
        assertThat(storedEmbedding).isNull();
        assertThat(result).isEqualTo(new EmbeddingBackfillResult(
            "issues",
            10,
            1,
            0,
            0,
            0,
            0,
            0,
            1,
            List.of("provider-failure-issue: IllegalStateException")
        ));
        assertThat(result.failureReasons())
            .containsExactly("provider-failure-issue: IllegalStateException");
    }

    @Test
    void reportsIssueBackfillDimensionMismatchReason() {
        embeddingValues = List.of(0.1, 0.2);
        repository.save(new RecommendedIssueJpaEntity(
            "dimension-mismatch-backfill-issue",
            "spring-projects/spring-boot",
            104,
            "Improve validation example",
            "검증 예시 개선 이슈입니다.",
            1.0,
            List.of("good first issue"),
            "OPEN",
            "dimension mismatch 관측 대상입니다."
        ));

        EmbeddingBackfillResult result = adapter.backfillMissingIssueEmbeddings(10);

        assertThat(result.skippedDimensionMismatch()).isEqualTo(1);
        assertThat(result.failureReasons())
            .containsExactly("dimension-mismatch-backfill-issue: expected 3 dimensions but was 2");
    }

    @Test
    void findsRecommendedIssueByRepositoryAndIssueNumber() {
        repository.save(new RecommendedIssueJpaEntity(
            "lookup-target",
            "vercel/next.js",
            1,
            "Improve contributor-facing documentation",
            "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
            0.91,
            List.of("good first issue", "documentation"),
            "OPEN",
            "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
        ));

        Optional<RecommendedIssue> issue = adapter.findByRepoFullNameAndIssueNumber("vercel/next.js", 1);

        assertThat(issue).isPresent();
        assertThat(issue.get().id()).isEqualTo("lookup-target");
        assertThat(issue.get().repoFullName()).isEqualTo("vercel/next.js");
        assertThat(issue.get().issueNumber()).isEqualTo(1);
    }

    @Test
    void findsRecommendedIssueByInternalId() {
        repository.save(new RecommendedIssueJpaEntity(
            "detail-target",
            "vercel/next.js",
            1,
            "Improve contributor-facing documentation",
            "문서 중심 첫 기여 흐름을 확인하기 좋은 이슈입니다.",
            0.91,
            List.of("good first issue", "documentation"),
            "OPEN",
            "프론트엔드와 문서 작업으로 첫 PR 흐름을 작게 검증할 수 있습니다."
        ));

        Optional<RecommendedIssue> issue = adapter.findById("detail-target");

        assertThat(issue).isPresent();
        assertThat(issue.get().id()).isEqualTo("detail-target");
        assertThat(issue.get().repoFullName()).isEqualTo("vercel/next.js");
        assertThat(issue.get().issueNumber()).isEqualTo(1);
    }

    @TestConfiguration
    static class EmbeddingTestConfiguration {

        @Bean
        EmbeddingClient embeddingClient() {
            return input -> {
                if (embeddingFails) {
                    throw new IllegalStateException("provider unavailable");
                }
                return new EmbeddingVector(embeddingValues);
            };
        }
    }
}
