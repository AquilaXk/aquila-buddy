package com.aquilabuddy.adapter.out.github.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.user.UserProfile;
import com.aquilabuddy.global.github.GitHubApiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubPullRequestDraftDiffAdapterTest {

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
    void loadsDiffWithCurrentUserLoginAsForkOwner() {
        route("/repos/spring-projects/spring-boot/compare/main...aquila-dev:feat/validation-docs", 200, """
            {
              "files": [
                {
                  "filename": "docs.adoc",
                  "patch": "@@ -1 +1 @@\\n+Add validation example"
                }
              ]
            }
            """);
        GitHubPullRequestDraftDiffAdapter adapter = new GitHubPullRequestDraftDiffAdapter(
            new GitHubApiClient(RestClient.builder(), baseUrl(), ""),
            new FakeLoadUserProfilePort()
        );

        String diff = adapter.loadDiff(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            null
        ));

        assertThat(diff).isEqualTo("""
            --- docs.adoc
            @@ -1 +1 @@
            +Add validation example""");
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

    private static class FakeLoadUserProfilePort implements LoadUserProfilePort {

        @Override
        public UserProfile loadCurrentUserProfile() {
            return new UserProfile(
                10001L,
                "aquila-dev",
                "Aquila Developer",
                "aquila@example.com",
                List.of("Java"),
                List.of("spring"),
                true,
                "2026-05-16T00:00:00Z"
            );
        }

        @Override
        public Optional<UserProfile> loadUserProfileByLogin(String login) {
            return Optional.empty();
        }

        @Override
        public Optional<UserProfile> loadUserProfileByGithubId(long githubId) {
            return Optional.empty();
        }
    }
}
