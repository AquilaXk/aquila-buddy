package com.aquilabuddy.adapter.out.github.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubRecommendedIssueSearchAdapterTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsGitHubSearchResponseToRecommendedIssues() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search/issues", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 987654321,
                      "number": 42,
                      "title": "Improve onboarding docs",
                      "body": "<img width=\\"360\\" height=\\"191\\" alt=\\"Image\\" src=\\"https://github.com/user-attachments/assets/09b66664-17ca-46b1-a2db-18b8a45d3e0a\\" /> Cerramos nuestra cuenta después de que cambió a X y dejó de ser una plataforma segura.",
                      "repository_url": "https://api.github.com/repos/spring-projects/spring-boot",
                      "labels": [
                        {"name": "good first issue"},
                        {"name": "documentation"}
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
            RestClient.builder(),
            "http://localhost:" + server.getAddress().getPort(),
            "test-token",
            true,
            "label:\"good first issue\"",
            2
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        assertThat(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8))
            .contains("q=label:\"good first issue\" is:issue is:open")
            .contains("per_page=2");
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().id()).isEqualTo("987654321");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(42);
        assertThat(issues.getFirst().summary())
            .isEqualTo("Cerramos nuestra cuenta después de que cambió a X y dejó de ser una plataforma segura.");
        assertThat(issues.getFirst().score()).isZero();
        assertThat(issues.getFirst().reason())
            .isEqualTo("GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다.");
        assertThat(issues.getFirst().labels()).containsExactly("good first issue", "documentation");
        assertThat(issues.getFirst().status()).isEqualTo("OPEN");
    }

    @Test
    void stripsMarkdownNoiseFromSummary() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search/issues", exchange -> {
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 456,
                      "number": 9,
                      "title": "Clean docs",
                      "body": "```json { \\"name\\": \\"demo\\" } ``` Please update `README.md` so new contributors can start quickly.",
                      "repository_url": "https://api.github.com/repos/example/docs",
                      "labels": [
                        {"name": "good first issue"}
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
            RestClient.builder(),
            "http://localhost:" + server.getAddress().getPort(),
            "",
            true,
            "label:\"good first issue\"",
            1
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(issues.getFirst().summary())
            .isEqualTo("Please update README.md so new contributors can start quickly.");
    }

    @Test
    void searchesWithRequestedQuery() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search/issues", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 123,
                      "number": 7,
                      "title": "Fix Java docs",
                      "body": "Good first issue for Java contributors.",
                      "repository_url": "https://api.github.com/repos/example/java-lib",
                      "labels": [
                        {"name": "good first issue"}
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
            RestClient.builder(),
            "http://localhost:" + server.getAddress().getPort(),
            "",
            true,
            "label:\"good first issue\"",
            5
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues("language:java label:\"good first issue\"");

        assertThat(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8))
            .contains("q=language:java label:\"good first issue\" is:issue is:open")
            .contains("per_page=5");
        assertThat(issues)
            .extracting(RecommendedIssue::repoFullName)
            .containsExactly("example/java-lib");
    }
}
