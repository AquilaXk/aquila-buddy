package com.aquilabuddy.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.user.AuthenticatedUserProfile;
import com.aquilabuddy.global.auth.CurrentUserGithubIdProvider;
import com.aquilabuddy.global.github.GitHubApiClient;
import com.aquilabuddy.support.SecurityContextTestUtils;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.client.RestClient;

/**
 * 운영 pgvector schema에서 OAuth 프로필 갱신이 vector 컬럼 타입과 충돌하지 않는지 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "PGVECTOR_INTEGRATION_TESTS", matches = "true")
@Import({
    UserProfilePersistenceAdapter.class,
    CurrentUserGithubIdProvider.class,
    UserProfilePgvectorPersistenceIntegrationTest.GitHubClientTestConfig.class
})
@TestPropertySource(properties = {
    "spring.sql.init.mode=never",
    "spring.jpa.hibernate.ddl-auto=none",
    "app.github.user-context.enabled=false",
    "app.embedding.expected-dimensions=1536"
})
class UserProfilePgvectorPersistenceIntegrationTest {

    private static final long GITHUB_ID = 186246795L;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserProfilePersistenceAdapter adapter;

    @Autowired
    private UserProfileJpaRepository repository;

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
        SecurityContextTestUtils.setCurrentGithubUser(GITHUB_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextTestUtils.clear();
    }

    @Test
    void upsertingExistingAuthenticatedProfileDoesNotRewritePgvectorEmbeddingAsVarchar() {
        jdbcTemplate.update("""
            INSERT INTO user_profiles (
                github_id,
                login,
                name,
                email,
                profile_ready,
                vector_updated_at,
                profile_embedding
            ) VALUES (?, ?, ?, ?, TRUE, ?, CAST(? AS vector))
            """,
            GITHUB_ID,
            "AquilaXk",
            "Previous Name",
            "previous@example.com",
            "2026-05-21T00:00:00Z",
            vectorLiteral()
        );

        adapter.upsertAuthenticatedUserProfile(new AuthenticatedUserProfile(
            GITHUB_ID,
            "AquilaXk",
            "Aquila",
            "aquila@example.com",
            "https://avatars.githubusercontent.com/u/186246795?v=4"
        ));
        repository.flush();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT name FROM user_profiles WHERE github_id = ?",
            String.class,
            GITHUB_ID
        )).isEqualTo("Aquila");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT vector_dims(profile_embedding) FROM user_profiles WHERE github_id = ?",
            Integer.class,
            GITHUB_ID
        )).isEqualTo(1536);
    }

    private static String vectorLiteral() {
        return IntStream.range(0, 1536)
            .mapToObj(index -> index == 0 ? "1.0" : "0.0")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @TestConfiguration
    static class GitHubClientTestConfig {

        @Bean
        GitHubApiClient gitHubApiClient() {
            return new GitHubApiClient(RestClient.builder(), "http://localhost", "");
        }
    }
}
