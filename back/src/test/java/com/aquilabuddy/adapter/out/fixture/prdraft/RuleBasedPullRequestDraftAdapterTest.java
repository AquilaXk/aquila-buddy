package com.aquilabuddy.adapter.out.fixture.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftIssueContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
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
}
