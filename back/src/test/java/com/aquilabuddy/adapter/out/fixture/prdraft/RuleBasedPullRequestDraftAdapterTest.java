package com.aquilabuddy.adapter.out.fixture.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftReadiness;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 fixture adapter가 입력값을 기반으로 재현 가능한 초안을 생성하는지 검증합니다.
 */
class RuleBasedPullRequestDraftAdapterTest {

    @Test
    void generatesDraftFromRequestContext() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
        ));

        assertThat(draft.title()).isEqualTo("docs: spring-projects/spring-boot#2 변경 초안");
        assertThat(draft.body()).contains("spring-projects/spring-boot #2");
        assertThat(draft.body()).contains("main -> feat/validation-docs");
        assertThat(draft.body()).contains("Add validation example");
    }

    @Test
    void includesRepositoryGuideContextWhenAvailable() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                "Please run tests before opening a PR.",
                "## Summary\n- Describe changes",
                List.of("## Summary\n- Improve docs")
            )
        ));

        assertThat(draft.body()).contains("## 저장소 참고 자료");
        assertThat(draft.body()).contains("CONTRIBUTING: Please run tests before opening a PR.");
        assertThat(draft.body()).contains("PR template: ## Summary");
        assertThat(draft.body()).contains("Merged PR samples: 1");
    }

    @Test
    void appliesPullRequestTemplateFormatWhenAvailable() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(
                null,
                """
                ## Summary
                - Describe changes

                ## Checklist
                - [ ] Tests added or updated
                """,
                List.of()
            )
        ));

        assertThat(draft.body())
            .contains("## Summary")
            .contains("## Checklist")
            .contains("- [ ] Tests added or updated")
            .contains("Add validation example");
    }

    @Test
    void appliesInferredMergedPullRequestHeadingsWhenTemplateIsMissing() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
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
            )
        ));

        assertThat(draft.body())
            .contains("## What changed")
            .contains("## Test plan")
            .contains("Add validation example");
        assertThat(draft.body().indexOf("## What changed"))
            .isLessThan(draft.body().indexOf("## Test plan"));
    }

    @Test
    void includesIssueContextWhenAvailable() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            PullRequestDraftRepositoryGuideContext.empty(),
            new PullRequestDraftIssueContext(
                "Fix validation message",
                "Error message should explain invalid input.",
                "Matches Java validation topic"
            )
        ));

        assertThat(draft.body()).contains("## 연결 이슈");
        assertThat(draft.body()).contains("Issue title: Fix validation message");
        assertThat(draft.body()).contains("Issue summary: Error message should explain invalid input.");
        assertThat(draft.body()).contains("Recommendation reason: Matches Java validation topic");
    }

    @Test
    void includesAnalysisContextWhenAvailable() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            PullRequestDraftRepositoryGuideContext.empty(),
            PullRequestDraftIssueContext.empty(),
            new PullRequestDraftAnalysisContext(
                "Follow the validation guide.",
                "if invalid then return a helpful message",
                List.of("Update documentation examples.")
            )
        ));

        assertThat(draft.body()).contains("## 분석 가이드");
        assertThat(draft.body()).contains("Guideline: Follow the validation guide.");
        assertThat(draft.body()).contains("Pseudo code: if invalid then return a helpful message");
        assertThat(draft.body()).contains("Side effect: Update documentation examples.");
    }

    @Test
    void populatesTemplateSectionsSemanticallyAndPassesReadinessGates() {
        RuleBasedPullRequestDraftAdapter adapter = new RuleBasedPullRequestDraftAdapter();

        String template = """
            ## Description
            <!-- Describe changes -->

            ## Test Plan
            - [ ] Unit tests

            ## Breaking Changes
            <!-- Compatibility impact -->
            """;

        PullRequestDraft draft = adapter.generate(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            42,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            new PullRequestDraftRepositoryGuideContext(null, template, List.of())
        ));

        assertThat(draft.body())
            .contains("close #42")
            .contains("## Description")
            .contains("Add validation example")
            .contains("## Test Plan")
            .contains("## Breaking Changes")
            .contains("하위 호환성을 깨뜨리는 변경 사항이 없습니다.");

        StoredPullRequestDraft storedDraft = new StoredPullRequestDraft(
            "draft-42",
            "spring-projects/spring-boot",
            42,
            "main",
            "feat/validation-docs",
            draft.title(),
            draft.body(),
            "issue-42",
            "Issue #42",
            "+Add validation example",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(storedDraft);
        assertThat(readiness.readyCount()).isEqualTo(7);
    }
}
