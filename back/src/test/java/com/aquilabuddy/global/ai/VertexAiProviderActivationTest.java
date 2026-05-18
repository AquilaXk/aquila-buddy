package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.adapter.out.ai.analysis.AiIssueGuideGenerationAdapter;
import com.aquilabuddy.adapter.out.ai.prdraft.AiPullRequestDraftAdapter;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.application.port.out.prdraft.TranslatePullRequestDraftPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VertexAiProviderActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AiIssueGuideGenerationAdapter.class, AiPullRequestDraftAdapter.class)
        .withBean(AiClient.class, () -> (systemPrompt, userPrompt) -> """
            TITLE: docs: update guide
            BODY: ## Summary
            - Update guide.
            """);

    @Test
    void activatesAiAdaptersForVertexProvider() {
        contextRunner
            .withPropertyValues("app.ai.provider=vertex-ai")
            .run(context -> {
                assertThat(context).hasSingleBean(GenerateIssueGuidePort.class);
                assertThat(context).hasSingleBean(GeneratePullRequestDraftPort.class);
                assertThat(context).hasSingleBean(TranslatePullRequestDraftPort.class);
            });
    }
}
