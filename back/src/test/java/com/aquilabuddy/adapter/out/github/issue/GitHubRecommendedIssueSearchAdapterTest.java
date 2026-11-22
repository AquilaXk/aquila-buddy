package com.aquilabuddy.adapter.out.github.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
    void retriesWithoutAuthorizationWhenConfiguredTokenIsUnauthorized() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> retryAuthorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search/issues", exchange -> {
            int attempt = requests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (attempt == 1) {
                byte[] body = "{\"message\":\"Bad credentials\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }

            retryAuthorization.set(authorization);
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 789,
                      "number": 11,
                      "title": "Fix TypeScript docs",
                      "body": "Good first issue for TypeScript contributors.",
                      "repository_url": "https://api.github.com/repos/example/typescript-lib",
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "expired-token",
            true,
            "label:\"good first issue\"",
            1
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues("language:typescript label:\"good first issue\"");

        assertThat(requests).hasValue(2);
        assertThat(retryAuthorization.get()).isNull();
        assertThat(issues)
            .extracting(RecommendedIssue::repoFullName)
            .containsExactly("example/typescript-lib");
    }

    @Test
    void returnsEmptyListWhenGitHubSearchStillFails() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search/issues", exchange -> {
            byte[] body = "{\"message\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
            RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "",
            true,
            "label:\"good first issue\"",
            1
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues("language:java label:\"good first issue\"");

        assertThat(issues).isEmpty();
    }

    @Test
    void mapsGitHubSearchResponseToRecommendedIssues() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
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

    @Test
    void collectsUnassignedIssuesWithStarAndCommitMetadataViaGraphQL() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                {
                  "data": {
                    "search": {
                      "issueCount": 2,
                      "nodes": [
                        {
                          "id": "I_kw123",
                          "number": 101,
                          "title": "Onboarding task for new contributors",
                          "body": "Welcome to open source! Fix this beginner issue.",
                          "url": "https://github.com/spring-projects/spring-boot/issues/101",
                          "state": "OPEN",
                          "assignees": { "totalCount": 0 },
                          "labels": {
                            "nodes": [
                              { "name": "good first issue" },
                              { "name": "java" }
                            ]
                          },
                          "repository": {
                            "nameWithOwner": "spring-projects/spring-boot",
                            "stargazerCount": 1250,
                            "defaultBranchRef": {
                              "target": {
                                "committedDate": "2026-05-18T10:00:00Z"
                              }
                            }
                          }
                        },
                        {
                          "id": "I_kw456",
                          "number": 102,
                          "title": "Already assigned issue",
                          "body": "Someone is already working on this.",
                          "url": "https://github.com/spring-projects/spring-boot/issues/102",
                          "state": "OPEN",
                          "assignees": { "totalCount": 1 },
                          "labels": {
                            "nodes": [
                              { "name": "good first issue" }
                            ]
                          },
                          "repository": {
                            "nameWithOwner": "spring-projects/spring-boot",
                            "stargazerCount": 1250,
                            "defaultBranchRef": {
                              "target": {
                                "committedDate": "2026-05-18T10:00:00Z"
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "valid-graphql-token",
            true,
            "label:\"good first issue\"",
            2
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues("language:java label:\"good first issue\"");

        assertThat(authorization.get()).isEqualTo("Bearer valid-graphql-token");
        // 미할당 이슈만 수집되고 할당된 이슈(totalCount: 1)는 필터링되어야 합니다.
        assertThat(issues).hasSize(1);
        RecommendedIssue issue = issues.getFirst();
        assertThat(issue.id()).isEqualTo("I_kw123");
        assertThat(issue.repoFullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(issue.issueNumber()).isEqualTo(101);
        assertThat(issue.title()).isEqualTo("Onboarding task for new contributors");
        assertThat(issue.labels()).containsExactly("good first issue", "java");
        assertThat(issue.reason()).contains("미할당");
        assertThat(issue.reason()).contains("스타수: 1.3k");
        assertThat(issue.reason()).contains("최근 커밋: 2026-05-18");
    }

    @Test
    void fallsBackToRestWhenGraphQLFails() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] body = "{\"message\":\"GraphQL internal error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/search/issues", exchange -> {
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 999,
                      "number": 55,
                      "title": "REST fallback issue",
                      "body": "Collected via REST fallback when GraphQL fails.",
                      "repository_url": "https://api.github.com/repos/example/rest-fallback",
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "token-with-failing-graphql",
            true,
            "label:\"good first issue\"",
            1
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().repoFullName()).isEqualTo("example/rest-fallback");
        assertThat(issues.getFirst().reason()).isEqualTo("GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다.");
    }

    @Test
    void ignoresNullAndNonIssueNodesInGraphQLSearchResults() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] body = """
                {
                  "data": {
                    "search": {
                      "issueCount": 3,
                      "nodes": [
                        null,
                        {
                          "id": "PR_123",
                          "number": null,
                          "title": null
                        },
                        {
                          "id": "I_valid",
                          "number": 200,
                          "title": "Valid unassigned issue",
                          "body": "Valid description",
                          "url": "https://github.com/valid/repo/issues/200",
                          "state": "OPEN",
                          "assignees": { "totalCount": 0 },
                          "labels": { "nodes": [] },
                          "repository": {
                            "nameWithOwner": "valid/repo",
                            "stargazerCount": 50,
                            "defaultBranchRef": null
                          }
                        }
                      ]
                    }
                  }
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "valid-token",
            true,
            "label:\"good first issue\"",
            5
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().id()).isEqualTo("I_valid");
        assertThat(issues.getFirst().repoFullName()).isEqualTo("valid/repo");
        assertThat(issues.getFirst().issueNumber()).isEqualTo(200);
    }

    @Test
    void handlesNullElementsInGraphQLNodesLabelsGracefully() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] body = """
                {
                  "data": {
                    "search": {
                      "issueCount": 1,
                      "nodes": [
                        {
                          "id": "I_with_null_labels",
                          "number": 300,
                          "title": "Issue with null label node",
                          "body": "Description with null labels in list",
                          "url": "https://github.com/valid/repo/issues/300",
                          "state": "OPEN",
                          "assignees": { "totalCount": 0 },
                          "labels": {
                            "nodes": [
                              null,
                              { "name": "good first issue" },
                              null,
                              { "name": "" }
                            ]
                          },
                          "repository": {
                            "nameWithOwner": "valid/repo",
                            "stargazerCount": 100,
                            "defaultBranchRef": null
                          }
                        }
                      ]
                    }
                  }
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "valid-token",
            true,
            "label:\"good first issue\"",
            5
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().id()).isEqualTo("I_with_null_labels");
        assertThat(issues.getFirst().labels()).containsExactly("good first issue");
    }

    @Test
    void fallsBackToRestWhenGraphQLThrowsMalformedJsonException() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] body = "malformed json response that is not json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/search/issues", exchange -> {
            byte[] body = """
                {
                  "items": [
                    {
                      "id": 888,
                      "number": 88,
                      "title": "Fallback from malformed GraphQL",
                      "body": "Recovered from malformed GraphQL payload.",
                      "repository_url": "https://api.github.com/repos/example/malformed-fallback",
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
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "valid-token",
            true,
            "label:\"good first issue\"",
            1
        );

        List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().repoFullName()).isEqualTo("example/malformed-fallback");
    }

    @Test
    void appendsArchivedFalseAndNinetyDayCreatedCutoffUsingInjectedClock() throws Exception {
        java.time.Clock fixedClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-06-15T00:00:00Z"),
            java.time.ZoneOffset.UTC
        );
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "data": {
                        "search": {
                          "issueCount": 1,
                          "nodes": [
                            {
                              "id": "issue-gql-90",
                              "number": 90,
                              "title": "Fresh GraphQL issue",
                              "body": "Body",
                              "url": "https://github.com/example/repo/issues/90",
                              "state": "OPEN",
                              "assignees": { "totalCount": 0 },
                              "labels": { "nodes": [ { "name": "good first issue" } ] },
                              "timelineItems": { "nodes": [] },
                              "repository": {
                                "nameWithOwner": "example/repo",
                                "stargazerCount": 250,
                                "defaultBranchRef": { "target": { "committedDate": "2026-06-10T10:00:00Z" } }
                              }
                            }
                          ]
                        }
                      }
                    }
                    """));
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "items": [
                        {
                          "id": 91,
                          "number": 91,
                          "title": "Fresh REST issue",
                          "body": "Body",
                          "repository_url": "https://api.github.com/repos/example/rest-repo",
                          "labels": [ { "name": "good first issue" } ]
                        }
                      ]
                    }
                    """));
            server.start();

            GitHubRecommendedIssueSearchAdapter graphQlAdapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                server.url("/").toString(),
                "test-token",
                true,
                "label:\"good first issue\"",
                5,
                true,
                fixedClock
            );
            List<RecommendedIssue> graphQlIssues = graphQlAdapter.searchRecommendedIssues();
            assertThat(graphQlIssues).hasSize(1);

            RecordedRequest graphQlRequest = server.takeRequest();
            assertThat(graphQlRequest.getPath()).isEqualTo("/graphql");
            assertThat(graphQlRequest.getBody().readUtf8())
                .contains("no:assignee archived:false created:>=2026-03-17");

            GitHubRecommendedIssueSearchAdapter restAdapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                server.url("/").toString(),
                "",
                true,
                "label:\"good first issue\"",
                5,
                false,
                fixedClock
            );
            List<RecommendedIssue> restIssues = restAdapter.searchRecommendedIssues();
            assertThat(restIssues).hasSize(1);

            RecordedRequest restRequest = server.takeRequest();
            String decodedQuery = java.net.URLDecoder.decode(
                restRequest.getRequestUrl().queryParameter("q"),
                java.nio.charset.StandardCharsets.UTF_8
            );
            assertThat(decodedQuery).contains("archived:false created:>=2026-03-17");
        }
    }

    @Test
    void excludesGraphQlIssuesWithOpenOrMergedPullRequestWhileKeepingClosedUnmergedPullRequest() throws Exception {
        java.time.Clock fixedClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-06-15T00:00:00Z"),
            java.time.ZoneOffset.UTC
        );
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "data": {
                        "search": {
                          "issueCount": 4,
                          "nodes": [
                            {
                              "id": "issue-connected-open",
                              "number": 10,
                              "title": "Has connected OPEN PR",
                              "body": "Exclude me",
                              "url": "https://github.com/example/repo/issues/10",
                              "state": "OPEN",
                              "assignees": { "totalCount": 0 },
                              "labels": { "nodes": [] },
                              "timelineItems": {
                                "nodes": [
                                  { "subject": { "state": "OPEN", "merged": false } }
                                ]
                              },
                              "repository": { "nameWithOwner": "example/repo", "stargazerCount": 100 }
                            },
                            {
                              "id": "issue-crossref-merged",
                              "number": 11,
                              "title": "Has cross-referenced MERGED closing PR",
                              "body": "Exclude me",
                              "url": "https://github.com/example/repo/issues/11",
                              "state": "OPEN",
                              "assignees": { "totalCount": 0 },
                              "labels": { "nodes": [] },
                              "timelineItems": {
                                "nodes": [
                                  { "willCloseTarget": true, "source": { "state": "MERGED", "merged": true } }
                                ]
                              },
                              "repository": { "nameWithOwner": "example/repo", "stargazerCount": 100 }
                            },
                            {
                              "id": "issue-crossref-nonclosing",
                              "number": 12,
                              "title": "Mentioned only in non-closing PR",
                              "body": "Keep me",
                              "url": "https://github.com/example/repo/issues/12",
                              "state": "OPEN",
                              "assignees": { "totalCount": 0 },
                              "labels": { "nodes": [ { "name": "good first issue" } ] },
                              "timelineItems": {
                                "nodes": [
                                  { "willCloseTarget": false, "source": { "state": "OPEN", "merged": false } }
                                ]
                              },
                              "repository": { "nameWithOwner": "example/repo", "stargazerCount": 120, "licenseInfo": { "spdxId": "MIT" } }
                            },
                            {
                              "id": "issue-closed-unmerged-pr",
                              "number": 13,
                              "title": "Linked PR was closed without merge",
                              "body": "Keep me",
                              "url": "https://github.com/example/repo/issues/13",
                              "state": "OPEN",
                              "assignees": { "totalCount": 0 },
                              "labels": { "nodes": [ { "name": "good first issue" } ] },
                              "timelineItems": {
                                "nodes": [
                                  { "willCloseTarget": true, "source": { "state": "CLOSED", "merged": false } }
                                ]
                              },
                              "repository": { "nameWithOwner": "example/repo", "stargazerCount": 150, "licenseInfo": { "spdxId": "NOASSERTION" } }
                            }
                          ]
                        }
                      }
                    }
                    """));
            server.start();

            GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                server.url("/").toString(),
                "test-token",
                true,
                "label:\"good first issue\"",
                10,
                true,
                fixedClock
            );

            List<RecommendedIssue> issues = adapter.searchRecommendedIssues();

            assertThat(issues)
                .extracting(RecommendedIssue::issueNumber)
                .containsExactly(12L, 13L);
            assertThat(issues.get(0).reason()).contains("라이선스: MIT");
            assertThat(issues.get(1).reason()).doesNotContain("NOASSERTION").doesNotContain("라이선스:");
            assertThat(issues.get(0).reason()).contains("라이선스: MIT");
        }
    }

    @Test
    void restFallbackIncludesNoAssigneeQualifier() throws Exception {
        java.time.Clock fixedClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-06-15T00:00:00Z"),
            java.time.ZoneOffset.UTC
        );
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"items\": []}"));
            server.start();

            GitHubRecommendedIssueSearchAdapter adapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                server.url("/").toString(),
                "",
                true,
                "label:\"good first issue\"",
                5,
                false,
                fixedClock
            );

            assertThat(adapter.searchRecommendedIssues()).isEmpty();
            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.getRequestUrl().queryParameter("q")).contains("no:assignee");
        }
    }

    @Test
    void entersCooldownOnRetryAfterAndRemainingZeroWithoutExtraHttpCalls() throws Exception {
        java.time.Clock fixedClock = java.time.Clock.fixed(
            java.time.Instant.parse("2026-06-15T00:00:00Z"),
            java.time.ZoneOffset.UTC
        );
        try (MockWebServer retryAfterServer = new MockWebServer()) {
            retryAfterServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "120"));
            retryAfterServer.start();

            GitHubRecommendedIssueSearchAdapter retryAfterAdapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                retryAfterServer.url("/").toString(),
                "",
                true,
                "label:\"good first issue\"",
                5,
                false,
                fixedClock
            );

            assertThat(retryAfterAdapter.searchRecommendedIssues()).isEmpty();
            assertThat(retryAfterAdapter.isRateLimitCoolingDown()).isTrue();
            assertThat(retryAfterAdapter.searchRecommendedIssues()).isEmpty();
            assertThat(retryAfterServer.getRequestCount()).isEqualTo(1);
        }

        try (MockWebServer remainingZeroServer = new MockWebServer()) {
            long resetEpochSeconds = java.time.Instant.parse("2026-06-15T00:02:00Z").getEpochSecond();
            remainingZeroServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-RateLimit-Remaining", "0")
                .addHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds))
                .setBody("""
                    {
                      "items": [
                        {
                          "id": 200,
                          "number": 200,
                          "title": "Last permitted response",
                          "body": "Body",
                          "repository_url": "https://api.github.com/repos/example/last",
                          "labels": []
                        }
                      ]
                    }
                    """));
            remainingZeroServer.start();

            GitHubRecommendedIssueSearchAdapter remainingZeroAdapter = new GitHubRecommendedIssueSearchAdapter(
                RestClient.builder(),
                remainingZeroServer.url("/").toString(),
                "",
                true,
                "label:\"good first issue\"",
                5,
                false,
                fixedClock
            );

            assertThat(remainingZeroAdapter.searchRecommendedIssues()).hasSize(1);
            assertThat(remainingZeroAdapter.isRateLimitCoolingDown()).isTrue();
            assertThat(remainingZeroAdapter.searchRecommendedIssues()).isEmpty();
            assertThat(remainingZeroServer.getRequestCount()).isEqualTo(1);
        }
    }
}
