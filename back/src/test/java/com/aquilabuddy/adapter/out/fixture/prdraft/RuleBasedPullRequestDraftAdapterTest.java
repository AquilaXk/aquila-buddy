package com.aquilabuddy.adapter.out.fixture.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
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
}
