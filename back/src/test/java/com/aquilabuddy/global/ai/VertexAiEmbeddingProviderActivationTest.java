package com.aquilabuddy.global.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class VertexAiEmbeddingProviderActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AiConfig.class, VertexAiScanConfig.class)
        .withPropertyValues(
            "app.ai.provider=vertex-ai",
            "app.ai.vertex.project-id=grepp-ai-project"
        );

    @Test
    void activatesEmbeddingClientForVertexProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(VertexAiEmbeddingClient.class);
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = VertexAiClient.class)
    static class VertexAiScanConfig {
    }
}
