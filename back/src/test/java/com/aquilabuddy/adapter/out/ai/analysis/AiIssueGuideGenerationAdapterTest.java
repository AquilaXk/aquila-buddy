package com.aquilabuddy.adapter.out.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiIssueGuideGenerationAdapterTest {

    @Test
    void generatesIssueGuideFromAiResponseMarkers() {
        CapturingAiClient aiClient = new CapturingAiClient(String.join("\n",
            "GUIDELINE: Validate the request contract before changing implementation.",
            "PSEUDO_CODE:",
            "1. Find validation entry point",
            "2. Add focused regression test",
            "SIDE_EFFECTS:",
            "- API error wording can change",
            "- Documentation examples may need the same term"
        ));
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(aiClient);

        IssueGuide guide = adapter.generateIssueGuide(issue());

        assertThat(guide.issueKey()).isEqualTo("demo-2");
        assertThat(guide.guideline()).contains("Validate the request contract");
        assertThat(guide.pseudoCode()).contains("Find validation entry point");
        assertThat(guide.sideEffects()).containsExactly(
            "API error wording can change",
            "Documentation examples may need the same term"
        );
        assertThat(aiClient.systemPrompt).contains("issue guide");
        assertThat(aiClient.userPrompt)
            .contains("spring-projects/spring-boot")
            .contains("Refine validation error example")
            .contains("good first issue, validation");
    }

    @Test
    void includesRepositoryCodeContextInPromptWhenAvailable() {
        CapturingAiClient aiClient = new CapturingAiClient(String.join("\n",
            "GUIDELINE: Update validation handling near the controller.",
            "PSEUDO_CODE:",
            "1. Inspect ValidationController",
            "SIDE_EFFECTS:",
            "- Controller response wording can change"
        ));
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(aiClient);
        RepositoryCodeContext codeContext = RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile("src/main/java/ValidationController.java", "class ValidationController {}")),
            3,
            200
        );

        adapter.generateIssueGuide(issue(), codeContext);

        assertThat(aiClient.systemPrompt)
            .contains("Base GUIDELINE, PSEUDO_CODE, and SIDE_EFFECTS on repository code context when it is present.");
        assertThat(aiClient.userPrompt)
            .contains("Repository code context: spring-projects/spring-boot@main")
            .contains("File: src/main/java/ValidationController.java")
            .contains("class ValidationController {}");
    }

    @Test
    void rejectsAiResponseWithoutRequiredMarkers() {
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(new CapturingAiClient("plain text"));

        assertThatThrownBy(() -> adapter.generateIssueGuide(issue()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GUIDELINE");
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

    private static class CapturingAiClient implements AiClient {

        private final String response;
        private String systemPrompt;
        private String userPrompt;

        private CapturingAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return response;
        }
    }
}
