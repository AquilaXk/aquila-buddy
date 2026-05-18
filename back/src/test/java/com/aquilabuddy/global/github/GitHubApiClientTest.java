package com.aquilabuddy.global.github;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
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

    @Test
    void buildsContributionContextText() {
        GitHubApiClient client = client();
        GitHubApiClient.GitHubUserProfile profile = new GitHubApiClient.GitHubUserProfile(
            "aquila-dev",
            "Open source contributor",
            12
        );
        List<GitHubApiClient.GitHubRepository> repositories = List.of(
            new GitHubApiClient.GitHubRepository("api", "Spring API", "Java", List.of("spring"), 7),
            new GitHubApiClient.GitHubRepository("web", "Next app", "TypeScript", List.of("nextjs", "oss"), 5),
            new GitHubApiClient.GitHubRepository("docs", null, null, List.of("docs"), 1)
        );
        Map<String, Integer> languages = new LinkedHashMap<>();
        languages.put("Java", 1);
        languages.put("TypeScript", 1);

        String text = client.buildContributionContextText(profile, repositories, languages);

        assertThat(text).contains("Developer GitHub Profile");
        assertThat(text).contains("Primary Programming Languages: Java, TypeScript");
        assertThat(text).contains("Bio: Open source contributor");
        assertThat(text).contains("- api (Java, Spring API) [spring]");
        assertThat(text).contains("- web (TypeScript, Next app) [nextjs, oss]");
        assertThat(text).contains("Technical Interests: spring, nextjs, oss, docs");
    }

    @Test
    void loadsContributionContextFromProfileAndRepositories() {
        route("/users/aquila-dev", 200, """
            {
              "login": "aquila-dev",
              "bio": "Open source contributor",
              "public_repos": 12
            }
            """);
        route("/users/aquila-dev/repos", 200, """
            [
              {
                "name": "api",
                "description": "Spring API",
                "language": "Java",
                "topics": ["spring", "oss"],
                "stargazers_count": 7,
                "fork": false
              },
              {
                "name": "worker",
                "description": "Batch worker",
                "language": "Java",
                "topics": ["batch"],
                "stargazers_count": 5,
                "fork": false
              },
              {
                "name": "web",
                "description": "Next app",
                "language": "TypeScript",
                "topics": ["nextjs", "oss"],
                "stargazers_count": 3,
                "fork": false
              }
            ]
            """);
        GitHubApiClient client = client();

        GitHubApiClient.GitHubContributionContext context = client.loadContributionContext("aquila-dev");

        assertThat(context.primaryLanguages()).containsExactly("Java", "TypeScript");
        assertThat(context.topics()).containsExactly("spring", "oss", "batch", "nextjs");
        assertThat(context.text()).contains("Primary Programming Languages: Java, TypeScript");
        assertThat(context.text()).contains("Technical Interests: spring, oss, batch, nextjs");
    }

    @Test
    void loadsCompareDiffFromPatchFiles() {
        route("/repos/spring-projects/spring-boot/compare/main...aquila-dev:feat/validation-docs", 200, """
            {
              "files": [
                {
                  "filename": "docs.adoc",
                  "patch": "@@ -1 +1 @@\\n+Add validation example"
                },
                {
                  "filename": "image.png",
                  "patch": null
                },
                {
                  "filename": "README.md",
                  "patch": "@@ -3 +3 @@\\n+Update README"
                }
              ]
            }
            """);
        GitHubApiClient client = client();

        Optional<String> diff = client.getCompareDiff(
            "spring-projects/spring-boot",
            "main",
            "aquila-dev:feat/validation-docs"
        );

        assertThat(diff).contains("""
            --- docs.adoc
            @@ -1 +1 @@
            +Add validation example
            --- README.md
            @@ -3 +3 @@
            +Update README""");
    }

    @Test
    void loadsRepositoryGuideContextFromContentsFiles() {
        route("/repos/spring-projects/spring-boot/contents/CONTRIBUTING.md", 200, """
            {
              "content": "UGxlYXNlIHJ1biB0ZXN0cyBiZWZvcmUgb3BlbmluZyBhIFBSLg=="
            }
            """);
        route("/repos/spring-projects/spring-boot/contents/.github/pull_request_template.md", 200, """
            {
              "content": "IyMgU3VtbWFyeQotIERlc2NyaWJlIGNoYW5nZXM="
            }
            """);
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryGuideContext context =
            client.loadRepositoryGuideContext("spring-projects/spring-boot");

        assertThat(context.contributing()).isEqualTo("Please run tests before opening a PR.");
        assertThat(context.pullRequestTemplate()).isEqualTo("## Summary\n- Describe changes");
        assertThat(context.mergedPullRequestSamples()).isEmpty();
    }

    @Test
    void loadsMergedPullRequestSamplesWhenRepositoryGuidesAreMissing() {
        route("/repos/spring-projects/spring-boot/pulls", 200, """
            [
              {
                "body": "## Summary\\n- Improve docs",
                "merged_at": "2026-05-15T01:00:00Z"
              },
              {
                "body": "",
                "merged_at": "2026-05-15T02:00:00Z"
              },
              {
                "body": "## Summary\\n- Fix validation",
                "merged_at": "2026-05-15T03:00:00Z"
              },
              {
                "body": "## Summary\\n- Closed without merge",
                "merged_at": null
              }
            ]
            """);
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryGuideContext context =
            client.loadRepositoryGuideContext("spring-projects/spring-boot");

        assertThat(context.contributing()).isNull();
        assertThat(context.pullRequestTemplate()).isNull();
        assertThat(context.mergedPullRequestSamples()).containsExactly(
            "## Summary\n- Improve docs",
            "## Summary\n- Fix validation"
        );
    }

    @Test
    void loadsRepositoryCodeContextFromCodeSearchAndContents() {
        route("/search/code", 200, """
            {
              "items": [
                {
                  "path": "src/main/java/ValidationController.java"
                },
                {
                  "path": "src/test/java/ValidationControllerTest.java"
                },
                {
                  "path": "README.md"
                }
              ]
            }
            """);
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationController.java",
            200,
            contentBody("class ValidationController {}")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/src/test/java/ValidationControllerTest.java",
            200,
            contentBody("assert validation response")
        );
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryCodeContext context =
            client.loadRepositoryCodeContext("spring-projects/spring-boot", "validation controller", 2, 12);

        assertThat(context.repository()).isEqualTo("spring-projects/spring-boot");
        assertThat(context.branch()).isEqualTo("default");
        assertThat(context.files()).hasSize(2);
        assertThat(context.files().getFirst().path()).isEqualTo("src/main/java/ValidationController.java");
        assertThat(context.files().getFirst().content()).isEqualTo("class Valida...");
        assertThat(context.files().getFirst().truncated()).isTrue();
        assertThat(context.files().get(1).path()).isEqualTo("src/test/java/ValidationControllerTest.java");
        assertThat(context.files().get(1).content()).isEqualTo("assert valid...");
    }

    @Test
    void loadsRepositoryCodeContextFromTreeWhenCodeSearchIsEmpty() {
        route("/search/code", 200, """
            {
              "items": []
            }
            """);
        route("/repos/spring-projects/spring-boot/git/trees/HEAD", 200, """
            {
              "tree": [
                {
                  "path": "src/main/java/ValidationController.java",
                  "type": "blob"
                }
              ]
            }
            """);
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationController.java",
            200,
            contentBody("class ValidationController { void validate() {} }")
        );
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryCodeContext context =
            client.loadRepositoryCodeContext("spring-projects/spring-boot", "validation controller", 2, 80);

        assertThat(context.repository()).isEqualTo("spring-projects/spring-boot");
        assertThat(context.files()).hasSize(1);
        assertThat(context.files().getFirst().path()).isEqualTo("src/main/java/ValidationController.java");
        assertThat(context.files().getFirst().content()).contains("ValidationController");
    }

    @Test
    void skipsVendorBuildAndAssetFilesWhenSelectingTreeCodeContext() {
        route("/search/code", 200, """
            {
              "items": []
            }
            """);
        route("/repos/spring-projects/spring-boot/git/trees/HEAD", 200, """
            {
              "tree": [
                {
                  "path": "node_modules/pkg/index.js",
                  "type": "blob"
                },
                {
                  "path": "build/generated/ValidationController.java",
                  "type": "blob"
                },
                {
                  "path": "src/main/resources/logo.png",
                  "type": "blob"
                },
                {
                  "path": "src/main/java/ValidationController.java",
                  "type": "blob"
                }
              ]
            }
            """);
        route(
            "/repos/spring-projects/spring-boot/contents/node_modules/pkg/index.js",
            200,
            contentBody("console.log('vendor')")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/build/generated/ValidationController.java",
            200,
            contentBody("class GeneratedValidationController {}")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/resources/logo.png",
            200,
            contentBody("png data")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationController.java",
            200,
            contentBody("class ValidationController { void validate() {} }")
        );
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryCodeContext context =
            client.loadRepositoryCodeContext("spring-projects/spring-boot", "validation controller", 1, 100);

        assertThat(context.files())
            .extracting(GitHubApiClient.GitHubRepositoryCodeContextFile::path)
            .containsExactly("src/main/java/ValidationController.java");
    }

    @Test
    void ranksTreeCodeContextCandidatesByIssueQueryTokensAndAppliesMaxFiles() {
        route("/search/code", 200, """
            {
              "items": []
            }
            """);
        route("/repos/spring-projects/spring-boot/git/trees/HEAD", 200, """
            {
              "tree": [
                {
                  "path": "src/main/java/HealthCheckEndpoint.java",
                  "type": "blob"
                },
                {
                  "path": "src/main/java/ValidationController.java",
                  "type": "blob"
                },
                {
                  "path": "src/main/java/ValidationService.java",
                  "type": "blob"
                }
              ]
            }
            """);
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/HealthCheckEndpoint.java",
            200,
            contentBody("class HealthCheckEndpoint {}")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationController.java",
            200,
            contentBody("class ValidationController { void validate() {} }")
        );
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationService.java",
            200,
            contentBody("class ValidationService { void validate() {} }")
        );
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryCodeContext context =
            client.loadRepositoryCodeContext("spring-projects/spring-boot", "validation controller", 2, 100);

        assertThat(context.files())
            .extracting(GitHubApiClient.GitHubRepositoryCodeContextFile::path)
            .containsExactly(
                "src/main/java/ValidationController.java",
                "src/main/java/ValidationService.java"
            );
    }

    @Test
    void fallsBackToTreeRepositoryCodeContextWhenCodeSearchFails() {
        route("/search/code", 403, """
            {"message": "API rate limit exceeded"}
            """);
        route("/repos/spring-projects/spring-boot/git/trees/HEAD", 200, """
            {
              "tree": [
                {
                  "path": "src/main/java/ValidationController.java",
                  "type": "blob"
                }
              ]
            }
            """);
        route(
            "/repos/spring-projects/spring-boot/contents/src/main/java/ValidationController.java",
            200,
            contentBody("class ValidationController { void validate() {} }")
        );
        GitHubApiClient client = client();

        GitHubApiClient.GitHubRepositoryCodeContext context =
            client.loadRepositoryCodeContext("spring-projects/spring-boot", "validation controller", 3, 100);

        assertThat(context.files())
            .extracting(GitHubApiClient.GitHubRepositoryCodeContextFile::path)
            .containsExactly("src/main/java/ValidationController.java");
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

    private String contentBody(String content) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return """
            {
              "content": "%s"
            }
            """.formatted(encoded);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
