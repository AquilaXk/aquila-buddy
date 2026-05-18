package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 실제 PostgreSQL pgvector 타입과 ranking SQL의 결합을 검증합니다.
 */
@EnabledIfEnvironmentVariable(named = "PGVECTOR_INTEGRATION_TESTS", matches = "true")
class PgvectorRecommendedIssueSimilarityIntegrationTest {

    @Test
    void ranksIssuesByPgvectorCosineDistanceAfterSchemaInitialization() {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertIssue(jdbcTemplate, "far-issue", 1L, vectorLiteral(0.0, 1.0), 0.99);
        insertIssue(jdbcTemplate, "near-issue", 2L, vectorLiteral(1.0, 0.0), 0.10);

        PgvectorRecommendedIssueSimilarityAdapter adapter = new PgvectorRecommendedIssueSimilarityAdapter(
            JdbcClient.create(dataSource),
            input -> new EmbeddingVector(vectorValues(1.0, 0.0)),
            currentUserProfilePort(),
            10
        );

        List<RecommendedIssue> rankedIssues = adapter.rankRecommendedIssues(List.of(
            candidate("far-issue", 0.99),
            candidate("near-issue", 0.10)
        ));

        assertThat(rankedIssues)
            .extracting(RecommendedIssue::id)
            .containsExactly("near-issue", "far-issue");
    }

    @Test
    void initializesVectorColumnsAndHnswIndexesForOperationalSchema() {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(columnType(jdbcTemplate, "user_profiles", "profile_embedding")).isEqualTo("vector");
        assertThat(columnType(jdbcTemplate, "recommended_issues", "issue_embedding")).isEqualTo("vector");
        assertThat(indexDefinition(jdbcTemplate, "user_profiles", "idx_user_profiles_profile_embedding"))
            .contains("USING hnsw")
            .contains("profile_embedding vector_cosine_ops");
        assertThat(indexDefinition(jdbcTemplate, "recommended_issues", "idx_recommended_issues_issue_embedding"))
            .contains("USING hnsw")
            .contains("issue_embedding vector_cosine_ops");
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(env("PGVECTOR_TEST_DB_URL", "jdbc:postgresql://localhost:5432/aquila_buddy"));
        dataSource.setUsername(env("PGVECTOR_TEST_DB_USER", "aquila"));
        dataSource.setPassword(env("PGVECTOR_TEST_DB_PASSWORD", "aquila"));
        return dataSource;
    }

    private static void initializeSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("schema.sql"),
            new ClassPathResource(PgvectorRecommendedIssueSimilaritySql.schemaResourcePath())
        );
        populator.execute(dataSource);
    }

    private static void insertIssue(
        JdbcTemplate jdbcTemplate,
        String issueId,
        long issueNumber,
        String embedding,
        double score
    ) {
        jdbcTemplate.update("""
            INSERT INTO recommended_issues (
                issue_id,
                repo_full_name,
                issue_number,
                title,
                summary,
                score,
                status,
                reason,
                issue_embedding
            )
            VALUES (?, 'aquila-buddy/demo', ?, 'Demo issue', 'Demo summary', ?, 'OPEN', 'Demo reason', CAST(? AS vector))
            """,
            issueId,
            issueNumber,
            score,
            embedding
        );
    }

    private static LoadUserProfilePort currentUserProfilePort() {
        return new LoadUserProfilePort() {
            @Override
            public UserProfile loadCurrentUserProfile() {
                return new UserProfile(
                    10001L,
                    "aquila-dev",
                    "Aquila Developer",
                    "aquila@example.com",
                    List.of("Java"),
                    List.of("spring", "pgvector"),
                    true,
                    "2026-05-18T00:00:00Z"
                );
            }
        };
    }

    private static RecommendedIssue candidate(String issueId, double score) {
        return new RecommendedIssue(
            issueId,
            "aquila-buddy/demo",
            1L,
            "Demo issue",
            "Demo summary",
            score,
            List.of("good first issue"),
            "OPEN",
            "Demo reason"
        );
    }

    private static String vectorLiteral(double firstValue, double secondValue) {
        return "[" + String.join(",", vectorValues(firstValue, secondValue).stream()
            .map(String::valueOf)
            .toList()) + "]";
    }

    private static String columnType(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
            SELECT udt_name
            FROM information_schema.columns
            WHERE table_name = ? AND column_name = ?
            """,
            String.class,
            tableName,
            columnName
        );
    }

    private static String indexDefinition(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        return jdbcTemplate.queryForObject("""
            SELECT indexdef
            FROM pg_indexes
            WHERE tablename = ? AND indexname = ?
            """,
            String.class,
            tableName,
            indexName
        );
    }

    private static List<Double> vectorValues(double firstValue, double secondValue) {
        return IntStream.range(0, 1536)
            .mapToObj(index -> {
                if (index == 0) {
                    return firstValue;
                }
                if (index == 1) {
                    return secondValue;
                }
                return 0.0;
            })
            .toList();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
