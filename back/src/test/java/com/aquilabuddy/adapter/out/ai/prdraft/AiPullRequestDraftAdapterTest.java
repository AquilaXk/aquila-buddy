package com.aquilabuddy.adapter.out.ai.prdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiPullRequestDraftAdapterTest {

    @Test
    void generatesPullRequestDraftWithAiClientPrompt() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.generate(request());

        assertThat(draft.title()).isEqualTo("docs: improve validation guide");
        assertThat(draft.body()).contains("## Summary");
        assertThat(aiClient.systemPrompt).contains("PR 초안");
        assertThat(aiClient.userPrompt).contains("Repository: spring-projects/spring-boot");
        assertThat(aiClient.userPrompt).contains("Issue: #2");
        assertThat(aiClient.userPrompt).contains("Add validation example");
        assertThat(aiClient.userPrompt).contains("CONTRIBUTING: Run tests before PR.");
        assertThat(aiClient.userPrompt).contains("PR template:");
        assertThat(aiClient.userPrompt).contains("## Summary\n- Describe changes");
        assertThat(aiClient.userPrompt).contains("Merged PR sample #1:");
        assertThat(aiClient.userPrompt).contains("## Summary\n- Improve docs");
        assertThat(aiClient.userPrompt).contains("Issue title: Fix validation message");
        assertThat(aiClient.userPrompt).contains("Guideline: Follow validation guide.");
    }

    @Test
    void addsClosingIssueLineWhenGeneratedDraftBodyOmitsIt() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.generate(request());

        assertThat(draft.body()).startsWith("close #2\n\n## Summary");
    }

    @Test
    void doesNotDuplicateExistingGeneratedDraftClosingLine() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: Close #2

            ## Summary
            - Improve the validation guide.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.generate(request());

        assertThat(draft.body()).doesNotStartWith("close #2\n\nClose #2");
        assertThat(draft.body()).containsOnlyOnce("Close #2");
    }

    @Test
    void translatesStoredDraftWithAiClientPrompt() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: prepare validation guide update
            BODY: ## Summary
            - Translate the source draft.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.translate(storedDraft(), "EN");

        assertThat(draft.title()).isEqualTo("docs: prepare validation guide update");
        assertThat(draft.body()).contains("Translate the source draft");
        assertThat(aiClient.systemPrompt).contains("영문 PR 초안");
        assertThat(aiClient.userPrompt).contains("Target language: en");
        assertThat(aiClient.userPrompt).contains("Source title: [Docs] README 기여 안내 보완");
        assertThat(aiClient.userPrompt).contains("Source body:");
    }

    @Test
    void addsClosingIssueLineWhenTranslatedDraftBodyOmitsIt() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: prepare validation guide update
            BODY: ## Summary
            - Translate the source draft.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.translate(storedDraft(), "en");

        assertThat(draft.body()).startsWith("close #123\n\n## Summary");
    }

    @Test
    void fallsBackToDeterministicDraftWhenAiResponseIsMalformed() {
        CapturingAiClient aiClient = new CapturingAiClient("plain response without markers");
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.generate(request());

        assertThat(draft.title()).isEqualTo("docs: spring-projects/spring-boot#2 변경 초안");
        assertThat(draft.body())
            .contains("close #2")
            .contains("## Summary")
            .contains("## Changes")
            .contains("## Tests")
            .contains("Add validation example");
    }

    @Test
    void fallsBackToDeterministicEnglishTranslationWhenAiResponseIsMalformed() {
        CapturingAiClient aiClient = new CapturingAiClient("plain response without markers");
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        PullRequestDraft draft = adapter.translate(storedDraft(), "en");

        assertThat(draft.title()).isEqualTo("docs: translate pull request draft");
        assertThat(draft.body())
            .contains("## Summary")
            .contains("Translation could not be generated automatically.")
            .contains("## Source title")
            .contains("[Docs] README 기여 안내 보완");
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
                List.of("Update documentation examples.")
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
            "## 변경 사항\n- README 안내를 보완합니다.",
            Instant.parse("2026-05-15T01:00:00Z")
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
