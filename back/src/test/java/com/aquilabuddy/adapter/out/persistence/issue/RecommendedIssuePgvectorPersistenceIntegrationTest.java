package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영 pgvector schema에서 이슈 후보 저장이 vector 컬럼 타입과 충돌하지 않는지 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "PGVECTOR_INTEGRATION_TESTS", matches = "true")
@Import({
    RecommendedIssuePersistenceAdapter.class,
    RecommendedIssuePgvectorPersistenceIntegrationTest.EmbeddingTestConfig.class
})
@TestPropertySource(properties = {
    "spring.sql.init.mode=never",
    "spring.jpa.hibernate.ddl-auto=none",
    "app.embedding.expected-dimensions=1536"
})
class RecommendedIssuePgvectorPersistenceIntegrationTest {

    private static final String ISSUE_ID = "pgvector-save-issue";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecommendedIssuePersistenceAdapter adapter;

    @Autowired
    private RecommendedIssueJpaRepository repository;

    @DynamicPropertySource
    static void pgvectorDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("PGVECTOR_TEST_DB_URL", "jdbc:postgresql://localhost:5432/aquila_buddy"));
        registry.add("spring.datasource.username", () -> env("PGVECTOR_TEST_DB_USER", "aquila"));
        registry.add("spring.datasource.password", () -> env("PGVECTOR_TEST_DB_PASSWORD", "aquila"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("schema.sql"),
            new ClassPathResource("schema-postgres-pgvector.sql")
        );
        populator.execute(dataSource);
        jdbcTemplate.update("DELETE FROM recommended_issue_labels WHERE recommended_issue_id = ?", ISSUE_ID);
        jdbcTemplate.update("DELETE FROM recommended_issues WHERE issue_id = ?", ISSUE_ID);
    }

    @Test
    void savesRecommendedIssueEmbeddingWithPgvectorColumnCast() {
        adapter.saveRecommendedIssues(List.of(new RecommendedIssue(
            ISSUE_ID,
            "aquila-buddy/demo",
            101,
            "Improve contributor guide",
            "GitHub에서 수집한 Good First Issue 후보입니다.",
            1.0,
            List.of("good first issue", "documentation"),
            "OPEN",
            "GitHub Search 후보입니다."
        )));
        repository.flush();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT vector_dims(issue_embedding) FROM recommended_issues WHERE issue_id = ?",
            Integer.class,
            ISSUE_ID
        )).isEqualTo(1536);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @TestConfiguration
    static class EmbeddingTestConfig {

        @Bean
        EmbeddingClient embeddingClient() {
            return input -> new EmbeddingVector(IntStream.range(0, 1536)
                .mapToObj(index -> index == 0 ? 1.0 : 0.0)
                .collect(Collectors.toList()));
        }
    }
}
