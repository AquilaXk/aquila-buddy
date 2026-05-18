package com.aquilabuddy.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.domain.user.UserProfileUpdateRequest;
import com.aquilabuddy.global.auth.CurrentUserGithubIdProvider;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@DataJpaTest
@Import({
    UserProfilePersistenceAdapter.class,
    CurrentUserGithubIdProvider.class,
    UserProfilePersistenceAdapterTest.GitHubClientTestConfig.class
})
@TestPropertySource(properties = {
    "app.github.user-context.enabled=true",
    "app.embedding.expected-dimensions=3"
})
class UserProfilePersistenceAdapterTest {

    private static final List<Double> DEFAULT_EMBEDDING_VALUES = List.of(0.4, 0.5, 0.6);
    private static List<Double> embeddingValues = DEFAULT_EMBEDDING_VALUES;

    @Autowired
    private UserProfilePersistenceAdapter adapter;

    @Autowired
    private UserProfileJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        embeddingValues = DEFAULT_EMBEDDING_VALUES;
    }

    @Test
    void loadsCurrentUserProfileByDefaultGithubId() {
        UserProfile profile = adapter.loadCurrentUserProfile();

        assertThat(profile.githubId()).isEqualTo(10001L);
        assertThat(profile.login()).isEqualTo("aquila-dev");
        assertThat(profile.primaryLanguages()).containsExactly("Java", "TypeScript", "SQL");
        assertThat(profile.topics()).containsExactly("spring", "nextjs", "open-source");
    }

    @Test
    void loadsUserProfileByLogin() {
        assertThat(adapter.loadUserProfileByLogin("aquila-dev"))
            .get()
            .extracting(UserProfile::githubId)
            .isEqualTo(10001L);
    }

    @Test
    void loadsUserProfileByGithubId() {
        assertThat(adapter.loadUserProfileByGithubId(10001L))
            .get()
            .extracting(UserProfile::login)
            .isEqualTo("aquila-dev");
    }

    @Test
    void returnsEmptyWhenLoginDoesNotExist() {
        assertThat(adapter.loadUserProfileByLogin("missing-user")).isEmpty();
    }

    @Test
    void returnsEmptyWhenGithubIdDoesNotExist() {
        assertThat(adapter.loadUserProfileByGithubId(99999L)).isEmpty();
    }

    @Test
    void savesCurrentUserProfileUpdate() {
        UserProfile updated = adapter.saveCurrentUserProfile(new UserProfileUpdateRequest(
            "Aquila Maintainer",
            "maintainer@example.com",
            List.of("Java", "TypeScript"),
            List.of("spring", "oss")
        ));

        assertThat(updated.name()).isEqualTo("Aquila Maintainer");
        assertThat(updated.email()).isEqualTo("maintainer@example.com");
        assertThat(updated.primaryLanguages()).containsExactly("Java", "TypeScript");
        assertThat(updated.topics()).containsExactly("spring", "oss");
        assertThat(adapter.loadCurrentUserProfile())
            .usingRecursiveComparison()
            .isEqualTo(updated);
    }

    @Test
    void refreshesCurrentUserProfileVectorState() {
        UserProfile refreshed = adapter.refreshCurrentUserProfileVector();

        assertThat(refreshed.profileReady()).isTrue();
        assertThat(refreshed.vectorUpdatedAt()).isEqualTo("2026-05-16T00:00:00Z");
    }

    @Test
    void refreshesCurrentUserProfileVectorWithGitHubContext() {
        UserProfile refreshed = adapter.refreshCurrentUserProfileVector();

        assertThat(refreshed.primaryLanguages()).containsExactly("Rust", "Go");
        assertThat(refreshed.topics()).containsExactly("cli", "automation");
    }

    @Test
    void savesProfileEmbeddingWhenProviderDimensionMatchesExpectedDimension() {
        adapter.refreshCurrentUserProfileVector();
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT profile_embedding FROM user_profiles WHERE github_id = ?",
            String.class,
            10001L
        );
        assertThat(storedEmbedding).isEqualTo("[0.4,0.5,0.6]");
    }

    @Test
    void skipsProfileEmbeddingWhenProviderDimensionDoesNotMatchExpectedDimension() {
        embeddingValues = List.of(0.4, 0.5);

        adapter.refreshCurrentUserProfileVector();
        repository.flush();

        String storedEmbedding = jdbcTemplate.queryForObject(
            "SELECT profile_embedding FROM user_profiles WHERE github_id = ?",
            String.class,
            10001L
        );
        assertThat(storedEmbedding).isNull();
    }

    @TestConfiguration
    static class GitHubClientTestConfig {

        @Bean
        GitHubApiClient gitHubApiClient() {
            return new StubGitHubApiClient();
        }

        @Bean
        EmbeddingClient embeddingClient() {
            return input -> new EmbeddingVector(embeddingValues);
        }
    }

    private static class StubGitHubApiClient extends GitHubApiClient {

        StubGitHubApiClient() {
            super(RestClient.builder(), "http://localhost", "");
        }

        @Override
        public GitHubContributionContext loadContributionContext(String username) {
            Map<String, Integer> languages = new LinkedHashMap<>();
            languages.put("Rust", 2);
            languages.put("Go", 1);

            return new GitHubContributionContext(
                new GitHubUserProfile(username, "CLI automation", 4),
                List.of(),
                List.copyOf(languages.keySet()),
                List.of("cli", "automation"),
                "Developer GitHub Profile"
            );
        }
    }
}
