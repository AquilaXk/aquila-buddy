package com.aquilabuddy.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.adapter.out.ai.analysis.AiIssueGuideGenerationAdapter;
import com.aquilabuddy.adapter.out.ai.prdraft.AiPullRequestDraftAdapter;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 생성물이 실제 제출 흐름에 필요한 최소 품질을 유지하는지 고정하는 회귀 평가입니다.
 */
class AiGeneratedOutputQualityRegressionTest {

    private static final String PROMPT_VERSION = "ai-output-quality-v2026-05-18";
    private static final String MODEL_UNDER_TEST = "openai-compatible-contract";

    @Test
    @DisplayName("[prompt=ai-output-quality-v2026-05-18 model=openai-compatible-contract fixture=issue-guide-code-context] issue guide keeps non-empty file paths and implementation sections")
    void evaluatesIssueGuideFixtureQuality() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            GUIDELINE: Update the validation controller and keep the API error envelope stable.
            PSEUDO_CODE:
            1. Open ValidationController and locate the invalid input branch.
            2. Add a regression test for the new message.
            3. Return the existing response envelope with the clearer detail.
            SIDE_EFFECTS:
            - Existing API clients may assert the previous message text.
            - Documentation examples should use the same wording.
            """);
        AiIssueGuideGenerationAdapter adapter = new AiIssueGuideGenerationAdapter(aiClient);

        IssueGuide guide = adapter.generateIssueGuide(issue(), codeContext());

        assertQualityMetadata("issue-guide-code-context");
        assertThat(guide.filePaths()).containsExactly("src/main/java/demo/ValidationController.java");
        assertThat(guide.guideline()).contains("validation controller").isNotBlank();
        assertThat(guide.pseudoCode()).contains("regression test").isNotBlank();
        assertThat(guide.sideEffects()).hasSize(2).allSatisfy(sideEffect -> assertThat(sideEffect).isNotBlank());
        assertThat(aiClient.userPrompt)
            .contains("Repository code context")
            .contains("ValidationController.java");
    }

    @Test
    @DisplayName("[prompt=ai-output-quality-v2026-05-18 model=openai-compatible-contract fixture=pr-draft-markdown-close] PR draft keeps markdown sections, close line, diff, issue, and analysis context")
    void evaluatesPullRequestDraftFixtureQuality() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Clarify the validation message for the linked issue.

            ## Changes
            - Add validation example from the diff.
            - Apply the issue guide implementation note.

            ## Tests
            - Run the focused validation regression test.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.generate(request());

        assertQualityMetadata("pr-draft-markdown-close");
        assertSubmittablePullRequestDraft(draft, 2);
        assertThat(draft.body())
            .contains("## Summary")
            .contains("## Changes")
            .contains("## Tests")
            .contains("validation message")
            .contains("validation example")
            .contains("implementation note");
        assertThat(aiClient.userPrompt)
            .contains("Diff:")
            .contains("+Add validation example")
            .contains("Issue context:")
            .contains("Analysis context:");
    }

    @Test
    @DisplayName("[prompt=ai-output-quality-v2026-05-18 model=openai-compatible-contract fixture=pr-translation-english] translation returns English title/body for submission")
    void evaluatesPullRequestTranslationFixtureQuality() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: prepare validation guide update
            BODY: ## Summary
            - Translate the source draft into an English submission.

            ## Changes
            - Keep the validation guide context.

            ## Tests
            - Run the same verification before opening the PR.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.translate(storedDraft(), "en");

        assertQualityMetadata("pr-translation-english");
        assertSubmittablePullRequestDraft(draft, 123);
        assertThat(draft.title()).contains("validation guide").doesNotContain("검증");
        assertThat(draft.body())
            .contains("## Summary")
            .contains("English submission")
            .doesNotContain("README 안내를 보완합니다");
        assertThat(aiClient.userPrompt)
            .contains("Target language: en")
            .contains("Source title:")
            .contains("Source body:");
    }

    @Test
    @DisplayName("[prompt=ai-output-quality-v2026-05-18 model=openai-compatible-contract fixture=pr-draft-parser-fallback] malformed PR draft response returns rule based markdown")
    void evaluatesPullRequestDraftParserFallbackQuality() {
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(new CapturingAiClient("plain text"));

        PullRequestDraft draft = adapter.generate(request());

        assertQualityMetadata("pr-draft-parser-fallback");
        assertSubmittablePullRequestDraft(draft, 2);
        assertThat(draft.body())
            .contains("## Summary")
            .contains("## Changes")
            .contains("## Tests")
            .contains("Fix validation message")
            .contains("Follow validation guide.");
    }

    private static void assertSubmittablePullRequestDraft(PullRequestDraft draft, long issueNumber) {
        assertThat(draft.title()).isNotBlank();
        assertThat(draft.body())
            .isNotBlank()
            .contains("close #" + issueNumber)
            .containsPattern("(?m)^##\\s+\\S+");
    }

    private static void assertQualityMetadata(String fixtureName) {
        assertThat(PROMPT_VERSION).isEqualTo("ai-output-quality-v2026-05-18");
        assertThat(MODEL_UNDER_TEST).isEqualTo("openai-compatible-contract");
        assertThat(fixtureName).isNotBlank();
    }

    private static PullRequestDraftRequest request() {
        return new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                "Run tests before PR.",
                "## Summary\n- Describe changes",
                List.of("## Summary\n- Improve docs")
            ),
            new PullRequestDraftIssueContext(
                "Fix validation message",
                "Error message should explain invalid input.",
                "Matches Java validation topic"
            ),
            new PullRequestDraftAnalysisContext(
                "Follow validation guide.",
                "if invalid then return helpful message",
                List.of("Apply the issue guide implementation note.")
            )
        );
    }

    private static StoredPullRequestDraft storedDraft() {
        return new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "[Docs] README 기여 안내 보완",
            "close #123\n\n## 변경 사항\n- README 안내를 보완합니다.",
            Instant.parse("2026-05-15T01:00:00Z")
        );
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

    private static RepositoryCodeContext codeContext() {
        return RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new RepositoryCodeContextFile(
                "src/main/java/demo/ValidationController.java",
                "class ValidationController { void validate() {} }"
            )),
            5,
            240
        );
    }

    private static class CapturingAiClient implements AiClient {

        private final String response;
        private String userPrompt;

        private CapturingAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.userPrompt = userPrompt;
            return response;
        }
    }
}
