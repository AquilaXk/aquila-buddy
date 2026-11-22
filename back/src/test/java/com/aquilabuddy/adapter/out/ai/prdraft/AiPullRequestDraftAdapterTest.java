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
        assertThat(aiClient.userPrompt).contains("Issue summary: Error message should explain invalid input.");
        assertThat(aiClient.userPrompt).contains("Recommendation reason: Matches Java validation topic");
        assertThat(aiClient.userPrompt).contains("Guideline: Follow validation guide.");
        assertThat(aiClient.userPrompt).contains("Pseudo code: if invalid then return helpful message");
        assertThat(aiClient.userPrompt).contains("Side effect: Update documentation examples.");
    }

    @Test
    void draftPromptIncludesRepositoryGuideQualityRules() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        adapter.generate(request());

        assertThat(aiClient.systemPrompt)
            .contains("CONTRIBUTING, PR template, merged PR sample");
        assertThat(aiClient.userPrompt)
            .contains("Repository guide quality rules:")
            .contains("- Use PR template headings and checkboxes as required body structure when a PR template is present.")
            .contains("- If there is no PR template, follow the common heading order inferred from merged PR samples.")
            .contains("- Follow CONTRIBUTING instructions without inventing repository rules.")
            .contains("- Use merged PR samples as structure references only when a PR template is absent.");
    }

    @Test
    void draftPromptIncludesRepositoryDraftFormatContract() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.

            ## Checklist
            - [ ] Tests added or updated
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        adapter.generate(requestWithTemplateFormat());

        assertThat(aiClient.userPrompt)
            .contains("Repository draft format:")
            .contains("Repository draft format source: PR template")
            .contains("Required PR headings:")
            .contains("- ## Checklist")
            .contains("Required PR checkboxes:")
            .contains("- - [ ] Tests added or updated")
            .contains("- If the generated BODY misses required repository format lines, deterministic fallback is used.");
    }

    @Test
    void draftPromptIncludesDiffIssueAndAnalysisQualityRules() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        adapter.generate(request());

        assertThat(aiClient.userPrompt)
            .contains("Draft quality requirements:")
            .contains("- Base Changes and Tests on the diff content.")
            .contains("- Explain why the change matters using Issue context.")
            .contains("- Convert Analysis context into implementation notes without copying it verbatim.")
            .contains("- Do not mention files, tests, or side effects that are not present in the provided context.");
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
        assertThat(aiClient.userPrompt).contains("close #123");
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
    void failsClosedWhenAiResponseIsMalformed() {
        CapturingAiClient aiClient = new CapturingAiClient("plain response without markers");
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        assertThatThrownBy(() -> adapter.generate(request()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TITLE and BODY markers");
    }

    @Test
    void failsClosedWhenGeneratedDraftMissesRepositoryTemplateFormat() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE: docs: improve validation guide
            BODY: ## Summary
            - Improve the validation guide without the repository checklist.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        assertThatThrownBy(() -> adapter.generate(requestWithTemplateFormat()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("repository format");
    }

    @Test
    void failsClosedWhenAiResponseHasBlankTitleMarker() {
        CapturingAiClient aiClient = new CapturingAiClient("""
            TITLE:
            BODY: ## Summary
            - Missing title should fail closed.
            """);
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        assertThatThrownBy(() -> adapter.generate(request()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TITLE and BODY must not be blank");
    }

    @Test
    void failsClosedWhenAiProviderFails() {
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(new ThrowingAiClient());

        assertThatThrownBy(() -> adapter.generate(request()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("provider timeout");
    }

    @Test
    void failsClosedWhenTranslatedAiResponseIsMalformed() {
        CapturingAiClient aiClient = new CapturingAiClient("plain response without markers");
        AiPullRequestDraftAdapter adapter = new AiPullRequestDraftAdapter(aiClient);

        assertThatThrownBy(() -> adapter.translate(storedDraft(), "en"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TITLE and BODY markers");
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

    private static PullRequestDraftRequest requestWithTemplateFormat() {
        return new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                "Run tests before PR.",
                """
                ## Summary
                - Describe changes

                ## Checklist
                - [ ] Tests added or updated
                """,
                List.of()
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

    private static PullRequestDraftRequest requestWithMergedPullRequestFormat() {
        return new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                null,
                null,
                List.of(
                    "## What changed\n- Improve docs\n\n## Test plan\n- Run tests",
                    "## What changed\n- Fix validation\n\n## Test plan\n- Run focused tests",
                    "## What changed\n- Add examples\n\n## Test plan\n- Run check"
                )
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
            "close #123\n\n## 변경 사항\n- README 안내를 보완합니다.",
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

    private static class ThrowingAiClient implements AiClient {

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("provider timeout");
        }
    }
}
