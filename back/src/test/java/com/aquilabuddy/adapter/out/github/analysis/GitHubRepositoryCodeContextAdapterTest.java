package com.aquilabuddy.adapter.out.github.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.global.github.GitHubApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubRepositoryCodeContextAdapterTest {

    @Test
    void loadsRepositoryCodeContextFromGitHubClientWithIssueQueryAndLimits() {
        RecordingGitHubApiClient client = new RecordingGitHubApiClient();
        client.response = new GitHubApiClient.GitHubRepositoryCodeContext(
            "spring-projects/spring-boot",
            "default",
            List.of(
                new GitHubApiClient.GitHubRepositoryCodeContextFile(
                    "src/main/java/ValidationController.java",
                    "class ValidationController {}",
                    false
                )
            )
        );
        GitHubRepositoryCodeContextAdapter adapter = new GitHubRepositoryCodeContextAdapter(client, 3, 12);

        RepositoryCodeContext context = adapter.loadRepositoryCodeContext(issue());

        assertThat(client.upstreamRepo).isEqualTo("spring-projects/spring-boot");
        assertThat(client.query)
            .contains("Refine validation error example")
            .contains("good first issue")
            .contains("validation");
        assertThat(client.maxFiles).isEqualTo(3);
        assertThat(client.maxContentChars).isEqualTo(12);
        assertThat(context.repository()).isEqualTo("spring-projects/spring-boot");
        assertThat(context.files()).hasSize(1);
        assertThat(context.files().getFirst().path()).isEqualTo("src/main/java/ValidationController.java");
        assertThat(context.files().getFirst().content()).isEqualTo("class Valida...");
        assertThat(context.files().getFirst().truncated()).isTrue();
    }

    @Test
    void returnsEmptyContextWhenGitHubClientFails() {
        RecordingGitHubApiClient client = new RecordingGitHubApiClient();
        client.fail = true;
        GitHubRepositoryCodeContextAdapter adapter = new GitHubRepositoryCodeContextAdapter(client, 3, 100);

        RepositoryCodeContext context = adapter.loadRepositoryCodeContext(issue());

        assertThat(context.hasFiles()).isFalse();
    }

    @Test
    void normalizesInvalidLimitsBeforeLoadingRepositoryCodeContext() {
        RecordingGitHubApiClient client = new RecordingGitHubApiClient();
        GitHubRepositoryCodeContextAdapter adapter = new GitHubRepositoryCodeContextAdapter(client, 0, -10);

        adapter.loadRepositoryCodeContext(issue());

        assertThat(client.maxFiles).isEqualTo(5);
        assertThat(client.maxContentChars).isEqualTo(3000);
    }

    @Test
    void enrichesSearchQueryWithCamelCaseSnakeCaseAndPathAnchorTokens() {
        RecordingGitHubApiClient client = new RecordingGitHubApiClient();
        GitHubRepositoryCodeContextAdapter adapter = new GitHubRepositoryCodeContextAdapter(client, 5, 3000);
        RecommendedIssue issue = new RecommendedIssue(
            "demo-9",
            "spring-projects/spring-boot",
            9L,
            "Fix tokenParser in src/auth/jwt_helper.ts",
            "Investigate session_timeout_ms fallback",
            0.9,
            List.of("good first issue"),
            "OPEN",
            "주 사용 언어 일치"
        );

        adapter.loadRepositoryCodeContext(issue);

        assertThat(client.query)
            .contains("tokenParser")
            .contains("token")
            .contains("parser")
            .contains("src/auth/jwt_helper.ts")
            .contains("session_timeout_ms");
    }

    private static RecommendedIssue issue() {
        return new RecommendedIssue(
            "demo-2",
            "spring-projects/spring-boot",
            2L,
            "Refine validation error example",
            "Java/Spring validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        );
    }

    private static class RecordingGitHubApiClient extends GitHubApiClient {

        private String upstreamRepo;
        private String query;
        private int maxFiles;
        private int maxContentChars;
        private boolean fail;
        private GitHubRepositoryCodeContext response = new GitHubRepositoryCodeContext(
            "spring-projects/spring-boot",
            "default",
            List.of()
        );

        RecordingGitHubApiClient() {
            super(RestClient.builder(), "http://localhost", "");
        }

        @Override
        public GitHubRepositoryCodeContext loadRepositoryCodeContext(
            String upstreamRepo,
            String query,
            int maxFiles,
            int maxContentChars
        ) {
            if (fail) {
                throw new IllegalStateException("GitHub API failed");
            }
            this.upstreamRepo = upstreamRepo;
            this.query = query;
            this.maxFiles = maxFiles;
            this.maxContentChars = maxContentChars;
            return response;
        }
    }
}
