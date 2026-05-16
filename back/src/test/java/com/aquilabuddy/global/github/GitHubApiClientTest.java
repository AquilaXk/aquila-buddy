package com.aquilabuddy.global.github;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubApiClientTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void loadsUserProfile() {
        route("/users/aquila-dev", 200, """
            {
              "login": "aquila-dev",
              "bio": "Open source contributor",
              "public_repos": 12
            }
            """);
        GitHubApiClient client = client();

        Optional<GitHubApiClient.GitHubUserProfile> profile = client.getUserProfile("aquila-dev");

        assertThat(profile).isPresent();
        assertThat(profile.get().login()).isEqualTo("aquila-dev");
        assertThat(profile.get().bio()).isEqualTo("Open source contributor");
        assertThat(profile.get().publicRepos()).isEqualTo(12);
    }

    @Test
    void returnsEmptyWhenUserProfileRequestFails() {
        route("/users/missing-user", 404, """
            {"message":"Not Found"}
            """);
        GitHubApiClient client = client();

        Optional<GitHubApiClient.GitHubUserProfile> profile = client.getUserProfile("missing-user");

        assertThat(profile).isEmpty();
    }

    @Test
    void loadsUserRepositoriesWithoutForksOrderedByStarsAndLimited() {
        route("/users/aquila-dev/repos", 200, """
            [
              {
                "name": "small-tool",
                "description": "Small helper",
                "language": "Java",
                "topics": ["spring"],
                "stargazers_count": 3,
                "fork": false
              },
              {
                "name": "forked-tool",
                "description": "Forked helper",
                "language": "Kotlin",
                "topics": ["fork"],
                "stargazers_count": 99,
                "fork": true
              },
              {
                "name": "popular-tool",
                "description": "Popular helper",
                "language": "TypeScript",
                "topics": ["nextjs", "oss"],
                "stargazers_count": 12,
                "fork": false
              },
              {
                "name": "extra-tool",
                "description": "Extra helper",
                "language": "SQL",
                "topics": [],
                "stargazers_count": 1,
                "fork": false
              }
            ]
            """);
        GitHubApiClient client = client();

        List<GitHubApiClient.GitHubRepository> repositories = client.getUserRepos("aquila-dev", 2);

        assertThat(repositories)
            .extracting(GitHubApiClient.GitHubRepository::name)
            .containsExactly("popular-tool", "small-tool");
        assertThat(repositories.getFirst().topics()).containsExactly("nextjs", "oss");
    }

    @Test
    void aggregatesRepositoryLanguagesByFrequencyDescending() {
        GitHubApiClient client = client();
        List<GitHubApiClient.GitHubRepository> repositories = List.of(
            new GitHubApiClient.GitHubRepository("api", "Spring API", "Java", List.of("spring"), 7),
            new GitHubApiClient.GitHubRepository("web", "Next app", "TypeScript", List.of("nextjs"), 5),
            new GitHubApiClient.GitHubRepository("worker", "Batch worker", "Java", List.of("batch"), 3),
            new GitHubApiClient.GitHubRepository("docs", "Docs", null, List.of("docs"), 1)
        );

        Map<String, Integer> languages = client.aggregateLanguages(repositories);

        assertThat(languages).containsExactly(
            entry("Java", 2),
            entry("TypeScript", 1)
        );
    }

    private GitHubApiClient client() {
        return new GitHubApiClient(RestClient.builder(), baseUrl(), "");
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void route(String path, int status, String body) {
        server.createContext(path, exchange -> respond(exchange, status, body));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
