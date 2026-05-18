package com.aquilabuddy.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryCodeContextTest {

    @Test
    void limitsFilesAndTruncatesLargeContentForPrompt() {
        RepositoryCodeContext context = RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(
                new RepositoryCodeContextFile("src/main/java/App.java", repeat("a", 20)),
                new RepositoryCodeContextFile("src/test/java/AppTest.java", "assert behavior"),
                new RepositoryCodeContextFile("README.md", "ignored by max file count")
            ),
            2,
            10
        );

        assertThat(context.repository()).isEqualTo("spring-projects/spring-boot");
        assertThat(context.branch()).isEqualTo("main");
        assertThat(context.files()).hasSize(2);
        assertThat(context.files().getFirst().content()).isEqualTo("aaaaaaaaaa...");
        assertThat(context.files().getFirst().truncated()).isTrue();
        assertThat(context.files().get(1).content()).isEqualTo("assert behavior");
        assertThat(context.files().get(1).truncated()).isFalse();
    }

    @Test
    void exposesStablePromptSummaryLines() {
        RepositoryCodeContext context = RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/App.java", "class App {}")),
            5,
            100
        );

        assertThat(context.promptSummaryLines()).containsExactly(
            "Repository code context: spring-projects/spring-boot@main",
            "File: src/main/java/App.java",
            "Content:",
            "class App {}"
        );
    }

    @Test
    void emptyContextHasNoPromptLines() {
        assertThat(RepositoryCodeContext.empty().hasFiles()).isFalse();
        assertThat(RepositoryCodeContext.empty().promptSummaryLines()).isEmpty();
    }

    private static String repeat(String value, int count) {
        return value.repeat(count);
    }
}
