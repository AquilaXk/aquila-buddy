package com.aquilabuddy.application.service.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 생성 application service가 생성 정책을 outbound port 뒤로 위임하는지 검증합니다.
 */
class PullRequestDraftServiceTest {

    @Test
    void delegatesDraftGenerationToOutboundPort() {
        PullRequestDraftService service = new PullRequestDraftService(new FakeGeneratePullRequestDraftPort());

        PullRequestDraft draft = service.create(new PullRequestDraftRequest(
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
        ));

        assertThat(draft.title()).isEqualTo("generated title");
        assertThat(draft.body()).contains("spring-projects/spring-boot");
    }

    private static class FakeGeneratePullRequestDraftPort implements GeneratePullRequestDraftPort {

        @Override
        public PullRequestDraft generate(PullRequestDraftRequest request) {
            return new PullRequestDraft("generated title", "body for " + request.upstreamRepo());
        }
    }
}
