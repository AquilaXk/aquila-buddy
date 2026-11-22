package com.aquilabuddy.global.github;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubCodeContextSupportTest {

    @Test
    void extractsCamelCaseSnakeCaseAndPathAnchorTokensInSinglePassWithinCap() {
        String query = "Fix tokenParser and session_timeout_ms in src/auth/jwt_helper.ts for good first issue";

        List<String> tokens = GitHubCodeContextSupport.codeContextQueryTokens(query);

        assertThat(tokens)
            .contains("token", "parser", "tokenparser", "session", "timeout", "session_timeout_ms", "src/auth/jwt_helper")
            .doesNotContain("good", "first", "issue", "and", "for");

        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            oversized.append("anchorToken").append(i).append(' ');
        }
        assertThat(GitHubCodeContextSupport.codeContextQueryTokens(oversized.toString()))
            .hasSizeLessThanOrEqualTo(32);
    }

    @Test
    void excludesLockfilesAssetsAndVendorDirectoriesFromTreeCandidates() {
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("package-lock.json")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("frontend/pnpm-lock.yaml")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("rust/Cargo.lock")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("go.sum")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("yarn.lock")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("dist/bundle.min.js")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("node_modules/pkg/index.js")).isFalse();
        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("../secret.java")).isFalse();

        assertThat(GitHubCodeContextSupport.isRepositoryTreeCodeCandidate("src/main/java/AuthService.java")).isTrue();
    }

    @Test
    void scoresImplementationSourceHigherThanDocsAndTestsForSameAnchor() {
        List<String> tokens = GitHubCodeContextSupport.codeContextQueryTokens("authService token");
        List<GitHubCodeContextSupport.TreeItem> tree = List.of(
            new GitHubCodeContextSupport.TreeItem("docs/auth.md", "blob", "sha-1"),
            new GitHubCodeContextSupport.TreeItem("src/test/java/AuthTest.java", "blob", "sha-2"),
            new GitHubCodeContextSupport.TreeItem("src/main/java/AuthService.java", "blob", "sha-3"),
            new GitHubCodeContextSupport.TreeItem("package-lock.json", "blob", "sha-4")
        );

        List<GitHubCodeContextSupport.TreeCandidate> ranked = GitHubCodeContextSupport.extractTreeCandidates(tree, tokens);

        assertThat(ranked)
            .extracting(GitHubCodeContextSupport.TreeCandidate::path)
            .containsExactly(
                "src/main/java/AuthService.java",
                "src/test/java/AuthTest.java",
                "docs/auth.md"
            );
    }
}
